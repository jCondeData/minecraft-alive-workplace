package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA bug test (lane E, 2026-11-11). moving-buildings.md: "Called off on the Move page, even half way, what went up at the
 * new spot comes down again and everything goes back up where it was." Once the Mover has taken the whole building down
 * and is putting it up at its new spot, its row on the Move page no longer opens its picker (the old spot holds no block
 * to capture), so there is no Call off. The control, called off while still half standing, is
 * QaMoveSpotSaves1111eGameTests#aMoveCalledOffHalfTakenDownGoesBackWhole (passes).
 */
public class QaMoveCallOff1111eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A's move with its floor already up at S: Call off on A's picker brings it all back to the old spot. */
	//$ gametest_ticks_batch AREA '200' '"qaMoveCallOff1111eBugUp"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveCallOff1111eBugUp")
	public void aMoveCalledOffHalfPutUpGoesBackWhole(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			PendingMoves.Move move = QaMoveSpotSaves1111eGameTests.planA(helper, v);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			String wrong = QaMoveSpotSaves1111eGameTests.callOffHalfWay(helper, v, site, x -> x.stage() == MoveSite.Stage.STRUCTURE);
			helper.assertTrue(wrong == null, wrong);
			wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, v, before);
			helper.assertTrue(wrong == null, wrong);
			helper.succeed();
		});
	}
}
