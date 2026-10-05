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

	/** A bout's result (the bouts come in 28.18): the round (1 = first), the winner and the loser. */
	public record Result(int round, UUID winner, UUID loser) {
	}

	/** A Cup won: on {@code day}, which theme, the champion's name and village. */
	public record Champion(long day, ResourceLocation theme, String name, BlockPos village) {
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
	}

	private final Map<BlockPos, Cup> cups = new LinkedHashMap<>();

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
				champions.add(ct);
			}
			t.put("champions", champions);
			list.add(t);
		});
		tag.put("cups", list);
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
					cup.champions.add(new Champion(Nbt.getLong(ct, "day"), theme, Nbt.getString(ct, "name"), BlockPos.of(Nbt.getLong(ct, "village"))));
				}
			}
		}
		return data;
	}
}
