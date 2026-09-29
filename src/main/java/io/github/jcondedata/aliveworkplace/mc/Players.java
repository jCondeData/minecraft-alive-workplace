package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Players. Changes: {@code ServerPlayer.serverLevel()} becomes {@code level()} by 1.21.11; the inventory's
 * {@code items} field becomes {@code getNonEquipmentItems()} in 1.21.5 (porting.md).
 */
public final class Players {
	/** The level the player is in. */
	public static ServerLevel level(ServerPlayer player) {
		return player.serverLevel();
	}

	/** The main inventory and hotbar (no armor or offhand). */
	public static NonNullList<ItemStack> mainItems(Player player) {
		return player.getInventory().items;
	}

	private Players() {
	}
}
