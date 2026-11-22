package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA of B1025's fix (lane-e-1121-1013), the sided cases: in a Tom's Simple Storage network a furnace read through faces
 * that show different slots keeps every reader (each slot once), one read through two faces that show the same slots
 * counts once, a hopper and a shulker box reached two ways count once, two real furnaces both count, and with
 * {@code moddedStorageNetworks} off the network is passed over and Tom's builds its networks as it always did.
 *
 * <p>Vanilla furnace faces: the top shows the input slot, every side the fuel slot, the bottom the result and fuel slots.
 * The furnaces hold dirt in the input slot (no recipe, so nothing smelts or burns during a test), coal as fuel and iron
 * ingots as the result.
 */
public class QaTomsMergeSided1121eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STOREHOUSE = QaTomsNetworks1108cCompatTests.STOREHOUSE;
	private static final BlockPos CONNECTOR = new BlockPos(6, 2, 12);
	private static final BlockPos CABLE = new BlockPos(6, 2, 13);

	/** The Storehouse, Tom's connector and a cable on its south side. */
	private static void network(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CONNECTOR, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(CABLE, TomsStorageCompatTests.block("inventory_cable"));
	}

	private static void cable(GameTestHelper helper, BlockPos... rels) {
		for (BlockPos rel : rels) {
			helper.setBlock(rel, TomsStorageCompatTests.block("inventory_cable"));
		}
	}

	private static List<BlockPos> store(GameTestHelper helper) {
		List<BlockPos> store = SupplyContainers.find(helper.getLevel(), helper.absolutePos(STOREHOUSE), null);
		helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))),
			"the store: " + QaTomsNetworks1108cCompatTests.rel(helper, store) + ", wanted the connector alone");
		return store;
	}

	/** The Storehouse's count and Tom's connector's own item storage both show {@code wanted} of {@code item}. */
	private static void assertShown(GameTestHelper helper, List<BlockPos> store, Item item, long wanted, String what) {
		long counted = SupplyContainers.count(helper.getLevel(), store, item);
		long shown = QaTomsNetworks1108cCompatTests.toms(helper, CONNECTOR, item);
		helper.assertTrue(counted == wanted && shown == wanted, "the Storehouse counts " + counted + " and Tom's connector shows " + shown
			+ " " + item + ", wanted " + wanted + " (" + what + ")");
	}

	private static void assertFree(GameTestHelper helper, List<BlockPos> store, int wanted, String what) {
		int free = SupplyContainers.freeSlots(helper.getLevel(), store);
		helper.assertTrue(free == wanted, "the store has " + free + " free slots, wanted " + wanted + " (" + what + ")");
	}

	/** A furnace with 5 dirt in its input, 3 coal as fuel and 2 iron ingots as its result. */
	private static void furnace(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, Blocks.FURNACE);
		Container c = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		c.setItem(0, new ItemStack(Items.DIRT, 5));
		c.setItem(1, new ItemStack(Items.COAL, 3));
		c.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
	}

	private static void fill(GameTestHelper helper, BlockPos rel, ItemStack stack) {
		((Container) helper.getLevel().getBlockEntity(helper.absolutePos(rel))).setItem(0, stack);
	}

	/** Case 1: a furnace touching the connector on its west side (fuel) and faced from above by a cable connector (input). */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_side_and_top"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_side_and_top")
	public void aFurnaceReadAtItsSideAndTopShowsEachSlotOnce(GameTestHelper helper) {
		network(helper);
		BlockPos furnace = new BlockPos(7, 2, 12); // east of the connector: the connector reads its west side
		BlockPos up = new BlockPos(6, 3, 12); // a cable on top of the connector
		BlockPos top = new BlockPos(7, 3, 12); // facing down, at the furnace's top
		furnace(helper, furnace);
		cable(helper, up);
		helper.setBlock(top, QaTomsNetworks1108cCompatTests.cableConnector(Direction.DOWN));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, up, top);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.DIRT, 5, "the input slot, through the top");
			assertShown(helper, store, Items.COAL, 3, "the fuel slot, through the side");
			assertShown(helper, store, Items.IRON_INGOT, 0, "the result slot shows at the bottom only, which nothing reads");
			helper.succeed();
		});
	}

	/** Case 1b: a furnace faced by two cable connectors, one under it (result and fuel) and one over it (input). */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_top_and_bottom"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_top_and_bottom")
	public void aFurnaceReadAtItsTopAndBottomShowsEverySlotOnce(GameTestHelper helper) {
		network(helper);
		BlockPos furnace = new BlockPos(8, 3, 13);
		BlockPos bottom = new BlockPos(8, 2, 13); // facing up, at the furnace's bottom
		BlockPos top = new BlockPos(8, 4, 13); // facing down, at the furnace's top
		BlockPos c1 = new BlockPos(7, 2, 13);
		BlockPos c2 = new BlockPos(7, 3, 13);
		BlockPos c3 = new BlockPos(7, 4, 13);
		furnace(helper, furnace);
		cable(helper, c1, c2, c3);
		helper.setBlock(bottom, QaTomsNetworks1108cCompatTests.cableConnector(Direction.UP));
		helper.setBlock(top, QaTomsNetworks1108cCompatTests.cableConnector(Direction.DOWN));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, c1, c2, c3, bottom, top);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.DIRT, 5, "the input slot, through the top");
			assertShown(helper, store, Items.COAL, 3, "the fuel slot, through the bottom");
			assertShown(helper, store, Items.IRON_INGOT, 2, "the result slot, through the bottom");
			helper.succeed();
		});
	}

	/** Case 2: a furnace touching the connector on its west side and faced by a cable connector on its south side: both the fuel slot. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_two_sides"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_two_sides")
	public void aFurnaceReadAtTwoSidesCountsItsFuelOnce(GameTestHelper helper) {
		network(helper);
		BlockPos furnace = new BlockPos(7, 2, 12);
		BlockPos south = new BlockPos(7, 2, 13); // facing north, at the furnace's south side, next to the cable
		furnace(helper, furnace);
		helper.setBlock(south, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, south);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.COAL, 3, "the fuel slot, shown at both sides");
			assertShown(helper, store, Items.DIRT, 0, "the input slot shows at the top only");
			assertShown(helper, store, Items.IRON_INGOT, 0, "the result slot shows at the bottom only");
			helper.succeed();
		});
	}

	/** Case 2b: a furnace faced by two cable connectors at its east and west sides, not touching the connector. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_east_west"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_east_west")
	public void aFurnaceFacedAtEastAndWestCountsItsFuelOnce(GameTestHelper helper) {
		network(helper);
		BlockPos c1 = new BlockPos(6, 2, 14);
		BlockPos c2 = new BlockPos(7, 2, 14);
		BlockPos c3 = new BlockPos(8, 2, 14);
		BlockPos c4 = new BlockPos(9, 2, 14);
		BlockPos west = new BlockPos(7, 2, 15); // facing east
		BlockPos furnace = new BlockPos(8, 2, 15);
		BlockPos east = new BlockPos(9, 2, 15); // facing west
		furnace(helper, furnace);
		cable(helper, c1, c2, c3, c4);
		helper.setBlock(west, QaTomsNetworks1108cCompatTests.cableConnector(Direction.EAST));
		helper.setBlock(east, QaTomsNetworks1108cCompatTests.cableConnector(Direction.WEST));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, c1, c2, c3, c4, west, east);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.COAL, 3, "the fuel slot, shown at the east and west sides");
			assertShown(helper, store, Items.DIRT, 0, "the input slot shows at the top only");
			helper.succeed();
		});
	}

	/** Case 3a: a hopper (the same slots at every face) touching the connector and faced by a cable connector. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_hopper"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_hopper")
	public void aHopperReachedTwoWaysCountsOnce(GameTestHelper helper) {
		network(helper);
		BlockPos hopper = new BlockPos(7, 2, 12); // pointing down into the floor: nowhere to push
		BlockPos south = new BlockPos(7, 2, 13);
		helper.setBlock(hopper, Blocks.HOPPER);
		helper.setBlock(south, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, south);
		fill(helper, hopper, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.COBBLESTONE, 10, "one hopper");
			assertFree(helper, store, 4, "one hopper's 5 less the cobblestone's slot");
			helper.succeed();
		});
	}

	/** Case 3b: a shulker box (sided, but every face shows all 27 slots) touching the connector and faced by a cable connector. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_shulker"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_shulker")
	public void aShulkerBoxReachedTwoWaysCountsOnce(GameTestHelper helper) {
		network(helper);
		BlockPos box = new BlockPos(7, 2, 12);
		BlockPos south = new BlockPos(7, 2, 13);
		helper.setBlock(box, Blocks.SHULKER_BOX);
		helper.setBlock(south, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, south);
		fill(helper, box, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.COBBLESTONE, 10, "one shulker box");
			assertFree(helper, store, 26, "one shulker box's 27 less the cobblestone's slot");
			helper.succeed();
		});
	}

	/** Case 4: two furnaces with the same contents, one touching the connector, one faced by a cable connector: both count. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_two_furnaces"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_two_furnaces")
	public void twoRealFurnacesBothCount(GameTestHelper helper) {
		network(helper);
		BlockPos first = new BlockPos(7, 2, 12); // the connector reads its west side
		BlockPos west = new BlockPos(7, 2, 13); // facing east, next to the cable
		BlockPos second = new BlockPos(8, 2, 13); // read at its west side
		furnace(helper, first);
		furnace(helper, second);
		helper.setBlock(west, QaTomsNetworks1108cCompatTests.cableConnector(Direction.EAST));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, west);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.COAL, 6, "two furnaces' 3 fuel each, both read at a side");
			helper.succeed();
		});
	}

	/** Case 4b: two furnaces side by side, the second reached through the first (Tom's touching scan): both count. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_furnace_row"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_furnace_row")
	public void twoFurnacesSideBySideBothCount(GameTestHelper helper) {
		network(helper);
		BlockPos first = new BlockPos(7, 2, 12);
		BlockPos second = new BlockPos(8, 2, 12);
		furnace(helper, first);
		furnace(helper, second);
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertShown(helper, store, Items.COAL, 6, "two furnaces side by side, 3 fuel each");
			helper.succeed();
		});
	}

	/**
	 * Case 5: config moddedStorageNetworks off. StorageNetworks promises the network blocks are passed over (each linked chest
	 * is its own store), and TomsMerge promises nothing changes: Tom's connector shows the chest reached two ways twice, as
	 * Tom's own code builds it (B1025's original behaviour).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_switch_off"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_switch_off")
	public void withNetworksOffTheMergeIsOff(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
		network(helper);
		BlockPos chest = new BlockPos(7, 2, 12);
		BlockPos south = new BlockPos(7, 2, 13);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(south, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, south);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(helper.getLevel(), helper.absolutePos(STOREHOUSE), null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(chest))),
				"the store with networks off: " + QaTomsNetworks1108cCompatTests.rel(helper, store) + ", wanted the chest alone");
			long counted = SupplyContainers.count(helper.getLevel(), store, Items.COBBLESTONE);
			helper.assertTrue(counted == 10, "the Storehouse counts " + counted + " cobblestone with networks off, wanted the chest's 10");
			long shown = QaTomsNetworks1108cCompatTests.toms(helper, CONNECTOR, Items.COBBLESTONE);
			helper.assertTrue(shown == 20, "Tom's connector shows " + shown
				+ " cobblestone with networks off, wanted 20 (the merge off: Tom's reads the chest at both readers, as it always did)");
			helper.succeed();
		});
	}
}
