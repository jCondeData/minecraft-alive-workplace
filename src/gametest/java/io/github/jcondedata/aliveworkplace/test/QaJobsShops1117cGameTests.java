package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlock;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1117-1253), written from the bugs' text, not the fixes: B1333 (planks of one wood beside logs of another
 * count every plank and log in the crafters' shortage line), B1349 (a shortfall cut short by the tick's shared planning is
 * never said; one with enough planning left is said at once), B1334 (a stone a player mines never reads "Nobody here
 * makes" beside the worker who makes it) and B1346 (the Shop Counter's welcome line names the server's real rate, read
 * from the config file and clamped to 1-10,000). The CobbleDollar halves are in QaJobsShops1117cCompatTests.
 */
public class QaJobsShops1117cGameTests implements FabricGameTest {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final Pattern LOGS = Pattern.compile("(\\d+) [A-Z][A-Za-z ]*? Logs?\\b");

	private static String ids(Map<Item, ? extends Number> m) {
		return m.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).sorted().toList().toString();
	}

	// --- B1333 ------------------------------------------------------------------------------------------------------------

	/**
	 * Every plank and log of every wood counts, each case on a fresh tick of planning: nothing there, one plank short (with
	 * no logs there, the planks are named),
	 * exactly enough of two woods' planks, planks of two woods short, stripped logs and wood beside planks of a third wood,
	 * just enough from three woods' logs and another's planks, and ten chests at once.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaJobsShops1117cPlanks"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaJobsShops1117cPlanks")
	public void everyWoodsPlanksAndLogsCountFromNothingToTenChests(GameTestHelper helper) {
		List<Object[]> cases = List.of(
			new Object[] {Map.<Item, Long>of(), 1, 2},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 7L), 1, -1},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 4L, Items.BIRCH_PLANKS, 4L), 1, 0},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 3L, Items.BIRCH_PLANKS, 3L), 1, -2},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 4L, Items.STRIPPED_BIRCH_LOG, 1L, Items.OAK_WOOD, 1L), 2, 1},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 4L, Items.OAK_LOG, 1L, Items.BIRCH_LOG, 1L, Items.JUNGLE_LOG, 1L), 2, 0},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 12L, Items.OAK_LOG, 1L, Items.BIRCH_LOG, 1L), 3, 1},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 13L, Items.OAK_LOG, 1L, Items.BIRCH_LOG, 1L), 3, 1},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 11L, Items.OAK_LOG, 1L, Items.BIRCH_LOG, 1L), 3, 2},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 20L, Items.OAK_LOG, 5L, Items.BIRCH_LOG, 5L), 10, 5});
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < cases.size(); i++) {
			Object[] c = cases.get(i);
			helper.runAfterDelay(1 + 2L * i, () -> {
				ServerLevel level = helper.getLevel();
				Crafting.freshPlanning(level);
				@SuppressWarnings("unchecked")
				Map<Item, Long> chest = (Map<Item, Long>) c[0];
				int chests = (Integer) c[1];
				// a negative count: that many planks short, with no logs there to go down to (crafter.md, B409)
				int want = Math.abs((Integer) c[2]);
				net.minecraft.tags.TagKey<Item> kind = (Integer) c[2] < 0 ? ItemTags.PLANKS : ItemTags.LOGS;
				Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, chests, chest);
				int sum = lack.values().stream().mapToInt(Integer::intValue).sum();
				boolean logs = lack.keySet().stream().allMatch(it -> it.builtInRegistryHolder().is(kind));
				String line = lack.isEmpty() ? "(nothing short)" : JobShortages.lackLine(lack, chests, Items.CHEST).getString();
				if (sum != want || !logs) {
					wrong.add(ids(chest) + " for " + chests + " chests: '" + line + "' " + ids(lack) + ", expected " + want + (kind == ItemTags.LOGS ? " log(s)" : " plank(s)")
						+ (Crafting.planningSpent(level) ? " (the tick's planning ran out)" : ""));
				}
				helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa1117c] " + ids(chest) + " x" + chests + " -> " + line));
			});
		}
		helper.runAfterDelay(2 + 2L * cases.size(), () -> {
			helper.assertTrue(wrong.isEmpty(), "planks and logs of several woods: " + wrong);
			helper.succeed();
		});
	}

	/** The line's words: one log for one chest, five logs for ten chests, every noun in its number. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShortageLineReadsInTheSingularAndThePlural(GameTestHelper helper) {
		helper.assertValueEqual(JobShortages.lackLine(Map.of(Items.OAK_LOG, 1), 1, Items.CHEST).getString(), "short of 1 Oak Log to make 1 Chest", "one");
		helper.assertValueEqual(JobShortages.lackLine(Map.of(Items.SPRUCE_LOG, 5), 10, Items.CHEST).getString(), "short of 5 Spruce Logs to make 10 Chests",
			"many");
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of a row of 3 chests (24 planks) with 8 spruce planks, an oak and
	 * a birch log (16) and a Carpenter. Nothing is lost or made from nothing, the build waits, and his line, read every 10
	 * ticks, never names planks as short (B1349: a plan cut short reads planks) and ends naming exactly 2 logs (B1333).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaJobsShops1117cCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaJobsShops1117cCarpenter")
	public void aCarpenterWithPlanksOfOneWoodAndLogsOfTwoOthersNamesTwoLogs(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.CHEST, new ItemStack(Items.SPRUCE_PLANKS, 8), new ItemStack(Items.OAK_LOG, 1),
			new ItemStack(Items.BIRCH_LOG, 1));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		List<String> planks = new ArrayList<>();
		helper.onEachTick(() -> {
			ServerLevel level = helper.getLevel();
			if (level.getGameTime() % 10 != 0 || BuildSiteManager.get(level).get(a.site().id()) == null) {
				return;
			}
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			String text = line == null ? "" : line.getString();
			if (text.contains("Planks")) {
				planks.add(text);
			}
		});
		helper.runAfterDelay(2000, () -> {
			ServerLevel level = helper.getLevel();
			Container chest = helper.getBlockEntity(a.chest());
			int worth = 0;
			StringBuilder seen = new StringBuilder();
			for (Item log : List.of(Items.OAK_LOG, Items.BIRCH_LOG)) {
				int n = chest.countItem(log) + QaCrafterFurnitureGameTests.loose(helper, log) + bag(carpenter, log);
				worth += 4 * n;
				seen.append(n).append(' ').append(BuiltInRegistries.ITEM.getKey(log).getPath()).append(", ");
			}
			for (Item plank : List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS)) {
				int n = chest.countItem(plank) + QaCrafterFurnitureGameTests.loose(helper, plank) + bag(carpenter, plank);
				worth += n;
				seen.append(n).append(' ').append(BuiltInRegistries.ITEM.getKey(plank).getPath()).append(", ");
			}
			int built = 0;
			for (int x = 0; x < 3; x++) {
				if (helper.getBlockState(QaJobChainFixesGameTests.ORIGIN.offset(x, 0, 0)).is(Blocks.CHEST)) {
					built++;
				}
			}
			int chests = chest.countItem(Items.CHEST) + QaCrafterFurnitureGameTests.loose(helper, Items.CHEST) + bag(carpenter, Items.CHEST) + built;
			worth += 8 * chests;
			seen.append(chests).append(" chests (").append(built).append(" built)");
			helper.assertTrue(worth == 16, "planks' worth " + worth + ", expected 16: " + seen);
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) != null, "the build finished 8 planks short: " + seen);
			helper.assertTrue(planks.isEmpty(), "the builder's line named planks short: " + planks.stream().findFirst().orElse("") + " (" + planks.size() + " times)");
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			String text = line == null ? null : line.getString();
			int logs = 0;
			if (text != null) {
				Matcher m = LOGS.matcher(text);
				while (m.find()) {
					logs += Integer.parseInt(m.group(1));
				}
			}
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && logs == 2 && text.contains("Chest"),
				"the builder's line: '" + text + "' (" + logs + " logs, expected 2); " + seen);
			helper.succeed();
		});
	}

	private static int bag(Villager villager, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	// --- B1349 ------------------------------------------------------------------------------------------------------------

	private static final Map<Item, Long> THREE_LOGS = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L);

	/** Spends the tick's planning, as other jobs' plans would, until no more than {@code leave} trials are left. */
	private static void spendDownTo(GameTestHelper helper, int leave) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 5000 && Crafting.planningLeft(level) > leave; i++) {
			Crafting.plan(level, Crafting.Kind.CRAFTING, Items.STICK, 4, Map.of(Items.OAK_PLANKS, 2L));
		}
	}

	/**
	 * Both sides of the edge: the whole plan's cost measured on a fresh tick; then, with a little more than that left,
	 * the answer comes that tick (a log, never "wait"); with a little less, nothing is said and the tick reads spent; and
	 * the tick after, the answer is the whole one again, nothing of the cut-short one kept.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaJobsShops1117cBudgetEdge"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaJobsShops1117cBudgetEdge")
	public void aShortfallIsSaidWithEnoughPlanningLeftAndNotWithJustTooLittle(GameTestHelper helper) {
		Leftovers.clear(helper);
		AtomicInteger cost = new AtomicInteger();
		AtomicReference<Map<Item, Integer>> whole = new AtomicReference<>();
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			Crafting.freshPlanning(level);
			long before = Crafting.trials();
			whole.set(Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, THREE_LOGS));
			cost.set((int) (Crafting.trials() - before));
			helper.assertTrue(whole.get().size() == 1 && whole.get().values().iterator().next() == 1
				&& whole.get().keySet().iterator().next().builtInRegistryHolder().is(ItemTags.LOGS),
				"on a fresh tick 2 chests from three woods' logs lack " + ids(whole.get()) + ", expected 1 log");
			helper.assertTrue(cost.get() > 20 && cost.get() + 60 < Crafting.PLAN_TRIALS, "the whole plan tried " + cost.get() + " recipes: the edge can't be set");
		});
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			Crafting.freshPlanning(level);
			spendDownTo(helper, cost.get() + 40);
			int left = Crafting.planningLeft(level);
			helper.assertTrue(left > cost.get(), "setup: spending left " + left + ", the whole plan needs " + cost.get());
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, THREE_LOGS);
			helper.assertValueEqual(lack, whole.get(), "with " + left + " trials left (the whole plan needs " + cost.get() + ") the answer");
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			Crafting.freshPlanning(level);
			spendDownTo(helper, cost.get() - 10);
			int left = Crafting.planningLeft(level);
			helper.assertTrue(left > 0 && left < cost.get(), "setup: spending left " + left + ", the whole plan needs " + cost.get());
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, THREE_LOGS);
			helper.assertTrue(lack.isEmpty(), "with " + left + " trials left (the whole plan needs " + cost.get() + ") it said " + ids(lack)
				+ " ('" + JobShortages.lackLine(lack, 2, Items.CHEST).getString() + "'): a plan cut short must say nothing");
			helper.assertTrue(Crafting.planningSpent(level), "a plan cut short left the tick's planning unspent (" + Crafting.planningLeft(level) + ")");
		});
		helper.runAfterDelay(7, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(!Crafting.planningSpent(level), "the tick after, planning still reads spent");
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, THREE_LOGS);
			helper.assertValueEqual(lack, whole.get(), "the tick after a cut-short plan, the answer");
			helper.succeed();
		});
	}

	/** A plan with nothing short, cut short by the tick: never a shortage either; the next tick, still nothing short. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaJobsShops1117cBudgetEnough"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaJobsShops1117cBudgetEnough")
	public void enoughLogsCutShortByTheTickNeverReadShort(GameTestHelper helper) {
		Leftovers.clear(helper);
		Map<Item, Long> enough = Map.of(Items.OAK_LOG, 2L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L);
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			Crafting.freshPlanning(level);
			spendDownTo(helper, 15);
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, enough);
			helper.assertTrue(lack.isEmpty(), "16 planks' worth of logs for 2 chests, cut short, said " + ids(lack));
		});
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			Crafting.freshPlanning(level);
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, enough);
			helper.assertTrue(lack.isEmpty(), "16 planks' worth of logs for 2 chests said " + ids(lack));
			Crafting.Plan plan = Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, enough);
			helper.assertTrue(plan != null && plan.count() >= 2, "no whole plan for 2 chests from 4 logs: " + plan);
			helper.succeed();
		});
	}

	// --- B1334 ------------------------------------------------------------------------------------------------------------

	/**
	 * A builder waiting on stones a player mines (andesite, granite, diorite or stone, those the Mason makes) past the 600 ticks "Nobody here makes" waits for:
	 * with no maker near, the line says whom to hire, never "Nobody here makes"; with the maker (a Mason) working near,
	 * never "Nobody here makes" nor "Hire".
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaJobsShops1117cMinedStone"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaJobsShops1117cMinedStone")
	public void aMinedStoneNeverReadsNobodyHereMakesWithOrWithoutItsMaker(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		// the stones a player mines that the Mason makes in this game (gathered, and a recipe of his makes them)
		Map<Item, Integer> missing = new LinkedHashMap<>();
		for (Item stone : List.of(Items.ANDESITE, Items.GRANITE, Items.DIORITE, Items.STONE)) {
			CraftJobs.Job job = CraftJobs.maker(level, stone);
			if (missing.size() < 2 && job != null && job.profession() == VillagerProfession.MASON && CraftJobs.gatherer(stone) != null) {
				missing.put(stone, 3);
			}
		}
		helper.assertTrue(!missing.isEmpty(), "setup: none of andesite, granite, diorite or stone is the Mason's to make here");
		String first = missing.keySet().iterator().next().getDescription().getString();
		UUID alone = UUID.randomUUID();
		UUID beside = UUID.randomUUID();
		BlockPos near = helper.absolutePos(new BlockPos(2, 2, 2));
		AtomicReference<String> without = new AtomicReference<>();
		helper.runAfterDelay(1, () -> {
			JobShortages.line(level, alone, missing, near);
			JobShortages.line(level, beside, missing, near);
		});
		helper.runAfterDelay(650, () -> {
			Component line = JobShortages.line(level, alone, missing, near);
			without.set(line == null ? null : line.getString());
			helper.assertTrue(without.get() != null && without.get().contains("Hire a Mason") && without.get().contains(first)
				&& !without.get().contains("Nobody here makes"),
				"with no Mason near, " + missing + " reads '" + without.get() + "'");
			BlockPos station = new BlockPos(6, 2, 6);
			helper.setBlock(station, Blocks.STONECUTTER);
			Villager mason = helper.spawn(EntityType.VILLAGER, station.south());
			Jobs.employ(level, mason, helper.absolutePos(station), PoiTypes.MASON, VillagerProfession.MASON);
		});
		helper.runAfterDelay(660, () -> {
			Component line = JobShortages.line(level, beside, missing, near);
			String text = line == null ? null : line.getString();
			helper.assertTrue(text == null || !(text.contains("Nobody here makes") || text.contains("Hire")),
				"with a Mason working near, the line reads '" + text + "' (without him: '" + without.get() + "')");
			JobShortages.forget(alone);
			JobShortages.forget(beside);
			helper.succeed();
		});
	}

	// --- B1346 (without CobbleDollars) ------------------------------------------------------------------------------------

	/**
	 * The Shop Counter's welcome line names the rate read from the config file: 2, 10,000 (the most), 0 clamped to 1 and
	 * 50,000 clamped to 10,000; never a leftover "%s" or the old fixed 100. The default config brings 100 back.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaJobsShops1117cRate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaJobsShops1117cRate")
	public void theShopCounterNamesTheRateFromTheConfigFileClamped(GameTestHelper helper) {
		Map<Integer, String> cases = new LinkedHashMap<>();
		cases.put(2, "2");
		cases.put(10_000, "10,000");
		cases.put(0, "1");
		cases.put(50_000, "10,000");
		List<String> wrong = new ArrayList<>();
		try {
			for (Map.Entry<Integer, String> c : cases.entrySet()) {
				WorkplaceConfig.parse("{\"configVersion\": 5, \"dollarsPerEmerald\": " + c.getKey() + "}").apply();
				String text = ShopCounterBlock.placedMessage().getString();
				String want = "With CobbleDollars installed, emerald prices are paid in CobbleDollars (" + c.getValue() + " per emerald) straight to you.";
				if (!text.contains(want) || text.contains("%")) {
					wrong.add("dollarsPerEmerald " + c.getKey() + ": '" + text + "'");
				}
				// without CobbleDollars the treasury asks for emeralds, whatever the rate
				String hint = RealmTreasury.giveHint().getString();
				if (!hint.equals("Click with emeralds to give one; shift-click to give the stack")) {
					wrong.add("dollarsPerEmerald " + c.getKey() + " treasury hint: '" + hint + "'");
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(Money.DOLLARS_PER_EMERALD == 100 && ShopCounterBlock.placedMessage().getString().contains("(100 per emerald)"),
			"the default config doesn't bring the rate back to 100: " + Money.DOLLARS_PER_EMERALD);
		helper.assertTrue(wrong.isEmpty(), "the counter's welcome line: " + wrong);
		helper.succeed();
	}
}
