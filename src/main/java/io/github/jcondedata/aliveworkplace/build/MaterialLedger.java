package io.github.jcondedata.aliveworkplace.build;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Counts every item builders take out of the world's stock or add to it, so the builder soak test (23.1) can prove no
 * item was duplicated or lost: what was stocked, plus what builders gained (drops from clearing, items given back by
 * taking a block down, a finished build's blueprint), minus what they built in and what they dropped on the ground,
 * must equal what is left in chests and bags. Off (and free) unless a soak switched it on.
 */
public final class MaterialLedger {
	private static boolean on;
	private static final Map<Item, Integer> USED = new HashMap<>();
	private static final Map<Item, Integer> GAINED = new HashMap<>();
	private static final Map<Item, Integer> DROPPED = new HashMap<>();
	/** Meals villagers ate from the village's store (27.22: the city soak's storehouse feeds the village too). */
	private static final Map<Item, Integer> EATEN = new HashMap<>();

	private MaterialLedger() {
	}

	/** Starts counting from zero. */
	public static void start() {
		USED.clear();
		GAINED.clear();
		DROPPED.clear();
		EATEN.clear();
		on = true;
	}

	public static void stop() {
		on = false;
	}

	public static boolean isOn() {
		return on;
	}

	/** Items built into the world. */
	static void used(Item item, int count) {
		add(USED, item, count);
	}

	/** Items that came into a builder's hands from outside the stock. */
	static void gained(ItemStack stack) {
		add(GAINED, stack.getItem(), stack.getCount());
	}

	static void gained(Item item, int count) {
		add(GAINED, item, count);
	}

	/**
	 * A free conversion within a family ({@link MaterialFamilies}: Rechiseled turns andesite into polished andesite at no
	 * cost): {@code from} taken out of the stock, {@code to} gained. Without it the soak counted andesite -1 and polished
	 * andesite +1 (B31).
	 */
	static void converted(Item from, Item to, int count) {
		add(USED, from, count);
		add(GAINED, to, count);
	}

	/** Items a builder dropped on the ground (nowhere to put them). */
	static void dropped(ItemStack stack) {
		add(DROPPED, stack.getItem(), stack.getCount());
	}

	/** A meal a villager ate from the store. */
	public static void eaten(ItemStack meal) {
		add(EATEN, meal.getItem(), meal.getCount());
	}

	private static void add(Map<Item, Integer> map, Item item, int count) {
		if (on && count > 0) {
			map.merge(item, count, Integer::sum);
		}
	}

	public static Map<Item, Integer> used() {
		return Map.copyOf(USED);
	}

	public static Map<Item, Integer> gained() {
		return Map.copyOf(GAINED);
	}

	public static Map<Item, Integer> dropped() {
		return Map.copyOf(DROPPED);
	}

	public static Map<Item, Integer> eaten() {
		return Map.copyOf(EATEN);
	}
}
