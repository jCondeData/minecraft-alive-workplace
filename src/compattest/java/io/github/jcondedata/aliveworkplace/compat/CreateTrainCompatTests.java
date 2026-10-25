package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
	static void line(GameTestHelper helper) {
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

	static CaravanTrains.Railway railway() {
		return CaravanTrains.RAILWAY.call(r -> r, null);
	}

	static Villager conductor(GameTestHelper helper, BlockPos at) {
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
					helper.assertTrue(r.assemble(level, a, b, owner, driver) == null, "the station assembled a train nobody built");
					java.util.Map<BlockPos, BlockState> parts = r.train(level, a, b);
					helper.assertTrue(parts != null && parts.size() == CaravanTrains.trainBlocks(level).size() && parts.size() > 6,
							"no room for the drawn train's blocks past the station: " + parts); // 39.7c: the architect's drawing, not six blocks
					parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state)); // as the village's builder puts them up (39.7b)
					helper.assertTrue(r.built(level, a, b), "the train's blocks put up don't count as built");
					helper.assertTrue(r.assemble(level, a, b, owner, driver) != null, "the station didn't assemble the village train");
					built[0] = true;
					helper.assertTrue(r.exists(level, owner) && r.at(level, owner, a), "the train isn't standing at the station it was assembled at");
					BlockPos track = helper.absolutePos(STATION_A).north();
					helper.assertTrue(level.getBlockState(track.east().above()).isAir() && level.getBlockState(track.west().above()).isAir(),
						"the train's blocks stayed in the world after the assembly");
					// 39.7b: the train's chest takes goods (all or none) and gives them back
					helper.assertTrue(r.load(level, owner, List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.WHEAT, 5))), "the goods didn't go into the train's chest");
					helper.assertTrue(logs(r.cargo(level, owner)) == 32, "the train's chest doesn't hold the logs: " + r.cargo(level, owner));
					helper.assertTrue(!r.load(level, owner, List.of(new ItemStack(Items.STONE, 64 * 27))), "more went into the chest than it holds");
					helper.assertTrue(r.cargo(level, owner).stream().noneMatch(st -> st.is(Items.STONE)), "a load that didn't fit left some in the chest");
				} else if (tick[0] > 200) {
					helper.fail("Create's railway doesn't know both stations on one network after 200 ticks (aimed " + aimed + ")");
				}
			} else if (tick[0] == 1100) {
				helper.fail("the train didn't reach the other station: " + r.status(level, owner));
			} else if (r.at(level, owner, b)) {
				helper.assertTrue(r.status(level, owner).contains("conductors true/true"), "the conductor isn't in the train: " + r.status(level, owner));
				helper.assertTrue(logs(r.cargo(level, owner)) == 32, "the logs didn't ride in the chest to the other station: " + r.cargo(level, owner));
				List<ItemStack> out = r.unload(level, owner, List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.WHEAT, 9)));
				helper.assertTrue(logs(out) == 32 && out.stream().filter(st -> st.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum() == 5,
					"the chest didn't give back what it held (and no more): " + out);
				helper.assertTrue(r.cargo(level, owner).isEmpty(), "goods stayed in the chest: " + r.cargo(level, owner));
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
				buildTrain(level, v);
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
			if (s != null && CaravanTrains.aboard(s.arrives())) {
				aboard[0] = true;
				if (reload && !reloaded[0]) {
					Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
					Caravans.Shipment back = loaded.onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
					helper.assertTrue(back != null && back.byTrain() && back.arrives() == s.arrives() && back.goods().get(0).getCount() == 32,
						"the caravan aboard changed over the save: " + back);
					reloaded[0] = true;
				}
			}
			// both storehouses are in each hall's reach here, so the logs may go into either chest: none lost, and the caravan in
			if (sent[0] && s == null && said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				int logs = chest.countItem(Items.OAK_LOG) + ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, "logs were lost on the way: " + logs + " of 64 in the two chests (" + chest.countItem(Items.OAK_LOG) + " at Bramble), train's chest "
					+ railway().cargo(level, owner) + ", arrivals " + seen + ", Bramble's chests " + Caravans.storehouse(level, v.b()).stream()
					.map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)) + "=" + ((Container) level.getBlockEntity(p)).countItem(Items.OAK_LOG)).toList());
				helper.assertTrue(aboard[0], "the goods arrived without riding the train: arrivals " + seen + " at tick " + tick[0] + ", train " + railway().status(level, owner));
				helper.assertTrue(!reload || reloaded[0], "the caravan was never saved while aboard");
				helper.assertTrue(said(level, v.b(), "chronicle.aliveworkplace.train_came"), "the other village's chronicle doesn't say the train came in");
				railway().scrap(level, owner);
				tidy(level, v);
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
				buildTrain(level, v);
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
				tidy(level, v);
				helper.succeed();
			}
		});
	}

	// ---- 39.7b: a stepped road, the builder's train, the goods in its chest -------------------------------------------

	private static final BlockPos STEPPED_A = new BlockPos(3, 2, TRACK_Z + 1);
	private static final BlockPos STEPPED_B = new BlockPos(24, 3, TRACK_Z + 1);

	/** The road's feet height at {@code x} on the stepped line: up a block at x = 10 and x = 14, down one at x = 18. */
	private static int feet(int x) {
		return x <= 9 ? 2 : x <= 13 ? 3 : x <= 17 ? 4 : 3;
	}

	/**
	 * A flat stone floor with a stepped bank along z = 10..14, the road's way along it (x = 1..28, feet height), Create
	 * track laid on it as {@link CaravanTrains#plan} says (ascending pieces where it steps), and a station by each end.
	 */
	private static void steppedLine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				int top = z >= 10 && z <= 14 ? feet(x) - 1 : 1;
				for (int y = 1; y < 10; y++) {
					helper.setBlock(new BlockPos(x, y, z), y <= top ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		List<BlockPos> route = new java.util.ArrayList<>();
		for (int x = 1; x <= 28; x++) {
			route.add(helper.absolutePos(new BlockPos(x, feet(x), TRACK_Z)));
		}
		CaravanTrains.TrackPlan plan = CaravanTrains.plan(route, p -> true);
		// one slope up both steps (x = 7 to 16, two blocks up) and one down (x = 16 to 20), straight pieces on the rest
		helper.assertTrue(plan.curves().equals(List.of(
			new CaravanTrains.Curve(helper.absolutePos(new BlockPos(7, 2, TRACK_Z)), net.minecraft.core.Direction.EAST, helper.absolutePos(new BlockPos(16, 4, TRACK_Z)), net.minecraft.core.Direction.WEST),
			new CaravanTrains.Curve(helper.absolutePos(new BlockPos(16, 4, TRACK_Z)), net.minecraft.core.Direction.EAST, helper.absolutePos(new BlockPos(20, 3, TRACK_Z)), net.minecraft.core.Direction.WEST))),
			"no slopes where the way steps: " + plan.curves());
		helper.assertTrue(plan.shapes().size() == 7 + 1 + 9, "the straight pieces aren't on the level ends: " + plan.shapes().size());
		BlockState track = state("track");
		plan.shapes().forEach((pos, shape) -> helper.getLevel().setBlockAndUpdate(pos, track.setValue(prop(track, "shape"), value(track, "shape", shape))));
		for (CaravanTrains.Curve curve : plan.curves()) { // as the track site's end joins them (CaravanTrains.trackBuilt)
			helper.assertTrue(railway().curve(helper.getLevel(), curve.a(), curve.outA(), curve.b(), curve.outB()), "Create's curve didn't join " + curve);
		}
		helper.setBlock(STEPPED_A, state("track_station")); // three blocks of straight track past each station towards the other, for the train
		helper.setBlock(STEPPED_B, state("track_station"));
	}

	/** The Builder's Bench, its barrel with the train's parts (or none) and its builder, by the halls. */
	private static void builder(GameTestHelper helper, boolean parts) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(14, 2, 20);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(13, 2, 20), Blocks.BARREL);
		if (parts) {
			Container barrel = helper.getBlockEntity(new BlockPos(13, 2, 20));
			barrel.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "railway_casing")), 3));
			barrel.setItem(4, new ItemStack(Items.SPRUCE_FENCE, 3)); // 39.7c: the drawn train's posts, canopy and lantern
			barrel.setItem(5, new ItemStack(Items.SPRUCE_SLAB, 4));
			barrel.setItem(6, new ItemStack(Items.LANTERN, 1));
			barrel.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "controls")), 2));
			barrel.setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "brown_seat")), 1));
			barrel.setItem(3, new ItemStack(Items.CHEST, 1));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 21));
		Builders.employ(level, builder, helper.absolutePos(bench));
	}

	static int logs(List<ItemStack> stacks) {
		return stacks.stream().filter(st -> st.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
	}

	/**
	 * The Done when of 39.7b: two villages on a road that steps up twice and down once, Create track laid along it (one
	 * network: both stations join it), the first village's builder builds the train from its blueprint out of the bench's
	 * barrel, the station assembles it, and the caravan's 32 logs go into the train's chest at the departing station and
	 * come out of it at the arriving one, into the storehouse there.
	 */
	//$ gametest_ticks_batch AREA '4800' '"createTrainStepped"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "createTrainStepped")
	public void aTrainOnASteppedRoadRunsWithTheGoodsInItsChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		steppedLine(helper);
		Villages v = villages(helper);
		builder(helper, true);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		BlockPos first = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		boolean[] sent = {false};
		boolean[] filled = {false};
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
					helper.assertTrue(said(level, first, "chronicle.aliveworkplace.train_built"), "the chronicle doesn't say a builder built the train");
					helper.assertTrue(railway().cargo(level, owner).isEmpty(), "the new train's chest isn't empty: " + railway().cargo(level, owner));
					sendAgain(level, v);
					sent[0] = true;
				} else if (tick[0] == 3000) {
					helper.fail("no train after 3000 ticks: stations linked " + railway().linked(level, helper.absolutePos(STEPPED_A),
						helper.absolutePos(STEPPED_B)) + ", sites " + Roads.openSegments(level, first).stream()
						.map(st -> st.structure() + " " + st.stage() + " " + st.status() + " missing " + st.missing()).toList());
				}
				return;
			}
			Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (tick[0] == 4700) {
				helper.fail("the goods didn't arrive: caravan " + s + ", train: " + railway().status(level, owner) + ", chest " + railway().cargo(level, owner));
			}
			if (s != null && s.arrives() == CaravanTrains.IN_CHEST && !filled[0]) {
				helper.assertTrue(logs(railway().cargo(level, owner)) == 32, "the caravan's logs aren't in the train's chest: " + railway().cargo(level, owner));
				filled[0] = true;
			}
			if (s == null && said(level, v.b(), "chronicle.aliveworkplace.train_came")) {
				helper.assertTrue(filled[0], "the goods arrived without riding in the train's chest");
				helper.assertTrue(logs(railway().cargo(level, owner)) == 0, "logs stayed in the train's chest: " + railway().cargo(level, owner));
				int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
					+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(logs == 64, "logs were lost or doubled on the way: " + logs + " of 64 in the two chests");
				railway().scrap(level, owner);
				tidy(level, v);
				helper.succeed();
			}
		});
	}

	/**
	 * The train's parts missing from the bench: the builder's train site waits for them (railway casing among them), no
	 * train is assembled and the caravan takes the timed trip. With village trains switched off, no train site opens.
	 */
	//$ gametest_ticks_batch AREA '1200' '"createTrainNoParts"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "createTrainNoParts")
	public void withoutTheTrainsPartsNoTrainAndTheCaravanTakesTheTimedTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		boolean was = CaravanTrains.ENABLED;
		CaravanTrains.ENABLED = false;
		builder(helper, false);
		conductor(helper, new BlockPos(6, 2, 16));
		BlockPos first = CaravanTrains.first(v.a(), v.b()) ? v.a() : v.b();
		for (int i = 0; i < 10; i++) {
			CaravanTrains.serve(level, first, hall(level, first));
		}
		helper.assertTrue(Roads.openSegments(level, first).stream().noneMatch(st -> CaravanTrains.isTrainSite(st.structure())), "a train site opened with village trains off");
		CaravanTrains.ENABLED = was;
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0) {
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
				CaravanTrains.serve(level, v.b(), hall(level, v.b()));
			}
			if (tick[0] < 900) {
				return;
			}
			helper.assertTrue(!CaravanTrains.realTrain(level, v.a(), v.b()), "a train was assembled without its parts");
			BuildSite site = Roads.openSegments(level, first).stream().filter(st -> CaravanTrains.isTrainSite(st.structure())).findFirst().orElse(null);
			helper.assertTrue(site != null, "no train site opened for the builder");
			helper.assertTrue(site.missing().containsKey(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "railway_casing"))),
				"the train site doesn't wait for railway casing: " + site.missing());
			sendAgain(level, v);
			for (int i = 0; i < 2; i++) {
				CaravanTrains.serve(level, v.a(), hall(level, v.a()));
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			helper.assertTrue(s != null && s.byTrain() && !CaravanTrains.waiting(s.arrives()), "the caravan doesn't take the timed trip: " + s);
			BuildSiteManager.get(level).remove(site.id());
			tidy(level, v);
			helper.succeed();
		});
	}

	/**
	 * The train broken up while the caravan's goods ride in its chest: they stay with the train (a player breaking it up
	 * at a station gets its chest back with them), never a second time with the caravan: it's taken off the road and the
	 * chronicle says so.
	 */
	//$ gametest_ticks_batch AREA '2400' '"createTrainBrokenUp"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "createTrainBrokenUp")
	public void theTrainBrokenUpMidTripKeepsTheGoodsInItsChestNeverTwice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] sent = {false};
		boolean[] scrapped = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
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
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			if (!scrapped[0]) {
				if (s != null && s.arrives() == CaravanTrains.IN_CHEST) {
					helper.assertTrue(logs(railway().cargo(level, owner)) == 32, "the logs aren't in the train's chest: " + railway().cargo(level, owner));
					railway().scrap(level, owner);
					scrapped[0] = true;
				} else if (tick[0] == 2000) {
					helper.fail("the caravan never boarded with its goods in the chest: " + s);
				}
				return;
			}
			if (CaravanTrains.realTrain(level, v.a(), v.b())) {
				return; // Create takes it off on its next tick
			}
			if (s == null && said(level, v.a(), "chronicle.aliveworkplace.train_goods_left")) {
				int logs = ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
					+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				int bramble = ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
				helper.assertTrue(bramble == 0 && logs <= 64, "the goods left in the train came again: " + logs + " logs in the chests, " + bramble + " at Bramble");
				tidy(level, v);
				helper.succeed();
			} else if (s != null && !CaravanTrains.waiting(s.arrives())) {
				helper.fail("the caravan went on with goods that stayed in the train's chest: " + s);
			}
		});
	}

	/**
	 * B214: the other village's hall taken away while the caravan waits at our station for the train: it doesn't wait on
	 * for a train to a village that's gone, nor go on to it; it comes home at once with its logs, and none are lost.
	 */
	//$ gametest_ticks_batch AREA '1200' '"createTrainHallGoneBoarding"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "createTrainHallGoneBoarding")
	public void theOtherHallGoneWhileTheCaravanWaitsAtTheStationSendsItHome(GameTestHelper helper) {
		hallGoneAtTheStation(helper, false, true);
	}

	/**
	 * B214: the other village's hall taken away while the caravan's logs ride in the train's chest: they come back out of
	 * the chest once and the caravan walks them home; the train's chest keeps none and they never come a second time.
	 */
	//$ gametest_ticks_batch AREA '2400' '"createTrainHallGoneInChest"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "createTrainHallGoneInChest")
	public void theOtherHallGoneWhileTheGoodsRideInTheChestBringsThemHomeOnce(GameTestHelper helper) {
		hallGoneAtTheStation(helper, true, true);
	}

	/**
	 * B214: the sending village's own hall taken away while its caravan waits at its station for the train: nobody serves
	 * that station's train now, so the caravan walks on to where it was going instead of waiting there forever.
	 */
	//$ gametest_ticks_batch AREA '1200' '"createTrainSenderGone"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "createTrainSenderGone")
	public void theSendersHallGoneWhileTheCaravanWaitsAtTheStationItWalksOn(GameTestHelper helper) {
		hallGoneAtTheStation(helper, false, false);
	}

	/** The logs in both villages' chests. */
	static int chestLogs(ServerLevel level, Villages v) {
		return ((Container) level.getBlockEntity(v.a().east(3).north(2))).countItem(Items.OAK_LOG)
			+ ((Container) level.getBlockEntity(v.b().east(3).north(2))).countItem(Items.OAK_LOG);
	}

	/** The caravans on the road to or from either village. */
	static List<Caravans.Shipment> road(Villages v) {
		return v.data().onTheRoad().stream().filter(s -> s.to().equals(v.a()) || s.to().equals(v.b()) || s.from().equals(v.a()) || s.from().equals(v.b())).toList();
	}

	/**
	 * B214: a caravan from Ashford to Bramble stopped for the village train (waiting to board, or {@code inChest} with its
	 * logs in the train's chest) when a hall is taken away: Bramble's ({@code destination}) or Ashford's own.
	 */
	private static void hallGoneAtTheStation(GameTestHelper helper, boolean inChest, boolean destination) {
		ServerLevel level = helper.getLevel();
		line(helper);
		Villages v = villages(helper);
		conductor(helper, new BlockPos(6, 2, 16));
		UUID owner = CaravanTrains.railwayId(v.a(), v.b());
		boolean[] sent = {false};
		boolean[] gone = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 10 == 0 && !gone[0]) {
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
			if (gone[0]) {
				return;
			}
			Caravans.Shipment s = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst().orElse(null);
			helper.assertTrue(s != null && s.byTrain() && CaravanTrains.waiting(s.arrives()), "the caravan isn't stopped for the train: " + s);
			if (inChest && s.arrives() != CaravanTrains.IN_CHEST) {
				if (tick[0] == 2000) {
					helper.fail("the caravan never boarded with its goods in the chest: " + s);
				}
				return;
			}
			if (inChest) {
				helper.assertTrue(logs(railway().cargo(level, owner)) == 32, "the logs aren't in the train's chest: " + railway().cargo(level, owner));
			} else {
				helper.assertTrue(s.arrives() == CaravanTrains.BOARDING, "the caravan should still wait to board: " + s);
			}
			gone[0] = true;
			BlockPos removed = destination ? v.b() : v.a();
			BlockPos stays = destination ? v.a() : v.b();
			long now = level.getGameTime();
			level.removeBlock(removed, false);
			helper.assertTrue(v.data().village(removed) == null, "the hall taken away is still on the list");
			List<Caravans.Shipment> road = road(v);
			helper.assertTrue(road.size() == 1, "one caravan should be on the road: " + road);
			Caravans.Shipment after = road.get(0);
			helper.assertTrue(after.to().equals(stays) && !after.byTrain() && !CaravanTrains.waiting(after.arrives()) && after.back() == destination
				&& logs(after.goods()) == 32, "the caravan should get off the train and walk to " + (destination ? "Ashford" : "Bramble") + ": " + after);
			helper.assertTrue(destination && !inChest ? after.arrives() <= now : after.arrives() > now,
				"the caravan's arrival is off: " + after.arrives() + " at " + now);
			helper.assertTrue(logs(railway().cargo(level, owner)) == 0, "logs were left in the train's chest: " + railway().cargo(level, owner));
			v.data().hurry(stays);
			Caravans.round(level, stays, null);
			int logs = chestLogs(level, v);
			helper.assertTrue(logs == 64, "the logs should all be back in the chests once: " + logs + " of 64, train's chest " + railway().cargo(level, owner));
			helper.assertTrue(road(v).isEmpty(), "nothing should be left on the road: " + road(v));
			if (!destination) {
				helper.assertTrue(said(level, v.b(), "chronicle.aliveworkplace.caravan_came"), "Bramble's chronicle doesn't say the caravan came");
			}
			railway().scrap(level, owner);
			tidy(level, v);
			helper.succeed();
		});
	}

	record Villages(BlockPos a, BlockPos b, Caravans.Data data) {
	}

	/** Two halls with a storehouse chest each, a caravan route, the road between them built and tracked, and the stations noted. */
	static Villages villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		// A hall's goods go into the chests by every Storehouse within its reach (Caravans.storehouse): one an earlier
		// batch left outside this area (CuisineTreatsCompatTests, MegaStoneCompatTests) took the train's 32 logs once
		// the batches' order put it within reach of the arriving hall.
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
	static void sendAgain(ServerLevel level, Villages v) {
		v.data().clearRoad(v.a());
		((Container) level.getBlockEntity(v.a().east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		((Container) level.getBlockEntity(v.b().east(3).north(2))).clearContent();
		v.data().setWants(v.a(), Component.literal("Ashford"), List.of());
		v.data().setWants(v.b(), Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		v.data().sent(v.a(), -1);
		Caravans.round(level, v.a(), null);
	}

	/**
	 * The train's blocks put up at the first village's station as its builder would (39.7b; the builder itself:
	 * {@link #aTrainOnASteppedRoadRunsWithTheGoodsInItsChest}), once Create knows both stations on one network.
	 */
	static void buildTrain(ServerLevel level, Villages v) {
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

	/**
	 * Takes the two villages away again (halls, storehouses and their emptied chests, and their entries on the list): the
	 * next batch's test areas stand within a hall's reach of this one, and a storehouse left here would take their goods.
	 */
	static void tidy(ServerLevel level, Villages v) {
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

	static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos) {
		return (VillageHallBlockEntity) level.getBlockEntity(pos);
	}

	static boolean said(ServerLevel level, BlockPos hall, String key) {
		return hall(level, hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CARAVAN
			&& e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}
}
