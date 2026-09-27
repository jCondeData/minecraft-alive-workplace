package io.github.jcondedata.aliveworkplace.farm;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.work.UpgradedJob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Farmer's WORK activity: {@link FieldWork} first, then vanilla's own farmer routine (harvesting
 * near the composter, composting, bone meal, strolling), which only runs while {@link Fields#vanillaMayRun}.
 */
public final class FarmerPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
		return UpgradedJob.work(vanilla, new FieldWork(), Fields::vanillaMayRun);
	}

	private FarmerPackages() {
	}
}
