package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jetbrains.annotations.Nullable;

/**
 * The Village Hall: right-click it for the village at a glance ({@link VillageHallScreen}) — every worker, their level,
 * what they're doing and waiting for; beds, food in store, guards, requests and the buildings going up. A named Name
 * Tag used on it names the village.
 */
public class VillageHallBlock extends BaseEntityBlock {
	public static final MapCodec<VillageHallBlock> CODEC = simpleCodec(VillageHallBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public VillageHallBlock(Properties properties) {
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
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	/** A hall taken away: its village leaves the caravans' list. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
			Caravans.Data.get(server).remove(pos);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/** A new hall: the first line of the village's chronicle. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof net.minecraft.server.level.ServerLevel server && level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity
			&& entity.chronicle().isEmpty()) {
			Chronicle.record(server, pos, Chronicle.Kind.FOUNDED, placer != null
				? Component.translatable("chronicle.aliveworkplace.founded_by", VillageHalls.name(server, pos), placer.getDisplayName())
				: Component.translatable("chronicle.aliveworkplace.founded", VillageHalls.name(server, pos)), true);
		}
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VillageHallBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
			net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
		return level.isClientSide() ? null
			: createTickerHelper(type, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL_ENTITY, VillageHallBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
											  BlockHitResult hit) {
		if (stack.is(io.github.jcondedata.aliveworkplace.registry.ModItems.VILLAGE_LEDGER)) {
			if (level instanceof net.minecraft.server.level.ServerLevel server && player instanceof ServerPlayer serverPlayer) {
				VillageLedgerItem.bind(server, serverPlayer, stack, pos);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		if (!stack.is(Items.NAME_TAG) || !stack.has(DataComponents.CUSTOM_NAME)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (player instanceof ServerPlayer && level.getBlockEntity(pos) instanceof VillageHallBlockEntity hall) {
			Component name = stack.get(DataComponents.CUSTOM_NAME);
			hall.setCustomName(name);
			level.playSound(null, pos, SoundEvents.VILLAGER_CELEBRATE, SoundSource.BLOCKS, 0.8f, 1f);
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.named", name).withStyle(ChatFormatting.GREEN));
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			VillageHallScreen.open(serverPlayer, pos);
		}
		return Interact.success(level.isClientSide());
	}
}
