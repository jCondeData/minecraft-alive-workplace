package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

/**
 * B933: a player names both villages' stations the same ("Station"). Create's schedule goes by name and sends a train
 * to the nearest station of it, so a train standing at one of them could count as already at the other and never
 * leave (QA's {@link QaTrainStationSameName1104eCompatTests} caught it 1 run in 8: whether Create's search finds the
 * station the train stands at depends on where exactly it stopped). Here that always happens: the rename falls while the
 * train stands at a station, once at the village's own (where it was put together) and once at the other's, and the
 * train is given Create's own outcome of picking that namesake (its schedule's stop for the other station counted as
 * reached where it stands, as Create's {@code ScheduleRuntime.tick} does when the nearest match is the current station).
 * The train leaves for the right station well before its wait there would end, and Ashford's 32 logs still ride to
 * Bramble and arrive, none lost or doubled. The line and villages are {@link CreateTrainCompatTests}'s.
 */
public class TrainStationsOneNameCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);

	/** Renamed while the train stands at the station it was put together at (the sending village's). */
	//$ gametest_ticks_batch AREA '3600' '"trainOneNameAtHome"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "trainOneNameAtHome")
	public void bothStationsNamedAlikeWhileTheTrainStandsAtHomeStillCarryTheCaravan(GameTestHelper helper) {
		run(helper, STATION_A, "at the sending village's station");
	}

	/** Renamed while the train stands at the other village's station (it has to come back for the caravan first). */
	//$ gametest_ticks_batch AREA '3600' '"trainOneNameAway"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "trainOneNameAway")
	public void bothStationsNamedAlikeWhileTheTrainStandsAwayStillCarryTheCaravan(GameTestHelper helper) {
		run(helper, STATION_B, "at the other village's station");
	}

	private static void rename(GameTestHelper helper, BlockPos which, String name) {
		StationBlockEntity be = (StationBlockEntity) helper.getBlockEntity(which);
		helper.assertTrue(be.getStation() != null, "setup: the station isn't a station Create knows");
		be.updateName(name); // what Create's station screen does when a player types a name
	}

	@org.jetbrains.annotations.Nullable
	private static Train train(UUID owner) {
		for (Train t : Create.RAILWAYS.trains.values()) {
			if (owner.equals(t.owner) && !t.invalid) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Create's outcome when the nearest station of the stop's name is the one the train stands at: the stop for the
	 * other station ({@code away}) counted as reached here (ScheduleRuntime.tick: IN_TRANSIT, then destinationReached).
	 */
	private static void arrivedAtTheNamesake(GameTestHelper helper, Train t, String away) {
		int entry = -1;
		for (int i = 0; i < t.runtime.getSchedule().entries.size(); i++) {
			if (t.runtime.getSchedule().entries.get(i).instruction instanceof DestinationInstruction to && to.getData().getString("Text").equals(away)) {
				entry = i;
			}
		}
		helper.assertTrue(entry >= 0, "setup: no stop of the schedule goes to '" + away + "'");
		t.runtime.currentEntry = entry;
		t.runtime.state = ScheduleRuntime.State.IN_TRANSIT;
		t.runtime.destinationReached();
	}

	private static void run(GameTestHelper helper, BlockPos standing, String where) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID train = CaravanTrains.railwayId(v.a(), v.b());
		BlockPos at = helper.absolutePos(standing);
		int[] tick = {0};
		int[] sentAt = {-1};
		boolean[] aboard = {false};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (sentAt[0] < 0) {
				// the bad case forced: both renamed in the tick the train is seen standing at the station
				Train t = train(train);
				if (CaravanTrains.realTrain(level, v.a(), v.b()) && t != null && t.runtime.state != ScheduleRuntime.State.IN_TRANSIT
					&& CreateTrainCompatTests.railway().at(level, train, at)) {
					String away = QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(standing.equals(STATION_A) ? STATION_B : STATION_A));
					arrivedAtTheNamesake(helper, t, away);
					rename(helper, STATION_A, "Station");
					rename(helper, STATION_B, "Station");
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
					sentAt[0] = tick[0];
				} else if (tick[0] > 1600) {
					helper.fail("setup: no village train stood " + where + " in 1600 ticks: "
						+ CreateTrainCompatTests.railway().status(level, train));
				}
				return;
			}
			int since = tick[0] - sentAt[0];
			if (since == 100 && CreateTrainCompatTests.railway().at(level, train, at)) {
				// the wait at a station is 200 ticks: still standing at the namesake means it took it for its stop
				helper.fail("with both stations named 'Station', the train stood " + where + " for its stop at the other station: "
					+ CreateTrainCompatTests.railway().status(level, train));
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
			}
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
					+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, "renamed " + where + ": logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.assertTrue(aboard[0], "renamed " + where + ": the goods arrived without riding the train");
				CreateTrainCompatTests.railway().scrap(level, train);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 3500) {
				helper.fail("with both stations named 'Station' " + where + ", the caravan sent " + since
					+ " ticks ago never arrived (aboard: " + aboard[0] + "): " + s + "; train: "
					+ CreateTrainCompatTests.railway().status(level, train));
			}
		});
	}
}
