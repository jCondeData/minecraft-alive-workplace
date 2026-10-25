package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1025-0953) on the modded-storage fill rule of B371 (FabricItemStores.fill), from its text: "A store that
 * takes items without holding more (a void upgrade) still counts what it says it took." The nightly mutation run of
 * 2026-10-25 left `landed > 0` to `landed >= 0` alive: with it, a voiding store reports nothing put, so whoever is
 * putting away keeps the whole stack (or pours it into the next chest too) while the store has already destroyed it.
 *
 * <p>The void store stands in for a Sophisticated barrel with a void upgrade (or a trash can): a Fabric item storage
 * that accepts everything and shows no slots. It is registered as a lookup fallback, answering only at the positions
 * these tests put in {@link #VOIDS}, on an enchanting table (a block entity that is no container of its own, so
 * {@code SupplyContainers.find} lists it the way it lists a modded barrel).
 */
public class QaVoidStoreGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos VOID = new BlockPos(4, 2, 2);
	private static final BlockPos CHEST = new BlockPos(7, 2, 2);
	/** Absolute positions the fallback answers at; each test removes its own when it ends. */
	private static final Set<BlockPos> VOIDS = ConcurrentHashMap.newKeySet();
	private static volatile boolean registered;

	/** Takes everything, holds nothing (a void upgrade). */
	private static final class VoidStorage implements InsertionOnlyStorage<ItemVariant> {
		long swallowed;

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			swallowed += maxAmount;
			return maxAmount;
		}
	}

	private static final VoidStorage STORE = new VoidStorage();

	private static synchronized void register() {
		if (!registered) {
			ItemStorage.SIDED.registerFallback((level, pos, state, blockEntity, side) -> VOIDS.contains(pos) ? STORE : null);
			registered = true;
		}
	}

	private static BlockPos voidStore(GameTestHelper helper) {
		register();
		helper.setBlock(VOID, Blocks.ENCHANTING_TABLE);
		BlockPos at = helper.absolutePos(VOID).immutable();
		VOIDS.add(at);
		Leftovers.after(helper, () -> VOIDS.remove(at));
		return at;
	}

	/**
	 * Ten cobblestone put into a lone void store: all ten are counted as put away (nothing comes back to the one putting
	 * them away), and the store was asked for them once, not over and over.
	 */
	//$ gametest_batch AREA '"qaVoidStoreAlone"'
	@GameTest(template = AREA, batch = "qaVoidStoreAlone")
	public void aVoidStoreCountsWhatItSwallowed(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos at = voidStore(helper);
		long before = STORE.swallowed;
		ItemStack rest = SupplyContainers.insert(helper.getLevel(), List.of(at), new ItemStack(Items.COBBLESTONE, 10));
		helper.assertTrue(rest.isEmpty(), "a void store took the stack but " + rest + " came back to be carried again");
		helper.assertTrue(STORE.swallowed - before == 10, "the void store was handed " + (STORE.swallowed - before) + " for a stack of 10");
		helper.succeed();
	}

	/**
	 * The void store nearest the bench and a chest behind it, both found the way a worker finds its stores: the stack
	 * goes into the void store and the chest gets none of it (the store said it took them all; pouring them into the
	 * chest too would make ten cobblestone out of nothing).
	 */
	//$ gametest_batch AREA '"qaVoidStoreThenChest"'
	@GameTest(template = AREA, batch = "qaVoidStoreThenChest")
	public void aVoidStoreBeforeAChestLeavesTheChestEmpty(GameTestHelper helper) {
		Leftovers.clear(helper);
		voidStore(helper);
		helper.setBlock(CHEST, Blocks.CHEST);
		List<BlockPos> stores = SupplyContainers.find(helper.getLevel(), helper.absolutePos(BENCH), null);
		helper.assertTrue(stores.size() == 2 && stores.get(0).equals(helper.absolutePos(VOID)),
			"setup: the stores near the bench, nearest first, are " + stores);
		long before = STORE.swallowed;
		ItemStack rest = SupplyContainers.insert(helper.getLevel(), stores, new ItemStack(Items.COBBLESTONE, 10));
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(rest.isEmpty(), rest + " came back from a void store and a chest");
		helper.assertTrue(STORE.swallowed - before == 10, "the void store was handed " + (STORE.swallowed - before));
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "the chest got " + chest.countItem(Items.COBBLESTONE)
			+ " cobblestone the void store had already taken");
		helper.succeed();
	}
}
