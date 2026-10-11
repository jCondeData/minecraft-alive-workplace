package io.github.jcondedata.aliveworkplace.threat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * One disaster, as {@code data/<ns>/disasters/<id>.json} describes it (ROADMAP 32.15; the shape is in
 * {@code docs/design/M32.md}, "disasters/&lt;id&gt;.json"):
 * <ul>
 * <li>{@code when}: the conditions of {@link DisasterConditions} (the raider cultures' toolbox, plus the weather, dry
 * days, the wood share and crowding); absent: always;</li>
 * <li>{@code chance} a day (default 0), {@code factors} ({@code if} conditions, then a {@code chance} that replaces the
 * base or a multiplier {@code times}) and {@code requires} ({@code unless} conditions, else {@code when} must hold);</li>
 * <li>{@code duration}: {@code min_days}, {@code max_days} (0: no end by time) and {@code ends_with_rain};</li>
 * <li>{@code effects}: a list, each with a {@code type} of {@link Effect.Type};</li>
 * <li>{@code answered_by} (a profession id), {@code foretold_by} (profession ids, or {@code seer}: 32.14's sources),
 * {@code foretell_days} (default 1), {@code messages} (the lang key prefix of its chat lines: {@code .name}, {@code .start},
 * {@code .foretold}, {@code .end}; absent: the engine's own lines, naming it by its id), {@code chronicle} (the same for
 * the chronicle: {@code .start}, {@code .foretold}, {@code .end}) and
 * {@code kind} ({@code fire}, {@code drought} or {@code plague}; default by its effects).</li>
 * </ul>
 */
public record Disaster(ResourceLocation id, DisasterConditions when, double chance, List<Factor> factors, List<Require> requires, int minDays,
	int maxDays, boolean endsWithRain, List<Effect> effects, Optional<ResourceLocation> answeredBy, List<String> foretoldBy, int foretellDays,
	String messages, String chronicle, Chronicle.Kind kind) {
	private static final Set<String> KEYS = Set.of("when", "chance", "factors", "requires", "duration", "effects", "answered_by", "foretold_by",
		"foretell_days", "messages", "chronicle", "kind");

	/** Where its {@code iff} holds, the day's chance is {@code chance} (if set, else unchanged) times {@code times}. */
	public record Factor(DisasterConditions iff, Optional<Double> chance, double times) {
	}

	/** An extra gate: unless {@code unless} holds (empty: never), {@code when} must. */
	public record Require(Optional<DisasterConditions> unless, DisasterConditions when) {
	}

	/** One effect of the toolbox; only the fields its {@link Type} uses are set. */
	public record Effect(Type type, double factor, int amount, String reason, List<ResourceLocation> unlessBuilt, Optional<ResourceLocation> job,
		int range) {
		public enum Type {
			CROP_GROWTH, FARMLAND_DRIES, CAULDRONS_DRAIN, ILLNESS_CHANCE, CONTAGION, MOOD, FIRES_A_DAY, JOB_PACE
		}

		static Effect read(JsonObject json, String file) {
			String name = json.get("type").getAsString();
			Type type;
			try {
				type = Type.valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("no effect type '" + name + "'");
			}
			List<ResourceLocation> unless = new ArrayList<>();
			if (json.has("unless_built")) {
				for (JsonElement e : json.getAsJsonArray("unless_built")) {
					unless.add(ResourceLocation.parse(e.getAsString()));
				}
			}
			double factor = switch (type) {
				case CROP_GROWTH, JOB_PACE -> num(json, "factor", 1);
				case ILLNESS_CHANCE -> num(json, "times", 1);
				case CONTAGION -> num(json, "chance", 0.2);
				default -> 0;
			};
			int amount = switch (type) {
				case CAULDRONS_DRAIN -> (int) num(json, "levels_a_day", 1);
				case MOOD -> (int) num(json, "amount", -10);
				case FIRES_A_DAY -> (int) num(json, "count", 1);
				default -> 0;
			};
			if (factor < 0 || (type != Type.MOOD && amount < 0)) {
				throw new IllegalArgumentException(name + ": a number can't be below 0");
			}
			if (type == Type.JOB_PACE && !json.has("job")) {
				throw new IllegalArgumentException("job_pace needs a job");
			}
			return new Effect(type, factor, amount, json.has("reason") ? json.get("reason").getAsString() : "mood.aliveworkplace.reason.disaster",
				List.copyOf(unless), json.has("job") ? Optional.of(ResourceLocation.parse(json.get("job").getAsString())) : Optional.empty(),
				(int) num(json, "range", 3));
		}

		private static double num(JsonObject json, String key, double fallback) {
			return json.has(key) ? json.get(key).getAsDouble() : fallback;
		}
	}

	/** The first effect of {@code type}, if it has one. */
	public Optional<Effect> effect(Effect.Type type) {
		for (Effect e : effects) {
			if (e.type() == type) {
				return Optional.of(e);
			}
		}
		return Optional.empty();
	}

	/** Reads the disaster {@code id} from {@code json}; throws {@link IllegalArgumentException} naming what's wrong. */
	public static Disaster read(ResourceLocation id, JsonObject json) {
		String file = id.toString();
		for (String key : json.keySet()) {
			if (!KEYS.contains(key)) {
				AliveWorkplace.LOG.warn("Disaster {}: the key '{}' isn't known and is ignored", file, key);
			}
		}
		DisasterConditions when = json.has("when") ? DisasterConditions.read(json.getAsJsonObject("when"), file) : DisasterConditions.ANY;
		double chance = json.has("chance") ? json.get("chance").getAsDouble() : 0;
		if (chance < 0 || chance > 1) {
			throw new IllegalArgumentException("chance must be 0 to 1, not " + chance);
		}
		List<Factor> factors = new ArrayList<>();
		if (json.has("factors")) {
			for (JsonElement e : json.getAsJsonArray("factors")) {
				JsonObject f = e.getAsJsonObject();
				factors.add(new Factor(DisasterConditions.read(f.getAsJsonObject("if"), file),
					f.has("chance") ? Optional.of(f.get("chance").getAsDouble()) : Optional.empty(), f.has("times") ? f.get("times").getAsDouble() : 1));
			}
		}
		List<Require> requires = new ArrayList<>();
		if (json.has("requires")) {
			for (JsonElement e : json.getAsJsonArray("requires")) {
				JsonObject r = e.getAsJsonObject();
				requires.add(new Require(r.has("unless") ? Optional.of(DisasterConditions.read(r.getAsJsonObject("unless"), file)) : Optional.empty(),
					DisasterConditions.read(r.getAsJsonObject("when"), file)));
			}
		}
		int min = 1;
		int max = 1;
		boolean rain = false;
		if (json.has("duration")) {
			JsonObject d = json.getAsJsonObject("duration");
			min = d.has("min_days") ? d.get("min_days").getAsInt() : d.has("days") ? d.get("days").getAsInt() : 0;
			max = d.has("max_days") ? d.get("max_days").getAsInt() : min;
			rain = d.has("ends_with_rain") && d.get("ends_with_rain").getAsBoolean();
		}
		if (min < 0 || max < min) {
			throw new IllegalArgumentException("duration: min_days " + min + ", max_days " + max);
		}
		if (max == 0 && !rain) {
			throw new IllegalArgumentException("duration: a disaster must end after some days or with rain");
		}
		List<Effect> effects = new ArrayList<>();
		if (json.has("effects")) {
			for (JsonElement e : json.getAsJsonArray("effects")) {
				effects.add(Effect.read(e.getAsJsonObject(), file));
			}
		}
		List<String> foretold = new ArrayList<>();
		if (json.has("foretold_by")) {
			for (JsonElement e : json.getAsJsonArray("foretold_by")) {
				String s = e.getAsString();
				if (!s.equals(SEER) && ResourceLocation.tryParse(s) == null) {
					throw new IllegalArgumentException("foretold_by: '" + s + "' is no job id or 'seer'");
				}
				foretold.add(s);
			}
		}
		String messages = json.has("messages") ? json.get("messages").getAsString() : "";
		String chronicle = json.has("chronicle") ? json.get("chronicle").getAsString() : "";
		Chronicle.Kind kind;
		if (json.has("kind")) {
			String k = json.get("kind").getAsString().toUpperCase(Locale.ROOT);
			if (!k.equals("FIRE") && !k.equals("DROUGHT") && !k.equals("PLAGUE")) {
				throw new IllegalArgumentException("kind must be fire, drought or plague, not " + k);
			}
			kind = Chronicle.Kind.valueOf(k);
		} else {
			kind = effects.stream().anyMatch(e -> e.type() == Effect.Type.FIRES_A_DAY) ? Chronicle.Kind.FIRE
				: effects.stream().anyMatch(e -> e.type() == Effect.Type.CONTAGION || e.type() == Effect.Type.ILLNESS_CHANCE) ? Chronicle.Kind.PLAGUE
				: Chronicle.Kind.DROUGHT;
		}
		return new Disaster(id, when, chance, List.copyOf(factors), List.copyOf(requires), min, max, rain, List.copyOf(effects),
			json.has("answered_by") ? Optional.of(ResourceLocation.parse(json.get("answered_by").getAsString())) : Optional.empty(),
			List.copyOf(foretold), json.has("foretell_days") ? Math.max(0, json.get("foretell_days").getAsInt()) : 1, messages, chronicle, kind);
	}

	/** The {@code foretold_by} entry for 32.14's sources of warning (a guard or scout by a tower, the Seer's list). */
	public static final String SEER = "seer";
}
