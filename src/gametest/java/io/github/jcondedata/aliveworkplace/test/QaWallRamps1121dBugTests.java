package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/** B1419 residue (lane D, 2026-11-21): a garden wall joined to the village wall's corner, on the ramp wall's line, with no gap. */
public class QaWallRamps1121dBugTests implements FabricGameTest {
	/**
	 * B1419's own garden wall (cobblestone, two high, x 8, z 22 to 26, outside the yard) but built on to the yard's
	 * south-west corner (z 20 and 21 filled too, no gap): the husk in a player's cell at (10, 2, 24) is outside the yard,
	 * beside the player's wall, not the village's; by B1419's "Expected" it must not be counted over the wall.
	 */
	//$ gametest_ticks_batch "aliveworkplace_test:huge_area" '400' '"qaWallRamps1121dJoinedGarden"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 400, batch = "qaWallRamps1121dJoinedGarden")
	public void qaAHuskOutsideBesideAGardenWallJoinedToTheVillageWallIsNotCountedOver(GameTestHelper helper) {
		QaWallRamps1121dGameTests.joinedGarden(helper);
	}
}
