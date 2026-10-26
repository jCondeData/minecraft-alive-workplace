package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.RoadStyles;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.15 (roads), from the spec, beyond RoadGameTests: every shipped style is the blocks the spec names and
 * its weighted mixes keep their weights; broken style files in the test pack are skipped; a road is cut into segments of
 * at most 24 blocks that never overlap and clear the cover over it; it never climbs a two-block step, goes round a finished
 * building and a player's blocks; an avenue steps up with stairs across all 5 blocks; the chronicle and the desk read as
 * the lang file says. The helpers are shared with QaRoadLampGameTests.
 */
public class QaRoadGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final Set<Block> STONEWORK_MIDDLE = Set.of(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);

	/** The test area cleared to flat grass (floor y 1, air up to {@code clearTo}), old sites and buildings near it forgotten, and a hall. */
	static BlockPos ground(GameTestHelper helper, BlockPos hallAt, int clearTo) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.stewardSites(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < clearTo; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(hallAt);
	}

	static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	/** A point of a road: the test-area spot {@code (x, z)} as an offset from the hall. */
	static BlockPos p(GameTestHelper helper, BlockPos hall, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		return new BlockPos(at.getX() - hall.getX(), 0, at.getZ() - hall.getZ());
	}

	static CityPlan.Road road(int width, String style, BlockPos... points) {
		return new CityPlan.Road(List.of(points), width, style, true);
	}

	static CityPlan.Road lane(String style, BlockPos... points) {
		return new CityPlan.Road(List.of(points), CityPlan.Road.LANE, style, true, List.of(), false, List.of(), true);
	}

	/** Lays every segment of every road as its blueprint says (as a builder would), each marked built by {@code builder}. */
	static void layAll(GameTestHelper helper, BlockPos hall, Villager builder) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, hall);
		for (int i = 0; i < entity.plan().roads().size(); i++) {
			CityPlan.Road road = entity.plan().roads().get(i);
			for (int n = 0; n < road.segments(); n++) {
				road = entity.plan().roads().get(i);
				Optional<Roads.Segment> segment = Roads.segment(level, hall, road, n);
				helper.assertTrue(segment.isPresent(), "segment " + n + " of road " + i + " has no blueprint");
				for (Blueprint.Entry e : segment.get().blueprint().blocks()) {
					level.setBlock(segment.get().placement().origin().offset(e.pos()), e.state(), Block.UPDATE_CLIENTS);
				}
				BuildSite site = BuildSiteManager.get(level).create(UUID.randomUUID(), "", segment.get().blueprint().id(), segment.get().placement());
				Roads.save(level, segment.get().blueprint());
				Roads.segmentBuilt(level, site, builder);
				BuildSiteManager.get(level).remove(site.id());
			}
		}
	}

	static Block at(GameTestHelper helper, int x, int y, int z) {
		return helper.getBlockState(new BlockPos(x, y, z)).getBlock();
	}

	static List<BlockPos> route(CityPlan.Road road, BlockPos hall) {
		return road.route().stream().map(o -> o.offset(hall)).toList();
	}

	private static Set<Block> both(RoadStyles.Style s) {
		Set<Block> out = new HashSet<>(s.middle().blocks());
		out.addAll(s.edge().blocks());
		return out;
	}

	/** Each shipped road style is the blocks the spec names, has every block a road needs, and keeps its mix's weights. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"qaRoadStyles"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "qaRoadStyles")
	public void qaEveryShippedRoadStyleIsTheBlocksTheSpecNames(GameTestHelper helper) {
		RoadStyles.Style asDrawn = RoadStyles.get("as_drawn").orElseThrow();
		helper.assertTrue(asDrawn.middle().blocks().equals(List.of(Blocks.DIRT_PATH)) && Set.copyOf(asDrawn.edge().blocks()).equals(Set.of(Blocks.COARSE_DIRT, Blocks.GRAVEL)),
			"As drawn isn't dirt path with coarse dirt and gravel edges: " + asDrawn);
		helper.assertTrue(both(RoadStyles.get("sandstone").orElseThrow()).equals(Set.of(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE)), "Sandstone isn't smooth and cut sandstone");
		helper.assertTrue(both(RoadStyles.get("dark_oak").orElseThrow()).equals(Set.of(Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE)), "Dark Oak isn't cobbled and polished deepslate");
		RoadStyles.Style cherry = RoadStyles.get("cherry").orElseThrow();
		helper.assertTrue(cherry.edge().blocks().equals(List.of(Blocks.STONE_BRICKS)), "Cherry's edges aren't stone bricks: " + cherry.edge());
		if (Platform.get().isModLoaded("cobblemon")) {
			RoadStyles.Style apricorn = RoadStyles.get("apricorn").orElseThrow();
			helper.assertTrue(apricorn.middle().blocks().equals(List.of(Blocks.BRICKS)) && apricorn.edge().blocks().equals(List.of(Blocks.MUD_BRICKS)), "Apricorn: " + apricorn);
		}
		for (RoadStyles.Style s : RoadStyles.all()) {
			helper.assertTrue(s.slab() instanceof SlabBlock && s.stairs() instanceof StairBlock, s.name() + "'s steps aren't a slab and stairs");
			for (Block b : List.of(s.deck(), s.rail(), s.pillar(), s.lamp(), s.lanternPost())) {
				helper.assertTrue(b != Blocks.AIR, s.name() + " has no bridge, lamp or post block");
			}
			for (String bs : s.blueprintStyles()) {
				helper.assertTrue(RoadStyles.forBlueprintStyle(bs) == s, "a road drawn in " + bs + " isn't paved in " + s.name());
			}
			String title = RoadStyles.title(s).getString();
			helper.assertTrue(!title.startsWith("road_style."), s.name() + " has no name for players: " + title);
		}
		helper.assertValueEqual(RoadStyles.title(RoadStyles.get("dark_oak").orElseThrow()).getString(), "Dark Oak", "Dark Oak's title");
		// the weights hold: Stonework's middle is 4 stone bricks to 1 cracked, As drawn's edge 2 coarse dirt to 1 gravel
		int cracked = 0;
		int coarse = 0;
		int n = 2000;
		RoadStyles.Style stone = RoadStyles.get("stonework").orElseThrow();
		for (int i = 0; i < n; i++) {
			BlockPos spot = new BlockPos(i % 50 - 25, 64, i / 50 - 20);
			cracked += stone.middle().at(spot) == Blocks.CRACKED_STONE_BRICKS ? 1 : 0;
			coarse += asDrawn.edge().at(spot) == Blocks.COARSE_DIRT ? 1 : 0;
		}
		helper.assertTrue(cracked > n * 0.12 && cracked < n * 0.28, "Stonework's cracked bricks: " + cracked + " of " + n + ", not about 1 in 5");
		helper.assertTrue(coarse > n * 0.55 && coarse < n * 0.78, "As drawn's coarse dirt edges: " + coarse + " of " + n + ", not about 2 in 3");
		// broken style files (the test pack ships three) are skipped, and the shipped ones still load
		for (String bad : List.of("qa_bad_json", "qa_bad_block", "qa_no_lamp")) {
			helper.assertTrue(RoadStyles.get(bad).isEmpty(), "the broken road style " + bad + " loaded");
		}
		helper.assertTrue(RoadStyles.all().size() >= 5, "a broken file stopped the shipped styles loading: " + RoadStyles.all());
		helper.succeed();
	}

	/**
	 * A 45-block street: cut into segments of at most 24 blocks with the spec's ids, which never pave a column twice and
	 * together pave every node; a poppy on the line and leaves over it are cleared.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRoadSegments"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadSegments")
	public void qaARoadIsCutIntoSegmentsOfAtMostTwentyFourThatNeverOverlap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14), 14);
		helper.setBlock(new BlockPos(9, 2, 4), Blocks.POPPY);
		helper.setBlock(new BlockPos(16, 4, 4), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false));
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 3, 4), p(helper, hall, 26, 4), p(helper, hall, 26, 26))));
		Roads.routeNow(level, hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		int size = street.route().size();
		helper.assertTrue(street.routed() && size >= 40, "the street is " + size + " nodes");
		helper.assertValueEqual(street.segments(), (size + 23) / 24, "segments of a " + size + "-node street");
		Map<Long, Integer> pavedBy = new HashMap<>();
		int surfaceY = helper.absolutePos(new BlockPos(0, 1, 0)).getY(); // the street's surface on the flat (a lamp's base stands over it)
		Set<BlockPos> cleared = new HashSet<>();
		for (int n = 0; n < street.segments(); n++) {
			Roads.Segment s = Roads.segment(level, hall, street, n).orElseThrow();
			ResourceLocation id = s.blueprint().id();
			helper.assertTrue(id.equals(Roads.segmentId(hall, street, n)) && id.getNamespace().equals(AliveWorkplace.MOD_ID) && id.getPath().startsWith("roads/")
				&& Roads.isSegment(id), "segment " + n + "'s id: " + id);
			for (Blueprint.Entry e : s.blueprint().blocks()) {
				BlockPos w = s.placement().origin().offset(e.pos());
				if (e.state().isAir()) {
					cleared.add(w);
				} else if (w.getY() == surfaceY && RoadStyles.get("stonework").orElseThrow().surface().contains(e.state().getBlock())) {
					Integer had = pavedBy.put(BlockPos.asLong(w.getX(), 0, w.getZ()), n);
					helper.assertTrue(had == null, "the column at " + w + " is paved by segments " + had + " and " + n);
				}
			}
		}
		// each segment paves the columns of at most 24 nodes: 24 x 3 columns, and 3 more at each end of the road
		for (int n = 0; n < street.segments(); n++) {
			final int seg = n;
			long cols = pavedBy.values().stream().filter(v -> v == seg).count();
			helper.assertTrue(cols <= 24 * 3 + 6, "segment " + n + " paves " + cols + " columns: more than 24 blocks of road");
		}
		for (BlockPos node : route(street, hall)) {
			helper.assertTrue(pavedBy.containsKey(BlockPos.asLong(node.getX(), 0, node.getZ())), "no segment paves the node at " + node);
		}
		helper.assertTrue(Roads.segment(level, hall, street, street.segments()).isEmpty(), "a segment past the end");
		helper.assertTrue(cleared.contains(helper.absolutePos(new BlockPos(9, 2, 4))), "the poppy on the line isn't cleared");
		helper.assertTrue(cleared.contains(helper.absolutePos(new BlockPos(16, 4, 4))), "the leaves over the road aren't cleared");
		helper.succeed();
	}

	/** A 2-block cliff across the line: the road never climbs more than a block at a time, and goes round. */
	//$ gametest_ticks_batch AREA '40' '"qaRoadCliff"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadCliff")
	public void qaARoadNeverClimbsAStepOfTwoBlocks(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		for (int x = 12; x <= 17; x++) {
			for (int z = 0; z <= 17; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 8), p(helper, hall, 27, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		List<BlockPos> nodes = route(street, hall);
		helper.assertTrue(street.routed() && !nodes.isEmpty(), "the street wasn't routed");
		for (int i = 1; i < nodes.size(); i++) {
			helper.assertTrue(Math.abs(nodes.get(i).getY() - nodes.get(i - 1).getY()) <= 1, "a step of more than a block: " + nodes.get(i - 1) + " to " + nodes.get(i));
		}
		int low = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		helper.assertTrue(nodes.stream().allMatch(n -> n.getY() == low), "the street climbed onto the 2-block cliff: " + nodes);
		helper.assertTrue(nodes.get(nodes.size() - 1).distManhattan(helper.absolutePos(new BlockPos(27, 2, 8))) <= 2, "the street didn't go round the cliff");
		helper.succeed();
	}

	/** A finished building and a player's planks and chest on the line: the road keeps clear of each, and leaves them be. */
	//$ gametest_ticks_batch AREA '40' '"qaRoadBuilding"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadBuilding")
	public void qaARoadGoesRoundABuildingAndAPlayersBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		ResourceLocation well = AliveWorkplace.id("well");
		BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(10, 2, 6)), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(well, placement, UUID.randomUUID());
		BoundingBox box = BlueprintOutline.bounds(placement, BlueprintLibrary.get(level, well).orElseThrow().size());
		helper.setBlock(new BlockPos(20, 2, 8), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(6, 2, 9), Blocks.CHEST);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 8), p(helper, hall, 27, 8))));
		try {
			Roads.routeNow(level, hall);
			CityPlan.Road street = entity.plan().roads().get(0);
			List<BlockPos> nodes = route(street, hall);
			helper.assertTrue(nodes.get(nodes.size() - 1).distManhattan(helper.absolutePos(new BlockPos(27, 2, 8))) <= 2, "the street stopped short: " + nodes);
			BlockPos planks = helper.absolutePos(new BlockPos(20, 2, 8));
			BlockPos chest = helper.absolutePos(new BlockPos(6, 2, 9));
			for (BlockPos n : nodes) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos c = n.offset(dx, 0, dz);
						helper.assertTrue(!(c.getX() >= box.minX() && c.getX() <= box.maxX() && c.getZ() >= box.minZ() && c.getZ() <= box.maxZ()),
							"the street runs over the finished Well at " + c + " (" + box + ")");
						helper.assertTrue(!(c.getX() == planks.getX() && c.getZ() == planks.getZ()) && !(c.getX() == chest.getX() && c.getZ() == chest.getZ()),
							"the street runs over a player's block at " + c);
					}
				}
			}
			layAll(helper, hall, null);
			helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(20, 2, 8));
			helper.assertBlockPresent(Blocks.CHEST, new BlockPos(6, 2, 9));
		} finally {
			BuildSiteManager.get(level).forgetFinished(placement);
		}
		helper.succeed();
	}

	/** One rise of a block under an avenue: stairs across all 5 blocks, facing up; past it the avenue is 5 wide again. */
	//$ gametest_ticks_batch AREA '40' '"qaRoadAvenue"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadAvenue")
	public void qaAnAvenueStepsUpWithStairsAcrossAllFiveBlocks(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		for (int x = 15; x < 30; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);
			}
		}
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.AVENUE, "stonework", p(helper, hall, 2, 12), p(helper, hall, 27, 12))));
		Roads.routeNow(helper.getLevel(), hall);
		layAll(helper, hall, null);
		for (int z = 10; z <= 14; z++) {
			BlockState st = helper.getBlockState(new BlockPos(15, 2, z));
			helper.assertTrue(st.is(Blocks.STONE_BRICK_STAIRS) && st.getValue(StairBlock.FACING) == Direction.EAST, "no stair up at z " + z + ": " + st);
		}
		for (int x : new int[] {8, 22}) {
			int y = x < 15 ? 1 : 2;
			helper.assertTrue(STONEWORK_MIDDLE.contains(at(helper, x, y, 12)) && STONEWORK_MIDDLE.contains(at(helper, x, y, 11)) && STONEWORK_MIDDLE.contains(at(helper, x, y, 13)),
				"the avenue's middle at x " + x);
			helper.assertTrue(at(helper, x, y, 10) == Blocks.COBBLESTONE && at(helper, x, y, 14) == Blocks.COBBLESTONE, "the avenue's edges at x " + x);
			helper.assertTrue(at(helper, x, y, 9) == Blocks.GRASS_BLOCK && at(helper, x, y, 15) == Blocks.GRASS_BLOCK, "the avenue is wider than 5 at x " + x);
		}
		helper.succeed();
	}

	/** The chronicle's line for a finished road and the desk's note of a gap, rendered as a player reads them. */
	//$ gametest_ticks_batch AREA '40' '"qaRoadWords"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadWords")
	public void qaTheChronicleAndTheDeskReadAsWritten(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(14, 2, 26), 14);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 3, 6), p(helper, hall, 22, 6))));
		Roads.routeNow(helper.getLevel(), hall);
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 20));
		mira.setCustomName(Component.literal("Mira"));
		int before = (int) entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.PLANS).count();
		layAll(helper, hall, mira);
		CityPlan.Road street = entity.plan().roads().get(0);
		List<Chronicle.Entry> plans = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.PLANS).toList();
		helper.assertValueEqual(plans.size(), before + 1, "chronicle lines for one finished road");
		String line = plans.get(plans.size() - 1).text().getString();
		helper.assertValueEqual(line, "Mira finished a Stonework road, " + street.route().size() + " blocks long", "the chronicle's line");
		mira.discard();
		// the desk: a gap of 17 and one wider than the far bank is looked for; none for a road without a gap
		CityPlan.Road gap17 = new CityPlan.Road(List.of(BlockPos.ZERO, new BlockPos(30, 0, 0)), 3, "", true, List.of(), true, List.of(), false, 17, 0, Optional.empty());
		CityPlan.Road gapFar = new CityPlan.Road(List.of(BlockPos.ZERO, new BlockPos(0, 0, 30)), 3, "", true, List.of(), true, List.of(), false, 41, 0, Optional.empty());
		CityPlan plan = CityPlan.EMPTY.addRoad(gap17).addRoad(gapFar).addRoad(road(3, "", new BlockPos(0, 0, 4), new BlockPos(20, 0, 4)));
		List<String> notes = Roads.deskNotes(plan).stream().map(Component::getString).toList();
		helper.assertValueEqual(notes, List.of("A road stops at a gap 17 blocks wide: a bridge spans 16 at most",
			"A road stops at a gap 40+ blocks wide: a bridge spans 16 at most"), "the desk's notes");
		helper.succeed();
	}
}
