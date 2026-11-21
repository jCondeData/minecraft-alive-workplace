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
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Artisan's House's biome looks (ROADMAP 49.10g, as the Farmstead's in 49.10e): a desert, savanna, snowy or taiga
 * village builds the potter's house in its culture's look at every level, plains as drawn; where the village's own look
 * doesn't fit, the others are tried after it. Every look of a level is that level to everything else (its base, its
 * upgrade, its lock on the Blueprint Table, its cellar, its place on the Village Map), with the same size, beds, job blocks
 * and furniture spots; Level III is its Level II over the same clay cellar; a builder builds Level I to DONE and digs
 * Level III's cellar under a finished Level II.
 */
public class ArtisansHouseLooksGameTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final List<StarterBlueprints.Entry> LEVELS = List.of(StarterBlueprints.ARTISANS_HOUSE, StarterBlueprints.ARTISANS_HOUSE_2,
		StarterBlueprints.ARTISANS_HOUSE_3);
	/**
	 * The cultures with a look, as {@code StarterBlueprints.ARTISANS_HOUSE_CULTURES}: to hold a look back, take it out of both
	 * lists (and delete its build test below).
	 */
	private static final List<String> LOOKS = List.of("desert", "savanna", "snowy", "taiga");
	/** Where BuilderGameTests builds the Artisan's House in the huge area (the barrels stand in the row at z 4). */
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 8);

	private static List<StarterBlueprints.Entry> look(String culture) {
		return StarterBlueprints.artisansHouseLook(culture);
	}

	/**
	 * Each culture's village picks its own look of every level, wherever its hall stands; a plains village (and one of a
	 * culture with no look) the Artisan's House as drawn, even when it is handed a look.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachCulturesVillagePicksItsOwnLookOfEveryLevel(GameTestHelper helper) {
		helper.assertTrue(new HashSet<>(LOOKS).equals(new HashSet<>(StarterBlueprints.ARTISANS_HOUSE_CULTURES)),
			"the test's looks " + LOOKS + " differ from StarterBlueprints' " + StarterBlueprints.ARTISANS_HOUSE_CULTURES);
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
					if (!LOOKS.contains(culture)) { // a look held back: that culture builds the house as drawn
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
	 * Where the village's own look doesn't fit, the steward tries every other look of that level (as drawn among them),
	 * each once, and every look is in the library.
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
			helper.assertTrue(tried.contains(drawn), "the Artisan's House as drawn should be a fallback: " + tried);
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
	 * Level III's cellar (and none under I and II), and a home on the Village Map.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookCountsAsItsLevelOfTheArtisansHouse(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		for (String culture : LOOKS) {
			List<StarterBlueprints.Entry> looks = look(culture);
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
				} else {
					helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), BlueprintUpgrades.upgradeOf(id)).isEmpty(),
						id + " has an upgrade in the library (" + BlueprintUpgrades.upgradeOf(id) + "): the Artisan's House stops at Level III");
				}
				helper.assertTrue(BlueprintUnlocks.managed(id) == BlueprintUnlocks.managed(drawn), id + " should be locked and unlocked as " + drawn + " is");
				helper.assertTrue(BlueprintUnlocks.state(helper.getLevel(), hall, id) == BlueprintUnlocks.state(helper.getLevel(), hall, drawn),
					id + " shows " + BlueprintUnlocks.state(helper.getLevel(), hall, id) + " on the Table, " + drawn + " "
						+ BlueprintUnlocks.state(helper.getLevel(), hall, drawn));
				helper.assertTrue(StarterBlueprints.basement(id) == LEVELS.get(level).basement(), id + "'s cellar should be " + LEVELS.get(level).basement() + " deep");
				helper.assertTrue(StarterBlueprints.WITH_BASEMENTS.stream().anyMatch(e -> e.id().equals(id)) == (LEVELS.get(level).basement() > 0),
					id + " should be listed with the basement builds exactly when " + drawn + " is");
				helper.assertTrue(VillageMaps.kindOf(id).equals(VillageMaps.kindOf(drawn)) && VillageMaps.kindOf(id).orElse(null) == VillageMaps.Kind.HOMES,
					id + " is " + VillageMaps.kindOf(id) + " on the Village Map, not a home as " + drawn);
				helper.assertTrue(StarterBlueprints.ARTISANS_HOUSE_LOOKS.contains(looks.get(level)), id + " isn't among ARTISANS_HOUSE_LOOKS");
			}
		}
		helper.assertTrue(StarterBlueprints.ARTISANS_HOUSE_LOOKS.size() == LOOKS.size() * LEVELS.size(),
			"ARTISANS_HOUSE_LOOKS holds " + StarterBlueprints.ARTISANS_HOUSE_LOOKS.size() + " looks, not " + LOOKS.size() * LEVELS.size());
		helper.succeed();
	}

	/**
	 * Each look of a level is the size of that level, as its entry says, with its cellar, its beds, its job blocks (by POI
	 * type and count: the crafting table, the cauldrons and the barrels) and the drawing's furniture spots, each piece in the
	 * same cell facing the same way, no more and no fewer.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookHasItsLevelsSizeBedsJobBlocksAndFurniture(GameTestHelper helper) {
		for (int level = 0; level < LEVELS.size(); level++) {
			StarterBlueprints.Entry drawnEntry = LEVELS.get(level);
			Blueprint drawn = WeaversCottageLooksGameTests.load(helper, drawnEntry.id());
			Map<String, Integer> drawnJobs = WeaversCottageLooksGameTests.jobBlocks(drawn);
			int drawnBeds = WeaversCottageLooksGameTests.beds(drawn);
			helper.assertTrue(drawnBeds >= 2, drawnEntry.id() + " has " + drawnBeds + " beds");
			helper.assertTrue(!drawnJobs.isEmpty(), drawnEntry.id() + " has no job blocks");
			Set<String> drawnSpots = spots(drawnEntry.id());
			helper.assertTrue(!drawnSpots.isEmpty(), drawnEntry.id() + " has no furniture spots");
			for (String culture : LOOKS) {
				StarterBlueprints.Entry entry = look(culture).get(level);
				Blueprint look = WeaversCottageLooksGameTests.load(helper, entry.id());
				Vec3i size = look.size();
				helper.assertTrue(size.equals(entry.size()), entry.id() + " is " + size.toShortString() + ", its entry says " + entry.size().toShortString());
				helper.assertTrue(size.equals(drawn.size()), entry.id() + " is " + size.toShortString() + ", " + drawnEntry.id() + " " + drawn.size().toShortString());
				helper.assertTrue(look.basement() == drawn.basement(), entry.id() + " has " + look.basement() + " under the ground, " + drawnEntry.id() + " " + drawn.basement());
				int beds = WeaversCottageLooksGameTests.beds(look);
				helper.assertTrue(beds == drawnBeds, entry.id() + " has " + beds + " beds, " + drawnEntry.id() + " " + drawnBeds);
				Map<String, Integer> jobs = WeaversCottageLooksGameTests.jobBlocks(look);
				helper.assertTrue(jobs.equals(drawnJobs), entry.id() + " has job blocks " + jobs + ", " + drawnEntry.id() + " " + drawnJobs);
				Set<String> lookSpots = spots(entry.id());
				List<String> missing = drawnSpots.stream().filter(s -> !lookSpots.contains(s)).sorted().toList();
				List<String> extra = lookSpots.stream().filter(s -> !drawnSpots.contains(s)).sorted().toList();
				helper.assertTrue(missing.isEmpty() && extra.isEmpty(), entry.id() + "'s furniture spots differ from " + drawnEntry.id() + "'s: missing "
					+ missing + ", extra " + extra);
			}
		}
		helper.succeed();
	}

	/**
	 * Every look's Level III is its own Level II above the ground, block for block but the hatch over the cellar, over a
	 * clay cellar 4 deep with a ladder down under that hatch and storage in it: a house finished at Level II keeps its
	 * workroom, kiln and chests when the cellar is dug.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLooksLevelThreeIsItsLevelTwoOverTheCellar(GameTestHelper helper) {
		for (String culture : LOOKS) {
			StarterBlueprints.Entry twoEntry = look(culture).get(1);
			StarterBlueprints.Entry threeEntry = look(culture).get(2);
			Blueprint two = WeaversCottageLooksGameTests.load(helper, twoEntry.id());
			Blueprint three = WeaversCottageLooksGameTests.load(helper, threeEntry.id());
			helper.assertTrue(three.basement() == 4, threeEntry.id() + " reaches " + three.basement() + " down, not 4");
			helper.assertTrue(three.size().equals(two.size()), threeEntry.id() + " is " + three.size() + ", its Level II " + two.size());
			Map<BlockPos, BlockState> above = new HashMap<>();
			Set<Long> cellarColumns = new HashSet<>();
			for (Blueprint.Entry e : three.blocks()) {
				if (e.pos().getY() >= 0) {
					above.put(e.pos(), e.state());
				} else {
					cellarColumns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()));
				}
			}
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
			helper.assertTrue(changed.isEmpty(), threeEntry.id() + " changes " + changed.size() + " block(s) of " + twoEntry.id() + ", e.g. "
				+ changed.stream().limit(3).toList());
			List<BlockPos> hatches = three.blocks().stream().filter(e -> e.pos().getY() == 0 && e.state().is(BlockTags.TRAPDOORS)
				&& cellarColumns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))).map(Blueprint.Entry::pos).toList();
			helper.assertTrue(hatches.size() == 1, threeEntry.id() + " has " + hatches.size() + " hatches down into its cellar, not one");
			BlockPos under = hatches.get(0).below();
			helper.assertTrue(three.blocks().stream().anyMatch(e -> e.pos().equals(under) && e.state().is(Blocks.LADDER)),
				threeEntry.id() + " has no ladder under its hatch at " + hatches.get(0).toShortString());
			helper.assertTrue(three.blocks().stream().anyMatch(e -> e.pos().getY() < 0 && (e.state().is(Blocks.CHEST) || e.state().is(Blocks.BARREL))),
				threeEntry.id() + "'s cellar holds no chest or barrel");
		}
		helper.succeed();
	}

	/** A builder builds the desert look of Level I to DONE from barrels stocked with what its plan needs. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"artisans_house_looks_desert"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "artisans_house_looks_desert")
	public void buildsTheDesertArtisansHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("desert").get(0), ORIGIN);
	}

	/** And the savanna look. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"artisans_house_looks_savanna"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "artisans_house_looks_savanna")
	public void buildsTheSavannaArtisansHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("savanna").get(0), ORIGIN);
	}

	/** And the snowy look. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"artisans_house_looks_snowy"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "artisans_house_looks_snowy")
	public void buildsTheSnowyArtisansHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("snowy").get(0), ORIGIN);
	}

	/** And the taiga look. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"artisans_house_looks_taiga"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "artisans_house_looks_taiga")
	public void buildsTheTaigaArtisansHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("taiga").get(0), ORIGIN);
	}

	/**
	 * The taiga Artisan's House III (the look with the most blocks) given to a builder over a finished taiga Level II on real
	 * ground: he digs the clay cellar under the hatch, walls and fits it out, and keeps the Level II (its chests where they were).
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"artisans_house_looks_cellar"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "artisans_house_looks_cellar")
	public void theTaigaArtisansHouseThreeDigsItsCellarUnderLevelTwo(GameTestHelper helper) {
		CellarGameTests.levelThreeOverLevelTwo(helper, look("taiga").get(1), look("taiga").get(2), 7, 7);
	}

	/**
	 * The desert look saved and loaded back mid-build (a restart): the site comes back as that look, not the Artisan's House
	 * as drawn, and the builder finishes it with every block of the look in place.
	 */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"artisans_house_looks_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "artisans_house_looks_reload")
	public void aDesertArtisansHouseSavedMidBuildReloadsAsTheDesertLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		WeaversCottageLooksGameTests.reloadMidBuild(helper, look("desert").get(0).id(), ORIGIN, "the desert Artisan's House");
	}

	/** {@code id}'s furniture spots, each as piece, cell and facing. */
	private static Set<String> spots(ResourceLocation id) {
		Set<String> out = new HashSet<>();
		BlueprintFurniture.of(id).forEach(s -> out.add(s.kind() + "@" + s.pos().toShortString() + ">" + s.facing()));
		return out;
	}
}
