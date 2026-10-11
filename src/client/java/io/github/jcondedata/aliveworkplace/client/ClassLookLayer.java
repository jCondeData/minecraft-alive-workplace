package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.jcondedata.aliveworkplace.people.ClassLook;
import io.github.jcondedata.aliveworkplace.people.ClassOutfits;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Dressed by class, in game (34.18): a villager's class drawn over their job's outfit ({@link ClassOutfits}), on grown
 * villagers and children alike, as the profession layer draws the job's over the biome's. The server tells the client
 * each villager's class and stage of life ({@link ClassLook.Look}); the elder's look is {@link Elder}, drawn after it.
 * Only the villager renderer gets these layers, so a zombie villager never wears them. {@code classOutfits} off: none.
 */
public class ClassLookLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
	/** Entity id to look, for the villagers this client tracks. */
	private static final Int2ObjectMap<ClassLook.Look> LOOKS = new Int2ObjectOpenHashMap<>();
	/** Layer textures checked for (a missing one is skipped instead of drawing the magenta check). */
	private static final Map<ResourceLocation, Boolean> TEXTURES = new HashMap<>();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ClassLook.Look.TYPE, (payload, context) -> LOOKS.put(payload.entityId(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LOOKS.clear();
			TEXTURES.clear();
		});
	}

	/** What the server last told this client about entity {@code entityId}'s class and stage of life, or null (the showcase checks it). */
	@Nullable
	public static ClassLook.Look told(int entityId) {
		return LOOKS.get(entityId);
	}

	/** What the server last said about {@code villager}, or null (not told, or class outfits off). */
	@Nullable
	static ClassLook.Look look(Villager villager) {
		return ClassLook.ENABLED && !villager.isInvisible() ? LOOKS.get(villager.getId()) : null;
	}

	static void draw(RenderLayer<Villager, VillagerModel<Villager>> layer, ResourceLocation texture, PoseStack poseStack, MultiBufferSource buffers,
		int light, Villager villager) {
		if (TEXTURES.computeIfAbsent(texture, t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent())) {
			renderColoredCutoutModel(layer.getParentModel(), texture, poseStack, buffers, light, villager, -1);
		}
	}

	public ClassLookLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
		float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		ClassLook.Look look = look(villager);
		if (look != null && look.outfit().isPresent()) {
			draw(this, look.outfit().get(), poseStack, buffers, light, villager);
		}
	}

	/** The elder's look (34.19): grey brows and a beard on the head, under any hat, for a grown villager the server calls an elder. */
	public static class Elder extends RenderLayer<Villager, VillagerModel<Villager>> {
		public Elder(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			ClassLook.Look look = look(villager);
			if (look != null && look.lifeStage() == LifeStages.Stage.ELDER && !villager.isBaby()) {
				draw(this, ClassOutfits.ELDER, poseStack, buffers, light, villager);
			}
		}
	}
}
