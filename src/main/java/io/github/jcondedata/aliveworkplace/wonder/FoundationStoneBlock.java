package io.github.jcondedata.aliveworkplace.wonder;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Foundation Stone (35.4): a carved stone with a brass plaque, laid just outside a Wonder's front corner when its
 * plan is handed to a builder. It is the bench of the Wonder's build sites, so the chests within 8 blocks of it are the
 * Wonder's stockpile. Right-click it: the stage, how far along it is, its goods and what the chests are short of
 * ({@link WonderStages#statusText}); right-click it holding one of the stage's goods to hand it in (35.7). Sneak-breaking it (the owner or an operator) ends the Wonder; anyone else's
 * breaking is refused ({@link WonderStages#init}). Builders never clear it, nor pistons push it.
 */
public class FoundationStoneBlock extends Block {
	public static final MapCodec<FoundationStoneBlock> CODEC = simpleCodec(FoundationStoneBlock::new);

	public FoundationStoneBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	/**
	 * Right-clicked holding one of the waiting stage's goods (35.7): it's handed in ({@link WonderGoods#handInAtStone});
	 * anything else shows the stone's status, as with an empty hand.
	 */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
											  BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND
			&& WonderGoods.handInAtStone(serverPlayer, pos, stack)) {
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			Chat.system(player, WonderStages.statusText(server, pos));
		}
		return Interact.success(level.isClientSide());
	}
}
