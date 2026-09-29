package io.github.jcondedata.aliveworkplace.platform;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The items in storage blocks, read and moved the loader's way, so modded storage (Sophisticated Storage chests and
 * barrels, Tom's filing cabinets...) works like a chest. On Fabric it's the transfer API. Positions without storage
 * are skipped; lists of positions are gone through in order. See {@code build/SupplyContainers} for how the jobs use it.
 */
public interface ItemStores {
	/** Whether the block at {@code pos} stores items. */
	boolean isStore(Level level, BlockPos pos);

	/** The same, for a block and block entity the caller already has. */
	boolean isStore(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);

	/** Empty slots across the stores. */
	int freeSlots(Level level, List<BlockPos> stores);

	/** How many of {@code item} (any components) could be taken out. */
	long count(Level level, List<BlockPos> stores, Item item);

	/** How many of each plain item (no extra components) the stores hold together. */
	Map<Item, Long> contents(Level level, List<BlockPos> stores);

	/** The first store holding at least one {@code item}, or null. */
	@Nullable
	BlockPos firstWith(Level level, List<BlockPos> stores, Item item);

	/** Takes up to {@code max} of {@code item}, first stores first; returns how many were taken. */
	int extract(Level level, List<BlockPos> stores, Item item, int max);

	/** The first store holding a stack that matches {@code test}, or null. */
	@Nullable
	BlockPos firstMatching(Level level, List<BlockPos> stores, Predicate<ItemStack> test);

	/** Takes one item matching {@code test}; empty if there's none. */
	ItemStack takeOne(Level level, List<BlockPos> stores, Predicate<ItemStack> test);

	/** How many items exactly like {@code template} (same components) the stores hold. */
	long countMatching(Level level, List<BlockPos> stores, ItemStack template);

	/** Takes up to {@code max} items exactly like {@code template}; returns how many were taken. */
	int extractMatching(Level level, List<BlockPos> stores, ItemStack template, int max);

	/** Takes up to {@code maxStacks} stacks of items matching {@code test} out of the store at {@code pos}. */
	List<ItemStack> takeMatching(Level level, BlockPos pos, Predicate<ItemStack> test, int maxStacks);

	/** Copies of the stacks in the store at {@code pos} that match {@code test} (nothing is taken). */
	List<ItemStack> peekMatching(Level level, BlockPos pos, Predicate<ItemStack> test);

	boolean hasMatching(Level level, BlockPos pos, Predicate<ItemStack> test);

	/** Puts a stack into the stores; returns what didn't fit. */
	ItemStack insert(Level level, List<BlockPos> stores, ItemStack stack);
}
