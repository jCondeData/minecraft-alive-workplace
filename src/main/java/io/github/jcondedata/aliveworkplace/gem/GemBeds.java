package io.github.jcondedata.aliveworkplace.gem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * The Gem Grower's beds (ROADMAP 28.11), one data file each: {@code data/<ns>/gem_beds/<name>.json}.
 * <pre>
 * {
 *   "plant": "cobblemon:tumblestone",                    // an item id, or "none" (absent: none)
 *   "touch": "#cobblemon:tumblestone_heat_source",        // the block (or #tag) it grows against
 *   "grows": ["cobblemon:small_budding_tumblestone", ...], // the blocks that grow, smallest first
 *   "ripe": "cobblemon:tumblestone_cluster",              // the ripe one, with properties: "id[stage=3]"
 *   "harvest": "loot",                                     // the block's own loot (the only kind)
 *   "plants": 4,                                           // how many she keeps planted round one touch block (1)
 *   "requires": ["cobblemon"],                             // a mod id, or "type_gems" (Cobblemon 1.8)
 *   "order": 10                                            // where it comes in the orders screen
 * }
 * </pre>
 * A bed whose {@code requires} isn't met is passed over quietly; a malformed one is logged with the file name and the
 * field, and skipped, and the rest still load. A planted item that isn't a block item places the first of
 * {@code grows} (Cobblemon's tumblestones place their small bud).
 */
public final class GemBeds implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("gem_beds");
	public static final String FOLDER = "gem_beds";
	/** The requirement that means Cobblemon 1.8's Type Gems. */
	public static final String TYPE_GEMS = "type_gems";

	/** What a bed grows against: one block, or any block of a tag. */
	public record Touch(@Nullable ResourceLocation block, @Nullable TagKey<Block> tag) {
		public boolean matches(BlockState state) {
			return tag != null ? state.is(tag) : BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(block);
		}
	}

	/** One bed: see the class comment. */
	public record Bed(ResourceLocation name, Optional<ResourceLocation> plant, Touch touch, List<ResourceLocation> grows,
			ResourceLocation ripe, Map<String, String> ripeProperties, int plants, Set<String> requires, int order) {

		/** Whether {@code state} is this bed's ripe block (with the ripe properties). */
		public boolean isRipe(BlockState state) {
			if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(ripe)) {
				return false;
			}
			for (Map.Entry<String, String> e : ripeProperties.entrySet()) {
				Property<?> property = state.getBlock().getStateDefinition().getProperty(e.getKey());
				if (property == null || !valueName(state, property).equals(e.getValue())) {
					return false;
				}
			}
			return true;
		}

		/** Whether {@code state} is one of the blocks that grow (a bud, a cluster) or the planted block itself. */
		public boolean isGrowth(BlockState state) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			return grows.contains(id) || plantBlock().map(b -> b == state.getBlock()).orElse(false);
		}

		/** The planted item, if it's registered. */
		public Optional<Item> plantItem() {
			return plant.flatMap(BuiltInRegistries.ITEM::getOptional);
		}

		/** The block planting places: the item's own block, or the first of {@code grows}. */
		public Optional<Block> plantBlock() {
			Optional<Item> item = plantItem();
			if (item.isEmpty()) {
				return Optional.empty();
			}
			if (item.get() instanceof BlockItem blockItem) {
				return Optional.of(blockItem.getBlock());
			}
			return grows.isEmpty() ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(grows.get(0));
		}

		/** The lang key of its name in the orders screen ({@code gem_bed.aliveworkplace.amethyst}). */
		public String nameKey() {
			return "gem_bed." + name.getNamespace() + "." + name.getPath();
		}
	}

	private static List<Bed> beds = List.of();

	/** Every bed loaded (and usable in this game), in order. */
	public static List<Bed> beds() {
		return beds;
	}

	@Nullable
	public static Bed byName(ResourceLocation name) {
		return beds.stream().filter(b -> b.name().equals(name)).findFirst().orElse(null);
	}

	/** Sets the beds (tests). */
	public static void set(List<Bed> newBeds) {
		beds = List.copyOf(newBeds);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				StringBuilder text = new StringBuilder();
				char[] buffer = new char[4096];
				for (int n; (n = reader.read(buffer)) > 0; ) {
					text.append(buffer, 0, n);
				}
				files.put(e.getKey(), text.toString());
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Alive Workplace: skipping gem bed {}: {}", e.getKey(), ex.getMessage());
			}
		}
		beds = List.copyOf(readAll(files, new ArrayList<>()));
		AliveWorkplace.LOG.info("Alive Workplace: {} gem beds", beds.size());
	}

	/**
	 * Reads every file ({@code data/<ns>/gem_beds/<name>.json} → its text): the usable beds, in order. A malformed one is
	 * logged and added to {@code problems} ("file: what's wrong"); one whose {@code requires} isn't met is passed over.
	 */
	public static List<Bed> readAll(Map<ResourceLocation, String> files, List<String> problems) {
		List<Bed> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, String> e : files.entrySet()) {
			ResourceLocation file = e.getKey();
			String path = file.getPath();
			int start = path.startsWith(FOLDER + "/") ? FOLDER.length() + 1 : 0;
			ResourceLocation name = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				path.substring(start, path.endsWith(".json") ? path.length() - 5 : path.length()));
			try {
				JsonObject json = JsonParser.parseString(e.getValue()).getAsJsonObject();
				Set<String> requires = requires(json);
				if (!met(requires)) {
					continue; // (Cobblemon's beds without Cobblemon, the Type Gems before 1.8: not an error)
				}
				out.add(parse(name, json, true));
			} catch (Exception ex) {
				String problem = file + ": " + ex.getMessage();
				problems.add(problem);
				AliveWorkplace.LOG.warn("Alive Workplace: skipping gem bed {}", problem);
			}
		}
		out.sort(Comparator.comparingInt(Bed::order).thenComparing(b -> b.name().toString()));
		return out;
	}

	/** Whether each of {@code requires} is here: a mod by id, or {@link #TYPE_GEMS}. */
	public static boolean met(Set<String> requires) {
		for (String r : requires) {
			if (r.equals(TYPE_GEMS) ? !PokemonFeatures.TYPE_GEMS.available() : !Platform.get().isModLoaded(r)) {
				return false;
			}
		}
		return true;
	}

	private static Set<String> requires(JsonObject json) {
		Set<String> out = new LinkedHashSet<>();
		if (json.has("requires")) {
			if (!json.get("requires").isJsonArray()) {
				throw new IllegalArgumentException("\"requires\" must be a list");
			}
			json.getAsJsonArray("requires").forEach(r -> out.add(r.getAsString()));
		}
		return out;
	}

	/**
	 * Reads one bed; throws {@link IllegalArgumentException} naming the field that's wrong. With {@code checkIds}, every
	 * block and item must be registered.
	 */
	public static Bed parse(ResourceLocation name, JsonObject json, boolean checkIds) {
		Optional<ResourceLocation> plant = Optional.empty();
		if (json.has("plant") && !json.get("plant").isJsonNull() && !json.get("plant").getAsString().equals("none")) {
			ResourceLocation item = id(json.get("plant"), "plant");
			if (checkIds && !BuiltInRegistries.ITEM.containsKey(item)) {
				throw new IllegalArgumentException("\"plant\": no item " + item);
			}
			plant = Optional.of(item);
		}
		if (!json.has("touch")) {
			throw new IllegalArgumentException("no \"touch\"");
		}
		String touchText = json.get("touch").getAsString();
		Touch touch;
		if (touchText.startsWith("#")) {
			touch = new Touch(null, TagKey.create(Registries.BLOCK, id(touchText.substring(1), "touch")));
		} else {
			ResourceLocation block = id(touchText, "touch");
			if (checkIds && !BuiltInRegistries.BLOCK.containsKey(block)) {
				throw new IllegalArgumentException("\"touch\": no block " + block);
			}
			touch = new Touch(block, null);
		}
		if (!json.has("grows") || !json.get("grows").isJsonArray() || json.getAsJsonArray("grows").isEmpty()) {
			throw new IllegalArgumentException("\"grows\" must be a list of blocks");
		}
		List<ResourceLocation> grows = new ArrayList<>();
		JsonArray growsJson = json.getAsJsonArray("grows");
		for (JsonElement g : growsJson) {
			ResourceLocation block = id(g, "grows");
			if (checkIds && !BuiltInRegistries.BLOCK.containsKey(block)) {
				throw new IllegalArgumentException("\"grows\": no block " + block);
			}
			grows.add(block);
		}
		if (!json.has("ripe")) {
			throw new IllegalArgumentException("no \"ripe\"");
		}
		String ripeText = json.get("ripe").getAsString();
		Map<String, String> properties = new LinkedHashMap<>();
		int bracket = ripeText.indexOf('[');
		if (bracket >= 0) {
			if (!ripeText.endsWith("]")) {
				throw new IllegalArgumentException("\"ripe\": no closing ]");
			}
			for (String pair : ripeText.substring(bracket + 1, ripeText.length() - 1).split(",")) {
				String[] kv = pair.split("=");
				if (kv.length != 2) {
					throw new IllegalArgumentException("\"ripe\": \"" + pair + "\" isn't property=value");
				}
				properties.put(kv[0].trim(), kv[1].trim());
			}
			ripeText = ripeText.substring(0, bracket);
		}
		ResourceLocation ripe = id(ripeText, "ripe");
		if (!grows.contains(ripe)) {
			throw new IllegalArgumentException("\"ripe\": " + ripe + " isn't one of \"grows\"");
		}
		if (checkIds) {
			Block block = BuiltInRegistries.BLOCK.get(ripe);
			for (Map.Entry<String, String> p : properties.entrySet()) {
				Property<?> property = block.getStateDefinition().getProperty(p.getKey());
				if (property == null || property.getValue(p.getValue()).isEmpty()) {
					throw new IllegalArgumentException("\"ripe\": " + ripe + " has no " + p.getKey() + "=" + p.getValue());
				}
			}
		}
		String harvest = json.has("harvest") ? json.get("harvest").getAsString() : "loot";
		if (!harvest.equals("loot")) {
			throw new IllegalArgumentException("\"harvest\": only \"loot\" (the block's own) is known");
		}
		int plants = json.has("plants") ? json.get("plants").getAsInt() : 1;
		if (plants < 1 || plants > 6) {
			throw new IllegalArgumentException("\"plants\" must be 1 to 6");
		}
		int order = json.has("order") ? json.get("order").getAsInt() : 100;
		return new Bed(name, plant, touch, List.copyOf(grows), ripe, Map.copyOf(properties), plants, requires(json), order);
	}

	private static ResourceLocation id(JsonElement element, String field) {
		if (!element.isJsonPrimitive()) {
			throw new IllegalArgumentException("\"" + field + "\" must be an id");
		}
		return id(element.getAsString(), field);
	}

	private static ResourceLocation id(String text, String field) {
		ResourceLocation id = ResourceLocation.tryParse(text);
		if (id == null) {
			throw new IllegalArgumentException("\"" + field + "\": \"" + text + "\" isn't an id");
		}
		return id;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private GemBeds() {
	}

	static GemBeds create() {
		return new GemBeds();
	}
}
