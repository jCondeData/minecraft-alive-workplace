package io.github.jcondedata.aliveworkplace.people;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Social classes (ROADMAP 34.2, {@code docs/design/M34.md}): every household in a village with a hall has a class on a
 * ladder read from {@code data/<namespace>/classes/<id>.json} (ours: Peasant, Artisan, Burgher, Noble; a data pack
 * replaces one by path, adds its own, or switches ours off with {@code "enabled": false}). A class is its
 * {@link ClassNeeds needs} (all must hold), its wants (extras, read by 34.7) and what it gives ({@code jobs},
 * {@code effects}; read by 34.7 and 34.8).
 *
 * <p>Each dawn the hall checks its households a few at a time ({@link #SLICE} a hall round): when the next class's needs
 * held {@link #RISE_DAYS} dawns running the household rises one class; when a need of its own class failed
 * {@link #FALL_DAYS} dawns running it falls one, never below the lowest (Peasant). One step a day at most: each
 * household is counted once a day ({@code class_progress.day}). Children take the class of the grown-ups who sleep in
 * their building. Villagers without a class yet ({@code social_class} absent) are left for seeding (34.22).
 * {@code villageClasses} in the config turns it all off; what's saved stays.
 */
public final class SocialClasses implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("classes");
	public static final String FOLDER = "classes";
	/** Households checked a hall round. */
	public static final int SLICE = 8;
	public static boolean ENABLED = true;
	/** Dawns running the next class's needs must hold to rise ({@code classRiseDays}). */
	public static int RISE_DAYS = 2;
	/** Dawns running a need of their own class must fail to fall ({@code classFallDays}). */
	public static int FALL_DAYS = 3;

	/** A class on the ladder. */
	public record SocialClass(ResourceLocation id, int tier, Component name, float tax, Optional<ResourceLocation> outfit,
							  List<ClassNeeds.Need> needs, List<ClassNeeds.Need> wants, List<ResourceLocation> jobs, List<JsonElement> effects) {
	}

	/**
	 * A villager's progress on the ladder ({@code class_progress}): dawns running the next class's needs held, dawns
	 * running a need of their own failed, days running they were fed, and the last dawn counted. All 0 when absent.
	 */
	public record Progress(int met, int missed, int fed, long day) {
		public static final Progress NONE = new Progress(0, 0, 0, 0);
		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("met", 0).forGetter(Progress::met),
			Codec.INT.optionalFieldOf("missed", 0).forGetter(Progress::missed),
			Codec.INT.optionalFieldOf("fed", 0).forGetter(Progress::fed),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Progress::day)
		).apply(i, Progress::new));
	}

	/**
	 * Where a villager stood at their last dawn ({@code class_standing}, 34.6): the day, how many needs of their class they
	 * lacked, and their last rise (+1) or fall (-1) and its day. Read by their mood; all 0 when absent.
	 */
	public record Standing(long day, int missing, long turnDay, int turn, int wants) {
		public static final Codec<Standing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Standing::day),
			Codec.INT.optionalFieldOf("missing", 0).forGetter(Standing::missing),
			Codec.LONG.optionalFieldOf("turn_day", 0L).forGetter(Standing::turnDay),
			Codec.INT.optionalFieldOf("turn", 0).forGetter(Standing::turn),
			Codec.INT.optionalFieldOf("wants", 0).forGetter(Standing::wants)
		).apply(i, Standing::new));

		/** A standing with no wants had (saves from before 34.7, and tests). */
		public Standing(long day, int missing, long turnDay, int turn) {
			this(day, missing, turnDay, turn, 0);
		}
	}

	/** "Rose in the world" and "came down in the world": this much mood, for {@link #TURN_DAYS} days. */
	public static final int TURN_MOOD = 10;
	public static final int TURN_DAYS = 2;
	/** "Has what their class needs" (+5), or -5 for each need lacking, at most {@link #LACKING_MOST}. */
	public static final int NEED_MOOD = 5;
	public static final int LACKING_MOST = 15;
	/** How far a rise is heard: players this close to the household's door get a chat line. */
	public static final int TELL_RANGE = 32;

	/** A household that rose or fell at a dawn. */
	public record Change(List<UUID> who, ResourceLocation from, ResourceLocation to) {
		public boolean rose() {
			SocialClass a = get(from);
			SocialClass b = get(to);
			return a != null && b != null && b.tier() > a.tier();
		}
	}

	/** What a hall round's check did: the rises and falls, how many households and children it counted, whether the day's done. */
	public record Result(List<Change> changes, int counted, boolean finished) {
	}

	/** The ladder, lowest first. */
	private static volatile List<SocialClass> ladder = List.of();
	/** Each hall's dawn check today (not saved: after a load the first round looks again, and skips whoever was counted). */
	private static final Map<VillageHallBlockEntity, Dawn> DAWNS = new WeakHashMap<>();

	public static void init() {
		Platform.get().onDataReload(ID, new SocialClasses());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(files(manager));
	}

	/** The class files {@code manager} holds (the top data pack's of each), by class id; an unreadable one is skipped with a warning. */
	public static Map<ResourceLocation, JsonElement> files(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				files.put(idOf(e.getKey()), JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping class {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return files;
	}

	/** {@code data/ns/classes/artisan.json} is the class {@code ns:artisan}. */
	public static ResourceLocation idOf(ResourceLocation file) {
		String path = file.getPath();
		return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
	}

	/** Reads every class file (one per id: the top data pack's); a broken file is skipped with a warning naming it. */
	public static void load(Map<ResourceLocation, JsonElement> files) {
		Map<Integer, SocialClass> byTier = new TreeMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				SocialClass c = read(e.getKey(), e.getValue());
				if (c == null) {
					continue;
				}
				SocialClass taken = byTier.putIfAbsent(c.tier(), c);
				if (taken != null) {
					AliveWorkplace.LOG.warn("Skipping class {}: tier {} is already {}", c.id(), c.tier(), taken.id());
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping class {}: {}", e.getKey(), ex.getMessage());
			}
		}
		ladder = List.copyOf(byTier.values());
		synchronized (DAWNS) {
			DAWNS.clear();
		}
	}

	/** Reads one class; null if it is switched off ({@code "enabled": false}) or its load conditions fail; throws naming the bad field. */
	@Nullable
	public static SocialClass read(ResourceLocation id, JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "class");
		if (!GsonHelper.getAsBoolean(o, "enabled", true) || !Guilds.conditionsMet(o)) {
			return null;
		}
		int tier = GsonHelper.getAsInt(o, "tier");
		if (tier < 0) {
			throw new JsonSyntaxException("tier: " + tier + " is below 0");
		}
		Component name = o.has("name")
			? ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o.get("name")).getOrThrow(m -> new JsonSyntaxException("name: " + m))
			: Component.translatable("class." + id.getNamespace() + "." + id.getPath());
		float tax = GsonHelper.getAsFloat(o, "tax", 1f);
		if (tax < 0) {
			throw new JsonSyntaxException("tax: " + tax + " is below 0");
		}
		Optional<ResourceLocation> outfit = o.has("outfit") ? Optional.of(ClassNeeds.id(GsonHelper.getAsString(o, "outfit"), "outfit")) : Optional.empty();
		List<ResourceLocation> jobs = new ArrayList<>();
		for (JsonElement e : GsonHelper.getAsJsonArray(o, "jobs", new com.google.gson.JsonArray())) {
			ResourceLocation job = ClassNeeds.id(GsonHelper.convertToString(e, "jobs"), "jobs");
			if (job.getNamespace().equals("minecraft")) {
				AliveWorkplace.LOG.warn("Class {}: vanilla job {} is never class-gated, ignored", id, job);
				continue;
			}
			jobs.add(job);
		}
		List<JsonElement> effects = new ArrayList<>();
		GsonHelper.getAsJsonArray(o, "effects", new com.google.gson.JsonArray()).forEach(effects::add);
		return new SocialClass(id, tier, name, tax, outfit, needs(o, "needs"), needs(o, "wants"), List.copyOf(jobs), List.copyOf(effects));
	}

	private static List<ClassNeeds.Need> needs(JsonObject o, String field) {
		List<ClassNeeds.Need> out = new ArrayList<>();
		for (JsonElement e : GsonHelper.getAsJsonArray(o, field, new com.google.gson.JsonArray())) {
			try {
				out.add(ClassNeeds.read(e));
			} catch (RuntimeException ex) {
				throw new JsonSyntaxException(field + "[" + out.size() + "]: " + ex.getMessage(), ex);
			}
		}
		return List.copyOf(out);
	}

	/** The ladder, lowest first (empty if no class file loaded). */
	public static List<SocialClass> ladder() {
		return ladder;
	}

	@Nullable
	public static SocialClass get(ResourceLocation id) {
		for (SocialClass c : ladder) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}

	/** The lowest class, everyone's floor (Peasant), or null with no classes. */
	@Nullable
	public static SocialClass floor() {
		List<SocialClass> l = ladder;
		return l.isEmpty() ? null : l.get(0);
	}

	/** The class one step above {@code c} (null at the top), or below with {@code step} -1. */
	@Nullable
	public static SocialClass step(SocialClass c, int step) {
		List<SocialClass> l = ladder;
		int at = l.indexOf(c) + step;
		return l.contains(c) && at >= 0 && at < l.size() ? l.get(at) : null;
	}

	/** {@code villager}'s class, or null if they have none yet (not seeded). A class no file has any more counts as the floor. */
	@Nullable
	public static SocialClass of(Villager villager) {
		ResourceLocation id = ModAttachments.SOCIAL_CLASS.get(villager);
		if (id == null) {
			return null;
		}
		SocialClass c = get(id);
		return c != null ? c : floor();
	}

	/** Gives {@code villager} the class {@code c} (seeding, 34.22; and tests), with fresh progress. */
	public static void seed(Villager villager, SocialClass c) {
		ModAttachments.SOCIAL_CLASS.set(villager, c.id());
		ModAttachments.CLASS_PROGRESS.remove(villager);
	}

	public static Progress progress(Villager villager) {
		return ModAttachments.CLASS_PROGRESS.getOrElse(villager, Progress.NONE);
	}

	/** The hall's round: once the day has dawned, the next {@link #SLICE} households not yet counted today. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		round(level, hall, entity, Chronicle.day(level));
	}

	/**
	 * {@link #round} on {@code today} (tests count several dawns); null when there was nothing left to count. The first
	 * round of a day finds the village's people (one entity lookup) and what their needs read about the village; the
	 * day's later rounds go on down that list.
	 */
	@Nullable
	public static Result round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long today) {
		if (!ENABLED || ladder.isEmpty()) {
			return null;
		}
		Dawn dawn;
		synchronized (DAWNS) {
			dawn = DAWNS.get(entity);
			if (dawn == null || dawn.context.today() != today || !dawn.hall.equals(hall)) {
				dawn = new Dawn(level, hall, level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive), today);
				DAWNS.put(entity, dawn);
				dawn.context.prepare();
				// The day's first round only finds who to count and reads the village; counting starts next round, so no
				// round does both.
				return new Result(List.of(), 0, false);
			}
		}
		return dawn.finished ? null : dawn.run(SLICE);
	}

	/**
	 * Counts up to {@code budget} of the households (then children) in {@code village} not yet counted on {@code today}:
	 * the grown-ups first, in a fixed order, then the children. Returns what changed and whether everyone's counted.
	 */
	public static Result check(ServerLevel level, BlockPos hall, List<Villager> village, long today, int budget) {
		return new Dawn(level, hall, village, today).run(budget);
	}

	/** A hall's dawn check: the day's households and children, and how far down them it has got (not saved: see {@link Progress#day}). */
	private static final class Dawn {
		final ServerLevel level;
		final BlockPos hall;
		final ClassNeeds.Village context;
		final List<Households.Household> households;
		final List<Villager> grown = new ArrayList<>();
		final List<Villager> children = new ArrayList<>();
		int next;
		boolean finished;

		Dawn(ServerLevel level, BlockPos hall, List<Villager> village, long today) {
			this.level = level;
			this.hall = hall;
			this.context = new ClassNeeds.Village(level, hall, today);
			for (Villager v : village) {
				if (v.isAlive() && v.isBaby()) {
					children.add(v);
				} else if (Households.grownResident(v)) {
					grown.add(v);
				}
			}
			children.sort(Comparator.comparing(Villager::getUUID));
			households = Households.of(grown);
		}

		Result run(int budget) {
			long today = context.today();
			List<Change> changes = new ArrayList<>();
			int counted = 0;
			for (; next < households.size() + children.size(); next++) {
				if (next < households.size()) {
					Households.Household h = households.get(next);
					if (!here(h.members()) || progress(h.lead()).day() >= today || h.members().stream().noneMatch(ModAttachments.SOCIAL_CLASS::has)) {
						continue; // gone since dawn, counted today, or not seeded yet (34.22 seeds them)
					}
					if (counted >= budget) {
						return new Result(changes, counted, false);
					}
					Change change = dawn(level, h, context);
					if (change != null) {
						changes.add(change);
					}
				} else {
					Villager child = children.get(next - households.size());
					if (!here(List.of(child)) || progress(child).day() >= today) {
						continue;
					}
					if (counted >= budget) {
						return new Result(changes, counted, false);
					}
					follow(level, child, grown, context);
				}
				counted++;
			}
			finished = true;
			return new Result(changes, counted, true);
		}

		/** Still in the world (not dead, unloaded or grown up since the list was made: they're counted tomorrow). */
		private static boolean here(List<Villager> who) {
			for (Villager v : who) {
				if (!v.isAlive() || v.isRemoved()) {
					return false;
				}
			}
			return true;
		}
	}

	/** One household's dawn: their fed days counted, the needs checked, maybe a step up or down. Returns the step, or null. */
	@Nullable
	static Change dawn(ServerLevel level, Households.Household household, ClassNeeds.Village village) {
		long today = village.today();
		long now = level.getGameTime();
		SocialClass current = null;
		for (Villager v : household.members()) {
			SocialClass c = of(v);
			if (c != null && (current == null || c.tier() > current.tier())) {
				current = c; // a couple of two classes lives as the higher, and is held to its needs
			}
		}
		boolean legend = household.members().stream().anyMatch(ClassPerks::isLegend);
		if (legend && !ladder.isEmpty()) {
			current = ladder.get(ladder.size() - 1); // 34.7: a Legend's household lives among the Nobles, held to their needs
		}
		if (current == null) {
			return null;
		}
		// Fed today (ate from the store within the last day) adds a day running; hungry starts again.
		for (Villager v : household.members()) {
			Progress p = progress(v);
			boolean fed = ModAttachments.LAST_MEAL.has(v) && !VillageNeeds.isHungry(v, now);
			ModAttachments.CLASS_PROGRESS.set(v, new Progress(p.met(), p.missed(), fed ? p.fed() + 1 : 0, p.day()));
		}
		// The day's luxuries (34.4) come before the needs are checked: one taken at dawn holds today.
		Luxuries.take(level, household.members(), current, village);
		Progress lead = progress(household.lead());
		SocialClass next = legend ? null : step(current, 1);
		SocialClass below = legend ? null : step(current, -1);
		boolean ownHeld = ClassNeeds.allHold(current.needs(), household.members(), village);
		boolean nextHeld = next != null && ClassNeeds.allHold(next.needs(), household.members(), village);
		int met = nextHeld ? lead.met() + 1 : 0;
		int missed = ownHeld || below == null ? 0 : lead.missed() + 1;
		SocialClass after = current;
		if (next != null && met >= RISE_DAYS) {
			after = next;
		} else if (below != null && missed >= FALL_DAYS) {
			after = below;
		}
		if (after != current) {
			met = 0;
			missed = 0;
		}
		int lacking = 0;
		for (ClassNeeds.Need need : after.needs()) {
			if (!ClassNeeds.holds(need, household.members(), village)) {
				lacking++;
			}
		}
		int wants = 0;
		for (ClassNeeds.Need want : after.wants()) {
			if (ClassNeeds.holds(want, household.members(), village)) {
				wants++;
			}
		}
		int turn = after.tier() > current.tier() ? 1 : after.tier() < current.tier() ? -1 : 0;
		List<UUID> who = new ArrayList<>();
		for (Villager v : household.members()) {
			ModAttachments.SOCIAL_CLASS.set(v, after.id());
			ModAttachments.CLASS_PROGRESS.set(v, new Progress(met, missed, progress(v).fed(), today));
			Standing was = ModAttachments.CLASS_STANDING.get(v);
			ModAttachments.CLASS_STANDING.set(v, turn != 0 ? new Standing(today, lacking, today, turn, wants)
				: new Standing(today, lacking, was == null ? 0 : was.turnDay(), was == null ? 0 : was.turn(), wants));
			who.add(v.getUUID());
		}
		if (after == current) {
			return null;
		}
		turned(level, village.hall, household, after, turn > 0);
		return new Change(List.copyOf(who), current.id(), after.id());
	}

	/**
	 * Tells the village a household rose or came down in the world (34.6): a chronicle line either way; a rise also gets
	 * golden sparkles and a chime at their door (where the lead sleeps) and a chat line to players within {@link #TELL_RANGE}.
	 */
	static void turned(ServerLevel level, BlockPos hall, Households.Household household, SocialClass now, boolean rose) {
		Component who = names(household.members());
		Chronicle.record(level, hall, Chronicle.Kind.CLASS,
			Component.translatable(rose ? "chronicle.aliveworkplace.class.rose" : "chronicle.aliveworkplace.class.fell", who, now.name()));
		if (!rose) {
			return;
		}
		Villager lead = household.lead();
		BlockPos bed = VillageNeeds.bed(level, lead);
		BlockPos door = bed != null ? bed : lead.blockPosition();
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, door.getX() + 0.5, door.getY() + 1.2, door.getZ() + 0.5, 24, 0.6, 0.6, 0.6, 0.05);
		for (Villager v : household.members()) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, v.getX(), v.getEyeY() + 0.3, v.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
		}
		level.playSound(null, door, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.NEUTRAL, 2f, 1.2f);
		Component line = Component.translatable("message.aliveworkplace.class.rose", who, now.name()).withStyle(net.minecraft.ChatFormatting.GOLD);
		for (net.minecraft.server.level.ServerPlayer player : listeners(level, door)) {
			io.github.jcondedata.aliveworkplace.mc.Chat.chat(player, line);
		}
	}

	/** The players who hear of a rise at {@code door}: within {@link #TELL_RANGE} blocks. */
	public static List<net.minecraft.server.level.ServerPlayer> listeners(ServerLevel level, BlockPos door) {
		List<net.minecraft.server.level.ServerPlayer> out = new ArrayList<>();
		for (net.minecraft.server.level.ServerPlayer player : level.players()) {
			if (player.isAlive() && player.distanceToSqr(door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5) <= TELL_RANGE * TELL_RANGE) {
				out.add(player);
			}
		}
		return out;
	}

	/** A household's names: "Odo", "Odo and Pia". */
	static Component names(List<Villager> members) {
		Component first = members.get(0).getDisplayName();
		return members.size() < 2 ? first : Component.translatable("class.aliveworkplace.household.two", first, members.get(1).getDisplayName());
	}

	/**
	 * The class moods of {@code villager} on {@code today} (34.6): "rose in the world" (+10) or "came down in the world"
	 * (-10) for {@link #TURN_DAYS} days, and from their last dawn (today's or yesterday's) "has what their class needs"
	 * (+5) or -5 for each need lacking, at most -15. None with classes off or no class.
	 */
	public static List<io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason> moods(Villager villager, long today) {
		Standing s = ModAttachments.CLASS_STANDING.get(villager);
		if (!ENABLED || s == null || !ModAttachments.SOCIAL_CLASS.has(villager)) {
			return List.of();
		}
		List<io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason> out = new ArrayList<>();
		if (s.turn() != 0 && today >= s.turnDay() && today - s.turnDay() < TURN_DAYS) {
			out.add(new io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason(
				Component.translatable(s.turn() > 0 ? "mood.aliveworkplace.reason.class_rose" : "mood.aliveworkplace.reason.class_fell"),
				s.turn() > 0 ? TURN_MOOD : -TURN_MOOD));
		}
		if (today >= s.day() && today - s.day() <= 1) {
			out.add(s.missing() == 0
				? new io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.class_needs_met"), NEED_MOOD)
				: new io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason(
					Component.translatable("mood.aliveworkplace.reason.class_lacking", s.missing()), -Math.min(LACKING_MOST, NEED_MOOD * s.missing())));
		}
		return out;
	}

	/**
	 * A child takes the class of the grown-ups who sleep in the building their bed is in (the highest, should two
	 * households share it); with no bed in a builder's building, or nobody there with a class, theirs stays as it is.
	 */
	static void follow(ServerLevel level, Villager child, List<Villager> grown, ClassNeeds.Village village) {
		long today = village.today();
		Homes.Building building = village.buildingAt(VillageNeeds.bed(level, child)).orElse(null);
		if (building != null) {
			SocialClass best = null;
			for (Villager v : grown) {
				BlockPos theirs = VillageNeeds.bed(level, v);
				SocialClass c = theirs != null && building.box().isInside(theirs) ? of(v) : null;
				if (c != null && (best == null || c.tier() > best.tier())) {
					best = c;
				}
			}
			if (best != null) {
				ModAttachments.SOCIAL_CLASS.set(child, best.id());
			}
		}
		Progress p = progress(child);
		ModAttachments.CLASS_PROGRESS.set(child, new Progress(p.met(), p.missed(), p.fed(), today));
	}

	/** Forget which halls finished today's check (tests). */
	public static void forget() {
		synchronized (DAWNS) {
			DAWNS.clear();
		}
	}

	/** Every class by id, lowest first (the hall's pages and tests). */
	public static Map<ResourceLocation, SocialClass> byId() {
		Map<ResourceLocation, SocialClass> out = new LinkedHashMap<>();
		ladder.forEach(c -> out.put(c.id(), c));
		return out;
	}

	private SocialClasses() {
	}
}
