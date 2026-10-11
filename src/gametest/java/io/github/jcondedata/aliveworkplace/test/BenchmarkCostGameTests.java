package io.github.jcondedata.aliveworkplace.test;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.command.OurTickCost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;

/**
 * ROADMAP 25.3: the benchmark's timer of our code a tick ({@link OurTickCost}). It counts our work once (nested calls
 * by the outermost), never other code's, starts each tick from zero, logs the long ticks and a line a window, and does
 * nothing while no benchmark records. Each test records only within its own call, so nothing else ticks in between.
 */
public class BenchmarkCostGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	/** Not recording: entering, leaving and closing a tick change nothing. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTickTimerDoesNothingWhenOff(GameTestHelper helper) {
		helper.assertTrue(!OurTickCost.recording(), "already recording");
		long ticks = OurTickCost.ticks();
		long worst = OurTickCost.worstNanos();
		OurTickCost.enter(this);
		busy(1_000_000L);
		OurTickCost.exit(this);
		OurTickCost.endTick();
		helper.assertTrue(OurTickCost.ticks() == ticks && OurTickCost.worstNanos() == worst, "counted while off");
		helper.assertTrue(!OurTickCost.recording(), "started recording by itself");
		helper.succeed();
	}

	/** Our nested work counts once, other code's not at all, each tick starts from zero, a long tick is logged. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTickTimerCountsOurWorkOnceAndResetsEachTick(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		OurTickCost.begin(lines::add);
		try {
			Object other = new Object(); // java.lang: not ours
			OurTickCost.enter(this);
			OurTickCost.enter(this); // nested: one count
			busy(2_000_000L);
			OurTickCost.exit(this);
			OurTickCost.exit(this);
			OurTickCost.enter(other);
			busy(60_000_000L);
			OurTickCost.exit(other);
			OurTickCost.endTick();
			long first = OurTickCost.lastTickNanos();
			helper.assertTrue(first >= 2_000_000L && first < 40_000_000L, "first tick " + first + " ns (counted twice, or other code?)");
			OurTickCost.endTick(); // a tick with nothing of ours
			helper.assertTrue(OurTickCost.lastTickNanos() == 0L, "the empty tick kept " + OurTickCost.lastTickNanos() + " ns");
			helper.assertTrue(lines.isEmpty(), "logged without a long tick: " + lines);
			OurTickCost.enter(this);
			busy(55_000_000L);
			OurTickCost.exit(this);
			OurTickCost.endTick();
			helper.assertTrue(OurTickCost.longTicks() == 1 && OurTickCost.ticks() == 3, "long ticks " + OurTickCost.longTicks() + " of " + OurTickCost.ticks());
			helper.assertTrue(lines.size() == 1 && lines.get(0).startsWith("Benchmark: a tick with ")
				&& lines.get(0).contains("BenchmarkCostGameTests"), "long tick line: " + lines);
			for (int i = 3; i < OurTickCost.WINDOW; i++) {
				OurTickCost.endTick();
			}
			helper.assertTrue(lines.size() == 2 && lines.get(1).startsWith("Benchmark: our code a tick over the last " + OurTickCost.WINDOW + " ticks: p50 0.0 ms")
				&& lines.get(1).contains("ticks with over 50 ms of ours so far: 1 of " + OurTickCost.WINDOW), "window line: " + lines);
		} finally {
			OurTickCost.finish();
		}
		helper.assertTrue(!OurTickCost.recording(), "still recording");
		helper.succeed();
	}

	/** A behaviour of ours is timed through the vanilla entry point the brain calls ({@code mixin/BehaviorMixin}). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTickTimerTimesOurBehaviours(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = EntityType.VILLAGER.create(level);
		Behavior<Villager> ours = new Behavior<>(ImmutableMap.of()) {
			@Override
			protected boolean checkExtraStartConditions(ServerLevel l, Villager v) {
				busy(3_000_000L);
				return false;
			}
		};
		ours.tryStart(level, villager, 0L); // not recording: nothing
		List<String> lines = new ArrayList<>();
		OurTickCost.begin(lines::add);
		try {
			ours.tryStart(level, villager, 0L);
			OurTickCost.endTick();
			long tick = OurTickCost.lastTickNanos();
			helper.assertTrue(tick >= 3_000_000L && tick < 45_000_000L, "our behaviour's tryStart: " + tick + " ns");
		} finally {
			OurTickCost.finish();
		}
		helper.assertTrue(lines.size() == 1 && lines.get(0).contains("biggest call: BenchmarkCostGameTests$1 "), "the partial window: " + lines);
		helper.succeed();
	}

	/**
	 * A long tick of ours that a GC pause explains isn't counted against us; one with no stall is, and both lines say
	 * the tick's length, pauses and time off the CPU.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aLongTickWithAGcPauseIsToldApart(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		OurTickCost.begin(lines::add);
		try {
			OurTickCost.enter(this);
			busy(60_000_000L);
			OurTickCost.exit(this);
			OurTickCost.endTick(70_000_000L, 1_000_000_000L, 70_000_000L); // a (made-up) 1 s pause in the tick
			helper.assertTrue(OurTickCost.longTicks() == 1 && OurTickCost.longTicksOnCpu() == 0,
				"the paused tick: long " + OurTickCost.longTicks() + ", on the CPU " + OurTickCost.longTicksOnCpu());
			helper.assertTrue(lines.size() == 1 && lines.get(0).contains("GC pauses 1000.0 ms")
				&& lines.get(0).contains("the server thread off the CPU 0.0 ms") && lines.get(0).endsWith("ours on the CPU 0.0 ms"),
				"the paused tick's line: " + lines);
			OurTickCost.enter(this);
			busy(60_000_000L);
			OurTickCost.exit(this);
			OurTickCost.endTick(60_000_000L, 0L, 10_000_000_000L); // no pause, the thread on the CPU throughout
			helper.assertTrue(OurTickCost.longTicks() == 2 && OurTickCost.longTicksOnCpu() == 1,
				"the plain tick: long " + OurTickCost.longTicks() + ", on the CPU " + OurTickCost.longTicksOnCpu());
			OurTickCost.enter(this);
			busy(60_000_000L);
			OurTickCost.exit(this);
			OurTickCost.endTick(); // figures unknown: all of it counts as ours
			helper.assertTrue(OurTickCost.longTicksOnCpu() == 2 && lines.get(2).contains("GC pauses unknown"), "unknown figures: " + lines);
		} finally {
			OurTickCost.finish();
		}
		helper.assertTrue(lines.get(lines.size() - 1).contains("on the CPU without GC pauses: 2 (worst "), "window line: " + lines);
		helper.succeed();
	}

	/** Builders' block changes per level and tick stop at the budget and start afresh the next tick (25.3). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void buildersBlockChangesStopAtTheTickBudget(GameTestHelper helper) {
		Object level = new Object();
		int taken = 0;
		while (io.github.jcondedata.aliveworkplace.build.BuildBudget.take(level, 100L) && taken < 1000) {
			taken++;
		}
		helper.assertTrue(taken == io.github.jcondedata.aliveworkplace.build.BuildBudget.CHANGES_PER_TICK, "took " + taken + " in one tick");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.BuildBudget.take(level, 101L), "the next tick had no budget");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.BuildBudget.take(new Object(), 100L), "another level shared the budget");
		helper.succeed();
	}

	private static void busy(long nanos) {
		long until = System.nanoTime() + nanos;
		while (System.nanoTime() < until) {
			Thread.onSpinWait();
		}
	}
}
