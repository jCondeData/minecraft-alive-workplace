package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where the daycares hand a Pokémon back, so that none is ever lost (B1284): Cobblemon's {@code PCStore.add} returns
 * false when no box has room, and a Pokémon whose return was ignored was gone.
 */
final class PokemonHome {
	private PokemonHome() {
	}

	/** To {@code player}'s party, else their PC; false (and the Pokémon is nowhere) when both are full. */
	static boolean toPartyOrPc(ServerPlayer player, Pokemon pokemon) {
		return Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon) || Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
	}

	/** Whether {@code player}'s party or PC has a free place. */
	static boolean hasRoom(ServerPlayer player) {
		return Cobblemon.INSTANCE.getStorage().getParty(player).getFirstAvailablePosition() != null
			|| Cobblemon.INSTANCE.getStorage().getPC(player).getFirstAvailablePosition() != null;
	}

	/** Takes {@code pokemon} back out of {@code player}'s party or PC (undoing {@link #toPartyOrPc}). */
	static void takeOut(ServerPlayer player, Pokemon pokemon) {
		if (!Cobblemon.INSTANCE.getStorage().getParty(player).remove(pokemon)) {
			Cobblemon.INSTANCE.getStorage().getPC(player).remove(pokemon);
		}
	}

	/**
	 * To {@code owner}'s PC, else (no box has room) to {@link WaitingPokemon}, saved with the world, until a place is
	 * free (B1295, B1296, B1287). Never Cobblemon's PC overflow: 1.7.3 saves it but never loads it back.
	 */
	static void toPcOrWaiting(MinecraftServer server, UUID owner, String ownerName, Pokemon pokemon) {
		if (Cobblemon.INSTANCE.getStorage().getPC(owner, server.registryAccess()).add(pokemon)) {
			return;
		}
		WaitingPokemon.keep(server, owner, ownerName, pokemon);
	}
}
