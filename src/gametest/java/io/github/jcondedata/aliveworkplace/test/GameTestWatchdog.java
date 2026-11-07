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
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

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
 * <p>It also ends a test whose own timeout can't fire (B941, B963). Vanilla's {@code GameTestInfo.tick} returns
 * before counting anything while any chunk of the test's area isn't entity-ticking (and while its structure waits to be
 * placed), and the timeout is counted in game time and checked only after that, so a test whose chunks never get there
 * (or whose game time stops) waits forever: the server keeps ticking, the stall check above is satisfied, no timeout
 * fires and the log fills with progress bars. A test of the running batch still not done {@link #GRACE_TICKS} server
 * ticks past its timeout and setup ticks, and after at least {@link #GRACE_SECONDS} of running, is failed with a report of its chunks, the chunk system and the clock, through
 * its listeners exactly as a timeout is, so the batch ends and the run goes on. After {@link #MAX_OVERDUE} of them the
 * run is halted with every thread's stack: the world is stuck and every later batch would wait the same way.
 *
 * <p>Only on the headless GameTest server, and it logs nothing unless the run stalls. The compatibility tests use the
 * same class (build.gradle.kts adds this file to their source set). {@code -PgametestStallSeconds=N} on either run
 * changes the limit.
 */
public final class GameTestWatchdog implements ModInitializer {
	static final long STALL_SECONDS = Long.getLong("aliveworkplace.gametestStallSeconds", 300);
	static final long STARTUP_SECONDS = Math.max(STALL_SECONDS, 1200);
	/** Server ticks a test may run past its own timeout and setup ticks (when that timeout can't fire) before it fails. */
	static final long GRACE_TICKS = Long.getLong("aliveworkplace.gametestGraceTicks", 2400);
	/** ... and the least time it must have been running by the clock (the server's ticks can be very fast or slow). */
	static final long GRACE_SECONDS = Long.getLong("aliveworkplace.gametestGraceSeconds", 120);
	static final int MAX_OVERDUE = 3;
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	private static volatile Watch watch;
	/** The running batch's tests not yet done, with the server ticks each has run (server thread only). */
	private static final Overdue<GameTestInfo> RUNNING = new Overdue<>(System::nanoTime, TimeUnit.SECONDS.toNanos(GRACE_SECONDS));
	private static int overdue;

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
				checkOverdue(server, w);
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

	/** Fails each test of the running batch that has run far past its own timeout (B941, B963); see the class comment. */
	private static void checkOverdue(MinecraftServer server, Watch w) {
		Collection<GameTestInfo> active = activeTests();
		if (active == null) {
			return;
		}
		List<GameTestInfo> running = new ArrayList<>();
		for (GameTestInfo info : active) {
			if (!info.isDone()) {
				running.add(info);
			}
		}
		for (GameTestInfo info : RUNNING.tick(running, GameTestWatchdog::limit)) {
			String report = stuckReport(server, info, RUNNING.ticks(info));
			LOGGER.error(report);
			info.fail(new GameTestAssertException(report.lines().findFirst().orElse(report)));
			GameTestRunner runner = runner();
			info.getListeners().forEach(listener -> listener.testFailed(info, runner)); // as GameTestInfo.tick does on a timeout
			if (++overdue >= MAX_OVERDUE) {
				fail(server, w, "GameTest watchdog: " + overdue + " tests ran far past their timeouts without one firing (the last: "
					+ info.getTestName() + ", reported above), so the world is stuck and every later batch would wait the same way. Failing the run.");
			}
		}
	}

	/** Server ticks a test may run before the watchdog ends it: its timeout and setup ticks, then {@link #GRACE_TICKS}. */
	static long limit(GameTestInfo info) {
		return info.getTimeoutTicks() + Math.max(0, info.getTestFunction().setupTicks()) + GRACE_TICKS;
	}

	/**
	 * Why a test's timeout didn't fire: its own state (started, its game-time tick against its timeout) and, for every
	 * chunk of its area, whether it is entity-ticking, forced and has its entities loaded, with the chunk holder's debug
	 * line; then the level's chunk system and the clock. The first line is the failure message.
	 */
	static String stuckReport(MinecraftServer server, GameTestInfo info, long ticks) {
		StringBuilder out = new StringBuilder();
		ServerLevel level = info.getLevel();
		out.append("GameTest watchdog: ").append(info.getTestName()).append(" still running after ").append(ticks)
			.append(" server ticks, its timeout of ").append(info.getTimeoutTicks()).append(" never fired (")
			.append(info.hasStarted() ? "it started, but its game-time clock is at " + field(GameTestInfo.class, info, "tickCount")
				: "it never started: vanilla counts no timeout until every chunk of its area is entity-ticking and its structure is placed")
			.append(")\n");
		out.append("  test: started=").append(info.hasStarted()).append(", chunksLoaded=").append(field(GameTestInfo.class, info, "chunksLoaded"))
			.append(", placedStructure=").append(field(GameTestInfo.class, info, "placedStructure"))
			.append(", ticksToWaitForChunkLoading=").append(field(GameTestInfo.class, info, "ticksToWaitForChunkLoading"))
			.append(", structure block=").append(info.getStructureBlockEntity() == null ? "none" : info.getStructureBlockPos().toShortString()).append('\n');
		try {
			if (info.getStructureBlockEntity() != null) {
				net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.gametest.framework.StructureUtils.getStructureBoundingBox(info.getStructureBlockEntity());
				out.append("  area: ").append(box).append('\n');
				box.intersectingChunks().forEach(chunk -> out.append("  chunk ").append(chunk.x).append(',').append(chunk.z)
					.append(": entity-ticking=").append(level.isPositionEntityTicking(chunk.getWorldPosition()))
					.append(", forced=").append(level.getForcedChunks().contains(chunk.toLong()))
					.append(", entities loaded=").append(level.areEntitiesLoaded(chunk.toLong()))
					.append(", holder: ").append(level.getChunkSource().getChunkDebugData(chunk)).append('\n'));
			}
			out.append("  chunk system: ").append(level.getChunkSource().chunkMap.size()).append(" holders, ")
				.append(level.getChunkSource().getLoadedChunksCount()).append(" loaded, ")
				.append(level.getChunkSource().getPendingTasksCount()).append(" pending tasks, ")
				.append(sizeOf(field(net.minecraft.server.level.ChunkMap.class, level.getChunkSource().chunkMap, "toDrop"))).append(" to drop, ")
				.append(level.getForcedChunks().size()).append(" forced; ").append(level.getWatchdogStats()).append('\n');
			out.append("  server: tick ").append(server.getTickCount()).append(", game time ").append(level.getGameTime())
				.append(", tick rate ").append(server.tickRateManager().tickrate())
				.append(server.tickRateManager().isFrozen() ? " FROZEN" : "").append(server.tickRateManager().isSprinting() ? " sprinting" : "")
				.append(", average tick ").append(TimeUnit.NANOSECONDS.toMillis(server.getAverageTickTimeNanos())).append(" ms\n");
		} catch (RuntimeException e) {
			out.append("  (report cut short: ").append(e).append(")\n");
		}
		return out.toString();
	}

	private static Object field(Class<?> owner, Object target, String name) {
		try {
			java.lang.reflect.Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "?";
		}
	}

	private static String sizeOf(Object collection) {
		return collection instanceof java.util.Collection<?> c ? String.valueOf(c.size()) : "?";
	}

	/** The tests the ticker is running now (the current batch), or null if they can't be read. */
	@SuppressWarnings("unchecked")
	private static Collection<GameTestInfo> activeTests() {
		Object tests = field(GameTestTicker.class, GameTestTicker.SINGLETON, "testInfos");
		return tests instanceof Collection<?> c ? (Collection<GameTestInfo>) c : null;
	}

	private static GameTestRunner runner() {
		Object runner = field(GameTestTicker.class, GameTestTicker.SINGLETON, "runner");
		return runner instanceof GameTestRunner r ? r : null;
	}

	/**
	 * Counts the server ticks each running item has run, apart from the server so a test can drive it. {@link #tick}
	 * takes the items running this tick (an item no longer among them is forgotten) and returns those that have just
	 * passed their limit in ticks and have run at least the minimum time, each once. The time matters because the
	 * headless server ticks as fast as it can (a 4000-tick test can take 3 s), so ticks alone could end a test whose
	 * chunks are only slow to generate.
	 */
	static final class Overdue<T> {
		private final LongSupplier clock;
		private final long minNanos;
		private final Map<T, Long> waited = new IdentityHashMap<>();
		private final Map<T, Long> since = new IdentityHashMap<>();
		private final Set<T> reported = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

		/** {@code minNanos}: an item is overdue only once it has also been running this long by {@code clock}. */
		Overdue(LongSupplier clock, long minNanos) {
			this.clock = clock;
			this.minNanos = minNanos;
		}

		List<T> tick(Collection<T> running, ToLongFunction<T> limit) {
			Set<T> now = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			now.addAll(running);
			waited.keySet().retainAll(now);
			since.keySet().retainAll(now);
			reported.retainAll(now);
			long time = clock.getAsLong();
			List<T> over = new ArrayList<>();
			for (T item : now) {
				long n = waited.merge(item, 1L, Long::sum);
				long first = since.computeIfAbsent(item, k -> time);
				if (n > limit.applyAsLong(item) && time - first >= minNanos && reported.add(item)) {
					over.add(item);
				}
			}
			return over;
		}

		long ticks(T item) {
			return waited.getOrDefault(item, 0L);
		}
	}

	/** Reports the stall with every thread's stack and halts the JVM with a failure. */
	private static void fail(MinecraftServer server, Watch w) {
		fail(server, w, null);
	}

	/** Halts the run with the stall report, or with {@code headline} and every thread's stack when one is given. */
	private static void fail(MinecraftServer server, Watch w, String headline) {
		String report = headline == null ? report(w, running(server)) : headline + " Every thread's stack:\n" + stacks();
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
		return out.append(stacks()).toString();
	}

	private static String stacks() {
		StringBuilder out = new StringBuilder();
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
