package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Sieges III: battle stations (ROADMAP 32.6). While a siege lasts the village's guards take stations instead of
 * rallying at the bell:
 * <ul>
 * <li><b>archers</b> climb to the free station highest over the breach's side, among those within {@link #POST_REACH}
 * blocks of their Guard Post; a build with fewer archers on it comes before a higher one, so they spread over the
 * towers and walks. A station is a walkway spot of a finished wall, gate or tower build (the walls and gates
 * family and the Lookout Towers): a block of the blueprint at least {@link #MIN_HEIGHT} above its floor that one can
 * stand on, with two blocks of air above it, and not a merlon (a spot one block above a walkway spot beside it). They
 * are <b>read once per blueprint</b> ({@link #reads} counts) and checked against the world when taken: a station whose
 * floor went, or that was built over, is not used, and a guard on it gets another;</li>
 * <li><b>knights</b> hold the inside of the breach gate, {@link #KNIGHT_BACK} blocks in; <b>medics</b> stand behind
 * them, {@link #MEDIC_BACK} in. They fight what comes within {@link #HOLD} blocks of their spot and let the rest be;</li>
 * <li>the rest, and whoever finds no station, rally at the bell as before.</li>
 * </ul>
 * From a station an archer shoots up to {@link #RANGE} blocks ({@link #RAMPARTS_RANGE} with the Ramparts research;
 * {@code GuardCombat}'s 16 on the ground) and does {@link #HEIGHT_BONUS} times the damage to a foe {@link #HEIGHT} or
 * more blocks below.
 *
 * <p>One watch per siege, every {@link #EVERY} ticks (never in the siege director's tick), sending at most
 * {@link #MAX_PATHS} guards on their way a tick. Villagers cannot path up a ladder, so a guard who has come within
 * {@link #CLIMB_REACH} blocks of the foot of his station and stands below it climbs: he is set on the station with a
 * ladder's sound. Nothing here is saved: stations and who holds which are found again after a restart. (In
 * {@code guard/}, not the design's {@code threat/}: {@code threat/} never imports {@code guard/}.)
 */
public final class BattleStations {
	public static final int EVERY = 20;
	public static final int MAX_PATHS = 4;
	public static final double RANGE = 24;
	public static final double RAMPARTS_RANGE = 28;
	public static final double HEIGHT = 3;
	public static final float HEIGHT_BONUS = 1.25f;
	/** How far from an archer's Guard Post a station may be. */
	public static final int POST_REACH = 24;
	/** A station is at least this many blocks above its blueprint's floor. */
	public static final int MIN_HEIGHT = 3;
	public static final int KNIGHT_BACK = 3;
	public static final int MEDIC_BACK = 6;
	public static final double CLIMB_REACH = 3.5;
	/** A knight or medic leaves their spot only for a foe within this many blocks of it. */
	public static final double HOLD = 6;
	private static final float SPEED = 0.9f;
	/** The tick of each second the watches run in (the director runs in tick 0, the ladders in every tenth). */
	private static final int OFFSET = 7;

	/** How many times a blueprint's stations were read since the server started (tests: once per blueprint). */
	public static int reads;

	private record Read(Blueprint blueprint, List<BlockPos> spots) {
	}

	private record Key(ResourceKey<Level> dimension, BlockPos hall) {
	}

	/** What a siege's watch keeps in memory. */
	private static final class Watch {
		/** Where one stands on each station, in the world. */
		Map<BlockPos, Integer> stations = Map.of();
		boolean found;
		boolean ramparts;
		int cursor;
		int most;
		final Set<UUID> guards = new HashSet<>();
	}

	/** A guard's place in the siege: where, and whether it is a station (an archer's) or a spot on the ground. */
	private record Place(BlockPos pos, boolean station, boolean ramparts) {
	}

	private static final Map<ResourceLocation, Read> READ = new ConcurrentHashMap<>();
	private static final Map<Key, Watch> WATCHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Place> PLACES = new ConcurrentHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % EVERY == OFFSET) {
				tick(level);
			}
			if (!PLACES.isEmpty()) {
				stay(level);
			}
		});
		Platform.get().onServerStarting(server -> forget());
	}

	/** Forgets everything kept in memory, as a restart does (tests). */
	public static void forget() {
		READ.clear();
		WATCHES.clear();
		PLACES.clear();
	}

	// Reading the stations.

	/** The walkway spots of {@code blueprint} (blueprint positions of the block stood on), read once and kept. */
	public static List<BlockPos> spots(Blueprint blueprint) {
		Read read = READ.get(blueprint.id());
		if (read == null || read.blueprint() != blueprint) {
			read = new Read(blueprint, read(blueprint));
			READ.put(blueprint.id(), read);
			reads++;
		}
		return read.spots();
	}

	private static List<BlockPos> read(Blueprint blueprint) {
		Map<BlockPos, BlockState> at = new HashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (!e.state().isAir()) {
				at.put(e.pos(), e.state());
			}
		}
		Set<BlockPos> spots = new HashSet<>();
		for (Map.Entry<BlockPos, BlockState> e : at.entrySet()) {
			BlockPos pos = e.getKey();
			if (pos.getY() >= MIN_HEIGHT - 1 && !at.containsKey(pos.above()) && !at.containsKey(pos.above(2))
				&& e.getValue().isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)) {
				spots.add(pos);
			}
		}
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : spots) {
			boolean merlon = false;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				merlon |= spots.contains(pos.below().relative(side));
			}
			if (!merlon) {
				out.add(pos);
			}
		}
		out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		return List.copyOf(out);
	}

	/** Whether {@code structure} is a build with battle stations: a wall or gate build, or a Lookout Tower, in any style or tier. */
	public static boolean hasStations(ResourceLocation structure) {
		if (Sieges.isDefence(structure)) {
			return true;
		}
		Set<ResourceLocation> lookouts = Set.of(StarterBlueprints.LOOKOUT_TOWER.id(), StarterBlueprints.LOOKOUT_TOWER_2.id(), StarterBlueprints.LOOKOUT_TOWER_3.id());
		ResourceLocation base = BlueprintStyles.base(structure);
		for (int i = 0; i < 4; i++) {
			if (lookouts.contains(base)) {
				return true;
			}
			Optional<ResourceLocation> lower = BlueprintUpgrades.baseOf(base);
			if (lower.isEmpty()) {
				return false;
			}
			base = lower.get();
		}
		return false;
	}

	/** Where one stands on every station of the finished builds of the village round {@code hall}, as the blueprints have them. */
	public static List<BlockPos> stations(ServerLevel level, BlockPos hall) {
		return List.copyOf(byBuild(level, hall).keySet());
	}

	/** Every station of the village round {@code hall}, with the number of its build among the village's builds with stations. */
	private static Map<BlockPos, Integer> byBuild(ServerLevel level, BlockPos hall) {
		Map<BlockPos, Integer> out = new java.util.LinkedHashMap<>();
		int build = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (!hasStations(f.structure())) {
				continue;
			}
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, f.structure());
			if (blueprint.isEmpty()) {
				continue;
			}
			for (BlockPos local : spots(blueprint.get())) {
				out.putIfAbsent(f.placement().origin().offset(StructureTemplate.transform(local, f.placement().mirror(), f.placement().rotation(), BlockPos.ZERO)).above(), build);
			}
			build++;
		}
		return out;
	}

	/** Whether one can stand at {@code pos} now: something to stand on and two blocks free. */
	public static boolean standable(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
			&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
	}

	// What the guards ask.

	/** Where {@code guard} is to stand in the siege, while they have a place. */
	public static Optional<BlockPos> post(Villager guard) {
		Place place = PLACES.get(guard.getUUID());
		return place == null ? Optional.empty() : Optional.of(place.pos());
	}

	/** The spot on the ground {@code guard} holds in the siege (a knight's inside the breach gate, a medic's behind), while they have one. */
	public static Optional<BlockPos> holding(Villager guard) {
		Place place = PLACES.get(guard.getUUID());
		return place == null || place.station() ? Optional.empty() : Optional.of(place.pos());
	}

	/** Whether {@code guard} has a place in a siege (they don't rally at the bell or walk their round). */
	public static boolean stationed(Villager guard) {
		return PLACES.containsKey(guard.getUUID());
	}

	private static boolean at(Villager guard, BlockPos pos) {
		double dx = guard.getX() - (pos.getX() + 0.5);
		double dz = guard.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= 1.5 * 1.5 && Math.abs(guard.getY() - pos.getY()) <= 1.2;
	}

	/** Whether {@code guard} stands on their battle station (an archer's, up on a wall or tower). */
	public static boolean onStation(Villager guard) {
		Place place = PLACES.get(guard.getUUID());
		return place != null && place.station() && at(guard, place.pos());
	}

	/** How far {@code guard} shoots: {@code ground} on the ground, {@link #RANGE} from a station, {@link #RAMPARTS_RANGE} with Ramparts. */
	public static double range(Villager guard, double ground) {
		Place place = PLACES.get(guard.getUUID());
		if (place == null || !place.station() || !at(guard, place.pos())) {
			return ground;
		}
		return place.ramparts() ? RAMPARTS_RANGE : RANGE;
	}

	/** What an archer's arrow is worth against {@code foe}: {@link #HEIGHT_BONUS} from a station at a foe {@link #HEIGHT} or more below, else 1. */
	public static float heightBonus(Villager guard, LivingEntity foe) {
		return onStation(guard) && guard.getY() - foe.getY() >= HEIGHT ? HEIGHT_BONUS : 1f;
	}

	/** The most guards one watch of the siege on {@code hall} sent on their way in a tick (tests). */
	public static int mostPaths(ServerLevel level, BlockPos hall) {
		Watch watch = WATCHES.get(new Key(level.dimension(), hall));
		return watch == null ? 0 : watch.most;
	}

	// The watch.

	private static void tick(ServerLevel level) {
		ThreatData data = ThreatData.get(level);
		Set<Key> live = new HashSet<>();
		for (ThreatData.Siege siege : data.sieges()) {
			if (!siege.over && Sieges.ENABLED) {
				Key key = new Key(level.dimension(), siege.hall);
				live.add(key);
				step(level, siege, WATCHES.computeIfAbsent(key, k -> new Watch()));
			}
		}
		WATCHES.entrySet().removeIf(e -> {
			if (e.getKey().dimension() != level.dimension() || live.contains(e.getKey())) {
				return false;
			}
			e.getValue().guards.forEach(PLACES::remove); // the siege is over: back to their rounds
			return true;
		});
	}

	/** Every tick: an archer on their station stays on it (nothing else of a villager's day walks them off the wall). */
	private static void stay(ServerLevel level) {
		for (Map.Entry<UUID, Place> e : PLACES.entrySet()) {
			Place place = e.getValue();
			if (place.station() && level.getEntity(e.getKey()) instanceof Villager guard && at(guard, place.pos()) && !guard.getNavigation().isDone()) {
				BlockPos to = guard.getNavigation().getTargetPos();
				if (to != null && flat(to, place.pos()) > 2) {
					guard.getNavigation().stop();
					guard.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				}
			}
		}
	}

	private static void step(ServerLevel level, ThreatData.Siege siege, Watch watch) {
		if (!watch.found) {
			watch.stations = byBuild(level, siege.hall);
			watch.found = true;
		}
		watch.ramparts = Sieges.ramparts(level, siege.hall);
		List<Villager> guards = level.getEntitiesOfClass(Villager.class, new AABB(siege.hall).inflate(VillageHalls.RADIUS, 48, VillageHalls.RADIUS),
			v -> v.isAlive() && !v.isBaby() && Guards.isGuard(v));
		guards.sort(Comparator.comparing(Villager::getUUID));
		Set<UUID> here = new HashSet<>();
		guards.forEach(g -> here.add(g.getUUID()));
		watch.guards.removeIf(id -> {
			if (!here.contains(id)) {
				PLACES.remove(id);
				return true;
			}
			return false;
		});
		Set<BlockPos> taken = new HashSet<>();
		Map<Integer, Integer> manned = new HashMap<>(); // archers on each build
		for (Villager guard : guards) {
			Place place = PLACES.get(guard.getUUID());
			if (place == null || !watch.guards.contains(guard.getUUID())) {
				continue;
			}
			Guards.Kind kind = Guards.kind(guard);
			// A station that can't be stood on any more, or a guard whose arms changed: the place is given up.
			if (place.station() != (kind == Guards.Kind.ARCHER) || place.station() && !standable(level, place.pos()) || kind == Guards.Kind.GUARD) {
				PLACES.remove(guard.getUUID());
				watch.guards.remove(guard.getUUID());
			} else {
				taken.add(place.pos());
				if (place.station()) {
					manned.merge(watch.stations.getOrDefault(place.pos(), -1), 1, Integer::sum);
				}
				if (place.ramparts() != watch.ramparts) {
					PLACES.put(guard.getUUID(), new Place(place.pos(), place.station(), watch.ramparts));
				}
			}
		}
		Direction in = siege.out.getOpposite();
		int knights = 0;
		int medics = 0;
		for (Villager guard : guards) {
			Guards.Kind kind = Guards.kind(guard);
			if (PLACES.containsKey(guard.getUUID())) {
				knights += kind == Guards.Kind.KNIGHT ? 1 : 0;
				medics += kind == Guards.Kind.MEDIC ? 1 : 0;
				continue;
			}
			BlockPos pos = null;
			if (kind == Guards.Kind.ARCHER) {
				BlockPos home = Builders.benchPos(guard).orElse(guard.blockPosition());
				// The breach's side first; then the build with the fewest archers, so they spread over the towers and
				// walks; then the highest; then the nearest the breach (with none: the farthest out from the hall).
				pos = watch.stations.keySet().stream()
					.filter(s -> !taken.contains(s) && flat(s, home) <= (double) POST_REACH * POST_REACH && standable(level, s))
					.min(Comparator.<BlockPos>comparingInt(s -> side(siege, s) ? 0 : 1).thenComparingInt(s -> manned.getOrDefault(watch.stations.get(s), 0))
						.thenComparingInt(s -> -s.getY())
						.thenComparingDouble(s -> siege.breach == null ? -flat(s, siege.hall) : flat(s, siege.breach.relative(siege.out, 4))).thenComparingLong(BlockPos::asLong))
					.orElse(null);
				if (pos != null) {
					manned.merge(watch.stations.get(pos), 1, Integer::sum);
				}
			} else if (kind == Guards.Kind.KNIGHT && siege.breach != null) {
				pos = ground(level, siege.breach.relative(in, KNIGHT_BACK).relative(in.getClockWise(), spread(knights++)));
			} else if (kind == Guards.Kind.MEDIC && siege.breach != null) {
				pos = ground(level, siege.breach.relative(in, MEDIC_BACK).relative(in.getClockWise(), spread(medics++)));
			}
			if (pos != null) {
				taken.add(pos);
				PLACES.put(guard.getUUID(), new Place(pos.immutable(), kind == Guards.Kind.ARCHER, watch.ramparts));
				watch.guards.add(guard.getUUID());
				if (guard.isSleeping()) {
					guard.stopSleeping();
				}
			}
		}
		// On their way: a few guards a tick, in turn.
		int sent = 0;
		for (int i = 0; i < guards.size() && sent < MAX_PATHS; i++) {
			Villager guard = guards.get((watch.cursor + i) % guards.size());
			Place place = PLACES.get(guard.getUUID());
			if (place == null) {
				continue;
			}
			boolean there = at(guard, place.pos());
			WorkerStatus.set(guard, GuardCombat.title(guard), -1f,
				Component.translatable(there ? "message.aliveworkplace.guard.state.station" : "message.aliveworkplace.guard.state.to_station").withStyle(ChatFormatting.GOLD));
			if (there || GuardCombat.isFighting(guard) && !place.station()) {
				continue;
			}
			if (guard.isSleeping()) {
				guard.stopSleeping();
			}
			if (place.station() && place.pos().getY() - guard.getY() >= 1.5 && flat(guard.blockPosition(), place.pos()) <= CLIMB_REACH * CLIMB_REACH) {
				// Up the ladder (villagers can't path up one).
				guard.getNavigation().stop();
				guard.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				guard.teleportTo(place.pos().getX() + 0.5, place.pos().getY(), place.pos().getZ() + 0.5);
				level.playSound(null, place.pos(), SoundEvents.LADDER_STEP, SoundSource.NEUTRAL, 1f, 1f);
				continue;
			}
			guard.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(place.pos(), SPEED, 0));
			sent++;
		}
		watch.cursor = guards.isEmpty() ? 0 : (watch.cursor + Math.max(1, sent)) % guards.size();
		watch.most = Math.max(watch.most, sent);
	}

	private static int spread(int n) {
		return n % 2 == 0 ? n / 2 : -(n + 1) / 2;
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** Whether the station {@code s} is over the breach's side of the village: from the hall, toward the outside of the breach. */
	private static boolean side(ThreatData.Siege siege, BlockPos s) {
		return siege.breach != null && (s.getX() - siege.hall.getX()) * siege.out.getStepX() + (s.getZ() - siege.hall.getZ()) * siege.out.getStepZ() > 0;
	}

	/** The ground at or near the height of {@code pos}: the first spot to stand on from two above it down to three below. */
	private static BlockPos ground(ServerLevel level, BlockPos pos) {
		for (int dy = 2; dy >= -3; dy--) {
			if (standable(level, pos.above(dy))) {
				return pos.above(dy);
			}
		}
		return pos;
	}

	private BattleStations() {
	}
}
