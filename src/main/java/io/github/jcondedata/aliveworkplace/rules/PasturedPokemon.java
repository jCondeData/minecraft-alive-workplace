package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.legend.PokemonCensus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code pastured_pokemon}: the village's Pasture Blocks hold {@code count} Pokémon of at least {@code types} types (the
 * Pokémon Professor, ROADMAP 29.21), counted by {@link PokemonCensus} (none without Cobblemon). The progress counts the
 * Pokémon, held one short of the goal until the types are there too, so the bar only fills when both are met.
 */
public record PasturedPokemon(int count, int types) implements Condition {
	static PasturedPokemon read(JsonObject json) {
		return new PasturedPokemon(Conditions.count(json, "count"), json.has("types") ? Conditions.count(json, "types") : 0);
	}

	@Override
	public String type() {
		return "pastured_pokemon";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		PokemonCensus.Count census = PokemonCensus.of(level, hall);
		int kinds = census.types().size();
		int have = kinds >= types ? census.pokemon() : Math.min(census.pokemon(), Math.max(0, count - 1));
		return new Progress(have, count, Component.translatable("rule.aliveworkplace.pastured_pokemon", census.pokemon(), count, kinds, types));
	}
}
