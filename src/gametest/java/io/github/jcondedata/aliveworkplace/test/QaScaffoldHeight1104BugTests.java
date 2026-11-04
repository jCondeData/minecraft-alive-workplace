package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-b-1104-0938), bug: the wiki's scaffolding page says a column runs down to the ground "at most 32 blocks",
 * and Scaffolds.MAX_HEIGHT is 32, but a block that needs a column of exactly 32 scaffolds gets none (the search stops
 * after 32 cells before looking at the ground under the 32nd), so it is skipped. See QaScaffoldHeight1104GameTests.
 */
public class QaScaffoldHeight1104BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A block 38 over the floor: a column of exactly 32 scaffolds, the builder standing on it 33 up. */
	//$ gametest 'HUGE'
	@GameTest(template = HUGE)
	public void aColumnOfExactlyThirtyTwoScaffoldsIsPlanned(GameTestHelper helper) {
		int[] c = QaScaffoldHeight1104GameTests.planned(helper, 32);
		helper.assertTrue(c != null, "no column planned for a block 38 over the floor, though 32 scaffolds (the page's \"at most 32\") reach it");
		helper.assertTrue(c[0] == 32 && c[1] == 33, "a block 38 over the floor: expected a column of 32 scaffolds standing 33 up, seen "
			+ c[0] + " scaffolds, standing " + c[1] + " up");
		helper.succeed();
	}
}
