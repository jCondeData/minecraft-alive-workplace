package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-01, lane-b-1101-0638) of B732: a bed placed after the village was founded is a home. A settlers' camp's
 * bedroll is broken once the village stands and its camp is cleared away, and the player puts his own brown bed on the
 * same spot before the hall's next round: the wagon-bed list still holds the spot, so the player's bed is taken for a
 * bedroll and its resident loses the home at the round.
 */
public class QaCabinBedsReplaceBug1101Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A real camp, its village founded (the hall's round holds its bedrolls); the camp's fire, hay and table cleared away
	 * and one bedroll broken; the player puts a brown bed straight back on that spot and a villager takes it. Expected
	 * (B732): it is a home, kept at the hall's next round and counted in Beds.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaCb1101RePlacedAtOnce"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaCb1101RePlacedAtOnce")
	public void qaABedPutBackAtOnceWhereAWagonBedStoodIsAHome(GameTestHelper helper) {
		QaCabinBedsHome1101GameTests.rePlaced(helper, false);
	}
}
