package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/** Battles with Pokémon trainers (see {@link Trainers}). Filled in by {@code compat/cobblemon}. */
public interface TrainerBattles {
	Extension<TrainerBattles> EXTENSION = new Extension<>("trainer battles");

	/**
	 * {@code player} challenges {@code trainer} to a battle. Returns whether the battle started (false when it was refused:
	 * either side already battling, no Pokémon able to fight, or the battle failed to start; the player is told why).
	 */
	boolean challenge(ServerPlayer player, Villager trainer);

	/** Why {@link #challenge} would refuse now (the player or the trainer already battling, no Pokémon able to fight), or null. */
	@Nullable
	default Component refusal(ServerPlayer player, Villager trainer) {
		return null;
	}

	/** The Trainer screen's Pokémon (ROADMAP 43.1): the trainer's team as {@code player} would meet it, the player's party, their level cap. */
	default TrainerView.Sides sides(ServerPlayer player, Villager trainer) {
		return TrainerView.Sides.NONE;
	}
}
