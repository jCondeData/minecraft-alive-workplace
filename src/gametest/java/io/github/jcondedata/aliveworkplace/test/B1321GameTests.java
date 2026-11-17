package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.CrafterWork;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * B1321: a level's crafters share {@link Crafting#TICK_TRIALS} recipes a tick. A plan of what's short that the tick's
 * planning ran out under was still read: what it had worked out so far (the planks, not the logs they're sawn from), or,
 * for a job that asks twice (the Mason's stonecutter, then his kiln), the half that was asked in time. Now it says
 * nothing, the tick's planning reads as used up, and the crafter asks again next time. Each test in a batch of its own,
 * starting from a fresh tick's planning ({@link FreshPlanning#fresh}): the budget is what they test.
 */
public class B1321GameTests {
	private static final Map<Item, Long> TWO_LOGS = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L);

	private static int sum(Map<Item, Integer> m) {
		return m.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Uses the tick's planning up to its last {@code left} recipes, with plans of one recipe each (planks from a log). */
	private static void leave(GameTestHelper helper, int left) {
		ServerLevel level = helper.getLevel();
		FreshPlanning.fresh(helper);
		while (Crafting.planningLeft(level) > left) {
			helper.assertTrue(Crafting.plan(level, Crafting.Kind.CRAFTING, Items.OAK_PLANKS, 1, Map.of(Items.OAK_LOG, 1L)) != null,
				"no planks planned from a log with " + Crafting.planningLeft(level) + " recipes left in the tick");
		}
		helper.assertTrue(Crafting.planningLeft(level) == left, "the tick has " + Crafting.planningLeft(level) + " recipes left, not the " + left + " meant");
	}

	/** The numbers of recipes to leave a plan that needs {@code cost}: the fewest, a few in between and one too few. */
	private static Set<Integer> tooFew(int cost) {
		Set<Integer> out = new LinkedHashSet<>();
		for (int left : List.of(1, 2, 3, 5, 8, cost / 4, cost / 2, cost - 2, cost - 1)) {
			if (left >= 1 && left < cost) {
				out.add(left);
			}
		}
		return out;
	}

	/**
	 * Two chests from an oak and a spruce log are short of 2 logs. Asked with fewer recipes left in the tick than that
	 * takes to work out, the answer is nothing at all, and the tick's planning reads as used up; the next tick it's 2
	 * logs again.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"b1321Cut"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "b1321Cut")
	public void whatsShortIsNeverReadFromAPlanTheTickCutShort(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FreshPlanning.fresh(helper);
		long before = Crafting.trials();
		Map<Item, Integer> right = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, TWO_LOGS);
		int cost = (int) (Crafting.trials() - before);
		helper.assertTrue(sum(right) == 2 && right.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS)),
			"with the whole tick's planning, 2 chests from 2 logs lack " + right + ", expected 2 logs");
		helper.assertTrue(cost >= 3 && cost < Crafting.TICK_TRIALS, "working that out tried " + cost + " recipes");
		for (int left : tooFew(cost)) {
			leave(helper, left);
			Map<Item, Integer> got = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, TWO_LOGS);
			helper.assertTrue(got.isEmpty(), "with " + left + " of the " + cost + " recipes it takes left in the tick, the line read \""
				+ JobShortages.lackLine(got, 2, Items.CHEST).getString() + "\"");
			helper.assertTrue(Crafting.planningSpent(level), "cut short with " + left + " recipes left, but the tick's planning doesn't read as used up");
		}
		// exactly enough left is enough
		leave(helper, cost);
		Map<Item, Integer> just = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, TWO_LOGS);
		helper.assertTrue(just.equals(right), "with exactly the " + cost + " recipes it takes left, it lacks " + just + ", expected " + right);
		leave(helper, 1);
		helper.assertTrue(Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, TWO_LOGS).isEmpty(), "read with one recipe left");
		helper.runAfterDelay(1, () -> {
			FreshPlanning.fresh(helper); // this test's tick, whoever else planned in it
			Map<Item, Integer> next = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, TWO_LOGS);
			helper.assertTrue(next.equals(right), "the next tick it lacks " + next + ", expected " + right);
			helper.succeed();
		});
	}

	/** A job that asks twice for what's short, as the Mason does (his stonecutter's chain, then his kiln's). */
	private static final class AsksTwice extends CrafterWork {
		AsksTwice() {
			super(Crafting.Kind.CRAFTING, false);
		}

		@Override
		protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
			Map<Item, Integer> lack = new LinkedHashMap<>(Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.OAK_PLANKS, 1, Map.of()));
			Crafting.lacking(level, Crafting.Kind.CRAFTING, item, count, usable).forEach((k, n) -> lack.merge(k, n, Integer::sum));
			return lack;
		}
	}

	/**
	 * A crafter's line of what's short ({@link CrafterWork#lackNow}): right with the tick's planning to itself; nothing
	 * once the planning is used up; and nothing when it runs out between a job's two plans, where the first alone would
	 * read "short of 1 Oak Log" for 2 chests.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"b1321Job"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "b1321Job")
	public void aCraftersLineWaitsWhenTheTicksPlanningRunsOutHalfWay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CrafterWork carpenter = new CrafterWork(Crafting.Kind.CRAFTING, false);
		FreshPlanning.fresh(helper);
		Map<Item, Integer> right = carpenter.lackNow(level, Items.CHEST, 2, TWO_LOGS);
		helper.assertTrue(sum(right) == 2 && right.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS)),
			"a crafter with the whole tick's planning: 2 chests from 2 logs lack " + right + ", expected 2 logs");
		leave(helper, 0);
		helper.assertTrue(carpenter.lackNow(level, Items.CHEST, 2, TWO_LOGS).isEmpty(), "a line with the tick's planning used up");

		CrafterWork twice = new AsksTwice();
		FreshPlanning.fresh(helper);
		long before = Crafting.trials();
		Map<Item, Integer> first = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.OAK_PLANKS, 1, Map.of());
		int firstCost = (int) (Crafting.trials() - before);
		helper.assertTrue(sum(first) == 1 && firstCost >= 1, "planks from nothing lack " + first + " after " + firstCost + " recipes");
		FreshPlanning.fresh(helper);
		Map<Item, Integer> both = twice.lackNow(level, Items.CHEST, 2, TWO_LOGS);
		helper.assertTrue(sum(both) == 3, "asked twice with the whole tick's planning, it lacks " + both + ", expected 3 logs");
		// the first plan fits exactly, the second finds nothing left; then the second cut short part-way
		for (int left : List.of(firstCost, firstCost + 1, firstCost + 2)) {
			leave(helper, left);
			Map<Item, Integer> got = twice.lackNow(level, Items.CHEST, 2, TWO_LOGS);
			helper.assertTrue(got.isEmpty(), "with " + left + " recipes left in the tick, the first plan's " + firstCost
				+ " and little for the second, the line read \"" + JobShortages.lackLine(got, 2, Items.CHEST).getString() + "\"");
		}
		helper.runAfterDelay(1, () -> {
			FreshPlanning.fresh(helper); // this test's tick, whoever else planned in it
			Map<Item, Integer> next = twice.lackNow(level, Items.CHEST, 2, TWO_LOGS);
			helper.assertTrue(next.equals(both), "the next tick it lacks " + next + ", expected " + both);
			helper.succeed();
		});
	}
}
