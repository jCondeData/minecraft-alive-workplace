package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA lane D (2026-11-22, lane-d-1122-0408), a bug to file: in a night raid with nobody ringing the bell, the Barracks'
 * guards stay by their grindstones indoors while raiders stand 8 to 10 blocks out in front of the building, well
 * inside {@code guardRadius} (24, "how far from their workstation guards patrol and fight") and the village's raid area
 * (raids.md: "Guards fight anything that carries the raider tag"). A guard picks a foe only when he sees it or it is
 * within 4 blocks, so walls kept him in. Same scenario as {@link QaBarracksTurnOut1122dGameTests} without the bell.
 */
public class QaBarracksTurnOut1122dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Barracks I, a night raid, no bell: both guards must come out to the raiders in front and go back after. */
	//$ gametest_ticks_batch AREA '2400' '"qaBarracksTurnOut1122dNoBell1"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBarracksTurnOut1122dNoBell1")
	public void qaBarracksGuardsTurnOutToANightRaidWithoutTheBell(GameTestHelper helper) {
		QaBarracksTurnOut1122dGameTests.turnOut(helper, StarterBlueprints.BARRACKS, 2, false);
	}

	/**
	 * Barracks I, a night raid, the bell rung: both guards turn out (this part passes), but 3,000 ticks after the raid is
	 * over, well past the rally's 1,800, they still stand at the bell about 10 blocks from their grindstones, not within the
	 * night patrol's 6 (GuardPatrol: "staying close to it at night"). Fresh guards with no bed claimed. Fixed with B1475:
	 * every night patrol point lay on the test area's barrier ceiling, far above his post, and was refused, so he stood where
	 * the rally left him; a guard outside his patrol's square now walks back to his post first (GuardPatrol).
	 */
	//$ gametest_ticks_batch AREA '4400' '"qaBarracksTurnOut1122dNightBell1"'
	@GameTest(template = AREA, timeoutTicks = 4400, batch = "qaBarracksTurnOut1122dNightBell1")
	public void qaBarracksGuardsGoBackToTheirPostsAfterANightRaidTheBellCalledThemTo(GameTestHelper helper) {
		QaBarracksTurnOut1122dGameTests.turnOut(helper, StarterBlueprints.BARRACKS, 2, true);
	}
}
