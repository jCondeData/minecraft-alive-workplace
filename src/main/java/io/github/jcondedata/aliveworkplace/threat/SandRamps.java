package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sand ramps (ROADMAP 32.9): where pillagers set ladders ({@link Ladders}), the desert raiders pile sand against the wall.
 * In a siege by a culture whose {@code tactics} name {@link #TACTIC}, a raider with the role {@code climber} whose path
 * to the hall ends at a wall begins a ramp at the spot nearest where its path ends, and adds a block of sand every
 * {@link #EVERY} ticks while it stands by: one up for one out, so after every few blocks a whole ramp stands, one higher
 * than before, until its top is level with the wall's. Then the raiders walk up it, over the top and drop inside.
 * <ul>
 * <li><b>Only into air, on firm ground.</b> A wall is whatever stands at least two blocks high in the way (up to
 * {@link #MAX_HEIGHT}); every block of the ramp must be air (or sand of this siege) with something solid under its
 * column, and never against a gate. At most {@link #MAX_RAMPS} ramps a siege; a climber whose spot is within
 * {@link #SHARE} blocks of one piles onto it.</li>
 * <li><b>Every block is recorded with the siege</b> ({@link ThreatData.Siege#sand}, saved, with each ramp's foot) and
 * taken away when the raid is over ({@link #takeAway}): each one that is still sand goes, nothing drops.</li>
 * <li>With the {@code mobGriefing} rule off no sand is piled. {@code sieges} off: no siege, so no ramps.</li>
 * </ul>
 */
public final class SandRamps {
	/** The tactic a culture names to pile sand against the walls. */
	public static final String TACTIC = "sand_ramps";
	/** A block of sand every this many ticks. */
	public static final int EVERY = 10;
	/** The highest wall a ramp gets over. */
	public static final int MAX_HEIGHT = 8;
	/** The most ramps in one siege. */
	public static final int MAX_RAMPS = 2;
	/** The most paths asked for in one tick. */
	public static final int MAX_PATHS = 4;
	/** A climber whose own spot at the wall is within this of a ramp uses that ramp. */
	public static final int SHARE = 8;
	/** How near the hall a path must end to count as getting in. */
	private static final int REACH = 5;
	/** How often (ticks) a climber without a ramp asks for its path again. */
	private static final int TRY_EVERY = 40;
	/** How near (blocks) the next block a climber must be to pile it. */
	private static final double PILE_REACH = 12;

	private record Key(ResourceKey<Level> dimension, BlockPos hall) {
	}

	/** A ramp: its foot (the lowest block, against the wall), the side the wall is on, the wall's height. */
	private static final class Site {
		final BlockPos foot;
		final Direction into;
		final Set<UUID> over;
		int height;
		int inside = 2;

		Site(BlockPos foot, Direction into, Set<UUID> over) {
			this.foot = foot.immutable();
			this.into = into;
			this.over = over;
		}

		/** The blocks of the ramp in the order they are piled: stage by stage, each a whole ramp one higher, outside in. */
		List<BlockPos> blocks() {
			List<BlockPos> out = new ArrayList<>();
			for (int s = 1; s <= height; s++) {
				for (int d = s; d >= 1; d--) {
					out.add(foot.relative(into, -(d - 1)).above(s - d));
				}
			}
			return out;
		}

		/** Where a raider stands on top of the ramp, against the wall. */
		BlockPos top() {
			return foot.above(height);
		}
	}

	private static final class Storm {
		final List<Site> sites = new ArrayList<>();
		final Map<UUID, Site> assigned = new HashMap<>();
		final Map<UUID, Long> nextTry = new HashMap<>();
		final Map<UUID, Long> sentAt = new HashMap<>();
		final Set<UUID> over = ConcurrentHashMap.newKeySet();
		List<Mob> raiders = List.of();
		int steps;
	}

	private static final Map<Key, Storm> STORMS = new ConcurrentHashMap<>();
	private static final Map<UUID, Site> CLIMBS = new ConcurrentHashMap<>();
	private static final Set<Mob> GOALED = Collections.newSetFromMap(new WeakHashMap<>());

	public static void init() {
		Threats.registerTactic(TACTIC, new Threats.Tactic() {
			@Override
			public void begin(ServerLevel level, BlockPos hall, Culture culture, List<Mob> raiders) {
				Sieges.begin(level, hall, culture, raiders);
			}

			@Override
			public void end(ServerLevel level, BlockPos hall, Culture culture, boolean fled) {
				Sieges.fled(level, hall, fled);
				Sieges.raidOver(level, hall);
			}
		});
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % EVERY == 3) {
				tick(level);
			}
		});
		Platform.get().onServerStarting(server -> forget());
	}

	/** Forgets what is kept in memory, as a restart does (tests). The sand and the ramps' feet are saved with the siege. */
	public static void forget() {
		STORMS.clear();
		CLIMBS.clear();
	}

	static boolean claims(UUID mob) {
		return CLIMBS.containsKey(mob);
	}

	/** The blocks of sand standing for the siege of the village round {@code hall}. */
	public static List<BlockPos> sand(ServerLevel level, BlockPos hall) {
		return Sieges.siege(level, hall).map(s -> List.copyOf(s.sand)).orElse(List.of());
	}

	/** Whether {@code mob} got over a wall by a sand ramp in the siege of the village round {@code hall}. */
	public static boolean over(ServerLevel level, BlockPos hall, UUID mob) {
		Storm storm = STORMS.get(new Key(level.dimension(), hall));
		return storm != null && storm.over.contains(mob);
	}

	/** Takes every sand ramp of {@code siege} away: each recorded block that is still sand becomes air, nothing drops. */
	public static void takeAway(ServerLevel level, ThreatData data, ThreatData.Siege siege) {
		Storm storm = STORMS.remove(new Key(level.dimension(), siege.hall));
		if (storm != null) {
			storm.assigned.keySet().forEach(CLIMBS::remove);
		}
		if (siege.sand.isEmpty() && siege.rampFeet.isEmpty()) {
			return;
		}
		// From the top down, so nothing above is left on air to fall.
		List<BlockPos> sand = new ArrayList<>(siege.sand);
		sand.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos pos : sand) {
			if (level.getBlockState(pos).is(Blocks.SAND)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
			}
		}
		for (BlockPos pos : sand) {
			level.blockUpdated(pos, Blocks.AIR); // (the neighbours learn of it once all is gone)
		}
		if (!sand.isEmpty()) {
			level.playSound(null, sand.get(sand.size() - 1), SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.5f, 0.7f);
		}
		siege.sand.clear();
		siege.rampFeet.clear();
		siege.rampDirs.clear();
		data.setDirty();
	}

	// The tick.

	private static void tick(ServerLevel level) {
		ThreatData data = ThreatData.get(level);
		for (ThreatData.Siege siege : data.sieges()) {
			if (siege.over) {
				continue;
			}
			Optional<Culture> culture = data.raid(siege.hall).flatMap(r -> Threats.get(r.culture()));
			if (culture.isPresent() && culture.get().tactics().contains(TACTIC)) {
				step(level, data, siege, culture.get());
			}
		}
	}

	private static void step(ServerLevel level, ThreatData data, ThreatData.Siege siege, Culture culture) {
		Storm storm = STORMS.computeIfAbsent(new Key(level.dimension(), siege.hall), k -> restore(siege));
		if (storm.steps % 4 == 0) {
			storm.raiders = Sieges.raiders(level, siege.hall);
		}
		storm.steps++;
		List<Mob> raiders = storm.raiders.stream().filter(m -> m.isAlive() && !m.isRemoved()).toList();
		long now = level.getGameTime();
		boolean may = Ladders.allowed(level);
		for (Site site : storm.sites) {
			int h = height(level, siege, site.foot, site.into);
			if (h >= 0) {
				site.height = h;
				site.inside = inside(level, site);
			}
		}
		Set<UUID> alive = new HashSet<>();
		int paths = 0;
		boolean shut = siege.breach == null || !Sieges.open(siege);
		for (Mob mob : raiders) {
			UUID id = mob.getUUID();
			alive.add(id);
			Culture.Role role = Threats.role(mob);
			if (storm.over.contains(id) || role == Culture.Role.RAM || Sieges.through(level, siege.hall, id)) {
				release(storm, id);
				continue;
			}
			Site site = storm.assigned.get(id);
			boolean climber = role == Culture.Role.CLIMBER;
			if (site != null && !climber && !shut) {
				release(storm, id);
				continue;
			}
			if (site == null) {
				if (climber) {
					if (!may || paths >= MAX_PATHS || now < storm.nextTry.getOrDefault(id, 0L)) {
						continue;
					}
					storm.nextTry.put(id, now + TRY_EVERY);
					paths++;
					Path path = mob.getNavigation().createPath(siege.hall, REACH);
					if (path == null || path.canReach()) {
						continue;
					}
					site = siteFor(level, siege, storm, mob, path);
					if (site == null) {
						if (mob.getNavigation().isDone()) {
							mob.getNavigation().moveTo(path, 1.0);
						}
						continue;
					}
					data.setDirty();
				} else if (shut) {
					site = storm.sites.stream().filter(s -> standing(level, siege, s)).findFirst().orElse(null);
					if (site == null) {
						continue;
					}
				} else {
					continue;
				}
				storm.assigned.put(id, site);
			}
			CLIMBS.put(id, site);
			if (GOALED.add(mob)) {
				((MobAccessor) mob).aliveworkplace$goals().addGoal(0, new RampGoal(mob));
			}
			// To the ramp: on top of it once it stands, else beside its outer end while the sand goes up.
			boolean up = standing(level, siege, site);
			BlockPos to = up ? site.top() : site.foot.relative(site.into, -(site.height + 1));
			if (!crossing(mob, site) && paths < MAX_PATHS && (mob.getNavigation().isDone() || now - storm.sentAt.getOrDefault(id, 0L) > 60)
				&& mob.blockPosition().distSqr(to) > (up ? 0 : 4)) {
				storm.sentAt.put(id, now);
				mob.getNavigation().moveTo(mob.getNavigation().createPath(to, 0, 64), 1.0);
				paths++;
			}
		}
		storm.assigned.keySet().removeIf(id -> {
			if (!alive.contains(id)) {
				CLIMBS.remove(id);
				return true;
			}
			return false;
		});
		// A block of sand for every ramp with a climber by it.
		for (Site site : storm.sites) {
			if (!may || site.height < 2) {
				continue;
			}
			BlockPos next = null;
			for (BlockPos pos : site.blocks()) {
				if (!level.getBlockState(pos).is(Blocks.SAND) || !siege.sand.contains(pos)) {
					next = pos;
					break;
				}
			}
			if (next == null) {
				continue;
			}
			BlockPos at = next;
			Mob builder = raiders.stream().filter(m -> storm.assigned.get(m.getUUID()) == site && Threats.role(m) == Culture.Role.CLIMBER
				&& m.position().distanceToSqr(Vec3.atCenterOf(at)) <= PILE_REACH * PILE_REACH).findFirst().orElse(null);
			if (builder == null || !level.getBlockState(next).isAir() || !blocked(level, next.below())
				|| !level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(next)).isEmpty()) {
				continue;
			}
			level.setBlock(next, Blocks.SAND.defaultBlockState(), 3);
			siege.sand.add(next);
			data.setDirty();
			builder.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, next, SoundEvents.SAND_PLACE, SoundSource.HOSTILE, 1f, 0.9f);
			if (standing(level, siege, site) && !siege.ramped) {
				siege.ramped = true;
				Sieges.tell(level, siege.hall, Component.translatable(culture.messages().key("sand_ramps", "message.aliveworkplace.siege.sand_ramps"),
					VillageHalls.name(level, siege.hall)).withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void release(Storm storm, UUID id) {
		if (storm.assigned.remove(id) != null) {
			CLIMBS.remove(id);
		}
	}

	private static Storm restore(ThreatData.Siege siege) {
		Storm storm = new Storm();
		for (int i = 0; i < siege.rampFeet.size(); i++) {
			storm.sites.add(new Site(siege.rampFeet.get(i), Direction.from3DDataValue(siege.rampDirs.get(i)), storm.over));
		}
		return storm;
	}

	/** Whether every block of {@code site}'s ramp is this siege's sand, up to the wall's top. */
	private static boolean standing(ServerLevel level, ThreatData.Siege siege, Site site) {
		if (site.height < 2) {
			return false;
		}
		for (BlockPos pos : site.blocks()) {
			if (!level.getBlockState(pos).is(Blocks.SAND) || !siege.sand.contains(pos)) {
				return false;
			}
		}
		return true;
	}

	// Walls.

	private static boolean blocked(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static boolean ours(ServerLevel level, ThreatData.Siege siege, BlockPos pos) {
		return level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.SAND) && siege.sand.contains(pos);
	}

	/**
	 * How high a ramp at {@code foot} must be to reach the top of the wall beside it, or -1 when it can't be piled here:
	 * the wall is higher than {@link #MAX_HEIGHT} or a gate, a block of the ramp would go where something stands that
	 * isn't air (or this siege's sand), a column has nothing solid under it, or there is no room on top.
	 */
	private static int height(ServerLevel level, ThreatData.Siege siege, BlockPos foot, Direction into) {
		BlockPos wall = foot.relative(into);
		int h = -1;
		for (int y = 0; y <= MAX_HEIGHT; y++) {
			BlockPos w = wall.above(y);
			if (Sieges.isGate(level.getBlockState(w))) {
				return -1;
			}
			if (!blocked(level, w) && !blocked(level, w.above())) {
				h = y;
				break;
			}
		}
		if (h < 0) {
			return -1;
		}
		for (int d = 1; d <= h; d++) {
			BlockPos column = foot.relative(into, -(d - 1));
			if (!blocked(level, column.below()) && !(level.getBlockState(column.below()).is(Blocks.SAND) && siege.sand.contains(column.below()))) {
				return -1;
			}
			for (int y = 0; y <= h - d; y++) {
				if (!ours(level, siege, column.above(y))) {
					return -1;
				}
			}
		}
		if (blocked(level, foot.above(h)) || blocked(level, foot.above(h + 1))) {
			return -1;
		}
		return h;
	}

	private static int inside(ServerLevel level, Site site) {
		int top = site.foot.getY() + site.height;
		for (int k = 2; k <= 8; k++) {
			BlockPos column = site.foot.relative(site.into, k);
			boolean free = true;
			for (int y = site.foot.getY(); y <= top + 1 && free; y++) {
				free = !blocked(level, column.atY(y));
			}
			if (free) {
				return k;
			}
		}
		return 2;
	}

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
			} else {
				into = dir;
			}
		}
		if (into == null) {
			return null;
		}
		for (Site site : storm.sites) {
			if (site.foot.distSqr(at) <= SHARE * SHARE) {
				return site;
			}
		}
		if (storm.sites.size() >= MAX_RAMPS) {
			return storm.sites.get(0);
		}
		Direction side = into.getClockWise();
		for (int k : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			BlockPos foot = at.relative(side, k);
			if (blocked(level, foot) || !blocked(level, foot.below())) {
				continue;
			}
			int height = height(level, siege, foot, into);
			if (height < 2) {
				continue;
			}
			Site site = new Site(foot, into, storm.over);
			site.height = height;
			site.inside = inside(level, site);
			storm.sites.add(site);
			siege.rampFeet.add(site.foot);
			siege.rampDirs.add(into.get3DDataValue());
			return site;
		}
		return null;
	}

	// The climb.

	/** How far in from the foot's middle {@code mob} is (toward the wall and beyond). */
	private static double inward(Mob mob, Site site) {
		return (mob.getX() - (site.foot.getX() + 0.5)) * site.into.getStepX() + (mob.getZ() - (site.foot.getZ() + 0.5)) * site.into.getStepZ();
	}

	/** Whether {@code mob} is on top of the ramp by the wall, or past it over the wall. */
	private static boolean crossing(Mob mob, Site site) {
		double aside = Math.abs((mob.getX() - (site.foot.getX() + 0.5)) * site.into.getStepZ() + (mob.getZ() - (site.foot.getZ() + 0.5)) * site.into.getStepX());
		double inward = inward(mob, site);
		if (aside >= 1.5 || inward >= site.inside + 2) {
			return false;
		}
		// On the ramp's top by the wall; or past it, up on the wall (its top, a walkway) or dropping down inside.
		return inward >= -0.6 && mob.getY() >= site.foot.getY() + site.height - 0.6
			|| inward >= 0.6 && (mob.getY() > site.foot.getY() + 1.5 || !mob.onGround());
	}

	/** Holds a raider to its ramp (it goes where the tick sends it, nowhere else): once it is on top, by the wall, it walks across the wall's top and drops inside; then it is let go. */
	private static final class RampGoal extends Goal {
		private final Mob mob;

		RampGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE));
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
			double inward = inward(mob, site);
			// Let go once down inside: a raider still up on the wall is pushed on.
			if (inward >= 1.5 && inward < site.inside + 3 && mob.onGround() && mob.getY() < site.foot.getY() + 1.5) {
				site.over.add(mob.getUUID());
				CLIMBS.remove(mob.getUUID());
				return;
			}
			if (!crossing(mob, site)) {
				return; // (walking to the ramp: the tick sends it)
			}
			mob.getNavigation().stop();
			mob.getMoveControl().setWantedPosition(site.foot.getX() + 0.5 + site.into.getStepX() * (site.inside + 1), site.foot.getY(),
				site.foot.getZ() + 0.5 + site.into.getStepZ() * (site.inside + 1), 1.0);
		}
	}

	private SandRamps() {
	}
}
