package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * Heart events (ROADMAP 31.7): at 2, 4, 6, 8 and 10 hearts a villager has something to tell a player. The next time
 * the player is within {@link #RANGE} blocks while the villager is off work ({@link Chatter#offWork}), they walk up,
 * face the player and tell it: three to five lines over their head ({@link WorkerStatus}), one every
 * {@link #LINE_EVERY} ticks, each also in the player's chat in grey. If the player walks away, the villager goes to
 * work or anything else interrupts, nothing is kept and they start again next time. Told to the end, the event is
 * noted on the friendship ({@link Friendship.Bond#told}), adds {@link #POINTS} friendship, writes a
 * {@link Chronicle.Kind#FRIEND} line and becomes part of the villager's life story ({@link LifeStory}).
 *
 * <p>Events are data, {@code data/<namespace>/heart_events/<id>.json}:
 *
 * <pre>{@code
 * {
 *   "hearts": 2,
 *   "when": {"born": true, "revived": false},
 *   "lines": ["heart_event.aliveworkplace.born_here.1", "...", "..."],
 *   "chronicle": "chronicle.aliveworkplace.heart_event.born_here",
 *   "story": "life_story.aliveworkplace.born_here"
 * }
 * }</pre>
 *
 * {@code when} holds conditions on the villager's facts, all of which must hold (each is optional):
 * {@code born} (born in the village: they have parents on record), {@code hired} (hired from an inn),
 * {@code revived} (brought back from a grave), {@code jobs} (a list of profession ids, a job family),
 * {@code married}, {@code courting}, {@code widowed} (a late partner and nobody since), {@code parent} (a child of
 * theirs lives in the village), {@code trait}, {@code mood} (a list of mood reasons, any one of which they feel, e.g.
 * {@code "hungry"}) and {@code rank} (the village's rank at least: {@code hamlet}, {@code village}, {@code town},
 * {@code city}); and, for the events of their life now (31.8): {@code trade} (they have a job: not jobless, not a
 * nitwit), {@code hungry} (hungry, or under {@link #LOW_STORE} meals in the village's store), {@code no_bed},
 * {@code raided} (a raid in the last {@link #RAID_DAYS} days, or a bandit camp preying on the village), {@code ill}
 * (they or their family: partner, parents, children), {@code lonely} (no partner, and nobody within a few blocks),
 * {@code happy}, {@code level} (their job level at least, 5 a Master), {@code level_below}, {@code home_tier_below}
 * (their home's tier, 0 without one) and {@code rank_below}. Every lang key gets the same arguments: {@code %1$s} the
 * villager, {@code %2$s} the player, {@code %3$s} the village, {@code %4$s} their mother, {@code %5$s} their father,
 * {@code %6$s} their partner (or late partner), {@code %7$s} their children, {@code %8$s} the day they were hired,
 * {@code %9$s} their wedding day and {@code %10$s} a sentence about the village's nearest free workstation
 * ({@link VillageHalls#freeStations}). A villager tells one event per heart level: of those whose conditions hold, the
 * one with the most conditions (the story that fits them most closely), then the one with the highest
 * {@code "weight"} (optional, 0), then the first by id. A data pack replaces an event of ours by shipping a file with
 * the same id, or adds its own; a broken file is skipped with a warning.
 *
 * <p>Ours (31.7, 31.8): <b>Where I come from</b> at 2 hearts, <b>My work</b> at 4 (one per job family), <b>What keeps
 * me up at night</b> at 6, <b>The people I love</b> at 8 and <b>What I dream of</b> at 10, after which they give the
 * player their keepsake ({@link Keepsakes}), once.
 *
 * <p>Config {@code heartEvents} off: nobody starts telling, a telling under way stops; what was told stays.
 */
public final class HeartEvents implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("heart_events");
	public static final String FOLDER = "heart_events";
	/** Config {@code heartEvents}. */
	public static boolean ENABLED = true;
	/** Off in gametests (a villager near a test's player would start telling); the heart event tests tick it themselves. */
	public static boolean AUTO = System.getProperty("fabric-api.gametest") == null;
	/** How near (blocks) the player has to be for a villager to start, and to stay for them to go on. */
	public static final double RANGE = 8;
	/** How near the villager walks before they start talking. */
	public static final double TALK_RANGE = 3.5;
	/** Ticks between two lines. */
	public static final int LINE_EVERY = 60;
	/** How long (ticks) a villager tries to walk up before telling it from where they stand. */
	public static final int WALK_UP = 100;
	/** How often (ticks) the engine looks. */
	public static final int CHECK_EVERY = 10;
	/** Friendship for an event told to the end. */
	public static final int POINTS = 20;
	public static final int MIN_LINES = 3;
	public static final int MAX_LINES = 5;
	/** {@code hungry}: fewer meals than this in the village's store worries them. */
	public static final int LOW_STORE = 16;
	/** {@code raided}: a raid this many days ago or less. */
	public static final int RAID_DAYS = 5;

	/** The conditions of an event on the villager's facts; null or empty: not asked. */
	public record When(@Nullable Boolean born, @Nullable Boolean hired, @Nullable Boolean revived, Set<ResourceLocation> jobs,
					   @Nullable Boolean married, @Nullable Boolean courting, @Nullable Boolean widowed, @Nullable Boolean parent,
					   @Nullable Traits.Trait trait, Set<String> moods, @Nullable VillageRanks.Rank rank, Now now) {
		public static final When ALWAYS = new When(null, null, null, Set.of(), null, null, null, null, null, Set.of(), null, Now.ANY);

		public boolean holds(ServerLevel level, Villager villager) {
			Couples.Partner partner = Couples.partner(villager);
			if (!is(born, Families.parents(villager) != null) || !is(hired, ModAttachments.HEAD_START.has(villager))
				|| !is(revived, ModAttachments.REVIVED.getOrElse(villager, false))
				|| !is(married, partner != null && partner.married()) || !is(courting, partner != null && !partner.married())
				|| !is(widowed, partner == null && ModAttachments.LATE_PARTNER.has(villager))) {
				return false;
			}
			if (!jobs.isEmpty() && !jobs.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()))) {
				return false;
			}
			if (trait != null && !Traits.has(villager, trait)) {
				return false;
			}
			if (parent != null && parent != !children(level, villager).isEmpty()) {
				return false;
			}
			if (!moods.isEmpty() && moodReasons(villager).stream().noneMatch(moods::contains)) {
				return false;
			}
			if (rank != null) {
				VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
				if (hall == null || hall.rank().compareTo(rank) < 0) {
					return false;
				}
			}
			return now.holds(level, villager);
		}

		/** How many conditions are asked: of two events for the same hearts, the one that asks more is told. */
		public int asked() {
			int n = 0;
			for (Boolean flag : new Boolean[] {born, hired, revived, married, courting, widowed, parent}) {
				n += flag == null ? 0 : 1;
			}
			return n + (jobs.isEmpty() ? 0 : 1) + (trait == null ? 0 : 1) + (moods.isEmpty() ? 0 : 1) + (rank == null ? 0 : 1) + now.asked();
		}

		private static boolean is(@Nullable Boolean want, boolean fact) {
			return want == null || want == fact;
		}
	}

	/**
	 * The conditions on a villager's life now (31.8), each null when not asked: {@code trade} (they have a job),
	 * {@code hungry}, {@code noBed}, {@code raided}, {@code ill}, {@code lonely}, {@code happy}, their job
	 * {@code level} at least and {@code levelBelow}, {@code homeTierBelow} and the village's {@code rankBelow}.
	 */
	public record Now(@Nullable Boolean trade, @Nullable Boolean hungry, @Nullable Boolean noBed, @Nullable Boolean raided,
					  @Nullable Boolean ill, @Nullable Boolean lonely, @Nullable Boolean happy, @Nullable Integer level,
					  @Nullable Integer levelBelow, @Nullable Integer homeTierBelow, @Nullable VillageRanks.Rank rankBelow) {
		public static final Now ANY = new Now(null, null, null, null, null, null, null, null, null, null, null);

		public boolean holds(ServerLevel level, Villager villager) {
			if (this == ANY) {
				return true;
			}
			// The cheap facts first: the store, the family and the neighbours are only looked at when the rest holds.
			if (trade != null && trade != hasTrade(villager)) {
				return false;
			}
			int jobLevel = villager.getVillagerData().getLevel();
			if (this.level != null && jobLevel < this.level || levelBelow != null && jobLevel >= levelBelow) {
				return false;
			}
			if (noBed != null && noBed != (VillageNeeds.bed(level, villager) == null)) {
				return false;
			}
			if (happy != null && happy != HeartEvents.happy(villager)) {
				return false;
			}
			if (rankBelow != null) {
				VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
				if (hall == null || hall.rank().compareTo(rankBelow) >= 0) {
					return false;
				}
			}
			if (homeTierBelow != null && homeTier(level, villager) >= homeTierBelow) {
				return false;
			}
			return (raided == null || raided == HeartEvents.raided(level, villager)) && (lonely == null || lonely == HeartEvents.lonely(level, villager))
				&& (ill == null || ill == HeartEvents.ill(level, villager)) && (hungry == null || hungry == HeartEvents.hungry(level, villager));
		}

		public int asked() {
			int n = 0;
			for (Object asked : new Object[] {trade, hungry, noBed, raided, ill, lonely, happy, level, levelBelow, homeTierBelow, rankBelow}) {
				n += asked == null ? 0 : 1;
			}
			return n;
		}
	}

	/**
	 * One event: its hearts, conditions, the lines' lang keys, the chronicle's and the life story's, and its weight (of
	 * two events for the same hearts that ask as much, the heavier is told).
	 */
	public record Event(ResourceLocation id, int hearts, When when, List<String> lines, String chronicle, String story, int weight) {
	}

	/** A telling under way (not saved: an interrupted one starts again). */
	public static final class Telling {
		public final UUID player;
		public final Event event;
		public final long started;
		/** How many lines have been said. */
		public int said;
		/** When the next line is due (once the first is said). */
		public long next;
		/** The line now over their head (to tell it from a work line). */
		@Nullable
		Component shown;

		Telling(UUID player, Event event, long started) {
			this.player = player;
			this.event = event;
			this.started = started;
		}
	}

	private static volatile Map<ResourceLocation, Event> events = Map.of();
	/** Villager → what they're telling. */
	private static final Map<UUID, Telling> TELLING = new HashMap<>();

	public static void init() {
		Platform.get().onDataReload(ID, new HeartEvents());
		Platform.get().onLevelTick(level -> {
			if (AUTO && level.getGameTime() % CHECK_EVERY == 0) {
				tick(level, level.getGameTime());
			}
		});
	}

	// --- Data -----------------------------------------------------------------------------------------------------

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(files(manager));
	}

	/** The event files {@code manager} holds (the top data pack's of each), by id; an unreadable one is skipped with a warning. */
	public static Map<ResourceLocation, JsonElement> files(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				String path = e.getKey().getPath();
				files.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length())),
					JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping heart event {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return files;
	}

	/** Reads every event file; a broken one is skipped with a warning naming it. Returns the ids skipped as broken. */
	public static List<ResourceLocation> load(Map<ResourceLocation, JsonElement> files) {
		Map<ResourceLocation, Event> out = new LinkedHashMap<>();
		List<ResourceLocation> broken = new ArrayList<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				Event event = read(e.getKey(), e.getValue());
				if (event != null) {
					out.put(event.id(), event);
				}
			} catch (Exception ex) {
				broken.add(e.getKey());
				AliveWorkplace.LOG.warn("Skipping heart event {}: {}", e.getKey(), ex.getMessage());
			}
		}
		events = Map.copyOf(out);
		synchronized (TELLING) {
			TELLING.clear(); // a telling of an event that may be gone starts again
		}
		return broken;
	}

	/** Reads one event file; null if it is switched off ({@code "enabled": false}) or its load conditions fail; throws naming the bad field. */
	@Nullable
	public static Event read(ResourceLocation id, JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "heart event");
		if (!GsonHelper.getAsBoolean(o, "enabled", true) || !Guilds.conditionsMet(o)) {
			return null;
		}
		int hearts = GsonHelper.getAsInt(o, "hearts");
		if (hearts < 1 || hearts > Friendship.HEARTS) {
			throw new JsonSyntaxException("hearts: " + hearts + " isn't 1 to " + Friendship.HEARTS);
		}
		List<String> lines = new ArrayList<>();
		for (JsonElement line : GsonHelper.getAsJsonArray(o, "lines")) {
			String key = GsonHelper.convertToString(line, "lines");
			if (key.isBlank()) {
				throw new JsonSyntaxException("lines: an empty line");
			}
			lines.add(key);
		}
		if (lines.size() < MIN_LINES || lines.size() > MAX_LINES) {
			throw new JsonSyntaxException("lines: " + lines.size() + " lines, an event has " + MIN_LINES + " to " + MAX_LINES);
		}
		String chronicle = GsonHelper.getAsString(o, "chronicle");
		String story = GsonHelper.getAsString(o, "story");
		if (chronicle.isBlank() || story.isBlank()) {
			throw new JsonSyntaxException(chronicle.isBlank() ? "chronicle: empty" : "story: empty");
		}
		return new Event(id, hearts, o.has("when") ? when(GsonHelper.getAsJsonObject(o, "when")) : When.ALWAYS, List.copyOf(lines), chronicle, story,
			GsonHelper.getAsInt(o, "weight", 0));
	}

	private static final Set<String> CONDITIONS = Set.of("born", "hired", "revived", "jobs", "married", "courting", "widowed", "parent", "trait",
		"mood", "rank", "trade", "hungry", "no_bed", "raided", "ill", "lonely", "happy", "level", "level_below", "home_tier_below", "rank_below");

	private static When when(JsonObject w) {
		for (String key : w.keySet()) {
			if (!CONDITIONS.contains(key)) {
				throw new JsonSyntaxException("when: no condition \"" + key + "\"");
			}
		}
		Set<ResourceLocation> jobs = new LinkedHashSet<>();
		if (w.has("jobs")) {
			for (JsonElement job : GsonHelper.getAsJsonArray(w, "jobs")) {
				String text = GsonHelper.convertToString(job, "when.jobs");
				ResourceLocation id = ResourceLocation.tryParse(text);
				if (id == null) {
					throw new JsonSyntaxException("when.jobs: \"" + text + "\" isn't an id");
				}
				jobs.add(id);
			}
			if (jobs.isEmpty()) {
				throw new JsonSyntaxException("when.jobs: no jobs");
			}
		}
		Traits.Trait trait = null;
		if (w.has("trait")) {
			String name = GsonHelper.getAsString(w, "trait");
			try {
				trait = Traits.Trait.valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ex) {
				throw new JsonSyntaxException("when.trait: no trait \"" + name + "\"");
			}
		}
		Set<String> moods = new LinkedHashSet<>();
		if (w.has("mood")) {
			for (JsonElement mood : GsonHelper.getAsJsonArray(w, "mood")) {
				moods.add(GsonHelper.convertToString(mood, "when.mood"));
			}
			if (moods.isEmpty()) {
				throw new JsonSyntaxException("when.mood: no reasons");
			}
		}
		Now now = new Now(flag(w, "trade"), flag(w, "hungry"), flag(w, "no_bed"), flag(w, "raided"), flag(w, "ill"), flag(w, "lonely"), flag(w, "happy"),
			number(w, "level"), number(w, "level_below"), number(w, "home_tier_below"), rank(w, "rank_below"));
		return new When(flag(w, "born"), flag(w, "hired"), flag(w, "revived"), Set.copyOf(jobs), flag(w, "married"), flag(w, "courting"),
			flag(w, "widowed"), flag(w, "parent"), trait, Set.copyOf(moods), rank(w, "rank"), now.asked() == 0 ? Now.ANY : now);
	}

	@Nullable
	private static VillageRanks.Rank rank(JsonObject w, String key) {
		if (!w.has(key)) {
			return null;
		}
		String name = GsonHelper.getAsString(w, key);
		try {
			return VillageRanks.Rank.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new JsonSyntaxException("when." + key + ": no rank \"" + name + "\"");
		}
	}

	@Nullable
	private static Integer number(JsonObject o, String key) {
		return o.has(key) ? GsonHelper.getAsInt(o, key) : null;
	}

	@Nullable
	private static Boolean flag(JsonObject o, String key) {
		if (!o.has(key)) {
			return null;
		}
		JsonElement value = o.get(key);
		// (GsonHelper reads any string as false, and a condition that silently never holds is hard to find.)
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			throw new JsonSyntaxException("when." + key + ": true or false, not " + value);
		}
		return value.getAsBoolean();
	}

	/** Every loaded event, by id. */
	public static Map<ResourceLocation, Event> all() {
		return events;
	}

	// --- Facts ----------------------------------------------------------------------------------------------------

	/** The names of {@code villager}'s children living in their village (those whose parents on record name them), by name. */
	public static List<Component> children(ServerLevel level, Villager villager) {
		List<Component> out = new ArrayList<>();
		for (Villager child : kin(level, villager, true, false)) {
			out.add(child.getDisplayName());
		}
		out.sort(Comparator.comparing(Component::getString));
		return out;
	}

	/** {@code villager}'s children and/or parents living in their village, going by the parents on record (names). */
	private static List<Villager> kin(ServerLevel level, Villager villager, boolean children, boolean parents) {
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall == null || !villager.hasCustomName()) {
			return List.of();
		}
		String name = villager.getDisplayName().getString();
		Families.Parents mine = parents ? Families.parents(villager) : null;
		List<Villager> out = new ArrayList<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall.getBlockPos()), v -> v.isAlive() && v != villager)) {
			Families.Parents theirs = children ? Families.parents(v) : null;
			if (theirs != null && (theirs.mother().getString().equals(name) || theirs.father().getString().equals(name))) {
				out.add(v);
			} else if (mine != null && v.hasCustomName()
				&& (mine.mother().getString().equals(v.getDisplayName().getString()) || mine.father().getString().equals(v.getDisplayName().getString()))) {
				out.add(v);
			}
		}
		return out;
	}

	/** {@code trade}: they have a job (not jobless, not a nitwit). */
	public static boolean hasTrade(Villager villager) {
		VillagerProfession job = villager.getVillagerData().getProfession();
		return job != VillagerProfession.NONE && job != VillagerProfession.NITWIT;
	}

	/** {@code hungry}: they are hungry, or their village's store holds under {@link #LOW_STORE} meals. */
	public static boolean hungry(ServerLevel level, Villager villager) {
		if (VillageNeeds.isHungry(villager, level.getGameTime())) {
			return true;
		}
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		return hall != null && VillageGrowth.meals(level, VillageNeeds.store(level, hall.getBlockPos())) < LOW_STORE;
	}

	/** {@code raided}: their village was raided in the last {@link #RAID_DAYS} days, or a bandit camp preys on it. */
	public static boolean raided(ServerLevel level, Villager villager) {
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall == null) {
			return false;
		}
		long since = Chronicle.day(level) - hall.lastRaidDay();
		return since >= 0 && since <= RAID_DAYS || BanditCamps.near(level, hall.getBlockPos()).isPresent();
	}

	/** {@code ill}: they are ill, or their partner, a parent or a child of theirs in the village is. */
	public static boolean ill(ServerLevel level, Villager villager) {
		if (Sickness.isIll(villager)) {
			return true;
		}
		Couples.Partner partner = Couples.partner(villager);
		if (partner != null && level.getEntity(partner.id()) instanceof Villager other && other.isAlive() && Sickness.isIll(other)) {
			return true;
		}
		return kin(level, villager, true, true).stream().anyMatch(Sickness::isIll);
	}

	/** {@code lonely}: with nobody (no partner), and no other villager within {@link Moods#COMPANY_RANGE} blocks. */
	public static boolean lonely(ServerLevel level, Villager villager) {
		return Couples.partner(villager) == null
			&& level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(Moods.COMPANY_RANGE), v -> v != villager && v.isAlive()).isEmpty();
	}

	/** {@code happy}: their mood reads happy (never with moods off, or outside a village). */
	public static boolean happy(Villager villager) {
		Moods.Mood mood = Moods.of(villager);
		return mood != null && mood.score() >= Moods.HAPPY;
	}

	/** Their home's tier: 0 without a bed, or with one that's in no finished house. */
	public static int homeTier(ServerLevel level, Villager villager) {
		return Homes.of(level, villager).map(Homes.Home::tier).orElse(0);
	}

	/**
	 * What someone without a trade says about the village's free workstations ({@code %10$s}): the nearest to the hall
	 * and the job it gives ("There's a Loom standing free..."), that there's none, or, for a nitwit, that no bench
	 * would have them.
	 */
	public static Component station(ServerLevel level, Villager villager) {
		if (villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
			return Component.translatable("heart_event.aliveworkplace.station.nitwit");
		}
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		List<VillageHalls.FreeStation> free = hall == null ? List.of() : VillageHalls.freeStations(level, hall.getBlockPos());
		if (free.isEmpty()) {
			return Component.translatable("heart_event.aliveworkplace.station.none");
		}
		VillageHalls.FreeStation station = free.get(0);
		return Component.translatable("heart_event.aliveworkplace.station.free", level.getBlockState(station.pos()).getBlock().getName(),
			Component.translatable("entity.minecraft.villager." + station.profession().name()));
	}

	/** The reasons of {@code villager}'s mood now, as the ids events ask for ({@code hungry}, {@code no_bed}, ...). */
	public static List<String> moodReasons(Villager villager) {
		Moods.Mood mood = Moods.of(villager);
		List<String> out = new ArrayList<>();
		if (mood != null) {
			List<Component> reasons = new ArrayList<>(mood.bad());
			reasons.addAll(mood.good());
			for (Component reason : reasons) {
				if (reason.getContents() instanceof TranslatableContents t && t.getKey().startsWith("mood.aliveworkplace.reason.")) {
					out.add(t.getKey().substring("mood.aliveworkplace.reason.".length()));
				}
			}
		}
		return out;
	}

	/**
	 * The arguments every line gets: the villager, the player, the village, their mother, father, partner (or late
	 * partner), children, the day they were hired, their wedding day and what they say of the village's free
	 * workstations ({@link #station}). What isn't known reads "nobody" (or "a day nobody wrote down").
	 */
	public static Object[] args(ServerLevel level, Villager villager, Component player) {
		Component nobody = Component.translatable("heart_event.aliveworkplace.nobody");
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		Component village = hall == null ? Component.translatable("heart_event.aliveworkplace.the_village") : VillageHalls.name(level, hall.getBlockPos());
		Families.Parents parents = Families.parents(villager);
		Couples.Partner partner = Couples.partner(villager);
		Couples.LatePartner late = ModAttachments.LATE_PARTNER.get(villager);
		Long hired = ModAttachments.HIRED_DAY.get(villager);
		return new Object[] {
			villager.getDisplayName(), player, village,
			parents == null ? nobody : parents.mother(), parents == null ? nobody : parents.father(),
			partner != null ? partner.name() : late != null ? late.name() : nobody,
			list(children(level, villager), nobody),
			hired == null ? Component.translatable("heart_event.aliveworkplace.day_unknown") : Component.translatable("heart_event.aliveworkplace.day", hired),
			partner == null || !partner.married() ? Component.translatable("heart_event.aliveworkplace.day_unknown")
				: Component.translatable("heart_event.aliveworkplace.day", partner.since()),
			station(level, villager)
		};
	}

	/** "Ana", "Ana and Ben", "Ana, Ben and Cal" ({@code none} when empty). */
	static Component list(List<Component> names, Component none) {
		if (names.isEmpty()) {
			return none;
		}
		MutableComponent head = Component.empty();
		for (int i = 0; i < names.size() - 1; i++) {
			if (i > 0) {
				head.append(Component.literal(", "));
			}
			head.append(names.get(i));
		}
		Component last = names.get(names.size() - 1);
		return names.size() == 1 ? last : Component.translatable("heart_event.aliveworkplace.and", head, last);
	}

	/** The lines of {@code event} as {@code villager} tells them to {@code player}. */
	public static List<Component> lines(ServerLevel level, Villager villager, ServerPlayer player, Event event) {
		Object[] args = args(level, villager, Component.literal(player.getGameProfile().getName()));
		List<Component> out = new ArrayList<>();
		for (String key : event.lines()) {
			out.add(Component.translatable(key, args));
		}
		return out;
	}

	// --- Who has what to tell ---------------------------------------------------------------------------------------

	/** The heart levels of which {@code told} holds an event (ids of events no longer loaded count for nothing). */
	static Set<Integer> toldLevels(List<String> told) {
		Set<Integer> levels = new LinkedHashSet<>();
		for (String id : told) {
			ResourceLocation key = ResourceLocation.tryParse(id);
			Event event = key == null ? null : events.get(key);
			if (event != null) {
				levels.add(event.hearts());
			}
		}
		return levels;
	}

	/** Which event is told first: the lowest hearts, then the one that asks the most of the villager, then the heaviest, then by id. */
	private static final Comparator<Event> ORDER = Comparator.comparingInt(Event::hearts)
		.thenComparing(Comparator.comparingInt((Event e) -> e.when().asked()).reversed())
		.thenComparing(Comparator.comparingInt(Event::weight).reversed())
		.thenComparing(e -> e.id().toString());

	/**
	 * What {@code villager} has to tell {@code player} now: the event of the lowest heart level the player has reached
	 * and hasn't been told one of: of those whose conditions hold, the one that asks the most, then the heaviest, then
	 * the first by id; null when there's none (or it's switched off).
	 */
	@Nullable
	public static Event pending(ServerLevel level, Villager villager, UUID player) {
		if (!ENABLED || !Friendship.ENABLED || !Friendship.eligible(villager)) {
			return null;
		}
		Friendship.Bond bond = Friendship.of(villager).bond(player);
		int hearts = bond.hearts();
		if (hearts < 1) {
			return null;
		}
		Set<Integer> told = toldLevels(bond.told());
		Event best = null;
		for (Event event : events.values()) {
			if (event.hearts() > hearts || told.contains(event.hearts()) || bond.told().contains(event.id().toString())) {
				continue;
			}
			if (best != null && ORDER.compare(event, best) > 0) {
				continue;
			}
			if (event.when().holds(level, villager)) {
				best = event;
			}
		}
		return best;
	}

	/** The lowest heart level above {@code player}'s hearts at which {@code villager} would have something to tell, or 0. */
	public static int nextHearts(ServerLevel level, Villager villager, UUID player) {
		Friendship.Bond bond = Friendship.of(villager).bond(player);
		Set<Integer> told = toldLevels(bond.told());
		int next = 0;
		for (Event event : events.values()) {
			if (event.hearts() > bond.hearts() && !told.contains(event.hearts()) && (next == 0 || event.hearts() < next) && event.when().holds(level, villager)) {
				next = event.hearts();
			}
		}
		return next;
	}

	/** What {@code villager} is telling now, or null. */
	@Nullable
	public static Telling telling(Villager villager) {
		synchronized (TELLING) {
			return TELLING.get(villager.getUUID());
		}
	}

	/** Forgets every telling under way (tests; nothing told halfway is kept anyway). */
	public static void forget() {
		synchronized (TELLING) {
			TELLING.clear();
		}
	}

	// --- Telling --------------------------------------------------------------------------------------------------

	/** One look of the engine at {@code level}, at game time {@code now}: tellings go on or stop, and new ones start. */
	public static void tick(ServerLevel level, long now) {
		Map<UUID, Telling> current;
		synchronized (TELLING) {
			current = new HashMap<>(TELLING);
		}
		Set<UUID> listening = new LinkedHashSet<>();
		for (Map.Entry<UUID, Telling> e : current.entrySet()) {
			Telling telling = e.getValue();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(telling.player);
			if (player == null) {
				player = level.players().stream().filter(p -> p.getUUID().equals(telling.player)).findFirst().orElse(null);
			}
			if (level.getEntity(e.getKey()) instanceof Villager villager) {
				if (player == null || player.level() != level || !goesOn(level, villager, player, telling, now)) {
					stop(e.getKey());
				} else {
					listening.add(telling.player);
					advance(level, villager, player, telling, now);
				}
			} else if (player == null || player.level() == level) {
				stop(e.getKey()); // the villager died or left; in another dimension it's that level's to look at
			}
		}
		if (!ENABLED || !Friendship.ENABLED) {
			return;
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.isSpectator() || !player.isAlive() || listening.contains(player.getUUID()) || listensTo(player.getUUID())) {
				continue;
			}
			List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(RANGE, 4, RANGE),
				v -> v.isAlive() && !v.isSleeping() && v.distanceToSqr(player) <= RANGE * RANGE && telling(v) == null && Chatter.offWork(v, now));
			near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
			for (Villager villager : near) {
				Event event = pending(level, villager, player.getUUID());
				if (event != null) {
					Telling telling = new Telling(player.getUUID(), event, now);
					synchronized (TELLING) {
						TELLING.put(villager.getUUID(), telling);
					}
					advance(level, villager, player, telling, now);
					break;
				}
			}
		}
	}

	private static boolean listensTo(UUID player) {
		synchronized (TELLING) {
			return TELLING.values().stream().anyMatch(t -> t.player.equals(player));
		}
	}

	private static void stop(UUID villager) {
		synchronized (TELLING) {
			TELLING.remove(villager);
		}
	}

	/** Whether {@code villager} can go on telling {@code player}: both there, near enough, off work, and the event still theirs to tell. */
	private static boolean goesOn(ServerLevel level, Villager villager, ServerPlayer player, Telling telling, long now) {
		if (!ENABLED || !Friendship.ENABLED || !villager.isAlive() || villager.isSleeping() || !player.isAlive() || player.isSpectator()
			|| villager.distanceToSqr(player) > RANGE * RANGE || !Friendship.eligible(villager)) {
			return false;
		}
		// Off work, but for the line of ours over their head.
		WorkerStatus.Entry status = WorkerStatus.get(villager, now);
		if (status != null && status.line() != telling.shown || Chatter.busy(villager)) {
			return false;
		}
		return !Friendship.of(villager).bond(player.getUUID()).told().contains(telling.event.id().toString());
	}

	/** Walks up, faces the player, says the next line when it's due, and finishes after the last. */
	private static void advance(ServerLevel level, Villager villager, ServerPlayer player, Telling telling, long now) {
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
		// They come up to the player and stay by them to the end, rather than strolling off between two lines.
		boolean far = villager.distanceToSqr(player) > TALK_RANGE * TALK_RANGE;
		if (far) {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.6f, 2));
		} else {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (telling.said == 0) {
			if (far && now - telling.started < WALK_UP) {
				return;
			}
		} else if (now < telling.next) {
			return;
		}
		List<Component> lines = lines(level, villager, player, telling.event);
		Component line = lines.get(telling.said).copy().withStyle(ChatFormatting.ITALIC);
		telling.shown = line;
		Component name = villager.getDisplayName();
		WorkerStatus.set(villager, name.copy().withStyle(ChatFormatting.GRAY), -1f, line);
		villager.playSound(SoundEvents.VILLAGER_AMBIENT, 0.6f, villager.getVoicePitch());
		Chat.chat(player, said(name, lines.get(telling.said)));
		telling.said++;
		telling.next = now + LINE_EVERY;
		if (telling.said >= lines.size()) {
			stop(villager.getUUID());
			finish(level, villager, player, telling.event);
		}
	}

	/** A line as it reads in the listener's chat: "Dara: ...", in grey. */
	public static Component said(Component name, Component line) {
		return Component.translatable("message.aliveworkplace.heart_event.said", name, line).withStyle(ChatFormatting.GRAY);
	}

	/**
	 * {@code event} told to the end: noted, {@link #POINTS} friendship, and a line in the chronicle; after the 10-heart
	 * event they give the player their keepsake, if they haven't yet ({@link Keepsakes}).
	 */
	private static void finish(ServerLevel level, Villager villager, ServerPlayer player, Event event) {
		Friendship.told(villager, player, event.id().toString());
		Friendship.add(villager, player, POINTS);
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall != null) {
			Chronicle.atHall(level, hall.getBlockPos(), Chronicle.Kind.FRIEND,
				Component.translatable(event.chronicle(), args(level, villager, Component.literal(player.getGameProfile().getName()))));
		}
		if (event.hearts() >= Friendship.HEARTS) {
			Keepsakes.give(level, villager, player);
		}
	}

	/** The events anyone was told by {@code villager}, lowest hearts first, each with the names of who was told. */
	public static Map<Event, List<String>> toldBy(Villager villager) {
		Map<Event, List<String>> out = new TreeMap<>(Comparator.comparingInt(Event::hearts).thenComparing(Event::id));
		for (Friendship.Bond bond : Friendship.of(villager).players().values()) {
			for (String id : bond.told()) {
				ResourceLocation key = ResourceLocation.tryParse(id);
				Event event = key == null ? null : events.get(key);
				if (event != null) {
					List<String> names = out.computeIfAbsent(event, k -> new ArrayList<>());
					if (!bond.name().isEmpty() && !names.contains(bond.name())) {
						names.add(bond.name());
					}
				}
			}
		}
		out.values().forEach(names -> names.sort(Comparator.naturalOrder()));
		return out;
	}

	/** The villager's village's hall position, or null (for the life story's page). */
	@Nullable
	static BlockPos hallPos(Villager villager) {
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		return hall == null ? null : hall.getBlockPos();
	}

	private HeartEvents() {
	}
}
