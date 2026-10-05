package io.github.jcondedata.aliveworkplace.cup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * The type chart the Festival Cup's bouts use (ROADMAP 28.18): how much damage a move of one type does to a Pokémon of
 * another, from {@code data/aliveworkplace/type_chart.json} (read on every load and {@code /reload}). The file lists,
 * for each of the {@link #TYPES 18 types}, the types it hits harder or softer than normal ({@code "fire": {"grass": 2}});
 * a pair it doesn't list is 1x. A file that can't be read, or lacks a type, is logged and the last good chart is kept.
 */
public final class TypeChart implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("type_chart");
	public static final ResourceLocation FILE = AliveWorkplace.id("type_chart.json");
	/** The 18 types, lower case, as Cobblemon names them. */
	public static final List<String> TYPES = List.of("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
		"flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");

	private static Map<String, Map<String, Double>> chart = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new TypeChart());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Optional<Resource> file = manager.getResource(FILE);
		if (file.isEmpty()) {
			AliveWorkplace.LOG.warn("No type chart at {}", FILE);
			return;
		}
		try (Reader reader = file.get().openAsReader()) {
			chart = read(JsonParser.parseReader(reader).getAsJsonObject());
		} catch (Exception e) {
			AliveWorkplace.LOG.warn("Skipping the type chart {}: {}", FILE, e.getMessage());
		}
	}

	/** Reads a chart; throws {@link IllegalArgumentException} naming what's wrong (an unknown type, a missing one, a bad number). */
	public static Map<String, Map<String, Double>> read(JsonObject json) {
		JsonObject body = json.has("chart") ? json.getAsJsonObject("chart") : json;
		Map<String, Map<String, Double>> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> attack : body.entrySet()) {
			String a = attack.getKey().toLowerCase(Locale.ROOT);
			if (!TYPES.contains(a)) {
				throw new IllegalArgumentException("unknown type " + attack.getKey());
			}
			Map<String, Double> row = new HashMap<>();
			for (Map.Entry<String, JsonElement> defend : attack.getValue().getAsJsonObject().entrySet()) {
				String d = defend.getKey().toLowerCase(Locale.ROOT);
				if (!TYPES.contains(d)) {
					throw new IllegalArgumentException("unknown type " + defend.getKey() + " under " + a);
				}
				double m = defend.getValue().getAsDouble();
				if (m < 0 || m > 4) {
					throw new IllegalArgumentException(a + " on " + d + " must be 0 to 4, not " + m);
				}
				row.put(d, m);
			}
			out.put(a, Map.copyOf(row));
		}
		for (String type : TYPES) {
			if (!out.containsKey(type)) {
				throw new IllegalArgumentException("missing type " + type);
			}
		}
		return Map.copyOf(out);
	}

	/** How much a move of type {@code attack} does to a Pokémon of type {@code defend} (1 for anything not in the chart). */
	public static double multiplier(String attack, String defend) {
		return chart.getOrDefault(attack.toLowerCase(Locale.ROOT), Map.of()).getOrDefault(defend.toLowerCase(Locale.ROOT), 1.0);
	}

	/** How much a move of type {@code attack} does to a Pokémon with all of {@code types} (the multipliers times each other). */
	public static double multiplier(String attack, Collection<String> types) {
		double m = 1;
		for (String t : types) {
			m *= multiplier(attack, t);
		}
		return m;
	}

	/** The types the loaded chart has (all 18 once a good file is read). */
	public static java.util.Set<String> loadedTypes() {
		return chart.keySet();
	}

	private TypeChart() {
	}
}
