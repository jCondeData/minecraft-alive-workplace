package io.github.jcondedata.aliveworkplace.nurse;

import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.server.level.ServerPlayer;

/** A nurse healing a player's Pokémon (see {@link Nurses}). Filled in by {@code compat/cobblemon}. */
public interface PokemonHealing {
	Extension<PokemonHealing> EXTENSION = new Extension<>("Pokémon healing");

	/** Whether {@code player} is in a battle (no healing then). */
	boolean inBattle(ServerPlayer player);

	/** Heals {@code player}'s party; how many Pokémon needed it. */
	int healParty(ServerPlayer player);
}
