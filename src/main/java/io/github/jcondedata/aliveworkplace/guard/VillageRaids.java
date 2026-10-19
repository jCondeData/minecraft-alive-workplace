package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.threat.Conditions;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Village raids: at night a village with a Village Hall and at least {@link #MIN_VILLAGERS} villagers may be raided by
 * monsters — the bigger the village, the likelier and the larger the raid, and the stronger its guards, the more of the
 * raiders come in iron. They gather at the edge of the village and go for its villagers; the bell rings (so guards rally
 * and everyone else hides) and players in the village are told. The raid is over when the raiders are dead, or at dawn,
 * when the last ones flee. Guards fight anywhere in the village while it's raided (and in a vanilla pillager raid's
 * whole area). {@code villageRaids} in the config turns our raids off.
 *
 * <p>Who comes is data (ROADMAP 32.2, {@code threat/}): {@link #start} takes the culture of the lair standing by the
 * village ({@link Lairs}, 32.3: the bandit camp), or else picks one by weight among the cultures whose {@code where}
 * fits (the monsters), and spawns its roster. A raid from a lair takes its raiders from the lair's strength: never more
 * than the band it has at home; those alive when the raid ends rejoin it, the dead are gone. Raids under way are kept in the saved {@link ThreatData}, so a restart mid-raid carries on.
 * <b>The threat clock:</b> at dusk, in the hall's round, each hall rolls the attack for the <i>next</i> night (with
 * {@link #chance} and {@link #REST_DAYS}), so there is a day in which a warning can be given; the attack then comes
 * that night at its hour, unless its culture was switched off or its lair broken up meanwhile.
 * A culture that lays sieges gathers beyond the village's outermost finished wall on its side
 * ({@code Sieges.outside}, 32.5).
 */
public final class VillageRaids {
	public static boolean ENABLED = true;
	/** Villages smaller than this are left alone. */
	public static int MIN_VILLAGERS = 8;
	/** At least this many days between raids on one village. */
	public static final int REST_DAYS = 3;
	/** The tag every raider carries. */
	public static final String TAG = "aliveworkplace_raider";
	/** Most raiders in one raid. */
	static final int MAX_RAIDERS = 16;
	/** The time of day (18:00) from which a hall's round rolls the next night's attack. */
	public static final long DUSK = 12000;

	/** A raid on the village round {@code hall}: its raiders, how many came, when it began. */
	public record Raid(BlockPos hall, int raiders, long began) {
	}

	private static Raid raid(ThreatData.Under raid) {
		return new Raid(raid.hall(), raid.raiders(), raid.began());
	}

	/** The raid on the village round {@code hall} right now, if any. */
	public static Optional<Raid> active(BlockPos hall) {
		return ThreatData.anywhere(hall).map(VillageRaids::raid);
	}

	/**
	 * Whether the village round {@code hall} is raided now: one of our raids (monsters or bandits) on it, or a vanilla
	 * pillager raid at {@code hall} or at {@code at} (Conscription, 30.10).
	 */
	public static boolean raided(ServerLevel level, BlockPos hall, BlockPos at) {
		return ThreatData.get(level).raid(hall).isPresent() || vanillaRaid(level, hall) || !at.equals(hall) && vanillaRaid(level, at);
	}

	private static boolean vanillaRaid(ServerLevel level, BlockPos pos) {
		net.minecraft.world.entity.raid.Raid vanilla = level.getRaidAt(pos);
		return vanilla != null && vanilla.isActive();
	}

	/**
	 * Counts a raid on the village round {@code hall} as under way from now, with {@code raiders} raiders (the mobs carrying
	 * {@link #TAG} near it): the showcase scenes and tests start one this way, without the gathering and the horn. It ends
	 * as every raid does, when those raiders are gone or fled at dawn.
	 */
	public static Raid track(ServerLevel level, BlockPos hall, int raiders) {
		return begin(level, hall, Threats.MONSTERS, raiders);
	}

	/**
	 * As {@link #track(ServerLevel, BlockPos, int)}, a raid by {@code culture} whose {@code raiders} already stand in the
	 * world (each carrying {@link #TAG}): its tactics begin as they do in {@link #start}, so a culture that rams gates
	 * lays its siege (32.4).
	 */
	public static Raid track(ServerLevel level, BlockPos hall, Culture culture, List<Mob> raiders) {
		Raid raid = begin(level, hall, culture.id(), raiders.size());
		Threats.tactics(culture).forEach(t -> t.begin(level, hall, culture, raiders));
		return raid;
	}

	private static Raid begin(ServerLevel level, BlockPos hall, net.minecraft.resources.ResourceLocation culture, int raiders) {
		ThreatData.Under raid = new ThreatData.Under(hall.immutable(), culture, raiders, level.getGameTime());
		ThreatData.get(level).begin(raid);
		io.github.jcondedata.aliveworkplace.wonder.powers.WonderBells.ring(level, hall, io.github.jcondedata.aliveworkplace.wonder.powers.WonderBells.Reason.RAID); // the Cathedral's bells (35.10)
		return raid(raid);
	}

	/**
	 * Where a guard with its post (or rally point) at {@code center} should look for foes while a raid is on: the whole
	 * village being raided, or a vanilla raid's area; empty when there's no raid there.
	 */
	public static Optional<AABB> raidArea(ServerLevel level, BlockPos center) {
		Optional<ThreatData.Under> raid = ThreatData.get(level).raidNear(center, VillageHalls.RADIUS);
		if (raid.isPresent()) {
			return Optional.of(new AABB(raid.get().hall()).inflate(VillageHalls.RADIUS, 16, VillageHalls.RADIUS));
		}
		net.minecraft.world.entity.raid.Raid vanilla = level.getRaidAt(center);
		if (vanilla != null && vanilla.isActive()) {
			return Optional.of(new AABB(vanilla.getCenter()).inflate(48, 16, 48));
		}
		return Optional.empty();
	}

	/**
	 * The chance a night that the village of {@code villagers} round {@code hall} is raided: {@link #nightlyChance}, twice
	 * that with a lair near (its raiders come from their camp), times {@link Threats#chanceFactor} (the {@code curfew}
	 * effects' {@code raids}: Curfew, 30.9, half as likely; research's {@code raid_chance}, 29.11; whatever else adds itself).
	 */
	public static float chance(ServerLevel level, BlockPos hall, int villagers) {
		return nightlyChance(villagers) * (Lairs.near(level, hall).isPresent() ? 2 : 1) * Threats.chanceFactor(level, hall)
			* Threats.scale(level, hall); // the threat level and At peace (32.21)
	}

	/** The chance a night that a village of {@code villagers} is raided. */
	public static float nightlyChance(int villagers) {
		return villagers < MIN_VILLAGERS ? 0f : Math.min(0.35f, 0.15f + 0.01f * (villagers - MIN_VILLAGERS));
	}

	static boolean isNight(ServerLevel level) {
		long time = level.getDayTime() % VillageNeeds.DAY;
		return time >= 13500 && time < 22000;
	}

	/**
	 * The hall's round: a raid under way is checked on (and ended); the attack the clock set for tonight begins at its
	 * hour; at dusk the clock rolls the next night's.
	 */
	public static void tick(ServerLevel level, BlockPos hall, int villagers, int guards, long lastRaidDay, java.util.function.LongConsumer raided) {
		ThreatData data = ThreatData.get(level);
		ThreatData.Under raid = data.raid(hall).orElse(null);
		long time = level.getDayTime() % VillageNeeds.DAY;
		if (raid != null) {
			List<Mob> left = raiders(level, hall);
			Optional<Culture> culture = Threats.get(raid.culture());
			boolean sea = culture.map(c -> c.arrival() == Culture.Arrival.SHORE).orElse(false);
			// Most raiders leave at dawn; a culture whose hours are until_noon stays through the morning.
			boolean over = culture.map(c -> c.hours() == Culture.Hours.UNTIL_NOON).orElse(false) ? time >= 6000 && time < 13500 : !isNight(level);
			if (left.isEmpty()) {
				end(level, hall, raid, false, 0);
			} else if (over && level.getGameTime() - raid.began() > 1200) {
				// Those of the lair's band who lived rejoin it; the extras and a captain who led them don't count (32.7).
				int band = (int) left.stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG) && !Lairs.leading(m)).count();
				left.forEach(m -> {
					culture.ifPresent(c -> Threats.tactics(c).forEach(t -> t.leave(level, hall, c, m))); // (what they carry goes with them, 32.10)
					// (those who came from the sea go back into it, 32.8)
					level.sendParticles(sea ? net.minecraft.core.particles.ParticleTypes.SPLASH : net.minecraft.core.particles.ParticleTypes.POOF,
						m.getX(), m.getY() + 0.5, m.getZ(), sea ? 30 : 10, 0.3, 0.5, 0.3, 0.02);
					if (Lairs.leading(m)) {
						Lairs.comeHome(level, m); // the Warlord goes back to his camp
					} else {
						m.discard();
					}
				});
				end(level, hall, raid, true, band);
			} else {
				culture.ifPresent(c -> Threats.tactics(c).forEach(t -> t.round(level, hall, c, left)));
			}
			return;
		}
		if (Threats.quiet(level, hall)) {
			data.drop(hall, Long.MAX_VALUE, false); // nothing comes to a quiet village (32.21): the attacks the clock set are off
			return;
		}
		long day = Chronicle.day(level);
		// With a Seer in the village (29.16) the night was rolled at dawn, and goes as foretold.
		Optional<io.github.jcondedata.aliveworkplace.legend.Seer.State> told = io.github.jcondedata.aliveworkplace.legend.Seer.tonight(level, hall);
		if (told.isPresent()) {
			Night night = told.get().night();
			data.drop(hall, day, false);
			if (ENABLED && night.raid() && !told.get().raidStarted() && isNight(level) && time >= night.at()) {
				// Who comes is the clock's attack the Seer read at dawn (B133); a foretelling saved before it has none.
				Optional<Culture> culture = data.attacks(hall).stream().filter(a -> a.day() == day).findFirst()
					.flatMap(a -> Threats.on(a.culture())).filter(c -> !c.needsLair() || lairOf(level, hall, c));
				Raid started = culture.isPresent() ? start(level, hall, villagers, guards, night.angle(), culture.get(), level.random)
					: start(level, hall, villagers, guards, night.angle());
				if (started != null) {
					data.drop(hall, day, true);
					io.github.jcondedata.aliveworkplace.legend.Seer.raidStarted(level, hall);
					raided.accept(day);
				}
			}
			return;
		}
		if (!ENABLED) {
			return;
		}
		// Tonight's attack, set at yesterday's dusk: it comes at its hour.
		data.drop(hall, day, false);
		long last = lastRaidDay;
		ThreatData.Attack due = data.attacks(hall).stream().filter(a -> a.day() == day).findFirst().orElse(null);
		if (due != null && isNight(level) && time >= due.at()) {
			data.drop(hall, day, true);
			Optional<Culture> culture = Threats.on(due.culture()).filter(c -> !c.needsLair() || lairOf(level, hall, c));
			if (culture.isPresent() && day - lastRaidDay >= REST_DAYS && start(level, hall, villagers, guards, due.angle(), culture.get(), level.random) != null) {
				raided.accept(day);
				last = day;
			}
		}
		// Dusk: tomorrow night's.
		if (time >= DUSK && data.rolled(hall) < day) {
			Night night = roll(level, hall, villagers, last, day + 1, level.random);
			ThreatData.Attack attack = null;
			if (night.raid() && data.attacks(hall).isEmpty()) {
				attack = cultureFor(level, hall, villagers, level.random).map(c -> new ThreatData.Attack(day + 1, c.id(), night.angle(), night.at())).orElse(null);
			}
			data.rolled(hall, day, attack);
		}
	}

	/** Whether a lair of {@code culture} stands by the village round {@code hall}. */
	static boolean lairOf(ServerLevel level, BlockPos hall, Culture culture) {
		return Lairs.of(level, hall, culture.id()).isPresent();
	}

	/**
	 * Who comes to the village of {@code villagers} round {@code hall}: the culture of the lair standing by it (raids come
	 * from the standing lair), or else one picked by weight among the cultures without a lair whose {@code where} fits
	 * ({@link Threats#pick}). Only cultures the config leaves on; empty when there is none.
	 */
	public static Optional<Culture> cultureFor(ServerLevel level, BlockPos hall, int villagers, RandomSource random) {
		Optional<Lairs.Lair> standing = Lairs.near(level, hall);
		if (standing.isPresent()) {
			Optional<Culture> lair = Threats.on(standing.get().culture());
			if (lair.isPresent()) {
				return lair;
			}
		}
		return Threats.pick(level, hall, villagers, random);
	}

	/**
	 * About how many raiders a raid by {@code culture} would bring to the village of {@code villagers} and {@code guards}
	 * round {@code hall} now, as {@link #start} sends them (a scout's count, 32.14; the culture's extras and captain aside).
	 */
	public static int expected(ServerLevel level, BlockPos hall, int villagers, int guards, net.minecraft.resources.ResourceLocation culture) {
		int plain = Math.min(MAX_RAIDERS, 3 + villagers / 4);
		int size = Threats.more(Math.min(MAX_RAIDERS, plain + Math.min(4, guards / 2))); // a quarter more when the threat level is hard (32.21)
		return Lairs.of(level, hall, culture).map(c -> Lairs.raidSize(c, size)).orElse(size);
	}

	/** The raiders still about in the village round {@code hall}. */
	public static List<Mob> raiders(ServerLevel level, BlockPos hall) {
		int r = VillageHalls.RADIUS + 32;
		return level.getEntitiesOfClass(Mob.class, new AABB(hall).inflate(r, 48, r), m -> m.isAlive() && m.getTags().contains(TAG));
	}

	/**
	 * A night rolled ahead (29.16: at dawn, with a Seer in the village): whether raiders come, from which side ({@code angle},
	 * radians from the hall as {@code atan2(dz, dx)}) and at what time of night ({@code at}, day time).
	 */
	public record Night(boolean raid, double angle, long at) {
		public static final Night QUIET = new Night(false, 0, 0);

		/** The side they come from: "north", "south-east"... */
		public Component side(BlockPos hall) {
			return io.github.jcondedata.aliveworkplace.legend.Pathfinder.direction(hall,
				hall.offset((int) Math.round(Math.cos(angle) * 100), 0, (int) Math.round(Math.sin(angle) * 100)));
		}
	}

	/**
	 * How far each raider stands from the gathering point (blocks, each way): 3, or less in a small village, so that on a
	 * foretold side the raiders' middle can't stray out of the eighth the Seer named (B82).
	 */
	static int scatter(double distance) {
		return (int) Math.max(1, Math.min(3, distance / 8));
	}

	/**
	 * How far the gathering point may stray from a foretold side (radians) at {@code distance} from the hall: the
	 * gathering point, the raiders' scatter round it and the rounding to blocks together stay within half of the eighth's
	 * half-width, so the raid comes well inside the side told (B82: a fixed 0.3 rad plus a 3-block scatter could cross
	 * into the next eighth).
	 */
	static double foretoldSpread(double distance) {
		double room = distance * Math.sin(Math.PI / 8) / 2 - (scatter(distance) + 1) * Math.sqrt(2);
		return room <= 0 ? 0 : Math.asin(Math.min(1, room / distance));
	}

	/**
	 * Rolls tonight's raid on the village of {@code villagers} round {@code hall} ahead, as the hall's night rounds would:
	 * none while raids are off or within {@link #REST_DAYS} of the last, else with {@link #chance} for the night. The side
	 * is the lair's when there is one near, else any of the eight; the hour falls between nightfall and well
	 * before dawn.
	 */
	public static Night rollNight(ServerLevel level, BlockPos hall, int villagers, long lastRaidDay, net.minecraft.util.RandomSource random) {
		return roll(level, hall, villagers, lastRaidDay, Chronicle.day(level), random);
	}

	/**
	 * Rolls the clock of the village of {@code villagers} round {@code hall} ahead through the night of {@code through}
	 * (B133: at a Seer's dawn, as many nights ahead as the Seer warns of, so that 32.14's warning comes that many days
	 * before), with {@code random}: each night from today on that the clock hasn't rolled yet, as the dusk roll does,
	 * an attack set only {@link #REST_DAYS} after the last raid and the last attack already set. Who comes is
	 * {@link #cultureFor}, else the plain monsters (as a foretold raid always brought). Returns tonight as the clock now
	 * holds it; quiet, and nothing rolled, while raids are off.
	 */
	public static Night rollAhead(ServerLevel level, BlockPos hall, int villagers, long lastRaidDay, long through, RandomSource random) {
		if (!ENABLED) {
			return Night.QUIET;
		}
		ThreatData data = ThreatData.get(level);
		long today = Chronicle.day(level);
		data.drop(hall, today, false);
		for (long night = Math.max(today, data.rolled(hall) + 2); night <= through; night++) {
			final long day = night;
			long last = Math.max(lastRaidDay, data.attacks(hall).stream().mapToLong(ThreatData.Attack::day).max().orElse(lastRaidDay));
			Night rolled = roll(level, hall, villagers, last, day, random);
			ThreatData.Attack attack = rolled.raid() ? cultureFor(level, hall, villagers, random).or(() -> Threats.on(Threats.MONSTERS))
				.map(c -> new ThreatData.Attack(day, c.id(), rolled.angle(), rolled.at())).orElse(null) : null;
			data.rolled(hall, day - 1, attack);
		}
		return data.attacks(hall).stream().filter(a -> a.day() == today).findFirst()
			.map(a -> new Night(true, a.angle(), a.at())).orElse(Night.QUIET);
	}

	/** As {@link #rollNight}, for the night of {@code day} (the clock rolls tomorrow's). */
	static Night roll(ServerLevel level, BlockPos hall, int villagers, long lastRaidDay, long day, RandomSource random) {
		float roll = random.nextFloat();
		double eighth = Math.PI / 4;
		Optional<Lairs.Lair> camp = Lairs.near(level, hall);
		double angle = camp.map(c -> Math.atan2(c.pos().getZ() - hall.getZ(), c.pos().getX() - hall.getX()))
			.orElseGet(() -> random.nextInt(8) * eighth);
		angle = Math.round(angle / eighth) * eighth; // the middle of its eighth, so the side told is the side they come from
		long at = 13500 + random.nextInt(4500);
		if (!ENABLED || day - lastRaidDay < REST_DAYS || roll >= chance(level, hall, villagers)) {
			return new Night(false, angle, at);
		}
		return new Night(true, angle, at);
	}

	/**
	 * Starts a raid on the village round {@code hall} now; null if the raiders found nowhere to gather, or no culture is
	 * left switched on to come.
	 */
	@Nullable
	public static Raid start(ServerLevel level, BlockPos hall, int villagers, int guards) {
		return start(level, hall, villagers, guards, Double.NaN);
	}

	/**
	 * As {@link #start(ServerLevel, BlockPos, int, int)}, gathering on the side {@code angle} (NaN: wherever they like).
	 * Who comes is {@link #cultureFor}; this is an order, so when no culture's {@code where} fits the village (one
	 * smaller than any culture asks for: the clock never sets such an attack, a foretelling or a command can) the plain
	 * monsters come, if they're switched on.
	 */
	@Nullable
	public static Raid start(ServerLevel level, BlockPos hall, int villagers, int guards, double angle) {
		Optional<Culture> culture = cultureFor(level, hall, villagers, level.random).or(() -> Threats.on(Threats.MONSTERS));
		return culture.isEmpty() ? null : start(level, hall, villagers, guards, angle, culture.get(), level.random);
	}

	/**
	 * Starts a raid by {@code culture} on the village round {@code hall} now, gathering on the side {@code angle} (NaN:
	 * where the culture's {@code arrival} says): its roster in its shares (picked with {@code random}), each with its
	 * role's gear. As many come as ever: 3 and one more for every 4 villagers, plus up to 4 in iron for a village with
	 * guards, at most {@link #MAX_RAIDERS}; from the culture's lair by the village, no more than the band it has at home
	 * ({@link Lairs#raidSize}), and they are out of it till the raid ends. Null if they found nowhere to gather, or the
	 * lair has nobody to send.
	 */
	@Nullable
	public static Raid start(ServerLevel level, BlockPos hall, int villagers, int guards, double angle, Culture culture, RandomSource random) {
		Optional<Lairs.Lair> camp = Lairs.of(level, hall, culture.id());
		int plain = Math.min(MAX_RAIDERS, 3 + villagers / 4);
		int size = Math.min(MAX_RAIDERS, plain + Math.min(4, guards / 2)); // (those past the plain ones come in iron)
		size = Threats.more(size); // a quarter more when the threat level is hard (32.21)
		if (camp.isPresent()) {
			size = Lairs.raidSize(camp.get(), size);
			if (size <= 0) {
				return null; // after a costly night the lair has nobody to send
			}
		}
		int raiders = size;
		boolean foretold = !Double.isNaN(angle);
		boolean portal = false;
		boolean wading = false;
		BlockPos gather = null;
		if (foretold) {
			gather = foretoldPoint(level, hall, angle);
		} else if (culture.arrival() == Culture.Arrival.LAIR) {
			gather = camp.map(c -> gatheringPoint(level, hall, Math.atan2(c.pos().getZ() - hall.getZ(), c.pos().getX() - hall.getX()), 0.5, random)).orElse(null);
		} else if (culture.arrival() == Culture.Arrival.SHORE) {
			// From a ship they wade ashore at the village's beach nearest it (32.8), not from the land side.
			BlockPos beach = camp.map(c -> io.github.jcondedata.aliveworkplace.threat.Ships.beach(level, hall, c.pos())).orElse(null);
			if (beach != null) {
				gather = io.github.jcondedata.aliveworkplace.threat.Ships.wading(level, beach, camp.get().pos());
				wading = true;
			} else {
				gather = shorePoint(level, hall);
			}
		} else if (culture.arrival() == Culture.Arrival.PORTAL) {
			// Out of their lair's own portal (32.10: the Blackstone Outpost's), else out of the village's.
			gather = camp.map(c -> io.github.jcondedata.aliveworkplace.threat.Piglins.portal(level, c)).orElse(null);
			if (gather == null) {
				gather = Conditions.netherLink(level, hall).filter(level::isLoaded).orElse(null);
			}
			portal = gather != null;
			if (portal) {
				io.github.jcondedata.aliveworkplace.threat.Piglins.stepOut(level, gather);
			}
		}
		if (gather == null) {
			gather = gatheringPoint(level, hall, 0, Math.PI, random);
		}
		if (gather == null) {
			return null;
		}
		lastGathering = gather;
		if (!portal && (io.github.jcondedata.aliveworkplace.threat.Sieges.lays(culture) || culture.tactics().contains(io.github.jcondedata.aliveworkplace.threat.Covens.TACTIC) && !Threats.gentle())) {
			// A siege gathers beyond the outermost finished wall on its side (32.5), and so does a coven (32.11): no raider appears inside the walls.
			BlockPos beyond = io.github.jcondedata.aliveworkplace.threat.Sieges.outside(level, hall, gather);
			// Only onto ground where mobs move (B176): raiders put down at the edge of the live ground stood frozen, and a
			// chunk loaded without its entities hid them from every count of the raid.
			if (!beyond.equals(gather) && level.isLoaded(beyond) && (level.isPositionEntityTicking(beyond) || !level.isPositionEntityTicking(gather))) {
				gather = surface(level, beyond);
			}
		}
		BlockPos from = gather;
		boolean atPortal = portal;
		boolean wades = wading;
		int scatter = foretold ? scatter(VillageHalls.RADIUS * 0.6) : wading ? 1 : 3;
		List<Villager> targets = level.getEntitiesOfClass(Villager.class, new AABB(hall).inflate(VillageHalls.RADIUS, 16, VillageHalls.RADIUS),
			Villager::isAlive);
		List<Mob> spawned = new ArrayList<>();
		for (int i = 0; i < raiders; i++) {
			Culture.Member member = culture.pick(random);
			Mob mob = Threats.create(level, member);
			if (mob == null) {
				continue;
			}
			BlockPos at = atPortal ? beside(level, from, random) : near(level, from, scatter, wades, random);
			mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360f, 0f);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
			Threats.outfit(level, mob, culture, member);
			if (camp.isPresent()) {
				mob.addTag(Lairs.TAG); // one of the lair's band
				if (culture.id().equals(BanditCamps.CULTURE)) {
					mob.addTag(BanditCamps.TAG);
				}
			}
			if (atPortal) {
				mob.setPortalCooldown(); // they came out of it: it doesn't take them back
			}
			if (i >= plain && !(mob instanceof net.minecraft.world.entity.monster.Spider)) {
				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST}) {
					if (!member.gear().containsKey(slot)) { // (the roster's own gear stays)
						mob.setItemSlot(slot, new ItemStack(slot == EquipmentSlot.HEAD ? Items.IRON_HELMET : Items.IRON_CHESTPLATE));
						mob.setDropChance(slot, 0f);
					}
				}
			}
			if (!targets.isEmpty()) {
				mob.setTarget(targets.get(random.nextInt(targets.size())));
			}
			mob.getNavigation().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 1.0);
			if (!io.github.jcondedata.aliveworkplace.threat.HostileSpawns.add(level, mob, "raid on the village hall at " + hall.toShortString())) {
				continue; // (said in the log: B208)
			}
			spawned.add(mob);
		}
		if (spawned.isEmpty()) {
			return null;
		}
		int band = spawned.size();
		// The culture's extras by the village's rank (32.7: the warband's ravager rams, its evoker at a City), and its
		// captain at the head of the raid where he leads it himself.
		VillageRanks.Rank rank = VillageRanks.of(level, hall);
		for (Culture.Extra extra : culture.extras()) {
			for (int n = extra.count(rank); n > 0; n--) {
				Mob mob = Threats.create(level, extra.entity());
				if (mob == null) {
					continue;
				}
				BlockPos at = atPortal ? beside(level, from, random)
					: near(level, from, scatter, false, random);
				mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360f, 0f);
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
				Threats.outfit(level, mob, culture, extra.member());
				mob.addTag(Threats.EXTRA_TAG);
				if (!targets.isEmpty()) {
					mob.setTarget(targets.get(random.nextInt(targets.size())));
				}
				mob.getNavigation().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 1.0);
				if (io.github.jcondedata.aliveworkplace.threat.HostileSpawns.add(level, mob, "raid on the village hall at " + hall.toShortString())) {
					spawned.add(mob);
				}
			}
		}
		Mob leader = camp.filter(c -> Lairs.leads(level, c)).map(c -> Lairs.lead(level, c, surface(level, from))).orElse(null);
		if (leader != null) {
			if (!targets.isEmpty()) {
				leader.setTarget(targets.get(random.nextInt(targets.size())));
			}
			leader.getNavigation().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 1.0);
			spawned.add(leader);
		}
		Raid raid = begin(level, hall, culture.id(), spawned.size());
		if (camp.isPresent()) {
			Lairs.sent(level, hall, band);
		}
		ringTheBell(level, hall);
		level.playSound(null, gather, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8f, 1f);
		Component name = VillageHalls.name(level, hall);
		// A lair's raid is its captain's: his name is in the horn's message and in the chronicle (32.3).
		Optional<Lairs.Lair> named = camp.filter(Lairs::named);
		Component said = named.isPresent() && leader != null
			? Lairs.message(named.get(), "raid_led", false, spawned.size(), name, Lairs.captainName(named.get()))
			: named.isPresent()
			? Lairs.message(named.get(), "raid", true, spawned.size(), name, Lairs.captainName(named.get()))
			: Component.translatable(culture.messages().key("raid", "message.aliveworkplace.raid.begins"), spawned.size(), name);
		for (ServerPlayer player : players(level, hall)) {
			Chat.chat(player, said.copy().withStyle(ChatFormatting.RED));
		}
		Chronicle.record(level, hall, Chronicle.Kind.RAID, named.isPresent() && leader != null
			? Lairs.chronicle(named.get(), "raid_led", false, spawned.size(), Lairs.captainName(named.get()))
			: named.isPresent()
			? Lairs.chronicle(named.get(), "raid", true, spawned.size(), Lairs.captainName(named.get()))
			: Component.translatable(culture.chronicle().key("raid", "chronicle.aliveworkplace.raid"), spawned.size()));
		Threats.tactics(culture).forEach(t -> t.begin(level, hall, culture, spawned));
		return raid;
	}

	/**
	 * Calls off the raid under way on the village round {@code hall} (an operator's {@code /workplace threat end}, 32.21):
	 * its raiders go at once (a captain back to his camp, the band's own back to their lair), its tactics end as when
	 * raiders leave at dawn (the gates open again), and the village is told. Nothing goes into the list of past attacks.
	 * False when no raid is under way.
	 */
	public static boolean callOff(ServerLevel level, BlockPos hall) {
		ThreatData data = ThreatData.get(level);
		ThreatData.Under raid = data.raid(hall).orElse(null);
		if (raid == null) {
			return false;
		}
		List<Mob> left = raiders(level, hall);
		int band = (int) left.stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG) && !Lairs.leading(m)).count();
		for (Mob m : left) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			if (Lairs.leading(m)) {
				Lairs.comeHome(level, m);
			} else {
				m.discard();
			}
		}
		data.end(hall);
		Lairs.back(level, hall, raid.culture(), band);
		Threats.get(raid.culture()).ifPresent(c -> Threats.tactics(c).forEach(t -> t.end(level, hall, c, true)));
		Component name = VillageHalls.name(level, hall);
		for (ServerPlayer player : players(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.raid.called_off", name).withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	/** The raid is over: {@code survivors} of its raiders were still about (they fled; 0 when it was fought off). */
	private static void end(ServerLevel level, BlockPos hall, ThreatData.Under raid, boolean fled, int survivors) {
		ThreatData data = ThreatData.get(level);
		data.end(hall);
		data.remember(hall, new ThreatData.Past(Chronicle.day(level), raid.culture(), raid.raiders(), Math.max(0, raid.raiders() - survivors), fled));
		Lairs.back(level, hall, raid.culture(), survivors); // those alive rejoin their lair, the dead are gone
		if (fled) {
			Scouts.trail(level, hall, raid.culture()); // a scout trails them home: the lair's place is known (32.12)
		}
		Threats.get(raid.culture()).ifPresent(c -> Threats.tactics(c).forEach(t -> t.end(level, hall, c, fled)));
		io.github.jcondedata.aliveworkplace.trade.TradeGoods.event(level, hall, io.github.jcondedata.aliveworkplace.trade.TradeGoods.RAID); // 33.2: arms wanted for a few days
		Component name = VillageHalls.name(level, hall);
		for (ServerPlayer player : players(level, hall)) {
			Chat.chat(player, Component.translatable(fled ? "message.aliveworkplace.raid.fled" : "message.aliveworkplace.raid.won", name)
				.withStyle(ChatFormatting.GREEN));
		}
		Chronicle.record(level, hall, Chronicle.Kind.RAID, Component.translatable(fled ? "chronicle.aliveworkplace.raid_fled" : "chronicle.aliveworkplace.raid_won",
			raid.raiders()));
		if (!fled) {
			level.playSound(null, hall, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1f);
		}
		io.github.jcondedata.aliveworkplace.hall.VillageMoments.award(level, hall, io.github.jcondedata.aliveworkplace.hall.VillageMoments.RAID, null); // the questbook's raid survived
	}

	private static List<ServerPlayer> players(ServerLevel level, BlockPos hall) {
		return level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) (VillageHalls.RADIUS + 32) * (VillageHalls.RADIUS + 32));
	}

	/** Rings the village's bell (nearest the hall), so guards rally and the others hide. */
	static void ringTheBell(ServerLevel level, BlockPos hall) {
		level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY).ifPresent(bell -> {
			if (level.getBlockState(bell).getBlock() instanceof BellBlock block) {
				block.attemptToRing(level, bell, Direction.NORTH);
			}
		});
	}

	/** Where the last raid started gathered (the showcase's and the tests' look at it; null before any). */
	@Nullable
	public static volatile BlockPos lastGathering;

	/** Where the raiders gather: out at the edge of the village, on open ground. */
	@Nullable
	static BlockPos gatheringPoint(ServerLevel level, BlockPos hall) {
		return gatheringPoint(level, hall, 0, Math.PI);
	}

	/**
	 * Where the raiders of a foretold raid gather: on the side {@code toward}, give or take {@link #foretoldSpread}; when
	 * that ground is no good (water, unloaded), further out along the same side rather than round the compass.
	 */
	@Nullable
	static BlockPos foretoldPoint(ServerLevel level, BlockPos hall, double toward) {
		double nearest = VillageHalls.RADIUS * 0.6;
		double spread = foretoldSpread(nearest);
		java.util.List<io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers.Arc> walls = io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers.arcs(level, hall);
		for (int tries = 0; tries < 8; tries++) {
			double distance = nearest + VillageHalls.RADIUS * 0.3 * tries / 7;
			// never behind a standing Great Wall (35.16): the open side nearest the foretold one
			double angle = io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers.bearing(walls, toward, spread, level.random);
			BlockPos at = groundAt(level, hall, hall.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance)));
			if (at != null) {
				return at;
			}
		}
		return null;
	}

	/** The open ground at the top of {@code column}, near the hall's height and dry; null if there is none. */
	@Nullable
	private static BlockPos groundAt(ServerLevel level, BlockPos hall, BlockPos column) {
		if (!level.isLoaded(column)) {
			return null;
		}
		BlockPos at = surface(level, column);
		return Math.abs(at.getY() - hall.getY()) <= 24 && level.getFluidState(at.below()).isEmpty() && level.getFluidState(at).isEmpty() ? at : null;
	}

	/** Where the raiders gather: out at the edge of the village, about {@code angle} from the hall (give or take {@code spread}). */
	@Nullable
	static BlockPos gatheringPoint(ServerLevel level, BlockPos hall, double toward, double spread) {
		return gatheringPoint(level, hall, toward, spread, level.random);
	}

	/**
	 * As {@link #gatheringPoint(ServerLevel, BlockPos, double, double)} with {@code random}; never behind a standing Great
	 * Wall (35.16): each bearing is drawn outside the arcs its village's walls cover ({@code wonder/GreatWallPowers}).
	 */
	@Nullable
	static BlockPos gatheringPoint(ServerLevel level, BlockPos hall, double toward, double spread, RandomSource random) {
		double distance = VillageHalls.RADIUS * 0.6;
		java.util.List<io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers.Arc> walls = io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers.arcs(level, hall);
		for (int tries = 0; tries < 8; tries++) {
			double angle = io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers.bearing(walls, toward, spread, random);
			BlockPos column = hall.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos at = surface(level, column);
			if (Math.abs(at.getY() - hall.getY()) <= 24 && level.getFluidState(at.below()).isEmpty() && level.getFluidState(at).isEmpty()) {
				return at;
			}
		}
		return null;
	}

	static BlockPos surface(ServerLevel level, BlockPos column) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
	}

	/**
	 * Where raiders who come ashore gather: at the edge of the village, on dry ground with water within 3 blocks; null
	 * if the village's edge has no shore.
	 */
	@Nullable
	static BlockPos shorePoint(ServerLevel level, BlockPos hall) {
		double distance = VillageHalls.RADIUS * 0.6;
		for (int tries = 0; tries < 16; tries++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			BlockPos at = groundAt(level, hall, hall.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance)));
			if (at == null) {
				continue;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (int out = 1; out <= 3; out++) {
					BlockPos column = at.relative(side, out);
					if (level.isLoaded(column) && level.getFluidState(surface(level, column).below()).is(net.minecraft.tags.FluidTags.WATER)) {
						return at;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Where a raider gathering at {@code from} stands: within {@code scatter} blocks of it, on the ground; one wading in
	 * ({@code wades}, from a ship, 32.8) on the water there, looking down from just over it. When the gathering point is
	 * on ground where mobs move, so is every raider (B176): a spot over the edge of it is drawn again, up to 8 times, and
	 * else the raider stands at the point itself.
	 */
	private static BlockPos near(ServerLevel level, BlockPos from, int scatter, boolean wades, RandomSource random) {
		boolean live = level.isPositionEntityTicking(from);
		BlockPos column = from;
		for (int tries = 0; tries < 8; tries++) {
			BlockPos drawn = from.offset(random.nextInt(2 * scatter + 1) - scatter, 0, random.nextInt(2 * scatter + 1) - scatter);
			if (!live || level.isPositionEntityTicking(drawn)) {
				column = drawn;
				break;
			}
		}
		return wades ? io.github.jcondedata.aliveworkplace.threat.Ships.standAt(level, column, from.getY() + 2) : surface(level, column);
	}

	/** A place to stand at or right beside {@code portal}, at its height (raiders stepping out of it). */
	private static BlockPos beside(ServerLevel level, BlockPos portal, RandomSource random) {
		BlockPos at = portal.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
			&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty() ? at : portal;
	}

	/** Forgets the raid under way on the village round {@code hall} (tests). */
	public static void forget(BlockPos hall) {
		ThreatData.forgetRaids(hall);
	}

	/** Forgets the raids under way (tests). */
	public static void forget() {
		ThreatData.forgetRaids(null);
	}

	private VillageRaids() {
	}
}
