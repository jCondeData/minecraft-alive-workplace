package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.work.JobSiteTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts workstations going, before vanilla removes their record, so a stale memory of one can be told apart (bug B15). */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "onBlockStateChange", at = @At("HEAD"))
	private void aliveworkplace$countWorkstationBreaks(BlockPos pos, BlockState old, BlockState now, CallbackInfo ci) {
		JobSiteTickets.changed((ServerLevel) (Object) this, pos, old, now);
	}
}
