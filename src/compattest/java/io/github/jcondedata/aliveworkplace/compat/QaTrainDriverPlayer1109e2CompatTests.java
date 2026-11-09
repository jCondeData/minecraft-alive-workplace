package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * QA lane E, 2026-11-09 (B962, Create trains): when the village train's driver is gone, the village sits a villager
 * from by the train in the empty seat and puts off only riders in no seat (the zombie villager the driver became).
 * A player is never put off. Here the driver is killed and a player sits down in his seat at once, with a grown
 * villager standing by the train: the player keeps the seat for the whole trip (the villager doesn't take it from him),
 * and the day's caravan still reaches the other village with all 64 logs (by the timed trip: Create never counts a
 * player as the train's driver). Written for the survivor of lane-e-1109-1321's local mutation of CreateTrains.seat
 * ('&& to ||' on the rider check, which calls stopRiding on every rider, players included): that mutant still survives
 * here, because Create keeps a seated player in the carriage when the server calls stopRiding on him (he stays among
 * its riders and in its seat mapping, seen with a debug line on every seat call), so with the drawing's one seat the
 * mutant is equivalent in play.
 */
public class QaTrainDriverPlayer1109e2CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '4000' '"qa_e1109_driver_player_seat"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_e1109_driver_player_seat")
	public void aPlayerWhoSitsInTheDeadDriversSeatIsNeverPutOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		CreateTrainCompatTests.Villages v = CreateTrainCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		ServerPlayer[] player = new ServerPlayer[1];
		Villager[] by = new Villager[1];
		AbstractContraptionEntity[] carriage = new AbstractContraptionEntity[1];
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CreateTrainCompatTests.buildTrain(level, v);
				CaravanTrains.serve(level, v.a(), CreateTrainCompatTests.hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), CreateTrainCompatTests.hall(level, v.b()));
			}
			if (player[0] == null) {
				if (!CaravanTrains.realTrain(level, v.a(), v.b())) {
					if (tick[0] > 600) {
						fail(helper, level, v, owner, null, null, "setup: no village train was put together in 600 ticks");
					}
					return;
				}
				Villager driver = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), Villager::isPassenger).stream().findFirst().orElse(null);
				if (driver == null) {
					if (tick[0] > 900) {
						fail(helper, level, v, owner, null, null, "setup: nobody sits in the train's seat");
					}
					return;
				}
				if (!(driver.getVehicle() instanceof AbstractContraptionEntity ride)) {
					fail(helper, level, v, owner, null, null, "setup: the driver sits in something other than the train: " + driver.getVehicle());
					return;
				}
				int seat = ride.getContraption().getSeatMapping().getOrDefault(driver.getUUID(), -1);
				driver.kill();
				ServerPlayer p = helper.makeMockServerPlayerInLevel();
				p.setGameMode(GameType.SURVIVAL);
				p.teleportTo(driver.getX(), driver.getY(), driver.getZ());
				ride.addSittingPassenger(p, seat);
				player[0] = p;
				carriage[0] = ride;
				by[0] = CreateTrainCompatTests.conductor(helper, new BlockPos(9, 2, 16));
				if (seat < 0 || p.getVehicle() != ride) {
					fail(helper, level, v, owner, p, by[0], "setup: the player couldn't sit in the driver's seat " + seat);
					return;
				}
				CreateTrainCompatTests.sendAgain(level, v);
				Caravans.Shipment s = shipment(v);
				helper.assertTrue(s != null && s.byTrain(), "setup: the caravan doesn't go by train: " + s);
				return;
			}
			if (player[0].getVehicle() != carriage[0]) {
				fail(helper, level, v, owner, player[0], by[0], "the player sitting in the train's seat was put off it (riding "
					+ player[0].getVehicle() + ") on tick " + tick[0] + ", villager by the train seated " + by[0].isPassenger());
				return;
			}
			if (by[0].isPassenger()) {
				fail(helper, level, v, owner, player[0], by[0], "the villager by the train sat down in a train whose seat a player holds: " + by[0].getVehicle());
				return;
			}
			Caravans.Shipment s = shipment(v);
			if (s == null && CreateTrainCompatTests.said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = CreateTrainCompatTests.chestLogs(level, v);
				tidy(level, v, owner, player[0], by[0]);
				helper.assertTrue(logs == 64, "logs were lost or doubled: " + logs + " of 64 in the two chests");
				helper.succeed();
			} else if (tick[0] == 3900) {
				fail(helper, level, v, owner, player[0], by[0], "the caravan never arrived with a player in the train's seat: caravan " + s
					+ ", train: " + CreateTrainCompatTests.railway().status(level, owner));
			}
		});
	}

	private static Caravans.Shipment shipment(CreateTrainCompatTests.Villages v) {
		return CreateTrainCompatTests.road(v).stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
	}

	private static void tidy(ServerLevel level, CreateTrainCompatTests.Villages v, UUID owner, ServerPlayer player, Villager by) {
		if (player != null) {
			player.stopRiding();
			player.discard();
		}
		if (by != null) {
			by.stopRiding();
			by.discard();
		}
		CreateTrainCompatTests.railway().scrap(level, owner);
		CreateTrainCompatTests.tidy(level, v);
	}

	private static void fail(GameTestHelper helper, ServerLevel level, CreateTrainCompatTests.Villages v, UUID owner, ServerPlayer player, Villager by, String why) {
		tidy(level, v, owner, player, by);
		helper.fail(why);
	}
}
