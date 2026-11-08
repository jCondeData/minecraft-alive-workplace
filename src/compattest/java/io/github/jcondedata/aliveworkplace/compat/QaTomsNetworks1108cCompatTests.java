package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-08) for ROADMAP 37.10, with Tom's Simple Storage from the pack: an inventory connector by the Storehouse is
 * one store for every inventory it joins, and each item is counted exactly once. The oracle everywhere is what a player
 * would count: the items the Storehouse's store list holds ({@link SupplyContainers#find} and its counting) equal the
 * items really put in the chests, never more (a chest read twice) and never less (a chest dropped from the list that
 * Tom's own connector doesn't expose); Tom's own connector storage ({@code ItemStorage.SIDED}) is checked to agree.
 */
public class QaTomsNetworks1108cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** The Storehouse in the middle of the area, so every network block below is within its reach (8 blocks). */
	static final BlockPos STOREHOUSE = new BlockPos(8, 2, 8);

	/**
	 * A player breaks the connector after the Storehouse has read it as one store: the very next look lists the chest
	 * that touched it and the chest on its cable as stores of their own, the connector is gone from the list, and every
	 * item is still counted once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_broken_head"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_broken_head")
	public void theConnectorBrokenByAPlayerFreesItsChests(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(6, 2, 12);
		BlockPos touching = new BlockPos(7, 2, 12);
		BlockPos cable = new BlockPos(6, 2, 13);
		BlockPos cableConnector = new BlockPos(6, 2, 14);
		BlockPos faced = new BlockPos(6, 2, 15);
		BlockPos lone = new BlockPos(12, 2, 8);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(touching, Blocks.CHEST);
		helper.setBlock(cable, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(cableConnector, cableConnector(Direction.SOUTH));
		helper.setBlock(faced, Blocks.CHEST);
		helper.setBlock(lone, Blocks.CHEST);
		joinUp(helper, connector, cable, cableConnector);
		put(helper, touching, new ItemStack(Items.COBBLESTONE, 10));
		put(helper, faced, new ItemStack(Items.OAK_PLANKS, 20));
		put(helper, lone, new ItemStack(Items.COBBLESTONE, 3));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		BlockPos at = helper.absolutePos(connector);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> before = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(before.size() == 2 && before.contains(at) && before.contains(helper.absolutePos(lone)),
				"with the connector, the store: " + rel(helper, before) + ", wanted the connector and the lone chest");
			assertCount(helper, before, Items.COBBLESTONE, 13);
			assertCount(helper, before, Items.OAK_PLANKS, 20);

			level.destroyBlock(at, true); // as a player's breaking it: the block goes, its item drops
			helper.assertTrue(level.getBlockState(at).isAir(), "the connector is still there: " + level.getBlockState(at));
			List<BlockPos> after = SupplyContainers.find(level, storehouse, null);
			helper.assertFalse(after.contains(at), "the broken connector is still a store: " + rel(helper, after));
			List<BlockPos> wanted = List.of(helper.absolutePos(touching), helper.absolutePos(faced), helper.absolutePos(lone));
			helper.assertTrue(after.size() == 3 && after.containsAll(wanted), "with the connector broken, the store: " + rel(helper, after)
				+ ", wanted the two formerly joined chests and the lone chest");
			assertCount(helper, after, Items.COBBLESTONE, 13);
			assertCount(helper, after, Items.OAK_PLANKS, 20);
			helper.assertTrue(StorageNetworks.members(level, at).isEmpty(), "a broken connector still joins " + StorageNetworks.members(level, at));
			// Nothing left for later batches to find.
			level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3)).forEach(Entity::discard);
			helper.succeed();
		});
	}

	/**
	 * The network has room for only 4 more cobblestone: a delivery of 100 into the whole store list fills the network
	 * and puts the other 96 in the lone chest by the Storehouse. Nothing is lost and nothing is left over: what went in
	 * is what the store holds.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_full"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_full")
	public void aFullNetworkSendsTheRestToALoneChest(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(8, 2, 10); // nearer the Storehouse than the lone chest: filled first
		BlockPos chest = new BlockPos(8, 2, 11);
		BlockPos lone = new BlockPos(12, 2, 8);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(lone, Blocks.CHEST);
		joinUp(helper, connector);
		Container box = (Container) level.getBlockEntity(helper.absolutePos(chest));
		helper.assertTrue(box != null && box.getContainerSize() == 27, "the network's chest isn't a 27-slot chest: " + box);
		for (int slot = 0; slot < 26; slot++) {
			box.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		box.setItem(26, new ItemStack(Items.COBBLESTONE, 60)); // 4 free
		long before = 26 * 64 + 60;
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector), helper.absolutePos(lone))),
				"the store: " + rel(helper, store) + ", wanted the connector then the lone chest");
			assertCount(helper, store, Items.COBBLESTONE, before);
			ItemStack rest1 = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 64));
			ItemStack rest2 = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 36));
			helper.assertTrue(rest1.isEmpty() && rest2.isEmpty(), "a delivery of 100 left " + (rest1.getCount() + rest2.getCount())
				+ " over, with room for it in the lone chest");
			long inNetwork = box.countItem(Items.COBBLESTONE);
			long inLone = SupplyContainers.count(level, List.of(helper.absolutePos(lone)), Items.COBBLESTONE);
			helper.assertTrue(inNetwork == 27 * 64, "the network's chest holds " + inNetwork + " cobblestone, wanted it full (1728)");
			helper.assertTrue(inLone == 96, "the lone chest holds " + inLone + " cobblestone, wanted the 96 the network had no room for");
			assertCount(helper, SupplyContainers.find(level, storehouse, null), Items.COBBLESTONE, before + 100);
			helper.assertTrue(toms(helper, connector, Items.COBBLESTONE) == 27 * 64,
				"Tom's connector shows " + toms(helper, connector, Items.COBBLESTONE) + " cobblestone, wanted 1728");
			helper.succeed();
		});
	}

	/**
	 * A closed ring of cables (the connector one of its corners) with two cable connectors, each facing a chest: the walk
	 * over the ring ends, the connector joins each chest once, and each chest's items count once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_ring"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_ring")
	public void aRingOfCablesJoinsEachChestOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(5, 2, 12);
		List<BlockPos> ring = List.of(new BlockPos(6, 2, 12), new BlockPos(7, 2, 12), new BlockPos(7, 2, 13), new BlockPos(7, 2, 14),
			new BlockPos(6, 2, 14), new BlockPos(5, 2, 14), new BlockPos(5, 2, 13));
		BlockPos cableConnectorA = new BlockPos(8, 2, 13);
		BlockPos chestA = new BlockPos(9, 2, 13);
		BlockPos cableConnectorB = new BlockPos(5, 2, 15);
		BlockPos chestB = new BlockPos(5, 2, 16);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		for (BlockPos p : ring) {
			helper.setBlock(p, TomsStorageCompatTests.block("inventory_cable"));
		}
		helper.setBlock(cableConnectorA, cableConnector(Direction.EAST));
		helper.setBlock(chestA, Blocks.CHEST);
		helper.setBlock(cableConnectorB, cableConnector(Direction.SOUTH));
		helper.setBlock(chestB, Blocks.CHEST);
		joinUp(helper, connector, cableConnectorA, cableConnectorB);
		joinUp(helper, ring.toArray(new BlockPos[0]));
		put(helper, chestA, new ItemStack(Items.GLASS, 9));
		put(helper, chestB, new ItemStack(Items.OAK_PLANKS, 20));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			Set<BlockPos> members = StorageNetworks.members(level, helper.absolutePos(connector));
			helper.assertTrue(members.size() == 2 && members.contains(helper.absolutePos(chestA)) && members.contains(helper.absolutePos(chestB)),
				"the connector on the ring joins " + rel(helper, List.copyOf(members)) + ", wanted the two chests once each");
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))), "the store: " + rel(helper, store) + ", wanted the connector alone");
			assertCount(helper, store, Items.GLASS, 9);
			assertCount(helper, store, Items.OAK_PLANKS, 20);
			helper.assertTrue(toms(helper, connector, Items.GLASS) == 9 && toms(helper, connector, Items.OAK_PLANKS) == 20,
				"Tom's connector shows " + toms(helper, connector, Items.GLASS) + " glass and " + toms(helper, connector, Items.OAK_PLANKS)
					+ " planks, wanted 9 and 20");
			helper.succeed();
		});
	}

	/**
	 * The same chest reached two ways: a builder taking more than it holds through the Storehouse gets only what is
	 * really there (10), and the chest is left empty: nothing is made out of nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_two_ways_take"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_two_ways_take")
	public void aChestReachedTwoWaysGivesOnlyWhatItHolds(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(6, 2, 12);
		BlockPos chest = new BlockPos(7, 2, 12);
		BlockPos cable = new BlockPos(6, 2, 13);
		BlockPos cableConnector = new BlockPos(7, 2, 13); // facing north, at the chest the connector touches
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(cable, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(cableConnector, cableConnector(Direction.NORTH));
		joinUp(helper, connector, cable, cableConnector);
		put(helper, chest, new ItemStack(Items.COBBLESTONE, 10));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			int taken = SupplyContainers.extract(level, store, Items.COBBLESTONE, 20);
			int left = 0;
			if (level.getBlockEntity(helper.absolutePos(chest)) instanceof net.minecraft.world.Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).is(Items.COBBLESTONE)) {
						left += c.getItem(i).getCount();
					}
				}
			}
			helper.assertTrue(taken == 10 && left == 0, "asked for 20 of the chest's 10 cobblestone through the store: took " + taken + ", "
				+ left + " left in the chest");
			helper.succeed();
		});
	}

	/**
	 * Inventories touching each other in a row from the connector (a barrel, a chest, a barrel), and a chest beyond a trim
	 * on the connector's other side: all four joined, each counted once, and the Storehouse's totals match what Tom's
	 * connector itself exposes.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_row_trim"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_row_trim")
	public void aRowOfChestsAndATrimAreAllJoinedOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(4, 2, 12);
		BlockPos barrel1 = new BlockPos(5, 2, 12);
		BlockPos chest = new BlockPos(6, 2, 12);
		BlockPos barrel2 = new BlockPos(7, 2, 12);
		BlockPos trim = new BlockPos(3, 2, 12);
		BlockPos beyondTrim = new BlockPos(2, 2, 12);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(barrel1, Blocks.BARREL);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(barrel2, Blocks.BARREL);
		helper.setBlock(trim, TomsStorageCompatTests.block("trim"));
		helper.setBlock(beyondTrim, Blocks.CHEST);
		joinUp(helper, connector);
		put(helper, barrel1, new ItemStack(Items.GLASS, 3));
		put(helper, chest, new ItemStack(Items.SAND, 5));
		put(helper, barrel2, new ItemStack(Items.OAK_LOG, 7));
		put(helper, beyondTrim, new ItemStack(Items.OAK_PLANKS, 11));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			Set<BlockPos> members = StorageNetworks.members(level, helper.absolutePos(connector));
			List<BlockPos> wanted = List.of(helper.absolutePos(barrel1), helper.absolutePos(chest), helper.absolutePos(barrel2), helper.absolutePos(beyondTrim));
			helper.assertTrue(members.size() == 4 && members.containsAll(wanted), "the connector joins " + rel(helper, List.copyOf(members))
				+ ", wanted the row of three and the chest beyond the trim");
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))), "the store: " + rel(helper, store) + ", wanted the connector alone");
			Item[] items = {Items.GLASS, Items.SAND, Items.OAK_LOG, Items.OAK_PLANKS};
			long[] counts = {3, 5, 7, 11};
			for (int i = 0; i < items.length; i++) {
				assertCount(helper, store, items[i], counts[i]);
				long shown = toms(helper, connector, items[i]);
				helper.assertTrue(shown == counts[i], "Tom's connector shows " + shown + " " + name(items[i]) + ", wanted " + counts[i]);
			}
			helper.succeed();
		});
	}

	/**
	 * A double chest whose one half touches the connector, its other half faced by nothing: it is one double chest's
	 * worth in the store (its items once, 54 slots), whether the walk reaches the far half or not.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_double_touch"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_double_touch")
	public void aDoubleChestTouchingTheConnectorCountsOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(6, 2, 12);
		BlockPos near = new BlockPos(7, 2, 12);
		BlockPos far = new BlockPos(7, 2, 13); // south of the near half, touching nothing of the network
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
		helper.setBlock(near, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(far, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		joinUp(helper, connector);
		// Straight into each half's own slots, so both halves hold something.
		((Container) level.getBlockEntity(helper.absolutePos(near))).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
		((Container) level.getBlockEntity(helper.absolutePos(far))).setItem(0, new ItemStack(Items.OAK_PLANKS, 20));
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(level.getBlockState(helper.absolutePos(near)).getValue(ChestBlock.TYPE) == ChestType.LEFT
				&& level.getBlockState(helper.absolutePos(far)).getValue(ChestBlock.TYPE) == ChestType.RIGHT, "the chest isn't a double chest");
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))), "the store: " + rel(helper, store) + ", wanted the connector alone");
			assertCount(helper, store, Items.COBBLESTONE, 10);
			assertCount(helper, store, Items.OAK_PLANKS, 20);
			helper.assertTrue(toms(helper, connector, Items.COBBLESTONE) == 10 && toms(helper, connector, Items.OAK_PLANKS) == 20,
				"Tom's connector shows " + toms(helper, connector, Items.COBBLESTONE) + " cobblestone and " + toms(helper, connector, Items.OAK_PLANKS)
					+ " planks, wanted 10 and 20");
			int slots = SupplyContainers.slots(level, store);
			helper.assertTrue(slots == 54, "the store has " + slots + " slots, wanted one double chest's 54");
			helper.succeed();
		});
	}

	static BlockState cableConnector(Direction facing) {
		return TomsStorageCompatTests.facing(TomsStorageCompatTests.block("inventory_cable_connector").defaultBlockState(), facing);
	}

	/** Tom's cable blocks join up as when a player places them (their connection states from their neighbours). */
	static void joinUp(GameTestHelper helper, BlockPos... rels) {
		ServerLevel level = helper.getLevel();
		for (BlockPos rel : rels) {
			BlockPos at = helper.absolutePos(rel);
			level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), Block.UPDATE_ALL);
		}
	}

	static void put(GameTestHelper helper, BlockPos rel, ItemStack stack) {
		ItemStack rest = SupplyContainers.insert(helper.getLevel(), List.of(helper.absolutePos(rel)), stack.copy());
		helper.assertTrue(rest.isEmpty(), "put " + stack + " in " + helper.getLevel().getBlockState(helper.absolutePos(rel)) + ": " + rest.getCount() + " left over");
	}

	static void assertCount(GameTestHelper helper, List<BlockPos> store, Item item, long wanted) {
		long n = SupplyContainers.count(helper.getLevel(), store, item);
		helper.assertTrue(n == wanted, n + " " + name(item) + " counted in the store " + rel(helper, store) + ", wanted " + wanted);
	}

	/** What Tom's connector itself exposes of {@code item} (its Fabric item storage). */
	static long toms(GameTestHelper helper, BlockPos connector, Item item) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(connector), null);
		helper.assertTrue(storage != null, "Tom's connector has no item storage");
		long n = 0;
		for (StorageView<ItemVariant> view : storage) {
			if (!view.isResourceBlank() && view.getResource().isOf(item)) {
				n += view.getAmount();
			}
		}
		return n;
	}

	private static String name(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	static List<BlockPos> rel(GameTestHelper helper, List<BlockPos> list) {
		return list.stream().map(helper::relativePos).toList();
	}
}
