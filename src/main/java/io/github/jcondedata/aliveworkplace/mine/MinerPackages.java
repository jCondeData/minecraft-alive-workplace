package io.github.jcondedata.aliveworkplace.mine;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/** The Miner's WORK activity: with a quarry they dig; without one they hang around their bench. */
public final class MinerPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new MinerWork()),
			Pair.of(2, BehaviorBuilder.<Villager>triggerIf(MinerPackages::idle,
				SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, speed, 9, 100, 1200))),
			Pair.of(5, BehaviorBuilder.<Villager>triggerIf(MinerPackages::idle,
				StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4f, 4))),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private static boolean idle(Villager villager) {
		return !villager.hasAttached(ModAttachments.MINER_JOB);
	}

	private MinerPackages() {
	}
}
