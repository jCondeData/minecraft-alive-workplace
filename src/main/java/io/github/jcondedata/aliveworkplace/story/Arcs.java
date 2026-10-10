package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Story arcs (ROADMAP 31.4): {@code data/<ns>/arcs/<id>.json}, one arc per file, read when data packs load. An arc has a
 * {@code trigger} (conditions and a chance a day, or an event), {@code roles}, {@code chapters} in order and an
 * {@code ending}. A chapter tells its intro (chat and the chronicle), runs its {@code on_start} effects
 * ({@link ArcEffects}), posts its quests ({@code giver: arc}), and is done when its quests are ({@code all}, {@code any}
 * or the ones named) and its {@code needs_flags} are set; then {@code delay_days} pass before the next. A chapter with a
 * {@code time_limit_days} that runs out runs its {@code on_fail} effects and the arc ends.
 *
 * <p>One arc runs in a village at a time; {@code side} arcs run beside it. The state is in the hall's entry of
 * {@code aliveworkplace_stories} ({@link ArcState}). The arc moves on in the hall's round ({@link #round}) and when one of
 * its quests is finished; placements and spawned mobs are checked every {@link #CHECK_EVERY} ticks against the players'
 * positions ({@link ArcEffects#check}). A malformed file is skipped with one log line naming it.
 */
public final class Arcs implements ResourceManagerReloadListener {
	public static final String FOLDER = "arcs";
	public static final String SPAWNS = "arc_spawns";
	/** Config {@code storyArcs}: off, no arc starts and a running one ends quietly at its next round. */
	public static boolean ENABLED = true;
	/** Arcs start by themselves (their trigger's chance, in the hall's round). Off in GameTests: they start arcs themselves. */
	public static boolean AUTO = true;
	/** Config {@code arcCooldownDays}: days between two arcs in one village (also before a village's first). */
	public static int COOLDOWN_DAYS = 8;
	/** Config {@code arcsAtOnce}: arcs running on the whole server (side arcs not counted). */
	public static int AT_ONCE = 3;
	/** Config {@code disabledArcs}: arc ids that never start ({@code bandit_king} or {@code pack:id}). */
	public static Set<String> DISABLED = Set.of();
	/** How close a player must come before a waiting placement is built or a mob spawned (the chunk is loaded then). */
	public static int NEAR = 96;
	public static final int CHECK_EVERY = 40;
	/** Every arc mob and arc villager carries this tag, and {@code aliveworkplace_story_<arc path>}. */
	public static final String TAG = "aliveworkplace_story";
	/** Arc mobs (not villagers): one that loads while no arc knows it is gone. */
	public static final String MOB_TAG = "aliveworkplace_story_mob";
	/** How many arcs have started since the server did (tests). */
	public static final AtomicInteger STARTED = new AtomicInteger();

	/** One arc file, as read. */
	public record Arc(ResourceLocation id, Component name, boolean threat, boolean side, List<Condition> conditions, float chance, String event,
					  Map<String, RoleSpec> roles, List<Chapter> chapters, List<JsonObject> ending) {
	}

	/** A role played by a villager: {@code new} (one comes to the village), {@code any} or {@code job:<profession>}. */
	public record RoleSpec(String villager, Component name) {
	}

	/** A quest a chapter posts: a quest file's id, or one written inline. */
	public record QuestRef(ResourceLocation id, @Nullable QuestFiles.QuestFile inline) {
	}

	/** One chapter, as read. */
	public record Chapter(Component name, List<Component> intro, List<JsonObject> onStart, List<QuestRef> quests, String complete,
						  List<String> completeIds, List<String> needsFlags, int delayDays, int timeLimitDays, List<JsonObject> onFail,
						  List<String> chatter) {
	}

	private static Map<ResourceLocation, Arc> arcs = Map.of();
	private static Map<ResourceLocation, List<JsonObject>> spawnGroups = Map.of();

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id("arcs"), new Arcs());
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % CHECK_EVERY == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					ArcEffects.check(level);
				}
			}
		});
		Platform.get().onEntityLoad((entity, level) -> ArcEffects.onLoad(level, entity));
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> ArcEffects.onTalk(player, level, hand, entity));
	}

	public static Collection<Arc> all() {
		return arcs.values();
	}

	public static Optional<Arc> get(ResourceLocation id) {
		return Optional.ofNullable(arcs.get(id));
	}

	/** An arc by its id, or by its path alone in our namespace or any ({@code bandit_king}). */
	public static Optional<Arc> find(String text) {
		ResourceLocation id = ResourceLocation.tryParse(text.contains(":") ? text : AliveWorkplace.MOD_ID + ":" + text);
		if (id != null && arcs.containsKey(id)) {
			return Optional.of(arcs.get(id));
		}
		return arcs.values().stream().filter(a -> a.id().getPath().equals(text)).findFirst();
	}

	/** Adds an arc read elsewhere (the showcase's demo arc, tests), as if a data pack had it. */
	public static void add(Arc arc) {
		Map<ResourceLocation, Arc> copy = new LinkedHashMap<>(arcs);
		copy.put(arc.id(), arc);
		arcs = java.util.Collections.unmodifiableMap(copy);
	}

	@Nullable
	static List<JsonObject> group(ResourceLocation id) {
		return spawnGroups.get(id);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")), manager.listResources(SPAWNS, p -> p.getPath().endsWith(".json")));
	}

	/** Reads these arc and spawn-group files in place of the ones read before. */
	public static void load(Map<ResourceLocation, Resource> arcFiles, Map<ResourceLocation, Resource> spawnFiles) {
		Map<ResourceLocation, List<JsonObject>> groups = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : new java.util.TreeMap<>(spawnFiles).entrySet()) {
			ResourceLocation id = strip(e.getKey(), SPAWNS);
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				List<JsonObject> mobs = new ArrayList<>();
				for (JsonElement m : GsonHelper.getAsJsonArray(json, "mobs")) {
					ArcEffects.validateSpawn(m.getAsJsonObject());
					mobs.add(m.getAsJsonObject());
				}
				groups.put(id, List.copyOf(mobs));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping arc spawn file {}: {}", e.getKey(), ex.getMessage());
			}
		}
		spawnGroups = java.util.Collections.unmodifiableMap(groups);
		Map<ResourceLocation, Arc> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : new java.util.TreeMap<>(arcFiles).entrySet()) {
			ResourceLocation id = strip(e.getKey(), FOLDER);
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (!GsonHelper.getAsBoolean(json, "enabled", true) || !Guilds.conditionsMet(json)) {
					continue;
				}
				out.put(id, read(id, json));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping story arc {}: {}", e.getKey(), ex.getMessage());
			}
		}
		arcs = java.util.Collections.unmodifiableMap(out);
		AliveWorkplace.LOG.info("Story arcs: {}", arcs.size());
	}

	private static ResourceLocation strip(ResourceLocation file, String folder) {
		return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), file.getPath().substring(folder.length() + 1, file.getPath().length() - ".json".length()));
	}

	/** Reads one arc; throws {@link IllegalArgumentException} naming the bad field. */
	public static Arc read(ResourceLocation id, JsonObject json) {
		Component name = Rewards.text(json.get("name"));
		JsonObject trigger = json.has("trigger") ? GsonHelper.getAsJsonObject(json, "trigger") : new JsonObject();
		float chance = GsonHelper.getAsFloat(trigger, "chance", 0f);
		if (chance < 0 || chance > 1) {
			throw new IllegalArgumentException("'trigger.chance' outside 0 to 1");
		}
		List<Condition> conditions = Conditions.parseAll(trigger.has("conditions") ? trigger.getAsJsonArray("conditions") : new JsonArray());
		Map<String, RoleSpec> roles = new LinkedHashMap<>();
		if (json.has("roles")) {
			for (Map.Entry<String, JsonElement> r : GsonHelper.getAsJsonObject(json, "roles").entrySet()) {
				JsonObject o = r.getValue().getAsJsonObject();
				String villager = GsonHelper.getAsString(o, "villager", "");
				if (!villager.equals("new") && !villager.equals("any") && !villager.startsWith("job:")) {
					throw new IllegalArgumentException("role '" + r.getKey() + "': 'villager' must be new, any or job:<profession>");
				}
				roles.put(r.getKey(), new RoleSpec(villager, o.has("name") ? Rewards.text(o.get("name")) : Component.literal(r.getKey())));
			}
		}
		List<Chapter> chapters = new ArrayList<>();
		JsonArray list = GsonHelper.getAsJsonArray(json, "chapters");
		for (int i = 0; i < list.size(); i++) {
			try {
				chapters.add(chapter(id, i, list.get(i).getAsJsonObject()));
			} catch (RuntimeException ex) {
				throw new IllegalArgumentException("chapter " + (i + 1) + ": " + ex.getMessage(), ex);
			}
		}
		if (chapters.isEmpty()) {
			throw new IllegalArgumentException("no 'chapters'");
		}
		return new Arc(id, name, GsonHelper.getAsBoolean(json, "threat", false), GsonHelper.getAsBoolean(json, "side", false), conditions, chance,
			GsonHelper.getAsString(trigger, "event", ""), roles, List.copyOf(chapters), effects(json, "ending"));
	}

	private static Chapter chapter(ResourceLocation arc, int index, JsonObject json) {
		Component name = Rewards.text(json.get("name"));
		List<QuestRef> quests = new ArrayList<>();
		JsonArray qs = json.has("quests") ? GsonHelper.getAsJsonArray(json, "quests") : new JsonArray();
		for (int i = 0; i < qs.size(); i++) {
			JsonElement q = qs.get(i);
			if (q.isJsonPrimitive()) {
				ResourceLocation qid = ResourceLocation.tryParse(q.getAsString());
				if (qid == null) {
					throw new IllegalArgumentException("bad quest id '" + q.getAsString() + "'");
				}
				quests.add(new QuestRef(qid, null));
			} else {
				JsonObject o = q.getAsJsonObject().deepCopy();
				if (!o.has("giver")) {
					o.addProperty("giver", "arc");
				}
				ResourceLocation qid = o.has("id") ? ResourceLocation.parse(GsonHelper.getAsString(o, "id"))
					: ResourceLocation.fromNamespaceAndPath(arc.getNamespace(), "arc/" + arc.getPath() + "/" + (index + 1) + "/" + (i + 1));
				quests.add(new QuestRef(qid, QuestFiles.read(qid, o)));
			}
		}
		String complete = "all";
		List<String> ids = new ArrayList<>();
		if (json.has("complete")) {
			JsonElement c = json.get("complete");
			if (c.isJsonArray()) {
				complete = "list";
				c.getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
			} else {
				complete = c.getAsString();
				if (!complete.equals("all") && !complete.equals("any")) {
					throw new IllegalArgumentException("'complete' must be all, any or a list of quest ids");
				}
			}
		}
		int delay = GsonHelper.getAsInt(json, "delay_days", 0);
		int limit = GsonHelper.getAsInt(json, "time_limit_days", 0);
		if (delay < 0 || limit < 0) {
			throw new IllegalArgumentException("'delay_days' or 'time_limit_days' below 0");
		}
		List<Component> intro = new ArrayList<>();
		if (json.has("intro")) {
			// a lang key each (ours), or a text component (a pack's may write {"text": ...})
			GsonHelper.getAsJsonArray(json, "intro").forEach(e -> intro.add(e.isJsonPrimitive() ? Component.translatable(e.getAsString()) : Rewards.text(e)));
		}
		return new Chapter(name, List.copyOf(intro), effects(json, "on_start"), List.copyOf(quests), complete, List.copyOf(ids),
			strings(json, "needs_flags"), delay, limit, effects(json, "on_fail"), strings(json, "chatter"));
	}

	private static List<String> strings(JsonObject json, String field) {
		List<String> out = new ArrayList<>();
		if (json.has(field)) {
			GsonHelper.getAsJsonArray(json, field).forEach(e -> out.add(e.getAsString()));
		}
		return List.copyOf(out);
	}

	private static List<JsonObject> effects(JsonObject json, String field) {
		List<JsonObject> out = new ArrayList<>();
		if (json.has(field)) {
			for (JsonElement e : GsonHelper.getAsJsonArray(json, field)) {
				ArcEffects.validate(e.getAsJsonObject());
				out.add(e.getAsJsonObject());
			}
		}
		return List.copyOf(out);
	}

	// ---- the engine ------------------------------------------------------------------------------------------------

	/** Whether {@code id} is listed in {@code disabledArcs} (by its full id, or its path in our namespace). */
	public static boolean disabled(ResourceLocation id) {
		return DISABLED.contains(id.toString()) || id.getNamespace().equals(AliveWorkplace.MOD_ID) && DISABLED.contains(id.getPath());
	}

	/** The main arc running in the village round {@code hall}, if any. */
	@Nullable
	public static ArcState running(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		return e == null ? null : e.arc;
	}

	/** The side arcs running in the village round {@code hall}. */
	public static List<ArcState> side(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		return e == null ? List.of() : List.copyOf(e.sideArcs);
	}

	/** The arcs running on the whole server (side arcs not counted), counted from the halls' entries. */
	public static int runningCount(MinecraftServer server) {
		int n = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Stories.Entry e : Stories.Data.get(level).halls.values()) {
				if (e.arc != null) {
					n++;
				}
			}
		}
		return n;
	}

	/** The day the last arc in the village ended (or the engine first saw it). */
	public static long lastArcDay(ServerLevel level, BlockPos hall) {
		return Stories.Data.get(level).entry(hall).lastArcDay;
	}

	/** The hall's round: running arcs move on (or end, switched off or with their file gone); with none, one may begin. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		Stories.Data data = Stories.Data.get(level);
		Stories.Entry e = data.entry(hall);
		if (e.lastArcDay == Stories.Entry.UNSEEN) {
			e.lastArcDay = Chronicle.day(level); // a village's first arc waits arcCooldownDays from now
			data.setDirty();
		}
		for (ArcState s : running(e)) {
			advance(level, hall, e, s);
		}
		if (ENABLED && AUTO) {
			int rounds = (int) Math.max(1, VillageNeeds.DAY / VillageNeeds.CHECK_EVERY);
			tryStart(level, hall, level.random, rounds);
		}
	}

	static List<ArcState> running(Stories.Entry e) {
		List<ArcState> out = new ArrayList<>();
		if (e.arc != null) {
			out.add(e.arc);
		}
		out.addAll(e.sideArcs);
		return out;
	}

	/**
	 * The trigger, rolled once for the village round {@code hall} ({@code rounds} rolls a day): an arc whose conditions
	 * hold starts with its chance a day, if the village has none running, its cooldown is over and fewer than
	 * {@code arcsAtOnce} run on the server; a side arc only needs none of its own kind running here. Returns the arc
	 * started, or null.
	 */
	@Nullable
	public static ArcState tryStart(ServerLevel level, BlockPos hall, RandomSource random, int rounds) {
		if (!ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return null;
		}
		Stories.Entry e = Stories.Data.get(level).entry(hall);
		long today = Chronicle.day(level);
		boolean mainFree = e.arc == null && (e.lastArcDay == Stories.Entry.UNSEEN || today - e.lastArcDay >= COOLDOWN_DAYS)
			&& runningCount(level.getServer()) < AT_ONCE;
		List<Arc> order = new ArrayList<>(arcs.values());
		java.util.Collections.shuffle(order, new java.util.Random(random.nextLong()));
		for (Arc arc : order) {
			if (!arc.event().isEmpty() || arc.chance() <= 0 || disabled(arc.id())) {
				continue;
			}
			if (arc.side() ? e.sideArcs.stream().anyMatch(s -> s.id.equals(arc.id().toString())) : !mainFree) {
				continue;
			}
			// A threat arc would also wait for a village set At peace or at threat level 0 (ROADMAP 32.21, not landed yet).
			if (!arc.conditions().stream().allMatch(c -> c.met(level, hall))) {
				continue;
			}
			if (random.nextFloat() < arc.chance() / Math.max(1, rounds)) {
				ArcState s = start(level, hall, arc);
				if (s != null) {
					return s;
				}
			}
		}
		return null;
	}

	/**
	 * An event arc (such as the Lost Caravan's {@code caravan_lost}) starts in the village round {@code hall}: it skips the
	 * cooldown, but not the one-arc-per-village rule nor {@code arcsAtOnce}.
	 */
	@Nullable
	public static ArcState onEvent(ServerLevel level, BlockPos hall, String event) {
		if (!ENABLED || running(level, hall) != null || runningCount(level.getServer()) >= AT_ONCE) {
			return null;
		}
		for (Arc arc : arcs.values()) {
			if (arc.event().equals(event) && !disabled(arc.id()) && arc.conditions().stream().allMatch(c -> c.met(level, hall))) {
				return start(level, hall, arc);
			}
		}
		return null;
	}

	/**
	 * Starts {@code arc} in the village round {@code hall} now, whatever its trigger (the operators' command, events,
	 * tests): its roles are cast and its first chapter begins. Null when the village already runs one of its kind, or a
	 * role can't be cast.
	 */
	@Nullable
	public static ArcState start(ServerLevel level, BlockPos hall, Arc arc) {
		Stories.Data data = Stories.Data.get(level);
		Stories.Entry e = data.entry(hall);
		if (arc.side() ? e.sideArcs.stream().anyMatch(s -> s.id.equals(arc.id().toString())) : e.arc != null) {
			return null;
		}
		ArcState s = new ArcState(arc.id().toString());
		long today = Chronicle.day(level);
		s.beganDay = today;
		s.nextChapterDay = today;
		for (Map.Entry<String, RoleSpec> r : arc.roles().entrySet()) {
			Villager v = ArcEffects.cast(level, hall, arc, r.getKey(), r.getValue());
			if (v == null) {
				AliveWorkplace.LOG.info("Story arc {} can't start in the village at {}: nobody can play {}", arc.id(), hall, r.getKey());
				return null;
			}
			s.roles.put(r.getKey(), new ArcState.Role(Optional.of(v.getUUID()), v.getDisplayName().copy()));
		}
		if (arc.side()) {
			e.sideArcs.add(s);
		} else {
			e.arc = s;
		}
		data.setDirty();
		STARTED.incrementAndGet();
		AliveWorkplace.LOG.info("Story arc {} begins in the village at {}", arc.id(), hall);
		advance(level, hall, e, s);
		return s;
	}

	/** Moves {@code s} on as far as it can go today: a chapter begins, finishes or fails. */
	static void advance(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s) {
		if (!running(e).contains(s)) {
			return;
		}
		Arc arc = arcs.get(ResourceLocation.tryParse(s.id));
		if (arc == null) {
			AliveWorkplace.LOG.info("Story arc {} in the village at {} ends: its file is gone", s.id, hall);
			end(level, hall, e, s, null);
			return;
		}
		if (!ENABLED || disabled(arc.id())) {
			AliveWorkplace.LOG.info("Story arc {} in the village at {} ends: it was switched off", s.id, hall);
			end(level, hall, e, s, null);
			return;
		}
		long today = Chronicle.day(level);
		Stories.Data data = Stories.Data.get(level);
		if (s.flags.values().removeIf(until -> until < today)) {
			data.setDirty();
		}
		for (int guard = 0; guard <= arc.chapters().size() + 1 && running(e).contains(s); guard++) {
			if (s.chapter >= arc.chapters().size()) {
				finish(level, hall, e, s, arc);
				return;
			}
			Chapter c = arc.chapters().get(s.chapter);
			if (!s.started) {
				if (today < s.nextChapterDay) {
					return;
				}
				begin(level, hall, e, s, arc, c);
				if (endIfAsked(level, hall, e, s, arc)) {
					return;
				}
				continue;
			}
			if (complete(s, c, today)) {
				Reputation.onChapterDone(level, hall, s); // 31.11: standing for everyone who helped in it
				closeChapter(level, hall, e, s, today, c.delayDays());
				continue;
			}
			if (c.timeLimitDays() > 0 && today >= s.chapterDay + c.timeLimitDays()) {
				fail(level, hall, e, s, arc, c);
			}
			return;
		}
	}

	/** Whether the chapter's quests and flags are done. */
	static boolean complete(ArcState s, Chapter c, long today) {
		boolean quests = switch (c.complete()) {
			case "any" -> s.quests.isEmpty() || s.quests.stream().anyMatch(q -> s.done.contains(q.id()));
			case "list" -> c.completeIds().stream().allMatch(id -> s.quests.stream().anyMatch(q -> q.file().equals(id) && s.done.contains(q.id())));
			default -> s.quests.stream().allMatch(q -> s.done.contains(q.id()));
		};
		return quests && c.needsFlags().stream().allMatch(f -> s.flag(f, today));
	}

	/** The chapter begins: its intro is told and written down, its effects run and its quests go up. */
	static void begin(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arc arc, Chapter c) {
		long today = Chronicle.day(level);
		s.started = true;
		s.chapterDay = today;
		s.quests.clear();
		s.done.clear();
		s.chapterHelpers.clear();
		s.chatter.clear();
		s.chatter.addAll(c.chatter());
		Stories.Data.get(level).setDirty();
		Component header = Component.translatable("message.aliveworkplace.arc.chapter", arc.name(), s.chapter + 1, c.name());
		List<Component> lines = c.intro();
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, header.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			for (Component line : lines) {
				Chat.chat(player, line.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
			}
		}
		level.playSound(null, hall, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 0.8f);
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.arc.chapter", arc.name(), s.chapter + 1, c.name()));
		for (Component line : lines) {
			Chronicle.atHall(level, hall, Chronicle.Kind.STORY, line);
		}
		ArcEffects.run(level, hall, e, s, arc, c.onStart());
		if (s.endWith != null) {
			return;
		}
		post(level, hall, e, s, arc, c);
	}

	/** Puts the chapter's quests up ({@code giver: arc}, no deadline of their own: the chapter's time limit is theirs). */
	static void post(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arc arc, Chapter c) {
		for (Arcs.QuestRef ref : c.quests()) {
			Quest q = quest(level, hall, s, ref);
			if (q != null) {
				s.quests.add(new ArcState.ChapterQuest(q.id, ref.id().toString()));
			}
		}
		Stories.Data.get(level).setDirty();
	}

	/** One of the arc's quests resolved and posted, or null (with a log line) when it can't be. */
	@Nullable
	static Quest quest(ServerLevel level, BlockPos hall, ArcState s, QuestRef ref) {
		QuestFiles.QuestFile file = ref.inline() != null ? ref.inline() : QuestFiles.get(ref.id()).orElse(null);
		if (file == null) {
			AliveWorkplace.LOG.warn("Story arc {}: no quest file {}", s.id, ref.id());
			return null;
		}
		float factor = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? VillageRanks.questRewardFactor(entity.rank()) : 1f;
		Quest q = Stories.resolve(level, hall, VillageHalls.census(level, hall), file, factor, level.random, "");
		q = q == null ? null : Stories.locate(level, hall, q);
		if (q == null) {
			AliveWorkplace.LOG.info("Story arc {}: quest {} can't be asked for now", s.id, ref.id());
			return null;
		}
		List<Objectives.Objective> objectives = new ArrayList<>();
		for (Objectives.Objective o : q.objectives) {
			objectives.add(cast(s, o));
		}
		Quest out = new Quest(q.id, q.file, "arc", q.name, "", q.posted, -1, objectives, q.progress, q.rewards);
		out.arc = s.id;
		Stories.post(level, hall, out);
		return out;
	}

	/** An objective with the arc's roles filled in (who to talk to, who to defeat). */
	static Objectives.Objective cast(ArcState s, Objectives.Objective o) {
		if (o instanceof Objectives.Talk talk) {
			ArcState.Role role = s.roles.get(talk.role());
			return role == null ? talk : new Objectives.Talk(talk.role(), Optional.of(role.name()), talk.says());
		}
		if (o instanceof Objectives.Kill kill && kill.role() != null) {
			ArcState.Role role = s.roles.get(kill.role());
			return role == null ? kill : new Objectives.Kill(kill.entity(), kill.count(), true, Optional.of(role.name()));
		}
		return o;
	}

	/** The chapter is over: its leftover quests come down and the next waits {@code delay} days. */
	static void closeChapter(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, long today, int delay) {
		takeDown(level, e, s, false);
		s.chapter++;
		s.started = false;
		s.nextChapterDay = today + delay;
		s.chatter.clear();
		Stories.Data.get(level).setDirty();
	}

	/** Takes the arc's open quests down ({@code all}: every one it posted, else only the chapter's). */
	static void takeDown(ServerLevel level, Stories.Entry e, ArcState s, boolean all) {
		Set<UUID> chapter = new java.util.HashSet<>();
		s.quests.forEach(q -> chapter.add(q.id()));
		for (Quest q : List.copyOf(e.quests)) {
			if (s.id.equals(q.arc) && (all || chapter.contains(q.id))) {
				e.quests.remove(q);
				QuestTracker.changed(level.getServer(), q.id);
			}
		}
	}

	/** The last chapter is done: the ending's effects run, everyone near is told, the chronicle remembers it. */
	static void finish(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arc arc) {
		ArcEffects.run(level, hall, e, s, arc, arc.ending());
		s.endWith = null; // (it's ending already)
		tell(level, hall, Component.translatable("message.aliveworkplace.arc.ended", arc.name()).withStyle(ChatFormatting.GOLD),
			Component.translatable("chronicle.aliveworkplace.arc.ended", arc.name()));
		level.playSound(null, hall, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.8f, 1f);
		end(level, hall, e, s, "done");
	}

	/** The chapter's time ran out: its {@code on_fail} effects run and the arc ends badly. */
	static void fail(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arc arc, Chapter c) {
		AliveWorkplace.LOG.info("Story arc {} in the village at {}: chapter {} ran out of time", s.id, hall, s.chapter + 1);
		ArcEffects.run(level, hall, e, s, arc, c.onFail());
		if (!endIfAsked(level, hall, e, s, arc)) {
			failed(level, hall, e, s, arc);
		}
	}

	static void failed(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arc arc) {
		tell(level, hall, Component.translatable("message.aliveworkplace.arc.failed", arc.name()).withStyle(ChatFormatting.RED),
			Component.translatable("chronicle.aliveworkplace.arc.failed", arc.name()));
		end(level, hall, e, s, "failed");
	}

	/** An {@code end_arc} effect asked the arc to end: it does, the way it asked. */
	static boolean endIfAsked(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arc arc) {
		String how = s.endWith;
		if (how == null || !running(e).contains(s)) {
			return how != null;
		}
		s.endWith = null;
		switch (how) {
			case "done" -> finish(level, hall, e, s, arc);
			case "quiet" -> end(level, hall, e, s, null);
			default -> failed(level, hall, e, s, arc);
		}
		return true;
	}

	private static void tell(ServerLevel level, BlockPos hall, Component message, Component chronicle) {
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, message);
		}
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, chronicle);
	}

	/**
	 * The arc ends ({@code how}: {@code done}, {@code failed}, or null: quietly): its quests come down, its mobs leave in a
	 * puff, and the village's cooldown starts. Placed builds stay.
	 */
	static void end(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, @Nullable String how) {
		takeDown(level, e, s, true);
		ArcEffects.dismiss(level, s);
		boolean main = e.arc == s;
		if (main) {
			e.arc = null;
			e.lastArcDay = Chronicle.day(level);
		} else {
			e.sideArcs.remove(s);
		}
		Stories.Data.get(level).setDirty();
		AliveWorkplace.LOG.info("Story arc {} in the village at {} is over ({})", s.id, hall, how == null ? "ended quietly" : how);
	}

	/** One of an arc's quests was finished: the arc notes it (and who helped) and moves on at once. */
	static void questDone(ServerLevel level, BlockPos hall, Quest quest, @Nullable ServerPlayer player) {
		if (quest.arc == null) {
			return;
		}
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e == null) {
			return;
		}
		for (ArcState s : running(e)) {
			if (!s.id.equals(quest.arc)) {
				continue;
			}
			s.done.add(quest.id);
			quest.helpers.forEach((who, n) -> {
				s.helpers.merge(who, n, Integer::sum);
				s.chapterHelpers.merge(who, n, Integer::sum);
			});
			if (player != null) {
				s.last = player.getUUID();
			}
			Stories.Data.get(level).setDirty();
			advance(level, hall, e, s);
			return;
		}
	}

	/** {@code /workplace story next}: the chapter running is done now and the next begins at once. False with no arc. */
	public static boolean next(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		ArcState s = e == null ? null : e.arc;
		if (s == null) {
			return false;
		}
		Arc arc = arcs.get(ResourceLocation.tryParse(s.id));
		if (arc == null) {
			advance(level, hall, e, s); // (ends it quietly)
			return true;
		}
		long today = Chronicle.day(level);
		if (!s.started) {
			s.nextChapterDay = today;
		} else {
			closeChapter(level, hall, e, s, today, 0);
		}
		advance(level, hall, e, s);
		return true;
	}

	/** {@code /workplace story stop}: the village's arc ends quietly. False with none. */
	public static boolean stop(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e == null || e.arc == null) {
			return false;
		}
		end(level, hall, e, e.arc, null);
		return true;
	}

	/** Ends every arc of the village round {@code hall}, main and side, quietly (tests). */
	public static void stopAll(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e != null) {
			for (ArcState s : running(e)) {
				end(level, hall, e, s, null);
			}
		}
	}

	/** The chatter lines (lang keys) of the chapters running in the village round {@code hall}. */
	public static List<String> chatter(ServerLevel level, BlockPos hall) {
		Stories.Entry e = Stories.Data.get(level).halls.get(hall);
		if (e == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (ArcState s : running(e)) {
			out.addAll(s.chatter);
		}
		return out;
	}

	/** Whether the arc mob or villager {@code entity} carries the arc tags. */
	public static boolean isArcEntity(Entity entity) {
		return entity.getTags().contains(TAG);
	}

	/** An arc mob died: it's noted (it won't be put back) and its role's {@code <key>_dead} flag is set. */
	public static void onDeath(ServerLevel level, LivingEntity entity) {
		if (!entity.getTags().contains(MOB_TAG)) {
			return;
		}
		Stories.Data data = Stories.Data.get(level);
		long today = Chronicle.day(level);
		for (Stories.Entry e : data.halls.values()) {
			for (ArcState s : running(e)) {
				for (ArcState.ArcMob m : s.mobs) {
					if (entity.getUUID().equals(m.uuid) && !m.dead) {
						m.dead = true;
						s.flags.put(m.key + "_dead", ArcState.FOREVER);
						ArcEffects.dropBar(m.uuid);
						data.setDirty();
					}
				}
			}
		}
	}

	private Arcs() {
	}
}
