package io.github.jcondedata.aliveworkplace.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The builder's supply stash: every storage block near the Builder's Bench. Goes through Fabric's
 * transfer API, so modded storage (Sophisticated Storage chests and barrels, Tom's filing cabinets...)
 * works too; storage-network access points are skipped so nothing is counted twice.
 */
public final class SupplyContainers {
	/** Horizontal search radius around the bench. */
	public static int RADIUS = 8;
	private static final int VERTICAL = 4;

	/**
	 * Storage positions near {@code bench}, nearest first, skipping any inside {@code exclude}. Furnaces are not
	 * storage (anything could end up in their input slot); miners tend them on purpose, see {@link #furnaces}.
	 */
	public static List<BlockPos> find(ServerLevel level, BlockPos bench, @Nullable BoundingBox exclude) {
		return near(level, bench, be -> !(exclude != null && exclude.isInside(be.getBlockPos()))
			&& !(be instanceof io.github.jcondedata.aliveworkplace.work.PrivateContainer)
			&& !(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)
			&& !ModdedBlocks.isStorageNetwork(be.getBlockState().getBlock())
			&& storage(level, be.getBlockPos()) != null);
	}

	/** Furnaces, blast furnaces and smokers near {@code bench}, nearest first. */
	public static List<BlockPos> furnaces(ServerLevel level, BlockPos bench) {
		return near(level, bench, be -> be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity);
	}

	private static List<BlockPos> near(ServerLevel level, BlockPos bench, java.util.function.Predicate<BlockEntity> keep) {
		List<BlockPos> found = new ArrayList<>();
		int minCx = SectionPos.blockToSectionCoord(bench.getX() - RADIUS), maxCx = SectionPos.blockToSectionCoord(bench.getX() + RADIUS);
		int minCz = SectionPos.blockToSectionCoord(bench.getZ() - RADIUS), maxCz = SectionPos.blockToSectionCoord(bench.getZ() + RADIUS);
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(cx, cz);
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos p = be.getBlockPos();
					if (Math.abs(p.getX() - bench.getX()) > RADIUS || Math.abs(p.getZ() - bench.getZ()) > RADIUS || Math.abs(p.getY() - bench.getY()) > VERTICAL) {
						continue;
					}
					if (keep.test(be) && !otherHalfFound(be, found)) {
						found.add(p.immutable());
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(bench)));
		return found;
	}

	/** A double chest is one container: only one of its halves is listed (both halves hold the same items). */
	private static boolean otherHalfFound(BlockEntity be, List<BlockPos> found) {
		net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
		if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
			|| !state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)
			|| state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
			return false;
		}
		return found.contains(be.getBlockPos().relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)));
	}

	/** Empty slots across the containers (a rough measure of room: stacks that are there can still grow). */
	public static int freeSlots(ServerLevel level, List<BlockPos> containers) {
		int free = 0;
		for (BlockPos p : containers) {
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

	@Nullable
	private static Storage<ItemVariant> storage(ServerLevel level, BlockPos pos) {
		return ItemStorage.SIDED.find(level, pos, null);
	}

	/** How many of {@code item} are available across all containers. */
	public static long count(ServerLevel level, List<BlockPos> containers, Item item) {
		ItemVariant variant = ItemVariant.of(item);
		long total = 0;
		for (BlockPos p : containers) {
			Storage<ItemVariant> s = storage(level, p);
			if (s != null) {
				// Integer.MAX_VALUE, not Long.MAX_VALUE: some storages (Sophisticated Storage) turn the amount into an int.
				total += StorageUtil.simulateExtract(s, variant, Integer.MAX_VALUE, null);
			}
		}
		return total;
	}

	/** How many of each plain item (no extra components) the containers hold together. */
	public static java.util.Map<Item, Long> contents(ServerLevel level, List<BlockPos> containers) {
		java.util.Map<Item, Long> out = new java.util.HashMap<>();
		for (BlockPos p : containers) {
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

	/** First container (in list order) holding at least one {@code item}. */
	@Nullable
	public static BlockPos firstWith(ServerLevel level, List<BlockPos> containers, Item item) {
		ItemVariant variant = ItemVariant.of(item);
		for (BlockPos p : containers) {
			Storage<ItemVariant> s = storage(level, p);
			if (s != null && StorageUtil.simulateExtract(s, variant, 1, null) > 0) {
				return p;
			}
		}
		return null;
	}

	/** Takes up to {@code max} of {@code item}, nearest containers first. Returns the amount taken. */
	public static int extract(ServerLevel level, List<BlockPos> containers, Item item, int max) {
		ItemVariant variant = ItemVariant.of(item);
		int taken = 0;
		for (BlockPos p : containers) {
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

	/** First container holding an item that matches {@code test} (tools with damage, enchantments... included). */
	@Nullable
	public static BlockPos firstMatching(ServerLevel level, List<BlockPos> containers, java.util.function.Predicate<ItemStack> test) {
		for (BlockPos p : containers) {
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

	/** Takes one item matching {@code test} (keeping its damage and enchantments); empty if there is none. */
	public static ItemStack takeOne(ServerLevel level, List<BlockPos> containers, java.util.function.Predicate<ItemStack> test) {
		for (BlockPos p : containers) {
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

	/** How many items exactly like {@code template} (same components) the containers hold. */
	public static long countMatching(ServerLevel level, List<BlockPos> containers, ItemStack template) {
		ItemVariant variant = ItemVariant.of(template);
		long total = 0;
		for (BlockPos p : containers) {
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

	/** Takes up to {@code max} items exactly like {@code template}. Returns the amount taken. */
	public static int extractMatching(ServerLevel level, List<BlockPos> containers, ItemStack template, int max) {
		ItemVariant variant = ItemVariant.of(template);
		int taken = 0;
		for (BlockPos p : containers) {
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

	/** Takes up to {@code maxStacks} stacks of items matching {@code test} out of the container at {@code pos}. */
	public static List<ItemStack> takeMatching(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> test, int maxStacks) {
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

	/** Copies of the stacks in the container at {@code pos} that match {@code test} (nothing is taken). */
	public static List<ItemStack> peekMatching(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> test) {
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

	/** True if the container at {@code pos} holds anything matching {@code test}. */
	public static boolean hasMatching(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> test) {
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

	/** Puts a stack into the containers; returns whatever did not fit. */
	public static ItemStack insert(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemVariant variant = ItemVariant.of(stack);
		long remaining = stack.getCount();
		for (BlockPos p : containers) {
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

	private SupplyContainers() {
	}
}
