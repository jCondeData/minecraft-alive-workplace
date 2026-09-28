package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Armor on a villager (in practice a guard): the player armor models fitted over the villager's shape — the helmet
 * raised to the villager's taller head, the chestplate over the robe with its sleeves on the folded arms, the legs and
 * boots on the legs under the robe.
 */
public class GuardArmorLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
	/** The villager's head is 10 pixels tall, a player's 8: the helmet sits 2 higher. */
	private static final float HEAD_RAISE = 2f;
	/** The robe is deeper than a player's body: the chestplate is stretched to sit on top of it. */
	private static final float VEST_DEPTH = 1.3f;
	private static final float VEST_WIDTH = 1.08f;
	/** The villager's folded arms lean forward this much (VillagerModel's "arms" part). */
	private static final float ARMS_PITCH = -0.75f;

	private final HumanoidModel<Villager> inner;
	private final HumanoidModel<Villager> outer;

	public GuardArmorLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, EntityModelSet models) {
		super(parent);
		this.inner = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
		this.outer = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
		float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
			ItemStack stack = villager.getItemBySlot(slot);
			if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) {
				continue;
			}
			HumanoidModel<Villager> model = slot == EquipmentSlot.LEGS ? inner : outer;
			if (!fit(model, slot)) {
				return;
			}
			poseStack.pushPose();
			int dye = stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536)) : -1;
			boolean innerLayer = slot == EquipmentSlot.LEGS;
			for (ArmorMaterial.Layer layer : armor.getMaterial().value().layers()) {
				model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.armorCutoutNoCull(layer.texture(innerLayer))), light,
					OverlayTexture.NO_OVERLAY, layer.dyeable() ? dye : -1);
			}
			if (stack.hasFoil()) {
				model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY);
			}
			poseStack.popPose();
		}
	}

	/**
	 * Puts the armor model's parts where the villager's are, showing only what this slot covers. False when the
	 * villager model isn't the vanilla shape (a resource pack or mod replaced it): then no armor is drawn.
	 */
	private boolean fit(HumanoidModel<Villager> model, EquipmentSlot slot) {
		VillagerModel<Villager> villager = getParentModel();
		ModelPart root = villager.root();
		if (!root.hasChild("body") || !root.hasChild("right_leg") || !root.hasChild("left_leg")) {
			return false;
		}
		ModelPart head = villager.getHead();
		ModelPart rightLeg = root.getChild("right_leg");
		ModelPart leftLeg = root.getChild("left_leg");
		model.setAllVisible(false);
		model.young = false; // a fresh model starts out as a child's (smaller, lower)
		model.riding = false;
		model.head.copyFrom(head);
		model.head.y = head.y - HEAD_RAISE;
		model.hat.copyFrom(model.head);
		model.body.copyFrom(root.getChild("body"));
		model.body.xScale = VEST_WIDTH;
		model.body.zScale = VEST_DEPTH;
		// Sleeves on the folded arms: a player's arm is 12 long, a villager's 8.
		for (ModelPart arm : new ModelPart[]{model.rightArm, model.leftArm}) {
			arm.setPos(arm == model.rightArm ? -5f : 5f, 3f, -1f);
			arm.setRotation(ARMS_PITCH, 0f, 0f);
			arm.xScale = 1f;
			arm.yScale = 8f / 12f;
			arm.zScale = 1f;
		}
		model.rightLeg.copyFrom(rightLeg);
		model.leftLeg.copyFrom(leftLeg);
		switch (slot) {
			case HEAD -> {
				model.head.visible = true;
				model.hat.visible = true;
			}
			case CHEST -> {
				model.body.visible = true;
				model.rightArm.visible = true;
				model.leftArm.visible = true;
			}
			case LEGS, FEET -> {
				model.rightLeg.visible = true;
				model.leftLeg.visible = true;
			}
			default -> {
			}
		}
		return true;
	}
}
