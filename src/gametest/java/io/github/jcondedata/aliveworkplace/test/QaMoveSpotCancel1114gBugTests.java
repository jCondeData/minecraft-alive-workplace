package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-14), B1230's fix, a bug it leaves: a move called off before anything went up at its new spot S still counts
 * as "being taken back down" there until the Mover's next swing, so a builder handed a hut on S is told "the Mover is
 * still taking it back down" though S is empty. Fails on main (not registered); helpers in QaMoveSpotCancel1114gGameTests
 * and QaMoveSpotBuild1114bGameTests.
 */
public class QaMoveSpotCancel1114gBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * Called off while A is still coming down at its old spot, before anything went up at S: nothing of A is at S, so the
	 * player is never told the Mover is "still taking it back down" there, and once A stands again S is taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gEarly"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gEarly")
	public void calledOffBeforeAnythingWentUpNeverClaimsAPutBackAtS(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[0] < before[0] / 2);
			helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE && QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0,
				"setup: A half taken down, nothing at S: " + site.stage() + ", " + QaMoveSpotSaves1111eGameTests.count(helper)[1] + " at S");
			helper.assertTrue(MoveSites.get(s.v().level()).cancel(s.v().level(), s.v().a()), "setup: A's move couldn't be called off");
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(!h.said().contains(QaMoveSpotCancel1114gGameTests.backLine(QaMoveSpotBuild1114bGameTests.hut())),
				"called off before anything went up at S (S empty), yet the player is told the Mover is still taking it back down there: " + h.said());
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "A back whole, a hut on S");
			helper.succeed();
		});
	}
}
