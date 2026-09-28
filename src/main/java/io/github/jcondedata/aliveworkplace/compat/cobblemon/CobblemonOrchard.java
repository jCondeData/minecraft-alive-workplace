package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.block.ApricornBlock;
import com.cobblemon.mod.common.block.BerryBlock;
import com.cobblemon.mod.common.block.entity.BerryBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cobblemon's fruit for the Orchard Keeper: apricorns (ripe at {@link ApricornBlock#MAX_AGE}) and berry
 * plants (ripe at {@link BerryBlock#FRUIT_AGE}). Only called when Cobblemon is installed.
 */
public final class CobblemonOrchard {
	public static boolean isRipe(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof ApricornBlock) {
			return state.getValue(ApricornBlock.Companion.getAGE()) >= ApricornBlock.MAX_AGE;
		}
		if (block instanceof BerryBlock) {
			return state.getValue(BerryBlock.Companion.getAGE()) >= BerryBlock.FRUIT_AGE;
		}
		return false;
	}

	/** True if this is Cobblemon fruit at all (ripe or not). */
	public static boolean isFruit(BlockState state) {
		return state.getBlock() instanceof ApricornBlock || state.getBlock() instanceof BerryBlock;
	}

	/** Apricorn seeds and berries: what an Orchard Keeper plants in their orchard. */
	public static boolean isSeed(ItemStack stack) {
		return stack.getItem() instanceof com.cobblemon.mod.common.item.ApricornSeedItem
			|| stack.getItem() instanceof com.cobblemon.mod.common.item.berry.BerryItem;
	}

	/** Apricorn seeds grow into small trees, so they're planted further apart. */
	public static boolean growsIntoTree(ItemStack stack) {
		return stack.getItem() instanceof com.cobblemon.mod.common.item.ApricornSeedItem;
	}

	/** Picks ripe Cobblemon fruit: the apricorn comes off (it grows again), the berry plant goes back to flowering. */
	public static List<ItemStack> pick(ServerLevel level, BlockPos pos, Entity picker) {
		BlockState state = level.getBlockState(pos);
		List<ItemStack> out = new ArrayList<>();
		if (state.getBlock() instanceof ApricornBlock) {
			out.addAll(Block.getDrops(state, level, pos, null, picker, ItemStack.EMPTY));
			level.setBlock(pos, state.setValue(ApricornBlock.Companion.getAGE(), ApricornBlock.MIN_AGE), Block.UPDATE_CLIENTS);
		} else if (state.getBlock() instanceof BerryBlock && level.getBlockEntity(pos) instanceof BerryBlockEntity berries) {
			out.addAll(berries.harvest(level, state, pos));
		}
		return out;
	}

	private CobblemonOrchard() {
	}
}
