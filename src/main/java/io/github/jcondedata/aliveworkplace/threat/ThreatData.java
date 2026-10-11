package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A dimension's threats, saved as {@code aliveworkplace_threats} (ROADMAP 32.2): the raids under way (so a restart
 * mid-raid carries on) and each hall's threat clock (the attacks rolled ahead). Empty by default; every field has a
 * default, and an entry that can't be read is left out.
 * <ul>
 * <li>{@code raids}: {@code hall}, {@code culture} (default {@code aliveworkplace:monsters}), {@code raiders} (how many
 * came), {@code began} (game time);</li>
 * <li>{@code clock}: per {@code hall}, {@code rolled} (the last day whose dusk was rolled; default -1: rolls at the
 * next dusk) and {@code attacks}: {@code day}, {@code culture}, {@code angle}, {@code at} (the hour, day time);</li>
 * <li>{@code history} (32.3): per {@code hall}, its last {@link #REMEMBERED} attacks, oldest first: {@code day},
 * {@code culture}, {@code came}, {@code fell}, {@code fled} (the last raiders left at the end of their hours; false:
 * the village fought them all off).</li>
 * <li>{@code sieges} (32.4): per {@code hall}, the siege laid to it, kept until the first dawn after it began (the
 * raid may be over sooner): {@code began} (game time), {@code dawn} (the day time at which the gates open again),
 * {@code over}, {@code breached}, {@code breach} (the gate block the rams go for; absent: no gate to break),
 * {@code out} (the side of the breach the raiders came from, a horizontal direction's number), {@code gates}: the
 * breach gate's blocks in the order the rams break them, each {@code pos}, {@code hp}, {@code state}
 * ({@code standing}, {@code broken}, {@code gone}), {@code lane} (0: the way through first opened), {@code high}
 * (above head height) and {@code dropped} (a bar of the dropped portcullis, not of the blueprint); and
 * {@code portcullis}: every bar the siege dropped, to draw up again. Since 32.5 also {@code ladders} (every rung the
 * raiders set against a wall, to take away again; default none) and {@code laddered} (the players were told of the
 * ladders; default false). Since 32.6 also the morning report's numbers: {@code hp_factor} (what the gates' hit
 * points were multiplied by; default 1), {@code came}, {@code fled}, {@code to_guards}, {@code to_players},
 * {@code to_others} (default 0 and false), {@code kills} (per guard: {@code guard}, {@code count}, {@code name};
 * default none) and {@code players} (who killed a raider; default none).</li>
 * <li>{@code broken} (32.4): per {@code hall}, the gate blocks rams broke that wait for a builder.</li>
 * <li>{@code after} (32.6): per {@code hall}, the game time until which its villagers are glad they held
 * ({@code held}) and its builders mend defence builds first ({@code mending}); default none.</li>
 * </ul>
 */
public final class ThreatData extends SavedData {
	public static final String NAME = "aliveworkplace_threats";

	/** A raid under way on the village round {@code hall}: whose, how many came, when it began (game time). */
	public record Under(BlockPos hall, ResourceLocation culture, int raiders, long began) {
	}

	/**
	 * An attack the clock has set: the night of {@code day} (the chronicle's day count), by {@code culture}, from the
	 * side {@code angle} (radians from the hall, {@code atan2(dz, dx)}), at the hour {@code at} (day time).
	 */
	public record Attack(long day, ResourceLocation culture, double angle, long at) {
	}

	/** An attack that is over: the day it came, whose, how many came and fell, and whether the last of them fled. */
	public record Past(long day, ResourceLocation culture, int came, int fell, boolean fled) {
	}

	/** How a gate block in a siege stands: whole or damaged, broken by the rams, or gone some other way (a player took it). */
	public enum GateState {
		STANDING, BROKEN, GONE;

		static GateState parse(String name) {
			for (GateState state : values()) {
				if (state.name().equalsIgnoreCase(name)) {
					return state;
				}
			}
			return STANDING;
		}
	}

	/**
	 * One block of the gate a siege's rams go for: where, the hit points it has left, how it stands, which way through
	 * it belongs to ({@code lane} 0 is opened first), whether it is above head height (broken last, for the ram itself)
	 * and whether it is a bar of the dropped portcullis (not in the blueprint, so nothing to repair).
	 */
	public static final class Gate {
		public final BlockPos pos;
		public int hp;
		public GateState state = GateState.STANDING;
		public final int lane;
		public final boolean high;
		public final boolean dropped;

		public Gate(BlockPos pos, int hp, int lane, boolean high, boolean dropped) {
			this.pos = pos.immutable();
			this.hp = hp;
			this.lane = lane;
			this.high = high;
			this.dropped = dropped;
		}
	}

	/** A siege laid to the village round {@code hall} (32.4); see the class comment for its fields. */
	public static final class Siege {
		public final BlockPos hall;
		public final long began;
		public long dawn;
		public boolean over;
		public boolean breached;
		@org.jetbrains.annotations.Nullable
		public BlockPos breach;
		public net.minecraft.core.Direction out = net.minecraft.core.Direction.SOUTH;
		public final List<Gate> gates = new ArrayList<>();
		public final List<BlockPos> portcullis = new ArrayList<>();
		/** Every rung the raiders set against a wall (32.5), to take away when the raid is over. */
		public final List<BlockPos> ladders = new ArrayList<>();
		/** Whether the players were told that ladders are at the walls. */
		public boolean laddered;
		/** The morning-after report's numbers (32.6); every one defaults to nothing in a save from before it. */
		/** What the gates' hit points were multiplied by when the siege began (2 with the Ramparts research; a save from before it: 1). */
		public int hpFactor = 1;
		/** How many raiders came. */
		public int came;
		/** Whether the raiders that were left fled at dawn (the siege was not won). */
		public boolean fled;
		/** Raiders that fell to guards, to players, and to anything else. */
		public int toGuards;
		public int toPlayers;
		public int toOthers;
		/** Each guard's kills in this siege, and the guards' names as they were at their last kill. */
		public final Map<java.util.UUID, Integer> kills = new LinkedHashMap<>();
		public final Map<java.util.UUID, String> names = new LinkedHashMap<>();
		/** The players who fought: who killed a raider of this siege. */
		public final Set<java.util.UUID> players = new java.util.LinkedHashSet<>();

		public Siege(BlockPos hall, long began, long dawn) {
			this.hall = hall.immutable();
			this.began = began;
			this.dawn = dawn;
		}
	}

	/** How many past attacks a hall remembers (the Defence page shows the last three). */
	public static final int REMEMBERED = 3;

	/** One hall's clock. */
	static final class Clock {
		long rolled = -1;
		final List<Attack> attacks = new ArrayList<>();
	}

	/** The data of every dimension loaded since the server started (for the few callers that know a hall but no level). */
	private static final Set<ThreatData> LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

	private final Map<BlockPos, Under> raids = new LinkedHashMap<>();
	private final Map<BlockPos, Clock> clocks = new LinkedHashMap<>();
	private final Map<BlockPos, List<Past>> history = new LinkedHashMap<>();
	private final Map<BlockPos, Siege> sieges = new LinkedHashMap<>();
	private final Map<BlockPos, List<BlockPos>> broken = new LinkedHashMap<>();
	/** Per hall, the game time until which its villagers are glad they held (32.6). */
	private final Map<BlockPos, Long> held = new LinkedHashMap<>();
	/** Per hall, the game time until which its builders mend defence builds first (32.6). */
	private final Map<BlockPos, Long> mending = new LinkedHashMap<>();

	public static ThreatData get(ServerLevel level) {
		ThreatData data = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ThreatData::new, ThreatData::load, null), NAME);
		LOADED.add(data);
		return data;
	}

	/** A new server: the last one's dimensions are gone. */
	static void serverStarting() {
		LOADED.clear();
	}

	/** The raid under way on {@code hall} in any loaded dimension. */
	public static Optional<Under> anywhere(BlockPos hall) {
		synchronized (LOADED) {
			for (ThreatData data : LOADED) {
				Under raid = data.raids.get(hall);
				if (raid != null) {
					return Optional.of(raid);
				}
			}
		}
		return Optional.empty();
	}

	/** Forgets the raid on {@code hall} (null: every raid) in every loaded dimension (tests). */
	public static void forgetRaids(@org.jetbrains.annotations.Nullable BlockPos hall) {
		synchronized (LOADED) {
			for (ThreatData data : LOADED) {
				if (hall == null ? !data.raids.isEmpty() : data.raids.containsKey(hall)) {
					if (hall == null) {
						data.raids.clear();
					} else {
						data.raids.remove(hall);
					}
					data.setDirty();
				}
			}
		}
	}

	// Raids.

	public Optional<Under> raid(BlockPos hall) {
		return Optional.ofNullable(raids.get(hall));
	}

	public List<Under> raids() {
		return List.copyOf(raids.values());
	}

	/** A raid under way on a hall within {@code radius} blocks of {@code center}. */
	public Optional<Under> raidNear(BlockPos center, int radius) {
		for (Under raid : raids.values()) {
			if (raid.hall().distSqr(center) <= (double) radius * radius) {
				return Optional.of(raid);
			}
		}
		return Optional.empty();
	}

	public void begin(Under raid) {
		raids.put(raid.hall(), raid);
		setDirty();
	}

	public void end(BlockPos hall) {
		if (raids.remove(hall) != null) {
			setDirty();
		}
	}

	// The clock.

	/** The last day whose dusk the hall's clock rolled (-1: never). */
	public long rolled(BlockPos hall) {
		Clock clock = clocks.get(hall);
		return clock == null ? -1 : clock.rolled;
	}

	/** The attacks set for the village round {@code hall}, soonest first. */
	public List<Attack> attacks(BlockPos hall) {
		Clock clock = clocks.get(hall);
		return clock == null ? List.of() : List.copyOf(clock.attacks);
	}

	/** The dusk of {@code day} is rolled: {@code attack} (null: a quiet night) is what it set. */
	public void rolled(BlockPos hall, long day, @org.jetbrains.annotations.Nullable Attack attack) {
		Clock clock = clocks.computeIfAbsent(hall.immutable(), h -> new Clock());
		clock.rolled = day;
		if (attack != null) {
			clock.attacks.add(attack);
			clock.attacks.sort(java.util.Comparator.comparingLong(Attack::day));
		}
		setDirty();
	}

	/** Takes the attacks of days before {@code day} off the hall's clock, and the attack of {@code day} itself if {@code too}. */
	public void drop(BlockPos hall, long day, boolean too) {
		Clock clock = clocks.get(hall);
		if (clock != null && clock.attacks.removeIf(a -> a.day() < day || too && a.day() == day)) {
			setDirty();
		}
	}

	/** Forgets the hall's clock (the hall is gone; tests). */
	public void forgetClock(BlockPos hall) {
		if (clocks.remove(hall) != null) {
			setDirty();
		}
	}

	// The attacks that are over.

	/** The last attacks on the village round {@code hall}, newest first (at most {@link #REMEMBERED}). */
	public List<Past> past(BlockPos hall) {
		List<Past> list = new ArrayList<>(history.getOrDefault(hall, List.of()));
		Collections.reverse(list);
		return list;
	}

	/** An attack on the village round {@code hall} is over: it's remembered, and the oldest forgotten. */
	public void remember(BlockPos hall, Past attack) {
		List<Past> list = history.computeIfAbsent(hall.immutable(), h -> new ArrayList<>());
		list.add(attack);
		while (list.size() > REMEMBERED) {
			list.remove(0);
		}
		setDirty();
	}

	/** Forgets the attacks on the village round {@code hall} (the hall is gone; tests). */
	public void forgetPast(BlockPos hall) {
		if (history.remove(hall) != null) {
			setDirty();
		}
	}

	// Sieges (32.4).

	/** The siege laid to the village round {@code hall}: from when it begins until the first dawn after. */
	public Optional<Siege> siege(BlockPos hall) {
		return Optional.ofNullable(sieges.get(hall));
	}

	public List<Siege> sieges() {
		return sieges.isEmpty() ? List.of() : List.copyOf(sieges.values());
	}

	public void beginSiege(Siege siege) {
		sieges.put(siege.hall, siege);
		setDirty();
	}

	public void endSiege(BlockPos hall) {
		if (sieges.remove(hall) != null) {
			setDirty();
		}
	}

	/** The gate blocks of the village round {@code hall} that rams broke and no builder has put back yet. */
	public List<BlockPos> brokenGates(BlockPos hall) {
		return List.copyOf(broken.getOrDefault(hall, List.of()));
	}

	/** A ram broke the gate block at {@code pos}: it waits for a builder. */
	public void gateBroken(BlockPos hall, BlockPos pos) {
		List<BlockPos> list = broken.computeIfAbsent(hall.immutable(), h -> new ArrayList<>());
		if (!list.contains(pos)) {
			list.add(pos.immutable());
			setDirty();
		}
	}

	/** The gate block at {@code pos} is back (or will never be: the build is gone). */
	public void gateMended(BlockPos hall, BlockPos pos) {
		List<BlockPos> list = broken.get(hall);
		if (list != null && list.remove(pos)) {
			if (list.isEmpty()) {
				broken.remove(hall);
			}
			setDirty();
		}
	}

	// The morning after (32.6).

	/** The game time until which the villagers round {@code hall} are glad they held (0: never). */
	public long heldUntil(BlockPos hall) {
		return held.getOrDefault(hall, 0L);
	}

	/** The game time until which the builders round {@code hall} mend defence builds first (0: never). */
	public long mendingUntil(BlockPos hall) {
		return mending.getOrDefault(hall, 0L);
	}

	/** The halls whose villagers are glad they held, with the game time it ends. */
	public Map<BlockPos, Long> held() {
		return held.isEmpty() ? Map.of() : Map.copyOf(held);
	}

	/** The halls whose builders mend defence builds first, with the game time it ends. */
	public Map<BlockPos, Long> mending() {
		return Map.copyOf(mending);
	}

	/** Sets the morning after of the village round {@code hall}: glad till {@code heldTill} (0: not), defences first till {@code mendTill}. */
	public void after(BlockPos hall, long heldTill, long mendTill) {
		if (heldTill > 0) {
			held.put(hall.immutable(), heldTill);
		} else {
			held.remove(hall);
		}
		if (mendTill > 0) {
			mending.put(hall.immutable(), mendTill);
		} else {
			mending.remove(hall);
		}
		setDirty();
	}

	/** Forgets the siege of the village round {@code hall}, its broken gates and its morning after (the hall is gone; tests). */
	public void forgetSiege(BlockPos hall) {
		if (sieges.remove(hall) != null | broken.remove(hall) != null | held.remove(hall) != null | mending.remove(hall) != null) {
			setDirty();
		}
	}

	// Saving.

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Under raid : raids.values()) {
			CompoundTag r = new CompoundTag();
			r.putLong("hall", raid.hall().asLong());
			r.putString("culture", raid.culture().toString());
			r.putInt("raiders", raid.raiders());
			r.putLong("began", raid.began());
			list.add(r);
		}
		tag.put("raids", list);
		ListTag halls = new ListTag();
		for (Map.Entry<BlockPos, Clock> e : clocks.entrySet()) {
			CompoundTag c = new CompoundTag();
			c.putLong("hall", e.getKey().asLong());
			c.putLong("rolled", e.getValue().rolled);
			ListTag attacks = new ListTag();
			for (Attack attack : e.getValue().attacks) {
				CompoundTag a = new CompoundTag();
				a.putLong("day", attack.day());
				a.putString("culture", attack.culture().toString());
				a.putDouble("angle", attack.angle());
				a.putLong("at", attack.at());
				attacks.add(a);
			}
			c.put("attacks", attacks);
			halls.add(c);
		}
		tag.put("clock", halls);
		ListTag pasts = new ListTag();
		for (Map.Entry<BlockPos, List<Past>> e : history.entrySet()) {
			CompoundTag h = new CompoundTag();
			h.putLong("hall", e.getKey().asLong());
			ListTag attacks = new ListTag();
			for (Past past : e.getValue()) {
				CompoundTag a = new CompoundTag();
				a.putLong("day", past.day());
				a.putString("culture", past.culture().toString());
				a.putInt("came", past.came());
				a.putInt("fell", past.fell());
				a.putBoolean("fled", past.fled());
				attacks.add(a);
			}
			h.put("attacks", attacks);
			pasts.add(h);
		}
		tag.put("history", pasts);
		ListTag laid = new ListTag();
		for (Siege siege : sieges.values()) {
			CompoundTag g = new CompoundTag();
			g.putLong("hall", siege.hall.asLong());
			g.putLong("began", siege.began);
			g.putLong("dawn", siege.dawn);
			g.putBoolean("over", siege.over);
			g.putBoolean("breached", siege.breached);
			if (siege.breach != null) {
				g.putLong("breach", siege.breach.asLong());
			}
			g.putInt("out", siege.out.get2DDataValue());
			ListTag gates = new ListTag();
			for (Gate gate : siege.gates) {
				CompoundTag b = new CompoundTag();
				b.putLong("pos", gate.pos.asLong());
				b.putInt("hp", gate.hp);
				b.putString("state", gate.state.name().toLowerCase(java.util.Locale.ROOT));
				b.putInt("lane", gate.lane);
				b.putBoolean("high", gate.high);
				b.putBoolean("dropped", gate.dropped);
				gates.add(b);
			}
			g.put("gates", gates);
			g.putLongArray("portcullis", siege.portcullis.stream().mapToLong(BlockPos::asLong).toArray());
			g.putLongArray("ladders", siege.ladders.stream().mapToLong(BlockPos::asLong).toArray());
			g.putBoolean("laddered", siege.laddered);
			g.putInt("hp_factor", siege.hpFactor);
			g.putInt("came", siege.came);
			g.putBoolean("fled", siege.fled);
			g.putInt("to_guards", siege.toGuards);
			g.putInt("to_players", siege.toPlayers);
			g.putInt("to_others", siege.toOthers);
			ListTag kills = new ListTag();
			for (Map.Entry<java.util.UUID, Integer> e : siege.kills.entrySet()) {
				CompoundTag k = new CompoundTag();
				k.putString("guard", e.getKey().toString());
				k.putInt("count", e.getValue());
				k.putString("name", siege.names.getOrDefault(e.getKey(), ""));
				kills.add(k);
			}
			g.put("kills", kills);
			ListTag fought = new ListTag();
			for (java.util.UUID id : siege.players) {
				CompoundTag k = new CompoundTag();
				k.putString("id", id.toString());
				fought.add(k);
			}
			g.put("players", fought);
			laid.add(g);
		}
		tag.put("sieges", laid);
		ListTag mornings = new ListTag();
		Set<BlockPos> villages = new java.util.LinkedHashSet<>(held.keySet());
		villages.addAll(mending.keySet());
		for (BlockPos hall : villages) {
			CompoundTag a = new CompoundTag();
			a.putLong("hall", hall.asLong());
			a.putLong("held", held.getOrDefault(hall, 0L));
			a.putLong("mending", mending.getOrDefault(hall, 0L));
			mornings.add(a);
		}
		tag.put("after", mornings);
		ListTag holes = new ListTag();
		for (Map.Entry<BlockPos, List<BlockPos>> e : broken.entrySet()) {
			CompoundTag h = new CompoundTag();
			h.putLong("hall", e.getKey().asLong());
			h.putLongArray("gates", e.getValue().stream().mapToLong(BlockPos::asLong).toArray());
			holes.add(h);
		}
		tag.put("broken", holes);
		return tag;
	}

	static ThreatData load(CompoundTag tag, HolderLookup.Provider registries) {
		ThreatData data = new ThreatData();
		data.read(tag);
		return data;
	}

	/** Replaces what this holds with what {@code tag} holds (a load). */
	public void read(CompoundTag tag) {
		raids.clear();
		clocks.clear();
		history.clear();
		sieges.clear();
		broken.clear();
		held.clear();
		mending.clear();
		ListTag mornings = Nbt.getList(tag, "after", Tag.TAG_COMPOUND);
		for (int i = 0; i < mornings.size(); i++) {
			CompoundTag a = Nbt.compoundAt(mornings, i);
			if (Nbt.has(a, "hall", Tag.TAG_LONG)) {
				BlockPos hall = BlockPos.of(Nbt.getLong(a, "hall"));
				if (Nbt.getLong(a, "held") > 0) {
					held.put(hall, Nbt.getLong(a, "held"));
				}
				if (Nbt.getLong(a, "mending") > 0) {
					mending.put(hall, Nbt.getLong(a, "mending"));
				}
			}
		}
		ListTag laid = Nbt.getList(tag, "sieges", Tag.TAG_COMPOUND);
		for (int i = 0; i < laid.size(); i++) {
			CompoundTag g = Nbt.compoundAt(laid, i);
			if (!Nbt.has(g, "hall", Tag.TAG_LONG)) {
				continue;
			}
			Siege siege = new Siege(BlockPos.of(Nbt.getLong(g, "hall")), Nbt.getLong(g, "began"), Nbt.getLong(g, "dawn"));
			siege.over = Nbt.getBoolean(g, "over");
			siege.breached = Nbt.getBoolean(g, "breached");
			siege.breach = Nbt.has(g, "breach", Tag.TAG_LONG) ? BlockPos.of(Nbt.getLong(g, "breach")) : null;
			siege.out = net.minecraft.core.Direction.from2DDataValue(Nbt.getInt(g, "out"));
			ListTag gates = Nbt.getList(g, "gates", Tag.TAG_COMPOUND);
			for (int j = 0; j < gates.size(); j++) {
				CompoundTag b = Nbt.compoundAt(gates, j);
				if (Nbt.has(b, "pos", Tag.TAG_LONG)) {
					Gate gate = new Gate(BlockPos.of(Nbt.getLong(b, "pos")), Nbt.getInt(b, "hp"), Nbt.getInt(b, "lane"), Nbt.getBoolean(b, "high"),
						Nbt.getBoolean(b, "dropped"));
					gate.state = GateState.parse(Nbt.getString(b, "state"));
					siege.gates.add(gate);
				}
			}
			for (long pos : Nbt.getLongArray(g, "portcullis")) {
				siege.portcullis.add(BlockPos.of(pos));
			}
			for (long pos : Nbt.getLongArray(g, "ladders")) {
				siege.ladders.add(BlockPos.of(pos));
			}
			siege.laddered = Nbt.getBoolean(g, "laddered");
			siege.hpFactor = Math.max(1, Nbt.getInt(g, "hp_factor"));
			siege.came = Nbt.getInt(g, "came");
			siege.fled = Nbt.getBoolean(g, "fled");
			siege.toGuards = Nbt.getInt(g, "to_guards");
			siege.toPlayers = Nbt.getInt(g, "to_players");
			siege.toOthers = Nbt.getInt(g, "to_others");
			ListTag kills = Nbt.getList(g, "kills", Tag.TAG_COMPOUND);
			for (int j = 0; j < kills.size(); j++) {
				CompoundTag k = Nbt.compoundAt(kills, j);
				try {
					java.util.UUID id = java.util.UUID.fromString(Nbt.getString(k, "guard"));
					siege.kills.put(id, Nbt.getInt(k, "count"));
					siege.names.put(id, Nbt.getString(k, "name"));
				} catch (IllegalArgumentException e) {
					// (not an id: the entry is skipped)
				}
			}
			ListTag fought = Nbt.getList(g, "players", Tag.TAG_COMPOUND);
			for (int j = 0; j < fought.size(); j++) {
				try {
					siege.players.add(java.util.UUID.fromString(Nbt.getString(Nbt.compoundAt(fought, j), "id")));
				} catch (IllegalArgumentException e) {
					// (not an id: the entry is skipped)
				}
			}
			sieges.put(siege.hall, siege);
		}
		ListTag holes = Nbt.getList(tag, "broken", Tag.TAG_COMPOUND);
		for (int i = 0; i < holes.size(); i++) {
			CompoundTag h = Nbt.compoundAt(holes, i);
			if (Nbt.has(h, "hall", Tag.TAG_LONG)) {
				List<BlockPos> list = new ArrayList<>();
				for (long pos : Nbt.getLongArray(h, "gates")) {
					list.add(BlockPos.of(pos));
				}
				if (!list.isEmpty()) {
					broken.put(BlockPos.of(Nbt.getLong(h, "hall")), list);
				}
			}
		}
		ListTag pasts = Nbt.getList(tag, "history", Tag.TAG_COMPOUND);
		for (int i = 0; i < pasts.size(); i++) {
			CompoundTag h = Nbt.compoundAt(pasts, i);
			if (!Nbt.has(h, "hall", Tag.TAG_LONG)) {
				continue;
			}
			List<Past> list = new ArrayList<>();
			ListTag attacks = Nbt.getList(h, "attacks", Tag.TAG_COMPOUND);
			for (int j = Math.max(0, attacks.size() - REMEMBERED); j < attacks.size(); j++) {
				CompoundTag a = Nbt.compoundAt(attacks, j);
				list.add(new Past(Nbt.getLong(a, "day"), culture(a), Nbt.getInt(a, "came"), Nbt.getInt(a, "fell"), Nbt.getBoolean(a, "fled")));
			}
			history.put(BlockPos.of(Nbt.getLong(h, "hall")), list);
		}
		ListTag list = Nbt.getList(tag, "raids", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag r = Nbt.compoundAt(list, i);
			if (Nbt.has(r, "hall", Tag.TAG_LONG)) {
				Under raid = new Under(BlockPos.of(Nbt.getLong(r, "hall")), culture(r), Nbt.getInt(r, "raiders"), Nbt.getLong(r, "began"));
				raids.put(raid.hall(), raid);
			}
		}
		ListTag halls = Nbt.getList(tag, "clock", Tag.TAG_COMPOUND);
		for (int i = 0; i < halls.size(); i++) {
			CompoundTag c = Nbt.compoundAt(halls, i);
			if (!Nbt.has(c, "hall", Tag.TAG_LONG)) {
				continue;
			}
			Clock clock = new Clock();
			clock.rolled = Nbt.has(c, "rolled", Tag.TAG_LONG) ? Nbt.getLong(c, "rolled") : -1;
			ListTag attacks = Nbt.getList(c, "attacks", Tag.TAG_COMPOUND);
			for (int j = 0; j < attacks.size(); j++) {
				CompoundTag a = Nbt.compoundAt(attacks, j);
				long at = Nbt.has(a, "at", Tag.TAG_LONG) ? Nbt.getLong(a, "at") : 13500;
				clock.attacks.add(new Attack(Nbt.getLong(a, "day"), culture(a), Nbt.getDouble(a, "angle"), at));
			}
			clocks.put(BlockPos.of(Nbt.getLong(c, "hall")), clock);
		}
	}

	private static ResourceLocation culture(CompoundTag tag) {
		ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(tag, "culture"));
		return id == null || Nbt.getString(tag, "culture").isEmpty() ? Threats.MONSTERS : id;
	}

	/**
	 * Saves this and reads it back, as a restart does (tests): what comes back is what a world loaded now would hold.
	 */
	public void roundTrip(HolderLookup.Provider registries) {
		read(save(new CompoundTag(), registries));
	}
}
