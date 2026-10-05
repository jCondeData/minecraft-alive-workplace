package io.github.jcondedata.aliveworkplace.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
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
 * The builder's supply stash: every storage block near the Builder's Bench. Goes through the loader's item storage
 * ({@link ItemStores}: Fabric's transfer API), so modded storage (Sophisticated Storage chests and barrels, Tom's filing cabinets...)
 * works too; storage-network access points are skipped so nothing is counted twice.
 */
public final class SupplyContainers {
	/** Horizontal search radius around the bench. */
	public static int RADIUS = 8;
	private static final int VERTICAL = 4;
	private static final ItemStores STORES = Platform.get().items();

	/**
	 * Storage positions near {@code bench}, nearest first, skipping any inside {@code exclude}. Furnaces are not
	 * storage (anything could end up in their input slot); miners tend them on purpose, see {@link #furnaces}.
	 */
	public static List<BlockPos> find(ServerLevel level, BlockPos bench, @Nullable BoundingBox exclude) {
		return near(level, bench, be -> !(exclude != null && exclude.isInside(be.getBlockPos()))
			&& !(be instanceof io.github.jcondedata.aliveworkplace.work.PrivateContainer)
			&& !(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)
			&& !isMachine(be)
			&& !ModdedBlocks.isStorageNetwork(be.getBlockState().getBlock())
			&& STORES.isStore(level, be.getBlockPos()));
	}

	/** Storage inside {@code box} (a building: a Legend's home chests, ROADMAP 29.5), the same kinds {@link #find} lists. */
	public static List<BlockPos> inside(ServerLevel level, BoundingBox box) {
		List<BlockPos> found = new ArrayList<>();
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (box.isInside(be.getBlockPos()) && !(be instanceof io.github.jcondedata.aliveworkplace.work.PrivateContainer)
						&& !(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) && !isMachine(be)
						&& !ModdedBlocks.isStorageNetwork(be.getBlockState().getBlock()) && STORES.isStore(level, be.getBlockPos())
						&& !otherHalfFound(be, found)) {
						found.add(be.getBlockPos().immutable());
					}
				}
			}
		}
		return found;
	}

	/**
	 * Blocks that hold items for their own work, not as storage: a brewing stand's bottles, a jukebox's disc, a lectern's
	 * book, a crafter's grid.
	 */
	static boolean isMachine(BlockEntity be) {
		return be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity;
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
		return STORES.freeSlots(level, containers);
	}

	/** How many of {@code item} are available across all containers. */
	public static long count(ServerLevel level, List<BlockPos> containers, Item item) {
		return STORES.count(level, containers, item);
	}

	/** How many of each plain item (no extra components) the containers hold together. */
	public static java.util.Map<Item, Long> contents(ServerLevel level, List<BlockPos> containers) {
		return STORES.contents(level, containers);
	}

	/** First container (in list order) holding at least one {@code item}. */
	@Nullable
	public static BlockPos firstWith(ServerLevel level, List<BlockPos> containers, Item item) {
		return STORES.firstWith(level, containers, item);
	}

	/** Takes up to {@code max} of {@code item}, nearest containers first. Returns the amount taken. */
	public static int extract(ServerLevel level, List<BlockPos> containers, Item item, int max) {
		return STORES.extract(level, containers, item, max);
	}

	/** First container holding an item that matches {@code test} (tools with damage, enchantments... included). */
	@Nullable
	public static BlockPos firstMatching(ServerLevel level, List<BlockPos> containers, java.util.function.Predicate<ItemStack> test) {
		return STORES.firstMatching(level, containers, test);
	}

	/** Takes one item matching {@code test} (keeping its damage and enchantments); empty if there is none. */
	public static ItemStack takeOne(ServerLevel level, List<BlockPos> containers, java.util.function.Predicate<ItemStack> test) {
		return STORES.takeOne(level, containers, test);
	}

	/** How many items exactly like {@code template} (same components) the containers hold. */
	public static long countMatching(ServerLevel level, List<BlockPos> containers, ItemStack template) {
		return STORES.countMatching(level, containers, template);
	}

	/** Takes up to {@code max} items exactly like {@code template}. Returns the amount taken. */
	public static int extractMatching(ServerLevel level, List<BlockPos> containers, ItemStack template, int max) {
		return STORES.extractMatching(level, containers, template, max);
	}

	/** Takes up to {@code maxStacks} stacks of items matching {@code test} out of the container at {@code pos}. */
	public static List<ItemStack> takeMatching(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> test, int maxStacks) {
		return STORES.takeMatching(level, pos, test, maxStacks);
	}

	/** Copies of the stacks in the container at {@code pos} that match {@code test} (nothing is taken). */
	public static List<ItemStack> peekMatching(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> test) {
		return STORES.peekMatching(level, pos, test);
	}

	/** True if the container at {@code pos} holds anything matching {@code test}. */
	public static boolean hasMatching(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> test) {
		return STORES.hasMatching(level, pos, test);
	}

	/** Puts a stack into the containers; returns whatever did not fit. */
	public static ItemStack insert(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
		return STORES.insert(level, containers, stack);
	}

	private SupplyContainers() {
	}
}
