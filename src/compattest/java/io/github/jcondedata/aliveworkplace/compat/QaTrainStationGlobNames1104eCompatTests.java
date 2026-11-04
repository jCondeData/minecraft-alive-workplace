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
 * QA (lane E, Create, 2026-11-04; B900, 39.7a): B900's spec says the village train keeps running between its two
 * villages' stations "whatever a player calls them". A schedule's stop doesn't hold a name but a pattern (Create reads
 * its text as a glob: {@code *}, {@code ?}, {@code [...]} and {@code {a,b}} mean something), so a station a player
 * names with brackets or braces, as many do ("Bramble [East]", "Platform {2}"), or with a bracket left open ("Main ["),
 * isn't found by a stop that copies the name as it is. In each, Ashford's 32 logs for Bramble ride the train and arrive,
 * none lost or doubled. The line and the two villages are {@link CreateTrainCompatTests}'s, the run is
 * {@link QaTrainStationSameName1104eCompatTests}'s.
 */
public class QaTrainStationGlobNames1104eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);

	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationBrackets"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationBrackets")
	public void aStationNamedWithBracketsStillGetsTheCaravan(GameTestHelper helper) {
		run(helper, STATION_B, "Bramble [East]");
	}

	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationBraces"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationBraces")
	public void aStationNamedWithBracesStillGetsTheCaravan(GameTestHelper helper) {
		run(helper, STATION_B, "Platform {2}");
	}

	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationOpenBracket"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationOpenBracket")
	public void aStationNamedWithAnOpenBracketStillGetsTheCaravan(GameTestHelper helper) {
		run(helper, STATION_B, "Main [");
	}

	/** The sending village's own station renamed: the train has to find its way home too. */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainHomeStationBrackets"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainHomeStationBrackets")
	public void theSendingStationNamedWithBracketsStillSendsTheCaravan(GameTestHelper helper) {
		run(helper, STATION_A, "Ashford [West]");
	}

	private static void rename(GameTestHelper helper, BlockPos which, String name) {
		StationBlockEntity be = (StationBlockEntity) helper.getBlockEntity(which);
		helper.assertTrue(be.getStation() != null, "setup: the station isn't a station Create knows");
		be.updateName(name); // what Create's station screen does when a player types a name
	}

	private static void run(GameTestHelper helper, BlockPos which, String name) {
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
					rename(helper, which, name);
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
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
			}
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
					+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, "station '" + name + "': logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.assertTrue(aboard[0], "station '" + name + "': the goods arrived without riding the train");
				CreateTrainCompatTests.railway().scrap(level, train);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 3500) {
				helper.fail("with a station named '" + name + "', the caravan sent " + since + " ticks ago never arrived (aboard: " + aboard[0]
					+ "): " + s + "; train: " + CreateTrainCompatTests.railway().status(level, train) + "; stations '"
					+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_A)) + "' and '"
					+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_B)) + "'");
			}
		});
	}
}
