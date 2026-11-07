package io.github.jcondedata.aliveworkplace.test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * B114: the watchdog that fails a GameTest run whose server thread stops ticking. Its clock is driven by hand here; the
 * real thing was proven once with a deliberately stuck test (B114 in ROADMAP).
 */
public class GameTestWatchdogGameTests implements FabricGameTest {
	private static final long S = 1_000_000_000L;

	/** Start-up gets its own allowance, then any gap between ticks over the stall limit is a stall; stopping ends it. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void watchdogNoticesAStall(GameTestHelper helper) {
		AtomicLong now = new AtomicLong(1000 * S);
		GameTestWatchdog.Watch watch = new GameTestWatchdog.Watch(now::get, 60 * S, 10 * S);
		now.addAndGet(59 * S);
		helper.assertFalse(watch.stalled(), "a slow start-up is not a stall");
		now.addAndGet(2 * S);
		helper.assertTrue(watch.stalled(), "a start-up past its allowance is a stall");
		watch.ticked();
		helper.assertFalse(watch.stalled(), "a tick is progress");
		for (int i = 0; i < 100; i++) {
			now.addAndGet(9 * S);
			watch.ticked();
		}
		helper.assertFalse(watch.stalled(), "slow ticks that keep coming are not a stall");
		now.addAndGet(10 * S);
		helper.assertFalse(watch.stalled(), "exactly the limit is still allowed");
		now.addAndGet(1);
		helper.assertTrue(watch.stalled(), "a tick that never ends is a stall");
		helper.assertTrue(watch.limit() == 10 * S, "after the first tick the stall limit counts, not start-up's");
		watch.stop();
		helper.assertFalse(watch.stalled(), "a stopped server is never a stall");
		helper.succeed();
	}

	/** The report names the tests still running and gives each thread's whole stack, not the JDK's first 8 frames. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void watchdogReportHasEveryStack(GameTestHelper helper) {
		AtomicLong now = new AtomicLong(0);
		GameTestWatchdog.Watch watch = new GameTestWatchdog.Watch(now::get, 60 * S, 10 * S);
		watch.ticked();
		now.addAndGet(11 * S);
		String report = GameTestWatchdog.report(watch, List.of("golemsmithgametests.sentryholdsitspointthroughafight"));
		helper.assertTrue(report.startsWith("GameTest watchdog: the server thread hasn't finished a tick for 11 s (limit 10 s, 1 ticks so far)"),
			"the headline: " + report.lines().findFirst().orElse(""));
		helper.assertTrue(report.contains("Tests still running: golemsmithgametests.sentryholdsitspointthroughafight"), "the running tests");
		String me = Thread.currentThread().getName();
		helper.assertTrue(report.contains("\"" + me + "\""), "this thread is in the dump: " + me);
		ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(Thread.currentThread().threadId(), Integer.MAX_VALUE);
		int depth = info.getStackTrace().length;
		helper.assertTrue(depth > 8, "a deep enough stack to check: " + depth);
		long frames = GameTestWatchdog.stack(info).lines().filter(l -> l.startsWith("\tat ")).count();
		helper.assertTrue(frames == depth, "every frame, not 8: " + frames + " of " + depth);
		helper.succeed();
	}

	/**
	 * B941, B963: a test whose own timeout can't fire (its chunks never entity-ticking, so vanilla never counts it) is
	 * ended once it has run its timeout, setup and the grace in server ticks, and for at least the minimum time: each such
	 * test once, and a test that finished or left the batch is forgotten.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void watchdogEndsATestPastItsTimeout(GameTestHelper helper) {
		AtomicLong now = new AtomicLong(0);
		GameTestWatchdog.Overdue<String> running = new GameTestWatchdog.Overdue<>(now::get, 10 * S);
		java.util.function.ToLongFunction<String> limit = name -> name.equals("short") ? 3 : 5;
		for (int tick = 1; tick <= 3; tick++) {
			helper.assertTrue(running.tick(List.of("short", "long"), limit).isEmpty(), "nothing is overdue at its limit (tick " + tick + ")");
		}
		helper.assertTrue(running.tick(List.of("short", "long"), limit).isEmpty(), "past its ticks but not yet its minimum time, the short test isn't overdue");
		running.tick(List.of(), limit); // both leave the batch: forgotten
		for (int tick = 1; tick <= 3; tick++) {
			now.addAndGet(5 * S);
			helper.assertTrue(running.tick(List.of("short", "long"), limit).isEmpty(), "nothing is overdue at its limit (tick " + tick + ")");
		}
		now.addAndGet(5 * S);
		helper.assertTrue(running.tick(List.of("short", "long"), limit).equals(List.of("short")), "the short test is overdue one tick past its limit, 20 s in");
		helper.assertTrue(running.ticks("short") == 4, "ticks counted: " + running.ticks("short"));
		helper.assertTrue(running.tick(List.of("short", "long"), limit).isEmpty(), "the long test isn't overdue yet, and the short one is reported once");
		helper.assertTrue(running.tick(List.of("long"), limit).equals(List.of("long")), "the long test is overdue after 6 ticks");
		helper.assertTrue(running.ticks("short") == 0, "a test no longer running is forgotten");
		helper.assertTrue(running.tick(List.of("short"), limit).isEmpty(), "a new run of the short test counts from 1 again");
		helper.succeed();
	}

	/**
	 * The report on an overdue test, made here on this running test: its name, why the timeout can't have fired, every
	 * chunk of its area with its entity-ticking state, the chunk system and the clock; and the limit is the test's
	 * timeout plus its setup ticks plus the grace.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void watchdogExplainsAnOverdueTest(GameTestHelper helper) throws ReflectiveOperationException {
		java.lang.reflect.Field field = GameTestHelper.class.getDeclaredField("testInfo");
		field.setAccessible(true);
		net.minecraft.gametest.framework.GameTestInfo info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
		helper.assertTrue(GameTestWatchdog.limit(info) == info.getTimeoutTicks() + info.getTestFunction().setupTicks() + GameTestWatchdog.GRACE_TICKS,
			"the limit: " + GameTestWatchdog.limit(info) + " for a timeout of " + info.getTimeoutTicks());
		String report = GameTestWatchdog.stuckReport(helper.getLevel().getServer(), info, 5000);
		String first = report.lines().findFirst().orElse("");
		helper.assertTrue(first.startsWith("GameTest watchdog: " + info.getTestName() + " still running after 5000 server ticks, its timeout of "
			+ info.getTimeoutTicks() + " never fired (it started, but its game-time clock is at "), "the headline: " + first);
		helper.assertTrue(report.contains("  test: started=true, chunksLoaded=true, placedStructure=true"), "the test's own state: " + report);
		helper.assertTrue(report.lines().anyMatch(l -> l.startsWith("  chunk ") && l.contains("entity-ticking=true, forced=true, entities loaded=true, holder: ")),
			"the test's chunks: " + report);
		helper.assertTrue(report.contains("  chunk system: ") && report.contains(" to drop, ") && !report.contains("? to drop"), "the chunk system: " + report);
		helper.assertTrue(report.contains("  server: tick ") && report.contains(", game time " + helper.getLevel().getGameTime()), "the clock: " + report);
		helper.assertFalse(report.contains("report cut short"), "the report failed: " + report);
		helper.succeed();
	}
}
