package io.github.jcondedata.aliveworkplace.city;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;

/**
 * Wall kits (ROADMAP 27.18), one per file in {@code data/aliveworkplace/wall_kits/<name>.json}: the three blueprints a
 * wall is made of — a {@code segment}, a {@code corner_tower} and a {@code gate} — and the ranks the kit is for
 * ({@code min_rank}, {@code max_rank}). Two ship: <b>Palisade</b> up to a Village, <b>Stone</b> from a Town, so a Town
 * replaces its palisade with stone a segment at a time ({@link Walls}). A file with {@code requires} loads only with
 * those mods, as road styles do.
 */
public final class WallKits implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("wall_kits");
	public static final String FOLDER = "wall_kits";

	/** One kit: its name (the file's), its three blueprints and the ranks it is built at. */
	public record Kit(String name, ResourceLocation segment, ResourceLocation cornerTower, ResourceLocation gate,
					  VillageRanks.Rank minRank, VillageRanks.Rank maxRank) {
		/** Whether a village of {@code rank} builds this kit. */
		public boolean fits(VillageRanks.Rank rank) {
			return rank.ordinal() >= minRank.ordinal() && rank.ordinal() <= maxRank.ordinal();
		}

		/** Every blueprint of the kit. */
		public List<ResourceLocation> blueprints() {
			return List.of(segment, cornerTower, gate);
		}

		/** The kit's name for players: "Palisade". */
		public Component title() {
			return Component.translatable("wall_kit.aliveworkplace." + name);
		}
	}

	private static Map<String, Kit> kits = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new WallKits());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, Kit> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
			try (Reader reader = e.getValue().openAsReader()) {
				Kit kit = read(name, JsonParser.parseReader(reader).getAsJsonObject());
				if (kit != null) {
					out.put(name, kit);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping wall kit {}: {}", e.getKey(), ex.toString());
			}
		}
		kits = Collections.unmodifiableMap(out);
		AliveWorkplace.LOG.info("Wall kits: {}", kits.keySet());
	}

	/** Reads one kit file; null if it needs a mod that isn't here. A missing or unreadable field refuses the file. */
	@Nullable
	static Kit read(String name, JsonObject json) {
		if (json.has("requires")) {
			for (JsonElement mod : json.getAsJsonArray("requires")) {
				if (!Platform.get().isModLoaded(mod.getAsString())) {
					return null;
				}
			}
		}
		return new Kit(name, id(json, "segment"), id(json, "corner_tower"), id(json, "gate"),
			rank(json, "min_rank", VillageRanks.Rank.HAMLET), rank(json, "max_rank", VillageRanks.Rank.CITY));
	}

	private static ResourceLocation id(JsonObject json, String field) {
		if (!json.has(field)) {
			throw new IllegalArgumentException("no '" + field + "'");
		}
		ResourceLocation id = ResourceLocation.tryParse(json.get(field).getAsString());
		if (id == null) {
			throw new IllegalArgumentException("'" + field + "' is not an id: " + json.get(field));
		}
		return id;
	}

	private static VillageRanks.Rank rank(JsonObject json, String field, VillageRanks.Rank fallback) {
		if (!json.has(field)) {
			return fallback;
		}
		String name = json.get(field).getAsString();
		for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
			if (rank.name().toLowerCase(Locale.ROOT).equals(name)) {
				return rank;
			}
		}
		throw new IllegalArgumentException("'" + field + "' is not a rank: " + name);
	}

	public static List<Kit> all() {
		return List.copyOf(kits.values());
	}

	public static Optional<Kit> get(String name) {
		return Optional.ofNullable(kits.get(name));
	}

	/** The kit a village of {@code rank} builds: the highest-ranking one that fits (none when no kit does). */
	public static Optional<Kit> forRank(VillageRanks.Rank rank) {
		Kit best = null;
		for (Kit kit : kits.values()) {
			if (kit.fits(rank) && (best == null || kit.minRank().ordinal() > best.minRank().ordinal())) {
				best = kit;
			}
		}
		return Optional.ofNullable(best);
	}

	/** Every blueprint of every kit (a wall's pieces: they count as one building and their gates shut at night). */
	public static List<ResourceLocation> allBlueprints() {
		List<ResourceLocation> out = new ArrayList<>();
		kits.values().forEach(k -> out.addAll(k.blueprints()));
		return List.copyOf(out);
	}

	/** Every kit's gate (shut at night by {@code guard/Gates}). */
	public static List<ResourceLocation> gates() {
		return kits.values().stream().map(Kit::gate).toList();
	}

	/** Only for tests: the kits loaded, until the next reload. */
	public static void set(List<Kit> list) {
		Map<String, Kit> out = new LinkedHashMap<>();
		list.forEach(k -> out.put(k.name(), k));
		kits = Collections.unmodifiableMap(out);
	}

	private WallKits() {
	}
}
