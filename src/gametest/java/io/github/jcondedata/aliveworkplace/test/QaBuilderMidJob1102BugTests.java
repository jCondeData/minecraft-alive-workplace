package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-02, lane-b-1102-1838's tester): a failing test of a Builder whose world changes under him, beside a
 * neighbor's build (QaBuilderMidJob1102GameTests' layout). Not registered: it fails on main until the mod is fixed.
 * <p>
 * Spec: docs/wiki/builder.md, LANDSCAPE "digs away ground above the floor" round the build, and "nothing a build leaves
 * is broken or changed by the game itself once placed". Hut A's levelling ring (workplaceLevelGround, 2 blocks) reaches
 * into the neighbor's footprint and digs out the stone pad its floor stands on, leaving the neighbor's floor floating.
 */
public class QaBuilderMidJob1102BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int EXTRA = QaBuilderTerrain1102GameTests.EXTRA;

	// ------------------------------------------------------------------------------------------- two sites side by side

	/**
	 * Two huts whose boxes touch (hut A at x 6-10, hut B at x 11-15), each with its own builder, bench and chests, built
	 * at once. Hut B stands on a one-high stone pad laid under its footprint, its floor on the pad. Neither build undoes
	 * the other's ("nothing a build leaves is broken"): both end as drawn, the pad under hut B's floor is all still there
	 * (hut A's levelling never digs the ground out from under its neighbor), and the planks, door and torch of both add up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMidJobNeighbors"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMidJobNeighbors")
	public void twoTouchingHutsOneOnAPadNeitherUndoesTheOther(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		for (int x = 11; x <= 15; x++) {
			for (int z = 6; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
			}
		}
		QaBuilderTerrain1102GameTests.Run a = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.Run b = QaBuilderMidJob1102GameTests.startAt(helper, new BlockPos(24, 2, 8), new BlockPos(11, 3, 6));
		helper.assertTrue(!a.plan().bounds().intersects(b.plan().bounds()), "setup: the huts' boxes overlap");
		helper.assertTrue(a.plan().bounds().maxX() + 1 == b.plan().bounds().minX(), "setup: the huts' boxes don't touch: " + a.plan().bounds() + " " + b.plan().bounds());
		QaBuilderTerrain1102GameTests.watchDeath(helper, a);
		QaBuilderTerrain1102GameTests.watchDeath(helper, b);
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, a);
			QaBuilderTerrain1102GameTests.assertFinished(helper, b);
			List<String> dug = new ArrayList<>();
			for (int x = 11; x <= 15; x++) {
				for (int z = 6; z <= 10; z++) {
					BlockState s = helper.getBlockState(new BlockPos(x, 2, z));
					if (s.isAir() || s.canBeReplaced()) {
						dug.add("(" + x + ",2," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
			helper.assertTrue(dug.isEmpty(), dug.size() + " block(s) of the pad under hut B's finished floor dug out: " + dug);
			// cobblestone left out: stone either build digs on its ring drops as cobblestone
			Map<Item, Integer> off = new HashMap<>();
			for (Item item : a.plan().materials().keySet()) {
				if (item == Items.COBBLESTONE) {
					continue;
				}
				int got = QaBuilderTerrain1102GameTests.loose(helper, a, item) + QaBuilderMidJob1102GameTests.supplies(helper, b, item);
				if (got != 2 * EXTRA) {
					off.put(item, got);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials made or lost by the two huts: left loose " + off + ", wanted " + 2 * EXTRA + " of each");
		});
	}

	// ------------------------------------------------------------------------------------------------------- night

	/**
	 * ModVillagers.BUILDER_SCHEDULE: builders "work from early morning to dusk, then go to bed like everyone else". Mid-
	 * STRUCTURE midnight comes (the clock held at 18000, a bed by the bench): 200 ticks on (time enough to set down the
	 * block in hand and walk six blocks to bed) the builder must have stopped building. On main he is still placing
	 * blocks in the REST activity (QaBuilderMidJob1102GameTests' night, with a 200-tick grace in place of 1300).
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaMidJobNightBug"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaMidJobNightBug")
	public void aBuilderStopsBuildingSoonAfterMidnightComes(GameTestHelper helper) {
		QaBuilderMidJob1102GameTests.night(helper, 200);
	}
}
