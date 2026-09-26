package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the blocks of a placed blueprint as translucent "ghost" blocks while the player holds it, so
 * you see exactly what will be built and where. Blocks that are already in place are not drawn.
 */
public final class BlueprintPreviewRenderer {
	private static final float ALPHA = 0.4f;
	private static final double MAX_DISTANCE = 64;
	private static final int MAX_DRAWN = 12_000;
	private static final int RECHECK_FRAMES = 10;
	private static final Direction[] DIRECTIONS = Direction.values();

	/** Template-space blocks per blueprint, as sent by the server. */
	private static final Map<ResourceLocation, List<Ghost>> TEMPLATES = new HashMap<>();
	private static final Set<ResourceLocation> REQUESTED = new HashSet<>();

	private record Ghost(BlockPos pos, BlockState state) {
	}

	/** A ghost ready to draw: its model, tint and which of its faces are visible (bit per Direction). */
	private record Drawn(BlockPos pos, BlockState state, BakedModel model, float r, float g, float b, int faces) {
	}

	@Nullable
	private static BlueprintData.Placement cachedPlacement;
	@Nullable
	private static ResourceLocation cachedId;
	/** World-space ghosts for the current placement. */
	private static List<Ghost> placed = List.of();
	/** The ones not built yet. */
	private static List<Drawn> remaining = List.of();
	private static int recheck;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PreviewNetworking.Data.TYPE, (payload, context) -> {
			List<Ghost> ghosts = new ArrayList<>(payload.blocks().length / 4);
			int[] b = payload.blocks();
			for (int i = 0; i + 3 < b.length; i += 4) {
				BlockState state = Block.stateById(payload.palette().get(b[i + 3]));
				ghosts.add(new Ghost(new BlockPos(b[i], b[i + 1], b[i + 2]), state));
			}
			TEMPLATES.put(payload.id(), ghosts);
			cachedId = null; // rebuild
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TEMPLATES.clear();
			REQUESTED.clear();
			cachedId = null;
			placed = List.of();
			remaining = List.of();
		});
		WorldRenderEvents.AFTER_TRANSLUCENT.register(BlueprintPreviewRenderer::render);
	}

	/**
	 * The blueprint's blocks (template space) if this client has them; otherwise asks the server once
	 * and returns null until the answer arrives.
	 */
	@Nullable
	public static List<BlockState> blocks(ResourceLocation id) {
		List<Ghost> template = TEMPLATES.get(id);
		if (template == null) {
			if (REQUESTED.add(id) && Minecraft.getInstance().getConnection() != null) {
				ClientPlayNetworking.send(new PreviewNetworking.Request(id));
			}
			return null;
		}
		return template.stream().map(Ghost::state).toList();
	}

	@Nullable
	private static BlueprintData heldPlacedBlueprint(Minecraft mc) {
		if (mc.player == null) {
			return null;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = mc.player.getItemInHand(hand);
			BlueprintData data = BlueprintItem.data(stack).orElse(null);
			if (data != null && data.placement().isPresent()
				&& data.placement().get().dimension().equals(mc.player.level().dimension().location())) {
				return data;
			}
		}
		return null;
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		BlueprintData data = heldPlacedBlueprint(mc);
		if (data == null) {
			return;
		}
		List<Ghost> template = TEMPLATES.get(data.structure());
		if (template == null) {
			if (REQUESTED.add(data.structure())) {
				ClientPlayNetworking.send(new PreviewNetworking.Request(data.structure()));
			}
			return;
		}
		BlueprintData.Placement placement = data.placement().get();
		ClientLevel level = context.world();
		if (!data.structure().equals(cachedId) || !placement.equals(cachedPlacement)) {
			cachedId = data.structure();
			cachedPlacement = placement;
			List<Ghost> world = new ArrayList<>(template.size());
			for (Ghost g : template) {
				BlockPos pos = placement.origin().offset(StructureTemplate.transform(g.pos(), placement.mirror(), placement.rotation(), BlockPos.ZERO));
				world.add(new Ghost(pos, MaterialRules.forPlacement(g.state().mirror(placement.mirror()).rotate(placement.rotation()))));
			}
			placed = world;
			recheck = 0;
		}
		if (--recheck <= 0) {
			recheck = RECHECK_FRAMES;
			remaining = remaining(mc, level);
		}
		draw(context, mc);
	}

	/** Ghosts not built yet, with the faces that are hidden behind other ghosts or real blocks culled. */
	private static List<Drawn> remaining(Minecraft mc, ClientLevel level) {
		Long2ObjectMap<BlockState> left = new Long2ObjectOpenHashMap<>();
		for (Ghost g : placed) {
			if (!MaterialRules.matches(level.getBlockState(g.pos()), g.state())) {
				left.put(g.pos().asLong(), g.state());
			}
		}
		BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
		List<Drawn> out = new ArrayList<>(left.size());
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		for (Long2ObjectMap.Entry<BlockState> e : left.long2ObjectEntrySet()) {
			BlockPos pos = BlockPos.of(e.getLongKey());
			BlockState state = e.getValue();
			if (state.getRenderShape() != RenderShape.MODEL) {
				continue; // chests, beds, signs draw through block-entity renderers: not in the preview
			}
			int faces = 0;
			for (Direction d : DIRECTIONS) {
				n.setWithOffset(pos, d);
				BlockState ghost = left.get(n.asLong());
				boolean hidden = ghost != null ? ghost.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) : level.getBlockState(n).isSolidRender(level, n);
				if (!hidden) {
					faces |= 1 << d.ordinal();
				}
			}
			int tint = mc.getBlockColors().getColor(state, level, pos, 0);
			float r = tint == -1 ? 1f : (tint >> 16 & 255) / 255f;
			float g = tint == -1 ? 1f : (tint >> 8 & 255) / 255f;
			float b = tint == -1 ? 1f : (tint & 255) / 255f;
			out.add(new Drawn(pos, state, dispatcher.getBlockModel(state), r, g, b, faces));
		}
		return out;
	}

	private static void draw(WorldRenderContext context, Minecraft mc) {
		if (remaining.isEmpty()) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		RenderType type = Sheets.translucentCullBlockSheet();
		VertexConsumer consumer = buffers.getBuffer(type);
		PoseStack pose = new PoseStack();
		RandomSource random = RandomSource.create();
		int drawn = 0;
		for (Drawn d : remaining) {
			BlockPos p = d.pos();
			double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
			if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
				continue;
			}
			pose.pushPose();
			pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
			// Shrink a hair so ghosts never z-fight with whatever is in the way.
			pose.translate(0.5, 0.5, 0.5);
			pose.scale(0.98f, 0.98f, 0.98f);
			pose.translate(-0.5, -0.5, -0.5);
			long seed = d.state().getSeed(p);
			for (Direction dir : DIRECTIONS) {
				if ((d.faces() & 1 << dir.ordinal()) != 0) {
					random.setSeed(seed);
					quads(consumer, pose.last(), d, d.model().getQuads(d.state(), dir, random));
				}
			}
			random.setSeed(seed);
			quads(consumer, pose.last(), d, d.model().getQuads(d.state(), null, random));
			pose.popPose();
			if (++drawn >= MAX_DRAWN) {
				break;
			}
		}
		buffers.endBatch(type);
	}

	private static void quads(VertexConsumer consumer, PoseStack.Pose pose, Drawn d, List<BakedQuad> quads) {
		for (BakedQuad quad : quads) {
			float r = quad.isTinted() ? d.r() : 1f;
			float g = quad.isTinted() ? d.g() : 1f;
			float b = quad.isTinted() ? d.b() : 1f;
			consumer.putBulkData(pose, quad, r, g, b, ALPHA, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		}
	}

	private BlueprintPreviewRenderer() {
	}
}
