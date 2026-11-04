package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.compat.create.CreateTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B894: a village train's Create schedule goes by station name and Create stops at the nearest station of that name, so
 * stations on one network never share a name a village gave them. Beside QA's {@link QaTrainSameNames1103eCompatTests}
 * (a new world): a world from before the fix, whose two Oakford stations and the train going to 'Oakford' are already
 * saved, has them told apart and its train sent on to the right one; a station a player named after the village keeps
 * its name and the village's takes another; and a server restarted between the round that aimed the stations and the
 * one that names them still names them apart. The line and villages are QA's.
 */
public class TrainStationNamesApartCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** How far the old world's setup got (for the failure message). */
	private static volatile String stage = "not aimed";

	private static String name(GameTestHelper helper, BlockPos station) {
		return QaTrainStationNames1102CompatTests.name(helper.getLevel(), helper.absolutePos(station));
	}

	private static boolean named(String name) {
		return !name.equals("Track Station") && !name.startsWith("(");
	}

	/**
	 * A world from before B894: the four stations named Pine, Oakford, Oakford and Dunmore (the old naming, no other name
	 * to fall back on) and the Pine-Oakford train put together going to 'Oakford'. The villages' next rounds tell the two
	 * Oakfords apart, the train's schedule follows the far one, and Pine's logs reach the far Oakford by train.
	 */
	//$ gametest_ticks_batch AREA '3600' '"trainNamesApartOldWorld"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "trainNamesApartOldWorld")
	public void aWorldFromBeforeTheFixHasItsOakfordsToldApartAndTheGoodsArrive(GameTestHelper helper) {
		oldWorld(helper, true);
	}

	/**
	 * The same old world with the middle Oakford no longer trading (nobody points its station any more): the far
	 * Oakford's station, whose train already runs, is the one told apart, the train's stop 'Oakford' (still a station's
	 * name, the middle one's) is pointed at it, and the logs reach the far Oakford.
	 */
	//$ gametest_ticks_batch AREA '3600' '"trainNamesApartOldWorldIdle"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "trainNamesApartOldWorldIdle")
	public void aWorldFromBeforeTheFixWithTheOtherOakfordIdleSendsTheTrainToTheFarOne(GameTestHelper helper) {
		oldWorld(helper, false);
	}

	private static void oldWorld(GameTestHelper helper, boolean trades) {
		ServerLevel level = helper.getLevel();
		QaTrainSameNames1103eCompatTests.Four v = QaTrainSameNames1103eCompatTests.villages(helper, "Oakford");
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		BlockPos pine = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_PINE);
		BlockPos mid = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_MID);
		BlockPos far = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_FAR);
		BlockPos dun = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_DUN);
		Villager[] driver = {null};
		ride(helper, v, () -> {
			// the old naming: each village's own name, nothing to tell namesakes apart by
			boolean aimed = r.aim(level, pine, far, "Pine") & r.aim(level, far, pine, "Oakford") & r.aim(level, mid, dun, "Oakford")
				& r.aim(level, dun, mid, "Dunmore");
			if (!aimed || !r.linked(level, pine, far) || !r.linked(level, pine, mid)) {
				return false;
			}
			stage = "aimed";
			if (!trades && !v.data().partners(v.mid()).isEmpty()) {
				v.data().toggleRoute(v.mid(), v.dun()); // the middle village stopped trading: nobody aims its station now
			}
			helper.assertTrue(name(helper, QaTrainSameNames1103eCompatTests.STATION_MID).equals("Oakford")
				&& name(helper, QaTrainSameNames1103eCompatTests.STATION_FAR).equals("Oakford"),
				"setup: the old naming didn't give both Oakfords 'Oakford': " + QaTrainSameNames1103eCompatTests.names(helper));
			BlockPos home = CaravanTrains.first(v.pine(), v.far()) ? pine : far;
			BlockPos there = home.equals(pine) ? far : pine;
			if (!r.built(level, home, there)) {
				r.train(level, home, there).forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
				driver[0] = CreateTrainCompatTests.conductor(helper, helper.relativePos(home).south(3));
				stage = "parts placed";
				return false;
			}
			UUID id = r.assemble(level, home, there, CaravanTrains.railwayId(v.pine(), v.far()), driver[0]);
			stage = "assembled " + id + " by " + driver[0];
			return id != null && (trades || v.data().partners(v.mid()).isEmpty());
		}, () -> {
			String m = name(helper, QaTrainSameNames1103eCompatTests.STATION_MID);
			String f = name(helper, QaTrainSameNames1103eCompatTests.STATION_FAR);
			helper.assertTrue(!m.equals(f) && m.startsWith("Oakford") && f.startsWith("Oakford") && (trades || m.equals("Oakford")),
				"the two Oakfords' stations weren't told apart (both still after their village): " + QaTrainSameNames1103eCompatTests.names(helper));
		}, 3500);
	}

	/**
	 * A station a player named 'Oakford' by hand on the line (the middle village is Elmwood): it keeps its name, the far
	 * Oakford's station gets the village's name with its key, and Pine's logs reach the far Oakford by train.
	 */
	//$ gametest_ticks_batch AREA '3000' '"trainNamesApartPlayers"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "trainNamesApartPlayers")
	public void aStationAPlayerNamedLikeTheVillageKeepsItsNameAndTheGoodsArrive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaTrainSameNames1103eCompatTests.Four v = QaTrainSameNames1103eCompatTests.villages(helper, "Elmwood");
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		BlockPos mid = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_MID);
		BlockPos dun = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_DUN);
		ride(helper, v, () -> {
			if (!r.aim(level, mid, dun, "Elmwood")) {
				return false;
			}
			// the player's own name, typed into Create's station screen
			((com.simibubi.create.content.trains.station.StationBlockEntity) level.getBlockEntity(mid)).updateName("Oakford");
			return name(helper, QaTrainSameNames1103eCompatTests.STATION_MID).equals("Oakford");
		}, () -> {
			String f = name(helper, QaTrainSameNames1103eCompatTests.STATION_FAR);
			helper.assertTrue(name(helper, QaTrainSameNames1103eCompatTests.STATION_MID).equals("Oakford"),
				"the station the player named was renamed: " + QaTrainSameNames1103eCompatTests.names(helper));
			helper.assertTrue(f.startsWith("Oakford ") && f.length() > "Oakford ".length(),
				"the far Oakford's station isn't its village's name told apart: " + QaTrainSameNames1103eCompatTests.names(helper));
		}, 2900);
	}

	/**
	 * The two Oakfords' stations aimed, then the server stopped before Create knew them (its waiting names lost) and
	 * started again: the next rounds still give them two names, each after its village.
	 */
	//$ gametest_ticks_batch AREA '600' '"trainNamesApartRestart"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "trainNamesApartRestart")
	public void twoOakfordsAimedAcrossARestartStillGetTwoNames(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaTrainSameNames1103eCompatTests.Four v = QaTrainSameNames1103eCompatTests.villages(helper, "Oakford");
		BlockPos pine = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_PINE);
		BlockPos mid = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_MID);
		BlockPos far = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_FAR);
		BlockPos dun = helper.absolutePos(QaTrainSameNames1103eCompatTests.STATION_DUN);
		CaravanTrains.Railway[] r = {new CreateTrains()};
		boolean[] restarted = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			if (++tick[0] % 10 != 0) {
				return;
			}
			boolean aimed = r[0].aim(level, far, pine, "Oakford", "Oakford far") & r[0].aim(level, mid, dun, "Oakford", "Oakford mid");
			if (!restarted[0]) {
				helper.assertTrue(!aimed, "setup: Create knew the stations before they were aimed");
				r[0] = new CreateTrains(); // the server stops after the round that pointed them, and starts again
				restarted[0] = true;
				return;
			}
			String m = name(helper, QaTrainSameNames1103eCompatTests.STATION_MID);
			String f = name(helper, QaTrainSameNames1103eCompatTests.STATION_FAR);
			if (aimed && r[0].linked(level, mid, far) && named(m) && named(f)) {
				helper.assertTrue(!m.equals(f) && m.startsWith("Oakford") && f.startsWith("Oakford"),
					"after a restart the two Oakfords' stations share a name: '" + m + "' and '" + f + "'");
				QaTrainSameNames1103eCompatTests.tidy(level, v, UUID.randomUUID());
				helper.succeed();
			} else if (tick[0] > 500) {
				helper.fail("the stations were never named after the restart: " + QaTrainSameNames1103eCompatTests.names(helper));
			}
		});
	}

	/**
	 * {@code before} each round until it says the world is ready, then the villages' rounds; once the Pine-Oakford train
	 * runs, Pine sends 32 logs to the far Oakford and they must come in there by train ({@code arrived} checks the names).
	 */
	private static void ride(GameTestHelper helper, QaTrainSameNames1103eCompatTests.Four v, java.util.function.BooleanSupplier before, Runnable arrived,
		int deadline) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		UUID train = CaravanTrains.railwayId(v.pine(), v.far());
		boolean[] ready = {false};
		boolean[] sent = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			if (!ready[0]) {
				ready[0] = before.getAsBoolean();
				if (!ready[0] && tick[0] > 1000) {
					helper.fail("setup: the world wasn't ready in 1000 ticks (" + stage + "); " + QaTrainSameNames1103eCompatTests.names(helper));
				}
				return;
			}
			QaTrainSameNames1103eCompatTests.round(level, v);
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.pine(), v.far())) {
					v.data().clearRoad(v.pine());
					for (BlockPos h : v.halls()) {
						((Container) level.getBlockEntity(h.east(3).north(2))).clearContent();
					}
					((Container) level.getBlockEntity(v.pine().east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
					v.data().setWants(v.pine(), Component.literal("Pine"), List.of());
					v.data().setWants(v.far(), Component.literal("Oakford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
					v.data().sent(v.pine(), -1);
					Caravans.round(level, v.pine(), null);
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.far())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: Pine's caravan for the far Oakford doesn't go by train: " + s);
					sent[0] = true;
				} else if (tick[0] > 2000) {
					helper.fail("setup: no Pine-Oakford train ran in 2000 ticks; " + QaTrainSameNames1103eCompatTests.names(helper));
				}
				return;
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.far())).findFirst().orElse(null);
			if (s == null && CreateTrainCompatTests.said(level, v.far(), "chronicle.aliveworkplace.train_came")) {
				arrived.run();
				int logs = 0;
				for (BlockPos h : v.halls()) {
					logs += ((Container) level.getBlockEntity(h.east(3).north(2))).countItem(Items.OAK_LOG);
				}
				helper.assertTrue(logs == 64, "logs were lost or doubled on the way: " + logs + " of 64 in the four chests");
				QaTrainSameNames1103eCompatTests.tidy(level, v, train);
				helper.succeed();
			} else if (tick[0] >= deadline) {
				helper.fail("Pine's goods never reached the far Oakford by train: caravan " + s + ", train: "
					+ CreateTrainCompatTests.railway().status(level, train) + "; " + QaTrainSameNames1103eCompatTests.names(helper));
			}
		});
	}
}
