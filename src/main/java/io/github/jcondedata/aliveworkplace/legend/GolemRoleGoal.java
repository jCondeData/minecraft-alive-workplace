package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.farm.FarmhandGolems;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The work of a Golem Smith's golem (29.15), as one of the iron golem's own goals (added to every iron golem by
 * {@code IronGolemRolesMixin}; it does nothing for a plain golem). It holds the golem's legs while a Hauler carries
 * goods or a Farmhand tends the fields, so the golem's wandering doesn't pull it off the job; a fight comes first (the
 * golem's attack goal ranks above this one and takes over whenever it has a target). The Wall Sentry's post is held by
 * {@link WallSentries.HoldGoal}, which ranks above the attack.
 *
 * <p>While at work the golem swims, the way most mobs do (B722): a plain iron golem sinks and walks the bottom, so in
 * water two deep with steep banks (a moat round a Storehouse) it could never climb out and stood there with its load
 * for good. At work its paths may cross water on the surface ({@code setCanFloat}) and it keeps its head up in deep
 * water, so it crosses or climbs back out at the bank; a plain golem, and a role golem in a fight, keep vanilla's
 * sinking.
 *
 * <p>Vanilla's paths only leave water where the bank is level with the surface: a floating golem's paths start on the
 * top water block, a step up from there is one block, and the bank of a pool or moat not filled to the brim stands a
 * block and a half above a floating golem's feet, so no path led out and the golem floated by the bank for good
 * (B783). When its path can't take it out of the water, the golem picks the nearest bank it can climb (at most
 * {@link #CLIMB} above its feet, with room to stand), toward where it is going, swims there and pulls itself up, the
 * way a swimming player climbs out of a pool. A golem on the bank whose path ends there, short of where it is going,
 * wades in once toward it (an island in a moat whose banks are too high for a path) and climbs out on the far side,
 * but only into water it could climb back out of where it went in (B813, {@link GolemPaths}).
 *
 * <p>At work its paths keep a block clear of lava and fire (B784). Vanilla lets a path run along a lava trench's edge at
 * a small cost, and a golem is wide: it steers for the middle of its 2x2 nodes but counts a node reached half a block
 * early, so turning round a trench's end it cut the corner and its whole width slid over the lava. Lava sunk below the
 * ground counts too (B814, {@link GolemPaths}).
 */
public class GolemRoleGoal extends Goal {
	/** How often (ticks) the golem decides its next step; walking happens in between. */
	public static final int EVERY = 5;
	private static final double SPEED = 1.0;
	/**
	 * How long (ticks) a golem may walk without getting a block farther before it squeezes through: its path runs
	 * between mobs that stand still and push it back (villagers with no AI, 2 blocks apart, are a golem's width), and
	 * until it arrives nothing pushes it (B329).
	 */
	public static final int STUCK_TICKS = 40;
	/** Where each walking golem last made headway, {x, z, game time}. */
	private static final Map<IronGolem, double[]> HEADWAY = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Set<IronGolem> SQUEEZING = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
	/** How many banks each golem has climbed out on in its current walk (at most {@link #CLIMBS}, B783). */
	private static final Map<IronGolem, Integer> CLIMBED = Collections.synchronizedMap(new WeakHashMap<>());
	/** Banks a golem climbs out on in one walk; past that it floats until it gives the walk up, as across water it can't leave. */
	private static final int CLIMBS = 2;
	/** Golems that have waded in on their current walk (once a walk, B783). */
	private static final Set<IronGolem> WADED = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
	/** Where each walking golem is going (for the bank it climbs out on, B783). */
	private static final Map<IronGolem, BlockPos> WALK_TARGETS = Collections.synchronizedMap(new WeakHashMap<>());
	/** How high (blocks) above its feet a golem in water climbs out onto a bank (B783): a block above the surface. */
	public static final double CLIMB = 1.75;
	/** How far (blocks) a golem in water looks for a bank to climb out on. */
	private static final int EXIT_RANGE = 8;
	/** Ticks a golem tries one bank before it looks again. */
	private static final int CLIMB_TICKS = 160;
	/** The bank each golem in water is climbing out on, {x, y, z, game time it was chosen}, y being the standing height. */
	private static final Map<IronGolem, double[]> EXITS = Collections.synchronizedMap(new WeakHashMap<>());
	private final IronGolem golem;
	/** The golem's own path costs by lava and fire, put back when the work stops (B784). */
	private float dangerFire = PathType.DANGER_FIRE.getMalus();
	private float damageFire = PathType.DAMAGE_FIRE.getMalus();

	public GolemRoleGoal(IronGolem golem) {
		this.golem = golem;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(golem.level() instanceof ServerLevel) || golem.getTarget() != null || golem.isPassenger()) {
			return false;
		}
		GolemSmith.Role role = GolemSmith.role(golem);
		return role == GolemSmith.Role.HAULER || role == GolemSmith.Role.FARMHAND;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		golem.getNavigation().setCanFloat(true);
		dangerFire = golem.getPathfindingMalus(PathType.DANGER_FIRE);
		damageFire = golem.getPathfindingMalus(PathType.DAMAGE_FIRE);
		golem.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F); // keeps a block clear of lava and fire (B784)
		golem.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
		GolemPaths.atWork(golem, true); // and of sunken lava and water too low to leave (B813, B814)
	}

	@Override
	public void stop() {
		golem.getNavigation().stop();
		golem.getNavigation().setCanFloat(false);
		golem.setPathfindingMalus(PathType.DANGER_FIRE, dangerFire);
		golem.setPathfindingMalus(PathType.DAMAGE_FIRE, damageFire);
		GolemPaths.atWork(golem, false);
		arrived(golem);
		EXITS.remove(golem);
	}

	@Override
	public void tick() {
		if (golem.isInWater() && golem.getFluidHeight(FluidTags.WATER) > golem.getFluidJumpThreshold()) {
			golem.getJumpControl().jump(); // swims up, as vanilla's FloatGoal does (B722)
		}
		if (golem.level() instanceof ServerLevel here) {
			climbOut(here, golem);
		}
		if ((golem.tickCount + golem.getId()) % EVERY != 0 || !(golem.level() instanceof ServerLevel level)) {
			return;
		}
		switch (GolemSmith.role(golem)) {
			case HAULER -> HaulerGolems.work(level, golem);
			case FARMHAND -> FarmhandGolems.work(level, golem);
			case null, default -> {
			}
		}
	}

	/** Walks {@code golem} toward {@code pos}; true once within {@code reach} blocks of its middle. */
	public static boolean walkTo(IronGolem golem, BlockPos pos, double reach) {
		if (golem.position().distanceToSqr(pos.getCenter()) <= reach * reach) {
			golem.getNavigation().stop();
			arrived(golem);
			return true;
		}
		WALK_TARGETS.put(golem, pos);
		if (EXITS.containsKey(golem)) {
			headway(golem); // climbing out of water toward the bank on the way (B783); no path leads out
			return false;
		}
		if (golem.getNavigation().isDone() || !pos.equals(golem.getNavigation().getTargetPos())) {
			golem.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED);
		}
		headway(golem);
		return false;
	}

	/** Notes the golem's headway on a walk; {@link #STUCK_TICKS} without a block of it and it squeezes through. */
	private static void headway(IronGolem golem) {
		long now = golem.level().getGameTime();
		double[] last = HEADWAY.get(golem);
		if (last == null || Math.abs(golem.getX() - last[0]) + Math.abs(golem.getZ() - last[1]) >= 1.0) {
			HEADWAY.put(golem, new double[]{golem.getX(), golem.getZ(), now});
		} else if (now - (long) last[2] >= STUCK_TICKS) {
			SQUEEZING.add(golem);
		}
	}

	/** How long (ticks) {@code golem} has walked without getting a block farther; 0 when it isn't on a walk. */
	public static long stillFor(IronGolem golem) {
		double[] last = HEADWAY.get(golem);
		return last == null ? 0 : golem.level().getGameTime() - (long) last[2];
	}

	/** Ends {@code golem}'s walk where it stands, as if it had arrived: the next walk starts its headway afresh. */
	public static void giveUp(IronGolem golem) {
		golem.getNavigation().stop();
		arrived(golem);
	}

	private static void arrived(IronGolem golem) {
		HEADWAY.remove(golem);
		SQUEEZING.remove(golem);
		WALK_TARGETS.remove(golem);
		WADED.remove(golem);
		CLIMBED.remove(golem);
	}

	/**
	 * Takes {@code golem} out of water its path can't leave (B783): with no path, or a path that ends in the water, it
	 * picks a bank it can climb ({@link #exit}), swims to it and, pressed against it, rises until it stands on top. Ends
	 * once the golem stands on dry ground, or after {@link #CLIMB_TICKS} on one bank (it looks again).
	 */
	private static void climbOut(ServerLevel level, IronGolem golem) {
		double[] exit = EXITS.get(golem);
		if (exit != null && exit.length > 4) { // wading in
			if (golem.isInWater() || level.getGameTime() - (long) exit[3] > CLIMB_TICKS) {
				EXITS.remove(golem);
			} else {
				golem.getMoveControl().setWantedPosition(exit[0], exit[1], exit[2], SPEED);
			}
			return;
		}
		if (exit != null && (golem.onGround() && !golem.isInWater() || level.getGameTime() - (long) exit[3] > CLIMB_TICKS)) {
			EXITS.remove(golem);
			exit = null;
		}
		if (exit == null && !golem.isInWater()) {
			wadeIn(level, golem);
			return;
		}
		if (exit == null) {
			if (CLIMBED.getOrDefault(golem, 0) >= CLIMBS || !stuckInWater(level, golem)) {
				return;
			}
			Vec3 to = exit(level, golem, WALK_TARGETS.get(golem));
			if (to == null) {
				return;
			}
			exit = new double[]{to.x, to.y, to.z, level.getGameTime()};
			EXITS.put(golem, exit);
			CLIMBED.merge(golem, 1, Integer::sum);
			golem.getNavigation().stop();
		}
		golem.getMoveControl().setWantedPosition(exit[0], exit[1], exit[2], SPEED);
		double reach = golem.getBbWidth() / 2.0 + 0.75;
		boolean atBank = Math.abs(golem.getX() - exit[0]) <= reach && Math.abs(golem.getZ() - exit[2]) <= reach;
		if (golem.getY() < exit[1] && (golem.horizontalCollision || atBank)) {
			Vec3 v = golem.getDeltaMovement();
			golem.setDeltaMovement(v.x, Math.max(v.y, 0.3), v.z); // pulls itself up the bank, as vanilla's swimmers do
		}
	}

	/**
	 * A golem on dry ground whose path ends at the water, short of where it is going, after {@link #STUCK_TICKS} without
	 * headway, wades in toward it (once a walk): the water nearest the target within a few blocks, and nearer the target
	 * than the golem. Its paths can't climb out of water onto a bank above the surface, so they stop at the water's edge.
	 */
	private static void wadeIn(ServerLevel level, IronGolem golem) {
		BlockPos target = WALK_TARGETS.get(golem);
		if (target == null || !golem.onGround() || WADED.contains(golem) || stillFor(golem) < STUCK_TICKS) {
			return;
		}
		Path path = golem.getNavigation().getPath();
		if (path != null && !golem.getNavigation().isDone() || path != null && path.canReach()) {
			return;
		}
		Vec3 goal = target.getCenter();
		double from = golem.position().distanceTo(goal);
		BlockPos at = golem.blockPosition();
		BlockPos best = null;
		double bestDist = from - 1.0;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -3; dy <= 0; dy++) {
					BlockPos p = at.offset(dx, dy, dz);
					if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getFluidState(p.above()).isEmpty()) {
						continue; // only the top water block
					}
					if (GolemPaths.tooLowToLeave(at.getY(), p.getY())) {
						continue; // water it couldn't climb back out of here (B813)
					}
					double d = p.getCenter().distanceTo(goal);
					if (d < bestDist) {
						bestDist = d;
						best = p;
					}
				}
			}
		}
		if (best != null) {
			WADED.add(golem);
			golem.getNavigation().stop();
			EXITS.put(golem, new double[]{best.getX() + 0.5, best.getY(), best.getZ() + 0.5, level.getGameTime(), 1});
		}
	}

	/** Whether {@code golem}'s walk has no way out of the water it floats in: no path, or one that ends in water. */
	private static boolean stuckInWater(ServerLevel level, IronGolem golem) {
		if (!WALK_TARGETS.containsKey(golem)) {
			return false;
		}
		Path path = golem.getNavigation().getPath();
		if (path == null || golem.getNavigation().isDone()) {
			return true;
		}
		return !path.canReach() && level.getFluidState(path.getEndNode().asBlockPos()).is(FluidTags.WATER);
	}

	/**
	 * Where {@code golem} climbs out: a dry place to stand (a block's middle or edge), by the water's edge, no more than {@link #CLIMB} above
	 * its feet and with room for the golem, within {@link #EXIT_RANGE}; of those, the one on the shortest way to
	 * {@code toward} (the nearest when it isn't going anywhere). Null when there is none.
	 */
	@Nullable
	static Vec3 exit(ServerLevel level, IronGolem golem, @Nullable BlockPos toward) {
		BlockPos at = golem.blockPosition();
		Vec3 best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.MutableBlockPos edge = new BlockPos.MutableBlockPos();
		for (int dx = -EXIT_RANGE; dx <= EXIT_RANGE; dx++) {
			for (int dz = -EXIT_RANGE; dz <= EXIT_RANGE; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos p = at.offset(dx, dy, dz);
					double y = standingY(level, p);
					if (Double.isNaN(y) || y - golem.getY() <= 0.25 || y - golem.getY() > CLIMB) {
						continue;
					}
					boolean byWater = false;
					for (Direction d : Direction.Plane.HORIZONTAL) {
						for (int down = 1; down <= 2 && !byWater; down++) {
							byWater = level.getFluidState(edge.setWithOffset(p, d.getStepX(), -down, d.getStepZ())).is(FluidTags.WATER);
						}
					}
					if (!byWater) {
						continue;
					}
					for (double ox = -0.5; ox <= 0.5; ox += 0.5) {
						for (double oz = -0.5; oz <= 0.5; oz += 0.5) {
							// the middle of the block, or its edge: a golem is wider than a block and may stand overhanging the water
							Vec3 stand = new Vec3(p.getX() + 0.5 + ox, y, p.getZ() + 0.5 + oz);
							AABB room = golem.getDimensions(golem.getPose()).makeBoundingBox(stand.x, y + 0.01, stand.z);
							if (!level.noCollision(golem, room) || level.containsAnyLiquid(room)) {
								continue;
							}
							double score = golem.position().distanceTo(stand) + (toward == null ? 0 : stand.distanceTo(toward.getCenter()));
							if (score < bestScore) {
								bestScore = score;
								best = stand;
							}
						}
					}
				}
			}
		}
		return best;
	}

	/** The height one stands at in {@code pos} (the top of the solid block below it); NaN when that isn't dry ground. */
	private static double standingY(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) {
			return Double.NaN;
		}
		VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
		VoxelShape here = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.isEmpty() || !here.isEmpty() ? Double.NaN : below.getY() + shape.max(Direction.Axis.Y);
	}

	/** Whether {@code golem} is squeezing past mobs that hold it up on its way: nothing pushes it until it arrives. */
	public static boolean squeezing(IronGolem golem) {
		return SQUEEZING.contains(golem);
	}
}
