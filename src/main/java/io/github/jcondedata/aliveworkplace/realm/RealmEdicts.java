package io.github.jcondedata.aliveworkplace.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Realm edicts (ROADMAP 33.22, design note M33 "Realm edicts"): edict files in M30's format with {@code "scope": "realm"},
 * proclaimed in the capital's Book of Edicts and in force in every member. A realm has 1 slot as a Barony or County, 2
 * as a Duchy, 3 as a Kingdom and one more with a finished Royal Hall II by the capital ({@link #slots}); an edict stays at
 * least {@link Edicts#MIN_DAYS} days like any edict. Their state is kept on the realm ({@link RealmData.Laws}), so a new
 * capital takes them. What they cost in money comes out of the realm treasury: the Royal Highway's upkeep and the
 * members' dues at the realm's dawn round, the Realm Festival's share when each member plans its festival, the Royal
 * Charter's when a member buys a charter; a treasury that can't pay an upkeep lets that edict lapse.
 *
 * <p>Each one's reform is a quest at the capital (its steps go up one a day, as M30's do, and kills round any member count
 * for a slay step) plus a {@link Reforms.Count} across the realm while the edict is in force. When both are done the
 * edict is reformed for that realm for good: the boost stays and the cost goes. Effect types are registered here and read
 * by the system each changes (caravans, the board, the levy, relief, festivals, feuds, bandit camps, takings, colonies).
 * Off with {@code realms} or {@code villageEdicts}: no effects and nothing proclaimed (what's in force stays saved).
 */
public final class RealmEdicts {
	/** The Book's realm slots, on the row between the village's slots and the list (row 2). */
	public static final int[] SLOTS = {19, 21, 23, 25};
	/** Slots a realm may have at most: a Kingdom's 3 and the Royal Hall II's one. */
	public static final int MAX_SLOTS = 4;
	/** How near a member a bandit camp may not stand under the Crown's Peace (the effect's own radius wins). */
	public static final int PEACE_RADIUS = 128;

	// The counts a realm reform can ask for.
	public static final ResourceLocation CARAVANS_BETWEEN_MEMBERS = AliveWorkplace.id("caravans_between_members");
	public static final ResourceLocation BOARD_TRADES = AliveWorkplace.id("board_trades");
	public static final ResourceLocation RAIDS_BEATEN_WITH_RELIEF = AliveWorkplace.id("raids_beaten_with_relief");
	public static final ResourceLocation REALM_FESTIVALS = AliveWorkplace.id("realm_festivals");
	public static final ResourceLocation DAYS_WITHOUT_RAID = AliveWorkplace.id("days_without_raid");
	public static final ResourceLocation COLONIES_AT_VILLAGE_RANK = AliveWorkplace.id("colonies_at_village_rank");

	// The effect types.
	public static final ResourceLocation CARRY = AliveWorkplace.id("caravan_carry");
	public static final ResourceLocation REALM_UPKEEP = AliveWorkplace.id("realm_upkeep");
	public static final ResourceLocation MEMBER_DUES = AliveWorkplace.id("member_dues");
	public static final ResourceLocation COMMON_BOARD = AliveWorkplace.id("common_board");
	public static final ResourceLocation MEMBER_SPREAD = AliveWorkplace.id("member_spread");
	public static final ResourceLocation LEVY = AliveWorkplace.id("levy");
	public static final ResourceLocation RELIEF_REACH = AliveWorkplace.id("relief_reach");
	public static final ResourceLocation RELIEF_BAND = AliveWorkplace.id("relief_band");
	public static final ResourceLocation FESTIVAL_TOGETHER = AliveWorkplace.id("festival_together");
	public static final ResourceLocation FESTIVAL_MOOD_DAYS = AliveWorkplace.id("festival_mood_days");
	public static final ResourceLocation REALM_FESTIVAL_COST = AliveWorkplace.id("realm_festival_cost");
	public static final ResourceLocation NO_FEUDS = AliveWorkplace.id("no_feuds");
	public static final ResourceLocation NO_BANDIT_CAMPS = AliveWorkplace.id("no_bandit_camps");
	public static final ResourceLocation TAKINGS = AliveWorkplace.id("takings");
	public static final ResourceLocation COLONY_RANK = AliveWorkplace.id("colony_rank");
	public static final ResourceLocation COLONY_COOLDOWN = AliveWorkplace.id("colony_cooldown");
	public static final ResourceLocation COLONY_COST = AliveWorkplace.id("colony_cost");

	private static final Codec<List<ResourceLocation>> JOBS = ResourceLocation.CODEC.listOf();
	private static final Codec<VillageRanks.Rank> RANK = Codec.STRING.comapFlatMap(s -> {
		try {
			return DataResult.success(VillageRanks.Rank.valueOf(s.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "unknown rank: " + s);
		}
	}, r -> r.name().toLowerCase(Locale.ROOT));
	private static boolean registered;

	private RealmEdicts() {
	}

	public static synchronized void init() {
		if (!registered) {
			registered = true;
			RealmEffects.init(); // caravan_speed
			CivicEffects.register(CARRY, RecordCodecBuilder.<Carry>mapCodec(i -> i.group(
				Codec.intRange(0, 27).fieldOf("stacks").forGetter(Carry::stacks),
				Codec.STRING.optionalFieldOf("between", "members").forGetter(Carry::between),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(Carry::jobs)
			).apply(i, Carry::new)));
			CivicEffects.register(REALM_UPKEEP, RecordCodecBuilder.<RealmUpkeep>mapCodec(i -> i.group(
				Codec.intRange(0, 1000).fieldOf("emeralds").forGetter(RealmUpkeep::emeralds),
				Codec.STRING.validate(s -> s.equals("route") || s.equals("member") ? DataResult.success(s) : DataResult.error(() -> "per route or member: " + s))
					.optionalFieldOf("per", "route").forGetter(RealmUpkeep::per),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(RealmUpkeep::jobs)
			).apply(i, RealmUpkeep::new)));
			CivicEffects.register(MEMBER_DUES, RecordCodecBuilder.<MemberDues>mapCodec(i -> i.group(
				Codec.intRange(0, 1000).fieldOf("emeralds").forGetter(MemberDues::emeralds),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(MemberDues::jobs)
			).apply(i, MemberDues::new)));
			CivicEffects.register(COMMON_BOARD, MapCodec.unit(new CommonBoard(List.of())));
			CivicEffects.register(MEMBER_SPREAD, RecordCodecBuilder.<MemberSpread>mapCodec(i -> i.group(
				Codec.intRange(0, 100).optionalFieldOf("points", 0).forGetter(MemberSpread::points),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(MemberSpread::jobs)
			).apply(i, MemberSpread::new)));
			CivicEffects.register(LEVY, RecordCodecBuilder.<Levy>mapCodec(i -> i.group(
				Codec.intRange(-100, 100).fieldOf("points").forGetter(Levy::points),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(Levy::jobs)
			).apply(i, Levy::new)));
			CivicEffects.register(RELIEF_REACH, MapCodec.unit(new ReliefReach(List.of())));
			CivicEffects.register(RELIEF_BAND, RecordCodecBuilder.<ReliefBand>mapCodec(i -> i.group(
				Codec.intRange(1, 8).fieldOf("factor").forGetter(ReliefBand::factor),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(ReliefBand::jobs)
			).apply(i, ReliefBand::new)));
			CivicEffects.register(FESTIVAL_TOGETHER, MapCodec.unit(new FestivalTogether(List.of())));
			CivicEffects.register(FESTIVAL_MOOD_DAYS, RecordCodecBuilder.<FestivalMoodDays>mapCodec(i -> i.group(
				Codec.intRange(1, 30).fieldOf("days").forGetter(FestivalMoodDays::days),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(FestivalMoodDays::jobs)
			).apply(i, FestivalMoodDays::new)));
			CivicEffects.register(REALM_FESTIVAL_COST, RecordCodecBuilder.<RealmFestivalCost>mapCodec(i -> i.group(
				Codec.intRange(0, 1000).fieldOf("emeralds").forGetter(RealmFestivalCost::emeralds),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(RealmFestivalCost::jobs)
			).apply(i, RealmFestivalCost::new)));
			CivicEffects.register(NO_FEUDS, MapCodec.unit(new NoFeuds(List.of())));
			CivicEffects.register(NO_BANDIT_CAMPS, RecordCodecBuilder.<NoBanditCamps>mapCodec(i -> i.group(
				Codec.intRange(0, 1024).optionalFieldOf("radius", PEACE_RADIUS).forGetter(NoBanditCamps::radius),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(NoBanditCamps::jobs)
			).apply(i, NoBanditCamps::new)));
			CivicEffects.register(TAKINGS, RecordCodecBuilder.<Takings>mapCodec(i -> i.group(
				Codec.intRange(-100, 100).fieldOf("percent").forGetter(Takings::percent),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(Takings::jobs)
			).apply(i, Takings::new)));
			CivicEffects.register(COLONY_RANK, RecordCodecBuilder.<ColonyRank>mapCodec(i -> i.group(
				RANK.fieldOf("rank").forGetter(ColonyRank::rank),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(ColonyRank::jobs)
			).apply(i, ColonyRank::new)));
			CivicEffects.register(COLONY_COOLDOWN, RecordCodecBuilder.<ColonyCooldown>mapCodec(i -> i.group(
				Codec.intRange(0, 100).fieldOf("percent").forGetter(ColonyCooldown::percent),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(ColonyCooldown::jobs)
			).apply(i, ColonyCooldown::new)));
			CivicEffects.register(COLONY_COST, RecordCodecBuilder.<ColonyCost>mapCodec(i -> i.group(
				Codec.intRange(0, 1000).fieldOf("emeralds").forGetter(ColonyCost::emeralds),
				JOBS.optionalFieldOf("jobs", List.of()).forGetter(ColonyCost::jobs)
			).apply(i, ColonyCost::new)));
			Platform.get().onServerTick(RealmEdicts::tick);
		}
	}

	// ---- the effect types ------------------------------------------------------------------------------------------------

	/** {@code caravan_carry} (not the guilds' {@code carry}, 30.20): caravans between members carry {@code stacks} more stacks of what the other waits for. */
	public record Carry(int stacks, String between, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return CARRY;
		}
	}

	/** {@code realm_upkeep}: the realm treasury pays {@code emeralds} a day for each route between members (or each member). */
	public record RealmUpkeep(int emeralds, String per, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return REALM_UPKEEP;
		}
	}

	/** {@code member_dues}: each member pays {@code emeralds} a day out of its treasury into the realm treasury. */
	public record MemberDues(int emeralds, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return MEMBER_DUES;
		}
	}

	/** {@code common_board}: every member's board pays the members' average price. */
	public record CommonBoard(List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return COMMON_BOARD;
		}
	}

	/** {@code member_spread}: a member's board sells to the members' people at only {@code points} over its price. */
	public record MemberSpread(int points, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return MEMBER_SPREAD;
		}
	}

	/** {@code levy}: the realm's levy {@code points} higher. */
	public record Levy(int points, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return LEVY;
		}
	}

	/** {@code relief_reach}: every member sends relief, wherever it is. */
	public record ReliefReach(List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return RELIEF_REACH;
		}
	}

	/** {@code relief_band}: relief bands {@code factor} times as big. */
	public record ReliefBand(int factor, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return RELIEF_BAND;
		}
	}

	/** {@code festival_together}: every member holds its festival on the capital's day. */
	public record FestivalTogether(List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return FESTIVAL_TOGETHER;
		}
	}

	/** {@code festival_mood_days}: a festival's good mood lasts at least {@code days} days. */
	public record FestivalMoodDays(int days, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return FESTIVAL_MOOD_DAYS;
		}
	}

	/** {@code realm_festival_cost}: each member's festival costs the realm treasury {@code emeralds}. */
	public record RealmFestivalCost(int emeralds, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return REALM_FESTIVAL_COST;
		}
	}

	/** {@code no_feuds}: no feud can be declared on a member. */
	public record NoFeuds(List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return NO_FEUDS;
		}
	}

	/** {@code no_bandit_camps}: bandits never camp within {@code radius} blocks of a member. */
	public record NoBanditCamps(int radius, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return NO_BANDIT_CAMPS;
		}
	}

	/** {@code takings}: every member's daily takings {@code percent} higher (negative: lower). */
	public record Takings(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return TAKINGS;
		}
	}

	/** {@code colony_rank}: members found colonies from {@code rank} on. */
	public record ColonyRank(VillageRanks.Rank rank, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return COLONY_RANK;
		}
	}

	/** {@code colony_cooldown}: members wait {@code percent} of the usual days between colonies. */
	public record ColonyCooldown(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return COLONY_COOLDOWN;
		}
	}

	/** {@code colony_cost}: each colony a member founds costs the realm treasury {@code emeralds}. */
	public record ColonyCost(int emeralds, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return COLONY_COST;
		}
	}

	// ---- the state ----------------------------------------------------------------------------------------------------

	/** Whether realm edicts work now: realms and edicts both on. */
	public static boolean on() {
		return Realms.ENABLED && Edicts.ENABLED;
	}

	/** The realm edict slots of {@code realm}: Barony and County 1, Duchy 2, Kingdom 3, one more with a Royal Hall II by the capital. */
	public static int slots(MinecraftServer server, RealmData.Realm realm) {
		return slots(Realms.Rank.parse(realm.rank()), RoyalHalls.multiplier(server, realm) >= 3);
	}

	/** Slots of a realm of {@code rank}, with or without a Royal Hall II. */
	public static int slots(Realms.Rank rank, boolean royalHall2) {
		int base = switch (rank) {
			case BARONY, COUNTY -> 1;
			case DUCHY -> 2;
			case KINGDOM -> 3;
		};
		return base + (royalHall2 ? 1 : 0);
	}

	/** The realm edicts in force in {@code realm}, oldest first. */
	public static List<Edicts.InForce> inForce(MinecraftServer server, RealmData.Realm realm) {
		return RealmData.get(server).laws(realm.id()).edicts();
	}

	/** Whether the realm edict {@code id} is in force in {@code realm}. */
	public static boolean inForce(MinecraftServer server, @Nullable RealmData.Realm realm, String id) {
		return realm != null && inForce(server, realm).stream().anyMatch(f -> f.id().equals(id));
	}

	/** {@code realm}'s progress on the reform of the realm edict {@code id}. */
	public static Reforms.Progress progress(MinecraftServer server, RealmData.Realm realm, String id) {
		return RealmData.get(server).laws(realm.id()).reforms().stream().filter(p -> p.id().equals(id)).findFirst()
			.orElse(new Reforms.Progress(id, 0, false, 0));
	}

	/** Whether {@code realm} has reformed the realm edict {@code id}. */
	public static boolean reformed(MinecraftServer server, RealmData.Realm realm, String id) {
		return progress(server, realm, id).reformed();
	}

	/** How far {@code realm} has got with the count of the realm edict {@code id}'s reform. */
	public static int count(MinecraftServer server, RealmData.Realm realm, String id) {
		Optional<Reforms.Count> count = Reforms.of(id).flatMap(Reforms.Reform::count);
		if (count.isEmpty()) {
			return 0;
		}
		RealmData.Laws laws = RealmData.get(server).laws(realm.id());
		ResourceLocation what = count.get().what();
		if (what.equals(DAYS_WITHOUT_RAID)) {
			long since = inForce(server, realm).stream().filter(f -> f.id().equals(id)).mapToLong(Edicts.InForce::day).findFirst().orElse(today(server));
			return (int) Math.max(0, today(server) - Math.max(since, laws.lastRaidDay()));
		}
		if (what.equals(COLONIES_AT_VILLAGE_RANK)) {
			return coloniesAtVillageRank(server, realm);
		}
		return laws.counts().getOrDefault(id, 0);
	}

	/** Colonies founded by any member of {@code realm} that are a Village or more now. */
	public static int coloniesAtVillageRank(MinecraftServer server, RealmData.Realm realm) {
		int n = 0;
		for (RealmData.Founded f : RealmData.get(server).founded()) {
			if (realm.has(f.mother()) && !Realms.gone(server, f.hall())
				&& Realms.rankOf(server, f.hall()).ordinal() >= VillageRanks.Rank.VILLAGE.ordinal()) {
				n++;
			}
		}
		return n;
	}

	static long today(MinecraftServer server) {
		return Chronicle.day(server.overworld());
	}

	/** The effects the realm edicts in force in {@code realm} give (the boost, and the cost until reformed); none with them off. */
	public static List<CivicEffects.Effect> effects(MinecraftServer server, @Nullable RealmData.Realm realm) {
		if (realm == null || !on()) {
			return List.of();
		}
		List<CivicEffects.Effect> out = new ArrayList<>();
		RealmData.Laws laws = RealmData.get(server).laws(realm.id());
		for (Edicts.InForce f : laws.edicts()) {
			ResourceLocation id = ResourceLocation.tryParse(f.id());
			Optional<Edicts.Edict> edict = id == null ? Optional.empty() : Edicts.get(id);
			if (edict.isPresent() && edict.get().realm()) {
				boolean reformed = laws.reforms().stream().anyMatch(p -> p.id().equals(f.id()) && p.reformed());
				out.addAll(edict.get().effects(reformed));
			}
		}
		return out;
	}

	/** The effects of type {@code type} the realm edicts in force in {@code realm} give. */
	public static <E extends CivicEffects.Effect> List<E> effects(MinecraftServer server, @Nullable RealmData.Realm realm, Class<E> type) {
		return effects(server, realm).stream().filter(type::isInstance).map(type::cast).toList();
	}

	/** The effects of type {@code type} the realm of the village at {@code hall} gives it. */
	public static <E extends CivicEffects.Effect> List<E> effects(ServerLevel level, BlockPos hall, Class<E> type) {
		if (!on()) {
			return List.of();
		}
		return effects(level.getServer(), RealmData.get(level.getServer()).realmOf(GlobalPos.of(level.dimension(), hall)), type);
	}

	@Nullable
	private static RealmData.Realm realmOf(ServerLevel level, BlockPos hall) {
		return RealmData.get(level.getServer()).realmOf(GlobalPos.of(level.dimension(), hall));
	}

	// ---- what the systems read --------------------------------------------------------------------------------------

	/** Royal Highway: how many more stacks a caravan from {@code from} to {@code to} carries (both of one realm). */
	public static int extraStacks(ServerLevel level, BlockPos from, BlockPos to) {
		RealmData.Realm realm = between(level, from, to);
		return effects(level.getServer(), realm, Carry.class).stream().mapToInt(Carry::stacks).sum();
	}

	/** Royal Highway: how much sooner (percent) a caravan from {@code from} to {@code to} arrives (both of one realm). */
	public static int caravanSpeed(ServerLevel level, BlockPos from, BlockPos to) {
		RealmData.Realm realm = between(level, from, to);
		return effects(level.getServer(), realm, RealmEffects.CaravanSpeed.class).stream().mapToInt(RealmEffects.CaravanSpeed::percent).sum();
	}

	/** The realm both villages are members of, while realm edicts are on; else null. */
	@Nullable
	static RealmData.Realm between(ServerLevel level, BlockPos from, BlockPos to) {
		if (!on()) {
			return null;
		}
		return RealmResearch.shared(level.getServer(), GlobalPos.of(level.dimension(), from), GlobalPos.of(level.dimension(), to));
	}

	/**
	 * Common Market: the price the board of the village at {@code hall} pays for {@code good} (its own {@code own}),
	 * as the average of the members' prices known in its dimension; {@code own} outside such a realm.
	 */
	public static int commonPrice(ServerLevel level, BlockPos hall, ResourceLocation good, int own) {
		RealmData.Realm realm = realmOf(level, hall);
		if (effects(level.getServer(), realm, CommonBoard.class).isEmpty()) {
			return own;
		}
		Caravans.Data data = Caravans.Data.get(level);
		long sum = 0;
		int n = 0;
		for (GlobalPos member : Realms.members(realm)) {
			if (!member.dimension().equals(level.dimension())) {
				continue;
			}
			if (member.pos().equals(hall)) {
				sum += own;
				n++;
				continue;
			}
			io.github.jcondedata.aliveworkplace.trade.Market.Price p = data.market(member.pos()).prices().get(good);
			if (p != null) {
				sum += p.cents();
				n++;
			}
		}
		return n == 0 ? own : (int) Math.round(sum / (double) n);
	}

	/**
	 * Common Market: what the board of the village at {@code hall} sells for, in percent of what it pays, to
	 * {@code buyer}: 100 plus the effect's points when they own a member (or are a friend of a member's owner), else
	 * {@code usual}.
	 */
	public static int sellPercent(ServerLevel level, BlockPos hall, @Nullable Player buyer, int usual) {
		if (buyer == null) {
			return usual;
		}
		RealmData.Realm realm = realmOf(level, hall);
		List<MemberSpread> spread = effects(level.getServer(), realm, MemberSpread.class);
		if (spread.isEmpty() || !memberPeople(level.getServer(), realm, buyer)) {
			return usual;
		}
		return Math.min(usual, 100 + spread.stream().mapToInt(MemberSpread::points).min().orElse(0));
	}

	/** Whether {@code player} owns a member of {@code realm}, is a friend of a member's owner, or an operator. */
	static boolean memberPeople(MinecraftServer server, RealmData.Realm realm, Player player) {
		for (GlobalPos member : Realms.members(realm)) {
			if (Realms.mayRule(server, member, player)) {
				return true;
			}
		}
		return false;
	}

	/** Common Market: how many points higher the levy of {@code realm} is. */
	public static int levyExtra(MinecraftServer server, RealmData.Realm realm) {
		return effects(server, realm, Levy.class).stream().mapToInt(Levy::points).sum();
	}

	/** Levy of Arms: whether every member of {@code realm} sends relief, wherever it is. */
	public static boolean reliefReach(MinecraftServer server, @Nullable RealmData.Realm realm) {
		return !effects(server, realm, ReliefReach.class).isEmpty();
	}

	/** Levy of Arms: how many times as big {@code realm}'s relief bands are (1: as usual). */
	public static int reliefFactor(MinecraftServer server, @Nullable RealmData.Realm realm) {
		return Math.max(1, effects(server, realm, ReliefBand.class).stream().mapToInt(ReliefBand::factor).max().orElse(1));
	}

	/** Realm Festival: the hall whose festival day the village at {@code hall} keeps: the capital's under the edict, else its own. */
	public static BlockPos festivalHall(ServerLevel level, BlockPos hall) {
		RealmData.Realm realm = realmOf(level, hall);
		if (realm == null || !realm.capital().dimension().equals(level.dimension()) || effects(level.getServer(), realm, FestivalTogether.class).isEmpty()) {
			return hall;
		}
		return realm.capital().pos();
	}

	/** Realm Festival: the fewest days a festival's good mood lasts for {@code villager} (0: as usual). */
	public static int festivalMoodDays(ServerLevel level, Villager villager) {
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall == null || !on()) {
			return 0;
		}
		return effects(level, hall.getBlockPos(), FestivalMoodDays.class).stream().mapToInt(FestivalMoodDays::days).max().orElse(0);
	}

	/** Realm Festival: emeralds the realm treasury pays for a festival of the village at {@code hall} (0: none). */
	public static int festivalCost(ServerLevel level, BlockPos hall) {
		return effects(level, hall, RealmFestivalCost.class).stream().mapToInt(RealmFestivalCost::emeralds).sum();
	}

	/**
	 * Realm Festival: the realm pays its share of the festival the village at {@code hall} plans today. True if paid or
	 * nothing is owed; false if the realm treasury is short (no festival).
	 */
	public static boolean payFestival(ServerLevel level, BlockPos hall) {
		int cost = festivalCost(level, hall);
		if (cost <= 0) {
			return true;
		}
		RealmData.Realm realm = realmOf(level, hall);
		return realm != null && RealmTreasury.spend(level.getServer(), realm, cost * 100L,
			Component.translatable("message.aliveworkplace.realm_edict.ledger.festival", VillageHalls.name(level, hall).getString()));
	}

	/** Realm Festival: a member held its festival on {@code day}; the realm's festival is counted once a day. */
	public static void festivalHeld(ServerLevel level, BlockPos hall, long day) {
		RealmData.Realm realm = realmOf(level, hall);
		if (realm == null || effects(level.getServer(), realm, FestivalTogether.class).isEmpty()) {
			return;
		}
		RealmData data = RealmData.get(level.getServer());
		RealmData.Laws laws = data.laws(realm.id());
		if (laws.festivalDay() == day) {
			return;
		}
		data.putLaws(laws.withFestivalDay(day));
		count(level.getServer(), realm, REALM_FESTIVALS, 1);
	}

	/** Crown's Peace: whether a feud may not be declared on the village at {@code target}. */
	public static boolean noFeuds(MinecraftServer server, GlobalPos target) {
		return on() && !effects(server, RealmData.get(server).realmOf(target), NoFeuds.class).isEmpty();
	}

	/** Crown's Peace: whether bandits may not camp at {@code site}: it's within the radius of a member of a realm under the peace. */
	public static boolean peaceNear(ServerLevel level, BlockPos site) {
		if (!on()) {
			return false;
		}
		MinecraftServer server = level.getServer();
		for (RealmData.Realm realm : RealmData.get(server).realms()) {
			int radius = effects(server, realm, NoBanditCamps.class).stream().mapToInt(NoBanditCamps::radius).max().orElse(-1);
			if (radius < 0) {
				continue;
			}
			for (GlobalPos member : Realms.members(realm)) {
				if (member.dimension().equals(level.dimension()) && member.pos().distSqr(site) <= (double) radius * radius) {
					return true;
				}
			}
		}
		return false;
	}

	/** Crown's Peace: {@code takings} (hundredths of an emerald) of the village at {@code hall} after its realm's edicts. */
	public static long takings(ServerLevel level, BlockPos hall, long takings) {
		int percent = effects(level, hall, Takings.class).stream().mapToInt(Takings::percent).sum();
		return percent == 0 ? takings : Math.max(0, takings * (100 + Math.max(-100, percent)) / 100);
	}

	/**
	 * Levy of Arms: the member at {@code hall} pays its dues for {@code days} days out of its treasury into the realm
	 * treasury, as far as it has them and the realm has room. What was paid, in hundredths.
	 */
	public static long dues(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long days) {
		int emeralds = effects(level, hall, MemberDues.class).stream().mapToInt(MemberDues::emeralds).sum();
		if (emeralds <= 0 || days <= 0) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		RealmData.Realm realm = realmOf(level, hall);
		if (realm == null) {
			return 0;
		}
		long room = Math.max(0, RealmTreasury.capCents(server, realm) - realm.treasury());
		long paid = Math.min(Math.min(emeralds * 100L * days, entity.treasury()), room);
		if (paid <= 0) {
			return 0;
		}
		entity.setTreasury((int) (entity.treasury() - paid));
		RealmTreasury.move(server, realm, paid, Component.translatable("message.aliveworkplace.realm_edict.ledger.dues", VillageHalls.name(level, hall).getString()));
		return paid;
	}

	/** Royal Charter: the rank the village at {@code hall} needs for a colony, given {@code usual}. */
	public static VillageRanks.Rank colonyRank(ServerLevel level, BlockPos hall, VillageRanks.Rank usual) {
		VillageRanks.Rank out = usual;
		for (ColonyRank r : effects(level, hall, ColonyRank.class)) {
			if (r.rank().ordinal() < out.ordinal()) {
				out = r.rank();
			}
		}
		return out;
	}

	/** Royal Charter: the days the village at {@code hall} waits between colonies, given {@code usual}. */
	public static int colonyCooldown(ServerLevel level, BlockPos hall, int usual) {
		int percent = effects(level, hall, ColonyCooldown.class).stream().mapToInt(ColonyCooldown::percent).min().orElse(100);
		return percent >= 100 ? usual : Math.max(usual > 0 && percent > 0 ? 1 : 0, (usual * percent + 50) / 100);
	}

	/** Royal Charter: emeralds a colony of the village at {@code hall} costs its realm's treasury (0: none). */
	public static int colonyCost(ServerLevel level, BlockPos hall) {
		return effects(level, hall, ColonyCost.class).stream().mapToInt(ColonyCost::emeralds).sum();
	}

	/** Royal Charter: whether the realm of the village at {@code hall} has its colony's share. */
	public static boolean canPayColony(ServerLevel level, BlockPos hall) {
		int cost = colonyCost(level, hall);
		RealmData.Realm realm = realmOf(level, hall);
		return cost <= 0 || realm != null && RealmData.get(level.getServer()).realm(realm.id()).treasury() >= cost * 100L;
	}

	/** Royal Charter: the realm pays its share of the charter the village at {@code hall} bought. */
	public static boolean payColony(ServerLevel level, BlockPos hall) {
		int cost = colonyCost(level, hall);
		RealmData.Realm realm = realmOf(level, hall);
		return cost <= 0 || realm != null && RealmTreasury.spend(level.getServer(), realm, cost * 100L,
			Component.translatable("message.aliveworkplace.realm_edict.ledger.colony", VillageHalls.name(level, hall).getString()));
	}

	// ---- counts -----------------------------------------------------------------------------------------------------

	/**
	 * {@code n} more of {@code what} in {@code realm}: counted for every realm edict in force there, not yet reformed,
	 * whose reform's count is of that; a count that is reached may finish a reform whose steps are done.
	 */
	public static void count(MinecraftServer server, @Nullable RealmData.Realm realm, ResourceLocation what, int n) {
		if (realm == null || n <= 0 || !on()) {
			return;
		}
		RealmData data = RealmData.get(server);
		RealmData.Laws laws = data.laws(realm.id());
		Map<String, Integer> counts = new HashMap<>(laws.counts());
		List<String> touched = new ArrayList<>();
		for (Edicts.InForce f : laws.edicts()) {
			Optional<Reforms.Count> count = Reforms.of(f.id()).flatMap(Reforms.Reform::count);
			if (count.isPresent() && count.get().what().equals(what) && !reformed(server, realm, f.id())) {
				counts.merge(f.id(), n, Integer::sum);
				touched.add(f.id());
			}
		}
		if (touched.isEmpty()) {
			return;
		}
		data.putLaws(laws.withCounts(counts));
		for (String id : touched) {
			tryReform(server, realm, id);
		}
	}

	/** A caravan set out from {@code from} to {@code to}: one between members if both are of one realm. */
	public static void caravanSent(ServerLevel level, BlockPos from, BlockPos to) {
		count(level.getServer(), between(level, from, to), CARAVANS_BETWEEN_MEMBERS, 1);
	}

	/** A player traded at the board of the village at {@code hall}. */
	public static void boardTrade(ServerLevel level, BlockPos hall) {
		if (on()) {
			count(level.getServer(), realmOf(level, hall), BOARD_TRADES, 1);
		}
	}

	/** A raid on the member at {@code at} that relief came to is over (the hall still stands). */
	public static void raidBeaten(MinecraftServer server, GlobalPos at) {
		if (on() && !Realms.gone(server, at)) {
			count(server, RealmData.get(server).realmOf(at), RAIDS_BEATEN_WITH_RELIEF, 1);
		}
	}

	/** The member at {@code at} is raided today: the days without a raid start again. */
	public static void raided(MinecraftServer server, GlobalPos at) {
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(at);
		if (realm == null) {
			return;
		}
		RealmData.Laws laws = data.laws(realm.id());
		long today = today(server);
		if (laws.lastRaidDay() != today) {
			data.putLaws(laws.withLastRaidDay(today));
		}
	}

	// ---- proclaiming and lifting ------------------------------------------------------------------------------------

	/** {@code by} (null: an operator's console) proclaims the realm edict {@code edict} from the capital at {@code capitalHall}. */
	public static Edicts.Result proclaim(ServerLevel level, BlockPos capitalHall, @Nullable Player by, Edicts.Edict edict) {
		MinecraftServer server = level.getServer();
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		if (!on()) {
			return no(Component.translatable(Realms.ENABLED ? "message.aliveworkplace.edict.disabled" : "message.aliveworkplace.realm.off"));
		}
		if (!edict.realm()) {
			return no(Component.translatable("message.aliveworkplace.realm_edict.not_realm", edict.name()));
		}
		if (realm == null || !realm.capital().equals(capital)) {
			return no(Component.translatable("message.aliveworkplace.realm.not_capital", village));
		}
		if (by != null && !Realms.mayRule(server, capital, by)) {
			return no(Component.translatable("message.aliveworkplace.realm.rule_not_yours", Realms.ownerName(server, capital), Realms.title(server, realm)));
		}
		RealmData.Laws laws = data.laws(realm.id());
		for (Edicts.InForce f : laws.edicts()) {
			if (f.id().equals(edict.id().toString())) {
				return no(Component.translatable("message.aliveworkplace.edict.already", edict.name(), Realms.title(server, realm)));
			}
			Optional<Edicts.Edict> other = Edicts.get(ResourceLocation.tryParse(f.id()));
			if (other.isPresent() && edict.excludes(other.get())) {
				return no(Component.translatable("message.aliveworkplace.edict.excluded", edict.name(), other.get().name()));
			}
		}
		int slots = slots(server, realm);
		if (laws.edicts().size() >= slots) {
			return no(Component.translatable("message.aliveworkplace.realm_edict.no_slot", Realms.title(server, realm), Realms.Rank.parse(realm.rank()).title(),
				Words.counted("message.aliveworkplace.edict.count", slots, slots)));
		}
		List<Edicts.InForce> next = new ArrayList<>(laws.edicts());
		next.add(new Edicts.InForce(edict.id().toString(), today(server)));
		Map<String, Integer> counts = new HashMap<>(laws.counts());
		counts.remove(edict.id().toString()); // a reform's count starts again each time it's proclaimed
		data.putLaws(laws.withEdicts(next).withCounts(counts));
		Realms.chronicle(server, realm, Component.translatable("chronicle.aliveworkplace.realm_edict.proclaimed", edict.name()));
		level.playSound(null, capitalHall, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1f);
		Component told = Component.translatable("message.aliveworkplace.realm_edict.proclaimed", Realms.title(server, realm), edict.name(), edict.description())
			.withStyle(ChatFormatting.GOLD);
		Realms.tellOwners(server, realm, told);
		if (level.getBlockEntity(capitalHall) instanceof VillageHallBlockEntity entity) {
			round(level, capitalHall, entity);
		}
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
		io.github.jcondedata.aliveworkplace.hall.VillageMoments.award(level, capitalHall, io.github.jcondedata.aliveworkplace.hall.VillageMoments.EDICT,
			by instanceof net.minecraft.server.level.ServerPlayer player ? player : null); // the capital's village (36.2)
		return new Edicts.Result(true, told, told(server, realm));
	}

	/** {@code by} (null: an operator's console) lifts the realm edict {@code id} at the capital at {@code capitalHall}. */
	public static Edicts.Result lift(ServerLevel level, BlockPos capitalHall, @Nullable Player by, String id) {
		MinecraftServer server = level.getServer();
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		if (!on()) {
			return no(Component.translatable(Realms.ENABLED ? "message.aliveworkplace.edict.disabled" : "message.aliveworkplace.realm.off"));
		}
		if (realm == null || !realm.capital().equals(capital)) {
			return no(Component.translatable("message.aliveworkplace.realm.not_capital", village));
		}
		Component name = Edicts.name(id);
		RealmData.Laws laws = data.laws(realm.id());
		Edicts.InForce found = laws.edicts().stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
		if (found == null) {
			return no(Component.translatable("message.aliveworkplace.edict.not_in_force", name, Realms.title(server, realm)));
		}
		if (by != null && !Realms.mayRule(server, capital, by)) {
			return no(Component.translatable("message.aliveworkplace.realm.rule_not_yours", Realms.ownerName(server, capital), Realms.title(server, realm)));
		}
		long from = found.day() + Edicts.MIN_DAYS;
		if (today(server) < from) {
			return no(Words.counted("message.aliveworkplace.edict.too_soon", Edicts.MIN_DAYS, name, Edicts.MIN_DAYS, from));
		}
		remove(server, realm, id);
		Realms.chronicle(server, realm, Component.translatable("chronicle.aliveworkplace.realm_edict.lifted", name));
		Component told = Component.translatable("message.aliveworkplace.realm_edict.lifted", Realms.title(server, realm), name).withStyle(ChatFormatting.GOLD);
		Realms.tellOwners(server, realm, told);
		return new Edicts.Result(true, told, told(server, realm));
	}

	private static void remove(MinecraftServer server, RealmData.Realm realm, String id) {
		RealmData data = RealmData.get(server);
		RealmData.Laws laws = data.laws(realm.id());
		List<Edicts.InForce> next = new ArrayList<>(laws.edicts());
		next.removeIf(f -> f.id().equals(id));
		data.putLaws(laws.withEdicts(next));
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
	}

	/** An edict lapses (the treasury can't pay its upkeep, or the realm lost a slot): gone, chronicled and told. */
	static void lapse(MinecraftServer server, RealmData.Realm realm, String id, Component why) {
		remove(server, realm, id);
		Component name = Edicts.name(id);
		Realms.chronicle(server, realm, Component.translatable("chronicle.aliveworkplace.realm_edict.lapsed", name, why));
		Realms.tellOwners(server, realm, Component.translatable("message.aliveworkplace.realm_edict.lapsed", Realms.title(server, realm), name, why)
			.withStyle(ChatFormatting.GOLD));
	}

	private static Edicts.Result no(Component message) {
		return new Edicts.Result(false, message.copy().withStyle(ChatFormatting.RED), List.of());
	}

	/** The members' owners online (who were told). */
	private static List<ServerPlayer> told(MinecraftServer server, RealmData.Realm realm) {
		List<ServerPlayer> out = new ArrayList<>();
		for (GlobalPos member : Realms.members(realm)) {
			UUID owner = Realms.ownerOf(server, member);
			ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
			if (player != null && !out.contains(player)) {
				out.add(player);
			}
		}
		return out;
	}

	// ---- the reforms --------------------------------------------------------------------------------------------------

	/**
	 * A hall's round: at a realm's capital, the next step of every realm edict in force that has a reform still to do
	 * goes up on its quest page when due (one a day, as M30's); anywhere else (a capital that was moved, a village that
	 * left) realm steps come down.
	 */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!on()) {
			return; // switched off: steps up stay as they are, for when it's back on
		}
		MinecraftServer server = level.getServer();
		RealmData.Realm realm = realmOf(level, hall);
		boolean capital = realm != null && realm.capital().equals(GlobalPos.of(level.dimension(), hall));
		List<VillageQuests.Quest> quests = new ArrayList<>(entity.quests());
		boolean changed = false;
		Set<String> wanted = new LinkedHashSet<>();
		if (capital) {
			long today = today(server);
			for (Edicts.InForce f : inForce(server, realm)) {
				Optional<Reforms.Reform> reform = Reforms.of(f.id());
				Reforms.Progress progress = progress(server, realm, f.id());
				if (reform.isEmpty() || progress.reformed() || progress.step() >= reform.get().steps().size()) {
					continue;
				}
				wanted.add(f.id());
				int step = progress.step();
				changed |= quests.removeIf(q -> q.reform().map(r -> r.edict().equals(f.id()) && r.step() != step).orElse(false));
				if (quests.stream().anyMatch(q -> q.reform().map(r -> r.edict().equals(f.id())).orElse(false)) || today < progress.nextDay()) {
					continue;
				}
				Reforms.Step s = reform.get().steps().get(step).resolve(Reforms.battles(level, hall));
				int reward = Math.round(s.reward() * VillageRanks.questRewardFactor(entity.rank()));
				VillageQuests.Quest quest = new VillageQuests.Quest(UUID.randomUUID(), s.kind(), s.item(), s.count(), 0, reward, level.getGameTime(), "",
					Optional.empty(), Optional.of(new VillageQuests.ReformStep(f.id(), step)));
				quests.add(quest);
				changed = true;
				Realms.tellOwners(server, realm, Component.translatable("message.aliveworkplace.realm_edict.posted", VillageHalls.name(level, hall),
					VillageQuests.describe(quest)).withStyle(ChatFormatting.GOLD));
			}
		}
		changed |= quests.removeIf(q -> q.reform().map(r -> Reforms.realm(r.edict()) && !wanted.contains(r.edict())).orElse(false));
		if (changed) {
			entity.setQuests(quests);
		}
	}

	/** A realm reform step was finished at the capital at {@code hall} (and paid): the next goes up tomorrow; after the last, the count decides. */
	public static void stepDone(ServerLevel level, BlockPos hall, VillageQuests.ReformStep done, @Nullable ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RealmData.Realm realm = realmOf(level, hall);
		if (realm == null) {
			return;
		}
		Reforms.Progress progress = progress(server, realm, done.edict());
		if (progress.reformed() || done.step() != progress.step()) {
			return;
		}
		int steps = Reforms.of(done.edict()).map(r -> r.steps().size()).orElse(done.step() + 1);
		saveProgress(server, realm, new Reforms.Progress(done.edict(), done.step() + 1, false, today(server) + 1));
		if (done.step() + 1 < steps) {
			if (player != null) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.reform.next", Reforms.of(done.edict()).map(Reforms.Reform::name)
					.orElse(Edicts.name(done.edict()))).withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!tryReform(server, realm, done.edict()) && player != null) {
			Optional<Reforms.Count> count = Reforms.of(done.edict()).flatMap(Reforms.Reform::count);
			count.ifPresent(c -> Chat.chat(player, Component.translatable("message.aliveworkplace.realm_edict.count_left",
				Edicts.name(done.edict()), countLine(server, realm, done.edict())).withStyle(ChatFormatting.GRAY)));
		}
	}

	private static void saveProgress(MinecraftServer server, RealmData.Realm realm, Reforms.Progress progress) {
		RealmData data = RealmData.get(server);
		RealmData.Laws laws = data.laws(realm.id());
		List<Reforms.Progress> all = new ArrayList<>(laws.reforms());
		all.removeIf(p -> p.id().equals(progress.id()));
		all.add(progress);
		data.putLaws(laws.withReforms(all));
	}

	/** Reforms the realm edict {@code id} for {@code realm} if its steps are done and its count reached; true if it did now. */
	public static boolean tryReform(MinecraftServer server, RealmData.Realm realm, String id) {
		Optional<Reforms.Reform> reform = Reforms.of(id);
		Reforms.Progress progress = progress(server, realm, id);
		if (reform.isEmpty() || progress.reformed() || progress.step() < reform.get().steps().size()) {
			return false;
		}
		int times = reform.get().count().map(Reforms.Count::times).orElse(0);
		if (count(server, realm, id) < times) {
			return false;
		}
		reform(server, realm, id);
		return true;
	}

	/** {@code realm} reforms the realm edict {@code id} for good: the boost stays, the cost goes; fireworks, every owner told, every chronicle. */
	public static void reform(MinecraftServer server, RealmData.Realm realm, String id) {
		Optional<Reforms.Reform> reform = Reforms.of(id);
		int steps = reform.map(r -> r.steps().size()).orElse(1);
		saveProgress(server, realm, new Reforms.Progress(id, steps, true, today(server)));
		Component edict = Edicts.name(id);
		Component name = reform.map(Reforms.Reform::name).orElse(edict);
		Component line = reform.map(Reforms.Reform::line).orElse(Component.empty());
		Realms.chronicle(server, realm, Component.translatable("chronicle.aliveworkplace.realm_edict.reformed", edict, name));
		Realms.tellOwners(server, realm, Component.translatable("message.aliveworkplace.realm_edict.reformed", Realms.title(server, realm), edict, name, line)
			.withStyle(ChatFormatting.GOLD));
		ServerLevel level = server.getLevel(realm.capital().dimension());
		if (level != null && level.isLoaded(realm.capital().pos())) {
			BlockPos hall = realm.capital().pos();
			for (int i = 0; i < 5; i++) {
				Festivals.launch(level, hall.offset(level.random.nextInt(5) - 2, 2, level.random.nextInt(5) - 2));
			}
			level.playSound(null, hall, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				round(level, hall, entity); // its step comes down
			}
		}
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
	}

	/** "7 of 12 caravans between members" for the realm edict {@code id}'s reform count. */
	public static Component countLine(MinecraftServer server, RealmData.Realm realm, String id) {
		Optional<Reforms.Count> count = Reforms.of(id).flatMap(Reforms.Reform::count);
		if (count.isEmpty()) {
			return Component.empty();
		}
		int times = count.get().times();
		return Words.counted("screen.aliveworkplace.realm_edict.count." + count.get().what().getPath(), times, Math.min(times, count(server, realm, id)), times);
	}

	// ---- the dawn round ----------------------------------------------------------------------------------------------

	private static long lastDawn = -1;

	private static void tick(MinecraftServer server) {
		if (!on() || server.getTickCount() % 20 != 0) {
			return;
		}
		watch(server);
		long day = today(server);
		if (lastDawn < 0) {
			lastDawn = day;
			return;
		}
		if (day != lastDawn) {
			lastDawn = day;
			dawn(server, day);
		}
	}

	/** Each second: a loaded member raided now resets its realm's days without a raid. */
	static void watch(MinecraftServer server) {
		for (RealmData.Realm realm : RealmData.get(server).realms()) {
			if (RealmData.get(server).laws(realm.id()).edicts().isEmpty()) {
				continue;
			}
			for (GlobalPos member : Realms.members(realm)) {
				ServerLevel level = server.getLevel(member.dimension());
				if (level != null && level.isLoaded(member.pos()) && io.github.jcondedata.aliveworkplace.guard.VillageRaids.raided(level, member.pos(), member.pos())) {
					raided(server, member);
					break;
				}
			}
		}
	}

	/**
	 * The realm's dawn round on {@code day} (once a day): edicts beyond the realm's slots lapse (newest first); the Royal
	 * Highway's upkeep is paid from the realm treasury (an edict whose upkeep can't be paid lapses); reforms whose count
	 * was reached overnight (days without a raid, colonies grown) are made. Returns the cents paid in upkeep.
	 */
	public static long dawn(MinecraftServer server, long day) {
		if (!on()) {
			return 0;
		}
		long paid = 0;
		RealmData data = RealmData.get(server);
		for (RealmData.Realm realm : data.realms()) {
			RealmData.Laws laws = data.laws(realm.id());
			if (laws.edicts().isEmpty() || laws.lastDay() >= day) {
				continue;
			}
			data.putLaws(laws.withLastDay(day));
			// the slots
			int slots = slots(server, realm);
			List<Edicts.InForce> held = new ArrayList<>(laws.edicts());
			while (held.size() > slots) {
				Edicts.InForce newest = held.remove(held.size() - 1);
				lapse(server, realm, newest.id(), Component.translatable("chronicle.aliveworkplace.realm_edict.why_slots", Realms.Rank.parse(realm.rank()).title()));
			}
			// the upkeep, edict by edict
			for (Edicts.InForce f : List.copyOf(data.laws(realm.id()).edicts())) {
				ResourceLocation id = ResourceLocation.tryParse(f.id());
				Optional<Edicts.Edict> edict = id == null ? Optional.empty() : Edicts.get(id);
				if (edict.isEmpty() || reformed(server, realm, f.id())) {
					continue;
				}
				long cents = 0;
				for (CivicEffects.Effect e : edict.get().cost()) {
					if (e instanceof RealmUpkeep upkeep) {
						cents += upkeep.emeralds() * 100L * (upkeep.per().equals("member") ? realm.members().size() : routes(server, realm));
					}
				}
				if (cents <= 0) {
					continue;
				}
				RealmData.Realm now = data.realm(realm.id());
				if (now != null && RealmTreasury.spend(server, now, cents, Component.translatable("message.aliveworkplace.realm_edict.ledger.upkeep", edict.get().name()))) {
					paid += cents;
				} else {
					lapse(server, realm, f.id(), Component.translatable("chronicle.aliveworkplace.realm_edict.why_money", RealmTreasury.emeralds(cents)));
				}
			}
			for (Edicts.InForce f : List.copyOf(data.laws(realm.id()).edicts())) {
				tryReform(server, data.realm(realm.id()), f.id());
			}
		}
		return paid;
	}

	/** Trade routes between {@code realm}'s members (either way counts once; sister routes too). */
	public static int routes(MinecraftServer server, RealmData.Realm realm) {
		Set<String> pairs = new LinkedHashSet<>();
		for (GlobalPos a : Realms.members(realm)) {
			ServerLevel level = server.getLevel(a.dimension());
			if (level == null) {
				continue;
			}
			for (BlockPos b : Caravans.Data.get(level).routesFrom(a.pos())) {
				GlobalPos other = GlobalPos.of(a.dimension(), b);
				if (realm.has(other)) {
					long x = a.pos().asLong();
					long y = b.asLong();
					pairs.add(Math.min(x, y) + ":" + Math.max(x, y));
				}
			}
		}
		return pairs.size();
	}

	/** Forgets the dawn seen (tests). */
	public static void forget() {
		lastDawn = -1;
	}

	// ---- in words (the Book) --------------------------------------------------------------------------------------------

	/** One realm effect in words for the Book; null for a type that isn't ours. */
	@Nullable
	public static Component describe(CivicEffects.Effect effect) {
		String key = "screen.aliveworkplace.edicts.effect.";
		if (effect instanceof Carry carry) {
			return Words.counted(key + "carry", carry.stacks(), carry.stacks());
		}
		if (effect instanceof RealmEffects.CaravanSpeed speed) {
			return Component.translatable(key + "caravan_speed", speed.percent());
		}
		if (effect instanceof RealmUpkeep upkeep) {
			return Words.counted(key + "realm_upkeep_" + upkeep.per(), upkeep.emeralds(), upkeep.emeralds());
		}
		if (effect instanceof MemberDues dues) {
			return Words.counted(key + "member_dues", dues.emeralds(), dues.emeralds());
		}
		if (effect instanceof CommonBoard) {
			return Component.translatable(key + "common_board");
		}
		if (effect instanceof MemberSpread spread) {
			return spread.points() == 0 ? Component.translatable(key + "member_spread_none") : Component.translatable(key + "member_spread", spread.points());
		}
		if (effect instanceof Levy levy) {
			return Component.translatable(levy.points() >= 0 ? key + "levy_up" : key + "levy_down", Math.abs(levy.points()));
		}
		if (effect instanceof ReliefReach) {
			return Component.translatable(key + "relief_reach");
		}
		if (effect instanceof ReliefBand band) {
			return band.factor() == 2 ? Component.translatable(key + "relief_band.twice") : Component.translatable(key + "relief_band", band.factor());
		}
		if (effect instanceof FestivalTogether) {
			return Component.translatable(key + "festival_together");
		}
		if (effect instanceof FestivalMoodDays days) {
			return Words.counted(key + "festival_mood_days", days.days(), days.days());
		}
		if (effect instanceof RealmFestivalCost cost) {
			return Words.counted(key + "realm_festival_cost", cost.emeralds(), cost.emeralds());
		}
		if (effect instanceof NoFeuds) {
			return Component.translatable(key + "no_feuds");
		}
		if (effect instanceof NoBanditCamps camps) {
			return Component.translatable(key + "no_bandit_camps", camps.radius());
		}
		if (effect instanceof Takings takings) {
			return Component.translatable(takings.percent() >= 0 ? key + "takings_up" : key + "takings_down", Math.abs(takings.percent()));
		}
		if (effect instanceof ColonyRank rank) {
			return Component.translatable(key + "colony_rank", rank.rank().title());
		}
		if (effect instanceof ColonyCooldown cooldown) {
			return cooldown.percent() == 50 ? Component.translatable(key + "colony_cooldown.half") : Component.translatable(key + "colony_cooldown", cooldown.percent());
		}
		if (effect instanceof ColonyCost cost) {
			return Words.counted(key + "colony_cost", cost.emeralds(), cost.emeralds());
		}
		return null;
	}
}
