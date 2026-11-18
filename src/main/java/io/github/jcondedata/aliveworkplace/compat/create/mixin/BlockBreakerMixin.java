package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * B808, B993, B1015: a Mechanical Drill or Saw standing outside a protected village can't break the blocks inside it
 * ({@link VillageProtection#mayMachineChange}): Create breaks with no player, so the break event never sees it. It turns
 * on as before and leaves the block standing, as it does a block it can't break.
 */
@Mixin(BlockBreakingKineticBlockEntity.class)
public abstract class BlockBreakerMixin {
	@Shadow(remap = false)
	protected BlockPos breakingPos;

	@WrapOperation(method = "tick", remap = false, at = @At(value = "INVOKE", remap = true,
		target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingKineticBlockEntity;canBreak(Lnet/minecraft/world/level/block/state/BlockState;F)Z"))
	private boolean aliveworkplace$keepVillagesWhole(BlockBreakingKineticBlockEntity self, BlockState state, float hardness, Operation<Boolean> original) {
		BlockEntity be = (BlockEntity) (Object) this;
		return original.call(self, state, hardness)
			&& (breakingPos == null || be.getLevel() == null || VillageProtection.mayMachineChange(be.getLevel(), be.getBlockPos(), breakingPos));
	}
}
