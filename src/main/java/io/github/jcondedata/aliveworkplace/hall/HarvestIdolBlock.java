package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Harvest Idol (ROADMAP 30.14): a straw figure crowned with wheat on a wooden post. In harvest season the crops
 * within {@link HarvestIdols#RADIUS} blocks grow a quarter faster (see {@link HarvestIdols}). Its block entity only tells
 * {@link HarvestIdols} where it is while its chunk is loaded; it saves nothing.
 */
public class HarvestIdolBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<HarvestIdolBlock> CODEC = simpleCodec(HarvestIdolBlock::new);
	/** The post, the straw body with its arms, the head and the crown; the shape stops at a fence's height. */
	private static final VoxelShape SHAPE_NS = Shapes.or(
		Block.box(7, 0, 7, 9, 11, 9),      // the post
		Block.box(4, 11, 6, 12, 20, 10),   // the body
		Block.box(0, 16, 7, 16, 18, 9),    // the arms
		Block.box(4, 20, 4, 12, 24, 12)    // the head and its crown
	);
	private static final VoxelShape SHAPE_EW = Shapes.or(
		Block.box(7, 0, 7, 9, 11, 9),
		Block.box(6, 11, 4, 10, 20, 12),
		Block.box(7, 16, 0, 9, 18, 16),
		Block.box(4, 20, 4, 12, 24, 12)
	);

	public HarvestIdolBlock(Properties properties) {
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
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HarvestIdolBlockEntity(pos, state);
	}
}
