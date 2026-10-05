package io.github.jcondedata.aliveworkplace.city;

import java.util.Arrays;

/**
 * What the Steward's own work costs the server (ROADMAP 27.22): his planning at the hall (rules, wishes, the desk,
 * walls and renewals proposed), the plot searches, the old-house surveys and the roads and walls ticking at the hall.
 * Each of those is wrapped in {@link #start()}/{@link #stop(long)}; {@link #endTick()} closes a server tick. Only
 * counted while a city soak records ({@code /workplace city}); otherwise both calls are a field read.
 */
public final class StewardCost {
	private static boolean recording;
	/** Nested measured calls (a plot search started from the planner) count once, by the outermost. */
	private static int depth;
	private static long thisTick;
	private static long[] ticks = new long[0];
	private static int count;
	private static long worst;
	private static String worstWhat = "none";
	private static int worstAt;

	private StewardCost() {
	}

	/** What {@link #start()} returns when not recording, and for a call inside another measured one (nanoTime may be negative). */
	public static final long OFF = Long.MIN_VALUE;
	static final long NESTED = Long.MIN_VALUE + 1;

	/** Starts measuring a piece of the Steward's work; pass the result to {@link #stop}. {@link #OFF} when not recording. */
	public static long start() {
		if (!recording) {
			return OFF;
		}
		return depth++ == 0 ? System.nanoTime() : NESTED;
	}

	public static void stop(long started) {
		stop(started, "other");
	}

	/** As {@link #stop(long)}, naming the work, so the slowest single call can be told apart. */
	public static void stop(long started, String what) {
		if (!recording || started == OFF) {
			return;
		}
		depth = Math.max(0, depth - 1);
		if (started != NESTED) {
			long took = System.nanoTime() - started;
			thisTick += took;
			if (took > worst) {
				worst = took;
				worstWhat = what;
				worstAt = count;
			}
		}
	}

	/** The slowest single call of the recording: what it was, how long (ms) and at which recorded tick. */
	public static String worstCall() {
		return worstWhat + " " + String.format(java.util.Locale.ROOT, "%.1f ms", worst / 1_000_000.0) + " at tick " + worstAt;
	}

	/** Once a server tick while recording: the tick's total goes on the list. */
	public static void endTick() {
		if (!recording) {
			return;
		}
		if (count == ticks.length) {
			ticks = Arrays.copyOf(ticks, Math.max(1024, ticks.length * 2));
		}
		ticks[count++] = thisTick;
		thisTick = 0L;
		depth = 0;
	}

	/** Starts a recording from nothing. */
	public static void begin() {
		ticks = new long[0];
		count = 0;
		worst = 0L;
		worstWhat = "none";
		worstAt = 0;
		thisTick = 0L;
		depth = 0;
		recording = true;
	}

	public static boolean recording() {
		return recording;
	}

	/** Ends the recording and returns each tick's nanoseconds, in tick order. */
	public static long[] finish() {
		recording = false;
		return Arrays.copyOf(ticks, count);
	}

	/** The {@code p}-th percentile (0-100) of {@code nanos}, in milliseconds, by the nearest-rank method; 0 for none. */
	public static double percentileMs(long[] nanos, double p) {
		if (nanos.length == 0) {
			return 0;
		}
		long[] sorted = nanos.clone();
		Arrays.sort(sorted);
		int rank = (int) Math.ceil(p / 100.0 * sorted.length);
		return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))] / 1_000_000.0;
	}
}
