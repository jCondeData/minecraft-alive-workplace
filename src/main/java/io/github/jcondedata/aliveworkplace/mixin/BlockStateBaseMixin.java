package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.hall.HarvestIdols;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every random tick goes through here, whatever the block (vanilla's crops and stems, berries, cocoa, nether wart, and
 * other mods' crops alike): in harvest season a crop near a Harvest Idol may take a second (see {@link HarvestIdols}).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	@Inject(method = "randomTick", at = @At("TAIL"))
	private void aliveworkplace$harvestIdol(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		HarvestIdols.afterRandomTick(level, pos, (BlockState) (Object) this, random);
	}
}
