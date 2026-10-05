package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * A Golem Smith's golem (29.15) wears its role over the iron golem's own skin: the Hauler's pack, the Farmhand's straw
 * hat, the Wall Sentry's helm ({@code textures/entity/iron_golem/<role>.png}). The server tells the client each golem's
 * role ({@link GolemSmith.Look}) when it starts seeing it.
 */
public class GolemRoleLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
	private static final Int2ObjectMap<ResourceLocation> ROLES = new Int2ObjectOpenHashMap<>();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(GolemSmith.Look.TYPE, (payload, context) -> {
			if (payload.role().isEmpty()) {
				ROLES.remove(payload.entityId());
			} else {
				ROLES.put(payload.entityId(), GolemSmith.texture(payload.role()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ROLES.clear());
	}

	public GolemRoleLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, IronGolem golem, float limbSwing, float limbSwingAmount,
		float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		ResourceLocation texture = ROLES.get(golem.getId());
		if (texture == null || golem.isInvisible()) {
			return;
		}
		renderColoredCutoutModel(getParentModel(), texture, poseStack, buffers, light, golem, -1);
	}
}
