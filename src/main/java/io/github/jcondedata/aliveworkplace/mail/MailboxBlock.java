package io.github.jcondedata.aliveworkplace.mail;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A player's mailbox. Whoever places it owns it; their mail arrives here (the flag goes up) and they
 * post parcels to other players from it. Only the owner, their friends and operators can open it.
 */
public class MailboxBlock extends BaseEntityBlock {
	public static final MapCodec<MailboxBlock> CODEC = simpleCodec(MailboxBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty HAS_MAIL = BooleanProperty.create("has_mail");
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

	public MailboxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_MAIL, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HAS_MAIL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MailboxBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox) {
			claim(player, mailbox);
			Chat.chat(player, Component.translatable("message.aliveworkplace.mail.placed"));
		}
	}

	private static void claim(ServerPlayer player, MailboxBlockEntity mailbox) {
		mailbox.setOwner(player.getUUID(), player.getGameProfile().getName());
		PostOffice.get(player.server).register(player.getUUID(), GlobalPos.of(player.level().dimension(), mailbox.getBlockPos()));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)
			|| !(level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox)) {
			return Interact.success(level.isClientSide());
		}
		if (mailbox.owner() == null) {
			claim(serverPlayer, mailbox); // a mailbox from a structure or a command: first to open it owns it
		}
		// A postman's mailbox is the post office's counter: parcels waiting for a player with no mailbox are picked up here.
		boolean collected = hasPostman((ServerLevel) level, pos) && Mail.collectAtDesk(serverPlayer, true) > 0;
		if (!mayOpen(serverPlayer, mailbox)) {
			if (!collected) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.mail.not_yours", mailbox.ownerName())
					.withStyle(ChatFormatting.RED));
			}
			return InteractionResult.CONSUME;
		}
		Platform.get().openMenu(serverPlayer, mailbox, mailbox.getBlockPos()); // the screen needs to know which mailbox
		return InteractionResult.CONSUME;
	}

	/** Whether a postman works at the mailbox at {@code pos} (it's their workstation since ROADMAP 21.1a). */
	public static boolean hasPostman(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getInRange(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.MAILBOX_POI), pos, 0,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.IS_OCCUPIED).anyMatch(r -> r.getPos().equals(pos));
	}

	public static boolean mayOpen(ServerPlayer player, MailboxBlockEntity mailbox) {
		return mailbox.owner() == null || player.hasPermissions(2)
			|| Friends.get(player.server).mayDirect(mailbox.owner(), player.getUUID());
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox) {
				Containers.dropContents(level, pos, mailbox);
				if (mailbox.owner() != null && level.getServer() != null) {
					PostOffice.get(level.getServer()).unregister(mailbox.owner(), GlobalPos.of(level.dimension(), pos));
				}
			}
			super.onRemove(state, level, pos, newState, moved);
		}
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return state.getValue(HAS_MAIL) ? 15 : 0;
	}
}
