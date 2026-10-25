package io.github.jcondedata.aliveworkplace.table;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where blueprints come from: browse every blueprint on the server, see what it needs, take a copy
 * (for a Blank Blueprint), and upload your own .litematic/.schem files. Bought blueprints can be stored in it and taken
 * back out (36.13, {@link BlueprintTableBlockEntity}); breaking the table drops them.
 */
public class BlueprintTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
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
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BlueprintTableBlockEntity(pos, state);
	}

	/** Broken (or replaced by another block): the blueprints stored in it drop where it stood (36.13). */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BlueprintTableBlockEntity table) {
			for (ItemStack stack : table.takeAll()) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
			}
		}
		super.onRemove(state, level, pos, newState, moved);
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
	 * opens as usual. With Create, a written Schematic imports its uploaded file (39.6), then the Table opens. A Book and
	 * Quill on a builder's Table is the list of his build (B337). Anything else opens it as without an item.
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
		// B337: a builder's workstation is this Table, so a Book and Quill on it gets the list of his build as on the
		// Builder's Bench (B279). A Table no builder works at opens as usual.
		if (stack.is(net.minecraft.world.item.Items.WRITABLE_BOOK) && level instanceof ServerLevel server
			&& io.github.jcondedata.aliveworkplace.build.SiteList.isBuildersBench(server, pos)) {
			io.github.jcondedata.aliveworkplace.build.SiteList.answerAtBench(server, player, pos, hand);
			return ItemInteractionResult.SUCCESS;
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
		// B394: vanilla asks the main hand first and, empty, its turn opens the Table, so an off-hand Book and Quill never
		// got the list (B337). As the hall's off-hand items (B357) and the Scan Tool (B374), the off hand's item is used
		// on the main hand's empty turn; a Table no builder works at still opens as usual. B419: the same for a found
		// Blueprint, a Wonder Plan or a Schematic in the off hand (anything else passes and the Table opens).
		if (player.getMainHandItem().isEmpty() && !player.getOffhandItem().isEmpty()) {
			ItemInteractionResult fromOffHand = useItemOn(player.getOffhandItem(), state, level, pos, player, InteractionHand.OFF_HAND, hit);
			if (fromOffHand != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
				return fromOffHand.result();
			}
		}
		if (player instanceof ServerPlayer serverPlayer) {
			// B290: sneaking with an empty hand next to a builder moves them to this table (none near: it opens as usual)
			if (!serverPlayer.isSecondaryUseActive() || !io.github.jcondedata.aliveworkplace.build.BenchMoves.sneakUse(serverPlayer, pos)) {
				TableServer.open(serverPlayer, pos);
			}
		}
		return Interact.success(level.isClientSide());
	}
}
