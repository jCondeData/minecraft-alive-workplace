package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import io.github.jcondedata.aliveworkplace.compat.create.CreateMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1487 (B993's rule for the Elevator Pulley): an elevator whose pulley stands outside a protected village leaves the
 * village's blocks out of its cabin when it is put together, so they stay where they are. Create never asks
 * {@link ContraptionColliderMixin}'s question for an elevator (it doesn't collide with terrain), so without this its
 * cabin took them along, and breaking the pulley then dropped them outside ({@link ContraptionPlacementMixin}). Here
 * such a block counts as one Create doesn't need to move, like air: the search for the cabin stops at it. Elevators
 * inside the village, or with its protection off, take their cabin as before.
 */
@Mixin(Contraption.class)
public abstract class ElevatorCabinMixin {
	@WrapOperation(method = "moveBlock", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/api/contraption/BlockMovementChecks;isMovementNecessary(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean aliveworkplace$leaveVillageBlocks(BlockState state, Level level, BlockPos pos, Operation<Boolean> original) {
		if (!original.call(state, level, pos)) {
			return false;
		}
		Contraption self = (Contraption) (Object) this;
		return !(self instanceof ElevatorContraption) || level.isClientSide() || CreateMachines.may(level, self, pos);
	}
}
