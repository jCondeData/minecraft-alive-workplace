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
 * QA (lane C, 2026-11-11, second pass): a building whose move is already under way, as its Move page row shows it. The
 * owner (B1163): the page must say "where it would go"; the wiki: the list shows "once planned, where it's going". With
 * A's move to one spot under way (the Mover packing it), the player opens A's row (it says where it's going), picks a
 * different spot and presses Confirm. Either the page refuses that (the move is under way) or the Mover follows it; what
 * it must not do is save and show the new spot while the Mover keeps building at the old one.
 */
public class QaHallMove1111c2BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** A's first spot: x 11-15, z 3-7. */
	private static final BlockPos FIRST = new BlockPos(13, 2, 5);
	/** A different spot for A, picked mid-move: x 11-15, z 15-19. */
	private static final BlockPos SECOND = new BlockPos(13, 2, 17);

	/**
	 * A's move to FIRST under way (5 swings packed), then a new spot picked and Confirmed on A's picker: whatever the
	 * page then says A is going to ("Move planned: to ...", the saved move) is where the Mover builds it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallMove1111c2Replan"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallMove1111c2Replan")
	public void aNewSpotConfirmedMidMoveIsWhereTheMoverBuilds(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			PendingMoves.Move first = QaHallMove1111c2GameTests.plan(helper, v, v.a(), FIRST, 0);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), first).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			for (int i = 0; i < 5; i++) {
				MoveSites.get(v.level()).step(v.level(), site);
			}
			helper.assertTrue(site.stage() != MoveSite.Stage.DONE && site.newMin().equals(first.to()), "setup: A's move under way to " + first.to() + ": " + site.stage() + " " + site.newMin());
			ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
			QaHallMove1111c2GameTests.clickMap(helper, v, menu, SECOND);
			boolean ready = menu.icon(MovePage.CONFIRM).is(Items.PISTON);
			menu.press(MovePage.CONFIRM, v.player());
			menu.removed(v.player());
			BlockPos said = PendingMoves.get(v.level()).forBuilding(v.a()).map(PendingMoves.Move::to).orElse(null);
			MoveSites.get(v.level()).step(v.level(), site);
			MoveSite now = MoveSites.get(v.level()).forBuilding(v.a()).orElse(site);
			helper.assertTrue(said != null, "A's move under way was dropped by a Confirm (Confirm looked " + (ready ? "ready" : "gray") + ")");
			helper.assertTrue(said.equals(now.newMin()),
				"with A's move under way, Confirm on a new spot (Confirm looked " + (ready ? "ready" : "gray") + ", action bar \"" + QaHallMove1111c2GameTests.actionBar(v)
					+ "\"): the page now says \"" + QaHallMove1111c2GameTests.planned(said) + "\" but the Mover is building it at " + now.newMin()
					+ " (expected: Confirm refused while the move is under way, or the Mover following the new spot)");
			helper.succeed();
		});
	}
}
