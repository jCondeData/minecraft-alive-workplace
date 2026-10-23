package io.github.jcondedata.aliveworkplace.bounty;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Bounty Board (ROADMAP 31.13): a notice board on two posts, its face showing 0 to 4 posters ({@link #POSTERS}, the
 * open bounties of its village). Right-click: the Bounties tab of the nearest hall's journal; with a Captain's
 * Insignia in hand, it's handed in.
 */
public class BountyBoardBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BountyBoardBlock> CODEC = simpleCodec(BountyBoardBlock::new);
	public static final IntegerProperty POSTERS = IntegerProperty.create("posters", 0, 4);
	private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(1, 0, 7, 3, 16, 9), Block.box(13, 0, 7, 15, 16, 9), Block.box(0, 4, 6.5, 16, 16, 8.5));
	private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(7, 0, 1, 9, 16, 3), Block.box(7, 0, 13, 9, 16, 15), Block.box(6.5, 4, 0, 8.5, 16, 16));

	public BountyBoardBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POSTERS, 0));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POSTERS);
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
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (level instanceof ServerLevel server && !old.is(this)) {
			Bounties.boardPlaced(server, pos);
		}
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
		if (level instanceof ServerLevel server && !now.is(this)) {
			Bounties.boardRemoved(server, pos);
		}
		super.onRemove(state, level, pos, now, moved);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
											  BlockHitResult hit) {
		if (!stack.is(ModItems.CAPTAINS_INSIGNIA)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			VillageHalls.nearest(server, pos).ifPresentOrElse(hall -> Bounties.claim(serverPlayer, server, hall, stack),
				() -> Chat.actionBar(serverPlayer, Component.translatable("message.aliveworkplace.bounty.no_hall").withStyle(ChatFormatting.YELLOW)));
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		// B338: vanilla tries the main hand first and, when it holds nothing the board takes, opens the board, so the
		// off hand's insignia was never asked; it is handed in here as from the main hand.
		ItemStack offHand = player.getOffhandItem();
		if (offHand.is(ModItems.CAPTAINS_INSIGNIA)) {
			return useItemOn(offHand, state, level, pos, player, InteractionHand.OFF_HAND, hit).result();
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			Bounties.boardPlaced(server, pos);
			BountyPage.openAtBoard(serverPlayer, server, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
