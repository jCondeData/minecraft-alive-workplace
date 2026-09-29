package io.github.jcondedata.aliveworkplace.block;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * The Builder's workstation. Chests (or any storage block) within {@link SupplyContainers#RADIUS}
 * blocks of the bench are the builder's supply stash.
 */
public class BuildersBenchBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BuildersBenchBlock> CODEC = simpleCodec(BuildersBenchBlock::new);

	public BuildersBenchBlock(Properties properties) {
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
		if (level instanceof ServerLevel && state.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.POSTAL_DESK)
			&& player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
			&& io.github.jcondedata.aliveworkplace.mail.Mail.collectAtDesk(serverPlayer) > 0) {
			return InteractionResult.SUCCESS; // the post office's lockers: parcels waiting for them
		}
		if (level instanceof ServerLevel serverLevel) {
			int chests = SupplyContainers.find(serverLevel, pos, null).size();
			Chat.chat(player, Component.translatable("message.aliveworkplace.bench.info", chests, SupplyContainers.RADIUS));
		}
		return Interact.success(level.isClientSide());
	}
}
