package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.ReadingFocus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * 36.5: the reading pane's focus rule in the Village Hall and the Storehouse board: the row under the mouse, held while
 * the mouse goes into the pane (changing only for another row or cell), let go outside the panel for the button last
 * clicked; and the wheel scrolls the reading over the pane or with Shift over the list.
 */
public class ReadingFocusGameTests implements FabricGameTest {
	private static final int ROW_A = 18;
	private static final int ROW_B = 19;
	private static final int CELL = 3;
	private static final int NONE = -1;

	/** Hover a long entry, cross the gap into the pane: it stays read out; another row takes over; outside, the pinned one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHoveredRowIsHeldOnTheWayIntoThePane(GameTestHelper helper) {
		ReadingFocus focus = new ReadingFocus();
		helper.assertTrue(focus.focus(NONE, true) == NONE, "something read out with nothing chosen");
		helper.assertTrue(focus.focus(ROW_A, true) == ROW_A, "the hovered row isn't read out");
		helper.assertTrue(focus.focus(NONE, true) == ROW_A, "the row wasn't held in the gap");
		helper.assertTrue(focus.focus(NONE, true) == ROW_A, "the row wasn't held over the pane");
		helper.assertTrue(focus.focus(ROW_B, true) == ROW_B && focus.focus(NONE, true) == ROW_B, "another row didn't take over");
		helper.assertTrue(focus.focus(CELL, true) == CELL && focus.focus(NONE, true) == CELL, "a strip cell didn't take over");
		helper.assertTrue(focus.focus(NONE, false) == NONE, "held after the mouse left the panel");
		focus.pin(ROW_A);
		helper.assertTrue(focus.pinned() == ROW_A, "not pinned");
		helper.assertTrue(focus.focus(NONE, true) == ROW_A, "the clicked row isn't held");
		helper.assertTrue(focus.focus(ROW_B, true) == ROW_B, "hovering doesn't override the pinned row");
		helper.assertTrue(focus.focus(NONE, true) == ROW_B, "the hovered row isn't held over a pinned one");
		helper.assertTrue(focus.focus(NONE, false) == ROW_A, "the pinned row isn't read out with the mouse away");
		helper.succeed();
	}

	/** Putting the pinned button away (× or Esc) takes it out of the pane; a button gone from the page is let go. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void unpinningAndPageChangesLetGo(GameTestHelper helper) {
		ReadingFocus focus = new ReadingFocus();
		focus.pin(ROW_A);
		focus.unpin();
		helper.assertTrue(focus.pinned() == NONE && focus.focus(NONE, true) == NONE, "still read out after it was put away");
		focus.pin(ROW_A);
		focus.focus(ROW_B, true);
		focus.unpin();
		helper.assertTrue(focus.focus(NONE, true) == ROW_B, "unpinning let go of another row being read");
		focus.pin(ROW_A);
		focus.focus(ROW_B, true);
		focus.keepOnly(slot -> slot != ROW_B);
		helper.assertTrue(focus.held() == NONE && focus.pinned() == ROW_A, "a row gone from the page is still held");
		focus.keepOnly(slot -> false);
		helper.assertTrue(focus.pinned() == NONE && focus.focus(NONE, false) == NONE, "a pinned button gone from the page is still read");
		helper.succeed();
	}

	/** The wheel: over the pane it scrolls the reading; over the list it scrolls the list, or the reading with Shift. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theWheelScrollsTheReadingInThePaneOrWithShift(GameTestHelper helper) {
		helper.assertTrue(ReadingFocus.wheelReads(false, true, false), "the wheel over the pane doesn't scroll the reading");
		helper.assertFalse(ReadingFocus.wheelReads(true, false, false), "the wheel over the list scrolls the reading");
		helper.assertTrue(ReadingFocus.wheelReads(true, false, true), "Shift and the wheel over a row don't scroll its reading");
		helper.assertFalse(ReadingFocus.wheelReads(false, false, true), "the wheel elsewhere scrolls the reading");
		helper.succeed();
	}
}
