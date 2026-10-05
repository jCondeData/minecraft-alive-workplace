package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Roads between villages (ROADMAP 27.17): each village with a trade route builds its half of a road towards the other;
 * the halves meet halfway, or a half that can't reach that far ends at a milestone; planned only where the world is
 * loaded; a finished road shortens the caravans' trip; both chronicles note it.
 */
public class CaravanRoadGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The hall, in the test area: the roads go north (towards smaller z) from it, out of the area. */
	private static final BlockPos HALL = new BlockPos(14, 2, 26);
	/** How far either side of the road's line the ground laid north of the area reaches. */
	private static final int SIDE = 7;

	/**
	 * The area cleared to flat grass (floor y 1), the old sites and finished buildings round it and along the roads north
	 * of it forgotten, and the hall; its absolute position. The roads run up to 276 blocks north, over the spots of earlier
	 * batches' tests (the test grid grows south, batch after batch): a finished building one of them recorded there stays
	 * on the record after the corridor wipes its blocks, and a road keeps out of it (CI, 2026-10-05: an earlier Builder
	 * test's guildhall record over the second hall walled that half in at its first node).
	 */
	private static BlockPos ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near(site.placement().origin(), a)) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near(f.placement().origin(), a)) {
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
		return helper.absolutePos(HALL);
	}

	/** Whether a site's or building's origin is round the area or the roads north of it (any of them within reach of a road). */
	private static boolean near(BlockPos origin, BlockPos area) {
		return Math.abs(origin.getX() - area.getX()) < 80 && origin.getZ() > area.getZ() - 400 && origin.getZ() < area.getZ() + 80;
	}

	/** The test's own chunk tickets: let go when the test ends without touching the chunks the test framework forces. */
	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
		net.minecraft.server.level.TicketType.create("aliveworkplace_caravan_road_test", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

	/**
	 * Loads the chunks within 64 blocks of the line north of the hall from {@code nearZ} to {@code farZ} (absolute,
	 * nearZ > farZ) now, and keeps them loaded with the test's own tickets until the test ends. (Not forced chunks: a
	 * forced chunk let go would also be let go for the next batch's test standing in it, which then waits for it forever.)
	 */
	private static void load(GameTestHelper helper, BlockPos hall, int nearZ, int farZ) {
		ServerLevel level = helper.getLevel();
		List<net.minecraft.world.level.ChunkPos> held = new ArrayList<>();
		for (int cx = (hall.getX() - 64) >> 4; cx <= (hall.getX() + 64) >> 4; cx++) {
			for (int cz = (farZ - 64) >> 4; cz <= (nearZ + 16) >> 4; cz++) {
				net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(cx, cz);
				level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos);
				held.add(pos);
				level.getChunk(cx, cz);
			}
		}
		Leftovers.after(helper, () -> held.forEach(pos -> level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos)));
	}

	/** Flat grass on dirt at the area's floor, with room over it, along the line north of the area down to {@code farZ}. */
	private static void corridor(GameTestHelper helper, BlockPos hall, int nearZ, int farZ) {
		ServerLevel level = helper.getLevel();
		int areaNorth = helper.absolutePos(BlockPos.ZERO).getZ();
		for (int z = Math.min(nearZ, areaNorth - 1); z >= farZ; z--) {
			for (int x = hall.getX() - SIDE; x <= hall.getX() + SIDE; x++) {
				level.setBlock(new BlockPos(x, hall.getY() - 2, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(new BlockPos(x, hall.getY() - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 0; y <= 7; y++) {
					level.setBlock(new BlockPos(x, hall.getY() + y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	/** Lays every segment of the hall's road to {@code other} as its blueprint says (as a builder would: block data too), each marked built. */
	private static void layAll(GameTestHelper helper, BlockPos hall, BlockPos other) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, hall);
		CityPlan.Road road = CaravanRoads.roadTo(entity.plan(), hall, other);
		helper.assertTrue(road != null && road.routed(), "no road with its way found from " + hall + " to " + other);
		for (int n = 0; n < road.segments(); n++) {
			road = CaravanRoads.roadTo(entity.plan(), hall, other);
			Optional<Roads.Segment> segment = Roads.segment(level, hall, road, n);
			helper.assertTrue(segment.isPresent(), "segment " + n + " has no blueprint");
			for (Blueprint.Entry e : segment.get().blueprint().blocks()) {
				BlockPos at = segment.get().placement().origin().offset(e.pos());
				level.setBlock(at, e.state(), Block.UPDATE_CLIENTS);
				BlockEntity be = level.getBlockEntity(at);
				if (e.nbt() != null && be != null) {
					CompoundTag tag = BlockEntityData.sanitize(level, at, e.state(), be, e.nbt());
					if (tag != null) {
						be.loadWithComponents(tag, level.registryAccess());
					}
				}
			}
			BuildSite site = BuildSiteManager.get(level).create(java.util.UUID.randomUUID(), "", segment.get().blueprint().id(), segment.get().placement());
			Roads.save(level, segment.get().blueprint());
			Roads.segmentBuilt(level, site, null);
			BuildSiteManager.get(level).remove(site.id());
		}
	}

	private static boolean said(VillageHallBlockEntity entity, String key) {
		return entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CARAVAN
			&& e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static double flat(BlockPos a, BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}

	private static BlockPos end(CityPlan.Road road, BlockPos hall) {
		return road.route().get(road.route().size() - 1).offset(hall);
	}

	/**
	 * Two villages 120 blocks apart with a trade route: each plans its half (a street from its hall towards the other),
	 * both are routed and built, and they meet halfway with no gap and no milestone; both chronicles say the road is
	 * finished; the plan and the caravans' list keep it all over a save and reload; and a caravan on the finished road
	 * arrives in three quarters of the time.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanRoadMeet"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanRoadMeet")
	public void twoVillages120ApartBuildBothHalvesAndTheyMeet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper);
		BlockPos b = a.offset(0, 0, -120);
		load(helper, a, a.getZ(), b.getZ() - 10);
		corridor(helper, a, a.getZ(), b.getZ() - 10);
		level.setBlock(b, ModBlocks.VILLAGE_HALL.defaultBlockState(), Block.UPDATE_ALL);
		helper.setBlock(new BlockPos(20, 2, 26), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(20, 2, 28), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(20, 2, 28));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			level.removeBlock(b, false);
			data.remove(a);
			data.remove(b);
		});
		helper.runAfterDelay(2, () -> {
			data.setWants(a, Component.literal("Ashford"), List.of());
			data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
			CaravanRoads.plan(level, a, hall(helper, a));
			CaravanRoads.plan(level, b, hall(helper, b)); // the route is Ashford's, but Bramble builds its half too
			for (BlockPos[] pair : new BlockPos[][] {{a, b}, {b, a}}) {
				CityPlan.Road half = CaravanRoads.roadTo(hall(helper, pair[0]).plan(), pair[0], pair[1]);
				helper.assertTrue(half != null, "no half planned from " + pair[0]);
				helper.assertTrue(half.width() == CityPlan.Road.STREET && half.approved() && half.caravan() && !half.lane(), "the half isn't an approved street: " + half);
				helper.assertTrue(hall(helper, pair[0]).plan().drawnRoads() == 0, "a road to another village counts as a drawn road");
				helper.assertTrue(Roads.routeNow(level, pair[0]), "the half's routing paused in loaded land");
			}
			CityPlan.Road ours = CaravanRoads.roadTo(hall(helper, a).plan(), a, b);
			CityPlan.Road theirs = CaravanRoads.roadTo(hall(helper, b).plan(), b, a);
			BlockPos mid = a.offset(0, 0, -60);
			helper.assertTrue(flat(end(ours, a), mid) <= 3 && flat(end(theirs, b), mid) <= 3,
				"the halves don't end halfway: " + end(ours, a) + " and " + end(theirs, b) + ", halfway is " + mid);
			helper.assertTrue(!CaravanRoads.endsShort(a, ours) && !CaravanRoads.endsShort(b, theirs), "a half that reaches halfway ends at a milestone");
			helper.assertTrue(ours.route().size() >= 55 && ours.route().size() <= 66, "Ashford's half is " + ours.route().size() + " nodes, not about 60");
			layAll(helper, a, b);
			helper.assertTrue(!data.roadFinished(a, b), "the road is finished with one half built");
			helper.assertTrue(!said(hall(helper, a), "chronicle.aliveworkplace.caravan_road_finished"), "the road is noted finished with one half built");
			layAll(helper, b, a);
			helper.assertTrue(data.roadFinished(a, b) && data.roadFinished(b, a), "both halves built, the road isn't finished");
			// One road from hall to hall: paved all the way between the villages, no grass left on its line.
			for (int z = b.getZ() + 4; z <= a.getZ() - 4; z++) {
				BlockState floor = level.getBlockState(new BlockPos(a.getX(), a.getY() - 1, z));
				helper.assertTrue(!floor.is(Blocks.GRASS_BLOCK) && !floor.isAir(), "a gap in the road at z " + z + ": " + floor);
			}
			BlockState halfway = level.getBlockState(new BlockPos(a.getX(), a.getY() - 1, mid.getZ()));
			helper.assertTrue(halfway.is(Blocks.DIRT_PATH) || halfway.is(Blocks.COARSE_DIRT) || halfway.is(Blocks.GRAVEL), "halfway isn't paved As drawn: " + halfway);
			for (BlockPos p : BlockPos.betweenClosed(mid.offset(-6, 0, -6), mid.offset(6, 3, 6))) {
				helper.assertTrue(!level.getBlockState(p).is(Blocks.OAK_WALL_SIGN), "a milestone where the halves meet, at " + p);
			}
			helper.assertTrue(said(hall(helper, a), "chronicle.aliveworkplace.caravan_road_finished")
				&& said(hall(helper, b), "chronicle.aliveworkplace.caravan_road_finished"), "not in both chronicles");
			long lines = hall(helper, a).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CARAVAN).count();
			CaravanRoads.plan(level, a, hall(helper, a));
			helper.assertTrue(hall(helper, a).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CARAVAN).count() == lines, "the road noted twice");
			// Save and reload: the plan's half (with where it leads) and the caravans' list of halves come back the same.
			CityPlan plan = hall(helper, a).plan();
			CityPlan back = CityPlan.CODEC.parse(NbtOps.INSTANCE, CityPlan.CODEC.encodeStart(NbtOps.INSTANCE, plan).getOrThrow()).getOrThrow();
			helper.assertTrue(back.equals(plan) && CaravanRoads.roadTo(back, a, b) != null, "the plan's road to Bramble didn't round-trip");
			Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(loaded.roadFinished(a, b) && loaded.half(a, b).equals(data.half(a, b)) && loaded.half(b, a).equals(data.half(b, a)),
				"the halves didn't round-trip: " + loaded.half(a, b) + " / " + data.half(a, b));
			// The caravan: on the finished road, three quarters of the trip.
			long now = level.getGameTime();
			Caravans.round(level, a, null);
			Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.from().equals(a) && x.to().equals(b)).findFirst()
				.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no caravan left Ashford"));
			long without = Caravans.travelTicks(120);
			helper.assertTrue(s.arrives() - now == without * 3 / 4 && Caravans.travelTicks(120, true) < without,
				"the caravan takes " + (s.arrives() - now) + " ticks on the road, not three quarters of " + without);
			helper.succeed();
		});
	}

	/** A route to a village 900 blocks away: the half goes 256 blocks (caravanRoadReach) and ends at a milestone naming it and how far. */
	//$ gametest_ticks_batch AREA '200' '"caravanRoadFar"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanRoadFar")
	public void aRouteToAVillage900AwayBuilds256BlocksAndAMilestone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper);
		BlockPos far = a.offset(0, 0, -900);
		helper.assertTrue(CaravanRoads.REACH == 256, "caravanRoadReach isn't 256 by default: " + CaravanRoads.REACH);
		load(helper, a, a.getZ(), a.getZ() - 276);
		corridor(helper, a, a.getZ(), a.getZ() - 276);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.remove(a);
			data.remove(far);
		});
		helper.runAfterDelay(2, () -> {
			data.setWants(a, Component.literal("Ashford"), List.of());
			data.setWants(far, Component.literal("Farhollow"), List.of()); // far off, in land nobody has loaded: only on the list
			helper.assertTrue(data.toggleRoute(a, far), "setup: no route");
			CaravanRoads.plan(level, a, hall(helper, a));
			CityPlan.Road half = CaravanRoads.roadTo(hall(helper, a).plan(), a, far);
			helper.assertTrue(half != null, "no half planned");
			helper.assertTrue(Roads.routeNow(level, a), "the routing paused in loaded land");
			half = CaravanRoads.roadTo(hall(helper, a).plan(), a, far);
			BlockPos end = end(half, a);
			helper.assertTrue(Math.abs(flat(a, end) - 256) <= 4, "the half goes " + flat(a, end) + " blocks, not 256: ends at " + end);
			helper.assertTrue(half.route().size() >= 250, "the half is " + half.route().size() + " nodes");
			helper.assertTrue(CaravanRoads.endsShort(a, half), "a half 194 blocks short of halfway doesn't end at a milestone");
			layAll(helper, a, far);
			for (int z = end.getZ() + 2; z <= a.getZ() - 4; z++) {
				BlockState floor = level.getBlockState(new BlockPos(a.getX(), a.getY() - 1, z));
				helper.assertTrue(!floor.is(Blocks.GRASS_BLOCK), "a gap in the road at z " + z + ": " + floor);
			}
			// The milestone: a stone post with a lantern, and a sign naming Farhollow and how far it is.
			BlockPos sign = null;
			for (BlockPos p : BlockPos.betweenClosed(end.offset(-5, -1, -5), end.offset(5, 3, 8))) {
				if (level.getBlockState(p).is(Blocks.OAK_WALL_SIGN)) {
					sign = p.immutable();
				}
			}
			helper.assertTrue(sign != null, "no milestone sign by the road's end at " + end);
			BlockPos post = sign.relative(level.getBlockState(sign).getValue(WallSignBlock.FACING).getOpposite());
			helper.assertTrue(level.getBlockState(post).is(Blocks.CHISELED_STONE_BRICKS) && level.getBlockState(post.below()).is(Blocks.STONE_BRICKS)
				&& level.getBlockState(post.above()).is(Blocks.STONE_BRICK_WALL) && level.getBlockState(post.above(2)).is(Blocks.LANTERN),
				"the milestone isn't a stone post with a lantern at " + post);
			SignBlockEntity be = (SignBlockEntity) level.getBlockEntity(sign);
			helper.assertTrue(be.getText(true).getMessage(1, false).getString().equals("Farhollow"), "the sign names " + be.getText(true).getMessage(1, false).getString());
			Component distance = be.getText(true).getMessage(2, false);
			helper.assertTrue(distance.getContents() instanceof TranslatableContents t && t.getKey().equals("sign.aliveworkplace.milestone.distance")
				&& t.getArgs().length == 1 && Math.abs(Integer.parseInt(t.getArgs()[0] instanceof Component c ? c.getString() : String.valueOf(t.getArgs()[0])) - 644) <= 6,
				"the sign says " + distance);
			helper.assertTrue(be.getText(true).getMessage(0, false).getContents() instanceof TranslatableContents t0 && t0.getKey().equals("sign.aliveworkplace.milestone.to"),
				"the sign's first line");
			helper.assertTrue(be.isWaxed(), "a passer-by can edit the milestone");
			Caravans.Half ours = data.half(a, far);
			helper.assertTrue(ours != null && ours.finished() && ours.milestone() && !data.roadFinished(a, far), "the list's half: " + ours);
			helper.assertTrue(said(hall(helper, a), "chronicle.aliveworkplace.caravan_road_milestone"), "the milestone isn't in the chronicle");
			helper.assertTrue(Caravans.travelTicks(900, data.roadFinished(a, far)) == Caravans.travelTicks(900), "half a road shortens the trip");
			helper.succeed();
		});
	}

	/** Land not loaded halfway along: the planning waits there without an error and loads nothing, and goes on once it's loaded. */
	//$ gametest_ticks_batch AREA '200' '"caravanRoadUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanRoadUnloaded")
	public void anUnloadedChunkPausesThePlanningAndItGoesOnWhenLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper);
		BlockPos other = a.offset(0, 0, -400);
		BlockPos mid = a.offset(0, 0, -200);
		load(helper, a, a.getZ(), a.getZ() - 40);
		corridor(helper, a, a.getZ(), a.getZ() - 40 - 64);
		helper.assertTrue(!level.hasChunk(mid.getX() >> 4, mid.getZ() >> 4), "setup: the land halfway is loaded already");
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.remove(a);
			data.remove(other);
		});
		helper.runAfterDelay(2, () -> {
			data.setWants(a, Component.literal("Ashford"), List.of());
			data.setWants(other, Component.literal("Coldwater"), List.of());
			helper.assertTrue(data.toggleRoute(other, a), "setup: no route"); // the other village's route: Ashford builds its half all the same
			CaravanRoads.plan(level, a, hall(helper, a));
			helper.assertTrue(CaravanRoads.roadTo(hall(helper, a).plan(), a, other) != null, "no half planned for a route into the village");
			Set<Long> forced = Set.copyOf(level.getForcedChunks());
			boolean done = Roads.routeNow(level, a);
			helper.assertTrue(!done && Roads.routingPaused(level, a), "the routing didn't wait at the unloaded land");
			int sofar = Roads.routedSoFar(level, a);
			helper.assertTrue(sofar >= 10 && sofar < 180, "the routing got " + sofar + " nodes before waiting");
			helper.assertTrue(!CaravanRoads.roadTo(hall(helper, a).plan(), a, other).routed(), "the half was saved routed, short");
			helper.assertTrue(!level.hasChunk(mid.getX() >> 4, mid.getZ() >> 4) && Set.copyOf(level.getForcedChunks()).equals(forced),
				"the road loaded land to plan in");
			helper.assertTrue(!Roads.routeNow(level, a) && Roads.routedSoFar(level, a) == sofar, "waiting, the routing moved or started over");
			// The land is loaded: it goes on from where it waited, to halfway.
			load(helper, a, a.getZ() - 40, mid.getZ() - 20);
			corridor(helper, a, a.getZ() - 40 - 64, mid.getZ() - 20);
			helper.assertTrue(Roads.routeNow(level, a), "the routing still waits with the land loaded");
			CityPlan.Road half = CaravanRoads.roadTo(hall(helper, a).plan(), a, other);
			helper.assertTrue(half.routed() && flat(end(half, a), mid) <= 3, "the half doesn't reach halfway: " + (half.routed() ? end(half, a) : "not routed"));
			helper.assertTrue(half.route().size() >= 195 && half.route().size() > sofar, "the half is " + half.route().size() + " nodes");
			helper.succeed();
		});
	}

	/** caravanRoads off: no half is planned; on, a half not yet started goes with its route. */
	//$ gametest_ticks_batch AREA '40' '"caravanRoadOff"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "caravanRoadOff")
	public void withCaravanRoadsOffNoRoadIsPlanned(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper);
		BlockPos other = a.offset(0, 0, -200);
		Caravans.Data data = Caravans.Data.get(level);
		boolean was = CaravanRoads.ENABLED;
		Leftovers.after(helper, () -> {
			CaravanRoads.ENABLED = was;
			data.remove(a);
			data.remove(other);
		});
		helper.runAfterDelay(2, () -> {
			data.setWants(a, Component.literal("Ashford"), List.of());
			data.setWants(other, Component.literal("Dunmere"), List.of());
			helper.assertTrue(data.toggleRoute(a, other), "setup: no route");
			CaravanRoads.ENABLED = false;
			CaravanRoads.plan(level, a, hall(helper, a));
			helper.assertTrue(hall(helper, a).plan().roads().isEmpty(), "a road planned with caravanRoads off");
			CaravanRoads.ENABLED = true;
			CaravanRoads.plan(level, a, hall(helper, a));
			helper.assertTrue(hall(helper, a).plan().caravanRoads() == 1, "no half planned with caravanRoads on");
			helper.assertTrue(!data.toggleRoute(a, other), "setup: the route didn't stop");
			CaravanRoads.plan(level, a, hall(helper, a));
			helper.assertTrue(hall(helper, a).plan().roads().isEmpty(), "the half not yet started stayed when its route stopped");
			helper.succeed();
		});
	}
}
