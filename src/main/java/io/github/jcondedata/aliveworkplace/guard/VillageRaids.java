package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Monster;
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

	/** A raid on the village round {@code hall}: its raiders, how many came, when it began. */
	public record Raid(BlockPos hall, int raiders, long began) {
	}

	private static final Map<BlockPos, Raid> ACTIVE = new ConcurrentHashMap<>();

	/** The raid on the village round {@code hall} right now, if any. */
	public static Optional<Raid> active(BlockPos hall) {
		return Optional.ofNullable(ACTIVE.get(hall));
	}

	/**
	 * Whether the village round {@code hall} is raided now: one of our raids (monsters or bandits) on it, or a vanilla
	 * pillager raid at {@code hall} or at {@code at} (Conscription, 30.10).
	 */
	public static boolean raided(ServerLevel level, BlockPos hall, BlockPos at) {
		return ACTIVE.containsKey(hall) || vanillaRaid(level, hall) || !at.equals(hall) && vanillaRaid(level, at);
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
		Raid raid = new Raid(hall.immutable(), raiders, level.getGameTime());
		ACTIVE.put(raid.hall(), raid);
		return raid;
	}

	/**
	 * Where a guard with its post (or rally point) at {@code center} should look for foes while a raid is on: the whole
	 * village being raided, or a vanilla raid's area; empty when there's no raid there.
	 */
	public static Optional<AABB> raidArea(ServerLevel level, BlockPos center) {
		for (Raid raid : ACTIVE.values()) {
			if (raid.hall().distSqr(center) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS) {
				return Optional.of(new AABB(raid.hall()).inflate(VillageHalls.RADIUS, 16, VillageHalls.RADIUS));
			}
		}
		net.minecraft.world.entity.raid.Raid vanilla = level.getRaidAt(center);
		if (vanilla != null && vanilla.isActive()) {
			return Optional.of(new AABB(vanilla.getCenter()).inflate(48, 16, 48));
		}
		return Optional.empty();
	}

	/**
	 * The chance a night that the village of {@code villagers} round {@code hall} is raided: {@link #nightlyChance}, twice
	 * that with a bandit camp near (bandits come from their camp), times the {@code curfew} effects' {@code raids} (Curfew,
	 * 30.9: half as likely).
	 */
	public static float chance(ServerLevel level, BlockPos hall, int villagers) {
		return nightlyChance(villagers) * (BanditCamps.near(level, hall).isPresent() ? 2 : 1)
			* io.github.jcondedata.aliveworkplace.hall.CivicEffects.of(level, hall).raids()
			* io.github.jcondedata.aliveworkplace.research.TreeEffects.raidChance(level, hall); // raid_chance (29.11)
	}

	/** The chance a night that a village of {@code villagers} is raided. */
	public static float nightlyChance(int villagers) {
		return villagers < MIN_VILLAGERS ? 0f : Math.min(0.35f, 0.15f + 0.01f * (villagers - MIN_VILLAGERS));
	}

	static boolean isNight(ServerLevel level) {
		long time = level.getDayTime() % VillageNeeds.DAY;
		return time >= 13500 && time < 22000;
	}

	/** The hall's round: a raid under way is checked on (and ended); at night one may begin. */
	public static void tick(ServerLevel level, BlockPos hall, int villagers, int guards, long lastRaidDay, java.util.function.LongConsumer raided) {
		Raid raid = ACTIVE.get(hall);
		if (raid != null) {
			List<Mob> left = raiders(level, hall);
			if (left.isEmpty()) {
				end(level, hall, raid, false);
			} else if (!isNight(level) && level.getGameTime() - raid.began() > 1200) {
				left.forEach(m -> {
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
					m.discard();
				});
				end(level, hall, raid, true);
			}
			return;
		}
		long day = Chronicle.day(level);
		// With a Seer in the village (29.16) the night was rolled at dawn, and goes as foretold.
		Optional<io.github.jcondedata.aliveworkplace.legend.Seer.State> told = io.github.jcondedata.aliveworkplace.legend.Seer.tonight(level, hall);
		if (told.isPresent()) {
			Night night = told.get().night();
			if (ENABLED && night.raid() && !told.get().raidStarted() && isNight(level) && level.getDayTime() % VillageNeeds.DAY >= night.at()
				&& start(level, hall, villagers, guards, night.angle()) != null) {
				io.github.jcondedata.aliveworkplace.legend.Seer.raidStarted(level, hall);
				raided.accept(day);
			}
			return;
		}
		if (!ENABLED || !isNight(level) || day - lastRaidDay < REST_DAYS) {
			return;
		}
		int rounds = Math.max(1, 8500 / VillageNeeds.CHECK_EVERY);
		float chance = chance(level, hall, villagers);
		if (level.random.nextFloat() < chance / rounds && start(level, hall, villagers, guards) != null) {
			raided.accept(day);
		}
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

	/** How far the raiders' gathering point may stray from a foretold side (radians): well inside its eighth of the compass. */
	static final double FORETOLD_SPREAD = 0.3;

	/**
	 * Rolls tonight's raid on the village of {@code villagers} round {@code hall} ahead, as the hall's night rounds would:
	 * none while raids are off or within {@link #REST_DAYS} of the last, else with {@link #chance} for the night. The side
	 * is the bandit camp's when there is one near, else any of the eight; the hour falls between nightfall and well
	 * before dawn.
	 */
	public static Night rollNight(ServerLevel level, BlockPos hall, int villagers, long lastRaidDay, net.minecraft.util.RandomSource random) {
		long day = Chronicle.day(level);
		float roll = random.nextFloat();
		double eighth = Math.PI / 4;
		Optional<BanditCamps.Camp> camp = BanditCamps.near(level, hall);
		double angle = camp.map(c -> Math.atan2(c.pos().getZ() - hall.getZ(), c.pos().getX() - hall.getX()))
			.orElseGet(() -> random.nextInt(8) * eighth);
		angle = Math.round(angle / eighth) * eighth; // the middle of its eighth, so the side told is the side they come from
		long at = 13500 + random.nextInt(4500);
		if (!ENABLED || day - lastRaidDay < REST_DAYS || roll >= chance(level, hall, villagers)) {
			return new Night(false, angle, at);
		}
		return new Night(true, angle, at);
	}

	/** Starts a raid on the village round {@code hall} now; null if the raiders found nowhere to gather. */
	@Nullable
	public static Raid start(ServerLevel level, BlockPos hall, int villagers, int guards) {
		return start(level, hall, villagers, guards, Double.NaN);
	}

	/** As {@link #start(ServerLevel, BlockPos, int, int)}, gathering on the side {@code angle} (NaN: wherever they like). */
	@Nullable
	public static Raid start(ServerLevel level, BlockPos hall, int villagers, int guards, double angle) {
		Optional<BanditCamps.Camp> camp = BanditCamps.near(level, hall);
		BlockPos gather = !Double.isNaN(angle) ? gatheringPoint(level, hall, angle, FORETOLD_SPREAD)
			: camp.map(c -> gatheringPoint(level, hall, Math.atan2(c.pos().getZ() - hall.getZ(), c.pos().getX() - hall.getX()), 0.5))
			.orElse(null);
		if (gather == null) {
			gather = gatheringPoint(level, hall);
		}
		if (gather == null) {
			return null;
		}
		int plain = Math.min(MAX_RAIDERS, 3 + villagers / 4);
		int armored = Math.min(4, guards / 2);
		List<Villager> targets = level.getEntitiesOfClass(Villager.class, new AABB(hall).inflate(VillageHalls.RADIUS, 16, VillageHalls.RADIUS),
			Villager::isAlive);
		int spawned = 0;
		for (int i = 0; i < Math.min(MAX_RAIDERS, plain + armored); i++) {
			float roll = level.random.nextFloat();
			EntityType<? extends Monster> type = roll < 0.5f ? EntityType.ZOMBIE : roll < 0.8f ? EntityType.SKELETON : EntityType.SPIDER;
			Mob mob = camp.isPresent() ? BanditCamps.raider(level) : type.create(level);
			if (mob == null) {
				continue;
			}
			BlockPos at = surface(level, gather.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3));
			mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
			if (camp.isPresent() && mob instanceof net.minecraft.world.entity.raid.Raider raider) {
				raider.setCanJoinRaid(false); // (finalizeSpawn lets a raider join vanilla's raids again: bandits stay out of them)
			}
			mob.setPersistenceRequired();
			mob.addTag(TAG);
			if (i >= plain && !(mob instanceof net.minecraft.world.entity.monster.Spider)) {
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.setDropChance(EquipmentSlot.HEAD, 0f);
				mob.setDropChance(EquipmentSlot.CHEST, 0f);
			}
			if (!targets.isEmpty()) {
				mob.setTarget(targets.get(level.random.nextInt(targets.size())));
			}
			mob.getNavigation().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 1.0);
			level.addFreshEntityWithPassengers(mob);
			spawned++;
		}
		if (spawned == 0) {
			return null;
		}
		Raid raid = new Raid(hall.immutable(), spawned, level.getGameTime());
		ACTIVE.put(raid.hall(), raid);
		ringTheBell(level, hall);
		level.playSound(null, gather, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8f, 1f);
		Component name = VillageHalls.name(level, hall);
		String kind = camp.isPresent() ? "bandits" : "begins";
		for (ServerPlayer player : players(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.raid." + kind, spawned, name).withStyle(ChatFormatting.RED));
		}
		Chronicle.record(level, hall, Chronicle.Kind.RAID, Component.translatable(camp.isPresent() ? "chronicle.aliveworkplace.bandit_raid" : "chronicle.aliveworkplace.raid", spawned));
		return raid;
	}

	private static void end(ServerLevel level, BlockPos hall, Raid raid, boolean fled) {
		ACTIVE.remove(hall);
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

	/** Where the raiders gather: out at the edge of the village, on open ground. */
	@Nullable
	static BlockPos gatheringPoint(ServerLevel level, BlockPos hall) {
		return gatheringPoint(level, hall, 0, Math.PI);
	}

	/** Where the raiders gather: out at the edge of the village, about {@code angle} from the hall (give or take {@code spread}). */
	@Nullable
	static BlockPos gatheringPoint(ServerLevel level, BlockPos hall, double toward, double spread) {
		double distance = VillageHalls.RADIUS * 0.6;
		for (int tries = 0; tries < 8; tries++) {
			double angle = toward + (level.random.nextDouble() * 2 - 1) * spread;
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

	/** Forgets the raid under way on the village round {@code hall} (tests). */
	public static void forget(BlockPos hall) {
		ACTIVE.remove(hall);
	}

	/** Forgets the raids under way (tests). */
	public static void forget() {
		ACTIVE.clear();
	}

	private VillageRaids() {
	}
}
