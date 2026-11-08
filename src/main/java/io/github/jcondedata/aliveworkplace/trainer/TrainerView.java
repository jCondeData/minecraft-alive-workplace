package io.github.jcondedata.aliveworkplace.trainer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What the Trainer screen shows (ROADMAP 43.1), worked out on the server when it opens and sent along with the menu: on
 * the left the trainer's name, title, rank, the player's record against them, today's line and the rules; on the right
 * the reward, the team (a hidden one has no icon), the player's party, and why Challenge is grayed out (empty: it isn't).
 */
public record TrainerView(int entityId, Component name, Component title, Component rank, Component record, Component today,
						  int todayColor, Component rules, Component reward, Component teamHeading, List<TeamSlot> team, Component teamHint,
						  List<PartyLine> party, Component partyNote, Component refusal, boolean canTrade) {
	/** Colors of the lines (the Hall's gold, and vanilla's green, red and orange). */
	public static final int GOLD = 0xFFFFD66B;
	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF5555;
	public static final int ORANGE = 0xFFFFAA00;
	public static final int GRAY = 0xFFAAAAAA;

	/** One of the trainer's Pokémon: its icon (empty: not seen yet), its level, and whether it came down to the level cap. */
	public record TeamSlot(ItemStack icon, int level, boolean scaled) {
		public boolean hidden() {
			return icon.isEmpty();
		}
	}

	/** A line of the player's party: the Pokémon's icon and name, level, and its state (ready, fainted, over the cap) in its color. */
	public record PartyLine(ItemStack icon, Component name, int level, Component state, int color) {
	}

	/** The Pokémon half, filled in by Cobblemon's side: the team, the party, and the player's RCT level cap (-1: none). */
	public record Sides(List<TeamSlot> team, List<PartyLine> party, int levelCap) {
		public static final Sides NONE = new Sides(List.of(), List.of(), -1);
	}

	public boolean canChallenge() {
		return refusal.getString().isEmpty();
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, TrainerView> STREAM_CODEC = StreamCodec.of((buf, v) -> {
		buf.writeVarInt(v.entityId());
		for (Component c : List.of(v.name(), v.title(), v.rank(), v.record(), v.today())) {
			ComponentSerialization.STREAM_CODEC.encode(buf, c);
		}
		buf.writeInt(v.todayColor());
		for (Component c : List.of(v.rules(), v.reward(), v.teamHeading())) {
			ComponentSerialization.STREAM_CODEC.encode(buf, c);
		}
		buf.writeVarInt(v.team().size());
		for (TeamSlot slot : v.team()) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, slot.icon());
			buf.writeVarInt(slot.level());
			buf.writeBoolean(slot.scaled());
		}
		ComponentSerialization.STREAM_CODEC.encode(buf, v.teamHint());
		buf.writeVarInt(v.party().size());
		for (PartyLine line : v.party()) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, line.icon());
			ComponentSerialization.STREAM_CODEC.encode(buf, line.name());
			buf.writeVarInt(line.level());
			ComponentSerialization.STREAM_CODEC.encode(buf, line.state());
			buf.writeInt(line.color());
		}
		ComponentSerialization.STREAM_CODEC.encode(buf, v.partyNote());
		ComponentSerialization.STREAM_CODEC.encode(buf, v.refusal());
		buf.writeBoolean(v.canTrade());
	}, buf -> {
		int id = buf.readVarInt();
		Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component rank = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component record = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component today = ComponentSerialization.STREAM_CODEC.decode(buf);
		int todayColor = buf.readInt();
		Component rules = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component reward = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component teamHeading = ComponentSerialization.STREAM_CODEC.decode(buf);
		int n = buf.readVarInt();
		List<TeamSlot> team = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			team.add(new TeamSlot(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean()));
		}
		Component teamHint = ComponentSerialization.STREAM_CODEC.decode(buf);
		int m = buf.readVarInt();
		List<PartyLine> party = new ArrayList<>();
		for (int i = 0; i < m; i++) {
			ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			Component pname = ComponentSerialization.STREAM_CODEC.decode(buf);
			int level = buf.readVarInt();
			Component state = ComponentSerialization.STREAM_CODEC.decode(buf);
			party.add(new PartyLine(icon, pname, level, state, buf.readInt()));
		}
		Component partyNote = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component refusal = ComponentSerialization.STREAM_CODEC.decode(buf);
		return new TrainerView(id, name, title, rank, record, today, todayColor, rules, reward, teamHeading, List.copyOf(team), teamHint,
			List.copyOf(party), partyNote, refusal, buf.readBoolean());
	});
}
