package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B1505 (B808's rule for the Hose Pulley's filling): a Hose Pulley standing outside a protected village pours no fluid
 * into it ({@link VillageProtection#mayMachineChange}, judged from the pulley). Create asks {@code getAtPos} what each cell
 * it would spread into is (fillable, already filled, or in the way), from the block there; a village cell the pulley may
 * not change reads as bedrock, so it is in the way and the fluid neither goes in nor spreads on through it. The draining
 * side is {@link FluidSearchMixin}'s.
 */
@Mixin(FluidFillingBehaviour.class)
public abstract class FluidFillMixin {
	@WrapOperation(method = "getAtPos", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState aliveworkplace$villageCellsInTheWay(Level level, BlockPos pos, Operation<BlockState> original) {
		BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
		if (VillageProtection.guarding() && self.blockEntity != null
			&& !VillageProtection.mayMachineChange(level, self.blockEntity.getBlockPos(), pos)) {
			return Blocks.BEDROCK.defaultBlockState();
		}
		return original.call(level, pos);
	}
}
