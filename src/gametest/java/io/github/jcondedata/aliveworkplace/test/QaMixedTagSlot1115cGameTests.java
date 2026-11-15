package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-15) past B1293, from crafter.md: a slot that takes any of a tag takes any mix of it, as a crafting
 * table does; truly short, the line counts the whole tag. Boundaries (exactly enough across three woods, one short), the
 * same planks shared by two slots (a pickaxe's head and its sticks), planks made from logs to top up a mix, a slot for
 * one wood never filled with another, a big uneven order, and two Carpenters at work: one uses exactly the chest's mix
 * and nothing else there, one names what a one-wood recipe lacks and takes nothing. Nothing here is random.
 */
public class QaMixedTagSlot1115cGameTests implements FabricGameTest {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;

	private static Crafting.Plan plan(GameTestHelper helper, Item target, int count, Map<Item, Long> chest) {
		return Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest);
	}

	private static Map<Item, Integer> lacking(GameTestHelper helper, Item target, int count, Map<Item, Long> chest) {
		return Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest);
	}

	/**
	 * A chest from 3 oak, 3 spruce and 2 birch planks: exactly enough, planned taking exactly those 8 and nothing short.
	 * One fewer (3 oak, 2 spruce, 2 birch): no plan, and one kind of planks is short by 1, read "short of 1 … Planks to
	 * make 1 Chest".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void threeWoodsExactlyEnoughOrOneShort(GameTestHelper helper) {
		Map<Item, Long> enough = Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 3L, Items.BIRCH_PLANKS, 2L);
		Crafting.Plan made = plan(helper, Items.CHEST, 1, enough);
		helper.assertTrue(made != null, "no plan for a chest from 3 oak, 3 spruce and 2 birch planks");
		helper.assertTrue(made.takes().equals(Map.of(Items.OAK_PLANKS, 3, Items.SPRUCE_PLANKS, 3, Items.BIRCH_PLANKS, 2)), "the chest's plan takes " + made.takes());
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, made);
		helper.assertTrue(left.getOrDefault(Items.CHEST, 0L) == 1, "the steps make " + left.getOrDefault(Items.CHEST, 0L) + " chests: " + made.steps());
		helper.assertTrue(lacking(helper, Items.CHEST, 1, enough).isEmpty(), "8 mixed planks: a chest lacks " + lacking(helper, Items.CHEST, 1, enough));

		Map<Item, Long> seven = Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 2L, Items.BIRCH_PLANKS, 2L);
		helper.assertTrue(plan(helper, Items.CHEST, 1, seven) == null, "a chest planned from 7 planks");
		Map<Item, Integer> lack = lacking(helper, Items.CHEST, 1, seven);
		helper.assertTrue(lack.size() == 1 && lack.values().iterator().next() == 1 && lack.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.PLANKS),
			"7 mixed planks: a chest lacks " + lack + ", expected 1 plank of one kind");
		String line = JobShortages.lackLine(lack, 1, Items.CHEST).getString();
		helper.assertTrue(line.matches("short of 1 [A-Z][a-z]+( [A-Z][a-z]+)* Planks to make 1 Chest"), "the shortage line reads \"" + line + "\"");
		helper.succeed();
	}

	/**
	 * A wooden pickaxe takes planks twice, 3 in its head and 2 more as its sticks: from 2 oak, 2 spruce and 1 birch plank
	 * it's planned taking all 5 and no plank counted twice; from 4 mixed planks it can't be, and what's short adds up to 1.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePickaxesHeadAndSticksShareTheMixWithoutCountingTwice(GameTestHelper helper) {
		Map<Item, Long> five = Map.of(Items.OAK_PLANKS, 2L, Items.SPRUCE_PLANKS, 2L, Items.BIRCH_PLANKS, 1L);
		Crafting.Plan made = plan(helper, Items.WOODEN_PICKAXE, 1, five);
		helper.assertTrue(made != null, "no plan for a wooden pickaxe from 2 oak, 2 spruce and 1 birch plank");
		helper.assertTrue(made.takes().equals(Map.of(Items.OAK_PLANKS, 2, Items.SPRUCE_PLANKS, 2, Items.BIRCH_PLANKS, 1)), "the pickaxe's plan takes " + made.takes());
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, made);
		helper.assertTrue(left.getOrDefault(Items.WOODEN_PICKAXE, 0L) == 1, "the steps make " + left.getOrDefault(Items.WOODEN_PICKAXE, 0L) + " pickaxes: " + made.steps());

		Map<Item, Long> four = Map.of(Items.OAK_PLANKS, 2L, Items.SPRUCE_PLANKS, 1L, Items.BIRCH_PLANKS, 1L);
		helper.assertTrue(plan(helper, Items.WOODEN_PICKAXE, 1, four) == null, "a wooden pickaxe planned from 4 planks: " + plan(helper, Items.WOODEN_PICKAXE, 1, four));
		Map<Item, Integer> lack = lacking(helper, Items.WOODEN_PICKAXE, 1, four);
		helper.assertTrue(lack.size() == 1 && lack.values().iterator().next() == 1 && lack.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.PLANKS),
			"4 mixed planks: a wooden pickaxe lacks " + lack + ", expected 1 plank");
		helper.succeed();
	}

	/**
	 * Planks made from logs top up a mix: an oak log with 2 oak and 2 spruce planks make a chest (the 4 planks there and the
	 * log's 4), taking exactly those.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void logsTopUpAMixOfPlanks(GameTestHelper helper) {
		Crafting.Plan topped = plan(helper, Items.CHEST, 1, Map.of(Items.OAK_LOG, 1L, Items.OAK_PLANKS, 2L, Items.SPRUCE_PLANKS, 2L));
		helper.assertTrue(topped != null, "no plan for a chest from an oak log, 2 oak and 2 spruce planks");
		helper.assertTrue(topped.takes().equals(Map.of(Items.OAK_LOG, 1, Items.OAK_PLANKS, 2, Items.SPRUCE_PLANKS, 2)), "the topped-up chest's plan takes " + topped.takes());
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, topped);
		helper.assertTrue(left.getOrDefault(Items.CHEST, 0L) == 1, "the steps make " + left.getOrDefault(Items.CHEST, 0L) + " chests: " + topped.steps());
		helper.succeed();
	}

	/**
	 * A recipe for one wood (oak stairs take oak planks) is never made from another: 3 oak and 6 spruce planks lack exactly
	 * 3 Oak Planks; 6 of each take the 6 oak; 4 oak planks and a stripped oak log (an oak log too) take no spruce.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aOneWoodRecipeNeverTakesAnotherWood(GameTestHelper helper) {
		Map<Item, Long> short3 = Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 6L);
		helper.assertTrue(plan(helper, Items.OAK_STAIRS, 4, short3) == null, "oak stairs planned from 3 oak and 6 spruce planks: " + plan(helper, Items.OAK_STAIRS, 4, short3));
		Map<Item, Integer> lack = lacking(helper, Items.OAK_STAIRS, 4, short3);
		helper.assertTrue(lack.equals(Map.of(Items.OAK_PLANKS, 3)), "3 oak and 6 spruce planks: oak stairs lack " + lack + ", expected {oak_planks=3}");

		Crafting.Plan oak = plan(helper, Items.OAK_STAIRS, 4, Map.of(Items.OAK_PLANKS, 6L, Items.SPRUCE_PLANKS, 6L));
		helper.assertTrue(oak != null && oak.takes().equals(Map.of(Items.OAK_PLANKS, 6)), "oak stairs from 6 oak and 6 spruce planks take "
			+ (oak == null ? "no plan" : oak.takes()));

		Crafting.Plan stripped = plan(helper, Items.OAK_STAIRS, 4, Map.of(Items.OAK_PLANKS, 4L, Items.STRIPPED_OAK_LOG, 1L, Items.SPRUCE_PLANKS, 6L));
		helper.assertTrue(stripped != null, "no plan for oak stairs from 4 oak planks and a stripped oak log");
		helper.assertTrue(!stripped.takes().containsKey(Items.SPRUCE_PLANKS) && stripped.takes().containsKey(Items.STRIPPED_OAK_LOG),
			"oak stairs from 4 oak planks and a stripped oak log take " + stripped.takes());
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, stripped);
		helper.assertTrue(left.getOrDefault(Items.OAK_STAIRS, 0L) >= 4, "the steps make " + left.getOrDefault(Items.OAK_STAIRS, 0L) + " oak stairs: " + stripped.steps());
		helper.succeed();
	}

	/** A big uneven order: 10 chests from 30 oak, 25 spruce, 15 birch and 10 jungle planks, every plank used once. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tenChestsFromFourUnevenWoodsUseEveryPlank(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.OAK_PLANKS, 30L, Items.SPRUCE_PLANKS, 25L, Items.BIRCH_PLANKS, 15L, Items.JUNGLE_PLANKS, 10L);
		Crafting.Plan made = plan(helper, Items.CHEST, 10, chest);
		helper.assertTrue(made != null, "no plan for 10 chests from 80 planks of four woods");
		helper.assertTrue(made.takes().equals(Map.of(Items.OAK_PLANKS, 30, Items.SPRUCE_PLANKS, 25, Items.BIRCH_PLANKS, 15, Items.JUNGLE_PLANKS, 10)),
			"the 10 chests' plan takes " + made.takes());
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, made);
		helper.assertTrue(left.getOrDefault(Items.CHEST, 0L) == 10 && made.count() == 10, "the steps make " + left.getOrDefault(Items.CHEST, 0L) + " chests: " + made.steps());
		Map<Item, Integer> lack = lacking(helper, Items.CHEST, 11, chest);
		helper.assertTrue(lack.values().stream().mapToInt(Integer::intValue).sum() == 8 && lack.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.PLANKS)),
			"80 mixed planks: 11 chests lack " + lack + ", expected 8 planks");
		helper.succeed();
	}

	/**
	 * The whole job, three woods: a builder's chest holds 5 oak, 4 spruce and 3 birch planks for his row of 3 crafting
	 * tables, plus 2 oak logs and 5 cobblestone. The Carpenter makes the tables from the planks, the row is built, and the
	 * chest is left with exactly the logs and the cobblestone, besides the builder's blueprint (no log sawn, nothing taken or
	 * added).
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaMixedTag1115cExact"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaMixedTag1115cExact")
	public void aCarpenterUsesExactlyTheChestsThreeWoods(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.CRAFTING_TABLE, new ItemStack(Items.OAK_PLANKS, 5), new ItemStack(Items.SPRUCE_PLANKS, 4),
			new ItemStack(Items.BIRCH_PLANKS, 3), new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.COBBLESTONE, 5));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			int planks = chest.countItem(Items.OAK_PLANKS) + chest.countItem(Items.SPRUCE_PLANKS) + chest.countItem(Items.BIRCH_PLANKS);
			helper.assertTrue(planks == 0, planks + " planks left in the chest");
			int all = 0;
			StringBuilder held = new StringBuilder();
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(ModItems.BLUEPRINT)) {
					continue; // the builder's blueprint, kept in his chest
				}
				all += chest.getItem(i).getCount();
				if (!chest.getItem(i).isEmpty()) {
					held.append(' ').append(chest.getItem(i));
				}
			}
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 2 && chest.countItem(Items.COBBLESTONE) == 5 && all == 7,
				"the chest holds " + chest.countItem(Items.OAK_LOG) + " oak logs, " + chest.countItem(Items.COBBLESTONE) + " cobblestone, " + all + " items in all:" + held);
			helper.assertTrue(QaCrafterFurnitureGameTests.loose(helper, Items.OAK_PLANKS) + QaCrafterFurnitureGameTests.loose(helper, Items.SPRUCE_PLANKS)
				+ QaCrafterFurnitureGameTests.loose(helper, Items.BIRCH_PLANKS) + QaCrafterFurnitureGameTests.loose(helper, Items.CRAFTING_TABLE) == 0,
				"planks or tables dropped on the ground");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "still in the carpenter's bag: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 3, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
		});
	}

	/**
	 * A one-wood recipe truly short at a real job: the builder wants 3 oak stairs and his chest holds 3 oak and 6 spruce
	 * planks. The Carpenter takes nothing (no spruce passed off as oak), and the builder's line reads "Can't be made yet:
	 * short of 3 Oak Planks to make 3 Oak Stairs".
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaMixedTag1115cShort"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaMixedTag1115cShort")
	public void aCarpenterShortOfOakPlanksNamesThemAndTakesNoSpruce(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		helper.assertTrue(CraftJobs.makes(helper.getLevel(), CraftJobs.Job.CARPENTER, Items.OAK_STAIRS), "oak stairs aren't the Carpenter's");
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			new BlockPos(6, 2, 9), Blocks.OAK_STAIRS, new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.SPRUCE_PLANKS, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		helper.runAfterDelay(600, () -> {
			ServerLevel level = helper.getLevel();
			Component lack = JobShortages.lackOf(a.site().id(), Items.OAK_STAIRS, level.getGameTime());
			helper.assertTrue(lack != null && lack.getString().equals("short of 3 Oak Planks to make 3 Oak Stairs"),
				"the builder's line for the stairs: " + (lack == null ? null : lack.getString()));
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals("Can't be made yet: short of 3 Oak Planks to make 3 Oak Stairs"),
				"the builder's shortage line: " + (line == null ? null : line.getString()));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 3 && chest.countItem(Items.SPRUCE_PLANKS) == 6 && chest.countItem(Items.SPRUCE_STAIRS) == 0,
				"the chest holds " + chest.countItem(Items.OAK_PLANKS) + " oak and " + chest.countItem(Items.SPRUCE_PLANKS) + " spruce planks, "
					+ chest.countItem(Items.SPRUCE_STAIRS) + " spruce stairs");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "the carpenter is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the carpenter took a job it can't finish");
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) != null, "the build can't have finished without its stairs");
			helper.succeed();
		});
	}
}
