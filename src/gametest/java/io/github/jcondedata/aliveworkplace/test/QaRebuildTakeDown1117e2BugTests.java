package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.AREA;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA lane E, second pass (2026-11-17), B1372: QaRebuildMoves1117e2GameTests' take-down after the drawing under the
 * house's id was rebuilt from scratch (another footprint). Moved out of QaRebuildMoves1117e2BugTests by lane-a-1117-1223
 * when B1372 was fixed, so this one is registered while the Mover's and Move page's cases (B1373, B1374) still fail.
 */
public class QaRebuildTakeDown1117e2BugTests implements FabricGameTest {

	/** A sneak take-down of the old house takes the old house down, not just what today's drawing would have put. */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuildTd1117e2Bug"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuildTd1117e2Bug")
	public void aHouseOfTheOldDrawingIsTakenDownAfterItsDrawingWasRebuilt(GameTestHelper helper) {
		QaRebuildMoves1117e2GameTests.takeDownBody(helper, true);
	}
}
