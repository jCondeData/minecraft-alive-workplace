package io.github.jcondedata.aliveworkplace.build;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A builder's personal inventory, separate from the vanilla villager inventory (which villagers use
 * for food and breeding). Mutable; saved with the villager through a Fabric data attachment.
 */
public final class BuilderBag {
	/** The stacks a bag holds (a builder's, with Engineering while the Great Library stands, holds more: {@link #setSlots}). */
	public static final int SLOTS = 27;
	/** The most a bag ever holds: {@link #SLOTS} and Engineering II's 18 more (35.14). */
	public static final int MAX_SLOTS = SLOTS + 18;
	public static final Codec<BuilderBag> CODEC = ItemStack.CODEC.listOf().xmap(BuilderBag::of, BuilderBag::stacks);

	private final SimpleContainer items = new SimpleContainer(MAX_SLOTS);
	/** How many of the slots take new stacks now (not saved: the builder's work sets it each round from Engineering). */
	private int slots = SLOTS;

	public static BuilderBag of(List<ItemStack> stacks) {
		BuilderBag bag = new BuilderBag();
		for (ItemStack s : stacks) {
			bag.put(s.copy(), MAX_SLOTS); // all of a saved bag comes back, whatever its room now
		}
		return bag;
	}

	/** How many stacks the bag takes now. */
	public int slots() {
		return slots;
	}

	/**
	 * Lets the bag take {@code n} stacks ({@link #SLOTS} to {@link #MAX_SLOTS}). Stacks already in slots above it stay
	 * (nothing is lost when Engineering's bonus pauses); only new stacks keep to it.
	 */
	public void setSlots(int n) {
		slots = Math.max(1, Math.min(MAX_SLOTS, n));
	}

	/** Puts {@code stack} into stacks of its kind first, then empty slots below {@code limit}; returns what didn't fit. */
	private ItemStack put(ItemStack stack, int limit) {
		ItemStack rest = stack.copy();
		for (int i = 0; i < items.getContainerSize() && !rest.isEmpty(); i++) {
			ItemStack s = items.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rest)) {
				int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
				if (move > 0) {
					s.grow(move);
					rest.shrink(move);
				}
			}
		}
		for (int i = 0; i < Math.min(limit, items.getContainerSize()) && !rest.isEmpty(); i++) {
			if (items.getItem(i).isEmpty()) {
				items.setItem(i, rest.copy());
				rest = ItemStack.EMPTY;
			}
		}
		items.setChanged();
		return rest;
	}

	public List<ItemStack> stacks() {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < items.getContainerSize(); i++) {
			ItemStack s = items.getItem(i);
			if (!s.isEmpty()) {
				out.add(s.copy());
			}
		}
		return out;
	}

	public int count(Item item) {
		return items.countItem(item);
	}

	public boolean has(Item item, int amount) {
		return count(item) >= amount;
	}

	/** Removes up to {@code amount}; returns how many were removed. */
	public int remove(Item item, int amount) {
		return items.removeItemType(item, amount).getCount();
	}

	/** Adds a stack (any size); returns what did not fit. */
	public ItemStack add(ItemStack stack) {
		ItemStack rest = stack.copy();
		int max = Math.max(1, rest.getMaxStackSize());
		while (!rest.isEmpty()) {
			ItemStack chunk = rest.split(Math.min(max, rest.getCount()));
			ItemStack left = put(chunk, slots);
			if (!left.isEmpty()) {
				left.grow(rest.getCount());
				return left;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Adds {@code count} plain items; returns how many did not fit. */
	public int addAll(Item item, int count) {
		return add(new ItemStack(item, count)).getCount();
	}

	/** How many of {@code item} still fit. */
	public int spaceFor(Item item) {
		int max = item.getDefaultMaxStackSize();
		int space = 0;
		for (int i = 0; i < items.getContainerSize(); i++) {
			ItemStack s = items.getItem(i);
			if (s.isEmpty()) {
				space += i < slots ? max : 0;
			} else if (s.is(item) && s.getComponentsPatch().isEmpty()) {
				space += Math.max(0, max - s.getCount());
			}
		}
		return space;
	}

	/** Empty slots the bag takes stacks into now (below {@link #slots}). */
	public int freeSlots() {
		int free = 0;
		for (int i = 0; i < slots; i++) {
			if (items.getItem(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	/** Removes and returns every stack whose item is not in {@code keep}. */
	public List<ItemStack> takeAllExcept(Set<Item> keep) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < items.getContainerSize(); i++) {
			ItemStack s = items.getItem(i);
			if (!s.isEmpty() && !keep.contains(s.getItem())) {
				out.add(items.removeItemNoUpdate(i));
			}
		}
		return out;
	}

	/** Removes and returns the first stack that passes {@code test} (empty if none does). */
	public ItemStack takeFirst(java.util.function.Predicate<ItemStack> test) {
		for (int i = 0; i < items.getContainerSize(); i++) {
			ItemStack s = items.getItem(i);
			if (!s.isEmpty() && test.test(s)) {
				return items.removeItemNoUpdate(i);
			}
		}
		return ItemStack.EMPTY;
	}

	public List<ItemStack> takeAll() {
		return takeAllExcept(Set.of());
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}
}
