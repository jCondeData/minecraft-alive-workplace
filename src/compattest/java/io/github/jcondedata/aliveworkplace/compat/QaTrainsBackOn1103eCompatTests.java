package io.github.jcondedata.aliveworkplace.compat;

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
 * QA (lane E, Create and config, 2026-11-03; 39.7, B359): config {@code villageTrains} switched off for a while and on
 * again, Create installed all along (a server owner trying the switch). With it off, caravans walk (take the timed
 * trip), and the village's train stays on its track. Switched on again, the next caravan between the two villages goes
 * by that train once more and arrives with all its goods; no second train is put together. The line and villages are
 * {@link CreateTrainCompatTests}'s.
 */
public class QaTrainsBackOn1103eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '4000' '"qaTrainsBackOn"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTrainsBackOn")
	public void trainsSwitchedOffAndOnAgainTheNextCaravanRidesTheSameTrain(GameTestHelper helper) {
		offAndOn(helper, true);
	}

	/** The same 200 ticks with the switch left on: the caravan rides the train (a control for the test above). */
	//$ gametest_ticks_batch AREA '4000' '"qaTrainsLeftOn"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTrainsLeftOn")
	public void trainsLeftOnTheCaravanSentAfterTheSameWaitRidesTheTrain(GameTestHelper helper) {
		offAndOn(helper, false);
	}

	private static void offAndOn(GameTestHelper helper, boolean off) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		boolean was = CaravanTrains.ENABLED;
		PartnerShowsCompatTests.after(helper, () -> CaravanTrains.ENABLED = was);
		CaravanTrains.ENABLED = true;
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16)); // a second free villager: a second train would find a seat
		UUID train = CaravanTrains.railwayId(v.a(), v.b());
		int[] tick = {0};
		int[] phase = {0}; // 0 train being made, 1 off, 2 on again and sent
		int[] at = {0};
		boolean[] aboard = {false};
		String[] statusOn = {""};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			switch (phase[0]) {
				case 0 -> {
					if (CaravanTrains.realTrain(level, v.a(), v.b())) {
						CaravanTrains.ENABLED = !off; // the server owner switches village trains off
						phase[0] = 1;
						at[0] = tick[0];
					} else if (tick[0] > 800) {
						helper.fail("setup: no village train was put together in 800 ticks");
					}
				}
				case 1 -> {
					if (tick[0] - at[0] == 200) {
						helper.assertTrue(!off || !CaravanTrains.rides(v.data(), v.a(), v.b()), "with village trains off the two villages are still a railway");
						String before = CreateTrainCompatTests.railway().status(level, train);
						CaravanTrains.ENABLED = true; // and on again
						helper.assertTrue(CaravanTrains.realTrain(level, v.a(), v.b()), "the village's train left its track while trains were off");
						CreateTrainCompatTests.sendAgain(level, v);
						Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
						helper.assertTrue(s != null && s.byTrain(), "switched on again, the caravan doesn't go by the train: " + s);
						statusOn[0] = before;
						phase[0] = 2;
						at[0] = tick[0];
					}
				}
				default -> {
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					if (s != null && CaravanTrains.aboard(s.arrives())) {
						aboard[0] = true;
					}
					if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
						int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
							+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
						helper.assertTrue(logs == 64, "logs were lost or doubled: " + logs + " of 64 in the two chests");
						helper.assertTrue(aboard[0], "the goods arrived without riding the train");
						long assembled = CreateTrainCompatTests.hall(level, v.a()).chronicle().stream().filter(e -> e.text().getContents()
							instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("chronicle.aliveworkplace.train_assembled")).count()
							+ CreateTrainCompatTests.hall(level, v.b()).chronicle().stream().filter(e -> e.text().getContents()
							instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("chronicle.aliveworkplace.train_assembled")).count();
						helper.assertTrue(assembled == 1, "the chronicles say a train was put together " + assembled + " times, not once");
						CreateTrainCompatTests.railway().scrap(level, train);
						CreateTrainCompatTests.tidy(level, v);
						helper.succeed();
					} else if (tick[0] >= 3900) {
						helper.fail((off ? "switched on again" : "left on") + ", the caravan sent " + (tick[0] - at[0]) + " ticks ago never arrived (aboard " + aboard[0] + "): "
							+ s + "; train: " + CreateTrainCompatTests.railway().status(level, train) + " (when switched on again: " + statusOn[0] + ")");
					}
				}
			}
		});
	}
}
