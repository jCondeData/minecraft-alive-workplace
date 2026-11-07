package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;

/**
 * QA (2026-11-03, verifying B845 and the CLEAR passes of B837), from the spec, not the code.
 * <ul>
 * <li>B845 (ROADMAP: "whenever a Builder's work restarts (he wakes, a reload, re-hired) ... Expected: a restart starts the
 * wait at the rule's delay (or keeps the remaining one), never a free block"; builder.md "Speed": "A restart of his work
 * never gives a free block: waking or a new stint keeps the wait in hand, and a fresh start (a reload, re-hired) begins a
 * whole wait at the rule" and "A lowered one cuts the wait in hand at once to the new delay ... (B831)"). Cases
 * BuildDelayChangeGameTests leaves out: the same villager re-hired at his bench, a different villager taking the site
 * over, the dusk-to-morning restart, a builder hired fresh under a huge rule, the rule lowered to the default after a
 * same-brain restart, and the default delay through restarts (no stall).</li>
 * <li>B837 (builder.md "Stages": "at the end of CLEAR the builder waits for what is still coming down, then goes back over
 * the list and digs out what fell in, up to 16 times a build (saved as clear_passes)"; CLEAR passes that run out count
 * what is left in skipped, like B508's "counted in skipped and left for the repair round"): sand put back every tick in
 * the box never holds CLEAR forever, at most 16 passes, the leftover counted, and the count survives a save and load.</li>
 * <li>Sand falling into the box after CLEAR (mid-STRUCTURE). builder.md promises a dig-out only at the end of CLEAR; what
 * it promises later is "A block ... where the drawing has another is taken out ... and the drawn block laid" and the
 * build "as drawn". The test checks that; sand left in the room's air is only logged (no promise covers it).</li>
 * </ul>
 * Helpers: {@link QaBuilderConfig1102GameTests} (the test hut in build_area, the delay tests) and
 * {@link QaBuilderTerrain1102GameTests} (the test hut in huge_area from a chest and barrels).
 */
public class QaBuilderRestartClear1103bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int HUGE_DELAY = 30_000_000;

	// ===================================================================================================== B845

	/** A tick list of every block the hut gained (its unfinished count went down), while a site is live. */
	private static List<Integer> watch(GameTestHelper helper, QaBuilderConfig1102GameTests.Run r) {
		List<Integer> ticks = new ArrayList<>();
		int[] last = {r.plan().unfinished(r.level()).size()};
		helper.onEachTick(() -> {
			if (r.live() == null) {
				return;
			}
			int now = r.plan().unfinished(r.level()).size();
			if (now < last[0]) {
				ticks.add((int) helper.getTick());
			}
			last[0] = now;
		});
		return ticks;
	}

	private static void setDelay(ServerLevel level, int delay) {
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
	}

	private static long between(List<Integer> ticks, int from, int to) {
		return ticks.stream().filter(t -> t > from && t <= to).count();
	}

	private static QaBuilderConfig1102GameTests.Run hut(GameTestHelper helper, int delay) {
		QaBuilderConfig1102GameTests.rules(helper, false, delay);
		return QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH, QaBuilderConfig1102GameTests.CHEST,
			QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
	}

	/**
	 * The shared shape of the huge-delay restart tests: building at 4, raised to 30,000,000 once 3 blocks are down; once
	 * the block already due has come (or 400 ticks), {@code restart} runs; then {@code quiet} ticks with no block, then
	 * the rule lowered to {@code lowerTo}: at least 3 blocks within {@code lowWindow} ticks (he really is at work, and the
	 * cut works after the restart).
	 */
	private static void hugeRestart(GameTestHelper helper, String what, java.util.function.Consumer<QaBuilderConfig1102GameTests.Run> restart,
		int quiet, int lowerTo, int lowWindow) {
		QaBuilderConfig1102GameTests.Run r = hut(helper, 4);
		ServerLevel level = r.level();
		List<Integer> ticks = watch(helper, r);
		int[] raisedAt = {-1};
		int[] restartedAt = {-1};
		int[] loweredAt = {-1};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			if (raisedAt[0] < 0 && ticks.size() >= 3) {
				setDelay(level, HUGE_DELAY);
				raisedAt[0] = t;
			} else if (raisedAt[0] >= 0 && restartedAt[0] < 0 && t >= raisedAt[0] + 40 && (between(ticks, raisedAt[0], t) >= 1 || t >= raisedAt[0] + 400)) {
				helper.assertTrue(r.live() != null, "setup: the hut was finished before the restart: " + ticks);
				restart.accept(r);
				restartedAt[0] = t;
				LOGGER.info("qa restart1103b {}: restarted at {} under {}, {}", what, t, HUGE_DELAY, QaBuilderConfig1102GameTests.where(helper, r));
			} else if (restartedAt[0] >= 0 && loweredAt[0] < 0 && t >= restartedAt[0] + quiet) {
				long free = between(ticks, restartedAt[0], t);
				helper.assertTrue(free == 0, free + " block(s) in the " + quiet + " ticks after " + what + " at " + restartedAt[0]
					+ " under workplaceBuildDelay 30,000,000 (blocks at " + ticks + "): a restart gave a free block");
				setDelay(level, lowerTo);
				loweredAt[0] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt[0] >= 0, "fewer than 3 blocks at delay 4 in " + helper.getTick() + " ticks: " + ticks + "; "
				+ QaBuilderConfig1102GameTests.where(helper, r));
			helper.assertTrue(loweredAt[0] >= 0, "waiting for the restart and the quiet ticks");
			long due = between(ticks, raisedAt[0], restartedAt[0]);
			helper.assertTrue(due <= 1, due + " blocks after the rule went up to 30,000,000 at " + raisedAt[0] + " (blocks at " + ticks + ")");
			long after = between(ticks, loweredAt[0], (int) helper.getTick());
			helper.assertTrue(after >= 3 || r.live() == null && after >= 1, "lowered to " + lowerTo + " at " + loweredAt[0] + " after " + what
				+ ", only " + after + " block(s) so far (blocks at " + ticks + "): " + QaBuilderConfig1102GameTests.where(helper, r));
			LOGGER.info("qa restart1103b {}: {} blocks within {} ticks of lowering to {}", what, after, helper.getTick() - loweredAt[0], lowerTo);
		});
		helper.onEachTick(() -> {
			if (loweredAt[0] >= 0 && helper.getTick() > loweredAt[0] + lowWindow) {
				helper.fail("lowered to " + lowerTo + " at " + loweredAt[0] + " after " + what + ", but fewer than 3 blocks in " + lowWindow
					+ " ticks (blocks at " + ticks + "): the old huge wait was kept; " + QaBuilderConfig1102GameTests.where(helper, r));
			}
		});
	}

	/**
	 * B845 "re-hired": the same villager is hired again at his bench (a fresh brain) under 30,000,000: no block in the
	 * next 300 ticks; lowered to 4, he builds again within 200 ticks.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaRestart1103bRehired"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRestart1103bRehired")
	public void aBuilderReHiredAtHisBenchUnderAHugeDelayGivesNoFreeBlock(GameTestHelper helper) {
		hugeRestart(helper, "re-hired at his bench",
			r -> Builders.employ(r.level(), r.villager(), helper.absolutePos(QaBuilderConfig1102GameTests.BENCH)), 300, 4, 200);
	}

	/**
	 * B845 "re-hired" by another: the builder leaves (what he carried put in the chest, then discarded), a new villager is hired at the same bench and takes the
	 * site over, under 30,000,000: no block for 300 ticks; lowered to 4, the new one builds within 200 ticks.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaRestart1103bTakeOver"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRestart1103bTakeOver")
	public void aNewBuilderTakingTheSiteUnderAHugeDelayGivesNoFreeBlock(GameTestHelper helper) {
		hugeRestart(helper, "a new builder took the site", r -> {
			BuildSite site = r.live();
			// what he carried goes back into the chest at the bench (as a player would gather it), then he leaves
			net.minecraft.world.Container chest = helper.getBlockEntity(QaBuilderConfig1102GameTests.CHEST);
			for (net.minecraft.world.item.ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).takeAll()) {
				net.minecraft.world.item.ItemStack rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, chest, stack, null);
				helper.assertTrue(rest.isEmpty(), "setup: the chest had no room for the old builder's " + rest);
			}
			r.villager().discard();
			site.setBuilder(null);
			Villager next = helper.spawn(EntityType.VILLAGER, QaBuilderConfig1102GameTests.BENCH.east());
			Builders.employ(r.level(), next, helper.absolutePos(QaBuilderConfig1102GameTests.BENCH));
			Builders.takeOver(r.level(), next, site);
			r.builder()[0] = next;
			helper.assertTrue(Builders.activeSite(r.level(), next) == site, "setup: the new builder doesn't hold the site");
		}, 300, 4, 200);
	}

	/**
	 * B845 "he wakes": under 30,000,000 the evening comes (clock held at 13000 for 400 ticks: his schedule leaves WORK),
	 * then the morning (1000): he is back at work (WORK active) and places no block in 400 morning ticks. Then lowered
	 * to the default 8 he builds again within 300 ticks.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaRestart1103bMorning"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRestart1103bMorning")
	public void theMorningRestartUnderAHugeDelayGivesNoFreeBlock(GameTestHelper helper) {
		int[] clock = {2000};
		long[] eveningAt = {-1};
		boolean[] restedAtNight = {false};
		boolean[] workedInMorning = {false};
		QaBuilderConfig1102GameTests.Run[] run = new QaBuilderConfig1102GameTests.Run[1];
		helper.onEachTick(() -> {
			if (eveningAt[0] >= 0) {
				long t = helper.getTick() - eveningAt[0];
				clock[0] = t < 400 ? 13000 : 1000 + (int) (t - 400);
				Villager v = run[0].villager();
				if (t >= 200 && t < 400 && !v.getBrain().isActive(Activity.WORK)) {
					restedAtNight[0] = true;
				}
				if (t >= 450 && v.getBrain().isActive(Activity.WORK)) {
					workedInMorning[0] = true;
				}
			}
			helper.setDayTime(clock[0]);
		});
		hugeRestart(helper, "the evening and the morning", r -> {
			run[0] = r;
			eveningAt[0] = helper.getTick();
		}, 800, 8, 300);
		helper.onEachTick(() -> {
			if (eveningAt[0] >= 0 && helper.getTick() == eveningAt[0] + 800) {
				helper.assertTrue(restedAtNight[0], "setup: his schedule never left WORK at 13000, so no restart happened");
				helper.assertTrue(workedInMorning[0], "setup: his schedule wasn't back at WORK in the morning");
			}
		});
	}

	/**
	 * B845 with B831: his work stops and starts on the same brain (as at dusk and dawn) under 30,000,000 (he keeps the
	 * huge wait in hand: no block for 200 ticks); the rule lowered to its default, 8, cuts that wait: blocks again within
	 * 200 ticks, not after the old wait.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaRestart1103bStopDefault"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRestart1103bStopDefault")
	public void aSameBrainRestartThenTheDefaultRuleBuildsAgain(GameTestHelper helper) {
		hugeRestart(helper, "his work stopped and started", r -> r.villager().getBrain().stopAll(r.level(), r.villager()), 200, 8, 200);
	}

	/**
	 * B845 "a fresh start begins a whole wait at the rule": a builder hired with the rule already at 30,000,000 places no
	 * block of the hut in 600 ticks; lowered to 4 he builds within 300 ticks.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaRestart1103bFresh"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaRestart1103bFresh")
	public void aBuilderHiredUnderAHugeDelayPlacesNothing(GameTestHelper helper) {
		QaBuilderConfig1102GameTests.Run r = hut(helper, HUGE_DELAY);
		List<Integer> ticks = watch(helper, r);
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(ticks.isEmpty(), ticks.size() + " block(s) of the hut placed by a builder hired under workplaceBuildDelay 30,000,000 (at "
				+ ticks + "): a fresh start gave a free block; " + QaBuilderConfig1102GameTests.where(helper, r));
			setDelay(r.level(), 4);
		});
		helper.runAtTickTime(900, () -> {
			long after = between(ticks, 600, 900);
			helper.assertTrue(after >= 3, "lowered to 4 at 600, but only " + after + " block(s) by 900: " + QaBuilderConfig1102GameTests.where(helper, r));
			helper.succeed();
		});
	}

	/**
	 * The boundary: at the default delay (8) restarts cost no pace. Mid-STRUCTURE 150 ticks are counted as they are; then
	 * his work is stopped and started on the same brain, and later the world is saved and loaded (a fresh brain). Each
	 * restart is followed by a block within 100 ticks, and the 150 ticks after each give at least half the control's
	 * blocks.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaRestart1103bDefault"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRestart1103bDefault")
	public void restartsAtTheDefaultDelayKeepTheNormalPace(GameTestHelper helper) {
		QaBuilderConfig1102GameTests.Run r = hut(helper, 8);
		ServerLevel level = r.level();
		List<Integer> ticks = watch(helper, r);
		int[] from = {-1};
		int[] stopAt = {-1};
		int[] reloadAt = {-1};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			if (from[0] < 0 && ticks.size() >= 3) {
				from[0] = t;
			} else if (from[0] >= 0 && stopAt[0] < 0 && t >= from[0] + 150) {
				r.villager().getBrain().stopAll(level, r.villager());
				stopAt[0] = t;
			} else if (stopAt[0] >= 0 && reloadAt[0] < 0 && t >= stopAt[0] + 150) {
				QaBuilderConfig1102GameTests.restart(helper, r);
				reloadAt[0] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadAt[0] >= 0 && helper.getTick() >= reloadAt[0] + 150, "waiting for both restarts and 150 ticks after");
			long control = between(ticks, from[0], stopAt[0]);
			long afterStop = between(ticks, stopAt[0], reloadAt[0]);
			long afterReload = between(ticks, reloadAt[0], reloadAt[0] + 150);
			LOGGER.info("qa restart1103b default: {} blocks in 150 ticks as they are, {} after a stop, {} after a reload ({})", control, afterStop,
				afterReload, ticks);
			helper.assertTrue(control >= 4, "setup: only " + control + " blocks in 150 ticks at the default delay: " + ticks);
			helper.assertTrue(afterStop * 2 >= control, "after his work stopped and started at the default delay only " + afterStop + " blocks in 150 ticks against "
				+ control + " before: a stall (blocks at " + ticks + ")");
			helper.assertTrue(afterReload * 2 >= control || r.live() == null && afterReload >= 1, "after a reload at the default delay only " + afterReload
				+ " blocks in 150 ticks against " + control + " before: a stall (blocks at " + ticks + ")");
			int firstAfterStop = ticks.stream().filter(t -> t > stopAt[0]).findFirst().orElse(Integer.MAX_VALUE);
			int firstAfterReload = ticks.stream().filter(t -> t > reloadAt[0]).findFirst().orElse(Integer.MAX_VALUE);
			helper.assertTrue(firstAfterStop - stopAt[0] <= 100, "first block " + (firstAfterStop - stopAt[0]) + " ticks after a stop at the default delay");
			helper.assertTrue(firstAfterReload - reloadAt[0] <= 100, "first block " + (firstAfterReload - reloadAt[0]) + " ticks after a reload at the default delay");
		});
	}

	// ===================================================================================================== CLEAR passes

	/** Two of the hut's floor spots, side by side in one row: sand there stands on the area's floor and doesn't fall. */
	static final BlockPos SPOT_A = new BlockPos(8, 2, 8);
	static final BlockPos SPOT_B = new BlockPos(7, 2, 8);

	/**
	 * Sand stands on the hut's floor spot A at the start. Each time the builder digs sand out of A or B during CLEAR,
	 * the same tick sand is put on the other spot, as if something kept dropping it back into the box. CLEAR goes back
	 * over its list at most 16 times (builder.md), then moves on with the sand still there counted in skipped (never
	 * claimed done, never stuck in CLEAR); the hut then finishes as drawn, its materials exact. With {@code reloadAtPass}
	 * &gt; 0 the world is saved and loaded once that many passes are done: the count is kept (no fresh 16).
	 */
	private static void putBack(GameTestHelper helper, int reloadAtPass) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		helper.setBlock(SPOT_A, Blocks.SAND);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		int[] most = {0};
		int[] putBacks = {0};
		long[] leftAt = {-1};
		int[] skippedAtExit = {-1};
		int[] passesAtExit = {-1};
		int[] reloaded = {-1};
		int[] afterReload = {-1};
		boolean[] was = {true, false}; // sand on A, on B last tick
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (s == null || leftAt[0] >= 0) {
				return;
			}
			most[0] = Math.max(most[0], s.clearPasses());
			if (s.stage() == BuildPlan.Stage.CLEAR) {
				if (reloadAtPass > 0 && reloaded[0] < 0 && s.clearPasses() >= reloadAtPass) {
					reloaded[0] = s.clearPasses();
					r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
					afterReload[0] = r.site().clearPasses();
					LOGGER.info("qa restart1103b put back: reloaded at pass {}, {} after", reloaded[0], afterReload[0]);
				}
				boolean a = helper.getBlockState(SPOT_A).is(Blocks.SAND);
				boolean b = helper.getBlockState(SPOT_B).is(Blocks.SAND);
				if (was[0] && !a && helper.getBlockState(SPOT_B).isAir()) {
					helper.setBlock(SPOT_B, Blocks.SAND);
					b = true;
					putBacks[0]++;
				} else if (was[1] && !b && helper.getBlockState(SPOT_A).isAir()) {
					helper.setBlock(SPOT_A, Blocks.SAND);
					a = true;
					putBacks[0]++;
				}
				was[0] = a;
				was[1] = b;
				helper.assertTrue(helper.getTick() < 9000, "still in CLEAR at tick " + helper.getTick() + " after " + s.clearPasses() + " passes and "
					+ putBacks[0] + " put-backs: " + QaBuilderTerrain1102GameTests.state(helper, r));
			} else {
				leftAt[0] = helper.getTick();
				skippedAtExit[0] = s.skipped();
				passesAtExit[0] = s.clearPasses();
				LOGGER.info("qa restart1103b put back: CLEAR left at {} for {} after {} passes, {} put-backs, skipped {}", leftAt[0], s.stage(),
					passesAtExit[0], putBacks[0], skippedAtExit[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(leftAt[0] >= 0, "CLEAR not left yet (" + most[0] + " passes so far)");
			helper.assertTrue(most[0] <= 16, "CLEAR went back over its list " + most[0] + " times; builder.md says up to 16 times a build");
			helper.assertTrue(putBacks[0] >= 16, "setup: the sand was dug and put back only " + putBacks[0] + " time(s)");
			helper.assertTrue(passesAtExit[0] == 16, "sand was put back each time it was dug, but CLEAR went back " + passesAtExit[0]
				+ " times, not the 16 builder.md gives");
			helper.assertTrue(skippedAtExit[0] >= 1, "CLEAR ran out of passes with sand standing in the box, but skipped is " + skippedAtExit[0]
				+ ": the leftover was claimed done");
			if (reloadAtPass > 0) {
				helper.assertTrue(reloaded[0] >= 0, "setup: never reloaded");
				helper.assertTrue(afterReload[0] == reloaded[0], "a save and load at pass " + reloaded[0] + " came back with " + afterReload[0]
					+ " passes: clear_passes not kept");
			}
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	/** Sand put back each time it is dug during CLEAR: at most (and here exactly) 16 passes, skipped counts it, the hut finishes. */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bPutBack"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bPutBack")
	public void sandPutBackEachTimeItIsDugEndsClearAfter16Passes(GameTestHelper helper) {
		putBack(helper, 0);
	}

	/** The same, saved and loaded after 6 passes: the pass count comes back, CLEAR still ends at 16. */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bPutBackReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bPutBackReload")
	public void sandPutBackThroughAReloadMidClearStillEndsAt16Passes(GameTestHelper helper) {
		putBack(helper, 6);
	}

	// ===================================================================================================== sand after CLEAR

	/**
	 * Sand stands three high on dirt props just over the box (y 6, outside it, so CLEAR leaves them): one column over the
	 * west wall (6, z 8), one over the room (8, z 8). Mid-STRUCTURE a player breaks both props and the sand comes down
	 * into the box: onto the wall going up and onto the room's floor. The hut ends as drawn (sand where a wall or roof
	 * block goes is taken out and the drawn block laid), nothing still falling, every material exact. Sand left in the
	 * room's air is logged: builder.md makes no promise for sand that falls in after CLEAR.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bSandAfter"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bSandAfter")
	public void sandFallingInMidStructureLeavesTheHutAsDrawn(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		List<BlockPos> props = List.of(new BlockPos(6, 6, 8), new BlockPos(8, 6, 8));
		for (BlockPos p : props) {
			helper.setBlock(p, Blocks.DIRT);
			for (int y = 7; y <= 9; y++) {
				helper.setBlock(p.atY(y), Blocks.SAND);
			}
		}
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		int[] atStart = {-1};
		long[] droppedAt = {-1};
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (droppedAt[0] >= 0 || s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int unbuilt = r.plan().unfinished(r.level()).size();
			if (atStart[0] < 0) {
				atStart[0] = unbuilt;
			}
			if (atStart[0] - unbuilt >= 30) { // the floor (25) down and the walls begun
				for (BlockPos p : props) {
					helper.setBlock(p, Blocks.AIR);
				}
				droppedAt[0] = helper.getTick();
				LOGGER.info("qa restart1103b sand after: the props broken mid-STRUCTURE at {}, {}", droppedAt[0], QaBuilderTerrain1102GameTests.state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(droppedAt[0] >= 0, "setup: the props weren't broken mid-STRUCTURE");
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			assertBlocksAsDrawn(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingFalling(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			int inBox = QaBuilderTerrain1102GameTests.count(r, st -> st.getBlock() instanceof FallingBlock);
			LOGGER.info("qa restart1103b sand after: finished, {} sand block(s) left in the hut's box (no promise covers these)", inBox);
		});
	}

	/** Every structure and decoration block of the hut stands as drawn (the room's air aside: no promise covers it). */
	static void assertBlocksAsDrawn(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		List<String> wrong = new ArrayList<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : r.plan().steps(stage)) {
				var world = r.level().getBlockState(step.pos());
				if (!MaterialRules.matches(world, step.state())) {
					wrong.add(helper.relativePos(step.pos()).toShortString() + "=" + world.getBlock().getDescriptionId());
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wall, roof, floor or decoration block(s) of the hut not as drawn: " + wrong);
	}
}
