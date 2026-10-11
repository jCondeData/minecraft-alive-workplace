package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * B101: a crafter's first look at the recipes went through all of a modpack's recipes in one tick (400 ms on the pack).
 * The crafters' recipe index is now built when the server starts and after every {@code /reload}, so no lookup in a
 * tick goes through them; it gives the same answers as the old lookup, and a new set of recipes gets its own index.
 */
public class RecipeIndexGameTests implements FabricGameTest {
	/** The items an ingredient gave the planner before B101 (the old code, kept here as the reference). */
	private static List<Item> oldOptions(Ingredient ingredient) {
		List<Item> options = new ArrayList<>();
		for (ItemStack option : Recipes.options(ingredient)) {
			if (option.getComponentsPatch().isEmpty() && !options.contains(option.getItem())) {
				options.add(option.getItem());
			}
		}
		return options;
	}

	/** Every kind is indexed before any crafter looks, and a chef's or a tinkerer's whole first round of plans scans nothing. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"b101_index"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "b101_index")
	public void b101RecipesAreIndexedBeforeAnyCrafterLooks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager manager = Recipes.manager(level);
		for (Crafting.Kind kind : Crafting.Kind.values()) {
			helper.assertTrue(Crafting.indexed(manager, kind), "the " + kind + " recipes weren't indexed when the server started");
		}
		int before = Crafting.scans();
		helper.assertTrue(Crafting.plan(level, Crafting.Kind.KITCHEN, Items.BREAD, 1, Map.of(Items.WHEAT, 3L)) != null, "no plan for bread from wheat");
		helper.assertTrue(Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.HOPPER, 1, Map.of(Items.RAW_IRON, 5L, Items.OAK_PLANKS, 8L)) != null,
			"no plan for a hopper from raw iron and planks");
		// A chef with empty chests tries every dish on the menu, down two steps (the B101 tick), and with some stock.
		for (Item dish : Chefs.menu(level)) {
			Crafting.plan(level, Crafting.Kind.KITCHEN, dish, 4, Map.of());
			Crafting.plan(level, Crafting.Kind.KITCHEN, dish, 4, Map.of(Items.WHEAT, 64L, Items.BEEF, 16L, Items.SUGAR, 16L, Items.EGG, 4L));
		}
		Crafting.plan(level, Crafting.Kind.KILN, Items.BRICKS, 1, Map.of(Items.CLAY_BALL, 4L));
		Crafting.plan(level, Crafting.Kind.STONECUTTING, Items.STONE_BRICK_STAIRS, 4, Map.of(Items.STONE, 4L));
		Crafting.plan(level, Crafting.Kind.CRAFTING, Items.OAK_FENCE, 3, Map.of(Items.OAK_LOG, 4L));
		helper.assertTrue(Crafting.scans() == before,
			"looking up recipes went through recipes or ingredients again " + (Crafting.scans() - before) + " times after the server had started");
		helper.succeed();
	}

	/** The index gives every recipe's ingredients exactly the items the old lookup gave, in the same order. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void b101TheIndexGivesTheSameOptionsAsBefore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager manager = Recipes.manager(level);
		int checked = 0;
		List<RecipeHolder<?>> all = new ArrayList<>();
		all.addAll(Recipes.all(manager, RecipeType.CRAFTING));
		all.addAll(Recipes.all(manager, RecipeType.SMOKING));
		all.addAll(Recipes.all(manager, RecipeType.BLASTING));
		all.addAll(Recipes.all(manager, RecipeType.SMELTING));
		all.addAll(Recipes.all(manager, RecipeType.STONECUTTING));
		for (RecipeHolder<?> holder : all) {
			for (Ingredient ingredient : Recipes.ingredients(holder.value())) {
				List<Item> now = Crafting.options(level, ingredient);
				List<Item> was = oldOptions(ingredient);
				helper.assertTrue(now.equals(was), Recipes.id(holder) + ": the index gives " + now + ", the old lookup " + was);
				checked++;
			}
		}
		helper.assertTrue(checked > 1000, "only " + checked + " ingredients checked");
		// And the recipes by result are the same as going through them by hand.
		List<RecipeHolder<?>> hoppers = new ArrayList<>();
		for (RecipeHolder<?> h : Recipes.all(manager, RecipeType.CRAFTING)) {
			if (Recipes.result(h.value(), level.registryAccess()).is(Items.HOPPER)) {
				hoppers.add(h);
			}
		}
		helper.assertTrue(!hoppers.isEmpty() && Crafting.recipesFor(manager, level.registryAccess(), Crafting.Kind.CRAFTING, Items.HOPPER).equals(hoppers),
			"the hopper's recipes by result differ from the recipe list's");
		helper.succeed();
	}

	/** A new set of recipes (what a reload makes) gets its own index, looked through once, and never the old one's answers. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void b101NewRecipesGetTheirOwnIndex(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager live = Recipes.manager(level);
		List<RecipeHolder<?>> hoppers = new ArrayList<>(Crafting.recipesFor(live, level.registryAccess(), Crafting.Kind.CRAFTING, Items.HOPPER));
		helper.assertTrue(!hoppers.isEmpty(), "no hopper recipe in the game");
		RecipeManager fresh = new RecipeManager(level.registryAccess());
		fresh.replaceRecipes(hoppers);
		helper.assertTrue(!Crafting.indexed(fresh, Crafting.Kind.CRAFTING), "a brand-new recipe manager was already indexed");
		int before = Crafting.scans();
		helper.assertTrue(Crafting.recipesFor(fresh, level.registryAccess(), Crafting.Kind.CRAFTING, Items.HOPPER).equals(hoppers),
			"the new recipes' index doesn't give their hopper recipe");
		helper.assertTrue(Crafting.recipesFor(fresh, level.registryAccess(), Crafting.Kind.CRAFTING, Items.CHEST).isEmpty(),
			"the new recipes' index gave the old recipes' chest");
		helper.assertTrue(Crafting.scans() == before + 1, "the new recipes were gone through " + (Crafting.scans() - before) + " times, not once");
		helper.assertTrue(!Crafting.recipesFor(live, level.registryAccess(), Crafting.Kind.CRAFTING, Items.CHEST).isEmpty(),
			"the live recipes lost their chest");
		helper.succeed();
	}

	/** A real {@code /reload}: the server's new recipes are indexed as soon as it finishes, before any crafter asks. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '1200' '"b101_reload"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "b101_reload")
	public void b101AReloadIndexesTheNewRecipes(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RecipeManager old = Recipes.manager(server);
		server.reloadResources(server.getPackRepository().getSelectedIds());
		helper.succeedWhen(() -> {
			RecipeManager now = Recipes.manager(server);
			helper.assertTrue(now != old, "the reload hasn't finished");
			for (Crafting.Kind kind : Crafting.Kind.values()) {
				helper.assertTrue(Crafting.indexed(now, kind), "the reloaded " + kind + " recipes weren't indexed");
			}
			helper.assertTrue(!Crafting.recipesFor(now, server.registryAccess(), Crafting.Kind.CRAFTING, Items.HOPPER).isEmpty(),
				"no hopper recipe after the reload");
		});
	}

	/** Everyday plans try only a few recipes, far below a plan's limit, and still come out the same. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"b101_trials"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "b101_trials")
	public void b101EverydayPlansTryFewRecipes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long before = Crafting.trials();
		Crafting.Plan fence = Crafting.plan(level, Crafting.Kind.CRAFTING, Items.OAK_FENCE, 3, Map.of(Items.OAK_LOG, 4L));
		helper.assertTrue(fence != null && fence.count() >= 3 && fence.takes().getOrDefault(Items.OAK_LOG, 0) > 0, "no fence from logs: " + fence);
		long fenceTrials = Crafting.trials() - before;
		helper.assertTrue(fenceTrials > 0 && fenceTrials < 100, "a fence from logs tried " + fenceTrials + " recipes");
		before = Crafting.trials();
		Crafting.Plan hopper = Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.HOPPER, 1, Map.of(Items.RAW_IRON, 5L, Items.OAK_PLANKS, 8L));
		helper.assertTrue(hopper != null && hopper.count() == 1, "no hopper from raw iron and planks");
		long hopperTrials = Crafting.trials() - before;
		helper.assertTrue(hopperTrials < 100, "a hopper tried " + hopperTrials + " recipes");
		// A chef with nothing at all goes through the whole menu: no plan, and no plan over its limit.
		for (Item dish : Chefs.menu(level)) {
			long one = Crafting.trials();
			helper.assertTrue(Crafting.plan(level, Crafting.Kind.KITCHEN, dish, 8, Map.of()) == null, "a plan for " + dish + " from nothing");
			helper.assertTrue(Crafting.trials() - one <= Crafting.PLAN_TRIALS, dish + " tried " + (Crafting.trials() - one) + " recipes");
		}
		helper.succeed();
	}

	/** Once a tick's planning is used up, plans wait for the next tick, and then work as before. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"b101_tick_budget"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "b101_tick_budget")
	public void b101ATicksPlanningIsSharedOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, Long> logs = Map.of(Items.OAK_LOG, 4L);
		long start = Crafting.trials();
		int plans = 0;
		while (Crafting.plan(level, Crafting.Kind.CRAFTING, Items.OAK_FENCE, 3, logs) != null) {
			plans++;
			helper.assertTrue(plans < Crafting.TICK_TRIALS, "planning never ran out in one tick");
		}
		long used = Crafting.trials() - start;
		helper.assertTrue(used <= Crafting.TICK_TRIALS, "one tick tried " + used + " recipes, over " + Crafting.TICK_TRIALS);
		helper.assertTrue(plans > 10, "only " + plans + " fence plans fitted in a tick");
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(Crafting.plan(level, Crafting.Kind.CRAFTING, Items.OAK_FENCE, 3, logs) != null, "no fence plan in the next tick");
			helper.succeed();
		});
	}
}
