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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, Create, 2026-11-03; 39.7a to 39.7d): a village in the middle of a Create line trading by train with a
 * neighbor on each side, the common shape of a server's villages (a hub). The hub has one station, so both of its
 * village trains use it: one comes in from the west, the other from the east. Both trains are put together and run, and
 * each neighbor's goods for the hub arrive by train, nothing lost or doubled. The line is
 * {@link CreateTrainCompatTests#line}'s (stations at x = 6 and x = 23) with the hub's station between them.
 */
public class QaTrainHub1103eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos STATION_WEST = new BlockPos(6, 2, 13);
	static final BlockPos STATION_HUB = new BlockPos(15, 2, 13);
	static final BlockPos STATION_EAST = new BlockPos(23, 2, 13);
	private static final BlockPos HALL_WEST = new BlockPos(6, 2, 22);
	private static final BlockPos HALL_HUB = new BlockPos(15, 2, 22);
	private static final BlockPos HALL_EAST = new BlockPos(23, 2, 22);

	record Three(BlockPos west, BlockPos hub, BlockPos east, Caravans.Data data) {
		List<BlockPos> halls() {
			return List.of(west, hub, east);
		}
	}

	private static BlockState create(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	static Three villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreateTrainCompatTests.line(helper);
		helper.setBlock(STATION_HUB, create("track_station"));
		Caravans.Data data = Caravans.Data.get(level);
		Three v = new Three(helper.absolutePos(HALL_WEST), helper.absolutePos(HALL_HUB), helper.absolutePos(HALL_EAST), data);
		// B898: as CreateTrainCompatTests.villages, a Storehouse an earlier batch left outside this area, within a hall's
		// reach, would take the goods that come in (Caravans.storehouse)
		net.minecraft.world.phys.AABB area = helper.getBounds();
		for (BlockPos hall : v.halls()) {
			for (BlockPos store : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
					p -> !area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS,
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				level.removeBlock(store, false);
			}
		}
		String[] names = {"Westmere", "Middleton", "Eastwick"};
		for (int i = 0; i < 3; i++) {
			BlockPos hall = v.halls().get(i);
			data.remove(hall);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3).north(2), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
			((VillageHallBlockEntity) level.getBlockEntity(hall)).setCustomName(Component.literal(names[i]));
			data.setWants(hall, Component.literal(names[i]), List.of());
		}
		route(data, v.west(), v.hub());
		route(data, v.east(), v.hub());
		for (BlockPos hall : v.halls()) {
			CaravanTrains.noteStations(level, hall, data);
		}
		helper.assertTrue(own(data, v.west(), helper.absolutePos(STATION_WEST)) && own(data, v.hub(), helper.absolutePos(STATION_HUB))
			&& own(data, v.east(), helper.absolutePos(STATION_EAST)),
			"setup: a village's nearest station isn't its own: " + v.halls().stream().map(h -> data.stations(h)).toList());
		helper.assertTrue(CaravanTrains.rides(data, v.west(), v.hub()) && CaravanTrains.rides(data, v.east(), v.hub()),
			"setup: the hub and its neighbors aren't on a railway");
		return v;
	}

	private static boolean own(Caravans.Data data, BlockPos hall, BlockPos station) {
		return !data.stations(hall).isEmpty() && data.stations(hall).get(0).equals(station);
	}

	private static void route(Caravans.Data data, BlockPos a, BlockPos b) {
		data.toggleRoute(a, b);
		BlockPos meet = new BlockPos((a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
		data.setHalf(a, b, new Caravans.Half(meet, true, false, true));
		data.setHalf(b, a, new Caravans.Half(meet, true, false, true));
		data.setTracked(a, b, true);
		data.setTracked(b, a, true);
	}

	/**
	 * A pair's train blocks put up at the station of the village that puts it together, as its builder would (39.7b), at
	 * the pair's stations (B898: each facing the other village); a station facing the pair put up first, as the builder of
	 * a village whose stations all face another neighbor would (B898, {@link CaravanTrains#secondStation}).
	 */
	static void buildTrain(ServerLevel level, Caravans.Data data, BlockPos x, BlockPos y) {
		BlockPos hall = CaravanTrains.first(x, y) ? x : y;
		BlockPos other = hall.equals(x) ? y : x;
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		for (BlockPos h : List.of(hall, other)) {
			java.util.Map<BlockPos, BlockState> station = CaravanTrains.secondStation(level, h, h.equals(hall) ? other : hall, data);
			if (station != null) {
				station.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
				CaravanTrains.noteStations(level, h, data);
			}
		}
		BlockPos ours = CaravanTrains.stationFor(level, hall, other, data);
		BlockPos theirs = CaravanTrains.stationFor(level, other, hall, data);
		if (CaravanTrains.realTrain(level, x, y) || ours == null || theirs == null || !r.linked(level, ours, theirs) || r.built(level, ours, theirs)) {
			return;
		}
		java.util.Map<BlockPos, BlockState> parts = r.train(level, ours, theirs);
		if (parts != null) {
			parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
		}
	}

	static void round(ServerLevel level, Three v) {
		if (!CaravanTrains.realTrain(level, v.west(), v.hub())) {
			buildTrain(level, v.data(), v.west(), v.hub());
		} else {
			buildTrain(level, v.data(), v.east(), v.hub()); // one at a time, as the hub's own builder would
		}
		for (BlockPos h : v.halls()) {
			CaravanTrains.serve(level, h, (VillageHallBlockEntity) level.getBlockEntity(h));
		}
	}

	static int count(ServerLevel level, Three v, Item item) {
		int n = 0;
		for (BlockPos h : v.halls()) {
			n += ((Container) level.getBlockEntity(h.east(3).north(2))).countItem(item);
		}
		return n;
	}

	static void tidy(ServerLevel level, Three v) {
		CreateTrainCompatTests.railway().scrap(level, CaravanTrains.railwayId(v.west(), v.hub()));
		CreateTrainCompatTests.railway().scrap(level, CaravanTrains.railwayId(v.east(), v.hub()));
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

	/**
	 * Where the village train with this owner is (B939's look): each carriage's entity position, the train's speed and
	 * whether it's derailed; "no train" without one.
	 */
	static String where(UUID owner) {
		for (com.simibubi.create.content.trains.entity.Train train : com.simibubi.create.Create.RAILWAYS.trains.values()) {
			if (owner.equals(train.owner) && !train.invalid) {
				StringBuilder b = new StringBuilder("speed " + String.format(java.util.Locale.ROOT, "%.3f", train.speed) + ", derailed " + train.derailed + ", carriages");
				for (com.simibubi.create.content.trains.entity.Carriage c : train.carriages) {
					net.minecraft.world.entity.Entity e = c.anyAvailableEntity();
					b.append(' ').append(e == null ? "(no entity)" : String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", e.getX(), e.getY(), e.getZ()));
				}
				return b.toString();
			}
		}
		return "no train";
	}

	static String trains(ServerLevel level, Three v) {
		CaravanTrains.Railway r = CreateTrainCompatTests.railway();
		return "west train: " + r.status(level, CaravanTrains.railwayId(v.west(), v.hub())) + "; east train: "
			+ r.status(level, CaravanTrains.railwayId(v.east(), v.hub()));
	}

	/**
	 * Both trains of the hub put together; then Westmere's 32 logs and Eastwick's 32 wheat sent to the hub, each by its
	 * own train: both arrive, the hub's chronicle says a train came in, and the chests hold every log and wheat once.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaTrainHub"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTrainHub")
	public void aVillageBetweenTwoNeighborsGetsEachOnesGoodsByItsOwnTrain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		Three v = villages(helper);
		CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(15, 2, 16));
		CreateTrainCompatTests.conductor(helper, new BlockPos(23, 2, 16));
		boolean[] sent = {false};
		int[] tick = {0};
		int[] sentAt = {0};
		int[] eastWaiting = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				round(level, v);
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
					List<Caravans.Shipment> out = v.data().onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
					helper.assertTrue(out.size() == 2 && out.stream().allMatch(Caravans.Shipment::byTrain),
						"setup: the hub's two neighbors don't each send a caravan by train: " + out);
					sent[0] = true;
					sentAt[0] = tick[0];
				} else if (tick[0] > 2000) {
					helper.fail("the hub's two village trains weren't both put together in 2000 ticks: " + trains(level, v)
						+ "; stations " + QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_WEST)) + " / "
						+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_HUB)) + " / "
						+ QaTrainStationNames1102CompatTests.name(level, helper.absolutePos(STATION_EAST)));
				}
				return;
			}
			if (CreateTrainCompatTests.railway().status(level, CaravanTrains.railwayId(v.east(), v.hub())).contains("PRE_TRANSIT")) {
				eastWaiting[0]++;
			}
			List<Caravans.Shipment> left = v.data().onTheRoad().stream().filter(s -> s.to().equals(v.hub())).toList();
			if (left.isEmpty()) {
				helper.assertTrue(count(level, v, Items.OAK_LOG) == 64 && count(level, v, Items.WHEAT) == 64, "goods were lost or doubled: "
					+ count(level, v, Items.OAK_LOG) + " logs and " + count(level, v, Items.WHEAT) + " wheat of 64 each in the chests");
				helper.assertTrue(CreateTrainCompatTests.said(level, v.hub(), "chronicle.aliveworkplace.train_came"),
					"the hub's chronicle doesn't say a train came in");
				tidy(level, v);
				helper.succeed();
			} else if (tick[0] >= 3900) {
				helper.fail("the neighbors' goods never all reached the hub in " + (tick[0] - sentAt[0]) + " ticks after they were sent (the east train waiting to set out for "
					+ eastWaiting[0] + " of them): still on the way " + left + "; " + trains(level, v));
			}
		});
	}
}
