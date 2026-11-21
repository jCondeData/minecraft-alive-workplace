package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Farmstead's biome looks (ROADMAP 49.10e, as the Weaver's Cottage's in 49.10d): a desert, savanna, snowy or taiga
 * village builds the Farmer's farmstead in its culture's look at every level, plains as drawn; where the village's own
 * look doesn't fit, the others are tried after it. Every look of a level is that level to everything else (its base, its
 * upgrade, its lock on the Blueprint Table, its place on the Village Map), as wide and deep as drawn (the snowy look's
 * roof stands taller), with the same beds, chests, composters, furniture spots and a watered field of crops; Level III
 * digs the same root cellar under its own Level II; and a builder builds it to DONE.
 */
public class FarmsteadLooksGameTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final List<StarterBlueprints.Entry> LEVELS = List.of(StarterBlueprints.FARMSTEAD, StarterBlueprints.FARMSTEAD_2,
		StarterBlueprints.FARMSTEAD_3);
	/**
	 * The cultures with a look, as {@code StarterBlueprints.FARMSTEAD_CULTURES}: to hold a look back, take it out of both
	 * lists (and delete its build test below).
	 */
	private static final List<String> LOOKS = List.of("desert", "savanna", "snowy", "taiga");
	/** Where the farmstead goes in the huge area: 15 wide and up to 22 deep, behind the barrels' row at z 4. */
	private static final BlockPos ORIGIN = new BlockPos(1, 2, 6);

	private static List<StarterBlueprints.Entry> look(String culture) {
		return StarterBlueprints.farmsteadLook(culture);
	}

	/**
	 * Each culture's village picks its own look of every level, wherever its hall stands; a plains village (and one of a
	 * culture with no look) the farmstead as drawn, even when it is handed a look.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachCulturesVillagePicksItsOwnLookOfEveryLevel(GameTestHelper helper) {
		helper.assertTrue(new HashSet<>(LOOKS).equals(new HashSet<>(StarterBlueprints.FARMSTEAD_CULTURES)),
			"the test's looks " + LOOKS + " differ from StarterBlueprints' " + StarterBlueprints.FARMSTEAD_CULTURES);
		List<BlockPos> halls = List.of(BlockPos.ZERO, new BlockPos(1000, 64, -2000), new BlockPos(-37, 70, 512), new BlockPos(5, 80, 5));
		for (int level = 0; level < LEVELS.size(); level++) {
			ResourceLocation drawn = LEVELS.get(level).id();
			helper.assertTrue(BlueprintLooks.byCulture(drawn), drawn + " should be built by culture");
			for (BlockPos hall : halls) {
				for (String culture : LOOKS) {
					ResourceLocation want = look(culture).get(level).id();
					ResourceLocation got = BlueprintLooks.pick(drawn, culture, hall);
					helper.assertTrue(got.equals(want), "a " + culture + " village at " + hall + " picked " + got + " for " + drawn + ", not " + want);
					helper.assertTrue(BlueprintLooks.pick(want, culture, hall).equals(want), "a look of " + drawn + " asked again changed: " + want);
					helper.assertTrue(BlueprintLooks.pick(want, "plains", hall).equals(drawn),
						"a plains village handed " + want + " should build " + drawn + " as drawn");
				}
				for (String culture : BlueprintLooks.CULTURES) {
					if (!LOOKS.contains(culture)) {
						helper.assertTrue(BlueprintLooks.pick(drawn, culture, hall).equals(drawn), "a " + culture + " village (no look) should build " + drawn + " as drawn");
					}
				}
				helper.assertTrue(BlueprintLooks.pick(drawn, "plains", hall).equals(drawn), "a plains village should build " + drawn + " as drawn");
				helper.assertTrue(BlueprintLooks.pick(drawn, "jungle", hall).equals(drawn), "a culture with no look should build " + drawn + " as drawn");
			}
		}
		helper.succeed();
	}

	/**
	 * Where the village's own look doesn't fit, the steward tries every other look of that level (as drawn first), each
	 * once, and every look is in the library.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLookThatDoesntFitFallsBackToTheOthers(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		String culture = VillageWaystones.culture(helper.getLevel(), hall);
		for (int level = 0; level < LEVELS.size(); level++) {
			ResourceLocation drawn = LEVELS.get(level).id();
			List<ResourceLocation> tried = BlueprintLooks.forVillage(helper.getLevel(), drawn, hall);
			helper.assertTrue(tried.get(0).equals(BlueprintLooks.pick(drawn, culture, hall)),
				"a " + culture + " village should try " + BlueprintLooks.pick(drawn, culture, hall) + " first, not " + tried.get(0));
			helper.assertTrue(tried.size() == LOOKS.size() + 1 && new HashSet<>(tried).size() == LOOKS.size() + 1, "every look of " + drawn + " once: " + tried);
			helper.assertTrue(tried.contains(drawn), "the Farmstead as drawn should be a fallback: " + tried);
			for (String c : LOOKS) {
				ResourceLocation id = look(c).get(level).id();
				helper.assertTrue(tried.contains(id), "missing " + id + " from " + tried);
				helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), id).isPresent(), id + " isn't in the library");
			}
		}
		helper.succeed();
	}

	/**
	 * Every look is its level to the rest of the mod: its base, its upgrade, the next level's look, its lock on the Table,
	 * its basement (Level III's cellar, listed with the basement builds), and a farm on the Village Map.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookCountsAsItsLevelOfTheFarmstead(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		for (String culture : LOOKS) {
			List<StarterBlueprints.Entry> looks = look(culture);
			helper.assertTrue(looks.size() == LEVELS.size(), culture + " has " + looks.size() + " levels, not " + LEVELS.size());
			for (int level = 0; level < LEVELS.size(); level++) {
				ResourceLocation id = looks.get(level).id();
				ResourceLocation drawn = LEVELS.get(level).id();
				helper.assertTrue(id.equals(BlueprintLooks.look(drawn, culture)), id + " isn't named as a look of " + drawn);
				helper.assertTrue(BlueprintLooks.base(id).equals(drawn), "the base of " + id + " is " + BlueprintLooks.base(id) + ", not " + drawn);
				helper.assertTrue(BlueprintLooks.lookOf(id).orElse("").equals(culture), id + " should be the " + culture + " look");
				helper.assertTrue(BlueprintLooks.of(id).equals(BlueprintLooks.of(drawn)), "the looks of " + id + " differ from " + drawn + "'s");
				helper.assertTrue(BlueprintUpgrades.tier(id) == level + 1, id + " should be level " + (level + 1));
				if (level + 1 < LEVELS.size()) {
					helper.assertTrue(BlueprintUpgrades.upgradeOf(id).equals(looks.get(level + 1).id()),
						id + " should upgrade to " + looks.get(level + 1).id() + ", not " + BlueprintUpgrades.upgradeOf(id));
				}
				helper.assertTrue(BlueprintUnlocks.managed(id), id + " should be locked and unlocked with " + drawn);
				helper.assertTrue(BlueprintUnlocks.state(helper.getLevel(), hall, id) == BlueprintUnlocks.state(helper.getLevel(), hall, drawn),
					id + " shows " + BlueprintUnlocks.state(helper.getLevel(), hall, id) + " on the Table, " + drawn + " "
						+ BlueprintUnlocks.state(helper.getLevel(), hall, drawn));
				int basement = LEVELS.get(level).basement();
				helper.assertTrue(StarterBlueprints.basement(id) == basement && looks.get(level).basement() == basement,
					id + "'s basement should be " + basement + ", not " + StarterBlueprints.basement(id));
				helper.assertTrue(StarterBlueprints.WITH_BASEMENTS.contains(looks.get(level)) == (basement > 0),
					id + (basement > 0 ? " isn't" : " is") + " listed with the basement builds");
				helper.assertTrue(VillageMaps.kindOf(id).equals(VillageMaps.kindOf(drawn)) && VillageMaps.kindOf(id).orElse(null) == VillageMaps.Kind.FARMS,
					id + " is " + VillageMaps.kindOf(id) + " on the Village Map, not a farm as " + drawn);
				helper.assertTrue(StarterBlueprints.FARMSTEAD_LOOKS.contains(looks.get(level)), id + " isn't among FARMSTEAD_LOOKS");
			}
		}
		helper.assertTrue(StarterBlueprints.FARMSTEAD_LOOKS.size() == LOOKS.size() * LEVELS.size(),
			"FARMSTEAD_LOOKS holds " + StarterBlueprints.FARMSTEAD_LOOKS.size() + " looks, not " + LOOKS.size() * LEVELS.size());
		helper.succeed();
	}

	/**
	 * Each look of a level is as wide and deep as that level and as tall as its entry says, with the same basement, beds,
	 * chests and job blocks (the composters), and every furniture spot of the drawing, the same piece in the same cell
	 * facing the same way.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookHasItsLevelsFootprintBedsChestsJobBlocksAndFurniture(GameTestHelper helper) {
		int[] composters = {1, 2, 2};
		for (int level = 0; level < LEVELS.size(); level++) {
			StarterBlueprints.Entry drawnEntry = LEVELS.get(level);
			Blueprint drawn = WeaversCottageLooksGameTests.load(helper, drawnEntry.id());
			Map<String, Integer> drawnJobs = jobBlocks(drawn);
			helper.assertTrue(drawnJobs.getOrDefault("minecraft:farmer", 0) == composters[level],
				drawnEntry.id() + "'s job blocks are " + drawnJobs + ", not " + composters[level] + " composter(s)");
			int drawnBeds = WeaversCottageLooksGameTests.beds(drawn);
			long drawnChests = count(drawn, s -> s.is(Blocks.CHEST));
			List<String> drawnSpots = spots(drawnEntry.id());
			helper.assertTrue(!drawnSpots.isEmpty(), drawnEntry.id() + " has no furniture spots");
			for (String culture : LOOKS) {
				StarterBlueprints.Entry entry = look(culture).get(level);
				Blueprint look = WeaversCottageLooksGameTests.load(helper, entry.id());
				Vec3i size = look.size();
				helper.assertTrue(size.equals(entry.size()), entry.id() + " is " + size.toShortString() + ", its entry says " + entry.size().toShortString());
				helper.assertTrue(size.getX() == drawn.size().getX() && size.getZ() == drawn.size().getZ(),
					entry.id() + " is " + size.getX() + " x " + size.getZ() + ", " + drawnEntry.id() + " " + drawn.size().getX() + " x " + drawn.size().getZ());
				helper.assertTrue(look.basement() == drawn.basement(), entry.id() + " has " + look.basement() + " under the ground, " + drawnEntry.id() + " " + drawn.basement());
				helper.assertTrue(WeaversCottageLooksGameTests.beds(look) == drawnBeds,
					entry.id() + " has " + WeaversCottageLooksGameTests.beds(look) + " beds, " + drawnEntry.id() + " " + drawnBeds);
				helper.assertTrue(count(look, s -> s.is(Blocks.CHEST)) == drawnChests,
					entry.id() + " has " + count(look, s -> s.is(Blocks.CHEST)) + " chests, " + drawnEntry.id() + " " + drawnChests);
				Map<String, Integer> jobs = jobBlocks(look);
				helper.assertTrue(jobs.equals(drawnJobs), entry.id() + " has job blocks " + jobs + ", " + drawnEntry.id() + " " + drawnJobs);
				List<String> have = spots(entry.id());
				List<String> missing = drawnSpots.stream().filter(s -> !have.contains(s)).toList();
				helper.assertTrue(missing.isEmpty(), entry.id() + " is missing the drawing's furniture spots " + missing);
			}
		}
		helper.succeed();
	}

	/**
	 * The field of every look at every level: at least as much farmland as the level drawn, all of it on the ground floor
	 * (y 0) with a crop on it and water within four blocks (wet farmland, as the Farmer needs it), and no crop off farmland.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookHasAWateredFieldOfCrops(GameTestHelper helper) {
		for (int level = 0; level < LEVELS.size(); level++) {
			StarterBlueprints.Entry drawnEntry = LEVELS.get(level);
			long drawnFarmland = count(WeaversCottageLooksGameTests.load(helper, drawnEntry.id()), s -> s.is(Blocks.FARMLAND));
			helper.assertTrue(drawnFarmland >= 36, drawnEntry.id() + " has only " + drawnFarmland + " farmland");
			for (String culture : LOOKS) {
				StarterBlueprints.Entry entry = look(culture).get(level);
				Map<BlockPos, BlockState> blocks = blocks(WeaversCottageLooksGameTests.load(helper, entry.id()));
				List<BlockPos> farmland = blocks.entrySet().stream().filter(e -> e.getValue().is(Blocks.FARMLAND)).map(Map.Entry::getKey).toList();
				List<BlockPos> water = blocks.entrySet().stream().filter(e -> e.getValue().is(Blocks.WATER)).map(Map.Entry::getKey).toList();
				helper.assertTrue(farmland.size() >= drawnFarmland, entry.id() + " has " + farmland.size() + " farmland, " + drawnEntry.id() + " " + drawnFarmland);
				List<String> wrong = new ArrayList<>();
				for (BlockPos p : farmland) {
					if (p.getY() != 0) {
						wrong.add(p.toShortString() + " farmland off the ground floor");
					}
					BlockState above = blocks.getOrDefault(p.above(), Blocks.AIR.defaultBlockState());
					if (!(above.getBlock() instanceof CropBlock)) {
						wrong.add(p.toShortString() + " farmland under " + above);
					}
					if (water.stream().noneMatch(w -> Math.abs(w.getX() - p.getX()) <= 4 && Math.abs(w.getZ() - p.getZ()) <= 4
						&& w.getY() - p.getY() >= 0 && w.getY() - p.getY() <= 1)) {
						wrong.add(p.toShortString() + " farmland with no water near");
					}
				}
				blocks.forEach((p, s) -> {
					if (s.getBlock() instanceof CropBlock && !blocks.getOrDefault(p.below(), Blocks.AIR.defaultBlockState()).is(Blocks.FARMLAND)) {
						wrong.add(p.toShortString() + " " + s + " not on farmland");
					}
				});
				helper.assertTrue(wrong.isEmpty(), entry.id() + "'s field: " + wrong.size() + " problem(s), e.g. " + wrong.stream().limit(4).toList());
			}
		}
		helper.succeed();
	}

	/**
	 * Level III of every look is its own Level II with the root cellar dug under it: every block of the look's Level II
	 * kept above the ground but the hatch over the ladder, a hatch down, and the cellar's ladder, chests and lanterns in
	 * the cells of the Farmstead III as drawn.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookOfLevelThreeDigsTheRootCellarUnderItsLevelTwo(GameTestHelper helper) {
		Blueprint drawnThree = WeaversCottageLooksGameTests.load(helper, StarterBlueprints.FARMSTEAD_3.id());
		Predicate<BlockState> ladder = s -> s.is(Blocks.LADDER);
		Predicate<BlockState> chest = s -> s.is(Blocks.CHEST) || s.is(Blocks.BARREL);
		Predicate<BlockState> lantern = s -> s.is(Blocks.LANTERN);
		helper.assertTrue(drawnThree.basement() == 4 && !below(drawnThree, ladder).isEmpty() && !below(drawnThree, lantern).isEmpty(),
			"the Farmstead III as drawn has no 4-deep cellar with a ladder and a lantern");
		for (String culture : LOOKS) {
			StarterBlueprints.Entry twoEntry = look(culture).get(1);
			StarterBlueprints.Entry threeEntry = look(culture).get(2);
			Blueprint two = WeaversCottageLooksGameTests.load(helper, twoEntry.id());
			Blueprint three = WeaversCottageLooksGameTests.load(helper, threeEntry.id());
			helper.assertTrue(three.basement() == 4, threeEntry.id() + " reaches " + three.basement() + " down, not 4");
			helper.assertTrue(three.size().equals(two.size()), threeEntry.id() + " is " + three.size().toShortString() + ", its Level II " + two.size().toShortString());
			Map<BlockPos, BlockState> above = new HashMap<>();
			three.blocks().stream().filter(e -> e.pos().getY() >= 0).forEach(e -> above.put(e.pos(), e.state()));
			Set<Long> cellarColumns = new HashSet<>();
			three.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> cellarColumns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
			List<String> changed = new ArrayList<>();
			for (Blueprint.Entry e : two.blocks()) {
				BlockState now = above.get(e.pos());
				if (now == null || !now.equals(e.state())) {
					boolean hatch = e.pos().getY() == 0 && now != null && now.is(BlockTags.TRAPDOORS)
						&& cellarColumns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()));
					if (!hatch) {
						changed.add(e.pos().toShortString() + " " + e.state() + " -> " + now);
					}
				}
			}
			helper.assertTrue(changed.isEmpty(), threeEntry.id() + " changes " + changed.size() + " block(s) of its Level II, e.g. " + changed.stream().limit(3).toList());
			helper.assertTrue(three.blocks().stream().anyMatch(e -> e.pos().getY() == 0 && e.state().is(BlockTags.TRAPDOORS)
				&& cellarColumns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))), threeEntry.id() + " has no hatch down into its cellar");
			helper.assertTrue(below(three, ladder).equals(below(drawnThree, ladder)), threeEntry.id() + "'s ladder is at " + below(three, ladder) + ", drawn " + below(drawnThree, ladder));
			helper.assertTrue(below(three, chest).equals(below(drawnThree, chest)), threeEntry.id() + "'s cellar stores are at " + below(three, chest) + ", drawn " + below(drawnThree, chest));
			helper.assertTrue(below(three, lantern).equals(below(drawnThree, lantern)), threeEntry.id() + "'s cellar lights are at " + below(three, lantern) + ", drawn " + below(drawnThree, lantern));
		}
		helper.succeed();
	}

	/** A builder builds the desert look of Level I to DONE from barrels stocked with what its plan needs. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"farmstead_looks_desert"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "farmstead_looks_desert")
	public void buildsTheDesertFarmstead(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("desert").get(0), ORIGIN);
	}

	/** And the savanna look. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"farmstead_looks_savanna"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "farmstead_looks_savanna")
	public void buildsTheSavannaFarmstead(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("savanna").get(0), ORIGIN);
	}

	/** And the snowy look, whose roof stands taller than the drawing's. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"farmstead_looks_snowy"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "farmstead_looks_snowy")
	public void buildsTheSnowyFarmstead(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("snowy").get(0), ORIGIN);
	}

	/** And the taiga look. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"farmstead_looks_taiga"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "farmstead_looks_taiga")
	public void buildsTheTaigaFarmstead(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("taiga").get(0), ORIGIN);
	}

	/**
	 * The snowy Farmstead III clicked onto a finished snowy Farmstead II on real ground: the builder digs the root cellar,
	 * keeps the Level II and its workers' chests where they were, and finishes with every block of the look in place.
	 */
	//$ gametest_ticks_batch HUGE_AREA '24000' '"farmstead_looks_cellar"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "farmstead_looks_cellar")
	public void theSnowyFarmsteadThreeDigsItsCellarUnderTheSnowyLevelTwo(GameTestHelper helper) {
		// 22 deep: from z 5, to stay in the 30-block area
		CellarGameTests.levelThreeOverLevelTwo(helper, look("snowy").get(1), look("snowy").get(2), 5);
	}

	/**
	 * The desert look saved and loaded back mid-build (a restart): the site comes back as that look, not the farmstead as
	 * drawn, and the builder finishes it with every block of the look in place.
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"farmstead_looks_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "farmstead_looks_reload")
	public void aDesertFarmsteadSavedMidBuildReloadsAsTheDesertLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		WeaversCottageLooksGameTests.reloadMidBuild(helper, look("desert").get(0).id(), ORIGIN, "the desert Farmstead");
	}

	/**
	 * The job blocks, by POI type: every POI but a bed's and a lightning rod's (a POI no villager works at; the drawn
	 * Level II's cupola carries one, a look's roof need not).
	 */
	private static Map<String, Integer> jobBlocks(Blueprint blueprint) {
		Map<String, Integer> out = new java.util.TreeMap<>(WeaversCottageLooksGameTests.jobBlocks(blueprint));
		out.remove("minecraft:lightning_rod");
		return out;
	}

	private static List<String> spots(ResourceLocation id) {
		return BlueprintFurniture.of(id).stream().map(s -> s.kind() + "@" + s.pos().toShortString() + ">" + s.facing()).toList();
	}

	private static long count(Blueprint blueprint, Predicate<BlockState> which) {
		return blueprint.blocks().stream().filter(e -> which.test(e.state())).count();
	}

	private static Map<BlockPos, BlockState> blocks(Blueprint blueprint) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		blueprint.blocks().forEach(e -> out.put(e.pos(), e.state()));
		return out;
	}

	/** The cells under the ground (the cellar) holding {@code which}. */
	private static Set<BlockPos> below(Blueprint blueprint, Predicate<BlockState> which) {
		Set<BlockPos> out = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() < 0 && which.test(e.state())).forEach(e -> out.add(e.pos()));
		return out;
	}
}
