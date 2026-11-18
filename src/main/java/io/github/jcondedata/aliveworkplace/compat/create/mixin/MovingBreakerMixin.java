package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B808: a drill or saw carried on a contraption (a piston's head, a bearing's arm) whose machine stands outside a
 * protected village doesn't break the blocks inside it: Create breaks them through the drill's movement behaviour, not
 * its block entity ({@link BlockBreakerMixin}). The contraption passes them by, as blocks it can't break.
 */
@Mixin(BlockBreakingMovementBehaviour.class)
public abstract class MovingBreakerMixin {
	@WrapOperation(method = {"visitNewPosition", "tickBreaker"}, remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingMovementBehaviour;canBreak(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean aliveworkplace$keepVillagesWhole(BlockBreakingMovementBehaviour self, Level level, BlockPos pos, BlockState state,
		Operation<Boolean> original, @Local(argsOnly = true) MovementContext context) {
		return original.call(self, level, pos, state) && (context.contraption == null || CreateMachines.may(level, context.contraption, pos));
	}
}
