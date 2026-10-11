package io.github.jcondedata.aliveworkplace.command;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

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
		return who != null && who.getClass().getName().startsWith(OURS);
	}

	/** Once a server tick: the tick's total goes into the window. */
	public static void endTick() {
		if (!recording) {
			return;
		}
		long tick = thisTick;
		String what = biggestWhat == null ? "none" : name(biggestWhat) + " " + ms(biggestCall);
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
			log.accept("Benchmark: a tick with " + ms(tick) + " of our code (biggest call: " + what + ")");
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
			+ " (worst " + ms(worst) + ")");
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
