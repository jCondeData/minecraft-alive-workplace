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
 * QA (lane E, Create, 2026-11-04; B900, 39.7a): the other side of B938. A schedule's stop is read as a glob, yet most
 * of the odd characters a player puts in a station's name either mean nothing to it or match themselves: parentheses,
 * dots, dollars and carets (regex characters Create escapes), a question mark (one character, itself included) and a
 * star (any characters, its own included). A station named with those still gets the village train, so B938's fix
 * (escaping the name in the stop) must keep them working. Each run is {@link QaTrainStationSameName1104eCompatTests}'s:
 * the 32 logs ride the train and arrive, none lost or doubled.
 */
public class QaTrainStationOddNames1104eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos STATION_B = new BlockPos(23, 2, 13);
	private static final BlockPos STATION_A = new BlockPos(6, 2, 13);

	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationParens"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationParens")
	public void aStationNamedWithParenthesesAndDotsGetsTheCaravan(GameTestHelper helper) {
		run(helper, STATION_B, "St. Bramble (East) $1^");
	}

	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationQuestion"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationQuestion")
	public void aStationNamedWithAQuestionMarkGetsTheCaravan(GameTestHelper helper) {
		run(helper, STATION_B, "Bramble?");
	}

	//$ gametest_ticks_batch AREA '3600' '"qaTrainStationStar"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainStationStar")
	public void aStationNamedWithAStarGetsTheCaravan(GameTestHelper helper) {
		run(helper, STATION_B, "Platform*");
	}

	/** The village itself named with parentheses (a name tag on its hall): its station takes the name. */
	//$ gametest_ticks_batch AREA '3600' '"qaTrainVillageParens"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaTrainVillageParens")
	public void aVillageNamedWithParenthesesGetsTheCaravan(GameTestHelper helper) {
		run(helper, null, "Bramble (East)");
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
		if (which == null) {
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(v.b())).setCustomName(net.minecraft.network.chat.Component.literal(name));
		}
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
					if (which != null) {
						rename(helper, which, name);
					} else {
						String b = QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_B));
						helper.assertTrue(name.equals(b), "setup: the village's station isn't named after it: '" + b + "'");
					}
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
