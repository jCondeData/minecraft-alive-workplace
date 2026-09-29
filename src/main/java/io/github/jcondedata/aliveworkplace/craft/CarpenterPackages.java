package io.github.jcondedata.aliveworkplace.craft;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;

/** The Carpenter's WORK activity (make what the builders nearby are waiting for) and the Chef's (cook). */
public final class CarpenterPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return work(new CrafterWork(Crafting.Kind.CRAFTING, true));
	}

	/** The Chef's: cook the menu all shift. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> chef(float speed) {
		return work(new ChefWork());
	}

	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(CrafterWork crafter) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, crafter),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private CarpenterPackages() {
	}
}
