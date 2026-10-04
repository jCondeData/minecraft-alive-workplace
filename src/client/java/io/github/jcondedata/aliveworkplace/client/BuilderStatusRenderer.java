package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Iterator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a builder's status above its head: "Starter Cottage · 42%", a progress bar, what it is
 * doing right now, and under that what the build is short of and where its builder takes materials from. Fed by {@link BuilderStatusSync} packets; an entry disappears a few seconds after
 * the last update (build finished, builder out of range).
 */
public final class BuilderStatusRenderer {
	private static final double MAX_DISTANCE = 24;
	private static final long FORGET_AFTER_MS = 3500;
	private static final int BAR_WIDTH = 60;
	private static final int LINE = 10;

	private record Entry(BuilderStatusSync.Status status, long received) {
	}

	private static final Int2ObjectMap<Entry> STATUS = new Int2ObjectOpenHashMap<>();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BuilderStatusSync.Status.TYPE,
			(payload, context) -> STATUS.put(payload.entityId(), new Entry(payload, System.currentTimeMillis())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATUS.clear());
		WorldRenderEvents.AFTER_ENTITIES.register(BuilderStatusRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		if (STATUS.isEmpty() || context.consumers() == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui && !Boolean.getBoolean("aliveworkplace.shots")) {
			return;
		}
		long now = System.currentTimeMillis();
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		PoseStack pose = new PoseStack();
		MultiBufferSource buffers = context.consumers();
		Iterator<Int2ObjectMap.Entry<Entry>> it = STATUS.int2ObjectEntrySet().iterator();
		while (it.hasNext()) {
			Int2ObjectMap.Entry<Entry> e = it.next();
			if (now - e.getValue().received() > FORGET_AFTER_MS) {
				it.remove();
				continue;
			}
			Entity entity = context.world().getEntity(e.getIntKey());
			if (entity == null || entity.isRemoved() || entity.isInvisible()) {
				continue;
			}
			Vec3 at = entity.getPosition(partial);
			if (at.distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE) {
				continue;
			}
			double top = entity.getBbHeight() + 0.55 + (entity.shouldShowName() ? 0.3 : 0);
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y + top, at.z - cam.z);
			pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
			pose.scale(0.025f, -0.025f, 0.025f);
			draw(mc.font, pose.last().pose(), buffers, e.getValue().status());
			pose.popPose();
		}
	}

	private static void draw(Font font, Matrix4f matrix, MultiBufferSource buffers, BuilderStatusSync.Status status) {
		int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255) << 24;
		int light = LightTexture.FULL_BRIGHT;
		// Lines from the bottom up: the materials lines (short of, takes from), status line, bar, title.
		int more = status.more().size();
		for (int i = 0; i < more; i++) {
			text(font, matrix, buffers, status.more().get(i), -LINE * (more - 1 - i), background, light);
		}
		float base = -LINE * more;
		text(font, matrix, buffers, status.line(), base, background, light);
		if (status.progress() < 0) {
			text(font, matrix, buffers, status.title(), base - 11, background, light); // no progress to show (lumberjacks)
			return;
		}
		bar(matrix, buffers, base - 6, status.progress(), light);
		text(font, matrix, buffers, status.title(), base - 18, background, light);
	}

	private static void text(Font font, Matrix4f matrix, MultiBufferSource buffers, Component text, float y, int background, int light) {
		float x = -font.width(text) / 2f;
		font.drawInBatch(text, x, y, 0x20FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, background, light);
		font.drawInBatch(text, x, y, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
	}

	private static void bar(Matrix4f matrix, MultiBufferSource buffers, float y, float progress, int light) {
		float left = -BAR_WIDTH / 2f;
		float right = BAR_WIDTH / 2f;
		float filled = left + BAR_WIDTH * Math.max(0, Math.min(1, progress));
		VertexConsumer vc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
		// The quads must not overlap: this render type sorts them by distance, which would bury the fill.
		int frame = 0xC0000000;
		quad(vc, matrix, left - 1, y - 1, right + 1, y, frame, light);
		quad(vc, matrix, left - 1, y + 3, right + 1, y + 4, frame, light);
		quad(vc, matrix, left - 1, y, left, y + 3, frame, light);
		quad(vc, matrix, right, y, right + 1, y + 3, frame, light);
		if (filled > left) {
			quad(vc, matrix, left, y, filled, y + 3, 0xFF5CCB3A, light);
		}
		if (filled < right) {
			quad(vc, matrix, filled, y, right, y + 3, 0xFF3A3A3A, light);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int light) {
		float z = 0;
		vc.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
		vc.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
		vc.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
		vc.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
	}

	private BuilderStatusRenderer() {
	}
}
