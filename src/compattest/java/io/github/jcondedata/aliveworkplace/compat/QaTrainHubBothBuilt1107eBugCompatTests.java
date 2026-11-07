package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane-e-1107-0713, Create trains 39.7a/39.7b/39.7d and B898; the cause of B939): a village between two train
 * neighbors on one straight Create line, both of its trains built by the builders before any Conductor is hired (a
 * train waits for its Conductor: "nobody free to drive it yet"). Once the three Conductors come, each pair's train is
 * put together at its own station from its own blocks and runs: expected one carriage each (the drawn village train is
 * one), neither derailed. B939's derail watcher showed otherwise: the first train put together took the other pair's
 * train blocks standing further along the same track as a second carriage, and the second train then put together on
 * that carriage derailed both.
 */
public class QaTrainHubBothBuilt1107eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** The pair's train blocks all stand at the station of the village that puts it together. */
	private static boolean built(ServerLevel level, QaTrainHub1103eCompatTests.Three v, BlockPos x, BlockPos y) {
		BlockPos hall = CaravanTrains.first(x, y) ? x : y;
		BlockPos other = hall.equals(x) ? y : x;
		BlockPos ours = CaravanTrains.stationFor(level, hall, other, v.data());
		BlockPos theirs = CaravanTrains.stationFor(level, other, hall, v.data());
		return ours != null && theirs != null && CreateTrainCompatTests.railway().built(level, ours, theirs);
	}

	/** How many carriages the village train with this owner has; -1 without one. */
	private static int carriages(UUID owner) {
		for (com.simibubi.create.content.trains.entity.Train train : com.simibubi.create.Create.RAILWAYS.trains.values()) {
			if (owner.equals(train.owner) && !train.invalid) {
				return train.carriages.size();
			}
		}
		return -1;
	}

	//$ gametest_ticks_batch AREA '3000' '"qaTrainHubBothBuilt"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaTrainHubBothBuilt")
	public void bothTrainsBuiltBeforeTheConductorsComeRunAsOneCarriageEachAndNeitherDerails(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		UUID westId = CaravanTrains.railwayId(v.west(), v.hub());
		UUID eastId = CaravanTrains.railwayId(v.east(), v.hub());
		int[] tick = {0};
		int[] hired = {-1};
		String[] derail = {null};
		helper.onEachTick(() -> {
			tick[0]++;
			if (derail[0] == null && hired[0] >= 0) {
				String now = "west train " + QaTrainHub1103eCompatTests.where(westId) + "; east train " + QaTrainHub1103eCompatTests.where(eastId);
				if (now.contains("derailed true")) {
					derail[0] = "tick " + (tick[0] - hired[0]) + " after the Conductors came: " + now;
				}
			}
			if (tick[0] % 10 != 0) {
				return;
			}
			if (hired[0] < 0) {
				// the builders' part: both pairs' train blocks (and the hub's second station) put up, as QaTrainHub1103e's
				// round does, the halls' looks aiming the stations; nobody to drive them yet
				QaTrainHub1103eCompatTests.buildTrain(level, v.data(), v.west(), v.hub());
				QaTrainHub1103eCompatTests.buildTrain(level, v.data(), v.east(), v.hub());
				for (BlockPos h : v.halls()) {
					CaravanTrains.serve(level, h, (VillageHallBlockEntity) level.getBlockEntity(h));
				}
				helper.assertTrue(!CaravanTrains.realTrain(level, v.west(), v.hub()) && !CaravanTrains.realTrain(level, v.east(), v.hub()),
					"setup: a train was put together with no Conductor");
				if (built(level, v, v.west(), v.hub()) && built(level, v, v.east(), v.hub())) {
					hired[0] = tick[0];
					for (BlockPos at : List.of(new BlockPos(6, 2, 16), new BlockPos(15, 2, 16), new BlockPos(23, 2, 16))) {
						CreateTrainCompatTests.conductor(helper, at);
					}
				} else if (tick[0] > 1500) {
					helper.fail("setup: both pairs' train blocks weren't standing in 1500 ticks: west " + built(level, v, v.west(), v.hub())
						+ ", east " + built(level, v, v.east(), v.hub()));
				}
				return;
			}
			QaTrainHub1103eCompatTests.round(level, v);
			if (derail[0] != null) {
				helper.fail("the hub's trains derailed, " + derail[0] + " (carriages: west " + carriages(westId) + ", east " + carriages(eastId)
					+ "; the village train is one)");
			}
			boolean both = CaravanTrains.realTrain(level, v.west(), v.hub()) && CaravanTrains.realTrain(level, v.east(), v.hub());
			if (both && tick[0] - hired[0] >= 600) {
				helper.assertTrue(carriages(westId) == 1 && carriages(eastId) == 1, "a village train took the other's blocks: "
					+ carriages(westId) + " carriages in the west train, " + carriages(eastId) + " in the east; "
					+ QaTrainHub1103eCompatTests.trains(level, v));
				QaTrainHub1103eCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] - hired[0] > 1200) {
				helper.fail("the hub's two trains weren't both running 1200 ticks after the Conductors came: "
					+ QaTrainHub1103eCompatTests.trains(level, v) + " (carriages: west " + carriages(westId) + ", east " + carriages(eastId) + ")");
			}
		});
	}
}
