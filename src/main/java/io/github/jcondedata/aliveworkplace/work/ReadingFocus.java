package io.github.jcondedata.aliveworkplace.work;

import java.util.function.IntPredicate;

/**
 * Which button the reading pane of a {@link ChoiceView} screen (the Village Hall, the Storehouse board) reads out
 * (36.5), with no drawing, so a GameTest can check it:
 * <ul>
 * <li><b>hovered</b>: the row or cell under the mouse, read out at once;</li>
 * <li><b>held</b>: the last one hovered, kept while the mouse stays over the list, the gap or the pane, so a long
 * entry can be read by moving into the pane and scrolling there; it changes only when another row or cell is hovered,
 * and is let go when the mouse leaves the list and the pane;</li>
 * <li><b>pinned</b>: the last one clicked, read out when the mouse is anywhere else.</li>
 * </ul>
 * The wheel scrolls the reading over the pane, or over the list with Shift (the list itself stays put).
 */
public final class ReadingFocus {
	private int pinned = -1;
	private int held = -1;

	/** The button clicked: read out while the mouse is elsewhere, and held (it was under the mouse). */
	public void pin(int slot) {
		pinned = slot;
		held = slot;
	}

	/** The pinned button put away (its ×, or Esc). */
	public void unpin() {
		if (held == pinned) {
			held = -1; // put away means out of the pane, not just unpinned
		}
		pinned = -1;
	}

	public int pinned() {
		return pinned;
	}

	public int held() {
		return held;
	}

	/**
	 * The button to read out this frame: {@code hovered} (a row or cell under the mouse, -1 for none) if any, else the
	 * one held while the mouse is over the list or the pane ({@code reading}), else the pinned one (-1: none).
	 */
	public int focus(int hovered, boolean reading) {
		if (hovered >= 0) {
			held = hovered;
			return hovered;
		}
		if (!reading) {
			held = -1;
		}
		return held >= 0 ? held : pinned;
	}

	/** Lets go of a held or pinned button no longer {@code shown} (another page, or the button changed). */
	public void keepOnly(IntPredicate shown) {
		if (held >= 0 && !shown.test(held)) {
			held = -1;
		}
		if (pinned >= 0 && !shown.test(pinned)) {
			pinned = -1;
		}
	}

	/** Whether the wheel scrolls the reading (not the list): over the pane, or over the list with Shift held. */
	public static boolean wheelReads(boolean overList, boolean overPane, boolean shift) {
		return overPane || overList && shift;
	}
}
