package io.github.jcondedata.aliveworkplace.cup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;

/**
 * The Festival Cup's themes (ROADMAP 28.17), as data: {@code data/<ns>/cups/<name>.json}, read on every load and
 * {@code /reload}. A file holds the theme's name (a lang key), its place in the order, the format (singles or doubles),
 * the level every Pokémon battles at, how many each trainer brings, the allowed types (none listed: any), the stage
 * (first, final or any), the banned labels, the Showdown rules, when the bouts start and must end (time of day; noon to
 * midnight unless it says), the fair's wares (item and price in emeralds), the feast dish, the firework colours and the
 * bard's disc. The Workers' Cup (28.22) adds {@code partner_days} (a player's Pokémon must have helped a villager at work on
 * that many days, see {@code work/Partners}) and {@code worker_types} (villager trainers field the types the jobs' partners
 * are: {@code Partners.allTypes}). Items are kept as ids (a theme may name Cobblemon's, which a game without Cobblemon doesn't have). A file
 * that can't be read is logged and skipped.
 */
public final class CupThemes implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("cup_themes");
	public static final String FOLDER = "cups";
	/** Noon and midnight, in ticks of the day: the bouts' hours when a theme doesn't say. */
	public static final long NOON = 6000;
	public static final long MIDNIGHT = 18000;
	static final Set<String> FORMATS = Set.of("singles", "doubles");
	static final Set<String> STAGES = Set.of("first", "final", "any");

	/** One thing the fair sells: the item's id and its price in emeralds. */
	public record Ware(ResourceLocation item, int price) {
	}

	/** A theme. {@code types} empty: any type. */
	public record Theme(ResourceLocation id, String name, int order, String format, int level, int bring, List<String> types, String stage,
		List<String> banned, List<String> rules, long start, long end, List<Ware> wares, ResourceLocation dish, List<Integer> fireworks,
		ResourceLocation disc, int partnerDays, boolean workerTypes) {
		/** A theme without the Workers' Cup's rules. */
		public Theme(ResourceLocation id, String name, int order, String format, int level, int bring, List<String> types, String stage,
			List<String> banned, List<String> rules, long start, long end, List<Ware> wares, ResourceLocation dish, List<Integer> fireworks,
			ResourceLocation disc) {
			this(id, name, order, format, level, bring, types, stage, banned, rules, start, end, wares, dish, fireworks, disc, 0, false);
		}

		public boolean doubles() {
			return format.equals("doubles");
		}

		/** The types villager trainers' teams are drawn from (lower case; empty: any). */
		public java.util.Set<String> trainerTypes() {
			java.util.Set<String> out = new java.util.TreeSet<>();
			if (workerTypes) {
				out.addAll(io.github.jcondedata.aliveworkplace.work.Partners.allTypes());
			} else {
				types.forEach(t -> out.add(t.toLowerCase(Locale.ROOT)));
			}
			return out;
		}
	}

	private static Map<ResourceLocation, Theme> themes = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new CupThemes());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				StringBuilder text = new StringBuilder();
				char[] buf = new char[4096];
				for (int n; (n = reader.read(buf)) > 0; ) {
					text.append(buf, 0, n);
				}
				files.put(e.getKey(), text.toString());
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping Cup theme {}: {}", e.getKey(), ex.toString());
			}
		}
		themes = load(files);
		AliveWorkplace.LOG.info("Cup themes: {}", themes.keySet());
	}

	/** Reads every file ({@code data/ns/cups/name.json} → its text); one that can't be read is logged and skipped. */
	public static Map<ResourceLocation, Theme> load(Map<ResourceLocation, String> files) {
		Map<ResourceLocation, Theme> out = new LinkedHashMap<>();
		files.forEach((file, text) -> {
			String path = file.getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length()));
			try {
				out.put(id, read(id, JsonParser.parseString(text).getAsJsonObject()));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping Cup theme {}: {}", file, ex.getMessage());
			}
		});
		List<Theme> sorted = new ArrayList<>(out.values());
		sorted.sort(Comparator.comparingInt(Theme::order).thenComparing(t -> t.id().toString()));
		Map<ResourceLocation, Theme> ordered = new LinkedHashMap<>();
		sorted.forEach(t -> ordered.put(t.id(), t));
		return Collections.unmodifiableMap(ordered);
	}

	/** Reads one theme; throws {@link IllegalArgumentException} naming what's wrong. */
	public static Theme read(ResourceLocation id, JsonObject json) {
		String name = string(json, "name", null);
		int order = integer(json, "order", null, 0, 1000);
		String format = string(json, "format", "singles").toLowerCase(Locale.ROOT);
		if (!FORMATS.contains(format)) {
			throw new IllegalArgumentException("format must be singles or doubles, not " + format);
		}
		int level = integer(json, "level", null, 1, 100);
		int bring = integer(json, "bring", null, 1, 6);
		List<String> types = strings(json, "types");
		String stage = string(json, "stage", "any").toLowerCase(Locale.ROOT);
		if (!STAGES.contains(stage)) {
			throw new IllegalArgumentException("stage must be first, final or any, not " + stage);
		}
		List<String> banned = strings(json, "banned");
		List<String> rules = strings(json, "rules");
		long start = integer(json, "start", (int) NOON, 0, 23999);
		long end = integer(json, "end", (int) MIDNIGHT, 0, 47999);
		if (end <= start) {
			end += 24000; // runs past midnight, until the next morning
		}
		List<Ware> wares = new ArrayList<>();
		JsonArray wareList = json.has("wares") ? json.getAsJsonArray("wares") : new JsonArray();
		for (JsonElement e : wareList) {
			JsonObject w = e.getAsJsonObject();
			wares.add(new Ware(location(w, "item"), integer(w, "price", null, 1, 64)));
		}
		ResourceLocation dish = location(json, "dish");
		List<Integer> fireworks = new ArrayList<>();
		for (String colour : strings(json, "fireworks")) {
			String hex = colour.startsWith("#") ? colour.substring(1) : colour;
			try {
				fireworks.add(Integer.parseInt(hex, 16) & 0xFFFFFF);
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("firework colour " + colour + " isn't #RRGGBB");
			}
		}
		if (fireworks.isEmpty()) {
			throw new IllegalArgumentException("no firework colours");
		}
		ResourceLocation disc = location(json, "disc");
		int partnerDays = integer(json, "partner_days", 0, 0, 1000);
		boolean workerTypes = json.has("worker_types") && json.get("worker_types").getAsBoolean();
		return new Theme(id, name, order, format, level, bring, List.copyOf(types), stage, List.copyOf(banned), List.copyOf(rules), start, end,
			List.copyOf(wares), dish, List.copyOf(fireworks), disc, partnerDays, workerTypes);
	}

	private static String string(JsonObject json, String key, @Nullable String fallback) {
		if (!json.has(key)) {
			if (fallback == null) {
				throw new IllegalArgumentException("missing " + key);
			}
			return fallback;
		}
		return json.get(key).getAsString();
	}

	private static int integer(JsonObject json, String key, @Nullable Integer fallback, int min, int max) {
		if (!json.has(key)) {
			if (fallback == null) {
				throw new IllegalArgumentException("missing " + key);
			}
			return fallback;
		}
		int v = json.get(key).getAsInt();
		if (v < min || v > max) {
			throw new IllegalArgumentException(key + " must be " + min + " to " + max + ", not " + v);
		}
		return v;
	}

	private static List<String> strings(JsonObject json, String key) {
		List<String> out = new ArrayList<>();
		if (json.has(key)) {
			for (JsonElement e : json.getAsJsonArray(key)) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	private static ResourceLocation location(JsonObject json, String key) {
		ResourceLocation id = ResourceLocation.tryParse(string(json, key, null));
		if (id == null) {
			throw new IllegalArgumentException(key + " isn't an id");
		}
		return id;
	}

	/** Every theme, in order. */
	public static List<Theme> all() {
		return List.copyOf(themes.values());
	}

	@Nullable
	public static Theme get(@Nullable ResourceLocation id) {
		return id == null ? null : themes.get(id);
	}

	/** The theme after {@code last} in the order (the first after the last one, or when there was none). */
	@Nullable
	public static Theme next(@Nullable ResourceLocation last) {
		List<Theme> all = all();
		if (all.isEmpty()) {
			return null;
		}
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).id().equals(last)) {
				return all.get((i + 1) % all.size());
			}
		}
		return all.get(0);
	}

	/** Puts a set of themes in place of the loaded ones (tests); null puts the loaded ones back on the next reload. */
	public static void setForTest(Map<ResourceLocation, Theme> loaded) {
		themes = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
	}

	public static Map<ResourceLocation, Theme> loaded() {
		return themes;
	}

	private CupThemes() {
	}
}
