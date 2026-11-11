package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * QA bug probe (lane E, 2026-11-11, B1192's other side). The guide: "Once he has started, you can't send it to a different
 * spot: call the move off first, then pick again." Between the Call off and the Mover having put everything back (he is
 * still taking down what went up at the new spot), the move is no longer "under way" (MovePage.underWay skips a canceled
 * site), so A's row opens again on what is standing. Whatever the page then does, a move it says is planned ("Move
 * planned: ...") must be the one that happens, not dropped when the put-back ends.
 */
public class QaMoveUnderWay1111eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos S = new BlockPos(13, 2, 5);
	private static final BlockPos T = new BlockPos(13, 2, 17);

	/** Called off at STRUCTURE, half put back, then A's row: a spot T confirmed there is still A's planned move once A is back. */
	//$ gametest_ticks_batch AREA '200' '"qaMoveUnderWay1111eBugPutBack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveUnderWay1111eBugPutBack")
	public void aSpotConfirmedWhileTheCallOffIsPutBackIsNotDropped(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			PendingMoves.Move move = QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
			MoveSites sites = MoveSites.get(v.level());
			MoveSite site = sites.begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.STRUCTURE && site.stage() != MoveSite.Stage.DONE; i++) {
				sites.step(v.level(), site);
			}
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE, "setup: A's move never reached STRUCTURE: " + site.stage());
			ChoiceMenu menu;
			try {
				menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
			} catch (AssertionError e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("setup: " + e.getMessage());
			}
			menu.press(MovePage.CALL_OFF, v.player());
			menu.removed(v.player());
			MovePage.stop(v.player());
			// put back until about half of A stands again at its old spot
			for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE && QaMoveSpotSaves1111eGameTests.count(helper)[0] < before[0] / 2; i++) {
				sites.step(v.level(), site);
			}
			int half = QaMoveSpotSaves1111eGameTests.count(helper)[0];
			helper.assertTrue(site.stage() != MoveSite.Stage.DONE && half > 0 && half < before[0], "setup: A half put back: " + half + " of " + before[0] + ", " + site.stage());

			String opened;
			boolean confirmed = false;
			try {
				menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
				QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
				boolean ready = menu.icon(MovePage.CONFIRM).is(Items.PISTON);
				menu.press(MovePage.CONFIRM, v.player());
				menu.removed(v.player());
				MovePage.stop(v.player());
				confirmed = PendingMoves.get(v.level()).forBuilding(v.a()).isPresent();
				opened = "the picker opened (Confirm " + (ready ? "ready" : "gray") + "), action bar \"" + QaHallMove1111c2GameTests.actionBar(v) + "\"";
			} catch (AssertionError e) {
				opened = "A's row didn't open: " + e.getMessage();
			}
			if (!confirmed) {
				helper.succeed(); // refused while it's being put back: nothing promised
				return;
			}
			String said = QaHallMove1111c2GameTests.planned(PendingMoves.get(v.level()).forBuilding(v.a()).get().to());
			for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE; i++) {
				sites.step(v.level(), site);
			}
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(),
				"with A half put back after a call-off, " + opened + " and the page said \"" + said + "\"; once the put-back ended that move was gone"
					+ " (expected: Confirm refused until A is back, or the move to T kept and carried out)");
			helper.succeed();
		});
	}
}
