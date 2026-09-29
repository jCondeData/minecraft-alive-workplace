//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

// The Cobblemon half of the Nurse. Only touched when Cobblemon is installed
// ({@code FabricLoader.isModLoaded("cobblemon")}); Cobblemon is a compile-only dependency.
public final class CobblemonNurse {
	// True while the player is in a Pokémon battle (no healing mid-fight).
	public static boolean inBattle(ServerPlayer player) {
		return BattleRegistry.getBattleByParticipatingPlayer(player) != null;
	}

	// Heals the player's whole party. Returns how many Pokémon needed it.
	public static int healParty(ServerPlayer player) {
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		int hurt = 0;
		for (Pokemon pokemon : party) {
			if (pokemon.canBeHealed()) {
				hurt++;
			}
		}
		party.heal();
		return hurt;
	}

	private CobblemonNurse() {
	}
}
//?}
