package io.github.jcondedata.aliveworkplace.wonder;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Interact;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Foundation Stone (35.4): a carved stone with a brass plaque, laid just outside a Wonder's front corner when its
 * plan is handed to a builder. It is the bench of the Wonder's build sites, so the chests within 8 blocks of it are the
 * Wonder's stockpile. Right-click it: the stage, how far along it is and what the chests are short of
 * ({@link WonderStages#statusText}). Sneak-breaking it (the owner or an operator) ends the Wonder; anyone else's
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

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			Chat.system(player, WonderStages.statusText(server, pos));
		}
		return Interact.success(level.isClientSide());
	}
}
