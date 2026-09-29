package io.github.jcondedata.aliveworkplace.craft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
		STONECUTTING
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
		Pool pool = new Pool(usable);
		List<Step> steps = new ArrayList<>();
		if (!make(level, kind, target, count, pool, steps, DEPTH)) {
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
	private static boolean make(ServerLevel level, Kind kind, Item target, int count, Pool pool, List<Step> steps, int depth) {
		if (count <= 0) {
			return true;
		}
		for (RecipeHolder<?> holder : recipesFor(level, kind, target)) {
			Recipe<?> recipe = holder.value();
			ItemStack out = recipe.getResultItem(level.registryAccess());
			if (out.isEmpty() || !out.is(target) || !out.getComponentsPatch().isEmpty()) {
				continue;
			}
			int times = Math.min(MAX_CRAFTS, (count + out.getCount() - 1) / out.getCount());
			// Try it on a copy, so a recipe that doesn't work out leaves everything as it was.
			Pool trial = pool.copy();
			List<Step> trialSteps = new ArrayList<>();
			Map<Item, Integer> in = choose(level, kind, recipe.getIngredients(), times, trial, trialSteps, depth);
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
	private static Map<Item, Integer> choose(ServerLevel level, Kind kind, List<Ingredient> ingredients, int times, Pool pool, List<Step> steps, int depth) {
		// The same ingredient in several slots (six planks for stairs) is needed that many times over.
		Map<List<Item>, Integer> slots = new LinkedHashMap<>();
		for (Ingredient ingredient : ingredients) {
			if (ingredient.isEmpty()) {
				continue;
			}
			List<Item> options = new ArrayList<>();
			for (ItemStack option : ingredient.getItems()) {
				if (option.getComponentsPatch().isEmpty() && !options.contains(option.getItem())) {
					options.add(option.getItem());
				}
			}
			if (options.isEmpty()) {
				return null;
			}
			slots.merge(options, 1, Integer::sum);
		}
		Map<Item, Integer> perCraft = new LinkedHashMap<>();
		for (Map.Entry<List<Item>, Integer> slot : slots.entrySet()) {
			long need = (long) slot.getValue() * times;
			Item pick = null;
			long best = 0;
			for (Item option : slot.getKey()) {
				long have = pool.have(option);
				if (have >= need && have > best) {
					pick = option;
					best = have;
				}
			}
			if (pick == null && depth > 0) {
				for (Item option : slot.getKey()) {
					if (make(level, kind, option, (int) (need - pool.have(option)), pool, steps, depth - 1)) {
						pick = option;
						break;
					}
				}
			}
			if (pick == null || pool.have(pick) < need) {
				return null;
			}
			pool.use(pick, need);
			perCraft.merge(pick, slot.getValue(), Integer::sum);
		}
		return perCraft.isEmpty() ? null : perCraft;
	}

	private record Index(RecipeManager manager, Map<Kind, Map<Item, List<RecipeHolder<?>>>> byResult) {
	}

	private static final Map<ServerLevel, Index> INDEX = new WeakHashMap<>();

	/** The recipes of this kind that make {@code target} (indexed once per recipe reload). */
	static List<RecipeHolder<?>> recipesFor(ServerLevel level, Kind kind, Item target) {
		RecipeManager manager = level.getRecipeManager();
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
				List<? extends RecipeHolder<?>> all = kind == Kind.CRAFTING ? manager.getAllRecipesFor(RecipeType.CRAFTING)
					: manager.getAllRecipesFor(RecipeType.STONECUTTING);
				for (RecipeHolder<?> holder : all) {
					if (holder.value().isSpecial()) {
						continue;
					}
					ItemStack out = holder.value().getResultItem(level.registryAccess());
					if (!out.isEmpty()) {
						byResult.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(holder);
					}
				}
				index.byResult().put(kind, byResult);
			}
		}
		return byResult.getOrDefault(target, List.of());
	}

	private Crafting() {
	}
}
