package io.github.jcondedata.aliveworkplace.ui;

/**
 * Where the Village Hall's Chronicle page puts its parts in Copperline (ROADMAP 48.4, docs/design/copperline/boards/Chronicle.png):
 * inside the Hall's body (right of the section rail, under the lintel, over the whisper line) a "Show" column of filters
 * (each its icon, its word and how many entries it holds; at 320x180 a column of icon cells with the count) with Back at
 * its foot, and right of it the stage: a sunken list of day headers ("DAY 47 · AUTUMN 5" over a rule) and entries (a
 * chevron, the entry's icon and its text on one line; an open entry grows to its whole text, wrapped). Pure arithmetic in
 * GUI pixels, so GameTests check it at GUI scale 2 and 3. Every box is {x, y, w, h}.
 */
public final class ChronicleLayout {
	/** The "SHOW" heading's height over the filters. */
	public static final int HEADING_H = 12;
	/** A filter row's height (an icon, a word and a count). */
	public static final int FILTER_H = 16;
	/** The smallest a filter row gets on a short screen. */
	public static final int FILTER_MIN = 12;
	/** The Back button's height at the column's foot. */
	public static final int BACK_H = 14;
	/** The compact column's width (an icon cell and its count). */
	public static final int COMPACT_W = 24;
	/** A day header's height. */
	public static final int DAY_H = 13;
	/** A closed entry's height (its icon is 16 pixels, drawn at 12). */
	public static final int ENTRY_H = 14;
	/** A wrapped line's height in an open entry. */
	public static final int LINE = 10;
	/** Room between the column and the stage. */
	public static final int GAP = 4;
	/** Where an entry's text starts, from the list's left: the chevron, then the icon. */
	public static final int TEXT_X = 26;
	/** The scroll bar's room at the list's right. */
	public static final int BAR = 6;

	/** The Show column's width: room for the longest word and count (from 70 to two fifths of the body), or the compact cell. */
	public static int columnWidth(int[] body, int labelW, int countW, boolean compact) {
		if (compact) {
			return COMPACT_W;
		}
		return Math.max(70, Math.min(body[2] * 2 / 5, 5 + 14 + 4 + labelW + 4 + countW + 3 + 2)); // the rail entry's icon, word and count
	}

	/** The Show column's box: the body's left, its whole height. */
	public static int[] column(int[] body, int columnW) {
		return new int[]{body[0], body[1], columnW, body[3]};
	}

	/** The stage's box: the sunken list right of the column. */
	public static int[] list(int[] body, int columnW) {
		int x = body[0] + columnW + GAP;
		return new int[]{x, body[1], body[0] + body[2] - x, body[3]};
	}

	/** The Back button at the column's foot. */
	public static int[] back(int[] column) {
		return new int[]{column[0], column[1] + column[3] - BACK_H, column[2], BACK_H};
	}

	/**
	 * The {@code count} filters down the column under the heading (none on a compact column, which has no room for the
	 * word), {@link #FILTER_H} tall or less on a short screen (never under {@link #FILTER_MIN}; then the last ones are
	 * left off and the list is cut, which a 320x180 screen with the seven groups never needs), clear of Back.
	 */
	public static int[][] filters(int[] column, int count, boolean compact) {
		int top = column[1] + (compact ? 0 : HEADING_H);
		int room = column[1] + column[3] - BACK_H - 2 - top;
		int h = count <= 0 ? FILTER_H : Math.max(FILTER_MIN, Math.min(FILTER_H, room / count));
		int fit = Math.max(0, Math.min(count, room / h));
		int[][] out = new int[fit][];
		for (int i = 0; i < fit; i++) {
			out[i] = new int[]{column[0], top + i * h, column[2], h};
		}
		return out;
	}

	/** The width an entry's text wraps at in the list (left of the scroll bar). */
	public static int textWidth(int[] list) {
		return Math.max(20, list[2] - TEXT_X - BAR - 4);
	}

	/** An entry's height: one line closed, or its {@code lines} wrapped lines open. */
	public static int entryHeight(int lines, boolean open) {
		return open ? Math.max(ENTRY_H, 3 + Math.max(1, lines) * LINE + 2) : ENTRY_H;
	}

	/**
	 * The rows' tops, from the list's top less {@code scroll}: each row is a day header (when {@code header[i]}) or an
	 * entry {@code heights[i]} tall. One more number at the end: the content's whole height.
	 */
	public static int[] rows(int[] list, boolean[] header, int[] heights, int scroll) {
		int[] out = new int[header.length + 1];
		int y = list[1] + 3 - scroll;
		for (int i = 0; i < header.length; i++) {
			out[i] = y;
			y += header[i] ? DAY_H : heights[i];
		}
		out[header.length] = y + 3 + scroll - list[1];
		return out;
	}

	/** The furthest the list scrolls for {@code content} pixels in {@code list}. */
	public static int maxScroll(int[] list, int content) {
		return Math.max(0, content - list[3]);
	}

	/** Whether a row from {@code y}, {@code h} tall, is wholly inside the list (only those take clicks). */
	public static boolean shown(int[] list, int y, int h) {
		return y >= list[1] && y + h <= list[1] + list[3];
	}

	private ChronicleLayout() {
	}
}
