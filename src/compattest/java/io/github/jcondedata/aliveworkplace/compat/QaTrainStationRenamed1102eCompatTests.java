package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.compat.create.CreateTrains;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane E, Create and saves, 2026-11-02; B800's fix): after a restart between the village round that pointed the
 * stations at their track and the next, the village names a station Create still calls by its default name ('Track
 * Station'), but a station a player renamed by hand (Create's station screen) keeps the player's name. The village's
 * train schedule is made from the stations' names as they are when it is put together, so a player's name doesn't
 * stop the train. The restart is a new {@link CreateTrains}, as the server makes at start-up (as in
 * {@link QaTrainStationNames1102CompatTests}).
 */
public class QaTrainStationRenamed1102eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);

	//$ gametest_ticks_batch AREA '600' '"qaStationRenamedRestart"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStationRenamedRestart")
	public void aStationAPlayerRenamedKeepsHisNameAcrossARestartAndTheOtherIsNamedAfterItsVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		CaravanTrains.Railway[] r = {new CreateTrains()};
		boolean[] renamed = {false};
		int[] tick = {0};
		int[] renamedAt = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] == 1) {
				helper.assertTrue(!(r[0].aim(level, a, b, "Alpha") & r[0].aim(level, b, a, "Beta")),
					"setup: the stations were aimed before this test pointed them at the track");
				r[0] = new CreateTrains(); // the server stops after the round that pointed them, and starts again
				return;
			}
			if (!renamed[0]) {
				if (level.getBlockEntity(a) instanceof StationBlockEntity be && be.getStation() != null) {
					helper.assertTrue("Track Station".equals(be.getStation().name), "setup: the station before the player renames it is called '"
						+ be.getStation().name + "', not Create's default");
					be.updateName("Harbor"); // what Create's station screen does when a player types a name
					renamed[0] = true;
					renamedAt[0] = tick[0];
				} else if (tick[0] > 300) {
					helper.fail("setup: Create never made station A a station after 300 ticks");
				}
				return;
			}
			if ((tick[0] - renamedAt[0]) % 20 != 1) {
				return; // a village round every second
			}
			boolean aimed = r[0].aim(level, a, b, "Alpha") & r[0].aim(level, b, a, "Beta");
			if (aimed && r[0].linked(level, a, b)) {
				String na = QaTrainStationNames1102CompatTests.name(level, a);
				String nb = QaTrainStationNames1102CompatTests.name(level, b);
				helper.assertTrue(na.equals("Harbor"), "the station a player renamed 'Harbor' after a restart is now called '" + na
					+ "': the village wrote over the player's name");
				helper.assertTrue(nb.equals("Beta"), "the other station, still Create's default after the restart, is called '" + nb
					+ "', not after its village (Beta)");
				helper.succeed();
			} else if (tick[0] > 500) {
				helper.fail("Create's railway doesn't know both stations on one network after 500 ticks (aimed " + aimed + ")");
			}
		});
	}

	/** A village round after both are named changes nothing (no renaming back and forth across restarts). */
	//$ gametest_ticks_batch AREA '600' '"qaStationNamedTwice"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStationNamedTwice")
	public void stationsNamedAfterTheirVillagesStaySoThroughASecondRestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		CaravanTrains.Railway[] r = {new CreateTrains()};
		int[] tick = {0};
		int[] namedAt = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 20 != 1) {
				return;
			}
			boolean aimed = r[0].aim(level, a, b, "Alpha") & r[0].aim(level, b, a, "Beta");
			if (tick[0] == 1) {
				r[0] = new CreateTrains(); // first restart, between aiming and naming
				return;
			}
			String na = QaTrainStationNames1102CompatTests.name(level, a);
			String nb = QaTrainStationNames1102CompatTests.name(level, b);
			if (namedAt[0] < 0) {
				if (aimed && na.equals("Alpha") && nb.equals("Beta")) {
					namedAt[0] = tick[0];
					r[0] = new CreateTrains(); // second restart, after both were named
				} else if (tick[0] > 400) {
					helper.fail("the stations weren't named after their villages after 400 ticks: '" + na + "' and '" + nb + "' (aimed " + aimed + ")");
				}
				return;
			}
			helper.assertTrue(aimed, "after a second restart the village no longer sees its station as aimed");
			helper.assertTrue(na.equals("Alpha") && nb.equals("Beta"), "after a second restart the stations are called '" + na + "' and '" + nb + "'");
			if (tick[0] - namedAt[0] >= 60) {
				helper.succeed(); // three village rounds after the second restart
			}
		});
	}
}
