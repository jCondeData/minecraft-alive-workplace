package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Golem Smith's golems (29.15): every iron golem gets the role goals (idle on a plain golem): a Wall Sentry holds
 * its post above everything, a Hauler's or Farmhand's work ranks below the golem's attack. A Wall Sentry's blow also
 * throws the attacker back off the wall.
 */
@Mixin(IronGolem.class)
abstract class IronGolemRolesMixin extends AbstractGolem {
	protected IronGolemRolesMixin(EntityType<? extends AbstractGolem> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void aliveworkplace$roleGoals(CallbackInfo ci) {
		IronGolem self = (IronGolem) (Object) this;
		this.goalSelector.addGoal(0, new WallSentries.HoldGoal(self));
		this.goalSelector.addGoal(2, new GolemRoleGoal(self));
	}

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void aliveworkplace$sentryKnockback(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			WallSentries.struck((IronGolem) (Object) this, target);
		}
	}
}
