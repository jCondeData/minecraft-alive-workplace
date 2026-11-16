package io.github.jcondedata.aliveworkplace.ui;

/**
 * Where the shared list menu (ROADMAP 42.1, the Move Tutor, the Daycares, the Professor, the Innkeeper, Research, the
 * Smith's orders, the builder's tool pickers, the shops and the Ferry) puts its parts in Copperline's shell (48.4): the
 * window, the plaque and lintel ({@link CopperLayout}), the menu's tabs down a **rail** at the left (a two-column grid of
 * icon cells, the open one copper-marked, the word in the whisper line), the list on the **stage** right of it (the
 * whole stage when the menu has no tabs), and the whisper line at the foot with the page arrows at its right. Pure
 * arithmetic in GUI pixels, so GameTests check it at GUI scale 2 and 3. Every box is {x, y, w, h}.
 */
public final class ListMenuLayout {
	/** A page arrow's height, in the whisper line. */
	public static final int PAGE_H = 13;

	/**
	 * The boxes: {@code rail} (w 0 with no tabs), {@code tabs} (one per tab, in order), {@code list} (the sunken list)
	 * and {@code pages} (the page arrows, in the order given, right-aligned in the whisper line).
	 */
	public record Boxes(int[] rail, int[][] tabs, int[] list, int[][] pages) {
	}

	/**
	 * The rail's width (0 with no tabs): always the icon grid (the 320x180 rail), two 20-pixel cells a row, so the list's
	 * columns (a price, a level, a button) keep the room they had under the old row of tabs; the tab's name is in the
	 * whisper line and its tooltip under the mouse. {@code labelW} and {@code compact} are kept for a worded rail later.
	 */
	public static int railWidth(int[] win, int tabs, int labelW, boolean compact) {
		return tabs <= 0 ? 0 : CopperLayout.railWidth(win, labelW, true);
	}

	/**
	 * Lays out a list menu in {@code win}: {@code tabs} tabs whose widest word is {@code labelW}, and page arrows
	 * {@code pageW} wide each.
	 */
	public static Boxes layout(int[] win, int tabs, int labelW, int[] pageW, boolean compact) {
		int railW = railWidth(win, tabs, labelW, compact);
		int[] rail = railW > 0 ? CopperLayout.rail(win, railW) : new int[]{win[0], win[1] + CopperLayout.STAGE_Y, 0, 0};
		int[][] tabBoxes = railW > 0 ? CopperLayout.railEntries(rail, tabs, true) : new int[0][];
		int[] stage = CopperLayout.stage(win, railW);
		int[] whisper = CopperLayout.whisper(win);
		int[][] pages = CopperLayout.rightRow(pageW, win[0] + win[2] - 8, whisper[1] - 1, 4, PAGE_H);
		return new Boxes(rail, tabBoxes, stage, pages);
	}

	/** How far the whisper line's words may run before the page arrows (its whole width with none). */
	public static int whisperWidth(int[] win, int[][] pages) {
		int[] whisper = CopperLayout.whisper(win);
		int left = whisper[0] + whisper[2];
		for (int[] p : pages) {
			left = Math.min(left, p[0] - 6);
		}
		return Math.max(0, left - whisper[0] - 4);
	}

	private ListMenuLayout() {
	}
}
