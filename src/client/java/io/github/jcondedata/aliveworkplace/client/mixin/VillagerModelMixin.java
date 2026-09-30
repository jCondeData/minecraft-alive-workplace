package io.github.jcondedata.aliveworkplace.client.mixin;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A villager riding something (a guard on horseback, a ferryman or a fisher in a boat) sits with their legs forward, as
 * a player does; the villager model has no riding pose of its own, so they stood on the saddle. Only villagers: they're
 * the ones lowered into the seat ({@code VillagerSeatMixin}); wandering traders and witches share the model but not the
 * seat. The model's parts are shared by every villager drawn, so the legs' sideways turn is put back for the others.
 */
@Mixin(VillagerModel.class)
abstract class VillagerModelMixin {
	@Shadow
	@Final
	private ModelPart rightLeg;
	@Shadow
	@Final
	private ModelPart leftLeg;

	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void aliveworkplace$sit(Entity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, CallbackInfo ci) {
		if (entity instanceof net.minecraft.world.entity.npc.Villager && entity.isPassenger()) {
			rightLeg.xRot = -1.4137167f;
			rightLeg.yRot = (float) (Math.PI / 10);
			rightLeg.zRot = 0.07853982f;
			leftLeg.xRot = -1.4137167f;
			leftLeg.yRot = (float) (-Math.PI / 10);
			leftLeg.zRot = -0.07853982f;
		} else {
			rightLeg.zRot = 0f;
			leftLeg.zRot = 0f;
		}
	}
}
