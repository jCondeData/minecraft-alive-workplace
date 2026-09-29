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
	public static final int SLOTS = 27;
	public static final Codec<BuilderBag> CODEC = ItemStack.CODEC.listOf().xmap(BuilderBag::of, BuilderBag::stacks);

	private final SimpleContainer items = new SimpleContainer(SLOTS);

	public static BuilderBag of(List<ItemStack> stacks) {
		BuilderBag bag = new BuilderBag();
		for (ItemStack s : stacks) {
			bag.items.addItem(s.copy());
		}
		return bag;
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
			ItemStack left = items.addItem(chunk);
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
				space += max;
			} else if (s.is(item) && s.getComponentsPatch().isEmpty()) {
				space += Math.max(0, max - s.getCount());
			}
		}
		return space;
	}

	public int freeSlots() {
		int free = 0;
		for (int i = 0; i < items.getContainerSize(); i++) {
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
