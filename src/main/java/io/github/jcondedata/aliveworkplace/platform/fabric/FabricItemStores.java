package io.github.jcondedata.aliveworkplace.platform.fabric;

import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** {@link ItemStores} through Fabric's transfer API. */
final class FabricItemStores implements ItemStores {
	/** How often one store is asked in a single take or put (an infinity or void upgrade never runs dry or full). */
	private static final int MAX_ASKS = 256;

	@Nullable
	private static Storage<ItemVariant> storage(Level level, BlockPos pos) {
		return ItemStorage.SIDED.find(level, pos, null);
	}

	@Override
	public boolean isStore(Level level, BlockPos pos) {
		return storage(level, pos) != null;
	}

	@Override
	public boolean isStore(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		return ItemStorage.SIDED.find(level, pos, state, blockEntity, null) != null;
	}

	@Override
	public Object identity(Level level, BlockPos pos) {
		Storage<ItemVariant> s = storage(level, pos);
		return s != null ? s : pos.immutable();
	}

	@Override
	public int freeSlots(Level level, List<BlockPos> stores) {
		int free = 0;
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			for (var view : s) {
				if (view.isResourceBlank()) {
					free++;
				}
			}
		}
		return free;
	}

	/**
	 * Plain stackable items the empty slots' room is tried with, the first one the store holds none of: a Sophisticated
	 * controller puts an item it already holds into the barrel holding it once for each way it looks (that stack, that
	 * item, any empty slot), and inside one transaction the barrel takes the same room each time, so a held item's tried
	 * room counts that barrel two or three times over (B371: 22164 for 8540).
	 */
	private static final List<Item> PROBES = List.of(Items.COBBLESTONE, Items.DIRT, Items.GRAVEL, Items.SAND, Items.ANDESITE,
		Items.DIORITE, Items.GRANITE, Items.COBBLED_DEEPSLATE, Items.NETHERRACK, Items.END_STONE);

	@Override
	public Room room(Level level, List<BlockPos> stores) {
		int freeSlots = 0;
		int freeStacks = 0;
		long freeItems = 0;
		boolean bigSlots = false;
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			int blanks = 0;
			long blankCapacity = 0;
			boolean bigBlank = false;
			Set<Item> kinds = new HashSet<>(); // every item it holds, with or without components
			long cobblestoneRoom = 0; // the room for more cobblestone in slots already holding some
			for (var view : s) {
				long capacity = view.getCapacity();
				if (view.isResourceBlank()) {
					blanks++;
					blankCapacity += Math.max(0, capacity);
					bigBlank |= capacity > 64;
				} else {
					long stack = Math.max(1, view.getResource().getItem().getDefaultMaxStackSize());
					long left = Math.max(0, capacity - view.getAmount());
					freeStacks += (int) Math.min(Integer.MAX_VALUE / 2, left / stack);
					freeItems += left;
					bigSlots |= capacity > stack;
					kinds.add(view.getResource().getItem());
					if (view.getResource().equals(ItemVariant.of(Items.COBBLESTONE))) {
						cobblestoneRoom += left;
					}
				}
			}
			freeSlots += blanks;
			if (blanks == 0) {
				continue;
			}
			if (!bigBlank) {
				freeStacks += blanks; // vanilla: an empty slot is a stack
				freeItems += blankCapacity;
				continue;
			}
			// An empty slot's capacity isn't its room everywhere: Sophisticated Storage reports a stack-upgraded empty
			// slot at four times what it takes of cobblestone, and putting into one slot fills others too. So the empty
			// slots' room is what the store would take of a plain stackable item it holds none of (tried and undone).
			// Only when it holds some of every probe is it tried with cobblestone, less the room for it in the slots
			// already holding some. A store that refuses it (a filter) counts a stack an empty slot.
			Item probe = PROBES.stream().filter(i -> !kinds.contains(i)).findFirst().orElse(Items.COBBLESTONE);
			long room;
			try (Transaction tx = Transaction.openOuter()) {
				room = s.insert(ItemVariant.of(probe), Integer.MAX_VALUE, tx) - (kinds.contains(probe) ? cobblestoneRoom : 0);
			}
			if (room <= 0) {
				room = 64L * blanks;
			}
			freeStacks += (int) Math.min(Integer.MAX_VALUE / 2, Math.max(blanks, room / 64));
			freeItems += room;
			bigSlots |= room > 64L * blanks;
		}
		return new Room(freeSlots, freeStacks, freeItems, bigSlots);
	}

	/**
	 * Takes up to {@code max} out of {@code s}, each ask its own committed transaction, asking again while it gives:
	 * Sophisticated Storage gives at most a stack from a slot each time it's asked (a limited barrel's slot of 110 raw
	 * iron gave 64), and asking twice inside one transaction gives the same stack twice, so every ask is committed.
	 */
	private static long drain(Storage<ItemVariant> s, ItemVariant variant, long max) {
		long taken = 0;
		for (int asks = 0; asks < MAX_ASKS && taken < max; asks++) {
			long got;
			try (Transaction tx = Transaction.openOuter()) {
				got = s.extract(variant, Math.min(max - taken, Integer.MAX_VALUE), tx);
				tx.commit();
			}
			if (got <= 0) {
				break;
			}
			taken += got;
		}
		return taken;
	}

	/**
	 * Puts up to {@code amount} into {@code s}, asking again while it takes (as {@link #drain}). Each ask counts what
	 * really landed when that is less than the store says it took: a Sophisticated controller puts an item it holds into
	 * the barrel holding it once for each way it looks, and inside one transaction the barrel takes the same room each
	 * time, so a put that overflows that barrel was reported whole while the overflow went nowhere (B371: 8540 reported,
	 * 6812 stored). Asked again, the controller puts the rest into the next barrel. A store that takes items without
	 * holding more (a void upgrade) still counts what it says it took.
	 */
	private static long fill(Storage<ItemVariant> s, ItemVariant variant, long amount) {
		long put = 0;
		for (int asks = 0; asks < MAX_ASKS && put < amount; asks++) {
			long before = held(s, variant);
			long got;
			try (Transaction tx = Transaction.openOuter()) {
				got = s.insert(variant, Math.min(amount - put, Integer.MAX_VALUE), tx);
				tx.commit();
			}
			if (got <= 0) {
				break;
			}
			long landed = held(s, variant) - before;
			put += landed > 0 ? Math.min(got, landed) : got;
		}
		return put;
	}

	/** How many of {@code variant} the slots of {@code s} hold. */
	private static long held(Storage<ItemVariant> s, ItemVariant variant) {
		long total = 0;
		for (var view : s.nonEmptyViews()) {
			if (view.getResource().equals(variant)) {
				total += view.getAmount();
			}
		}
		return total;
	}

	@Override
	public int slots(Level level, List<BlockPos> stores) {
		int slots = 0;
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			for (var ignored : s) {
				slots++;
			}
		}
		return slots;
	}

	@Override
	public long count(Level level, List<BlockPos> stores, Item item) {
		ItemVariant variant = ItemVariant.of(item);
		long total = 0;
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s != null) {
				// The slots' amounts, not a simulated take: Sophisticated Storage gives at most a stack a slot each time.
				for (var view : s.nonEmptyViews()) {
					if (view.getResource().equals(variant)) {
						total += view.getAmount();
					}
				}
			}
		}
		return total;
	}

	@Override
	public Map<Item, Long> contents(Level level, List<BlockPos> stores) {
		Map<Item, Long> out = new HashMap<>();
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			for (var view : s.nonEmptyViews()) {
				ItemVariant variant = view.getResource();
				if (variant.getComponents().isEmpty()) {
					out.merge(variant.getItem(), view.getAmount(), Long::sum);
				}
			}
		}
		return out;
	}

	@Override
	@Nullable
	public BlockPos firstWith(Level level, List<BlockPos> stores, Item item) {
		ItemVariant variant = ItemVariant.of(item);
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s != null && StorageUtil.simulateExtract(s, variant, 1, null) > 0) {
				return p;
			}
		}
		return null;
	}

	@Override
	public int extract(Level level, List<BlockPos> stores, Item item, int max) {
		ItemVariant variant = ItemVariant.of(item);
		int taken = 0;
		for (BlockPos p : stores) {
			if (taken >= max) {
				break;
			}
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			taken += (int) drain(s, variant, max - taken);
		}
		return taken;
	}

	@Override
	@Nullable
	public BlockPos firstMatching(Level level, List<BlockPos> stores, Predicate<ItemStack> test) {
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			for (var view : s.nonEmptyViews()) {
				if (test.test(view.getResource().toStack())) {
					return p;
				}
			}
		}
		return null;
	}

	@Override
	public ItemStack takeOne(Level level, List<BlockPos> stores, Predicate<ItemStack> test) {
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			for (var view : s.nonEmptyViews()) {
				ItemVariant variant = view.getResource();
				if (!test.test(variant.toStack())) {
					continue;
				}
				try (Transaction tx = Transaction.openOuter()) {
					if (view.extract(variant, 1, tx) == 1) {
						tx.commit();
						return variant.toStack(1);
					}
				}
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public long countMatching(Level level, List<BlockPos> stores, ItemStack template) {
		ItemVariant variant = ItemVariant.of(template);
		long total = 0;
		for (BlockPos p : stores) {
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			for (var view : s.nonEmptyViews()) {
				if (view.getResource().equals(variant)) {
					total += view.getAmount();
				}
			}
		}
		return total;
	}

	@Override
	public int extractMatching(Level level, List<BlockPos> stores, ItemStack template, int max) {
		ItemVariant variant = ItemVariant.of(template);
		int taken = 0;
		for (BlockPos p : stores) {
			if (taken >= max) {
				break;
			}
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			taken += (int) drain(s, variant, max - taken);
		}
		return taken;
	}

	@Override
	public List<ItemStack> takeMatching(Level level, BlockPos pos, Predicate<ItemStack> test, int maxStacks) {
		List<ItemStack> out = new ArrayList<>();
		Storage<ItemVariant> s = storage(level, pos);
		if (s == null) {
			return out;
		}
		for (var view : s.nonEmptyViews()) {
			if (out.size() >= maxStacks) {
				break;
			}
			ItemVariant variant = view.getResource();
			ItemStack sample = variant.toStack();
			if (!test.test(sample)) {
				continue;
			}
			try (Transaction tx = Transaction.openOuter()) {
				long got = view.extract(variant, Math.min(view.getAmount(), sample.getMaxStackSize()), tx);
				tx.commit();
				if (got > 0) {
					out.add(variant.toStack((int) got));
				}
			}
		}
		return out;
	}

	@Override
	public List<ItemStack> peekMatching(Level level, BlockPos pos, Predicate<ItemStack> test) {
		List<ItemStack> out = new ArrayList<>();
		Storage<ItemVariant> s = storage(level, pos);
		if (s == null) {
			return out;
		}
		for (var view : s.nonEmptyViews()) {
			ItemStack sample = view.getResource().toStack((int) Math.min(view.getAmount(), 64));
			if (test.test(sample)) {
				out.add(sample);
			}
		}
		return out;
	}

	@Override
	public boolean hasMatching(Level level, BlockPos pos, Predicate<ItemStack> test) {
		Storage<ItemVariant> s = storage(level, pos);
		if (s == null) {
			return false;
		}
		for (var view : s.nonEmptyViews()) {
			if (test.test(view.getResource().toStack())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public ItemStack insert(Level level, List<BlockPos> stores, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemVariant variant = ItemVariant.of(stack);
		long remaining = stack.getCount();
		for (BlockPos p : stores) {
			if (remaining <= 0) {
				break;
			}
			Storage<ItemVariant> s = storage(level, p);
			if (s == null) {
				continue;
			}
			remaining -= fill(s, variant, remaining);
		}
		return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount((int) remaining);
	}

	/** Fabric counts fluids in droplets: 81 to the millibucket. */
	private static final long DROPLETS_PER_MB = net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET / 1000;

	@Override
	public long fluidMb(Level level, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
		Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> s = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, null);
		if (s == null) {
			return 0;
		}
		long droplets = 0;
		for (var view : s) {
			if (!view.isResourceBlank() && view.getResource().isOf(fluid)) {
				droplets += view.getAmount();
			}
		}
		return droplets / DROPLETS_PER_MB;
	}

	@Override
	public long pourFluid(Level level, BlockPos pos, net.minecraft.world.level.material.Fluid fluid, long mb) {
		Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> s = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, null);
		if (s == null || mb <= 0) {
			return 0;
		}
		try (Transaction tx = Transaction.openOuter()) {
			long got = s.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid), mb * DROPLETS_PER_MB, tx);
			tx.commit();
			return got / DROPLETS_PER_MB;
		}
	}
}
