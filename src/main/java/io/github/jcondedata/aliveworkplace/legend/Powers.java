package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** The kinds of {@link Power} a Legend file may name. An unknown type fails the file (design note, decision 7). */
public final class Powers {
	private static final Map<String, Function<JsonObject, Power>> KINDS = new LinkedHashMap<>();

	static {
		register("pace", PacePower::read);
		register("mood", MoodPower::read);
		register("grand_rebuild", GrandRebuildPower::read);
		register("far_expeditions", FarExpeditionsPower::read);
		register("expedition", ExpeditionPower::read);
		register("bank", BankPower::read);
		register("caravan_pay", CaravanPayPower::read);
		register("trade_fair", TradeFairPower::read);
		register("keeps_to", KeepsToPower::read);
		register("golem_forge", GolemForgePower::read);
		register("banquet", BanquetPower::read);
		GiftPowers.register();
		Seer.register();
		BardLaureate.register();
		Beastmaster.register();
		Founder.register();
		PokemonProfessor.register();
		PokemonRanger.register();
	}

	/** Adds a named power (each Legend item adds its own). */
	public static void register(String type, Function<JsonObject, Power> reader) {
		KINDS.put(type, reader);
	}

	public static Set<String> types() {
		return KINDS.keySet();
	}

	public static Power parse(JsonObject json) {
		String type = json.has("type") ? json.get("type").getAsString() : "";
		Function<JsonObject, Power> reader = KINDS.get(type);
		if (reader == null) {
			throw new IllegalArgumentException("unknown power type '" + type + "'");
		}
		try {
			return reader.apply(json);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("power '" + type + "': " + e.getMessage(), e);
		}
	}

	public static List<Power> parseAll(JsonArray array) {
		List<Power> out = new ArrayList<>();
		for (JsonElement e : array) {
			out.add(parse(e.getAsJsonObject()));
		}
		return List.copyOf(out);
	}

	private Powers() {
	}
}
