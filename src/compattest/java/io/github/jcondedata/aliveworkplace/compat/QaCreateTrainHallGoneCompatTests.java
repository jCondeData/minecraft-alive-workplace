package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane-e-1025-1314) on B214 with the owner's Create port, from its text: "a stationed caravan whose destination goes
 * away gets its goods back once and heads home or is dropped", and the fix's "one waiting at the removed sender's
 * station walks on ... Saves unchanged". CreateTrainCompatTests covers three of the cases; these cover the fourth (the
 * sender's hall gone while the goods ride in the train's chest) and a save and load of the caravan turned off the train.
 * The two villages, track, stations and train are CreateTrainCompatTests' own.
 */
public class QaCreateTrainHallGoneCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** Caravans.Data's saved-data name. */
	private static final String CARAVANS = "aliveworkplace_caravans";

	/**
	 * The sending village's hall taken away while its caravan's 32 logs ride in the train's chest at its station: they
	 * come out of the chest once and the caravan walks on to the other village (nobody runs that train now), where all 32
	 * arrive; the train's chest keeps none and the other village's chronicle says the caravan came.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaCreateTrainSenderGoneInChest"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaCreateTrainSenderGoneInChest")
	public void theSendersHallGoneWhileTheGoodsRideInTheChestWalksThemOn(GameTestHelper helper) {
		inTheChest(helper, false, false);
	}

	/**
	 * The other village's hall taken away while the caravan's logs ride in the train's chest, then the caravans saved and
	 * loaded: the caravan turned home off the train is the same after the load (on foot, coming home, its 32 logs, its
	 * arrival), and once home all 64 logs are in the chests, none left in the train's chest and none twice.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaCreateTrainTurnedHomeSaved"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaCreateTrainTurnedHomeSaved")
	public void aCaravanTurnedHomeOffTheTrainIsTheSameAfterASave(GameTestHelper helper) {
		inTheChest(helper, true, true);
	}

	/**
	 * A caravan from Ashford to Bramble with its logs in the train's chest when a hall is taken away: Bramble's
	 * ({@code destination}) or Ashford's own; {@code reload}: the caravans are saved and loaded just after.
	 */
	private static void inTheChest(GameTestHelper helper, boolean destination, boolean reload) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] sent = {false};
		boolean[] gone = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (gone[0]) {
				return;
			}
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					CreateTrainCompatTests.sendAgain(level, v);
					sent[0] = true;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks");
				}
				return;
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			helper.assertTrue(s != null && s.byTrain() && CaravanTrains.waiting(s.arrives()), "the caravan isn't with the train: " + s);
			if (s.arrives() != CaravanTrains.IN_CHEST) {
				if (tick[0] == 2000) {
					helper.fail("the caravan never boarded with its goods in the chest: " + s);
				}
				return;
			}
			helper.assertTrue(CreateTrainCompatTests.logs(CreateTrainCompatTests.railway().cargo(level, owner)) == 32,
				"the logs aren't in the train's chest: " + CreateTrainCompatTests.railway().cargo(level, owner));
			gone[0] = true;
			BlockPos removed = destination ? v.b() : v.a();
			BlockPos stays = destination ? v.a() : v.b();
			long now = level.getGameTime();
			level.removeBlock(removed, false);
			List<Caravans.Shipment> road = CreateTrainCompatTests.road(v);
			helper.assertTrue(road.size() == 1, "one caravan should be on the road: " + road);
			Caravans.Shipment after = road.get(0);
			helper.assertTrue(after.to().equals(stays) && !after.byTrain() && !CaravanTrains.waiting(after.arrives()) && after.back() == destination
				&& CreateTrainCompatTests.logs(after.goods()) == 32 && after.arrives() > now,
				"the caravan should get off the train and walk to " + (destination ? "Ashford" : "Bramble") + " with its 32 logs: " + after + " at " + now);
			helper.assertTrue(CreateTrainCompatTests.logs(CreateTrainCompatTests.railway().cargo(level, owner)) == 0,
				"logs were left in the train's chest: " + CreateTrainCompatTests.railway().cargo(level, owner));
			CreateTrainCompatTests.Villages w = v;
			if (reload) {
				Caravans.Data loaded = Caravans.Data.load(v.data().save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				level.getDataStorage().set(CARAVANS, loaded);
				w = new CreateTrainCompatTests.Villages(v.a(), v.b(), loaded);
				helper.assertTrue(Caravans.Data.get(level) == loaded, "setup: the loaded caravans aren't the level's");
				List<Caravans.Shipment> back = CreateTrainCompatTests.road(w);
				helper.assertTrue(back.size() == 1, "after the load, caravans on the road: " + back);
				Caravans.Shipment b = back.get(0);
				helper.assertTrue(b.from().equals(after.from()) && b.to().equals(after.to()) && b.arrives() == after.arrives() && b.back() == after.back()
					&& b.byTrain() == after.byTrain() && CreateTrainCompatTests.logs(b.goods()) == 32,
					"the caravan turned off the train changed over the save: " + after + " became " + b);
			}
			w.data().hurry(stays);
			Caravans.round(level, stays, null);
			int logs = CreateTrainCompatTests.chestLogs(level, w);
			// both villages' Storehouses are in each hall's reach here (CreateTrainCompatTests), so the logs may land in either chest
			helper.assertTrue(logs == 64, "the logs should all be in the chests once: " + logs + " of 64, train's chest "
				+ CreateTrainCompatTests.railway().cargo(level, owner) + ", road " + CreateTrainCompatTests.road(w));
			helper.assertTrue(CreateTrainCompatTests.road(w).isEmpty(), "nothing should be left on the road: " + CreateTrainCompatTests.road(w));
			if (!destination) {
				helper.assertTrue(CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.caravan_came"), "Bramble's chronicle doesn't say the caravan came");
			}
			CreateTrainCompatTests.railway().scrap(level, owner);
			CreateTrainCompatTests.tidy(level, w);
			helper.succeed();
		});
	}
}
