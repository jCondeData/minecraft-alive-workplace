package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * B1333: planks of one wood beside logs of another were miscounted in the shortage line (4 spruce planks, an oak and a
 * birch log for 2 chests read "short of 3 Oak Logs"; 1 is short). The mix of what the logs at hand make is kept and only
 * the rest goes down to logs, after the planks already there. Each check on a tick of its own (Crafting.TICK_TRIALS).
 */
public class B1333GameTests implements FreshPlanning {
	/** Truly short: the exact line, naming the logs of the wood the chests still hold logs of, and only what's short. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"b1333Lines"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "b1333Lines")
	public void planksAndAnotherWoodsLogsReadOnlyWhatsShort(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> line(helper, Map.of(Items.SPRUCE_PLANKS, 4L, Items.OAK_LOG, 1L, Items.BIRCH_LOG, 1L), 2,
			Map.of(Items.OAK_LOG, 1)));
		helper.runAfterDelay(3, () -> line(helper, Map.of(Items.SPRUCE_PLANKS, 8L, Items.OAK_LOG, 1L), 3, Map.of(Items.OAK_LOG, 3)));
		helper.runAfterDelay(5, () -> line(helper, Map.of(Items.OAK_PLANKS, 4L, Items.SPRUCE_PLANKS, 4L, Items.BIRCH_LOG, 1L), 2,
			Map.of(Items.BIRCH_LOG, 1)));
		// the same wood still reads as before B1333
		helper.runAfterDelay(7, () -> line(helper, Map.of(Items.OAK_PLANKS, 4L, Items.OAK_LOG, 2L), 2, Map.of(Items.OAK_LOG, 1)));
		helper.runAfterDelay(9, () -> {
			helper.assertTrue(JobShortages.lackLine(Map.of(Items.OAK_LOG, 1), 2, Items.CHEST).getString()
				.equals("short of 1 Oak Log to make 2 Chests"), "the line reads: " + JobShortages.lackLine(Map.of(Items.OAK_LOG, 1), 2, Items.CHEST).getString());
			helper.succeed();
		});
	}

	/** Enough in all: a real plan takes every plank and log there, exactly, and nothing is short. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"b1333Plan"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "b1333Plan")
	public void planksAndAnotherWoodsLogsJustEnoughMakeARealPlan(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.SPRUCE_PLANKS, 4L, Items.OAK_LOG, 2L, Items.BIRCH_LOG, 1L);
		helper.runAfterDelay(1, () -> {
			Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, chest);
			helper.assertTrue(plan != null, "no plan for 2 chests from 4 spruce planks, 2 oak logs and a birch log");
			helper.assertTrue(plan.count() >= 2, "the plan makes " + plan.count() + " chests");
			helper.assertTrue(plan.takes().equals(Map.of(Items.SPRUCE_PLANKS, 4, Items.OAK_LOG, 2, Items.BIRCH_LOG, 1)),
				"the plan takes " + plan.takes());
		});
		helper.runAfterDelay(3, () -> {
			Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, 2, chest);
			helper.assertTrue(lack.isEmpty(), "nothing is short, the line says " + lack);
			helper.succeed();
		});
	}

	private static void line(GameTestHelper helper, Map<Item, Long> chest, int chests, Map<Item, Integer> want) {
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.CHEST, chests, chest);
		helper.assertTrue(lack.equals(want), chest + " for " + chests + " chests: '" + JobShortages.lackLine(lack, chests, Items.CHEST).getString()
			+ "' (" + lack + "), expected " + want + (Crafting.planningSpent(helper.getLevel()) ? " (the tick's planning ran out)" : ""));
	}
}
