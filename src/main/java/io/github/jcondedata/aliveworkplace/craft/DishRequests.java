package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Dishes asked for at the hall's board (ROADMAP 38.8a, the owner's change of 2026-10-19): the Chef no longer cooks
 * another mod's dishes ({@link CuisineDishes}, Cobblecuisine) on his own; a player asks for them on the Trade page's
 * Dishes tab ({@link DishesTab}), and only once the village has a Chef. Each village (by its hall) keeps how many of
 * each dish are still waiting; the Chef whose stove is in that village cooks them from the makings in his chests, the
 * porters' and the butchers' (as he cooks his menu) when his level allows the dish, and carries them to the village's
 * Storehouse (to the chests by his stove if it has none). One file per dimension; older worlds start with none waiting.
 */
public final class DishRequests extends SavedData {
	public static final String NAME = "aliveworkplace_dish_requests";
	/** At most this many of one dish waiting in a village. */
	public static final int MAX = 64;
	/** A click asks for this many; shift-click for a stack's worth more ({@link #STACK}). */
	public static final int CLICK = 4;
	public static final int STACK = 16;

	/** hall position to dish to how many are waiting, in the order they were asked for. */
	private final Map<Long, LinkedHashMap<ResourceLocation, Integer>> waiting = new HashMap<>();

	public static DishRequests get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DishRequests::new, DishRequests::load, null), NAME);
	}

	/** Whether the board has a Dishes tab: another mod's dishes are loaded and switched on. */
	public static boolean shown() {
		return !CuisineDishes.dishes().isEmpty();
	}

	/** The dishes waiting in the village of {@code hall}, oldest first (a copy). */
	public Map<ResourceLocation, Integer> of(BlockPos hall) {
		Map<ResourceLocation, Integer> map = waiting.get(hall.asLong());
		return map == null ? Map.of() : new LinkedHashMap<>(map);
	}

	public int waiting(BlockPos hall, ResourceLocation dish) {
		return of(hall).getOrDefault(dish, 0);
	}

	/** Adds {@code count} of {@code dish} to what the village waits for (up to {@link #MAX}); how many were added. */
	public int add(BlockPos hall, ResourceLocation dish, int count) {
		LinkedHashMap<ResourceLocation, Integer> map = waiting.computeIfAbsent(hall.asLong(), k -> new LinkedHashMap<>());
		int have = map.getOrDefault(dish, 0);
		int added = Math.max(0, Math.min(count, MAX - have));
		if (added > 0) {
			map.put(dish, have + added);
			setDirty();
		}
		return added;
	}

	/** {@code count} of {@code dish} were delivered (or the request was taken down): fewer wait. */
	public void done(BlockPos hall, ResourceLocation dish, int count) {
		LinkedHashMap<ResourceLocation, Integer> map = waiting.get(hall.asLong());
		if (map == null || !map.containsKey(dish)) {
			return;
		}
		int left = map.get(dish) - count;
		if (left > 0) {
			map.put(dish, left);
		} else {
			map.remove(dish);
			if (map.isEmpty()) {
				waiting.remove(hall.asLong());
			}
		}
		setDirty();
	}

	/** Takes {@code dish}'s request down in the village of {@code hall}; how many were waiting. */
	public int cancel(BlockPos hall, ResourceLocation dish) {
		int was = waiting(hall, dish);
		done(hall, dish, was);
		return was;
	}

	/** Forgets every request (tests). */
	public void clear() {
		waiting.clear();
		setDirty();
	}

	/**
	 * The village's Chef: of the grown villagers in the village of {@code hall} who are Chefs with a stove, the one of the
	 * highest level (the dishes his level allows are the village's); null without one.
	 */
	@Nullable
	public static Villager chef(ServerLevel level, BlockPos hall) {
		Villager best = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && Chefs.isChef(v)
			&& v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent())) {
			if (best == null || BuilderLevels.level(v) > BuilderLevels.level(best)) {
				best = v;
			}
		}
		return best;
	}

	/** What came of a request at the board. */
	public enum Result { ASKED, NO_CHEF, LEVEL, FULL, UNKNOWN }

	/**
	 * A player asks the village of {@code hall} for {@code count} of {@code dish}: refused without a Chef in the village,
	 * for a dish above the best Chef's level, for an item that isn't one of the dishes, and once {@link #MAX} wait.
	 */
	public static Result request(ServerLevel level, BlockPos hall, Item dish, int count) {
		CuisineDishes.Dish known = CuisineDishes.of(dish);
		if (known == null) {
			return Result.UNKNOWN;
		}
		Villager chef = chef(level, hall);
		if (chef == null) {
			return Result.NO_CHEF;
		}
		if (known.level() > BuilderLevels.level(chef)) {
			return Result.LEVEL;
		}
		return get(level).add(hall, known.id(), count) > 0 ? Result.ASKED : Result.FULL;
	}

	/** What a request's result says to the player who asked. */
	public static Component message(Result result, Item dish, int count, int waiting) {
		CuisineDishes.Dish known = CuisineDishes.of(dish);
		Component name = dish.getDescription();
		return switch (result) {
			case ASKED -> Component.translatable("screen.aliveworkplace.dishes.asked", count, name, waiting);
			case NO_CHEF -> Component.translatable("screen.aliveworkplace.dishes.no_chef");
			case LEVEL -> Component.translatable("screen.aliveworkplace.dishes.level", name, levelName(known == null ? 1 : known.level()));
			case FULL -> Component.translatable("screen.aliveworkplace.dishes.full", MAX, name);
			case UNKNOWN -> Component.translatable("screen.aliveworkplace.dishes.unknown");
		};
	}

	/** A job level's name as the trade screen says it: "Novice", "Expert". */
	public static Component levelName(int level) {
		return Component.translatable("merchant.level." + level);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		waiting.forEach((hall, map) -> map.forEach((dish, count) -> {
			CompoundTag e = new CompoundTag();
			e.putLong("hall", hall);
			e.putString("dish", dish.toString());
			e.putInt("count", count);
			list.add(e);
		}));
		tag.put("requests", list);
		return tag;
	}

	public static DishRequests load(CompoundTag tag, HolderLookup.Provider registries) {
		DishRequests data = new DishRequests();
		ListTag list = Nbt.getList(tag, "requests", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = Nbt.compoundAt(list, i);
			ResourceLocation dish = ResourceLocation.tryParse(Nbt.getString(e, "dish"));
			int count = Nbt.getInt(e, "count");
			if (dish != null && count > 0) {
				data.waiting.computeIfAbsent(Nbt.getLong(e, "hall"), k -> new LinkedHashMap<>()).put(dish, Math.min(MAX, count));
			}
		}
		return data;
	}

	/** A copy through save and load (tests: what a reload brings back). */
	public DishRequests reloaded(HolderLookup.Provider registries) {
		return load(save(new CompoundTag(), registries), registries);
	}
}
