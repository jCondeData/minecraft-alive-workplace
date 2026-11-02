package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA bug test (lane E, 2026-11-02; 39.7a): the server restarted between the village round that pointed the two stations
 * at their track and the next leaves both stations with Create's default name ('Track Station'), never their villages'
 * names: what names them is kept only in memory ({@code CreateTrains.pending}). See {@link QaTrainStationNames1102CompatTests}.
 */
public class QaTrainStationNamesRestart1102BugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '600' '"qaStationNamesRestart"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStationNamesRestart")
	public void bothStationsAreNamedAfterTheirVillagesAcrossARestart(GameTestHelper helper) {
		QaTrainStationNames1102CompatTests.named(helper, true);
	}
}
