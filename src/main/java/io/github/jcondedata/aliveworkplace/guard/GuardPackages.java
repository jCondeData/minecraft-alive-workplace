package io.github.jcondedata.aliveworkplace.guard;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;

/** The Guard's WORK activity (patrol); fighting is added to their CORE activity so it happens any time. */
public final class GuardPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new GuardPatrol()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	/** Vanilla's CORE package plus {@link GuardCombat} first. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> core(
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
			.add(Pair.of(0, new GuardCombat()))
			.addAll(vanilla)
			.build();
	}

	private GuardPackages() {
	}
}
