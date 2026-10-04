//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.nurse.PokemonHealing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

	// ROADMAP 28.7: the nurse puts the player's team in the Healing Machine at her station. The machine is charged
	// first (it's free), then started as if the player had used it: its own animation and heal time.
	public static int healAtMachine(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof HealingMachineBlockEntity machine)) {
			return PokemonHealing.NO_MACHINE;
		}
		if (machine.isInUse()) {
			return PokemonHealing.MACHINE_BUSY;
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		int hurt = 0;
		for (Pokemon pokemon : party) {
			if (pokemon.canBeHealed()) {
				hurt++;
			}
		}
		if (hurt == 0) {
			return 0;
		}
		charge(level, pos);
		if (!machine.canHeal(party)) {
			return PokemonHealing.NO_MACHINE;
		}
		machine.activate(player.getUUID(), party);
		return hurt;
	}

	// Fills the Healing Machine's charge (the nurse on shift keeps it topped up).
	public static void charge(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof HealingMachineBlockEntity machine && machine.getHealingCharge() < machine.getMaxCharge()) {
			machine.setHealingCharge(machine.getMaxCharge());
		}
	}

	private CobblemonNurse() {
	}
}
//?}
