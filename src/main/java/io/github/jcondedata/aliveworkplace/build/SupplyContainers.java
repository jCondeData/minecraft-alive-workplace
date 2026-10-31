package io.github.jcondedata.aliveworkplace.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
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
 * works too; storage-network access points are skipped so nothing is counted twice, except that the Storehouse reads a
 * Sophisticated Storage controller (ROADMAP 37.9) or a Tom's Simple Storage inventory connector (37.10) as one store for
 * the inventories it links ({@link StorageNetworks}).
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
	/** Whether a store at {@code pos} is close enough to {@code bench} for {@link #find} to list it (B696). */
	public static boolean inReach(BlockPos bench, BlockPos pos) {
		return Math.abs(pos.getX() - bench.getX()) <= RADIUS && Math.abs(pos.getZ() - bench.getZ()) <= RADIUS
			&& Math.abs(pos.getY() - bench.getY()) <= VERTICAL;
	}

	public static List<BlockPos> find(ServerLevel level, BlockPos bench, @Nullable BoundingBox exclude) {
		List<BlockPos> found = stores(level, bench, exclude);
		return isStorehouseWithNetworks(level, bench) ? folded(level, bench, found, exclude).stores() : found;
	}

	/**
	 * 37.9 and 37.10: around a Storehouse, with {@code moddedStorageNetworks} on, a Sophisticated Storage controller
	 * stands for the storage blocks it links and a Tom's Simple Storage inventory connector for the inventories it joins
	 * (each one store, read and filled through the controller or connector), which leave the list. Every other worker's
	 * chests are read one by one, as before. {@code linked()} counts the controllers' blocks only.
	 */
	public static io.github.jcondedata.aliveworkplace.store.StorageNetworks.Folded networks(ServerLevel level, BlockPos storehouse) {
		List<BlockPos> found = stores(level, storehouse, null);
		return isStorehouseWithNetworks(level, storehouse) ? folded(level, storehouse, found, null)
			: new io.github.jcondedata.aliveworkplace.store.StorageNetworks.Folded(found, 0);
	}

	private static boolean isStorehouseWithNetworks(ServerLevel level, BlockPos at) {
		return io.github.jcondedata.aliveworkplace.store.StorageNetworks.ENABLED && level.isLoaded(at)
			&& level.getBlockState(at).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE);
	}

	private static io.github.jcondedata.aliveworkplace.store.StorageNetworks.Folded folded(ServerLevel level, BlockPos storehouse, List<BlockPos> found,
		@Nullable BoundingBox exclude) {
		// 37.10: a storage network's head (a Tom's inventory connector) stands for the inventories it joins
		List<BlockPos> heads = near(level, storehouse, be -> !(exclude != null && exclude.isInside(be.getBlockPos()))
			&& StorageNetworks.isHead(level, be.getBlockPos()) && STORES.isStore(level, be.getBlockPos()));
		found = StorageNetworks.asStorehouse(level, storehouse, found, heads);
		// 37.9: a Sophisticated Storage controller stands for the storage blocks it links
		List<BlockPos> controllers = near(level, storehouse, be -> io.github.jcondedata.aliveworkplace.store.StorageNetworks.isController(be.getBlockState())
			&& STORES.isStore(level, be.getBlockPos()));
		return io.github.jcondedata.aliveworkplace.store.StorageNetworks.fold(level, storehouse, found, controllers, exclude);
	}

	private static List<BlockPos> stores(ServerLevel level, BlockPos bench, @Nullable BoundingBox exclude) {
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
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // loaded only, and without waiting (B103)
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
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
	 * book, a crafter's grid, Create's machines and toolboxes (39.2), a Cobblemon Pasture Block's eggs (Cobbreeding makes it a container for the eggs it lays,
	 * so a keeper putting things away would fill her own pasture instead of her chest).
	 */
	static boolean isMachine(BlockEntity be) {
		return io.github.jcondedata.aliveworkplace.work.Pastures.isPasture(be.getBlockState())
			// 37.5: with furnished builds on, a furniture mod's desk, counter, shelf or cabinet holds items but is furniture
			// (design note M37, "decoration only"): no job takes it for a chest
			|| io.github.jcondedata.aliveworkplace.furnish.Furnishings.ENABLED
			&& be.getBlockState().is(io.github.jcondedata.aliveworkplace.furnish.Furnishings.FURNITURE_STORAGE)
			|| be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity
			|| be instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity
			// Create's machines, belts and toolboxes (39.2): a mill's wheat and a player's tools are nobody's supplies
			|| io.github.jcondedata.aliveworkplace.mechanic.Kinetics.keepsOwnItems(be.getBlockState());
	}

	/** Furnaces, blast furnaces and smokers near {@code bench}, nearest first. */
	public static List<BlockPos> furnaces(ServerLevel level, BlockPos bench) {
		return near(level, bench, be -> be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity);
	}

	private static List<BlockPos> near(ServerLevel level, BlockPos bench, java.util.function.Predicate<BlockEntity> keep) {
		List<BlockPos> found = new ArrayList<>();
		// a store that several blocks hand out (a Create item vault's) is listed once, so its items are counted once
		java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		int minCx = SectionPos.blockToSectionCoord(bench.getX() - RADIUS), maxCx = SectionPos.blockToSectionCoord(bench.getX() + RADIUS);
		int minCz = SectionPos.blockToSectionCoord(bench.getZ() - RADIUS), maxCz = SectionPos.blockToSectionCoord(bench.getZ() + RADIUS);
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // loaded only, and without waiting (B103)
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos p = be.getBlockPos();
					if (Math.abs(p.getX() - bench.getX()) > RADIUS || Math.abs(p.getZ() - bench.getZ()) > RADIUS || Math.abs(p.getY() - bench.getY()) > VERTICAL) {
						continue;
					}
					if (keep.test(be) && !otherHalfFound(be, found) && seen.add(STORES.identity(level, p))) {
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

	/**
	 * Free room across the containers counted slot by slot (ROADMAP 37.8): empty slots, stacks and items that still fit,
	 * and whether a slot holds more than a stack (Sophisticated Storage's stack upgrades and limited barrels).
	 */
	public static ItemStores.Room room(ServerLevel level, List<BlockPos> containers) {
		return STORES.room(level, containers);
	}

	/**
	 * Stacks that still fit across the containers: {@link #freeSlots} for vanilla chests; with M37's modded storage
	 * (ROADMAP 37.8), a slot that holds several stacks counts each stack still free in it, so a porter keeps filling a
	 * stack-upgraded chest or a limited barrel whose slots all hold something.
	 */
	public static int freeStacks(ServerLevel level, List<BlockPos> containers) {
		return io.github.jcondedata.aliveworkplace.Expansions.on(io.github.jcondedata.aliveworkplace.Expansions.M37)
			? STORES.room(level, containers).freeStacks() : STORES.freeSlots(level, containers);
	}

	/** All slots across the containers, empty or not. */
	public static int slots(ServerLevel level, List<BlockPos> containers) {
		return STORES.slots(level, containers);
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
