package io.github.jcondedata.aliveworkplace.research;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;

/** The Scholar's WORK activity: the village's research at the desk. */
public final class ScholarPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new ScholarWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private ScholarPackages() {
	}
}
