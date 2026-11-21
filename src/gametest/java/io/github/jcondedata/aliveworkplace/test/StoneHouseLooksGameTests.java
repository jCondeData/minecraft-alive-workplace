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
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.ArrayList;
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
 * The Stone House's biome looks (ROADMAP 49.10b, as the Starter Cottage's in 49.10a): a desert, savanna or taiga village
 * builds the Stone House in its culture's look, plains as drawn, and snowy as drawn too (its snowy look went back to the
 * critic three times and waits for the owner, so it isn't shipped); where the village's own look doesn't fit, the
 * others are tried after it. Every look of a level is that level to everything else (its base, its upgrade, its lock on
 * the Blueprint Table), with the same size, beds and job blocks, and a builder builds it to DONE.
 */
public class StoneHouseLooksGameTests {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final List<StarterBlueprints.Entry> LEVELS = List.of(StarterBlueprints.STONE_HOUSE, StarterBlueprints.STONE_HOUSE_2,
		StarterBlueprints.STONE_HOUSE_3, StarterBlueprints.STONE_HOUSE_4);
	private static final Map<String, List<StarterBlueprints.Entry>> LOOKS = Map.of("desert", StarterBlueprints.STONE_HOUSE_DESERT,
		"savanna", StarterBlueprints.STONE_HOUSE_SAVANNA, "taiga", StarterBlueprints.STONE_HOUSE_TAIGA);

	/**
	 * Each culture's village picks its own look of every level, wherever its hall stands; a plains village the Stone House
	 * as drawn, and a snowy one too (no snowy look ships), even when it is handed another look.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachCulturesVillagePicksItsOwnLookOfEveryLevel(GameTestHelper helper) {
		List<BlockPos> halls = List.of(BlockPos.ZERO, new BlockPos(1000, 64, -2000), new BlockPos(-37, 70, 512), new BlockPos(5, 80, 5));
		for (int level = 0; level < LEVELS.size(); level++) {
			ResourceLocation drawn = LEVELS.get(level).id();
			helper.assertTrue(BlueprintLooks.byCulture(drawn), drawn + " should be built by culture");
			for (BlockPos hall : halls) {
				for (Map.Entry<String, List<StarterBlueprints.Entry>> look : LOOKS.entrySet()) {
					ResourceLocation want = look.getValue().get(level).id();
					ResourceLocation got = BlueprintLooks.pick(drawn, look.getKey(), hall);
					helper.assertTrue(got.equals(want), "a " + look.getKey() + " village at " + hall + " picked " + got + " for " + drawn + ", not " + want);
					// asked with a look in hand (an upgrade of one), the same answer
					helper.assertTrue(BlueprintLooks.pick(want, look.getKey(), hall).equals(want), "a look of " + drawn + " asked again changed: " + want);
					// a snowy village handed this look builds the drawing: the Stone House has no snowy look
					helper.assertTrue(BlueprintLooks.pick(want, "snowy", hall).equals(drawn),
						"a snowy village handed " + want + " should build " + drawn + " as drawn");
				}
				helper.assertTrue(BlueprintLooks.pick(drawn, "plains", hall).equals(drawn), "a plains village should build " + drawn + " as drawn");
				helper.assertTrue(BlueprintLooks.pick(drawn, "snowy", hall).equals(drawn), "a snowy village should build " + drawn + " as drawn");
				helper.assertTrue(BlueprintLooks.pick(drawn, "jungle", hall).equals(drawn), "a culture with no look should build " + drawn + " as drawn");
			}
		}
		helper.succeed();
	}

	/**
	 * Where the village's own look doesn't fit, the steward tries every other look of that level (as drawn first), each
	 * once; the snowy look isn't among them, and no snowy Stone House is in the library.
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
			helper.assertTrue(tried.size() == 4 && new java.util.HashSet<>(tried).size() == 4, "every look of " + drawn + " once: " + tried);
			helper.assertTrue(tried.contains(drawn), "the Stone House as drawn should be a fallback: " + tried);
			for (List<StarterBlueprints.Entry> look : LOOKS.values()) {
				helper.assertTrue(tried.contains(look.get(level).id()), "missing " + look.get(level).id() + " from " + tried);
			}
			ResourceLocation snowy = BlueprintLooks.look(drawn, "snowy");
			helper.assertFalse(tried.contains(snowy), "the snowy look isn't shipped, yet a village would try it: " + tried);
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), snowy).isEmpty(), snowy + " is in the library, but the critic never passed it");
		}
		helper.succeed();
	}

	/** Every look is its level to the rest of the mod: its base, its upgrade, the next level's look, and its lock on the Table. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookCountsAsItsLevelOfTheStoneHouse(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		for (Map.Entry<String, List<StarterBlueprints.Entry>> look : LOOKS.entrySet()) {
			for (int level = 0; level < LEVELS.size(); level++) {
				ResourceLocation id = look.getValue().get(level).id();
				ResourceLocation drawn = LEVELS.get(level).id();
				helper.assertTrue(id.equals(BlueprintLooks.look(drawn, look.getKey())), id + " isn't named as a look of " + drawn);
				helper.assertTrue(BlueprintLooks.base(id).equals(drawn), "the base of " + id + " is " + BlueprintLooks.base(id) + ", not " + drawn);
				helper.assertTrue(BlueprintLooks.lookOf(id).orElse("").equals(look.getKey()), id + " should be the " + look.getKey() + " look");
				helper.assertTrue(BlueprintLooks.of(id).equals(BlueprintLooks.of(drawn)), "the looks of " + id + " differ from " + drawn + "'s");
				helper.assertTrue(BlueprintUpgrades.tier(id) == level + 1, id + " should be level " + (level + 1));
				if (level + 1 < LEVELS.size()) {
					helper.assertTrue(BlueprintUpgrades.upgradeOf(id).equals(look.getValue().get(level + 1).id()),
						id + " should upgrade to " + look.getValue().get(level + 1).id() + ", not " + BlueprintUpgrades.upgradeOf(id));
				}
				helper.assertTrue(BlueprintUnlocks.managed(id), id + " should be locked and unlocked with " + drawn);
				helper.assertTrue(BlueprintUnlocks.state(helper.getLevel(), hall, id) == BlueprintUnlocks.state(helper.getLevel(), hall, drawn),
					id + " shows " + BlueprintUnlocks.state(helper.getLevel(), hall, id) + " on the Table, " + drawn + " "
						+ BlueprintUnlocks.state(helper.getLevel(), hall, drawn));
				helper.assertTrue(StarterBlueprints.basement(id) == LEVELS.get(level).basement(), id + "'s basement should be " + LEVELS.get(level).basement());
			}
		}
		helper.succeed();
	}

	/** Each look of a level is the size of that level, as its entry says, with its beds and job blocks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLookHasItsLevelsSizeBedsAndJobBlocks(GameTestHelper helper) {
		for (int level = 0; level < LEVELS.size(); level++) {
			StarterBlueprints.Entry drawnEntry = LEVELS.get(level);
			Blueprint drawn = load(helper, drawnEntry.id());
			Map<String, Integer> drawnJobs = jobBlocks(drawn);
			int drawnBeds = beds(drawn);
			for (List<StarterBlueprints.Entry> looks : LOOKS.values()) {
				StarterBlueprints.Entry entry = looks.get(level);
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
	//$ gametest_ticks_batch BIG_AREA '30000' '"stone_house_looks_desert"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "stone_house_looks_desert")
	public void buildsTheDesertStoneHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, StarterBlueprints.STONE_HOUSE_DESERT.get(0), new BlockPos(9, 2, 9));
	}

	/** And the savanna look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"stone_house_looks_savanna"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "stone_house_looks_savanna")
	public void buildsTheSavannaStoneHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, StarterBlueprints.STONE_HOUSE_SAVANNA.get(0), new BlockPos(9, 2, 9));
	}

	/** And the taiga look. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"stone_house_looks_taiga"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "stone_house_looks_taiga")
	public void buildsTheTaigaStoneHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuilderGameTests.buildStarter(helper, StarterBlueprints.STONE_HOUSE_TAIGA.get(0), new BlockPos(9, 2, 9));
	}

	/**
	 * The desert look saved and loaded back mid-build (a restart): the site comes back as that look, not the Stone House
	 * as drawn, and the builder finishes it with every block of the look in place.
	 */
	//$ gametest_ticks_batch BIG_AREA '30000' '"stone_house_looks_reload"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "stone_house_looks_reload")
	public void aDesertStoneHouseSavedMidBuildReloadsAsTheDesertLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = StarterBlueprints.STONE_HOUSE_DESERT.get(0).id();
		BlockPos origin = new BlockPos(9, 2, 9);
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
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) != null, "the desert Stone House was done before the save: no mid-build reload tested");
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			BuildSite back = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(back != null && back.structure().equals(id), "the site came back as " + (back == null ? "nothing" : back.structure()) + ", not " + id);
			reloaded[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "not reloaded yet");
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) == null, "still building the desert Stone House after the reload");
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the desert look wrong after the reload, e.g. "
				+ unfinished.stream().limit(3).map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList());
		});
	}

	private static Blueprint load(GameTestHelper helper, ResourceLocation id) {
		return BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new GameTestAssertException("missing blueprint " + id));
	}

	private static int beds(Blueprint blueprint) {
		return (int) blueprint.blocks().stream()
			.filter(e -> e.state().getBlock() instanceof BedBlock && e.state().getValue(BedBlock.PART) == BedPart.HEAD).count();
	}

	/** The job blocks (every POI but a bed's), by POI type. */
	private static Map<String, Integer> jobBlocks(Blueprint blueprint) {
		Map<String, Integer> out = new TreeMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			PoiTypes.forState(e.state()).filter(p -> !p.is(PoiTypes.HOME))
				.ifPresent(p -> out.merge(p.unwrapKey().map(k -> k.location().toString()).orElse("?"), 1, Integer::sum));
		}
		return out;
	}
}
