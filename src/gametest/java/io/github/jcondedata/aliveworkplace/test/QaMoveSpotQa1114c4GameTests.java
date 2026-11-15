package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane c, 2026-11-14, fourth pass) verifying B1230 from the bug text and moving-buildings.md: while a called-off
 * move is still being taken back down off its new spot S (x 11-15, z 3-7), a builder refuses a blueprint on S with
 * "That spot isn't free yet: the move of <building> there was called off, and the Mover is still taking it back down."
 * QaMoveSpotBuild1114bBugTests covers a hut right on S. These add a hut sharing only one corner block of S, the same
 * after a save and reload mid take-down, and that S is taken once the building stands at its old place again. Village,
 * builder and hand-over as QaMoveSpotBuild1114bGameTests (the player's real click on the builder).
 */
public class QaMoveSpotQa1114c4GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** The line the fix added, word for word (lang key message.aliveworkplace.assign.move_spot_back). */
	static String backLine() {
		return "That spot isn't free yet: the move of " + QaMoveSpotBuild1114bGameTests.hut() + " there was called off, and the Mover is still taking it back down.";
	}

	static void assertRefusedBack(GameTestHelper helper, QaMoveSpotBuild1114bGameTests.Hand h, String what) {
		helper.assertTrue(!h.taken(), what + ": taken while the called-off move is still taken back down there; he said " + h.said());
		helper.assertTrue(h.said().contains(backLine()), what + ": refused, but not with \"" + backLine() + "\"; he said " + h.said());
		helper.assertTrue(h.said().size() == 1, what + ": one line for one refusal, not " + h.said());
	}

	/** A's move started and called off while A is half up at S; returns the site, still taking A back down off S. */
	static MoveSite calledOffHalfUp(GameTestHelper helper, QaMoveSpotBuild1114bGameTests.Setup s) {
		ServerLevel level = s.v().level();
		MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
		QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> m.stage() == MoveSite.Stage.STRUCTURE && QaMoveSpotSaves1111eGameTests.count(helper)[1] > 5);
		helper.assertTrue(MoveSites.get(level).cancel(level, s.v().a()), "setup: A's move couldn't be called off");
		MoveSites.get(level).step(level, site);
		int atS = QaMoveSpotSaves1111eGameTests.count(helper)[1];
		helper.assertTrue(site.canceled() && site.stage() != MoveSite.Stage.DONE && atS > 0,
			"setup: called off with A's blocks at S: canceled " + site.canceled() + ", " + site.stage() + ", " + atS + " blocks at S");
		return site;
	}

	/**
	 * During the take-down, huts sharing just one corner block of S (north-east (15, 3) and south-east (15, 7)) are
	 * refused with the line word for word; a hut diagonally clear of each corner is taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1114c4SpotCorner"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1114c4SpotCorner")
	public void aHutSharingOneCornerOfACalledOffMovesSpotIsRefusedWhileItComesDown(GameTestHelper helper) {
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			calledOffHalfUp(helper, s);
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 15, -1), "taken back down, a hut sharing only S's north-east corner (15, 3)");
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 15, 7), "taken back down, a hut sharing only S's south-east corner (15, 7)");
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 16, -2), "taken back down, a hut diagonally off S's north-east corner");
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 16, 8), "taken back down, a hut diagonally off S's south-east corner");
			helper.succeed();
		});
	}

	/**
	 * Called off half up at S, then saved and loaded back (a server restart) while A is still at S: a hut on S is still
	 * refused with the line; after a second reload with fewer blocks left at S, still refused; once A is back whole at its
	 * old place and S empty, the hut on S is taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1114c4SpotReload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1114c4SpotReload")
	public void aCalledOffMovesSpotStaysRefusedOverAReloadAndIsTakenOnceItsBack(GameTestHelper helper) {
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = s.v().level();
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			calledOffHalfUp(helper, s);
			QaMoveSpotBuild1114bGameTests.reload(level);
			MoveSite site = MoveSites.get(level).forBuilding(s.v().a()).orElseThrow(() -> new AssertionError("the called-off move was lost in the reload"));
			helper.assertTrue(site.canceled(), "the reload forgot the move was called off: " + site.stage());
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "reloaded mid take-down, a hut right on S");
			int atS = QaMoveSpotSaves1111eGameTests.count(helper)[1];
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[1] <= atS / 2);
			helper.assertTrue(QaMoveSpotSaves1111eGameTests.count(helper)[1] > 0, "setup: S already empty, no second reload mid take-down");
			QaMoveSpotBuild1114bGameTests.reload(level);
			site = MoveSites.get(level).forBuilding(s.v().a()).orElseThrow(() -> new AssertionError("the called-off move was lost in the second reload"));
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 13, 5), "reloaded twice mid take-down (" + QaMoveSpotSaves1111eGameTests.count(helper)[1]
				+ " blocks left at S), a hut over S's middle");
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "A back whole at its old place after the reloads, a hut on S");
			helper.succeed();
		});
	}
}
