package io.github.jcondedata.aliveworkplace.mechanic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The kinetic parts a builder puts in after everything else (ROADMAP 39.5, design note M39, "Contraptions"):
 * {@code data/<ns>/kinetic_parts/<id>.json}, by block id, so an add-on's parts can be added by a datapack:
 * <pre>
 * {"requires": "create", "groups": [["create:shaft", "create:gearbox"], ["create:cogwheel"], ["create:belt"]],
 *  "belts": ["create:belt"]}
 * </pre>
 * The groups go in their order, after the build's other blocks (what holds the contraption up): shafts and gearboxes,
 * then cogwheels, then belts. {@code belts} are the blocks whose saved data names their controller segment. A file whose
 * {@code requires} mod isn't loaded is skipped quietly, and so is an id that doesn't exist (so without Create there are
 * none, and every build goes in today's order). No Create class is used.
 */
public final class KineticParts implements ResourceManagerReloadListener {
	static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AliveWorkplace.MOD_ID, "kinetic_parts");

	private static volatile Map<Block, Integer> groups = Map.of();
	private static volatile Set<Block> belts = Set.of();
	/** The mods the loaded files are for: their blocks with a turning speed in their data are checked when a build is done. */
	private static volatile Set<String> mods = Set.of();

	public static void init() {
		Platform.get().onDataReload(ID, new KineticParts());
	}

	/** The group {@code state}'s block goes in (0 shafts and gearboxes, 1 cogwheels, 2 belts), or -1 for any other block. */
	public static int group(BlockState state) {
		return groups.getOrDefault(state.getBlock(), -1);
	}

	/** Whether {@code state} is a belt segment (its data names its controller). */
	public static boolean isBelt(BlockState state) {
		return belts.contains(state.getBlock());
	}

	/** Whether any parts are known (a file for a loaded mod). */
	public static boolean any() {
		return !groups.isEmpty();
	}

	/** Whether {@code block} is of a mod a kinetic-parts file is for (its turning speed is read when a build is done). */
	public static boolean ofKineticMod(Block block) {
		return mods.contains(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Block, Integer> g = new HashMap<>();
		Set<Block> b = new HashSet<>();
		Set<String> m = new HashSet<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("kinetic_parts", p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				String requires = json.has("requires") ? json.get("requires").getAsString() : null;
				if (requires != null && !Platform.get().isModLoaded(requires)) {
					continue;
				}
				if (requires != null) {
					m.add(requires);
				}
				JsonArray list = json.getAsJsonArray("groups");
				for (int i = 0; list != null && i < list.size(); i++) {
					for (JsonElement id : list.get(i).getAsJsonArray()) {
						Block block = block(id.getAsString());
						if (block != null) {
							g.merge(block, i, Math::min);
						}
					}
				}
				if (json.has("belts")) {
					for (JsonElement id : json.getAsJsonArray("belts")) {
						Block block = block(id.getAsString());
						if (block != null) {
							b.add(block);
						}
					}
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.debug("Skipping kinetic parts {}: {}", e.getKey(), ex.toString());
			}
		}
		groups = Map.copyOf(g);
		belts = Set.copyOf(b);
		mods = Set.copyOf(m);
	}

	private static Block block(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		return rl != null && BuiltInRegistries.BLOCK.containsKey(rl) ? BuiltInRegistries.BLOCK.get(rl) : null;
	}

	private KineticParts() {
	}
}
