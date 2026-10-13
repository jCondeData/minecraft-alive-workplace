package io.github.jcondedata.aliveworkplace.wonder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.rules.JobLevel;
import io.github.jcondedata.aliveworkplace.rules.LegendSettled;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;

/**
 * The Wonders (M35, 35.2): one file each in {@code data/<ns>/wonders/}, read when data packs load (through
 * {@link Platform}), so a server owner's own Wonder works like ours. A file with a mistake (an unknown condition or
 * power type, a missing field, a bad count, an unknown item or job) is logged with its path and the reason and skipped;
 * the rest load. A file with {@code "enabled": false}, or whose {@code fabric:load_conditions} don't hold, is skipped
 * quietly. Every Wonder also needs the Master Architect (stand-in: a Master builder), added here, not in the files.
 *
 * <p>The caps: {@link #CAPS} Wonders a village may hold by rank, one of each kind per village, one going up per village
 * at a time, and {@link #SERVER_LIMIT} villages per kind on the whole server (0: no limit). {@link #DISABLED} Wonders
 * can't be started; {@link #ENABLED} false turns the lot off (sites stay saved for when it's on again).
 */
public final class Wonders implements ResourceManagerReloadListener {
	public static final String FOLDER = "wonders";
	/** The Legend every Wonder needs to draw up its plans, and its stand-in's trade. */
	public static final ResourceLocation MASTER_ARCHITECT = AliveWorkplace.id("master_architect");
	public static final ResourceLocation BUILDER = AliveWorkplace.id("builder");
	/** The job a found goods line names when its file names none: the village's explorers (35.7a). */
	public static final ResourceLocation FINDERS = ResourceLocation.withDefaultNamespace("cartographer");
	/** Most days a found line's helper may take (35.7b). */
	public static final int MAX_HELPER_DAYS = 60;
	/** Most builders a Wonder's crew may list in its file. */
	public static final int MAX_CREW = 32;

	/** The {@code wonders} switch (behind M35's gate). */
	public static volatile boolean ENABLED = true;
	/** The {@code wonderLegends} switch: false puts every Wonder's Legends' stand-ins in their place. */
	public static volatile boolean LEGENDS = true;
	/** Wonders a village may hold by rank: Hamlet, Village, Town, City (config {@code wonderCaps}). */
	public static volatile int[] CAPS = {0, 0, 1, 2};
	/** How many villages on the server may each build the same kind of Wonder (config {@code wonderServerLimit}); 0: no limit. */
	public static volatile int SERVER_LIMIT = 0;
	/** Wonder ids that can't be started (config {@code disabledWonders}). */
	public static volatile Set<ResourceLocation> DISABLED = Set.of();

	private static volatile Map<ResourceLocation, Wonder> wonders = Map.of();
	/** The files the last load skipped, and why. */
	private static volatile Map<ResourceLocation, String> problems = Map.of();

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id("wonders"), new Wonders());
	}

	/** Every Wonder players may see (on, not disabled), in file order; none while Wonders are off. */
	public static Collection<Wonder> all() {
		if (!ENABLED) {
			return List.of();
		}
		return wonders.values().stream().filter(w -> !DISABLED.contains(w.id())).toList();
	}

	/** A loaded Wonder by id, disabled ones too (a finished one keeps its powers); empty while Wonders are off. */
	public static Optional<Wonder> get(ResourceLocation id) {
		return ENABLED ? Optional.ofNullable(wonders.get(id)) : Optional.empty();
	}

	/** Every loaded Wonder, whatever the switches say (tests and the command). */
	public static Map<ResourceLocation, Wonder> loaded() {
		return wonders;
	}

	/** The files the last load skipped, with the reason each was logged with. */
	public static Map<ResourceLocation, String> problems() {
		return problems;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		reload(manager);
	}

	/** Reads every Wonder file in {@code manager} and puts them in place. */
	public static void reload(ResourceManager manager) {
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		Map<ResourceLocation, String> unreadable = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				StringBuilder text = new StringBuilder();
				char[] buf = new char[4096];
				for (int n; (n = reader.read(buf)) > 0; ) {
					text.append(buf, 0, n);
				}
				files.put(e.getKey(), text.toString());
			} catch (Exception ex) {
				unreadable.put(e.getKey(), String.valueOf(ex.getMessage()));
				AliveWorkplace.LOG.warn("Skipping Wonder {}: {}", e.getKey(), ex.getMessage());
			}
		}
		Map<ResourceLocation, Wonder> out = load(files);
		if (!unreadable.isEmpty()) {
			Map<ResourceLocation, String> all = new LinkedHashMap<>(unreadable);
			all.putAll(problems);
			problems = java.util.Collections.unmodifiableMap(all);
		}
		wonders = out;
		forgetPowers(); // 35.8: the powers as the new files give them
		WonderBlueprints.forget(); // the whole blueprints are merged again from the new files (35.3)
		AliveWorkplace.LOG.info("Wonders: {}", wonders.keySet());
	}

	/**
	 * Reads every file ({@code ns:wonders/name.json} to its text): the Wonders that load, by id ({@code ns:name}). A file
	 * that fails is logged with its path and the reason, kept in {@link #problems()}, and skipped.
	 */
	public static Map<ResourceLocation, Wonder> load(Map<ResourceLocation, String> files) {
		Map<ResourceLocation, Wonder> out = new LinkedHashMap<>();
		Map<ResourceLocation, String> skipped = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, String> e : files.entrySet()) {
			ResourceLocation file = e.getKey();
			String path = file.getPath();
			if (!path.startsWith(FOLDER + "/") || !path.endsWith(".json")) {
				continue;
			}
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try {
				JsonElement tree = JsonParser.parseString(e.getValue());
				if (!tree.isJsonObject()) {
					throw new IllegalArgumentException("not a JSON object");
				}
				Wonder wonder = read(id, tree.getAsJsonObject());
				if (wonder != null) {
					out.put(id, wonder);
				}
			} catch (RuntimeException ex) {
				String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
				skipped.put(file, reason);
				AliveWorkplace.LOG.warn("Skipping Wonder {}: {}", file, reason);
			}
		}
		problems = java.util.Collections.unmodifiableMap(skipped);
		return java.util.Collections.unmodifiableMap(out);
	}

	/** Puts loaded Wonders in place (tests). */
	public static void setForTest(Map<ResourceLocation, Wonder> map) {
		wonders = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(map));
		forgetPowers();
	}

	/**
	 * Reads one Wonder file; null when it's switched off ({@code "enabled": false}) or its {@code fabric:load_conditions}
	 * don't hold. Throws {@link IllegalArgumentException} naming what's wrong.
	 */
	@Nullable
	public static Wonder read(ResourceLocation id, JsonObject json) {
		if (!Guilds.conditionsMet(json)) {
			return null;
		}
		boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
		if (!enabled) {
			return null;
		}
		String name = string(json, "name");
		String description = string(json, "description");
		ResourceLocation icon = id(string(json, "icon"), "icon");
		if (!BuiltInRegistries.ITEM.containsKey(icon)) {
			throw new IllegalArgumentException("unknown icon item '" + icon + "'");
		}
		int crew = json.has("crew") ? json.get("crew").getAsInt() : 12;
		if (crew < 1 || crew > MAX_CREW) {
			throw new IllegalArgumentException("'crew' must be 1 to " + MAX_CREW + ", not " + crew);
		}
		List<Condition> requirements = new ArrayList<>(Conditions.parseAll(json.has("requirements") ? array(json, "requirements") : new JsonArray()));
		requirements.add(masterArchitect());
		JsonArray stageArray = array(json, "stages");
		if (stageArray.isEmpty()) {
			throw new IllegalArgumentException("no stages");
		}
		List<Wonder.Stage> stages = new ArrayList<>();
		int n = 0;
		for (JsonElement s : stageArray) {
			n++;
			try {
				stages.add(stage(s.getAsJsonObject()));
			} catch (RuntimeException ex) {
				throw new IllegalArgumentException("stage " + n + ": " + ex.getMessage(), ex);
			}
		}
		List<Wonder.Power> powers = new ArrayList<>();
		for (JsonElement p : json.has("powers") ? array(json, "powers") : new JsonArray()) {
			JsonObject power = p.getAsJsonObject();
			// 35.8: each type is read by the code registered for it (wonder/powers/); an unknown type fails the file
			io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower read = io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers.parse(power);
			JsonObject numbers = power.deepCopy();
			numbers.remove("type");
			powers.add(new Wonder.Power(read.type(), numbers, read));
		}
		boolean colours = json.has("village_colours") && json.get("village_colours").getAsBoolean();
		return new Wonder(id, name, description, icon, true, crew, List.copyOf(requirements), List.copyOf(stages), List.copyOf(powers), colours);
	}

	/** The line every Wonder has: the Master Architect settled in the village, or else a Master builder. */
	public static Condition masterArchitect() {
		return new LegendSettled(MASTER_ARCHITECT, List.of(new JobLevel(BUILDER, 5, 1)));
	}

	private static Wonder.Stage stage(JsonObject json) {
		String name = string(json, "name");
		List<Wonder.Part> parts = new ArrayList<>();
		JsonArray partArray = array(json, "parts");
		if (partArray.isEmpty()) {
			throw new IllegalArgumentException("no parts");
		}
		for (JsonElement p : partArray) {
			JsonObject part = p.getAsJsonObject();
			ResourceLocation structure = id(string(part, "part"), "part");
			BlockPos offset = BlockPos.ZERO;
			if (part.has("offset")) {
				JsonArray o = part.getAsJsonArray("offset");
				if (o.size() != 3) {
					throw new IllegalArgumentException("'offset' of " + structure + " must be [x, y, z]");
				}
				offset = new BlockPos(o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt());
			}
			parts.add(new Wonder.Part(structure, offset));
		}
		List<Wonder.Good> goods = new ArrayList<>();
		for (JsonElement g : json.has("goods") ? array(json, "goods") : new JsonArray()) {
			goods.add(good(g.getAsJsonObject()));
		}
		return new Wonder.Stage(name, List.copyOf(parts), List.copyOf(goods));
	}

	private static Wonder.Good good(JsonObject json) {
		boolean item = json.has("item");
		boolean tag = json.has("tag");
		if (item == tag) {
			throw new IllegalArgumentException("a goods line needs an 'item' or a 'tag'");
		}
		Optional<ResourceLocation> itemId = Optional.empty();
		Optional<ResourceLocation> tagId = Optional.empty();
		if (item) {
			ResourceLocation i = id(json.get("item").getAsString(), "item");
			if (!BuiltInRegistries.ITEM.containsKey(i)) {
				throw new IllegalArgumentException("unknown item '" + i + "'");
			}
			itemId = Optional.of(i);
		} else {
			String t = json.get("tag").getAsString();
			tagId = Optional.of(id(t.startsWith("#") ? t.substring(1) : t, "tag"));
		}
		if (!json.has("count") || json.get("count").getAsInt() < 1) {
			throw new IllegalArgumentException("a goods line's 'count' must be 1 or more");
		}
		int count = json.get("count").getAsInt();
		// 35.7a: a found line (explored for, not made) names no job unless it wants to: the explorers find it
		boolean found = json.has("found") && json.get("found").getAsBoolean();
		ResourceLocation job = found && !json.has("job") ? FINDERS : id(string(json, "job"), "job");
		if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job)) {
			throw new IllegalArgumentException("unknown job '" + job + "'");
		}
		Optional<ResourceLocation> potion = Optional.empty();
		if (json.has("potion")) {
			ResourceLocation p = id(json.get("potion").getAsString(), "potion");
			if (!BuiltInRegistries.POTION.containsKey(p)) {
				throw new IllegalArgumentException("unknown potion '" + p + "'");
			}
			potion = Optional.of(p);
		}
		Optional<Wonder.Helper> helper = Optional.empty();
		if (json.has("helper")) {
			if (!found) {
				throw new IllegalArgumentException("only a found line ('found': true) may have a 'helper'");
			}
			JsonObject h = json.getAsJsonObject("helper");
			int days = h.has("days") ? h.get("days").getAsInt() : 0;
			if (days < 1 || days > MAX_HELPER_DAYS) {
				throw new IllegalArgumentException("a helper's 'days' must be 1 to " + MAX_HELPER_DAYS);
			}
			if (!h.has("condition") || !h.get("condition").isJsonObject()) {
				throw new IllegalArgumentException("a helper needs a 'condition'");
			}
			helper = Optional.of(new Wonder.Helper(Conditions.parse(h.getAsJsonObject("condition")), days));
		}
		return new Wonder.Good(itemId, tagId, count, job, potion, found, helper);
	}

	private static String string(JsonObject json, String field) {
		if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
			throw new IllegalArgumentException("missing '" + field + "'");
		}
		return json.get(field).getAsString();
	}

	private static JsonArray array(JsonObject json, String field) {
		if (!json.has(field) || !json.get(field).isJsonArray()) {
			throw new IllegalArgumentException("missing '" + field + "' (a list)");
		}
		return json.getAsJsonArray(field);
	}

	private static ResourceLocation id(String text, String field) {
		ResourceLocation id = ResourceLocation.tryParse(text);
		if (id == null) {
			throw new IllegalArgumentException("bad '" + field + "': " + text);
		}
		return id;
	}

	// ---------------------------------------------------------------- requirements and caps

	/** Every requirement of {@code wonder} for the village round {@code hall}, with how far along it is. */
	public static List<Condition.Progress> requirements(ServerLevel level, BlockPos hall, Wonder wonder) {
		return wonder.requirements().stream().map(c -> c.progress(level, hall)).toList();
	}

	/** Whether the village round {@code hall} meets every requirement of {@code wonder}. */
	public static boolean requirementsMet(ServerLevel level, BlockPos hall, Wonder wonder) {
		for (Condition c : wonder.requirements()) {
			if (!c.met(level, hall)) {
				return false;
			}
		}
		return true;
	}

	/** Wonders a village of {@code rank} may hold. */
	public static int cap(VillageRanks.Rank rank) {
		int[] caps = CAPS;
		return rank.ordinal() < caps.length ? caps[rank.ordinal()] : 0;
	}

	/**
	 * Why the village round {@code hall} may not begin {@code id} because of the switches and the caps (not its
	 * requirements: {@link #requirementsMet}); empty when it may.
	 */
	public static Optional<Component> capRefusal(ServerLevel level, BlockPos hall, ResourceLocation id) {
		if (!ENABLED) {
			return Optional.of(Component.translatable("wonder.aliveworkplace.refused.off"));
		}
		Wonder wonder = wonders.get(id);
		if (wonder == null || DISABLED.contains(id)) {
			return Optional.of(Component.translatable("wonder.aliveworkplace.refused.unknown", id.toString()));
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Optional.of(Component.translatable("wonder.aliveworkplace.refused.no_hall"));
		}
		List<WonderSite> here = WonderManager.get(level).in(hall);
		if (here.stream().anyMatch(s -> s.wonder().equals(id))) {
			return Optional.of(Component.translatable("wonder.aliveworkplace.refused.same_kind", wonder.nameText()));
		}
		Optional<WonderSite> rising = here.stream().filter(WonderSite::goingUp).findFirst();
		if (rising.isPresent()) {
			return Optional.of(Component.translatable("wonder.aliveworkplace.refused.going_up", name(rising.get().wonder())));
		}
		VillageRanks.Rank rank = entity.rank();
		int cap = cap(rank);
		if (here.size() >= cap) {
			return Optional.of(Component.translatable("wonder.aliveworkplace.refused.cap", here.size(), cap, rank.title()));
		}
		int limit = SERVER_LIMIT;
		if (limit > 0) {
			Set<String> villages = new HashSet<>();
			for (WonderManager.Located l : WonderManager.everywhere(level.getServer())) {
				if (l.site().wonder().equals(id)) {
					villages.add(l.level().dimension().location() + "|" + l.site().hall().asLong());
				}
			}
			if (villages.size() >= limit) {
				return Optional.of(Component.translatable("wonder.aliveworkplace.refused.server_limit", wonder.nameText(), limit));
			}
		}
		return Optional.empty();
	}

	/** Why the village round {@code hall} may not begin {@code id} (the caps, then the first unmet requirement); empty when it may. */
	public static Optional<Component> startRefusal(ServerLevel level, BlockPos hall, ResourceLocation id) {
		Optional<Component> capped = capRefusal(level, hall, id);
		if (capped.isPresent()) {
			return capped;
		}
		for (Condition c : wonders.get(id).requirements()) {
			Condition.Progress p = c.progress(level, hall);
			if (!p.met()) {
				return Optional.of(Component.translatable("wonder.aliveworkplace.refused.requirement", p.line()));
			}
		}
		return Optional.empty();
	}

	/** "Wonders: 1 of 1 for a Town", for the village round {@code hall}. */
	public static Component capLine(ServerLevel level, BlockPos hall) {
		VillageRanks.Rank rank = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.rank() : VillageRanks.Rank.HAMLET;
		return Component.translatable("wonder.aliveworkplace.cap_line", WonderManager.get(level).in(hall).size(), cap(rank), rank.title());
	}

	/** A Wonder's name by id: its file's, or the id when its file isn't loaded. */
	public static Component name(ResourceLocation id) {
		Wonder w = wonders.get(id);
		return w != null ? w.nameText() : Component.literal(id.toString());
	}

	// ---------------------------------------------------------------- powers (35.8)

	/**
	 * One finished Wonder's power reaching a village: the Wonder's site and file, the power, and whether the village is
	 * the Wonder's own ({@code home}) or one of its realm's ({@link #linked}).
	 */
	public record Granted(WonderSite site, Wonder wonder, WonderPower power, boolean home) {
	}

	/** Each village's own powers by dimension and hall, from every finished, standing Wonder; null: to be worked out again. */
	@Nullable
	private static volatile Map<ResourceKey<Level>, Map<BlockPos, List<Granted>>> powerCache;
	/** The server {@link #powerCache} was worked out for (a new world in the same game starts afresh). */
	@Nullable
	private static volatile MinecraftServer powerServer;
	/** Whether any power in {@link #powerCache} reaches a realm (else a lookup never asks for the linked villages). */
	private static volatile boolean anyRealm;

	/**
	 * The power {@code type} of a finished, standing Wonder of the village round {@code hall} (or, for a realm power, of
	 * a village linked to it), the first such; empty when none, when the Wonder is damaged, or while Wonders are off.
	 * A lookup in a cache worked out again when a Wonder is finished, damaged, repaired or forgotten, and on load.
	 */
	public static Optional<WonderPower> power(ServerLevel level, BlockPos hall, String type) {
		List<Granted> all = powers(level, hall, type);
		return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0).power());
	}

	/** Every grant of the power {@code type} reaching the village round {@code hall}: its own Wonders' first, then its realm's. */
	public static List<Granted> powers(ServerLevel level, BlockPos hall, String type) {
		if (!ENABLED) {
			return List.of();
		}
		Map<ResourceKey<Level>, Map<BlockPos, List<Granted>>> cache = powerCache(level.getServer());
		List<Granted> out = new ArrayList<>();
		for (Granted g : cache.getOrDefault(level.dimension(), Map.of()).getOrDefault(hall, List.of())) {
			if (g.power().type().equals(type)) {
				out.add(g);
			}
		}
		if (anyRealm) {
			for (GlobalPos other : linked(level, hall)) {
				for (Granted g : cache.getOrDefault(other.dimension(), Map.of()).getOrDefault(other.pos(), List.of())) {
					if (g.power().realm() && g.power().type().equals(type)) {
						out.add(new Granted(g.site(), g.wonder(), g.power(), false));
					}
				}
			}
		}
		return out;
	}

	/** Forgets the powers' cache: a Wonder was finished, damaged, repaired, forgotten or loaded, or the files were read again. */
	public static void forgetPowers() {
		powerCache = null;
	}

	private static Map<ResourceKey<Level>, Map<BlockPos, List<Granted>>> powerCache(MinecraftServer server) {
		Map<ResourceKey<Level>, Map<BlockPos, List<Granted>>> cache = powerCache;
		if (cache != null && powerServer == server) {
			return cache;
		}
		Map<ResourceKey<Level>, Map<BlockPos, List<Granted>>> built = new LinkedHashMap<>();
		boolean realm = false;
		for (WonderManager.Located l : WonderManager.everywhere(server)) {
			WonderSite site = l.site();
			Wonder wonder = wonders.get(site.wonder());
			if (!site.empowered() || wonder == null) {
				continue;
			}
			for (Wonder.Power p : wonder.powers()) {
				built.computeIfAbsent(l.level().dimension(), k -> new LinkedHashMap<>()).computeIfAbsent(site.hall(), k -> new ArrayList<>())
					.add(new Granted(site, wonder, p.power(), true));
				realm |= p.power().realm();
			}
		}
		anyRealm = realm;
		powerServer = server;
		powerCache = built;
		return built;
	}

	/**
	 * The villages linked to the one round {@code hall}, which its realm powers reach and whose chronicles record its
	 * Wonder (35.8): the other villages of its realm (M33), or with realms off (or no realm), its caravan partners.
	 */
	public static List<GlobalPos> linked(ServerLevel level, BlockPos hall) {
		List<GlobalPos> out = new ArrayList<>();
		GlobalPos self = GlobalPos.of(level.dimension(), hall);
		io.github.jcondedata.aliveworkplace.realm.RealmData.Realm realm = io.github.jcondedata.aliveworkplace.realm.Realms.ENABLED
			? io.github.jcondedata.aliveworkplace.realm.Realms.realmOf(level, hall) : null;
		if (realm != null) {
			for (GlobalPos member : io.github.jcondedata.aliveworkplace.realm.Realms.members(realm)) {
				if (!member.equals(self) && !out.contains(member)) {
					out.add(member);
				}
			}
			return out;
		}
		for (BlockPos partner : io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level).routesFrom(hall)) {
			GlobalPos at = GlobalPos.of(level.dimension(), partner);
			if (!at.equals(self) && !out.contains(at)) {
				out.add(at);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- /workplace wonders

	/** {@code /workplace wonders}: every Wonder on the server with its village, owner and stage, one line each. */
	public static List<Component> listLines(MinecraftServer server) {
		if (!ENABLED) {
			return List.of(Component.translatable("command.aliveworkplace.wonders.off"));
		}
		List<WonderManager.Located> all = WonderManager.everywhere(server);
		if (all.isEmpty()) {
			return List.of(Component.translatable("command.aliveworkplace.wonders.none", wonders.size()));
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("command.aliveworkplace.wonders.header", all.size()));
		for (WonderManager.Located l : all) {
			WonderSite site = l.site();
			Component village = VillageHalls.name(l.level(), site.hall());
			Component owner = site.ownerName().isEmpty() ? Component.translatable("command.aliveworkplace.wonders.no_owner") : Component.literal(site.ownerName());
			Wonder wonder = wonders.get(site.wonder());
			Component stage;
			if (site.done()) {
				stage = site.kept().damaged() // 35.8: under wonderStanding, its powers off
					? Component.translatable("command.aliveworkplace.wonders.finished_damaged", site.finished().get(), site.kept().standing())
					: Component.translatable("command.aliveworkplace.wonders.finished", site.finished().get());
			} else if (wonder != null) {
				int i = Math.max(1, Math.min(site.stage(), wonder.stages().size()));
				stage = Component.translatable("command.aliveworkplace.wonders.stage", i, wonder.stages().size(), wonder.stages().get(i - 1).nameText());
			} else {
				stage = Component.translatable("command.aliveworkplace.wonders.stage_unknown", site.stage());
			}
			lines.add(Component.translatable("command.aliveworkplace.wonders.line", name(site.wonder()), village, owner, stage));
		}
		return lines;
	}

	/** Today's day, as the Wonders record it ({@link Chronicle#day}). */
	public static long today(ServerLevel level) {
		return Chronicle.day(level);
	}

	/** Only {@link #init} makes one: the reload listener. */
	private Wonders() {
	}
}
