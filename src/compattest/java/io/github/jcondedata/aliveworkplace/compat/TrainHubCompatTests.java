package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.compat.create.CreateTrains;
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
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B898: a village between two train neighbors on one Create line. A Create station takes trains from one side only, so
 * the hub's one station, facing the first neighbor, can't take the second neighbor's train: the hub's builder puts up a
 * second station beside the track (a Create station from the bench's barrel), facing the second neighbor, and that
 * neighbor's train runs to it. The line and villages are {@link QaTrainHub1103eCompatTests}'s (QA's happy path).
 */
public class TrainHubCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A builder of the hub's, at a bench by its hall, with a Create station (and nothing else) in the bench's barrel. */
	private static Villager builder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000); // the builder's working hours
		BlockPos bench = new BlockPos(14, 2, 20);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(13, 2, 20), Blocks.BARREL);
		Container barrel = helper.getBlockEntity(new BlockPos(13, 2, 20));
		barrel.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "track_station")), 1));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 21));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return builder;
	}

	/** The pair's train blocks at the station of the village that puts it together, as its builder would; no station put up. */
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

	/** The halls' rounds: their stations noted (the hall's round), the trains built one at a time, the looks at their trains. */
	private static void round(ServerLevel level, QaTrainHub1103eCompatTests.Three v) {
		for (BlockPos h : v.halls()) {
			CaravanTrains.noteStations(level, h, v.data());
		}
		if (!CaravanTrains.realTrain(level, v.west(), v.hub())) {
			train(level, v.data(), v.west(), v.hub());
		} else {
			train(level, v.data(), v.east(), v.hub());
		}
		for (BlockPos h : v.halls()) {
			CaravanTrains.serve(level, h, (VillageHallBlockEntity) level.getBlockEntity(h));
		}
	}

	private static String names(GameTestHelper helper, BlockPos second) {
		ServerLevel level = helper.getLevel();
		return QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(QaTrainHub1103eCompatTests.STATION_WEST)) + " / "
			+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(QaTrainHub1103eCompatTests.STATION_HUB)) + " / "
			+ (second == null ? "(no second station)" : QaTrainStationNames1102CompatTests.name(level, second)) + " / "
			+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(QaTrainHub1103eCompatTests.STATION_EAST));
	}

	/** The hub's stations other than its first one (the second station, once it's up). */
	private static BlockPos second(GameTestHelper helper, QaTrainHub1103eCompatTests.Three v) {
		BlockPos first = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_HUB);
		for (int dx = -3; dx <= 3; dx++) {
			BlockPos p = first.east(dx);
			if (dx != 0 && CaravanTrains.isStation(helper.getLevel().getBlockState(p))) {
				return p;
			}
		}
		return null;
	}

	/**
	 * As in a world from before B898, the hub's one station is pointed at the neighbor whose pair came first (either
	 * one). The other neighbor's train can't come in there, so the hub's builder takes the Create station from the bench's
	 * barrel and puts it up beside the track (the chronicle says so); it faces that neighbor and is named after both
	 * villages, as "Middleton (Eastwick)" (the first keeps "Middleton"), that neighbor's train runs to it, and its 32
	 * wheat come in by train, none lost or doubled.
	 */
	//$ gametest_ticks_batch AREA '5000' '"trainHubBuilder"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "trainHubBuilder")
	public void theHubsBuilderPutsUpASecondStationForTheSecondNeighborsTrain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		builder(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(15, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		BlockPos hubStation = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_HUB);
		boolean[] sent = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			round(level, v);
			BlockPos second = second(helper, v);
			if (second != null) {
				helper.assertTrue(CreateTrainCompatTests.railway().facing(level, hubStation, v.data().stations(v.west()).get(0)) != 0,
					"a second station went up before the hub's first one was pointed at a neighbor: " + names(helper, second));
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.west(), v.hub()) && CaravanTrains.realTrain(level, v.east(), v.hub())) {
					helper.assertTrue(second != null, "both trains run without a second station at the hub: " + names(helper, null));
					// whichever pair pointed the hub's first station first keeps it; the other neighbor's train uses the second
					boolean eastLate = second.equals(CaravanTrains.stationFor(level, v.hub(), v.east(), v.data()));
					BlockPos late = eastLate ? v.east() : v.west();
					BlockPos early = eastLate ? v.west() : v.east();
					String lateName = eastLate ? "Eastwick" : "Westmere";
					helper.assertTrue(hubStation.equals(CaravanTrains.stationFor(level, v.hub(), early, v.data()))
						&& second.equals(CaravanTrains.stationFor(level, v.hub(), late, v.data())),
						"the hub's trains don't each use a station of their own: " + CaravanTrains.stationFor(level, v.hub(), v.west(), v.data())
							+ " and " + CaravanTrains.stationFor(level, v.hub(), v.east(), v.data()));
					helper.assertTrue(CreateTrainCompatTests.said(level, v.hub(), "chronicle.aliveworkplace.second_station_built"),
						"the hub's chronicle doesn't say its builder put up the second station");
					helper.assertTrue("Middleton".equals(QaTrainStationNames1102CompatTests.name(level, hubStation))
						&& ("Middleton (" + lateName + ")").equals(QaTrainStationNames1102CompatTests.name(level, second)),
						"the hub's stations aren't 'Middleton' and 'Middleton (" + lateName + ")': " + names(helper, second));
					for (BlockPos h : v.halls()) {
						v.data().clearRoad(h);
						((Container) level.getBlockEntity(h.east(3).north(2))).clearContent();
					}
					((Container) level.getBlockEntity(late.east(3).north(2))).setItem(0, new ItemStack(Items.WHEAT, 64));
					v.data().setWants(v.hub(), Component.literal("Middleton"), List.of(new Caravans.Want(Items.WHEAT, 32)));
					v.data().sent(late, -1);
					Caravans.round(level, late, null);
					helper.assertTrue(v.data().onTheRoad().stream().anyMatch(s -> s.from().equals(late) && s.to().equals(v.hub()) && s.byTrain()),
						"setup: " + lateName + "'s caravan for the hub doesn't go by train: " + v.data().onTheRoad());
					sent[0] = true;
				} else if (tick[0] > 3200) {
					helper.fail("the hub's two trains weren't both running in 3200 ticks: " + QaTrainHub1103eCompatTests.trains(level, v) + "; stations "
						+ names(helper, second) + "; the hub's sites " + Roads.openSegments(level, v.hub()).stream()
						.map(st -> st.structure() + " " + st.stage() + " " + st.status() + " missing " + st.missing()).toList());
				}
				return;
			}
			List<Caravans.Shipment> left = v.data().onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
			if (left.isEmpty()) {
				helper.assertTrue(QaTrainHub1103eCompatTests.count(level, v, Items.WHEAT) == 64, "wheat was lost or doubled: "
					+ QaTrainHub1103eCompatTests.count(level, v, Items.WHEAT) + " of 64 in the chests; the hub's storehouse chests "
					+ Caravans.storehouse(level, v.hub()).stream().map(p -> p.toShortString() + " " + (level.getBlockEntity(p) instanceof Container c ? c.countItem(Items.WHEAT) : -1)).toList()
					+ ", the bench's barrel " + ((Container) helper.getBlockEntity(new BlockPos(13, 2, 20))).countItem(Items.WHEAT));
				helper.assertTrue(CreateTrainCompatTests.said(level, v.hub(), "chronicle.aliveworkplace.train_came"), "the hub's chronicle doesn't say a train came in");
				QaTrainHub1103eCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 4900) {
				helper.fail("the second neighbor's wheat never reached the hub: still on the way " + left + "; " + QaTrainHub1103eCompatTests.trains(level, v));
			}
		});
	}

	/**
	 * Saved and loaded with both of the hub's trains running and a caravan aboard each: the list of stations and the
	 * caravans come back as they were, and a fresh railway (a restarted server, its waiting names gone) still reads each
	 * of the hub's stations as facing its own neighbor, so each train keeps its station and both caravans' goods arrive.
	 */
	//$ gametest_ticks_batch AREA '4400' '"trainHubReload"'
	@GameTest(template = AREA, timeoutTicks = 4400, batch = "trainHubReload")
	public void theHubsTwoStationsAndCaravansSurviveARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(15, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		boolean[] sent = {false};
		boolean[] reloaded = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				QaTrainHub1103eCompatTests.round(level, v); // QA's: the second station put up as the hub's builder would
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.west(), v.hub()) && CaravanTrains.realTrain(level, v.east(), v.hub())) {
					for (BlockPos h : v.halls()) {
						v.data().clearRoad(h);
						((Container) level.getBlockEntity(h.east(3).north(2))).clearContent();
					}
					((Container) level.getBlockEntity(v.west().east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
					((Container) level.getBlockEntity(v.east().east(3).north(2))).setItem(0, new ItemStack(Items.WHEAT, 64));
					v.data().setWants(v.hub(), Component.literal("Middleton"), List.of(new Caravans.Want(Items.OAK_LOG, 32), new Caravans.Want(Items.WHEAT, 32)));
					v.data().sent(v.west(), -1);
					v.data().sent(v.east(), -1);
					Caravans.round(level, v.west(), null);
					Caravans.round(level, v.east(), null);
					sent[0] = true;
				} else if (tick[0] > 2000) {
					helper.fail("setup: the hub's two trains weren't both running in 2000 ticks: " + QaTrainHub1103eCompatTests.trains(level, v));
				}
				return;
			}
			List<Caravans.Shipment> left = v.data().onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
			if (!reloaded[0] && left.size() == 2 && left.stream().allMatch(s -> CaravanTrains.aboard(s.arrives()))) {
				Caravans.Data loaded = Caravans.Data.load(v.data().save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				for (BlockPos h : v.halls()) {
					helper.assertTrue(loaded.stations(h).equals(v.data().stations(h)), "the stations of " + h.toShortString() + " changed over the save: "
						+ v.data().stations(h) + " before, " + loaded.stations(h) + " after");
				}
				List<Caravans.Shipment> back = loaded.onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
				helper.assertTrue(back.size() == 2 && back.stream().allMatch(s -> s.byTrain() && CaravanTrains.aboard(s.arrives())),
					"the caravans aboard changed over the save: " + back);
				CreateTrains fresh = new CreateTrains();
				BlockPos forWest = CaravanTrains.stationFor(level, v.hub(), v.west(), loaded);
				BlockPos forEast = CaravanTrains.stationFor(level, v.hub(), v.east(), loaded);
				helper.assertTrue(forWest != null && forEast != null && !forWest.equals(forEast),
					"after the restart the hub's trains don't have a station each: " + forWest + " and " + forEast);
				helper.assertTrue(fresh.facing(level, forWest, loaded.stations(v.west()).get(0)) == 1
					&& fresh.facing(level, forEast, loaded.stations(v.east()).get(0)) == 1
					&& fresh.facing(level, forWest, loaded.stations(v.east()).get(0)) == -1,
					"after the restart the hub's stations don't read as facing their own neighbors");
				reloaded[0] = true;
			}
			if (left.isEmpty()) {
				helper.assertTrue(reloaded[0], "the caravans were never saved while both were aboard");
				helper.assertTrue(QaTrainHub1103eCompatTests.count(level, v, Items.OAK_LOG) == 64 && QaTrainHub1103eCompatTests.count(level, v, Items.WHEAT) == 64,
					"goods were lost or doubled: " + QaTrainHub1103eCompatTests.count(level, v, Items.OAK_LOG) + " logs and "
						+ QaTrainHub1103eCompatTests.count(level, v, Items.WHEAT) + " wheat of 64 each");
				QaTrainHub1103eCompatTests.tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 4300) {
				helper.fail("the neighbors' goods never all reached the hub after the save: " + left + "; " + QaTrainHub1103eCompatTests.trains(level, v));
			}
		});
	}

	/**
	 * Before B898 a village with one train neighbor used its nearest station whichever way it faced: it still does (no
	 * second station for it), and the hub's second station and its builder's chronicle line have their text.
	 */
	//$ gametest_ticks_batch AREA '600' '"trainHubOneNeighbor"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "trainHubOneNeighbor")
	public void aVillageWithOneTrainNeighborKeepsItsStationWhicheverWayItFaces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String key : List.of("chronicle.aliveworkplace.second_station_built", "blueprint.aliveworkplace.second_station")) {
			helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
		}
		QaTrainHub1103eCompatTests.Three v = QaTrainHub1103eCompatTests.villages(helper);
		v.data().toggleRoute(v.east(), v.hub()); // Eastwick's route off: the hub has one train neighbor, Westmere
		BlockPos hubStation = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_HUB);
		BlockPos east = helper.absolutePos(QaTrainHub1103eCompatTests.STATION_EAST);
		int[] tick = {0};
		helper.onEachTick(() -> {
			if (++tick[0] % 10 != 0) {
				return;
			}
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			r.aim(level, hubStation, east, "Middleton"); // a player's station facing away from Westmere
			if (r.facing(level, hubStation, east) != 1) {
				if (tick[0] > 400) {
					helper.fail("setup: the hub's station was never pointed at the track");
				}
				return;
			}
			BlockPos west = v.data().stations(v.west()).get(0);
			helper.assertTrue(r.facing(level, hubStation, west) == -1, "the hub's station facing Eastwick reads as facing Westmere too");
			helper.assertTrue(hubStation.equals(CaravanTrains.stationFor(level, v.hub(), v.west(), v.data())),
				"a village with one train neighbor doesn't keep its station: " + CaravanTrains.stationFor(level, v.hub(), v.west(), v.data()));
			helper.assertTrue(CaravanTrains.secondStation(level, v.hub(), v.west(), v.data()) == null, "a second station for a village with one train neighbor");
			QaTrainHub1103eCompatTests.tidy(level, v);
			helper.succeed();
		});
	}
}
