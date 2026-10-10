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
