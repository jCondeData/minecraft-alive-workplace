package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pokémon kept in Cobblemon Pasture Blocks, as partners for villagers (see {@code work/Partners}). */
public final class CobblemonPartners {
	/**
	 * Names of the pastured Pokémon within {@code radius} of {@code center} with one of {@code types}
	 * (lower-case type names), nearest first, at most {@code max}.
	 */
	public static List<Component> helpers(ServerLevel level, BlockPos center, int radius, Set<String> types, int max) {
		AABB box = new AABB(center).inflate(radius);
		Vec3 middle = Vec3.atCenterOf(center);
		return level.getEntitiesOfClass(PokemonEntity.class, box, e -> e.isAlive() && e.getTethering() != null && fits(e.getPokemon(), types))
			.stream()
			.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(middle)))
			.limit(max)
			.map(e -> (Component) e.getPokemon().getDisplayName(false))
			.toList();
	}

	static boolean fits(Pokemon pokemon, Set<String> types) {
		for (ElementalType type : pokemon.getTypes()) {
			if (types.contains(type.getName().toLowerCase(java.util.Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private CobblemonPartners() {
	}
}
