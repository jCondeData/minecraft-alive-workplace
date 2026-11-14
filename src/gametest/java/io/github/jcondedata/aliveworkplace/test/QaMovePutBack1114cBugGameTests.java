package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA bug probe (lane c, 2026-11-14) for B1204's fix. A called-off move becomes a put-back only on the Mover's next step
 * (MoveSites.step cancels the site when its waiting move is gone), and he steps only while he works, so after Call off
 * (and at night, or with the Mover away) A's row and its Confirm don't say "The Mover is still putting it back...": the
 * page still treats the move as under way. Expected (B1204): from Call off on, A is being put back, and the page says so.
 */
public class QaMovePutBack1114cBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos T = new BlockPos(13, 2, 17);
	private static final String PUTTING_BACK = "The Mover is still putting it back: pick a new spot once it stands again";

	/** Called off at STRUCTURE; before the Mover's next step, A's row and Confirm on T say it's put back. */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114cBugNoStep"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114cBugNoStep")
	public void rightAfterCallOffThePageSaysItIsPutBack(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				MoveSite site = QaMovePutBack1114cGameTests.callOff(helper, v, s -> s.stage() == MoveSite.Stage.STRUCTURE, false);
				helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "setup: Call off left A's move saved");
				ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
				QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
				menu.press(MovePage.CONFIRM, v.player());
				String said = QaHallMove1111c2GameTests.actionBar(v);
				java.util.List<String> confirm = QaHallMove1111c2GameTests.lore(menu.icon(MovePage.CONFIRM));
				boolean callOffOffered = !menu.icon(MovePage.CALL_OFF).isEmpty();
				menu.removed(v.player());
				MovePage.stop(v.player());
				helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "Confirm right after Call off saved a move");
				helper.assertTrue(said.equals(PUTTING_BACK), "right after Call off (canceled " + site.canceled() + ", " + site.stage() + "), Confirm on T said \"" + said
					+ "\" (Call off offered: " + callOffOffered + "; Confirm's lines " + confirm + "), not \"" + PUTTING_BACK + "\"");
				QaMovePutBack1114cGameTests.assertRefused(helper, v, 0, "right after Call off");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}
}
