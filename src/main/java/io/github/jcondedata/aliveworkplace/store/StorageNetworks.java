package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Modded storage networks as one store (ROADMAP 37.9 and 37.10, design note {@code docs/design/M37.md}): a
 * Sophisticated Storage controller or a Tom's Simple Storage inventory connector in the Storehouse's area stands for
 * every inventory it links, read and filled through its own item storage. Config {@code moddedStorageNetworks}, gated by
 * {@link Expansions#M37}; off, every linked chest is its own store and the network blocks are passed over, as before.
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
