package io.github.jcondedata.aliveworkplace.store;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Porter's workstation. The chests within {@link SupplyContainers#RADIUS} blocks of it are the village's
 * storehouse: the porter carries what the other workers make into them, and every worker nearby can take from them.
 * Whoever places it owns it (their porter only works for them and their friends); a village's belongs to the village.
 * Right-clicking it opens the requests board ({@link StorehouseBoard}).
 */
public class StorehouseBlock extends BaseEntityBlock {
	public static final MapCodec<StorehouseBlock> CODEC = simpleCodec(StorehouseBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public StorehouseBlock(Properties properties) {
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

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StorehouseBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof StorehouseBlockEntity storehouse) {
			storehouse.setOwner(player.getUUID(), player.getGameProfile().getName());
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			StorehouseBoard.open(serverPlayer, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
