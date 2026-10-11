package io.github.jcondedata.aliveworkplace.test;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.server.MinecraftServer;

/**
 * Fails a headless GameTest run whose server thread stops advancing (B114). A local full build once hung for two hours:
 * the server thread waited forever in {@code ServerChunkCache.getChunk} while clearing a test's area, no test timed out
 * (timeouts are counted in ticks, and no tick came) and nothing ended the run.
 *
 * <p>A daemon thread watches the server's ticks. When none has finished for {@link #STALL_SECONDS} (no test needs
 * more than a few seconds for one tick; the longest pause in a CI run's log is about 14 s), it writes every thread's
 * full stack to the log and to {@code gametest-hang.txt} in the run directory, so the next hang shows its
 * cause, and halts the JVM with exit code 1, which fails {@code runGameTest} or {@code runCompatGameTest}. It halts
 * rather than exits: a normal exit runs the server's shutdown hook, which waits for the stuck server thread.
 * Before the first tick (start-up, the pack's mods loading) it allows {@link #STARTUP_SECONDS}.
 *
 * <p>Only on the headless GameTest server, and it logs nothing unless the run stalls. The compatibility tests use the
 * same class (build.gradle.kts adds this file to their source set). {@code -PgametestStallSeconds=N} on either run
 * changes the limit.
 */
public final class GameTestWatchdog implements ModInitializer {
	static final long STALL_SECONDS = Long.getLong("aliveworkplace.gametestStallSeconds", 300);
	static final long STARTUP_SECONDS = Math.max(STALL_SECONDS, 1200);
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	private static volatile Watch watch;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (server instanceof GameTestServer && watch == null) {
				Watch w = new Watch(System::nanoTime, TimeUnit.SECONDS.toNanos(STARTUP_SECONDS), TimeUnit.SECONDS.toNanos(STALL_SECONDS));
				watch = w;
				Thread thread = new Thread(() -> run(server, w), "GameTest watchdog");
				thread.setDaemon(true);
				thread.start();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Watch w = watch;
			if (w != null) {
				w.ticked();
			}
		});
		// Saving the world on the way out isn't a tick: give it the same allowance, counted from the stop.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Watch w = watch;
			if (w != null) {
				w.ticked();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Watch w = watch;
			if (w != null) {
				w.stop();
			}
		});
	}

	private static void run(MinecraftServer server, Watch w) {
		while (!w.stopped()) {
			try {
				Thread.sleep(Math.min(10_000L, Math.max(1_000L, TimeUnit.NANOSECONDS.toMillis(w.stallNanos) / 10)));
			} catch (InterruptedException e) {
				return;
			}
			if (w.stalled()) {
				fail(server, w);
			}
		}
	}

	/** Reports the stall with every thread's stack and halts the JVM with a failure. */
	private static void fail(MinecraftServer server, Watch w) {
		String report = report(w, running(server));
		try {
			LOGGER.error(report);
		} catch (Throwable e) {
			PrintStream err = System.err; // (Minecraft sends stderr to the log too, so only when logging failed)
			err.println(report);
			err.flush();
		}
		System.out.flush();
		try {
			Files.writeString(Path.of("gametest-hang.txt"), report, StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException ignored) {
			// the log has it
		}
		Runtime.getRuntime().halt(1);
	}

	static String report(Watch w, List<String> running) {
		StringBuilder out = new StringBuilder();
		out.append("GameTest watchdog: the server thread hasn't finished a tick for ")
			.append(TimeUnit.NANOSECONDS.toSeconds(w.sinceProgress())).append(" s (limit ")
			.append(TimeUnit.NANOSECONDS.toSeconds(w.limit())).append(" s, ").append(w.ticks()).append(" ticks so far). ")
			.append("Tests still running: ").append(running.isEmpty() ? "none found" : String.join(", ", running))
			.append(". Failing the run. Every thread's stack:\n");
		for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
			out.append(stack(info));
		}
		return out.toString();
	}

	/** One thread's whole stack ({@code ThreadInfo.toString} stops after 8 frames). */
	static String stack(ThreadInfo info) {
		StringBuilder out = new StringBuilder();
		out.append('"').append(info.getThreadName()).append("\" id=").append(info.getThreadId()).append(' ').append(info.getThreadState());
		if (info.getLockName() != null) {
			out.append(" on ").append(info.getLockName());
		}
		if (info.getLockOwnerName() != null) {
			out.append(" owned by \"").append(info.getLockOwnerName()).append("\" id=").append(info.getLockOwnerId());
		}
		out.append('\n');
		StackTraceElement[] frames = info.getStackTrace();
		MonitorInfo[] monitors = info.getLockedMonitors();
		for (int i = 0; i < frames.length; i++) {
			out.append("\tat ").append(frames[i]).append('\n');
			for (MonitorInfo monitor : monitors) {
				if (monitor.getLockedStackDepth() == i) {
					out.append("\t- locked ").append(monitor).append('\n');
				}
			}
		}
		for (LockInfo lock : info.getLockedSynchronizers()) {
			out.append("\t- holds ").append(lock).append('\n');
		}
		return out.append('\n').toString();
	}

	/** The names of the tests not yet done, best effort (read from another thread while the server is stuck). */
	private static List<String> running(MinecraftServer server) {
		List<String> names = new ArrayList<>();
		try {
			java.lang.reflect.Field field = GameTestTicker.class.getDeclaredField("runner");
			field.setAccessible(true);
			GameTestRunner runner = (GameTestRunner) field.get(GameTestTicker.SINGLETON);
			if (runner != null) {
				for (GameTestInfo info : List.copyOf(runner.getTestInfos())) {
					if (!info.isDone()) {
						names.add(info.getTestName());
					}
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			names.clear();
			names.add("unknown (" + e + ")");
		}
		return names;
	}

	/**
	 * The stall clock, apart from the server so a test can drive it. Before the first tick the limit is the start-up
	 * allowance; after it, the stall limit.
	 */
	static final class Watch {
		private final LongSupplier clock;
		private final long startupNanos;
		final long stallNanos;
		private volatile long last;
		private volatile long ticks;
		private volatile boolean stopped;

		Watch(LongSupplier clock, long startupNanos, long stallNanos) {
			this.clock = clock;
			this.startupNanos = startupNanos;
			this.stallNanos = stallNanos;
			this.last = clock.getAsLong();
		}

		void ticked() {
			last = clock.getAsLong();
			ticks++; // only the server thread writes it
		}

		long ticks() {
			return ticks;
		}

		long limit() {
			return ticks == 0 ? startupNanos : stallNanos;
		}

		long sinceProgress() {
			return clock.getAsLong() - last;
		}

		boolean stalled() {
			return !stopped && sinceProgress() > limit();
		}

		void stop() {
			stopped = true;
		}

		boolean stopped() {
			return stopped;
		}
	}
}
