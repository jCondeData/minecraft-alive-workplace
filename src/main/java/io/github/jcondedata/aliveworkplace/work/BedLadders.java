package io.github.jcondedata.aliveworkplace.work;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Villagers climb ladders to a bed upstairs (B79). Vanilla's pathfinding has no ladders, so a villager whose bed is in
 * an attic reached by a ladder (the Stone House's) finds no path, and after a minute of that vanilla gives the bed up.
 * At bedtime such a villager walks to the foot of the ladder that reaches the bed's floor, climbs it and steps off
 * towards the bed; from there vanilla's own walking and sleeping take over. Runs after the brain each tick, and only
 * for a resting villager more than a jump below their bed.
 */
public final class BedLadders {
	/** How far from the bed (each way, flat) a ladder up to its floor is looked for. */
	static final int REACH = 8;
	private static final double CLIMB = 0.2;
	private static final double STEP = 0.25;
	/** The ladder found for each villager's bed: [bed, top, bottom]. */
	private static final Map<Villager, BlockPos[]> LADDERS = new WeakHashMap<>();

	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level) || villager.isSleeping() || villager.isPassenger()) {
			return;
		}
		Brain<Villager> brain = villager.getBrain();
		if (!brain.isActive(Activity.REST)) {
			LADDERS.remove(villager);
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

	/** The ladder up to the bed's floor nearest the villager: [bed, top, bottom], or null when there's none. */
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
		BlockPos best = null;
		BlockPos bestBottom = null;
		double bestDistance = Double.MAX_VALUE;
		int y = bed.getY() - 1;
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				BlockPos top = new BlockPos(bed.getX() + dx, y, bed.getZ() + dz);
				if (!(level.getBlockState(top).getBlock() instanceof LadderBlock)) {
					continue;
				}
				BlockPos bottom = top;
				while (bottom.getY() > feet.getY() - 2 && level.getBlockState(bottom.below()).getBlock() instanceof LadderBlock) {
					bottom = bottom.below();
				}
				if (bottom.getY() > feet.getY() + 1 || bottom.getY() < feet.getY() - 1) {
					continue; // its foot isn't on the villager's floor
				}
				double d = bottom.distSqr(feet);
				if (d < bestDistance) {
					bestDistance = d;
					best = top;
					bestBottom = bottom;
				}
			}
		}
		if (best == null) {
			LADDERS.remove(villager);
			return null;
		}
		BlockPos[] found = {bed.immutable(), best, bestBottom};
		LADDERS.put(villager, found);
		return found;
	}

	private BedLadders() {
	}
}
