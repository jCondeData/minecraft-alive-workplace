package io.github.jcondedata.aliveworkplace.ranch;

import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** The rancher's daycare for Pokémon (see {@link Daycare}). Filled in by {@code compat/cobblemon}. */
public interface DaycareDesk {
	Extension<DaycareDesk> EXTENSION = new Extension<>("the daycare");

	/** Opens the daycare screen for {@code player} at {@code rancher}. */
	void open(ServerPlayer player, Villager rancher);

	/** Sends every Pokémon in {@code rancher}'s care back to its trainer. */
	void returnAll(ServerLevel level, Villager rancher);
}
