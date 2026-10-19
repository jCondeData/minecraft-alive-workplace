package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 39.7 with the owner's Create port: two villages 140 blocks apart with a caravan route, the road between them
 * built (27.17), and a Create station each. A builder lays Create track down the middle of the village's half; a caravan
 * between them goes by train (a third of the trip) and arrives with its goods; without stations, or with the switch off,
 * caravans walk as before; a train is saved mid-trip; a station broken mid-trip leaves the caravan to walk the rest. And
 * a builder builds the Train Station. Without Create: {@code VillageTrainGameTests}.
 */
public class VillageTrainCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** The hall, in the test area: the road goes north (towards smaller z) from it, out of the area. */
	private static final BlockPos HALL = new BlockPos(14, 2, 26);
	/** How far the other village is, north. */
	private static final int APART = 140;
	private static final int SIDE = 9;

	/** Two villages with the road built between them: their halls, Ashford (in the area) and Bramble (north of it). */
	private record Villages(BlockPos a, BlockPos b, Caravans.Data data) {
	}

	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
		net.minecraft.server.level.TicketType.create("aliveworkplace_village_train_test", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

	/** Runs {@code reset} when the test ends, passed or failed. */
	private static void after(GameTestHelper helper, Runnable reset) {
		try {
			java.lang.reflect.Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			net.minecraft.gametest.framework.GameTestInfo info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
			info.addListener(new net.minecraft.gametest.framework.GameTestListener() {
				@Override
				public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {
				}

				@Override
				public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
					reset.run();
				}

				@Override
				public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) {
					reset.run();
				}

				@Override
				public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old, net.minecraft.gametest.framework.GameTestInfo test,
						net.minecraft.gametest.framework.GameTestRunner runner) {
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * The area flat grass, a corridor of flat grass north of it to past Bramble (its chunks held loaded until the test
	 * ends), the two halls with Ashford's route to Bramble, each half of the road planned and laid as its blueprint says,
	 * Ashford's storehouse chest with 64 oak logs (Bramble waits for 32) and Bramble's empty storehouse chest.
	 */
	private static Villages villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			BlockPos o = site.placement().origin();
			if (Math.abs(o.getX() - origin.getX()) < 80 && o.getZ() > origin.getZ() - APART - 100 && o.getZ() < origin.getZ() + 80) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			BlockPos o = f.placement().origin();
			if (Math.abs(o.getX() - origin.getX()) < 80 && o.getZ() > origin.getZ() - APART - 100 && o.getZ() < origin.getZ() + 80) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 14; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = a.offset(0, 0, -APART);
		int farZ = b.getZ() - 12;
		List<net.minecraft.world.level.ChunkPos> held = new ArrayList<>();
		for (int cx = (a.getX() - 64) >> 4; cx <= (a.getX() + 64) >> 4; cx++) {
			for (int cz = (farZ - 64) >> 4; cz <= (a.getZ() + 16) >> 4; cz++) {
				net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(cx, cz);
				level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos);
				held.add(pos);
				level.getChunk(cx, cz);
			}
		}
		for (int z = origin.getZ() - 1; z >= farZ; z--) {
			for (int x = a.getX() - SIDE; x <= a.getX() + SIDE; x++) {
				level.setBlock(new BlockPos(x, a.getY() - 2, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(new BlockPos(x, a.getY() - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 0; y <= 7; y++) {
					level.setBlock(new BlockPos(x, a.getY() + y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		level.setBlock(b, ModBlocks.VILLAGE_HALL.defaultBlockState(), Block.UPDATE_ALL);
		noStations(level, a);
		noStations(level, b);
		helper.setBlock(new BlockPos(20, 2, 26), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(20, 2, 28), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(20, 2, 28));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		level.setBlock(b.offset(6, 0, 0), ModBlocks.STOREHOUSE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(b.offset(6, 0, 2), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		Caravans.Data data = Caravans.Data.get(level);
		boolean trains = CaravanTrains.ENABLED;
		after(helper, () -> {
			CaravanTrains.ENABLED = trains;
			noStations(level, a);
			noStations(level, b);
			level.removeBlock(b.offset(6, 0, 2), false);
			level.removeBlock(b, false);
			data.clearRoad(a);
			data.remove(a);
			data.remove(b);
			held.forEach(pos -> level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos));
		});
		data.remove(a);
		data.remove(b);
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
		CaravanRoads.plan(level, a, hall(helper, a));
		CaravanRoads.plan(level, b, hall(helper, b));
		for (BlockPos hall : List.of(a, b)) {
			helper.assertTrue(Roads.routeNow(level, hall), "setup: the half's routing paused from " + hall);
		}
		layRoad(helper, a, b);
		layRoad(helper, b, a);
		helper.assertTrue(data.roadFinished(a, b), "setup: the road isn't finished");
		return new Villages(a, b, data);
	}

	/** Takes away every Create station within the hall's reach (an earlier test's, next door). */
	private static void noStations(ServerLevel level, BlockPos hall) {
		for (List<BlockPos> found = CaravanTrains.find(level, hall); !found.isEmpty(); found = CaravanTrains.find(level, hall)) {
			found.forEach(p -> level.removeBlock(p, false));
		}
	}

	/**
	 * Two villages 140 blocks apart up in the test's own column of the world, with their road built between them (both
	 * halves finished and meeting, as the caravans' list notes it once laid): Ashford 100 blocks over the area's floor,
	 * Bramble 140 over Ashford. Out of reach (64 blocks) of every other test's halls, storehouses, chests and stations,
	 * which in the full run stand round the area and along a road north of it while this test runs (a storehouse within a
	 * hall's reach is that village's store too, and takes the goods), so the trip and its goods are this test's alone. Each has a storehouse and a chest by its
	 * hall; Ashford's holds 64 oak logs, Bramble waits for 32.
	 */
	private static Villages skyVillages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL).above(100);
		BlockPos b = a.above(APART);
		Caravans.Data data = Caravans.Data.get(level);
		boolean trains = CaravanTrains.ENABLED;
		List<BlockPos> placed = new ArrayList<>();
		for (BlockPos hall : List.of(a, b)) {
			for (BlockPos p : BlockPos.betweenClosed(hall.offset(-8, -1, -8), hall.offset(8, 3, 8))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(hall.offset(6, 0, 0), ModBlocks.STOREHOUSE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(hall.offset(6, 0, 2), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
			placed.addAll(List.of(hall, hall.offset(6, 0, 0), hall.offset(6, 0, 2)));
			noStations(level, hall);
		}
		((Container) level.getBlockEntity(a.offset(6, 0, 2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		after(helper, () -> {
			CaravanTrains.ENABLED = trains;
			noStations(level, a);
			noStations(level, b);
			data.clearRoad(a);
			data.remove(a);
			data.remove(b);
			placed.forEach(p -> {
				if (level.getBlockEntity(p) instanceof Container c) {
					c.clearContent();
				}
				level.removeBlock(p, false);
			});
		});
		data.remove(a);
		data.remove(b);
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
		data.setHalf(a, b, new Caravans.Half(a, true, false, true));
		data.setHalf(b, a, new Caravans.Half(b, true, false, true)); // the same column: the halves meet
		helper.assertTrue(data.roadFinished(a, b), "setup: the road isn't finished");
		return new Villages(a, b, data);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	/** Places a blueprint's blocks as a builder would (block data too). */
	private static void place(ServerLevel level, Roads.Segment segment) {
		for (Blueprint.Entry e : segment.blueprint().blocks()) {
			BlockPos at = segment.placement().origin().offset(e.pos());
			level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
			BlockEntity be = level.getBlockEntity(at);
			if (e.nbt() != null && be != null) {
				CompoundTag tag = BlockEntityData.sanitize(level, at, e.state(), be, e.nbt());
				if (tag != null) {
					be.loadWithComponents(tag, level.registryAccess());
				}
			}
		}
	}

	/** Lays every segment of the hall's half of the road to {@code other}, each marked built as a builder's would be. */
	private static void layRoad(GameTestHelper helper, BlockPos hall, BlockPos other) {
		ServerLevel level = helper.getLevel();
		CityPlan.Road road = CaravanRoads.roadTo(hall(helper, hall).plan(), hall, other);
		helper.assertTrue(road != null && road.routed(), "setup: no road with its way found from " + hall);
		for (int n = 0; n < road.segments(); n++) {
			road = CaravanRoads.roadTo(hall(helper, hall).plan(), hall, other);
			Optional<Roads.Segment> segment = Roads.segment(level, hall, road, n);
			helper.assertTrue(segment.isPresent(), "setup: segment " + n + " has no blueprint");
			place(level, segment.get());
			finish(level, segment.get());
		}
	}

	/** A site for {@code segment} finished, as {@code Builders} ends one. */
	private static void finish(ServerLevel level, Roads.Segment segment) {
		BuildSite site = BuildSiteManager.get(level).create(java.util.UUID.randomUUID(), "", segment.blueprint().id(), segment.placement());
		Roads.save(level, segment.blueprint());
		Roads.segmentBuilt(level, site, null);
		BuildSiteManager.get(level).remove(site.id());
	}

	/** A Create station beside each hall, noted on the list by each hall's round. */
	private static void stations(GameTestHelper helper, Villages v) {
		ServerLevel level = helper.getLevel();
		BlockState station = BuiltInRegistries.BLOCK.get(CaravanTrains.STATION).defaultBlockState();
		for (BlockPos hall : List.of(v.a(), v.b())) {
			level.setBlock(hall.offset(-5, 0, 2), station, Block.UPDATE_ALL);
			CaravanTrains.noteStations(level, hall, v.data());
			helper.assertTrue(v.data().stations(hall).equals(List.of(hall.offset(-5, 0, 2))), "the station by " + hall + " isn't noted: " + v.data().stations(hall));
		}
	}

	/** Lays the track along the hall's half to {@code other} from its blueprint, as a builder would, and finishes the site. */
	private static void layTrack(GameTestHelper helper, Villages v, BlockPos hall, BlockPos other) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CaravanTrains.due(level, hall, other, hall(helper, hall).plan(), v.data()), "no track due from " + hall);
		Roads.Segment track = CaravanTrains.blueprint(level, hall, other, CaravanRoads.roadTo(hall(helper, hall).plan(), hall, other)).orElseThrow();
		place(level, track);
		finish(level, track);
		helper.assertTrue(v.data().tracked(hall, other), "the track from " + hall + " isn't marked laid");
	}

	private static boolean said(GameTestHelper helper, BlockPos hall, String key) {
		return hall(helper, hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CARAVAN
			&& e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static Caravans.Shipment sent(GameTestHelper helper, Villages v) {
		ServerLevel level = helper.getLevel();
		Caravans.round(helper.getLevel(), v.a(), null);
		return v.data().onTheRoad().stream().filter(x -> x.from().equals(v.a()) && x.to().equals(v.b())).findFirst()
			.orElseThrow(() -> new GameTestAssertException("no caravan left Ashford (day " + level.getDayTime() + ")"));
	}

	private static int logsIn(ServerLevel level, BlockPos chest) {
		Container c = (Container) level.getBlockEntity(chest);
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			n += c.getItem(i).is(Items.OAK_LOG) ? c.getItem(i).getCount() : 0;
		}
		return n;
	}

	/** The walk on the finished road (three quarters of the trip), and the train's third of it. */
	private static long walking() {
		return Caravans.travelTicks(APART, true);
	}

	/**
	 * Two villages with stations and the road between them: track is due on each half; Ashford's idle builder takes the
	 * track site and lays it from the bench's barrel, Create track down the middle of the road; Bramble's half is laid
	 * too; both chronicles say the railway is open. Before the stations stand, no track is due.
	 */
	//$ gametest_ticks_batch AREA '6000' '"villageTrainTrack"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "villageTrainTrack")
	public void aBuilderLaysTrackAlongTheRoadBetweenTwoStations(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Villages v = villages(helper);
		helper.assertTrue(!CaravanTrains.due(level, v.a(), v.b(), hall(helper, v.a()).plan(), v.data()), "track due without stations");
		helper.assertTrue(CaravanTrains.open(level, v.a(), hall(helper, v.a())) == null, "a track site opened without stations");
		stations(helper, v);
		BlockPos bench = new BlockPos(4, 2, 22);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 23));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSite site = CaravanTrains.open(level, v.a(), hall(helper, v.a()));
		helper.assertTrue(site != null && builder.getUUID().equals(site.builder()), "the idle builder didn't take the track site: " + site);
		helper.assertTrue(CaravanTrains.open(level, v.a(), hall(helper, v.a())) == null, "a second track site opened");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "the track site has no blueprint");
		Item track = BuiltInRegistries.ITEM.get(CaravanTrains.TRACK);
		int needed = plan.materials().getOrDefault(track, 0);
		CityPlan.Road road = CaravanRoads.roadTo(hall(helper, v.a()).plan(), v.a(), v.b());
		helper.assertTrue(needed >= road.route().size() - 6 && plan.materials().size() == 1, "the track site wants " + plan.materials() + " for " + road.route().size() + " nodes");
		helper.setBlock(new BlockPos(3, 2, 22), Blocks.BARREL);
		Container barrel = helper.getBlockEntity(new BlockPos(3, 2, 22));
		for (int i = 0; needed > 0; i++, needed -= 64) {
			barrel.setItem(i, new ItemStack(track, Math.min(64, needed)));
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still laying: " + site.stage() + " " + site.status() + " missing " + site.missing());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			helper.assertTrue(v.data().tracked(v.a(), v.b()), "Ashford's half isn't marked tracked");
			helper.assertTrue(said(helper, v.a(), "chronicle.aliveworkplace.train_track_laid"), "the chronicle doesn't say the track was laid");
			// Create track down the middle of the road, along it (north-south), a node halfway along Ashford's half
			CityPlan.Road half = CaravanRoads.roadTo(hall(helper, v.a()).plan(), v.a(), v.b());
			BlockPos node = half.route().get(half.route().size() / 2).offset(v.a());
			BlockState there = level.getBlockState(node);
			helper.assertTrue(CaravanTrains.isTrack(there) && "zo".equals(there.getValues().entrySet().stream()
				.filter(e -> e.getKey().getName().equals("shape")).map(e -> e.getValue().toString().toLowerCase(java.util.Locale.ROOT)).findFirst().orElse("")),
				"no straight Create track at " + node + ": " + there);
			helper.assertTrue(!level.getBlockState(node.below()).isAir(), "the track floats");
			helper.assertTrue(!CaravanTrains.rides(v.data(), v.a(), v.b()), "a train with one half tracked");
			layTrack(helper, v, v.b(), v.a());
			helper.assertTrue(CaravanTrains.rides(v.data(), v.a(), v.b()) && CaravanTrains.rides(v.data(), v.b(), v.a()), "no train with both halves tracked");
			helper.assertTrue(said(helper, v.b(), "chronicle.aliveworkplace.railway_open") && said(helper, v.a(), "chronicle.aliveworkplace.railway_open"),
				"the railway's opening isn't in both chronicles");
			helper.assertTrue(!CaravanTrains.due(level, v.a(), v.b(), hall(helper, v.a()).plan(), v.data()), "track due again once laid");
		});
	}

	/**
	 * Stations at both ends and track on both halves: Ashford's caravan goes by train, a third of the walk, and the
	 * chronicle says so; it comes in at Bramble on time with all its goods, into Bramble's storehouse chest.
	 */
	//$ gametest_ticks_batch AREA '1500' '"villageTrainRide"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "villageTrainRide")
	public void aCaravanBetweenTwoStationsGoesByTrainAndArrivesWithItsGoods(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villages v = skyVillages(helper);
		stations(helper, v);
		v.data().setTracked(v.a(), v.b(), true); // laid as aBuilderLaysTrackAlongTheRoadBetweenTwoStations proves
		v.data().setTracked(v.b(), v.a(), true);
		long now = level.getGameTime();
		Caravans.Shipment s = sent(helper, v);
		long train = CaravanTrains.trainTrip(walking());
		helper.assertTrue(s.byTrain() && s.arrives() - now == train && train < walking(),
			"the caravan isn't on the train: byTrain " + s.byTrain() + ", " + (s.arrives() - now) + " ticks (walking " + walking() + ")");
		helper.assertTrue(s.goods().size() == 1 && s.goods().get(0).is(Items.OAK_LOG) && s.goods().get(0).getCount() == 32, "the train carries " + s.goods());
		helper.assertTrue(said(helper, v.a(), "chronicle.aliveworkplace.train_left"), "Ashford's chronicle doesn't say a train left");
		BlockPos chest = v.b().offset(6, 0, 2);
		helper.runAfterDelay(train - 20, () -> {
			Caravans.round(level, v.b(), null);
			helper.assertTrue(logsIn(level, chest) == 0, "the train came in early");
		});
		helper.runAfterDelay(train + 2, () -> {
			Caravans.round(level, v.b(), null);
			helper.assertTrue(logsIn(level, chest) == 32, "Bramble's chest has " + logsIn(level, chest) + " logs, not the train's 32");
			helper.assertTrue(said(helper, v.b(), "chronicle.aliveworkplace.train_came"), "Bramble's chronicle doesn't say the train came in");
			helper.assertTrue(v.data().onTheRoad().stream().noneMatch(x -> x.to().equals(v.b())), "the caravan is still on its way");
			helper.succeed();
		});
	}

	/** No stations (the track laid, even): the caravan walks, the whole walk, as before 39.7; one station alone, the same. */
	//$ gametest_ticks_batch AREA '300' '"villageTrainWalk"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "villageTrainWalk")
	public void withoutStationsCaravansWalkAsToday(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villages v = skyVillages(helper);
		v.data().setTracked(v.a(), v.b(), true);
		v.data().setTracked(v.b(), v.a(), true);
		long now = level.getGameTime();
		Caravans.Shipment s = sent(helper, v);
		helper.assertTrue(!s.byTrain() && s.arrives() - now == walking(), "without stations: byTrain " + s.byTrain() + ", " + (s.arrives() - now) + " ticks, not " + walking());
		helper.assertTrue(said(helper, v.a(), "chronicle.aliveworkplace.caravan_left") && !said(helper, v.a(), "chronicle.aliveworkplace.train_left"), "not told as a walking caravan");
		v.data().clearRoad(v.a());
		BlockState station = BuiltInRegistries.BLOCK.get(CaravanTrains.STATION).defaultBlockState();
		level.setBlock(v.a().offset(-5, 0, 2), station, Block.UPDATE_ALL);
		CaravanTrains.noteStations(level, v.a(), v.data());
		CaravanTrains.noteStations(level, v.b(), v.data());
		helper.assertTrue(!v.data().stations(v.a()).isEmpty() && v.data().stations(v.b()).isEmpty() && !CaravanTrains.rides(v.data(), v.a(), v.b()),
			"a train with a station at one end only");
		helper.succeed();
	}

	/**
	 * Stations at both ends and the road routed, but the village's half of it not finished yet: no track is due (the
	 * track goes down a finished road); once the half is finished, it is.
	 */
	//$ gametest_ticks_batch AREA '300' '"villageTrainHalfUnfinished"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "villageTrainHalfUnfinished")
	public void noTrackIsDueBeforeTheVillagesHalfOfTheRoadIsFinished(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villages v = villages(helper);
		stations(helper, v);
		Caravans.Half half = v.data().half(v.a(), v.b());
		helper.assertTrue(half != null && half.finished(), "setup: Ashford's half of the road isn't finished: " + half);
		v.data().setHalf(v.a(), v.b(), new Caravans.Half(half.end(), false, half.milestone(), half.noted()));
		helper.assertTrue(!CaravanTrains.due(level, v.a(), v.b(), hall(helper, v.a()).plan(), v.data()),
			"track due with Ashford's half of the road unfinished");
		v.data().setHalf(v.a(), v.b(), half);
		helper.assertTrue(CaravanTrains.due(level, v.a(), v.b(), hall(helper, v.a()).plan(), v.data()), "no track due once the half is finished");
		helper.succeed();
	}

	/** {@code villageTrains} off: the Train Station isn't at the table, no track is due or opened, and the caravan walks. */
	//$ gametest_ticks_batch AREA '300' '"villageTrainOff"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "villageTrainOff")
	public void withVillageTrainsOffCaravansWalkAndNoTrackIsLaid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villages v = skyVillages(helper);
		stations(helper, v);
		List<ResourceLocation> listed = TableServer.listing(level.getServer()).stream().map(e -> e.id()).toList();
		helper.assertTrue(listed.contains(StarterBlueprints.TRAIN_STATION.id()), "the Train Station isn't at the table with Create");
		CaravanTrains.ENABLED = false;
		helper.assertTrue(!CaravanTrains.listed(StarterBlueprints.TRAIN_STATION.id()), "the Train Station is listed with the switch off");
		helper.assertTrue(!TableServer.listing(level.getServer()).stream().map(e -> e.id()).toList().contains(StarterBlueprints.TRAIN_STATION.id()),
			"the Train Station is at the table with the switch off");
		helper.assertTrue(!CaravanTrains.due(level, v.a(), v.b(), hall(helper, v.a()).plan(), v.data()), "track due with the switch off");
		helper.assertTrue(CaravanTrains.open(level, v.a(), hall(helper, v.a())) == null, "a track site opened with the switch off");
		v.data().setTracked(v.a(), v.b(), true); // laid before it was turned off
		v.data().setTracked(v.b(), v.a(), true);
		long now = level.getGameTime();
		Caravans.Shipment s = sent(helper, v);
		helper.assertTrue(!s.byTrain() && s.arrives() - now == walking(), "with the switch off the caravan takes the train");
		helper.succeed();
	}

	/**
	 * A train saved and loaded mid-trip (the caravans' list written and read back, as on a server restart) is still on the
	 * train, due at the same tick, with its goods; and it comes in with them.
	 */
	//$ gametest_ticks_batch AREA '1500' '"villageTrainReload"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "villageTrainReload")
	public void aTrainSavedAndReloadedMidTripStillArrives(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villages v = skyVillages(helper);
		stations(helper, v);
		v.data().setTracked(v.a(), v.b(), true); // laid as aBuilderLaysTrackAlongTheRoadBetweenTwoStations proves
		v.data().setTracked(v.b(), v.a(), true);
		Caravans.Shipment s = sent(helper, v);
		helper.runAfterDelay(100, () -> {
			Caravans.Data loaded = Caravans.Data.load(v.data().save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			Caravans.Shipment back = loaded.onTheRoad().stream().filter(x -> x.from().equals(v.a()) && x.to().equals(v.b())).findFirst()
				.orElseThrow(() -> new GameTestAssertException("the train didn't come back from the save"));
			helper.assertTrue(back.byTrain() && back.arrives() == s.arrives() && back.goods().size() == 1 && back.goods().get(0).getCount() == 32,
				"the train changed over the save: " + back);
			helper.assertTrue(loaded.stations(v.a()).equals(v.data().stations(v.a())) && loaded.tracked(v.a(), v.b()) && loaded.tracked(v.b(), v.a()),
				"the stations or the track didn't come back from the save");
			helper.assertTrue(CaravanTrains.rides(loaded, v.a(), v.b()), "no train after the reload");
		});
		BlockPos chest = v.b().offset(6, 0, 2);
		helper.runAfterDelay(s.arrives() - level.getGameTime() + 2, () -> {
			Caravans.round(level, v.b(), null);
			helper.assertTrue(logsIn(level, chest) == 32, "after the reload Bramble's chest has " + logsIn(level, chest) + " logs, not 32");
			helper.succeed();
		});
	}

	/**
	 * Bramble's station is broken while the train is on its way: at Bramble's next round the caravan gets off and walks
	 * the rest (no longer on the train, coming in no sooner than the train would have, a quarter of the walk from then),
	 * the chronicle says so, and it still arrives with all its goods; new caravans walk.
	 */
	//$ gametest_ticks_batch AREA '1500' '"villageTrainBroken"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "villageTrainBroken")
	public void aStationBrokenMidTripTheCaravanWalksTheRest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villages v = skyVillages(helper);
		stations(helper, v);
		v.data().setTracked(v.a(), v.b(), true); // laid as aBuilderLaysTrackAlongTheRoadBetweenTwoStations proves
		v.data().setTracked(v.b(), v.a(), true);
		Caravans.Shipment s = sent(helper, v);
		helper.assertTrue(s.byTrain(), "setup: not on the train");
		BlockPos chest = v.b().offset(6, 0, 2);
		helper.runAfterDelay(100, () -> {
			level.destroyBlock(v.b().offset(-5, 0, 2), false);
			long now = level.getGameTime();
			Caravans.round(level, v.b(), null);
			helper.assertTrue(v.data().stations(v.b()).isEmpty(), "the broken station is still noted");
			Caravans.Shipment walking = v.data().onTheRoad().stream().filter(x -> x.to().equals(v.b())).findFirst()
				.orElseThrow(() -> new GameTestAssertException("the caravan is gone from the road"));
			long rest = Caravans.trip(level, v.a(), v.b(), v.data()) / 4;
			helper.assertTrue(!walking.byTrain() && walking.arrives() == Math.max(s.arrives(), now + rest),
				"the caravan didn't get off to walk the rest: byTrain " + walking.byTrain() + ", due " + (walking.arrives() - now) + " ticks from now, not " + rest);
			helper.assertTrue(walking.goods().equals(s.goods()) || walking.goods().get(0).getCount() == 32, "the caravan lost goods getting off");
			helper.assertTrue(said(helper, v.b(), "chronicle.aliveworkplace.train_walked"), "Bramble's chronicle doesn't say the caravan walks the rest");
			helper.assertTrue(!CaravanTrains.rides(v.data(), v.a(), v.b()), "trains still run to a village with no station");
			helper.assertTrue(logsIn(level, chest) == 0, "the goods came in at once");
			helper.runAfterDelay(walking.arrives() - level.getGameTime() + 2, () -> {
				Caravans.round(level, v.b(), null);
				helper.assertTrue(logsIn(level, chest) == 32, "Bramble's chest has " + logsIn(level, chest) + " logs, not the caravan's 32");
				helper.assertTrue(said(helper, v.b(), "chronicle.aliveworkplace.caravan_came"), "not told as a caravan coming in on foot");
				helper.succeed();
			});
		});
	}

	/** A builder builds the Train Station from barrels holding exactly its materials: the station beside straight track. */
	//$ gametest_ticks_batch AREA '30000' '"trainStationBuild"'
	@GameTest(template = AREA, timeoutTicks = 30000, batch = "trainStationBuild")
	public void aBuilderBuildsTheTrainStation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		StarterBlueprints.Entry entry = StarterBlueprints.TRAIN_STATION;
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(12)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			helper.assertTrue(CaravanTrains.isStation(level.getBlockState(placement.origin().offset(6, 1, 4))), "no station at 6,1,4");
			for (int x = 0; x < 13; x++) {
				helper.assertTrue(CaravanTrains.isTrack(level.getBlockState(placement.origin().offset(x, 1, 6))), "no track at " + x + ",1,6");
			}
			// A hall by it notes the station
			BlockPos hall = helper.absolutePos(new BlockPos(14, 2, 26));
			helper.assertTrue(CaravanTrains.find(level, hall).contains(placement.origin().offset(6, 1, 4)), "the station isn't found from a hall nearby");
		});
	}
}
