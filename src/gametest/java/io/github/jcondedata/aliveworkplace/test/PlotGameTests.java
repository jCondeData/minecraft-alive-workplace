package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 27.7: finding a plot. On flat grass with a Homes zone east of the hall (helper x 6..29, z 2..25 at the
 * default hall radius, cells of 4 blocks), a Well (7×5×7) goes nearest the hall, facing the road on the plan (else the
 * hall); never across the zone's edge, over a player's wall or chest, near another village's build site, on a slope of
 * 6, over water or lava, out of a Blueprint Table's reach, or next to the same blueprint mirrored the same way. The search
 * reads at most 64 columns a tick per hall and ends on a fully painted plan within 200 ticks; its result is kept till the
 * plan or a build changes.
 */
public class PlotGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation BENCH = AliveWorkplace.id("park_bench");
	/** The spot nearest the hall the Well fits: box x 8..14, z 10..16 (helper), centred at (11, 13). */
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	/** Flat grass over dirt, the hall and a Blueprint Table; no builds left from earlier batches near the area. */
	private static BlockPos ground(GameTestHelper helper) {
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
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		return helper.absolutePos(HALL);
	}

	/** Cells under helper x {@code x0..x1}, z {@code z0..z1}. */
	private static BitSet cells(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		BlockPos hall = helper.absolutePos(HALL);
		BitSet out = new BitSet();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				out.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		return out;
	}

	/** A Homes zone in {@code style} at helper x 6..29, z 2..25. */
	private static CityPlan homes(GameTestHelper helper, String style) {
		return CityPlan.EMPTY.addZone("homes", "Homes 1", style).paint(0, cells(helper, 6, 29, 2, 25));
	}

	/** The plan with a lane down helper x = 28, z 2..25 (east of the plots). */
	private static CityPlan withRoad(CityPlan plan) {
		return plan.addRoad(new CityPlan.Road(List.of(new BlockPos(26, 0, -12), new BlockPos(26, 0, 11)), CityPlan.Road.LANE, ""));
	}

	private static Plots.Verdict check(GameTestHelper helper, CityPlan plan, BlockPos spot, Rotation turn, Mirror mirror) {
		return Plots.check(helper.getLevel(), helper.absolutePos(HALL), plan, "homes", WELL, helper.absolutePos(spot), turn, mirror);
	}

	/** The Well at {@link #SPOT}, facing west (the hall, no road on the plan). */
	private static Plots.Verdict atSpot(GameTestHelper helper, CityPlan plan) {
		return check(helper, plan, SPOT, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	private static void fits(GameTestHelper helper, Plots.Verdict v, String what) {
		helper.assertTrue(v.plot().isPresent(), what + ": no plot, " + v.reason());
	}

	private static void refused(GameTestHelper helper, Plots.Verdict v, Plots.Reason reason, String what) {
		helper.assertTrue(v.reason().equals(Optional.of(reason)), what + ": expected " + reason + ", got " + v);
	}

	private static Optional<Plots.Plot> search(GameTestHelper helper, CityPlan plan, List<ResourceLocation> blueprints) {
		Plots.Search search = Plots.search(helper.getLevel(), helper.absolutePos(HALL), plan, new Plots.Request(blueprints, "homes"));
		search.finish();
		helper.assertTrue(search.maxColumnsInTick() <= Plots.COLUMNS_PER_TICK, "read " + search.maxColumnsInTick() + " columns in a tick");
		return search.result();
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin, Mirror mirror) {
		return new BlueprintData.Placement(Ids.of(helper.getLevel().dimension()), helper.absolutePos(origin), Rotation.NONE, mirror);
	}

	/** On flat ground inside the zone, nearest the hall: facing the road on the plan, else the hall; in the zone's style. */
	//$ gametest_ticks_batch AREA '20' '"plotFlat"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotFlat")
	public void plotOnFlatGroundFacesTheRoad(GameTestHelper helper) {
		ground(helper);
		Optional<Plots.Plot> plot = search(helper, withRoad(homes(helper, "")), List.of(WELL));
		helper.assertTrue(plot.isPresent(), "no plot on flat ground");
		BoundingBox box = plot.get().box();
		BlockPos min = helper.absolutePos(new BlockPos(8, 2, 0));
		helper.assertTrue(box.minX() == min.getX() && box.minY() == min.getY(), "not nearest the hall, on the ground: " + box + " (want x " + min.getX() + ", y " + min.getY() + ")");
		BlockPos zoneMin = helper.absolutePos(new BlockPos(6, 2, 2));
		BlockPos zoneMax = helper.absolutePos(new BlockPos(29, 2, 25));
		helper.assertTrue(box.minX() - 2 >= zoneMin.getX() && box.minZ() - 2 >= zoneMin.getZ() && box.maxX() + 2 <= zoneMax.getX()
			&& box.maxZ() + 2 <= zoneMax.getZ(), "the plot and its margin leave the zone: " + box);
		helper.assertTrue(plot.get().facing() == Direction.EAST && plot.get().placement().rotation() == Rotation.CLOCKWISE_90,
			"doesn't face the road (east): " + plot.get().facing());
		helper.assertTrue(plot.get().zone().equals("Homes 1") && plot.get().blueprint().equals(WELL), "wrong zone or blueprint: " + plot.get());
		// no road on the plan: it faces the hall (west)
		Optional<Plots.Plot> toHall = search(helper, homes(helper, ""), List.of(WELL));
		helper.assertTrue(toHall.isPresent() && toHall.get().facing() == Direction.WEST, "doesn't face the hall: " + toHall);
		// the zone's style
		Optional<Plots.Plot> styled = search(helper, homes(helper, "cherry"), List.of(WELL));
		helper.assertTrue(styled.isPresent() && styled.get().blueprint().equals(BlueprintStyles.styled(WELL, "cherry")),
			"not in the zone's style: " + styled);
		// a turn whose front looks away from the road is refused
		refused(helper, check(helper, withRoad(homes(helper, "")), SPOT, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE), Plots.Reason.FACING, "facing away");
		helper.succeed();
	}

	/** Never across the zone's edge: the margin of 2 counts, and a zone too narrow for it has no plot at all. */
	//$ gametest_ticks_batch AREA '20' '"plotZoneEdge"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotZoneEdge")
	public void noPlotAcrossTheZoneEdge(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper, "");
		fits(helper, atSpot(helper, plan), "the spot");
		refused(helper, check(helper, plan, SPOT.west(2), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE), Plots.Reason.ZONE_EDGE, "margin over the edge");
		refused(helper, check(helper, plan, SPOT.west(4), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE), Plots.Reason.ZONE_EDGE, "footprint over the edge");
		// a zone two cells (8 blocks) wide can't hold 7 blocks plus 2 each side
		CityPlan narrow = CityPlan.EMPTY.addZone("homes", "Narrow", "").paint(0, cells(helper, 6, 13, 2, 25));
		helper.assertTrue(search(helper, narrow, List.of(WELL)).isEmpty(), "a plot in a zone too narrow for it");
		// another kind of zone isn't looked at
		CityPlan farms = CityPlan.EMPTY.addZone("farms", "Farms", "").paint(0, cells(helper, 6, 29, 2, 25));
		helper.assertTrue(search(helper, farms, List.of(WELL)).isEmpty(), "a Homes plot in a Farms zone");
		helper.succeed();
	}

	/** Never over a player's cobblestone wall, a chest or placed leaves; natural trees, flowers and berry bushes are cleared. */
	//$ gametest_ticks_batch AREA '20' '"plotBlocked"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotBlocked")
	public void noPlotOverAWallOrAChest(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper, "");
		BlockPos in = new BlockPos(12, 2, 14);
		helper.setBlock(in, Blocks.COBBLESTONE_WALL);
		refused(helper, atSpot(helper, plan), Plots.Reason.BLOCKED, "over a cobblestone wall");
		Optional<Plots.Plot> elsewhere = search(helper, plan, List.of(WELL));
		helper.assertTrue(elsewhere.isPresent() && !elsewhere.get().box().isInside(helper.absolutePos(in)), "the plot covers the wall: " + elsewhere);
		helper.setBlock(in, Blocks.AIR);
		helper.setBlock(in, Blocks.CHEST);
		refused(helper, atSpot(helper, plan), Plots.Reason.BLOCKED, "over a chest");
		helper.setBlock(in, Blocks.AIR);
		// a natural tree, flowers, a berry bush and tall grass: all natural, the builder clears them
		helper.setBlock(in, Blocks.OAK_LOG);
		helper.setBlock(in.above(), Blocks.OAK_LOG);
		helper.setBlock(in.above(2), Blocks.OAK_LOG);
		for (BlockPos leaf : new BlockPos[]{in.above(2).east(), in.above(2).west(), in.above(2).north(), in.above(2).south(), in.above(3)}) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
		}
		helper.setBlock(new BlockPos(9, 2, 11), Blocks.POPPY);
		helper.setBlock(new BlockPos(10, 2, 15), Blocks.SWEET_BERRY_BUSH);
		helper.setBlock(new BlockPos(13, 2, 11), Blocks.SHORT_GRASS);
		fits(helper, atSpot(helper, plan), "a natural tree, flowers and a bush");
		// leaves a player placed are not natural
		helper.setBlock(new BlockPos(9, 2, 15), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
		refused(helper, atSpot(helper, plan), Plots.Reason.BLOCKED, "over placed leaves");
		helper.succeed();
	}

	/** Never within 2 blocks of another village's build site or a finished building: 1 block between is too near, 2 is clear. */
	//$ gametest_ticks_batch AREA '20' '"plotSites"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotSites")
	public void noPlotOverlappingAnotherVillagesSite(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper, "");
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		ResourceLocation stall = AliveWorkplace.id("market_stall"); // 8×7×6
		// another village's stall (x 12..19, z 16..21) overlapping the spot (box x 8..14, z 10..16)
		BuildSite site = manager.create(UUID.randomUUID(), "Someone", stall, placement(helper, new BlockPos(12, 2, 16), Mirror.NONE));
		Leftovers.after(helper, () -> manager.remove(site.id()));
		refused(helper, atSpot(helper, plan), Plots.Reason.BUILD, "over another village's site");
		Optional<Plots.Plot> plot = search(helper, plan, List.of(WELL));
		BlockPos siteMin = helper.absolutePos(new BlockPos(12, 2, 16));
		BlockPos siteMax = helper.absolutePos(new BlockPos(19, 2, 21));
		BoundingBox siteBox = new BoundingBox(siteMin.getX(), -64, siteMin.getZ(), siteMax.getX(), 400, siteMax.getZ());
		helper.assertTrue(plot.isPresent() && !plot.get().box().inflatedBy(2).intersects(siteBox), "the plot comes within 2 of the site: " + plot);
		manager.remove(site.id());
		fits(helper, atSpot(helper, plan), "the site gone");
		// a finished building with one block between (x 16; the spot ends at 14) is too near; with two (x 17) it's clear
		BlueprintData.Placement one = placement(helper, new BlockPos(16, 2, 10), Mirror.NONE);
		manager.recordFinished(stall, one, UUID.randomUUID());
		Leftovers.after(helper, () -> manager.forgetFinished(one));
		refused(helper, atSpot(helper, plan), Plots.Reason.BUILD, "1 block from a finished building");
		manager.forgetFinished(one);
		BlueprintData.Placement two = placement(helper, new BlockPos(17, 2, 10), Mirror.NONE);
		manager.recordFinished(stall, two, UUID.randomUUID());
		Leftovers.after(helper, () -> manager.forgetFinished(two));
		fits(helper, atSpot(helper, plan), "2 blocks clear of a finished building");
		helper.succeed();
	}

	/** Ground within 4 of level: a slope of 6 under the footprint is refused, a rise of 4 is levelled. */
	//$ gametest_ticks_batch AREA '20' '"plotSlope"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotSlope")
	public void noPlotOnASlopeOf6(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper, "");
		// the east half of the spot (x 12..14) a 6-block bank
		for (int x = 12; x <= 14; x++) {
			for (int z = 10; z <= 16; z++) {
				for (int y = 1; y <= 6; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
				}
				helper.setBlock(new BlockPos(x, 7, z), Blocks.GRASS_BLOCK);
			}
		}
		refused(helper, atSpot(helper, plan), Plots.Reason.SLOPE, "a slope of 6");
		// cut down to 4: the builder levels it
		for (int x = 12; x <= 14; x++) {
			for (int z = 10; z <= 16; z++) {
				helper.setBlock(new BlockPos(x, 7, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 6, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 5, z), Blocks.GRASS_BLOCK);
			}
		}
		Plots.Verdict v = atSpot(helper, plan);
		fits(helper, v, "a rise of 4");
		helper.assertTrue(v.plot().get().placement().origin().getY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(),
			"not on the level ground (the median): " + v.plot().get().placement());
		helper.succeed();
	}

	/** At most a tenth over water, none over lava. */
	//$ gametest_ticks_batch AREA '20' '"plotWater"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotWater")
	public void noPlotOverMuchWaterOrAnyLava(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper, "");
		int[][] pond = {{8, 10}, {9, 10}, {8, 11}, {9, 11}, {10, 10}};
		for (int i = 0; i < 4; i++) {
			helper.setBlock(new BlockPos(pond[i][0], 1, pond[i][1]), Blocks.WATER);
		}
		fits(helper, atSpot(helper, plan), "4 of 49 over water");
		helper.setBlock(new BlockPos(pond[4][0], 1, pond[4][1]), Blocks.WATER);
		refused(helper, atSpot(helper, plan), Plots.Reason.WATER, "5 of 49 over water");
		for (int[] p : pond) {
			helper.setBlock(new BlockPos(p[0], 1, p[1]), Blocks.GRASS_BLOCK);
		}
		helper.setBlock(new BlockPos(13, 1, 15), Blocks.LAVA);
		refused(helper, atSpot(helper, plan), Plots.Reason.LAVA, "one block of lava");
		helper.setBlock(new BlockPos(13, 1, 15), Blocks.GRASS_BLOCK);
		helper.succeed();
	}

	/**
	 * ROADMAP 27.11: a Ferry House goes on the shore, water within {@link Plots#SHORE} blocks of its front; nowhere else.
	 * Box x 11..20, z 9..17 (helper), its front to the west, the hall's way.
	 */
	//$ gametest_ticks_batch AREA '20' '"plotShore"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotShore")
	public void aFerryHouseGoesOnTheShore(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper, "");
		ResourceLocation ferry = AliveWorkplace.id("ferry_house");
		BlockPos spot = new BlockPos(16, 2, 13);
		java.util.function.Supplier<Plots.Verdict> at = () -> Plots.check(helper.getLevel(), helper.absolutePos(HALL), plan, "homes", ferry,
			helper.absolutePos(spot), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		refused(helper, at.get(), Plots.Reason.SHORE, "no water at all");
		helper.setBlock(new BlockPos(6, 1, 13), Blocks.WATER);
		refused(helper, at.get(), Plots.Reason.SHORE, "water 5 blocks in front");
		helper.setBlock(new BlockPos(7, 1, 13), Blocks.WATER);
		fits(helper, at.get(), "water 4 blocks in front");
		helper.setBlock(new BlockPos(6, 1, 13), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(7, 1, 13), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(22, 1, 13), Blocks.WATER);
		refused(helper, at.get(), Plots.Reason.SHORE, "water behind it, not in front");
		helper.setBlock(new BlockPos(22, 1, 13), Blocks.GRASS_BLOCK);
		// Other buildings don't care about water.
		helper.assertTrue(Plots.check(helper.getLevel(), helper.absolutePos(HALL), plan, "homes", AliveWorkplace.id("kitchen"),
			helper.absolutePos(spot), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE).plot().isPresent(), "a Kitchen there");
		helper.succeed();
	}

	/** Its centre within maxSiteDistance of a Blueprint Table; none in reach, no plot. */
	//$ gametest_ticks_batch AREA '20' '"plotTable"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotTable")
	public void noPlotOutOfATablesReach(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		int reach = Builders.MAX_SITE_DISTANCE;
		Builders.MAX_SITE_DISTANCE = 16;
		Leftovers.after(helper, () -> Builders.MAX_SITE_DISTANCE = reach);
		// tables left by earlier batches round the area (this test is alone in its batch)
		BlockPos corner = helper.absolutePos(BlockPos.ZERO);
		BoundingBox area = new BoundingBox(corner.getX(), 0, corner.getZ(), corner.getX() + 29, 0, corner.getZ() + 29);
		level.getPoiManager().findAll(h -> h.is(ModVillagers.BLUEPRINT_TABLE_POI) || h.is(ModVillagers.BUILDERS_BENCH_POI), p -> true,
				helper.absolutePos(HALL), 120, PoiManager.Occupancy.ANY).map(BlockPos::immutable).toList()
			.forEach(p -> {
				if (p.getX() < area.minX() || p.getX() > area.maxX() || p.getZ() < area.minZ() || p.getZ() > area.maxZ()) {
					level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				}
			});
		CityPlan plan = homes(helper, "");
		fits(helper, atSpot(helper, plan), "10 blocks from the table");
		// 22 blocks from the table: out of a reach of 16
		refused(helper, check(helper, plan, new BlockPos(23, 2, 13), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE), Plots.Reason.NO_TABLE, "22 blocks off");
		helper.setBlock(TABLE, Blocks.AIR);
		refused(helper, atSpot(helper, plan), Plots.Reason.NO_TABLE, "no table");
		helper.assertTrue(search(helper, plan, List.of(WELL)).isEmpty(), "a plot with no table");
		helper.succeed();
	}

	/** Never the same blueprint mirrored the same way within 24 blocks: it mirrors, or takes the request's next blueprint. */
	//$ gametest_ticks_batch AREA '20' '"plotSame"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "plotSame")
	public void noSameHouseMirroredTheSameWayNearby(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper, "");
		BuildSiteManager manager = BuildSiteManager.get(helper.getLevel());
		// a finished Well 12 blocks off, clear of the spot
		BlueprintData.Placement plain = placement(helper, new BlockPos(18, 2, 0), Mirror.NONE);
		manager.recordFinished(WELL, plain, UUID.randomUUID());
		Leftovers.after(helper, () -> manager.forgetFinished(plain));
		refused(helper, atSpot(helper, plan), Plots.Reason.SAME_NEARBY, "the same Well 12 blocks off");
		fits(helper, check(helper, plan, SPOT, Rotation.COUNTERCLOCKWISE_90, Mirror.FRONT_BACK), "the Well mirrored");
		Optional<Plots.Plot> mirrored = search(helper, plan, List.of(WELL));
		helper.assertTrue(mirrored.isPresent() && mirrored.get().placement().mirror() == Mirror.FRONT_BACK, "didn't mirror: " + mirrored);
		// both mirrorings near: the next blueprint
		BlueprintData.Placement flipped = placement(helper, new BlockPos(18, 2, 20), Mirror.FRONT_BACK);
		manager.recordFinished(WELL, flipped, UUID.randomUUID());
		Leftovers.after(helper, () -> manager.forgetFinished(flipped));
		Optional<Plots.Plot> next = search(helper, plan, List.of(WELL, BENCH));
		helper.assertTrue(next.isPresent() && next.get().blueprint().equals(BENCH), "didn't take the next blueprint: " + next);
		helper.succeed();
	}

	/**
	 * A search over a fully painted plan, run by the server's ticks: never more than 64 columns a tick for the hall (two
	 * searches share them), ends within 200 ticks, and its result is kept until the plan or a build changes.
	 */
	//$ gametest_ticks_batch AREA '300' '"plotBudget"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "plotBudget")
	public void searchKeepsItsBudgetAndEnds(GameTestHelper helper) {
		BlockPos hall = ground(helper);
		ServerLevel level = helper.getLevel();
		// water everywhere near the hall, so the search reads and turns down many columns
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				if (!new BlockPos(x, 2, z).equals(HALL) && !new BlockPos(x, 2, z).equals(TABLE)) {
					helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
				}
			}
		}
		BitSet all = new BitSet();
		all.set(0, CityPlan.GRID * CityPlan.GRID);
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Everywhere", "").paint(0, all);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.changePlan(plan, 10);
		Plots.Request well = new Plots.Request(List.of(WELL), "homes");
		Plots.Request house = new Plots.Request(List.of(AliveWorkplace.id("stone_house")), "homes");
		Plots.Search first = Plots.request(level, hall, well).orElseThrow();
		Plots.Search second = Plots.request(level, hall, house).orElseThrow();
		helper.assertTrue(Plots.request(level, hall, well).orElseThrow() == first, "asked again, the search started over");
		long start = level.getGameTime();
		AtomicInteger most = new AtomicInteger();
		helper.onEachTick(() -> most.accumulateAndGet(Plots.columnsLastTick(level, hall), Math::max));
		helper.succeedWhen(() -> {
			helper.assertTrue(most.get() <= Plots.COLUMNS_PER_TICK, "the hall read " + most.get() + " columns in a tick");
			helper.assertTrue(first.done() && second.done(), "still searching after " + (level.getGameTime() - start) + " ticks");
			long took = level.getGameTime() - start;
			helper.assertTrue(took <= 200, "took " + took + " ticks");
			helper.assertTrue(most.get() > 0 && first.columnsRead() > 0, "read no columns");
			helper.assertTrue(Plots.result(level, hall, well).isPresent(), "no result kept");
			// unchanged: the same result; a zone changed or a new build in the plan's area: searched again
			helper.assertTrue(Plots.request(level, hall, well).orElseThrow() == first, "the result wasn't kept");
			BitSet one = new BitSet();
			one.set(0);
			entity.changePlan(entity.plan().erase(one), 10);
			Plots.Search afterZone = Plots.request(level, hall, well).orElseThrow();
			helper.assertTrue(afterZone != first, "a zone changed but the old result was kept");
			BuildSite site = BuildSiteManager.get(level).create(UUID.randomUUID(), "Someone", AliveWorkplace.id("market_stall"),
				placement(helper, new BlockPos(20, 2, 20), Mirror.NONE));
			try {
				helper.assertTrue(Plots.request(level, hall, well).orElseThrow() != afterZone, "a build went up but the old result was kept");
			} finally {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
	}
}
