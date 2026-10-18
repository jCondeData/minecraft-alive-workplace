package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * No villager is left standing inside a block (B185). B92 taught builders to step out of a block that changed shape
 * around them, but any other villager stayed there for good, as the city time-lapse showed in one corner of a growing
 * town:
 * <ul>
 * <li>a baby (half a villager wide and tall) plays in a nook a grown villager doesn't fit: under a roof's stair being
 * laid, or beside a lantern on a fence post. When it grows up (24000 ticks old) vanilla looks for room for the bigger
 * body only right around it, finds none, and leaves the grown villager inside the stair or the lantern;</li>
 * <li>a villager standing still in a doorway has the door shut on it by a neighbour walking through: vanilla's
 * villagers close a door behind them unless another villager is <em>walking</em> through it, so one standing there
 * isn't counted, and a body inside a shut door can't move out of it.</li>
 * </ul>
 * Runs after each villager's brain, twice a second: a villager found inside a block (as {@link BuilderWork#insideBlock}
 * sees it: a wall it leans on doesn't count) at two looks in a row opens a wooden door it is shut in, as a player would,
 * or else is moved to the nearest spot it can stand on, with a puff like a builder's hop. Nothing is saved: a villager
 * loaded inside a block is simply found there again.
 */
public final class StuckVillagers {
	/** Ticks between looks. */
	static final int EVERY = 10;
	/** Looks in a row inside a block before the villager is let out (a body only brushing past one is never moved). */
	static final int LOOKS = 2;
	/** How far (each way, flat) a spot to stand is looked for. */
	private static final int REACH = 4;
	private static final Map<Villager, Integer> INSIDE = new WeakHashMap<>();

	private StuckVillagers() {
	}

	public static void tick(Villager villager) {
		if ((villager.tickCount + villager.getId()) % EVERY != 0 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (!villager.isAlive() || villager.isSleeping() || villager.isPassenger() || villager.noPhysics) {
			INSIDE.remove(villager);
			return;
		}
		BlockPos inside = BuilderWork.insideBlock(level, villager);
		if (inside == null) {
			INSIDE.remove(villager);
			return;
		}
		int looks = INSIDE.merge(villager, 1, Integer::sum);
		if (looks < LOOKS) {
			return;
		}
		INSIDE.remove(villager);
		letOut(level, villager, inside);
	}

	/** Opens the wooden door the villager is shut in, or moves it to the nearest spot with room to stand. */
	static boolean letOut(ServerLevel level, Villager villager, BlockPos inside) {
		BlockState state = level.getBlockState(inside);
		if (state.getBlock() instanceof DoorBlock door && DoorBlock.isWoodenDoor(state) && !door.isOpen(state)) {
			door.setOpen(villager, level, state, inside, true);
			if (BuilderWork.insideBlock(level, villager) == null) {
				return true; // the door swung away from it; it walks on as from any open door
			}
		}
		BlockPos spot = standingSpot(level, villager);
		if (spot == null) {
			return false; // walled in on every side: looked at again in a moment
		}
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
		return true;
	}

	/**
	 * The nearest spot (flat distance first, then the same height before a step up or down) with a floor, nothing harmful
	 * and room for this villager's whole body, or null.
	 */
	@Nullable
	static BlockPos standingSpot(ServerLevel level, Villager villager) {
		BlockPos from = villager.blockPosition();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy : new int[] {0, 1, -1, 2, -2}) {
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					double score = dx * dx + dz * dz + Math.abs(dy) * 0.5;
					if (score >= bestScore) {
						continue;
					}
					p.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
					if (WalkNodeEvaluator.getPathTypeStatic(villager, p) != PathType.WALKABLE) {
						continue;
					}
					AABB body = villager.getDimensions(villager.getPose()).makeBoundingBox(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
					if (level.noCollision(villager, body) && !level.containsAnyLiquid(body)) {
						best = p.immutable();
						bestScore = score;
					}
				}
			}
		}
		return best;
	}
}
