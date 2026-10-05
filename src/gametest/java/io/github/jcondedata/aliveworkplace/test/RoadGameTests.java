package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.RoadStyles;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Roads (ROADMAP 27.15): routed over the ground, cut into segments, built by the village's builders, joined by lanes. */
public class RoadGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final Set<Block> STONEWORK_MIDDLE = Set.of(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);

	/** The test area cleared to flat grass (floor y 1), the old sites and buildings round it forgotten, and a hall. */
	private static BlockPos ground(GameTestHelper helper, BlockPos hallAt) {
		Leftovers.clear(helper);
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
				for (int y = 2; y < 14; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(hallAt);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	/** A point of a road: the test-area spot {@code (x, z)} as an offset from the hall. */
	private static BlockPos p(GameTestHelper helper, BlockPos hall, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		return new BlockPos(at.getX() - hall.getX(), 0, at.getZ() - hall.getZ());
	}

	private static CityPlan.Road road(int width, String style, BlockPos... points) {
		return new CityPlan.Road(List.of(points), width, style, true);
	}

	/** Lays every segment of every road as its blueprint says (as a builder would), each marked built on the plan. */
	private static void layAll(GameTestHelper helper, BlockPos hall) {
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
				BuildSite site = BuildSiteManager.get(level).create(entity.owner() != null ? entity.owner() : java.util.UUID.randomUUID(), "",
					segment.get().blueprint().id(), segment.get().placement());
				Roads.save(level, segment.get().blueprint()); // as opening it would: the lamps are counted from it (27.16)
				Roads.segmentBuilt(level, site, null);
				BuildSiteManager.get(level).remove(site.id());
			}
		}
	}

	private static Block level(GameTestHelper helper, BlockPos absolute) {
		return helper.getLevel().getBlockState(absolute).getBlock();
	}

	private static Block at(GameTestHelper helper, int x, int y, int z) {
		return helper.getBlockState(new BlockPos(x, y, z)).getBlock();
	}

	/** Every road style there is; Apricorn only with Cobblemon; building styles map onto road styles. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"roadStyles"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "roadStyles")
	public void roadStylesLoadOnePerBlueprintStyle(GameTestHelper helper) {
		for (String name : List.of("as_drawn", "stonework", "sandstone", "dark_oak", "cherry")) {
			helper.assertTrue(RoadStyles.get(name).isPresent(), "no road style " + name + ": " + RoadStyles.all());
		}
		boolean cobblemon = Platform.get().isModLoaded("cobblemon");
		helper.assertTrue(RoadStyles.get("apricorn").isPresent() == cobblemon, "Apricorn loaded " + RoadStyles.get("apricorn").isPresent() + " with Cobblemon " + cobblemon);
		RoadStyles.Style stone = RoadStyles.get("stonework").orElseThrow();
		helper.assertTrue(stone.middle().blocks().containsAll(STONEWORK_MIDDLE) && stone.edge().blocks().equals(List.of(Blocks.COBBLESTONE)),
			"Stonework isn't stone bricks with cracked ones and cobblestone edges: " + stone);
		helper.assertTrue(stone.stairs() == Blocks.STONE_BRICK_STAIRS && stone.slab() == Blocks.STONE_BRICK_SLAB, "Stonework's steps");
		helper.assertTrue(RoadStyles.forBlueprintStyle("stonework") == stone && RoadStyles.forBlueprintStyle("grand") == stone, "stonework/grand roads");
		helper.assertTrue(RoadStyles.forBlueprintStyle("").name().equals(RoadStyles.AS_DRAWN)
			&& RoadStyles.forBlueprintStyle("no_such_style").name().equals(RoadStyles.AS_DRAWN), "a road in no style isn't As drawn");
		helper.assertTrue(RoadStyles.forBlueprintStyle("cherry").middle().blocks().equals(List.of(Blocks.POLISHED_DIORITE)), "Cherry's middle");
		// The mix is the same block for one spot every time, and both blocks of the mix turn up along a street.
		BlockPos spot = new BlockPos(5, 64, 9);
		helper.assertTrue(stone.middle().at(spot) == stone.middle().at(spot), "the mix isn't fixed per spot");
		java.util.Set<Block> seen = new java.util.HashSet<>();
		for (int x = 0; x < 64; x++) {
			seen.add(stone.middle().at(new BlockPos(x, 64, 0)));
		}
		helper.assertTrue(seen.equals(STONEWORK_MIDDLE), "64 blocks of street used " + seen);
		helper.succeed();
	}

	/** A 40-block street over a hill: routed, built 3 wide in Stonework, with stairs up and down the hill. */
	//$ gametest_ticks_batch AREA '40' '"roadStreet"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadStreet")
	public void aFortyBlockStreetIsBuiltThreeWideInStoneworkWithStairSteps(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(14, 2, 26));
		for (int x = 10; x <= 14; x++) {
			for (int z = 0; z <= 12; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);
			}
		}
		helper.setBlock(new BlockPos(6, 2, 4), Blocks.SHORT_GRASS);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 4), p(helper, hall, 27, 4), p(helper, hall, 27, 17))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.routed(), "the street wasn't routed");
		helper.assertTrue(street.route().size() >= 36, "the street is " + street.route().size() + " nodes, not about 40");
		BlockPos end = street.route().get(street.route().size() - 1).offset(hall);
		helper.assertTrue(end.distManhattan(helper.absolutePos(new BlockPos(27, 2, 17))) <= 2, "the street doesn't reach its last point: " + end);
		helper.assertTrue(street.segments() == 2, "a 40-block street is " + street.segments() + " segments, not 2");
		layAll(helper, hall);
		street = entity.plan().roads().get(0);
		helper.assertTrue(street.finished() && street.built().size() == 2, "not every segment is marked built: " + street.built());
		// 3 wide on the flat: stone brick middle, cobblestone edges, grass beyond; the tuft on the line is cleared.
		for (int x : new int[] {4, 6, 8, 20, 24}) {
			helper.assertTrue(STONEWORK_MIDDLE.contains(at(helper, x, 1, 4)), "middle at x " + x + " is " + at(helper, x, 1, 4));
			helper.assertTrue(at(helper, x, 1, 3) == Blocks.COBBLESTONE && at(helper, x, 1, 5) == Blocks.COBBLESTONE,
				"edges at x " + x + ": " + at(helper, x, 1, 3) + ", " + at(helper, x, 1, 5));
			helper.assertTrue(at(helper, x, 1, 2) == Blocks.GRASS_BLOCK && at(helper, x, 1, 6) == Blocks.GRASS_BLOCK, "the street is wider than 3 at x " + x);
		}
		helper.assertTrue(at(helper, 6, 2, 4) == Blocks.AIR, "the grass on the street's line is still there");
		// Up the hill and down again: a row of stairs each way, facing the climb.
		for (int z = 3; z <= 5; z++) {
			BlockState up = helper.getBlockState(new BlockPos(10, 2, z));
			BlockState down = helper.getBlockState(new BlockPos(14, 2, z));
			helper.assertTrue(up.is(Blocks.STONE_BRICK_STAIRS) && up.getValue(StairBlock.FACING) == Direction.EAST, "no stairs up the hill at z " + z + ": " + up);
			helper.assertTrue(down.is(Blocks.STONE_BRICK_STAIRS) && down.getValue(StairBlock.FACING) == Direction.WEST, "no stairs down the hill at z " + z + ": " + down);
		}
		helper.assertTrue(STONEWORK_MIDDLE.contains(at(helper, 12, 2, 4)), "the hilltop isn't paved: " + at(helper, 12, 2, 4));
		// Down the side street, 3 wide the other way.
		BlockPos south = street.route().stream().map(o -> o.offset(hall)).filter(n -> n.getZ() == helper.absolutePos(new BlockPos(0, 0, 12)).getZ())
			.findFirst().orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("the street never turns south"));
		helper.assertTrue(STONEWORK_MIDDLE.contains(level(helper, south.below())) && level(helper, south.below().west()) == Blocks.COBBLESTONE
			&& level(helper, south.below().east()) == Blocks.COBBLESTONE, "the turn south isn't 3 wide at " + south);
		helper.succeed();
	}

	/** A player's fence on the road's line: the road goes round it, and it's left standing. */
	//$ gametest_ticks_batch AREA '40' '"roadFence"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadFence")
	public void aPlayersFenceOnTheLineIsGoneRoundAndLeftStanding(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(14, 2, 26));
		BlockPos fence = new BlockPos(15, 2, 8);
		helper.setBlock(fence, Blocks.OAK_FENCE);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 8), p(helper, hall, 27, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		BlockPos f = helper.absolutePos(fence);
		for (BlockPos offset : street.route()) {
			BlockPos node = offset.offset(hall);
			helper.assertTrue(Math.max(Math.abs(node.getX() - f.getX()), Math.abs(node.getZ() - f.getZ())) > 1,
				"the street runs over the fence at " + node);
		}
		BlockPos end = street.route().get(street.route().size() - 1).offset(hall);
		helper.assertTrue(end.distManhattan(helper.absolutePos(new BlockPos(27, 2, 8))) <= 2, "the street stopped at the fence: " + end);
		helper.assertTrue(street.route().size() > 26, "the street didn't go round: " + street.route().size() + " nodes");
		layAll(helper, hall);
		helper.assertBlockPresent(Blocks.OAK_FENCE, fence);
		helper.assertTrue(STONEWORK_MIDDLE.contains(at(helper, 4, 1, 8)), "the street wasn't laid");
		helper.succeed();
	}

	/** The village for a real builder: a builder at a table with a chest of Stonework, a Steward at the hall, its owner. */
	private record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, List<Villager> builders) {
	}

	private static Village village(GameTestHelper helper, BlockPos hallAt, List<BlockPos> tables) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, hallAt);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		List<Villager> builders = new ArrayList<>();
		for (BlockPos table : tables) {
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			helper.setBlock(table.west(), Blocks.CHEST);
			Container chest = helper.getBlockEntity(table.west());
			chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 64));
			chest.setItem(1, new ItemStack(Items.CRACKED_STONE_BRICKS, 32));
			chest.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
			chest.setItem(3, new ItemStack(Items.STONE_BRICK_STAIRS, 16));
			Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
			Builders.employ(level, builder, helper.absolutePos(table));
			builders.add(builder);
		}
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, hallAt.north(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(hall, entity, owner, builders);
	}

	/**
	 * The real builder lays a segment; half built, it is saved and loaded back (the site, the plan and the segment's
	 * blueprint from disk) as it was; finished, it's marked on the plan and the village's rank and buildings are unchanged.
	 */
	//$ gametest_ticks_batch AREA '4000' '"roadReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "roadReload")
	public void aHalfBuiltSegmentSurvivesSaveAndReloadAndRoadsLeaveTheRankAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 26), List.of(new BlockPos(8, 2, 20)));
		v.entity().setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, v.hall(), 4, 14), p(helper, v.hall(), 13, 14))));
		int buildingsBefore = VillageRanks.score(level, v.hall(), 0).buildings();
		Roads.routeNow(level, v.hall());
		Roads.round(level, v.hall(), v.entity());
		List<BuildSite> open = Roads.openSegments(level, v.hall());
		helper.assertTrue(open.size() == 1, "the road opened " + open.size() + " segments, not 1");
		BuildSite site = open.get(0);
		helper.assertTrue(v.builders().get(0).getUUID().equals(site.builder()), "not the village's builder");
		helper.assertTrue(!site.levelGround(), "a road segment would level the ground round it");
		helper.assertTrue(StewardDesk.openSites(level, v.hall()).isEmpty(), "a road segment counts as one of the Steward's builds");
		ResourceLocation id = site.structure();
		boolean[] reloaded = {false};
		helper.succeedWhen(() -> {
			if (!reloaded[0]) {
				BuildSite live = BuildSiteManager.get(level).get(site.id());
				helper.assertTrue(live != null, "the site went before half built");
				helper.assertTrue(live.placed() >= 6, "not half built yet: " + live.placed());
				CompoundTag saved = live.save();
				BuildSite loaded = BuildSite.load(saved);
				helper.assertTrue(loaded != null, "the site didn't load");
				helper.assertValueEqual(loaded.save(), saved, "round-tripped site");
				level.getServer().getStructureManager().remove(id); // forget the loaded blueprint: read it back from disk
				Optional<Blueprint> again = BlueprintLibrary.get(level, id);
				helper.assertTrue(again.isPresent(), "the segment's blueprint wasn't saved");
				helper.assertTrue(loaded.plan(level) != null && loaded.stage() == live.stage() && loaded.placed() == live.placed(),
					"the loaded site isn't where it was");
				CompoundTag plan = (CompoundTag) CityPlan.CODEC.encodeStart(NbtOps.INSTANCE, v.entity().plan()).getOrThrow();
				CityPlan back = CityPlan.CODEC.parse(NbtOps.INSTANCE, plan).getOrThrow();
				helper.assertTrue(back.equals(v.entity().plan()) && back.roads().get(0).routed(), "the plan's road didn't round-trip");
				reloaded[0] = true;
			}
			CityPlan.Road street = v.entity().plan().roads().get(0);
			helper.assertTrue(street.built().contains(0), "segment 0 isn't built yet");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the site is still open");
			helper.assertTrue(STONEWORK_MIDDLE.contains(at(helper, 8, 1, 14)) && at(helper, 8, 1, 13) == Blocks.COBBLESTONE, "the street isn't laid");
			helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().noneMatch(f -> Roads.isSegment(f.structure())),
				"a road segment is in the finished buildings");
			helper.assertValueEqual(VillageRanks.score(level, v.hall(), 0).buildings(), buildingsBefore, "buildings counted for the rank");
			helper.assertTrue(v.entity().chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.PLANS), "no chronicle line for the road");
		});
	}

	/** Roads aren't buildings: never in the finished list, the library, the map or a home. */
	//$ gametest_ticks_batch AREA '40' '"roadSkip"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadSkip")
	public void roadsLeaveTheVillagesRankAndBuildingCountUnchanged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(14, 2, 26));
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "", p(helper, hall, 2, 6), p(helper, hall, 20, 6))));
		int before = VillageRanks.score(level, hall, 0).buildings();
		Roads.routeNow(level, hall);
		layAll(helper, hall);
		CityPlan.Road road = entity.plan().roads().get(0);
		ResourceLocation id = Roads.segmentId(hall, road, 0);
		Roads.Segment segment = Roads.segment(level, hall, road, 0).orElseThrow();
		BuildSiteManager.get(level).recordFinished(id, segment.placement(), java.util.UUID.randomUUID());
		helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().noneMatch(f -> Roads.isSegment(f.structure())), "a road was recorded as a building");
		helper.assertValueEqual(VillageRanks.score(level, hall, 0).buildings(), before, "buildings counted for the rank");
		helper.assertTrue(VillageMaps.kindOf(id).isEmpty(), "a road is drawn on the map as a building");
		helper.assertTrue(BlueprintLibrary.list(level.getServer(), false).stream().noneMatch(Roads::isSegment), "a road segment is in the blueprint library");
		helper.assertTrue(Homes.building(level, helper.absolutePos(new BlockPos(8, 1, 6))).isEmpty(), "a road is someone's home");
		helper.assertTrue(at(helper, 8, 1, 6) == Blocks.DIRT_PATH, "the As drawn road isn't dirt path: " + at(helper, 8, 1, 6));
		helper.succeed();
	}

	/** A new building's door joins the street with a lane (a road on the plan, built the same way); off, or with no road, it doesn't. */
	//$ gametest_ticks_batch AREA '40' '"roadLane"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadLane")
	public void aNewBuildingsLaneJoinsTheStreet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(26, 2, 26));
		VillageHallBlockEntity entity = hall(helper, hall);
		ResourceLocation well = AliveWorkplace.id("well");
		BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(12, 2, 16)), Rotation.NONE, Mirror.NONE);
		BuildSite site = BuildSiteManager.get(level).create(java.util.UUID.randomUUID(), "", well, placement);
		boolean was = Roads.ENABLED;
		try {
			Roads.ENABLED = true;
			helper.assertTrue(!Roads.joinNearest(level, site), "a lane with no road to join");
			entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 4), p(helper, hall, 27, 4))));
			helper.assertTrue(!Roads.joinNearest(level, site), "a lane to a road with no way found yet");
			Roads.routeNow(level, hall);
			Roads.ENABLED = false;
			helper.assertTrue(!Roads.joinNearest(level, site) && entity.plan().roads().size() == 1, "a lane with stewardRoads off");
			Roads.ENABLED = true;
			helper.assertTrue(Roads.joinNearest(level, site), "the new building didn't join the street");
		} finally {
			Roads.ENABLED = was;
			BuildSiteManager.get(level).remove(site.id());
		}
		helper.assertTrue(entity.plan().roads().size() == 2, "no lane on the plan");
		CityPlan.Road lane = entity.plan().roads().get(1);
		helper.assertTrue(lane.lane() && lane.approved() && lane.width() == CityPlan.Road.LANE && lane.style().equals("stonework"),
			"the lane isn't an approved 1-wide Stonework lane: " + lane);
		helper.assertTrue(entity.plan().drawnRoads() == 1, "the lane counts as a drawn road");
		Roads.routeNow(level, hall);
		lane = entity.plan().roads().get(1);
		helper.assertTrue(lane.routed() && lane.route().size() >= 5, "the lane wasn't routed: " + lane.route());
		BlockPos last = lane.route().get(lane.route().size() - 1).offset(hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.route().stream().anyMatch(o -> o.offset(hall).distManhattan(last) <= 2), "the lane ends at " + last + ", off the street");
		BoundingBox box = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(placement, BlueprintLibrary.get(level, well).orElseThrow().size());
		BlockPos first = lane.route().get(0).offset(hall);
		helper.assertTrue(!box.isInside(first) && box.inflatedBy(2).isInside(first), "the lane doesn't start at the building: " + first + " vs " + box);
		layAll(helper, hall);
		helper.assertTrue(STONEWORK_MIDDLE.contains(level.getBlockState(lane.route().get(2).offset(hall).below()).getBlock()), "the lane wasn't laid");
		helper.succeed();
	}

	/** At most 2 segments open, each with its own builder, and none while a building of the village waits. */
	//$ gametest_ticks_batch AREA '40' '"roadOpen"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadOpen")
	public void atMostTwoSegmentsOpenAndNoneWhileABuildingWaits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14), List.of(new BlockPos(10, 2, 10), new BlockPos(14, 2, 10), new BlockPos(18, 2, 10)));
		v.entity().setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.LANE, "", p(helper, v.hall(), 2, 2), p(helper, v.hall(), 27, 2),
			p(helper, v.hall(), 27, 27), p(helper, v.hall(), 2, 27))));
		Roads.routeNow(level, v.hall());
		helper.assertTrue(v.entity().plan().roads().get(0).segments() >= 3, "the road is only " + v.entity().plan().roads().get(0).segments() + " segments");
		// A Steward's build waiting in a queue: no road yet.
		BlueprintData.Placement spot = new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(6, 2, 16)), Rotation.NONE, Mirror.NONE);
		BuildSite waiting = Builders.enqueue(level, v.builders().get(0), v.owner().getUUID(), "", AliveWorkplace.id("well"), spot);
		waiting.setStewardHall(v.hall());
		Roads.round(level, v.hall(), v.entity());
		helper.assertTrue(Roads.openSegments(level, v.hall()).isEmpty(), "a road segment opened while a building waits");
		BuildSiteManager.get(level).remove(waiting.id());
		Roads.round(level, v.hall(), v.entity());
		Roads.round(level, v.hall(), v.entity());
		List<BuildSite> open = Roads.openSegments(level, v.hall());
		helper.assertTrue(open.size() == Roads.MAX_OPEN, open.size() + " segments open, not " + Roads.MAX_OPEN);
		helper.assertTrue(!open.get(0).builder().equals(open.get(1).builder()), "one builder has both segments");
		helper.succeed();
	}

	/** Config off (as in every other test): a Steward's approved road is never routed or built, however long it waits. */
	//$ gametest_ticks_batch AREA '200' '"roadOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "roadOff")
	public void withStewardRoadsOffNothingIsBuilt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!Roads.ENABLED, "setup: stewardRoads is on in tests");
		Village v = village(helper, new BlockPos(14, 2, 26), List.of(new BlockPos(8, 2, 20)));
		v.entity().setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, v.hall(), 4, 14), p(helper, v.hall(), 13, 14))));
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(!v.entity().plan().roads().get(0).routed(), "the road was routed with the config off");
			helper.assertTrue(Roads.openSegments(level, v.hall()).isEmpty(), "a segment opened with the config off");
			helper.succeed();
		});
	}

	// ---- 27.16: lamps, bridges and steps ------------------------------------------------------------------------------

	/** A river (water 2 deep, bed at y 0) at test x {@code x0..x1} across the whole area, the banks raised to y 2. */
	private static void river(GameTestHelper helper, int x0, int x1) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				boolean water = x >= x0 && x <= x1;
				helper.setBlock(new BlockPos(x, 1, z), water ? Blocks.WATER : Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), water ? Blocks.WATER : Blocks.GRASS_BLOCK);
			}
		}
	}

	/** A 60-block street: a Street Lamp every 16 blocks on alternate sides, the one by a door moved along; they add beauty. */
	//$ gametest_ticks_batch AREA '40' '"roadLamps"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadLamps")
	public void aSixtyBlockStreetGetsLampsEverySixteenBlocksOnAlternateSidesNoneByADoor(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14));
		helper.setBlock(new BlockPos(16, 2, 1), Blocks.OAK_DOOR.defaultBlockState());
		helper.setBlock(new BlockPos(16, 3, 1), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
			net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 4), p(helper, hall, 26, 4),
			p(helper, hall, 26, 22), p(helper, hall, 8, 22))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.route().size() >= 55, "the street is " + street.route().size() + " nodes, not about 60");
		int beautyBefore = io.github.jcondedata.aliveworkplace.hall.Decorations.beauty(helper.getLevel(), hall);
		layAll(helper, hall);
		street = entity.plan().roads().get(0);
		// each lamp's top (a trapdoor, 5 over its foot): one north of the first stretch, one west of the second, one south of the third
		List<BlockPos> tops = new ArrayList<>();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				if (!helper.getBlockState(new BlockPos(x, 7, z)).isAir()) {
					tops.add(new BlockPos(x, 7, z));
				}
			}
		}
		helper.assertTrue(tops.size() == 3, "not 3 lamps on a 60-block street: " + tops);
		helper.assertTrue(tops.stream().anyMatch(t -> t.getZ() <= 2 && t.getX() >= 19 && t.getX() <= 22), "the first lamp isn't north of the street, moved past the door: " + tops);
		helper.assertTrue(tops.stream().anyMatch(t -> t.getX() >= 21 && t.getX() <= 24 && t.getZ() >= 6 && t.getZ() <= 20), "the second lamp isn't on the other side: " + tops);
		helper.assertTrue(tops.stream().anyMatch(t -> t.getZ() >= 24), "the third lamp isn't back on the first side: " + tops);
		// none in front of the door: only air round it but the door
		for (BlockPos q : BlockPos.betweenClosed(new BlockPos(14, 2, 0), new BlockPos(18, 7, 2))) {
			BlockState st = helper.getBlockState(q);
			helper.assertTrue(st.isAir() || st.is(Blocks.OAK_DOOR), "a lamp stands by the door: " + st + " at " + q);
		}
		int lanterns = 0;
		for (BlockPos q : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(29, 7, 29))) {
			if (helper.getBlockState(q).is(Blocks.LANTERN)) {
				lanterns++;
			}
		}
		helper.assertTrue(lanterns == 6, lanterns + " lanterns, not 2 on each of 3 lamps");
		helper.assertTrue(street.lamps() == 3, "the plan counts " + street.lamps() + " lamps, not 3");
		int beauty = io.github.jcondedata.aliveworkplace.hall.Decorations.beauty(helper.getLevel(), hall);
		helper.assertTrue(beauty == beautyBefore + 3, "the lamps add " + (beauty - beautyBefore) + " beauty, not 3 (as Street Lamps)");
		helper.succeed();
	}

	/** A river 9 wide: a Stonework bridge, a stair up at each end, rails, 2 pillars down to the bed; a villager walks over. */
	//$ gametest_ticks_batch AREA '600' '"roadBridge"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "roadBridge")
	public void aRiverNineWideIsBridgedWithTwoPillarsAndVillagersWalkOverIt(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(3, 3, 26));
		river(helper, 10, 18);
		helper.setBlock(new BlockPos(3, 3, 26), ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 8), p(helper, hall, 27, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.gap() == 0, "a 9-wide river was refused: gap " + street.gap());
		BlockPos end = street.route().get(street.route().size() - 1).offset(hall);
		helper.assertTrue(end.distManhattan(helper.absolutePos(new BlockPos(27, 3, 8))) <= 2, "the street doesn't cross the river: it ends at " + end);
		layAll(helper, hall);
		for (int x = 10; x <= 18; x++) {
			for (int z = 7; z <= 9; z++) {
				BlockState deck = helper.getBlockState(new BlockPos(x, 3, z));
				if (x == 10 || x == 18) {
					helper.assertTrue(deck.is(Blocks.STONE_BRICK_STAIRS) && deck.getValue(StairBlock.FACING) == (x == 10 ? Direction.EAST : Direction.WEST),
						"no stair up onto the bridge at x " + x + " z " + z + ": " + deck);
				} else {
					helper.assertTrue(deck.is(Blocks.STONE_BRICKS), "the deck at x " + x + " z " + z + " is " + deck);
				}
			}
			helper.assertTrue(at(helper, x, 4, 6) == Blocks.STONE_BRICK_WALL && at(helper, x, 4, 10) == Blocks.STONE_BRICK_WALL, "no rails at x " + x);
		}
		int pillars = 0;
		for (int x = 10; x <= 18; x++) {
			if (at(helper, x, 2, 8) == Blocks.STONE_BRICKS) {
				pillars++;
				helper.assertTrue(at(helper, x, 1, 8) == Blocks.STONE_BRICKS, "the pillar at x " + x + " doesn't reach the bed");
			}
		}
		helper.assertTrue(pillars == 2, pillars + " pillars under a 9-wide bridge, not 2");
		helper.assertTrue(at(helper, 12, 2, 8) == Blocks.WATER, "the river under the bridge was filled");
		// a villager walks from one bank to the other over it
		Villager walker = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 3, 8));
		BlockPos goal = helper.absolutePos(new BlockPos(23, 3, 8));
		boolean[] wet = {false};
		helper.succeedWhen(() -> {
			walker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
				new net.minecraft.world.entity.ai.memory.WalkTarget(goal, 0.6f, 0));
			wet[0] = wet[0] || walker.isInWater();
			helper.assertTrue(walker.getX() >= goal.getX() - 2, "the villager hasn't crossed: " + walker.blockPosition());
			helper.assertTrue(!wet[0], "the villager crossed through the river, not over the bridge");
		});
	}

	/** A gap 20 wide (wider than a bridge spans): the road stops at the near bank and the Steward's desk says why. */
	//$ gametest_ticks_batch AREA '40' '"roadGap"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadGap")
	public void aGapTwentyWideIsRefusedAndTheDeskSaysWhy(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 3, 26));
		river(helper, 5, 24);
		helper.setBlock(new BlockPos(2, 3, 26), ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 1, 8), p(helper, hall, 28, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.routed() && street.gap() == 20, "the 20-wide gap: routed " + street.routed() + ", gap " + street.gap());
		for (BlockPos o : street.route()) {
			helper.assertTrue(o.offset(hall).getX() <= helper.absolutePos(new BlockPos(4, 0, 0)).getX(), "the road goes past the bank: " + o.offset(hall));
		}
		List<net.minecraft.network.chat.Component> notes = Roads.deskNotes(entity.plan());
		helper.assertTrue(notes.size() == 1 && notes.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
			&& t.getKey().equals("screen.aliveworkplace.desk.road_gap") && "20".equals(String.valueOf(t.getArgs()[0])) && "16".equals(String.valueOf(t.getArgs()[1])),
			"the desk's note: " + notes);
		// saved and loaded, the gap stays on the road (and older saves load with none)
		CompoundTag saved = (CompoundTag) CityPlan.Road.CODEC.encodeStart(NbtOps.INSTANCE, street).getOrThrow();
		helper.assertTrue(CityPlan.Road.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().gap() == 20, "the gap isn't saved");
		saved.remove("gap");
		saved.remove("lamps");
		CityPlan.Road old = CityPlan.Road.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(old.gap() == 0 && old.lamps() == 0, "an older save doesn't load with no gap and no lamps");
		helper.succeed();
	}

	/** A slope of one in one: every one-block rise of the street becomes stairs across its width. */
	//$ gametest_ticks_batch AREA '40' '"roadSlope"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "roadSlope")
	public void aSlopeOfOneInOneGetsStairsAcrossTheRoad(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26));
		for (int x = 8; x < 30; x++) {
			int top = Math.min(7, x - 6);
			for (int z = 0; z < 22; z++) {
				for (int y = 1; y < top; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
				}
				helper.setBlock(new BlockPos(x, top, z), Blocks.GRASS_BLOCK);
			}
		}
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 8), p(helper, hall, 27, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		layAll(helper, hall);
		for (int x = 8; x <= 13; x++) {
			for (int z = 7; z <= 9; z++) {
				BlockState st = helper.getBlockState(new BlockPos(x, x - 6, z));
				helper.assertTrue(st.is(Blocks.STONE_BRICK_STAIRS) && st.getValue(StairBlock.FACING) == Direction.EAST,
					"no stairs up the slope at x " + x + " z " + z + ": " + st);
			}
		}
		helper.assertTrue(STONEWORK_MIDDLE.contains(at(helper, 20, 7, 8)), "the top isn't paved: " + at(helper, 20, 7, 8));
		helper.succeed();
	}
}
