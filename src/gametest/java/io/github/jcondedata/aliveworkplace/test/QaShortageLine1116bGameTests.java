package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1116-1238), written from the specs of B1305 and B1311 and crafter.md, not from the fixes: "A block packed
 * from 9 of one thing names that thing: short of 27 Wheat to make 3 Hay Bales, and with 20 wheat the third bale short of
 * 7 Wheat" (B1305), for every 9-to-1 pair, from both sides of the one-short boundary; iron and gold blocks still name
 * ingots, never nuggets; "Logs of every wood at hand count: an oak and a spruce log for 2 chests read short of 2 Oak Logs,
 * and with a birch log too short of 1 Oak Log" (B1311), as the logs come in one by one, and with planks at hand.
 */
public class QaShortageLine1116bGameTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/** Every vanilla 9-to-1 storage block whose small item is only unpacked from it, with the small item. */
	private static final Item[][] PAIRS = {
		{Items.HAY_BLOCK, Items.WHEAT}, {Items.SLIME_BLOCK, Items.SLIME_BALL}, {Items.DRIED_KELP_BLOCK, Items.DRIED_KELP},
		{Items.COAL_BLOCK, Items.COAL}, {Items.LAPIS_BLOCK, Items.LAPIS_LAZULI}, {Items.EMERALD_BLOCK, Items.EMERALD},
		{Items.DIAMOND_BLOCK, Items.DIAMOND}, {Items.REDSTONE_BLOCK, Items.REDSTONE}, {Items.RAW_IRON_BLOCK, Items.RAW_IRON},
		{Items.RAW_GOLD_BLOCK, Items.RAW_GOLD}, {Items.RAW_COPPER_BLOCK, Items.RAW_COPPER}};

	/**
	 * From nothing, from too little, one short, exactly enough, more than enough: each pair names its small item with the
	 * right count. One plan a tick, so the tick's planning budget (Crafting.TICK_TRIALS) never decides the answer.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaShortLine1116bPairs"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaShortLine1116bPairs")
	public void everyStoragePairNamesItsSmallItemAtEachBoundary(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<Runnable> steps = new ArrayList<>();
		for (Item[] pair : PAIRS) {
			Item block = pair[0];
			Item small = pair[1];
			String b = id(block);
			steps.add(() -> expect(helper, wrong, "2 " + b + " from nothing", block, 2, Map.of(), Map.of(small, 18)));
			steps.add(() -> expect(helper, wrong, "2 " + b + " from 10", block, 2, Map.of(small, 10L), Map.of(small, 8)));
			steps.add(() -> expect(helper, wrong, "1 " + b + " from 8 (one short)", block, 1, Map.of(small, 8L), Map.of(small, 1)));
			steps.add(() -> expect(helper, wrong, "1 " + b + " from 9 (exactly)", block, 1, Map.of(small, 9L), Map.of()));
			steps.add(() -> expect(helper, wrong, "2 " + b + " from 17 (one short)", block, 2, Map.of(small, 17L), Map.of(small, 1)));
			steps.add(() -> expect(helper, wrong, "2 " + b + " from 30 (more than enough)", block, 2, Map.of(small, 30L), Map.of()));
			steps.add(() -> {
				if (Crafting.chain(helper.getLevel(), Crafting.Kind.CRAFTING, block, 1, Map.of(small, 9L)) == null) {
					wrong.add("no plan for 1 " + b + " from 9 " + id(small) + spent(helper));
				}
			});
		}
		steps(helper, steps, () -> helper.assertTrue(wrong.isEmpty(), "storage pairs: " + wrong));
	}

	/** The sentence a player reads, with its real plurals, for a few pairs. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaShortLine1116bWords"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaShortLine1116bWords")
	public void storageLinesReadAsThePlayerIsTold(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		steps(helper, List.of(
			() -> line(helper, wrong, Items.HAY_BLOCK, 1, Map.of(Items.WHEAT, 8L), "short of 1 Wheat to make 1 Hay Bale"),
			() -> line(helper, wrong, Items.HAY_BLOCK, 3, Map.of(), "short of 27 Wheat to make 3 Hay Bales"),
			() -> line(helper, wrong, Items.SLIME_BLOCK, 1, Map.of(Items.SLIME_BALL, 4L), "short of 5 Slimeballs to make 1 Slime Block"),
			() -> line(helper, wrong, Items.EMERALD_BLOCK, 1, Map.of(), "short of 9 Emeralds to make 1 Block of Emerald"),
			() -> line(helper, wrong, Items.COAL_BLOCK, 3, Map.of(Items.COAL, 5L), "short of 22 Coal to make 3 Blocks of Coal"),
			() -> line(helper, wrong, Items.DIAMOND_BLOCK, 1, Map.of(Items.DIAMOND, 7L), "short of 2 Diamonds to make 1 Block of Diamond"),
			() -> line(helper, wrong, Items.IRON_BLOCK, 1, Map.of(), "short of 9 Iron Ingots to make 1 Block of Iron")),
			() -> helper.assertTrue(wrong.isEmpty(), "lines: " + wrong));
	}

	/**
	 * Iron and gold blocks are packed from ingots, and ingots are themselves a storage of nuggets (B1305's fix keeps that):
	 * the line names ingots, never 81 nuggets; one ingot short reads one ingot; nuggets in the chest still turn into the
	 * missing ingot; ingots from nothing never name the iron block.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaShortLine1116bIngots"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaShortLine1116bIngots")
	public void metalBlocksStillNameIngotsNotNuggets(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		steps(helper, List.of(
			() -> expect(helper, wrong, "iron block from nothing", Items.IRON_BLOCK, 1, Map.of(), Map.of(Items.IRON_INGOT, 9)),
			() -> expect(helper, wrong, "iron block from 8 ingots", Items.IRON_BLOCK, 1, Map.of(Items.IRON_INGOT, 8L), Map.of(Items.IRON_INGOT, 1)),
			() -> expect(helper, wrong, "iron block from 9 ingots", Items.IRON_BLOCK, 1, Map.of(Items.IRON_INGOT, 9L), Map.of()),
			() -> expect(helper, wrong, "2 gold blocks from nothing", Items.GOLD_BLOCK, 2, Map.of(), Map.of(Items.GOLD_INGOT, 18)),
			() -> expect(helper, wrong, "iron block from 8 ingots and 9 nuggets", Items.IRON_BLOCK, 1, Map.of(Items.IRON_INGOT, 8L, Items.IRON_NUGGET, 9L),
				Map.of()),
			() -> {
				Map<Item, Integer> fromNothing = lack(helper.getLevel(), Items.IRON_INGOT, 2, Map.of());
				if (fromNothing.containsKey(Items.IRON_BLOCK)) {
					wrong.add("2 iron ingots from nothing name the iron block: " + names(fromNothing) + spent(helper));
				}
			}),
			() -> helper.assertTrue(wrong.isEmpty(), "metal blocks: " + wrong));
	}

	/**
	 * B1311 from both sides: 4 chests (32 planks, 8 logs) as logs of eight woods come in one at a time, the logs short
	 * drop by one each time, named as logs, to none at all with all eight.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaShortLine1116bLogsDrop"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaShortLine1116bLogsDrop")
	public void logsShortDropOneByOneAsEveryWoodComesIn(GameTestHelper helper) {
		Item[] woods = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG,
			Items.CHERRY_LOG};
		List<String> wrong = new ArrayList<>();
		List<Runnable> steps = new ArrayList<>();
		for (int k = 0; k <= woods.length; k++) {
			Map<Item, Long> chest = new LinkedHashMap<>();
			for (int i = 0; i < k; i++) {
				chest.put(woods[i], 1L);
			}
			int want = woods.length - k;
			int woodsIn = k;
			steps.add(() -> {
				Map<Item, Integer> lack = lack(helper.getLevel(), Items.CHEST, 4, chest);
				if (sum(lack) != want || !allLogs(lack)) {
					wrong.add(woodsIn + " woods: " + names(lack) + ", expected " + want + " logs" + spent(helper));
				}
			});
		}
		steps.add(() -> {
			Map<Item, Integer> six = lack(helper.getLevel(), Items.CHEST, 4, Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L));
			String line = JobShortages.lackLine(six, 4, Items.CHEST).getString();
			if (!line.matches("short of 6 [A-Z][a-z]+( [A-Z][a-z]+)? Logs to make 4 Chests")) {
				wrong.add("2 woods for 4 chests read '" + line + "', expected 'short of 6 … Logs to make 4 Chests'" + spent(helper));
			}
		});
		steps.add(() -> {
			Map<Item, Integer> uneven = lack(helper.getLevel(), Items.CHEST, 4, Map.of(Items.OAK_LOG, 2L, Items.SPRUCE_LOG, 1L));
			if (sum(uneven) != 5 || !allLogs(uneven)) {
				wrong.add("2 oak and 1 spruce for 4 chests: " + names(uneven) + ", expected 5 logs" + spent(helper));
			}
		});
		steps(helper, steps, () -> helper.assertTrue(wrong.isEmpty(), "mixed logs: " + wrong));
	}

	/**
	 * Planks and logs together: planks of one wood and logs of two others, exactly enough, leave nothing short; oak planks
	 * with oak logs one log short read 1 log; planks of two woods with no log at hand name the planks (crafter.md: the
	 * line goes further down only when that uses something that's there).
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaShortLine1116bPlanks"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaShortLine1116bPlanks")
	public void plankAndLogsCountTogetherWhereTheyAgree(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		steps(helper, List.of(
			() -> expect(helper, wrong, "8 spruce planks, an oak and a birch log for 2 chests", Items.CHEST, 2,
				Map.of(Items.SPRUCE_PLANKS, 8L, Items.OAK_LOG, 1L, Items.BIRCH_LOG, 1L), Map.of()),
			() -> expect(helper, wrong, "4 oak planks and 2 oak logs for 2 chests", Items.CHEST, 2, Map.of(Items.OAK_PLANKS, 4L, Items.OAK_LOG, 2L),
				Map.of(Items.OAK_LOG, 1)),
			() -> {
				Map<Item, Integer> planks = lack(helper.getLevel(), Items.CHEST, 2, Map.of(Items.OAK_PLANKS, 4L, Items.SPRUCE_PLANKS, 4L));
				if (sum(planks) != 8 || !planks.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.PLANKS))) {
					wrong.add("4 oak and 4 spruce planks for 2 chests lack " + names(planks) + ", expected 8 planks" + spent(helper));
				}
			}),
			() -> helper.assertTrue(wrong.isEmpty(), "planks and logs: " + wrong));
	}

	/**
	 * Through the player's entry point, the one-short boundary from below: 8 wheat for a row of 3 hay bales and a
	 * Carpenter. Not one bale can be made, so nothing is taken and the line names the 19 wheat short for all three.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaShortLine1116bHay8"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortLine1116bHay8")
	public void eightWheatMakeNoBaleAndNameNineteen(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.HAY_BLOCK, new ItemStack(Items.WHEAT, 8));
		Villager carpenter = carpenter(helper);
		helper.runAfterDelay(1200, () -> {
			String text = line(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue("Can't be made yet: short of 19 Wheat to make 3 Hay Bales".equals(text), "the builder's line: '" + text
				+ "', expected 'Can't be made yet: short of 19 Wheat to make 3 Hay Bales'");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0 && chest.countItem(Items.WHEAT) == 8,
				"8 wheat can't make a bale: the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + ", " + chest.countItem(Items.WHEAT)
					+ " wheat left");
			helper.succeed();
		});
	}

	/** From the other side: 27 wheat, exactly enough, and the Carpenter bales all three; no shortage line is left. */
	//$ gametest_ticks_batch AREA '3600' '"qaShortLine1116bHay27"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaShortLine1116bHay27")
	public void exactlyEnoughWheatIsBaledAndNoLineIsLeft(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.HAY_BLOCK, new ItemStack(Items.WHEAT, 27));
		Villager carpenter = carpenter(helper);
		helper.runAfterDelay(1200, () -> {
			String text = line(helper, a);
			helper.assertTrue(text == null || !text.contains("short of"), "27 wheat for 3 bales, yet the builder's line: '" + text + "'");
		});
		helper.succeedWhen(() -> {
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0);
			helper.assertTrue(made >= 3, "the Carpenter baled " + made + " of 3 from 27 wheat (line: '" + line(helper, a) + "')");
			String text = line(helper, a);
			helper.assertTrue(text == null || !text.contains("short of"), "all baled, yet the builder's line: '" + text + "'");
		});
	}

	/**
	 * Through the player's entry point: an oak, a spruce and a birch log (12 planks) for a row of 3 chests (24). One
	 * chest is made, and the other two read short of 3 logs (12 planks), counting every wood, never 4 or more.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaShortLine1116bChests"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaShortLine1116bChests")
	public void threeWoodsLogsMakeOneChestAndNameThreeLogsForTheRest(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.CHEST, new ItemStack(Items.OAK_LOG), new ItemStack(Items.SPRUCE_LOG),
			new ItemStack(Items.BIRCH_LOG));
		Villager carpenter = carpenter(helper);
		helper.runAfterDelay(2800, () -> {
			String text = line(helper, a);
			helper.assertTrue(text != null && text.matches("Can't be made yet: short of 3 [A-Z][a-z]+( [A-Z][a-z]+)? Logs to make 2 Chests"),
				"the builder's line: '" + text + "' (the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0)
					+ "), expected 'Can't be made yet: short of 3 … Logs to make 2 Chests'");
			helper.succeed();
		});
	}

	private static Villager carpenter(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return carpenter;
	}

	private static String line(GameTestHelper helper, QaJobChainsGameTests.Asker a) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		if (live == null) {
			return null;
		}
		Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
		return line == null ? null : line.getString();
	}

	private static Map<Item, Integer> lack(ServerLevel level, Item target, int count, Map<Item, Long> usable) {
		return Crafting.lacking(level, Crafting.Kind.CRAFTING, target, count, usable);
	}

	private static void line(GameTestHelper helper, List<String> wrong, Item target, int count, Map<Item, Long> usable, String want) {
		String got = JobShortages.lackLine(lack(helper.getLevel(), target, count, usable), count, target).getString();
		if (!want.equals(got)) {
			wrong.add("'" + got + "', expected '" + want + "'" + spent(helper));
		}
	}

	/** Each step on a tick of its own (the tick's planning budget is fresh), then {@code end}, then success. */
	private static void steps(GameTestHelper helper, List<Runnable> steps, Runnable end) {
		for (int i = 0; i < steps.size(); i++) {
			Runnable step = steps.get(i);
			helper.runAfterDelay(1 + 2L * i, () -> {
				helper.assertTrue(!Crafting.planningSpent(helper.getLevel()), "the tick's planning was spent before this test's plan");
				step.run();
			});
		}
		helper.runAfterDelay(2 + 2L * steps.size(), () -> {
			end.run();
			helper.succeed();
		});
	}

	/** A note when the tick's planning ran out during the plan just made (its answer then means nothing). */
	private static String spent(GameTestHelper helper) {
		return Crafting.planningSpent(helper.getLevel()) ? " (the tick's planning ran out)" : "";
	}

	private static boolean allLogs(Map<Item, Integer> lack) {
		return lack.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS));
	}

	private static int sum(Map<Item, Integer> m) {
		return m.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	private static String names(Map<Item, Integer> lack) {
		StringBuilder sb = new StringBuilder("{");
		lack.forEach((k, v) -> sb.append(id(k)).append('=').append(v).append(' '));
		return sb.append('}').toString();
	}

	private static void expect(GameTestHelper helper, List<String> wrong, String what, Item target, int count, Map<Item, Long> usable,
		Map<Item, Integer> want) {
		Map<Item, Integer> got = lack(helper.getLevel(), target, count, usable);
		if (!got.equals(want)) {
			wrong.add(what + " lacks " + names(got) + ", expected " + names(want) + spent(helper));
		}
	}
}
