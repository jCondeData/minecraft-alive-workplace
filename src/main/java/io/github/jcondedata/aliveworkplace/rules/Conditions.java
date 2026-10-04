package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * The kinds of {@link Condition} a data file may use, by type name. An unknown type is an error, so a typo never makes
 * something free (M29 design note, decision 7). {@code compat/} adds its own kinds with {@link #register}.
 */
public final class Conditions {
	private static final Map<String, Function<JsonObject, Condition>> KINDS = new LinkedHashMap<>();

	static {
		register("rank_at_least", RankAtLeast::read);
		register("villagers", VillagerCount::read);
		register("finished", FinishedBuildings::read);
		register("job_level", JobLevel::read);
		register("treasury_total", TreasuryTotal::read);
		register("caravan_routes", CaravanRoutes::read);
		register("meal_kinds", MealKinds::read);
		register("festival_crowd", FestivalCrowd::read);
		register("animals_at_job", AnimalsAtJob::read);
		register("research_levels", ResearchLevels::read);
		register("iron_golems", IronGolems::read);
		register("full_moon", FullMoon::read);
		register("first_city", FirstCity::read);
		register("legend", LegendSettled::read);
	}

	public static void register(String type, Function<JsonObject, Condition> reader) {
		KINDS.put(type, reader);
	}

	public static Set<String> types() {
		return KINDS.keySet();
	}

	/** One condition; throws {@link IllegalArgumentException} for an unknown type or a bad field. */
	public static Condition parse(JsonObject json) {
		String type = json.has("type") ? json.get("type").getAsString() : "";
		Function<JsonObject, Condition> reader = KINDS.get(type);
		if (reader == null) {
			throw new IllegalArgumentException("unknown condition type '" + type + "'");
		}
		try {
			return reader.apply(json);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("condition '" + type + "': " + e.getMessage(), e);
		}
	}

	public static List<Condition> parseAll(JsonArray array) {
		List<Condition> out = new ArrayList<>();
		for (JsonElement e : array) {
			out.add(parse(e.getAsJsonObject()));
		}
		return List.copyOf(out);
	}

	static int count(JsonObject json, String field) {
		if (!json.has(field)) {
			throw new IllegalArgumentException("missing '" + field + "'");
		}
		int n = json.get(field).getAsInt();
		if (n < 0) {
			throw new IllegalArgumentException("'" + field + "' below 0");
		}
		return n;
	}

	static ResourceLocation id(JsonObject json, String field) {
		ResourceLocation id = json.has(field) ? ResourceLocation.tryParse(json.get(field).getAsString()) : null;
		if (id == null) {
			throw new IllegalArgumentException("missing or bad '" + field + "'");
		}
		return id;
	}

	private Conditions() {
	}
}
