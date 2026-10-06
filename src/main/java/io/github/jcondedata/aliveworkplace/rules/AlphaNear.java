package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code alpha_near}: a wild Alpha Pokémon is within {@code radius} blocks of the hall (the Pokémon Ranger, ROADMAP
 * 29.22): Cobblemon's Alpha mark, or a wild Pokémon of level 50 or more, found by {@link WildPokemon} (none without
 * Cobblemon).
 */
public record AlphaNear(int radius) implements Condition {
	public static final int DEFAULT_RADIUS = 96;

	static AlphaNear read(JsonObject json) {
		return new AlphaNear(json.has("radius") ? Conditions.count(json, "radius") : DEFAULT_RADIUS);
	}

	@Override
	public String type() {
		return "alpha_near";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), WildPokemon.alphasNear(level, hall, radius).isEmpty() ? 0 : 1, 1, radius);
	}
}
