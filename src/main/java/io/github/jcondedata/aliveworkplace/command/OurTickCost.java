package io.github.jcondedata.aliveworkplace.command;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * What our own code costs each server tick in the benchmark (ROADMAP 25.3): every job behaviour of ours (its start,
 * tick and stop, through {@code mixin/BehaviorMixin}) and every server and level tick handler of ours (wrapped by the
 * platform) is timed with {@link System#nanoTime()}, and the times are added up per server tick. That tells a long
 * tick of ours from a run of ticks the server plays back to back to catch up, which a sampling profile can't
 * ({@code tools/packtest/perf.py --spikes}).
 *
 * <p>Only on with {@code -Daliveworkplace.benchmark=true} and after {@code /workplace benchmark} has started a
 * recording; until then every call is one field read, and without the property the tick handlers aren't even wrapped.
 * Every {@link #WINDOW} ticks it logs a "Benchmark: our code a tick" line (p50, p99, the worst tick and its biggest
 * call), and each tick with over {@link #LONG_MS} ms of ours gets a line of its own.
 *
 * <p>A long tick can also be a stall that only happened to fall in our code: a garbage collection pause (every thread
 * stops) or the server thread waiting for the CPU on a loaded machine. So each tick also reads, once, the collectors'
 * total pause time and the server thread's CPU time: a long tick's line says the tick's own length, the pauses and the
 * time the thread was off the CPU, and what is left of ours without them ("on the CPU"). That last figure is what the
 * "no tick over 50 ms caused by us" target is judged by.
 */
public final class OurTickCost {
	/** The property the pack test's measuring modes set; read once. */
	public static final boolean ENABLED = Boolean.getBoolean("aliveworkplace.benchmark");
	/** Ticks a summary line covers (a minute). */
	public static final int WINDOW = 1200;
	/** 25.2's target: no tick over this caused by us. */
	public static final double LONG_MS = 50.0;
	private static final String OURS = "io.github.jcondedata.aliveworkplace.";

	private static boolean recording;
	private static Consumer<String> log = AliveWorkplace.LOG::info;
	/** Nested measured calls (our behaviour inside our tick handler) count once, by the outermost. */
	private static int depth;
	private static long startedAt;
	private static long thisTick;
	private static long biggestCall;
	private static Class<?> biggestWhat;
	private static long[] window = new long[WINDOW];
	private static int filled;
	private static long windowWorst;
	private static String windowWorstWhat = "none";
	private static long ticks;
	private static long lastTick;
	private static long worst;
	private static int longTicks;
	/** Long ticks still over {@link #LONG_MS} without the tick's GC pauses and off-CPU time, and their worst. */
	private static int longOnCpu;
	private static long worstOnCpu;
	/** The collectors' pause total and the server thread's CPU time at the last tick's end (-1: not read yet). */
	private static long lastGc = -1L;
	private static long lastCpu = -1L;
	/** Whether a class is ours, once per class: {@code ours} runs for every behaviour of every mob each tick. */
	private static final ClassValue<Boolean> OURS_BY_CLASS = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			return type.getName().startsWith(OURS);
		}
	};
	private static List<GarbageCollectorMXBean> collectors;
	private static ThreadMXBean threads;

	private OurTickCost() {
	}

	/** Starts a recording from nothing; {@code sink} gets the log lines. */
	public static void begin(Consumer<String> sink) {
		log = sink;
		depth = 0;
		thisTick = 0L;
		biggestCall = 0L;
		biggestWhat = null;
		filled = 0;
		windowWorst = 0L;
		windowWorstWhat = "none";
		ticks = 0L;
		lastTick = 0L;
		worst = 0L;
		longTicks = 0;
		longOnCpu = 0;
		worstOnCpu = 0L;
		lastGc = -1L;
		lastCpu = -1L;
		recording = true;
	}

	/** Ends the recording, logging the partial window if any. */
	public static void finish() {
		if (recording && filled > 0) {
			logWindow();
		}
		recording = false;
		log = AliveWorkplace.LOG::info;
	}

	public static boolean recording() {
		return recording;
	}

	/** Before a piece of work of {@code who} (a behaviour or a tick handler); only ours is measured. */
	public static void enter(Object who) {
		if (!recording || !ours(who)) {
			return;
		}
		if (depth++ == 0) {
			startedAt = System.nanoTime();
		}
	}

	/** After the work {@link #enter} started; the same {@code who}. */
	public static void exit(Object who) {
		if (!recording || depth == 0 || !ours(who)) {
			return;
		}
		if (--depth == 0) {
			long took = System.nanoTime() - startedAt;
			thisTick += took;
			if (took > biggestCall) {
				biggestCall = took;
				biggestWhat = who.getClass();
			}
		}
	}

	private static boolean ours(Object who) {
		return who != null && OURS_BY_CLASS.get(who.getClass());
	}

	/** Once a server tick, at its end: the tick's total goes into the window, with the tick's stalls read once. */
	public static void endTick(MinecraftServer server) {
		if (!recording) {
			return;
		}
		long[] times = server.getTickTimesNanos();
		long wall = times[server.getTickCount() % times.length]; // tallied just before the server's end-tick event
		long gc = gcNanos();
		long cpu = cpuNanos();
		long gcInTick = lastGc < 0 || gc < 0 ? -1L : gc - lastGc;
		long cpuInTick = lastCpu < 0 || cpu < 0 ? -1L : cpu - lastCpu;
		lastGc = gc;
		lastCpu = cpu;
		endTick(wall, gcInTick, cpuInTick);
	}

	/** A tick's end with no stall figures (tests). */
	public static void endTick() {
		endTick(-1L, -1L, -1L);
	}

	/**
	 * A tick's end: {@code tickNanos} the tick's own length, {@code gcNanos} the collectors' pauses and
	 * {@code cpuNanos} the server thread's CPU time since the last tick's end; -1 for a figure not known.
	 */
	public static void endTick(long tickNanos, long gcNanos, long cpuNanos) {
		if (!recording) {
			return;
		}
		long tick = thisTick;
		// B412: written out only for a tick that is logged or is the window's worst so far; formatting it every tick put
		// the benchmark's own String.format in our share of the profile
		String what = tick > windowWorst || tick / 1_000_000.0 > LONG_MS
			? biggestWhat == null ? "none" : name(biggestWhat) + " " + ms(biggestCall)
			: null;
		thisTick = 0L;
		biggestCall = 0L;
		biggestWhat = null;
		depth = 0;
		ticks++;
		lastTick = tick;
		window[filled++] = tick;
		if (tick > windowWorst) {
			windowWorst = tick;
			windowWorstWhat = what;
		}
		if (tick > worst) {
			worst = tick;
		}
		if (tick / 1_000_000.0 > LONG_MS) {
			longTicks++;
			long onCpu = onCpu(tick, tickNanos, gcNanos, cpuNanos);
			if (onCpu / 1_000_000.0 > LONG_MS) {
				longOnCpu++;
			}
			worstOnCpu = Math.max(worstOnCpu, onCpu);
			log.accept("Benchmark: a tick with " + ms(tick) + " of our code (biggest call: " + what + "); the tick "
				+ known(tickNanos) + ", GC pauses " + known(gcNanos) + ", the server thread off the CPU "
				+ known(offCpu(tickNanos, cpuNanos)) + "; ours on the CPU " + ms(onCpu));
		}
		if (filled == WINDOW) {
			logWindow();
		}
	}

	private static void logWindow() {
		long[] part = Arrays.copyOf(window, filled);
		log.accept("Benchmark: our code a tick over the last " + filled + " ticks: p50 " + ms(percentile(part, 50))
			+ ", p99 " + ms(percentile(part, 99)) + ", max " + ms(windowWorst) + " (biggest call: " + windowWorstWhat
			+ "); ticks with over " + (int) LONG_MS + " ms of ours so far: " + longTicks + " of " + ticks
			+ " (worst " + ms(worst) + "), on the CPU without GC pauses: " + longOnCpu + " (worst " + ms(worstOnCpu) + ")");
		filled = 0;
		windowWorst = 0L;
		windowWorstWhat = "none";
	}

	/** Ticks recorded, the last and the worst one's nanoseconds and the ticks over {@link #LONG_MS}, for tests. */
	public static long ticks() {
		return ticks;
	}

	public static long lastTickNanos() {
		return lastTick;
	}

	public static long worstNanos() {
		return worst;
	}

	public static int longTicks() {
		return longTicks;
	}

	/** Long ticks still over {@link #LONG_MS} once the tick's GC pauses and off-CPU time are taken out. */
	public static int longTicksOnCpu() {
		return longOnCpu;
	}

	/**
	 * Our time in a tick without the tick's stalls: the larger of its GC pauses and the server thread's time off the
	 * CPU is taken out (both stop our calls as much as anyone's; an estimate, since a stall may have fallen outside our
	 * calls). Unknown figures take nothing out.
	 */
	static long onCpu(long ours, long tickNanos, long gcNanos, long cpuNanos) {
		long stall = Math.max(Math.max(0L, gcNanos), Math.max(0L, offCpu(tickNanos, cpuNanos)));
		return Math.max(0L, ours - stall);
	}

	/** The tick's length minus the thread's CPU time, -1 if either is unknown (CPU time also counts the tasks run
	 * between ticks, so this never overstates the time off the CPU). */
	static long offCpu(long tickNanos, long cpuNanos) {
		return tickNanos < 0 || cpuNanos < 0 ? -1L : Math.max(0L, tickNanos - cpuNanos);
	}

	private static String known(long nanos) {
		return nanos < 0 ? "unknown" : ms(nanos);
	}

	/** The collectors' pause time so far (ZGC's and Shenandoah's "Cycles" beans count concurrent work, left out). */
	private static long gcNanos() {
		if (collectors == null) {
			collectors = ManagementFactory.getGarbageCollectorMXBeans().stream()
				.filter(c -> !c.getName().contains("Cycles")).toList();
		}
		long total = 0L;
		for (GarbageCollectorMXBean c : collectors) {
			long t = c.getCollectionTime();
			if (t < 0) {
				return -1L;
			}
			total += t;
		}
		return total * 1_000_000L;
	}

	/** The calling (server) thread's CPU time, -1 where the JVM can't tell. */
	private static long cpuNanos() {
		if (threads == null) {
			threads = ManagementFactory.getThreadMXBean();
		}
		return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : -1L;
	}

	/** The {@code p}-th percentile (0-100) of {@code nanos} by the nearest-rank method; 0 for none. */
	static long percentile(long[] nanos, double p) {
		if (nanos.length == 0) {
			return 0L;
		}
		long[] sorted = nanos.clone();
		Arrays.sort(sorted);
		int rank = (int) Math.ceil(p / 100.0 * sorted.length);
		return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
	}

	/** A short name for a behaviour or a handler's lambda ({@code CupDays$$Lambda/0x...} is CupDays). */
	static String name(Class<?> type) {
		String name = type.getName();
		int lambda = name.indexOf("$$");
		if (lambda >= 0) {
			name = name.substring(0, lambda);
		}
		return name.substring(name.lastIndexOf('.') + 1);
	}

	private static String ms(long nanos) {
		return String.format(Locale.ROOT, "%.1f ms", nanos / 1_000_000.0);
	}
}
