package io.github.jcondedata.aliveworkplace.devclient;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * Cobblemon-typed scene helpers (B59). The screenshot client also runs without Cobblemon, and the JVM verifier loads a
 * class's local and argument types when it loads that class, so a {@code PokemonEntity} local in {@link JobScenes} made
 * every scene crash with NoClassDefFoundError. Code that holds a Cobblemon type lives here instead: this class loads
 * only when a Cobblemon scene calls it.
 */
final class WildPokemon {
	private WildPokemon() {
	}

	/** Spawns a still, wild Pokémon from a spec such as {@code "eevee shiny=yes level=12"} at the given point. */
	static void spawnStill(ServerLevel level, String spec, double x, double y, double z) {
		PokemonEntity pokemon = PokemonProperties.Companion.parse(spec, " ", "=").createEntity(level);
		pokemon.setPos(x, y, z);
		pokemon.setNoAi(true);
		level.addFreshEntity(pokemon);
	}
}
