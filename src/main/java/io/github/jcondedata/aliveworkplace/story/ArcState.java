package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A running story arc in one village (ROADMAP 31.4), saved in {@code aliveworkplace_stories} with the hall's entry: the
 * arc, its chapter, the days it began and the chapter began, its flags, roles, spawned mobs and placed spots, who helped,
 * and the chapter's quests. Everything the arc does next is worked out from this, so it carries on after a restart at any
 * tick. Every field is optional when read, with a default.
 */
public final class ArcState {
	/** The arc's file id ({@code aliveworkplace:bandit_king}). */
	public final String id;
	/** The chapter now (0 for the first). */
	public int chapter;
	/** Whether the chapter has begun (its intro told, its quests posted); false while waiting out the delay before it. */
	public boolean started;
	public long beganDay;
	public long chapterDay;
	/** The day the next chapter begins (when not {@link #started}). */
	public long nextChapterDay;
	/** Flag → the last day it holds ({@link #FOREVER}: until the arc ends). */
	public final Map<String, Long> flags = new LinkedHashMap<>();
	/** Role → who plays it. */
	public final Map<String, Role> roles = new LinkedHashMap<>();
	public final List<ArcMob> mobs = new ArrayList<>();
	public final List<ArcPlace> places = new ArrayList<>();
	/** Everyone credited with progress on the arc's quests, and on this chapter's. */
	public final Map<UUID, Integer> helpers = new LinkedHashMap<>();
	public final Map<UUID, Integer> chapterHelpers = new LinkedHashMap<>();
	/** The chapter's quests (posted when it began), and which of them were finished. */
	public final List<ChapterQuest> quests = new ArrayList<>();
	public final Set<UUID> done = new LinkedHashSet<>();
	/** Chatter lines (lang keys) villagers say while the chapter runs. */
	public final List<String> chatter = new ArrayList<>();
	/** The player who last moved the arc on (rewards given as effects go to them, if online). */
	@Nullable
	public UUID last;
	/** Set by an {@code end_arc} effect: the arc ends this way once the effects have run (not saved: it ends at once). */
	@Nullable
	String endWith;

	public static final long FOREVER = Long.MAX_VALUE;

	public ArcState(String id) {
		this.id = id;
	}

	/** Who plays a role: a mob or villager by UUID (empty until spawned or chosen), and the name shown for it. */
	public record Role(Optional<UUID> who, Component name) {
	}

	/** A quest the chapter posted: its id and the file (or inline id) it came from. */
	public record ChapterQuest(UUID id, String file) {
	}

	/**
	 * A mob an arc spawned or is to spawn: its key (and role), where it stands ({@code at} a place key, or empty for the
	 * hall, with its spot once known), the spawn written out as JSON, its UUID once spawned, and whether it died (then it
	 * isn't put back).
	 */
	public static final class ArcMob {
		public final String key;
		public final String at;
		public final JsonObject spec;
		@Nullable
		public BlockPos spot;
		@Nullable
		public UUID uuid;
		public boolean dead;

		public ArcMob(String key, String at, JsonObject spec, @Nullable BlockPos spot) {
			this.key = key;
			this.at = at;
			this.spec = spec;
			this.spot = spot;
		}
	}

	/**
	 * A spot an arc builds on: its key, the column chosen for it (its height found when it's placed), the templates
	 * (one, or stages placed one a dawn), how many are placed, the rotation, the day the next stage may go up, and whether
	 * placing it was given up (no clear ground found).
	 */
	public static final class ArcPlace {
		public final String key;
		public BlockPos spot;
		public final List<String> stages;
		public int placed;
		public int rotation;
		public long nextDay;
		public boolean skipped;
		/** The placed build's corner (its origin), once the first stage is down. */
		@Nullable
		public BlockPos origin;

		public ArcPlace(String key, BlockPos spot, List<String> stages, int rotation) {
			this.key = key;
			this.spot = spot;
			this.stages = List.copyOf(stages);
			this.rotation = rotation;
		}

		public boolean waiting() {
			return !skipped && placed < stages.size();
		}

		/** Where things stand on it: the middle of the placed build, else the chosen column. */
		public BlockPos middle() {
			return spot;
		}
	}

	public boolean flag(String name, long today) {
		Long until = flags.get(name);
		return until != null && until >= today;
	}

	@Nullable
	public ArcPlace place(String key) {
		for (ArcPlace p : places) {
			if (p.key.equals(key)) {
				return p;
			}
		}
		return null;
	}

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putString("id", id);
		t.putInt("chapter", chapter);
		t.putBoolean("started", started);
		t.putLong("began_day", beganDay);
		t.putLong("chapter_day", chapterDay);
		t.putLong("next_chapter_day", nextChapterDay);
		CompoundTag f = new CompoundTag();
		flags.forEach((k, v) -> f.putLong(k, v));
		t.put("flags", f);
		CompoundTag r = new CompoundTag();
		roles.forEach((k, v) -> {
			CompoundTag role = new CompoundTag();
			v.who().ifPresent(u -> Nbt.putUuid(role, "uuid", u));
			role.putString("name", Rewards.json(v.name()).toString());
			r.put(k, role);
		});
		t.put("roles", r);
		ListTag ms = new ListTag();
		for (ArcMob m : mobs) {
			CompoundTag c = new CompoundTag();
			c.putString("key", m.key);
			c.putString("at", m.at);
			c.putString("spawn", m.spec.toString());
			if (m.spot != null) {
				c.putLong("spot", m.spot.asLong());
			}
			if (m.uuid != null) {
				Nbt.putUuid(c, "uuid", m.uuid);
			}
			c.putBoolean("dead", m.dead);
			ms.add(c);
		}
		t.put("mobs", ms);
		ListTag ps = new ListTag();
		for (ArcPlace p : places) {
			CompoundTag c = new CompoundTag();
			c.putString("key", p.key);
			c.putLong("spot", p.spot.asLong());
			ListTag stages = new ListTag();
			p.stages.forEach(s -> stages.add(StringTag.valueOf(s)));
			c.put("stages", stages);
			c.putInt("placed", p.placed);
			c.putInt("rotation", p.rotation);
			c.putLong("next_day", p.nextDay);
			c.putBoolean("skipped", p.skipped);
			if (p.origin != null) {
				c.putLong("origin", p.origin.asLong());
			}
			ps.add(c);
		}
		t.put("places", ps);
		t.put("helpers", helpers(helpers));
		t.put("chapter_helpers", helpers(chapterHelpers));
		ListTag qs = new ListTag();
		for (ChapterQuest q : quests) {
			CompoundTag c = new CompoundTag();
			Nbt.putUuid(c, "id", q.id());
			c.putString("file", q.file());
			c.putBoolean("done", done.contains(q.id()));
			qs.add(c);
		}
		t.put("quests", qs);
		ListTag ch = new ListTag();
		chatter.forEach(s -> ch.add(StringTag.valueOf(s)));
		t.put("chatter", ch);
		if (last != null) {
			Nbt.putUuid(t, "last", last);
		}
		return t;
	}

	private static CompoundTag helpers(Map<UUID, Integer> map) {
		CompoundTag h = new CompoundTag();
		map.forEach((k, v) -> h.putInt(k.toString(), v));
		return h;
	}

	private static void helpers(CompoundTag h, Map<UUID, Integer> into) {
		for (String k : Nbt.keys(h)) {
			into.put(UUID.fromString(k), Nbt.getInt(h, k));
		}
	}

	static ArcState load(CompoundTag t) {
		ArcState s = new ArcState(Nbt.getString(t, "id"));
		s.chapter = Nbt.getInt(t, "chapter");
		s.started = Nbt.getBoolean(t, "started");
		s.beganDay = Nbt.getLong(t, "began_day");
		s.chapterDay = Nbt.getLong(t, "chapter_day");
		s.nextChapterDay = Nbt.getLong(t, "next_chapter_day");
		CompoundTag f = Nbt.getCompound(t, "flags");
		for (String k : Nbt.keys(f)) {
			s.flags.put(k, Nbt.getLong(f, k));
		}
		CompoundTag r = Nbt.getCompound(t, "roles");
		for (String k : Nbt.keys(r)) {
			CompoundTag role = Nbt.getCompound(r, k);
			Component name = role.contains("name") ? Rewards.text(JsonParser.parseString(Nbt.getString(role, "name"))) : Component.literal(k);
			s.roles.put(k, new Role(Nbt.hasUuid(role, "uuid") ? Optional.of(Nbt.getUuid(role, "uuid")) : Optional.empty(), name));
		}
		ListTag ms = Nbt.getList(t, "mobs", Tag.TAG_COMPOUND);
		for (int i = 0; i < ms.size(); i++) {
			CompoundTag c = Nbt.compoundAt(ms, i);
			ArcMob m = new ArcMob(Nbt.getString(c, "key"), Nbt.getString(c, "at"), JsonParser.parseString(Nbt.getString(c, "spawn")).getAsJsonObject(),
				c.contains("spot") ? BlockPos.of(Nbt.getLong(c, "spot")) : null);
			m.uuid = Nbt.hasUuid(c, "uuid") ? Nbt.getUuid(c, "uuid") : null;
			m.dead = Nbt.getBoolean(c, "dead");
			s.mobs.add(m);
		}
		ListTag ps = Nbt.getList(t, "places", Tag.TAG_COMPOUND);
		for (int i = 0; i < ps.size(); i++) {
			CompoundTag c = Nbt.compoundAt(ps, i);
			List<String> stages = new ArrayList<>();
			ListTag l = Nbt.getList(c, "stages", Tag.TAG_STRING);
			for (int j = 0; j < l.size(); j++) {
				stages.add(Nbt.stringAt(l, j));
			}
			ArcPlace p = new ArcPlace(Nbt.getString(c, "key"), BlockPos.of(Nbt.getLong(c, "spot")), stages, Nbt.getInt(c, "rotation"));
			p.placed = Nbt.getInt(c, "placed");
			p.nextDay = Nbt.getLong(c, "next_day");
			p.skipped = Nbt.getBoolean(c, "skipped");
			p.origin = c.contains("origin") ? BlockPos.of(Nbt.getLong(c, "origin")) : null;
			s.places.add(p);
		}
		helpers(Nbt.getCompound(t, "helpers"), s.helpers);
		helpers(Nbt.getCompound(t, "chapter_helpers"), s.chapterHelpers);
		ListTag qs = Nbt.getList(t, "quests", Tag.TAG_COMPOUND);
		for (int i = 0; i < qs.size(); i++) {
			CompoundTag c = Nbt.compoundAt(qs, i);
			ChapterQuest q = new ChapterQuest(Nbt.getUuid(c, "id"), Nbt.getString(c, "file"));
			s.quests.add(q);
			if (Nbt.getBoolean(c, "done")) {
				s.done.add(q.id());
			}
		}
		ListTag ch = Nbt.getList(t, "chatter", Tag.TAG_STRING);
		for (int i = 0; i < ch.size(); i++) {
			s.chatter.add(Nbt.stringAt(ch, i));
		}
		s.last = Nbt.hasUuid(t, "last") ? Nbt.getUuid(t, "last") : null;
		return s;
	}
}
