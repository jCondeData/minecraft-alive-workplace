package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.Disasters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A disaster's {@code crop_growth} for the plants that aren't crops (ROADMAP 32.17, the drought): melon and pumpkin stems
 * and sweet berry bushes, the same one check as {@link DroughtCropMixin}.
 */
@Mixin({StemBlock.class, SweetBerryBushBlock.class})
public abstract class DroughtPlantMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$disasterGrowth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (!Disasters.cropGrows(level, pos, random)) {
			ci.cancel();
		}
	}
}
