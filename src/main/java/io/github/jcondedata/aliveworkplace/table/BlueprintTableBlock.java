package io.github.jcondedata.aliveworkplace.table;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where blueprints come from: browse every blueprint on the server, see what it needs, take a copy
 * (for a Blank Blueprint), and upload your own .litematic/.schem files.
 */
public class BlueprintTableBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<BlueprintTableBlock> CODEC = simpleCodec(BlueprintTableBlock::new);

	public BlueprintTableBlock(Properties properties) {
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

	/**
	 * A Blueprint (or a Wonder Plan) used on the Table unlocks its build in the village's library (26.10), then the Table
	 * opens as usual. With Create, a written Schematic imports its uploaded file (39.6), then the Table opens. Anything else
	 * opens it as without an item.
	 */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
											  BlockHitResult hit) {
		if (CreateSchematics.isSchematic(stack) && CreateSchematics.active()) {
			if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
				CreateSchematics.useOnTable(serverPlayer, stack);
				TableServer.open(serverPlayer, pos);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		if (!stack.is(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT) && !stack.is(io.github.jcondedata.aliveworkplace.registry.ModItems.WONDER_PLAN)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			BlueprintUnlocks.useOnTable(server, pos, stack, serverPlayer);
			TableServer.open(serverPlayer, pos);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			TableServer.open(serverPlayer, pos);
		}
		return Interact.success(level.isClientSide());
	}
}
