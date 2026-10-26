package io.github.jcondedata.aliveworkplace.devclient;

import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The hall_waystones scene's use of the Waystones mod itself (ROADMAP 41.2): the player discovers a stone, as using it
 * does. Its own class, so Waystones' classes are loaded only when a scene filmed with Waystones calls it.
 */
final class ShowcaseWaystones {
	static void discover(ServerPlayer player, ServerLevel level, BlockPos stone) {
		WaystonesAPI.getWaystoneAt(level, stone).ifPresent(w -> WaystonesAPI.activateWaystone(player, w));
	}

	private ShowcaseWaystones() {
	}
}
