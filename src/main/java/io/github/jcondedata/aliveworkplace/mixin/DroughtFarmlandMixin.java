package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.Disasters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A disaster's {@code farmland_dries} (ROADMAP 32.15): wet farmland in an afflicted village dries a step, water or not. */
@Mixin(FarmBlock.class)
public abstract class DroughtFarmlandMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$disasterDries(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		int moisture = state.getValue(FarmBlock.MOISTURE);
		if (moisture > 0 && Disasters.farmlandDries(level, pos)) {
			level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, moisture - 1), 2);
			ci.cancel();
		}
	}
}
