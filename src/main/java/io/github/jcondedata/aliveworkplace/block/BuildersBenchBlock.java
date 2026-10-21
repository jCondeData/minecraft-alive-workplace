package io.github.jcondedata.aliveworkplace.block;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

	/** B279: a Book and Quill used on a Builder's Bench writes the material list of the build under way from it. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
											  BlockHitResult hit) {
		if (!stack.is(Items.WRITABLE_BOOK) || !state.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (level instanceof ServerLevel serverLevel) {
			io.github.jcondedata.aliveworkplace.build.BuildSite site = io.github.jcondedata.aliveworkplace.build.SiteList.atBench(serverLevel, pos);
			if (site == null) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.site_list.no_build_at_bench").withStyle(net.minecraft.ChatFormatting.YELLOW));
			} else {
				io.github.jcondedata.aliveworkplace.build.SiteList.write(serverLevel, player, site,
					io.github.jcondedata.aliveworkplace.build.SiteList.builderAt(serverLevel, pos), hand);
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
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
			Chat.chat(player, io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.bench.info", chests, chests, SupplyContainers.RADIUS));
		}
		return Interact.success(level.isClientSide());
	}
}
