package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-10-28, second pass) bug tests for 36.20. "A worker whose far site sits in unloaded chunks waits there as today
 * (no chunk loading)": a field, a tree farm and a build 120 blocks out (outside a village of radius 16, inside the long
 * reach), in chunks nobody has loaded, keepWorkLoaded and the villages' keep-loaded off, the player gone; the far chunks
 * stay unloaded for 100 ticks with the worker still, and are loaded on the worker's first tick at work (like B666, the
 * miner's quarry). And "builds 128": a 15-tall pillar standing on the ground, its outline's centre exactly 128 blocks east
 * of the Blueprint Table, is refused ("That spot is 129 blocks ...") because the centre's height counts.
 * Setups in {@link QaFarReach1028bGameTests}.
 */
public class QaFarReach1028bBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_ticks_batch AREA '500' '"qa1028bFarField"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa1028bFarField")
	public void aFarFieldInUnloadedChunksIsNotLoadedByTheFarmer(GameTestHelper helper) {
		QaFarReach1028bGameTests.farField(helper, true);
	}

	//$ gametest_ticks_batch AREA '500' '"qa1028bFarTreeFarm"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa1028bFarTreeFarm")
	public void aFarTreeFarmInUnloadedChunksIsNotLoadedByTheLumberjack(GameTestHelper helper) {
		QaFarReach1028bGameTests.farTreeFarm(helper, true);
	}

	//$ gametest_ticks_batch AREA '500' '"qa1028bFarBuild"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa1028bFarBuild")
	public void aFarBuildInUnloadedChunksIsNotLoadedByTheBuilder(GameTestHelper helper) {
		QaFarReach1028bGameTests.farBuild(helper, true);
	}

	//$ gametest_ticks_batch AREA '200' '"qa1028bTallAt128"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028bTallAt128")
	public void aTallBuildCentred128OutIsInTheBuildersReach(GameTestHelper helper) {
		QaFarReach1028bGameTests.tall(helper, 128);
	}
}
