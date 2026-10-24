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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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

/**
 * QA (lane E, 2026-10-24), B359 with the owner's Create port: config {@code villageTrains} switched off while Create stays
 * installed. The fix's decision: caravans stopped for the village train take the timed trip; goods riding in the train's
 * chest come out of it (once), and when the chest holds none of them any more (the train broken up) the caravan is dropped
 * with the chronicle's "broken up" line. Written from the bug's Expected and the fix's decision, not from the code: the
 * real village train is put together on Create track, as in {@code CreateTrainCompatTests}, whose set-up this copies.
 */
public class QaTrainsOffCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int TRACK_Z = 12;
	private static final BlockPos STATION_A = new BlockPos(6, 2, TRACK_Z + 1);
	private static final BlockPos STATION_B = new BlockPos(23, 2, TRACK_Z + 1);
	private static final BlockPos HALL_A = new BlockPos(6, 2, 22);
	private static final BlockPos HALL_B = new BlockPos(23, 2, 22);
	private static final String LEFT = "chronicle.aliveworkplace.train_goods_left";

	/**
	 * The village train runs with Bramble's 32 logs in its chest when the owner switches {@code villageTrains} off: the logs
	 * come out of the train's chest, the caravan takes the timed trip and comes in (none left in the train, none twice,
	 * none lost: Ashford's 64 always add up), with no "broken up" line in either chronicle.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaTrainsOffInChest"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTrainsOffInChest")
	public void switchedOffWithTheGoodsInTheTrainsChestTheyArriveOnceByTheTimedTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean trains = CaravanTrains.ENABLED;
		PartnerShowsCompatTests.after(helper, () -> {
			CaravanTrains.ENABLED = trains;
			if (CaravanTrains.RAILWAY.present()) {
				railway().scrap(level, owner);
			}
			tidy(level, v);
		});
		boolean[] sent = {false};
		boolean[] off = {false};
		int[] tick = {0};
		int[] done = {-1};
		int[] rounds = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (!off[0]) {
				if (tick[0] % 10 == 0) {
					buildTrain(level, v);
					CaravanTrains.serve(level, v.a(), hall(level, v.a()));
					CaravanTrains.serve(level, v.b(), hall(level, v.b()));
				}
				if (!sent[0]) {
					if (CaravanTrains.realTrain(level, v.a(), v.b())) {
						sendAgain(level, v);
						sent[0] = true;
					} else if (tick[0] > 600) {
						helper.fail("no village train was put together in 600 ticks");
					}
					return;
				}
				Caravans.Shipment s = toBramble(v);
				if (s != null && s.arrives() == CaravanTrains.IN_CHEST) {
					helper.assertTrue(logs(railway().cargo(level, owner)) == 32, "setup: the logs aren't in the train's chest: " + railway().cargo(level, owner));
					CaravanTrains.ENABLED = false; // the owner switches villageTrains off
					helper.assertTrue(!CaravanTrains.live(), "villageTrains off, but trains still run");
					off[0] = true;
				} else if (tick[0] > 2400) {
					helper.fail("the caravan never boarded with its goods in the chest: " + s);
				}
				return;
			}
			if (tick[0] % 10 == 0) {
				Caravans.round(level, v.a(), null);
				Caravans.round(level, v.b(), null);
				rounds[0]++;
			}
			Caravans.Shipment s = toBramble(v);
			helper.assertTrue(s == null || !CaravanTrains.waiting(s.arrives()) || rounds[0] == 0,
				"a round with villageTrains off left the caravan waiting for the train: " + s);
			helper.assertTrue(!said(level, v.a(), LEFT) && !said(level, v.b(), LEFT),
				"the chronicle says the train was broken up, but it still runs with the logs in its chest");
			int bramble = chest(level, v.b()).countItem(Items.OAK_LOG);
			int all = bramble + chest(level, v.a()).countItem(Items.OAK_LOG) + logs(railway().cargo(level, owner))
				+ (s == null || s.arrives() == CaravanTrains.IN_CHEST ? 0 : logs(s.goods())); // in the chest: its record lists what the chest holds
			helper.assertTrue(all == 64, "the logs add up to " + all + ", not Ashford's 64 (Bramble " + bramble + ", train "
				+ railway().cargo(level, owner) + ", caravan " + s + ")");
			if (done[0] < 0 && s == null) { // it came in (the two halls' storehouses share their chests: they stand 17 blocks apart)
				helper.assertTrue(logs(railway().cargo(level, owner)) == 0, "logs left in the train's chest: " + railway().cargo(level, owner));
				done[0] = tick[0];
			}
			if (done[0] >= 0 && tick[0] >= done[0] + 200) { // and nothing more comes in the rounds after
				helper.succeed();
			}
		});
	}

	/**
	 * A world saved with two caravans stopped for the village train, opened with {@code villageTrains} off and no train
	 * left on the line (Create still installed): the one waiting to board brings its 16 logs by the timed trip; the one
	 * whose 16 logs were in the train's chest is dropped with one "broken up" line, and its logs never turn up at Bramble.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaTrainsOffNoTrain"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaTrainsOffNoTrain")
	public void switchedOffWithNoTrainLeftTheBoardingCaravanArrivesAndTheChestOneIsWrittenOff(GameTestHelper helper)
			throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		boolean trains = CaravanTrains.ENABLED;
		PartnerShowsCompatTests.after(helper, () -> {
			CaravanTrains.ENABLED = trains;
			tidy(level, v);
		});
		CaravanTrains.ENABLED = false;
		v.data().clearRoad(v.a());
		chest(level, v.b()).clearContent();
		chest(level, v.a()).clearContent(); // Ashford has nothing more to send: only the two saved caravans are on the road
		v.data().setWants(v.b(), Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 64)));
		java.lang.reflect.Method ship = Caravans.Data.class.getDeclaredMethod("ship", Caravans.Shipment.class);
		ship.setAccessible(true);
		ship.invoke(v.data(), new Caravans.Shipment(v.a(), v.b(), List.of(new ItemStack(Items.OAK_LOG, 16)), CaravanTrains.BOARDING, List.of(), false, true));
		ship.invoke(v.data(), new Caravans.Shipment(v.a(), v.b(), List.of(new ItemStack(Items.OAK_LOG, 16)), CaravanTrains.IN_CHEST, List.of(), false, true));
		helper.assertTrue(!CaravanTrains.realTrain(level, v.a(), v.b()), "setup: a village train stands on the line");
		int[] tick = {0};
		int[] done = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				Caravans.round(level, v.a(), null);
				Caravans.round(level, v.b(), null);
			}
			// the two halls' storehouses share their chests (they stand 17 blocks apart): what came in is in either
			int bramble = chest(level, v.b()).countItem(Items.OAK_LOG) + chest(level, v.a()).countItem(Items.OAK_LOG);
			int road = v.data().onTheRoad().stream().filter(x -> (x.to().equals(v.b()) || x.to().equals(v.a())) && x.arrives() != CaravanTrains.IN_CHEST).mapToInt(x -> logs(x.goods())).sum();
			helper.assertTrue(bramble + road <= 16, bramble + " logs came in and " + road + " are on the road: the 16 that were in the train's chest (gone with it) came too");
			int lines = lines(level, v.a(), LEFT) + lines(level, v.b(), LEFT);
			helper.assertTrue(lines <= 1, "the chronicles say " + lines + " times that the train was broken up");
			if (done[0] < 0 && bramble == 16 && road == 0 && lines == 1) {
				done[0] = tick[0];
			}
			if (done[0] >= 0 && tick[0] >= done[0] + 200) {
				helper.succeed();
			}
			if (tick[0] == 2300) {
				helper.fail(bramble + " of the boarding caravan's 16 logs came in, " + lines + " 'broken up' line(s); on the road: "
					+ v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).map(x -> x.goods() + "@" + x.arrives()).toList());
			}
		});
	}

	// ---- The line and the villages (as in CreateTrainCompatTests) --------------------------------------------------

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

	private record Villages(BlockPos a, BlockPos b, Caravans.Data data) {
	}

	private static Villages villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		net.minecraft.world.phys.AABB own = helper.getBounds();
		for (BlockPos hall : List.of(a, b)) {
			for (BlockPos store : level.getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
					p -> !own.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS,
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				level.removeBlock(store, false);
			}
		}
		for (BlockPos hall : List.of(a, b)) {
			data.remove(hall);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
			level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
		}
		chest(level, a).setItem(0, new ItemStack(Items.OAK_LOG, 64));
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
		helper.assertTrue(CaravanTrains.rides(data, a, b), "setup: the two villages aren't on a railway: stations " + data.stations(a) + " / " + data.stations(b));
		return new Villages(a, b, data);
	}

	private static void sendAgain(ServerLevel level, Villages v) {
		v.data().clearRoad(v.a());
		chest(level, v.a()).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		chest(level, v.b()).clearContent();
		v.data().setWants(v.a(), Component.literal("Ashford"), List.of());
		v.data().setWants(v.b(), Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		v.data().sent(v.a(), -1);
		Caravans.round(level, v.a(), null);
	}

	private static void buildTrain(ServerLevel level, Villages v) {
		BlockPos hall = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		BlockPos other = hall.equals(v.a()) ? v.b() : v.a();
		List<BlockPos> ours = v.data().stations(hall);
		List<BlockPos> theirs = v.data().stations(other);
		if (ours.isEmpty() || theirs.isEmpty() || CaravanTrains.realTrain(level, v.a(), v.b()) || !railway().linked(level, ours.get(0), theirs.get(0))
			|| railway().built(level, ours.get(0), theirs.get(0))) {
			return;
		}
		java.util.Map<BlockPos, BlockState> parts = railway().train(level, ours.get(0), theirs.get(0));
		if (parts != null) {
			parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
		}
	}

	private static void tidy(ServerLevel level, Villages v) {
		for (BlockPos hall : List.of(v.a(), v.b())) {
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

	private static Container chest(ServerLevel level, BlockPos hall) {
		return (Container) level.getBlockEntity(hall.east(3).north(2));
	}

	private static Caravans.Shipment toBramble(Villages v) {
		return v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
	}

	private static int logs(List<ItemStack> stacks) {
		return stacks.stream().filter(st -> st.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
	}

	private static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos) {
		return (VillageHallBlockEntity) level.getBlockEntity(pos);
	}

	private static int lines(ServerLevel level, BlockPos hall, String key) {
		return (int) hall(level, hall).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CARAVAN
			&& e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key)).count();
	}

	private static boolean said(ServerLevel level, BlockPos hall, String key) {
		return lines(level, hall, key) > 0;
	}
}
