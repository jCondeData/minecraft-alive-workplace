package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.work.JobSiteTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts workstations going, before vanilla removes their record, so a stale memory of one can be told apart (bug B15);
 * and, in GameTests only, refuses the entities {@link HostileSpawns#REFUSED_FOR_TESTS} names at {@code addEntity}, as
 * a mob blocklist such as MobsBeGone does (B208).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "onBlockStateChange", at = @At("HEAD"))
	private void aliveworkplace$countWorkstationBreaks(BlockPos pos, BlockState old, BlockState now, CallbackInfo ci) {
		JobSiteTickets.changed((ServerLevel) (Object) this, pos, old, now);
	}

	@Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$refuseForTests(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		java.util.function.Predicate<Entity> refused = HostileSpawns.REFUSED_FOR_TESTS;
		if (refused != null && refused.test(entity)) {
			entity.discard();
			cir.setReturnValue(false);
		}
	}
}
