package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane B, 2026-11-14), B1185's other edge: a called-off move whose building is still being taken back down off its
 * new spot. MoveFit.moving leaves a canceled move out, so Builders.assign hands S to a builder while the Mover is still
 * taking A's blocks down there. Fails on main (not registered); helpers in QaMoveSpotBuild1114bGameTests.
 */
public class QaMoveSpotBuild1114bBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A's move called off while A is half up at S: the Mover still has to take those blocks back down off S
	 * (moving-buildings.md: "what went up at the new spot comes down again"; B1204: a new spot is refused while it's put
	 * back). Meanwhile a builder must not take a hut on S, or he clears A's blocks out from under the Mover. Once S is
	 * empty again (put back up at the old spot), the hut on S is taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveBuild1114bPuttingBack"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveBuild1114bPuttingBack")
	public void theSpotIsNotTakenWhileACalledOffMoveIsTakenBackDownThere(GameTestHelper helper) {
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> m.stage() == MoveSite.Stage.STRUCTURE && QaMoveSpotSaves1111eGameTests.count(helper)[1] > 5);
			helper.assertTrue(MoveSites.get(s.v().level()).cancel(s.v().level(), s.v().a()), "setup: A's move couldn't be called off");
			MoveSites.get(s.v().level()).step(s.v().level(), site);
			int atS = QaMoveSpotSaves1111eGameTests.count(helper)[1];
			helper.assertTrue(site.canceled() && site.stage() != MoveSite.Stage.DONE && atS > 0, "setup: called off with A's blocks at S: canceled "
				+ site.canceled() + ", " + site.stage() + ", " + atS + " blocks at S");
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(!h.taken(), "A's move called off but " + atS + " of its blocks still stand at S for the Mover to take back down ("
				+ site.stage() + "), yet the builder took a hut on S; he said " + h.said());
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0);
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> false);
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "A put back whole after the call-off, a hut on S");
			helper.succeed();
		});
	}

}
