package io.github.jcondedata.aliveworkplace.guard;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A straw-stuffed sack on a post. Guards spar with the ones near their Guard Post between fights ({@link GuardPatrol}),
 * which earns them experience up to {@link GuardPatrol#TRAIN_UP_TO}; players can give it a whack too.
 */
public class TrainingDummyBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<TrainingDummyBlock> CODEC = simpleCodec(TrainingDummyBlock::new);
	/** Facing north or south; the arms run east-west. It stands taller than a block; the shape stops at a fence's height. */
	private static final VoxelShape SHAPE_NS = Shapes.or(
		Block.box(7, 0, 7, 9, 12, 9),     // the post
		Block.box(4, 12, 5, 12, 22, 11),  // the body
		Block.box(0, 17, 7, 16, 19, 9),   // the arms
		Block.box(5, 22, 5, 11, 24, 11)   // the head
	);
	private static final VoxelShape SHAPE_EW = Shapes.or(
		Block.box(7, 0, 7, 9, 12, 9),
		Block.box(5, 12, 4, 11, 22, 12),
		Block.box(7, 17, 0, 9, 19, 16),
		Block.box(5, 22, 5, 11, 24, 11)
	);

	public TrainingDummyBlock(Properties properties) {
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

	/** A player's hit: the same thud and puff of straw a guard's gets. */
	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel server) {
			hit(server, pos);
		}
	}

	/** The thud and puff of straw of a hit. */
	public static void hit(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 1f, 0.8f + level.random.nextFloat() * 0.3f);
		level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.6f, 1f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()),
			pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
	}
}
