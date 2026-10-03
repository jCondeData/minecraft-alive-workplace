package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-1003-2233, B23): "FOUNDATION and LANDSCAPE keep placing (or skip what can't be done) without 30 s gaps on
 * shift". TerrainStallGameTests builds with the hill rising toward the build's right-hand side; players turn their
 * blueprints, so here the same hillside runs front-to-back across the build (turned 90 degrees either way), which
 * changes the order the terrain steps come in. Same footprint on the slope as the builder's own tests.
 */
public class QaB23TurnedHillsideGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	/** The tinker's workshop (12 wide, 10 deep) turned clockwise: it covers x 9..18, z 8..19 of the slope. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_b23_tinkers_turned"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_b23_tinkers_turned")
	public void qaB23TinkersWorkshopTurnedOnAHillsideNeverStalls(GameTestHelper helper) {
		Leftovers.clear(helper);
		TerrainStallGameTests.buildOnHillside(helper, StarterBlueprints.TINKERS_WORKSHOP, new BlockPos(18, 10, 8), Rotation.CLOCKWISE_90);
	}

	/** The graveyard (13 by 13) turned counterclockwise: it covers x 9..21, z 8..20, as in the builder's own test. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_b23_graveyard_turned"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_b23_graveyard_turned")
	public void qaB23GraveyardTurnedOnAHillsideNeverStalls(GameTestHelper helper) {
		Leftovers.clear(helper);
		TerrainStallGameTests.buildOnHillside(helper, StarterBlueprints.GRAVEYARD, new BlockPos(9, 10, 20), Rotation.COUNTERCLOCKWISE_90);
	}
}
