package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * B962: a village train whose driver is gone. With a villager free by the train, he takes the empty seat and the train
 * runs on, its caravan riding it all the way (never sent on the timed trip). The QA classes beside this one
 * (QaTrainDriverLost1104eBugCompatTests, QaTrainDriverZombified1104e2BugCompatTests) cover the train with nobody free,
 * whose caravans take the timed trip.
 */
public class TrainDriverCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '3000' '"b962_train_new_driver"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "b962_train_new_driver")
	public void aVillagerByTheTrainTakesTheSeatOfItsDeadDriver(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		Villager[] next = new Villager[1];
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (next[0] == null) {
				if (!CaravanTrains.realTrain(level, v.a(), v.b())) {
					helper.assertTrue(tick[0] < 600, "setup: no village train was put together in 600 ticks");
					return;
				}
				Villager driver = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), Villager::isPassenger).stream().findFirst().orElse(null);
				if (driver == null) {
					helper.assertTrue(tick[0] < 900, "setup: nobody sits in the train's seat");
					return;
				}
				driver.kill();
				next[0] = CreateTrainCompatTests.conductor(helper, new BlockPos(9, 2, 16));
				CreateTrainCompatTests.sendAgain(level, v);
				Caravans.Shipment s = CreateTrainCompatTests.road(v).stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
				helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
				return;
			}
			Caravans.Shipment s = CreateTrainCompatTests.road(v).stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && !CaravanTrains.waiting(s.arrives())) {
				finish(level, v, owner, next[0]);
				helper.fail("the caravan took the timed trip though a villager stood by the train: " + CreateTrainCompatTests.railway().status(level, owner));
				return;
			}
			// both villages' storehouses are within each hall's reach in this layout, so the logs may go into either chest
			// (as CreateTrainCompatTests counts them): the caravan came in at Bramble (its chronicle says so), none lost
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = CreateTrainCompatTests.chestLogs(level, v);
				boolean seated = next[0].isPassenger();
				boolean driven = CreateTrainCompatTests.railway().driven(level, owner);
				finish(level, v, owner, next[0]);
				helper.assertTrue(logs == 64, "logs were lost: " + logs + " of 64 in the two chests");
				helper.assertTrue(seated, "the villager by the train isn't in its seat");
				helper.assertTrue(driven, "the train has no driver though it brought the caravan");
				helper.succeed();
			} else if (tick[0] == 2900) {
				String why = "the caravan never arrived: villager seated " + next[0].isPassenger() + ", train: " + CreateTrainCompatTests.railway().status(level, owner);
				finish(level, v, owner, next[0]);
				helper.fail(why);
			}
		});
	}

	private static void finish(ServerLevel level, CreateTrainCompatTests.Villages v, UUID owner, Villager villager) {
		villager.stopRiding();
		villager.discard();
		CreateTrainCompatTests.railway().scrap(level, owner);
		CreateTrainCompatTests.tidy(level, v);
	}
}
