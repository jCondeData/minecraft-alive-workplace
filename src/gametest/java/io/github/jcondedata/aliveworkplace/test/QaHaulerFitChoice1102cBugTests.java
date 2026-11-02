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
 * QA lane c, 2026-11-02: B806's spec ("a busy Storehouse with a stack of every kind ... stops its haulers for good.
 * Expected: a Storehouse with room for the load is not full; the golem brings the logs in") on a Storehouse that has room
 * for one worker's goods and not another's. The porter skips a stash whose goods don't fit (B820's fix); the hauler
 * picks the nearest stash, or a non-empty Drop Box, whatever fits, and when nothing of it fits it stands there for good.
 * These fail on main (2026-11-02) and travel on tests/hauler-fit-choice-1102c until the fix.
 */
public class QaHaulerFitChoice1102cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A busy Storehouse: 26 full stacks of cobblestone and one oak log. The miner nearest the Storehouse has spare
	 * cobblestone, which doesn't fit; a lumberjack farther off has 64 logs, 63 of which do. The Storehouse has room for the
	 * logs, so it isn't full for them: the golem brings 63 in and the miner's cobblestone stays.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaRoom1102cHaulerMixed"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaRoom1102cHaulerMixed")
	public void qaTheHaulerBringsTheGoodsThatFitPastANearerWorkersThatDont(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int stored = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int left = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored + left + QaStackRoom1102cGameTests.carried(golem, Items.OAK_LOG) == 65, "logs lost or made: " + stored + " + " + left + " + " + HaulerGolems.load(golem));
			helper.assertTrue(stored == 64 && left == 1 && HaulerGolems.load(golem).isEmpty(), "the Storehouse (room for 63 logs, none for cobblestone) has "
				+ stored + " of 64 logs; the lumberjack keeps " + left + "; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64 && QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE) == 26 * 64,
				"cobblestone moved though none fits: the miner has " + QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE));
		});
	}

	/**
	 * The same busy Storehouse (a log in every slot: room for logs, none for cobblestone), with a Drop Box holding 32
	 * cobblestone and a lumberjack with 64 logs. The Drop Box's cobblestone can't go in, so it doesn't hold the hauler up:
	 * the logs come in and the Drop Box keeps its cobblestone.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaRoom1102cHaulerDropBox"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaRoom1102cHaulerDropBox")
	public void qaADropBoxWhoseGoodsDontFitDoesntStopTheHauler(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.OAK_LOG, 1);
		store.setItem(0, new ItemStack(Items.COBBLESTONE, 64)); // the only cobblestone stack, full
		helper.setBlock(QaStackRoom1102cGameTests.HAUL_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(QaStackRoom1102cGameTests.HAUL_BOX)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		for (int i = 1; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(i % 2 == 0 ? Items.OAK_LOG : Items.STONE, 1));
		}
		// slots 1..26: 13 stone (1 each, no room for cobblestone) and 13 oak logs (room for 63 each)
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int stored = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int left = QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored + left + QaStackRoom1102cGameTests.carried(golem, Items.OAK_LOG) == 13 + 64, "logs lost or made: " + stored + " + " + left + " + " + HaulerGolems.load(golem));
			helper.assertTrue(stored == 13 + 64 && left == 0 && HaulerGolems.load(golem).isEmpty(), "the Storehouse has " + stored + " of " + (13 + 64)
				+ " logs; the lumberjack keeps " + left + "; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE) == 32 && QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE) == 64,
				"the Drop Box's cobblestone moved though none fits: the box has " + QaStackRoom1102cGameTests.count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE));
		});
	}
}
