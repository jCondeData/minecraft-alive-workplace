package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug tests (2026-11-15, qa-lane-b-1115-1537), failing until B1294's fix: B1280's fix still flooded a sunk room whose drawn pool sits
 * over a water pocket in the pond's bed shut in by stone, as soon as the pocket also runs under the solid floor beside
 * the pool (two blocks wide is enough). docs/wiki/builder.md "Stages" (B1280): "over a water-filled pocket in the bed,
 * shut in by stone, it ends dry too: the water under the floor counts only when it reaches past the box's footprint or
 * up to its height". Seen: 18 water sources fill the 3x3x2 room and the site counts as done (74 wet spots at the
 * first tick after CLEAR). A pocket straight down under the pool alone (QaBuilderPocketDrain1115gGameTests, four deep)
 * ends dry. Cause (B1294): the spots FOUNDATION fills under the solid floor (the pocket's water there) counted as the
 * box's, so the box's bottom dropped into the pocket and the pocket stood "at the box's height". Helpers are QaBuilderPocketDrain1115gGameTests' (the basin on a pond y 6-8 over a stone bed, the pool at
 * (8, 6, 8)).
 */
public class QaBuilderPocketDrain1115gBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A pocket two blocks wide, one deep: under the pool (8, 5, 8) and under the floor beside it (9, 5, 8), stone round both. */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gWidePocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gWidePocket")
	public void aPoolOverAPocketTwoWideUnderTheFloorEndsDryButThePool(GameTestHelper helper) {
		QaBuilderPocketDrain1115gGameTests.poolOver(helper, "wide", QaBuilderPocketDrain1115gGameTests.put(helper, Blocks.WATER.defaultBlockState(),
			new BlockPos(8, 5, 8), new BlockPos(9, 5, 8)));
	}

	/**
	 * An L-shaped pocket under the pool running out to the footprint's edge column (x 10) and round to its corner column
	 * (x 10, z 10), two deep at the corner: every spot of it under the footprint and shut in by stone, so the room must
	 * end dry but the pool.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gEdgePocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gEdgePocket")
	public void aPoolOverAnLShapedPocketOutToTheFootprintsEdgeEndsDryButThePool(GameTestHelper helper) {
		QaBuilderPocketDrain1115gGameTests.poolOver(helper, "edge", QaBuilderPocketDrain1115gGameTests.put(helper, Blocks.WATER.defaultBlockState(),
			new BlockPos(8, 5, 8), new BlockPos(9, 5, 8), new BlockPos(10, 5, 8), new BlockPos(10, 5, 9), new BlockPos(10, 5, 10),
			new BlockPos(10, 4, 10), new BlockPos(9, 4, 10), new BlockPos(10, 4, 9)));
	}
}
