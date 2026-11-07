package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.VILLAGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.oldBuild;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane b, 2026-11-07, second pass): the three builder fixes B974, B981 and B988, from their wiki pages, the
 * neighbours of each fix the bug tests on main don't reach.
 * <ul>
 * <li>B974 (docs/wiki/cellars.md: "the building gone" is "under a quarter of its blocks on and above the ground still
 * standing (solid, whatever the block)"; "its finished record stays ... so the check is made on the blocks each time"):
 * the quarter from both sides on one Smithy, grass and snow grown over the plot not counting, other blocks put back
 * counting, and the trade and the hall's advice coming back when it stands again.</li>
 * <li>B981 (docs/wiki/builder.md: "ground a little lower than a full block (... farmland, mud, soul sand) is walked over
 * and left as it is, and a Steward's road crosses it the same way"): a real build's path over bands of farmland, mud
 * and soul sand; a Steward's road over mud and old path, never over a field's farmland or soul sand.</li>
 * <li>B988 (docs/wiki/grander-homes.md: "every builder in [the builder's village] (even one on its edge up a hill)
 * sells the Townhouse", the Manor "once it has a household of Burghers"): the Manor on the rim up a hill, a builder
 * exactly on the circle, a builder down a hill from the hall, a builder on the rim levelling up, and the rim with only
 * Peasants selling neither.</li>
 * </ul>
 */
public class QaBuilderFixes1107bGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final net.minecraft.resources.ResourceLocation TEST_HUT =
		net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// --- B974: the building gone ---------------------------------------------------------------------------------------

	/**
	 * "Under a quarter of its blocks on and above the ground still standing (solid, whatever the block)" is gone, from
	 * both sides on one old Smithy, through a player opening the builder's trades:
	 * <ol>
	 * <li>torn down with one block in five put back as cobblestone (20%), and short grass and snow grown over other
	 * spots of the plot (not solid): no "Dig the cellar" sold, none advised;</li>
	 * <li>more cobblestone put back, to three in ten (30%, not the blocks it was built with): it stands again, its
	 * finished record kept, so the trade and the advice come back.</li>
	 * </ol>
	 */
	//$ gametest_ticks_batch TALL_AREA '200' '"qaFixes1107bQuarter"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qaFixes1107bQuarter")
	public void aSmithyUnderAQuarterStandingIsGoneAndOverAQuarterIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		ServerPlayer player = QaGranderHomes1107GameTests.player(helper);
		try {
			QaGranderHomes1107GameTests.openTrades(helper, villager, player);
			helper.assertTrue(sells(villager, placement), "setup: the old Smithy isn't sold its \"Dig the cellar\"");
			List<BlockPos> drawn = new ArrayList<>();
			for (Blueprint.Entry e : BlueprintLibrary.get(level, StarterBlueprints.SMITHY.id()).orElseThrow().blocks()) {
				if (e.pos().getY() >= 0 && !e.state().isAir()) {
					drawn.add(placement.origin().offset(e.pos()).immutable());
				}
			}
			int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			for (BlockPos p : drawn) {
				level.removeBlockEntity(p);
				level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
			}
			int standing = 0;
			int overgrown = 0;
			for (int i = 0; i < drawn.size(); i++) {
				if (i % 20 < 4) {
					level.setBlock(drawn.get(i), Blocks.COBBLESTONE.defaultBlockState(), flags);
					standing++;
				} else if (i % 20 == 10) {
					level.setBlock(drawn.get(i), Blocks.SHORT_GRASS.defaultBlockState(), flags);
					overgrown++;
				} else if (i % 20 == 15) {
					level.setBlock(drawn.get(i), Blocks.SNOW.defaultBlockState(), flags);
					overgrown++;
				}
			}
			double low = standing / (double) drawn.size();
			QaGranderHomes1107GameTests.openTrades(helper, villager, player);
			boolean soldLow = sells(villager, placement);
			boolean advisedLow = advised(level, placement);
			for (int i = 0; i < drawn.size(); i++) {
				if (i % 20 == 4 || i % 20 == 5) {
					level.setBlock(drawn.get(i), Blocks.COBBLESTONE.defaultBlockState(), flags);
					standing++;
				}
			}
			double high = standing / (double) drawn.size();
			QaGranderHomes1107GameTests.openTrades(helper, villager, player);
			boolean soldHigh = sells(villager, placement);
			boolean advisedHigh = advised(level, placement);
			LOGGER.info("qa fixes 1107b quarter: {} drawn, {}% standing (+{} grass/snow): sold {} advised {}; {}% standing: sold {} advised {}",
				drawn.size(), Math.round(low * 100), overgrown, soldLow, advisedLow, Math.round(high * 100), soldHigh, advisedHigh);
			helper.assertTrue(drawn.size() > 100 && low < 0.22 && high > 0.28, "setup: " + drawn.size() + " blocks, " + low + " then " + high);
			helper.assertFalse(soldLow, "the Smithy with only " + Math.round(low * 100) + "% of it standing (under a quarter) is still sold \"Dig the cellar\"");
			helper.assertFalse(advisedLow, "the Smithy with only " + Math.round(low * 100) + "% of it standing is still advised by the hall");
			helper.assertTrue(soldHigh, "the Smithy standing again at " + Math.round(high * 100) + "% (a quarter or more, cobblestone counts) isn't sold \"Dig the cellar\" again");
			helper.assertTrue(advisedHigh, "the Smithy standing again at " + Math.round(high * 100) + "% isn't advised by the hall again");
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	private static boolean sells(Villager villager, BlueprintData.Placement placement) {
		return villager.getOffers().stream().map(MerchantOffer::getResult).anyMatch(r -> Cellars.isJob(r)
			&& BlueprintItem.data(r).flatMap(BlueprintData::placement).equals(Optional.of(placement)));
	}

	private static boolean advised(ServerLevel level, BlueprintData.Placement placement) {
		return Cellars.toDig(level, placement.origin(), 64).stream().anyMatch(f -> f.placement().equals(placement));
	}

	// --- B981: low ground walked over ----------------------------------------------------------------------------------

	/**
	 * "Farmland, mud, soul sand ... walked over and left as it is": a band of each right across the way from a finished
	 * hut to the bell. The real build's path crosses them, none of the band is turned into path (farmland may only dry
	 * back to dirt by itself), and the path runs on both sides and reaches the bell.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaFixes1107bLowBands"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaFixes1107bLowBands")
	public void aBuildersPathCrossesFarmlandMudAndSoulSandAndLeavesThem(GameTestHelper helper) {
		ServerLevel level = QaBuilderPaths1105GameTests.arena(helper);
		Block[] band = {Blocks.FARMLAND, Blocks.MUD, Blocks.SOUL_SAND};
		for (int x = 0; x < 30; x++) {
			for (int i = 0; i < band.length; i++) {
				helper.setBlock(new BlockPos(x, 1, 14 + i), band[i]);
			}
		}
		BlockPos bell = new BlockPos(14, 2, 27);
		helper.setBlock(bell, Blocks.BELL);
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 24), QaBenchMovePathsPaceGameTests.hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(12, 2, 2)));
		helper.succeedWhen(() -> {
			QaBuilderPaths1105GameTests.done(helper, level, site, builder);
			for (int x = 0; x < 30; x++) {
				for (int i = 0; i < band.length; i++) {
					BlockPos p = new BlockPos(x, 1, 14 + i);
					BlockState now = helper.getBlockState(p);
					boolean kept = now.is(band[i]) || band[i] == Blocks.FARMLAND && now.is(Blocks.DIRT);
					helper.assertTrue(kept, "the " + band[i].getName().getString() + " at " + p + " is now " + now);
				}
			}
			int north = QaBuilderPaths1105GameTests.paths(helper, 0, 13);
			int south = QaBuilderPaths1105GameTests.paths(helper, 17, 29);
			helper.assertTrue(north >= 4 && south >= 4, "the path should run on both sides of the bands: " + north + " north, " + south + " south");
			helper.assertTrue(QaBuilderPaths1105GameTests.near(helper, bell, 3), "the path doesn't reach the bell");
			LOGGER.info("qa fixes 1107b low bands: path {} north, {} south of farmland, mud and soul sand, bands kept", north, south);
			helper.succeed();
		});
	}

	/**
	 * "A Steward's road crosses it the same way", within the Steward's own rule that a road takes only natural ground or
	 * road (steward.md, Roads.clear's javadoc: "anything built is never a road's"): a road (width 1, Roads.clear) crosses a
	 * band of mud and a band of old dirt path right across its way, over each, not round; across a band of a farm's
	 * farmland or of soul sand it lays no road over either.
	 */
	//$ gametest_batch HUGE '"qaFixes1107bRoad"'
	@GameTest(template = HUGE, batch = "qaFixes1107bRoad")
	public void aStewardsRoadCrossesMudAndOldPathButNeverAField(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> over = road(helper, level, Blocks.MUD, Blocks.DIRT_PATH);
		helper.assertTrue(!over.isEmpty(), "no road across the mud and the old dirt path");
		for (Block b : List.of(Blocks.MUD, Blocks.DIRT_PATH)) {
			helper.assertTrue(over.stream().anyMatch(p -> level.getBlockState(p.below()).is(b)), "the road goes round the " + b.getName().getString() + " instead of over it");
		}
		for (Block b : List.of(Blocks.FARMLAND, Blocks.SOUL_SAND)) {
			List<BlockPos> field = road(helper, level, b, b);
			helper.assertTrue(field.stream().noneMatch(p -> level.getBlockState(p.below()).is(b)), "a Steward's road was laid over " + b.getName().getString() + ": " + field);
		}
		LOGGER.info("qa fixes 1107b road: {} blocks over mud and old path; none over farmland or soul sand", over.size());
		helper.succeed();
	}

	/** Grass all over and rows z 12 and 13 of {@code a} and {@code b} right across; a road's route from z 5 to z 22. */
	private static List<BlockPos> road(GameTestHelper helper, ServerLevel level, Block a, Block b) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
			helper.setBlock(new BlockPos(x, 1, 12), a);
			helper.setBlock(new BlockPos(x, 1, 13), b);
		}
		Paths.Search search = new Paths.Search(level, helper.absolutePos(new BlockPos(15, 2, 5)), helper.absolutePos(new BlockPos(15, 2, 22)),
			new Paths.Options(1, List.of(), 24, Roads::clear));
		search.step(20000);
		return search.result();
	}

	// --- B988: the builder's village, not a ball round the hall -------------------------------------------------------

	/**
	 * "Once it has a household of Burghers or a higher class, every builder there sells the Manor": a builder up a hill
	 * standing exactly on the village's circle (6 and 8 across from the hall, 10 blocks, the radius) and 7 up, with a
	 * Burgher household by the hall, sells the Manor and the Townhouse.
	 */
	//$ gametest_batch BIG '"qaFixes1107bOnTheCircle"'
	@GameTest(template = BIG, batch = "qaFixes1107bOnTheCircle")
	public void aBuilderUpAHillOnTheCircleSellsTheManorWithBurghers(GameTestHelper helper) {
		BlockPos hall = QaGranderHomes1107GameTests.village(helper, new BlockPos(4, 2, 4), 10);
		QaGranderHomes1107GameTests.classed(helper, new BlockPos(6, 2, 6), "Bertram", "burgher");
		helper.setBlock(new BlockPos(10, 8, 12), Blocks.STONE);
		Villager hill = QaGranderHomes1107GameTests.builder(helper, new BlockPos(10, 9, 12), "Hilda", 2);
		BlockPos up = helper.absolutePos(new BlockPos(10, 9, 12));
		helper.assertTrue(VillageHalls.containing(helper.getLevel(), up).equals(Optional.of(hall)), "setup: the spot on the circle isn't in the village: " + up);
		open(helper, hill);
		helper.assertTrue(QaGranderHomes1107GameTests.sold(hill, StarterBlueprints.MANOR) == 1, "the builder on the village's circle up a hill doesn't sell the Manor though a "
			+ "Burgher household lives by the hall: " + QaGranderHomes1107GameTests.blueprintsSold(hill));
		helper.assertTrue(QaGranderHomes1107GameTests.sold(hill, StarterBlueprints.TOWNHOUSE) == 1, "the builder on the village's circle up a hill doesn't sell the "
			+ "Townhouse (Burghers are higher than Artisans): " + QaGranderHomes1107GameTests.blueprintsSold(hill));
		helper.succeed();
	}

	/**
	 * The hall up a hill and the builder below it on the rim (8 across, 7 down): down counts as much as up, so he sells
	 * the Townhouse for the Artisan household by the hall.
	 */
	//$ gametest_batch BIG '"qaFixes1107bDownAHill"'
	@GameTest(template = BIG, batch = "qaFixes1107bDownAHill")
	public void aBuilderDownAHillFromTheHallSellsTheTownhouse(GameTestHelper helper) {
		for (int x = 2; x <= 8; x++) {
			for (int z = 2; z <= 8; z++) {
				helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
			}
		}
		BlockPos hall = QaGranderHomes1107GameTests.village(helper, new BlockPos(4, 9, 4), 10);
		QaGranderHomes1107GameTests.classed(helper, new BlockPos(6, 9, 6), "Ann", "artisan");
		Villager low = QaGranderHomes1107GameTests.builder(helper, new BlockPos(12, 2, 4), "Dale", 2);
		BlockPos at = helper.absolutePos(new BlockPos(12, 2, 4));
		helper.assertTrue(VillageHalls.containing(helper.getLevel(), at).equals(Optional.of(hall)), "setup: the spot down the hill isn't in the village: " + at);
		open(helper, low);
		helper.assertTrue(QaGranderHomes1107GameTests.sold(low, StarterBlueprints.TOWNHOUSE) == 1, "the builder down the hill on the village's rim (8 across, 7 down) "
			+ "doesn't sell the Townhouse: " + QaGranderHomes1107GameTests.blueprintsSold(low));
		helper.assertTrue(QaGranderHomes1107GameTests.sold(low, StarterBlueprints.MANOR) == 0, "the builder sells the Manor with only Artisans: "
			+ QaGranderHomes1107GameTests.blueprintsSold(low));
		helper.succeed();
	}

	/**
	 * "The offers are added when the builder levels up": a Novice builder on the rim up a hill (8 across, 7 up), with an
	 * Artisan household by the hall, levels up to Apprentice and has the Townhouse at once, no trade screen opened.
	 */
	//$ gametest_batch BIG '"qaFixes1107bRimLevelUp"'
	@GameTest(template = BIG, batch = "qaFixes1107bRimLevelUp")
	public void aBuilderOnTheRimUpAHillGetsTheTownhouseWhenHeLevelsUp(GameTestHelper helper) {
		QaGranderHomes1107GameTests.village(helper, new BlockPos(4, 2, 4), 10);
		QaGranderHomes1107GameTests.classed(helper, new BlockPos(6, 2, 6), "Ann", "artisan");
		helper.setBlock(new BlockPos(12, 8, 6), Blocks.STONE);
		Villager hill = QaGranderHomes1107GameTests.builder(helper, new BlockPos(12, 9, 6), "Hilda", 1);
		((VillagerAccessor) hill).aliveworkplace$increaseMerchantCareer();
		helper.assertTrue(hill.getVillagerData().getLevel() == 2, "setup: not an Apprentice after the level-up: " + hill.getVillagerData().getLevel());
		helper.assertTrue(QaGranderHomes1107GameTests.sold(hill, StarterBlueprints.TOWNHOUSE) == 1, "the builder on the rim up a hill levelled up to Apprentice "
			+ "without the Townhouse: " + QaGranderHomes1107GameTests.blueprintsSold(hill));
		helper.succeed();
	}

	/**
	 * The gate still asks for the class: on the rim up a hill (8 across, 7 up) with only a Peasant household by the hall,
	 * the builder sells no Townhouse and no Manor.
	 */
	//$ gametest_batch BIG '"qaFixes1107bRimPeasants"'
	@GameTest(template = BIG, batch = "qaFixes1107bRimPeasants")
	public void aBuilderOnTheRimWithOnlyPeasantsSellsNeither(GameTestHelper helper) {
		QaGranderHomes1107GameTests.village(helper, new BlockPos(4, 2, 4), 10);
		QaGranderHomes1107GameTests.classed(helper, new BlockPos(6, 2, 6), "Piers", "peasant");
		helper.setBlock(new BlockPos(12, 8, 6), Blocks.STONE);
		Villager hill = QaGranderHomes1107GameTests.builder(helper, new BlockPos(12, 9, 6), "Hilda", 2);
		open(helper, hill);
		helper.assertTrue(QaGranderHomes1107GameTests.sold(hill, StarterBlueprints.TOWNHOUSE) == 0 && QaGranderHomes1107GameTests.sold(hill, StarterBlueprints.MANOR) == 0,
			"the builder on the rim sells a grander home with only Peasants in the village: " + QaGranderHomes1107GameTests.blueprintsSold(hill));
		helper.assertTrue(QaGranderHomes1107GameTests.sold(hill, StarterBlueprints.ARTISANS_HOUSE) == 1, "the Apprentice on the rim doesn't sell the Artisan's House: "
			+ QaGranderHomes1107GameTests.blueprintsSold(hill));
		helper.succeed();
	}

	private static void open(GameTestHelper helper, Villager villager) {
		ServerPlayer player = QaGranderHomes1107GameTests.player(helper);
		try {
			QaGranderHomes1107GameTests.openTrades(helper, villager, player);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}
}
