package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane C, 2026-11-16), a bug past B1301 (B1311, fixed by Crafting.madeMix). crafter.md: truly short, the line names
 * what's short, counting the whole tag, and "planks still to be sawn from the logs at hand read short of 1 Oak Log". With
 * logs of several woods truly short, the count ignores every wood but the first: an oak and a spruce log (8 planks) for 2
 * chests (16) read "short of 3 Oak Logs to make 2 Chests", as if the spruce log weren't there; 2 logs are short.
 */
public class QaCrafterLogs1116cBugGameTests implements FabricGameTest {
	private static String ids(Map<Item, Integer> lack) {
		return lack.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
	}

	private static int sum(Map<Item, Integer> m) {
		return m.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Two chests (16 planks) from an oak and a spruce log (8) are short of 2 logs, read "short of 2 … Logs to make 2
	 * Chests"; from an oak, a spruce and a birch log (12) short of 1 log. Each plan on a tick of its own, alone in its
	 * batch: a tick's planning is capped at Crafting.TICK_TRIALS for the whole level, and in the default batch hundreds of
	 * tests plan on the same first tick, so the second plan here ran out part way and read "16 Oak Planks" (B1340).
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaCrafterLogs1116cMixed"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaCrafterLogs1116cMixed")
	public void mixedLogsTrulyShortCountEveryWoodsLogs(GameTestHelper helper) {
		Leftovers.clear(helper);
		java.util.concurrent.atomic.AtomicReference<Map<Item, Integer>> two = new java.util.concurrent.atomic.AtomicReference<>(Map.of());
		String[] line = new String[1];
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(!Crafting.planningSpent(helper.getLevel()), "the tick's planning was spent before the first plan");
			two.set(Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L)));
			line[0] = JobShortages.lackLine(two.get(), 2, Items.CHEST).getString();
		});
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!Crafting.planningSpent(helper.getLevel()), "the tick's planning was spent before the second plan");
			Map<Item, Integer> three = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2,
				Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L));
			helper.assertTrue(sum(two.get()) == 2 && two.get().keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS))
					&& line[0].matches("short of 2 [A-Z][a-z]+ Logs to make 2 Chests") && sum(three) == 1,
				"an oak and a spruce log: 2 chests lack " + ids(two.get()) + " (\"" + line[0] + "\"), expected 2 logs; an oak, a spruce and a birch log lack "
					+ ids(three) + ", expected 1 log");
			helper.succeed();
		});
	}
}
