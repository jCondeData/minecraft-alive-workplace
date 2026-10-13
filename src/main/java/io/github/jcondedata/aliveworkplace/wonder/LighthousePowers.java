package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.travel.TravelPostBlock;
import io.github.jcondedata.aliveworkplace.wonder.powers.DeclaredPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * The Lighthouse's powers (ROADMAP 35.12), read wherever the feature they change works out its numbers. Each answers
 * only while a finished, standing Wonder of the village grants the type ({@link Wonders#power}: a damaged one, or
 * Wonders off, turns them all off), so any Wonder file that names the type has the power, not only the Lighthouse's:
 * <ul>
 * <li>{@code caravan_range} ({@code factor}, 2): caravans from the village reach villages that much further than
 * {@link Caravans#RANGE} (its trade routes list, {@link Caravans#neighbours});</li>
 * <li>{@code caravan_speed} ({@code factor}, 0.5): a caravan from the village takes that share of its trip
 * ({@link Caravans#trip});</li>
 * <li>{@code ferry_fares} ({@code factor}, 0.5): a ferry fare to or from a Travel Post of the village is that share
 * ({@link TravelNetwork#fare}), at least an emerald; and the Wonder's own Travel Post (the Lighthouse's jetty) joins
 * the network under the village's name, "Thornholm Lighthouse";</li>
 * <li>{@code early_warning}: the keeper is a source of 32.14's day's warning ({@link Warnings#SOURCES}) for raiders
 * who come from the sea (M32's drowned pirates, any culture arriving at the {@code shore}): the day before, everyone in
 * the village is told and the chronicle has it; at dusk on the day the guards gather on the shore.</li>
 * </ul>
 * Nothing here is saved: the powers come from the Wonder, the jetty post's name from the network's own save.
 */
public final class LighthousePowers {
	public static final String CARAVAN_RANGE = "caravan_range";
	public static final String CARAVAN_SPEED = "caravan_speed";
	public static final String FERRY_FARES = "ferry_fares";
	public static final String EARLY_WARNING = "early_warning";
	/** How often (ticks) the jetty posts and the shore watch are looked over. */
	public static final int EVERY = 100;

	/** Each finished site's Travel Post, where its drawing has one (worked out once per site). */
	private static final Map<UUID, Optional<BlockPos>> POSTS = new ConcurrentHashMap<>();
	/** Each village's shore spot for tonight's watch, by hall, with the day it was found for. */
	private static final Map<GlobalPos, Shore> SHORES = new ConcurrentHashMap<>();

	private record Shore(long day, @Nullable BlockPos spot) {
	}

	public static void init() {
		Warnings.SOURCES.add(LighthousePowers::source);
		Platform.get().onLevelTick(LighthousePowers::tick);
	}

	/** The power's {@code factor} for the village round {@code hall}, or 1 without it (kept between 1/16 and 16). */
	static double factor(ServerLevel level, BlockPos hall, String type, double fallback) {
		Optional<WonderPower> power = Wonders.power(level, hall, type);
		if (power.isEmpty()) {
			return 1;
		}
		double f = power.get() instanceof DeclaredPower d ? d.number("factor", fallback) : fallback;
		return Math.max(1 / 16.0, Math.min(16, f));
	}

	/** How far caravans from the village round {@code hall} reach: {@link Caravans#RANGE}, times {@code caravan_range}'s factor. */
	public static int caravanRange(ServerLevel level, BlockPos hall) {
		return (int) Math.round(Caravans.RANGE * Math.max(1, factor(level, hall, CARAVAN_RANGE, 2)));
	}

	/** A trip of {@code ticks} from the village round {@code from}: that share of it with {@code caravan_speed}. */
	public static long caravanTrip(ServerLevel level, BlockPos from, long ticks) {
		double f = factor(level, from, CARAVAN_SPEED, 0.5);
		return f == 1 ? ticks : Math.max(1, Math.round(ticks * f));
	}

	/** The fare between posts {@code from} and {@code to}: {@code fare} times {@code ferry_fares}'s factor when either is in a village with it. */
	public static int fare(MinecraftServer server, GlobalPos from, GlobalPos to, int fare) {
		double f = Math.min(postFactor(server, from), postFactor(server, to));
		return f >= 1 ? fare : Math.max(1, (int) Math.round(fare * f));
	}

	private static double postFactor(MinecraftServer server, GlobalPos post) {
		ServerLevel level = server.getLevel(post.dimension());
		if (level == null) {
			return 1;
		}
		BlockPos hall = hallOf(level, post.pos());
		return hall == null ? 1 : factor(level, hall, FERRY_FARES, 0.5);
	}

	/** The hall of the village {@code pos} is in: the nearest on the caravans' list within {@link VillageHalls#RADIUS} (known unloaded too). */
	@Nullable
	static BlockPos hallOf(ServerLevel level, BlockPos pos) {
		BlockPos best = null;
		double bestFar = (double) VillageHalls.RADIUS * VillageHalls.RADIUS;
		for (Caravans.Village v : Caravans.Data.get(level).villages()) {
			double d = v.hall().distSqr(pos);
			if (d <= bestFar) {
				best = v.hall();
				bestFar = d;
			}
		}
		if (best == null) {
			best = VillageHalls.nearest(level, pos).orElse(null);
		}
		return best;
	}

	/** "Thornholm Lighthouse": the village's name and its Wonder's. */
	public static Component postName(ServerLevel level, WonderSite site) {
		return Component.translatable("message.aliveworkplace.lighthouse.post", VillageHalls.name(level, site.hall()), Wonders.name(site.wonder()));
	}

	// ---- the early warning ----

	/** Whether {@code attack} comes from the sea (its culture arrives at the shore: the drowned pirates). */
	public static boolean fromTheSea(ThreatData.Attack attack) {
		return Threats.get(attack.culture()).map(c -> c.arrival() == Culture.Arrival.SHORE).orElse(false);
	}

	/** The next attack on the village's clock, if it comes from the sea. */
	static Optional<ThreatData.Attack> seaAttack(ServerLevel level, BlockPos hall) {
		long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		return ThreatData.get(level).attacks(hall).stream().filter(a -> a.day() >= today).findFirst().filter(LighthousePowers::fromTheSea);
	}

	/** 32.14's source: the keeper of the village's Wonder with {@code early_warning}, for an attack from the sea. */
	static Optional<Warnings.Source> source(ServerLevel level, BlockPos hall) {
		if (Wonders.powers(level, hall, EARLY_WARNING).isEmpty() || seaAttack(level, hall).isEmpty()) {
			return Optional.empty();
		}
		WonderSite site = Wonders.powers(level, hall, EARLY_WARNING).get(0).site();
		return Optional.of(new Warnings.Source(Component.translatable("message.aliveworkplace.lighthouse.keeper", postName(level, site)), false));
	}

	/**
	 * Where the guards of the village round {@code hall} gather against {@code attack}: the village's beach nearest the
	 * pirates' ship, else nearest the Wonder; null when the village has no shore.
	 */
	@Nullable
	public static BlockPos shore(ServerLevel level, BlockPos hall, ThreatData.Attack attack, WonderSite site) {
		BlockPos from = Lairs.of(level, hall, attack.culture()).map(Lairs.Lair::pos)
			.orElseGet(() -> site.placement().map(p -> p.origin()).orElse(hall));
		return Ships.beach(level, hall, from);
	}

	/**
	 * The shore watch: on the evening of a warned attack from the sea, the village's guards gather on the shore (as they
	 * line up at a dedication; the bell, when the raid comes, takes over). Returns how many were sent.
	 */
	public static int watch(ServerLevel level, BlockPos hall) {
		if (!Warnings.evening(level, hall) || Wonders.powers(level, hall, EARLY_WARNING).isEmpty()) {
			return 0;
		}
		Optional<ThreatData.Attack> attack = Warnings.warned(level, hall).filter(LighthousePowers::fromTheSea);
		if (attack.isEmpty()) {
			return 0;
		}
		long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		GlobalPos key = GlobalPos.of(level.dimension(), hall);
		Shore shore = SHORES.get(key);
		if (shore == null || shore.day() != day) {
			shore = new Shore(day, shore(level, hall, attack.get(), Wonders.powers(level, hall, EARLY_WARNING).get(0).site()));
			SHORES.put(key, shore);
		}
		if (shore.spot() == null) {
			return 0;
		}
		int sent = 0;
		long until = level.getGameTime() + 2L * EVERY;
		for (Villager guard : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby() && Guards.isGuard(v))) {
			GuardRally.lineUp(guard, shore.spot().offset(sent % 5 - 2, 0, sent / 5), until);
			sent++;
		}
		return sent;
	}

	// ---- the jetty's post ----

	/**
	 * Puts the finished {@code site}'s Travel Post (the Lighthouse's jetty) on the network under the village's name, when
	 * the Wonder grants {@code ferry_fares} and the post stands. Returns the post, or null.
	 */
	@Nullable
	public static TravelNetwork.Post jetty(ServerLevel level, WonderSite site) {
		if (!site.empowered() || Wonders.power(level, site.hall(), FERRY_FARES).isEmpty()) {
			return null;
		}
		Optional<BlockPos> pos = POSTS.computeIfAbsent(site.id(), id -> WonderUpkeep.blocks(level, site).stream()
			.filter(b -> b.state().getBlock() instanceof TravelPostBlock).map(WonderUpkeep.Block::pos).findFirst());
		if (pos.isEmpty() || !level.isLoaded(pos.get()) || !(level.getBlockState(pos.get()).getBlock() instanceof TravelPostBlock)) {
			return null;
		}
		TravelNetwork network = TravelNetwork.get(level.getServer());
		GlobalPos where = GlobalPos.of(level.dimension(), pos.get());
		String name = postName(level, site).getString();
		TravelNetwork.Post post = network.at(where);
		return post != null && post.name().equals(name) ? post : network.add(where, name);
	}

	static void tick(ServerLevel level) {
		if (!Wonders.ENABLED || level.getGameTime() % EVERY != 0) {
			return;
		}
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (!site.empowered()) {
				continue;
			}
			jetty(level, site);
			watch(level, site.hall());
		}
	}

	/** Forgets the posts found in drawings (the files were read again). */
	public static void forget() {
		POSTS.clear();
		SHORES.clear();
	}

	private LighthousePowers() {
	}
}
