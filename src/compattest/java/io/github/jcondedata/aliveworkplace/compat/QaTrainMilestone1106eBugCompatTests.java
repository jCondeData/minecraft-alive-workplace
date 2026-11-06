package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-e-1106-2212, 39.7 with the owner's Create port), from docs/guide/create-trains.md: "a road that ends at a
 * milestone: caravans walk". Two villages 140 blocks apart with {@code caravanRoadReach} at its lowest (32): each half
 * stops at a milestone 32 blocks out, leaving a gap of about 76 blocks in the middle, so the road is never one and no
 * train can run. Both villages have a Create station. The guide promises the caravans walk (they do); the chronicle
 * must not tell the player "The railway to ... is open: caravans go by train" for a railway with a gap in it.
 */
public class QaTrainMilestone1106eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 26);
	private static final int APART = 140;
	private static final int SIDE = 9;

	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
		net.minecraft.server.level.TicketType.create("aliveworkplace_qa_train_milestone", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

	/**
	 * Both halves built to their milestones and both tracked (the builder's track site finished on each): the road isn't
	 * finished, caravans don't ride, and neither chronicle says the railway is open.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTrainMilestone1106e"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTrainMilestone1106e")
	public void halvesEndingAtMilestonesAreNoRailwayInTheChronicle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		int reach = CaravanRoads.REACH;
		CaravanRoads.REACH = 32; // caravanRoadReach's lowest
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			BlockPos o = site.placement().origin();
			if (Math.abs(o.getX() - origin.getX()) < 80 && o.getZ() > origin.getZ() - APART - 100 && o.getZ() < origin.getZ() + 80) {
				manager.remove(site.id());
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
		Caravans.Data data = Caravans.Data.get(level);
		after(helper, () -> {
			CaravanRoads.REACH = reach;
			noStations(level, a);
			noStations(level, b);
			level.removeBlock(b, false);
			data.clearRoad(a);
			data.clearRoad(b);
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
		CaravanRoads.plan(level, a, hall(helper, a));
		CaravanRoads.plan(level, b, hall(helper, b));
		Caravans.Half ab = data.half(a, b);
		Caravans.Half ba = data.half(b, a);
		helper.assertTrue(ab != null && ba != null && ab.finished() && ba.finished() && ab.milestone() && ba.milestone(),
			"setup: both halves aren't built to a milestone: " + ab + " / " + ba);
		helper.assertTrue(!data.roadFinished(a, b), "setup: halves 32 blocks out of villages 140 apart meet: " + ab + " / " + ba);

		// A Create station by each hall.
		BlockState station = BuiltInRegistries.BLOCK.get(CaravanTrains.STATION).defaultBlockState();
		for (BlockPos hall : List.of(a, b)) {
			level.setBlock(hall.offset(-5, 0, 2), station, Block.UPDATE_ALL);
			CaravanTrains.noteStations(level, hall, data);
			helper.assertTrue(data.stations(hall).equals(List.of(hall.offset(-5, 0, 2))), "setup: the station by " + hall + " isn't noted");
		}
		// The track on each half that's due, laid from its blueprint and its site finished, as a builder's would be.
		for (BlockPos[] pair : new BlockPos[][] {{a, b}, {b, a}}) {
			if (CaravanTrains.due(level, pair[0], pair[1], hall(helper, pair[0]).plan(), data)) {
				CityPlan.Road road = CaravanRoads.roadTo(hall(helper, pair[0]).plan(), pair[0], pair[1]);
				Optional<Roads.Segment> track = CaravanTrains.blueprint(level, pair[0], pair[1], road);
				if (track.isPresent()) {
					place(level, track.get());
					finish(level, track.get());
				}
			}
		}
		helper.assertTrue(!CaravanTrains.rides(data, a, b) && !CaravanTrains.rides(data, b, a), "a caravan rides a railway with a gap in the middle");
		boolean saidA = said(helper, a, "chronicle.aliveworkplace.railway_open");
		boolean saidB = said(helper, b, "chronicle.aliveworkplace.railway_open");
		helper.assertTrue(!saidA && !saidB, "the chronicle says 'The railway to ... is open: caravans go by train' (Ashford " + saidA
			+ ", Bramble " + saidB + ") though each half stops at its milestone (" + ab.end() + " / " + ba.end()
			+ ", tracked " + data.tracked(a, b) + "/" + data.tracked(b, a) + ") and caravans walk");
		helper.succeed();
	}

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

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	private static void noStations(ServerLevel level, BlockPos hall) {
		for (List<BlockPos> found = CaravanTrains.find(level, hall); !found.isEmpty(); found = CaravanTrains.find(level, hall)) {
			found.forEach(p -> level.removeBlock(p, false));
		}
	}

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

	private static void finish(ServerLevel level, Roads.Segment segment) {
		BuildSite site = BuildSiteManager.get(level).create(java.util.UUID.randomUUID(), "", segment.blueprint().id(), segment.placement());
		Roads.save(level, segment.blueprint());
		Roads.segmentBuilt(level, site, null);
		BuildSiteManager.get(level).remove(site.id());
	}

	private static boolean said(GameTestHelper helper, BlockPos hall, String key) {
		return hall(helper, hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CARAVAN
			&& e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}
}
