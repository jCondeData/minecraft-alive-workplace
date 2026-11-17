package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.HashMap;
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
 * B1293: a recipe slot that takes any of a tag (planks, logs) takes any mix of it, as a crafting table does. The plan
 * takes exactly what's there (nothing more, nothing less), every step's makings are in hand when it comes, a shortage
 * line counts the whole tag, and a Carpenter makes the things from a chest of mixed planks and uses all of them.
 * Nothing here is random.
 */
public class MixedTagSlotGameTests implements FreshPlanning {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;

	/**
	 * Plays the plan's steps from what it takes: every step's makings must be in hand when it comes; returns what's left
	 * at the end, or throws.
	 */
	static Map<Item, Long> play(GameTestHelper helper, Crafting.Plan plan) {
		Map<Item, Long> have = new HashMap<>();
		plan.takes().forEach((item, n) -> have.merge(item, (long) n, Long::sum));
		for (Crafting.Step step : plan.steps()) {
			step.in().forEach((item, n) -> {
				long left = have.getOrDefault(item, 0L) - (long) n * step.times();
				helper.assertTrue(left >= 0, "step " + step + " takes more " + item + " than the plan has: " + plan);
				have.put(item, left);
			});
			have.merge(step.out().getItem(), (long) step.out().getCount() * step.times(), Long::sum);
		}
		return have;
	}

	/** A chest from 4 oak and 4 spruce planks: planned, taking exactly those 8, and the steps make the chest from them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromMixedPlanksIsPlannedFromExactlyThose(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.OAK_PLANKS, 4L, Items.SPRUCE_PLANKS, 4L);
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, chest);
		helper.assertTrue(plan != null, "no plan for a chest from 4 oak and 4 spruce planks");
		helper.assertTrue(plan.takes().equals(Map.of(Items.OAK_PLANKS, 4, Items.SPRUCE_PLANKS, 4)), "the chest's plan takes " + plan.takes());
		Map<Item, Long> left = play(helper, plan);
		helper.assertTrue(left.getOrDefault(Items.CHEST, 0L) == 1 && plan.count() == 1, "the steps make " + left.getOrDefault(Items.CHEST, 0L) + " chests");
		helper.assertTrue(left.getOrDefault(Items.OAK_PLANKS, 0L) == 0 && left.getOrDefault(Items.SPRUCE_PLANKS, 0L) == 0, "planks left over: " + left);
		helper.succeed();
	}

	/** A smoker from 8 cobblestone and four logs of four woods: the furnace, then the smoker, every log used once. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSmokerFromFourWoodsLogsIsPlannedFromExactlyThose(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.COBBLESTONE, 8L, Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L, Items.CHERRY_LOG, 1L);
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.SMOKER, 1, chest);
		helper.assertTrue(plan != null, "no plan for a smoker from 8 cobblestone and four woods' logs");
		helper.assertTrue(plan.takes().equals(Map.of(Items.COBBLESTONE, 8, Items.OAK_LOG, 1, Items.SPRUCE_LOG, 1, Items.BIRCH_LOG, 1, Items.CHERRY_LOG, 1)),
			"the smoker's plan takes " + plan.takes());
		Map<Item, Long> left = play(helper, plan);
		helper.assertTrue(left.getOrDefault(Items.SMOKER, 0L) == 1, "the steps make " + left.getOrDefault(Items.SMOKER, 0L) + " smokers: " + plan.steps());
		helper.succeed();
	}

	/**
	 * Several crafts from a mix that doesn't split evenly: 3 crafting tables from 6 oak and 6 spruce planks (one craft
	 * takes 2 of each). Every plank is used, three tables come out, and no step takes what isn't there yet.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void threeTablesFromAnUnevenMixUseEveryPlank(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.OAK_PLANKS, 6L, Items.SPRUCE_PLANKS, 6L);
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 3, chest);
		helper.assertTrue(plan != null, "no plan for 3 crafting tables from 6 oak and 6 spruce planks");
		helper.assertTrue(plan.takes().equals(Map.of(Items.OAK_PLANKS, 6, Items.SPRUCE_PLANKS, 6)), "the tables' plan takes " + plan.takes());
		Map<Item, Long> left = play(helper, plan);
		helper.assertTrue(left.getOrDefault(Items.CRAFTING_TABLE, 0L) == 3 && plan.count() == 3, "the steps make " + left.getOrDefault(Items.CRAFTING_TABLE, 0L)
			+ " tables: " + plan.steps());
		helper.succeed();
	}

	/**
	 * Truly short, the line counts the whole tag: one oak and one spruce log are 2 logs short of a smoker's 4 (not 3), and
	 * 3 oak and 2 spruce planks with no logs are 3 planks short of a chest (not 5). One kind is named, with that count.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void trulyShortTheLineCountsWhatsThereOfTheTag(GameTestHelper helper) {
		Map<Item, Integer> logs = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.SMOKER, 1,
			Map.of(Items.COBBLESTONE, 8L, Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L));
		helper.assertTrue(logs.size() == 1 && logs.values().iterator().next() == 2 && logs.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.LOGS),
			"one oak and one spruce log: a smoker lacks " + logs + ", expected 2 logs of one kind");
		Map<Item, Integer> planks = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1,
			Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 2L));
		helper.assertTrue(planks.size() == 1 && planks.values().iterator().next() == 3 && planks.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.PLANKS),
			"3 oak and 2 spruce planks: a chest lacks " + planks + ", expected 3 planks of one kind");
		helper.assertTrue(Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 2L)) == null,
			"a chest planned from 5 planks");
		helper.succeed();
	}

	/**
	 * The whole job: a builder's chest holds 6 oak and 6 spruce planks and his row of 3 crafting tables; the Carpenter
	 * makes them from the mix, the row is built, every plank is used and nothing is left lying about.
	 */
	//$ gametest_ticks_batch AREA '3600' '"b1293MixedPlanks"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "b1293MixedPlanks")
	public void aCarpenterMakesTablesFromAChestOfMixedPlanks(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		helper.assertTrue(CraftJobs.makes(helper.getLevel(), CraftJobs.Job.CARPENTER, Items.CRAFTING_TABLE), "a crafting table isn't the Carpenter's");
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.CRAFTING_TABLE, new ItemStack(Items.OAK_PLANKS, 6), new ItemStack(Items.SPRUCE_PLANKS, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			int planks = chest.countItem(Items.OAK_PLANKS) + chest.countItem(Items.SPRUCE_PLANKS);
			helper.assertTrue(planks == 0, planks + " planks left in the chest");
			helper.assertTrue(chest.countItem(Items.CRAFTING_TABLE) == 0, chest.countItem(Items.CRAFTING_TABLE) + " spare tables in the chest");
			helper.assertTrue(QaCrafterFurnitureGameTests.loose(helper, Items.OAK_PLANKS) + QaCrafterFurnitureGameTests.loose(helper, Items.SPRUCE_PLANKS)
				+ QaCrafterFurnitureGameTests.loose(helper, Items.CRAFTING_TABLE) == 0, "planks or tables dropped on the ground");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "still in the carpenter's bag: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 3, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
		});
	}
}
