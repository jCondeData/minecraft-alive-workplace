package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The wild Pokémon round a village, as the Pokémon Ranger (ROADMAP 29.22) sees them: the Alphas near the hall (the
 * condition {@code alpha_near} and the ranger's calming), the wild ones they may befriend, and the village's Pasture
 * Blocks a befriended one joins. Filled in by {@code compat/cobblemon} when Cobblemon is installed; without it there
 * are no wild Pokémon (the Ranger's file needs Cobblemon, so without it nobody comes).
 */
public interface WildPokemon {
	Extension<WildPokemon> EXTENSION = new Extension<>("the wild Pokémon");

	/** How a befriending ended. */
	enum Befriended {
		/** In a Pasture Block of the village, the hall owner's. */
		PASTURED,
		/** In the hall owner's PC: they were away, so it couldn't be let out in a pasture (Cobblemon needs them there). */
		IN_PC,
		/** Never: a legendary, mythical, Ultra Beast or paradox Pokémon, or one that isn't wild any more. */
		REFUSED,
		/** No Pasture Block of the village with room. */
		NO_ROOM
	}

	/** Wild Alphas within {@code radius} blocks of {@code center}: Cobblemon's Alpha mark, or a wild Pokémon of {@link PokemonRanger#ALPHA_LEVEL} or more. */
	List<Entity> alphas(ServerLevel level, BlockPos center, int radius);

	/** Whether {@code entity} is a wild Pokémon (no trainer, not in a pasture, not in a battle). */
	boolean isWild(Entity entity);

	/** Whether {@code entity} is a Pokémon nobody may befriend: a legendary, mythical, Ultra Beast or paradox one, as with trainers. */
	boolean banned(Entity entity);

	/** Wild Pokémon within {@code radius} of {@code center} a Ranger may befriend (never a banned one), the nearest first. */
	List<Entity> befriendable(ServerLevel level, BlockPos center, int radius);

	/** A Pasture Block of the village round {@code hall} with room, nobody's or {@code owner}'s, the nearest to the hall first. */
	Optional<BlockPos> pastureWithRoom(ServerLevel level, BlockPos hall, UUID owner);

	/** {@code wild} becomes {@code owner}'s Pokémon: into their PC first, as Cobblemon does, then into the pasture at {@code pasture}. */
	Befriended befriend(ServerLevel level, Entity wild, BlockPos pasture, UUID owner);

	/** The Pokémon's name ("Pikachu"). */
	Component name(Entity entity);

	/** The Alphas near {@code center}, or none without Cobblemon. */
	static List<Entity> alphasNear(ServerLevel level, BlockPos center, int radius) {
		return EXTENSION.call(w -> w.alphas(level, center, radius), List.of());
	}
}
