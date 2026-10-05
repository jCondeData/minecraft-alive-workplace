package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

/**
 * The quest files (ROADMAP 31.2): {@code data/<ns>/quests/<group>/<id>.json}, one quest each, read when data packs load
 * (and on {@code /reload}). A malformed file is skipped with one log line naming it; {@code "enabled": false} switches
 * one off; {@code fabric:load_conditions} are honoured.
 */
public final class QuestFiles implements ResourceManagerReloadListener {
	public static final String FOLDER = "quests";
	public static final Set<String> GIVERS = Set.of("hall", "villager", "bounty", "arc");

	/** One quest file, as read. */
	public record QuestFile(ResourceLocation id, String giver, Optional<Component> name, int weight, int priority, List<Condition> conditions,
							List<Objectives.Objective> objectives, List<Rewards.Reward> rewards, int days, boolean repeatable) {
	}

	private static Map<ResourceLocation, QuestFile> files = Map.of();

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id("quests"), new QuestFiles());
	}

	public static Collection<QuestFile> all() {
		return files.values();
	}

	public static Optional<QuestFile> get(ResourceLocation id) {
		return Optional.ofNullable(files.get(id));
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		reload(manager);
	}

	/** Reads every quest file again. */
	public static void reload(ResourceManager manager) {
		load(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")));
	}

	/** Reads these files (paths under {@code quests/}), in place of the ones read before. */
	public static void load(Map<ResourceLocation, Resource> found) {
		Map<ResourceLocation, QuestFile> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : new java.util.TreeMap<>(found).entrySet()) {
			ResourceLocation file = e.getKey();
			String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (!GsonHelper.getAsBoolean(json, "enabled", true) || !Guilds.conditionsMet(json)) {
					continue;
				}
				out.put(id, read(id, json));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping quest file {}: {}", file, ex.getMessage());
			}
		}
		files = java.util.Collections.unmodifiableMap(out);
		AliveWorkplace.LOG.info("Quest files: {}", files.size());
	}

	/** Reads one quest file; throws {@link IllegalArgumentException} naming the bad field. */
	public static QuestFile read(ResourceLocation id, JsonObject json) {
		String giver = GsonHelper.getAsString(json, "giver", "");
		if (!GIVERS.contains(giver)) {
			throw new IllegalArgumentException("unknown or missing 'giver' '" + giver + "'");
		}
		Optional<Component> name = json.has("name") ? Optional.of(Rewards.text(json.get("name"))) : Optional.empty();
		int weight = GsonHelper.getAsInt(json, "weight", 1);
		if (weight < 0) {
			throw new IllegalArgumentException("'weight' below 0");
		}
		List<Objectives.Objective> objectives = new ArrayList<>();
		for (JsonElement o : GsonHelper.getAsJsonArray(json, "objectives")) {
			objectives.add(Objectives.parse(o.getAsJsonObject()));
		}
		if (objectives.isEmpty()) {
			throw new IllegalArgumentException("no 'objectives'");
		}
		List<Rewards.Reward> rewards = new ArrayList<>();
		for (JsonElement r : json.has("rewards") ? GsonHelper.getAsJsonArray(json, "rewards") : new JsonArray()) {
			rewards.add(Rewards.parse(r.getAsJsonObject()));
		}
		int days = GsonHelper.getAsInt(json, "days", 3);
		if (days < 1) {
			throw new IllegalArgumentException("'days' below 1");
		}
		return new QuestFile(id, giver, name, weight, GsonHelper.getAsInt(json, "priority", 0),
			Conditions.parseAll(json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray()),
			List.copyOf(objectives), List.copyOf(rewards), days, GsonHelper.getAsBoolean(json, "repeatable", true));
	}

	private QuestFiles() {
	}
}
