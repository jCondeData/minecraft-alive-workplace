package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;

/**
 * QA (2026-11-03, tests/builder-clear-1103b), bug candidates found next to {@link QaBuilderRestartClear1103bGameTests}.
 * Their first, a CLEAR spot refilled every tick (B868), went in with that fix as
 * {@link B868GameTests#aSpotRefilledEveryTickNoLongerKeepsTheBuilderInClear}.
 * <p>
 * B869: sand that falls into the hut's room mid-STRUCTURE (a prop under a sand pile broken) was left standing in the
 * finished room, the hut called done with sand where the drawing has air. Now, once LANDSCAPE has run out, the build goes
 * back over CLEAR in a dig-out ({@link BuildSite#digOut}) that digs only such blocks, then finishes. The QA test is
 * renamed to its fixed meaning; a save and load in the dig-out and a spot refilled for ever in it are added.
 */
public class QaBuilderRestartClear1103bBugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** A spot of the room, air in the drawing, over the floor spot (8, 2, 8). */
	private static final BlockPos ROOM = new BlockPos(8, 3, 8);

	/**
	 * The same pile as QaBuilderRestartClear1103bGameTests.sandFallingInMidStructureLeavesTheHutAsDrawn (sand three high on
	 * dirt props over the west wall and the room, the props broken mid-STRUCTURE). Expected: no sand left in the finished
	 * hut's box (where the drawing has air too), the hut as drawn, every material kept.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bBugSandRoom"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bBugSandRoom")
	public void sandFallenIntoTheRoomMidStructureIsDugOutOfTheFinishedHut(GameTestHelper helper) {
		sandRoom(helper, false);
	}

	/**
	 * B869 with a save and load in the middle of the dig-out (back in CLEAR after LANDSCAPE): the dig-out goes on after the
	 * load (only the fallen sand dug, nothing of the built hut), and the hut finishes with no sand in it.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bBugSandRoomReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bBugSandRoomReload")
	public void sandFallenIntoTheRoomIsDugOutThroughAReload(GameTestHelper helper) {
		sandRoom(helper, true);
	}

	/**
	 * Sand put in the room mid-STRUCTURE and put back each time it is dug in the dig-out, for ever: the site still
	 * finishes (the spot given up as a refilled CLEAR spot is, B868, and counted in skipped), nothing of the hut undone.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bBugSandRoomRefill"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bBugSandRoomRefill")
	public void sandRefilledInTheRoomForEverStillLetsTheHutFinish(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		int[] atStart = {-1};
		long[] put = {-1};
		int[] refills = {0};
		BuildSite[] last = {null}; // the site as it was finished (it is gone from the manager then)
		int[] passes = {0};
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (s == null) {
				return;
			}
			last[0] = s;
			passes[0] = Math.max(passes[0], s.clearPasses());
			if (put[0] < 0 && s.stage() == BuildPlan.Stage.STRUCTURE) {
				int unbuilt = r.plan().unfinished(r.level()).size();
				if (atStart[0] < 0) {
					atStart[0] = unbuilt;
				}
				if (atStart[0] - unbuilt >= 30) { // the floor down and the walls begun
					helper.setBlock(ROOM, Blocks.SAND);
					put[0] = helper.getTick();
				}
			}
			if (s.digOut() && helper.getBlockState(ROOM).isAir()) {
				helper.setBlock(ROOM, Blocks.SAND);
				refills[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(put[0] >= 0, "setup: no sand put in the room mid-STRUCTURE");
			helper.assertTrue(r.site() == null, "still building after " + refills[0] + " refills of the room's sand: "
				+ QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(refills[0] > 0, "the sand in the room was never dug in a dig-out");
			helper.assertTrue(last[0].skipped() >= 1, "the room's sand, refilled for ever, wasn't counted in skipped (" + last[0].skipped() + ")");
			helper.assertTrue(passes[0] <= 16, "more than CLEAR's 16 passes: " + passes[0]);
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			QaBuilderRestartClear1103bGameTests.assertBlocksAsDrawn(helper, r);
			LOGGER.info("qa restart1103b bug sand room refill: finished after {} refills, {} passes, skipped {}", refills[0], passes[0], last[0].skipped());
		});
	}

	private static void sandRoom(GameTestHelper helper, boolean reloadInDigOut) {
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
		boolean[] digOut = {false, false}; // seen, reloaded in it
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (s != null && s.digOut()) {
				helper.assertTrue(s.stage() == BuildPlan.Stage.CLEAR, "a dig-out outside CLEAR: " + s.stage());
				digOut[0] = true;
				if (reloadInDigOut && !digOut[1]) {
					digOut[1] = true;
					r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
					helper.assertTrue(r.site().digOut() && r.site().stage() == BuildPlan.Stage.CLEAR, "the dig-out wasn't kept through a save and load");
					return;
				}
			}
			if (droppedAt[0] >= 0 || s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int unbuilt = r.plan().unfinished(r.level()).size();
			if (atStart[0] < 0) {
				atStart[0] = unbuilt;
			}
			if (atStart[0] - unbuilt >= 30) {
				for (BlockPos p : props) {
					helper.setBlock(p, Blocks.AIR);
				}
				droppedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(droppedAt[0] >= 0, "setup: the props weren't broken mid-STRUCTURE");
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(digOut[0], "the hut was finished without a dig-out of the fallen sand");
			helper.assertTrue(!reloadInDigOut || digOut[1], "setup: no save and load in the dig-out");
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, st -> st.getBlock() instanceof FallingBlock);
			QaBuilderRestartClear1103bGameTests.assertBlocksAsDrawn(helper, r);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}
}
