package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
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
		 * A cook's: the smoker's (cooked meat and fish, baked potatoes), the crafting table's (bread, pies, stews...),
		 * with Cobblemon its Campfire Pot's (Poké Snacks, Aprijuice, candies...), found by recipe type id, and the
		 * campfire's (roasted berries).
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
		LUXURY,
		/**
		 * The Crafter's (ROADMAP 37.14): the crafting table's and the stonecutter's together, and every recipe type a
		 * {@code crafter_benches} file names (a furniture mod's bench, by recipe type id), used only while one of that file's
		 * blocks stands by the Crafter's Workbench ({@link CrafterBenches}). Whole chains: {@link #CHAIN_DEPTH} steps down.
		 */
		CRAFTER
	}

	/** How far down a Crafter's chains go (a chair from slabs from planks from logs, and one more for the sticks). */
	public static final int CHAIN_DEPTH = 3;

	/** Recipes this deep below the thing asked for may be used for its ingredients (fences: sticks from planks from logs). */
	static final int DEPTH = 2;
	/** At most this many crafts in one plan (a bag-full). */
	public static final int MAX_CRAFTS = 64;

	/** One recipe made {@code times} times: what goes in (per craft) and what comes out (per craft). */
	public record Step(Map<Item, Integer> in, ItemStack out, int times) {
		/** Equal when the same recipe makes the same stack as often (an {@link ItemStack} has no equals of its own). */
		@Override
		public boolean equals(Object o) {
			return o instanceof Step other && times == other.times && in.equals(other.in) && ItemStack.matches(out, other.out);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(in, out.getItem(), out.getCount(), times);
		}
	}

	/**
	 * How to make at least {@code count} of the target: the steps in the order to do them (ingredients first), everything
	 * taken from the chests ({@code takes}) and everything there is at the end ({@code makes}: the target, and any
	 * ingredients made one too many). {@code count} is how many of the target that comes to.
	 */
	public record Plan(Item target, int count, List<Step> steps, Map<Item, Integer> takes, Map<Item, Integer> makes) {
	}

	/**
	 * The most recipes one plan tries (each with its ingredients, all the way down), so a search that can't work out
	 * gives up rather than holding up the server's tick (B101). A carpenter's fence from logs tries a few dozen.
	 */
	public static final int PLAN_TRIALS = 500;
	/**
	 * The most recipes all of a level's crafters together try in one tick (about 10 ms); once it's used up, plans wait
	 * for the next tick.
	 */
	public static final int TICK_TRIALS = 1500;

	/** Recipes tried per level in the current tick: the tick it's for and how many. */
	private static final Map<ServerLevel, long[]> TICK_USE = new WeakHashMap<>();
	/** Recipes tried by every plan so far (for the tests). */
	private static long trials;

	/** A plan to make {@code count} of {@code target} out of {@code usable}, or null if it can't be made from it. */
	@Nullable
	public static Plan plan(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable) {
		return plan(level, kind, target, count, usable, r -> true);
	}

	/** {@link #plan} with only the luxury recipes {@code allowed} lets a maker use (for {@link Kind#LUXURY}). */
	@Nullable
	public static Plan plan(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable, Predicate<LuxuryRecipes.Recipe> allowed) {
		return plan(level, kind, target, count, usable, allowed, t -> true, DEPTH, false);
	}

	/**
	 * A Crafter's plan (37.14): {@link Kind#CRAFTER}'s recipes whose type {@code types} lets in (a bench's only while the
	 * bench is there), {@link #CHAIN_DEPTH} steps down.
	 */
	@Nullable
	public static Plan chain(ServerLevel level, Item target, int count, Map<Item, Long> usable, Predicate<RecipeType<?>> types) {
		Plan plan = plan(level, Kind.CRAFTER, target, count, usable, r -> true, types, CHAIN_DEPTH, false);
		return plan == null ? null : new Plan(plan.target(), plan.count(), merged(plan.steps()), plan.takes(), plan.makes());
	}

	/**
	 * A job's whole chain (37.14a): {@code kind}'s recipes, {@link #CHAIN_DEPTH} steps down (a Carpenter's barrel from
	 * logs: planks, slabs, the barrel), each recipe one step.
	 */
	@Nullable
	public static Plan chain(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable) {
		Plan plan = plan(level, kind, target, count, usable, r -> true, t -> true, CHAIN_DEPTH, false);
		return plan == null ? null : new Plan(plan.target(), plan.count(), merged(plan.steps()), plan.takes(), plan.makes());
	}

	/** What a job of {@code kind} lacks to make {@code count} of {@code target} from {@code usable} (37.14a), as {@link #lacking}. */
	public static Map<Item, Integer> lacking(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable) {
		return lacks(shortfall(level, kind, target, count, usable));
	}

	/**
	 * The plan {@link #lacking(ServerLevel, Kind, Item, int, Map)} reads: its steps as if every raw material were there
	 * (a kiln counts its fuel from them, B352), and what's short as negative takes. Null if no recipe makes it.
	 */
	@Nullable
	public static Plan shortfall(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable) {
		return plan(level, kind, target, count, usable, r -> true, t -> true, CHAIN_DEPTH, true);
	}

	/** What a {@link #shortfall} plan lacks, by item. */
	public static Map<Item, Integer> lacks(@Nullable Plan plan) {
		return plan == null ? Map.of() : plan.takes().entrySet().stream()
			.filter(e -> e.getValue() < 0)
			.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> -e.getValue(), (a, b) -> a, LinkedHashMap::new));
	}

	/**
	 * The steps with each recipe made once, at its first place: planks sawn for the barrel and more planks later for its
	 * slabs are one step of planks (the first is before everything that needs either, so the order still holds).
	 */
	static List<Step> merged(List<Step> steps) {
		List<Step> out = new ArrayList<>();
		for (Step step : steps) {
			int same = -1;
			for (int i = 0; i < out.size(); i++) {
				Step o = out.get(i);
				if (o.in().equals(step.in()) && ItemStack.matches(o.out(), step.out())) {
					same = i;
					break;
				}
			}
			// only if nothing it takes is made in between (it could only be made once that's done)
			for (int i = same + 1; same >= 0 && i < out.size(); i++) {
				if (step.in().containsKey(out.get(i).out().getItem())) {
					same = -1;
				}
			}
			if (same < 0) {
				out.add(step);
			} else {
				Step o = out.get(same);
				out.set(same, new Step(o.in(), o.out(), o.times() + step.times()));
			}
		}
		return out;
	}

	/**
	 * What a Crafter lacks to make {@code count} of {@code target} from {@code usable} (37.14): the raw materials (things
	 * no recipe of theirs makes) still short once everything that can be made is, by item. Empty when it can be made, or
	 * when no recipe makes it at all; it says what's short of the first way down the recipes, the way a player would try.
	 */
	public static Map<Item, Integer> lacking(ServerLevel level, Item target, int count, Map<Item, Long> usable, Predicate<RecipeType<?>> types) {
		Plan plan = plan(level, Kind.CRAFTER, target, count, usable, r -> true, types, CHAIN_DEPTH, true);
		return plan == null ? Map.of() : plan.takes().entrySet().stream()
			.filter(e -> e.getValue() < 0)
			.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> -e.getValue(), (a, b) -> a, LinkedHashMap::new));
	}

	@Nullable
	private static Plan plan(ServerLevel level, Kind kind, Item target, int count, Map<Item, Long> usable, Predicate<LuxuryRecipes.Recipe> allowed,
			Predicate<RecipeType<?>> types, int depth, boolean lenient) {
		long[] tick;
		synchronized (TICK_USE) {
			tick = TICK_USE.computeIfAbsent(level, l -> new long[2]);
			if (tick[0] != level.getGameTime()) {
				tick[0] = level.getGameTime();
				tick[1] = 0;
			}
		}
		int left = (int) Math.min(PLAN_TRIALS, TICK_TRIALS - tick[1]);
		if (left <= 0) {
			return null; // this tick's planning is used up: the crafter asks again later
		}
		Pool pool = new Pool(usable, left);
		pool.budget.types = types;
		pool.budget.lenient = lenient;
		List<Step> steps = new ArrayList<>();
		boolean made = make(level, kind, target, count, pool, steps, depth, allowed);
		tick[1] += pool.budget.tried;
		trials += pool.budget.tried;
		if (!made) {
			return null;
		}
		if (lenient) {
			// what's short, as negative takes (only {@link #lacking} reads a lenient plan)
			Map<Item, Integer> lacks = new LinkedHashMap<>();
			pool.lacks.forEach((item, n) -> {
				if (n > 0) {
					lacks.put(item, (int) -n);
				}
			});
			return new Plan(target, 0, steps, lacks, Map.of());
		}
		Map<Item, Integer> makes = new LinkedHashMap<>();
		pool.made.forEach((item, n) -> {
			if (n > 0) {
				makes.put(item, (int) (long) n);
			}
		});
		return new Plan(target, makes.getOrDefault(target, 0), steps, new LinkedHashMap<>(pool.takes), makes);
	}

	/**
	 * Whether this tick's planning is used up ({@link #TICK_TRIALS}): a plan now would come back null whatever the
	 * chests hold, so a crafter going down a long menu stops there and carries on from that dish next time (38.11).
	 */
	public static boolean planningSpent(ServerLevel level) {
		synchronized (TICK_USE) {
			long[] tick = TICK_USE.get(level);
			return tick != null && tick[0] == level.getGameTime() && tick[1] >= TICK_TRIALS;
		}
	}

	/** How many recipes every plan so far has tried (tests compare it before and after). */
	public static long trials() {
		return trials;
	}

	/** How many recipes one plan may still try. */
	private static final class Budget {
		final int max;
		int tried;
		/** The recipe types this plan may use (a Crafter's bench recipes only while the bench stands: 37.14). */
		Predicate<RecipeType<?>> types = t -> true;
		/** Planning what's short ({@link #lacking}): a raw material that's short is noted, and planning goes on. */
		boolean lenient;

		Budget(int max) {
			this.max = max;
		}

		/** Counts one more recipe tried; false once the plan has tried its most. */
		boolean tryOne() {
			if (tried >= max) {
				return false;
			}
			tried++;
			return true;
		}
	}

	/**
	 * What's to hand while planning: the chests' usable stock, what earlier steps made, and what's been taken so far. A
	 * trial (a recipe tried out) is a pool on top of another that keeps only its own changes, so trying a recipe costs
	 * nothing like a copy of the whole stock (B101); a trial that works is folded into the pool under it.
	 */
	private static final class Pool {
		@Nullable
		final Pool parent;
		final Budget budget;
		/** The chests' stock as the plan was asked for (read only: not copied for every plan). */
		final Map<Item, Long> stock;
		/** Changes to the parent's (or, in the root pool, to the stock). */
		final Map<Item, Long> chest;
		final Map<Item, Long> made;
		final Map<Item, Integer> takes;
		/** Raw materials short (a lenient plan's only). */
		final Map<Item, Long> lacks = new LinkedHashMap<>();

		Pool(Map<Item, Long> chest, int budget) {
			this.parent = null;
			this.budget = new Budget(budget);
			this.stock = chest;
			this.chest = new HashMap<>();
			this.made = new HashMap<>();
			this.takes = new LinkedHashMap<>();
		}

		private Pool(Pool parent) {
			this.parent = parent;
			this.budget = parent.budget;
			this.stock = Map.of();
			this.chest = new HashMap<>();
			this.made = new HashMap<>();
			this.takes = new LinkedHashMap<>();
		}

		long chest(Item item) {
			return chest.getOrDefault(item, 0L) + (parent == null ? stock.getOrDefault(item, 0L) : parent.chest(item));
		}

		long made(Item item) {
			return made.getOrDefault(item, 0L) + (parent == null ? 0 : parent.made(item));
		}

		long have(Item item) {
			return chest(item) + made(item);
		}

		/** Uses {@code n}: what was made first, then from the chests. */
		void use(Item item, long n) {
			long fromMade = Math.min(made(item), n);
			made.merge(item, -fromMade, Long::sum);
			long fromChest = n - fromMade;
			if (fromChest > 0) {
				chest.merge(item, -fromChest, Long::sum);
				takes.merge(item, (int) fromChest, Integer::sum);
			}
		}

		void addMade(Item item, long n) {
			made.merge(item, n, Long::sum);
		}

		/** A trial on top of this pool. */
		Pool copy() {
			return new Pool(this);
		}

		/** Keeps a trial of this pool that worked out. */
		void set(Pool trial) {
			if (trial.parent != this) {
				throw new IllegalArgumentException("not a trial of this pool");
			}
			trial.chest.forEach((item, n) -> chest.merge(item, n, Long::sum));
			trial.made.forEach((item, n) -> made.merge(item, n, Long::sum));
			trial.takes.forEach((item, n) -> takes.merge(item, n, Integer::sum));
			trial.lacks.forEach((item, n) -> lacks.merge(item, n, Long::sum));
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
				if (!pool.budget.tryOne()) {
					return false;
				}
				int times = Math.min(MAX_CRAFTS, (count + recipe.count() - 1) / recipe.count());
				List<Slot> slots = new ArrayList<>();
				for (LuxuryRecipes.Input input : recipe.inputs()) {
					slots.add(new Slot(input.options(), input.count(), input.minAgeDays() <= 0, input.mix()));
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
				pool.addMade(target, (long) recipe.count() * times);
				return true;
			}
		}
		// Planning what's short (B489, the B409 rule across recipes): not the first recipe that can be noted as short, but
		// the one closest to what's there (raw iron fired into ingots when the chest holds raw iron, not the iron ore no
		// one has); a recipe that only unpacks a storage block is no way to say what's short (raw iron from its block).
		boolean lenient = pool.budget.lenient;
		Pool best = null;
		List<Step> bestSteps = null;
		Step bestStep = null;
		long bestMade = 0;
		int bestRank = -1;
		for (RecipeHolder<?> holder : recipesFor(level, kind, target)) {
			Recipe<?> recipe = holder.value();
			if (!pool.budget.types.test(recipe.getType())) {
				continue; // a bench's recipe while the bench isn't there (37.14)
			}
			ItemStack out = Recipes.result(recipe, level.registryAccess());
			if (out.isEmpty() || !out.is(target) || !out.getComponentsPatch().isEmpty()) {
				continue;
			}
			if (lenient && unpacks(level, kind, recipe, target)) {
				continue;
			}
			if (!pool.budget.tryOne()) {
				if (best != null) {
					break;
				}
				return false; // tried enough for one plan (B101)
			}
			int times = Math.min(MAX_CRAFTS, (count + out.getCount() - 1) / out.getCount());
			// Try it on a copy, so a recipe that doesn't work out leaves everything as it was.
			Pool trial = pool.copy();
			List<Step> trialSteps = new ArrayList<>();
			Map<Item, Integer> in = choose(level, kind, Recipes.ingredients(recipe), times, trial, trialSteps, depth, allowed);
			if (in == null) {
				continue;
			}
			Step step = new Step(in, out.copy(), times);
			if (!lenient) {
				pool.set(trial);
				steps.addAll(trialSteps);
				steps.add(step);
				pool.addMade(target, (long) out.getCount() * times);
				return true;
			}
			int rank = shortRank(pool, trial);
			if (rank > bestRank) {
				best = trial;
				bestSteps = trialSteps;
				bestStep = step;
				bestMade = (long) out.getCount() * times;
				bestRank = rank;
			}
			if (rank == RANK_ALL_THERE) {
				break;
			}
		}
		if (best != null) {
			pool.set(best);
			steps.addAll(bestSteps);
			steps.add(bestStep);
			pool.addMade(target, bestMade);
			return true;
		}
		if (kind == Kind.CRAFTER && !CrafterBenches.all().isEmpty()) {
			return makeAtBench(level, kind, target, count, pool, steps, depth, allowed);
		}
		return false;
	}

	/** {@link #shortRank}: nothing short at all. */
	private static final int RANK_ALL_THERE = 3;

	/**
	 * How well a recipe tried while planning what's short ({@code trial}, on top of {@code pool}) fits what's there:
	 * {@link #RANK_ALL_THERE} if nothing's short; 2 if it uses something the chests hold (five raw iron of six); 1 if
	 * what's short are items, not blocks (raw iron, not the ore a player only gets with Silk Touch); else 0.
	 */
	private static int shortRank(Pool pool, Pool trial) {
		if (trial.lacks.values().stream().noneMatch(n -> n > 0)) {
			return RANK_ALL_THERE;
		}
		if (trial.takes.keySet().stream().anyMatch(item -> pool.chest(item) > 0)) {
			return 2;
		}
		boolean blocks = trial.lacks.entrySet().stream()
			.anyMatch(e -> e.getValue() > 0 && e.getKey() instanceof net.minecraft.world.item.BlockItem);
		return blocks ? 0 : 1;
	}

	/**
	 * Whether {@code recipe} only unpacks a storage form of {@code target}: all its ingredients are one item that every
	 * recipe of {@code kind} for it packs from {@code target} alone (raw iron from a raw iron block, ingots from a block
	 * or nuggets; not nuggets from ingots, which are fired from raw iron too).
	 */
	private static boolean unpacks(ServerLevel level, Kind kind, Recipe<?> recipe, Item target) {
		Item from = null;
		for (Ingredient ingredient : Recipes.ingredients(recipe)) {
			if (ingredient.isEmpty()) {
				continue;
			}
			List<Item> options = options(level, ingredient);
			if (options.size() != 1 || (from != null && from != options.get(0))) {
				return false;
			}
			from = options.get(0);
		}
		if (from == null || from == target) {
			return false;
		}
		List<RecipeHolder<?>> packs = recipesFor(level, kind, from);
		if (packs.isEmpty()) {
			return false;
		}
		for (RecipeHolder<?> holder : packs) {
			boolean any = false;
			for (Ingredient ingredient : Recipes.ingredients(holder.value())) {
				if (ingredient.isEmpty()) {
					continue;
				}
				any = true;
				List<Item> options = options(level, ingredient);
				if (options.size() != 1 || options.get(0) != target) {
					return false; // made some other way too (ingots, fired from raw iron, are no mere storage of nuggets)
				}
			}
			if (!any) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A Crafter's bench recipe that doesn't list its materials as ingredients (37.15: CobbleFurnies' Furni Crafter takes
	 * so many of each item, read from its files by {@link FurnitureRecipes}): made the same way, with each material's
	 * count, if its recipe type is a {@code crafter_benches} bench's and the bench stands in the workshop.
	 */
	private static boolean makeAtBench(ServerLevel level, Kind kind, Item target, int count, Pool pool, List<Step> steps, int depth,
			Predicate<LuxuryRecipes.Recipe> allowed) {
		RecipeManager manager = Recipes.manager(level);
		for (FurnitureRecipes.Recipe recipe : FurnitureRecipes.making(level.getServer(), target)) {
			RecipeType<?> type = Recipes.byId(manager, recipe.id()).map(h -> (RecipeType<?>) h.value().getType()).orElse(null);
			if (type == null || !CrafterBenches.isBenchType(type) || !pool.budget.types.test(type)) {
				continue; // not a bench's, or the bench isn't in the workshop
			}
			if (!recipe.result().is(target) || !recipe.result().getComponentsPatch().isEmpty()) {
				continue;
			}
			if (!pool.budget.tryOne()) {
				return false;
			}
			int each = recipe.result().getCount();
			int times = Math.min(MAX_CRAFTS, (count + each - 1) / each);
			List<Slot> slots = new ArrayList<>();
			recipe.materials().forEach((item, n) -> slots.add(new Slot(List.of(item), n, true, false)));
			Pool trial = pool.copy();
			List<Step> trialSteps = new ArrayList<>();
			Map<Item, Integer> in = pick(level, kind, slots, times, trial, trialSteps, depth, allowed);
			if (in == null) {
				continue;
			}
			pool.set(trial);
			steps.addAll(trialSteps);
			steps.add(new Step(in, recipe.result().copy(), times));
			pool.addMade(target, (long) each * times);
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
			List<Item> options = options(level, ingredient);
			if (options.isEmpty()) {
				return null;
			}
			slots.merge(options, 1, Integer::sum);
		}
		List<Slot> list = new ArrayList<>();
		slots.forEach((options, n) -> list.add(new Slot(options, n, true, false)));
		return pick(level, kind, list, times, pool, steps, depth, allowed);
	}

	/**
	 * One making of a recipe: one of {@code options}, {@code count} a craft; {@code makeable}: it may be made from further
	 * down; {@code mix}: several of the options may make up the count together (a luxury recipe's "3 wool of any colour").
	 */
	private record Slot(List<Item> options, int count, boolean makeable, boolean mix) {
	}

	/**
	 * A mixed slot: every craft takes the same mix (so a step still says what one craft takes), the best-stocked options
	 * first; what's still short is made from further down, as one of the options. False if it can't be filled.
	 */
	private static boolean pickMix(ServerLevel level, Kind kind, Slot slot, int times, Pool pool, List<Step> steps, int depth,
			Predicate<LuxuryRecipes.Recipe> allowed, Map<Item, Integer> perCraft) {
		List<Item> options = new ArrayList<>(slot.options());
		Map<Item, Long> stock = new HashMap<>();
		options.forEach(o -> stock.put(o, pool.have(o)));
		options.sort((a, b) -> Long.compare(stock.get(b), stock.get(a))); // a stable sort: ties stay in the tag's order
		int left = slot.count();
		for (Item option : options) {
			int each = (int) Math.min(left, stock.get(option) / times);
			if (each > 0) {
				pool.use(option, (long) each * times);
				perCraft.merge(option, each, Integer::sum);
				left -= each;
			}
			if (left == 0) {
				return true;
			}
		}
		if (depth > 0 && slot.makeable()) {
			for (Item option : slot.options()) {
				long need = (long) left * times;
				if (make(level, kind, option, (int) (need - pool.have(option)), pool, steps, depth - 1, allowed) && pool.have(option) >= need) {
					pool.use(option, need);
					perCraft.merge(option, left, Integer::sum);
					return true;
				}
			}
		}
		return false;
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
			if (slot.mix()) {
				if (!pickMix(level, kind, slot, times, pool, steps, depth, allowed, perCraft)) {
					return null;
				}
				continue;
			}
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
				pick = makeOne(level, kind, slot, need, pool, steps, depth, allowed);
			}
			if (pick == null || pool.have(pick) < need) {
				if (!pool.budget.lenient) {
					return null;
				}
				// 37.14, what's short: an option the chests hold some of (B409: the leather, not the rabbit hide that
				// makes it), else the first raw option (logs, not wood made from logs), or the first one
				if (pick == null) {
					pick = slot.options().stream().filter(o -> pool.chest(o) > 0).findFirst()
						.orElseGet(() -> slot.options().stream().filter(o -> recipesFor(level, kind, o).isEmpty()).findFirst().orElse(slot.options().get(0)));
				}
				pool.lacks.merge(pick, need - Math.max(0, pool.have(pick)), Long::sum);
			}
			pool.use(pick, need);
			perCraft.merge(pick, slot.count(), Integer::sum);
		}
		return perCraft.isEmpty() ? null : perCraft;
	}

	/**
	 * Makes {@code need} of one of a slot's options from further down; the option made, or null. Planning what's short, each
	 * option is tried properly first, so a lack is only noted down the way nothing could be made (37.14).
	 */
	@Nullable
	private static Item makeOne(ServerLevel level, Kind kind, Slot slot, long need, Pool pool, List<Step> steps, int depth,
			Predicate<LuxuryRecipes.Recipe> allowed) {
		if (pool.budget.lenient) {
			pool.budget.lenient = false;
			try {
				for (Item option : slot.options()) {
					Pool trial = pool.copy();
					List<Step> trialSteps = new ArrayList<>();
					if (make(level, kind, option, (int) (need - trial.have(option)), trial, trialSteps, depth - 1, allowed) && trial.have(option) >= need) {
						pool.set(trial);
						steps.addAll(trialSteps);
						return option;
					}
				}
			} finally {
				pool.budget.lenient = true;
			}
			if (slot.options().stream().anyMatch(o -> recipesFor(level, kind, o).isEmpty())) {
				return null; // a raw material: noted as short
			}
			if (slot.options().stream().anyMatch(o -> pool.chest(o) > 0)) {
				// B409: the chests hold some, too few. Going further down is only worth saying when it uses something
				// that's there (planks from the logs at hand); else the thing itself is what's short (the leather, not
				// rabbit hide nobody has).
				for (Item option : slot.options()) {
					Pool trial = pool.copy();
					List<Step> trialSteps = new ArrayList<>();
					if (make(level, kind, option, (int) (need - trial.have(option)), trial, trialSteps, depth - 1, allowed)
							&& trial.lacks.entrySet().stream().anyMatch(e -> e.getValue() > 0 && pool.chest(e.getKey()) > 0)) {
						pool.set(trial);
						steps.addAll(trialSteps);
						return option;
					}
				}
				return null; // noted as short
			}
		}
		for (Item option : slot.options()) {
			if (make(level, kind, option, (int) (need - pool.have(option)), pool, steps, depth - 1, allowed)) {
				return option;
			}
		}
		return null;
	}

	/**
	 * What one recipe manager (one load of the datapacks) looks like to the crafters: its recipes by what they make, per
	 * kind, and each ingredient's plain items. Built in full when the server starts and after every {@code /reload}
	 * ({@link #warm}), so no villager's first look at the recipes goes through them all in one tick (B101: 400 ms on the
	 * pack); a recipe manager that wasn't warmed (another one in a test) is still indexed on first use.
	 */
	private record Index(Map<Kind, Map<Item, List<RecipeHolder<?>>>> byResult, Map<Ingredient, List<Item>> options) {
	}

	/** One index per recipe manager (by identity: a reload makes a new manager, so an old index is never read again). */
	private static final Map<RecipeManager, Index> INDEX = new WeakHashMap<>();
	/** How many times a kind's recipes or an ingredient's items were gone through (for the tests: a warm lookup adds none). */
	private static int scans;

	private static Index index(RecipeManager manager) {
		synchronized (INDEX) {
			return INDEX.computeIfAbsent(manager, m -> new Index(new HashMap<>(), new IdentityHashMap<>()));
		}
	}

	/**
	 * Prepares everything a crafter looks up in the server's recipes: every ingredient's items (the game works those out
	 * the first time they're asked for, which is the slow part with a big modpack's tags), then each kind's recipes by
	 * result. Run once the datapacks are loaded and after each {@code /reload}, outside the game's ticks.
	 */
	public static void warm(MinecraftServer server) {
		long start = System.nanoTime();
		RecipeManager manager = Recipes.manager(server);
		int failed = 0;
		for (RecipeHolder<?> holder : Recipes.all(manager)) {
			try {
				for (Ingredient ingredient : Recipes.ingredients(holder.value())) {
					Recipes.options(ingredient); // the game keeps the answer: later looks (any job's) are quick
				}
			} catch (RuntimeException e) {
				failed++; // a broken modded recipe: the job that meets it handles it as it always did
			}
		}
		Index index = index(manager);
		for (Kind kind : Kind.values()) {
			for (List<RecipeHolder<?>> holders : byResult(manager, server.registryAccess(), kind).values()) {
				for (RecipeHolder<?> holder : holders) {
					for (Ingredient ingredient : Recipes.ingredients(holder.value())) {
						options(index, ingredient);
					}
				}
			}
		}
		AliveWorkplace.LOG.debug("Indexed the recipes for crafting villagers in {} ms ({} recipes could not be read)",
			(System.nanoTime() - start) / 1_000_000, failed);
	}

	/** Whether this recipe manager's recipes of {@code kind} are indexed already (a lookup won't go through them). */
	public static boolean indexed(RecipeManager manager, Kind kind) {
		Index index;
		synchronized (INDEX) {
			index = INDEX.get(manager);
		}
		if (index == null) {
			return false;
		}
		synchronized (index) {
			return index.byResult().containsKey(kind);
		}
	}

	/** How many full passes over recipes or ingredients have been made so far (tests compare it before and after). */
	public static int scans() {
		return scans;
	}

	/** The plain (component-free) items an ingredient accepts, in its order, each once; worked out once per load. */
	public static List<Item> options(ServerLevel level, Ingredient ingredient) {
		return options(index(Recipes.manager(level)), ingredient);
	}

	private static List<Item> options(Index index, Ingredient ingredient) {
		synchronized (index) {
			List<Item> known = index.options().get(ingredient);
			if (known != null) {
				return known;
			}
			scans++;
			Set<Item> options = new LinkedHashSet<>();
			for (ItemStack option : Recipes.options(ingredient)) {
				if (option.getComponentsPatch().isEmpty()) {
					options.add(option.getItem());
				}
			}
			List<Item> list = List.copyOf(options);
			index.options().put(ingredient, list);
			return list;
		}
	}

	/** The recipes of this kind that make {@code target} (indexed once per recipe load). */
	static List<RecipeHolder<?>> recipesFor(ServerLevel level, Kind kind, Item target) {
		return byResult(Recipes.manager(level), level.registryAccess(), kind).getOrDefault(target, List.of());
	}

	/** The recipes of this kind in {@code manager} that make {@code target} (a test's own recipe manager, say). */
	public static List<RecipeHolder<?>> recipesFor(RecipeManager manager, HolderLookup.Provider registries, Kind kind, Item target) {
		return byResult(manager, registries, kind).getOrDefault(target, List.of());
	}

	private static Map<Item, List<RecipeHolder<?>>> byResult(RecipeManager manager, HolderLookup.Provider registries, Kind kind) {
		Index index = index(manager);
		synchronized (index) {
			Map<Item, List<RecipeHolder<?>>> byResult = index.byResult().get(kind);
			if (byResult != null) {
				return byResult;
			}
			scans++;
			byResult = new HashMap<>();
			List<RecipeHolder<?>> all = new ArrayList<>();
			Set<Item> kept = kind == Kind.KITCHEN ? Chefs.keptDishes(manager, registries) : null;
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
					// Last, the campfire's (38.8: Cobblecuisine's sandwiches want roasted berries); the smoker's come first.
					all.addAll(Recipes.all(manager, RecipeType.CAMPFIRE_COOKING));
				}
				case KILN -> {
					for (RecipeHolder<?> holder : Recipes.all(manager, RecipeType.SMELTING)) {
						ItemStack result = Recipes.result(holder.value(), registries);
						if (!meltsGear(holder.value()) && !result.has(net.minecraft.core.component.DataComponents.FOOD)
								&& (result.getItem() instanceof net.minecraft.world.item.BlockItem || result.is(net.minecraft.world.item.Items.BRICK)
								|| result.is(net.minecraft.world.item.Items.NETHER_BRICK))) {
							all.add(holder);
						}
					}
					all.addAll(Recipes.all(manager, RecipeType.STONECUTTING));
					all.addAll(Recipes.all(manager, RecipeType.CRAFTING));
				}
				case CRAFTER -> {
					all.addAll(Recipes.all(manager, RecipeType.CRAFTING));
					all.addAll(Recipes.all(manager, RecipeType.STONECUTTING));
					// a furniture mod's bench (by recipe type id: no mod class), used only while its bench is there
					for (RecipeHolder<?> holder : Recipes.all(manager)) {
						if (CrafterBenches.isBenchType(holder.value().getType())) {
							all.add(holder);
						}
					}
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
				ItemStack out = Recipes.result(holder.value(), registries);
				if (kind == Kind.KITCHEN && kept != null && kept.contains(out.getItem()) && onlyDishes(holder.value(), kept)) {
					continue; // B435, B459: one kept dish cooked up or broken down into another (EXP Candies, XS to XL and back)
				}
				if (!out.isEmpty()) {
					byResult.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(holder);
				}
			}
			index.byResult().put(kind, byResult);
			return byResult;
		}
	}

	/** Whether every making of {@code recipe} is one of {@code dishes} (each of its ingredient's items is one). */
	private static boolean onlyDishes(Recipe<?> recipe, Set<Item> dishes) {
		boolean any = false;
		for (Ingredient ingredient : Recipes.ingredients(recipe)) {
			if (ingredient.isEmpty()) {
				continue;
			}
			ItemStack[] options = Recipes.options(ingredient);
			if (options.length == 0) {
				return false;
			}
			for (ItemStack option : options) {
				if (!dishes.contains(option.getItem())) {
					return false;
				}
			}
			any = true;
		}
		return any;
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
