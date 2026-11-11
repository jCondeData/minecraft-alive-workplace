package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QA (lane E, saves, 2026-11-01): every list, set and map of every file of saved data the mod keeps (each {@code
 * SavedData}, found among the mod's classes, so one added later is covered too) comes back after a save. {@link
 * QaEveryBlockEntityField1101GameTests#everyFieldOfEverySavedDataFileComesBackAfterASave} covers each file's single
 * values and skips its collections, whose entries are keyed by and tied to each other; this covers them. Each list, set
 * and map in turn gets made entries (records, the mod's hand-written classes such as a siege or a cup field by field,
 * lists, maps), the file is saved and read back with its own {@code load}, and an entry that doesn't come back is named.
 * One whose entries can only load with what they are tied to (a route needs its village, a lair is keyed by its own
 * hall) is listed in {@link #TIED} with the reason read in its load code and the rest of a consistent file it is given.
 */
public class QaSavedDataCollections1101GameTests implements FabricGameTest {

	/** How a tied field's made entries are made consistent: the value to put in the field, after filling what it needs. */
	interface Fix {
		Object apply(Object data, Object made, Maker maker) throws Exception;
	}

	record Tie(String reason, Fix fix) {
	}

	/**
	 * Lists and maps whose made entries can't load on their own (the class's name after the mod's package, then the
	 * field), each with why (read in its load code) and how the test gives it a consistent file.
	 */
	static final Map<String, Tie> TIED = new LinkedHashMap<>();

	/**
	 * Fields of the hand-written classes inside saved data that aren't saved but should be: each a bug, proved in {@code
	 * QaSavedDataCollections1101BugTests} (skipped here so the rest stays checked).
	 */
	static final Map<String, String> KNOWN_BUGS = new LinkedHashMap<>();

	/** Fields of the hand-written classes inside saved data that are never saved, each with where it is worked out again. */
	static final Map<String, String> INNER_NOT_SAVED = new LinkedHashMap<>();

	static {
		// Caravans.Data#load reads every village's routes, halves, leader, market, colour, stations and tracks from
		// inside that village's entry: none of them is saved for a hall that isn't a listed village.
		String village = "saved inside its village's entry (Caravans.Data#load): only a listed village's comes back";
		TIED.put("hall.Caravans$Data.villages", new Tie("keyed by the village's own hall (load puts it at Village#hall)", (d, made, m) -> rekey(made, "hall")));
		for (String f : List.of("routes", "halves", "leaders", "markets", "colours", "stations", "tracks")) {
			TIED.put("hall.Caravans$Data." + f, new Tie(village, (d, made, m) -> {
				villages(d, ((Map<?, ?>) made).keySet(), m);
				return made;
			}));
		}
		// Each is put back at a part of its own value, whatever key it was saved under.
		TIED.put("threat.Lairs$Data.lairs", new Tie("keyed by its village's hall (Lairs.Data#read puts it at Lair#hall)", (d, made, m) -> rekey(made, "hall")));
		TIED.put("guard.Scouts$Data.missions", new Tie("keyed by its scout (Scouts.Data#read: Mission#scout)", (d, made, m) -> rekey(made, "scout")));
		TIED.put("legend.LegendRecord.entries", new Tie("keyed by its villager (LegendRecord#load: Entry#villager)", (d, made, m) -> rekey(made, "villager")));
		TIED.put("legend.LegendRecord.captives", new Tie("keyed by its villager (LegendRecord#load: Captive#villager)", (d, made, m) -> rekey(made, "villager")));
		TIED.put("travel.TravelNetwork.posts", new Tie("keyed by its own id (TravelNetwork#load: Post#id)", (d, made, m) -> rekey(made, "id")));
		TIED.put("wonder.WonderManager.sites", new Tie("keyed by its own id (WonderManager#load: WonderSite#id)", (d, made, m) -> rekey(made, "id")));
		TIED.put("threat.ThreatData.raids", new Tie("keyed by its hall (ThreatData#read: Under#hall)", (d, made, m) -> rekey(made, "hall")));
		TIED.put("trainer.RaidHelpers$Data.hires", new Tie("keyed by its player (RaidHelpers.Data#load: Hire#player)", (d, made, m) -> rekey(made, "player")));
		TIED.put("guard.RaidDens$Sites.dens", new Tie("keyed by its den's place as a long (RaidDens.Sites#load: Den#pos)", (d, made, m) -> {
			Map<Object, Object> out = new LinkedHashMap<>();
			for (Object den : ((Map<?, ?>) made).values()) {
				out.put(((BlockPos) part(den, "pos")).asLong(), den);
			}
			return out;
		}));
		TIED.put("threat.ThreatData.fires", new Tie("a fire is saved as VillageFire writes it, its hall inside (ThreatData#read keeps the tag whole)", (d, made, m) -> {
			for (Map.Entry<?, ?> e : ((Map<?, ?>) made).entrySet()) {
				((CompoundTag) e.getValue()).putLong("hall", ((BlockPos) e.getKey()).asLong());
			}
			return made;
		}));
		// WonderManager#load keeps a wonder's searches only while its site is listed.
		TIED.put("wonder.WonderManager.searches", new Tie("kept only for a listed wonder site (WonderManager#load)", (d, made, m) -> {
			Map<Object, Object> sites = field(d, "sites");
			for (Object id : ((Map<?, ?>) made).keySet()) {
				sites.put(id, with(m.make(io.github.jcondedata.aliveworkplace.wonder.WonderSite.class, "sites"), "id", id));
			}
			return made;
		}));
		// BuildSiteManager#load reads these from inside each finished building's entry, by its placement.
		String finished = "saved inside a finished building's entry (BuildSiteManager#load): only a listed one's comes back";
		for (String f : List.of("gaveUp", "furniture", "notTurning", "repairAsks", "refreshAsked")) {
			TIED.put("build.BuildSiteManager." + f, new Tie(finished, (d, made, m) -> {
				java.util.ArrayDeque<Object> list = field(d, "finished");
				Collection<?> placements = made instanceof Map<?, ?> map ? map.keySet() : (Collection<?>) made;
				for (Object placement : placements) {
					list.addLast(with(m.make(io.github.jcondedata.aliveworkplace.build.BuildSiteManager.Finished.class, "finished"), "placement", placement));
				}
				return made;
			}));
		}
		TIED.put("bounty.Bounties$Data.bounties", new Tie("keyed by its own id (Bounties.Data#load: Bounty#id), its hideout the bandit camp at a saved spot and turn (Bounties.Data#load)", (d, made, m) -> {
			Map<Object, Object> out = rekey(made, "id");
			for (Object b : out.values()) {
				Field hideout = b.getClass().getDeclaredField("hideout");
				hideout.setAccessible(true);
				hideout.set(b, new io.github.jcondedata.aliveworkplace.story.ArcState.ArcPlace("hideout", new BlockPos(40, 70, -12), List.of(io.github.jcondedata.aliveworkplace.bounty.Bounties.HIDEOUT.toString()), 3));
			}
			return out;
		}));
		// Quest#load parses each objective and reward against the game (an item, a hall to bring it to, a catch's time):
		// made-up ones are rightly dropped, so each made quest gets real ones.
		TIED.put("story.Stories$Data.halls", new Tie("a quest's objectives and rewards must parse (Quest#load, Objectives#parse, Rewards#parse), an arc's done quests be its own (ArcState#save)", (d, made, m) -> {
			for (Object e : ((Map<?, ?>) made).values()) {
				List<io.github.jcondedata.aliveworkplace.story.Quest> quests = field(e, "quests");
				List<io.github.jcondedata.aliveworkplace.story.Quest> real = new ArrayList<>();
				for (io.github.jcondedata.aliveworkplace.story.Quest q : quests) {
					io.github.jcondedata.aliveworkplace.story.Quest r = new io.github.jcondedata.aliveworkplace.story.Quest(q.id, q.file, q.giver, q.name, q.poster,
						q.posted, q.due, List.of(io.github.jcondedata.aliveworkplace.story.Objectives.parse(json("{\"type\": \"bring\", \"item\": \"minecraft:emerald\", \"count\": 24, \"to\": \"hall\"}")),
						io.github.jcondedata.aliveworkplace.story.Objectives.parse(json("{\"type\": \"bring\", \"item\": \"minecraft:bread\", \"count\": 6, \"to\": \"hall\"}"))),
						new int[] {24, 2}, List.of(io.github.jcondedata.aliveworkplace.story.Rewards.parse(json("{\"type\": \"money\", \"emeralds\": 8}"))));
					for (Field f : io.github.jcondedata.aliveworkplace.story.Quest.class.getDeclaredFields()) {
						if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
							f.setAccessible(true);
							f.set(r, f.get(q));
						}
					}
					r.helpers.putAll(q.helpers);
					real.add(r);
				}
				quests.clear();
				quests.addAll(real);
				// an arc saves which of its chapter's quests are done beside each quest (ArcState#save): only its own quests' ids
				List<io.github.jcondedata.aliveworkplace.story.ArcState> arcs = new ArrayList<>(field(e, "sideArcs"));
				if (field(e, "arc") != null) {
					arcs.add(field(e, "arc"));
				}
				for (io.github.jcondedata.aliveworkplace.story.ArcState arc : arcs) {
					arc.done.clear();
					arc.done.add(arc.quests.get(0).id());
				}
			}
			return made;
		}));
		TIED.put("build.BuildSiteManager.sites", new Tie("keyed by its own id (BuildSiteManager#add: BuildSite#id)", (d, made, m) -> rekey(made, "id")));
		TIED.put("mine.QuarrySiteManager.sites", new Tie("keyed by its own id (QuarrySiteManager#add: QuarrySite#id)", (d, made, m) -> rekey(made, "id")));
		TIED.put("threat.ThreatData.sieges", new Tie("keyed by its hall (ThreatData#read: Siege#hall), its way out level (read with Direction#from2DDataValue), each guard's kills and name one entry", (d, made, m) -> {
			Map<Object, Object> out = rekey(made, "hall");
			for (Object o : out.values()) {
				io.github.jcondedata.aliveworkplace.threat.ThreatData.Siege siege = (io.github.jcondedata.aliveworkplace.threat.ThreatData.Siege) o;
				siege.out = net.minecraft.core.Direction.EAST;
				// a guard's kills and name are saved as one entry (ThreatData#save): the same guards in both
				List<String> names = new ArrayList<>(siege.names.values());
				siege.names.clear();
				int i = 0;
				for (java.util.UUID guard : siege.kills.keySet()) {
					siege.names.put(guard, names.get(i++ % names.size()));
				}
			}
			return out;
		}));
		// CampGround#read refuses a ground whose cells don't fill its size, and turns a turn outside 0-3 to 0; Clearing#read a turn to -1.
		TIED.put("threat.Lairs$Data.grounds", new Tie("saved only while a lair stands at its place (Lairs.Data#save), its cells filling its size and its turn 0-3 (CampGround#read)", (d, made, m) -> {
			Map<Object, Object> out = new LinkedHashMap<>();
			Map<Object, Object> lairs = field(d, "lairs");
			for (Object pos : ((Map<?, ?>) made).keySet()) {
				out.put(pos, ground((BlockPos) pos));
				Object lair = with(m.make(io.github.jcondedata.aliveworkplace.threat.Lairs.Lair.class, "lairs"), "pos", pos);
				lairs.put(part(lair, "hall"), lair);
			}
			return out;
		}));
		TIED.put("threat.Lairs$Data.clearing", new Tie("a clearing's turn is 0-3 and its ground's cells fill its size (CampCleanup.Clearing#read)", (d, made, m) -> {
			List<Object> out = new ArrayList<>();
			for (Object c : (List<?>) made) {
				Object turned = with(c, "rotation", 2);
				out.add(with(turned, "ground", ground((BlockPos) part(c, "middle"))));
			}
			return out;
		}));
		// MoveSite#load refuses a site whose "where" (one byte per block of its plan) isn't as long as its plan, and drops
		// packed data for a block index outside it.
		TIED.put("move.MoveSites.sites", new Tie("a site's where has one byte per block of its plan, packed data (and its turned mark) only for those blocks (MoveSite#load)", (d, made, m) -> {
			for (Object site : (List<?>) made) {
				int blocks = ((List<?>) part(part(site, "plan"), "blocks")).size();
				byte[] where = new byte[blocks];
				java.util.Arrays.fill(where, (byte) 1);
				Field f = site.getClass().getDeclaredField("where");
				f.setAccessible(true);
				f.set(site, where);
				Map<?, ?> packed = (Map<?, ?>) part(site, "packedData");
				packed.keySet().removeIf(i -> (Integer) i >= blocks);
				// B1137: the "turned" mark is saved inside a packed entry, so only blocks with packed data keep it
				((java.util.Set<?>) part(site, "packedTurned")).retainAll(packed.keySet());
			}
			return made;
		}));
		for (String f : List.of("plan", "planDepth", "planMargin", "status", "missing", "shortfallStale", "trips", "detail", "claims", "helpersSeen", "lastNotified")) {
			INNER_NOT_SAVED.put("build.BuildSite." + f, "in BuildSite's block of fields marked \"Transient, recomputed as needed\"");
		}
		INNER_NOT_SAVED.put("move.MoveSite.waiting", "why the Mover waits, for his status line: set again at his next step (MoverWork#work)");
		for (String f : List.of("downOrder", "upOrder")) {
			INNER_NOT_SAVED.put("move.MoveSite." + f, "a cache of the plan's blocks in taking-down or putting-up order, worked out again from the plan when first needed (MoveSite#" + f + ")");
		}
		INNER_NOT_SAVED.put("mine.QuarrySite.lastNotified", "when the owner was last told of a missing pickaxe or ladders, in memory (a restart may tell him once more)");
		INNER_NOT_SAVED.put("build.BuildSite.leadClaim", "says so: the block the lead is on away from his cursor, not saved");
		for (String f : List.of("crewColumns", "crewLayerTick", "crewLayerStage", "crewLayer")) {
			INNER_NOT_SAVED.put("build.BuildSite." + f, "says so: a Wonder's crew, not saved, worked out again after a load");
		}
		INNER_NOT_SAVED.put("build.BuildSite.placedThisStage", "says so: not saved, after a load such a block is redone once at most");
		INNER_NOT_SAVED.put("build.BuildSite.placedStage", "the stage placedThisStage is for, not saved with it");
		INNER_NOT_SAVED.put("build.BuildSite.digs", "says so (B868): how often each spot was dug on the current list, not saved; after a load a spot gets its digs afresh");
		INNER_NOT_SAVED.put("build.BuildSite.placedBy", "says so: blocks placed per builder since the site was loaded");
		INNER_NOT_SAVED.put("story.ArcState.endWith", "says so: set by an end_arc effect, not saved (it ends at once)");
		INNER_NOT_SAVED.put("build.BuildSite.progressFromCursor", "says so: transient, worked out at load from a save without shown_progress (BuildSite#load)");
		INNER_NOT_SAVED.put("mine.QuarrySite.status", "the miner's current step for the hall's page, set again as he works (MinerWork)");
		INNER_NOT_SAVED.put("mine.QuarrySite.upgradeNeed", "what the miner lacks for an iron pickaxe, for the status line, looked up again as he works (MinerWork#upgradePickaxe, 36.11a)");
		INNER_NOT_SAVED.put("mine.QuarrySite.fromOldVersion", "worked out at load: whether the save is from before stairs (QuarrySite#load)");
		INNER_NOT_SAVED.put("build.BuildSite.onChange", "a callback the manager sets on the site it holds, not data");
		INNER_NOT_SAVED.put("trainer.RaidHelpers$Hire.lastNear", "when the player was last by the den, refreshed every look while he is (RaidHelpers#tick)");
		INNER_NOT_SAVED.put("trainer.RaidHelpers$Hire.nextStrike", "the helpers' next strike, set again at the join and every strike (RaidHelpers#tick)");
		INNER_NOT_SAVED.put("trainer.RaidHelpers$Hire.nextLook", "says so: not saved, after a restart they look at once");
		INNER_NOT_SAVED.put("cup.CupMatches$Call.battle", "says so: the running battle, not saved (a restart ends it, and the bout is called again)");
		INNER_NOT_SAVED.put("story.ReformStories$Entry.dummies", "the drill's training dummies, found again at the drill's first beat (ReformStories#drill)");
		INNER_NOT_SAVED.put("mine.QuarrySite.onChange", "a callback the manager sets on the site it holds, not data");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyListAndMapOfEverySavedDataFileComesBackAfterASave(GameTestHelper helper) {
		Maker maker = new Maker();
		maker.registries = helper.getLevel().registryAccess();
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> report = new ArrayList<>();
		int files = 0;
		int fields = 0;
		for (Class<?> c : savedDataClasses()) {
			files++;
			for (Field f : collections(c)) {
				String name = name(c, f);
				if (TIED.containsKey(name)) {
					report.add(name + " (tied)");
					continue;
				}
				fields++;
				String d = roundTrip(c, f, maker, registries, null);
				if (d != null) {
					lost.add(d);
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA saved data collections: {} files, {} lone fields checked, {} tied: {}", files, fields, report.size(), report);
		helper.assertTrue(files >= 30, "only " + files + " saved data files found: the search for them is broken");
		lost.forEach(l -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("QA saved data collection lost: {}", l));
		helper.assertTrue(lost.isEmpty(), cut(lost.size() + " saved data list(s) or map(s) don't come back after a save (each in the log): " + lost));
		helper.succeed();
	}

	/**
	 * The tied lists and maps (Caravans' routes, halves, leaders, markets, colours, stations and tracks with their villages;
	 * a lair, mission, legend, post, wonder, raid, siege, hire and den keyed by its own part; a fire with its hall; a
	 * wonder's searches with its site; a finished building's furniture, give-ups, repair asks and checks with it) come back
	 * given the rest of a consistent file. A name in {@link #TIED} that is no longer a field fails, so the list stays true.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyTiedListAndMapComesBackWithWhatItIsTiedTo(GameTestHelper helper) {
		Maker maker = new Maker();
		maker.registries = helper.getLevel().registryAccess();
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Class<?> c : savedDataClasses()) {
			for (Field f : collections(c)) {
				String name = name(c, f);
				Tie tie = TIED.get(name);
				if (tie != null) {
					seen.add(name);
					String d = roundTrip(c, f, maker, registries, tie.fix());
					if (d != null) {
						lost.add(d + " [tied: " + tie.reason() + "]");
					}
				}
			}
		}
		List<String> stale = TIED.keySet().stream().filter(n -> !seen.contains(n)).toList();
		helper.assertTrue(stale.isEmpty(), "TIED names fields that aren't a saved data list or map any more: " + stale);
		lost.forEach(l -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("QA tied saved data collection lost: {}", l));
		helper.assertTrue(lost.isEmpty(), cut(lost.size() + " tied saved data list(s) or map(s) don't come back with what they're tied to (each in the log): " + lost));
		helper.succeed();
	}

	/** Every concrete SavedData class of the mod with a plain constructor and a static {@code load(tag, registries)}. */
	static List<Class<?>> savedDataClasses() {
		List<Class<?>> out = new ArrayList<>();
		for (Class<?> c : QaEveryRecordField1101GameTests.modClasses()) {
			if (SavedData.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers()) && loader(c) != null) {
				try {
					c.getDeclaredConstructor();
					out.add(c);
				} catch (NoSuchMethodException x) {
					// (every file of the mod's has one; a file without would show as a lower count)
				}
			}
		}
		out.sort(java.util.Comparator.comparing(Class::getName));
		return out;
	}

	static List<Field> collections(Class<?> c) {
		List<Field> out = new ArrayList<>();
		for (Field f : c.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()
				&& (Map.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType()))) {
				f.setAccessible(true);
				out.add(f);
			}
		}
		out.sort(java.util.Comparator.comparing(Field::getName));
		return out;
	}

	/** A failure's message, short enough for the test report (which takes 1024 characters at most). */
	static String cut(String message) {
		return message.length() <= 1000 ? message : message.substring(0, 997) + "...";
	}

	static String name(Class<?> c, Field f) {
		return c.getName().substring(MOD.length()) + "." + f.getName();
	}

	/** Fills {@code f} of a new file with made entries (made consistent by {@code fix}), saves, loads; null when it all came back. */
	static String roundTrip(Class<?> c, Field f, Maker maker, HolderLookup.Provider registries, Fix fix) {
		String name = name(c, f);
		try {
			java.lang.reflect.Constructor<?> make = c.getDeclaredConstructor();
			make.setAccessible(true);
			Object wrote = make.newInstance();
			Object value = maker.make(f.getGenericType(), c.getSimpleName() + "." + f.getName());
			if (fix != null) {
				value = fix.apply(wrote, value, maker);
			}
			if (!set(f, wrote, value)) {
				return name + ": the test can't fill it (final " + f.getType().getSimpleName() + ")";
			}
			if (value instanceof Map<?, ?> m ? m.isEmpty() : ((Collection<?>) value).isEmpty()) {
				return name + ": the test made no entries for it";
			}
			CompoundTag tag = ((SavedData) wrote).save(new CompoundTag(), registries);
			Object read = loader(c).invoke(null, tag, registries);
			return diff(value, f.get(read), name);
		} catch (QaEveryRecordField1101GameTests.Unbuildable x) {
			return name + ": the test can't make its entries (" + x.getMessage() + ")";
		} catch (java.lang.reflect.InvocationTargetException x) {
			return name + ": saving or loading it threw " + x.getCause();
		} catch (Exception x) {
			return name + ": " + x + " at " + java.util.Arrays.stream(x.getStackTrace()).limit(6).map(String::valueOf).toList();
		}
	}

	static Method loader(Class<?> c) {
		for (Method m : c.getDeclaredMethods()) {
			if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == c && m.getParameterCount() == 2 && m.getParameterTypes()[0] == CompoundTag.class) {
				m.setAccessible(true);
				return m;
			}
		}
		return null;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static boolean set(Field f, Object owner, Object value) throws IllegalAccessException {
		if (!Modifier.isFinal(f.getModifiers())) {
			f.set(owner, value);
			return true;
		}
		Object now = f.get(owner);
		if (now instanceof Collection held && value instanceof Collection made) {
			held.clear();
			held.addAll(made);
			return true;
		}
		if (now instanceof Map held && value instanceof Map made) {
			held.clear();
			held.putAll(made);
			return true;
		}
		return false;
	}

	// ---- making entries consistent ------------------------------------------------------------------------------------

	/** A part of a record (its accessor) or of a hand-written class (its field). */
	static Object part(Object owner, String name) throws ReflectiveOperationException {
		if (owner instanceof Record) {
			Method m = owner.getClass().getDeclaredMethod(name);
			m.setAccessible(true);
			return m.invoke(owner);
		}
		Field f = owner.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(owner);
	}

	/** The same record with one part changed. */
	static Object with(Object record, String name, Object value) throws ReflectiveOperationException {
		java.lang.reflect.RecordComponent[] parts = record.getClass().getRecordComponents();
		Class<?>[] kinds = new Class<?>[parts.length];
		Object[] values = new Object[parts.length];
		for (int i = 0; i < parts.length; i++) {
			kinds[i] = parts[i].getType();
			parts[i].getAccessor().setAccessible(true);
			values[i] = parts[i].getName().equals(name) ? value : parts[i].getAccessor().invoke(record);
		}
		java.lang.reflect.Constructor<?> ctor = record.getClass().getDeclaredConstructor(kinds);
		ctor.setAccessible(true);
		return ctor.newInstance(values);
	}

	/** The map's values, each at its own part {@code part} (as the load puts them back). */
	static Map<Object, Object> rekey(Object made, String part) throws ReflectiveOperationException {
		Map<Object, Object> out = new LinkedHashMap<>();
		for (Object v : ((Map<?, ?>) made).values()) {
			out.put(part(v, part), v);
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	static <T> T field(Object owner, String name) throws ReflectiveOperationException {
		Field f = owner.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return (T) f.get(owner);
	}

	static com.google.gson.JsonObject json(String text) {
		return com.google.gson.JsonParser.parseString(text).getAsJsonObject();
	}

	/** A camp's ground of two cells, its turn 1, at {@code min} (as CampGround#read takes it). */
	static io.github.jcondedata.aliveworkplace.threat.CampGround ground(BlockPos min) {
		return new io.github.jcondedata.aliveworkplace.threat.CampGround(min, 2, 1, 1, 1,
			List.of(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()), new int[] {1, 0});
	}

	/** Lists a made village (with its own hall) at each of {@code halls} in a caravans file. */
	static void villages(Object data, Collection<?> halls, Maker m) throws Exception {
		Map<Object, Object> villages = field(data, "villages");
		for (Object hall : halls) {
			villages.put(hall, with(m.make(io.github.jcondedata.aliveworkplace.hall.Caravans.Village.class, "villages"), "hall", hall));
		}
	}

	// ---- making values: the record test's maker, and also the kinds of things saved data holds that records don't --

	static final String MOD = "io.github.jcondedata.aliveworkplace.";

	/** True for one of the mod's own hand-written classes (not a record, enum or interface): made and compared field by field. */
	static boolean handWritten(Class<?> c) {
		return c.getName().startsWith(MOD) && !c.isRecord() && !c.isEnum() && !c.isInterface() && !c.isArray()
			&& !Modifier.isAbstract(c.getModifiers()) && !SavedData.class.isAssignableFrom(c);
	}

	/**
	 * The record test's maker, which also makes the concrete lists, sets and maps saved data declares (an {@code
	 * ArrayDeque}, a {@code LinkedHashSet}, fastutil's sets and maps of longs), a block state, and the mod's own
	 * hand-written classes (a siege, a cup, a bounty): made with their constructor, then every field given a made value.
	 */
	static final class Maker extends QaEveryRecordField1101GameTests {
		private int k = 1;

		@Override
		Object make(java.lang.reflect.Type type, String path) throws Unbuildable {
			if (path.length() > 400 || path.split("\\.", -1).length > 14) {
				throw new Unbuildable(path + " goes too deep");
			}
			Class<?> raw = type instanceof Class<?> c ? c
				: type instanceof java.lang.reflect.ParameterizedType p ? (Class<?>) p.getRawType() : null;
			java.lang.reflect.Type[] args = type instanceof java.lang.reflect.ParameterizedType p ? p.getActualTypeArguments() : new java.lang.reflect.Type[0];
			if (raw == java.util.ArrayDeque.class) {
				java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
				q.add(make(args[0], path + "[0]"));
				q.add(make(args[0], path + "[1]"));
				return q;
			}
			if (raw == ArrayList.class) {
				return new ArrayList<>((List<?>) make(parameterized(List.class, args[0]), path));
			}
			if (raw == java.util.LinkedHashSet.class || raw == java.util.HashSet.class) {
				return new java.util.LinkedHashSet<>((java.util.Set<?>) make(parameterized(java.util.Set.class, args[0]), path));
			}
			if (raw == LinkedHashMap.class || raw == java.util.HashMap.class || raw == java.util.TreeMap.class) {
				Map<?, ?> m = (Map<?, ?>) make(parameterized(Map.class, args[0], args[1]), path);
				return raw == java.util.TreeMap.class ? new java.util.TreeMap<>(m) : new LinkedHashMap<>(m);
			}
			if (raw == it.unimi.dsi.fastutil.longs.LongSet.class || raw == it.unimi.dsi.fastutil.longs.LongOpenHashSet.class) {
				it.unimi.dsi.fastutil.longs.LongOpenHashSet set = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
				set.add(net.minecraft.core.BlockPos.asLong(3 + k, 64, -k));
				set.add(net.minecraft.core.BlockPos.asLong(9 + k++, 70, 4));
				return set;
			}
			if (raw == it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap.class) {
				it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap map = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
				map.put(net.minecraft.core.BlockPos.asLong(3 + k, 64, -k), net.minecraft.core.BlockPos.asLong(40 + k++, 66, 7));
				return map;
			}
			if (raw == it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap.class) {
				it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap map = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
				map.put(net.minecraft.core.BlockPos.asLong(3 + k, 64, -k), 5 + k++ % 7);
				return map;
			}
			if (raw == net.minecraft.world.level.block.state.BlockState.class) {
				return (k++ % 2 == 0 ? net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS : net.minecraft.world.level.block.Blocks.COBBLESTONE).defaultBlockState();
			}
			if (raw == int[].class) {
				return new int[] {3 + k, 7 + k++ % 5, 2};
			}
			if (raw == byte[].class) {
				return new byte[] {(byte) (1 + k++ % 3)};
			}
			if (raw == long[].class) {
				return new long[] {5_000_000_000L + k++, 42L};
			}
			if (raw == java.util.UUID[].class) {
				return new java.util.UUID[] {new java.util.UUID(0x99L, k++), new java.util.UUID(0x98L, k++)};
			}
			if (raw == com.google.gson.JsonObject.class) {
				com.google.gson.JsonObject json = new com.google.gson.JsonObject();
				json.addProperty("qa", "qa_" + k++);
				return json;
			}
			if (raw == net.minecraft.world.phys.Vec3.class) {
				return new net.minecraft.world.phys.Vec3(1.5 + k, 64.0, -2.5 - k++);
			}
			if (raw != null && args.length == 0 && handWritten(raw)) {
				return makeHandWritten(raw, path);
			}
			return super.make(type, path);
		}

		private Object makeHandWritten(Class<?> c, String path) throws Unbuildable {
			java.lang.reflect.Constructor<?> best = null;
			for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
				if (!ctor.isSynthetic() && (best == null || ctor.getParameterCount() > best.getParameterCount())) {
					best = ctor;
				}
			}
			if (best == null) {
				throw new Unbuildable(path + " (" + c.getSimpleName() + ") has no constructor");
			}
			Object[] values = new Object[best.getParameterCount()];
			java.lang.reflect.Type[] kinds = best.getGenericParameterTypes();
			if (kinds.length != values.length) {
				throw new Unbuildable(path + " (" + c.getSimpleName() + ") has an inner class's constructor");
			}
			for (int i = 0; i < values.length; i++) {
				values[i] = make(kinds[i], path + "(" + i + ")");
			}
			Object made;
			try {
				best.setAccessible(true);
				made = best.newInstance(values);
			} catch (java.lang.reflect.InvocationTargetException e) {
				throw new Unbuildable(path + " (" + c.getSimpleName() + ") refused the values: " + e.getCause());
			} catch (ReflectiveOperationException e) {
				throw new Unbuildable(path + " (" + c.getSimpleName() + "): " + e);
			}
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || notData(f)) {
					continue;
				}
				try {
					f.setAccessible(true);
					if (Modifier.isFinal(f.getModifiers()) && !(Map.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType()))) {
						continue; // set by the constructor
					}
					set(f, made, make(f.getGenericType(), path + "." + f.getName()));
				} catch (UnsupportedOperationException e) {
					// a final list the constructor made unchangeable from its own made values: they stay
				} catch (IllegalAccessException e) {
					throw new Unbuildable(path + "." + f.getName() + ": " + e);
				}
			}
			return made;
		}
	}

	static java.lang.reflect.ParameterizedType parameterized(Class<?> raw, java.lang.reflect.Type... args) {
		return new java.lang.reflect.ParameterizedType() {
			@Override
			public java.lang.reflect.Type[] getActualTypeArguments() {
				return args;
			}

			@Override
			public java.lang.reflect.Type getRawType() {
				return raw;
			}

			@Override
			public java.lang.reflect.Type getOwnerType() {
				return null;
			}

			@Override
			public String toString() {
				return raw.getSimpleName() + java.util.Arrays.toString(args);
			}
		};
	}

	// ---- comparing ---------------------------------------------------------------------------------------------------

	/** As the record test's diff, also field by field for the mod's hand-written classes, and with a block state by state. */
	static String diff(Object wrote, Object read, String path) {
		if (wrote == null || read == null) {
			return wrote == read ? null : path + ": saved " + wrote + ", read " + read;
		}
		if (wrote instanceof net.minecraft.world.item.ItemStack a && read instanceof net.minecraft.world.item.ItemStack b) {
			return net.minecraft.world.item.ItemStack.matches(a, b) ? null : path + ": saved " + a + ", read " + b;
		}
		if (handWritten(wrote.getClass()) && wrote.getClass() == read.getClass()) {
			for (Field f : wrote.getClass().getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || notData(f)) {
					continue;
				}
				try {
					f.setAccessible(true);
					String d = diff(f.get(wrote), f.get(read), path + "." + f.getName());
					if (d != null) {
						return d;
					}
				} catch (IllegalAccessException e) {
					return path + "." + f.getName() + ": can't read (" + e + ")";
				}
			}
			return null;
		}
		if (wrote instanceof Record && wrote.getClass() == read.getClass()) {
			for (java.lang.reflect.RecordComponent part : wrote.getClass().getRecordComponents()) {
				try {
					part.getAccessor().setAccessible(true);
					String d = diff(part.getAccessor().invoke(wrote), part.getAccessor().invoke(read), path + "." + part.getName());
					if (d != null) {
						return d;
					}
				} catch (ReflectiveOperationException e) {
					return path + "." + part.getName() + ": can't read (" + e + ")";
				}
			}
			return null;
		}
		if (wrote instanceof java.util.Optional<?> a && read instanceof java.util.Optional<?> b) {
			return diff(a.orElse(null), b.orElse(null), path + ".get");
		}
		if (wrote instanceof Map<?, ?> a && read instanceof Map<?, ?> b) {
			if (a.size() != b.size()) {
				return path + ": saved " + a.size() + " entries, read " + b.size() + " (" + b + ")";
			}
			for (Map.Entry<?, ?> e : a.entrySet()) {
				if (!b.containsKey(e.getKey())) {
					return path + ": key " + e.getKey() + " lost (read " + b.keySet() + ")";
				}
				String d = diff(e.getValue(), b.get(e.getKey()), path + "[" + e.getKey() + "]");
				if (d != null) {
					return d;
				}
			}
			return null;
		}
		if (wrote instanceof java.util.Set<?> a && read instanceof java.util.Set<?> b) {
			if (a.size() != b.size()) {
				return path + ": saved " + a.size() + " entries, read " + b.size() + " (" + b + ")";
			}
			for (Object x : a) {
				boolean found = false;
				for (Object y : b) {
					if (diff(x, y, path) == null) {
						found = true;
						break;
					}
				}
				if (!found) {
					return path + ": " + x + " lost (read " + b + ")";
				}
			}
			return null;
		}
		if (wrote instanceof Collection<?> a && read instanceof Collection<?> b) {
			if (a.size() != b.size()) {
				return path + ": saved " + a.size() + " entries, read " + b.size() + " (" + b + ")";
			}
			java.util.Iterator<?> ia = a.iterator();
			java.util.Iterator<?> ib = b.iterator();
			for (int i = 0; ia.hasNext(); i++) {
				String d = diff(ia.next(), ib.next(), path + "[" + i + "]");
				if (d != null) {
					return d;
				}
			}
			return null;
		}
		if (wrote.getClass().isArray() && read.getClass() == wrote.getClass()) {
			return java.util.Objects.deepEquals(wrote, read) ? null : path + ": saved " + java.util.Arrays.deepToString(new Object[] {wrote})
				+ ", read " + java.util.Arrays.deepToString(new Object[] {read});
		}
		return wrote.equals(read) ? null : path + ": saved " + wrote + ", read " + read;
	}

	/** A hand-written class's field that isn't saved data: listed in {@link #INNER_NOT_SAVED}. */
	static boolean notData(Field f) {
		String name = f.getDeclaringClass().getName().substring(MOD.length()) + "." + f.getName();
		return INNER_NOT_SAVED.containsKey(name) || KNOWN_BUGS.containsKey(name);
	}
}
