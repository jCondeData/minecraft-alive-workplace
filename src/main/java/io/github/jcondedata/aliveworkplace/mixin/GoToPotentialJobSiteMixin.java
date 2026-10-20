package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.work.JobSiteApproach;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager walking to the workstation it picked is walked to the floor beside one built in under solid blocks, from
 * where it takes it (B168, {@link JobSiteApproach}); vanilla's walk target on the block itself is lifted into the roof.
 * <p>
 * And the walk ends at bedtime (B258). Vanilla starts it only while idle, working or playing, but then keeps it up for a
 * minute whatever the hour: a jobless villager who picked a workstation just before dusk went on walking to it instead of
 * to bed, on a walk target vanilla's bed walk can't replace, and the minute of "can't reach" it ran up made vanilla give
 * the bed up. Now it stops once the villager rests (the workstation is let go, as when the walk times out, and picked
 * again by day), and its walk and its "can't reach" time go with it, so the bed walk starts fresh.
 */
@Mixin(GoToPotentialJobSite.class)
abstract class GoToPotentialJobSiteMixin {
	@Shadow
	@Final
	float speedModifier;

	@Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("TAIL"))
	private void aliveworkplace$besideBuiltInWorkstation(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		JobSiteApproach.retarget(villager, speedModifier);
	}

	@Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)Z", at = @At("RETURN"), cancellable = true)
	private void aliveworkplace$notAtBedtime(ServerLevel level, Villager villager, long gameTime, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && villager.getBrain().isActive(Activity.REST)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At("TAIL"))
	private void aliveworkplace$bedWalkStartsFresh(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (villager.getBrain().isActive(Activity.REST)) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		}
	}
}
