package io.github.jcondedata.aliveworkplace.compat.create.mixin;

import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B1516 (B808's rule for the Schematicannon): a Schematicannon standing outside a protected village prints nothing into
 * it ({@link VillageProtection#mayMachineChange}, judged from the cannon to each cell of its schematic). Create asks
 * {@code shouldPlace} before it fires a block at a cell (or clears one in a replace mode); a village cell the cannon may
 * not change is skipped, as a cell it has nothing to do at, and the cells outside are printed as usual.
 */
@Mixin(SchematicannonBlockEntity.class)
public abstract class SchematicannonMixin {
	@Inject(method = "shouldPlace", remap = false, at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$skipVillageCells(BlockPos pos, BlockState state, BlockEntity blockEntity, BlockState toReplace,
		BlockState toReplaceOther, boolean isNormalCube, CallbackInfoReturnable<Boolean> cir) {
		BlockEntity self = (BlockEntity) (Object) this;
		if (VillageProtection.guarding() && self.getLevel() != null && !VillageProtection.mayMachineChange(self.getLevel(), self.getBlockPos(), pos)) {
			cir.setReturnValue(false);
		}
	}
}
