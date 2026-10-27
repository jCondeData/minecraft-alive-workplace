package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player placed a block: the ledger of what players built marks its section (27.19, {@link PlayerBuilt}), and the
 * section of a two-block block's other half (a bed's head, a door's top), which can sit across a section edge (B603).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void aliveworkplace$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() != null && context.getLevel() instanceof ServerLevel level && cir.getReturnValue().consumesAction()) {
			BlockPos pos = context.getClickedPos();
			PlayerBuilt.changed(level, pos);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BedBlock) {
				PlayerBuilt.changed(level, pos.relative(BedBlock.getConnectedDirection(state)));
			} else if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
				PlayerBuilt.changed(level, state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
			}
		}
	}
}
