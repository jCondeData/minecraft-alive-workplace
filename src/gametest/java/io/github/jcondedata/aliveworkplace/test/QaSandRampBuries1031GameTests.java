package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-10-31) for B553's fix, from its spec and desert.md, not from the code: desert raiders' sand ramp
 * (32.9) "buries dead bushes, grass and snow at the wall's foot (they are gone after the raid), but never your flowers
 * or blocks"; "a flower or any block a player put in the way moves it along the wall". Each runs the Stone Wall yard of
 * {@link QaRaiderCulturesGameTests#atTheWallsFoot}: the ramp must reach the top, then after the siege every grain of its
 * sand is gone, nothing drops and no other block changed.
 */
public class QaSandRampBuries1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final List<Integer> ROW = List.of(11, 12, 13, 14, 15, 16, 17);

	/** Fallen snow (one layer) along the whole foot, on sand: buried by the ramp, gone afterward, no snowball drops. */
	//$ gametest_ticks_batch AREA '900' '"qaRampSnowRow"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaRampSnowRow")
	public void aRampBuriesFallenSnowAtTheWallsFoot(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, ROW, Blocks.SNOW, Blocks.SAND, false, false);
	}

	/** The badlands: dead bushes along the foot on red sand ground (B437 with B553): the ramp still rises, bushes gone. */
	//$ gametest_ticks_batch AREA '900' '"qaRampBushRedSand"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaRampBushRedSand")
	public void aRampBuriesDeadBushesOnRedSand(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, ROW, Blocks.DEAD_BUSH, Blocks.RED_SAND, false, false);
	}

	/**
	 * One poppy (a player's flower, on grass: it can't stand on sand) at the foot right in front of the husk: the ramp moves along the wall and still reaches
	 * the top, and the poppy is still there after the siege.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaRampOnePoppy"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaRampOnePoppy")
	public void aPoppyInFrontOfTheHuskMovesTheRampAlongAndStays(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, List.of(14), Blocks.POPPY, Blocks.GRASS_BLOCK, true, false);
	}

	/**
	 * A ramp up over dead bushes, then the world saved and loaded before the raid ends: the ramp's sand is still known and
	 * all of it goes when the siege is over (none left standing where the bushes were).
	 */
	//$ gametest_ticks_batch AREA '900' '"qaRampBushReload"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaRampBushReload")
	public void aRampOverDeadBushesIsTakenAwayAfterASaveAndLoad(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, ROW, Blocks.DEAD_BUSH, Blocks.SAND, false, true);
	}
}
