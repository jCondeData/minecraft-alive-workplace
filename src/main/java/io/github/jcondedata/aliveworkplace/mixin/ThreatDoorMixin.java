package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A raider (a vindicator, a rising's zombie) never breaks down a builder's door (ROADMAP 32.16a, {@link ThreatDamage}). */
@Mixin(BreakDoorGoal.class)
public abstract class ThreatDoorMixin extends DoorInteractGoal {
	protected ThreatDoorMixin(Mob mob) {
		super(mob);
	}

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$notTheBuildersDoor(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && aliveworkplace$spared()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$stopAtTheBuildersDoor(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && aliveworkplace$spared()) {
			cir.setReturnValue(false);
		}
	}

	private boolean aliveworkplace$spared() {
		return doorPos != null && mob.level() instanceof ServerLevel level && ThreatDamage.spares(level, doorPos, mob);
	}
}
