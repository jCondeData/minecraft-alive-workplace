package io.github.jcondedata.aliveworkplace.mechanic;

import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's blocks the Mechanic knows (ROADMAP 39.2, design note M39), by id alone: no Create class is needed, so the mod
 * compiles and runs without it (every check is simply false then).
 * <ul>
 * <li>The drive train ({@link #DRIVE_PARTS}): shafts, gearboxes, cogwheels and belts, put back from a finished build's
 * blueprint in that order (what a cogwheel sits on before the cogwheel, the pulleys' shafts before the belt).</li>
 * <li>The machines on the round ({@link #MACHINES}): what each is refilled with when it's empty. Their outputs go to the
 * store.</li>
 * <li>Blocks that keep their own items ({@link #keepsOwnItems}): the machines, the depot, the basin, the belt and the
 * toolbox. They're never anyone's supply chest, so no worker puts the store's goods into a mill or takes a player's
 * tools out of a toolbox.</li>
 * </ul>
 */
public final class Kinetics {
	static final String CREATE = "create";

	/** The drive-train parts a Mechanic puts back, in the order they go back (a lower group first). */
	static final Map<String, Integer> DRIVE_PARTS = Map.of(
		"shaft", 0, "gearbox", 0,
		"cogwheel", 1, "large_cogwheel", 1,
		"belt", 2);

	/** The machines on a Mechanic's round, each with what it's refilled with (39.3's machine files feed the rest). */
	static final Map<String, List<Item>> MACHINES = Map.of(
		"millstone", List.of(Items.WHEAT));

	/** Create's blocks that hold items for their own work, never as storage. */
	private static final List<String> OWN_ITEMS = List.of("millstone", "mechanical_press", "mechanical_mixer", "basin", "depot",
		"crushing_wheel", "crushing_wheel_controller", "belt", "chute", "smart_chute", "deployer", "mechanical_crafter",
		"item_drain", "spout", "blaze_burner");

	private Kinetics() {
	}

	/** Create's id of {@code state}'s block ({@code "shaft"}), or null for a block of any other mod. */
	static String createId(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return CREATE.equals(id.getNamespace()) ? id.getPath() : null;
	}

	/** Whether {@code state} is a drive-train part the Mechanic puts back. */
	public static boolean isDrivePart(BlockState state) {
		String id = createId(state);
		return id != null && DRIVE_PARTS.containsKey(id);
	}

	/** The group a part goes back in (0 shafts and gearboxes, 1 cogwheels, 2 belts). */
	static int group(BlockState state) {
		String id = createId(state);
		return id == null ? 0 : DRIVE_PARTS.getOrDefault(id, 0);
	}

	/** Whether {@code state} is Create's belt (placed in segments; its item is the belt connector). */
	static boolean isBelt(BlockState state) {
		return "belt".equals(createId(state));
	}

	/** Whether {@code state} is a machine on the Mechanic's round. */
	public static boolean isMachine(BlockState state) {
		String id = createId(state);
		return id != null && MACHINES.containsKey(id);
	}

	/** What a machine on the round is refilled with. */
	static List<Item> feed(BlockState state) {
		String id = createId(state);
		return id == null ? List.of() : MACHINES.getOrDefault(id, List.of());
	}

	/** Whether {@code state} is a toolbox, the Mechanic's job block. */
	public static boolean isToolbox(BlockState state) {
		String id = createId(state);
		return id != null && id.endsWith("_toolbox");
	}

	/** A Create block that holds items for its own work (a machine, a belt, a toolbox): never a supply chest. */
	public static boolean keepsOwnItems(BlockState state) {
		String id = createId(state);
		return id != null && (OWN_ITEMS.contains(id) || id.endsWith("_toolbox"));
	}

	/**
	 * The item putting {@code state} back uses up: the block's own (a shaft, a cogwheel), and for a belt the belt
	 * connector, one for the whole belt (charged on its controller segment; {@code controller}). Air: nothing.
	 */
	static Item cost(BlockState state, boolean controller) {
		if (isBelt(state)) {
			return controller ? Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(CREATE, "belt_connector")) : Items.AIR;
		}
		return state.getBlock().asItem();
	}
}
