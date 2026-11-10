package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTanks1110gCompatTests.AREA;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTanks1110gCompatTests.TALL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTanks1110gCompatTests.movesWhole;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverTanks1110gCompatTests.reloadedHalfUp;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, 2026-11-10, fourth run): the Mover (45.3) and a Create Fluid Tank three blocks tall, which fails on main
 * after B1135's fix (the two-tall tank of B1135 and a tank two wide and two tall pass: QaCreateMoverTanks1110gCompatTests).
 * Holding twenty buckets (more than two blocks hold), it arrives as one tank (Height 3) reading only eight buckets
 * through every block: twelve are gone, nothing loose. The same with a save and reload while it is half put up.
 */
public class QaCreateMoverTanks1110gBugCompatTests implements FabricGameTest {
	/** A three-tall tank holding twenty buckets arrives as one tank with all twenty. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110g_mover_tank3"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110g_mover_tank3")
	public void aMovedThreeTallTankKeepsAllItsWater(GameTestHelper helper) {
		movesWhole(helper, "tank3", TALL, 20);
	}

	/** Saved and read back with the three-tall tank half put up at the new spot: one tank, its twenty buckets once. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110g_mover_tank3_reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110g_mover_tank3_reload")
	public void aMoveReloadedWithTheTallTankHalfUpLosesAndDoublesNothing(GameTestHelper helper) {
		reloadedHalfUp(helper, "tank3_reload", TALL, 20);
	}
}
