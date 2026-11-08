package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-e-1108-0713, Create trains), verifying B938 from its spec: a village train runs between its stations "whatever
 * a player calls them", glob characters included ('Bramble [East]', 'Platform {2}'). The fix's tests name a station once
 * before the caravan is sent; these rename stations with Create's station screen after the Pine-Oakford train already
 * runs, so the stops it holds are re-pointed (CreateTrains.follow) from a name to a bracketed one, from one bracketed name
 * to another, at the sending end with wildcards, and through B933's namesake rule when a second station gets the same
 * bracketed name. Each time Pine's 32 logs reach the far village by train, none lost or doubled, and every station keeps
 * the player's name. (Renaming a village with a name tag after its train runs leaves its station's name as it was, as
 * create.md says: the schedule is made from the names the stations have when the train is put together.) Line, villages
 * and run are
 * {@link QaTrainVillageRenamed1104eCompatTests}'s ({@link QaTrainSameNames1103eCompatTests}'s four villages).
 */
public class QaTrainGlobRenames1108eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A player names {@code station} (relative) with Create's station screen. */
	private record Rename(BlockPos station, String name) {
	}

	private static Rename far(String name) {
		return new Rename(QaTrainSameNames1103eCompatTests.STATION_FAR, name);
	}

	private static void rename(GameTestHelper helper, BlockPos station, String name) {
		StationBlockEntity be = (StationBlockEntity) helper.getBlockEntity(station);
		helper.assertTrue(be.getStation() != null, "setup: the station at " + station + " isn't a station Create knows");
		be.updateName(name); // what Create's station screen does when a player types a name
	}

	/**
	 * The Pine-Oakford train runs; then each rename is done in turn, 200 ticks apart; 300 ticks after the last Pine sends
	 * 32 logs to the far village (named {@code farName} by then), which must come in there by train.
	 */
	private static void ride(GameTestHelper helper, List<Rename> renames, boolean apart) {
		String farName = "Oakford";
		ServerLevel level = helper.getLevel();
		QaTrainSameNames1103eCompatTests.Four v = QaTrainSameNames1103eCompatTests.villages(helper, "Elmwood");
		// as QaTrainVillageRenamed1104eCompatTests: a Storehouse an earlier batch left outside this area would take the goods
		net.minecraft.world.phys.AABB area = helper.getBounds();
		for (BlockPos hall : v.halls()) {
			for (BlockPos store : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
					p -> !area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS,
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				level.removeBlock(store, false);
			}
		}
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		UUID train = CaravanTrains.railwayId(v.pine(), v.far());
		String pineName = "Pine";
		int[] tick = {0};
		int[] phase = {0};
		int[] since = {0};
		int[] done = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			QaTrainSameNames1103eCompatTests.round(level, v);
			switch (phase[0]) {
				case 0 -> {
					if (CaravanTrains.realTrain(level, v.pine(), v.far())) {
						since[0] = tick[0] - 200;
						phase[0] = 1;
					} else if (tick[0] > 2000) {
						helper.fail("setup: no Pine-Oakford train ran in 2000 ticks; " + QaTrainSameNames1103eCompatTests.names(helper));
					}
				}
				case 1 -> {
					if (tick[0] - since[0] < 200) {
						return;
					}
					if (done[0] < renames.size()) {
						Rename r = renames.get(done[0]++);
						rename(helper, r.station(), r.name());
						since[0] = tick[0];
						return;
					}
					if (tick[0] - since[0] < 300) {
						return;
					}
					v.data().clearRoad(v.pine());
					for (BlockPos h : v.halls()) {
						((Container) level.getBlockEntity(h.east(3).north(2))).clearContent();
					}
					((Container) level.getBlockEntity(v.pine().east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
					v.data().setWants(v.pine(), Component.literal(pineName), List.of());
					v.data().setWants(v.far(), Component.literal(farName), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
					v.data().sent(v.pine(), -1);
					Caravans.round(level, v.pine(), null);
					v.data().setWants(v.far(), Component.literal(farName), List.of());
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.far())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain(), "setup: Pine's caravan for the far village doesn't go by train: " + s);
					since[0] = tick[0];
					phase[0] = 2;
				}
				default -> {
					Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.far())).findFirst().orElse(null);
					if (s == null) {
						helper.assertTrue(CreateTrainCompatTests.said(level, v.far(), "chronicle.aliveworkplace.train_came"),
							"Pine's logs reached the far village but not by train: " + QaTrainSameNames1103eCompatTests.names(helper));
						Set<String> names = new HashSet<>();
						for (BlockPos p : List.of(QaTrainSameNames1103eCompatTests.STATION_PINE, QaTrainSameNames1103eCompatTests.STATION_MID,
							QaTrainSameNames1103eCompatTests.STATION_FAR, QaTrainSameNames1103eCompatTests.STATION_DUN)) {
							names.add(QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(p)));
						}
						for (Rename r : renames) {
							String last = renames.stream().filter(x -> x.station().equals(r.station())).reduce((a, b) -> b).orElseThrow().name();
							String now = QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(r.station()));
							helper.assertTrue(now.equals(last), "a player's name for a station didn't stay: '" + now + "', named '" + last + "'");
						}
						helper.assertTrue(!apart || names.size() == 4, "two stations on the line share a name after the renames: " + QaTrainSameNames1103eCompatTests.names(helper));
						int logs = 0;
						for (BlockPos h : v.halls()) {
							logs += ((Container) level.getBlockEntity(h.east(3).north(2))).countItem(Items.OAK_LOG);
						}
						helper.assertTrue(logs == 64, "logs were lost or doubled on the way: " + logs + " of 64 in the four chests; the far village's storehouse chests "
							+ Caravans.storehouse(level, v.far()));
						QaTrainSameNames1103eCompatTests.tidy(level, v, train);
						helper.succeed();
					} else if (tick[0] - since[0] > 2600) {
						helper.fail("after the renames Pine's logs never reached the far village by train: caravan " + s + ", train: "
							+ CreateTrainCompatTests.railway().status(level, train) + "; " + QaTrainSameNames1103eCompatTests.names(helper));
					}
				}
			}
		});
	}

	/** The far village's station renamed 'Oakford [East]' after the train runs: its stop goes from a name to a pattern. */
	//$ gametest_ticks_batch AREA '5400' '"qaTrainGlobRenameFar"'
	@GameTest(template = AREA, timeoutTicks = 5400, batch = "qaTrainGlobRenameFar")
	public void theFarStationRenamedWithBracketsWhileTheTrainRunsStillGetsTheGoods(GameTestHelper helper) {
		ride(helper, List.of(far("Oakford [East]")), true);
	}

	/** Renamed twice, from one bracketed name to one with braces too: the old escaped stop is read back and re-pointed. */
	//$ gametest_ticks_batch AREA '5400' '"qaTrainGlobRenameTwice"'
	@GameTest(template = AREA, timeoutTicks = 5400, batch = "qaTrainGlobRenameTwice")
	public void theFarStationRenamedFromOneBracketedNameToAnotherStillGetsTheGoods(GameTestHelper helper) {
		ride(helper, List.of(far("Oakford [East]"), far("Oak {North} [2]")), true);
	}

	/**
	 * The far station named 'Oak [E]', then the middle village's station (on the same network) given the same name by the
	 * player: B933's namesake rule with an escaped stop; the goods still reach the far village.
	 */
	//$ gametest_ticks_batch AREA '5400' '"qaTrainGlobNamesake"'
	@GameTest(template = AREA, timeoutTicks = 5400, batch = "qaTrainGlobNamesake")
	public void twoStationsGivenOneBracketedNameStillSendTheGoodsToTheRightOne(GameTestHelper helper) {
		ride(helper, List.of(far("Oak [E]"), new Rename(QaTrainSameNames1103eCompatTests.STATION_MID, "Oak [E]")), false);
	}

	/** Pine's own station renamed 'Pine*' and then 'Pine?': the home stop, with glob wildcards, still sends the goods. */
	//$ gametest_ticks_batch AREA '5400' '"qaTrainGlobRenameHome"'
	@GameTest(template = AREA, timeoutTicks = 5400, batch = "qaTrainGlobRenameHome")
	public void theSendingStationRenamedWithWildcardsStillSendsTheGoods(GameTestHelper helper) {
		ride(helper, List.of(new Rename(QaTrainSameNames1103eCompatTests.STATION_PINE, "Pine*"),
			new Rename(QaTrainSameNames1103eCompatTests.STATION_PINE, "Pine?")), true);
	}
}
