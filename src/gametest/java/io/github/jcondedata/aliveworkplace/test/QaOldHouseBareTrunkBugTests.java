package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.OldHouses;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-b-1027-0938), a bug test on B517's fix: a log column is judged only from the log the house's fill reaches
 * upward, so a bare column taller than a house can be (a tree's, by the fix's own rule) reached halfway up is measured
 * short and taken into the house (box 1 wider and 1 taller, 160 blocks for 153). Fails on main fa1177333.
 */
public class QaOldHouseBareTrunkBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A bare log column one taller than a house can be (OldHouses.MAX_Y + 1, nothing on its top) against the west wall:
	 * "a tree's when the column is taller than a house can be", so it stays out.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseTallBareTrunk"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseTallBareTrunk")
	public void aBareTrunkTallerThanAHouseIsLeftOut(GameTestHelper helper) {
		QaOldHouseTreesGameTests.trunkAgainstTheWall(helper, OldHouses.MAX_Y + 1, false, "a bare " + (OldHouses.MAX_Y + 1) + "-log column");
	}

}
