package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA lane D (2026-11-22, lane-d-1122-0408), a bug to file (intermittent: ladders failed 3 of 9 runs, rams 1 of 3): in a night siege (ladders or rams)
 * on a village behind Palisade I, the archer takes his walk station (11, 5, 7), hits and kills the raider 12 blocks out,
 * then about 190 ticks later leaves the walk and walks off the palisade inland and out past its end (seen at (-1.5, 0,
 * 2.5) and (1.5, 0, -2.5), helper positions), still "stationed" with the same place, the siege and raid still on, the
 * raider 6 out alive and never hit, no ladder up. sieges.md: "On a station he never climbs down to chase: when the raider
 * he is shooting at goes out of his sight ... he turns to the nearest raider he can see and reach." The same raiders on
 * Palisade II (ladders and rams) and on Palisade I with a raider 12 out and one at the foot passed every run.
 */
public class QaPalisadeNight1122dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A ladder culture's night attack on Palisade I: the archer on the walk hits raiders 6 and 12 out and never leaves the walk. */
	//$ gametest_ticks_batch AREA '1100' '"qaPalisadeNight1122dLadders1"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaPalisadeNight1122dLadders1")
	public void qaAtNightAnArcherOnThePalisadesWalkHitsLadderRaidersSixAndTwelveOut(GameTestHelper helper) {
		QaPalisadeNight1122dGameTests.nightAttack(helper, StarterBlueprints.PALISADE, QaPalisadeNight1122dGameTests.NIGHT_CLIMBERS, "ladders", 6, 12, 0);
	}

	/** The same with a ram culture. */
	//$ gametest_ticks_batch AREA '1100' '"qaPalisadeNight1122dRams1"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaPalisadeNight1122dRams1")
	public void qaAtNightAnArcherOnThePalisadesWalkHitsRamRaidersSixAndTwelveOut(GameTestHelper helper) {
		QaPalisadeNight1122dGameTests.nightAttack(helper, StarterBlueprints.PALISADE, QaPalisadeNight1122dGameTests.NIGHT_RAMS, "ram_gates", 6, 12, 0);
	}
}
