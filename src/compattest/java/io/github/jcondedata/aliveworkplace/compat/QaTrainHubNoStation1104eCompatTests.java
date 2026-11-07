package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-e-1104-0413), B898 from its spec: a village between two train neighbors on one Create line gets each
 * neighbor's goods, the second neighbor's by a station the hub's builder puts up from the bench's chests. These try the
 * ways that breaks: no Create station in the bench's chests (the goods must still come, by the timed trip, and the
 * station goes up once one is brought), and the second station mined by a player while its train runs (the builder puts
 * it back and the goods aboard still arrive, none lost or doubled). Line and villages: {@link QaTrainHub1103eCompatTests}.
 */
public class QaTrainHubNoStation1104eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BARREL = new BlockPos(13, 2, 20);

	private static Item station() {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "track_station"));
	}

	/** A builder of the hub's at a bench by its hall, with {@code stations} Create stations in the bench's barrel. */
	private static Villager builder(GameTestHelper helper, int stations) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(14, 2, 20);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BARREL, Blocks.BARREL);
		if (stations > 0) {
			((Container) helper.getBlockEntity(BARREL)).setItem(0, new ItemStack(station(), stations));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 21));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return builder;
	}

	/** The pair's train blocks at the station of the village that puts it together (its builder's job); no station put up. */
	private static void train(ServerLevel level, Caravans.Data data, BlockPos x, BlockPos y) {
		BlockPos hall = CaravanTrains.first(x, y) ? x : y;
		BlockPos other = hall.equals(x) ? y : x;
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		BlockPos ours = CaravanTrains.stationFor(level, hall, other, data);
		BlockPos theirs = CaravanTrains.stationFor(level, other, hall, data);
		if (CaravanTrains.realTrain(level, x, y) || ours == null || theirs == null || !r.linked(level, ours, theirs) || r.built(level, ours, theirs)) {
			return;
		}
		Map<BlockPos, BlockState> parts = r.train(level, ours, theirs);
		if (parts != null) {
			parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
		}
	}

	/** The halls' rounds (stations noted, caravans unloaded), the trains built one at a time, the halls' looks at their trains. */
	private static void round(ServerLevel level, QaTrainHub1103eCompatTests.Three v) {
		for (BlockPos h : v.halls()) {
			v.data().sent(h, io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level)); // no new caravans of their own
			Caravans.round(level, h, null);
		}
		if (!CaravanTrains.realTrain(level, v.west(), v.hub())) {
			train(level, v.data(), v.west(), v.hub());
		}
		if (!CaravanTrains.realTrain(level, v.east(), v.hub())) {
			train(level, v.data(), v.east(), v.hub());
		}
		for (BlockPos h : v.halls()) {
			CaravanTrains.serve(level, h, (VillageHallBlockEntity) level.getBlockEntity(h));
		}
	}

	/** The hub's stations other than its first one. */
	private static BlockPos second(GameTestHelper helper) {
		BlockPos first = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_HUB);
		for (int dx = -3; dx <= 3; dx++) {
			BlockPos p = first.east(dx);
			if (dx != 0 && CaravanTrains.isStation(helper.getLevel().getBlockState(p))) {
				return p;
			}
		}
		return null;
	}

	private static String sites(ServerLevel level, QaTrainHub1103eCompatTests.Three v) {
		return Roads.openSegments(level, v.hub()).stream().map(st -> st.structure() + " " + st.stage() + " " + st.status() + " missing " + st.missing()).toList().toString();
	}

	/** The neighbor whose train runs (the early one) and the one still waiting for a station of its own at the hub; null while neither or both run. */
	private static BlockPos[] earlyLate(ServerLevel level, QaTrainHub1103eCompatTests.Three v) {
		boolean west = CaravanTrains.realTrain(level, v.west(), v.hub());
		boolean east = CaravanTrains.realTrain(level, v.east(), v.hub());
		return west == east ? null : west ? new BlockPos[] {v.west(), v.east()} : new BlockPos[] {v.east(), v.west()};
	}

	/** Sends {@code from}'s caravan of 32 wheat to the hub (its chest holds 64), the hub wanting no more after. */
	private static void sendWheat(GameTestHelper helper, QaTrainHub1103eCompatTests.Three v, BlockPos from) {
		ServerLevel level = helper.getLevel();
		for (BlockPos h : v.halls()) {
			((Container) level.getBlockEntity(h.east(3).north(2))).clearContent();
		}
		((Container) level.getBlockEntity(from.east(3).north(2))).setItem(0, new ItemStack(Items.WHEAT, 64));
		v.data().setWants(v.hub(), Component.literal("Middleton"), List.of(new Caravans.Want(Items.WHEAT, 32)));
		v.data().sent(from, -1);
		Caravans.round(level, from, null);
		v.data().setWants(v.hub(), Component.literal("Middleton"), List.of());
		helper.assertTrue(v.data().onTheRoad().stream().anyMatch(s -> s.from().equals(from) && s.to().equals(v.hub())),
			"setup: no caravan from " + from + " to the hub: " + v.data().onTheRoad());
	}

	/**
	 * No Create station in the hub's bench chests: the first neighbor's train runs to the hub's one station, the second
	 * neighbor's can't come in, and no station appears at the hub out of nothing. That neighbor's 32 wheat still reach the
	 * hub (by the timed trip, as when there's no train), none lost or doubled. Then a Create station brought to the barrel:
	 * the builder puts it up and the second neighbor's train runs too.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaTrainHubNoStation"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaTrainHubNoStation")
	public void withNoStationInTheBenchChestsTheSecondNeighborsGoodsStillArriveAndTheStationGoesUpOnceBrought(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		builder(helper, 0);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(15, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		int[] tick = {0};
		int[] phase = {0};
		int[] since = {0};
		BlockPos[] pair = {null};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			round(level, v);
			BlockPos second = second(helper);
			switch (phase[0]) {
				case 0 -> { // the first train
					pair[0] = earlyLate(level, v) == null ? null : earlyLate(level, v)[1];
					if (pair[0] != null) {
						since[0] = tick[0];
						phase[0] = 1;
					} else if (tick[0] > 2400) {
						helper.fail("no train of the hub's ran in 2400 ticks: " + QaTrainHub1103eCompatTests.trains(level, v));
					}
				}
				case 1 -> { // a while with nothing in the barrel: no station from nowhere, the late train not put together
					helper.assertTrue(second == null, "a second station went up at the hub with no Create station in the bench's chests: " + second);
					helper.assertTrue(!CaravanTrains.realTrain(level, pair[0], v.hub()),
						"the second neighbor's train runs with no station facing it at the hub: " + QaTrainHub1103eCompatTests.trains(level, v));
					if (tick[0] - since[0] >= 400) {
						sendWheat(helper, v, pair[0]);
						// the guide: till the station is up, that neighbor's caravans take the quicker trip without a train you can see
						long now = level.getGameTime();
						long walk = Caravans.trip(level, pair[0], v.hub(), v.data());
						helper.assertTrue(v.data().onTheRoad().stream().anyMatch(s -> s.from().equals(pair[0]) && s.to().equals(v.hub()) && s.byTrain()
								&& s.arrives() <= now + CaravanTrains.trainTrip(walk)),
							"the second neighbor's caravan doesn't take the quicker train trip (walk " + walk + " ticks, now " + now + "): " + v.data().onTheRoad());
						since[0] = tick[0];
						phase[0] = 2;
					}
				}
				case 2 -> { // the late neighbor's wheat comes anyway
					List<Caravans.Shipment> left = v.data().onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
					helper.assertTrue(second == null, "a second station went up at the hub with no Create station in the bench's chests: " + second);
					if (left.isEmpty()) {
						int wheat = QaTrainHub1103eCompatTests.count(level, v, Items.WHEAT);
						helper.assertTrue(wheat == 64, "wheat was lost or doubled on the timed trip: " + wheat + " of 64 in the chests");
						((Container) helper.getBlockEntity(BARREL)).setItem(0, new ItemStack(station(), 1));
						since[0] = tick[0];
						phase[0] = 3;
					} else if (tick[0] - since[0] > 2000) {
						helper.fail("the second neighbor's wheat never reached the hub with no station for its train (2000 ticks): " + left
							+ "; " + QaTrainHub1103eCompatTests.trains(level, v) + "; the hub's sites " + sites(level, v));
					}
				}
				default -> { // the station brought: it goes up and the late train runs
					if (second != null && CaravanTrains.realTrain(level, pair[0], v.hub())) {
						helper.assertTrue(second.equals(CaravanTrains.stationFor(level, v.hub(), pair[0], v.data())),
							"the late neighbor's train doesn't use the station put up for it: " + CaravanTrains.stationFor(level, v.hub(), pair[0], v.data()));
						helper.assertTrue(((Container) helper.getBlockEntity(BARREL)).countItem(station()) == 0,
							"the builder put the station up but it's still in the barrel (doubled)");
						QaTrainHub1103eCompatTests.tidy(level, v);
						helper.succeed();
					} else if (tick[0] - since[0] > 3000) {
						helper.fail("with a Create station brought, the second station or train never came in 3000 ticks: second station " + second
							+ "; " + QaTrainHub1103eCompatTests.trains(level, v) + "; the hub's sites " + sites(level, v));
					}
				}
			}
		});
	}

	/**
	 * Both of the hub's trains running, the second neighbor's 32 wheat aboard its train, and the second station mined by a
	 * player (the barrel holds a spare): the builder puts it back, the train comes in to it, and the 32 wheat reach the hub,
	 * none lost or doubled.
	 */
	//$ gametest_ticks_batch AREA '7000' '"qaTrainHubMined"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qaTrainHubMined")
	public void theSecondStationMinedWhileItsTrainRunsIsPutBackAndTheGoodsArrive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		builder(helper, 2);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(15, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		int[] tick = {0};
		int[] phase = {0};
		int[] since = {0};
		BlockPos[] late = {null};
		BlockPos[] mined = {null};
		// B939: both of the hub's trains were once found derailed in phase 1; note the first tick either is, where both
		// were then and the tick before, for the failure messages (nothing here changes what the test asks)
		String[] derail = {"neither train derailed"};
		String[] before = {""};
		java.util.UUID westId = CaravanTrains.railwayId(v.west(), v.hub());
		java.util.UUID eastId = CaravanTrains.railwayId(v.east(), v.hub());
		helper.onEachTick(() -> {
			tick[0]++;
			if (derail[0].startsWith("neither")) {
				String now = "west train " + QaTrainHub1103eCompatTests.where(westId) + "; east train " + QaTrainHub1103eCompatTests.where(eastId);
				if (now.contains("derailed true")) {
					derail[0] = "first derailed at tick " + tick[0] + " (phase " + phase[0] + ", second station " + second(helper)
						+ ", hub station " + helper.absolutePos(QaTrainHub1103eCompatTests.STATION_HUB) + "): " + now + "; the tick before: " + before[0];
				}
				before[0] = now;
			}
			if (tick[0] % 10 != 0) {
				return;
			}
			round(level, v);
			BlockPos second = second(helper);
			switch (phase[0]) {
				case 0 -> { // both trains, the second station up
					if (late[0] == null && earlyLate(level, v) != null) {
						late[0] = earlyLate(level, v)[1];
					}
					if (late[0] != null && second != null && CaravanTrains.realTrain(level, late[0], v.hub())) {
						sendWheat(helper, v, late[0]);
						since[0] = tick[0];
						phase[0] = 1;
					} else if (tick[0] > 3200) {
						helper.fail("the hub's two trains weren't both running in 3200 ticks: " + QaTrainHub1103eCompatTests.trains(level, v)
							+ "; the hub's sites " + sites(level, v) + "; " + derail[0]);
					}
				}
				case 1 -> { // the wheat aboard: the station mined (as a player breaking it, no drop kept)
					boolean aboard = v.data().onTheRoad().stream().anyMatch(s -> s.from().equals(late[0]) && s.to().equals(v.hub()) && CaravanTrains.aboard(s.arrives()));
					if (aboard) {
						mined[0] = second;
						level.removeBlock(second, false);
						since[0] = tick[0];
						phase[0] = 2;
					} else if (tick[0] - since[0] > 1500) {
						helper.fail("the second neighbor's wheat never got aboard its train in 1500 ticks: " + v.data().onTheRoad()
							+ "; " + QaTrainHub1103eCompatTests.trains(level, v) + "; " + derail[0]);
					}
				}
				default -> { // put back, and the wheat in
					List<Caravans.Shipment> left = v.data().onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
					if (left.isEmpty()) {
						int wheat = QaTrainHub1103eCompatTests.count(level, v, Items.WHEAT);
						helper.assertTrue(wheat == 64, "wheat was lost or doubled with the second station mined: " + wheat + " of 64 in the chests");
						helper.assertTrue(CreateTrainCompatTests.said(level, v.hub(), "chronicle.aliveworkplace.train_came"), "the wheat didn't come in by train");
						QaTrainHub1103eCompatTests.tidy(level, v);
						helper.succeed();
					} else if (tick[0] - since[0] > 3500) {
						helper.fail("with the second station mined, the wheat never reached the hub in 3500 ticks: " + left + "; second station now "
							+ second + " (mined at " + mined[0] + "); " + QaTrainHub1103eCompatTests.trains(level, v) + "; the hub's sites "
							+ sites(level, v) + "; the barrel holds " + ((Container) helper.getBlockEntity(BARREL)).countItem(station()) + " stations; " + derail[0]);
					}
				}
			}
		});
	}
	/**
	 * A world saved before B898 opened with this jar: the hub's one station pointed at Westmere, and the Eastwick-hub
	 * train already put together the old way (at the pair's first village's station, its schedule to the other's), so it
	 * stood with no way in. With a Create station in the hub's bench barrel, the builder puts up the second station, the
	 * old train's stop follows it, and Eastwick's 32 wheat then reach the hub by that train, none lost or doubled.
	 */
	//$ gametest_ticks_batch AREA '7000' '"qaTrainHubOldSave"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qaTrainHubOldSave")
	public void aWorldFromBeforeTheFixWithTheStuckSecondTrainGetsItsGoodsThrough(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		builder(helper, 1);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(15, 2, 16));
		Villager eastConductor = CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		BlockPos west = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_WEST);
		BlockPos hub = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_HUB);
		BlockPos east = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_EAST);
		java.util.UUID id = CaravanTrains.railwayId(v.east(), v.hub());
		int[] tick = {0};
		int[] phase = {0};
		int[] since = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			switch (phase[0]) {
				case 0 -> { // the old world: the hub's station faces Westmere, the Eastwick train put together the old way
					boolean aimed = r.aim(level, hub, west, "Middleton") & r.aim(level, west, hub, "Westmere") & r.aim(level, east, hub, "Eastwick");
					if (!aimed || !r.linked(level, hub, east)) {
						if (tick[0] > 600) {
							helper.fail("setup: the old world's stations weren't aimed in 600 ticks");
						}
						return;
					}
					BlockPos ours = CaravanTrains.first(v.east(), v.hub()) ? east : hub;
					BlockPos theirs = ours.equals(east) ? hub : east;
					if (!r.built(level, ours, theirs)) {
						Map<BlockPos, BlockState> parts = r.train(level, ours, theirs);
						helper.assertTrue(parts != null, "setup: no room for the old train at " + ours);
						parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
						return;
					}
					Villager driver = ours.equals(east) ? eastConductor : CaravanTrains.conductorFor(level, ours);
					java.util.UUID train = r.assemble(level, ours, theirs, id, driver);
					if (train == null) {
						if (tick[0] > 600) {
							helper.fail("setup: the old Eastwick-hub train couldn't be put together: " + r.status(level, id));
						}
						return;
					}
					helper.assertTrue(r.facing(level, hub, east) == -1, "setup: the hub's one station doesn't face away from Eastwick");
					since[0] = tick[0];
					phase[0] = 1;
				}
				case 1 -> { // opened with this jar: the second station goes up and the old train runs to it
					round(level, v);
					BlockPos second = second(helper);
					if (second != null && tick[0] - since[0] > 200 && CaravanTrains.realTrain(level, v.east(), v.hub())) {
						sendWheat(helper, v, v.east());
						helper.assertTrue(v.data().onTheRoad().stream().anyMatch(s -> s.from().equals(v.east()) && s.to().equals(v.hub()) && CaravanTrains.waiting(s.arrives())),
							"setup: Eastwick's caravan doesn't wait for the old train: " + v.data().onTheRoad());
						since[0] = tick[0];
						phase[0] = 2;
					} else if (tick[0] - since[0] > 3000) {
						helper.fail("opened with this jar, no second station or no Eastwick train in 3000 ticks: second " + second + "; "
							+ QaTrainHub1103eCompatTests.trains(level, v) + "; the hub's sites " + sites(level, v));
					}
				}
				default -> {
					round(level, v);
					List<Caravans.Shipment> left = v.data().onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
					if (left.isEmpty()) {
						int wheat = QaTrainHub1103eCompatTests.count(level, v, Items.WHEAT);
						helper.assertTrue(wheat == 64, "wheat was lost or doubled: " + wheat + " of 64 in the chests");
						helper.assertTrue(CreateTrainCompatTests.said(level, v.hub(), "chronicle.aliveworkplace.train_came"), "the wheat didn't come in by train");
						QaTrainHub1103eCompatTests.tidy(level, v);
						helper.succeed();
					} else if (tick[0] - since[0] > 3300) {
						helper.fail("the old Eastwick train never brought the wheat to the hub in 3300 ticks: " + left + "; "
							+ QaTrainHub1103eCompatTests.trains(level, v) + "; stations: second " + second(helper) + " named "
							+ (second(helper) == null ? "-" : QaTrainStationNames1102CompatTests.name(level, second(helper))));
					}
				}
			}
		});
	}
}
