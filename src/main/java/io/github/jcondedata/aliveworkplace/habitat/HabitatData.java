package io.github.jcondedata.aliveworkplace.habitat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * What the Habitat Keeper reads from Cobblemon's own data files, by id and never by its classes (ROADMAP 28.10):
 * <ul>
 * <li>{@code spawn_bait_effects/**}: which berries season a snack for a type ({@code cobblemon:typing}) or an egg group
 * ({@code cobblemon:egg_group}), the lures she can be asked to set out;</li>
 * <li>{@code spawn_pool_world/*}: the species Cobblemon only spawns in its {@code rare} and {@code ultra-rare} buckets,
 * which she tells the village of when she sights one.</li>
 * </ul>
 * Without Cobblemon both are empty, and the job isn't there either.
 */
public final class HabitatData implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("habitat_data");
	/** The bait effect types that make a lure, and the lure kind each gives. */
	private static final Map<String, String> LURE_EFFECTS = Map.of("cobblemon:typing", "typing", "cobblemon:egg_group", "egg_group");
	private static final Set<String> RARE_BUCKETS = Set.of("rare", "ultra-rare");

	/** Each lure ("typing/fire", "egg_group/field") and the berries that season a snack for it, sorted by lure. */
	private static Map<String, List<ResourceLocation>> lures = Map.of();
	/** Species (by id, e.g. cobblemon:dratini) that Cobblemon only spawns as rare or ultra-rare. */
	private static Set<ResourceLocation> rareOnly = Set.of();

	public static Map<String, List<ResourceLocation>> lures() {
		return lures;
	}

	public static boolean rareOnly(ResourceLocation species) {
		return rareOnly.contains(species);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, Set<ResourceLocation>> found = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("spawn_bait_effects", p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				readBait(JsonParser.parseReader(reader).getAsJsonObject(), found);
			} catch (Exception ex) {
				AliveWorkplace.LOG.debug("Alive Workplace: skipping bait effects {}: {}", e.getKey(), ex.getMessage());
			}
		}
		Map<String, List<ResourceLocation>> out = new LinkedHashMap<>();
		found.keySet().stream().sorted().forEach(lure -> out.put(lure, found.get(lure).stream().sorted().toList()));
		lures = Collections.unmodifiableMap(out);

		Map<ResourceLocation, Boolean> rare = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("spawn_pool_world", p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				readSpawns(e.getKey().getNamespace(), JsonParser.parseReader(reader).getAsJsonObject(), rare);
			} catch (Exception ex) {
				AliveWorkplace.LOG.debug("Alive Workplace: skipping spawn pool {}: {}", e.getKey(), ex.getMessage());
			}
		}
		Set<ResourceLocation> only = new HashSet<>();
		rare.forEach((species, onlyRare) -> {
			if (onlyRare) {
				only.add(species);
			}
		});
		rareOnly = Set.copyOf(only);
		if (!lures.isEmpty() || !rareOnly.isEmpty()) {
			AliveWorkplace.LOG.info("Alive Workplace: {} habitat lures, {} rare-only species", lures.size(), rareOnly.size());
		}
	}

	/** One bait effects file: {@code {"item": "cobblemon:occa_berry", "effects": [{"type": "cobblemon:typing", "subcategory": "fire"}]}}. */
	static void readBait(JsonObject json, Map<String, Set<ResourceLocation>> into) {
		if (!json.has("item") || !json.has("effects")) {
			return;
		}
		ResourceLocation item = ResourceLocation.parse(json.get("item").getAsString());
		for (JsonElement element : json.getAsJsonArray("effects")) {
			JsonObject effect = element.getAsJsonObject();
			String kind = LURE_EFFECTS.get(effect.has("type") ? effect.get("type").getAsString() : "");
			if (kind == null || !effect.has("subcategory")) {
				continue;
			}
			String sub = effect.get("subcategory").getAsString().toLowerCase(Locale.ROOT);
			sub = sub.contains(":") ? sub.substring(sub.indexOf(':') + 1) : sub;
			into.computeIfAbsent(kind + "/" + sub, k -> new LinkedHashSet<>()).add(item);
		}
	}

	/** One spawn pool file: marks each species false as soon as one of its spawns is in a bucket other than rare and ultra-rare. */
	static void readSpawns(String namespace, JsonObject json, Map<ResourceLocation, Boolean> rare) {
		if (json.has("enabled") && !json.get("enabled").getAsBoolean() || !json.has("spawns")) {
			return;
		}
		for (JsonElement element : json.getAsJsonArray("spawns")) {
			JsonObject spawn = element.getAsJsonObject();
			if (!spawn.has("pokemon") || !spawn.has("bucket")) {
				continue;
			}
			ResourceLocation species = species(namespace, spawn.get("pokemon").getAsString());
			if (species == null) {
				continue;
			}
			boolean isRare = RARE_BUCKETS.contains(spawn.get("bucket").getAsString().toLowerCase(Locale.ROOT));
			rare.merge(species, isRare, Boolean::logicalAnd);
		}
	}

	/** "eevee", "eevee shiny=yes" or "cobblemon:eevee" as a species id (the first word; Cobblemon's namespace by default). */
	static ResourceLocation species(String namespace, String pokemon) {
		String first = pokemon.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
		if (first.isEmpty()) {
			return null;
		}
		return first.contains(":") ? ResourceLocation.tryParse(first) : ResourceLocation.tryBuild(namespace, first);
	}

	/** Sets the data (tests). */
	public static void set(Map<String, List<ResourceLocation>> newLures, Set<ResourceLocation> newRareOnly) {
		lures = Collections.unmodifiableMap(new LinkedHashMap<>(newLures));
		rareOnly = Set.copyOf(newRareOnly);
	}

	/** The lures in order, for the picker. */
	public static List<String> lureNames() {
		return new ArrayList<>(lures.keySet());
	}

	private HabitatData() {
	}

	static HabitatData create() {
		return new HabitatData();
	}
}
