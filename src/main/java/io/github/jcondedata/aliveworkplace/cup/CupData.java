package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Festival Cups of one dimension (ROADMAP 28.17), saved as {@code aliveworkplace_cups}, by host hall: the next Cup's
 * theme and day, the players signed up, and once sign-up closes the entrants and bracket; the results (filled by the
 * bouts, 28.18) and the host's roll of champions. Every field has a default, so a host saved before a field existed
 * loads.
 */
public final class CupData extends SavedData {
	static final String NAME = "aliveworkplace_cups";

	/** What an entrant is: a circuit village's Trainer Leader or (without one) its best Trainer, a player, a host's Trainer. */
	public enum Kind {
		LEADER, TRAINER, PLAYER, HOST_TRAINER
	}

	/**
	 * Someone in the Cup: who ({@code id}), their name ({@code ""}: a villager without one, shown by title), tier (villagers
	 * 1-5; players 0), experience, and the village they stand for.
	 */
	public record Entrant(Kind kind, UUID id, String name, int tier, int xp, BlockPos village) {
		public Component display() {
			if (!name.isEmpty()) {
				return Component.literal(name);
			}
			return kind == Kind.LEADER ? Component.translatable("message.aliveworkplace.trainer.leader_title")
				: Component.translatable("message.aliveworkplace.trainer.title", io.github.jcondedata.aliveworkplace.build.BuilderLevels.levelName(tier));
		}
	}

	/** A player signed up for {@code village}. */
	public record Signup(UUID player, String name, BlockPos village) {
	}

	/** A bout's result (28.18): the round (1 = first), the winner and the loser. */
	public record Result(int round, UUID winner, UUID loser) {
	}

	/**
	 * A Cup won: on {@code day}, which theme, the champion's name and village, and who it was (null in saves from before
	 * 28.21), for the defending champion's seed.
	 */
	public record Champion(long day, ResourceLocation theme, String name, BlockPos village, @Nullable UUID id) {
		public Champion(long day, ResourceLocation theme, String name, BlockPos village) {
			this(day, theme, name, village, null);
		}
	}

	/** A host's Cup. */
	public static final class Cup {
		@Nullable
		public ResourceLocation theme;
		/** The theme of the Cup before (the next comes after it in the order). */
		@Nullable
		public ResourceLocation lastTheme;
		/** Whether the host's owner picked this theme (it isn't simply the next in order). */
		public boolean themePicked;
		/** The day of the Cup (a festival day), -1 before the first round. */
		public long day = -1;
		/** Sign-up has closed: the entrants and the bracket are drawn. */
		public boolean closed;
		/** Why there's no Cup this time (a lang key), "" if there is one. */
		public String noCup = "";
		public long toldOpen = -1;
		public long toldEvening = -1;
		public final List<Signup> signups = new ArrayList<>();
		public final List<Entrant> entrants = new ArrayList<>();
		/** The bracket's slots, in order (slot 2i meets 2i+1); null: a bye. */
		public final List<Entrant> bracket = new ArrayList<>();
		public final List<Result> results = new ArrayList<>();
		public final List<Champion> champions = new ArrayList<>();
		/** The bout being fought at the ring (28.18), or null. */
		@Nullable
		public CupBout bout;
		/** The bout with a player called at the ring (28.20), or null. */
		@Nullable
		public CupMatches.Call call;
		/** The Cup's day (28.19): how many days it has been put off (its host wasn't loaded that morning). */
		public int postponed;
		/** The days (28.19) the delegates came, the fair was held, the feast and the bard's disc, and the champion was cheered; -1: not yet. */
		public long delegatesDay = -1;
		public long fairDay = -1;
		public long feastDay = -1;
		public long finaleDay = -1;
		/** When (time of day) the champion was cheered, for the stands to empty after. */
		public long finaleTime = -1;
		/** How many of the results the stands have cheered. */
		public int cheered;
	}

	/** A chronicle entry for a circuit village that wasn't loaded (28.19): the day and the entry, written when it next loads. */
	public record Note(long day, String json) {
	}

	private final Map<BlockPos, List<Note>> notes = new LinkedHashMap<>();

	/** Keeps {@code note} for the village round {@code hall} until it loads. */
	public void note(BlockPos hall, Note note) {
		notes.computeIfAbsent(hall.immutable(), k -> new ArrayList<>()).add(note);
		setDirty();
	}

	/** Takes the notes kept for {@code hall}. */
	public List<Note> takeNotes(BlockPos hall) {
		List<Note> out = notes.remove(hall);
		if (out != null) {
			setDirty();
		}
		return out == null ? List.of() : out;
	}

	public List<Note> notes(BlockPos hall) {
		return List.copyOf(notes.getOrDefault(hall, List.of()));
	}

	private final Map<BlockPos, Cup> cups = new LinkedHashMap<>();

	/** The Cup banners flying (28.21), by the hall of the village that holds the Cup: where they were put up. */
	private final Map<BlockPos, List<BlockPos>> banners = new LinkedHashMap<>();

	/** Where the Cup banners of the village round {@code hall} were put up (empty: none fly). */
	public List<BlockPos> banners(BlockPos hall) {
		return List.copyOf(banners.getOrDefault(hall, List.of()));
	}

	/** Records the Cup banners of the village round {@code hall} (none: forgets them). */
	public void setBanners(BlockPos hall, List<BlockPos> at) {
		if (at.isEmpty()) {
			if (banners.remove(hall) == null) {
				return;
			}
		} else {
			banners.put(hall.immutable(), at.stream().map(BlockPos::immutable).toList());
		}
		setDirty();
	}

	public static CupData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CupData::new, CupData::load, null), NAME);
	}

	/** The host's Cup, made if it has none. */
	public Cup cup(BlockPos host) {
		return cups.computeIfAbsent(host.immutable(), k -> new Cup());
	}

	@Nullable
	public Cup existing(BlockPos host) {
		return cups.get(host);
	}

	public Map<BlockPos, Cup> all() {
		return Map.copyOf(cups);
	}

	public void forget(BlockPos host) {
		if (cups.remove(host) != null) {
			setDirty();
		}
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		cups.forEach((host, cup) -> {
			CompoundTag t = new CompoundTag();
			t.putLong("host", host.asLong());
			if (cup.theme != null) {
				t.putString("theme", cup.theme.toString());
			}
			if (cup.lastTheme != null) {
				t.putString("lastTheme", cup.lastTheme.toString());
			}
			t.putBoolean("themePicked", cup.themePicked);
			t.putLong("day", cup.day);
			t.putBoolean("closed", cup.closed);
			t.putString("noCup", cup.noCup);
			t.putLong("toldOpen", cup.toldOpen);
			t.putLong("toldEvening", cup.toldEvening);
			ListTag signups = new ListTag();
			for (Signup s : cup.signups) {
				CompoundTag st = new CompoundTag();
				st.putUUID("player", s.player());
				st.putString("name", s.name());
				st.putLong("village", s.village().asLong());
				signups.add(st);
			}
			t.put("signups", signups);
			t.put("entrants", entrants(cup.entrants));
			t.put("bracket", entrants(cup.bracket));
			ListTag results = new ListTag();
			for (Result r : cup.results) {
				CompoundTag rt = new CompoundTag();
				rt.putInt("round", r.round());
				rt.putUUID("winner", r.winner());
				rt.putUUID("loser", r.loser());
				results.add(rt);
			}
			t.put("results", results);
			ListTag champions = new ListTag();
			for (Champion c : cup.champions) {
				CompoundTag ct = new CompoundTag();
				ct.putLong("day", c.day());
				ct.putString("theme", c.theme().toString());
				ct.putString("name", c.name());
				ct.putLong("village", c.village().asLong());
				if (c.id() != null) {
					ct.putUUID("id", c.id());
				}
				champions.add(ct);
			}
			t.put("champions", champions);
			if (cup.bout != null) {
				t.put("bout", cup.bout.save());
			}
			if (cup.call != null) {
				t.put("call", cup.call.save());
			}
			t.putInt("postponed", cup.postponed);
			t.putLong("delegatesDay", cup.delegatesDay);
			t.putLong("fairDay", cup.fairDay);
			t.putLong("feastDay", cup.feastDay);
			t.putLong("finaleDay", cup.finaleDay);
			t.putLong("finaleTime", cup.finaleTime);
			t.putInt("cheered", cup.cheered);
			list.add(t);
		});
		tag.put("cups", list);
		ListTag noteList = new ListTag();
		notes.forEach((hall, kept) -> kept.forEach(n -> {
			CompoundTag nt = new CompoundTag();
			nt.putLong("hall", hall.asLong());
			nt.putLong("day", n.day());
			nt.putString("text", n.json());
			noteList.add(nt);
		}));
		tag.put("notes", noteList);
		ListTag bannerList = new ListTag();
		banners.forEach((hall, at) -> at.forEach(pos -> {
			CompoundTag bt = new CompoundTag();
			bt.putLong("hall", hall.asLong());
			bt.putLong("pos", pos.asLong());
			bannerList.add(bt);
		}));
		tag.put("banners", bannerList);
		return tag;
	}

	private static ListTag entrants(List<Entrant> entrants) {
		ListTag list = new ListTag();
		for (Entrant e : entrants) {
			CompoundTag t = new CompoundTag();
			if (e == null) {
				t.putBoolean("bye", true);
			} else {
				t.putString("kind", e.kind().name());
				t.putUUID("id", e.id());
				t.putString("name", e.name());
				t.putInt("tier", e.tier());
				t.putInt("xp", e.xp());
				t.putLong("village", e.village().asLong());
			}
			list.add(t);
		}
		return list;
	}

	private static List<Entrant> entrants(ListTag list) {
		List<Entrant> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = Nbt.compoundAt(list, i);
			if (Nbt.getBoolean(t, "bye") || !t.hasUUID("id")) {
				out.add(null);
				continue;
			}
			Kind kind;
			try {
				kind = Kind.valueOf(Nbt.getString(t, "kind"));
			} catch (IllegalArgumentException e) {
				kind = Kind.TRAINER;
			}
			out.add(new Entrant(kind, t.getUUID("id"), Nbt.getString(t, "name"), Nbt.getInt(t, "tier"), Nbt.getInt(t, "xp"),
				BlockPos.of(Nbt.getLong(t, "village"))));
		}
		return out;
	}

	@Nullable
	private static ResourceLocation id(String text) {
		return text.isEmpty() ? null : ResourceLocation.tryParse(text);
	}

	public static CupData load(CompoundTag tag, HolderLookup.Provider registries) {
		CupData data = new CupData();
		ListTag list = Nbt.getList(tag, "cups", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = Nbt.compoundAt(list, i);
			Cup cup = data.cup(BlockPos.of(Nbt.getLong(t, "host")));
			cup.theme = id(Nbt.getString(t, "theme"));
			cup.lastTheme = id(Nbt.getString(t, "lastTheme"));
			cup.themePicked = Nbt.getBoolean(t, "themePicked");
			cup.day = t.contains("day") ? Nbt.getLong(t, "day") : -1;
			cup.closed = Nbt.getBoolean(t, "closed");
			cup.noCup = Nbt.getString(t, "noCup");
			cup.toldOpen = t.contains("toldOpen") ? Nbt.getLong(t, "toldOpen") : -1;
			cup.toldEvening = t.contains("toldEvening") ? Nbt.getLong(t, "toldEvening") : -1;
			ListTag signups = Nbt.getList(t, "signups", Tag.TAG_COMPOUND);
			for (int j = 0; j < signups.size(); j++) {
				CompoundTag st = Nbt.compoundAt(signups, j);
				if (st.hasUUID("player")) {
					cup.signups.add(new Signup(st.getUUID("player"), Nbt.getString(st, "name"), BlockPos.of(Nbt.getLong(st, "village"))));
				}
			}
			cup.entrants.addAll(entrants(Nbt.getList(t, "entrants", Tag.TAG_COMPOUND)).stream().filter(java.util.Objects::nonNull).toList());
			cup.bracket.addAll(entrants(Nbt.getList(t, "bracket", Tag.TAG_COMPOUND)));
			ListTag results = Nbt.getList(t, "results", Tag.TAG_COMPOUND);
			for (int j = 0; j < results.size(); j++) {
				CompoundTag rt = Nbt.compoundAt(results, j);
				if (rt.hasUUID("winner") && rt.hasUUID("loser")) {
					cup.results.add(new Result(Nbt.getInt(rt, "round"), rt.getUUID("winner"), rt.getUUID("loser")));
				}
			}
			ListTag champions = Nbt.getList(t, "champions", Tag.TAG_COMPOUND);
			for (int j = 0; j < champions.size(); j++) {
				CompoundTag ct = Nbt.compoundAt(champions, j);
				ResourceLocation theme = id(Nbt.getString(ct, "theme"));
				if (theme != null) {
					cup.champions.add(new Champion(Nbt.getLong(ct, "day"), theme, Nbt.getString(ct, "name"), BlockPos.of(Nbt.getLong(ct, "village")),
						ct.hasUUID("id") ? ct.getUUID("id") : null)); // 28.21; older saves have no id
				}
			}
			if (t.contains("bout")) { // 28.18; older saves have none
				cup.bout = CupBout.load(Nbt.getCompound(t, "bout"));
			}
			if (t.contains("call")) { // 28.20; older saves have none
				cup.call = CupMatches.Call.load(Nbt.getCompound(t, "call"));
			}
			// 28.19; older saves have none
			cup.postponed = Nbt.getInt(t, "postponed");
			cup.delegatesDay = t.contains("delegatesDay") ? Nbt.getLong(t, "delegatesDay") : -1;
			cup.fairDay = t.contains("fairDay") ? Nbt.getLong(t, "fairDay") : -1;
			cup.feastDay = t.contains("feastDay") ? Nbt.getLong(t, "feastDay") : -1;
			cup.finaleDay = t.contains("finaleDay") ? Nbt.getLong(t, "finaleDay") : -1;
			cup.finaleTime = t.contains("finaleTime") ? Nbt.getLong(t, "finaleTime") : -1;
			cup.cheered = Nbt.getInt(t, "cheered");
		}
		ListTag noteList = Nbt.getList(tag, "notes", Tag.TAG_COMPOUND);
		for (int i = 0; i < noteList.size(); i++) {
			CompoundTag nt = Nbt.compoundAt(noteList, i);
			data.notes.computeIfAbsent(BlockPos.of(Nbt.getLong(nt, "hall")), k -> new ArrayList<>()).add(new Note(Nbt.getLong(nt, "day"), Nbt.getString(nt, "text")));
		}
		ListTag bannerList = Nbt.getList(tag, "banners", Tag.TAG_COMPOUND); // 28.21; older saves have none
		for (int i = 0; i < bannerList.size(); i++) {
			CompoundTag bt = Nbt.compoundAt(bannerList, i);
			List<BlockPos> at = new ArrayList<>(data.banners.getOrDefault(BlockPos.of(Nbt.getLong(bt, "hall")), List.of()));
			at.add(BlockPos.of(Nbt.getLong(bt, "pos")));
			data.banners.put(BlockPos.of(Nbt.getLong(bt, "hall")), List.copyOf(at));
		}
		return data;
	}
}
