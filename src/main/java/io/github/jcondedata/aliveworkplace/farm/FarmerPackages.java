package io.github.jcondedata.aliveworkplace.farm;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.work.Gated;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Farmer's WORK activity: {@link FieldWork} first, then vanilla's own farmer routine (harvesting
 * near the composter, composting, bone meal, strolling), which only runs while {@link Fields#vanillaMayRun}.
 */
public final class FarmerPackages {
	/** Behaviours at this priority or later (vanilla: the schedule update) always run. */
	private static final int ALWAYS = 99;

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
		ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
		out.add(Pair.of(0, new FieldWork()));
		for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : vanilla) {
			if (entry.getFirst() >= ALWAYS) {
				out.add(entry);
			} else {
				out.add(Pair.of(entry.getFirst(), new Gated<Villager>(Fields::vanillaMayRun, entry.getSecond())));
			}
		}
		return out.build();
	}

	private FarmerPackages() {
	}
}
