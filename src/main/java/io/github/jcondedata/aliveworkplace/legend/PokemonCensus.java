package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Pokémon a village keeps, as the Pokémon Professor (ROADMAP 29.21) sees them: the census of its Pasture Blocks (the
 * condition {@code pastured_pokemon} and the village Pokédex) and the Professor's hints screen. Filled in by
 * {@code compat/cobblemon} when Cobblemon is installed; without it the census is empty and there are no hints (the
 * Professor's file needs Cobblemon, so without it nobody comes to ask for them).
 */
public interface PokemonCensus {
	Extension<PokemonCensus> EXTENSION = new Extension<>("the Pokémon census");

	/** What the pastures hold: how many Pokémon, their types (lower-case names) and species (ids such as {@code cobblemon:pikachu}). */
	record Count(int pokemon, Set<String> types, Set<String> species) {
		public static final Count EMPTY = new Count(0, Set.of(), Set.of());
	}

	/** The Pokémon kept in Pasture Blocks in the village round {@code hall}. */
	Count census(ServerLevel level, BlockPos hall);

	/** A species' name, as Cobblemon writes it ({@code cobblemon:pikachu}: "Pikachu"). */
	Component speciesName(String species);

	/** Opens the Professor's hints: {@code player}'s party, each Pokémon's IVs, nature, hidden ability and EVs in words. */
	void openHints(ServerPlayer player, Villager professor);

	/** The census, or {@link Count#EMPTY} without Cobblemon. */
	static Count of(ServerLevel level, BlockPos hall) {
		return EXTENSION.call(c -> c.census(level, hall), Count.EMPTY);
	}

	/** A species' name, or its id without Cobblemon. */
	static Component name(String species) {
		return EXTENSION.call(c -> c.speciesName(species), Component.literal(species));
	}
}
