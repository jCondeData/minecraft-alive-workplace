package io.github.jcondedata.aliveworkplace.build;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WalkToJobSite;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Builder's WORK activity. Replaces the vanilla work package for our profession (see
 * VillagerGoalPackagesMixin): with a job they build; without one they hang around their bench.
 */
public final class BuilderPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new BuilderWork()),
			Pair.of(1, new PathWork()),
			Pair.of(1, new Upkeep.Look()),
			// Not vanilla's SetWalkTargetFromBlockMemory: it gives the bench up after a minute stuck, and the builder then
			// takes the nearest free one, often another builder's (B28).
			Pair.of(2, BehaviorBuilder.<Villager>triggerIf(BuilderPackages::idle, WalkToJobSite.create(speed, 9))),
			Pair.of(5, BehaviorBuilder.<Villager>triggerIf(BuilderPackages::idle,
				StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4f, 4))),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private static boolean idle(Villager villager) {
		return !ModAttachments.BUILDER_JOB.has(villager) && !PathWork.hasPath(villager);
	}

	private BuilderPackages() {
	}
}
