package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.Disasters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A disaster's {@code farmland_dries} (ROADMAP 32.15): wet farmland in an afflicted village dries a step, water or not;
 * and farmland a Well Keeper watered today keeps its moisture that day, drought or not (32.17). Dry farmland by water
 * isn't wet again by it in a drought (B946): it stays tilled and dry, as a pond keeps it from turning to dirt, until
 * a Well Keeper waters it or it rains.
 */
@Mixin(FarmBlock.class)
public abstract class DroughtFarmlandMixin {
	@Shadow
	private static boolean isNearWater(LevelReader level, BlockPos pos) {
		throw new AssertionError();
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$disasterDries(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (io.github.jcondedata.aliveworkplace.threat.Drought.keepsWet(level, pos, state)) {
			ci.cancel();
			return;
		}
		int moisture = state.getValue(FarmBlock.MOISTURE);
		if (moisture > 0 && Disasters.farmlandDries(level, pos)) {
			level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, moisture - 1), 2);
			ci.cancel();
		} else if (moisture == 0 && !level.isRainingAt(pos.above()) && isNearWater(level, pos) && Disasters.farmlandDries(level, pos)) {
			ci.cancel(); // vanilla would wet it to 7 from the water: in a drought the water is too low to reach it
		}
	}
}
