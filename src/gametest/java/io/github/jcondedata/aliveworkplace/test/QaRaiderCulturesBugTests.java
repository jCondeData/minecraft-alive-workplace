package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane D, 2026-10-24) candidate bugs for desert raiders (ROADMAP 32.9). Not registered in fabric.mod.json: each
 * test here fails because of the mod, and is registered once the bug is fixed.
 *
 * <p>The item: "desert raiders pile sand against the wall ... until it reaches the top". SandRamps refuses a ramp whose
 * blocks would go where anything but air or the siege's own sand stands, so a player's sand at the wall's foot turns
 * the husk away; it then piles a block or so against the plain stone-brick side walls and stands behind the back wall
 * to the end, never ramping (run 2026-10-24: the row 3 of 3 times, "piled [4, 2, 9], the husk at (10.1, 2.0, 3.5)";
 * the single block 1 of 3 times, the same stall; with no player's sand, 2 of 2 passed).
 */
public class QaRaiderCulturesBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * One block of sand the player laid at the outside foot of the Stone Wall, in front of the husk: the husk should still
	 * ramp over the wall, and the player's block stay after the siege.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaPlayersSandBlock"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaPlayersSandBlock")
	public void aRampStillRisesOverAPlayersSandBlockAtTheWallsFoot(GameTestHelper helper) {
		QaRaiderCulturesGameTests.playersSand(helper, List.of(14));
	}

	/**
	 * A row of sand the player laid along the whole outside foot of the Stone Wall: the husk should still ramp over the
	 * wall, and the player's row stay after the siege.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaPlayersSandRow"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaPlayersSandRow")
	public void aRampStillRisesOverAPlayersRowOfSandAtTheWallsFoot(GameTestHelper helper) {
		QaRaiderCulturesGameTests.playersSand(helper, List.of(11, 12, 13, 14, 15, 16, 17));
	}
}
