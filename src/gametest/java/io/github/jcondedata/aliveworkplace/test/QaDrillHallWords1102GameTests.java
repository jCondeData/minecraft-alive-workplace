package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-c-1102-0953) of B797's fix, from the bug's expected behaviour ("a line that doesn't name dummies when there
 * were none, in American English, with its .one form"): the whole line, word for word, in the plural and the singular;
 * a drill with no dummies that spans a restart (the fix's comment: the dummy list isn't saved, so it looks again) still
 * reads "by the hall"; and a village that has a dummy still reads "at the dummies" across a restart.
 */
public class QaDrillHallWords1102GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Two Guards by the hall, no dummy: "Drill day in X: 2 villagers sparred by the hall." */
	//$ gametest_ticks_batch HUGE '100' '"qaDrillHallTwo1102"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDrillHallTwo1102")
	public void qaTwoGuardsWithNoDummiesSparredByTheHall(GameTestHelper helper) {
		QaVillageFixesVerify1102GameTests.noDummyScene(helper, (level, hall) -> {
			QaVillageFixesVerify1102GameTests.standing(helper, QaVillageEconomySaves1101GameTests.HALL.offset(1, 0, 0), ModVillagers.GUARD, 0);
			QaVillageFixesVerify1102GameTests.standing(helper, QaVillageEconomySaves1101GameTests.HALL.offset(-1, 0, 0), ModVillagers.GUARD, 0);
			List<String> lines = QaVillageFixesVerify1102GameTests.drillDay(level, hall);
			String expected = "Drill day in " + VillageHalls.name(level, hall).getString() + ": 2 villagers sparred by the hall.";
			helper.assertTrue(lines.equals(List.of(expected)), "two Guards, no dummy: the chronicle reads " + lines + ", not [" + expected + "]");
		});
	}

	/** One Guard, no dummy: the singular, word for word. */
	//$ gametest_ticks_batch HUGE '100' '"qaDrillHallOne1102"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDrillHallOne1102")
	public void qaOneGuardWithNoDummiesReadsTheSingular(GameTestHelper helper) {
		QaVillageFixesVerify1102GameTests.noDummyScene(helper, (level, hall) -> {
			QaVillageFixesVerify1102GameTests.standing(helper, QaVillageEconomySaves1101GameTests.HALL.offset(1, 0, 0), ModVillagers.GUARD, 0);
			List<String> lines = QaVillageFixesVerify1102GameTests.drillDay(level, hall);
			String expected = "Drill day in " + VillageHalls.name(level, hall).getString() + ": 1 villager sparred by the hall.";
			helper.assertTrue(lines.equals(List.of(expected)), "one Guard, no dummy: the chronicle reads " + lines + ", not [" + expected + "]");
		});
	}

	/** No dummy, and the server restarts after the Guard drilled and before the drill ends: still "by the hall". */
	//$ gametest_ticks_batch HUGE '100' '"qaDrillHallRestart1102"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDrillHallRestart1102")
	public void qaADrillWithNoDummiesAcrossARestartStillSaysByTheHall(GameTestHelper helper) {
		QaVillageFixesVerify1102GameTests.noDummyScene(helper, (level, hall) -> {
			QaVillageFixesVerify1102GameTests.standing(helper, QaVillageEconomySaves1101GameTests.HALL.offset(1, 0, 0), ModVillagers.GUARD, 0);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();
			QaVillageEconomySaves1101GameTests.drillToday(level, hall, today);
			QaVillageEconomySaves1101GameTests.beat(level, today, 1000);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 20);
			QaVillageEconomySaves1101GameTests.restart(level, true);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_END + 20);
			List<String> lines = QaVillageEconomySaves1101GameTests.drillLines(level, hall, village);
			String expected = "Drill day in " + village + ": 1 villager sparred by the hall.";
			helper.assertTrue(lines.equals(List.of(expected)), "no dummy, a restart mid-drill: the chronicle reads " + lines + ", not [" + expected + "]");
		});
	}

	/** The other side: a village with a dummy, restarted mid-drill, still reads "at the dummies" (two villagers). */
	//$ gametest_ticks_batch HUGE '100' '"qaDrillDummiesRestart1102"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDrillDummiesRestart1102")
	public void qaADrillAtADummyAcrossARestartStillSaysAtTheDummies(GameTestHelper helper) {
		QaVillageEconomySaves1101GameTests.drillScene(helper, (level, hall) -> {
			QaVillageEconomySaves1101GameTests.driller(helper, 0);
			QaVillageEconomySaves1101GameTests.driller(helper, 1);
			long today = Chronicle.day(level);
			String village = VillageHalls.name(level, hall).getString();
			QaVillageEconomySaves1101GameTests.drillToday(level, hall, today);
			QaVillageEconomySaves1101GameTests.beat(level, today, 1000);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_START + 20);
			QaVillageEconomySaves1101GameTests.restart(level, true);
			QaVillageEconomySaves1101GameTests.beat(level, today, ReformStories.DRILL_END + 20);
			List<String> lines = QaVillageEconomySaves1101GameTests.drillLines(level, hall, village);
			String expected = "Drill day in " + village + ": 2 villagers sparred at the dummies.";
			helper.assertTrue(lines.equals(List.of(expected)), "a dummy, a restart mid-drill: the chronicle reads " + lines + ", not [" + expected + "]");
		});
	}
}
