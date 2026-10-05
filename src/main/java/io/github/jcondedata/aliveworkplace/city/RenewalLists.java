package io.github.jcondedata.aliveworkplace.city;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;

/**
 * Renewal lists (ROADMAP 27.21), one per file in {@code data/<ns>/steward_renewal/<name>.json}: the kind of old house
 * ({@code "kind": "home"}, or {@code "job": "<profession id>"} for a house with that job's workstation) and the
 * {@code buildings} to try in order. A home takes the first with at least as many beds as the old house; a job's house
 * the first that fits. Ships one for homes and one per job, the building 27.11, 27.13 and 27.14 give that job. A file
 * with {@code requires} loads only with those mods.
 */
public final class RenewalLists implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("steward_renewal");
	public static final String FOLDER = "steward_renewal";
	/** The kind of a list for homes. */
	public static final String HOME = "home";

	/** One list: its name (the file's), the kind ({@link #HOME} or a profession id) and the buildings to try, in order. */
	public record RenewalList(String name, String kind, List<ResourceLocation> buildings) {
		public RenewalList {
			buildings = List.copyOf(buildings);
		}

		public boolean home() {
			return HOME.equals(kind);
		}
	}

	private static Map<String, RenewalList> lists = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new RenewalLists());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, RenewalList> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
			try (Reader reader = e.getValue().openAsReader()) {
				RenewalList list = read(name, JsonParser.parseReader(reader).getAsJsonObject());
				if (list != null) {
					out.put(name, list);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping renewal list {}: {}", e.getKey(), ex.toString());
			}
		}
		lists = Collections.unmodifiableMap(out);
		AliveWorkplace.LOG.info("Renewal lists: {}", lists.size());
	}

	/** Reads one file; null if it needs a mod that isn't here. A missing or unreadable field refuses the file. */
	@Nullable
	static RenewalList read(String name, JsonObject json) {
		if (json.has("requires")) {
			for (JsonElement mod : json.getAsJsonArray("requires")) {
				if (!Platform.get().isModLoaded(mod.getAsString())) {
					return null;
				}
			}
		}
		String kind;
		if (json.has("job")) {
			ResourceLocation job = ResourceLocation.tryParse(json.get("job").getAsString());
			if (job == null) {
				throw new IllegalArgumentException("'job' is not an id: " + json.get("job"));
			}
			kind = job.toString();
		} else if (json.has("kind") && HOME.equals(json.get("kind").getAsString())) {
			kind = HOME;
		} else {
			throw new IllegalArgumentException("needs \"kind\": \"home\" or a \"job\"");
		}
		if (!json.has("buildings") || json.getAsJsonArray("buildings").isEmpty()) {
			throw new IllegalArgumentException("no 'buildings'");
		}
		List<ResourceLocation> buildings = new ArrayList<>();
		for (JsonElement b : json.getAsJsonArray("buildings")) {
			ResourceLocation id = ResourceLocation.tryParse(b.getAsString());
			if (id == null) {
				throw new IllegalArgumentException("not a blueprint id: " + b);
			}
			buildings.add(id);
		}
		return new RenewalList(name, kind, buildings);
	}

	public static List<RenewalList> all() {
		return List.copyOf(lists.values());
	}

	/** The list for homes. */
	public static Optional<RenewalList> homes() {
		return lists.values().stream().filter(RenewalList::home).findFirst();
	}

	/** The list for a house of {@code profession}'s workstation. */
	public static Optional<RenewalList> forJob(ResourceLocation profession) {
		String kind = profession.toString();
		return lists.values().stream().filter(l -> l.kind().equals(kind)).findFirst();
	}

	private RenewalLists() {
	}
}
