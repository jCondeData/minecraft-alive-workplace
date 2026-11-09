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
import net.minecraft.world.entity.npc.Villager;

/**
 * QA lane E, 2026-11-09 (B962's fix, saves): a village train whose driver is gone gets a new villager in its seat, and while
 * nobody can take it the caravans take the timed trip "with their goods"; the time the train has stood without a driver is
 * not saved ("a restart waits again"). What a server restart in the middle does, from the spec, with the caravans' saved
 * data written and read back and put in the world in place of the old (as the server does on a restart):
 * <ul>
 * <li>saved right after the driver was killed, while the caravan's 32 logs are still in the train's chest
 * ({@code IN_CHEST}, the record and the chest the same logs): after the restart they come out for the timed trip and
 * arrive once, never doubled by the record read back, never lost;</li>
 * <li>saved while the driverless train stands and the caravan waits for it, a villager coming by the train only after the
 * restart: he takes the seat and the caravan still rides the train in (never stuck waiting for good).</li>
 * </ul>
 */
public class QaTrainDriverSaves1109eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String NAME = "aliveworkplace_caravans";

	/** The driver killed with the logs in the train's chest, the server restarted at once: the logs arrive once. */
	//$ gametest_ticks_batch AREA '4000' '"qa_e1109_driver_saved_in_chest"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1109_driver_saved_in_chest")
	public void savedWithTheLogsInTheChestOfATrainWithoutADriverTheyArriveOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages[] v = {CreateTrainCompatTests.villages(helper)};
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v[0].a(), v[0].b());
		int[] tick = {0};
		int[] phase = {0}; // 0 waiting for the train, 1 sent (waiting for the logs in the chest), 2 killed and restarted
		int[] restartedAt = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v[0]);
				CaravanTrains.serve(level, v[0].a(), CreateTrainCompatTests.hall(level, v[0].a()));
				CaravanTrains.serve(level, v[0].b(), CreateTrainCompatTests.hall(level, v[0].b()));
			}
			if (phase[0] == 0) {
				if (!CaravanTrains.realTrain(level, v[0].a(), v[0].b())) {
					if (tick[0] > 600) {
						fail(helper, level, v[0], owner, "setup: no village train was put together in 600 ticks");
					}
					return;
				}
				if (driver(helper) == null) {
					if (tick[0] > 900) {
						fail(helper, level, v[0], owner, "setup: nobody sits in the train's seat");
					}
					return;
				}
				CreateTrainCompatTests.sendAgain(level, v[0]);
				phase[0] = 1;
				return;
			}
			Caravans.Shipment s = shipment(v[0]);
			int inChest = CreateTrainCompatTests.logs(CreateTrainCompatTests.railway().cargo(level, owner));
			// while the caravan is IN_CHEST its record and the train's chest are the same logs: count them once
			int total = CreateTrainCompatTests.chestLogs(level, v[0]) + inChest
				+ (s == null || s.arrives() == CaravanTrains.IN_CHEST ? 0 : CreateTrainCompatTests.logs(s.goods()));
			if (total > 64) {
				fail(helper, level, v[0], owner, "logs were doubled after the restart: " + total + " of 64 (train's chest " + inChest + ", caravan " + s + ")");
				return;
			}
			if (phase[0] == 1) {
				if (s != null && s.arrives() == CaravanTrains.IN_CHEST && inChest == 32) {
					Villager driver = driver(helper);
					if (driver == null) {
						fail(helper, level, v[0], owner, "setup: the logs are in the train's chest but nobody drives it");
						return;
					}
					driver.kill();
					v[0] = restart(helper, level, v[0]);
					Caravans.Shipment back = shipment(v[0]);
					if (back == null || back.arrives() != CaravanTrains.IN_CHEST || !back.byTrain() || CreateTrainCompatTests.logs(back.goods()) != 32) {
						fail(helper, level, v[0], owner, "the caravan in the train's chest changed over the save: " + s + " came back as " + back);
						return;
					}
					restartedAt[0] = tick[0];
					phase[0] = 2;
				} else if (s == null || tick[0] > 2000) {
					fail(helper, level, v[0], owner, "setup: the caravan's logs never went into the train's chest: caravan " + s + ", chest " + inChest);
				}
				return;
			}
			int chests = CreateTrainCompatTests.chestLogs(level, v[0]);
			if (s == null && chests == 64) {
				helper.assertTrue(inChest == 0, "logs stayed in the train's chest after the caravan arrived: " + inChest);
				tidy(level, v[0], owner);
				helper.succeed();
			} else if (s == null && tick[0] - restartedAt[0] > 200) {
				fail(helper, level, v[0], owner, "the caravan left the road but its logs didn't arrive: " + chests + " of 64 in the two chests, train's chest " + inChest);
			} else if (tick[0] == 3900) {
				fail(helper, level, v[0], owner, "the logs in the chest of the driverless train never reached Bramble in " + (tick[0] - restartedAt[0])
					+ " ticks after the restart: caravan " + s + ", chests " + chests + ", train's chest " + inChest + ", train: "
					+ CreateTrainCompatTests.railway().status(level, owner));
			}
		});
	}

	/** The driverless train saved with the caravan waiting for it; a villager comes by after the restart and drives it in. */
	//$ gametest_ticks_batch AREA '4000' '"qa_e1109_driver_saved_waiting"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1109_driver_saved_waiting")
	public void aVillagerWhoComesByAfterTheRestartTakesTheSeatAndTheCaravanRides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages[] v = {CreateTrainCompatTests.villages(helper)};
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v[0].a(), v[0].b());
		int[] tick = {0};
		int[] phase = {0}; // 0 waiting for the train and its driver, 1 driver killed, caravan sent, 2 restarted, villager by the train
		int[] killedAt = {-1};
		Villager[] waiting = new Villager[1];
		boolean[] aboard = {false};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v[0]);
				CaravanTrains.serve(level, v[0].a(), CreateTrainCompatTests.hall(level, v[0].a()));
				CaravanTrains.serve(level, v[0].b(), CreateTrainCompatTests.hall(level, v[0].b()));
			}
			if (phase[0] == 0) {
				if (!CaravanTrains.realTrain(level, v[0].a(), v[0].b())) {
					if (tick[0] > 600) {
						fail(helper, level, v[0], owner, "setup: no village train was put together in 600 ticks");
					}
					return;
				}
				Villager driver = driver(helper);
				if (driver == null) {
					if (tick[0] > 900) {
						fail(helper, level, v[0], owner, "setup: nobody sits in the train's seat");
					}
					return;
				}
				driver.kill();
				killedAt[0] = tick[0];
				CreateTrainCompatTests.sendAgain(level, v[0]);
				Caravans.Shipment s = shipment(v[0]);
				helper.assertTrue(s != null && s.byTrain() && CaravanTrains.waiting(s.arrives()), "setup: the caravan doesn't wait for the train: " + s);
				phase[0] = 1;
				return;
			}
			if (phase[0] == 1) {
				// the train stands without a driver a while (well under the 100 ticks after which its caravans walk), then the restart
				if (tick[0] - killedAt[0] < 30) {
					return;
				}
				v[0] = restart(helper, level, v[0]);
				Caravans.Shipment back = shipment(v[0]);
				if (back == null || !back.byTrain() || !CaravanTrains.waiting(back.arrives()) || CreateTrainCompatTests.logs(back.goods()) != 32) {
					fail(helper, level, v[0], owner, "the caravan waiting for the driverless train changed over the save: " + back);
					return;
				}
				waiting[0] = CreateTrainCompatTests.conductor(helper, new BlockPos(9, 2, 16));
				phase[0] = 2;
				return;
			}
			Caravans.Shipment s = shipment(v[0]);
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
			}
			if (s != null && !s.byTrain()) {
				fail(helper, level, v[0], owner, "the caravan stopped going by train: " + s);
				return;
			}
			if (s == null && CreateTrainCompatTests.said(level, v[0].b(), "chronicle.aliveworkplace.train_came")) {
				int logs = CreateTrainCompatTests.chestLogs(level, v[0]);
				boolean seated = level.getEntity(waiting[0].getUUID()) instanceof Villager w && w.isAlive() && w.isPassenger();
				tidy(level, v[0], owner);
				helper.assertTrue(logs == 64, "logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.assertTrue(aboard[0], "the caravan never rode the train after the restart");
				helper.assertTrue(seated, "the villager by the train isn't in its seat");
				helper.succeed();
			} else if (tick[0] == 3900) {
				fail(helper, level, v[0], owner, "the caravan never arrived after the restart though a villager stood by the driverless train: seated "
					+ waiting[0].isPassenger() + ", caravan " + s + ", chests " + CreateTrainCompatTests.chestLogs(level, v[0]) + ", train: "
					+ CreateTrainCompatTests.railway().status(level, owner));
			}
		});
	}

	/**
	 * A server restart for the caravans: their saved data written, read back and put in the world in place of the old one,
	 * as the server reads it from disk. Create's own trains keep standing as they were (they live in Create's save).
	 */
	private static CreateTrainCompatTests.Villages restart(GameTestHelper helper, ServerLevel level, CreateTrainCompatTests.Villages v) {
		Caravans.Data old = Caravans.Data.get(level);
		helper.assertTrue(old == v.data(), "setup: the villages' caravan data isn't the world's");
		Caravans.Data loaded = Caravans.Data.load(old.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(NAME, loaded);
		helper.assertTrue(Caravans.Data.get(level) == loaded, "setup: the reloaded caravan data didn't take the old one's place");
		return new CreateTrainCompatTests.Villages(v.a(), v.b(), loaded);
	}

	/** The living villager sitting in the train (riding it), or null. */
	private static Villager driver(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), w -> w.isAlive() && w.isPassenger()).stream().findFirst().orElse(null);
	}

	private static Caravans.Shipment shipment(CreateTrainCompatTests.Villages v) {
		return v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
	}

	private static void fail(GameTestHelper helper, ServerLevel level, CreateTrainCompatTests.Villages v, UUID owner, String why) {
		tidy(level, v, owner);
		helper.fail(why);
	}

	private static void tidy(ServerLevel level, CreateTrainCompatTests.Villages v, UUID owner) {
		for (Villager villager : List.copyOf(level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(v.a()).inflate(40)))) {
			villager.stopRiding();
			villager.discard();
		}
		CreateTrainCompatTests.railway().scrap(level, owner);
		CreateTrainCompatTests.tidy(level, v);
	}
}
