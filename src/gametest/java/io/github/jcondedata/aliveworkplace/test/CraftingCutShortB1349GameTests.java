package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * B1349: a tick's planning (Crafting.TICK_TRIALS) is shared by the whole level. With most of it spent by other jobs, what
 * a crafter lacks for two chests from an oak, a spruce and a birch log was cut short part way and read "short of 16 Oak
 * Planks". A shortfall cut short is not planned yet: nothing is said that tick, and the next tick's whole plan names a log.
 */
public class CraftingCutShortB1349GameTests implements FabricGameTest {
	private static final Map<Item, Long> LOGS = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L);

	private static String ids(Map<Item, Integer> lack) {
		return lack.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
	}

	/** Spends the tick's planning, as other jobs' plans would, until about {@code leave} trials are left. */
	private static void spendDownTo(GameTestHelper helper, int leave) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 5000 && Crafting.planningLeft(level) > leave; i++) {
			Crafting.plan(level, Crafting.Kind.CRAFTING, Items.STICK, 4, Map.of(Items.OAK_PLANKS, 2L));
		}
		helper.assertTrue(Crafting.planningLeft(level) <= leave && Crafting.planningLeft(level) > 0,
			"spending the tick's planning left " + Crafting.planningLeft(level) + ", expected 1 to " + leave);
	}

	private static void cutShortThenWhole(GameTestHelper helper, int leave) {
		Leftovers.clear(helper);
		AtomicInteger left = new AtomicInteger();
		AtomicReference<Map<Item, Integer>> cut = new AtomicReference<>();
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(Crafting.planningLeft(level) > leave, "the tick's planning was spent before the test spent it");
			spendDownTo(helper, leave);
			left.set(Crafting.planningLeft(level));
			cut.set(Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, LOGS));
			helper.assertTrue(cut.get().isEmpty(), "with " + left.get() + " trials left in the tick, 2 chests from three woods' logs lack "
				+ ids(cut.get()) + " (\"" + JobShortages.lackLine(cut.get(), 2, Items.CHEST).getString()
				+ "\"): a plan cut short must say nothing yet");
			helper.assertTrue(Crafting.planningSpent(level), "a shortfall cut short by the tick's planning left it unspent");
		});
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(!Crafting.planningSpent(level), "the next tick's planning was spent before the plan");
			long before = Crafting.trials();
			Map<Item, Integer> whole = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, LOGS);
			long cost = Crafting.trials() - before;
			String line = JobShortages.lackLine(whole, 2, Items.CHEST).getString();
			helper.assertTrue(cost > left.get(), "the whole plan tried " + cost + " recipes, no more than the " + left.get()
				+ " left before: the test didn't cut it short");
			helper.assertTrue(whole.size() == 1 && whole.values().iterator().next() == 1
					&& whole.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.LOGS)
					&& line.matches("short of 1 [A-Z][a-z]+ Log to make 2 Chests"),
				"the next tick, 2 chests from three woods' logs lack " + ids(whole) + " (\"" + line + "\"), expected 1 log");
			helper.succeed();
		});
	}

	/** About a hundred trials left, half the whole plan's: nothing said that tick, a log the next. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"b1341CutShort100"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "b1341CutShort100")
	public void aShortfallCutShortByTheTickSaysNothingUntilTheWholePlan(GameTestHelper helper) {
		cutShortThenWhole(helper, 100);
	}

	/** A couple dozen trials left: the same. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"b1341CutShort20"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "b1341CutShort20")
	public void aShortfallWithAlmostNoPlanningLeftSaysNothingUntilTheWholePlan(GameTestHelper helper) {
		cutShortThenWhole(helper, 20);
	}
}
