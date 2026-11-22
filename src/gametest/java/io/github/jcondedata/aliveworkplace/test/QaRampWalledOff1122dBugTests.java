package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1122-1009), over-triggers of the B1445/B1479 fix (SandRamps.walledOff), from the spec (32.9): a climber
 * that can walk in (an open gate, a gap) does not ramp, and ramps go against the walls on the way in. Setup as
 * QaRampWalledOff1122dGameTests: the hall at (14, 2, 14), a climbing husk with no target three blocks north of where
 * its path ends, (14, 2, 9), exactly five from the hall along the axes.
 */
public class QaRampWalledOff1122dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * The yard's north wall (z 10, four high) has a doorway two wide, open full height, at x 16 and 17, two blocks east of
	 * where the husk's path ends at the wall's foot: the husk can walk in, so no sand is piled.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWalledOff1122dDoorway"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalledOff1122dDoorway")
	public void qaAClimberBesideAnOpenDoorwayDoesNotRamp(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		QaRampWalledOff1122dGameTests.walls(helper, QaRampWalledOff1122dGameTests.R, 4, Blocks.STONE_BRICKS);
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(16, y, 10), Blocks.AIR);
			helper.setBlock(new BlockPos(17, y, 10), Blocks.AIR);
		}
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 14.5, 6.5);
		QaRampWalledOff1122dGameTests.expectNoRamp(helper, yard, husk, "doorway open two blocks east");
	}

	/**
	 * No walls at all: the village is open, with one lamp post (an oak fence with glowstone on it, two high) at (14, 2, 11),
	 * on the line from where the husk's path ends to the hall. A lone post is no wall on the way in: no sand is piled.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWalledOff1122dLampPost"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalledOff1122dLampPost")
	public void qaALampPostInAnOpenVillageGetsNoRamp(GameTestHelper helper) {
		QaRampWalledOff1122dGameTests.Yard yard = QaRampWalledOff1122dGameTests.village(helper);
		helper.setBlock(new BlockPos(14, 2, 11), Blocks.OAK_FENCE);
		helper.setBlock(new BlockPos(14, 3, 11), Blocks.GLOWSTONE);
		Mob husk = QaRampWalledOff1122dGameTests.husk(helper, yard, 14.5, 5.5);
		QaRampWalledOff1122dGameTests.expectNoRamp(helper, yard, husk, "lone lamp post");
	}
}
