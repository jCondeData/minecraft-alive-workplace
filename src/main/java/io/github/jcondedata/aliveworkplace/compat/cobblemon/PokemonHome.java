package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import kotlin.Unit;
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
	 * To {@code pc}, else (no box has room) to its overflow: Cobblemon's own backup store, where it puts the Pokémon of
	 * boxes it removes, saved with the PC and moved back into the boxes when room is made by a resize.
	 */
	@SuppressWarnings("unchecked")
	static void toPcOrOverflow(PCStore pc, Pokemon pokemon, String owner) {
		if (pc.add(pokemon)) {
			return;
		}
		pc.getBackupStore().add(pokemon);
		// The backup store's changes don't mark the PC for saving by themselves
		pc.getPcChangeObservable().emit(Unit.INSTANCE);
		AliveWorkplace.LOG.warn("{}'s PC is full: {} went to its overflow store", owner, pokemon.getSpecies().getName());
	}
}
