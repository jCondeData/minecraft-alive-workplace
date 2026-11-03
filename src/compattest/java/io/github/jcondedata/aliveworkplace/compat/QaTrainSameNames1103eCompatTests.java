package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, Create, 2026-11-03; 39.7a, B800): a village train's Create schedule goes by station name, so the village
 * names its station after itself and, when the two villages of a train have one name (a player named both halls the
 * same with name tags), tells them apart. Here three villages share one Create line: Pine at one end, Oakford at the
 * other, and a second village a player also named Oakford in the middle, whose own caravans go to a fourth village
 * (Dunmore) on a track of its own. Pine's goods for the far Oakford go by the Pine-Oakford train: they must arrive at
 * that Oakford, not stop at the other Oakford's station on the way. Halls are named as a name tag names them
 * ({@code setCustomName}); the line is {@link CreateTrainCompatTests#line}'s, with the middle station and Dunmore's
 * track added.
 */
public class QaTrainSameNames1103eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** The line's end stations ({@link CreateTrainCompatTests#line}) and the one between them. */
	private static final BlockPos STATION_PINE = new BlockPos(6, 2, 13);
	private static final BlockPos STATION_MID = new BlockPos(15, 2, 13);
	private static final BlockPos STATION_FAR = new BlockPos(23, 2, 13);
	/** Dunmore's own short track, along x at z = 3, apart from the line. */
	private static final BlockPos STATION_DUN = new BlockPos(5, 2, 4);
	private static final BlockPos HALL_PINE = new BlockPos(6, 2, 22);
	private static final BlockPos HALL_MID = new BlockPos(15, 2, 22);
	private static final BlockPos HALL_FAR = new BlockPos(23, 2, 22);
	private static final BlockPos HALL_DUN = new BlockPos(2, 2, 7);

	private record Four(BlockPos pine, BlockPos mid, BlockPos far, BlockPos dun, Caravans.Data data) {
		List<BlockPos> halls() {
			return List.of(pine, mid, far, dun);
		}
	}

	private static BlockState create(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState xTrack() {
		BlockState track = create("track");
		net.minecraft.world.level.block.state.properties.Property shape = track.getBlock().getStateDefinition().getProperty("shape");
		return track.setValue(shape, (Comparable) shape.getValue("xo").orElseThrow());
	}

	private static Four villages(GameTestHelper helper, String middle) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper); // track along z = 12, stations at x = 6 and x = 23
		helper.setBlock(STATION_MID, create("track_station"));
		for (int x = 1; x <= 9; x++) {
			helper.setBlock(new BlockPos(x, 2, 3), xTrack());
		}
		helper.setBlock(STATION_DUN, create("track_station"));
		Caravans.Data data = Caravans.Data.get(level);
		Four v = new Four(helper.absolutePos(HALL_PINE), helper.absolutePos(HALL_MID), helper.absolutePos(HALL_FAR), helper.absolutePos(HALL_DUN), data);
		for (BlockPos hall : v.halls()) {
			data.remove(hall);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
		}
		name(level, v.pine(), "Pine");
		name(level, v.mid(), middle);
		name(level, v.far(), "Oakford");
		name(level, v.dun(), "Dunmore");
		data.setWants(v.pine(), Component.literal("Pine"), List.of()); // the hall's first round puts it on the caravans' list
		data.setWants(v.mid(), Component.literal(middle), List.of());
		data.setWants(v.far(), Component.literal("Oakford"), List.of());
		data.setWants(v.dun(), Component.literal("Dunmore"), List.of());
		route(data, v.pine(), v.far());
		route(data, v.mid(), v.dun());
		for (BlockPos hall : v.halls()) {
			CaravanTrains.noteStations(level, hall, data);
		}
		helper.assertTrue(own(data, v.pine(), helper.absolutePos(STATION_PINE)) && own(data, v.mid(), helper.absolutePos(STATION_MID))
			&& own(data, v.far(), helper.absolutePos(STATION_FAR)) && own(data, v.dun(), helper.absolutePos(STATION_DUN)),
			"setup: a village's nearest station isn't its own: " + v.halls().stream().map(h -> data.stations(h)).toList());
		helper.assertTrue(CaravanTrains.rides(data, v.pine(), v.far()) && CaravanTrains.rides(data, v.mid(), v.dun()),
			"setup: the two pairs aren't on a railway");
		return v;
	}

	private static boolean own(Caravans.Data data, BlockPos hall, BlockPos station) {
		return !data.stations(hall).isEmpty() && data.stations(hall).get(0).equals(station);
	}

	private static void name(ServerLevel level, BlockPos hall, String name) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setCustomName(Component.literal(name)); // a name tag on the hall
	}

	private static void route(Caravans.Data data, BlockPos a, BlockPos b) {
		data.toggleRoute(a, b);
		BlockPos meet = new BlockPos((a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
		data.setHalf(a, b, new Caravans.Half(meet, true, false, true));
		data.setHalf(b, a, new Caravans.Half(meet, true, false, true));
		data.setTracked(a, b, true);
		data.setTracked(b, a, true);
	}

	/** Every hall's look at its trains, and the Pine-Oakford train's blocks put up as its builder would. */
	private static void round(ServerLevel level, Four v) {
		BlockPos hall = CaravanTrains.first(v.pine(), v.far()) ? v.pine() : v.far();
		BlockPos other = hall.equals(v.pine()) ? v.far() : v.pine();
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		List<BlockPos> ours = v.data().stations(hall);
		List<BlockPos> theirs = v.data().stations(other);
		if (!CaravanTrains.realTrain(level, v.pine(), v.far()) && r.linked(level, ours.get(0), theirs.get(0)) && !r.built(level, ours.get(0), theirs.get(0))) {
			java.util.Map<BlockPos, BlockState> parts = r.train(level, ours.get(0), theirs.get(0));
			if (parts != null) {
				parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
			}
		}
		for (BlockPos h : v.halls()) {
			CaravanTrains.serve(level, h, (VillageHallBlockEntity) level.getBlockEntity(h));
		}
	}

	private static String names(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		return "Pine's station '" + QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_PINE)) + "', the middle village's '"
			+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_MID)) + "', the far Oakford's '"
			+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_FAR)) + "', Dunmore's '"
			+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_DUN)) + "'";
	}

	private static void tidy(ServerLevel level, Four v, UUID train) {
		CreateTrainCompatTests.railway().scrap(level, train);
		for (BlockPos hall : v.halls()) {
			if (level.getBlockEntity(hall.east(3).north(2)) instanceof Container c) {
				c.clearContent();
			}
			level.removeBlock(hall.east(3).north(2), false);
			level.removeBlock(hall.east(3), false);
			level.removeBlock(hall, false);
			v.data().clearRoad(hall);
			v.data().remove(hall);
		}
	}

	/** Once every village has pointed its station at its track, the three stations on the one line have three names. */
	//$ gametest_ticks_batch AREA '1200' '"qaTrainSameNamesStations"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaTrainSameNamesStations")
	public void twoVillagesOfOneNameOnOneLineGetStationsOfTwoNames(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		Four v = villages(helper, "Oakford");
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 != 0) {
				return;
			}
			for (BlockPos h : v.halls()) {
				CaravanTrains.serve(level, h, (VillageHallBlockEntity) level.getBlockEntity(h));
			}
			String mid = QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_MID));
			String far = QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_FAR));
			String pine = QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_PINE));
			boolean named = !mid.equals("Track Station") && !far.equals("Track Station") && !pine.equals("Track Station") && !mid.startsWith("(")
				&& !far.startsWith("(") && !pine.startsWith("(");
			if (named && tick[0] >= 200) {
				helper.assertTrue(r.linked(level, helper.absolutePos(STATION_PINE), helper.absolutePos(STATION_FAR))
					&& r.linked(level, helper.absolutePos(STATION_PINE), helper.absolutePos(STATION_MID)), "setup: the three stations aren't on one network");
				helper.assertTrue(!mid.equals(far) && !mid.equals(pine) && !far.equals(pine),
					"two stations on one line share a name, so a train's schedule can't tell them apart: " + names(helper));
				tidy(level, v, UUID.randomUUID());
				helper.succeed();
			} else if (tick[0] >= 1100) {
				helper.fail("the villages never named their stations in 1100 ticks: " + names(helper));
			}
		});
	}

	/**
	 * Pine's goods for the far Oakford go by the Pine-Oakford train past the other Oakford's station: the train takes
	 * them to the far Oakford, where they come off into its storehouse and its chronicle says the train came in.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaTrainSameNamesRide"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaTrainSameNamesRide")
	public void theTrainToTheFarOakfordTakesItsGoodsThereNotToTheOtherOakford(GameTestHelper helper) {
		ride(helper, "Oakford");
	}

	/** The same line with the middle village called something else (Elmwood): the goods arrive (a control for the test above). */
	//$ gametest_ticks_batch AREA '3000' '"qaTrainOtherNamesRide"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaTrainOtherNamesRide")
	public void withTheMiddleVillageCalledElmwoodTheTrainTakesItsGoodsToOakford(GameTestHelper helper) {
		ride(helper, "Elmwood");
	}

	private static void ride(GameTestHelper helper, String middle) {
		ServerLevel level = helper.getLevel();
		Four v = villages(helper, middle);
		CreateTrainCompatTests.conductor(helper, new net.minecraft.core.BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new net.minecraft.core.BlockPos(23, 2, 16));
		UUID train = CaravanTrains.railwayId(v.pine(), v.far());
		boolean[] sent = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				round(level, v);
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.pine(), v.far())) {
					v.data().clearRoad(v.pine()); // the day's caravan sent now that the train runs (as CreateTrainCompatTests.sendAgain)
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
				} else if (tick[0] > 1000) {
					helper.fail("setup: no Pine-Oakford train was put together in 1000 ticks; " + names(helper));
				}
				return;
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.far())).findFirst().orElse(null);
			if (s == null && CreateTrainCompatTests.said(level, v.far(), "chronicle.aliveworkplace.train_came")) {
				int logs = 0;
				for (BlockPos h : v.halls()) {
					logs += ((Container) level.getBlockEntity(h.east(3).north(2))).countItem(Items.OAK_LOG);
				}
				helper.assertTrue(logs == 64, "logs were lost or doubled on the way: " + logs + " of 64 in the four chests");
				tidy(level, v, train);
				helper.succeed();
			} else if (tick[0] >= 2900) {
				helper.fail("Pine's goods never reached the far Oakford by train: caravan " + s + ", train: "
					+ CreateTrainCompatTests.railway().status(level, train) + " (standing at the middle village's station: "
					+ CreateTrainCompatTests.railway().at(level, train, helper.absolutePos(STATION_MID)) + "); " + names(helper));
			}
		});
	}
}
