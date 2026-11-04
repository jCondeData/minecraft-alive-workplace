package io.github.jcondedata.aliveworkplace.work;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Villagers climb ladders to a bed upstairs (B79). Vanilla's pathfinding has no ladders, so a villager whose bed is in
 * an attic reached by a ladder (the Stone House's) finds no path, and after a minute of that vanilla gives the bed up.
 * At bedtime such a villager walks to the foot of the ladder that reaches the bed's floor, climbs it and steps off
 * towards the bed; from there vanilla's own walking and sleeping take over. Runs after the brain each tick, and only
 * for a resting villager more than a jump below their bed.
 *
 * <p>The ladder taken is one that comes up in the bed's room (B304), and a free bed up a ladder can be claimed, which
 * vanilla's claim (it wants a path to the bed) never allowed ({@link #pathUpALadder}, from {@code AcquirePoiMixin}).
 * A villager already up on the floor of a bed it can't walk to there (the Terrace's other bedroom) goes down its own
 * ladder first, then across and up the right one (B925, {@link #down}); such a bed can be claimed too.
 */
public final class BedLadders {
	/** How far from the bed (each way, flat) a ladder up to its floor is looked for. */
	static final int REACH = 8;
	private static final double CLIMB = 0.2;
	private static final double STEP = 0.25;
	/** The tallest block a villager steps onto without a jump (its step height): a carpet, a slab, a bed. */
	private static final double STEP_UP = 0.6;
	/** How many paths to a ladder's foot one bed claim may work out ({@link #pathUpALadder}). */
	private static final int PATHS = 2;
	/** The ladder found for each villager's bed: [bed, top, bottom]. */
	private static final Map<Villager, BlockPos[]> LADDERS = new WeakHashMap<>();
	/** The ladder down from the floor a villager is on, for a bed on that floor it can't walk to: [bed, top, bottom] (B925). */
	private static final Map<Villager, BlockPos[]> DOWN = new WeakHashMap<>();

	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level) || villager.isSleeping() || villager.isPassenger()) {
			return;
		}
		Brain<Villager> brain = villager.getBrain();
		if (!brain.isActive(Activity.REST)) {
			LADDERS.remove(villager);
			DOWN.remove(villager);
			return;
		}
		GlobalPos home = brain.getMemory(MemoryModuleType.HOME).orElse(null);
		if (home == null || !home.dimension().equals(level.dimension())) {
			return;
		}
		BlockPos bed = home.pos();
		BlockPos feet = villager.blockPosition();
		if (feet.getY() > bed.getY() || Math.abs(feet.getX() - bed.getX()) > REACH + 2 || Math.abs(feet.getZ() - bed.getZ()) > REACH + 2) {
			return;
		}
		if (down(level, villager, bed, feet)) {
			return; // going down a ladder first: the bed is on this floor, but behind a wall (B925)
		}
		if (feet.getY() >= bed.getY() - 1 && !LADDERS.containsKey(villager)) {
			return; // on the bed's floor or a step below it: vanilla walks there
		}
		BlockPos[] ladder = ladder(level, villager, bed);
		if (ladder == null) {
			return;
		}
		BlockPos top = ladder[1];
		BlockPos bottom = ladder[2];
		boolean inColumn = feet.getX() == top.getX() && feet.getZ() == top.getZ() && feet.getY() >= bottom.getY() && feet.getY() <= top.getY() + 1;
		if (!inColumn) {
			if (feet.getY() >= bed.getY() - 1) {
				return; // a step below the bed: vanilla walks there
			}
			// to the foot of the ladder (vanilla can walk there); the bed isn't given up meanwhile
			if (!brain.getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getTarget().currentBlockPosition().equals(bottom)).orElse(false)) {
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bottom, 0.5f, 0));
			}
			brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
			return;
		}
		// on the ladder: up it, then off it towards the bed; vanilla's walking waits meanwhile
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		villager.getNavigation().stop();
		villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ(), 0);
		boolean ceiling = villager.verticalCollision && !villager.verticalCollisionBelow && villager.getY() > top.getY() + 0.5;
		if (villager.getY() < top.getY() + 1.0 && !ceiling) { // till the feet clear the floor (or the head meets the ceiling at the top)
			villager.setDeltaMovement((top.getX() + 0.5 - villager.getX()) * 0.2, CLIMB, (top.getZ() + 0.5 - villager.getZ()) * 0.2);
		} else {
			Vec3 to = new Vec3(bed.getX() + 0.5 - villager.getX(), 0, bed.getZ() + 0.5 - villager.getZ()).normalize().scale(STEP);
			villager.setDeltaMovement(to.x, 0.02, to.z);
			villager.getLookControl().setLookAt(bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5);
		}
		villager.fallDistance = 0;
	}

	/**
	 * A villager on the bed's floor who can't walk to it there (in the Terrace's other bedroom, the party wall between them:
	 * the owner's Aldous, B925) goes down a ladder from that floor first; then {@link #tick} takes them across downstairs
	 * and up the ladder in the bed's room. Whether this has the villager in hand (walking to that ladder or down it).
	 */
	private static boolean down(ServerLevel level, Villager villager, BlockPos bed, BlockPos feet) {
		Brain<Villager> brain = villager.getBrain();
		BlockPos[] plan = DOWN.get(villager);
		if (plan != null && (!plan[0].equals(bed) || !(level.getBlockState(plan[1]).getBlock() instanceof LadderBlock))) {
			DOWN.remove(villager);
			plan = null;
		}
		if (plan == null) {
			// only once vanilla's walk to the bed has fallen short, and looked for once a second
			if (Math.abs(feet.getY() - bed.getY()) > 1 || !brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
				|| villager.tickCount % 20 != 0) {
				return false;
			}
			BlockPos[] found = downAndAcross(level, feet, bed);
			if (found == null) {
				return false;
			}
			plan = new BlockPos[] {bed.immutable(), found[0], found[1]};
			DOWN.put(villager, plan);
		}
		BlockPos top = plan[1];
		BlockPos bottom = plan[2];
		if (feet.getY() <= bottom.getY() || feet.getY() > top.getY() + 1) {
			DOWN.remove(villager); // down (or somewhere else altogether): the way up to the bed takes over
			return false;
		}
		brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		double dx = top.getX() + 0.5 - villager.getX();
		double dz = top.getZ() + 0.5 - villager.getZ();
		boolean inColumn = feet.getX() == top.getX() && feet.getZ() == top.getZ();
		if (!inColumn && dx * dx + dz * dz > 1.7 * 1.7) {
			// to the ladder's hole (vanilla walks there); the bed isn't given up meanwhile
			if (!brain.getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getTarget().currentBlockPosition().equals(top.above())).orElse(false)) {
				brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(top.above(), 0.5f, 1));
			}
			return true;
		}
		// beside the hole or in it: over it and down the ladder (a climbable slows the fall); vanilla's walking waits meanwhile
		brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
		villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ(), 0);
		double fall = inColumn ? -CLIMB : villager.getDeltaMovement().y;
		villager.setDeltaMovement(dx * 0.2, fall, dz * 0.2);
		villager.fallDistance = 0;
		return true;
	}

	/**
	 * For a villager at {@code feet} on {@code bed}'s floor who can't walk to it there: a ladder down from that floor [top,
	 * bottom] that they can walk to, from whose foot they can walk to the foot of a ladder that comes up in the bed's room
	 * ({@link #reachesBed}); or null.
	 */
	@Nullable
	private static BlockPos[] downAndAcross(ServerLevel level, BlockPos feet, BlockPos bed) {
		if (reachesBed(level, feet, bed)) {
			return null; // vanilla walks there
		}
		List<BlockPos> ups = new ArrayList<>();
		for (BlockPos top : tops(level, bed)) {
			if (reachesBed(level, top, bed)) {
				ups.add(bottom(level, top, level.getMinBuildHeight()));
			}
		}
		if (ups.isEmpty()) {
			return null;
		}
		BlockPos[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos top : tops(level, feet)) {
			BlockPos bottom = bottom(level, top, level.getMinBuildHeight());
			double d = top.distSqr(feet);
			if (bottom.getY() >= top.getY() - 1 || d >= bestDistance || !reachesBed(level, top, feet)) {
				continue; // no floor below to go down to, a farther one, or not one they can walk to up here
			}
			for (BlockPos up : ups) {
				if (!up.equals(bottom) && walks(level, bottom, up)) {
					best = new BlockPos[] {top, bottom};
					bestDistance = d;
					break;
				}
			}
		}
		return best;
	}

	/**
	 * Whether a villager can walk from {@code from} to beside {@code to} (the feet of two ladders): over blocks it can stand
	 * on, a step up or down at a time, through doors it opens, within {@link #REACH} + 2 of them both.
	 */
	static boolean walks(ServerLevel level, BlockPos from, BlockPos to) {
		int margin = REACH + 2;
		int minX = Math.min(from.getX(), to.getX()) - margin;
		int maxX = Math.max(from.getX(), to.getX()) + margin;
		int minZ = Math.min(from.getZ(), to.getZ()) - margin;
		int maxZ = Math.max(from.getZ(), to.getZ()) + margin;
		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		seen.add(from.asLong());
		open.add(from);
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			if (Math.abs(at.getX() - to.getX()) + Math.abs(at.getZ() - to.getZ()) <= 1 && Math.abs(at.getY() - to.getY()) <= 1) {
				return true;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = at.relative(d);
				if (side.getX() < minX || side.getX() > maxX || side.getZ() < minZ || side.getZ() > maxZ) {
					continue;
				}
				for (int dy : new int[] {0, 1, -1}) {
					BlockPos next = side.above(dy);
					if (dy > 0 && !standable(level, at.above())) {
						continue; // no room overhead to step up
					}
					if (dy < 0 && !standable(level, side)) {
						break; // a wall in the way down
					}
					if (standable(level, next) && onFloor(level, next)) {
						if (seen.add(next.asLong())) {
							open.add(next);
						}
						break;
					}
				}
			}
		}
		return false;
	}

	/** Whether there is something to stand on at {@code at}: a block below, or a low one (a slab, a carpet) in it. */
	private static boolean onFloor(ServerLevel level, BlockPos at) {
		return !level.getBlockState(at).getCollisionShape(level, at).isEmpty()
			|| !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
	}

	/**
	 * The ladder up to the bed's floor for the villager: [bed, top, bottom], or null when there's none. Of the ladders whose
	 * foot is on the villager's floor, one that comes up in the bed's room ({@link #reachesBed}) is taken before one that
	 * doesn't, then the nearest. In the Terrace (two homes side by side, each with its own ladder up) the other home's
	 * ladder was often the nearer, and up there the bed was behind the party wall (B304).
	 */
	@Nullable
	private static BlockPos[] ladder(ServerLevel level, Villager villager, BlockPos bed) {
		BlockPos[] known = LADDERS.get(villager);
		if (known != null && known[0].equals(bed) && level.getBlockState(known[1]).getBlock() instanceof LadderBlock) {
			return known;
		}
		if (villager.tickCount % 20 != 0) {
			return null; // looking costs a few hundred block reads: once a second will do
		}
		BlockPos feet = villager.blockPosition();
		BlockPos[] best = null;
		boolean bestReaches = false;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos top : tops(level, bed)) {
			BlockPos bottom = bottom(level, top, feet.getY() - 2);
			if (bottom.getY() > feet.getY() + 1 || bottom.getY() < feet.getY() - 1) {
				continue; // its foot isn't on the villager's floor
			}
			boolean reaches = reachesBed(level, top, bed);
			double d = bottom.distSqr(feet);
			if (reaches && !bestReaches || reaches == bestReaches && d < bestDistance) {
				best = new BlockPos[] {bed.immutable(), top, bottom};
				bestReaches = reaches;
				bestDistance = d;
			}
		}
		if (best == null) {
			LADDERS.remove(villager);
			return null;
		}
		LADDERS.put(villager, best);
		return best;
	}

	/** The ladders within {@link #REACH} of {@code bed} whose top is on the bed's floor (one below the bed), nearest the bed first. */
	private static List<BlockPos> tops(ServerLevel level, BlockPos bed) {
		List<BlockPos> out = new ArrayList<>();
		int y = bed.getY() - 1;
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				BlockPos top = new BlockPos(bed.getX() + dx, y, bed.getZ() + dz);
				if (level.getBlockState(top).getBlock() instanceof LadderBlock) {
					out.add(top);
				}
			}
		}
		return out;
	}

	/** The lowest rung of the ladder whose top is {@code top}, going no lower than {@code lowest}. */
	private static BlockPos bottom(ServerLevel level, BlockPos top, int lowest) {
		BlockPos bottom = top;
		while (bottom.getY() > lowest && level.getBlockState(bottom.below()).getBlock() instanceof LadderBlock) {
			bottom = bottom.below();
		}
		return bottom;
	}

	/**
	 * Whether a villager stepping off the ladder whose top is {@code top} can walk to {@code bed} on the bed's floor: a walk
	 * over blocks it can stand in (nothing taller than a step underfoot, nothing at head height; doors a villager opens, B876) to the bed or a
	 * block beside it, within {@link #REACH} of the bed. A wall between them (the Terrace's party wall) says no.
	 */
	static boolean reachesBed(ServerLevel level, BlockPos top, BlockPos bed) {
		int y = bed.getY();
		BlockPos start = new BlockPos(top.getX(), y, top.getZ());
		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		seen.add(start.asLong());
		open.add(start);
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			if (Math.abs(at.getX() - bed.getX()) + Math.abs(at.getZ() - bed.getZ()) <= 1) {
				return true;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(d);
				if (Math.abs(next.getX() - bed.getX()) > REACH + 1 || Math.abs(next.getZ() - bed.getZ()) > REACH + 1 || !seen.add(next.asLong())) {
					continue;
				}
				if (next.equals(bed) || standable(level, next)) {
					open.add(next);
				}
			}
		}
		return false;
	}

	/** Whether a villager can stand at {@code at}: no more than a step (a carpet, a slab, a bed) underfoot, room for the head. */
	private static boolean standable(ServerLevel level, BlockPos at) {
		BlockState feet = level.getBlockState(at);
		if (!passableDoor(feet)) {
			VoxelShape shape = feet.getCollisionShape(level, at);
			if (!shape.isEmpty() && shape.max(Direction.Axis.Y) > STEP_UP) {
				return false;
			}
		}
		BlockState head = level.getBlockState(at.above());
		return passableDoor(head) || head.getCollisionShape(level, at.above()).isEmpty();
	}

	/**
	 * A door a villager gets through, as vanilla's pathing sees it: an open one, or a shut wooden one (villagers open those).
	 * A shut iron door is a wall to them (B876).
	 */
	private static boolean passableDoor(BlockState state) {
		return state.getBlock() instanceof DoorBlock door && (door.isOpen(state) || DoorBlock.isWoodenDoor(state));
	}

	/**
	 * For vanilla's bed claim (AcquirePoi, B304): when its path to the beds it found falls short, a path "to" the nearest
	 * of them that is free and that {@code villager} can reach up a ladder (it can walk to the ladder's foot, and the ladder
	 * comes up in the bed's room), or null. Vanilla only reads where a path ends and whether it gets there, so the path is
	 * the bed alone; at bedtime {@link #tick} takes the villager up the ladder.
	 */
	@Nullable
	public static Path pathUpALadder(Villager villager, Set<Pair<Holder<PoiType>, BlockPos>> pois) {
		if (!(villager.level() instanceof ServerLevel level) || pois.isEmpty()) {
			return null;
		}
		BlockPos feet = villager.blockPosition();
		PoiManager poi = level.getPoiManager();
		List<BlockPos> beds = pois.stream().filter(p -> p.getFirst().is(PoiTypes.HOME)).map(Pair::getSecond)
			.filter(b -> b.getY() >= feet.getY() - 1 && poi.getFreeTickets(b) > 0)
			.sorted(Comparator.comparingDouble(b -> b.distSqr(feet))).toList();
		int walks = PATHS; // a path costs more than the block reads: a few per look (vanilla looks again a second later)
		for (BlockPos bed : beds) {
			if (walks <= 0) {
				break;
			}
			if (bed.getY() <= feet.getY() + 1) {
				// on the villager's floor but not one vanilla walks to: down a ladder here, across and up one there (B925)
				walks--;
				if (Math.abs(bed.getX() - feet.getX()) <= REACH + 2 && Math.abs(bed.getZ() - feet.getZ()) <= REACH + 2
					&& downAndAcross(level, feet, bed) != null) {
					return new Path(List.of(new Node(bed.getX(), bed.getY(), bed.getZ())), bed.immutable(), true);
				}
				continue;
			}
			List<BlockPos> bottoms = new ArrayList<>();
			for (BlockPos top : tops(level, bed)) {
				if (reachesBed(level, top, bed)) {
					bottoms.add(bottom(level, top, level.getMinBuildHeight()));
				}
			}
			bottoms.sort(Comparator.comparingDouble(b -> b.distSqr(feet)));
			for (BlockPos bottom : bottoms.subList(0, Math.min(walks, bottoms.size()))) {
				walks--;
				Path walk = villager.getNavigation().createPath(bottom, 1);
				if (walk != null && walk.canReach()) {
					return new Path(List.of(new Node(bed.getX(), bed.getY(), bed.getZ())), bed.immutable(), true);
				}
			}
		}
		return null;
	}

	private BedLadders() {
	}
}
