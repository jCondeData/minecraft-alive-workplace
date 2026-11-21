package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * QA of B1025's fix (lane-e-1121-1013): with Tom's Simple Storage, an inventory reached two ways in one network counts
 * once for the Storehouse and in Tom's connector (double chests, barrels, two cable connectors at one chest), while a
 * real second inventory still counts, taking and putting through the store work, and the count survives the cable
 * connector being broken and placed again.
 */
public class QaTomsMerge1121eCompatTests implements FabricGameTest {
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

	private static List<BlockPos> store(GameTestHelper helper) {
		List<BlockPos> store = SupplyContainers.find(helper.getLevel(), helper.absolutePos(STOREHOUSE), null);
		helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))),
			"the store: " + QaTomsNetworks1108cCompatTests.rel(helper, store) + ", wanted the connector alone");
		return store;
	}

	private static void assertCobble(GameTestHelper helper, List<BlockPos> store, long wanted) {
		long counted = SupplyContainers.count(helper.getLevel(), store, Items.COBBLESTONE);
		long shown = QaTomsNetworks1108cCompatTests.toms(helper, CONNECTOR, Items.COBBLESTONE);
		helper.assertTrue(counted == wanted && shown == wanted,
			"the Storehouse counts " + counted + " cobblestone and Tom's connector shows " + shown + ", wanted " + wanted);
	}

	private static void assertFree(GameTestHelper helper, List<BlockPos> store, int wanted, String what) {
		int free = SupplyContainers.freeSlots(helper.getLevel(), store);
		helper.assertTrue(free == wanted, "the store has " + free + " free slots, wanted " + wanted + " (" + what + ")");
	}

	/** A double chest facing {@code facing}: its left half at {@code left}, the right half at the next block clockwise. */
	private static void doubleChest(GameTestHelper helper, BlockPos left, BlockPos right, Direction facing) {
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
		helper.setBlock(left, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
	}

	private static void assertDouble(GameTestHelper helper, BlockPos left, BlockPos right) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.getBlockState(helper.absolutePos(left)).getValue(ChestBlock.TYPE) == ChestType.LEFT
			&& level.getBlockState(helper.absolutePos(right)).getValue(ChestBlock.TYPE) == ChestType.RIGHT, "the chest isn't a double chest");
	}

	private static int held(GameTestHelper helper, BlockPos rel) {
		Container c = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(Items.COBBLESTONE)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	/** Case 1: a double chest, one half touching the connector, the other half faced by the cable connector. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_double_both_halves"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_double_both_halves")
	public void aDoubleChestReachedAtBothHalvesCountsOnce(GameTestHelper helper) {
		network(helper);
		BlockPos near = new BlockPos(7, 2, 12);
		BlockPos far = new BlockPos(7, 2, 13); // south of the near half (left half facing east joins south)
		BlockPos cable2 = new BlockPos(6, 2, 14);
		BlockPos cableConnector = new BlockPos(7, 2, 14); // facing north, at the far half
		doubleChest(helper, near, far, Direction.EAST);
		helper.setBlock(cable2, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cable2, cableConnector);
		((Container) helper.getLevel().getBlockEntity(helper.absolutePos(near))).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			assertDouble(helper, near, far);
			List<BlockPos> store = store(helper);
			assertCobble(helper, store, 10);
			assertFree(helper, store, 53, "one double chest's 54 less the cobblestone's slot");
			helper.succeed();
		});
	}

	/** Case 2: a double chest, the half touching the connector also faced by the cable connector. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_double_same_half"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_double_same_half")
	public void aDoubleChestReachedTwiceAtOneHalfCountsOnce(GameTestHelper helper) {
		network(helper);
		BlockPos near = new BlockPos(7, 2, 12);
		BlockPos far = new BlockPos(8, 2, 12); // east of the near half (left half facing north joins east)
		BlockPos cableConnector = new BlockPos(7, 2, 13); // facing north, at the near half
		doubleChest(helper, near, far, Direction.NORTH);
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cableConnector);
		((Container) helper.getLevel().getBlockEntity(helper.absolutePos(far))).setItem(3, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			assertDouble(helper, near, far);
			List<BlockPos> store = store(helper);
			assertCobble(helper, store, 10);
			assertFree(helper, store, 53, "one double chest's 54 less the cobblestone's slot");
			helper.succeed();
		});
	}

	/** Case 3: a barrel touching the connector and faced by the cable connector. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_barrel"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_barrel")
	public void aBarrelReachedTwoWaysCountsOnce(GameTestHelper helper) {
		network(helper);
		BlockPos barrel = new BlockPos(7, 2, 12);
		BlockPos cableConnector = new BlockPos(7, 2, 13);
		helper.setBlock(barrel, Blocks.BARREL);
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cableConnector);
		QaTomsNetworks1108cCompatTests.put(helper, barrel, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertCobble(helper, store, 10);
			assertFree(helper, store, 26, "one barrel's 27 less the cobblestone's slot");
			helper.succeed();
		});
	}

	/** Case 4: a chest that doesn't touch the connector, faced by two cable connectors on the connector's cable. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_two_cable_connectors"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_two_cable_connectors")
	public void aChestFacedByTwoCableConnectorsCountsOnce(GameTestHelper helper) {
		network(helper);
		BlockPos cable2 = new BlockPos(6, 2, 14);
		BlockPos cable3 = new BlockPos(7, 2, 14);
		BlockPos chest = new BlockPos(8, 2, 13);
		BlockPos west = new BlockPos(7, 2, 13); // facing east, at the chest
		BlockPos south = new BlockPos(8, 2, 14); // facing north, at the chest
		helper.setBlock(cable2, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(cable3, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(west, QaTomsNetworks1108cCompatTests.cableConnector(Direction.EAST));
		helper.setBlock(south, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cable2, cable3, west, south);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertCobble(helper, store, 10);
			assertFree(helper, store, 26, "one chest's 27 less the cobblestone's slot");
			helper.succeed();
		});
	}

	/** Case 5: two separate chests, one touching the connector and one faced by the cable connector, both count. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_two_real_chests"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_two_real_chests")
	public void twoRealChestsBothCount(GameTestHelper helper) {
		network(helper);
		BlockPos chest1 = new BlockPos(7, 2, 12); // touching the connector
		BlockPos cable2 = new BlockPos(6, 2, 14);
		BlockPos cableConnector = new BlockPos(7, 2, 14); // facing east, at the second chest
		BlockPos chest2 = new BlockPos(8, 2, 14);
		helper.setBlock(chest1, Blocks.CHEST);
		helper.setBlock(chest2, Blocks.CHEST);
		helper.setBlock(cable2, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.EAST));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cable2, cableConnector);
		// Both empty while Tom's builds the network: empty chests look alike and must not be taken for one.
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			assertCobble(helper, store, 0);
			assertFree(helper, store, 54, "two empty chests' 27 each");
			QaTomsNetworks1108cCompatTests.put(helper, chest1, new ItemStack(Items.COBBLESTONE, 10));
			QaTomsNetworks1108cCompatTests.put(helper, chest2, new ItemStack(Items.COBBLESTONE, 10));
			helper.runAfterDelay(20, () -> {
				assertCobble(helper, store, 20);
				assertFree(helper, store, 52, "two chests' 54 less one slot each");
				helper.succeed();
			});
		});
	}

	/** Case 6: after the merge, taking through the store empties the chest and gives exactly its 10; putting works. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_take_put"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_take_put")
	public void takingAndPuttingThroughAMergedChest(GameTestHelper helper) {
		network(helper);
		BlockPos chest = new BlockPos(7, 2, 12);
		BlockPos cableConnector = new BlockPos(7, 2, 13);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cableConnector);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			ServerLevel level = helper.getLevel();
			List<BlockPos> store = store(helper);
			assertCobble(helper, store, 10);
			int taken = SupplyContainers.extract(level, store, Items.COBBLESTONE, 20);
			helper.assertTrue(taken == 10, "taking 20 through the store gave " + taken + " of the chest's 10");
			helper.assertTrue(held(helper, chest) == 0, "the chest still holds " + held(helper, chest) + " cobblestone after taking all 10");
			assertCobble(helper, store, 0);
			ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 5));
			helper.assertTrue(rest.isEmpty(), "putting 5 through the store left " + rest.getCount());
			helper.assertTrue(held(helper, chest) == 5, "the chest holds " + held(helper, chest) + " cobblestone after putting 5");
			assertCobble(helper, store, 5);
			helper.succeed();
		});
	}

	/** Case 7: the cable connector broken after the merge, then placed again: 10 throughout. */
	//$ gametest_ticks_batch AREA '300' '"qa_merge_break_replace"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_merge_break_replace")
	public void breakingAndReplacingTheCableConnector(GameTestHelper helper) {
		network(helper);
		BlockPos chest = new BlockPos(7, 2, 12);
		BlockPos cableConnector = new BlockPos(7, 2, 13);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cableConnector);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			assertCobble(helper, store(helper), 10);
			helper.getLevel().destroyBlock(helper.absolutePos(cableConnector), false);
			helper.runAfterDelay(40, () -> {
				List<BlockPos> store = store(helper);
				assertCobble(helper, store, 10);
				assertFree(helper, store, 26, "one chest's 27 less the cobblestone's slot, the cable connector broken");
				helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
				QaTomsNetworks1108cCompatTests.joinUp(helper, cableConnector);
				helper.runAfterDelay(40, () -> {
					List<BlockPos> again = store(helper);
					assertCobble(helper, again, 10);
					assertFree(helper, again, 26, "one chest's 27 less the cobblestone's slot, the cable connector placed again");
					helper.succeed();
				});
			});
		});
	}
}
