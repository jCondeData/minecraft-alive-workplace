package io.github.jcondedata.aliveworkplace.work;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import java.util.function.Predicate;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;

/**
 * A vanilla profession we give extra work to (Farmer, Fisherman): our behaviour runs first, and vanilla's
 * own routine only while {@code vanillaMayRun} (the villager has no job from us, or nothing to do right now).
 */
public final class UpgradedJob {
	/** Behaviours at this priority or later (vanilla: the schedule update) always run. */
	private static final int ALWAYS = 99;

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla,
			BehaviorControl<? super Villager> ours, Predicate<Villager> vanillaMayRun) {
		ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
		out.add(Pair.of(0, ours));
		for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : vanilla) {
			if (entry.getFirst() >= ALWAYS) {
				out.add(entry);
			} else {
				out.add(Pair.of(entry.getFirst(), new Gated<Villager>(vanillaMayRun, entry.getSecond())));
			}
		}
		return out.build();
	}

	private UpgradedJob() {
	}
}
