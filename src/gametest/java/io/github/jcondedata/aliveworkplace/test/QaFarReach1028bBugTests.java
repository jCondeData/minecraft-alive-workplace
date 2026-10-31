package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-10-28, second pass) bug test for 36.20 "builds 128": a 15-tall pillar standing on the ground, its outline's
 * centre exactly 128 blocks east of the Blueprint Table, was refused ("That spot is 129 blocks ...") because the centre's
 * height counted (B668). The pass's unloaded-chunks tests (B667) stay on tests/far-reach-2 until that bug is fixed.
 * Setups in {@link QaFarReach1028bGameTests}.
 */
public class QaFarReach1028bBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_ticks_batch AREA '200' '"qa1028bTallAt128"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028bTallAt128")
	public void aTallBuildCentred128OutIsInTheBuildersReach(GameTestHelper helper) {
		QaFarReach1028bGameTests.tall(helper, 128);
	}
}
