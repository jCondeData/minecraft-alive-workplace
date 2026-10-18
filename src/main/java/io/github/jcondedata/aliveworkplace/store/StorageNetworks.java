package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.Expansions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Modded storage networks as one store (ROADMAP 37.9 and 37.10, design note {@code docs/design/M37.md}): a
 * Sophisticated Storage controller or a Tom's Simple Storage inventory connector in the Storehouse's area stands for
 * every inventory it links, read and filled through its own item storage. Config {@code moddedStorageNetworks}, gated by
 * {@link Expansions#M37}; off, every linked chest is its own store and the network blocks are passed over, as before.
 *
 * <p>Sophisticated Storage (37.9) goes by block ids only, never the mod's classes: a {@code sophisticatedstorage:controller}
 * links the mod's chests, barrels, limited barrels and shulker boxes that touch it, or touch one it links, or touch a
 * {@code storage_link} it links, within its reach (15 blocks each way, the mod's own). The controller exposes their
 * contents through Fabric's transfer API (checked in the mod's jar: it registers {@code ItemStorage.SIDED} for its
 * block entity), so the Storehouse reads and fills it like one big chest.
 */
public final class StorageNetworks {
	/** Controllers and connectors count as one store (config {@code moddedStorageNetworks}). */
	public static volatile boolean ENABLED = Expansions.on(Expansions.M37);

	static final String SOPHISTICATED = "sophisticatedstorage";
	/** The controller's reach each way from it, as the mod's own {@code ControllerBlockEntityBase.SEARCH_RANGE}. */
	public static final int CONTROLLER_REACH = 15;
	private static final Pattern SOPHISTICATED_STORAGE =
		Pattern.compile("(limited_)?(copper_|iron_|gold_|diamond_|netherite_)?(chest|barrel(_[1-4])?|shulker_box)");

	private StorageNetworks() {
	}

	/** A Sophisticated Storage controller. */
	public static boolean isController(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals(SOPHISTICATED) && id.getPath().equals("controller");
	}

	/** One of Sophisticated Storage's storage blocks: a chest, barrel, limited barrel or shulker box of any tier. */
	public static boolean isSophisticatedStorage(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals(SOPHISTICATED) && SOPHISTICATED_STORAGE.matcher(id.getPath()).matches();
	}

	private static boolean isLink(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals(SOPHISTICATED) && id.getPath().equals("storage_link");
	}

	/**
	 * The storage blocks {@code controller} links: walking from it through touching storage blocks and storage links,
	 * within its reach, loaded blocks only. Empty if {@code controller} isn't one.
	 */
	public static Set<BlockPos> linkedTo(ServerLevel level, BlockPos controller) {
		Set<BlockPos> storage = new LinkedHashSet<>();
		if (!level.isLoaded(controller) || !isController(level.getBlockState(controller))) {
			return storage;
		}
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		seen.add(controller);
		open.add(controller);
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			for (Direction d : Direction.values()) {
				BlockPos next = at.relative(d);
				if (!seen.add(next) || Math.abs(next.getX() - controller.getX()) > CONTROLLER_REACH
					|| Math.abs(next.getY() - controller.getY()) > CONTROLLER_REACH
					|| Math.abs(next.getZ() - controller.getZ()) > CONTROLLER_REACH || !level.isLoaded(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (isSophisticatedStorage(state)) {
					storage.add(next.immutable());
					open.add(next.immutable());
				} else if (isLink(state)) {
					open.add(next.immutable());
				}
			}
		}
		return storage;
	}

	/** What the Storehouse's controllers stand for: the store list with their linked blocks folded in, and how many. */
	public record Folded(List<BlockPos> stores, int linked) {
	}

	/**
	 * {@code found} (the Storehouse's stores, nearest first) with each controller among {@code controllers} that links
	 * at least one storage block standing for them: the linked blocks leave the list and the controller joins it, so
	 * nothing is counted twice. Controllers inside {@code exclude} are passed over.
	 */
	public static Folded fold(ServerLevel level, BlockPos center, List<BlockPos> found, List<BlockPos> controllers, @Nullable BoundingBox exclude) {
		List<BlockPos> out = new ArrayList<>(found);
		Set<BlockPos> linkedAll = new HashSet<>();
		for (BlockPos controller : controllers) {
			if (exclude != null && exclude.isInside(controller)) {
				continue;
			}
			Set<BlockPos> linked = linkedTo(level, controller);
			if (linked.isEmpty()) {
				continue;
			}
			linkedAll.addAll(linked);
			out.add(controller.immutable());
		}
		if (linkedAll.isEmpty()) {
			return new Folded(found, 0);
		}
		out.removeIf(linkedAll::contains);
		out.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
		return new Folded(List.copyOf(out), linkedAll.size());
	}

	/**
	 * A builder has just set {@code state} at {@code pos}: a controller it builds links its storage the way a player's
	 * placing it would (the mod links on placement, through the block's own {@code setPlacedBy}), and a storage block or
	 * link it adds to a controller's network is linked by that controller looking again. With the switch off, nothing.
	 */
	public static void afterBuilt(ServerLevel level, BlockPos pos, BlockState state) {
		if (!ENABLED || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(SOPHISTICATED)) {
			return;
		}
		if (isController(state)) {
			relink(level, pos);
			return;
		}
		if (!isSophisticatedStorage(state) && !isLink(state)) {
			return;
		}
		// The controllers whose network this block joins: walk out from it the way a controller walks.
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		seen.add(pos);
		open.add(pos);
		Set<BlockPos> controllers = new LinkedHashSet<>();
		while (!open.isEmpty() && seen.size() < 4096) {
			BlockPos at = open.poll();
			for (Direction d : Direction.values()) {
				BlockPos next = at.relative(d);
				if (!seen.add(next) || next.distManhattan(pos) > 3 * CONTROLLER_REACH || !level.isLoaded(next)) {
					continue;
				}
				BlockState s = level.getBlockState(next);
				if (isController(s)) {
					controllers.add(next.immutable());
				} else if (isSophisticatedStorage(s) || isLink(s)) {
					open.add(next.immutable());
				}
			}
		}
		for (BlockPos controller : controllers) {
			if (linkedTo(level, controller).contains(pos) || isLink(state)) {
				relink(level, controller);
			}
		}
	}

	private static void relink(ServerLevel level, BlockPos controller) {
		BlockState state = level.getBlockState(controller);
		ItemStack item = new ItemStack(state.getBlock().asItem());
		state.getBlock().setPlacedBy(level, controller, state, null, item);
	}
}
