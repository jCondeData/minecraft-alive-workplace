package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.jcondedata.aliveworkplace.legend.LegendLook;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * A Legend's outfit (29.4), drawn over their trade's outfit on the villager model, as the profession layer draws the
 * job's over the biome's: {@code textures/entity/villager/legend/<id>.png}, or the stand-in when a Legend has no
 * texture of their own yet. The server tells the client who is a Legend ({@link LegendLook.Look}).
 */
public class LegendLookLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
	/** Entity id to outfit, for the Legends this client tracks. */
	private static final Int2ObjectMap<ResourceLocation> LOOKS = new Int2ObjectOpenHashMap<>();
	/** Outfit textures checked for (a missing one falls back to the stand-in instead of the magenta check). */
	private static final Map<ResourceLocation, ResourceLocation> TEXTURES = new HashMap<>();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LegendLook.Look.TYPE, (payload, context) -> {
			if (payload.legend()) {
				LOOKS.put(payload.entityId(), payload.outfit());
			} else {
				LOOKS.remove(payload.entityId());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LOOKS.clear();
			TEXTURES.clear();
		});
	}

	/** Whether the server said {@code entity} is a Legend (their name shows in gold over their head). */
	public static boolean isLegend(Entity entity) {
		return LOOKS.containsKey(entity.getId());
	}

	public LegendLookLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
		float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		ResourceLocation outfit = LOOKS.get(villager.getId());
		if (outfit == null || villager.isInvisible()) {
			return;
		}
		renderColoredCutoutModel(getParentModel(), texture(outfit), poseStack, buffers, light, villager, -1);
	}

	private static ResourceLocation texture(ResourceLocation outfit) {
		return TEXTURES.computeIfAbsent(outfit, o -> Minecraft.getInstance().getResourceManager().getResource(o).isPresent() ? o : LegendLook.PLACEHOLDER);
	}
}
