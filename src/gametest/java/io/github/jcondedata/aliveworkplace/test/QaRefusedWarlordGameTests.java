package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Bug test (lane D QA, 2026-10-24): 32.10 says the Warlord's band, at the outpost and raiding, turns zombified when he
 * falls, and stays; B208 says every threat mob must stay on a server whose blocklist (Cobbleverse's MobsBeGone) refuses
 * nearly every vanilla mob. Piglins.zombify uses vanilla convertTo, whose addFreshEntity such a blocklist refuses, so
 * the whole band vanishes instead. The control with nothing refused is QaRefusedThreatsGameTests.
 */
public class QaRefusedWarlordGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * 32.10 with the world refusing every hostile mob (zombified piglins and zoglins too): each piglin and hoglin of the
	 * band and the raid is still there, zombified, after the Warlord falls.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaRefusedWarlord"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaRefusedWarlord")
	public void aWarlordsBandTurnsZombifiedWhenAnotherModRefusesZombifiedPiglins(GameTestHelper helper) {
		QaRefusedThreatsGameTests.warlordFalls(helper, true);
	}

}
