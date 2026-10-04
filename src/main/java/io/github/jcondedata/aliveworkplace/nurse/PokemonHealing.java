package io.github.jcondedata.aliveworkplace.nurse;

import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** A nurse healing a player's Pokémon (see {@link Nurses}). Filled in by {@code compat/cobblemon}. */
public interface PokemonHealing {
	Extension<PokemonHealing> EXTENSION = new Extension<>("Pokémon healing");

	/** Whether {@code player} is in a battle (no healing then). */
	boolean inBattle(ServerPlayer player);

	/** Heals {@code player}'s party; how many Pokémon needed it. */
	int healParty(ServerPlayer player);

	/** Not a Healing Machine at {@code machine}, or it can't take this party (ROADMAP 28.7). */
	int NO_MACHINE = -1;
	/** The machine is healing someone else's team. */
	int MACHINE_BUSY = -2;

	/**
	 * Puts {@code player}'s Poké Balls in the Healing Machine at {@code machine}, free (its own animation and heal time):
	 * how many Pokémon needed healing (0: none, and the machine isn't started), or {@link #NO_MACHINE} or
	 * {@link #MACHINE_BUSY}.
	 */
	default int healAtMachine(ServerPlayer player, ServerLevel level, BlockPos machine) {
		return NO_MACHINE;
	}

	/** Tops up the Healing Machine at {@code machine} (a nurse on shift there keeps it charged). */
	default void charge(ServerLevel level, BlockPos machine) {
	}
}
