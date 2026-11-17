package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.AREA;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA lane E, second pass (2026-11-17): QaRebuildMoves1117e2GameTests' bodies run after the drawing under the house's id
 * was rebuilt from scratch (another footprint, 7x3 instead of 5x5; tools/blueprints/REBUILD.md: "standing buildings ...
 * keep their old drawing until a refresh"). Each fails today because the feature reads the building's box and blocks
 * from TODAY's drawing. Not registered (they fail); each passes once fixed, and the controls pass today. The take-down case
 * (B1372, fixed) is in {@link QaRebuildTakeDown1117e2BugTests}.
 */
public class QaRebuildMoves1117e2BugTests implements FabricGameTest {

	/** The Mover moves the whole old house, not today's drawing's box of it: nothing left behind, its bed moved. */
	//$ gametest_ticks_batch AREA '400' '"qaRebuildMv1117e2Bug"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRebuildMv1117e2Bug")
	public void aHouseOfTheOldDrawingIsMovedWholeAfterItsDrawingWasRebuilt(GameTestHelper helper) {
		QaRebuildMoves1117e2GameTests.moveBody(helper, true);
	}

	/** The Move page plans the old house as it stands (5x5, its bed), not today's 7x3 drawing. */
	//$ gametest_ticks_batch AREA '200' '"qaRebuildFp1117e2Bug"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRebuildFp1117e2Bug")
	public void theMovePagePlansAHouseOfTheOldDrawingAsItStands(GameTestHelper helper) {
		QaRebuildMoves1117e2GameTests.footprintBody(helper, true);
	}

	/** Another building can't be moved onto the old house's back rows (outside today's drawing's footprint). */
	//$ gametest_ticks_batch AREA '200' '"qaRebuildFit1117e2Bug"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRebuildFit1117e2Bug")
	public void aSpotOverTheBackOfAHouseOfTheOldDrawingIsRefused(GameTestHelper helper) {
		QaRebuildMoves1117e2GameTests.fitBody(helper, true);
	}
}
