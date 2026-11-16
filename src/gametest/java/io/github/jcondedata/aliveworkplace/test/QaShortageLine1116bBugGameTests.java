package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane-b-1116-1238), a bug past B1311. crafter.md: "Logs of every wood at hand count" and "planks still to be sawn
 * from the logs at hand read short of 1 Oak Log". With planks of one wood beside logs of another, the count of logs short
 * ignores some of what's there: 2 chests (16 planks) from 4 spruce planks, an oak and a birch log (12) read "short of 3
 * Oak Logs to make 2 Chests" (1 log is short); 4 spruce planks and 2 oak logs read 2 logs (1 short); 4 spruce planks and
 * an oak log read 3 (2 short); 4 oak and 4 spruce planks and a birch log read 3 Birch Logs (2 short). Planks and logs of
 * the same wood count right (4 oak planks and 2 oak logs: 1 log).
 */
public class QaShortageLine1116bBugGameTests {
	/** Each case on a tick of its own, so the tick's planning budget (Crafting.TICK_TRIALS) never decides the answer. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaShortLine1116bPlanksBug"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaShortLine1116bPlanksBug")
	public void planksOfOneWoodAndLogsOfAnotherCountEveryPlankAndLog(GameTestHelper helper) {
		List<Object[]> cases = List.of(
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 4L, Items.OAK_LOG, 1L, Items.BIRCH_LOG, 1L), 1},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 4L, Items.OAK_LOG, 2L), 1},
			new Object[] {Map.of(Items.SPRUCE_PLANKS, 4L, Items.OAK_LOG, 1L), 2},
			new Object[] {Map.of(Items.OAK_PLANKS, 4L, Items.SPRUCE_PLANKS, 4L, Items.BIRCH_LOG, 1L), 2});
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < cases.size(); i++) {
			Object[] c = cases.get(i);
			helper.runAfterDelay(1 + 2L * i, () -> {
				@SuppressWarnings("unchecked")
				Map<Item, Long> chest = (Map<Item, Long>) c[0];
				int want = (Integer) c[1];
				Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, chest);
				String line = JobShortages.lackLine(lack, 2, Items.CHEST).getString();
				int sum = lack.values().stream().mapToInt(Integer::intValue).sum();
				boolean logs = lack.keySet().stream().allMatch(it -> it.builtInRegistryHolder().is(ItemTags.LOGS));
				if (sum != want || !logs) {
					wrong.add(ids(chest) + " for 2 chests read '" + line + "', expected " + want + (want == 1 ? " log" : " logs")
						+ (Crafting.planningSpent(helper.getLevel()) ? " (the tick's planning ran out)" : ""));
				}
			});
		}
		helper.runAfterDelay(2 + 2L * cases.size(), () -> {
			helper.assertTrue(wrong.isEmpty(), "planks and logs of other woods: " + wrong);
			helper.succeed();
		});
	}

	private static String ids(Map<Item, Long> chest) {
		return chest.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).sorted().toList().toString();
	}
}
