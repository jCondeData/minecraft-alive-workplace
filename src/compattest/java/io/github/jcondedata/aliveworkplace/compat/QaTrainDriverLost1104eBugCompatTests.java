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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * QA lane E, 2026-11-04 (Create trains 39.7a/39.7d, from the guide): a caravan by train is "never lost on the way"
 * (CaravanTrains), and a Create train runs only with a driver in its seat. Here the villager the village sat in the new
 * train's seat dies (killed, or struck by lightning into a witch) before the day's caravan is sent: the caravan's 32 logs
 * must still reach the other village (by the train with a new driver, or the timed trip), not wait for ever on a train
 * that can no longer move. Both fail on main (filed with them): register this class with the fix.
 */
public class QaTrainDriverLost1104eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** The driver killed in the seat. */
	//$ gametest_ticks_batch AREA '4000' '"qa_e1104_train_driver_killed"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1104_train_driver_killed")
	public void aCaravanStillArrivesWhenTheTrainsDriverIsKilled(GameTestHelper helper) {
		run(helper, false);
	}

	/** The driver struck by lightning in the seat (a witch now). */
	//$ gametest_ticks_batch AREA '4000' '"qa_e1104_train_driver_struck"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1104_train_driver_struck")
	public void aCaravanStillArrivesWhenTheTrainsDriverIsStruck(GameTestHelper helper) {
		run(helper, true);
	}

	private static void run(GameTestHelper helper, boolean struck) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		Container chestA = (Container) level.getBlockEntity(v.a().east(3).north(2));
		Container chestB = (Container) level.getBlockEntity(v.b().east(3).north(2));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] sent = {false};
		int[] tick = {0};
		int[] lostAt = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					// Create's assembly puts the carriage (and who sits in it) away and takes it out again: the one in the seat now
					Villager driver = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), Villager::isPassenger).stream().findFirst().orElse(null);
					if (driver == null) {
						helper.assertTrue(tick[0] < 900, "setup: nobody sits in the train's seat: " + CreateTrainCompatTests.railway().status(level, owner));
						return; // not taken out yet
					}
					if (struck) {
						QaMechanicPartStruck1104eCompatTests.strike(level, driver);
					} else {
						driver.kill();
					}
					helper.assertTrue(!driver.isAlive() || driver.isRemoved(), "setup: the driver is still there");
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
					sent[0] = true;
					lostAt[0] = tick[0];
				} else if (tick[0] > 600) {
					helper.fail("setup: no village train was put together in 600 ticks");
				}
				return;
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			int logs = chestA.countItem(Items.OAK_LOG) + chestB.countItem(Items.OAK_LOG);
			// both villages' storehouses are within each hall's reach in this layout, so the logs may go into either chest
			// (as CreateTrainCompatTests counts them): the caravan came in at Bramble (its chronicle says so), none lost
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				helper.assertTrue(logs == 64, "logs were lost: " + logs + " of 64 in the two chests");
				CreateTrainCompatTests.railway().scrap(level, owner);
				CreateTrainCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] == 3900) {
				String why = "the caravan's 32 logs never reached Bramble in " + (tick[0] - lostAt[0]) + " ticks after the train's driver "
					+ (struck ? "was struck into a witch" : "was killed") + ": caravan " + (s == null ? "off the road" : (CaravanTrains.aboard(s.arrives())
					? "aboard" : CaravanTrains.waiting(s.arrives()) ? "waiting at the station" : "arriving at " + s.arrives())) + ", Bramble's chest "
					+ chestB.countItem(Items.OAK_LOG) + ", train: " + CreateTrainCompatTests.railway().status(level, owner);
				CreateTrainCompatTests.railway().scrap(level, owner);
				CreateTrainCompatTests.tidy(level, v);
				helper.fail(why);
			}
		});
	}
}
