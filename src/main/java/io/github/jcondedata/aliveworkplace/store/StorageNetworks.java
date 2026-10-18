package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
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
import net.minecraft.world.level.block.state.properties.Property;
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
 *
 * <p>Tom's Simple Storage (37.10) by block ids only, the way its inventory connector finds its inventories (read from
 * its 2.3.0 jar): the inventories touching the connector, and those touching them in turn, through its trims, within
 * 16 blocks (its {@code invConnectorScanRange}); and through its cables (plain and framed cables, cable connectors,
 * terminals and its other cable blocks), the one inventory each cable connector faces. The connector exposes the merged
 * inventories through Fabric's transfer API, so the Storehouse reads and fills it like a chest.
 */
public final class StorageNetworks {
	/** Controllers and connectors count as one store (config {@code moddedStorageNetworks}). */
	public static volatile boolean ENABLED = Expansions.on(Expansions.M37);

	static final String SOPHISTICATED = "sophisticatedstorage";
	/** The controller's reach each way from it, as the mod's own {@code ControllerBlockEntityBase.SEARCH_RANGE}. */
	public static final int CONTROLLER_REACH = 15;
	private static final Pattern SOPHISTICATED_STORAGE =
		Pattern.compile("(limited_)?(copper_|iron_|gold_|diamond_|netherite_)?(chest|barrel(_[1-4])?|shulker_box)");
	private static final String TOMS = "toms_storage";
	static final ResourceLocation TOMS_CONNECTOR = ResourceLocation.fromNamespaceAndPath(TOMS, "inventory_connector");
	/** Tom's connector's scan range (its config's default {@code invConnectorScanRange}). */
	static final int TOMS_RANGE = 16;
	/** Tom's limit on cables in one network (its config's default {@code invConnectorMaxCables}). */
	static final int TOMS_MAX_CABLES = 2048;
	/** Tom's blocks the connector's touching scan passes through without reading them (its {@code #trims} tag, its inventory interface). */
	private static final Set<String> TOMS_PASS = Set.of("trim", "painted_trim", "inventory_interface");
	/** Tom's cable blocks (its {@code IInventoryCable} blocks): a cable network joins them. */
	private static final Set<String> TOMS_CABLES = Set.of("inventory_cable", "inventory_cable_framed", "inventory_cable_connector",
		"inventory_cable_connector_framed", "inventory_connector", "storage_terminal", "crafting_terminal", "basic_inventory_hopper",
		"level_emitter", "inventory_interface");
	/** Tom's cable connectors: each joins the one inventory it faces. */
	private static final Set<String> TOMS_CABLE_CONNECTORS = Set.of("inventory_cable_connector", "inventory_cable_connector_framed");

	private static final ItemStores STORES = Platform.get().items();

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

	/** True if the block at {@code pos} is a network's head the Storehouse reads as one store (a Tom's inventory connector). */
	public static boolean isHead(ServerLevel level, BlockPos pos) {
		return isTomsConnector(level.getBlockState(pos));
	}

	static boolean isTomsConnector(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(TOMS_CONNECTOR);
	}

	/**
	 * The inventories the network head at {@code head} stands for (empty when it isn't one): for a Tom's inventory
	 * connector, the inventories it joins. Found again on every call (nothing saved), so a chest unplugged from the
	 * network is its own store again at once.
	 */
	public static Set<BlockPos> members(ServerLevel level, BlockPos head) {
		if (!isTomsConnector(level.getBlockState(head))) {
			return Set.of();
		}
		Set<BlockPos> out = new LinkedHashSet<>();
		Set<BlockPos> connectors = new HashSet<>();
		// The cable network: every cable block joined to this connector, each cable connector's faced inventory, and the
		// inventories touching every inventory connector on it.
		ArrayDeque<BlockPos> cables = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		cables.add(head.immutable());
		seen.add(head.immutable());
		while (!cables.isEmpty() && seen.size() <= TOMS_MAX_CABLES) {
			BlockPos p = cables.poll();
			BlockState state = level.getBlockState(p);
			String path = tomsPath(state);
			if (path == null) {
				continue;
			}
			if (path.equals("inventory_connector")) {
				connectors.add(p);
			} else if (TOMS_CABLE_CONNECTORS.contains(path)) {
				Direction facing = facing(state);
				if (facing != null) {
					BlockPos faced = p.relative(facing);
					if (isMemberInventory(level, faced)) {
						out.add(faced.immutable());
					}
				}
			}
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (TOMS_CABLE_CONNECTORS.contains(path) && d == facing(state)) {
					continue; // a cable connector's front is its inventory, not more cable
				}
				String next = tomsPath(level.getBlockState(q));
				if (next != null && TOMS_CABLES.contains(next) && seen.add(q.immutable())) {
					cables.add(q.immutable());
				}
			}
		}
		for (BlockPos connector : connectors) {
			touching(level, connector, out);
		}
		return out;
	}

	/** Tom's connector's own scan: inventories touching it or each other, through trims, within its range. */
	private static void touching(ServerLevel level, BlockPos connector, Set<BlockPos> out) {
		ArrayDeque<BlockPos> stack = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		stack.push(connector);
		visited.add(connector);
		int range2 = TOMS_RANGE * TOMS_RANGE;
		while (!stack.isEmpty()) {
			BlockPos p = stack.pop();
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d).immutable();
				if (visited.contains(q) || q.distSqr(connector) >= range2) {
					continue;
				}
				visited.add(q);
				String path = tomsPath(level.getBlockState(q));
				if (path != null && TOMS_PASS.contains(path)) {
					stack.push(q);
				} else if (isMemberInventory(level, q)) {
					out.add(q);
					stack.push(q);
				}
			}
		}
	}

	/** An inventory a Tom's network joins: any item store but its own network blocks (its filing cabinet is one). */
	private static boolean isMemberInventory(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		String path = tomsPath(state);
		if (path != null && !path.equals("filing_cabinet") && !path.equals("open_crate")) {
			return false;
		}
		return state.hasBlockEntity() && STORES.isStore(level, pos);
	}

	/** The path of a Tom's Simple Storage block's id; null for any other block. */
	private static String tomsPath(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals(TOMS) ? id.getPath() : null;
	}

	private static Direction facing(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("facing") && property.getValueClass() == Direction.class) {
				return (Direction) state.getValue(property);
			}
		}
		return null;
	}

	/**
	 * The Storehouse's store list (ROADMAP 37.10): {@code found} (its chests, network blocks left out) with each network
	 * head in {@code heads} put in, nearest first, and the inventories it joins taken out, so each item counts once.
	 * Unchanged when the switch is off or no head stands there.
	 */
	public static List<BlockPos> asStorehouse(ServerLevel level, BlockPos storehouse, List<BlockPos> found, List<BlockPos> heads) {
		if (!ENABLED || heads.isEmpty()) {
			return found;
		}
		Set<BlockPos> joined = new HashSet<>();
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos head : heads) {
			Set<BlockPos> members = members(level, head);
			if (!members.isEmpty() && !joined.contains(head)) {
				joined.addAll(members);
				out.add(head);
			}
		}
		for (BlockPos p : found) {
			if (!joined.contains(p) && !joinedOtherHalf(level, p, joined)) {
				out.add(p);
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.distSqr(storehouse)));
		return out;
	}

	/** A double chest's half listed while its other half is the one the network joined (both hold the same items). */
	private static boolean joinedOtherHalf(ServerLevel level, BlockPos pos, Set<BlockPos> joined) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
			|| !state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)
			|| state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
			return false;
		}
		return joined.contains(pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)));
	}

	/** How many inventories the heads in {@code store} join (the Storehouse board's line; 0 when there are none). */
	public static int joinedCount(ServerLevel level, List<BlockPos> store) {
		if (!ENABLED) {
			return 0;
		}
		int n = 0;
		for (BlockPos p : store) {
			n += members(level, p).size();
		}
		return n;
	}
}
