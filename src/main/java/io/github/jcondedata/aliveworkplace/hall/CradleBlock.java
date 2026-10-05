package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Cradle (ROADMAP 30.12): a wooden cradle on rockers with a wool blanket. Within {@link Cradles#BED_RANGE} blocks of
 * a bed in a village with a hall it makes the village a nursery (see {@link Cradles}); a point of interest, so the hall
 * finds it without scanning.
 */
public class CradleBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<CradleBlock> CODEC = simpleCodec(CradleBlock::new);
	/** The body over the rockers, the same both ways round (the rockers reach a block's width). */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 3, 2, 14, 10, 14), Block.box(2, 0, 2, 14, 3, 14));

	public CradleBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
