package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane b, 2026-11-15, h), past B1293's fix, from its text and crafter.md: a slot that takes any of a tag "fills ...
 * from any mix of it, as a crafting table does ... one kind when one has enough, else what's there of them all", "the
 * line names what the chest is short of", and "nothing is taken" when short. Through the player's entry point (a builder
 * short of one chest, a Carpenter by him) and at the plan: three woods in one chest, one wood alone enough, the boundary
 * from both sides (exactly enough across three woods, one plank short: the line, then one plank added), Nether planks,
 * things the recipe doesn't take left alone, two chests' worth, and the Carpenter saved and loaded back mid-job.
 * Nothing here is random.
 */
public class QaTagMix1115hGameTests implements FreshPlanning {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final Item[] PLANKS = {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS};

	// ---- at the plan ------------------------------------------------------------------------------------------------

	private static Crafting.Plan chest(GameTestHelper helper, int count, Map<Item, Long> there) {
		return Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, count, there);
	}

	/** 3 oak, 3 spruce and 2 birch planks, cobblestone, stone and sticks by them: the chest takes exactly the 8 planks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromThreeWoodsTakesExactlyThoseEightAndNothingElse(GameTestHelper helper) {
		Map<Item, Long> there = Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 3L, Items.BIRCH_PLANKS, 2L, Items.COBBLESTONE, 9L, Items.STONE, 4L,
			Items.STICK, 6L);
		Crafting.Plan plan = chest(helper, 1, there);
		helper.assertTrue(plan != null, "no plan for a chest from 3 oak, 3 spruce and 2 birch planks");
		helper.assertTrue(plan.takes().equals(Map.of(Items.OAK_PLANKS, 3, Items.SPRUCE_PLANKS, 3, Items.BIRCH_PLANKS, 2)), "the chest's plan takes " + plan.takes());
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, plan);
		helper.assertTrue(left.getOrDefault(Items.CHEST, 0L) == 1 && plan.count() == 1, "the steps make " + left.getOrDefault(Items.CHEST, 0L) + " chests: " + plan.steps());
		helper.succeed();
	}

	/** Nether planks are planks too: 4 crimson, 3 warped and 1 cherry plank make the chest, taking exactly those. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void netherAndCherryPlanksMixIntoAChest(GameTestHelper helper) {
		Map<Item, Long> there = Map.of(Items.CRIMSON_PLANKS, 4L, Items.WARPED_PLANKS, 3L, Items.CHERRY_PLANKS, 1L, Items.COBBLESTONE, 8L);
		Crafting.Plan plan = chest(helper, 1, there);
		helper.assertTrue(plan != null && plan.takes().equals(Map.of(Items.CRIMSON_PLANKS, 4, Items.WARPED_PLANKS, 3, Items.CHERRY_PLANKS, 1)),
			"a chest from crimson, warped and cherry planks: " + (plan == null ? "no plan" : plan.takes()));
		helper.assertTrue(MixedTagSlotGameTests.play(helper, plan).getOrDefault(Items.CHEST, 0L) == 1, "no chest made: " + plan.steps());
		helper.succeed();
	}

	/** One wood alone has enough (8 oak), with 3 spruce and 2 birch by it: only the oak is taken. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneWoodWithEnoughIsTakenAlone(GameTestHelper helper) {
		Crafting.Plan plan = chest(helper, 1, Map.of(Items.OAK_PLANKS, 8L, Items.SPRUCE_PLANKS, 3L, Items.BIRCH_PLANKS, 2L));
		helper.assertTrue(plan != null && plan.takes().equals(Map.of(Items.OAK_PLANKS, 8)),
			"8 oak, 3 spruce, 2 birch planks: the chest takes " + (plan == null ? "nothing (no plan)" : plan.takes()) + ", expected the 8 oak alone");
		helper.succeed();
	}

	/**
	 * The boundary from both sides: 3 oak, 3 spruce and 2 birch are a chest; 3 oak, 3 spruce and 1 birch (7, with
	 * cobblestone and sticks by them) are no plan, and lack exactly 1 plank of one wood, never 5 of one wood (counting one
	 * kind alone) nor the cobblestone or sticks.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sevenPlanksOfThreeWoodsAreOnePlankShortEightAreEnough(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(chest(helper, 1, Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 3L, Items.BIRCH_PLANKS, 2L)) != null, "8 planks of three woods: no chest");
		Map<Item, Long> seven = Map.of(Items.OAK_PLANKS, 3L, Items.SPRUCE_PLANKS, 3L, Items.BIRCH_PLANKS, 1L, Items.COBBLESTONE, 8L, Items.STICK, 4L);
		helper.assertTrue(chest(helper, 1, seven) == null, "a chest planned from 7 planks");
		Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, seven);
		helper.assertTrue(lack.size() == 1 && lack.values().iterator().next() == 1 && lack.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.PLANKS),
			"7 planks of three woods: a chest lacks " + lack + ", expected 1 plank of one wood");
		helper.succeed();
	}

	/**
	 * Two chests' worth across three woods (6 oak, 5 spruce, 5 birch): two chests take all 16; three chests lack 8 planks
	 * of one wood (not 18 or 19, one wood's count alone).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void twoChestsFromSixteenMixedPlanksAndTheThirdIsEightShort(GameTestHelper helper) {
		Map<Item, Long> there = Map.of(Items.OAK_PLANKS, 6L, Items.SPRUCE_PLANKS, 5L, Items.BIRCH_PLANKS, 5L);
		Crafting.Plan two = chest(helper, 2, there);
		helper.assertTrue(two != null && two.count() == 2 && two.takes().equals(Map.of(Items.OAK_PLANKS, 6, Items.SPRUCE_PLANKS, 5, Items.BIRCH_PLANKS, 5)),
			"two chests from 6 oak, 5 spruce, 5 birch: " + (two == null ? "no plan" : two.count() + " taking " + two.takes()));
		helper.assertTrue(MixedTagSlotGameTests.play(helper, two).getOrDefault(Items.CHEST, 0L) == 2, "the steps don't make 2 chests: " + two.steps());
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 3, there);
		helper.assertTrue(lack.size() == 1 && lack.values().iterator().next() == 8 && lack.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.PLANKS),
			"three chests from 16 mixed planks lack " + lack + ", expected 8 planks of one wood");
		helper.succeed();
	}

	/** A crafting table (2x2) from one plank each of four woods: all four taken, one table. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCraftingTableFromOnePlankOfEachOfFourWoods(GameTestHelper helper) {
		Map<Item, Long> there = Map.of(Items.OAK_PLANKS, 1L, Items.SPRUCE_PLANKS, 1L, Items.MANGROVE_PLANKS, 1L, Items.BAMBOO_PLANKS, 1L);
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, there);
		helper.assertTrue(plan != null && plan.takes().equals(Map.of(Items.OAK_PLANKS, 1, Items.SPRUCE_PLANKS, 1, Items.MANGROVE_PLANKS, 1, Items.BAMBOO_PLANKS, 1)),
			"a crafting table from four woods' planks: " + (plan == null ? "no plan" : plan.takes()));
		helper.assertTrue(MixedTagSlotGameTests.play(helper, plan).getOrDefault(Items.CRAFTING_TABLE, 0L) == 1, "no table made: " + plan.steps());
		helper.succeed();
	}

	// ---- through the Carpenter --------------------------------------------------------------------------------------

	/** A one-block drawing of {@code block} at {@code origin} (a builder short of one of it). */
	private static ResourceLocation one(GameTestHelper helper, BlockPos origin, Block block) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(origin, block);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qatagmix_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()) + "_" + origin.getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(origin, Blocks.AIR);
		return id;
	}

	private static QaCrafterFurnitureGameTests.Asker asker(GameTestHelper helper, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = one(helper, ORIGIN, Blocks.CHEST);
		BlockPos bench = QaCrafterFurnitureGameTests.BENCH;
		BlockPos chestAt = QaCrafterFurnitureGameTests.CHEST;
		helper.setBlock(bench, io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container container = helper.getBlockEntity(chestAt);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, bench.south());
		io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, helper.absolutePos(bench));
		io.github.jcondedata.aliveworkplace.build.BuildSite site = io.github.jcondedata.aliveworkplace.build.Builders.start(level, builder, null, id,
			new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
		io.github.jcondedata.aliveworkplace.build.BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: the chest's drawing not found");
		return new QaCrafterFurnitureGameTests.Asker(builder, site, plan, chestAt);
	}

	private static Map<Item, Integer> counts(Container c, Item... items) {
		Map<Item, Integer> m = new HashMap<>();
		for (Item i : items) {
			m.put(i, c.countItem(i));
		}
		return m;
	}

	private static int loose(GameTestHelper helper, Item... items) {
		int n = 0;
		for (Item i : items) {
			n += QaCrafterFurnitureGameTests.loose(helper, i);
		}
		return n;
	}

	/**
	 * A builder short of a chest whose chest holds 3 oak, 3 spruce and 2 birch planks, with 2 oak logs, 5 cobblestone and
	 * 3 sticks by them: the Carpenter makes the chest from the planks, it's built, every plank is used and the logs,
	 * cobblestone and sticks are all still there (nothing sawn or taken that the planks didn't need).
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaH1115hTagMixChest"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaH1115hTagMixChest")
	public void aCarpenterMakesHisChestFromThreeWoodsAndTakesNothingElse(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		helper.assertTrue(CraftJobs.makes(helper.getLevel(), CraftJobs.Job.CARPENTER, Items.CHEST), "setup: a chest isn't the Carpenter's");
		QaCrafterFurnitureGameTests.Asker a = asker(helper, new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.SPRUCE_PLANKS, 3), new ItemStack(Items.BIRCH_PLANKS, 2),
			new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.COBBLESTONE, 5), new ItemStack(Items.STICK, 3));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(counts(chest, PLANKS).values().stream().allMatch(n -> n == 0), "planks left in the chest: " + counts(chest, PLANKS));
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 2 && chest.countItem(Items.COBBLESTONE) == 5 && chest.countItem(Items.STICK) == 3,
				"taken from the chest though the planks were enough: " + counts(chest, Items.OAK_LOG, Items.COBBLESTONE, Items.STICK));
			helper.assertTrue(chest.countItem(Items.CHEST) == 0 && loose(helper, Items.CHEST, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS) == 0,
				"a spare chest or planks lying about");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "still in the carpenter's bag: " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 1, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
		});
	}

	/**
	 * One plank short across three woods (3 oak, 3 spruce, 1 birch): nothing is taken or made, and the builder's line says
	 * "Can't be made yet: short of 1 Oak/Spruce/Birch Planks to make 1 Chest" (the one plank, of a wood that's there, never
	 * 5 of one wood). Then a player puts one birch plank in: the chest is made from all 8 and built.
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaH1115hTagMixShort"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaH1115hTagMixShort")
	public void onePlankShortTheLineSaysOnePlankThenOneMoreMakesTheChest(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = asker(helper, new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.SPRUCE_PLANKS, 3), new ItemStack(Items.BIRCH_PLANKS, 1));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		ServerLevel level = helper.getLevel();
		AtomicReference<String> said = new AtomicReference<>();
		AtomicInteger since = new AtomicInteger();
		AtomicBoolean added = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (added.get()) {
				return;
			}
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null && chest.countItem(Items.CHEST) == 0,
				"a job was taken with 7 planks: " + ModAttachments.CRAFTER_JOB.get(carpenter));
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 3 && chest.countItem(Items.SPRUCE_PLANKS) == 3 && chest.countItem(Items.BIRCH_PLANKS) == 1,
				"planks taken while short: " + counts(chest, PLANKS));
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			if (line != null && said.get() == null) {
				said.set(line.getString());
			}
			if (said.get() != null && since.incrementAndGet() > 400) {
				chest.setItem(10, new ItemStack(Items.BIRCH_PLANKS, 1));
				added.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(added.get(), "waiting for the line and 400 ticks with it");
			String line = said.get();
			helper.assertTrue(line.matches("Can't be made yet: short of 1 (Oak|Spruce|Birch) Planks to make 1 Chest"),
				"7 planks of three woods, the builder's line: \"" + line + "\"");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(counts(chest, PLANKS).values().stream().allMatch(n -> n == 0), "planks left once the eighth came: " + counts(chest, PLANKS));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 1, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
		});
	}

	/**
	 * The Carpenter saved and loaded back while he makes the chest from 3 oak, 3 spruce and 2 birch planks (the planks in
	 * his bag): the job goes on, the chest is built, and no plank is lost or made twice (none left, none lying about, his
	 * bag empty, one thing made).
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaH1115hTagMixReload"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaH1115hTagMixReload")
	public void aCarpenterReloadedMidJobStillMakesTheChestFromTheMix(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = asker(helper, new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.SPRUCE_PLANKS, 3), new ItemStack(Items.BIRCH_PLANKS, 2));
		AtomicReference<Villager> carpenter = new AtomicReference<>(QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION));
		AtomicReference<String> bagThen = new AtomicReference<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter.get());
			if (bagThen.get() == null && job != null && job.phase() == CrafterJob.Phase.CRAFTING) {
				bagThen.set(String.valueOf(ModAttachments.BUILDER_BAG.getOrCreate(carpenter.get()).stacks()));
				carpenter.set(QaCrafterFurnitureGameTests.reload(helper.getLevel(), carpenter.get()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(bagThen.get() != null, "the carpenter never got to making the chest");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(counts(chest, PLANKS).values().stream().allMatch(n -> n == 0) && loose(helper, PLANKS) == 0,
				"planks left or lying about after the reload: " + counts(chest, PLANKS) + " (bag at the reload: " + bagThen.get() + ")");
			helper.assertTrue(chest.countItem(Items.CHEST) == 0 && loose(helper, Items.CHEST) == 0, "a second chest made after the reload");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter.get()).isEmpty(), "still in the carpenter's bag: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter.get()).stacks());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0) == 1, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0));
		});
	}
}
