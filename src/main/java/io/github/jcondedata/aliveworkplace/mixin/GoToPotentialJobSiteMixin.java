package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.work.JobSiteApproach;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A villager walking to the workstation it picked is walked to the floor beside one built in under solid blocks, from
 * where it takes it (B168, {@link JobSiteApproach}); vanilla's walk target on the block itself is lifted into the roof.
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
}
