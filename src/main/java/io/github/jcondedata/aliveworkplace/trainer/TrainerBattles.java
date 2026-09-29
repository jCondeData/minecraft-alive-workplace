package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Battles with Pokémon trainers (see {@link Trainers}). Filled in by {@code compat/cobblemon}. */
public interface TrainerBattles {
	Extension<TrainerBattles> EXTENSION = new Extension<>("trainer battles");

	/** {@code player} challenges {@code trainer} to a battle. */
	void challenge(ServerPlayer player, Villager trainer);
}
