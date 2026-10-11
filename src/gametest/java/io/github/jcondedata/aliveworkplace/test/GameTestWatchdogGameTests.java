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
}
