package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1505 (B808's rule for the Hose Pulley's draining): a Hose Pulley standing outside a protected village counts no fluid
 * inside it as part of the pool it drains ({@link VillageProtection#mayMachineChange}, judged from the pulley). Create
 * finds the pool to drain (and checks whether a pool it fills is bottomless) with {@code search}, which follows the fluid
 * cell by cell; a village cell the pulley may not change reads as empty there, so the search neither queues it nor goes
 * on through it. The filling side is {@link FluidFillMixin}'s.
 */
@Mixin(FluidManipulationBehaviour.class)
public abstract class FluidSearchMixin {
	@WrapOperation(method = "search", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
	private FluidState aliveworkplace$noVillageFluid(Level level, BlockPos pos, Operation<FluidState> original) {
		BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
		if (VillageProtection.guarding() && self.blockEntity != null
			&& !VillageProtection.mayMachineChange(level, self.blockEntity.getBlockPos(), pos)) {
			return Fluids.EMPTY.defaultFluidState();
		}
		return original.call(level, pos);
	}
}
