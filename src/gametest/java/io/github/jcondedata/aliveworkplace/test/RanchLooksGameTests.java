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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

/**
 * The Ranch's biome looks (ROADMAP 49.10e, as the Weaver's Cottage's in 49.10d): a desert, savanna, snowy or taiga
 * village builds the Rancher's barn in its culture's look at both levels, plains as drawn; where the village's own look
 * doesn't fit, the others are tried after it. Every look of a level is that level to everything else (its base, its
 * upgrade, its lock on the Blueprint Table, its place on the Village Map), with the same footprint, bed, job blocks (the
 * smoker, and Level II's barrel) and furniture spots, and a builder builds Level I to DONE.
 */
public class RanchLooksGameTests {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final List<StarterBlueprints.Entry> LEVELS = List.of(StarterBlueprints.RANCH, StarterBlueprints.RANCH_2);
	/**
	 * The cultures with a look, as {@code StarterBlueprints.RANCH_CULTURES}: to hold a look back, take it out of both
	 * lists (and delete its build test below).
	 */
	private static final List<String> LOOKS = List.of("desert", "savanna", "snowy", "taiga");
	/** Where the barn goes in the big area (13 by 13, behind the barrels' row at z 4), as {@code BuilderGameTests.buildsRanch}. */
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);

	private static List<StarterBlueprints.Entry> look(String culture) {
		return StarterBlueprints.ranchLook(culture);
	}

	/**
	 * Each culture's village picks its own look of every level, wherever its hall stands; a plains village (and one of a
	 * culture with no look) the Ranch as drawn, even when it is handed a look.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachCulturesVillagePicksItsOwnLookOfEveryLevel(GameTestHelper helper) {
		helper.assertTrue(new HashSet<>(LOOKS).equals(new HashSet<>(StarterBlueprints.RANCH_CULTURES)),
			"the test's looks " + LOOKS + " differ from StarterBlueprints' " + StarterBlueprints.RANCH_CULTURES);
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
					if (!LOOKS.contains(culture)) { // a look held back: that culture builds the barn as drawn
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
			helper.assertTrue(tried.contains(drawn), "the Ranch as drawn should be a fallback: " + tried);
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
	 * no basement, and a farm on the Village Map.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookCountsAsItsLevelOfTheRanch(GameTestHelper helper) {
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
						id + " has an upgrade in the library (" + BlueprintUpgrades.upgradeOf(id) + "): the Ranch stops at Level II");
				}
				helper.assertTrue(BlueprintUnlocks.managed(id), id + " should be locked and unlocked with " + drawn);
				helper.assertTrue(BlueprintUnlocks.state(helper.getLevel(), hall, id) == BlueprintUnlocks.state(helper.getLevel(), hall, drawn),
					id + " shows " + BlueprintUnlocks.state(helper.getLevel(), hall, id) + " on the Table, " + drawn + " "
						+ BlueprintUnlocks.state(helper.getLevel(), hall, drawn));
				helper.assertTrue(StarterBlueprints.basement(id) == 0 && LEVELS.get(level).basement() == 0, id + " should have no basement");
				helper.assertTrue(StarterBlueprints.WITH_BASEMENTS.stream().noneMatch(e -> e.id().equals(id)), id + " is listed with the basement builds");
				helper.assertTrue(VillageMaps.kindOf(id).equals(VillageMaps.kindOf(drawn)) && VillageMaps.kindOf(id).orElse(null) == VillageMaps.Kind.FARMS,
					id + " is " + VillageMaps.kindOf(id) + " on the Village Map, not a farm as " + drawn);
				helper.assertTrue(StarterBlueprints.RANCH_LOOKS.contains(looks.get(level)), id + " isn't among RANCH_LOOKS");
			}
		}
		helper.assertTrue(StarterBlueprints.RANCH_LOOKS.size() == LOOKS.size() * LEVELS.size(),
			"RANCH_LOOKS holds " + StarterBlueprints.RANCH_LOOKS.size() + " looks, not " + LOOKS.size() * LEVELS.size());
		helper.succeed();
	}

	/**
	 * Each look of a level is as wide and deep as that level and sized as its entry says (the savanna look's thatch sits
	 * lower), with its bed, its job blocks (the smoker, and Level II's barrel) and every furniture spot of the drawing, the
	 * same piece in the same cell facing the same way.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookHasItsLevelsFootprintBedsJobBlocksAndFurniture(GameTestHelper helper) {
		for (int level = 0; level < LEVELS.size(); level++) {
			StarterBlueprints.Entry drawnEntry = LEVELS.get(level);
			Blueprint drawn = WeaversCottageLooksGameTests.load(helper, drawnEntry.id());
			Map<String, Integer> drawnJobs = jobSites(drawn);
			int drawnBeds = WeaversCottageLooksGameTests.beds(drawn);
			helper.assertTrue(!drawnJobs.isEmpty(), drawnEntry.id() + " has no job blocks");
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
				int beds = WeaversCottageLooksGameTests.beds(look);
				helper.assertTrue(beds == drawnBeds, entry.id() + " has " + beds + " beds, " + drawnEntry.id() + " " + drawnBeds);
				Map<String, Integer> jobs = jobSites(look);
				helper.assertTrue(jobs.equals(drawnJobs), entry.id() + " has job blocks " + jobs + ", " + drawnEntry.id() + " " + drawnJobs);
				List<String> lookSpots = spots(entry.id());
				List<String> missing = drawnSpots.stream().filter(s -> !lookSpots.contains(s)).toList();
				helper.assertTrue(missing.isEmpty(), entry.id() + " is missing the drawing's furniture spots " + missing);
			}
		}
		helper.succeed();
	}

	/** A builder builds the desert look of Level I to DONE from barrels stocked with what its plan needs. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"ranch_looks_desert"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "ranch_looks_desert")
	public void buildsTheDesertRanch(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("desert").get(0), ORIGIN);
	}

	/** And the savanna look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"ranch_looks_savanna"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "ranch_looks_savanna")
	public void buildsTheSavannaRanch(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("savanna").get(0), ORIGIN);
	}

	/** And the snowy look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"ranch_looks_snowy"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "ranch_looks_snowy")
	public void buildsTheSnowyRanch(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("snowy").get(0), ORIGIN);
	}

	/** And the taiga look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"ranch_looks_taiga"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "ranch_looks_taiga")
	public void buildsTheTaigaRanch(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("taiga").get(0), ORIGIN);
	}

	/**
	 * The desert look saved and loaded back mid-build (a restart): the site comes back as that look, not the Ranch as
	 * drawn, and the builder finishes it with every block of the look in place.
	 */
	//$ gametest_ticks_batch BIG_AREA '30000' '"ranch_looks_reload"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "ranch_looks_reload")
	public void aDesertRanchSavedMidBuildReloadsAsTheDesertLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		WeaversCottageLooksGameTests.reloadMidBuild(helper, look("desert").get(0).id(), ORIGIN, "the desert Ranch");
	}

	/** {@code id}'s furniture spots, each as piece, cell and facing. */
	private static List<String> spots(ResourceLocation id) {
		return BlueprintFurniture.of(id).stream().map(s -> s.kind() + "@" + s.pos().toShortString() + ">" + s.facing()).toList();
	}

	/**
	 * {@code blueprint}'s job sites, by POI type: the blocks a villager can take a job at (a composter, a smoker, a barrel),
	 * not every POI ({@code WeaversCottageLooksGameTests.jobBlocks} counts a lightning rod too, which only some looks carry).
	 */
	private static Map<String, Integer> jobSites(Blueprint blueprint) {
		Map<String, Integer> out = new java.util.TreeMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			PoiTypes.forState(e.state())
				.filter(p -> BuiltInRegistries.VILLAGER_PROFESSION.stream().anyMatch(job -> job.heldJobSite().test(p)))
				.ifPresent(p -> out.merge(p.unwrapKey().map(k -> k.location().toString()).orElse("?"), 1, Integer::sum));
		}
		return out;
	}
}
