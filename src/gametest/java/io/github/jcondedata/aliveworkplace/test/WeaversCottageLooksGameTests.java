package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * The Weaver's Cottage's biome looks (ROADMAP 49.10d, as the Terrace's in 49.10c): a desert, savanna, snowy or taiga
 * village builds the Shepherd's cottage in its culture's look at both levels, plains as drawn; where the village's own
 * look doesn't fit, the others are tried after it. Every look of a level is that level to everything else (its base, its
 * upgrade, its lock on the Blueprint Table, its place on the Village Map), with the same size, beds and job blocks (the
 * loom), and a builder builds it to DONE.
 */
public class WeaversCottageLooksGameTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final List<StarterBlueprints.Entry> LEVELS = List.of(StarterBlueprints.WEAVERS_COTTAGE, StarterBlueprints.WEAVERS_COTTAGE_2);
	/**
	 * The cultures with a look, as {@code StarterBlueprints.WEAVERS_COTTAGE_CULTURES}: to hold a look back, take it out of
	 * both lists (and delete its build test below).
	 */
	private static final List<String> LOOKS = List.of("desert", "savanna", "snowy", "taiga");
	/** Where the cottage goes in the huge area: 18 wide and up to 17 deep, behind the barrels' row at z 4. */
	private static final BlockPos ORIGIN = new BlockPos(1, 2, 6);

	private static List<StarterBlueprints.Entry> look(String culture) {
		return StarterBlueprints.weaversCottageLook(culture);
	}

	/**
	 * Each culture's village picks its own look of every level, wherever its hall stands; a plains village (and one of a
	 * culture with no look) the cottage as drawn, even when it is handed a look.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachCulturesVillagePicksItsOwnLookOfEveryLevel(GameTestHelper helper) {
		helper.assertTrue(new HashSet<>(LOOKS).equals(new HashSet<>(StarterBlueprints.WEAVERS_COTTAGE_CULTURES)),
			"the test's looks " + LOOKS + " differ from StarterBlueprints' " + StarterBlueprints.WEAVERS_COTTAGE_CULTURES);
		List<BlockPos> halls = List.of(BlockPos.ZERO, new BlockPos(1000, 64, -2000), new BlockPos(-37, 70, 512), new BlockPos(5, 80, 5));
		for (int level = 0; level < LEVELS.size(); level++) {
			ResourceLocation drawn = LEVELS.get(level).id();
			helper.assertTrue(BlueprintLooks.byCulture(drawn), drawn + " should be built by culture");
			for (BlockPos hall : halls) {
				for (String culture : LOOKS) {
					ResourceLocation want = look(culture).get(level).id();
					ResourceLocation got = BlueprintLooks.pick(drawn, culture, hall);
					helper.assertTrue(got.equals(want), "a " + culture + " village at " + hall + " picked " + got + " for " + drawn + ", not " + want);
					// asked with a look in hand (an upgrade of one), the same answer
					helper.assertTrue(BlueprintLooks.pick(want, culture, hall).equals(want), "a look of " + drawn + " asked again changed: " + want);
					// a plains village handed this look builds the drawing
					helper.assertTrue(BlueprintLooks.pick(want, "plains", hall).equals(drawn),
						"a plains village handed " + want + " should build " + drawn + " as drawn");
				}
				for (String culture : BlueprintLooks.CULTURES) {
					if (!LOOKS.contains(culture)) { // a look held back: that culture builds the cottage as drawn
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
			helper.assertTrue(tried.contains(drawn), "the Weaver's Cottage as drawn should be a fallback: " + tried);
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
	 * no basement, and a workshop on the Village Map.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookCountsAsItsLevelOfTheWeaversCottage(GameTestHelper helper) {
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
				}
				helper.assertTrue(BlueprintUnlocks.managed(id), id + " should be locked and unlocked with " + drawn);
				helper.assertTrue(BlueprintUnlocks.state(helper.getLevel(), hall, id) == BlueprintUnlocks.state(helper.getLevel(), hall, drawn),
					id + " shows " + BlueprintUnlocks.state(helper.getLevel(), hall, id) + " on the Table, " + drawn + " "
						+ BlueprintUnlocks.state(helper.getLevel(), hall, drawn));
				helper.assertTrue(StarterBlueprints.basement(id) == 0 && LEVELS.get(level).basement() == 0, id + " should have no basement");
				helper.assertTrue(StarterBlueprints.WITH_BASEMENTS.stream().noneMatch(e -> e.id().equals(id)), id + " is listed with the basement builds");
				helper.assertTrue(VillageMaps.kindOf(id).equals(VillageMaps.kindOf(drawn)) && VillageMaps.kindOf(id).orElse(null) == VillageMaps.Kind.WORKSHOPS,
					id + " is " + VillageMaps.kindOf(id) + " on the Village Map, not a workshop as " + drawn);
				helper.assertTrue(StarterBlueprints.WEAVERS_COTTAGE_LOOKS.contains(looks.get(level)), id + " isn't among WEAVERS_COTTAGE_LOOKS");
			}
		}
		helper.assertTrue(StarterBlueprints.WEAVERS_COTTAGE_LOOKS.size() == LOOKS.size() * LEVELS.size(),
			"WEAVERS_COTTAGE_LOOKS holds " + StarterBlueprints.WEAVERS_COTTAGE_LOOKS.size() + " looks, not " + LOOKS.size() * LEVELS.size());
		helper.succeed();
	}

	/** Each look of a level is the size of that level, as its entry says, with its beds (none) and job blocks (the loom). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookHasItsLevelsSizeBedsAndJobBlocks(GameTestHelper helper) {
		for (int level = 0; level < LEVELS.size(); level++) {
			StarterBlueprints.Entry drawnEntry = LEVELS.get(level);
			Blueprint drawn = load(helper, drawnEntry.id());
			Map<String, Integer> drawnJobs = jobBlocks(drawn);
			int drawnBeds = beds(drawn);
			helper.assertTrue(drawnJobs.equals(Map.of("minecraft:shepherd", 1)), drawnEntry.id() + "'s job blocks are " + drawnJobs + ", not one loom");
			for (String culture : LOOKS) {
				StarterBlueprints.Entry entry = look(culture).get(level);
				Blueprint look = load(helper, entry.id());
				Vec3i size = look.size();
				helper.assertTrue(size.equals(entry.size()), entry.id() + " is " + size.toShortString() + ", its entry says " + entry.size().toShortString());
				helper.assertTrue(size.equals(drawn.size()), entry.id() + " is " + size.toShortString() + ", " + drawnEntry.id() + " " + drawn.size().toShortString());
				helper.assertTrue(look.basement() == drawn.basement(), entry.id() + " has " + look.basement() + " under the ground, " + drawnEntry.id() + " " + drawn.basement());
				helper.assertTrue(beds(look) == drawnBeds, entry.id() + " has " + beds(look) + " beds, " + drawnEntry.id() + " " + drawnBeds);
				helper.assertTrue(jobBlocks(look).equals(drawnJobs), entry.id() + " has job blocks " + jobBlocks(look) + ", " + drawnEntry.id() + " " + drawnJobs);
			}
		}
		helper.succeed();
	}

	/** A builder builds the desert look of Level I to DONE from barrels stocked with what its plan needs. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"weavers_cottage_looks_desert"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "weavers_cottage_looks_desert")
	public void buildsTheDesertWeaversCottage(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("desert").get(0), ORIGIN);
	}

	/** And the savanna look. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"weavers_cottage_looks_savanna"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "weavers_cottage_looks_savanna")
	public void buildsTheSavannaWeaversCottage(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("savanna").get(0), ORIGIN);
	}

	/** And the snowy look. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"weavers_cottage_looks_snowy"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "weavers_cottage_looks_snowy")
	public void buildsTheSnowyWeaversCottage(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("snowy").get(0), ORIGIN);
	}

	/** And the taiga look. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"weavers_cottage_looks_taiga"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "weavers_cottage_looks_taiga")
	public void buildsTheTaigaWeaversCottage(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, look("taiga").get(0), ORIGIN);
	}

	/**
	 * The desert look saved and loaded back mid-build (a restart): the site comes back as that look, not the cottage as
	 * drawn, and the builder finishes it with every block of the look in place.
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"weavers_cottage_looks_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "weavers_cottage_looks_reload")
	public void aDesertWeaversCottageSavedMidBuildReloadsAsTheDesertLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		reloadMidBuild(helper, look("desert").get(0).id(), ORIGIN, "the desert Weaver's Cottage");
	}

	/** Builds {@code id} at {@code origin}, saves and loads the sites back 400 ticks in, and waits for it to be finished as that look. */
	static void reloadMidBuild(GameTestHelper helper, ResourceLocation id, BlockPos origin, String name) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(load(helper, id), placement);
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, count) -> {
			for (int left = count; left > 0; left -= item.getDefaultMaxStackSize()) {
				stock.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		});
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "the test needs more barrels for " + id);
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(new BlockPos(2, 2, 2)));
		BuildSite site = Builders.start(level, villager, null, id, placement);
		java.util.UUID siteId = site.id();
		boolean[] reloaded = {false};
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) != null, name + " was done before the save: no mid-build reload tested");
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			BuildSite back = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(back != null && back.structure().equals(id), "the site came back as " + (back == null ? "nothing" : back.structure()) + ", not " + id);
			reloaded[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "not reloaded yet");
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) == null, "still building " + name + " after the reload");
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of " + id + " wrong after the reload, e.g. "
				+ unfinished.stream().limit(3).map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList());
		});
	}

	static Blueprint load(GameTestHelper helper, ResourceLocation id) {
		return BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new GameTestAssertException("missing blueprint " + id));
	}

	static int beds(Blueprint blueprint) {
		return (int) blueprint.blocks().stream()
			.filter(e -> e.state().getBlock() instanceof BedBlock && e.state().getValue(BedBlock.PART) == BedPart.HEAD).count();
	}

	/** The job blocks (every POI but a bed's), by POI type. */
	static Map<String, Integer> jobBlocks(Blueprint blueprint) {
		Map<String, Integer> out = new TreeMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			PoiTypes.forState(e.state()).filter(p -> !p.is(PoiTypes.HOME))
				.ifPresent(p -> out.merge(p.unwrapKey().map(k -> k.location().toString()).orElse("?"), 1, Integer::sum));
		}
		return out;
	}
}
