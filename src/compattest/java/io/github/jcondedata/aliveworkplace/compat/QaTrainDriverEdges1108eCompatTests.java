package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA lane E, 2026-11-08 (B962's fix, from its text and the Conductor guide page): a village train whose driver is gone
 * gets the village's Conductor or a jobless villager in its seat, and while nobody can take it the line's caravans take
 * the timed trip "with their goods". The corners TrainDriverCompatTests and the 1104e QA classes leave out:
 * <ul>
 * <li>the driver killed while the caravan's 32 logs are already in the train's chest, nobody free: the logs come out of
 * the chest and arrive once by the timed trip (never lost in a train that can't move, never doubled);</li>
 * <li>the new driver killed too: a third villager takes the seat and the train still brings the caravan;</li>
 * <li>only a baby (jobless) and a grown Farmer by the train: neither is sat in the seat (conductorFor: a grown-up
 * Conductor, else a grown-up jobless villager), and the caravan takes the timed trip and arrives.</li>
 * </ul>
 */
public class QaTrainDriverEdges1108eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** The driver killed with the caravan's logs in the train's chest, nobody else by the train. */
	//$ gametest_ticks_batch AREA '4000' '"qa_e1108_driver_killed_in_chest"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1108_driver_killed_in_chest")
	public void theDriverKilledWithTheLogsInTheChestTheyStillArriveOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] tick = {0};
		int[] phase = {0}; // 0 waiting for the train, 1 sent (waiting for the logs in the chest), 2 driver killed
		int[] killedAt = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (phase[0] == 0) {
				if (!CaravanTrains.realTrain(level, v.a(), v.b())) {
					if (tick[0] > 600) {
						fail(helper, level, v, owner, "setup: no village train was put together in 600 ticks");
					}
					return;
				}
				if (driver(helper) == null) {
					if (tick[0] > 900) {
						fail(helper, level, v, owner, "setup: nobody sits in the train's seat");
					}
					return;
				}
				CreateTrainCompatTests.sendAgain(level, v);
				phase[0] = 1;
				return;
			}
			Caravans.Shipment s = shipment(v);
			int inChest = CreateTrainCompatTests.logs(CreateTrainCompatTests.railway().cargo(level, owner));
			// while the caravan is IN_CHEST its record and the train's chest are the same logs: count them once
			int total = CreateTrainCompatTests.chestLogs(level, v) + inChest
				+ (s == null || s.arrives() == CaravanTrains.IN_CHEST ? 0 : CreateTrainCompatTests.logs(s.goods()));
			if (total > 64) {
				fail(helper, level, v, owner, "logs were doubled: " + total + " of 64 (train's chest " + inChest + ", caravan " + s + ")");
				return;
			}
			if (phase[0] == 1) {
				if (s != null && s.arrives() == CaravanTrains.IN_CHEST && inChest == 32) {
					Villager driver = driver(helper);
					if (driver == null) {
						fail(helper, level, v, owner, "setup: the logs are in the train's chest but nobody drives it");
						return;
					}
					driver.kill();
					killedAt[0] = tick[0];
					phase[0] = 2;
				} else if (s == null || tick[0] > 2000) {
					fail(helper, level, v, owner, "setup: the caravan's logs never went into the train's chest: caravan " + s + ", chest " + inChest);
				}
				return;
			}
			int chests = CreateTrainCompatTests.chestLogs(level, v);
			if (s == null && chests == 64) {
				helper.assertTrue(inChest == 0, "logs stayed in the train's chest after the caravan arrived: " + inChest);
				helper.assertTrue(driver(helper) == null, "somebody sits in the seat though nobody was by the train");
				tidy(level, v, owner);
				helper.succeed();
			} else if (s == null && tick[0] - killedAt[0] > 200) {
				fail(helper, level, v, owner, "the caravan left the road but its logs didn't arrive: " + chests + " of 64 in the two chests, train's chest " + inChest);
			} else if (tick[0] == 3900) {
				fail(helper, level, v, owner, "the logs in the train's chest never reached Bramble in " + (tick[0] - killedAt[0]) + " ticks after its driver was killed: caravan "
					+ s + ", chests " + chests + ", train's chest " + inChest + ", train: " + CreateTrainCompatTests.railway().status(level, owner));
			}
		});
	}

	/** The driver killed, the villager who takes his seat killed too: a third takes it and the caravan rides on. */
	//$ gametest_ticks_batch AREA '4000' '"qa_e1108_driver_killed_twice"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1108_driver_killed_twice")
	public void aSecondDriverLostToo_aThirdTakesTheSeat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] tick = {0};
		int[] lost = {0};
		Villager[] waiting = new Villager[1];
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (lost[0] < 2) {
				if (!CaravanTrains.realTrain(level, v.a(), v.b())) {
					if (tick[0] > 600) {
						fail(helper, level, v, owner, "setup: no village train was put together in 600 ticks");
					}
					return;
				}
				Villager driver = driver(helper);
				if (driver == null) {
					if (tick[0] > 900 + 600 * lost[0]) {
						fail(helper, level, v, owner, "nobody took the seat after " + lost[0] + " driver(s) lost, though a villager stood by: " + CreateTrainCompatTests.railway().status(level, owner));
					}
					return;
				}
				if (lost[0] == 1 && !driver.getUUID().equals(waiting[0].getUUID())) {
					fail(helper, level, v, owner, "the seat was taken by someone other than the villager by the train: " + driver);
					return;
				}
				driver.kill();
				lost[0]++;
				waiting[0] = CreateTrainCompatTests.conductor(helper, new BlockPos(9 + lost[0], 2, 16));
				if (lost[0] == 2) {
					CreateTrainCompatTests.sendAgain(level, v);
					Caravans.Shipment s = shipment(v);
					helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
				}
				return;
			}
			Caravans.Shipment s = shipment(v);
			if (s != null && !CaravanTrains.waiting(s.arrives())) {
				fail(helper, level, v, owner, "the caravan took the timed trip though a villager stood by the train after its second driver was lost: "
					+ CreateTrainCompatTests.railway().status(level, owner));
				return;
			}
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = CreateTrainCompatTests.chestLogs(level, v);
				boolean seated = level.getEntity(waiting[0].getUUID()) instanceof Villager w && w.isPassenger();
				tidy(level, v, owner);
				helper.assertTrue(logs == 64, "logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.assertTrue(seated, "the third villager isn't in the seat");
				helper.succeed();
			} else if (tick[0] == 3900) {
				fail(helper, level, v, owner, "the caravan never arrived after two drivers were lost: third villager seated " + waiting[0].isPassenger()
					+ ", caravan " + s + ", train: " + CreateTrainCompatTests.railway().status(level, owner));
			}
		});
	}

	/** Only a baby and a grown Farmer stand by the train when its driver is killed: neither drives it, the caravan walks the timed trip. */
	//$ gametest_ticks_batch AREA '4000' '"qa_e1108_driver_baby_farmer"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1108_driver_baby_farmer")
	public void aBabyOrAFarmerIsNeverSatInTheEmptySeat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		int[] tick = {0};
		int[] killedAt = {-1};
		Villager[] by = new Villager[2];
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (killedAt[0] < 0) {
				if (!CaravanTrains.realTrain(level, v.a(), v.b())) {
					if (tick[0] > 600) {
						fail(helper, level, v, owner, "setup: no village train was put together in 600 ticks");
					}
					return;
				}
				Villager driver = driver(helper);
				if (driver == null) {
					if (tick[0] > 900) {
						fail(helper, level, v, owner, "setup: nobody sits in the train's seat");
					}
					return;
				}
				driver.kill();
				killedAt[0] = tick[0];
				by[0] = CreateTrainCompatTests.conductor(helper, new BlockPos(9, 2, 16));
				by[0].setAge(-24000);
				by[1] = CreateTrainCompatTests.conductor(helper, new BlockPos(10, 2, 16));
				by[1].setVillagerData(by[1].getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
				by[1].setVillagerXp(10); // a Farmer for good, as one who has traded
				CreateTrainCompatTests.sendAgain(level, v);
				Caravans.Shipment s = shipment(v);
				helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
				return;
			}
			Villager seated = driver(helper);
			if (seated != null) {
				fail(helper, level, v, owner, (seated.isBaby() ? "a baby villager" : "a " + seated.getVillagerData().getProfession()) + " was sat in the dead driver's seat");
				return;
			}
			Caravans.Shipment s = shipment(v);
			int chests = CreateTrainCompatTests.chestLogs(level, v);
			if (s == null && chests == 64) {
				helper.assertTrue(by[1].getVillagerData().getProfession() == VillagerProfession.FARMER, "the Farmer lost his job");
				tidy(level, v, owner);
				helper.succeed();
			} else if (chests > 64) {
				fail(helper, level, v, owner, "logs were doubled: " + chests + " of 64");
			} else if (tick[0] == 3900) {
				fail(helper, level, v, owner, "the caravan never arrived with only a baby and a Farmer by the driverless train: caravan " + s + ", chests " + chests
					+ ", train: " + CreateTrainCompatTests.railway().status(level, owner));
			}
		});
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
