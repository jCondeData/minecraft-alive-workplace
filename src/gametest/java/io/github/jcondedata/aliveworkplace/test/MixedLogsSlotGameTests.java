package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
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
 * B1301: a slot that takes any of a tag (a chest's planks) is filled from planks made from the logs of several woods, each
 * wood making what it can, as a player at a crafting table does; the plan takes only the logs it needs, and a Carpenter
 * makes the builder's chests that way through his normal job. Nothing here is random.
 */
public class MixedLogsSlotGameTests implements FreshPlanning {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;

	private static long sum(Map<Item, Integer> takes) {
		return takes.values().stream().mapToLong(Integer::longValue).sum();
	}

	/** Two chests from 2 oak, 1 spruce and 1 birch log: 8 oak planks and 4 of each other, all four logs, nothing short. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void twoChestsFromAnUnevenMixOfLogs(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.OAK_LOG, 2L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L);
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, chest);
		helper.assertTrue(plan != null, "no plan for 2 chests from 2 oak, 1 spruce and 1 birch log");
		helper.assertTrue(plan.takes().equals(Map.of(Items.OAK_LOG, 2, Items.SPRUCE_LOG, 1, Items.BIRCH_LOG, 1)), "the chests' plan takes " + plan.takes());
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, plan);
		helper.assertTrue(left.getOrDefault(Items.CHEST, 0L) == 2, "the steps make " + left.getOrDefault(Items.CHEST, 0L) + " chests: " + plan.steps());
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, chest);
		helper.assertTrue(lack.isEmpty(), "2 chests from 4 logs of three woods lack " + lack);
		helper.succeed();
	}

	/**
	 * Nothing extra: one chest from 1 oak log, 1 spruce log and a birch log to spare takes just two logs (8 planks), and
	 * the logs top up planks already there (3 oak planks, an oak and a spruce log) without taking a third log.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromLogsTakesNothingExtra(GameTestHelper helper) {
		Crafting.Plan three = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1,
			Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L));
		helper.assertTrue(three != null, "no plan for a chest from an oak, a spruce and a birch log");
		helper.assertTrue(sum(three.takes()) == 2 && three.takes().keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS)),
			"a chest from three woods' logs takes " + three.takes() + ", expected two logs");
		helper.assertTrue(MixedTagSlotGameTests.play(helper, three).getOrDefault(Items.CHEST, 0L) == 1, "the steps make no chest: " + three.steps());

		Map<Item, Long> topped = Map.of(Items.OAK_PLANKS, 3L, Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L);
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, topped);
		helper.assertTrue(plan != null, "no plan for a chest from 3 oak planks and three woods' logs");
		helper.assertTrue(plan.takes().getOrDefault(Items.OAK_PLANKS, 0) == 3 && sum(plan.takes()) == 5,
			"a chest from 3 oak planks and three logs takes " + plan.takes() + ", expected the 3 planks and two logs");
		helper.assertTrue(MixedTagSlotGameTests.play(helper, plan).getOrDefault(Items.CHEST, 0L) == 1, "the steps make no chest: " + plan.steps());
		helper.assertTrue(Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, topped).isEmpty(), "short of something");
		// one log alone is still short: not made up from nothing
		helper.assertTrue(Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.OAK_LOG, 1L)) == null,
			"a chest planned from one log");
		helper.succeed();
	}

	/**
	 * The whole job: a builder's chest holds 3 oak, 3 spruce and 1 birch log for his row of 3 chests (24 planks, more than
	 * any one wood makes). The Carpenter saws planks from several woods, makes the chests, the row is built, six logs are
	 * used and one is left, and no plank is left over or lying about.
	 */
	//$ gametest_ticks_batch AREA '4800' '"b1301MixedLogs"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "b1301MixedLogs")
	public void aCarpenterMakesChestsFromSeveralWoodsLogs(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		helper.assertTrue(CraftJobs.makes(helper.getLevel(), CraftJobs.Job.CARPENTER, Items.CHEST), "a chest isn't the Carpenter's");
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.CHEST, new ItemStack(Items.OAK_LOG, 3), new ItemStack(Items.SPRUCE_LOG, 3),
			new ItemStack(Items.BIRCH_LOG, 1));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		Item[] logs = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG};
		Item[] planks = {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS};
		helper.succeedWhen(() -> {
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
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "still in the carpenter's bag: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
		});
	}
}
