package io.github.jcondedata.aliveworkplace.trader;

import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** A Pokémon Trader's offers (see {@link PokemonTraders}). Filled in by {@code compat/cobblemon}. */
public interface PokemonTrades {
	Extension<PokemonTrades> EXTENSION = new Extension<>("Pokémon trades");

	/** Opens {@code trader}'s offers for {@code player}. */
	void open(ServerPlayer player, Villager trader);
}
