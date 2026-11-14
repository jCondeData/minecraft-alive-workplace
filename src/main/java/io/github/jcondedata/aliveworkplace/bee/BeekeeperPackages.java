package io.github.jcondedata.aliveworkplace.bee;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Beekeeper's WORK activity: look after the beehives near the Apiary all shift, and make her list for builders at
 * the Apiary in between (46.5: candles, colored candles, honeycomb blocks; the two take turns).
 */
public final class BeekeeperPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new io.github.jcondedata.aliveworkplace.craft.BeekeeperCraftWork()),
			Pair.of(0, new BeekeeperWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private BeekeeperPackages() {
	}
}
