package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.compat.create.CreateTrains;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane E, Create and saves, 2026-11-02; 39.7a): the village train's schedule goes by its two stations' names, so each
 * village's station is named after its village once Create knows it (two stations of one name would be one stop: {@code
 * CaravanTrains.aimed}). A station is pointed at its track in one village round and named in a later one, when Create
 * has made it a station. A server stopped between those rounds (a restart, or a crash) must still end with both stations
 * named, as a server that ran on does (that case fails: B-number in QaTrainStationNamesRestart1102BugCompatTests on
 * tests/station-names-1102). The restart is a new {@link CreateTrains}, as the server makes at start-up.
 */
public class QaTrainStationNames1102CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);

	static String name(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof StationBlockEntity be)) {
			return "(no station block)";
		}
		GlobalStation station = be.getStation();
		return station == null ? "(not a station yet)" : station.name;
	}

	static void named(GameTestHelper helper, boolean restart) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		CaravanTrains.Railway[] r = {new CreateTrains()};
		boolean[] first = {true};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 20 != 1) {
				return; // a village round every second
			}
			boolean aimed = r[0].aim(level, a, b, "Alpha") & r[0].aim(level, b, a, "Beta");
			if (first[0]) {
				first[0] = false;
				helper.assertTrue(!aimed, "setup: the stations were aimed before this test pointed them at the track");
				if (restart) {
					r[0] = new CreateTrains(); // the server stops after the round that pointed them, and starts again
				}
				return;
			}
			if (aimed && r[0].linked(level, a, b)) {
				String na = name(level, a);
				String nb = name(level, b);
				helper.assertTrue(na.equals("Alpha") && nb.equals("Beta"), "the stations, on one network, are named '" + na + "' and '" + nb
					+ "', not after their villages (Alpha and Beta)" + (restart ? " after a restart between the round that aimed them and the next" : ""));
				helper.succeed();
			} else if (tick[0] > 400) {
				helper.fail("Create's railway doesn't know both stations on one network after 400 ticks (aimed " + aimed + ")");
			}
		});
	}

	/** Without a restart: aimed in one round, named in a later one. */
	//$ gametest_ticks_batch AREA '600' '"qaStationNames"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStationNames")
	public void bothStationsAreNamedAfterTheirVillages(GameTestHelper helper) {
		named(helper, false);
	}

	/**
	 * What a player sees of it: the stations aimed, the server restarted, then the village's train put together at its
	 * station must still run to the other village's station on its schedule (which goes by the two stations' names).
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaStationNamesRestartRuns"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaStationNamesRestartRuns")
	public void aTrainAimedBeforeARestartStillRunsToTheOtherStation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		java.util.UUID owner = java.util.UUID.randomUUID();
		net.minecraft.world.entity.npc.Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CaravanTrains.Railway[] r = {new CreateTrains()};
		boolean[] built = {false};
		int[] tick = {0};
		int[] builtAt = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] == 1) {
				helper.assertTrue(!(r[0].aim(level, a, b, "Alpha") & r[0].aim(level, b, a, "Beta")), "setup: aimed before pointed at the track");
				r[0] = new CreateTrains(); // the server stops after that round, and starts again
				return;
			}
			if (!built[0]) {
				if (tick[0] % 20 != 1) {
					return;
				}
				boolean aimed = r[0].aim(level, a, b, "Alpha") & r[0].aim(level, b, a, "Beta");
				if (aimed && r[0].linked(level, a, b)) {
					java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> parts = r[0].train(level, a, b);
					helper.assertTrue(parts != null && !parts.isEmpty(), "setup: no room for the train's blocks past the station");
					parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
					helper.assertTrue(r[0].assemble(level, a, b, owner, driver) != null, "setup: the station didn't assemble the village train");
					built[0] = true;
					builtAt[0] = tick[0];
					helper.assertTrue(r[0].at(level, owner, a) && !r[0].at(level, owner, b), "the train put together at its own station stands at "
						+ (r[0].at(level, owner, b) ? "the other village's station" : "neither") + " (stations '" + name(level, a) + "' and '"
						+ name(level, b) + "'): " + r[0].status(level, owner));
					return;
				} else if (tick[0] > 400) {
					helper.fail("Create's railway doesn't know both stations on one network after 400 ticks (aimed " + aimed + ")");
				}
			} else if (r[0].at(level, owner, b)) {
				r[0].scrap(level, owner);
				helper.succeed();
			} else if (tick[0] - builtAt[0] > 1000) {
				String status = r[0].status(level, owner);
				r[0].scrap(level, owner);
				helper.fail("after a restart between aiming and naming, the village train never reached the other station in 1000 ticks (stations '"
					+ name(level, a) + "' and '" + name(level, b) + "'): " + status);
			}
		});
	}
}
