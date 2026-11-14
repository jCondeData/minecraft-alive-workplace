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
 * QA (lane b, 2026-11-14) for B1204, written from the bug report and the guide ("call the move off first, then pick
 * again"; "Other buildings' moves go ahead meanwhile"), not the code. The fix's tests call the move off at STRUCTURE and
 * check the refusal half way through the put-back (with a reload) and Confirm taken once A stands. These add: a move
 * called off while A is still coming down (nothing at the new spot yet), refused until A stands; the boundary with one
 * block of A left to put back (refused) and none left (taken); and another building's move planned and carried out
 * while A is being put back; and a move under way (not called off) never said to be put back.
 */
public class QaMovePutBack1114bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos S = new BlockPos(13, 2, 5);
	private static final BlockPos T = new BlockPos(13, 2, 17);
	private static final String PUTTING_BACK = "The Mover is still putting it back: pick a new spot once it stands again";

	/** A's move planned to S and begun, worked until {@code until} holds, then called off through A's row (the site sees it on its next step, taken here). */
	private static MoveSite calledOff(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, java.util.function.Predicate<MoveSite> until) {
		PendingMoves.Move move = QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
		MoveSites sites = MoveSites.get(v.level());
		MoveSite site = sites.begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
		for (int i = 0; i < 100_000 && !until.test(site) && site.stage() != MoveSite.Stage.DONE; i++) {
			sites.step(v.level(), site);
		}
		helper.assertTrue(site.stage() != MoveSite.Stage.DONE, "setup: A's move ended before the call-off");
		ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
		menu.press(MovePage.CALL_OFF, v.player());
		menu.removed(v.player());
		MovePage.stop(v.player());
		sites.step(v.level(), site); // the site sees its plan called off on its next step
		helper.assertTrue(site.canceled() && site.stage() != MoveSite.Stage.DONE, "setup: Call off didn't cancel A's move (or it was put back in one step): " + site.stage());
		return site;
	}

	/** Confirm on T in A's picker: refused with the put-back line and nothing planned (when {@code refused}), or taken. */
	private static void confirmOnT(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, boolean refused, String when) {
		ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
		QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
		boolean gray = menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE);
		java.util.List<String> lore = QaHallMove1111c2GameTests.lore(menu.icon(MovePage.CONFIRM));
		menu.press(MovePage.CONFIRM, v.player());
		menu.removed(v.player());
		MovePage.stop(v.player());
		boolean planned = PendingMoves.get(v.level()).forBuilding(v.a()).isPresent();
		if (refused) {
			helper.assertTrue(gray && lore.contains(PUTTING_BACK), when + ": Confirm on T wasn't gray with the put-back line: " + lore);
			helper.assertTrue(QaHallMove1111c2GameTests.actionBar(v).equals(PUTTING_BACK), when + ": Confirm on T said \"" + QaHallMove1111c2GameTests.actionBar(v) + "\"");
			helper.assertTrue(!planned, when + ": a refused Confirm planned " + PendingMoves.get(v.level()).forBuilding(v.a()));
		} else {
			helper.assertTrue(!gray && planned, when + ": Confirm on T wasn't taken (" + lore + "), said \"" + QaHallMove1111c2GameTests.actionBar(v) + "\"");
		}
	}

	/**
	 * Called off while A is still coming down at its old spot (half of it packed, nothing up at S yet): until A stands
	 * again Confirm on T is refused with the put-back line; once it stands, it's taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114bDismantle"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114bDismantle")
	public void calledOffWhileItComesDownIsRefusedUntilItStands(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
				MoveSite site = calledOff(helper, v, x -> x.stage() == MoveSite.Stage.DISMANTLE && QaMoveSpotSaves1111eGameTests.count(helper)[0] <= before[0] / 2);
				int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
				helper.assertTrue(now[0] < before[0] && now[1] == 0, "setup: A half down, nothing at S: " + now[0] + " of " + before[0] + ", at S " + now[1]);
				MoveSites sites = MoveSites.get(v.level());
				confirmOnT(helper, v, true, "called off while coming down, being put back");
				for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE; i++) {
					sites.step(v.level(), site);
				}
				int[] back = QaMoveSpotSaves1111eGameTests.count(helper);
				helper.assertTrue(back[0] == before[0] && back[1] == 0, "A put back: " + back[0] + " of " + before[0] + ", at S " + back[1]);
				confirmOnT(helper, v, false, "once A stands again");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/** The boundary: with one block of A left to put back Confirm on T is refused; with none left (the move done) it's taken. */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114bLastBlock"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114bLastBlock")
	public void oneBlockLeftToPutBackIsStillRefused(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
				MoveSite site = calledOff(helper, v, x -> x.stage() == MoveSite.Stage.STRUCTURE);
				MoveSites sites = MoveSites.get(v.level());
				for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE && QaMoveSpotSaves1111eGameTests.count(helper)[0] < before[0] - 1; i++) {
					sites.step(v.level(), site);
				}
				int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
				helper.assertTrue(site.stage() != MoveSite.Stage.DONE && now[0] == before[0] - 1,
					"setup: one block of A left to put back: " + now[0] + " of " + before[0] + ", " + site.stage());
				confirmOnT(helper, v, true, "one block of A left to put back");
				for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE; i++) {
					sites.step(v.level(), site);
				}
				helper.assertTrue(QaMoveSpotSaves1111eGameTests.count(helper)[0] == before[0] && sites.forBuilding(v.a()).isEmpty(), "A wasn't put back whole");
				confirmOnT(helper, v, false, "A put back whole");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/** While A is being put back, B's move to T is planned through its own row and carried out ("other moves go ahead"). */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114bOther"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114bOther")
	public void anotherBuildingsMoveGoesAheadWhileOneIsPutBack(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				MoveSite a = calledOff(helper, v, x -> x.stage() == MoveSite.Stage.STRUCTURE);
				MoveSites sites = MoveSites.get(v.level());
				PendingMoves.Move toT = QaHallMove1111c2GameTests.plan(helper, v, v.b(), T, 0);
				helper.assertTrue(a.stage() != MoveSite.Stage.DONE, "setup: A's put-back ended before B was planned");
				MoveSite b = sites.begin(v.level(), toT).orElseThrow(() -> new AssertionError("B's move didn't start while A is put back"));
				for (int i = 0; i < 200_000 && (a.stage() != MoveSite.Stage.DONE || b.stage() != MoveSite.Stage.DONE); i++) {
					if (a.stage() != MoveSite.Stage.DONE) {
						sites.step(v.level(), a);
					}
					if (b.stage() != MoveSite.Stage.DONE) {
						sites.step(v.level(), b);
					}
				}
				helper.assertTrue(b.stage() == MoveSite.Stage.DONE && !b.canceled() && b.skipped() == 0 && b.newMin().equals(toT.to()),
					"B's move ended " + b.stage() + ", canceled " + b.canceled() + ", skipped " + b.skipped() + ", at " + b.newMin() + " not " + toT.to());
				helper.assertTrue(a.stage() == MoveSite.Stage.DONE && sites.forBuilding(v.a()).isEmpty() && PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(),
					"A's put-back didn't end cleanly beside B's move: " + a.stage());
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/**
	 * The other side: a move under way and not called off is never said to be put back. Its row has no put-back line,
	 * and Confirm on T says the under-way line (B1192), not the put-back one.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114bUnderWay"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114bUnderWay")
	public void aMoveUnderWayIsNotSaidToBePutBack(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				PendingMoves.Move move = QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
				MoveSites sites = MoveSites.get(v.level());
				MoveSite site = sites.begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
				for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.STRUCTURE && site.stage() != MoveSite.Stage.DONE; i++) {
					sites.step(v.level(), site);
				}
				helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && !site.canceled(), "setup: A's move under way at STRUCTURE: " + site.stage());
				int row = QaHallMove1111c2GameTests.rowOf(helper, v, v.a());
				ChoiceMenu menu = QaHallMove1111c2GameTests.list(v);
				java.util.List<String> lore = QaHallMove1111c2GameTests.lore(menu.icon(row));
				menu.removed(v.player());
				helper.assertTrue(!lore.contains(PUTTING_BACK), "the row of a move under way says it's put back: " + lore);
				menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
				QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
				java.util.List<String> confirm = QaHallMove1111c2GameTests.lore(menu.icon(MovePage.CONFIRM));
				menu.removed(v.player());
				MovePage.stop(v.player());
				helper.assertTrue(confirm.contains("The Mover is already moving it: call the move off first to pick another spot") && !confirm.contains(PUTTING_BACK),
					"Confirm on T while A's move is under way: " + confirm);
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}
}
