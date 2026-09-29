package io.github.jcondedata.aliveworkplace.table;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where blueprints come from: browse every blueprint on the server, see what it needs, take a copy
 * (for a Blank Blueprint), and upload your own .litematic/.schem files.
 */
public class BlueprintTableBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BlueprintTableBlock> CODEC = simpleCodec(BlueprintTableBlock::new);

	public BlueprintTableBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			TableServer.open(serverPlayer, pos);
		}
		return Interact.success(level.isClientSide());
	}
}
