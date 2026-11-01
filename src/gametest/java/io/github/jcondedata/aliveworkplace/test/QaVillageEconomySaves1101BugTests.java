package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

/**
 * QA (2026-11-01), two bugs found by {@link QaVillageEconomySaves1101GameTests}.
 * <p>
 * After B760: when the Storehouse fills up while the Carpenter carries a chestless builder's planks to it and stays
 * full, he waits 2400 ticks, gives up and takes them "home" ({@code CrafterWork.tidy}); with no chest by his bench that
 * is the ground by it, the B721 symptom again: the builder never gets them and waits. Expected (B721/B760): what he makes
 * reaches the builder who asked, or isn't made; never planks lying on the ground while the builder waits.
 * <p>
 * After B762: the drill's villagers are now written in the reform stories' file, but a file of saved
 * data is only written when something marked it changed. Nothing in a drill beat does, so a server that saved before noon
 * (an autosave, every 5 minutes) and stops mid-drill writes the file from before anyone drilled: the restart still
 * forgets them and the chronicle's "drilled N villagers" line is short. Expected (B762): "the count is the same with or
 * without a restart".
 */
public class QaVillageEconomySaves1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";

	/**
	 * The Storehouse fills up while the Carpenter carries a chestless builder's 4 planks to it and stays full for good.
	 * However long he waits and whatever he does after giving up, the planks never lie on the ground (the builder never
	 * looks there), and 3200 ticks later all 4 are still in a bag or a chest.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaEcon1101StaysFull"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaEcon1101StaysFull")
	public void aStorehouseThatStaysFullAfterHeSetOffLeavesNoPlanksLying(GameTestHelper helper) {
		QaCarpenterStorehouse1101GameTests.Run run = QaCarpenterStorehouse1101GameTests.setUp(helper, "econfull", 1, false, false, false);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			QaCarpenterStorehouse1101GameTests.conserve(helper, run, 4);
			int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
			if (lying > 0) {
				ItemEntity e = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), i -> i.getItem().is(Items.SPRUCE_PLANKS)).get(0);
				throw new GameTestAssertException("at tick " + run.tick() + " (" + (run.tick() - filledAt.get()) + " after the Storehouse filled) " + lying
					+ " spruce planks lie on the ground " + String.format("%.1f", Math.sqrt(e.blockPosition().distSqr(helper.absolutePos(QaCarpenterStorehouse1101GameTests.STATION))))
					+ " blocks from the Carpenter's bench and " + String.format("%.1f", Math.sqrt(e.blockPosition().distSqr(helper.absolutePos(QaCarpenterStorehouse1101GameTests.STORE_CHEST))))
					+ " from the Storehouse chest, while the chestless builder still waits for " + run.a().missing());
			}
			if (filledAt.get() < 0 && QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set((int) run.tick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filledAt.get() >= 0, "the Carpenter never set off with the planks");
			helper.assertTrue(run.tick() >= filledAt.get() + 3200, "still watching");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) + store.countItem(Items.SPRUCE_PLANKS) == 4,
				"the 4 planks aren't in a bag or the Storehouse: bags " + QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) + ", store "
				+ store.countItem(Items.SPRUCE_PLANKS));
		});
	}

	/**
	 * An autosave in the morning, two villagers drill at noon, the server stops (saving what changed) and starts again,
	 * the drill ends: the line counts both, as without the restart.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEcon1101DrillAutosave"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEcon1101DrillAutosave")
	public void aRestartAfterAnAutosaveStillCountsTheMorningsDrillers(GameTestHelper helper) {
		QaVillageEconomySaves1101GameTests.drillScene(helper, (level, hall) -> {
			QaVillageEconomySaves1101GameTests.driller(helper, 0);
			QaVillageEconomySaves1101GameTests.driller(helper, 1);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();
			QaVillageEconomySaves1101GameTests.drillToday(level, hall, today);
			QaVillageEconomySaves1101GameTests.beat(level, today, 1000);
			QaVillageEconomySaves1101GameTests.restart(level, false); // the morning's autosave: the drill day is written
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 20);
			int drilled = QaVillageEconomySaves1101GameTests.drillers(ReformStories.Data.get(level).entry(hall)).size();
			helper.assertTrue(drilled == 2, "before the restart " + drilled + " villagers drilled, not 2");
			QaVillageEconomySaves1101GameTests.restart(level, false); // the server stops: it writes what changed
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_END + 20);
			List<String> lines = QaVillageEconomySaves1101GameTests.drillLines(level, hall, village);
			String expected = "Drill day in " + village + ": 2 villagers sparred at the dummies.";
			helper.assertTrue(lines.equals(List.of(expected)), "after a restart mid-drill (the file last written in the morning) the chronicle reads "
				+ lines + ", not [" + expected + "]: the drillers were never marked to be saved");
		});
	}
}
