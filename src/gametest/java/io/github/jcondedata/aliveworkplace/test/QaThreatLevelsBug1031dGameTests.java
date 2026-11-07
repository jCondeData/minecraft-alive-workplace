package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
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
 * QA (2026-10-31, lane d), ROADMAP 32.21: failing tests for a real bug. A lair an operator sets down by a quiet village
 * ({@code /workplace threat lair}) should stand until the next dawn (ThreatCommand's own doc and docs/wiki/peace.md: "a
 * lair set down by a quiet village still leaves at the next dawn"; 32.21: "a lair already standing leaves at the next
 * dawn"), but {@code Lairs.round} compares today with the day the village became quiet (the hall's {@code peaceSince},
 * or the in-memory day level 0 was first seen with a lair), not the day the lair came: a lair set down any later day
 * leaves at the very next hall round.
 */
public class QaThreatLevelsBug1031dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * The operators' order (32.21; the guide: "a lair set down by a quiet village still leaves at the next dawn"): a
	 * camp set down by a village At peace since day 70, on day 75 at noon, stands through day 75 and leaves at day 76's
	 * dawn.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031dOrderedAtPeace"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031dOrderedAtPeace")
	public void aCampOrderedByAVillageAtPeaceStandsUntilTheNextDawn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 70, 6000);
			entity.setAtPeace(true, 70);
			Lairs.round(level, hall, true, always());
			at(level, 75, 6000);
			Lairs.round(level, hall, true, always());
			camp(helper, hall);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "a camp set down on day 75 by a village at peace since day 70 left at the next round, not at the next dawn");
			at(level, 76, 50);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the ordered camp still stands at the next dawn");
			helper.succeed();
		});
	}

	/**
	 * The same at threat level 0, after an earlier camp already left: the first camp leaves at day 81's dawn; a camp an
	 * operator sets down on day 85 stands through day 85 and leaves at day 86's dawn.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031dOrderedLevelZero"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031dOrderedLevelZero")
	public void aCampOrderedAtLevelZeroAfterAnotherLeftStandsUntilTheNextDawn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 80, 13000);
			camp(helper, hall);
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			Lairs.round(level, hall, true, always());
			at(level, 81, 100);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the first camp didn't leave at level 0");
			at(level, 85, 6000);
			Lairs.round(level, hall, true, always());
			camp(helper, hall);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "a camp set down on day 85 at level 0 left at the next round, not at the next dawn");
			at(level, 86, 50);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the ordered camp still stands at the next dawn");
			helper.succeed();
		});
	}
}
