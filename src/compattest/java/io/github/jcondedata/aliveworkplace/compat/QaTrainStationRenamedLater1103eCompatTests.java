package io.github.jcondedata.aliveworkplace.compat;

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
 * QA (lane E, Create, 2026-11-03; 39.7a, B800): a station a player renames by hand keeps his name (B800's fix), and
 * players do rename stations once a line runs (Create's station screen, for their own maps and trains). The village
 * train already running between the two villages must still carry the caravans after one of its stations is renamed:
 * Ashford's 32 logs for Bramble, sent after a player renamed Bramble's station 'Harbor', arrive at Bramble by the
 * train. The line and the two villages are {@link CreateTrainCompatTests}'s.
 */
public class QaTrainStationRenamedLater1103eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);

	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationRenamedLater"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationRenamedLater")
	public void aCaravanSentAfterAPlayerRenamedTheOtherStationStillArrivesByTrain(GameTestHelper helper) {
		renamed(helper, STATION_B);
	}

	/** The same with the sending village's own station renamed: the train still comes back for the goods and takes them. */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainHomeStationRenamedLater"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainHomeStationRenamedLater")
	public void aCaravanSentAfterAPlayerRenamedItsOwnStationStillArrivesByTrain(GameTestHelper helper) {
		renamed(helper, STATION_A);
	}

	private static void renamed(GameTestHelper helper, BlockPos which) {
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
					StationBlockEntity be = (StationBlockEntity) helper.getBlockEntity(which);
					helper.assertTrue(be.getStation() != null, "setup: the station isn't a station Create knows");
					be.updateName("Harbor"); // what Create's station screen does when a player types a name
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
					sentAt[0] = tick[0];
				} else if (tick[0] > 800) {
					helper.fail("setup: no village train was put together in 800 ticks");
				}
				return;
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
			}
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
					+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, "logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.assertTrue(aboard[0], "the goods arrived without riding the train");
				helper.assertTrue("Harbor".equals(QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(which))),
					"the village wrote over the player's station name: " + QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(which)));
				CreateTrainCompatTests.railway().scrap(level, train);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 3500) {
				helper.fail("after a player renamed the " + (which.equals(STATION_B) ? "other village's" : "sending village's") + " station 'Harbor', "
					+ "the caravan sent " + (tick[0] - sentAt[0]) + " ticks ago never arrived (aboard: " + aboard[0] + "): " + s + "; train: "
					+ CreateTrainCompatTests.railway().status(level, train) + "; stations '" + QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_A))
					+ "' and '" + QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_B)) + "'");
			}
		});
	}
}
