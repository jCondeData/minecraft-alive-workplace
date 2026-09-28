package io.github.jcondedata.aliveworkplace.orchard;

import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonOrchard;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What an Orchard Keeper picks, and how: ripe sweet berries, glow berries, cocoa pods and — with
 * Cobblemon — apricorns and berry plants. Picking leaves the plant in place to grow again, like a player
 * harvesting it.
 */
public final class Fruit {
	private static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");

	/** Ready to pick? */
	public static boolean isRipe(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof SweetBerryBushBlock) {
			return state.getValue(SweetBerryBushBlock.AGE) >= 2;
		}
		if (block instanceof CaveVines) {
			return state.hasProperty(BlockStateProperties.BERRIES) && state.getValue(BlockStateProperties.BERRIES);
		}
		if (block instanceof CocoaBlock) {
			return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
		}
		return COBBLEMON && CobblemonOrchard.isRipe(state);
	}

	/** Picks the fruit at {@code pos} (it must be ripe): returns what came off, and sets the plant back to growing. */
	public static List<ItemStack> pick(ServerLevel level, BlockPos pos, Villager picker) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		List<ItemStack> out = new ArrayList<>();
		if (block instanceof SweetBerryBushBlock) {
			int age = state.getValue(SweetBerryBushBlock.AGE);
			out.add(new ItemStack(Items.SWEET_BERRIES, 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0)));
			level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
			level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1f, 0.8f + level.random.nextFloat() * 0.4f);
		} else if (block instanceof CaveVines) {
			out.add(new ItemStack(Items.GLOW_BERRIES));
			level.setBlock(pos, state.setValue(BlockStateProperties.BERRIES, false), Block.UPDATE_CLIENTS);
			level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1f, 0.8f + level.random.nextFloat() * 0.4f);
		} else if (block instanceof CocoaBlock) {
			out.addAll(Block.getDrops(state, level, pos, null, picker, ItemStack.EMPTY));
			level.setBlock(pos, state.setValue(CocoaBlock.AGE, 0), Block.UPDATE_CLIENTS); // the pod grows back
			level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6f, 1.2f);
		} else if (COBBLEMON && CobblemonOrchard.isRipe(state)) {
			out.addAll(CobblemonOrchard.pick(level, pos, picker));
			level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.8f, 1.1f);
		}
		return out;
	}

	private Fruit() {
	}
}
