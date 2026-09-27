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
 * transfer API, so modded storage (Sophisticated Storage, Tom's Storage, ...) works too.
 */
public final class SupplyContainers {
	/** Horizontal search radius around the bench. */
	public static final int RADIUS = 8;
	private static final int VERTICAL = 4;

	/** Storage positions near {@code bench}, nearest first, skipping any inside {@code exclude}. */
	public static List<BlockPos> find(ServerLevel level, BlockPos bench, @Nullable BoundingBox exclude) {
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
					if (exclude != null && exclude.isInside(p)) {
						continue;
					}
					if (storage(level, p) != null) {
						found.add(p.immutable());
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(bench)));
		return found;
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
				total += StorageUtil.simulateExtract(s, variant, Long.MAX_VALUE, null);
			}
		}
		return total;
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
