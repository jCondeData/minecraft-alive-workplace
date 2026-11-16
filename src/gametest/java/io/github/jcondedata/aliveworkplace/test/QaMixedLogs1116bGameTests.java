package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-16, qa-lane-b-1116-0038) past B1301 (dd725ba1a), from its Expected and docs/wiki/crafter.md: a slot that
 * takes any of a tag (a chest's planks) is filled from planks made from several woods' logs, each wood making its
 * planks, as a player at a crafting table does; only the makings needed are taken, and a real shortage is still short.
 * MixedLogsSlotGameTests and QaMixedTagSlot1115cGameTests cover oak, spruce and birch logs; these go to the other things
 * in #logs and #planks (nether stems, bark and stripped logs, bamboo blocks, which make 2 planks, not 4), four woods for
 * two chests, a barrel (planks and slabs of a wood, both tag slots), the shortage boundary (7 planks' worth) and the
 * Carpenter's job saved and loaded mid-way. Nothing here is random.
 */
public class QaMixedLogs1116bGameTests implements FreshPlanning {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	private static long sum(Map<Item, Integer> m) {
		return m.values().stream().mapToLong(Integer::longValue).sum();
	}

	/** A chest from {@code chest}: planned taking exactly {@code takes}, played to one chest, nothing lacking; the chain agrees. */
	private static void oneChest(GameTestHelper helper, Map<Item, Long> chest, Map<Item, Integer> takes, String what) {
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, chest);
		Crafting.Plan chain = Crafting.chain(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, chest);
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, chest);
		helper.assertTrue(plan != null && chain != null && lack.isEmpty(), what + ": plan " + (plan == null ? "none" : plan.takes()) + ", chain "
			+ (chain == null ? "none" : chain.takes()) + ", lacks " + lack);
		helper.assertTrue(plan.takes().equals(takes), what + ": the chest's plan takes " + plan.takes() + ", expected " + takes);
		helper.assertTrue(MixedTagSlotGameTests.play(helper, plan).getOrDefault(Items.CHEST, 0L) == 1, what + ": the steps make no chest: " + plan.steps());
		helper.assertTrue(MixedTagSlotGameTests.play(helper, chain).getOrDefault(Items.CHEST, 0L) == 1, what + ": the chain makes no chest: " + chain.steps());
	}

	/** One crimson stem and one warped stem (nether woods: their planks are #planks) make a chest. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromACrimsonAndAWarpedStem(GameTestHelper helper) {
		oneChest(helper, Map.of(Items.CRIMSON_STEM, 1L, Items.WARPED_STEM, 1L), Map.of(Items.CRIMSON_STEM, 1, Items.WARPED_STEM, 1), "two stems");
		helper.succeed();
	}

	/** An oak wood (bark on all sides) and a stripped spruce log make a chest: each is its wood's log for planks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromOakWoodAndAStrippedSpruceLog(GameTestHelper helper) {
		oneChest(helper, Map.of(Items.OAK_WOOD, 1L, Items.STRIPPED_SPRUCE_LOG, 1L), Map.of(Items.OAK_WOOD, 1, Items.STRIPPED_SPRUCE_LOG, 1),
			"oak wood and a stripped spruce log");
		helper.succeed();
	}

	/**
	 * Bamboo blocks make 2 planks each, not 4: one oak log and two blocks of bamboo are a chest (4 + 2 + 2), taking all
	 * three; one oak log and one bamboo block (6 planks) are not, and lack something.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromAnOakLogAndBambooBlocksCountsTwoPlanksABlock(GameTestHelper helper) {
		oneChest(helper, Map.of(Items.OAK_LOG, 1L, Items.BAMBOO_BLOCK, 2L), Map.of(Items.OAK_LOG, 1, Items.BAMBOO_BLOCK, 2), "an oak log and 2 bamboo blocks");
		Map<Item, Long> six = Map.of(Items.OAK_LOG, 1L, Items.BAMBOO_BLOCK, 1L);
		helper.assertTrue(Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, six) == null, "a chest planned from 6 planks' worth");
		helper.assertTrue(!Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, six).isEmpty(),
			"6 planks' worth (an oak log and a bamboo block) lack nothing for a chest");
		helper.succeed();
	}

	/** Two chests from one log of each of four woods (16 planks): all four logs taken, two chests, nothing short. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void twoChestsFromFourWoodsOneLogEach(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L, Items.JUNGLE_LOG, 1L);
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, chest);
		helper.assertTrue(plan != null, "no plan for 2 chests from four woods' logs");
		helper.assertTrue(plan.takes().equals(Map.of(Items.OAK_LOG, 1, Items.SPRUCE_LOG, 1, Items.BIRCH_LOG, 1, Items.JUNGLE_LOG, 1)),
			"2 chests from four logs take " + plan.takes());
		helper.assertTrue(MixedTagSlotGameTests.play(helper, plan).getOrDefault(Items.CHEST, 0L) == 2, "the steps don't make 2 chests: " + plan.steps());
		helper.assertTrue(Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, chest).isEmpty(), "2 chests from 16 planks' worth lack something");
		// a third chest is short
		helper.assertTrue(Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 3, chest) == null, "3 chests planned from 16 planks' worth");
		helper.succeed();
	}

	/**
	 * The boundary from below: one oak log and 3 spruce planks are 7 planks, one short: no plan, and the line's lack is
	 * exactly one thing that makes planks (a log or a plank), never nothing. One more spruce plank (8) is a chest that takes
	 * the log and the 4 planks.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sevenPlanksWorthOfLogAndPlanksIsShortAndEightIsAChest(GameTestHelper helper) {
		Map<Item, Long> seven = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_PLANKS, 3L);
		helper.assertTrue(Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, seven) == null, "a chest planned from 7 planks' worth");
		helper.assertTrue(Crafting.chain(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, seven) == null, "a chest chained from 7 planks' worth");
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, seven);
		helper.assertTrue(sum(lack) == 1 && lack.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS)
			|| i.builtInRegistryHolder().is(ItemTags.PLANKS)), "7 planks' worth should lack one log or plank: " + lack);
		oneChest(helper, Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_PLANKS, 4L), Map.of(Items.OAK_LOG, 1, Items.SPRUCE_PLANKS, 4), "an oak log and 4 spruce planks");
		helper.succeed();
	}

	/**
	 * A barrel (6 planks and 2 wooden slabs, both any wood) from one oak, one spruce and one birch log (12 planks): no
	 * single wood makes it, the mix does (3 planks of one wood for the slabs, 6 of the rest), taking all three logs.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBarrelFromThreeWoodsLogs(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L);
		Crafting.Plan chain = Crafting.chain(helper.getLevel(), Crafting.Kind.CRAFTING, Items.BARREL, 1, chest);
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.BARREL, 1, chest);
		helper.assertTrue(chain != null && lack.isEmpty(), "a barrel from three woods' logs: chain " + (chain == null ? "none" : chain.takes()) + ", lacks " + lack);
		helper.assertTrue(chain.takes().equals(Map.of(Items.OAK_LOG, 1, Items.SPRUCE_LOG, 1, Items.BIRCH_LOG, 1)), "the barrel takes " + chain.takes());
		helper.assertTrue(MixedTagSlotGameTests.play(helper, chain).getOrDefault(Items.BARREL, 0L) == 1, "the steps make no barrel: " + chain.steps());
		helper.succeed();
	}

	/**
	 * The whole job, saved and loaded mid-way: a builder's row of 3 chests with 2 oak, 2 spruce, 2 birch and 1 jungle log
	 * in his chest. The Carpenter is saved and loaded once he holds a job; the row is still built, six logs used, one
	 * left, no plank left over or lying about, his bag empty.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1116bMixedLogsReload"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1116bMixedLogsReload")
	public void aCarpentersMixedLogsJobSavedAndLoadedMidWayFinishes(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.CHEST, new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.SPRUCE_LOG, 2),
			new ItemStack(Items.BIRCH_LOG, 2), new ItemStack(Items.JUNGLE_LOG, 1));
		Villager[] carpenter = {QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION)};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reloaded[0] && ModAttachments.CRAFTER_JOB.get(carpenter[0]) != null && !ModAttachments.BUILDER_BAG.getOrCreate(carpenter[0]).isEmpty()) {
				reloaded[0] = true;
				carpenter[0] = QaCrafterFurnitureGameTests.reload(helper.getLevel(), carpenter[0]);
				LOGGER.info("qa 1116b mixed logs: carpenter saved and loaded mid-job at tick " + helper.getTick());
			}
		});
		Item[] logs = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG};
		Item[] planks = {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS};
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the carpenter never held a job with makings in his bag");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			int logsLeft = 0;
			int planksLeft = 0;
			for (Item log : logs) {
				logsLeft += chest.countItem(log) + QaCrafterFurnitureGameTests.loose(helper, log);
			}
			for (Item plank : planks) {
				planksLeft += chest.countItem(plank) + QaCrafterFurnitureGameTests.loose(helper, plank);
			}
			helper.assertTrue(logsLeft == 1, logsLeft + " logs left, expected 1 (six used for 24 planks)");
			helper.assertTrue(planksLeft == 0, planksLeft + " planks left over");
			helper.assertTrue(chest.countItem(Items.CHEST) + QaCrafterFurnitureGameTests.loose(helper, Items.CHEST) == 0, "spare chests about");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter[0]).isEmpty(), "still in the carpenter's bag: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter[0]).stacks());
		});
	}
}
