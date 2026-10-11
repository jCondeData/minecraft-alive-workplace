package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestBatchListener;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Logs out every mock player when a new batch of GameTests starts. Most tests make a mock player
 * ({@code makeMockServerPlayerInLevel}) and never log it out, so it stays in the player list for the rest of the run:
 * its connection's channel keeps every packet sent to it and its advancements keep listening, and every later broadcast
 * goes to all of them. By the 860th batch there were 347 of them, and the suite ran out of its 1.5 GB heap on a 7 GB
 * machine. Batches run one after another, so when one starts no earlier test's player is in use.
 *
 * <p>Taking them off the player list wasn't enough (2026-10-11, about 1,800 tests: out of heap again at 1.75 GB). Two
 * things still held every one, each with its ~1,400 advancement progresses (about half a megabyte a player):
 * <ul>
 * <li>Fabric's networking keeps each connection's add-on until the connection's channel closes, and a mock player's
 * channel was never closed. The sweep now disconnects the player and closes the channel, as a real logout does.</li>
 * <li>The runner keeps every test's {@code GameTestInfo} to the end of the run, and with it whatever the test's
 * sequences, scheduled runnables and end-of-test listeners captured: its player, its villagers, its positions. The sweep
 * drops those of the tests already done, which nothing runs again.</li>
 * </ul>
 *
 * <p>Only on the headless GameTest server: a player running {@code /test} in a client keeps their own player.
 */
public final class MockPlayerSweep implements ModInitializer {
	private static GameTestRunner watched;
	private static boolean warned;
	private static boolean forgetPending;

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
		if (runner != null && runner == watched && forgetPending) {
			forgetPending = false;
			forgetDone(runner);
		}
		if (runner == null || runner == watched) {
			return;
		}
		watched = runner;
		runner.addListener(new GameTestBatchListener() {
			@Override
			public void testBatchStarting(GameTestBatch batch) {
				sweep(server);
				// (after the tick, not here: the last test's own tick, still going through its sequences, started this batch)
				forgetPending = true;
			}

			@Override
			public void testBatchFinished(GameTestBatch batch) {
			}
		});
	}

	/**
	 * Logs out every player on the server: on the GameTest server they are all tests' mock players. Each is disconnected
	 * as a real logout would be (off the level and the player list), its channel closed so Fabric's networking lets go
	 * of the connection, and the packets queued on that channel released.
	 */
	static int sweep(MinecraftServer server) {
		List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
		for (ServerPlayer player : players) {
			if (player.connection != null) {
				player.connection.onDisconnect(new DisconnectionDetails(Component.literal("GameTest batch over")));
			}
			server.getPlayerList().remove(player); // (already done by the disconnect; harmless, and sure)
			Channel channel = channel(player);
			if (channel != null) {
				channel.close(); // Fabric ends the connection's networking session when its channel goes inactive
				if (channel instanceof EmbeddedChannel embedded) {
					embedded.finishAndReleaseAll();
				}
			}
		}
		return players.size();
	}

	/** A mock player's netty channel, or null when it can't be reached. */
	private static Channel channel(ServerPlayer player) {
		try {
			Field connectionField = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
			connectionField.setAccessible(true);
			Connection connection = (Connection) connectionField.get(player.connection);
			Field channelField = Connection.class.getDeclaredField("channel");
			channelField.setAccessible(true);
			return (Channel) channelField.get(connection);
		} catch (ReflectiveOperationException | RuntimeException e) {
			warn("Can't close a mock player's channel: {}", e);
			return null;
		}
	}

	/**
	 * Drops what the tests already done still hold: their sequences, scheduled runnables and listeners. The runner keeps
	 * every test to the end of the run (for the report), and these hold everything the test's lambdas captured.
	 */
	static void forgetDone(GameTestRunner runner) {
		try {
			Field sequences = GameTestInfo.class.getDeclaredField("sequences");
			Field scheduled = GameTestInfo.class.getDeclaredField("runAtTickTimeMap");
			Field listeners = GameTestInfo.class.getDeclaredField("listeners");
			sequences.setAccessible(true);
			scheduled.setAccessible(true);
			listeners.setAccessible(true);
			for (GameTestInfo info : runner.getTestInfos()) {
				if (info.isDone()) {
					((Collection<?>) sequences.get(info)).clear();
					((Map<?, ?>) scheduled.get(info)).clear();
					((Collection<?>) listeners.get(info)).clear();
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			warn("Can't forget the finished tests' state: {}", e);
		}
	}

	private static void warn(String message, Exception e) {
		if (!warned) {
			warned = true;
			org.slf4j.LoggerFactory.getLogger("aliveworkplace_test").warn(message, e.toString());
		}
	}

	private static GameTestRunner runner() {
		try {
			Field field = GameTestTicker.class.getDeclaredField("runner");
			field.setAccessible(true);
			return (GameTestRunner) field.get(GameTestTicker.SINGLETON);
		} catch (ReflectiveOperationException e) {
			warn("Can't sweep mock players between batches: {}", e);
			return null;
		}
	}
}
