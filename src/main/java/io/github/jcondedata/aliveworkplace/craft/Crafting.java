package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.mc.Recipes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * Works out how to make something from what's in stock with the game's own recipes (so modded recipes count too):
 * crafting-table recipes for carpenters, stonecutter recipes for masons. An ingredient that's short can itself be made,
 * up to two steps down (fences from sticks and planks, from logs). Only plain items are used, and only as many as {@code usable} allows.
 */
public final class Crafting {
	/** Which recipes a crafter uses. */
	public enum Kind {
		/** The crafting table's (shaped and shapeless; special recipes like fireworks are skipped). */
		CRAFTING,
		/** The stonecutter's: one block in, one or more out. */
		STONECUTTING,
		/**
		 * A cook's: the smoker's (cooked meat and fish, baked potatoes), the crafting table's (bread, pies, stews...) and,
		 * with Cobblemon, its Campfire Pot's (Poké Snacks, Aprijuice, candies...), found by recipe type id.
		 */
		KITCHEN,
		/**
		 * A tinkerer's: the blast furnace's for metal (raw ore and ore into ingots; worn tools and armour are never melted
		 * down), then the crafting table's.
		 */
		WORKSHOP,
		/**
		 * A mason's kiln: the furnace's for building blocks (stone, smooth stone, glass, terracotta, cracked bricks, and brick
		 * and nether brick items — never food, ingots or melted-down gear), the stonecutter's and the crafting table's
		 * (clay into bricks into a brick block). A mason only uses it for a plan with something fired in it.
		 */
		KILN,
		/**
		 * A luxury maker's (ROADMAP 34.5): the {@link LuxuryRecipes} files the maker may use, with the crafting table's for
		 * the makings further down. An aged making is never made in the same plan (it has to have aged).
		 */
		LUXURY
	}

	/** Recipes this deep below the thing asked for may be used for its ingredients (fences: sticks from planks from logs). */
	static final int DEPTH = 2;
	/** At most this many crafts in one plan (a bag-full). */
	public static final int MAX_CRAFTS = 64;

	/** One recipe made {@code times} times: what goes in (per craft) and what comes out (per craft). */
	public record Step(Map<Item, Integer> in, ItemStack out, int times) {
	}

	/**
	 * How to make at least {@code count} of the target: the steps in the order to do them (ingredients first), everything
	 * taken from the chests ({@code takes}) and everything there is at the end ({@code makes}: the target, and any
	 * ingredients made one too many). {@code count} is how many of the target that comes to.
	 */
	public record Plan(Item target, int count, List<Step> steps, Map<Item, Integer> takes, Map<Item, Integer> makes) {
	}

	/** A plan to make {@code count} of {@code target} out of {@code usable}, or null if it can't be made from it. */
	@Nullable
	public static Plan plan(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable) {
		return plan(level, kind, target, count, usable, r -> true);
	}

	/** {@link #plan} with only the luxury recipes {@code allowed} lets a maker use (for {@link Kind#LUXURY}). */
	@Nullable
	public static Plan plan(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable, Predicate<LuxuryRecipes.Recipe> allowed) {
		Pool pool = new Pool(usable);
		List<Step> steps = new ArrayList<>();
		if (!make(level, kind, target, count, pool, steps, DEPTH, allowed)) {
			return null;
		}
		Map<Item, Integer> makes = new LinkedHashMap<>();
		pool.made.forEach((item, n) -> {
			if (n > 0) {
				makes.put(item, (int) (long) n);
			}
		});
		return new Plan(target, makes.getOrDefault(target, 0), steps, new LinkedHashMap<>(pool.takes), makes);
	}

	/** What's to hand while planning: the chests' usable stock, what earlier steps made, and what's been taken so far. */
	private static final class Pool {
		final Map<Item, Long> chest;
		final Map<Item, Long> made;
		final Map<Item, Integer> takes;

		Pool(Map<Item, Long> chest) {
			this(new HashMap<>(chest), new HashMap<>(), new LinkedHashMap<>());
		}

		private Pool(Map<Item, Long> chest, Map<Item, Long> made, Map<Item, Integer> takes) {
			this.chest = chest;
			this.made = made;
			this.takes = takes;
		}

		long have(Item item) {
			return chest.getOrDefault(item, 0L) + made.getOrDefault(item, 0L);
		}

		/** Uses {@code n}: what was made first, then from the chests. */
		void use(Item item, long n) {
			long fromMade = Math.min(made.getOrDefault(item, 0L), n);
			made.merge(item, -fromMade, Long::sum);
			long fromChest = n - fromMade;
			if (fromChest > 0) {
				chest.merge(item, -fromChest, Long::sum);
				takes.merge(item, (int) fromChest, Integer::sum);
			}
		}

		Pool copy() {
			return new Pool(new HashMap<>(chest), new HashMap<>(made), new LinkedHashMap<>(takes));
		}

		void set(Pool other) {
			chest.clear();
			chest.putAll(other.chest);
			made.clear();
			made.putAll(other.made);
			takes.clear();
			takes.putAll(other.takes);
		}
	}

	/** Adds the steps that make {@code count} of {@code target} to {@code steps}, using up the pool; false if no recipe works. */
	private static boolean make(ServerLevel level, Kind kind, Item target, int count, Pool pool, List<Step> steps, int depth,
			Predicate<LuxuryRecipes.Recipe> allowed) {
		if (count <= 0) {
			return true;
		}
		if (kind == Kind.LUXURY) {
			for (LuxuryRecipes.Recipe recipe : LuxuryRecipes.making(target)) {
				if (!allowed.test(recipe)) {
					continue;
				}
				int times = Math.min(MAX_CRAFTS, (count + recipe.count() - 1) / recipe.count());
				List<Slot> slots = new ArrayList<>();
				for (LuxuryRecipes.Input input : recipe.inputs()) {
					slots.add(new Slot(input.options(), input.count(), input.minAgeDays() <= 0));
				}
				Pool trial = pool.copy();
				List<Step> trialSteps = new ArrayList<>();
				Map<Item, Integer> in = pick(level, kind, slots, times, trial, trialSteps, depth, allowed);
				if (in == null) {
					continue;
				}
				pool.set(trial);
				steps.addAll(trialSteps);
				steps.add(new Step(in, new ItemStack(target, recipe.count()), times));
				pool.made.merge(target, (long) recipe.count() * times, Long::sum);
				return true;
			}
		}
		for (RecipeHolder<?> holder : recipesFor(level, kind, target)) {
			Recipe<?> recipe = holder.value();
			ItemStack out = Recipes.result(recipe, level.registryAccess());
			if (out.isEmpty() || !out.is(target) || !out.getComponentsPatch().isEmpty()) {
				continue;
			}
			int times = Math.min(MAX_CRAFTS, (count + out.getCount() - 1) / out.getCount());
			// Try it on a copy, so a recipe that doesn't work out leaves everything as it was.
			Pool trial = pool.copy();
			List<Step> trialSteps = new ArrayList<>();
			Map<Item, Integer> in = choose(level, kind, Recipes.ingredients(recipe), times, trial, trialSteps, depth, allowed);
			if (in == null) {
				continue;
			}
			pool.set(trial);
			steps.addAll(trialSteps);
			steps.add(new Step(in, out.copy(), times));
			pool.made.merge(target, (long) out.getCount() * times, Long::sum);
			return true;
		}
		return false;
	}

	/**
	 * Picks an item for each ingredient of a recipe made {@code times} times — from the pool, or made from further down —
	 * and uses them up; returns how many of each one craft takes, or null if something is short.
	 */
	@Nullable
	private static Map<Item, Integer> choose(ServerLevel level, Kind kind, List<Ingredient> ingredients, int times, Pool pool, List<Step> steps, int depth,
			Predicate<LuxuryRecipes.Recipe> allowed) {
		// The same ingredient in several slots (six planks for stairs) is needed that many times over.
		Map<List<Item>, Integer> slots = new LinkedHashMap<>();
		for (Ingredient ingredient : ingredients) {
			if (ingredient.isEmpty()) {
				continue;
			}
			List<Item> options = new ArrayList<>();
			for (ItemStack option : Recipes.options(ingredient)) {
				if (option.getComponentsPatch().isEmpty() && !options.contains(option.getItem())) {
					options.add(option.getItem());
				}
			}
			if (options.isEmpty()) {
				return null;
			}
			slots.merge(options, 1, Integer::sum);
		}
		List<Slot> list = new ArrayList<>();
		slots.forEach((options, n) -> list.add(new Slot(options, n, true)));
		return pick(level, kind, list, times, pool, steps, depth, allowed);
	}

	/** One making of a recipe: one of {@code options}, {@code count} a craft; {@code makeable}: it may be made from further down. */
	private record Slot(List<Item> options, int count, boolean makeable) {
	}

	/** Picks an item for each slot made {@code times} times (from the pool, or made from further down) and uses them up. */
	@Nullable
	private static Map<Item, Integer> pick(ServerLevel level, Kind kind, List<Slot> slots, int times, Pool pool, List<Step> steps, int depth,
			Predicate<LuxuryRecipes.Recipe> allowed) {
		if (slots.isEmpty()) {
			return null;
		}
		Map<Item, Integer> perCraft = new LinkedHashMap<>();
		for (Slot slot : slots) {
			long need = (long) slot.count() * times;
			Item pick = null;
			long best = 0;
			for (Item option : slot.options()) {
				long have = pool.have(option);
				if (have >= need && have > best) {
					pick = option;
					best = have;
				}
			}
			if (pick == null && depth > 0 && slot.makeable()) {
				for (Item option : slot.options()) {
					if (make(level, kind, option, (int) (need - pool.have(option)), pool, steps, depth - 1, allowed)) {
						pick = option;
						break;
					}
				}
			}
			if (pick == null || pool.have(pick) < need) {
				return null;
			}
			pool.use(pick, need);
			perCraft.merge(pick, slot.count(), Integer::sum);
		}
		return perCraft.isEmpty() ? null : perCraft;
	}

	private record Index(RecipeManager manager, Map<Kind, Map<Item, List<RecipeHolder<?>>>> byResult) {
	}

	private static final Map<ServerLevel, Index> INDEX = new WeakHashMap<>();

	/** The recipes of this kind that make {@code target} (indexed once per recipe reload). */
	static List<RecipeHolder<?>> recipesFor(ServerLevel level, Kind kind, Item target) {
		RecipeManager manager = Recipes.manager(level);
		Index index;
		synchronized (INDEX) {
			index = INDEX.get(level);
			if (index == null || index.manager() != manager) {
				index = new Index(manager, new HashMap<>());
				INDEX.put(level, index);
			}
		}
		Map<Item, List<RecipeHolder<?>>> byResult;
		synchronized (index) {
			byResult = index.byResult().get(kind);
			if (byResult == null) {
				byResult = new HashMap<>();
				List<RecipeHolder<?>> all = new ArrayList<>();
				switch (kind) {
					case CRAFTING -> all.addAll(Recipes.all(manager, RecipeType.CRAFTING));
					case STONECUTTING -> all.addAll(Recipes.all(manager, RecipeType.STONECUTTING));
					case LUXURY -> all.addAll(Recipes.all(manager, RecipeType.CRAFTING)); // below the luxury recipes
					case KITCHEN -> {
						// Pot dishes first (a Poké Puff is made in the pot even if some mod adds a crafting recipe too).
						for (RecipeHolder<?> holder : Recipes.all(manager)) {
							if (isCookingPot(holder.value().getType())) {
								all.add(holder);
							}
						}
						all.addAll(Recipes.all(manager, RecipeType.SMOKING));
						all.addAll(Recipes.all(manager, RecipeType.CRAFTING));
					}
					case KILN -> {
						for (RecipeHolder<?> holder : Recipes.all(manager, RecipeType.SMELTING)) {
							ItemStack result = Recipes.result(holder.value(), level.registryAccess());
							if (!meltsGear(holder.value()) && !result.has(net.minecraft.core.component.DataComponents.FOOD)
									&& (result.getItem() instanceof net.minecraft.world.item.BlockItem || result.is(net.minecraft.world.item.Items.BRICK)
									|| result.is(net.minecraft.world.item.Items.NETHER_BRICK))) {
								all.add(holder);
							}
						}
						all.addAll(Recipes.all(manager, RecipeType.STONECUTTING));
						all.addAll(Recipes.all(manager, RecipeType.CRAFTING));
					}
					case WORKSHOP -> {
						for (RecipeHolder<?> holder : Recipes.all(manager, RecipeType.BLASTING)) {
							if (!meltsGear(holder.value())) {
								all.add(holder);
							}
						}
						all.addAll(Recipes.all(manager, RecipeType.CRAFTING));
					}
				}
				for (RecipeHolder<?> holder : all) {
					// Special recipes (fireworks, map copies) and ones that don't list their ingredients can't be planned.
					if (holder.value().isSpecial() || Recipes.ingredients(holder.value()).stream().allMatch(Ingredient::isEmpty)) {
						continue;
					}
					ItemStack out = Recipes.result(holder.value(), level.registryAccess());
					if (!out.isEmpty()) {
						byResult.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(holder);
					}
				}
				index.byResult().put(kind, byResult);
			}
		}
		return byResult.getOrDefault(target, List.of());
	}

	/** Whether a recipe melts down something with durability (iron tools into nuggets): the tinkerer leaves those alone. */
	private static boolean meltsGear(Recipe<?> recipe) {
		for (Ingredient ingredient : Recipes.ingredients(recipe)) {
			for (ItemStack option : Recipes.options(ingredient)) {
				if (option.isDamageableItem()) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether {@code step} is fired in a blast furnace (one item in, by a blasting recipe) rather than crafted. */
	public static boolean isFired(ServerLevel level, Step step) {
		return isFired(level, Kind.WORKSHOP, step);
	}

	/** Whether {@code step}, in a plan of {@code kind}, is fired (a blasting or smelting recipe) rather than crafted or cut. */
	public static boolean isFired(ServerLevel level, Kind kind, Step step) {
		if (step.in().size() != 1) {
			return false;
		}
		Item in = step.in().keySet().iterator().next();
		for (RecipeHolder<?> holder : recipesFor(level, kind, step.out().getItem())) {
			RecipeType<?> type = holder.value().getType();
			if ((type == RecipeType.BLASTING || type == RecipeType.SMELTING) && Recipes.ingredients(holder.value()).get(0).test(new ItemStack(in))) {
				return true;
			}
		}
		return false;
	}

	/** A Cobblemon Campfire Pot recipe type ({@code cobblemon:cooking_pot}...), by id: no Cobblemon classes needed. */
	static boolean isCookingPot(RecipeType<?> type) {
		net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(type);
		return id != null && id.getNamespace().equals("cobblemon") && id.getPath().contains("cooking_pot");
	}

	private Crafting() {
	}
}
