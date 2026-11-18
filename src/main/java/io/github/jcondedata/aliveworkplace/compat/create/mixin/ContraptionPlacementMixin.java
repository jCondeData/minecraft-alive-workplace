package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.Contraption;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1033: a contraption whose machine stands outside a protected village (a bearing's arm stopped over its edge) sets
 * none of its blocks down inside it. Each such block drops as an item at the machine instead, as Create drops a block
 * that has nowhere to go.
 */
@Mixin(Contraption.class)
public abstract class ContraptionPlacementMixin {
	@WrapOperation(method = "addBlocksToWorld", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/contraptions/Contraption;customBlockPlacement(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean aliveworkplace$notInsideVillages(Contraption self, LevelAccessor world, BlockPos target, BlockState state, Operation<Boolean> original) {
		if (world instanceof Level level && !level.isClientSide() && !CreateMachines.may(level, self, target)) {
			Block.dropResources(state, level, CreateMachines.machine(self));
			return true; // placed elsewhere: Create skips it
		}
		return original.call(self, world, target, state);
	}
}
