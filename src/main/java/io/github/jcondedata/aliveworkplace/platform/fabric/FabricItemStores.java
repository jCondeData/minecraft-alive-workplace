package io.github.jcondedata.aliveworkplace.platform.fabric;

import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** {@link ItemStores} through Fabric's transfer API. */
final class FabricItemStores implements ItemStores {
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
				// Integer.MAX_VALUE, not Long.MAX_VALUE: some storages (Sophisticated Storage) turn the amount into an int.
				total += StorageUtil.simulateExtract(s, variant, Integer.MAX_VALUE, null);
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
			try (Transaction tx = Transaction.openOuter()) {
				taken += (int) s.extract(variant, max - taken, tx);
				tx.commit();
			}
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
			try (Transaction tx = Transaction.openOuter()) {
				taken += (int) s.extract(variant, max - taken, tx);
				tx.commit();
			}
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
			try (Transaction tx = Transaction.openOuter()) {
				remaining -= s.insert(variant, remaining, tx);
				tx.commit();
			}
		}
		return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount((int) remaining);
	}
}
