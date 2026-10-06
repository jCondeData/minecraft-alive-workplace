package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * The Festival Cup (ROADMAP 28.17; the bouts: {@link CupBouts}, 28.18): a village with a Village Hall, a finished Arena, Cobblemon
 * and at least Village rank holds every {@link #EVERY}th of its regular festivals as a Cup. Its circuit is the host and
 * every village with a hall it has a trade route with, either way, {@link #MAX_CIRCUIT} at most, nearest first. Each
 * circuit village sends its Trainer Leader (else its best Trainer); the host adds up to {@link #HOST_TRAINERS} of its
 * other Trainers to fill the bracket; players sign up for a village they may stand for, {@link #PLAYERS_PER_VILLAGE}
 * a village at most. The bracket holds 4, 8 (Town host) or 16 (City host); byes go to the highest seeds. Fewer than
 * {@link #MIN_ENTRANTS}: no Cup, a plain festival.
 *
 * <p>The calendar: a hall's regular festivals fall every {@link Festivals#every} days from its own offset; festival
 * {@code n} (counted from that offset) is a Cup when {@code n % EVERY == 0}. Sign-up opens when the festival before
 * ends, and closes when the Cup's day dawns: then the entrants and bracket are drawn and kept. Players in circuit
 * villages are told when it opens and the evening before.
 */
public final class Cups {
	/** Config {@code festivalCup}. */
	public static boolean ENABLED = true;
	/** Config {@code cupEveryFestivals}: every how many festivals is a Cup. */
	public static int EVERY = 1;
	/** Whether Cobblemon is here (a Cup needs it; the tests of the calendar and entrants stand in for it). */
	public static boolean COBBLEMON = Trainers.COBBLEMON;
	public static final int MAX_CIRCUIT = 7;
	public static final int MIN_ENTRANTS = 4;
	public static final int PLAYERS_PER_VILLAGE = 2;
	public static final int HOST_TRAINERS = 2;
	/** When players are told the Cup is tomorrow (dusk). */
	public static final long EVENING = 12000;
	static final long DAY = 24000;

	private Cups() {
	}

	// ---------------------------------------------------------------- hosts and circuit

	/** Why the village round {@code hall} can't host a Cup (a lang key), or null if it can. */
	@Nullable
	public static String hostProblem(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return "screen.aliveworkplace.cup.why.off";
		}
		if (!COBBLEMON) {
			return "screen.aliveworkplace.cup.why.cobblemon";
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return "screen.aliveworkplace.cup.why.hall";
		}
		if (entity.rank().ordinal() < VillageRanks.Rank.VILLAGE.ordinal()) {
			return "screen.aliveworkplace.cup.why.rank";
		}
		if (!Arenas.has(level, hall)) {
			return "screen.aliveworkplace.cup.why.arena";
		}
		return null;
	}

	public static boolean canHost(ServerLevel level, BlockPos hall) {
		return hostProblem(level, hall) == null;
	}

	/** How many the bracket holds: 4, 8 for a Town host, 16 for a City. */
	public static int capacity(VillageRanks.Rank rank) {
		return rank == VillageRanks.Rank.CITY ? 16 : rank == VillageRanks.Rank.TOWN ? 8 : 4;
	}

	/** The host and every village with a hall it has a trade route with (either way), nearest first, {@link #MAX_CIRCUIT} at most. */
	public static List<BlockPos> circuit(ServerLevel level, BlockPos host) {
		Caravans.Data data = Caravans.Data.get(level);
		List<BlockPos> partners = new ArrayList<>(data.partners(host).stream().filter(p -> data.village(p) != null).toList());
		partners.sort(Comparator.comparingDouble(p -> p.distSqr(host)));
		List<BlockPos> out = new ArrayList<>();
		out.add(host.immutable());
		for (BlockPos p : partners) {
			if (out.size() >= MAX_CIRCUIT) {
				break;
			}
			out.add(p);
		}
		return out;
	}

	/** The host whose Cup the village round {@code hall} belongs to: itself if it hosts, else the nearest host whose circuit has it. */
	public static Optional<BlockPos> hostFor(ServerLevel level, BlockPos hall) {
		if (canHost(level, hall)) {
			return Optional.of(hall);
		}
		return CupData.get(level).all().keySet().stream()
			.filter(h -> !h.equals(hall) && (!level.isLoaded(h) || canHost(level, h)))
			.filter(h -> circuit(level, h).contains(hall))
			.min(Comparator.comparingDouble(h -> h.distSqr(hall)));
	}

	// ---------------------------------------------------------------- calendar

	/** Whether festival number {@code n} (counted from the hall's offset) is a Cup, with a Cup every {@code every} festivals. */
	public static boolean isCup(long n, int every) {
		return Math.floorMod(n, Math.max(1, every)) == 0;
	}

	/**
	 * The first day from {@code from} on that is a regular festival (every {@code gap} days from {@code offset}) and a
	 * Cup (every {@code every}th of them).
	 */
	public static long cupDay(long from, long offset, int gap, int every) {
		gap = Math.max(1, gap);
		long day = from + Math.floorMod(offset - from, gap);
		long n = Math.floorDiv(day - offset, gap);
		return day + Math.floorMod(-n, Math.max(1, every)) * (long) gap;
	}

	/**
	 * The day (as the chronicle counts them, from 1) of the next Cup at {@code host} whose bouts aren't over yet at
	 * {@code now} (the level's day time).
	 */
	public static long upcoming(BlockPos host, VillageHallBlockEntity entity, long now, CupThemes.Theme theme) {
		long end = theme == null ? CupThemes.MIDNIGHT : theme.end();
		long from = Math.floorDiv(now - end, DAY) + 2;
		return cupDay(from, Festivals.offset(host), Festivals.every(entity), EVERY);
	}

	/** The day sign-up opens for a Cup on {@code day}: when the festival before it ends. */
	public static long opensDay(long day, VillageHallBlockEntity entity) {
		return day - Festivals.every(entity);
	}

	public static boolean signupOpen(ServerLevel level, VillageHallBlockEntity entity, CupData.Cup cup) {
		long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		long opens = opensDay(cup.day, entity);
		return !cup.closed && (today > opens || today == opens && level.getDayTime() % DAY >= io.github.jcondedata.aliveworkplace.hall.Curfew.festivalEnd(entity));
	}

	// ---------------------------------------------------------------- delegates (and the Leader record on the caravans' list)

	/** The village's delegate, live: its senior Trainer Leader, else its best Trainer; null if it has neither. */
	@Nullable
	public static Caravans.Leader liveDelegate(ServerLevel level, BlockPos hall) {
		List<Villager> trainers = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && Trainers.isTrainer(v) && !CupDays.isDelegate(v));
		Villager best = trainers.stream().min(seniority()).orElse(null);
		return best == null ? null : record(best);
	}

	/** Leaders first, then by tier, experience and id. */
	static Comparator<Villager> seniority() {
		return Comparator.<Villager>comparingInt(v -> Trainers.isLeader(v) ? 0 : 1)
			.thenComparingInt(v -> -Trainers.tier(v)).thenComparingInt(v -> -v.getVillagerXp()).thenComparing(Villager::getUUID);
	}

	static Caravans.Leader record(Villager v) {
		return new Caravans.Leader(v.getUUID(), v.hasCustomName() ? v.getCustomName().getString() : "", Trainers.tier(v), v.getVillagerXp(),
			Trainers.isLeader(v));
	}

	/** The village's delegate: live where it's loaded, else as its hall's last round wrote it on the caravans' list. */
	@Nullable
	public static Caravans.Leader delegate(ServerLevel level, BlockPos hall) {
		return delegate(level, hall, level.isLoaded(hall));
	}

	@Nullable
	public static Caravans.Leader delegate(ServerLevel level, BlockPos hall, boolean loaded) {
		return loaded ? liveDelegate(level, hall) : Caravans.Data.get(level).leader(hall);
	}

	// ---------------------------------------------------------------- entrants, seeding and byes

	/** The players' right to stand for {@code village}: it's theirs, its owner made them a friend, or nobody owns it. */
	public static boolean mayStandFor(ServerLevel level, UUID player, BlockPos village) {
		if (!(level.getBlockEntity(village) instanceof VillageHallBlockEntity entity) || entity.owner() == null) {
			return true;
		}
		return Friends.get(level.getServer()).mayDirect(entity.owner(), player);
	}

	/** Everyone who'd be in the host's Cup now, seeded: the circuit's delegates, the players, the host's Trainers to fill it. */
	public static List<CupData.Entrant> entrants(ServerLevel level, BlockPos host, CupData.Cup cup) {
		int capacity = capacity(VillageRanks.of(level, host));
		List<CupData.Entrant> out = new ArrayList<>();
		Set<UUID> in = new LinkedHashSet<>();
		List<BlockPos> circuit = circuit(level, host);
		for (BlockPos village : circuit) {
			Caravans.Leader d = delegate(level, village);
			if (d != null && in.add(d.id())) {
				out.add(new CupData.Entrant(d.leader() ? CupData.Kind.LEADER : CupData.Kind.TRAINER, d.id(), d.name(), d.tier(), d.xp(), village));
			}
		}
		for (CupData.Signup s : cup.signups) {
			if (circuit.contains(s.village()) && in.add(s.player())) {
				out.add(new CupData.Entrant(CupData.Kind.PLAYER, s.player(), s.name(), 0, 0, s.village()));
			}
		}
		if (level.isLoaded(host)) {
			List<Villager> mine = level.getEntitiesOfClass(Villager.class, VillageHalls.area(host), v -> v.isAlive() && Trainers.isTrainer(v)
				&& !CupDays.isDelegate(v) && !in.contains(v.getUUID()));
			mine.sort(seniority());
			for (int i = 0; i < mine.size() && i < HOST_TRAINERS && out.size() < capacity; i++) {
				Caravans.Leader t = record(mine.get(i));
				in.add(t.id());
				out.add(new CupData.Entrant(CupData.Kind.HOST_TRAINER, t.id(), t.name(), t.tier(), t.xp(), host));
			}
		}
		return seed(out, CupChampions.defending(cup, out));
	}

	/** Seeds as {@link #seed(List)}, with the defending champion ({@code defending}, if entered) first. */
	public static List<CupData.Entrant> seed(List<CupData.Entrant> entrants, @Nullable UUID defending) {
		List<CupData.Entrant> out = seed(entrants);
		if (defending != null) {
			for (int i = 0; i < out.size(); i++) {
				if (out.get(i).id().equals(defending)) {
					out.add(0, out.remove(i));
					break;
				}
			}
		}
		return out;
	}

	/** Seeds: the villages' Leaders and Trainers by tier (a Leader first at a tier, then experience), then players, then the host's Trainers. */
	public static List<CupData.Entrant> seed(List<CupData.Entrant> entrants) {
		List<CupData.Entrant> out = new ArrayList<>(entrants);
		out.sort(Comparator.<CupData.Entrant>comparingInt(e -> switch (e.kind()) {
				case LEADER, TRAINER -> 0;
				case PLAYER -> 1;
				case HOST_TRAINER -> 2;
			}).thenComparingInt(e -> e.kind() == CupData.Kind.PLAYER ? 0 : -e.tier())
			.thenComparingInt(e -> e.kind() == CupData.Kind.LEADER ? 0 : 1)
			.thenComparingInt(e -> e.kind() == CupData.Kind.PLAYER ? 0 : -e.xp()));
		return out;
	}

	/** Seed numbers (1-based) in bracket order for a bracket of {@code size} (a power of two): 1 v N, then so on, the top seeds apart. */
	public static int[] order(int size) {
		int[] seeds = {1};
		while (seeds.length < size) {
			int n = seeds.length * 2;
			int[] next = new int[n];
			for (int i = 0; i < seeds.length; i++) {
				next[2 * i] = seeds[i];
				next[2 * i + 1] = n + 1 - seeds[i];
			}
			seeds = next;
		}
		return seeds;
	}

	/**
	 * The bracket for {@code seeded} entrants in a Cup that holds {@code capacity}: the smallest of 4, 8 and 16 that fits
	 * them (no more than {@code capacity}; the lowest seeds left out of a full one), byes (null) against the highest
	 * seeds. Empty if there are fewer than {@link #MIN_ENTRANTS}.
	 */
	public static List<CupData.Entrant> bracket(List<CupData.Entrant> seeded, int capacity) {
		List<CupData.Entrant> out = new ArrayList<>();
		if (seeded.size() < MIN_ENTRANTS) {
			return out;
		}
		int size = MIN_ENTRANTS;
		while (size < seeded.size() && size < capacity) {
			size *= 2;
		}
		for (int seed : order(size)) {
			out.add(seed <= seeded.size() ? seeded.get(seed - 1) : null);
		}
		return out;
	}

	/** How many byes a bracket has. */
	public static int byes(List<CupData.Entrant> bracket) {
		return (int) bracket.stream().filter(java.util.Objects::isNull).count();
	}

	// ---------------------------------------------------------------- the hall's round

	/** The hall's round: its delegate written on the caravans' list; a host's Cup kept up to date. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		Caravans.Data caravans = Caravans.Data.get(level);
		if (caravans.village(hall) != null) {
			caravans.setLeader(hall, liveDelegate(level, hall));
		}
		CupBouts.payBanked(level, hall); // 28.18: XP its trainers earned at a Cup while the village was away
		CupDays.writeNotes(level, hall); // 28.19: a Cup's chronicle entry from while the village was away
		CupChampions.round(level, hall); // 28.21: the Cup banner up over a holder (one away at the final too), down when the title passed
		if (!canHost(level, hall)) {
			return;
		}
		CupData data = CupData.get(level);
		CupData.Cup cup = data.cup(hall);
		long now = level.getDayTime();
		if (cup.theme == null || CupThemes.get(cup.theme) == null) {
			CupThemes.Theme next = CupThemes.next(cup.lastTheme);
			if (next == null) {
				return; // no themes loaded: no Cup
			}
			cup.theme = next.id();
			cup.themePicked = false;
		}
		CupThemes.Theme theme = CupThemes.get(cup.theme);
		if (cup.day >= 0 && cup.closed && now >= (cup.day - 1) * DAY + theme.end()) {
			CupDays.settle(level, hall, cup); // the day is over: any bout left is settled as an exhibition (28.19)
			over(cup);
			theme = CupThemes.get(cup.theme);
			if (theme == null) {
				data.setDirty();
				return;
			}
		}
		if (!cup.closed && cup.postponed == 0) { // a Cup put off keeps its new day (28.19)
			cup.day = upcoming(hall, entity, now, theme);
		}
		long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		if (signupOpen(level, entity, cup) && cup.toldOpen != cup.day) {
			cup.toldOpen = cup.day;
			tell(level, hall, Component.translatable("message.aliveworkplace.cup.open", Component.translatable(theme.name()),
				VillageHalls.name(level, hall), cup.day));
		}
		if (today == cup.day - 1 && now % DAY >= EVENING && cup.toldEvening != cup.day) {
			cup.toldEvening = cup.day;
			tell(level, hall, Component.translatable("message.aliveworkplace.cup.tomorrow", Component.translatable(theme.name()),
				VillageHalls.name(level, hall), hour(theme.start())));
		}
		if (!cup.closed && today >= cup.day) {
			close(level, hall, cup);
		}
		data.setDirty();
	}

	/** Sign-up closes: the entrants and bracket are drawn; too few, no Cup (and why). */
	public static void close(ServerLevel level, BlockPos host, CupData.Cup cup) {
		cup.closed = true;
		cup.entrants.clear();
		cup.entrants.addAll(entrants(level, host, cup));
		cup.bracket.clear();
		cup.bracket.addAll(bracket(cup.entrants, capacity(VillageRanks.of(level, host))));
		cup.noCup = cup.bracket.isEmpty() ? "screen.aliveworkplace.cup.why.few" : "";
	}

	/** The Cup's day is over: the next one, with the next theme in order. */
	public static void over(CupData.Cup cup) {
		cup.lastTheme = cup.theme;
		CupThemes.Theme next = CupThemes.next(cup.theme);
		cup.theme = next == null ? null : next.id();
		cup.themePicked = false;
		cup.closed = false;
		cup.noCup = "";
		cup.signups.clear();
		cup.entrants.clear();
		cup.bracket.clear();
		cup.results.clear();
		cup.postponed = 0;
		cup.cheered = 0;
		cup.finaleTime = -1;
	}

	/** Tells the players in the host's circuit villages. */
	static void tell(ServerLevel level, BlockPos host, Component message) {
		List<BlockPos> circuit = circuit(level, host);
		for (ServerPlayer player : level.players()) {
			if (circuit.stream().anyMatch(v -> VillageHalls.area(v).contains(player.position()))) {
				Chat.chat(player, message.copy().withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** "noon", "midnight", "dusk", "dawn", else "15:00" (a day's tick 0 is 6 in the morning). */
	public static Component hour(long tick) {
		long t = Math.floorMod(tick, DAY);
		if (t == 6000) {
			return Component.translatable("screen.aliveworkplace.cup.hour.noon");
		}
		if (t == 18000) {
			return Component.translatable("screen.aliveworkplace.cup.hour.midnight");
		}
		if (t == 12000) {
			return Component.translatable("screen.aliveworkplace.cup.hour.dusk");
		}
		if (t == 0) {
			return Component.translatable("screen.aliveworkplace.cup.hour.dawn");
		}
		return Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", (t / 1000 + 6) % 24, t % 1000 * 60 / 1000));
	}

	// ---------------------------------------------------------------- the players' sign-up, and the owner's theme

	/** {@code player} signs up to stand for the village round {@code village}; returns what to tell them. */
	public static Component signUp(ServerLevel level, BlockPos village, ServerPlayer player) {
		Optional<BlockPos> host = hostFor(level, village);
		if (host.isEmpty()) {
			return Component.translatable("message.aliveworkplace.cup.no_host").withStyle(ChatFormatting.YELLOW);
		}
		CupData data = CupData.get(level);
		CupData.Cup cup = data.cup(host.get());
		if (cup.closed || cup.day < 0) {
			return Component.translatable("message.aliveworkplace.cup.closed").withStyle(ChatFormatting.YELLOW);
		}
		if (!mayStandFor(level, player.getUUID(), village)) {
			return Component.translatable("message.aliveworkplace.cup.not_yours", VillageHalls.name(level, village)).withStyle(ChatFormatting.YELLOW);
		}
		if (cup.signups.stream().anyMatch(s -> s.player().equals(player.getUUID()))) {
			return Component.translatable("message.aliveworkplace.cup.already").withStyle(ChatFormatting.YELLOW);
		}
		if (cup.signups.stream().filter(s -> s.village().equals(village)).count() >= PLAYERS_PER_VILLAGE) {
			return Component.translatable("message.aliveworkplace.cup.full", VillageHalls.name(level, village), PLAYERS_PER_VILLAGE)
				.withStyle(ChatFormatting.YELLOW);
		}
		cup.signups.add(new CupData.Signup(player.getUUID(), player.getGameProfile().getName(), village.immutable()));
		data.setDirty();
		return Component.translatable("message.aliveworkplace.cup.signed_up", VillageHalls.name(level, village)).withStyle(ChatFormatting.GREEN);
	}

	/** {@code player} withdraws from the Cup the village round {@code village} belongs to. */
	public static Component withdraw(ServerLevel level, BlockPos village, ServerPlayer player) {
		Optional<BlockPos> host = hostFor(level, village);
		CupData data = CupData.get(level);
		CupData.Cup cup = host.map(data::existing).orElse(null);
		if (cup == null || cup.signups.stream().noneMatch(s -> s.player().equals(player.getUUID()))) {
			return Component.translatable("message.aliveworkplace.cup.not_signed_up").withStyle(ChatFormatting.YELLOW);
		}
		if (cup.closed) {
			return Component.translatable("message.aliveworkplace.cup.closed").withStyle(ChatFormatting.YELLOW);
		}
		cup.signups.removeIf(s -> s.player().equals(player.getUUID()));
		data.setDirty();
		return Component.translatable("message.aliveworkplace.cup.withdrawn").withStyle(ChatFormatting.GRAY);
	}

	/** The host's owner (or an operator; anyone while nobody owns it) picks the next theme in order instead, until sign-up closes. */
	public static Component nextTheme(ServerLevel level, BlockPos host, ServerPlayer player) {
		CupData data = CupData.get(level);
		CupData.Cup cup = data.existing(host);
		if (cup == null || !canHost(level, host)) {
			return Component.translatable("message.aliveworkplace.cup.no_host").withStyle(ChatFormatting.YELLOW);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(host);
		if (entity.owner() != null && !entity.owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
			return Component.translatable("message.aliveworkplace.cup.owner_only").withStyle(ChatFormatting.YELLOW);
		}
		if (cup.closed) {
			return Component.translatable("message.aliveworkplace.cup.closed").withStyle(ChatFormatting.YELLOW);
		}
		CupThemes.Theme next = CupThemes.next(cup.theme);
		if (next == null) {
			return Component.translatable("message.aliveworkplace.cup.no_host").withStyle(ChatFormatting.YELLOW);
		}
		cup.theme = next.id();
		cup.themePicked = true;
		data.setDirty();
		return Component.translatable("message.aliveworkplace.cup.theme_picked", Component.translatable(next.name())).withStyle(ChatFormatting.GREEN);
	}

	/** The theme of a Cup, or null. */
	@Nullable
	public static CupThemes.Theme theme(CupData.Cup cup) {
		return CupThemes.get(cup.theme);
	}

	/** Who stands for whom in a Cup: its drawn entrants once closed, else who'd be in it now. */
	public static List<CupData.Entrant> shownEntrants(ServerLevel level, BlockPos host, CupData.Cup cup) {
		return cup.closed ? List.copyOf(cup.entrants) : entrants(level, host, cup);
	}

}
