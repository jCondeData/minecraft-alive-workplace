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
 * QA (2026-11-03), bug candidates found next to {@link QaBuilderRestartClear1103bGameTests}. They fail on today's code and
 * are NOT registered in fabric.mod.json; the builder registers each with its fix.
 * <ul>
 * <li>A spot of the box refilled the tick after the Builder digs it (a sand dropper, a cobblestone generator, a player)
 * keeps him in CLEAR for ever: he digs the same block again and again (4,468 times in 9,000 ticks) and the 16-pass cap
 * of builder.md never comes into play. Expected (CLAUDE.md "Steps that can't be done yet are deferred once, then
 * skipped"; builder.md, B508 "left for the repair round"): the build moves on, the spot counted in skipped.</li>
 * <li>Sand that falls into the hut's room mid-STRUCTURE (a prop under a sand pile broken) is left standing in the finished
 * room: the hut is called done with sand where the drawing has air. builder.md only promises the dig-out at the end of
 * CLEAR (B837), so this is a spec gap to decide.</li>
 * </ul>
 */
public class QaBuilderRestartClear1103bBugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * Sand stands on the hut's floor spot (8, 2, 8) and is put back the tick after the builder digs it, while the site
	 * is in CLEAR. Expected: CLEAR ends within 9,000 ticks (it has 16 passes at most) and the hut finishes.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bBugRefill"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bBugRefill")
	public void aSpotRefilledEveryTickNeverLetsClearEnd(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		BlockPos spot = QaBuilderRestartClear1103bGameTests.SPOT_A;
		helper.setBlock(spot, Blocks.SAND);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		int[] digs = {0};
		long[] leftAt = {-1};
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (s == null || leftAt[0] >= 0) {
				return;
			}
			if (s.stage() != BuildPlan.Stage.CLEAR) {
				leftAt[0] = helper.getTick();
				LOGGER.info("qa restart1103b bug refill: CLEAR left at {} after {} digs, skipped {}", leftAt[0], digs[0], s.skipped());
				return;
			}
			if (helper.getBlockState(spot).isAir()) {
				helper.setBlock(spot, Blocks.SAND);
				digs[0]++;
			}
			helper.assertTrue(helper.getTick() < 9000, "the builder is still in CLEAR at tick " + helper.getTick() + ": he dug the refilled sand at (8, 2, 8) "
				+ digs[0] + " times and never moved on (" + s.clearPasses() + " passes): " + QaBuilderTerrain1102GameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(leftAt[0] >= 0, "CLEAR not left yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	/**
	 * The same pile as QaBuilderRestartClear1103bGameTests.sandFallingInMidStructureLeavesTheHutAsDrawn (sand three high on
	 * dirt props over the west wall and the room, the props broken mid-STRUCTURE). Expected: no sand left in the finished
	 * hut's box (where the drawing has air too).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaRestart1103bBugSandRoom"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaRestart1103bBugSandRoom")
	public void sandFallenIntoTheRoomMidStructureIsLeftInTheFinishedHut(GameTestHelper helper) {
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
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, st -> st.getBlock() instanceof FallingBlock);
		});
	}
}
