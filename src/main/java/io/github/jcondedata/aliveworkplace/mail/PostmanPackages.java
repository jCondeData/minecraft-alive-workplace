package io.github.jcondedata.aliveworkplace.mail;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.work.WalkToJobSite;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/** The Postman's WORK activity: with mail to see to they do their round; otherwise they wait at the desk. */
public final class PostmanPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new PostmanWork()),
			Pair.of(2, BehaviorBuilder.<Villager>triggerIf(PostmanPackages::idle,
				WalkToJobSite.create(speed, 9))),
			Pair.of(5, BehaviorBuilder.<Villager>triggerIf(PostmanPackages::idle,
				StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4f, 4))),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private static boolean idle(Villager villager) {
		return !PostmanWork.isBusy(villager);
	}

	private PostmanPackages() {
	}
}
