//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import io.github.jcondedata.aliveworkplace.hall.Foes;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.mc.Players;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// Wild Pokémon as the village's foes with Cobblemon (B301): where no monster can come (Cobbleverse's MobsBeGone), a
// battle a player wins against wild Pokémon, or a wild Pokémon caught, counts towards the village's clearing-out quests
// ("Defeat 8 wild Pokémon"), each Pokémon beaten once, where the player stands. A capture counts through
// POKEMON_CAPTURED only (its battle's victory is a capture too).
public final class CobblemonWildFoes {
	private static MinecraftServer server;

	// Called once at startup (when Cobblemon is installed).
	public static void init() {
		Foes.WILD_POKEMON.register("cobblemon", Boolean.TRUE);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
			if (event.getWasWildCapture() || server == null) {
				return;
			}
			List<UUID> winners = new ArrayList<>();
			for (BattleActor a : event.getWinners()) {
				if (a.getType() == ActorType.PLAYER) {
					a.getPlayerUUIDs().forEach(winners::add);
				}
			}
			int beaten = 0;
			for (BattleActor a : event.getLosers()) {
				if (a.getType() == ActorType.WILD) {
					beaten += a.getPokemonList().size();
				}
			}
			if (winners.isEmpty() || beaten == 0) {
				return;
			}
			MinecraftServer s = server;
			int count = beaten;
			s.execute(() -> {
				ServerPlayer player = s.getPlayerList().getPlayer(winners.get(0));
				if (player != null) {
					defeated(player, count);
				}
			});
		});
		CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
			defeated(event.getPlayer(), 1);
		});
	}

	// {@code player} beat or caught {@code count} wild Pokémon where they stand.
	public static void defeated(ServerPlayer player, int count) {
		VillageQuests.onWildDefeated(Players.level(player), player.blockPosition(), player, count);
	}

	private CobblemonWildFoes() {
	}
}
//?}
