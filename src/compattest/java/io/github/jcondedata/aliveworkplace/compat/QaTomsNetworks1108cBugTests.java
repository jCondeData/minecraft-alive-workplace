package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Failing QA test (ROADMAP 37.10, lane-c-1108-0053): one chest that touches a Tom's inventory connector and is also
 * faced by a cable connector on that connector's cable is read twice: the Storehouse counts its items double.
 */
public class QaTomsNetworks1108cBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * A chest that touches the connector and is also faced by a cable connector on the connector's own cable: one
	 * inventory reached two ways, its items counted once (by the Storehouse and by Tom's connector alike).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_toms_two_ways"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_two_ways")
	public void aChestReachedTwoWaysCountsOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos connector = new BlockPos(6, 2, 12);
		BlockPos chest = new BlockPos(7, 2, 12);
		BlockPos cable = new BlockPos(6, 2, 13);
		BlockPos cableConnector = new BlockPos(7, 2, 13); // facing north, at the chest the connector touches
		helper.setBlock(QaTomsNetworks1108cCompatTests.STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(connector, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(cable, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, connector, cable, cableConnector);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 10));
		BlockPos storehouse = helper.absolutePos(QaTomsNetworks1108cCompatTests.STOREHOUSE);
		helper.runAfterDelay(40, () -> {
			Set<BlockPos> members = StorageNetworks.members(level, helper.absolutePos(connector));
			helper.assertTrue(members.equals(Set.of(helper.absolutePos(chest))), "the connector joins " + QaTomsNetworks1108cCompatTests.rel(helper, List.copyOf(members)) + ", wanted the chest");
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))), "the store: " + QaTomsNetworks1108cCompatTests.rel(helper, store) + ", wanted the connector alone");
			helper.assertTrue(SupplyContainers.count(level, store, Items.COBBLESTONE) == 10, "the chest holds 10 cobblestone; the Storehouse counts "
				+ SupplyContainers.count(level, store, Items.COBBLESTONE) + " and Tom's connector shows " + QaTomsNetworks1108cCompatTests.toms(helper, connector, Items.COBBLESTONE));
			helper.assertTrue(QaTomsNetworks1108cCompatTests.toms(helper, connector, Items.COBBLESTONE) == 10,
				"Tom's connector shows " + QaTomsNetworks1108cCompatTests.toms(helper, connector, Items.COBBLESTONE) + " cobblestone of the chest's 10");
			// One chest's room, not two: its 26 free slots.
			int free = SupplyContainers.freeSlots(level, store);
			helper.assertTrue(free == 26, "the store has " + free + " free slots, wanted the one chest's 26");
			helper.succeed();
		});
	}

}
