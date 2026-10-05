package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A luxury maker's shift (ROADMAP 34.5), the engine the luxury workshops share: the store's stock orders first
 * ({@link StockOrders}), then, in turn, each good of the maker's {@link LuxuryRecipes} (their job, up to their level) that
 * the village store and their own chests hold fewer than {@link #KEEP} of, from their own chests, the store and the
 * village's stashes (not the builders'). Aged makings are only taken once old enough; goods that age get the day they
 * were made ({@code made_day}). When nothing short can be made, the first missing making goes on the requests board.
 */
public class LuxuryWork extends CrafterWork {
	/** The store is kept in this many of each good. */
	public static final int KEEP = 8;
	private int next;
	/** Whose recipes the plans use (the villager whose shift this is). */
	@Nullable
	private Villager maker;

	public LuxuryWork() {
		this("luxury");
	}

	/** {@code who}: whose title the line above the head shows ("message.aliveworkplace.<who>.title"). */
	public LuxuryWork(String who) {
		super(Crafting.Kind.LUXURY, who, "luxury", true);
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		maker = villager;
		Job order = chooseOrder(level, villager, station);
		if (order != null) {
			Requests.clear(villager);
			return order; // the store's orders first
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		if (own.isEmpty()) {
			return null; // nowhere to put what's made
		}
		List<BlockPos> store = store(level, station);
		Set<BlockPos> all = new LinkedHashSet<>(own);
		all.addAll(store);
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			if (stash.job() != ModVillagers.BUILDER) {
				all.addAll(stash.chests()); // a builder's chests are for their builds
			}
		}
		List<BlockPos> sources = new ArrayList<>(all);
		Set<BlockPos> counted = new LinkedHashSet<>(store);
		counted.addAll(own);
		Map<Item, Long> held = held(level, new ArrayList<>(counted));
		Map<Item, Long> usable = usableIn(level, sources);
		List<Item> goods = goods(villager);
		LuxuryRecipes.Recipe firstShort = null;
		for (int i = 0; i < goods.size(); i++) {
			Item good = goods.get((next + i) % goods.size());
			long have = held.getOrDefault(good, 0L);
			if (have >= KEEP) {
				continue;
			}
			for (int count = (int) (KEEP - have); count >= 1; count /= 2) {
				Crafting.Plan plan = planFor(level, good, count, usable);
				if (fits(plan)) {
					next = (next + i + 1) % goods.size();
					Requests.clear(villager);
					return new Job(station, null, plan, "", sources);
				}
			}
			if (firstShort == null) {
				firstShort = allowed(villager).stream().filter(r -> r.output() == good).findFirst().orElse(null);
			}
		}
		if (firstShort != null) {
			ask(level, villager, firstShort, usable, sources);
		} else {
			Requests.clear(villager);
		}
		return null;
	}

	/** Posts the first making {@code recipe} is short of on the requests board (aged makings: only old enough ones count). */
	private static void ask(ServerLevel level, Villager villager, LuxuryRecipes.Recipe recipe, Map<Item, Long> usable, List<BlockPos> sources) {
		for (LuxuryRecipes.Input input : recipe.inputs()) {
			List<Item> options = input.options();
			long have = 0;
			for (Item option : options) {
				have += usable.getOrDefault(option, 0L);
			}
			if (have < input.count() && !options.isEmpty()) {
				long today = LuxuryRecipes.today(level);
				Predicate<ItemStack> accepts = s -> input.matches(s) && LuxuryRecipes.agedFor(s, input.minAgeDays(), today);
				Requests.post(villager, new ItemStack(options.get(0)), (int) (input.count() - have), options.get(0).getDescription(), accepts);
				return;
			}
		}
		Requests.clear(villager);
	}

	/** The village store: its hall's (kitchens and storehouses), or without a hall the storehouses near the workstation. */
	static List<BlockPos> store(ServerLevel level, BlockPos station) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, station);
		if (hall.isPresent()) {
			return VillageNeeds.store(level, hall.get());
		}
		List<BlockPos> out = new ArrayList<>();
		level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, station, StockOrders.RANGE, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingDouble(p -> p.distSqr(station)))
			.forEach(storehouse -> out.addAll(SupplyContainers.find(level, storehouse, null)));
		return out;
	}

	/** The recipes {@code villager} may make: their job's, up to their level. */
	static List<LuxuryRecipes.Recipe> allowed(Villager villager) {
		return LuxuryRecipes.forJob(villager.getVillagerData().getProfession(), villager.getVillagerData().getLevel());
	}

	/** The goods {@code villager} may make, in the files' order. */
	static List<Item> goods(Villager villager) {
		List<Item> out = new ArrayList<>();
		for (LuxuryRecipes.Recipe r : allowed(villager)) {
			if (!out.contains(r.output())) {
				out.add(r.output());
			}
		}
		return out;
	}

	@Override
	protected boolean wants(Item item) {
		return maker != null && goods(maker).contains(item);
	}

	@Nullable
	@Override
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (maker == null) {
			return null;
		}
		Villager who = maker;
		net.minecraft.resources.ResourceLocation job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(who.getVillagerData().getProfession());
		return Crafting.plan(level, kind, item, count, usable, r -> r.job().equals(job) && r.level() <= who.getVillagerData().getLevel());
	}

	/** Plain makings, and aged ones only once old enough for the recipes that want them aged. */
	@Override
	protected boolean fetchable(ServerLevel level, ItemStack stack) {
		return LuxuryRecipes.plainButMadeDay(stack) && LuxuryRecipes.agedFor(stack, LuxuryRecipes.agingDays(stack), LuxuryRecipes.today(level));
	}

	@Override
	protected int takeFrom(ServerLevel level, BlockPos chest, Item item, int max) {
		int got = SupplyContainers.extract(level, List.of(chest), item, max);
		long today = LuxuryRecipes.today(level);
		while (got < max) {
			List<ItemStack> taken = SupplyContainers.takeMatching(level, chest, s -> s.is(item) && s.has(ModComponents.MADE_DAY)
				&& LuxuryRecipes.plainButMadeDay(s) && LuxuryRecipes.agedFor(s, LuxuryRecipes.agingDays(s), today), 1);
			if (taken.isEmpty()) {
				break;
			}
			ItemStack stack = taken.get(0);
			int use = Math.min(stack.getCount(), max - got);
			got += use;
			stack.shrink(use);
			if (!stack.isEmpty()) {
				ItemStack rest = SupplyContainers.insert(level, List.of(chest), stack);
				if (!rest.isEmpty()) {
					net.minecraft.world.level.block.Block.popResource(level, chest.above(), rest);
				}
			}
		}
		return got;
	}

	/** The recipes' own ticks for the luxury steps, the usual for the crafting-table ones below; at the maker's pace. */
	@Override
	protected int ticksFor(Villager villager, Crafting.Plan plan, int crafts) {
		int base = 0;
		for (Crafting.Step step : plan.steps()) {
			int each = CRAFT_TICKS;
			for (LuxuryRecipes.Recipe r : allowed(villager)) {
				if (r.output() == step.out().getItem()) {
					each = r.ticks();
					break;
				}
			}
			base += each * step.times();
		}
		return Math.max(20, BuilderLevels.delay(base, villager));
	}

	/** Goods that age go into the chests stamped with today. */
	@Override
	protected ItemStack finished(ServerLevel level, ItemStack stack) {
		return LuxuryRecipes.stamp(stack, LuxuryRecipes.today(level));
	}

	/** Every good in {@code chests}, made days and all. */
	@Override
	protected Map<Item, Long> held(ServerLevel level, List<BlockPos> chests) {
		Map<Item, Long> out = new HashMap<>(SupplyContainers.contents(level, chests));
		for (BlockPos chest : chests) {
			for (ItemStack s : SupplyContainers.peekMatching(level, chest, s -> s.has(ModComponents.MADE_DAY) && LuxuryRecipes.plainButMadeDay(s))) {
				out.merge(s.getItem(), (long) s.getCount(), Long::sum);
			}
		}
		return out;
	}

	/** Plain things, and goods with a made day once they're old enough. */
	@Override
	protected Map<Item, Long> usableIn(ServerLevel level, List<BlockPos> chests) {
		Map<Item, Long> out = new HashMap<>(SupplyContainers.contents(level, chests));
		long today = LuxuryRecipes.today(level);
		for (BlockPos chest : chests) {
			for (ItemStack s : SupplyContainers.peekMatching(level, chest, s -> s.has(ModComponents.MADE_DAY) && LuxuryRecipes.plainButMadeDay(s)
				&& LuxuryRecipes.agedFor(s, LuxuryRecipes.agingDays(s), today))) {
				out.merge(s.getItem(), (long) s.getCount(), Long::sum);
			}
		}
		return out;
	}
}
