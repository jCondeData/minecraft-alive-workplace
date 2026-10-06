package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * An open quest (ROADMAP 31.2): a snapshot of what its file asked and paid when it went up, so changing or removing the
 * file never changes it; how far each objective has got; who helped and how much; when it went up and when it's due.
 */
public final class Quest {
	public final UUID id;
	public final ResourceLocation file;
	public final String giver;
	public final Optional<Component> name;
	public final String poster;
	public final long posted;
	/** Game time it comes down; -1: never. */
	public final long due;
	public final List<Objectives.Objective> objectives;
	public final int[] progress;
	public final List<Rewards.Reward> rewards;
	public final Map<UUID, Integer> helpers = new LinkedHashMap<>();
	/** The player who last moved it on (paid when a {@code wait} finishes it in the round). */
	@Nullable
	public UUID last;
	/** The story arc that posted it (31.4), or null. */
	@Nullable
	public String arc;

	public Quest(UUID id, ResourceLocation file, String giver, Optional<Component> name, String poster, long posted, long due,
				 List<Objectives.Objective> objectives, int[] progress, List<Rewards.Reward> rewards) {
		this.id = id;
		this.file = file;
		this.giver = giver;
		this.name = name;
		this.poster = poster;
		this.posted = posted;
		this.due = due;
		this.objectives = List.copyOf(objectives);
		this.progress = progress.length == objectives.size() ? progress : java.util.Arrays.copyOf(progress, objectives.size());
		this.rewards = List.copyOf(rewards);
	}

	/** The objective being worked on now (the first one not done); -1 when all are. */
	public int current() {
		for (int i = 0; i < objectives.size(); i++) {
			if (progress[i] < objectives.get(i).need()) {
				return i;
			}
		}
		return -1;
	}

	@Nullable
	public Objectives.Objective currentObjective() {
		int i = current();
		return i < 0 ? null : objectives.get(i);
	}

	public boolean done() {
		return current() < 0;
	}

	/** What it's called: its file's name, else its first objective's line. */
	public Component title() {
		return name.orElseGet(() -> objectives.get(0).line());
	}

	public int emeralds() {
		return Rewards.emeralds(rewards);
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		Nbt.putUuid(tag, "id", id);
		tag.putString("file", file.toString());
		tag.putString("giver", giver);
		name.ifPresent(n -> tag.putString("name", Rewards.json(n).toString()));
		tag.putString("poster", poster);
		tag.putLong("posted", posted);
		if (due >= 0) {
			tag.putLong("due", due);
		}
		ListTag objs = new ListTag();
		for (int i = 0; i < objectives.size(); i++) {
			CompoundTag o = new CompoundTag();
			o.putString("objective", objectives.get(i).json().toString());
			o.putInt("progress", progress[i]);
			objs.add(o);
		}
		tag.put("objectives", objs);
		ListTag pays = new ListTag();
		for (Rewards.Reward r : rewards) {
			pays.add(net.minecraft.nbt.StringTag.valueOf(r.json().toString()));
		}
		tag.put("rewards", pays);
		CompoundTag help = new CompoundTag();
		helpers.forEach((k, v) -> help.putInt(k.toString(), v));
		tag.put("helpers", help);
		if (last != null) {
			Nbt.putUuid(tag, "last", last);
		}
		if (arc != null) {
			tag.putString("arc", arc);
		}
		return tag;
	}

	static Quest load(CompoundTag tag) {
		List<Objectives.Objective> objectives = new ArrayList<>();
		ListTag objs = Nbt.getList(tag, "objectives", Tag.TAG_COMPOUND);
		int[] progress = new int[objs.size()];
		for (int i = 0; i < objs.size(); i++) {
			CompoundTag o = Nbt.compoundAt(objs, i);
			progress[i] = Nbt.getInt(o, "progress");
			objectives.add(Objectives.parse(JsonParser.parseString(Nbt.getString(o, "objective")).getAsJsonObject()));
		}
		List<Rewards.Reward> rewards = new ArrayList<>();
		ListTag pays = Nbt.getList(tag, "rewards", Tag.TAG_STRING);
		for (int i = 0; i < pays.size(); i++) {
			rewards.add(Rewards.parse(JsonParser.parseString(Nbt.stringAt(pays, i)).getAsJsonObject()));
		}
		Optional<Component> name = Optional.empty();
		if (tag.contains("name")) {
			name = Optional.of(Rewards.text(JsonParser.parseString(Nbt.getString(tag, "name"))));
		}
		Quest quest = new Quest(Nbt.getUuid(tag, "id"), ResourceLocation.parse(Nbt.getString(tag, "file")), Nbt.getString(tag, "giver"), name,
			Nbt.getString(tag, "poster"), Nbt.getLong(tag, "posted"), tag.contains("due") ? Nbt.getLong(tag, "due") : -1, objectives, progress, rewards);
		CompoundTag help = Nbt.getCompound(tag, "helpers");
		for (String k : Nbt.keys(help)) {
			quest.helpers.put(UUID.fromString(k), Nbt.getInt(help, k));
		}
		if (Nbt.hasUuid(tag, "last")) {
			quest.last = Nbt.getUuid(tag, "last");
		}
		if (tag.contains("arc")) {
			quest.arc = Nbt.getString(tag, "arc");
		}
		return quest;
	}

	@Override
	public String toString() {
		return "Quest[" + file + " " + java.util.Arrays.toString(progress) + " of " + objectives + "]";
	}
}
