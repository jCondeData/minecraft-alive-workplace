package io.github.jcondedata.aliveworkplace.grave;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A villager's grave: a mound with a headstone at its head. Right-click to read it; an Undertaker can bring them back. */
public class GraveBlock extends BaseEntityBlock {
	public static final MapCodec<GraveBlock> CODEC = simpleCodec(GraveBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	private static final VoxelShape MOUND = Block.box(1, 0, 1, 15, 2, 15);
	private static final VoxelShape NORTH = Shapes.or(MOUND, Block.box(3, 2, 11, 13, 14, 14));
	private static final VoxelShape SOUTH = Shapes.or(MOUND, Block.box(3, 2, 2, 13, 14, 5));
	private static final VoxelShape EAST = Shapes.or(MOUND, Block.box(2, 2, 3, 5, 14, 13));
	private static final VoxelShape WEST = Shapes.or(MOUND, Block.box(11, 2, 3, 14, 14, 13));

	public GraveBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
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
		return switch (state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GraveBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
			player.displayClientMessage(Graves.epitaph(grave), false);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
