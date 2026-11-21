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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * The Daub House's biome looks (ROADMAP 49.10d, as the Terrace's in 49.10c): a desert, savanna, snowy or taiga village
 * builds the Daub House in its culture's look, plains as drawn; where the village's own look doesn't fit, the others are
 * tried after it. Every look is the Daub House to everything else (its base, one tier with no upgrade, its lock on the
 * Blueprint Table, a home on the Village Map), with the same size, four beds, job blocks and furniture spots, and a
 * builder builds it to DONE.
 */
public class DaubHouseLooksGameTests {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final StarterBlueprints.Entry DRAWN = StarterBlueprints.DAUB_HOUSE;
	/**
	 * The cultures with a look, as {@code StarterBlueprints.DAUB_HOUSE_CULTURES}: to hold a look back, take it out of both
	 * lists (and delete its build test below).
	 */
	private static final List<String> LOOKS = List.of("desert", "savanna", "snowy", "taiga");
	/** Where the house goes in the big area: 13 by 13, behind the barrels' row at z 4. */
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	private static StarterBlueprints.Entry look(String culture) {
		return StarterBlueprints.daubHouseLook(culture).get(0);
	}

	/**
	 * Each culture's village picks its own look, wherever its hall stands; a plains village (and one of a culture with no
	 * look) the Daub House as drawn, even when it is handed a look.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachCulturesVillagePicksItsOwnLook(GameTestHelper helper) {
		helper.assertTrue(new HashSet<>(LOOKS).equals(new HashSet<>(StarterBlueprints.DAUB_HOUSE_CULTURES)),
			"the test's looks " + LOOKS + " differ from StarterBlueprints' " + StarterBlueprints.DAUB_HOUSE_CULTURES);
		ResourceLocation drawn = DRAWN.id();
		helper.assertTrue(BlueprintLooks.byCulture(drawn), drawn + " should be built by culture");
		List<BlockPos> halls = List.of(BlockPos.ZERO, new BlockPos(1000, 64, -2000), new BlockPos(-37, 70, 512), new BlockPos(5, 80, 5));
		for (BlockPos hall : halls) {
			for (String culture : LOOKS) {
				ResourceLocation want = look(culture).id();
				ResourceLocation got = BlueprintLooks.pick(drawn, culture, hall);
				helper.assertTrue(got.equals(want), "a " + culture + " village at " + hall + " picked " + got + ", not " + want);
				helper.assertTrue(BlueprintLooks.pick(want, culture, hall).equals(want), "a look of " + drawn + " asked again changed: " + want);
				helper.assertTrue(BlueprintLooks.pick(want, "plains", hall).equals(drawn), "a plains village handed " + want + " should build " + drawn + " as drawn");
			}
			for (String culture : BlueprintLooks.CULTURES) {
				if (!LOOKS.contains(culture)) { // a look held back: that culture builds the house as drawn
					helper.assertTrue(BlueprintLooks.pick(drawn, culture, hall).equals(drawn), "a " + culture + " village (no look) should build " + drawn + " as drawn");
				}
			}
			helper.assertTrue(BlueprintLooks.pick(drawn, "plains", hall).equals(drawn), "a plains village should build " + drawn + " as drawn");
			helper.assertTrue(BlueprintLooks.pick(drawn, "jungle", hall).equals(drawn), "a culture with no look should build " + drawn + " as drawn");
		}
		helper.succeed();
	}

	/**
	 * Where the village's own look doesn't fit, the steward tries every other look (as drawn first), each once, and every
	 * look is in the library.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLookThatDoesntFitFallsBackToTheOthers(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		String culture = VillageWaystones.culture(helper.getLevel(), hall);
		ResourceLocation drawn = DRAWN.id();
		List<ResourceLocation> tried = BlueprintLooks.forVillage(helper.getLevel(), drawn, hall);
		helper.assertTrue(tried.get(0).equals(BlueprintLooks.pick(drawn, culture, hall)),
			"a " + culture + " village should try " + BlueprintLooks.pick(drawn, culture, hall) + " first, not " + tried.get(0));
		helper.assertTrue(tried.size() == LOOKS.size() + 1 && new HashSet<>(tried).size() == LOOKS.size() + 1, "every look of " + drawn + " once: " + tried);
		helper.assertTrue(tried.contains(drawn), "the Daub House as drawn should be a fallback: " + tried);
		for (String c : LOOKS) {
			ResourceLocation id = look(c).id();
			helper.assertTrue(tried.contains(id), "missing " + id + " from " + tried);
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), id).isPresent(), id + " isn't in the library");
		}
		helper.succeed();
	}

	/**
	 * Every look is the Daub House to the rest of the mod: its base, one tier with no upgrade look, its lock on the Table,
	 * no basement, and a home on the Village Map.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookCountsAsTheDaubHouse(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		ResourceLocation drawn = DRAWN.id();
		for (String culture : LOOKS) {
			ResourceLocation id = look(culture).id();
			helper.assertTrue(id.equals(BlueprintLooks.look(drawn, culture)), id + " isn't named as a look of " + drawn);
			helper.assertTrue(BlueprintLooks.base(id).equals(drawn), "the base of " + id + " is " + BlueprintLooks.base(id) + ", not " + drawn);
			helper.assertTrue(BlueprintLooks.lookOf(id).orElse("").equals(culture), id + " should be the " + culture + " look");
			helper.assertTrue(BlueprintLooks.of(id).equals(BlueprintLooks.of(drawn)), "the looks of " + id + " differ from " + drawn + "'s");
			helper.assertTrue(BlueprintUpgrades.tier(id) == 1, id + " should be level 1");
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), BlueprintUpgrades.upgradeOf(id)).isEmpty(),
				id + " has an upgrade in the library (" + BlueprintUpgrades.upgradeOf(id) + "): the Daub House is one tier");
			helper.assertTrue(BlueprintUnlocks.managed(id), id + " should be locked and unlocked with " + drawn);
			helper.assertTrue(BlueprintUnlocks.state(helper.getLevel(), hall, id) == BlueprintUnlocks.state(helper.getLevel(), hall, drawn),
				id + " shows " + BlueprintUnlocks.state(helper.getLevel(), hall, id) + " on the Table, " + drawn + " "
					+ BlueprintUnlocks.state(helper.getLevel(), hall, drawn));
			helper.assertTrue(StarterBlueprints.basement(id) == 0 && StarterBlueprints.WITH_BASEMENTS.stream().noneMatch(e -> e.id().equals(id)),
				id + " should have no basement");
			helper.assertTrue(VillageMaps.kindOf(id).orElse(null) == VillageMaps.Kind.HOMES, id + " is " + VillageMaps.kindOf(id) + " on the Village Map, not a home");
			helper.assertTrue(StarterBlueprints.DAUB_HOUSE_LOOKS.contains(look(culture)), id + " isn't among DAUB_HOUSE_LOOKS");
		}
		helper.assertTrue(StarterBlueprints.DAUB_HOUSE_LOOKS.size() == LOOKS.size(),
			"DAUB_HOUSE_LOOKS holds " + StarterBlueprints.DAUB_HOUSE_LOOKS.size() + " looks, not " + LOOKS.size());
		helper.succeed();
	}

	/**
	 * Each look is the size of the house as drawn, as its entry says, with its four beds, its job blocks (the crafting
	 * table) and every furniture spot of the drawing, the same piece in the same cell facing the same way.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookHasTheSizeBedsJobBlocksAndFurniture(GameTestHelper helper) {
		Blueprint drawn = WeaversCottageLooksGameTests.load(helper, DRAWN.id());
		Map<String, Integer> drawnJobs = WeaversCottageLooksGameTests.jobBlocks(drawn);
		int drawnBeds = WeaversCottageLooksGameTests.beds(drawn);
		helper.assertTrue(drawnBeds == 4, DRAWN.id() + " has " + drawnBeds + " beds, not four");
		List<String> drawnSpots = BlueprintFurniture.of(DRAWN.id()).stream().map(s -> s.kind() + "@" + s.pos().toShortString() + ">" + s.facing()).toList();
		helper.assertTrue(!drawnSpots.isEmpty(), DRAWN.id() + " has no furniture spots");
		for (String culture : LOOKS) {
			StarterBlueprints.Entry entry = look(culture);
			Blueprint look = WeaversCottageLooksGameTests.load(helper, entry.id());
			Vec3i size = look.size();
			helper.assertTrue(size.equals(entry.size()), entry.id() + " is " + size.toShortString() + ", its entry says " + entry.size().toShortString());
			helper.assertTrue(size.equals(drawn.size()), entry.id() + " is " + size.toShortString() + ", " + DRAWN.id() + " " + drawn.size().toShortString());
			helper.assertTrue(look.basement() == drawn.basement(), entry.id() + " has " + look.basement() + " under the ground, " + DRAWN.id() + " " + drawn.basement());
			int beds = WeaversCottageLooksGameTests.beds(look);
			helper.assertTrue(beds == drawnBeds, entry.id() + " has " + beds + " beds, " + DRAWN.id() + " " + drawnBeds);
			Map<String, Integer> jobs = WeaversCottageLooksGameTests.jobBlocks(look);
			helper.assertTrue(jobs.equals(drawnJobs), entry.id() + " has job blocks " + jobs + ", " + DRAWN.id() + " " + drawnJobs);
			List<String> spots = BlueprintFurniture.of(entry.id()).stream().map(s -> s.kind() + "@" + s.pos().toShortString() + ">" + s.facing()).toList();
			List<String> missing = drawnSpots.stream().filter(s -> !spots.contains(s)).toList();
			helper.assertTrue(missing.isEmpty(), entry.id() + " is missing the drawing's furniture spots " + missing);
		}
		helper.succeed();
	}

	/** A builder builds the desert look to DONE from barrels stocked with what its plan needs. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"daub_house_looks_desert"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "daub_house_looks_desert")
	public void buildsTheDesertDaubHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("desert"), ORIGIN);
	}

	/** And the savanna look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"daub_house_looks_savanna"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "daub_house_looks_savanna")
	public void buildsTheSavannaDaubHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("savanna"), ORIGIN);
	}

	/** And the snowy look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"daub_house_looks_snowy"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "daub_house_looks_snowy")
	public void buildsTheSnowyDaubHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("snowy"), ORIGIN);
	}

	/** And the taiga look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"daub_house_looks_taiga"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "daub_house_looks_taiga")
	public void buildsTheTaigaDaubHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("taiga"), ORIGIN);
	}

	/**
	 * The desert look saved and loaded back mid-build (a restart): the site comes back as that look, not the Daub House as
	 * drawn, and the builder finishes it with every block of the look in place.
	 */
	//$ gametest_ticks_batch BIG_AREA '30000' '"daub_house_looks_reload"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "daub_house_looks_reload")
	public void aDesertDaubHouseSavedMidBuildReloadsAsTheDesertLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		WeaversCottageLooksGameTests.reloadMidBuild(helper, look("desert").id(), ORIGIN, "the desert Daub House");
	}
}
