package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * ROADMAP 39.7a with the owner's Create port: real Create trains. A straight line of Create track with a station near
 * each end: Create's railway knows both stations on one network; a village train (bogey, casing, chest, controls both
 * ways and a villager conductor in the seat, glued) is assembled by the station and, with its Create schedule, runs to
 * the other station; a caravan between two villages rides it (waits at the station, boards, comes off at the other
 * village's station) and its goods arrive; a caravan aboard is saved and reloaded and still arrives; with the train gone
 * the caravan takes the timed trip. The timed trip and walking: {@code VillageTrainCompatTests}.
 */
public class CreateTrainCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** The track runs along x at this z, y = 2 (on the floor), from x = 1 to x = 28. */
	private static final int TRACK_Z = 12;
	private static final BlockPos STATION_A = new BlockPos(6, 2, TRACK_Z + 1);
	private static final BlockPos STATION_B = new BlockPos(23, 2, TRACK_Z + 1);
	private static final BlockPos HALL_A = new BlockPos(6, 2, 22);
	private static final BlockPos HALL_B = new BlockPos(23, 2, 22);

	/** A flat stone floor, the track and the two stations. */
	private static void line(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y < 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockState track = state("track");
		track = track.setValue(prop(track, "shape"), value(track, "shape", "xo"));
		for (int x = 1; x <= 28; x++) {
			helper.setBlock(new BlockPos(x, 2, TRACK_Z), track);
		}
		helper.setBlock(STATION_A, state("track_station"));
		helper.setBlock(STATION_B, state("track_station"));
	}

	private static BlockState state(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static net.minecraft.world.level.block.state.properties.Property prop(BlockState state, String name) {
		return state.getBlock().getStateDefinition().getProperty(name);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Comparable value(BlockState state, String name, String value) {
		return (Comparable) prop(state, name).getValue(value).orElseThrow();
	}

	private static CaravanTrains.Railway railway() {
		return CaravanTrains.RAILWAY.call(r -> r, null);
	}

	private static Villager conductor(GameTestHelper helper, BlockPos at) {
		Villager villager = EntityType.VILLAGER.spawn(helper.getLevel(), helper.absolutePos(at), MobSpawnType.COMMAND);
		villager.setNoAi(true);
		return villager;
	}

	/** Track and stations: Create knows both stations on one network, and a train put together at one runs to the other on its schedule. */
	//$ gametest_ticks_batch AREA '1200' '"createTrainRuns"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "createTrainRuns")
	public void aVillageTrainIsAssembledAtAStationAndRunsToTheOther(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.RAILWAY.present(), "nothing fills in Create's trains with Create installed");
		line(helper);
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		UUID owner = UUID.randomUUID(); // a train of an earlier run may still be on the railway
		Villager driver = conductor(helper, new BlockPos(6, 2, 16));
		boolean[] built = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = railway();
			if (!built[0]) {
				boolean aimed = r.aim(level, a, b, "Alpha") & r.aim(level, b, a, "Beta");
				if (aimed && r.linked(level, a, b)) {
					helper.assertTrue(r.assemble(level, a, b, owner, driver) != null, "the station didn't assemble the village train");
					built[0] = true;
					helper.assertTrue(r.exists(level, owner) && r.at(level, owner, a), "the train isn't standing at the station it was assembled at");
					BlockPos track = helper.absolutePos(STATION_A).north();
					helper.assertTrue(level.getBlockState(track.east().above()).isAir() && level.getBlockState(track.west().above()).isAir(),
						"the train's blocks stayed in the world after the assembly");
				} else if (tick[0] > 200) {
					helper.fail("Create's railway doesn't know both stations on one network after 200 ticks (aimed " + aimed + ")");
				}
			} else if (tick[0] == 1100) {
				helper.fail("the train didn't reach the other station: " + r.status(level, owner));
			} else if (r.at(level, owner, b)) {
				helper.assertTrue(r.status(level, owner).contains("conductors true/true"), "the conductor isn't in the train: " + r.status(level, owner));
				r.scrap(level, owner);
				helper.succeed();
			}
		});
	}

	/** Two villages with the stations and the track: the train is put together with a jobless villager, the caravan rides it and arrives. */
	//$ gametest_ticks_batch AREA '2400' '"createTrainCaravan"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "createTrainCaravan")
	public void aCaravanRidesTheVillageTrainAndItsGoodsArrive(GameTestHelper helper) {
		caravanByTrain(helper, false);
	}

	/** The same, saved and reloaded while the caravan is aboard: it still arrives with its goods. */
	//$ gametest_ticks_batch AREA '2400' '"createTrainReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "createTrainReload")
	public void aCaravanAboardSavedAndReloadedStillArrives(GameTestHelper helper) {
		caravanByTrain(helper, true);
	}

	private static void caravanByTrain(GameTestHelper helper, boolean reload) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		Container chest = (Container) level.getBlockEntity(v.b().east(3).north(2));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] sent = {false};
		boolean[] aboard = {false};
		List<Long> seen = new java.util.ArrayList<>();
		boolean[] reloaded = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			Caravans.Data data = v.data();
			if (tick[0] % 10 == 0) {
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			if (!sent[0]) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					helper.assertTrue(said(level, v.a(), "chronicle.aliveworkplace.train_assembled") || said(level, v.b(), "chronicle.aliveworkplace.train_assembled"),
						"neither chronicle says the train was put together: "
						+ hall(level, v.a()).chronicle().stream().map(e -> e.text().getString()).toList());
					sendAgain(level, v);
					Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(s != null && s.byTrain() && CaravanTrains.waiting(s.arrives()),
						"the caravan doesn't wait for the village train: " + s);
					sent[0] = true;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks");
				}
				return;
			}
			Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (tick[0] == 2300) {
				helper.fail("the goods didn't arrive: caravan " + s + ", train: " + railway().status(level, owner));
			}
			if (s != null && (seen.isEmpty() || seen.get(seen.size() - 1) != s.arrives())) {
				seen.add(s.arrives());
			}
			if (s != null && s.arrives() == CaravanTrains.ABOARD) {
				aboard[0] = true;
				if (reload && !reloaded[0]) {
					Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
					Caravans.Shipment back = loaded.onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(back != null && back.byTrain() && back.arrives() == CaravanTrains.ABOARD && back.goods().get(0).getCount() == 32,
						"the caravan aboard changed over the save: " + back);
					reloaded[0] = true;
				}
			}
			// both storehouses are in each hall's reach here, so the logs may go into either chest: none lost, and the caravan in
			if (sent[0] && s == null && said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = chest.countItem(Items.OAK_LOG) + ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, "logs were lost on the way: " + logs + " of 64 in the two chests");
				helper.assertTrue(aboard[0], "the goods arrived without riding the train: arrivals " + seen + " at tick " + tick[0] + ", train " + railway().status(level, owner));
				helper.assertTrue(!reload || reloaded[0], "the caravan was never saved while aboard");
				helper.assertTrue(said(level, v.b(), "chronicle.aliveworkplace.train_came"), "the other village's chronicle doesn't say the train came in");
				railway().scrap(level, owner);
				helper.succeed();
			}
		});
	}

	/** The village train gone (a player broke it up): the caravan waiting for it takes the timed train trip instead. */
	//$ gametest_ticks_batch AREA '1200' '"createTrainGone"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "createTrainGone")
	public void withTheTrainGoneTheCaravanTakesTheTimedTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] scrapped = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			if (!scrapped[0]) {
				if (CaravanTrains.realTrain(level, v.a(), v.b())) {
					sendAgain(level, v);
					railway().scrap(level, owner);
					scrapped[0] = true;
				} else if (tick[0] > 600) {
					helper.fail("no village train was put together in 600 ticks: " + hall(level, v.a()).chronicle().stream().map(e -> e.text().getString()).toList());
				}
				return;
			}
			if (CaravanTrains.realTrain(level, v.a(), v.b())) {
				return; // Create takes it off on its next tick
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (s != null && !CaravanTrains.waiting(s.arrives())) {
				helper.assertTrue(s.byTrain() && s.arrives() > level.getGameTime(), "the caravan didn't take the timed trip: " + s);
				helper.succeed();
			}
		});
	}

	private record Villages(BlockPos a, BlockPos b, Caravans.Data data) {
	}

	/** Two halls with a storehouse chest each, a caravan route, the road between them built and tracked, and the stations noted. */
	private static Villages villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		for (BlockPos hall : List.of(a, b)) {
			data.remove(hall);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
		}
		((Container) level.getBlockEntity(a.east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		data.toggleRoute(a, b);
		BlockPos meet = a.offset(8, 0, 0);
		data.setHalf(a, b, new Caravans.Half(meet, true, false, true));
		data.setHalf(b, a, new Caravans.Half(meet, true, false, true));
		data.setTracked(a, b, true);
		data.setTracked(b, a, true);
		CaravanTrains.noteStations(level, a, data);
		CaravanTrains.noteStations(level, b, data);
		helper.assertTrue(CaravanTrains.rides(data, a, b), "the two villages aren't on a railway: stations " + data.stations(a) + " / " + data.stations(b));
		return new Villages(a, b, data);
	}

	/**
	 * Today's caravan sent now that the train runs: the hall's own first round (its first tick) may have sent one already,
	 * before there was a train; that one is taken off the road, its logs put back, and the day's caravan sent again.
	 */
	private static void sendAgain(ServerLevel level, Villages v) {
		v.data().clearRoad(v.a());
		((Container) level.getBlockEntity(v.a().east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		((Container) level.getBlockEntity(v.b().east(3).north(2))).clearContent();
		v.data().setWants(v.a(), Component.literal("Ashford"), List.of());
		v.data().setWants(v.b(), Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		v.data().sent(v.a(), -1);
		Caravans.round(level, v.a(), null);
	}

	private static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos) {
		return (VillageHallBlockEntity) level.getBlockEntity(pos);
	}

	private static boolean said(ServerLevel level, BlockPos hall, String key) {
		return hall(level, hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CARAVAN
			&& e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}
}
