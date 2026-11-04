package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, Create, 2026-11-04; B900 verification, 39.7a): B900's spec says the village train keeps running between
 * its two villages' stations "whatever a player calls them". Create's schedules find stations by name, so the names a
 * player is likeliest to pick next are the hard ones: the same plain name on both stations ("Station"), the two
 * villages' names swapped, and a station renamed twice (the second after the train already followed the first). In
 * each, Ashford's 32 logs for Bramble ride the train and arrive, none lost or doubled. The line and the two villages
 * are {@link CreateTrainCompatTests}'s.
 */
public class QaTrainStationSameName1104eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);

	/** A player names both stations "Station" (as many do with every station on a map). */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationsSameName"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationsSameName")
	public void bothStationsGivenTheSameNameStillCarryTheCaravan(GameTestHelper helper) {
		run(helper, "both named 'Station'", (h, step) -> {
			if (step == 0) {
				rename(h, STATION_A, "Station");
				rename(h, STATION_B, "Station");
			}
		});
	}

	/** A player swaps the two villages' station names. */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationsSwapped"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationsSwapped")
	public void theTwoStationsNamesSwappedStillCarryTheCaravan(GameTestHelper helper) {
		run(helper, "names swapped", (h, step) -> {
			if (step == 0) {
				ServerLevel level = h.getLevel();
				String a = QaTrainStationNames1102CompatTests.name(level, h.absolutePos(STATION_A));
				String b = QaTrainStationNames1102CompatTests.name(level, h.absolutePos(STATION_B));
				h.assertTrue(a != null && b != null && !a.equals(b), "setup: the two stations' names: '" + a + "' and '" + b + "'");
				rename(h, STATION_A, b);
				rename(h, STATION_B, a);
			}
		});
	}

	/** The other village's station renamed 'Harbor', then, once the train has followed that, renamed 'Port'. */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationRenamedTwice"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationRenamedTwice")
	public void aStationRenamedTwiceStillCarriesTheCaravan(GameTestHelper helper) {
		run(helper, "renamed 'Harbor' then 'Port'", (h, step) -> {
			if (step == 0) {
				rename(h, STATION_B, "Harbor");
			} else if (step == 200) {
				rename(h, STATION_B, "Port");
			}
		});
	}

	private static void rename(GameTestHelper helper, BlockPos which, String name) {
		StationBlockEntity be = (StationBlockEntity) helper.getBlockEntity(which);
		helper.assertTrue(be.getStation() != null, "setup: the station isn't a station Create knows");
		be.updateName(name); // what Create's station screen does when a player types a name
	}

	/** {@code renames} is called with the ticks since the train was put together (0 first, then every tick). */
	private static void run(GameTestHelper helper, String what, BiConsumer<GameTestHelper, Integer> renames) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID train = CaravanTrains.railwayId(v.a(), v.b());
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
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					renames.accept(helper, 0);
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
					sentAt[0] = tick[0];
				} else if (tick[0] > 800) {
					helper.fail("setup: no village train was put together in 800 ticks");
				}
				return;
			}
			int since = tick[0] - sentAt[0];
			if (since > 0) {
				renames.accept(helper, since);
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
			}
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
					+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, what + ": logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.assertTrue(aboard[0], what + ": the goods arrived without riding the train");
				CreateTrainCompatTests.railway().scrap(level, train);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 3500) {
				helper.fail("with the stations " + what + ", the caravan sent " + since + " ticks ago never arrived (aboard: " + aboard[0]
					+ "): " + s + "; train: " + CreateTrainCompatTests.railway().status(level, train) + "; stations '"
					+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_A)) + "' and '"
					+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_B)) + "'");
			}
		});
	}
}
