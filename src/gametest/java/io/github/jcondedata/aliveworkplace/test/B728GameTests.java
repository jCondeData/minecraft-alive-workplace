package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.always;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.at;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.camp;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.entity;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.village;

/**
 * B728: a lair by a quiet village leaves at the first dawn after both the village went quiet and the lair came, so a
 * camp an operator sets down by a village quiet since an earlier day stands until the day turns over.
 */
public class B728GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Set down on day 75 by a village at peace since day 70: it stands all day 75, across a save, and leaves at day 76's dawn. */
	//$ gametest_ticks_batch AREA '40' '"b728LaterCamp"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "b728LaterCamp")
	public void aCampSetDownAfterPeaceStandsAcrossASaveUntilTheDayTurns(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 70, 6000);
			entity.setAtPeace(true, 70);
			at(level, 75, 6000);
			camp(helper, hall);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "a camp set down on day 75 left at its first round");
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			at(level, 75, 23990);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "after a save, the camp set down on day 75 left before the day turned");
			helper.assertTrue(Lairs.near(level, hall).get().day() == 75, "the saved camp lost the day it came");
			at(level, 76, 20);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp set down on day 75 still stands at day 76's dawn");
			helper.succeed();
		});
	}

	/** A camp that stood before the village went quiet still leaves at the dawn after it went quiet, as before. */
	//$ gametest_ticks_batch AREA '40' '"b728EarlierCamp"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "b728EarlierCamp")
	public void aCampStandingBeforePeaceStillLeavesTheDawnAfterPeace(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 60, 6000);
			camp(helper, hall);
			at(level, 70, 6000);
			entity.setAtPeace(true, 70);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "a camp from day 60 left the day the village was set at peace");
			at(level, 71, 20);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a camp from day 60 still stands the dawn after the village was set at peace");
			helper.succeed();
		});
	}

	/** In a village that isn't quiet, a camp set down stays day after day. */
	//$ gametest_ticks_batch AREA '40' '"b728NotQuiet"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "b728NotQuiet")
	public void aCampByAVillageNotQuietStaysDayAfterDay(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 75, 6000);
			camp(helper, hall);
			Lairs.round(level, hall, true, always());
			at(level, 76, 20);
			Lairs.round(level, hall, true, always());
			at(level, 78, 6000);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "a camp by a village that isn't quiet left");
			helper.succeed();
		});
	}
}
