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
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, Create trains, 2026-11-03; 39.7a/39.7b): a piece of the railway between the two villages broken after the
 * village train runs (a player digging through the line, a creeper). The wiki promises a caravan never stuck by a
 * station broken on the way (it walks the rest), and caravans take the timed trip whenever the village's own train
 * can't serve them. Here a block of track halfway between the stations is broken just as the day's caravan is sent:
 * the caravan's goods still reach the other village. With the same piece put back 200 ticks later the goods ride the
 * train and arrive (the control). The line and villages are {@link CreateTrainCompatTests}'s (track along x at z = 12,
 * stations at x = 6 and x = 23).
 */
public class QaTrainTrackBroken1103eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos GAP = new BlockPos(14, 2, 12);

	//$ gametest_ticks_batch AREA '4200' '"qaTrainTrackBroken"'
	@GameTest(template = AREA, timeoutTicks = 4200, batch = "qaTrainTrackBroken")
	public void aPieceOfTrackBrokenBetweenTheStationsStillLetsTheGoodsArrive(GameTestHelper helper) {
		broken(helper, false);
	}

	/** The same piece put back 200 ticks later: the train runs again and the goods arrive (a control). */
	//$ gametest_ticks_batch AREA '4200' '"qaTrainTrackMended"'
	@GameTest(template = AREA, timeoutTicks = 4200, batch = "qaTrainTrackMended")
	public void aPieceOfTrackPutBackTheGoodsArriveByTrain(GameTestHelper helper) {
		broken(helper, true);
	}

	private static void broken(GameTestHelper helper, boolean putBack) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		boolean was = CaravanTrains.ENABLED;
		PartnerShowsCompatTests.after(helper, () -> CaravanTrains.ENABLED = was);
		CaravanTrains.ENABLED = true;
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID train = CaravanTrains.railwayId(v.a(), v.b());
		BlockPos gap = helper.absolutePos(GAP);
		BlockState[] piece = {null};
		int[] tick = {0};
		int[] phase = {0}; // 0 train being made, 1 sent with the track broken
		int[] at = {0};
		boolean[] mended = {false};
		boolean[] aboard = {false};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (phase[0] == 0) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					piece[0] = level.getBlockState(gap);
					helper.assertTrue(!piece[0].isAir(), "setup: no track at the gap " + GAP);
					level.destroyBlock(gap, false); // a player digs through the line
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null, "setup: no caravan was sent to Bramble");
					phase[0] = 1;
					at[0] = tick[0];
				} else if (tick[0] > 800) {
					helper.fail("setup: no village train was put together in 800 ticks");
				}
				return;
			}
			if (putBack && !mended[0] && tick[0] - at[0] == 200) {
				level.setBlockAndUpdate(gap, piece[0]); // and puts it back
				mended[0] = true;
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
			}
			int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
				+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
			if (s == null && logs == 64) {
				helper.assertTrue(!putBack || aboard[0], "with the track put back the goods arrived without riding the train");
				CreateTrainCompatTests.railway().scrap(level, train);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 4100) {
				helper.fail("with the track " + (putBack ? "broken and put back" : "broken between the stations") + ", the caravan sent "
					+ (tick[0] - at[0]) + " ticks ago never arrived (aboard " + aboard[0] + ", " + logs + " of 64 logs in the two chests): " + s
					+ "; train: " + CreateTrainCompatTests.railway().status(level, train) + ", its chest " + CreateTrainCompatTests.railway().cargo(level, train));
			}
		});
	}
}
