package io.github.jcondedata.aliveworkplace.work;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * WORK for jobs where players come to the villager (Move Tutors, Pokémon Traders): keep office hours by
 * the workstation, look at players who come close, and show a status line overhead.
 */
public final class DeskPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed,
			Function<Villager, Component> title, Function<Villager, Component> status) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, BehaviorBuilder.<Villager>triggerIf(v -> {
				WorkerStatus.set(v, title.apply(v), -1f, status.apply(v).copy().withStyle(ChatFormatting.GRAY));
				return false;
			})),
			Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, speed, 2, 100, 1200)),
			Pair.of(5, StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4f, 3)),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 6)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private DeskPackages() {
	}
}
