package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Under Curfew a villager kept in rests from dusk to dawn whatever their schedule says (30.9, {@code hall/Curfew}): the
 * schedule check of the villager whose brain runs now ({@code WorkerLimits.thinker}) is overruled.
 */
@Mixin(Brain.class)
abstract class BrainMixin {
	@Inject(method = "updateActivityFromSchedule", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$curfew(long dayTime, long gameTime, CallbackInfo ci) {
		Villager villager = io.github.jcondedata.aliveworkplace.work.WorkerLimits.thinker();
		if (villager != null && villager.getBrain() == (Object) this && io.github.jcondedata.aliveworkplace.hall.Curfew.rest(villager)) {
			ci.cancel();
		}
	}
}
