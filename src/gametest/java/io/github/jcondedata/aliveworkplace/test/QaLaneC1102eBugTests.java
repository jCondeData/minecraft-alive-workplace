package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA lane c (1102e): B830's spec ("skip it and haul the logs that fit", as porters skip a stash or Drop Box whose goods
 * don't fit) with two Drop Boxes: the nearer one's goods don't fit the Storehouse, the farther one's do.
 */
public class QaLaneC1102eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos FAR_BOX = QaLaneC1102eGameTests.FAR_BOX;

	/**
	 * A Storehouse with room only for logs (26 full cobblestone stacks and a log). A Drop Box near it holds 32
	 * cobblestone, which can't go in; a second Drop Box farther off holds 32 logs, which can. The lumberjack has nothing to
	 * carry. The golem should pass the first box over and bring the 32 logs in.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaLaneC1102eTwoDropBoxes"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaLaneC1102eTwoDropBoxes")
	public void qaB830AFartherDropBoxWhoseGoodsFitIsEmptiedPastANearerOneThatDont(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		helper.setBlock(QaStackRoom1102cGameTests.HAUL_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(QaStackRoom1102cGameTests.HAUL_BOX)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		helper.setBlock(FAR_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(FAR_BOX)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 0);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int stored = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int left = QaStackRoom1102cGameTests.count(helper, FAR_BOX, Items.OAK_LOG);
			helper.assertTrue(stored == 33 && left == 0 && HaulerGolems.load(golem).isEmpty(), "the Storehouse (room for 63 logs) has " + stored
				+ " of 33 logs; the farther Drop Box keeps " + left + "; the golem carries " + HaulerGolems.load(golem)
				+ "; the nearer Drop Box's cobblestone (no room) holds it up");
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE) == 32,
				"the nearer Drop Box's cobblestone moved though none fits");
		});
	}
}
