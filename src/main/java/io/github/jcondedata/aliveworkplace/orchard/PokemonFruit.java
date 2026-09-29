package io.github.jcondedata.aliveworkplace.orchard;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Fruit another mod grows (Cobblemon's apricorns and berries), for the orchard keeper. Filled in by {@code compat/cobblemon}. */
public interface PokemonFruit {
	Extension<PokemonFruit> EXTENSION = new Extension<>("apricorns and berries");

	boolean isRipe(BlockState state);

	/** Something the orchard keeper plants. */
	boolean isSeed(ItemStack stack);

	/** Planted on farmland (berries) rather than grass (apricorns). */
	boolean needsFarmland(ItemStack seed);

	/** Grows into a tree, so it's planted further apart. */
	boolean growsIntoTree(ItemStack seed);

	/** Picks the ripe fruit at {@code pos}; what came off. */
	List<ItemStack> pick(ServerLevel level, BlockPos pos, Entity picker);
}
