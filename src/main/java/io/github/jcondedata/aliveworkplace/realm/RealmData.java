package io.github.jcondedata.aliveworkplace.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The server-wide saved file {@code aliveworkplace_realms} (design note M33, "Save data"), kept in the overworld's data
 * storage so it is one file for the server; villages are named by dimension and hall position. ROADMAP 33.8 starts it
 * with the colonies' part: the orders on the road, the colonies founded, and each mother village's count and last
 * colony day. 33.11 adds the realms themselves ({@link Realm}) and what the halls' rounds last saw of every village
 * ({@link Known}: name, owner, rank, villagers, wellbeing), so a realm's tab reads its members without loading them.
 * 33.12 adds the invitations to other players' villages ({@link Invite}) and the capital votes ({@link Vote}).
 * Everything is empty by default, so an older world loads unchanged; a part that can't be read is left empty and logged,
 * never a crash.
 */
public final class RealmData extends SavedData {
	private static final String NAME = "aliveworkplace_realms";

	/** A colony order's states, in the order they pass (33.9). */
	public static final String GATHERING = "gathering";
	public static final String ON_ROAD = "on_road";

	/** Between the two (33.9): the settlers are gathering at the hall, then walking out of the village. */
	public static final String MUSTER = "muster";
	public static final String LEAVING = "leaving";

	/**
	 * After the road (33.10): the spot's chunks are loading and the settlers look for open ground ({@code arrives} is then
	 * when they got there); or, with no room, they are on their way home, back when the mother village is next awake.
	 */
	public static final String ARRIVING = "arriving";
	public static final String RETURNING = "returning";

	/**
	 * A colony order: the mother village, the spot, the colony's name (none: one is made up on arrival), where it has got
	 * to, when the settlers leave and arrive (game time; 0 until known) and what was paid, in hundredths of an emerald,
	 * for a refund. 33.9 adds, each with an empty default: when it was given, who volunteered (while they still walk the
	 * village) and which of them is the colony's builder, the supplies taken out of the Storehouses, and the settlers
	 * themselves once on the road, saved the way a grave keeps a villager.
	 */
	public record Order(GlobalPos mother, BlockPos spot, Optional<Component> name, String state, long leaves, long arrives, int cost,
						long ordered, List<UUID> volunteers, Optional<UUID> builder, Map<Item, Integer> supplies, List<CompoundTag> settlers) {
		public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("mother").forGetter(Order::mother),
			BlockPos.CODEC.fieldOf("spot").forGetter(Order::spot),
			ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Order::name),
			Codec.STRING.optionalFieldOf("state", GATHERING).forGetter(Order::state),
			Codec.LONG.optionalFieldOf("leaves", 0L).forGetter(Order::leaves),
			Codec.LONG.optionalFieldOf("arrives", 0L).forGetter(Order::arrives),
			Codec.INT.optionalFieldOf("cost", 0).forGetter(Order::cost),
			Codec.LONG.optionalFieldOf("ordered", 0L).forGetter(Order::ordered),
			UUIDUtil.CODEC.listOf().optionalFieldOf("volunteers", List.of()).forGetter(Order::volunteers),
			UUIDUtil.CODEC.optionalFieldOf("builder").forGetter(Order::builder),
			Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT).optionalFieldOf("supplies", Map.of()).forGetter(Order::supplies),
			CompoundTag.CODEC.listOf().optionalFieldOf("settlers", List.of()).forGetter(Order::settlers)
		).apply(i, Order::new));

		/** An order as 33.8 knew it: nobody chosen, nothing taken. */
		public Order(GlobalPos mother, BlockPos spot, Optional<Component> name, String state, long leaves, long arrives, int cost) {
			this(mother, spot, name, state, leaves, arrives, cost, 0L, List.of(), Optional.empty(), Map.of(), List.of());
		}

		public Order withState(String state, long leaves, long arrives) {
			return new Order(mother, spot, name, state, leaves, arrives, cost, ordered, volunteers, builder, supplies, settlers);
		}

		public Order withVolunteers(List<UUID> volunteers) {
			return new Order(mother, spot, name, state, leaves, arrives, cost, ordered, List.copyOf(volunteers), builder, supplies, settlers);
		}

		public Order withSupplies(Map<Item, Integer> supplies) {
			return new Order(mother, spot, name, state, leaves, arrives, cost, ordered, volunteers, builder, Map.copyOf(supplies), settlers);
		}

		public Order withSettlers(List<CompoundTag> settlers) {
			return new Order(mother, spot, name, state, leaves, arrives, cost, ordered, volunteers, builder, supplies, List.copyOf(settlers));
		}

		/** Whether the settlers have left the village (the order can't be called off any more). */
		public boolean gone() {
			return state.equals(LEAVING) || state.equals(ON_ROAD) || state.equals(ARRIVING) || state.equals(RETURNING);
		}
	}

	/** A colony founded: its mother village, its own hall and the day. */
	public record Founded(GlobalPos mother, GlobalPos hall, long day) {
		public static final Codec<Founded> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("mother").forGetter(Founded::mother),
			GlobalPos.CODEC.fieldOf("hall").forGetter(Founded::hall),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Founded::day)
		).apply(i, Founded::new));
	}

	/** A mother village's count: the day of its last colony and how many it has founded (kept when a colony's hall is lost). */
	public record Cooldown(GlobalPos mother, long lastDay, int founded) {
		public static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("mother").forGetter(Cooldown::mother),
			Codec.LONG.optionalFieldOf("lastDay", -1L).forGetter(Cooldown::lastDay),
			Codec.INT.optionalFieldOf("founded", 0).forGetter(Cooldown::founded)
		).apply(i, Cooldown::new));
	}

	/** A realm's arms (33.11): a banner design, as the hall keeps a village's colours. */
	public static final Codec<VillageBanners.Colours> ARMS_CODEC = RecordCodecBuilder.create(i -> i.group(
		DyeColor.CODEC.fieldOf("base").forGetter(VillageBanners.Colours::base),
		BannerPatternLayers.CODEC.optionalFieldOf("patterns", BannerPatternLayers.EMPTY).forGetter(VillageBanners.Colours::patterns)
	).apply(i, VillageBanners.Colours::new));

	/** A village in a realm (33.11): its hall and the day it joined. */
	public record Member(GlobalPos hall, long joined) {
		public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("hall").forGetter(Member::hall),
			Codec.LONG.optionalFieldOf("joined", 0L).forGetter(Member::joined)
		).apply(i, Member::new));
	}

	/**
	 * A movement in a realm's treasury (33.13): the day (as the chronicle counts them), what went in (positive) or out
	 * (negative), in hundredths of an emerald, and why ("Levy from Thornholm", "Given by Alex", a spending's reason).
	 */
	public record Movement(long day, long cents, Component reason) {
		public static final Codec<Movement> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Movement::day),
			Codec.LONG.optionalFieldOf("cents", 0L).forGetter(Movement::cents),
			ComponentSerialization.CODEC.optionalFieldOf("reason", Component.empty()).forGetter(Movement::reason)
		).apply(i, Movement::new));
	}

	/**
	 * A realm (33.11): its id, its own name (a Name Tag's; absent: the capital's), the capital, the members (the capital
	 * first), the arms (absent until a banner sets them), the rank last counted (a {@code Realms.Rank} name, so a rank up
	 * is noticed once) and the day it was founded. 33.13 adds the treasury: the levy in percent (10 by default), what it
	 * holds in hundredths of an emerald (none by default) and its last movements, newest last. Later items add their keys
	 * (research, edicts...) with defaults.
	 */
	public record Realm(UUID id, Optional<Component> name, GlobalPos capital, List<Member> members, Optional<VillageBanners.Colours> arms, String rank,
						long founded, int levy, long treasury, List<Movement> ledger) {
		/** The levy a realm starts with, and an older file's realms (33.13). */
		public static final int DEFAULT_LEVY = 10;

		public static final Codec<Realm> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Realm::id),
			ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Realm::name),
			GlobalPos.CODEC.fieldOf("capital").forGetter(Realm::capital),
			Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Realm::members),
			ARMS_CODEC.optionalFieldOf("arms").forGetter(Realm::arms),
			Codec.STRING.optionalFieldOf("rank", "barony").forGetter(Realm::rank),
			Codec.LONG.optionalFieldOf("founded", 0L).forGetter(Realm::founded),
			Codec.INT.optionalFieldOf("levy", DEFAULT_LEVY).forGetter(Realm::levy),
			Codec.LONG.optionalFieldOf("treasury", 0L).forGetter(Realm::treasury),
			Movement.CODEC.listOf().optionalFieldOf("ledger", List.of()).forGetter(Realm::ledger)
		).apply(i, Realm::new));

		public Realm {
			members = List.copyOf(members);
			ledger = List.copyOf(ledger);
		}

		/** A realm as 33.11 knew it: the default levy, an empty treasury. */
		public Realm(UUID id, Optional<Component> name, GlobalPos capital, List<Member> members, Optional<VillageBanners.Colours> arms, String rank,
					 long founded) {
			this(id, name, capital, members, arms, rank, founded, DEFAULT_LEVY, 0L, List.of());
		}

		/** Whether {@code hall} is one of its villages. */
		public boolean has(GlobalPos hall) {
			return members.stream().anyMatch(m -> m.hall().equals(hall));
		}

		public Realm withName(Optional<Component> name) {
			return new Realm(id, name, capital, members, arms, rank, founded, levy, treasury, ledger);
		}

		public Realm withCapital(GlobalPos capital) {
			return new Realm(id, name, capital, members, arms, rank, founded, levy, treasury, ledger);
		}

		public Realm withMembers(List<Member> members) {
			return new Realm(id, name, capital, members, arms, rank, founded, levy, treasury, ledger);
		}

		public Realm withArms(Optional<VillageBanners.Colours> arms) {
			return new Realm(id, name, capital, members, arms, rank, founded, levy, treasury, ledger);
		}

		public Realm withRank(String rank) {
			return new Realm(id, name, capital, members, arms, rank, founded, levy, treasury, ledger);
		}

		public Realm withLevy(int levy) {
			return new Realm(id, name, capital, members, arms, rank, founded, levy, treasury, ledger);
		}

		public Realm withTreasury(long treasury, List<Movement> ledger) {
			return new Realm(id, name, capital, members, arms, rank, founded, levy, treasury, ledger);
		}
	}

	/**
	 * What a hall's last round saw of its village (33.11): its name, owner (and the owner's name), rank, villagers,
	 * wellbeing in percent, and when. A realm's tab and its rank count read these for villages that aren't loaded.
	 */
	public record Known(GlobalPos hall, Component name, Optional<UUID> owner, String ownerName, String rank, int villagers, int wellbeing, long seen) {
		public static final Codec<Known> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("hall").forGetter(Known::hall),
			ComponentSerialization.CODEC.optionalFieldOf("name", Component.empty()).forGetter(Known::name),
			UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Known::owner),
			Codec.STRING.optionalFieldOf("ownerName", "").forGetter(Known::ownerName),
			Codec.STRING.optionalFieldOf("rank", "hamlet").forGetter(Known::rank),
			Codec.INT.optionalFieldOf("villagers", 0).forGetter(Known::villagers),
			Codec.INT.optionalFieldOf("wellbeing", 0).forGetter(Known::wellbeing),
			Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Known::seen)
		).apply(i, Known::new));

		/** The same, ignoring when it was seen (whether a round has anything new to save). */
		boolean sameAs(Known other) {
			return hall.equals(other.hall) && name.equals(other.name) && owner.equals(other.owner) && ownerName.equals(other.ownerName)
				&& rank.equals(other.rank) && villagers == other.villagers && wellbeing == other.wellbeing;
		}
	}

	/**
	 * An invitation (33.12): the realm's capital asked the village at {@code village}, owned by {@code owner} (another
	 * player), to join, at game time {@code sent}. It is answered on the village's Realm tab, or lapses after
	 * {@code RealmInvites.LAPSE}.
	 */
	public record Invite(UUID realm, GlobalPos village, UUID owner, long sent) {
		public static final Codec<Invite> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("realm").forGetter(Invite::realm),
			GlobalPos.CODEC.fieldOf("village").forGetter(Invite::village),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Invite::owner),
			Codec.LONG.optionalFieldOf("sent", 0L).forGetter(Invite::sent)
		).apply(i, Invite::new));
	}

	/** A member village's vote (33.12): for moving the capital, or against. */
	public record Ballot(GlobalPos hall, boolean yes) {
		public static final Codec<Ballot> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("hall").forGetter(Ballot::hall),
			Codec.BOOL.optionalFieldOf("yes", false).forGetter(Ballot::yes)
		).apply(i, Ballot::new));
	}

	/**
	 * A vote on moving a realm's capital (33.12): the member put forward, when the vote opened (game time) and the
	 * members' ballots so far. A member that hasn't voted counts for the current capital.
	 */
	public record Vote(UUID realm, GlobalPos candidate, long started, List<Ballot> ballots) {
		public static final Codec<Vote> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("realm").forGetter(Vote::realm),
			GlobalPos.CODEC.fieldOf("candidate").forGetter(Vote::candidate),
			Codec.LONG.optionalFieldOf("started", 0L).forGetter(Vote::started),
			Ballot.CODEC.listOf().optionalFieldOf("ballots", List.of()).forGetter(Vote::ballots)
		).apply(i, Vote::new));

		public Vote {
			ballots = List.copyOf(ballots);
		}

		/** {@code hall}'s ballot, or null if it hasn't voted. */
		@Nullable
		public Boolean ballot(GlobalPos hall) {
			for (Ballot b : ballots) {
				if (b.hall().equals(hall)) {
					return b.yes();
				}
			}
			return null;
		}

		/** The same vote with {@code hall}'s ballot cast (or changed). */
		public Vote cast(GlobalPos hall, boolean yes) {
			List<Ballot> now = new ArrayList<>(ballots);
			now.removeIf(b -> b.hall().equals(hall));
			now.add(new Ballot(hall, yes));
			return new Vote(realm, candidate, started, now);
		}
	}

	/**
	 * A realm's shared research (33.15): its realm topics' levels, the one being researched and how far along (a
	 * {@code Research.State} keyed by realm topic), and the capital's scholars' levels as its last round saw them, which
	 * members use while the capital isn't loaded.
	 */
	public record Study(UUID realm, io.github.jcondedata.aliveworkplace.research.Research.State research, java.util.Map<String, Integer> capital) {
		public static final Codec<Study> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("realm").forGetter(Study::realm),
			io.github.jcondedata.aliveworkplace.research.Research.State.CODEC.optionalFieldOf("research",
				io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY).forGetter(Study::research),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("capital", java.util.Map.of()).forGetter(Study::capital)
		).apply(i, Study::new));

		public Study {
			capital = java.util.Map.copyOf(capital);
		}

		public Study withResearch(io.github.jcondedata.aliveworkplace.research.Research.State research) {
			return new Study(realm, research, capital);
		}

		public Study withCapital(java.util.Map<String, Integer> capital) {
			return new Study(realm, research, capital);
		}
	}

	private final List<Realm> realms = new ArrayList<>();
	private final List<Study> studies = new ArrayList<>();
	private final List<Known> known = new ArrayList<>();
	private final List<Invite> invites = new ArrayList<>();
	private final List<Vote> votes = new ArrayList<>();

	private final List<Order> orders = new ArrayList<>();
	private final List<Founded> founded = new ArrayList<>();
	private final List<Cooldown> cooldowns = new ArrayList<>();

	public static RealmData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RealmData::new, RealmData::load, null), NAME);
	}

	/** The colony orders not yet arrived, oldest first. */
	public List<Order> orders() {
		return List.copyOf(orders);
	}

	/** {@code mother}'s order on the road (or getting ready), or null. */
	@Nullable
	public Order orderOf(GlobalPos mother) {
		for (Order o : orders) {
			if (o.mother().equals(mother)) {
				return o;
			}
		}
		return null;
	}

	/** Adds {@code order}, in place of any other of the same mother village (a village has one at a time). */
	public void putOrder(Order order) {
		orders.removeIf(o -> o.mother().equals(order.mother()));
		orders.add(order);
		setDirty();
	}

	/** Takes {@code mother}'s order off the list (arrived or called off); the order, or null if it had none. */
	@Nullable
	public Order removeOrder(GlobalPos mother) {
		Order order = orderOf(mother);
		if (order != null) {
			orders.remove(order);
			setDirty();
		}
		return order;
	}

	/** Every colony founded, oldest first. */
	public List<Founded> founded() {
		return List.copyOf(founded);
	}

	/** The colonies {@code mother} founded, oldest first. */
	public List<Founded> foundedBy(GlobalPos mother) {
		return founded.stream().filter(f -> f.mother().equals(mother)).toList();
	}

	/** Notes a colony of {@code mother} founded at {@code hall} on {@code day}: on the list, and on the mother's count. */
	public void recordFounded(GlobalPos mother, GlobalPos hall, long day) {
		founded.add(new Founded(mother, hall, day));
		Cooldown was = cooldown(mother);
		cooldowns.removeIf(c -> c.mother().equals(mother));
		cooldowns.add(new Cooldown(mother, was.founded() == 0 ? day : Math.max(day, was.lastDay()), was.founded() + 1));
		setDirty();
	}

	/** {@code mother}'s count (last day -1 and none founded for a village that never sent a colony). */
	public Cooldown cooldown(GlobalPos mother) {
		for (Cooldown c : cooldowns) {
			if (c.mother().equals(mother)) {
				return c;
			}
		}
		return new Cooldown(mother, -1, 0);
	}

	// ---- realms (33.11) -------------------------------------------------------------------------------------------------

	/** Every realm, oldest first. */
	public List<Realm> realms() {
		return List.copyOf(realms);
	}

	/** The realm {@code hall} is in, or null. */
	@Nullable
	public Realm realmOf(GlobalPos hall) {
		for (Realm r : realms) {
			if (r.has(hall)) {
				return r;
			}
		}
		return null;
	}

	/** The realm with {@code id}, or null. */
	@Nullable
	public Realm realm(UUID id) {
		for (Realm r : realms) {
			if (r.id().equals(id)) {
				return r;
			}
		}
		return null;
	}

	/** {@code realm}'s shared research (33.15): an empty one until it has some. */
	public Study study(UUID realm) {
		for (Study s : studies) {
			if (s.realm().equals(realm)) {
				return s;
			}
		}
		return new Study(realm, io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY, java.util.Map.of());
	}

	/** Keeps {@code study} in the place of the realm's last one. */
	public void putStudy(Study study) {
		studies.removeIf(s -> s.realm().equals(study.realm()));
		studies.add(study);
		setDirty();
	}

	/** Adds {@code realm}, or puts it in the place of the one with its id. */
	public void putRealm(Realm realm) {
		for (int i = 0; i < realms.size(); i++) {
			if (realms.get(i).id().equals(realm.id())) {
				realms.set(i, realm);
				setDirty();
				return;
			}
		}
		realms.add(realm);
		setDirty();
	}

	/** Ends the realm with {@code id}. */
	public void removeRealm(UUID id) {
		if (realms.removeIf(r -> r.id().equals(id)) | invites.removeIf(i -> i.realm().equals(id)) | votes.removeIf(v -> v.realm().equals(id))) {
			setDirty();
		}
	}

	// ---- invitations and votes (33.12) ----------------------------------------------------------------------------------

	/** Every invitation not yet answered, oldest first (lapsed ones until the next settling). */
	public List<Invite> invites() {
		return List.copyOf(invites);
	}

	/** {@code realm}'s invitation to {@code village}, or null. */
	@Nullable
	public Invite invite(UUID realm, GlobalPos village) {
		for (Invite i : invites) {
			if (i.realm().equals(realm) && i.village().equals(village)) {
				return i;
			}
		}
		return null;
	}

	/** Adds {@code invite}, in place of the same realm's earlier one to the same village. */
	public void putInvite(Invite invite) {
		invites.removeIf(i -> i.realm().equals(invite.realm()) && i.village().equals(invite.village()));
		invites.add(invite);
		setDirty();
	}

	/** Takes {@code realm}'s invitation to {@code village} off the list. */
	public void removeInvite(UUID realm, GlobalPos village) {
		if (invites.removeIf(i -> i.realm().equals(realm) && i.village().equals(village))) {
			setDirty();
		}
	}

	/** Takes every invitation to {@code village} off the list (it joined a realm, or its hall is gone). */
	public void removeInvitesTo(GlobalPos village) {
		if (invites.removeIf(i -> i.village().equals(village))) {
			setDirty();
		}
	}

	/** Every capital vote running. */
	public List<Vote> votes() {
		return List.copyOf(votes);
	}

	/** {@code realm}'s capital vote, or null (a realm has one at a time). */
	@Nullable
	public Vote vote(UUID realm) {
		for (Vote v : votes) {
			if (v.realm().equals(realm)) {
				return v;
			}
		}
		return null;
	}

	/** Adds {@code vote}, in place of the realm's earlier one. */
	public void putVote(Vote vote) {
		votes.removeIf(v -> v.realm().equals(vote.realm()));
		votes.add(vote);
		setDirty();
	}

	/** Ends {@code realm}'s vote. */
	public void removeVote(UUID realm) {
		if (votes.removeIf(v -> v.realm().equals(realm))) {
			setDirty();
		}
	}

	/** What the last round of {@code hall} saw, or null. */
	@Nullable
	public Known known(GlobalPos hall) {
		for (Known k : known) {
			if (k.hall().equals(hall)) {
				return k;
			}
		}
		return null;
	}

	/** Every village a round has seen. */
	public List<Known> knownVillages() {
		return List.copyOf(known);
	}

	/** Keeps what a round saw of a village (saved only when something but the time changed). */
	public void putKnown(Known seen) {
		Known was = known(seen.hall());
		if (was != null && was.sameAs(seen)) {
			return;
		}
		known.removeIf(k -> k.hall().equals(seen.hall()));
		known.add(seen);
		setDirty();
	}

	/** Forgets a village whose hall is gone. */
	public void forgetKnown(GlobalPos hall) {
		if (known.removeIf(k -> k.hall().equals(hall))) {
			setDirty();
		}
	}

	/** Forgets every realm and village seen (tests). */
	public void clearRealms() {
		realms.clear();
		studies.clear();
		known.clear();
		invites.clear();
		votes.clear();
		setDirty();
	}

	/** Forgets everything about colonies (tests). */
	public void clearColonies() {
		orders.clear();
		founded.clear();
		cooldowns.clear();
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		CompoundTag colonies = new CompoundTag();
		Order.CODEC.listOf().encodeStart(ops, orders).result().ifPresent(t -> colonies.put("orders", t));
		Founded.CODEC.listOf().encodeStart(ops, founded).result().ifPresent(t -> colonies.put("founded", t));
		tag.put("colonies", colonies);
		Cooldown.CODEC.listOf().encodeStart(ops, cooldowns).result().ifPresent(t -> tag.put("cooldowns", t));
		Realm.CODEC.listOf().encodeStart(ops, realms).result().ifPresent(t -> tag.put("realms", t));
		Study.CODEC.listOf().encodeStart(ops, studies).result().ifPresent(t -> tag.put("studies", t));
		Known.CODEC.listOf().encodeStart(ops, known).result().ifPresent(t -> tag.put("known", t));
		Invite.CODEC.listOf().encodeStart(ops, invites).result().ifPresent(t -> tag.put("invites", t));
		Vote.CODEC.listOf().encodeStart(ops, votes).result().ifPresent(t -> tag.put("votes", t));
		return tag;
	}

	public static RealmData load(CompoundTag tag, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		RealmData data = new RealmData();
		CompoundTag colonies = Nbt.getCompound(tag, "colonies");
		read(Order.CODEC, colonies.get("orders"), ops, data.orders, "colony orders");
		read(Founded.CODEC, colonies.get("founded"), ops, data.founded, "founded colonies");
		read(Cooldown.CODEC, tag.get("cooldowns"), ops, data.cooldowns, "colony cooldowns");
		read(Realm.CODEC, tag.get("realms"), ops, data.realms, "realms");
		read(Study.CODEC, tag.get("studies"), ops, data.studies, "realm research");
		read(Known.CODEC, tag.get("known"), ops, data.known, "villages seen");
		read(Invite.CODEC, tag.get("invites"), ops, data.invites, "realm invitations");
		read(Vote.CODEC, tag.get("votes"), ops, data.votes, "capital votes");
		return data;
	}

	private static <T> void read(Codec<T> codec, @Nullable Tag tag, com.mojang.serialization.DynamicOps<Tag> ops, List<T> into, String what) {
		if (tag == null) {
			return;
		}
		codec.listOf().parse(ops, tag).resultOrPartial(e -> AliveWorkplace.LOG.warn("Couldn't read the {} in {}: {}", what, NAME, e))
			.ifPresent(into::addAll);
	}
}
