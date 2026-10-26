package io.github.jcondedata.aliveworkplace.shop;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Shopkeeper's workstation. Whoever places it owns the shop: they set the price list in it, stock
 * the chests nearby, and collect the takings from those chests.
 */
public class ShopCounterBlock extends BaseEntityBlock {
	public static final MapCodec<ShopCounterBlock> CODEC = simpleCodec(ShopCounterBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public ShopCounterBlock(Properties properties) {
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

	/** Turns and mirrors with a placed blueprint like vanilla's facing blocks (B448), so it faces the way its building does. */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ShopCounterBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof ShopCounterBlockEntity counter) {
			counter.setOwner(player.getUUID(), player.getGameProfile().getName());
			Chat.chat(player, Component.translatable("message.aliveworkplace.shop.placed"));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return click(level, pos, player);
	}

	/**
	 * B336: vanilla skips a block's own use when a sneaking player holds anything, so the owner's sneak-click for the
	 * sales log only worked with both hands empty. This use-block hook (registered in AliveWorkplace.init) answers a
	 * sneak-click on a counter with something in either hand as the counter does with empty hands: the sales log for
	 * the owner, the "not yours" line for anyone else. Empty hands and plain clicks are left to the counter itself.
	 */
	public static InteractionResult beforeBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isSecondaryUseActive()
			|| (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof ShopCounterBlock)) {
			return InteractionResult.PASS;
		}
		InteractionResult result = click(level, hit.getBlockPos(), player);
		return level.isClientSide() ? InteractionResult.SUCCESS : result;
	}

	private static InteractionResult click(Level level, BlockPos pos, Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof ShopCounterBlockEntity counter)) {
			return Interact.success(level.isClientSide());
		}
		if (counter.owner() == null) {
			counter.setOwner(serverPlayer.getUUID(), serverPlayer.getGameProfile().getName());
		}
		boolean mine = serverPlayer.hasPermissions(2) || Friends.get(serverPlayer.server).mayDirect(counter.owner(), serverPlayer.getUUID());
		if (!mine) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.shop.not_yours", counter.ownerName())
				.withStyle(ChatFormatting.YELLOW));
			return InteractionResult.CONSUME;
		}
		if (player.isShiftKeyDown()) {
			Shops.sendLog(serverPlayer, counter);
			return InteractionResult.CONSUME;
		}
		player.openMenu(counter);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.shop.how_to").withStyle(ChatFormatting.GRAY));
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof ShopCounterBlockEntity counter) {
				Containers.dropContents(level, pos, counter);
			}
			super.onRemove(state, level, pos, newState, moved);
		}
	}
}
