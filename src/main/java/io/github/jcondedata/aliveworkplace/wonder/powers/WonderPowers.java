package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The power types a Wonder's file may name (35.8), each registered by id with the code that reads its numbers, like
 * {@code legend/Powers} for Legends. {@code mood} and {@code beauty} are the generic two, made for server owners' own
 * Wonders; the others are declared ({@link DeclaredPower}) until their Wonder's item registers its own class in their
 * place. An unknown type, or numbers its reader refuses, fail the file (logged with its path and the reason).
 */
public final class WonderPowers {
	/** The types our Wonders' powers items (35.12 to 35.24) fill in, declared until then (35.10's, 35.16's and 35.22's are registered above). */
	public static final List<String> DECLARED = List.of("caravan_range", "caravan_speed", "ferry_fares",
		"early_warning", "research_tier", "sky_chart", "forecast", "harvest", "births",
		"stadium_days", "tournament_host");

	private static final Map<String, Function<JsonObject, WonderPower>> KINDS = new LinkedHashMap<>();

	static {
		register(MoodPower.TYPE, MoodPower::read);
		register(BeautyPower.TYPE, BeautyPower::read);
		register(UndertakersPower.TYPE, UndertakersPower::read); // the Great Cathedral's (35.10)
		register(WeddingsPower.TYPE, WeddingsPower::read);
		register(TravellersPower.TYPE, TravellersPower::read); // the Colossus's (35.22)
		register(RaidBarrierPower.TYPE, RaidBarrierPower::read); // the Great Wall's (35.16)
		register(SentriesPower.TYPE, SentriesPower::read);
		for (String type : DECLARED) {
			register(type, json -> DeclaredPower.read(type, json));
		}
	}

	/**
	 * Adds (or replaces) the power type {@code type}, read by {@code reader} from the file's power object (its
	 * {@code type} included). A Wonder's item calls this for its own types; files loaded after it read with it.
	 */
	public static synchronized void register(String type, Function<JsonObject, WonderPower> reader) {
		KINDS.put(type, reader);
	}

	/** Every type a file may name, in the order they were registered. */
	public static synchronized Set<String> types() {
		return Set.copyOf(KINDS.keySet());
	}

	/** Reads one power object; throws {@link IllegalArgumentException} naming an unknown type or what its reader refused. */
	public static WonderPower parse(JsonObject json) {
		String type = json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : "";
		Function<JsonObject, WonderPower> reader;
		synchronized (WonderPowers.class) {
			reader = KINDS.get(type);
		}
		if (reader == null) {
			throw new IllegalArgumentException("unknown power type '" + type + "'");
		}
		try {
			return reader.apply(json);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("power '" + type + "': " + e.getMessage(), e);
		}
	}

	private WonderPowers() {
	}
}
