package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Mega Bracelet (38.5): a gold band with the rainbow Key Stone on the left wrist of a villager trainer whose team can
 * Mega Evolve, drawn over their outfit (and any class or Legend look). The server says who wears one
 * ({@link MegaBracelets.Bracelet}); only with Mega Showdown installed.
 */
public class MegaBraceletLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
	public static final ResourceLocation TEXTURE = AliveWorkplace.id("textures/entity/villager/mega_bracelet.png");
	/** The entity ids of the villagers this client was told wear one. */
	private static final IntSet WEARING = new IntOpenHashSet();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MegaBracelets.Bracelet.TYPE, (payload, context) -> {
			if (payload.wears()) {
				WEARING.add(payload.entityId());
			} else {
				WEARING.remove(payload.entityId());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WEARING.clear());
	}

	/** Whether the server said {@code entity} wears a Mega Bracelet (the showcase checks it). */
	public static boolean wears(Entity entity) {
		return MegaBracelets.MOD && WEARING.contains(entity.getId());
	}

	public MegaBraceletLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
		float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (wears(villager) && !villager.isInvisible() && !villager.isBaby()) {
			renderColoredCutoutModel(getParentModel(), TEXTURE, poseStack, buffers, light, villager, -1);
		}
	}
}
