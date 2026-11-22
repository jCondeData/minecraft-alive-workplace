package io.github.jcondedata.aliveworkplace.test;

import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-d-1121-1609), B1445, a real problem next to B726 (32.9 sand ramps), fixed with B1479 (lane-a-1122-0324):
 * the path to the hall ended within five blocks of it counted along the axes, right outside the back wall, so it
 * counted as getting in.
 */
public class QaRampFootStall1121dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A climbing husk starts three blocks behind the yard's back wall (14.5, 1.5) with the villager inside, a little to
	 * the west, as its target. It walks straight to the wall's foot nearest the villager, (10.2, 2, 3.5), and stands
	 * there: no ramp is ever begun (piled []), for the whole 850 ticks, in 5 of 5 runs. The same husk from (20.5, 1.5)
	 * begins its ramp at x = 10 on its way there and is over in about 200 ticks; one with no target goes round to the
	 * east wall and ramps there. Expected (32.9): a climber whose way in ends at a wall piles a ramp there and goes over.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaRampFootStall1121d"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaRampFootStall1121d")
	public void aClimberStandingAtTheWallsFootBehindItsTargetStillBeginsARamp(GameTestHelper helper) {
		QaThreatFixesGameTests.rampOver(helper, Map.of(), 14.5, 1.5, null);
	}
}
