package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B830: a Hauler Golem passes over a Drop Box or worker whose goods don't fit the Storehouse for the next one that does
 * (QaHaulerFitChoice1102cBugTests). The edge: when nothing anyone has fits, the golem stays empty-handed and moves nothing.
 */
public class HaulerFitChoiceGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A Storehouse chest full of cobblestone, a Drop Box with 32 cobblestone, a miner with 64 more and a lumberjack with 64
	 * logs: none of it fits, so after 1,200 ticks the golem carries nothing and every chest holds what it did.
	 */
	//$ gametest_ticks_batch HUGE '1300' '"haulerFitChoiceNothingFits"'
	@GameTest(template = HUGE, timeoutTicks = 1300, batch = "haulerFitChoiceNothingFits")
	public void aHaulerWithNothingThatFitsMovesNothing(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		helper.setBlock(QaStackRoom1102cGameTests.HAUL_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(QaStackRoom1102cGameTests.HAUL_BOX)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.runAtTickTime(1200, () -> {
			helper.assertTrue(golem.isAlive() && HaulerGolems.load(golem).isEmpty(), "the golem carries " + HaulerGolems.load(golem) + " though nothing fits");
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE) == 27 * 64,
				"the full Storehouse chest changed");
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE) == 32,
				"the Drop Box has " + QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE) + " of 32 cobblestone");
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64,
				"the miner has " + QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) + " of 64 cobblestone");
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG) == 64,
				"the lumberjack has " + QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG) + " of 64 logs");
			helper.succeed();
		});
	}
}
