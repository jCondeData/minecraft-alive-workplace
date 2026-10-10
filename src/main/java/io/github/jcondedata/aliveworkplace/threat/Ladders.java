package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sieges II (ROADMAP 32.5): ladders over the walls. In a siege by a culture whose {@code tactics} name {@link #TACTIC},
 * the raiders with the role {@code climber} whose path to the hall ends at a wall set a ladder up its outer face, at the
 * spot nearest where their path ends: a rung every {@link #EVERY} ticks while a climber stands at its foot, up to
 * {@link #MAX_HEIGHT} high. They climb over and drop inside, and the other raiders (not the rams) follow up the same
 * ladder while the gate is shut.
 * <ul>
 * <li><b>Any wall, only into air.</b> A wall is whatever stands at least two blocks high in the way, a player's too; a
 * rung only ever replaces air, and never leans on a gate. At most {@link #MAX_LADDERS} ladders a siege; a climber whose
 * spot is within {@link #SHARE} blocks of one uses it.</li>
 * <li><b>A guard throws it down.</b> A guard standing at least two blocks above a ladder's foot and within
 * {@link #THROW_REACH} blocks of its top rung throws it down: the whole column goes, whoever is on it falls, and the
 * raiders wait {@link #RAISE_AGAIN} ticks before they raise it again.</li>
 * <li><b>Every rung is recorded with the siege</b> ({@link ThreatData.Siege#ladders}, saved) and taken away when the
 * raid is over ({@link #takeAway}), also the ones a restart found standing. Nothing else is changed and nothing drops.</li>
 * <li>With the {@code mobGriefing} rule off no rung is set. {@code sieges} off: no siege, so no ladders.</li>
 * <li>It runs beside the siege's director, every {@link #EVERY} ticks (never in the director's own tick), asking for at
 * most {@link #MAX_PATHS} paths a tick.</li>
 * </ul>
 */
public final class Ladders {
	/** The tactic a culture names to set ladders against the walls. */
	public static final String TACTIC = "ladders";
	/** A rung every this many ticks. */
	public static final int EVERY = 10;
	/** The highest wall a ladder gets over (rungs). */
	public static final int MAX_HEIGHT = 10;
	/** The most ladders in one siege. */
	public static final int MAX_LADDERS = 3;
	/** The most paths asked for in one tick. */
	public static final int MAX_PATHS = 4;
	/** A guard this near a ladder's top rung throws it down. */
	public static final int THROW_REACH = 2;
	/** How long (ticks) the raiders wait before raising a ladder that was thrown down. */
	public static final int RAISE_AGAIN = 200;
	/** A climber whose own spot at the wall is within this of a ladder uses that ladder. */
	public static final int SHARE = 8;
	/** How near the hall (blocks, as paths count them) a path must end to count as getting in. */
	private static final int REACH = 5;
	/** How often (ticks) a climber without a ladder asks for its path again. */
	private static final int TRY_EVERY = 40;

	private record Key(ResourceKey<Level> dimension, BlockPos hall) {
	}

	/** A ladder: its foot (the lowest rung's place), the side the wall is on, and how it stands. */
	private static final class Site {
		final BlockPos foot;
		final Direction into;
		/** The raiders of this siege who are over the wall (shared by its ladders). */
		final Set<UUID> over;
		/** The height a climber's feet must reach to get over; the rungs go from the foot to one below it. */
		int top;
		/** How many blocks in from the foot the ground inside is. */
		int inside = 2;
		int rungs;
		long downUntil;

		Site(BlockPos foot, Direction into, Set<UUID> over) {
			this.foot = foot.immutable();
			this.into = into;
			this.over = over;
			this.top = foot.getY();
		}

		boolean finished() {
			return rungs >= top - foot.getY();
		}

		/** Whether the ladder is up: every rung set, and at least one. */
		boolean standing() {
			return rungs > 0 && finished();
		}
	}

	/** What is kept in memory of a siege's ladders (rebuilt from the saved rungs after a restart). */
	private static final class Storm {
		final List<Site> sites = new ArrayList<>();
		final Map<UUID, Site> assigned = new HashMap<>();
		final Map<UUID, Long> nextTry = new HashMap<>();
		/** Where each raider was last sent (so one on an older errand is sent anew). */
		final Map<UUID, BlockPos> sent = new HashMap<>();
		final Set<UUID> over = ConcurrentHashMap.newKeySet();
		List<Mob> raiders = List.of();
		int steps;
		int cursor;
		int most;
		int thrown;
	}

	private static final Map<Key, Storm> STORMS = new ConcurrentHashMap<>();
	/** The ladder each raider is held to (its goal reads it). */
	private static final Map<UUID, Site> CLIMBS = new ConcurrentHashMap<>();
	private static final Set<Mob> GOALED = Collections.newSetFromMap(new WeakHashMap<>());

	public static void init() {
		Threats.registerTactic(TACTIC, new Threats.Tactic() {
			@Override
			public void begin(ServerLevel level, BlockPos hall, Culture culture, List<Mob> raiders) {
				Sieges.begin(level, hall, culture, raiders); // (the siege its rams may already have laid)
			}

			@Override
			public void end(ServerLevel level, BlockPos hall, Culture culture, boolean fled) {
				Sieges.fled(level, hall, fled);
				Sieges.raidOver(level, hall);
			}
		});
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % EVERY == EVERY / 2) {
				tick(level);
			}
		});
		Platform.get().onServerStarting(server -> forget());
	}

	/** Forgets what is kept in memory, as a restart does (tests). The rungs themselves are saved with the siege. */
	public static void forget() {
		STORMS.clear();
		CLIMBS.clear();
	}

	/** Whether raiders may set rungs here: the {@code mobGriefing} rule is on. */
	public static boolean allowed(ServerLevel level) {
		return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** Whether {@code mob} is held to a ladder now (the siege's director leaves it be). */
	static boolean claims(UUID mob) {
		return CLIMBS.containsKey(mob);
	}

	// What others read.

	/** The rungs standing for the siege of the village round {@code hall}. */
	public static List<BlockPos> rungs(ServerLevel level, BlockPos hall) {
		return Sieges.siege(level, hall).map(s -> List.copyOf(s.ladders)).orElse(List.of());
	}

	/** How many ladders guards threw down in the siege of the village round {@code hall} (since the last restart). */
	public static int thrown(ServerLevel level, BlockPos hall) {
		Storm storm = STORMS.get(new Key(level.dimension(), hall));
		return storm == null ? 0 : storm.thrown;
	}

	/** Whether {@code mob} got over a wall by a ladder in the siege of the village round {@code hall}. */
	public static boolean over(ServerLevel level, BlockPos hall, UUID mob) {
		Storm storm = STORMS.get(new Key(level.dimension(), hall));
		return storm != null && storm.over.contains(mob);
	}

	/** The most paths asked for in one tick for the ladders of that siege (-1: nothing ran yet). */
	public static int mostPaths(ServerLevel level, BlockPos hall) {
		Storm storm = STORMS.get(new Key(level.dimension(), hall));
		return storm == null ? -1 : storm.most;
	}

	// Taking them away.

	/** Takes every ladder of {@code siege} away: each recorded rung that is still a ladder becomes air, nothing drops. */
	public static void takeAway(ServerLevel level, ThreatData data, ThreatData.Siege siege) {
		Storm storm = STORMS.remove(new Key(level.dimension(), siege.hall));
		if (storm != null) {
			storm.assigned.keySet().forEach(CLIMBS::remove);
		}
		if (siege.ladders.isEmpty()) {
			return;
		}
		for (BlockPos pos : siege.ladders) {
			if (level.getBlockState(pos).is(Blocks.LADDER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		level.playSound(null, siege.ladders.get(0), SoundEvents.LADDER_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
		siege.ladders.clear();
		data.setDirty();
	}

	// The tick.

	private static void tick(ServerLevel level) {
		ThreatData data = ThreatData.get(level);
		for (ThreatData.Siege siege : data.sieges()) {
			if (siege.over) {
				continue; // (the director takes the ladders away)
			}
			Optional<Culture> culture = data.raid(siege.hall).flatMap(r -> Threats.get(r.culture()));
			if (culture.isPresent() && culture.get().tactics().contains(TACTIC)) {
				step(level, data, siege, culture.get());
			}
		}
	}

	private static void step(ServerLevel level, ThreatData data, ThreatData.Siege siege, Culture culture) {
		Storm storm = STORMS.computeIfAbsent(new Key(level.dimension(), siege.hall), k -> restore(level, data, siege));
		if (storm.steps % 4 == 0) {
			storm.raiders = Sieges.raiders(level, siege.hall);
		}
		storm.steps++;
		List<Mob> raiders = storm.raiders.stream().filter(m -> m.isAlive() && !m.isRemoved()).toList();
		long now = level.getGameTime();
		boolean may = allowed(level);
		// The ladders as the world has them now: a wall that changed, a rung someone took.
		for (Iterator<Site> it = storm.sites.iterator(); it.hasNext(); ) {
			Site site = it.next();
			int height = height(level, siege, site.foot, site.into);
			if (height < 0) {
				site.top = site.foot.getY();
				trim(level, data, siege, site);
				it.remove();
				storm.assigned.values().removeIf(s -> s == site);
				CLIMBS.values().removeIf(s -> s == site);
				continue;
			}
			site.top = site.foot.getY() + height;
			site.inside = inside(level, site);
			trim(level, data, siege, site);
		}
		// A guard at a ladder's top throws it down.
		for (Site site : storm.sites) {
			if (site.rungs > 0) {
				Villager guard = guardAt(level, site);
				if (guard != null) {
					throwDown(level, data, siege, storm, site, guard, now);
				}
			}
		}
		// Who goes to which ladder.
		Set<UUID> alive = new HashSet<>();
		int paths = 0;
		int n = raiders.size();
		boolean shut = siege.breach == null || !Sieges.open(siege);
		for (int i = 0; i < n; i++) {
			Mob mob = raiders.get((storm.cursor + i) % n);
			UUID id = mob.getUUID();
			alive.add(id);
			Culture.Role role = Threats.role(mob);
			if (storm.over.contains(id) || role == Culture.Role.RAM || Sieges.through(level, siege.hall, id)) {
				release(storm, id);
				continue;
			}
			Site site = storm.assigned.get(id);
			boolean climber = role == Culture.Role.CLIMBER;
			if (site != null && !climber && !onIt(mob, site) && (siege.breach != null && !site.standing() || !shut)) {
				release(storm, id); // (back to the others at the gate: its ladder went down, or the gate is open)
				continue;
			}
			if (site == null) {
				if (role == Culture.Role.CLIMBER) {
					if (!may || paths >= MAX_PATHS || now < storm.nextTry.getOrDefault(id, 0L)) {
						continue;
					}
					storm.nextTry.put(id, now + TRY_EVERY);
					paths++;
					Path path = mob.getNavigation().createPath(siege.hall, REACH);
					if (path == null || path.canReach()) {
						continue; // (no wall in its way)
					}
					site = siteFor(level, siege, storm, mob, path);
					if (site == null) {
						if (mob.getNavigation().isDone()) {
							mob.getNavigation().moveTo(path, 1.0); // (as far as it gets; it asks again from there)
						}
						continue;
					}
				} else if (shut) {
					// The others: up a ladder that stands; with no gate to wait at, they wait at the nearest ladder's foot.
					site = nearest(storm, mob.blockPosition(), true);
					if (site == null && siege.breach == null) {
						site = nearest(storm, mob.blockPosition(), false);
					}
					if (site == null) {
						continue;
					}
				} else {
					continue;
				}
				storm.assigned.put(id, site);
				storm.sent.remove(id);
			}
			CLIMBS.put(id, site);
			if (GOALED.add(mob)) {
				((MobAccessor) mob).aliveworkplace$goals().addGoal(0, new ClimbGoal(mob));
			}
			if (!approaching(mob, site) || paths >= MAX_PATHS) {
				continue;
			}
			// To the foot; one who only follows waits three blocks out from it until the ladder stands.
			boolean waits = !climber && !site.standing();
			BlockPos to = waits ? site.foot.relative(site.into, -3) : site.foot;
			if (!to.equals(storm.sent.get(id)) || mob.getNavigation().isDone() && (!waits || mob.blockPosition().distSqr(to) > 4)) {
				storm.sent.put(id, to);
				mob.getNavigation().moveTo(mob.getNavigation().createPath(to, 0, 64), 1.0); // (to the block itself, however far)
				paths++;
			}
		}
		if (n > 0) {
			storm.cursor = (storm.cursor + MAX_PATHS) % n;
		}
		storm.assigned.keySet().removeIf(id -> {
			if (!alive.contains(id)) {
				CLIMBS.remove(id);
				storm.sent.remove(id);
				return true;
			}
			return false;
		});
		// A rung for every ladder with a climber at its foot.
		for (Site site : storm.sites) {
			if (!may || site.finished() || now < site.downUntil) {
				continue;
			}
			Vec3 foot = Vec3.atBottomCenterOf(site.foot);
			Mob builder = raiders.stream().filter(m -> storm.assigned.get(m.getUUID()) == site && Threats.role(m) == Culture.Role.CLIMBER
				&& m.position().distanceToSqr(foot) <= 9).findFirst().orElse(null);
			BlockPos pos = site.foot.above(site.rungs);
			if (builder == null || !level.getBlockState(pos).isAir()) {
				continue;
			}
			level.setBlock(pos, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, site.into.getOpposite()), 3);
			siege.ladders.add(pos);
			site.rungs++;
			data.setDirty();
			builder.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, pos, SoundEvents.LADDER_PLACE, SoundSource.HOSTILE, 1f, 0.9f);
			if (site.finished() && !siege.laddered) {
				siege.laddered = true;
				Sieges.tell(level, siege.hall, Component.translatable(culture.messages().key("ladders", "message.aliveworkplace.siege.ladders"),
					VillageHalls.name(level, siege.hall)).withStyle(ChatFormatting.RED));
			}
		}
		storm.most = Math.max(storm.most, paths);
	}

	private static void release(Storm storm, UUID id) {
		if (storm.assigned.remove(id) != null) {
			CLIMBS.remove(id);
			storm.sent.remove(id);
		}
	}

	/** The ladders of {@code siege} as its saved rungs give them: one for each column, facing as its lowest rung does. */
	private static Storm restore(ServerLevel level, ThreatData data, ThreatData.Siege siege) {
		Storm storm = new Storm();
		Map<Long, List<BlockPos>> columns = new TreeMap<>();
		for (BlockPos pos : siege.ladders) {
			columns.computeIfAbsent(BlockPos.asLong(pos.getX(), 0, pos.getZ()), k -> new ArrayList<>()).add(pos);
		}
		for (List<BlockPos> column : columns.values()) {
			BlockPos foot = column.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
			BlockState state = level.getBlockState(foot);
			if (!state.is(Blocks.LADDER)) {
				// Its foot is gone: what is left of it comes down, and the climbers start again.
				for (BlockPos pos : column) {
					remove(level, siege, pos);
				}
				data.setDirty();
				continue;
			}
			Site site = new Site(foot, state.getValue(LadderBlock.FACING).getOpposite(), storm.over);
			site.rungs = column.size();
			storm.sites.add(site);
		}
		return storm;
	}

	// Walls.

	private static boolean blocked(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * How many rungs a ladder at {@code foot} needs to get over the wall beside it: up to the first height at which the
	 * wall's column leaves two blocks free (0: no wall here any more). -1 when there is no getting over here: the wall is higher than
	 * {@link #MAX_HEIGHT}, a rung would replace something that isn't air (or a rung of this siege), it would lean on a
	 * gate, or there is no room over the ladder.
	 */
	private static int height(ServerLevel level, ThreatData.Siege siege, BlockPos foot, Direction into) {
		BlockPos wall = foot.relative(into);
		for (int h = 0; h <= MAX_HEIGHT; h++) {
			BlockPos w = wall.above(h);
			if (!blocked(level, w) && !blocked(level, w.above())) {
				return blocked(level, foot.above(h)) || blocked(level, foot.above(h + 1)) ? -1 : h;
			}
			BlockPos rung = foot.above(h);
			BlockState there = level.getBlockState(rung);
			if (!there.isAir() && !(there.is(Blocks.LADDER) && siege.ladders.contains(rung))) {
				return -1;
			}
			if (Sieges.isGate(level.getBlockState(w))) {
				return -1;
			}
		}
		return -1;
	}

	/** How many blocks in from the foot of {@code site} the first column stands free from the ground to over the wall. */
	private static int inside(ServerLevel level, Site site) {
		for (int k = 2; k <= 8; k++) {
			BlockPos column = site.foot.relative(site.into, k);
			boolean free = true;
			for (int y = site.foot.getY() + 1; y <= site.top + 1 && free; y++) {
				free = !blocked(level, column.atY(y));
			}
			if (free) {
				return k;
			}
		}
		return 2;
	}

	/** Brings {@code site}'s rungs in line with the world: those above a missing one, or above the wall's top, come down. */
	private static void trim(ServerLevel level, ThreatData data, ThreatData.Siege siege, Site site) {
		int want = site.top - site.foot.getY();
		int have = 0;
		while (have < site.rungs && have < want && level.getBlockState(site.foot.above(have)).is(Blocks.LADDER)) {
			have++;
		}
		if (have == site.rungs) {
			return;
		}
		for (int i = have; i < site.rungs; i++) {
			remove(level, siege, site.foot.above(i));
		}
		site.rungs = have;
		data.setDirty();
	}

	/** Takes the rung at {@code pos} away (if it still stands) and off the siege's list. */
	private static void remove(ServerLevel level, ThreatData.Siege siege, BlockPos pos) {
		if (siege.ladders.remove(pos) && level.getBlockState(pos).is(Blocks.LADDER)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	/** The ladder nearest {@code pos}; with {@code standing}, only among those that are up. */
	@Nullable
	private static Site nearest(Storm storm, BlockPos pos, boolean standing) {
		Site best = null;
		for (Site site : storm.sites) {
			if (standing && !site.standing()) {
				continue;
			}
			if (best == null || site.foot.distSqr(pos) < best.foot.distSqr(pos)) {
				best = site;
			}
		}
		return best;
	}

	/**
	 * The ladder for {@code mob}, whose {@code path} to the hall ends short of it: the wall is looked for from where the
	 * path ends, straight toward the hall; a ladder already within {@link #SHARE} blocks of that spot is used, else a new
	 * one is begun there or a little to either side. Null when the path doesn't end at a wall, or no ladder fits.
	 */
	@Nullable
	private static Site siteFor(ServerLevel level, ThreatData.Siege siege, Storm storm, Mob mob, Path path) {
		Node end = path.getEndNode();
		BlockPos at = end == null ? mob.blockPosition() : end.asBlockPos();
		Direction into = null;
		for (int i = 0; i < 6 && into == null; i++) {
			int dx = siege.hall.getX() - at.getX();
			int dz = siege.hall.getZ() - at.getZ();
			if (dx == 0 && dz == 0) {
				return null;
			}
			Direction dir = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			BlockPos next = at.relative(dir);
			if (!blocked(level, next) && !blocked(level, next.above())) {
				for (int down = 0; down < 3 && !blocked(level, next.below()); down++) {
					next = next.below();
				}
				at = next;
			} else if (blocked(level, next) && level.getBlockState(next).getCollisionShape(level, next).max(Direction.Axis.Y) <= 1.0
				&& !blocked(level, next.above()) && !blocked(level, next.above(2)) && !blocked(level, at.above(2))) {
				at = next.above(); // a step up
			} else {
				into = dir;
			}
		}
		if (into == null) {
			return null;
		}
		Site near = nearest(storm, at, false);
		if (near != null && (near.foot.distSqr(at) <= SHARE * SHARE || storm.sites.size() >= MAX_LADDERS)) {
			return near;
		}
		Direction side = into.getClockWise();
		for (int k : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			BlockPos foot = at.relative(side, k);
			if (blocked(level, foot) || blocked(level, foot.above()) || !blocked(level, foot.below())) {
				continue;
			}
			int height = height(level, siege, foot, into);
			if (height < 2) {
				continue; // (not a wall, or no getting over it here)
			}
			Site site = new Site(foot, into, storm.over);
			site.top = foot.getY() + height;
			site.inside = inside(level, site);
			storm.sites.add(site);
			return site;
		}
		return null;
	}

	// The guard.

	/** A guard who can throw {@code site}'s ladder down: standing on something at least two above its foot, within reach of its top rung. */
	@Nullable
	private static Villager guardAt(ServerLevel level, Site site) {
		BlockPos top = site.foot.above(site.rungs - 1);
		List<Villager> guards = level.getEntitiesOfClass(Villager.class, new AABB(top).inflate(THROW_REACH),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && v.getVillagerData().getProfession() == ModVillagers.GUARD
				&& v.getY() >= site.foot.getY() + 2 && blocked(level, BlockPos.containing(v.getX(), v.getY() - 0.2, v.getZ())));
		return guards.isEmpty() ? null : guards.get(0);
	}

	/** {@code guard} throws {@code site}'s ladder down: the whole column goes (nothing drops) and whoever is on it falls. */
	private static void throwDown(ServerLevel level, ThreatData data, ThreatData.Siege siege, Storm storm, Site site, Villager guard, long now) {
		BlockPos top = site.foot.above(site.rungs - 1);
		for (int i = site.rungs - 1; i >= 0; i--) {
			BlockPos pos = site.foot.above(i);
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.LADDER)) {
				level.levelEvent(2001, pos, Block.getId(state)); // the block's break particles and sound
			}
			remove(level, siege, pos);
		}
		site.rungs = 0;
		site.downUntil = now + RAISE_AGAIN;
		storm.thrown++;
		data.setDirty();
		guard.getLookControl().setLookAt(Vec3.atCenterOf(top));
		guard.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, top, SoundEvents.LADDER_BREAK, SoundSource.NEUTRAL, 1.5f, 0.6f);
	}

	// The climb.

	/** Whether {@code mob} is on {@code site}'s ladder or over its wall already (so it isn't sent anywhere else). */
	private static boolean onIt(Mob mob, Site site) {
		return !approaching(mob, site) && mob.getY() > site.foot.getY() + 0.5;
	}

	/** Whether {@code mob} still has to walk to {@code site}'s foot: it is neither there nor past it, over the wall. */
	private static boolean approaching(Mob mob, Site site) {
		return !atFoot(mob, site) && !crossing(mob, site);
	}

	private static boolean atFoot(Mob mob, Site site) {
		return Math.abs(mob.getX() - (site.foot.getX() + 0.5)) < 0.75 && Math.abs(mob.getZ() - (site.foot.getZ() + 0.5)) < 0.75
			&& mob.getY() >= site.foot.getY() - 0.5;
	}

	/** How far in from the foot's middle {@code mob} is (toward the wall and beyond). */
	private static double inward(Mob mob, Site site) {
		return (mob.getX() - (site.foot.getX() + 0.5)) * site.into.getStepX() + (mob.getZ() - (site.foot.getZ() + 0.5)) * site.into.getStepZ();
	}

	private static boolean crossing(Mob mob, Site site) {
		double aside = Math.abs((mob.getX() - (site.foot.getX() + 0.5)) * site.into.getStepZ() + (mob.getZ() - (site.foot.getZ() + 0.5)) * site.into.getStepX());
		double inward = inward(mob, site);
		return inward >= 0.6 && inward < site.inside + 2 && aside < 1.5 && mob.getY() >= site.foot.getY() - 1;
	}

	/**
	 * Holds a raider to its ladder: it waits at the foot while the rungs go up, climbs when they stand (as fast as a
	 * ladder is climbed), walks across the wall's top and drops inside; then it is let go. When the ladder goes from under
	 * it, it falls.
	 */
	private static final class ClimbGoal extends Goal {
		private final Mob mob;

		ClimbGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return CLIMBS.containsKey(mob.getUUID());
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Site site = CLIMBS.get(mob.getUUID());
			if (site == null) {
				return;
			}
			double cx = site.foot.getX() + 0.5;
			double cz = site.foot.getZ() + 0.5;
			int sx = site.into.getStepX();
			int sz = site.into.getStepZ();
			if (crossing(mob, site)) {
				// Over the top: across the wall and down inside.
				double inward = inward(mob, site);
				if (inward >= site.inside - 0.4 || mob.onGround() && mob.getY() < site.top - 1.5 && inward >= 1.5) {
					site.over.add(mob.getUUID());
					CLIMBS.remove(mob.getUUID());
					return;
				}
				mob.getNavigation().stop();
				mob.getMoveControl().setWantedPosition(cx + sx * site.inside, site.foot.getY(), cz + sz * site.inside, 1.0);
				return;
			}
			if (!atFoot(mob, site)) {
				// Walking to the foot (the tick sends it); the last step is taken straight, a path stops a little short.
				double dx = cx - mob.getX();
				double dz = cz - mob.getZ();
				if (dx * dx + dz * dz < 4 && Math.abs(mob.getY() - site.foot.getY()) < 1 && mob.getNavigation().isDone()
					&& (site.standing() || Threats.role(mob) == Culture.Role.CLIMBER)) {
					mob.getMoveControl().setWantedPosition(cx, site.foot.getY(), cz, 1.0);
				}
				return;
			}
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(cx + sx, site.top, cz + sz);
			if (!site.finished()) {
				return; // the rungs are going up (or it came down: it falls, and waits)
			}
			double y = mob.getY();
			if (y >= site.top - 0.4) {
				// At the top: over the edge.
				mob.setDeltaMovement(sx * 0.2, y < site.top + 0.15 ? 0.18 : 0, sz * 0.2);
				mob.resetFallDistance();
			} else if (Mth.floor(y) - site.foot.getY() < site.rungs) {
				mob.setDeltaMovement((cx - mob.getX()) * 0.3, 0.18, (cz - mob.getZ()) * 0.3);
				mob.resetFallDistance();
			}
		}
	}

	private Ladders() {
	}
}
