package io.github.jcondedata.aliveworkplace.wonder;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * The drawing a Wonder was begun with (ROADMAP 52.6, the old-drawing fallback). A Wonder is begun with its drawing of the
 * day ({@link Wonders#drawing}, the key its file names), recorded on the site ({@link WonderSite#drawing}). When the
 * Wonder is redrawn, the drawing it replaced ships in {@code data/<ns>/blueprint_history/wonder/<id>/}: its file as it
 * was ({@code <key>.json}: its stages, their parts and offsets, their goods) and each stage part
 * ({@code stage_<n>_<i>/<key>.nbt}, read by {@link Refreshes} with every other older drawing), both written by
 * {@code tools/blueprints/wonder_history.py}. A Wonder begun with an older drawing goes on with it: it is counted,
 * repaired and its next stage built from that drawing's stages ({@link #of}), so a standing Wonder doesn't read as
 * damaged, its builders don't "repair" it into the new design and no new stage is stacked on the old ones. A new Wonder
 * takes today's drawing.
 *
 * <p>An older drawing's parts are named {@code <ns>:wonder/older/<key>/<part path>} ({@link #olderId}), which
 * {@link BlueprintLibrary#get} reads from the history, so build sites, repairs and outlines work with them as with any
 * blueprint. A site whose part sites were made before the redraw (named by today's ids) is built from its drawing's part
 * all the same ({@link #partFor}).
 *
 * <p>A Wonder begun before this record existed has none, and is matched by best fit, the way
 * {@code threat/CampCleanup} matches an older camp: today's drawing and each older one shipped are tried, and the one
 * of which the most stands where the Wonder is (its stages built so far) is taken (a tie goes to today's) and recorded.
 * Only once all of it is loaded; until then today's drawing stands in, unrecorded.
 */
public final class WonderDrawings {
	private static final String DIR = "blueprint_history";
	/** Where a Wonder's older drawings' files are, under {@link #DIR}. */
	private static final String WONDERS = DIR + "/" + WonderBlueprints.FOLDER;
	/** The path prefix of an older drawing's part: {@code wonder/older/<key>/<part path>}. */
	public static final String OLDER = WonderBlueprints.FOLDER + "older/";
	/** Ticks between tries to match a Wonder whose ground isn't all loaded. */
	static final int RETRY = 200;

	@Nullable
	private static ResourceManager loadedFrom;
	/** The older drawings' stages (part ids as their file names them), by Wonder and key, as shipped. */
	private static Map<ResourceLocation, Map<String, List<Wonder.Stage>>> shipped = Map.of();
	/** Older drawings added by hand (tests, or a datapack's own code), by Wonder and key. */
	private static final Map<ResourceLocation, Map<String, List<Wonder.Stage>>> EXTRA = new HashMap<>();
	/** Each older drawing as a Wonder (its parts named by {@link #olderId}), by Wonder and key; cleared when anything reloads. */
	private static final Map<String, Wonder> OLDER_WONDERS = new ConcurrentHashMap<>();
	/** Each older drawing's whole blueprint (its parts merged), by Wonder and key. */
	private static final Map<String, Optional<Blueprint>> WHOLE = new ConcurrentHashMap<>();
	/** When a site waiting for its ground to load was last tried (game time), by site. */
	private static final Map<UUID, Long> TRIED = new ConcurrentHashMap<>();

	// ---- the drawings ------------------------------------------------------------------------------------------------

	/** Adds an older drawing {@code key} of the Wonder {@code wonder}: its stages, parts named as its file would name them. */
	public static synchronized void addHistory(ResourceLocation wonder, String key, List<Wonder.Stage> stages) {
		EXTRA.computeIfAbsent(wonder, k -> new LinkedHashMap<>()).put(key, List.copyOf(stages));
		forget();
	}

	/** Forgets the older drawings added by hand of {@code wonder}. */
	public static synchronized void removeHistory(ResourceLocation wonder) {
		EXTRA.remove(wonder);
		forget();
	}

	/** Forgets what was put together from the drawings (the Wonders or the history were loaded again). */
	public static void forget() {
		OLDER_WONDERS.clear();
		WHOLE.clear();
		TRIED.clear();
	}

	/** The stages of the drawing {@code key} of {@code wonder} as shipped (or added), parts named as its file names them. */
	public static Optional<List<Wonder.Stage>> stages(MinecraftServer server, ResourceLocation wonder, String key) {
		return Optional.ofNullable(history(server, wonder).get(key));
	}

	/** The keys of every older drawing of {@code wonder} shipped (or added), in key order. */
	public static List<String> olderKeys(MinecraftServer server, ResourceLocation wonder) {
		return List.copyOf(history(server, wonder).keySet());
	}

	/** The older drawings of {@code wonder}; with no server, as last read (none shipped before the first read). */
	private static synchronized Map<String, List<Wonder.Stage>> history(@Nullable MinecraftServer server, ResourceLocation wonder) {
		ResourceManager resources = server == null ? loadedFrom : server.getResourceManager();
		if (resources != null && resources != loadedFrom) {
			loadedFrom = resources;
			shipped = load(resources);
			forget();
		}
		Map<String, List<Wonder.Stage>> out = new java.util.TreeMap<>(shipped.getOrDefault(wonder, Map.of()));
		out.putAll(EXTRA.getOrDefault(wonder, Map.of()));
		return out;
	}

	private static Map<ResourceLocation, Map<String, List<Wonder.Stage>>> load(ResourceManager resources) {
		Map<ResourceLocation, Map<String, List<Wonder.Stage>>> out = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : resources.listResources(DIR, id -> id.getPath().startsWith(WONDERS)
			&& id.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath().substring(WONDERS.length(), e.getKey().getPath().length() - ".json".length());
			int slash = path.lastIndexOf('/');
			if (slash <= 0) {
				continue;
			}
			ResourceLocation wonder = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(0, slash));
			try (Reader reader = e.getValue().openAsReader()) {
				JsonElement tree = JsonParser.parseReader(reader);
				if (!tree.isJsonObject()) {
					throw new IllegalArgumentException("not a JSON object");
				}
				if (!io.github.jcondedata.aliveworkplace.hall.Guilds.conditionsMet(tree.getAsJsonObject())) {
					continue; // (as its Wonder file was: a Stadium without Cobblemon isn't loaded, nor its older drawings)
				}
				out.computeIfAbsent(wonder, k -> new java.util.TreeMap<>()).put(path.substring(slash + 1), List.copyOf(Wonders.stages(tree.getAsJsonObject())));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Couldn't read the older Wonder drawing {}: {}", e.getKey(), ex.toString());
			}
		}
		return out;
	}

	/** The id of {@code part} as the older drawing {@code key} has it: {@code <ns>:wonder/older/<key>/<path>}. */
	public static ResourceLocation olderId(ResourceLocation part, String key) {
		return ResourceLocation.fromNamespaceAndPath(part.getNamespace(), OLDER + key + "/" + part.getPath());
	}

	/** An older drawing's part: the key, and the part as its file names it. */
	public record Older(String key, ResourceLocation part) {
	}

	/** What the id {@code id} names if it is an older drawing's part ({@link #olderId}); empty otherwise. */
	public static Optional<Older> parse(ResourceLocation id) {
		if (!id.getPath().startsWith(OLDER)) {
			return Optional.empty();
		}
		String rest = id.getPath().substring(OLDER.length());
		int slash = rest.indexOf('/');
		if (slash <= 0 || slash == rest.length() - 1) {
			return Optional.empty();
		}
		ResourceLocation part = ResourceLocation.tryBuild(id.getNamespace(), rest.substring(slash + 1));
		return part == null ? Optional.empty() : Optional.of(new Older(rest.substring(0, slash), part));
	}

	/** The blueprint of an older drawing's part {@code id} ({@link #olderId}), from the history; empty when it isn't one or isn't shipped. */
	public static Optional<Blueprint> blueprint(MinecraftServer server, ResourceLocation id) {
		return parse(id).flatMap(o -> Refreshes.olderDrawing(server, o.part(), o.key()))
			.map(b -> new Blueprint(id, b.size(), b.blocks(), b.entities()));
	}

	/**
	 * {@code current} as its older drawing {@code key} has it (its stages, their parts named by {@link #olderId}), or
	 * {@code current} itself for today's drawing, no key, or a key whose drawing isn't shipped (logged once).
	 */
	static Wonder drawn(@Nullable MinecraftServer server, Wonder current, String key) {
		if (key.isEmpty() || key.equals(Wonders.drawing(current.id()))) {
			return current;
		}
		List<Wonder.Stage> stages = history(server, current.id()).get(key);
		if (stages == null) {
			if (OLDER_WONDERS.putIfAbsent(current.id() + "@" + key, current) == null) {
				AliveWorkplace.LOG.warn("Wonder {} was begun with the drawing {}, which isn't shipped in {}: it goes on with today's", current.id(), key, DIR);
			}
			return current;
		}
		return OLDER_WONDERS.computeIfAbsent(current.id() + "@" + key, k -> {
			List<Wonder.Stage> renamed = new ArrayList<>();
			for (Wonder.Stage stage : stages) {
				renamed.add(new Wonder.Stage(stage.name(), stage.parts().stream()
					.map(p -> new Wonder.Part(olderId(p.part(), key), p.offset())).toList(), stage.goods()));
			}
			return new Wonder(current.id(), current.name(), current.description(), current.icon(), current.enabled(), current.crew(),
				current.requirements(), List.copyOf(renamed), current.powers(), current.villageColours());
		});
	}

	// ---- a site's drawing --------------------------------------------------------------------------------------------

	/**
	 * The Wonder {@code site} is built, counted and repaired by: as the drawing it was begun with has it (matched by best
	 * fit, and recorded, for a site from before the record); null when its Wonder isn't loaded.
	 */
	@Nullable
	public static Wonder of(ServerLevel level, WonderSite site) {
		Wonder current = Wonders.loaded().get(site.wonder());
		if (current == null) {
			return null;
		}
		return drawn(level.getServer(), current, site.drawing().orElseGet(() -> match(level, site, current)));
	}

	/**
	 * As {@link #of}, by the record alone (a site not recorded yet goes by today's drawing; with no server, the older
	 * drawings as last read): for what a player reads, and where no level is at hand.
	 */
	@Nullable
	public static Wonder recorded(@Nullable MinecraftServer server, WonderSite site) {
		Wonder current = Wonders.loaded().get(site.wonder());
		if (current == null) {
			return null;
		}
		return site.drawing().map(key -> drawn(server, current, key)).orElse(current);
	}

	/** The key of the drawing {@code site} goes by (empty: today's, unrecorded and with no key). */
	public static String key(ServerLevel level, WonderSite site) {
		Wonder current = Wonders.loaded().get(site.wonder());
		return site.drawing().orElseGet(() -> current == null ? "" : match(level, site, current));
	}

	/** Whether {@code site} goes by an older drawing than today's. */
	public static boolean older(ServerLevel level, WonderSite site) {
		Wonder current = Wonders.loaded().get(site.wonder());
		return current != null && of(level, site) != current;
	}

	/** {@code site}'s whole blueprint (every stage's parts merged) as the drawing it goes by has it. */
	public static Optional<Blueprint> whole(ServerLevel level, WonderSite site) {
		Wonder current = Wonders.loaded().get(site.wonder());
		Wonder wonder = of(level, site);
		ResourceLocation id = WonderBlueprints.idOf(site.wonder());
		if (wonder == null || wonder == current) {
			return BlueprintLibrary.get(level, id);
		}
		String key = key(level, site);
		return WHOLE.computeIfAbsent(site.wonder() + "@" + key, k -> WonderBlueprints.merge(level.getServer(), id, wonder));
	}

	/**
	 * The blueprint a part site of the Wonder {@code wonderSite} is built from, {@code structure} being the id the part
	 * site was made with: a site made before a redraw named its part by today's id, and while its Wonder goes by an older
	 * drawing the part is that drawing's ({@link #olderId}, in the same style). Any other id as it is.
	 */
	public static ResourceLocation partFor(ServerLevel level, UUID wonderSite, ResourceLocation structure) {
		ResourceLocation base = BlueprintStyles.base(structure);
		if (parse(base).isPresent()) {
			return structure;
		}
		Optional<WonderSite> site = WonderManager.get(level).site(wonderSite);
		if (site.isEmpty()) {
			return structure;
		}
		Wonder current = Wonders.loaded().get(site.get().wonder());
		Wonder wonder = of(level, site.get());
		if (wonder == null || wonder == current) {
			return structure;
		}
		String key = key(level, site.get());
		boolean ofDrawing = wonder.stages().stream().flatMap(s -> s.parts().stream()).anyMatch(p -> p.part().equals(olderId(base, key)));
		if (!ofDrawing) {
			return structure;
		}
		ResourceLocation older = olderId(base, key);
		return BlueprintStyles.styleOf(structure).map(style -> BlueprintStyles.styled(older, style)).orElse(older);
	}

	/**
	 * The drawing of {@code site} (recorded none) by best fit: of today's drawing and each older one shipped, the one of
	 * which the most of its stages built so far stands where the Wonder is (a tie goes to today's). Recorded on the site;
	 * with no older drawing to choose from, or nothing placed yet, today's is recorded at once. While its ground isn't all
	 * loaded, today's stands in, unrecorded, and it is tried again {@link #RETRY} ticks later.
	 */
	static String match(ServerLevel level, WonderSite site, Wonder current) {
		String today = Wonders.drawing(current.id());
		List<String> keys = new ArrayList<>();
		keys.add(today);
		for (String key : olderKeys(level.getServer(), current.id())) {
			if (!keys.contains(key)) {
				keys.add(key);
			}
		}
		if (keys.size() == 1 || site.placement().isEmpty()) {
			record(level, site, today, false);
			return today;
		}
		Long tried = TRIED.get(site.id());
		if (tried != null && level.getGameTime() - tried < RETRY) {
			return today;
		}
		TRIED.put(site.id(), level.getGameTime());
		int upTo = site.done() ? Integer.MAX_VALUE : Math.max(1, site.stage());
		String best = today;
		float bestShare = -1;
		for (String key : keys) {
			Wonder drawing = drawn(level.getServer(), current, key);
			if (!key.equals(today) && drawing == current) {
				continue; // (its drawing isn't shipped after all)
			}
			List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, site, drawing, upTo);
			if (blocks.isEmpty()) {
				continue;
			}
			if (!WonderUpkeep.loaded(level, blocks)) {
				return today; // tried again once its ground is loaded
			}
			int standing = 0;
			for (WonderUpkeep.Block b : blocks) {
				if (WonderUpkeep.stands(level.getBlockState(b.pos()), b)) {
					standing++;
				}
			}
			float share = standing / (float) blocks.size();
			if (share > bestShare) { // (a tie goes to the drawing tried first: today's)
				best = key;
				bestShare = share;
			}
		}
		record(level, site, best, !best.equals(today));
		AliveWorkplace.LOG.info("Wonder {} at {} matched to its drawing {} ({}% of it standing)", current.id(), site.placement().get().origin(),
			best.isEmpty() ? "of today" : best, Math.round(Math.max(0, bestShare) * 100));
		return best;
	}

	private static void record(ServerLevel level, WonderSite site, String key, boolean older) {
		TRIED.remove(site.id());
		if (key.isEmpty()) {
			return; // (a Wonder whose file names no drawing: nothing to record, and nothing older to choose)
		}
		WonderManager.get(level).setDrawing(site.id(), key);
		if (older) {
			// what was found in today's drawing before the match is looked for again in the older one
			ColossusPlaque.forget();
			LighthousePowers.forget();
			GreatWallPowers.forget();
			io.github.jcondedata.aliveworkplace.wonder.powers.WonderBells.forget();
		}
	}

	private WonderDrawings() {
	}
}
