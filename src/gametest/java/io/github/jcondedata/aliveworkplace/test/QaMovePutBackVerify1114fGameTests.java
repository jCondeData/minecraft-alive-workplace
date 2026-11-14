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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (2026-11-14, verifying B1237's fix), written from the bug's expected behavior: from the moment Call off is clicked
 * on the Move page, the building's row and Confirm on a new spot say "The Mover is still putting it back: pick a new spot
 * once it stands again" (never the under-way line, whose Call off button is gone), whether or not a Mover ever steps
 * again, and through a save and load; once it stands again, Confirm on a new spot through the page is taken.
 * QaMovePutBack1114cBugGameTests covers the moment right after Call off with no step. These add: a reload at that
 * moment (no step at all), the Mover dead before the Call off (time passing, nobody to put it back), the put-back then
 * finished and Confirm pressed on the same page, a Call off while it is still coming down with no step, and the other
 * side of the boundary: calling off a move the Mover never began leaves nothing to put back (Confirm is taken at once).
 * Village, Mover and spots are QaMovePutBack1114cGameTests'.
 */
public class QaMovePutBackVerify1114fGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos T = new BlockPos(13, 2, 17);
	private static final BlockPos S = new BlockPos(13, 2, 5);

	private static String putBack() {
		return Component.translatable("message.aliveworkplace.move.putting_back").getString();
	}

	private static String underWay() {
		return Component.translatable("message.aliveworkplace.move.under_way").getString();
	}

	private static MoveSite reload(QaHallMove1111c2GameTests.Village v) {
		CompoundTag saved = MoveSites.get(v.level()).save(new CompoundTag(), v.level().registryAccess());
		v.level().getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, v.level().registryAccess()));
		return MoveSites.get(v.level()).forBuilding(v.a()).orElseThrow(() -> new AssertionError("the put-back didn't survive the reload"));
	}

	private static void finish(QaHallMove1111c2GameTests.Village v, MoveSite site) {
		for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoveSites.get(v.level()).step(v.level(), site);
		}
	}

	private static int standing(GameTestHelper helper) {
		return QaMoveSpotSaves1111eGameTests.count(helper)[0];
	}

	/** The picker of A has no Call off button left (the move is already called off). */
	private static void assertNoCallOff(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, String when) {
		ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
		boolean offered = !menu.icon(MovePage.CALL_OFF).isEmpty();
		java.util.List<String> words = QaHallMove1111c2GameTests.words(menu);
		menu.removed(v.player());
		MovePage.stop(v.player());
		helper.assertFalse(words.contains(underWay()), when + ": the page says the under-way line (\"" + underWay() + "\"), Call off offered " + offered);
	}

	/** Confirm on T through A's picker, on a page opened now: what the action bar said and whether a move was saved. */
	private static String confirmOnT(GameTestHelper helper, QaHallMove1111c2GameTests.Village v) {
		ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
		QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
		menu.press(MovePage.CONFIRM, v.player());
		String said = QaHallMove1111c2GameTests.actionBar(v);
		menu.removed(v.player());
		MovePage.stop(v.player());
		return said;
	}

	private static String plannedLine(GameTestHelper helper) {
		return Component.translatable("message.aliveworkplace.move.planned", QaHallMove1111c2GameTests.name(), "", "", "").getString().split(" at ")[0];
	}

	/**
	 * Called off at STRUCTURE with no step after it, the world saved and loaded at once: the loaded site is a put-back,
	 * the row and Confirm (pressed three times, turned) say the put-back line, never the under-way one; then it is put
	 * back and Confirm on T, through the page, is taken ("Move planned") and saved.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114fReload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114fReload")
	public void calledOffThenReloadedBeforeAnyStepStillSaysItIsPutBack(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int before = standing(helper);
				MoveSite site = QaMovePutBack1114cGameTests.callOff(helper, v, s -> s.stage() == MoveSite.Stage.STRUCTURE, false);
				helper.assertTrue(site.canceled(), "right after Call off (no step) the site isn't canceled: " + site.stage());
				site = reload(v);
				helper.assertTrue(site.canceled() && site.stage() != MoveSite.Stage.DONE, "after the reload: canceled " + site.canceled() + ", " + site.stage());
				assertNoCallOff(helper, v, "reloaded right after Call off");
				QaMovePutBack1114cGameTests.assertRefused(helper, v, 1, "reloaded right after Call off");
				finish(v, site);
				helper.assertTrue(standing(helper) == before, "A put back " + standing(helper) + " of " + before);
				String said = confirmOnT(helper, v);
				helper.assertTrue(said.startsWith(plannedLine(helper)), "Confirm on T once A stands again said \"" + said + "\", not \"" + plannedLine(helper) + " at ...\"");
				helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(), "Confirm on T once A stands again saved no move");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/**
	 * The Mover dies while A is half moved, then the player calls it off: nobody will ever step the site, yet from the
	 * Call off on (and 60 ticks later) the page says the put-back line, and Confirm on T saves nothing.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114fNoMover"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114fNoMover")
	public void withTheMoverDeadCallOffStillSaysItIsPutBack(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				MoveSite[] site = new MoveSite[1];
				PendingMoves.Move move = QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
				MovePage.stop(v.player());
				site[0] = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
				for (int i = 0; i < 100_000 && site[0].stage() != MoveSite.Stage.STRUCTURE && site[0].stage() != MoveSite.Stage.DONE; i++) {
					MoveSites.get(v.level()).step(v.level(), site[0]);
				}
				helper.assertTrue(site[0].stage() == MoveSite.Stage.STRUCTURE, "setup: A's move at " + site[0].stage());
				int killed = 0;
				for (Villager m : v.level().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
					m.kill();
					killed++;
				}
				helper.assertTrue(killed >= 1, "setup: no Mover to kill");
				ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
				helper.assertTrue(!menu.icon(MovePage.CALL_OFF).isEmpty(), "setup: no Call off on the page of a move under way");
				menu.press(MovePage.CALL_OFF, v.player());
				menu.removed(v.player());
				MovePage.stop(v.player());
				assertNoCallOff(helper, v, "the Mover dead, right after Call off");
				QaMovePutBack1114cGameTests.assertRefused(helper, v, 0, "the Mover dead, right after Call off");
				helper.runAfterDelay(60, () -> {
					try {
						helper.assertTrue(site[0].stage() != MoveSite.Stage.DONE, "setup: something put A back with no Mover");
						QaMovePutBack1114cGameTests.assertRefused(helper, v, 0, "the Mover dead, 60 ticks after Call off");
						helper.succeed();
					} catch (AssertionError e) {
						helper.fail(e.getMessage());
					}
				});
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/** Called off while A still comes down, no step after it: refused with the put-back line at once, then taken once it stands. */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114fComingDown"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114fComingDown")
	public void calledOffWhileComingDownWithNoStepSaysItIsPutBack(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int before = standing(helper);
				MoveSite site = QaMovePutBack1114cGameTests.callOff(helper, v, s -> s.stage() == MoveSite.Stage.DISMANTLE && standing(helper) <= before / 2, false);
				helper.assertTrue(standing(helper) > 0 && standing(helper) < before, "setup: A half down: " + standing(helper) + " of " + before);
				helper.assertTrue(site.canceled(), "right after Call off while coming down, the site isn't canceled: " + site.stage());
				QaMovePutBack1114cGameTests.assertRefused(helper, v, 0, "called off while coming down, no step");
				finish(v, site);
				helper.assertTrue(standing(helper) == before, "A put back " + standing(helper) + " of " + before);
				String said = confirmOnT(helper, v);
				helper.assertTrue(said.startsWith(plannedLine(helper)) && PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(),
					"Confirm on T once A stands again said \"" + said + "\"; saved " + PendingMoves.get(v.level()).forBuilding(v.a()));
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/**
	 * The other side: a move planned but never begun (no Mover has touched A) is called off. Nothing is taken down, so
	 * there is nothing to put back: A's row doesn't say the put-back line and Confirm on T is taken at once.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114fNotBegun"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114fNotBegun")
	public void callingOffAMoveNeverBegunLeavesNothingToPutBack(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int before = standing(helper);
				QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
				MovePage.stop(v.player());
				helper.assertTrue(MoveSites.get(v.level()).forBuilding(v.a()).isEmpty(), "setup: A's move began on its own");
				ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
				helper.assertTrue(!menu.icon(MovePage.CALL_OFF).isEmpty(), "no Call off on the page of a planned move");
				menu.press(MovePage.CALL_OFF, v.player());
				menu.removed(v.player());
				MovePage.stop(v.player());
				helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "Call off left the planned move saved");
				helper.assertTrue(standing(helper) == before, "A isn't whole after calling off a move never begun: " + standing(helper) + " of " + before);
				int row = QaHallMove1111c2GameTests.rowOf(helper, v, v.a());
				ChoiceMenu list = QaHallMove1111c2GameTests.list(v);
				java.util.List<String> lore = QaHallMove1111c2GameTests.lore(list.icon(row));
				list.removed(v.player());
				helper.assertFalse(lore.contains(putBack()), "A's row says it is being put back, though nothing was taken down: " + lore);
				String said = confirmOnT(helper, v);
				helper.assertTrue(said.startsWith(plannedLine(helper)) && PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(),
					"Confirm on T after calling off a move never begun said \"" + said + "\"; saved " + PendingMoves.get(v.level()).forBuilding(v.a()));
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}
}
