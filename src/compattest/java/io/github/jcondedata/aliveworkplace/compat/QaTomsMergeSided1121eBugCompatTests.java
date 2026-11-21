package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Failing QA test for B1025's fix (lane-e-1121-1013), NOT registered: a furnace read by Tom's Simple Storage at a side
 * (the fuel slot) and at its bottom (the result and fuel slots) shows its fuel twice. TomsMerge's class comment leaves
 * "a block whose faces show different slots (a furnace)" as it is, so this is the fix's stated limit, not a regression;
 * it is still an item doubled in the network view (the Storehouse and Tom's terminal).
 */
public class QaTomsMergeSided1121eBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STOREHOUSE = QaTomsNetworks1108cCompatTests.STOREHOUSE;
	private static final BlockPos CONNECTOR = new BlockPos(6, 2, 12);
	private static final BlockPos CABLE = new BlockPos(6, 2, 13);

	private static void network(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CONNECTOR, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(CABLE, TomsStorageCompatTests.block("inventory_cable"));
	}

	/** A furnace with 5 dirt in its input, 3 coal as fuel and 2 iron ingots as its result. */
	private static void furnace(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, Blocks.FURNACE);
		Container c = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		c.setItem(0, new ItemStack(Items.DIRT, 5));
		c.setItem(1, new ItemStack(Items.COAL, 3));
		c.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
	}

	/** A furnace touching Tom's connector at its west side and faced from below by a cable connector: a furnace touching the connector on its west side (fuel) and faced from below (result and fuel). */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_sided_side_and_bottom"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_sided_side_and_bottom")
	public void aFurnaceReadAtItsSideAndBottomCountsItsFuelOnce(GameTestHelper helper) {
		network(helper);
		BlockPos furnace = new BlockPos(7, 3, 12); // on top of the cable connector, east of a cable over the connector
		BlockPos under = new BlockPos(7, 2, 12); // facing up, at the furnace's bottom; next to the connector
		BlockPos up = new BlockPos(6, 3, 12);
		furnace(helper, furnace);
		helper.setBlock(up, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(CONNECTOR, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(under, QaTomsNetworks1108cCompatTests.cableConnector(Direction.UP));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, up, under);
		helper.runAfterDelay(40, () -> {
			long coal = QaTomsNetworks1108cCompatTests.toms(helper, up, Items.COAL);
			long ingots = QaTomsNetworks1108cCompatTests.toms(helper, up, Items.IRON_INGOT);
			helper.assertTrue(coal == 3 && ingots == 2, "Tom's connector shows " + coal + " coal and " + ingots
				+ " iron ingots of a furnace read at its side and bottom, wanted 3 and 2");
			helper.succeed();
		});
	}

}
