package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA for ROADMAP 27.5 (the Steward: appointing by distance, the open-build table, save and reload, his trades) and 27.7
 * (finding a plot: every boundary one block either side). Written from the spec; complements StewardGameTests and
 * PlotGameTests.
 */
public class QaStewardPlotsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	// ---------------------------------------------------------------- 27.7 plots (same layout as PlotGameTests)

	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	/** The Well (7×5×7) here has box x 8..14, z 10..16 (helper), centre (11.5, 13.5). */
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private static BlockPos ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 80, level.getMinBuildHeight(), a.getZ() - 80, a.getX() + 110, level.getMaxBuildHeight(), a.getZ() + 110);
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

	private static BitSet cells(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		BlockPos hall = helper.absolutePos(HALL);
		BitSet out = new BitSet();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int cell = CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z)));
				if (cell >= 0) { // off the grid of a small village (theTablesReachIsInclusive)
					out.set(cell);
				}
			}
		}
		return out;
	}

	/** A Homes zone at helper x 6..29, z 2..25 (cells of 4). */
	private static CityPlan homes(GameTestHelper helper) {
		return CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells(helper, 6, 29, 2, 25));
	}

	private static Plots.Verdict check(GameTestHelper helper, CityPlan plan, BlockPos spot) {
		return Plots.check(helper.getLevel(), helper.absolutePos(HALL), plan, "homes", WELL, helper.absolutePos(spot), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	private static void fits(GameTestHelper helper, Plots.Verdict v, String what) {
		helper.assertTrue(v.plot().isPresent(), what + ": no plot, " + v.reason());
	}

	private static void refused(GameTestHelper helper, Plots.Verdict v, Plots.Reason reason, String what) {
		helper.assertTrue(v.reason().equals(Optional.of(reason)), what + ": expected " + reason + ", got " + v);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin, Mirror mirror) {
		return new BlueprintData.Placement(Ids.of(helper.getLevel().dimension()), helper.absolutePos(origin), Rotation.NONE, mirror);
	}

	/** The margin of 2 counts to the block on both sides: the zone's first column as the margin fits, one block over doesn't. */
	//$ gametest_ticks_batch AREA '20' '"qaPlotEdgeByOne"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotEdgeByOne")
	public void theMarginReachesTheZoneEdgeButNotOneBlockPast(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper);
		// west: box x 8..14, margin to 6 = the zone's first column
		fits(helper, check(helper, plan, SPOT), "margin exactly on the west edge");
		refused(helper, check(helper, plan, SPOT.west(1)), Plots.Reason.ZONE_EDGE, "margin 1 past the west edge");
		// east: centre 24 -> box x 21..27, margin to 29 = the zone's last column; centre 25 -> margin to 30
		fits(helper, check(helper, plan, new BlockPos(24, 2, 13)), "margin exactly on the east edge");
		refused(helper, check(helper, plan, new BlockPos(25, 2, 13)), Plots.Reason.ZONE_EDGE, "margin 1 past the east edge");
		// north: centre z 7 -> box z 4..10, margin to 2 = the zone's first row; z 6 -> margin to 1
		fits(helper, check(helper, plan, new BlockPos(11, 2, 7)), "margin exactly on the north edge");
		refused(helper, check(helper, plan, new BlockPos(11, 2, 6)), Plots.Reason.ZONE_EDGE, "margin 1 past the north edge");
		helper.succeed();
	}

	/** Within 4 of level: a rise of exactly 5 is refused (PlotGameTests covers 4 and 6). */
	//$ gametest_ticks_batch AREA '20' '"qaPlotSlope5"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotSlope5")
	public void aRiseOfFiveIsRefused(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper);
		// one column of the footprint (14, 16) raised: ground at 6 where the rest is at 1
		for (int y = 1; y <= 5; y++) {
			helper.setBlock(new BlockPos(14, y, 16), Blocks.DIRT);
		}
		helper.setBlock(new BlockPos(14, 6, 16), Blocks.GRASS_BLOCK);
		refused(helper, check(helper, plan, SPOT), Plots.Reason.SLOPE, "a rise of 5 in one corner");
		helper.setBlock(new BlockPos(14, 6, 16), Blocks.AIR);
		helper.setBlock(new BlockPos(14, 5, 16), Blocks.GRASS_BLOCK);
		fits(helper, check(helper, plan, SPOT), "a rise of 4 in one corner");
		helper.succeed();
	}

	/** A running build site (not only a finished building): 1 block between is too near, 2 is clear. */
	//$ gametest_ticks_batch AREA '20' '"qaPlotSiteClear"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotSiteClear")
	public void aRunningSiteNeedsTwoBlocksClear(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper);
		BuildSiteManager manager = BuildSiteManager.get(helper.getLevel());
		ResourceLocation stall = AliveWorkplace.id("market_stall");
		BuildSite one = manager.create(UUID.randomUUID(), "Someone", stall, placement(helper, new BlockPos(16, 2, 10), Mirror.NONE));
		Leftovers.after(helper, () -> manager.remove(one.id()));
		refused(helper, check(helper, plan, SPOT), Plots.Reason.BUILD, "1 block from a running site");
		manager.remove(one.id());
		BuildSite two = manager.create(UUID.randomUUID(), "Someone", stall, placement(helper, new BlockPos(17, 2, 10), Mirror.NONE));
		Leftovers.after(helper, () -> manager.remove(two.id()));
		fits(helper, check(helper, plan, SPOT), "2 blocks clear of a running site");
		manager.remove(two.id());
		// a site to the north-west corner diagonal, its box one block off on both axes: still within 2
		BuildSite diag = manager.create(UUID.randomUUID(), "Someone", stall, placement(helper, new BlockPos(16, 2, 18), Mirror.NONE));
		Leftovers.after(helper, () -> manager.remove(diag.id()));
		refused(helper, check(helper, plan, SPOT), Plots.Reason.BUILD, "a site 1 block off diagonally");
		helper.succeed();
	}

	/**
	 * Its centre within maxSiteDistance of a table outside the village: exactly at the distance fits, one block more doesn't.
	 * 36.20: a table in the village reaches all of it, so the village is made small (hall radius 20) to leave the tables out.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaPlotTableReach"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotTableReach")
	public void theTablesReachIsInclusive(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		int reach = Builders.MAX_SITE_DISTANCE;
		Builders.MAX_SITE_DISTANCE = 16;
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 20; // the tables 25 and 26 from the hall are outside it
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			Builders.MAX_SITE_DISTANCE = reach;
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		});
		BlockPos corner = helper.absolutePos(BlockPos.ZERO);
		level.getPoiManager().findAll(h -> h.is(ModVillagers.BLUEPRINT_TABLE_POI) || h.is(ModVillagers.BUILDERS_BENCH_POI), p -> true,
				helper.absolutePos(HALL), 120, PoiManager.Occupancy.ANY).map(BlockPos::immutable).toList()
			.forEach(p -> {
				if (p.getX() < corner.getX() || p.getX() > corner.getX() + 29 || p.getZ() < corner.getZ() || p.getZ() > corner.getZ() + 29) {
					level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				}
			});
		helper.setBlock(TABLE, Blocks.AIR);
		CityPlan plan = homes(helper);
		// the Well's centre (11.5, 13.5); a table at (27, 13) has its centre (27.5, 13.5): exactly 16 off
		helper.setBlock(new BlockPos(27, 2, 13), ModBlocks.BLUEPRINT_TABLE);
		fits(helper, check(helper, plan, SPOT), "a table exactly 16 off");
		helper.setBlock(new BlockPos(27, 2, 13), Blocks.AIR);
		helper.setBlock(new BlockPos(28, 2, 13), ModBlocks.BLUEPRINT_TABLE);
		refused(helper, check(helper, plan, SPOT), Plots.Reason.NO_TABLE, "a table 17 off");
		helper.setBlock(new BlockPos(28, 2, 13), Blocks.AIR);
		helper.succeed();
	}

	/** Never the same blueprint mirrored the same way within 24: a Well 24 off is too near, 26 off is not (also diagonally). */
	//$ gametest_ticks_batch AREA '20' '"qaPlotSameRange"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotSameRange")
	public void theSameHouseRuleEndsAtTwentyFour(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper);
		BuildSiteManager manager = BuildSiteManager.get(helper.getLevel());
		// a finished Well with origin (ox, oz), not turned: its centre (ox + 3.5, oz + 3.5); the spot's centre (11.5, 13.5)
		// dx 24 refused, 26 fits; (16, 17) = 545 refused, (18, 18) = 648 fits (25 and (17, 17): QaStewardBugTests)
		int[][] cases = {{32, 10, 0}, {34, 10, 1}, {24, 27, 0}, {26, 28, 1}};
		for (int[] c : cases) {
			BlueprintData.Placement p = placement(helper, new BlockPos(c[0], 2, c[1]), Mirror.NONE);
			manager.recordFinished(WELL, p, UUID.randomUUID());
			Leftovers.after(helper, () -> manager.forgetFinished(p));
			Plots.Verdict v = check(helper, plan, SPOT);
			if (c[2] == 0) {
				refused(helper, v, Plots.Reason.SAME_NEARBY, "a Well with origin (" + c[0] + ", " + c[1] + ")");
			} else {
				fits(helper, v, "a Well with origin (" + c[0] + ", " + c[1] + ")");
			}
			manager.forgetFinished(p);
		}
		helper.succeed();
	}

	/** Lava in the margin round the footprint isn't under it; lava under its very corner is. */
	//$ gametest_ticks_batch AREA '20' '"qaPlotLavaCorner"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotLavaCorner")
	public void lavaUnderTheCornerIsRefusedButNotNextToIt(GameTestHelper helper) {
		ground(helper);
		CityPlan plan = homes(helper);
		helper.setBlock(new BlockPos(15, 1, 17), Blocks.LAVA);
		fits(helper, check(helper, plan, SPOT), "lava just outside the corner");
		helper.setBlock(new BlockPos(15, 1, 17), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(14, 1, 16), Blocks.LAVA);
		refused(helper, check(helper, plan, SPOT), Plots.Reason.LAVA, "lava under the corner");
		helper.setBlock(new BlockPos(14, 1, 16), Blocks.GRASS_BLOCK);
		// a player's cobblestone at the corner, on the ground: refused
		helper.setBlock(new BlockPos(8, 2, 10), Blocks.COBBLESTONE);
		refused(helper, check(helper, plan, SPOT), Plots.Reason.BLOCKED, "cobblestone at the corner");
		helper.succeed();
	}

	/** A search never reads more than 64 columns in a tick, and its plot is the one nearest the hall. */
	//$ gametest_ticks_batch AREA '20' '"qaPlotSearchNearest"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaPlotSearchNearest")
	public void aSearchKeepsToItsBudgetAndPicksTheNearest(GameTestHelper helper) {
		BlockPos hall = ground(helper);
		CityPlan plan = homes(helper);
		// a wall where the nearest spot is: the next nearest is taken, still within budget
		helper.setBlock(new BlockPos(9, 2, 13), Blocks.COBBLESTONE_WALL);
		Plots.Search search = Plots.search(helper.getLevel(), hall, plan, new Plots.Request(List.of(WELL), "homes"));
		search.finish();
		helper.assertTrue(search.maxColumnsInTick() <= Plots.COLUMNS_PER_TICK && search.maxColumnsInTick() > 0,
			"read " + search.maxColumnsInTick() + " columns in a tick");
		Optional<Plots.Plot> plot = search.result();
		helper.assertTrue(plot.isPresent(), "no plot");
		helper.assertTrue(!plot.get().box().isInside(helper.absolutePos(new BlockPos(9, 2, 13))), "over the wall: " + plot.get().box());
		// no spot that fits is nearer the hall than the one found
		double found = Math.sqrt(sq(plot.get().box(), hall));
		for (int x = 6; x <= 20; x++) {
			for (int z = 2; z <= 25; z++) {
				Plots.Verdict v = check(helper, plan, new BlockPos(x, 2, z));
				if (v.plot().isPresent()) {
					helper.assertTrue(Math.sqrt(sq(v.plot().get().box(), hall)) >= found - 1.5,
						"a spot at (" + x + ", " + z + ") is nearer the hall than the one found " + plot.get().box());
				}
			}
		}
		helper.succeed();
	}

	private static double sq(BoundingBox box, BlockPos hall) {
		double cx = (box.minX() + box.maxX() + 1) / 2.0 - hall.getX();
		double cz = (box.minZ() + box.maxZ() + 1) / 2.0 - hall.getZ();
		return cx * cx + cz * cz;
	}

	// ---------------------------------------------------------------- 27.5 the Steward

	private static final BlockPos HALL_A = new BlockPos(5, 2, 5);

	private static void otherHalls(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		for (BlockPos old : level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), q -> true,
			helper.absolutePos(at), 180, PoiManager.Occupancy.ANY).map(BlockPos::immutable).toList()) {
			level.removeBlock(old, false);
		}
	}

	private static ItemStack planFor(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ItemStack stack = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(helper.getLevel(), player, stack, helper.absolutePos(hall));
		return stack;
	}

	private static boolean stewardAt(GameTestHelper helper, Villager villager, BlockPos hall) {
		return villager.getVillagerData().getProfession() == ModVillagers.STEWARD && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(hall))::equals).isPresent();
	}

	/** Every level × rank × cap: min(1, 2, 2, 3, 4 by level; Hamlet 1 .. City 4; stewardMaxOpenBuilds). */
	//$ gametest_batch EMPTY_STRUCTURE '"qaStewardOpenTable"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaStewardOpenTable")
	public void openBuildsEveryLevelRankAndCap(GameTestHelper helper) {
		int was = Stewards.MAX_OPEN_BUILDS;
		int[] byLevel = {1, 2, 2, 3, 4};
		try {
			for (int cap : new int[] {4, 3, 1}) {
				Stewards.MAX_OPEN_BUILDS = cap;
				for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
					int byRank = switch (rank.name()) {
						case "HAMLET" -> 1;
						case "VILLAGE" -> 2;
						case "TOWN" -> 3;
						case "CITY" -> 4;
						default -> throw new IllegalStateException("a rank the spec doesn't name: " + rank);
					};
					for (int l = 1; l <= 5; l++) {
						int want = Math.min(Math.min(byLevel[l - 1], byRank), cap);
						int got = Stewards.maxOpenBuilds(l, rank);
						helper.assertTrue(got == want, "level " + l + ", " + rank + ", cap " + cap + ": " + got + " open, want " + want);
					}
				}
			}
		} finally {
			Stewards.MAX_OPEN_BUILDS = was;
		}
		helper.succeed();
	}

	/** "Standing by the hall": just inside the reach he's appointed, just outside he isn't. */
	//$ gametest_ticks_batch AREA '40' '"qaStewardReach"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaStewardReach")
	public void appointedJustInsideTheHallsReachNotJustOutside(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL_A);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player, HALL_A);
		double edge = Stewards.REACH + 1; // the hall's centre to his feet
		// his feet half a block under the hall's centre: horizontal d with d² + 0.25 against edge²
		double inside = Math.sqrt(edge * edge - 0.25) - 0.1;
		double outside = Math.sqrt(edge * edge - 0.25) + 0.1;
		Villager far = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, new Vec3(HALL_A.getX() + 0.5 + outside, 2, HALL_A.getZ() + 0.5)));
		far.setNoAi(true);
		helper.assertTrue(Stewards.appoint(player, far, plan) == InteractionResult.CONSUME && far.getVillagerData().getProfession() == ModVillagers.BUILDER,
			"appointed " + outside + " blocks from the hall's centre");
		Villager near = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, new Vec3(HALL_A.getX() + 0.5, 2, HALL_A.getZ() + 0.5 + inside)));
		near.setNoAi(true);
		helper.assertTrue(Stewards.appoint(player, near, plan) == InteractionResult.SUCCESS && stewardAt(helper, near, HALL_A),
			"not appointed " + inside + " blocks from the hall's centre");
		helper.succeed();
	}

	/** A Steward saved and loaded keeps his job and his hall; the hall's one place stays his, so nobody else takes it. */
	//$ gametest_ticks_batch AREA '200' '"qaStewardReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaStewardReload")
	public void aStewardSurvivesSaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL_A);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player, HALL_A);
		Villager first = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		helper.assertTrue(Stewards.appoint(player, first, plan) == InteractionResult.SUCCESS, "not appointed");
		CompoundTag tag = new CompoundTag();
		first.saveWithoutId(tag);
		first.discard();
		Villager loaded = EntityType.VILLAGER.create(level);
		loaded.load(tag);
		loaded.setUUID(UUID.randomUUID());
		level.addFreshEntity(loaded);
		helper.assertTrue(stewardAt(helper, loaded, HALL_A), "lost the job on reload: " + loaded.getVillagerData() + " "
			+ loaded.getBrain().getMemory(MemoryModuleType.JOB_SITE));
		helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(HALL_A)) == 0, "the hall's place came free on reload");
		Villager second = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.south(2)));
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(stewardAt(helper, loaded, HALL_A), "dismissed after reload: " + loaded.getVillagerData());
			String before = "loaded alive " + loaded.isAlive() + " removed " + loaded.getRemovalReason() + " at " + loaded.blockPosition()
				+ ", second " + second.getVillagerData() + " alive " + second.isAlive() + " at " + second.blockPosition()
				+ ", free places " + level.getPoiManager().getFreeTickets(helper.absolutePos(HALL_A)) + ", time " + level.getDayTime();
			InteractionResult result = Stewards.appoint(player, second, plan);
			helper.assertTrue(result == InteractionResult.CONSUME && second.getVillagerData().getProfession() == ModVillagers.BUILDER,
				"a second Steward took the reloaded one's hall: " + result + ", before: " + before);
			Villager found = Stewards.stewardOf(level, helper.absolutePos(HALL_A));
			// B695: says who the hall's Steward is instead (a leftover from an earlier batch at this spot, or the second)
			helper.assertTrue(found == loaded, "the hall's Steward isn't the reloaded one: "
				+ (found == null ? "none" : (found == second ? "the second" : "another") + " at " + found.blockPosition() + ", age " + found.tickCount)
				+ "; the reloaded at " + loaded.blockPosition());
			helper.succeed();
		});
	}

	/**
	 * B687: a Steward who is somewhere nothing is loaded (a far stop of his rounds, a hop that put him in a chunk that then
	 * unloaded) still holds his hall. Neither the hall's first tick nor an appointment may give his place to someone else
	 * because he can't be seen: when he comes back there would be two Stewards of one hall.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaStewardAway"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStewardAway")
	public void aStewardAwayInAnUnloadedChunkKeepsHisHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL_A);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL_A);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player, HALL_A);
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		steward.setNoAi(true);
		helper.assertTrue(Stewards.appoint(player, steward, plan) == InteractionResult.SUCCESS, "not appointed");
		// Somewhere within the hall's reach whose chunk isn't loaded (the GameTest server loads only the test areas).
		BlockPos away = null;
		for (int[] d : new int[][] {{100, 100}, {-100, 100}, {100, -100}, {-100, -100}, {110, 0}, {-110, 0}, {0, 110}, {0, -110}}) {
			BlockPos p = hall.offset(d[0], 0, d[1]);
			if (!level.isLoaded(p)) {
				away = p;
				break;
			}
		}
		helper.assertTrue(away != null, "no unloaded chunk near the test area");
		steward.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.assertTrue(Stewards.stewardOf(level, hall) == null, "the Steward away at " + away + " is still seen: the test proves nothing");
		helper.runAfterDelay(5, () -> {
			// the hall has ticked (its first tick checks its Steward's place) with him away
			helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 0, "the hall's place came free while its Steward was away");
			Villager second = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.south(2)));
			second.setNoAi(true);
			InteractionResult result = Stewards.appoint(player, second, plan);
			helper.assertTrue(result == InteractionResult.CONSUME && second.getVillagerData().getProfession() == ModVillagers.BUILDER
				&& level.getPoiManager().getFreeTickets(hall) == 0, "a second Steward took the hall of one who is away: " + result);
			// He comes back: still the hall's one Steward.
			steward.teleportTo(helper.absolutePos(HALL_A.east(2)).getX() + 0.5, hall.getY(), hall.getZ() + 0.5);
			helper.assertTrue(Stewards.stewardOf(level, hall) == steward && stewardAt(helper, steward, HALL_A), "the Steward back from away isn't the hall's");
			steward.discard();
			helper.succeed();
		});
	}

	/** Not a grown-up, not by his hall, or a nitwit: nobody appointed, the hall's place stays free. */
	//$ gametest_ticks_batch AREA '40' '"qaStewardWhoNot"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaStewardWhoNot")
	public void aChildAFarVillagerOrAPlainVillagerIsntAppointed(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL_A);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player, HALL_A);
		Villager child = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		child.setAge(-24000);
		helper.assertTrue(Stewards.appoint(player, child, plan) == InteractionResult.CONSUME && child.getVillagerData().getProfession() != ModVillagers.STEWARD,
			"a seasoned child was appointed");
		Villager far = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(12)));
		helper.assertTrue(Stewards.appoint(player, far, plan) == InteractionResult.CONSUME && far.getVillagerData().getProfession() == ModVillagers.BUILDER,
			"a villager 12 blocks off was appointed");
		Villager plain = helper.spawn(EntityType.VILLAGER, HALL_A.south(2));
		helper.assertTrue(Stewards.appoint(player, plain, plan) == InteractionResult.CONSUME && plain.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a jobless villager was appointed");
		helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(HALL_A)) == 1, "the hall's place went");
		// another item than the plan does nothing (PASS: the villager's own trade screen)
		helper.assertTrue(Stewards.appoint(player, plain, new ItemStack(Items.PAPER)) == InteractionResult.PASS, "paper did something");
		helper.succeed();
	}

	/** His trades: City Plans, Blank Blueprints and Village Ledgers for emeralds; he buys paper and books. */
	//$ gametest_ticks_batch AREA '40' '"qaStewardTrades"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaStewardTrades")
	public void hisTradesAreTheVillagesPaperwork(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, HALL_A.east(2));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.STEWARD));
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.STEWARD);
		helper.assertTrue(byLevel != null, "the Steward has no trades");
		Set<Item> sells = new HashSet<>();
		Set<Item> buys = new HashSet<>();
		RandomSource random = RandomSource.create(27);
		for (int l = 1; l <= 5; l++) {
			VillagerTrades.ItemListing[] listings = byLevel.get(l);
			helper.assertTrue(listings != null && listings.length > 0, "no trades at level " + l);
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(v, random);
				helper.assertTrue(offer != null, "a trade at level " + l + " made nothing");
				if (offer.getCostA().is(Items.EMERALD)) {
					sells.add(offer.getResult().getItem());
				}
				if (offer.getResult().is(Items.EMERALD)) {
					buys.add(offer.getCostA().getItem());
				}
			}
		}
		helper.assertTrue(sells.containsAll(List.of(ModItems.CITY_PLAN, ModItems.BLANK_BLUEPRINT, ModItems.VILLAGE_LEDGER)), "sells " + sells);
		helper.assertTrue(buys.contains(Items.PAPER) && buys.contains(Items.BOOK), "buys " + buys);
		// a Novice's own offers, as the game makes them
		helper.assertTrue(!v.getOffers().isEmpty(), "a Novice Steward offers nothing");
		helper.succeed();
	}

	/** A hall registered again for its old 0-ticket record (27.5) still isn't a job site a jobless villager takes by himself. */
	//$ gametest_ticks_batch AREA '500' '"qaStewardOldHallJobless"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaStewardOldHallJobless")
	public void anOldHallRegisteredAgainIsntTakenByAJoblessVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL_A);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL_A);
		level.getPoiManager().take(h -> h.is(ModVillagers.VILLAGE_HALL_POI), (h, p) -> p.equals(hall), hall, 1);
		helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 0, "setup: the place is still free");
		Stewards.fixTicket(level, hall);
		helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 1, "not registered again");
		Villager villager = helper.spawn(EntityType.VILLAGER, HALL_A.east(1));
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "took a job: " + villager.getVillagerData());
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "took the hall as his job site");
			helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 1, "the hall's place was taken");
			helper.succeed();
		});
	}
}
