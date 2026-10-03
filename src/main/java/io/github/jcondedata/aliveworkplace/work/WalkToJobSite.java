package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/**
 * Walks a worker back to their job site when they have nothing else to do, without ever letting go of it (bug B28).
 * Vanilla's SetWalkTargetFromBlockMemory gives the job site up when the villager is over 100 blocks from it or hasn't
 * reached a walk target for a minute: the record's place is freed and the memory erased, and the villager then takes
 * the nearest free block of their kind, which for two builders whose benches stand near each other is often the
 * other's bench (they swapped benches mid-build in the 23.1 soak). Our workers go far and get stuck on purpose-built
 * terrain far more than vanilla's, and their block is theirs until it's broken or they're given another job.
 */
public final class WalkToJobSite {
	/** How often to try again while walks keep failing, so an unreachable job site isn't searched for every tick. */
	static final long RETRY = 100;

	public static OneShot<Villager> create(float speed, int closeEnough) {
		return BehaviorBuilder.create(i -> i.group(i.absent(MemoryModuleType.WALK_TARGET), i.present(MemoryModuleType.JOB_SITE),
				i.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE))
			.apply(i, (walk, site, cantReach) -> (level, villager, time) -> {
				GlobalPos at = i.get(site);
				if (at.dimension() != level.dimension() || at.pos().distManhattan(villager.blockPosition()) <= closeEnough) {
					return false;
				}
				if (i.tryGet(cantReach).isPresent() && (time + villager.getId()) % RETRY != 0) {
					return false; // the last walk somewhere failed: try again now and then, not every tick
				}
				walk.set(new WalkTarget(at.pos(), speed, closeEnough));
				return true;
			}));
	}

	private WalkToJobSite() {
	}
}
