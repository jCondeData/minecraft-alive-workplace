package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Messages to a player. Changes: 1.21.5-1.21.11 keep only {@code Player.displayClientMessage}; 26.1 replaces it with
 * {@code Player.sendSystemMessage} and {@code sendOverlayMessage} (porting.md "Chat to a player").
 */
public final class Chat {
	/** A line in the player's chat. */
	public static void chat(Player player, Component message) {
		player.displayClientMessage(message, false);
	}

	/** Text above the hotbar. */
	public static void actionBar(Player player, Component message) {
		player.displayClientMessage(message, true);
	}

	/** {@link #actionBar} when {@code overlay}, else {@link #chat}. */
	public static void show(Player player, Component message, boolean overlay) {
		player.displayClientMessage(message, overlay);
	}

	/** A system message (Entity.sendSystemMessage: a chat line for a player on the server). */
	public static void system(Player player, Component message) {
		player.sendSystemMessage(message);
	}

	private Chat() {
	}
}
