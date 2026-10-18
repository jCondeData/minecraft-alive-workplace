package io.github.jcondedata.aliveworkplace.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

/**
 * Milestone 39 (Create integration, design note M39) works with the owner's Fabric port of Create 6.0.10 (libs/) by its
 * ids alone: blocks, their states, block entity types, items, recipe types and data components. This test lists every id
 * the milestone reads and fails naming any that's missing, so a new build of the port that renames one shows up here
 * first. Checked against the jar on 2026-10-18: the belt's item is {@code create:belt_connector} ({@code create:belt} is
 * only the placed block), the sails are {@code create:white_sail} and {@code create:sail_frame}, and the crushing wheels
 * work through {@code create:crushing_wheel_controller}, placed by the wheels themselves.
 */
public class CreateCompatTests implements FabricGameTest {
	static final String CREATE = "create";

	/** Every block the milestone places, works, reads or keeps up (39.2 to 39.7), with the states it reads by name. */
	static final Map<String, List<String>> BLOCKS = Map.ofEntries(
		// 39.3, the machines villagers work
		Map.entry("millstone", List.of()),
		Map.entry("mechanical_press", List.of("facing")),
		Map.entry("mechanical_mixer", List.of()),
		Map.entry("basin", List.of("facing")),
		Map.entry("crushing_wheel", List.of("axis")),
		Map.entry("crushing_wheel_controller", List.of()),
		Map.entry("depot", List.of()),
		// 39.4 and 39.5, power and the drive train
		Map.entry("water_wheel", List.of("facing")),
		Map.entry("large_water_wheel", List.of("axis")),
		Map.entry("windmill_bearing", List.of("facing")),
		Map.entry("white_sail", List.of("facing")),
		Map.entry("sail_frame", List.of("facing")),
		Map.entry("shaft", List.of("axis")),
		Map.entry("cogwheel", List.of("axis")),
		Map.entry("large_cogwheel", List.of("axis")),
		Map.entry("gearbox", List.of("axis")),
		Map.entry("belt", List.of("facing", "part", "slope", "casing")),
		Map.entry("andesite_casing", List.of()),
		// 39.2, the Mechanic's job block (design note M39: the toolbox, in each of its sixteen colours below)
		Map.entry("brown_toolbox", List.of("facing")),
		// 39.6 and 39.7
		Map.entry("schematic_table", List.of()),
		Map.entry("track", List.of("shape")),
		Map.entry("track_station", List.of()),
		Map.entry("controls", List.of()));

	/** The dye colours of Create's toolboxes: every one is a Mechanic's job block (39.2). */
	static final List<String> COLOURS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/** Block entity types whose saved data the milestone reads by name (the speed of a turning machine, a belt's length). */
	static final List<String> BLOCK_ENTITIES = List.of("millstone", "mechanical_press", "mechanical_mixer", "basin", "depot",
		"crushing_wheel", "crushing_wheel_controller", "water_wheel", "large_water_wheel", "windmill_bearing", "simple_kinetic",
		"gearbox", "belt", "toolbox", "schematic_table", "track", "track_station");

	/** Items: the blocks' own, the belt's (belt_connector), the schematics, the Mechanic's tools, and what the machines make. */
	static final List<String> ITEMS = List.of("millstone", "mechanical_press", "mechanical_mixer", "basin", "crushing_wheel",
		"depot", "water_wheel", "large_water_wheel", "windmill_bearing", "white_sail", "sail_frame", "shaft", "cogwheel",
		"large_cogwheel", "gearbox", "belt_connector", "andesite_casing", "brown_toolbox", "schematic_table", "track",
		"track_station", "controls", "schematic", "empty_schematic", "schematic_and_quill", "wrench", "goggles",
		"andesite_alloy", "wheat_flour", "dough", "iron_sheet", "copper_sheet", "crushed_raw_iron", "crushed_raw_copper",
		"crushed_raw_gold");

	/** The recipe types 39.3 reads, each with one of Create's own recipes of that type that the design note's example uses. */
	static final Map<String, String> RECIPE_TYPES = Map.of(
		"milling", "milling/wheat",
		"pressing", "pressing/iron_ingot",
		"mixing", "mixing/dough_by_mixing",
		"crushing", "crushing/raw_copper",
		"compacting", "compacting/blaze_cake");

	/**
	 * Create is in the compat runtime (tests.compat_files) with the libraries it nests, the newest of each (Porting Lib
	 * beta.90, not the beta.47 Sophisticated Core nests), and without its own GameTests (build.gradle.kts, localModsInto).
	 * Flywheel is Create's renderer and loads only on a client.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void createIsLoaded(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (String mod : List.of(CREATE, "porting_lib_base", "porting_lib_core", "registrate-fabric", "ponder", "forgeconfigapiport", "milk-lib")) {
			if (!FabricLoader.getInstance().isModLoaded(mod)) {
				problems.add(mod + " not loaded");
			}
		}
		FabricLoader.getInstance().getModContainer("porting_lib_core")
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.filter(version -> !version.startsWith("3.1.0-beta.90"))
			.ifPresent(version -> problems.add("porting_lib_core is " + version + ", not the 3.1.0-beta.90 Create nests"));
		FabricLoader.getInstance().getEntrypointContainers("fabric-gametest", Object.class).stream()
			.map(container -> container.getProvider().getMetadata().getId())
			.filter(id -> id.equals(CREATE) || id.startsWith("porting_lib"))
			.forEach(id -> problems.add(id + "'s own GameTests are in our run"));
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The blocks, the states read by name, and the block entity types. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void createBlockIdsExist(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		BLOCKS.forEach((path, properties) -> {
			Block block = block(path, missing);
			if (block != null) {
				for (String name : properties) {
					if (block.getStateDefinition().getProperty(name) == null) {
						missing.add(path + "[" + name + "] (has " + block.getStateDefinition().getProperties().stream().map(p -> p.getName()).toList() + ")");
					}
				}
			}
		});
		for (String colour : COLOURS) {
			block(colour + "_toolbox", missing);
		}
		for (String path : BLOCK_ENTITIES) {
			if (BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id(path)).isEmpty()) {
				missing.add("block entity type " + id(path));
			}
		}
		helper.assertTrue(missing.isEmpty(), "Create ids missing: " + missing);
		helper.succeed();
	}

	/** The items, and the schematic item's data components 39.6 reads by name. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void createItemIdsExist(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String path : ITEMS) {
			if (BuiltInRegistries.ITEM.getOptional(id(path)).isEmpty()) {
				missing.add("item " + id(path));
			}
		}
		// The belt is placed by its item, belt_connector; create:belt is the block alone.
		if (BuiltInRegistries.ITEM.containsKey(id("belt"))) {
			missing.add("an item create:belt (the design note says belt_connector)");
		}
		for (String component : List.of("schematic_file", "schematic_owner", "schematic_deployed", "schematic_anchor", "schematic_rotation")) {
			if (BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id(component)).isEmpty()) {
				missing.add("data component " + id(component));
			}
		}
		helper.assertTrue(missing.isEmpty(), "Create ids missing: " + missing);
		helper.succeed();
	}

	/** The recipe types, and one loaded recipe of each, of that type, making what the design note says. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void createRecipeTypesExist(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		Map<String, String> makes = Map.of("milling/wheat", "create:wheat_flour", "pressing/iron_ingot", "create:iron_sheet",
			"mixing/dough_by_mixing", "create:dough", "crushing/raw_copper", "create:crushed_raw_copper");
		RECIPE_TYPES.forEach((type, recipe) -> {
			if (BuiltInRegistries.RECIPE_TYPE.getOptional(id(type)).isEmpty()) {
				missing.add("recipe type " + id(type));
				return;
			}
			RecipeHolder<?> holder = server.getRecipeManager().byKey(id(recipe)).orElse(null);
			if (holder == null) {
				missing.add("recipe " + id(recipe));
				return;
			}
			ResourceLocation actual = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
			if (!id(type).equals(actual)) {
				missing.add("recipe " + id(recipe) + " is " + actual + ", not " + id(type));
			}
			String result = makes.get(recipe);
			if (result != null) {
				Item item = holder.value().getResultItem(server.registryAccess()).getItem();
				if (!BuiltInRegistries.ITEM.getKey(item).toString().equals(result)) {
					missing.add("recipe " + id(recipe) + " makes " + BuiltInRegistries.ITEM.getKey(item) + ", not " + result);
				}
			}
		});
		helper.assertTrue(missing.isEmpty(), "Create recipe ids missing: " + missing);
		helper.succeed();
	}

	static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(CREATE, path);
	}

	static Block block(String path, List<String> missing) {
		Block block = BuiltInRegistries.BLOCK.getOptional(id(path)).orElse(null);
		if (block == null) {
			missing.add("block " + id(path));
		}
		return block;
	}
}
