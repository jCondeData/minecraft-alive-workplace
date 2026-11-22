package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane c, 2026-11-22), B1378's neighbour, with Chipped and Carved Wood (not registered: fails on main). Three planks
 * of a mix whose fourth makes Minecraft's crafting table at the game's own table (2 oak and 1 acacia; 2 Chipped acacia
 * mosaic and 1 oak; oak, spruce and crimson) are one plank short, but the planner names 2-3 Oak Planks: it counts
 * only the oak planks already there toward the plain oak recipe and ignores the other planks the mixed recipe takes. So
 * the builder's line overstates what's missing. Expected: one thing (one plank, or one log) short.
 */
public class QaMixedPlanks1122cBugCompatTests implements FabricGameTest {
	/** Three mixed planks lack one thing, not two or three Oak Planks. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaMixedPlanks1122cBugThree"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaMixedPlanks1122cBugThree")
	public void threeMixedPlanksAreOnePlankShortNotTwoOrThreeOak(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			List<Item> kinds = QaMixedPlanks1122cCompatTests.kinds();
			Item chipped = kinds.get(kinds.size() - 1);
			List<Item[]> threes = List.of(
				new Item[] {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.ACACIA_PLANKS},
				new Item[] {chipped, chipped, Items.OAK_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.CRIMSON_PLANKS});
			List<String> wrong = new ArrayList<>();
			for (Item[] three : threes) {
				Map<Item, Long> chest = new LinkedHashMap<>();
				for (Item i : three) {
					chest.merge(i, 1L, Long::sum);
				}
				Crafting.freshPlanning(level);
				Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, chest);
				int total = lack.values().stream().mapToInt(Integer::intValue).sum();
				if (total != 1) {
					wrong.add(QaMixedPlanks1122cCompatTests.describe(chest) + " lacks " + QaMixedPlanks1122cCompatTests.describe(lack));
				}
			}
			helper.assertTrue(wrong.isEmpty(), "three planks are one plank short, but: " + wrong);
			helper.succeed();
		});
	}
}
