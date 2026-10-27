package io.github.jcondedata.aliveworkplace.test;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1025-0408) bug found while verifying B382 (32.9: desert raiders live in deserts AND badlands): the ramp
 * still stalls when the wall's foot holds red sand, the badlands' own ground. Run 2026-10-25, 2 of 2: "no ramp over the
 * player's blocks in time: piled [4, 2, 7], the husk at (11.9, 2.0, 3.5)" (behind the back wall, as B382 was), while the
 * same row of plain sand passes. Register it with the fix.
 */
public class QaThreatFixesBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A row of red sand along the wall's whole outside foot doesn't stop the ramp, and stays after the siege. */
	//$ gametest_ticks_batch HUGE '900' '"qaFixRedSandRow"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qaFixRedSandRow")
	public void qaARampRisesOverAPlayersRowOfRedSand(GameTestHelper helper) {
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		for (int x = 11; x <= 17; x++) {
			mine.put(new BlockPos(x, 2, QaThreatFixesGameTests.FRONT + 1), Blocks.RED_SAND);
		}
		QaThreatFixesGameTests.rampOver(helper, mine);
	}
}
