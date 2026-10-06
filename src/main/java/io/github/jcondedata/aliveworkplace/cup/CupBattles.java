package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * The real battles of the Festival Cup's player bouts (ROADMAP 28.20). Filled in by {@code compat/cobblemon}; without it
 * a bout with a player is never called and is settled at the theme's end as before (a player not at the ring loses by
 * walkover, else the bout's seed decides).
 */
public interface CupBattles {
	Extension<CupBattles> EXTENSION = new Extension<>("cup battles");

	/** Who of a player's party may fight under a theme: the names of those going, and a line for each left out (who and why). */
	record Team(List<Component> going, List<Component> leftOut) {
	}

	/** The player's team for {@code theme}: the party's first eligible Pokémon, up to the theme's count. */
	Team team(ServerPlayer player, CupThemes.Theme theme);

	/**
	 * Starts {@code player}'s battle against {@code trainer} (the entrant {@code entrant}, or their delegate) in the theme's
	 * format, level and rules, both sides with healed copies. Returns the battle's id, or null if it couldn't start.
	 */
	@Nullable
	UUID versusVillager(ServerPlayer player, Villager trainer, Component name, int tier, UUID entrant, CupThemes.Theme theme);

	/** Starts a battle between two players in the theme's format, level and rules, with healed copies of their eligible teams. */
	@Nullable
	UUID versusPlayer(ServerPlayer a, ServerPlayer b, CupThemes.Theme theme);

	/** Whether the battle is still being fought. */
	boolean running(UUID battle);

	/** Puts {@code spectator} in the spectator view of the battle; false if they can't watch it. */
	boolean watch(ServerPlayer spectator, UUID battle);
}
