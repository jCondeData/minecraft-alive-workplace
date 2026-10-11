package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Field;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestBatchListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Logs out every mock player when a new batch of GameTests starts. Most tests make a mock player
 * ({@code makeMockServerPlayerInLevel}) and never log it out, so it stays in the player list for the rest of the run:
 * its connection's channel keeps every packet sent to it and its advancements keep listening, and every later broadcast
 * goes to all of them. By the 860th batch there were 347 of them, and the suite ran out of its 1.5 GB heap on a 7 GB
 * machine. Batches run one after another, so when one starts no earlier test's player is in use.
 *
 * <p>Only on the headless GameTest server: a player running {@code /test} in a client keeps their own player.
 */
public final class MockPlayerSweep implements ModInitializer {
	private static GameTestRunner watched;
	private static boolean warned;

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(MockPlayerSweep::watch);
	}

	/** Adds the sweep to the GameTest runner once it exists (it's made after the server starts). */
	private static void watch(MinecraftServer server) {
		if (!(server instanceof GameTestServer)) {
			return;
		}
		GameTestRunner runner = runner();
		if (runner == null || runner == watched) {
			return;
		}
		watched = runner;
		runner.addListener(new GameTestBatchListener() {
			@Override
			public void testBatchStarting(GameTestBatch batch) {
				sweep(server);
			}

			@Override
			public void testBatchFinished(GameTestBatch batch) {
			}
		});
	}

	/** Logs out every player on the server: on the GameTest server they are all tests' mock players. */
	static int sweep(MinecraftServer server) {
		List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
		for (ServerPlayer player : players) {
			server.getPlayerList().remove(player);
		}
		return players.size();
	}

	private static GameTestRunner runner() {
		try {
			Field field = GameTestTicker.class.getDeclaredField("runner");
			field.setAccessible(true);
			return (GameTestRunner) field.get(GameTestTicker.SINGLETON);
		} catch (ReflectiveOperationException e) {
			if (!warned) {
				warned = true;
				org.slf4j.LoggerFactory.getLogger("aliveworkplace_test").warn("Can't sweep mock players between batches: {}", e.toString());
			}
			return null;
		}
	}
}
