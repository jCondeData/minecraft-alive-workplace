package io.github.jcondedata.aliveworkplace.city;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;

/**
 * The Steward's rules (ROADMAP 27.6): what he wants for his village, as data. {@code data/<ns>/steward_rules/<name>.json}
 * holds one rule:
 * <ul>
 * <li>{@code when}: its conditions ({@link StewardConditions}), {@code [{"beds_short": {"at_least": 1}}, ...]}, all of
 * which must hold;</li>
 * <li>{@code do}: one effect, {@code {"build": {"blueprint": "aliveworkplace:stone_house", "zone": "homes"}}},
 * {@code upgrade {blueprint}}, {@code assign_jobs {}}, {@code research {topic?}} or {@code ask {key}} (a tip only a
 * player can act on);</li>
 * <li>{@code priority} 0–100 (50), {@code why} (a lang key filled with the conditions' numbers, in order),
 * {@code cooldown_days} (0: the days the rule rests after its wish was carried out), {@code max} (how many times it may be
 * carried out in a village; no limit when left out), {@code min_rank} ({@code hamlet}) and {@code requires} (mod ids).</li>
 * </ul>
 * A file with an unknown condition or effect, a bad or unknown field, is skipped with a warning naming the file and the
 * field. Each morning the Steward ranks the rules that hold into the day's wishes ({@link StewardWishes}).
 */
public final class StewardRules implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("steward_rules");
	public static final String FOLDER = "steward_rules";

	/** A rule file with something wrong: {@code field} names where ({@code when[1].at_least}). */
	public static final class BadRule extends RuntimeException {
		public final String field;

		public BadRule(String field, String message) {
			super(field + ": " + message);
			this.field = field;
		}
	}

	/** One effect: what the Steward would do. 27.7–27.9 carry them out. */
	public enum Kind {
		BUILD, UPGRADE, ASSIGN_JOBS, RESEARCH, ASK;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Kind byKey(String key) {
			for (Kind k : values()) {
				if (k.key().equals(key)) {
					return k;
				}
			}
			return null;
		}

		public static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(s -> {
			Kind k = byKey(s);
			return k != null ? DataResult.success(k) : DataResult.error(() -> "unknown effect " + s);
		}, Kind::key);
	}

	/** A rule's effect: {@code build {blueprint, zone}}, {@code upgrade {blueprint?, adds_beds?}}, {@code assign_jobs}, {@code research {topic?}}, {@code ask {key}}. */
	public record Effect(Kind kind, Optional<ResourceLocation> blueprint, Optional<String> zone, Optional<String> topic, Optional<String> key,
						 boolean addsBeds) {
		public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
			Kind.CODEC.fieldOf("type").forGetter(Effect::kind),
			ResourceLocation.CODEC.optionalFieldOf("blueprint").forGetter(Effect::blueprint),
			Codec.STRING.optionalFieldOf("zone").forGetter(Effect::zone),
			Codec.STRING.optionalFieldOf("topic").forGetter(Effect::topic),
			Codec.STRING.optionalFieldOf("key").forGetter(Effect::key),
			Codec.BOOL.optionalFieldOf("adds_beds", false).forGetter(Effect::addsBeds)
		).apply(i, Effect::new));

		public Effect(Kind kind, Optional<ResourceLocation> blueprint, Optional<String> zone, Optional<String> topic, Optional<String> key) {
			this(kind, blueprint, zone, topic, key, false);
		}

		/** "Build a Stone House in a Homes zone", for explain and the Steward's line. */
		public Component describe() {
			return switch (kind) {
				case BUILD -> Component.translatable("steward.aliveworkplace.effect.build", Blueprints.displayName(blueprint.orElseThrow()),
					CityZones.get(zone.orElse("")).map(CityZones.Kind::title).orElse(Component.literal(zone.orElse(""))));
				case UPGRADE -> blueprint.isPresent()
					? Component.translatable("steward.aliveworkplace.effect.upgrade", Blueprints.displayName(blueprint.get()))
					: Component.translatable(addsBeds ? "steward.aliveworkplace.effect.upgrade.beds" : "steward.aliveworkplace.effect.upgrade.any");
				case ASSIGN_JOBS -> Component.translatable("steward.aliveworkplace.effect.assign_jobs");
				case RESEARCH -> topic.flatMap(StewardRules::topicOf)
					.<Component>map(t -> Component.translatable("steward.aliveworkplace.effect.research", t.title()))
					.orElse(Component.translatable("steward.aliveworkplace.effect.research.any"));
				case ASK -> Component.translatable("steward.aliveworkplace.effect.ask", Component.translatable(key.orElse("")));
			};
		}
	}

	/** One rule (its id is the file's name). */
	public record Rule(ResourceLocation id, List<StewardConditions.Condition> when, Effect effect, int priority, String why,
					   int cooldownDays, int max, VillageRanks.Rank minRank, List<String> requires) {
		/** No {@code max}: as often as it holds. */
		public static final int NO_MAX = Integer.MAX_VALUE;
	}

	private static List<Rule> rules = List.of();

	public static void init() {
		Platform.get().onDataReload(ID, new StewardRules());
		StewardWishes.init();
	}

	/** Every rule loaded, highest priority first. */
	public static List<Rule> all() {
		return rules;
	}

	/** Puts in other rules (tests); returns those that were there. */
	public static List<Rule> set(List<Rule> next) {
		List<Rule> before = rules;
		rules = sorted(next);
		return before;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				files.put(e.getKey(), JsonParser.parseReader(reader));
			} catch (Exception ex) {
				warn(e.getKey(), "file", "not readable JSON (" + ex.getMessage() + ")");
			}
		}
		rules = readAll(files);
		AliveWorkplace.LOG.info("Alive Workplace: {} Steward rules", rules.size());
	}

	/**
	 * Reads rule files ({@code <ns>:steward_rules/<name>.json} to their JSON): the good ones, highest priority first; each
	 * bad one is skipped with a warning naming the file and the field.
	 */
	public static List<Rule> readAll(Map<ResourceLocation, JsonElement> files) {
		List<Rule> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
			ResourceLocation file = e.getKey();
			String path = file.getPath();
			String name = path.startsWith(FOLDER + "/") ? path.substring(FOLDER.length() + 1) : path;
			name = name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
			try {
				out.add(read(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name), e.getValue()));
			} catch (BadRule bad) {
				warn(file, bad.field, bad.getMessage().substring(bad.field.length() + 2));
			} catch (RuntimeException ex) {
				warn(file, "file", ex.toString());
			}
		}
		return sorted(out);
	}

	private static void warn(ResourceLocation file, String field, String message) {
		AliveWorkplace.LOG.warn("Skipping Steward rule {}: field '{}': {}", file, field, message);
	}

	private static List<Rule> sorted(List<Rule> list) {
		List<Rule> out = new ArrayList<>(list);
		out.sort(Comparator.comparingInt(Rule::priority).reversed().thenComparing(r -> r.id().toString()));
		return List.copyOf(out);
	}

	/** Reads one rule; throws {@link BadRule} naming the field that's wrong. */
	public static Rule read(ResourceLocation id, JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			throw new BadRule("file", "not a JSON object");
		}
		Fields f = new Fields(json.getAsJsonObject(), "");
		List<StewardConditions.Condition> when = new ArrayList<>();
		JsonElement list = f.raw("when");
		if (list != null) {
			if (!list.isJsonArray()) {
				throw new BadRule("when", "not a list of conditions");
			}
			JsonArray array = list.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				when.add(StewardConditions.parse(array.get(i), "when[" + i + "]"));
			}
		}
		Effect does = effect(f.raw("do"));
		int priority = f.integer("priority", 50, 0, 100);
		String why = f.string("why", null);
		int cooldown = f.integer("cooldown_days", 0, 0, 3650);
		int max = f.integer("max", Rule.NO_MAX, 1, Rule.NO_MAX);
		VillageRanks.Rank minRank = f.has("min_rank") ? rank(f, "min_rank") : VillageRanks.Rank.HAMLET;
		List<String> requires = f.strings("requires");
		f.done();
		return new Rule(id, List.copyOf(when), does, priority, why, cooldown, max, minRank, List.copyOf(requires));
	}

	/** {@code "do": {"build": {"blueprint": ..., "zone": ...}}}: one effect's name and its fields. */
	private static Effect effect(@Nullable JsonElement json) {
		if (json == null) {
			throw new BadRule("do", "missing");
		}
		if (!json.isJsonObject() || json.getAsJsonObject().size() != 1) {
			throw new BadRule("do", "an effect is one name and its fields, as {\"build\": {\"blueprint\": ..., \"zone\": ...}}");
		}
		Map.Entry<String, JsonElement> only = json.getAsJsonObject().entrySet().iterator().next();
		Kind kind = Kind.byKey(only.getKey());
		if (kind == null) {
			throw new BadRule("do." + only.getKey(), "unknown effect (build, upgrade, assign_jobs, research, ask)");
		}
		if (!only.getValue().isJsonObject()) {
			throw new BadRule("do." + only.getKey(), "its fields must be an object ({} for none), not " + only.getValue());
		}
		Fields f = new Fields(only.getValue().getAsJsonObject(), "do." + only.getKey());
		Effect effect = switch (kind) {
			case BUILD -> {
				ResourceLocation blueprint = f.id("blueprint");
				String zone = f.string("zone", null);
				if (ResourceLocation.tryParse(zone) == null) {
					throw new BadRule(f.path("zone"), "not a zone kind: " + zone);
				}
				yield new Effect(kind, Optional.of(blueprint), Optional.of(zone), Optional.empty(), Optional.empty());
			}
			case UPGRADE -> new Effect(kind, f.has("blueprint") ? Optional.of(f.id("blueprint")) : Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), f.bool("adds_beds", false));
			case ASSIGN_JOBS -> new Effect(kind, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
			case RESEARCH -> new Effect(kind, Optional.empty(), Optional.empty(),
				f.has("topic") ? Optional.of(topic(f, "topic").key()) : Optional.empty(), Optional.empty());
			case ASK -> new Effect(kind, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(f.string("key", null)));
		};
		f.done();
		return effect;
	}

	static VillageRanks.Rank rank(Fields f, String key) {
		String name = f.string(key, null);
		try {
			return VillageRanks.Rank.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BadRule(f.path(key), "unknown rank '" + name + "' (hamlet, village, town, city)");
		}
	}

	static Research.Topic topic(Fields f, String key) {
		String name = f.string(key, null);
		return topicOf(name).orElseThrow(() -> new BadRule(f.path(key), "unknown research topic '" + name + "'"));
	}

	static Optional<Research.Topic> topicOf(String name) {
		try {
			return Optional.of(Research.Topic.valueOf(name.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** A JSON object's fields, read with checks: each problem names the field; {@link #done} refuses fields nobody read. */
	public static final class Fields {
		private final JsonObject json;
		private final String at;
		private final Set<String> read = new HashSet<>();

		Fields(JsonObject json, String at) {
			this.json = json;
			this.at = at;
		}

		/** The full name of a field: {@code when[0].at_least}. */
		public String path(String key) {
			return at.isEmpty() ? key : at + "." + key;
		}

		public boolean has(String key) {
			return json.has(key);
		}

		@Nullable
		JsonElement raw(String key) {
			read.add(key);
			return json.get(key);
		}

		private JsonElement required(String key) {
			JsonElement e = raw(key);
			if (e == null || e.isJsonNull()) {
				throw new BadRule(path(key), "missing");
			}
			return e;
		}

		/** A whole number from {@code min} to {@code max}; {@code fallback} when it's left out (null: it must be there). */
		public int integer(String key, @Nullable Integer fallback, int min, int max) {
			if (!has(key) && fallback != null) {
				raw(key);
				return fallback;
			}
			JsonElement e = required(key);
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
				throw new BadRule(path(key), "not a number: " + e);
			}
			double d = e.getAsDouble();
			if (d != Math.rint(d) || d < min || d > max) {
				throw new BadRule(path(key), "must be a whole number from " + min + (max == Integer.MAX_VALUE ? " up" : " to " + max) + ", not " + e);
			}
			return (int) d;
		}

		/** A share from 0 to 1. */
		public double share(String key, @Nullable Double fallback) {
			if (!has(key) && fallback != null) {
				raw(key);
				return fallback;
			}
			JsonElement e = required(key);
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || e.getAsDouble() < 0 || e.getAsDouble() > 1) {
				throw new BadRule(path(key), "must be a number from 0 to 1, not " + e);
			}
			return e.getAsDouble();
		}

		/** A non-empty string. */
		public String string(String key, @Nullable String fallback) {
			if (!has(key) && fallback != null) {
				raw(key);
				return fallback;
			}
			JsonElement e = required(key);
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank()) {
				throw new BadRule(path(key), "must be some text, not " + e);
			}
			return e.getAsString();
		}

		public boolean bool(String key, boolean fallback) {
			JsonElement e = raw(key);
			if (e == null) {
				return fallback;
			}
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
				throw new BadRule(path(key), "must be true or false, not " + e);
			}
			return e.getAsBoolean();
		}

		/** An id such as {@code aliveworkplace:stone_house}. */
		public ResourceLocation id(String key) {
			String s = string(key, null);
			ResourceLocation id = ResourceLocation.tryParse(s);
			if (id == null) {
				throw new BadRule(path(key), "not an id: " + s);
			}
			return id;
		}

		/** A list of strings (empty when left out). */
		public List<String> strings(String key) {
			JsonElement e = raw(key);
			if (e == null) {
				return List.of();
			}
			if (!e.isJsonArray()) {
				throw new BadRule(path(key), "must be a list, not " + e);
			}
			List<String> out = new ArrayList<>();
			for (JsonElement item : e.getAsJsonArray()) {
				if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString() || item.getAsString().isBlank()) {
					throw new BadRule(path(key), "must be a list of text, not " + e);
				}
				out.add(item.getAsString());
			}
			return out;
		}

		/** Refuses any field nobody read (a typo would otherwise do nothing, silently). */
		public void done() {
			for (String key : json.keySet()) {
				if (!read.contains(key)) {
					throw new BadRule(path(key), "unknown field");
				}
			}
		}
	}

	private StewardRules() {
	}
}
