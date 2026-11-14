package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNoFluid;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-14, lane-b-1114-1237's tester), failing on main: a hut sunk below the water line ends with its room
 * flooded. docs/wiki/builder.md "Stages": CLEAR takes away what is in the way; the finished build stands as drawn
 * (QaBuilderTerrain1102GameTests: a pond beside the doorway leaves no water in the finished hut). Water standing in the
 * room's air above the ground is never taken away: the walls go up round it, the door shuts it in, and the room keeps
 * its water sources for good.
 */
public class QaBuilderWater1114bBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/**
	 * A stone plateau two high with a lake two deep cut into it (x 6-14, z 7-15, the hut's box and its leveled ring);
	 * the hut is clicked onto the lake's bed (origin y 2), so its doorway (y 3) is under the water line. The finished
	 * hut has no water inside and stands as drawn, the builder alive.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bLake"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bLake")
	public void aHutSunkInALakeTwoDeepEndsDryInside(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 6, 14, 2, 3, 7, 15, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 4, new BlockPos(8, 2, 9));
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			LOGGER.info("qa 1114b lake: the sunk hut is dry and whole at tick {}", helper.getTick());
		});
	}

	/**
	 * A pond two deep exactly the size of the hut's footprint, cut into a stone plateau two high, and the hut clicked
	 * onto the pond's bed: no water outside the footprint at all, so nothing can run back in once the walls stand. The
	 * finished hut has no water inside and stands as drawn.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bPondInFootprint"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bPondInFootprint")
	public void aHutClickedOntoAPondsBedEndsDryInside(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 3, 6, 10, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 4, new BlockPos(6, 2, 6));
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			LOGGER.info("qa 1114b pond in footprint: the hut is dry and whole at tick {}", helper.getTick());
		});
	}
}
