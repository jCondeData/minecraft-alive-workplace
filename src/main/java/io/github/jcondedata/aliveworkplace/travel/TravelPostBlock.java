package io.github.jcondedata.aliveworkplace.travel;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A stop on the travel network and the Ferryman's workstation. Name it in an anvil before placing it.
 * Right-click it to add it to the posts you know; ferrymen sell tickets to posts you know.
 */
public class TravelPostBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<TravelPostBlock> CODEC = simpleCodec(TravelPostBlock::new);

	public TravelPostBlock(Properties properties) {
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
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel) {
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			String name = custom != null ? custom.getString() : Component.translatable("message.aliveworkplace.travel.default_name",
				pos.getX(), pos.getZ()).getString();
			TravelNetwork network = TravelNetwork.get(serverLevel.getServer());
			TravelNetwork.Post post = network.add(GlobalPos.of(level.dimension(), pos), name);
			if (placer instanceof ServerPlayer player) {
				network.visit(player.getUUID(), post);
				player.displayClientMessage(Component.translatable("message.aliveworkplace.travel.placed", name), false);
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			TravelNetwork network = TravelNetwork.get(serverLevel.getServer());
			// A post nobody placed (it came with a village) joins the network under a village name.
			TravelNetwork.Post post = network.atOrAdd(serverLevel, pos);
			if (post == null) {
				return InteractionResult.CONSUME;
			}
			boolean found = network.visit(serverPlayer.getUUID(), post);
			int others = network.known(serverPlayer.getUUID(), post).size();
			player.displayClientMessage(Component.translatable(found ? "message.aliveworkplace.travel.found" : "message.aliveworkplace.travel.known",
				post.name(), others).withStyle(found ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level.getServer() != null) {
			TravelNetwork.get(level.getServer()).remove(GlobalPos.of(level.dimension(), pos));
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
