package io.github.jcondedata.aliveworkplace.work;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Two villagers coming home through one door at once don't jam in it (B255). Both walk for the doorway; the one behind
 * pushes the one in front sideways against the open door's leaf, so neither gets through, sometimes for a minute. Vanilla
 * counts all that time as "can't reach the bed" and after a minute gives the bed up, so one of them slept nowhere that
 * night (the Stone House's doorstep showed it in about one homecoming in twenty). Now a villager that has stood
 * still for a second by a door, pressed by another villager who is walking too, is jammed: the jam isn't counted as the
 * bed being out of reach, and the one further from the door steps back for a moment, so the one in front walks in and
 * the other follows. Runs after each villager's brain; nothing is saved (a reload just starts the count again).
 *
 * <p>The same at any time of day (B1512): four smiths hired at a finished Smithy II jammed two abreast on its doorstep
 * on their way to their job blocks, for as long as vanilla kept them walking there (over a minute).
 *
 * <p>And a walker held up away from a door, or by a villager who isn't walking, shoulders past (B1512). In the Smithy II
 * the Weaponsmith at his grindstone stands in the mouth of the way to the back room, wedged between the grindstone and
 * the wall; the Armorer on his way to the back blast furnace came round the bench's corner beside him, and vanilla's
 * push between two bodies that touch held him against that corner for as long as the Weaponsmith stood there (700 ticks
 * and more in the traces). Nobody can step back out of that, so the walker keeps his course for a moment against the
 * push: he slides along the corner and is through.
 */
public final class DoorJams {
	/** Ticks standing still (by a door, pressed by another walker) before it's a jam. */
	static final int STILL = 20;
	/** Ticks the one further from the door steps back. */
	static final int GIVE_WAY = 15;
	/** Ticks after giving way before the same villager would give way again (the one in front has gone in meanwhile). */
	static final int AGAIN = 40;
	/** How close (flat, centre to centre) another villager presses. */
	private static final double PRESS = 0.9;
	/** Moved less than this far, it's standing still. */
	private static final double MOVED = 0.25;
	private static final double BACK = 0.12;
	/** Blocks a tick a walker shoulders on along his path, against the push of the villager in his way. */
	private static final double SHOULDER = 0.12;

	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	private static final class State {
		Vec3 anchor;
		int still;
		long givingWayUntil;
		long shoulderingUntil;
		long calmUntil;
		@Nullable
		BlockPos door;

		State(Vec3 anchor) {
			this.anchor = anchor;
		}
	}

	private DoorJams() {
	}

	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (!villager.isAlive() || villager.isSleeping() || villager.isPassenger()) {
			STATES.remove(villager);
			return;
		}
		long now = level.getGameTime();
		State state = STATES.computeIfAbsent(villager, v -> new State(v.position()));
		if (now < state.givingWayUntil && state.door != null) {
			stepBack(villager, state.door);
			return;
		}
		if (!villager.getNavigation().isInProgress()) {
			state.anchor = villager.position();
			state.still = 0;
			return;
		}
		if (now < state.shoulderingUntil) {
			shoulder(villager);
			return;
		}
		if (villager.position().distanceToSqr(state.anchor) > MOVED * MOVED) {
			state.anchor = villager.position();
			state.still = 0;
			return;
		}
		if (++state.still < STILL) {
			return;
		}
		BlockPos door = doorBy(level, villager.blockPosition());
		Villager other = presser(level, villager);
		if (other == null) {
			return;
		}
		if (door == null || !other.getNavigation().isInProgress()) {
			// no door to step back from, or the other isn't going anywhere: keep walking, against the push
			if (now >= state.calmUntil) {
				state.shoulderingUntil = now + GIVE_WAY;
				state.calmUntil = now + GIVE_WAY + AGAIN;
				state.still = 0;
				shoulder(villager);
			}
			return;
		}
		// a jam, not a bed out of reach: neither villager's "can't reach" clock runs on it
		villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		other.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		if (now >= state.calmUntil && givesWay(villager, other, door)) {
			state.door = door;
			state.givingWayUntil = now + GIVE_WAY;
			state.calmUntil = now + GIVE_WAY + AGAIN;
			state.still = 0;
			stepBack(villager, door);
		}
	}

	/** Whether {@code villager}, not {@code other}, gives way: the one further from the door does (the same pick both ways). */
	static boolean givesWay(Villager villager, Villager other, BlockPos door) {
		Vec3 centre = Vec3.atBottomCenterOf(door);
		double mine = flat(villager.position(), centre);
		double theirs = flat(other.position(), centre);
		if (Math.abs(mine - theirs) > 1.0E-4) {
			return mine > theirs;
		}
		return villager.getUUID().compareTo(other.getUUID()) > 0;
	}

	private static double flat(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private static void stepBack(Villager villager, BlockPos door) {
		villager.getNavigation().stop();
		villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ(), 0);
		Vec3 away = new Vec3(villager.getX() - (door.getX() + 0.5), 0, villager.getZ() - (door.getZ() + 0.5));
		if (away.lengthSqr() < 1.0E-4) {
			return;
		}
		away = away.normalize().scale(BACK);
		villager.setDeltaMovement(away.x, villager.getDeltaMovement().y, away.z);
	}

	/**
	 * A step on towards the next point of the villager's path. Set after the brain, it takes the place of the push the
	 * touching villager gave it last tick; blocks still stop it, so it slides along a corner and never goes into a wall.
	 */
	private static void shoulder(Villager villager) {
		Path path = villager.getNavigation().getPath();
		if (path == null || path.isDone()) {
			return;
		}
		Vec3 next = path.getNextEntityPos(villager);
		Vec3 on = new Vec3(next.x - villager.getX(), 0, next.z - villager.getZ());
		if (on.lengthSqr() < 1.0E-4) {
			return;
		}
		on = on.normalize().scale(SHOULDER);
		villager.setDeltaMovement(on.x, villager.getDeltaMovement().y, on.z);
	}

	/** The lower half of a door at the villager's feet or head, or beside them; null when there's none. */
	@Nullable
	static BlockPos doorBy(ServerLevel level, BlockPos feet) {
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof DoorBlock) {
				return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? p.below().immutable() : p.immutable();
			}
		}
		return null;
	}

	/** Another villager pressed up against this one, one walking too before one standing; null when there's none. */
	@Nullable
	private static Villager presser(ServerLevel level, Villager villager) {
		Villager standing = null;
		for (Villager other : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(PRESS, 0.5, PRESS),
				o -> o != villager && o.isAlive() && !o.isSleeping() && !o.isPassenger())) {
			if (flat(other.position(), villager.position()) <= PRESS * PRESS) {
				if (other.getNavigation().isInProgress()) {
					return other;
				}
				standing = other;
			}
		}
		return standing;
	}
}
