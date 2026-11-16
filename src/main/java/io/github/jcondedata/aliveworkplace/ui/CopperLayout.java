package io.github.jcondedata.aliveworkplace.ui;

/**
 * Where Copperline's shell goes on a screen (ROADMAP 47.1, docs/design/copperline/README.md): the window, the copper
 * plaque overhanging its top edge, the lintel bar under it (where you are, coins, the close X), the left rail, the stage
 * and the bottom whisper line. Pure arithmetic in GUI pixels, so the client's screens draw from it and the GameTests
 * check it at GUI scale 2 (427x240 and up) and 3 (320x180). Every box is {x, y, w, h}.
 *
 * <p>The 320x180 rule: on a screen that small ({@link #compact}) the rail turns into a two-column icon grid and the
 * whisper line keeps only its key hint's key.
 */
public final class CopperLayout {
	/** The window's largest size; it shrinks to fit the screen with {@link #MARGIN} pixels round it. */
	public static final int MAX_W = 440;
	public static final int MAX_H = 290;
	public static final int MARGIN = 8;
	/** How far the plaque rises over the window's top edge, and its height. */
	public static final int PLAQUE_RISE = 6;
	public static final int PLAQUE_H = 18;
	/** The lintel bar: its top inside the window, and its height. */
	public static final int LINTEL_Y = 2;
	public static final int LINTEL_H = 16;
	/** The close X's square. */
	public static final int CLOSE = 11;
	/** Where the stage starts under the window's top (under the lintel). */
	public static final int STAGE_Y = 20;
	/** The whisper line's height, at the window's foot. */
	public static final int WHISPER_H = 13;
	/** A rail entry's tallest height (an icon and a label), and a compact rail's icon cell. */
	public static final int RAIL_ROW = 20;
	public static final int RAIL_CELL = 20;
	public static final int RAIL_GAP = 2;

	/** Whether a screen this size (GUI pixels) is a small one (GUI scale 3 on a 960x540 window: 320x180). */
	public static boolean compact(int screenW, int screenH) {
		return screenW < 400 || screenH < 220;
	}

	public static int windowWidth(int screenW) {
		return Math.max(0, Math.min(screenW - 2 * MARGIN, MAX_W));
	}

	public static int windowHeight(int screenH) {
		return Math.max(0, Math.min(screenH - 2 * MARGIN, MAX_H));
	}

	/** The window, centered. */
	public static int[] window(int screenW, int screenH) {
		int w = windowWidth(screenW);
		int h = windowHeight(screenH);
		return new int[]{(screenW - w) / 2, (screenH - h) / 2, w, h};
	}

	/** The plaque for a title {@code titleW} pixels wide: at the window's left, rising over its top, at most half its width. */
	public static int[] plaque(int[] win, int titleW) {
		int w = Math.min(titleW + 14, win[2] / 2);
		return new int[]{win[0] + 6, win[1] - PLAQUE_RISE, w, PLAQUE_H};
	}

	/** The close X at the lintel's right end. */
	public static int[] close(int[] win) {
		return new int[]{win[0] + win[2] - 4 - CLOSE, win[1] + LINTEL_Y + 2, CLOSE, CLOSE};
	}

	/** The lintel's room for text: from the plaque's right to the close X. */
	public static int[] lintel(int[] win, int[] plaque) {
		int x = plaque[0] + plaque[2] + 6;
		int right = close(win)[0] - 4;
		return new int[]{x, win[1] + LINTEL_Y, Math.max(0, right - x), LINTEL_H};
	}

	/** The whisper line along the window's foot. */
	public static int[] whisper(int[] win) {
		return new int[]{win[0] + 4, win[1] + win[3] - 2 - WHISPER_H, win[2] - 8, WHISPER_H};
	}

	/**
	 * The rail's width: on a compact screen the two-column icon grid, else an icon, the widest label ({@code labelW}) and
	 * a count, at most a third of the window.
	 */
	public static int railWidth(int[] win, int labelW, boolean compact) {
		if (compact) {
			return 4 + 2 * RAIL_CELL + RAIL_GAP + 4;
		}
		return Math.min(4 + 3 + 16 + 4 + labelW + 4 + 12 + 4, win[2] / 3);
	}

	/** The rail's box: under the lintel, over the whisper line, at the window's left. */
	public static int[] rail(int[] win, int railW) {
		int y = win[1] + STAGE_Y;
		return new int[]{win[0] + 2, y, railW - 2, whisper(win)[1] - 2 - y};
	}

	/** The stage: right of the rail (if any), under the lintel, over the whisper line. */
	public static int[] stage(int[] win, int railW) {
		int x = win[0] + (railW > 0 ? railW + 4 : 8);
		int y = win[1] + STAGE_Y;
		return new int[]{x, y, win[0] + win[2] - 8 - x, whisper(win)[1] - 3 - y};
	}

	/**
	 * The box a screen draws its own body in (the Hall's People, Overview and Map, the Move page): right of the rail
	 * ({@code railW}, 0 for none), under the lintel, over the whisper line.
	 */
	public static int[] body(int[] win, int railW) {
		return new int[]{win[0] + 8 + railW, win[1] + 22, win[2] - 16 - railW, win[3] - 16 - 22};
	}

	/**
	 * The rail's {@code count} entries: one column of rows (an icon, a label and a count) as tall as fit, from
	 * {@link #RAIL_ROW} down to 10; on a compact screen a two-column grid of {@link #RAIL_CELL} icon cells.
	 */
	public static int[][] railEntries(int[] rail, int count, boolean compact) {
		int[][] out = new int[count][];
		if (compact) {
			int rows = (count + 1) / 2;
			int step = Math.max(12, Math.min(RAIL_CELL + RAIL_GAP, rail[3] / Math.max(1, rows)));
			int cell = Math.min(RAIL_CELL, step - RAIL_GAP);
			for (int i = 0; i < count; i++) {
				int col = i % 2;
				out[i] = new int[]{rail[0] + 2 + col * (RAIL_CELL + RAIL_GAP), rail[1] + 2 + (i / 2) * step, RAIL_CELL, cell};
			}
			return out;
		}
		int rowH = Math.max(10, Math.min(RAIL_ROW, (rail[3] - 4) / Math.max(1, count)));
		for (int i = 0; i < count; i++) {
			out[i] = new int[]{rail[0] + 2, rail[1] + 2 + i * rowH, rail[2] - 4, rowH - 1};
		}
		return out;
	}

	/** Whether {@code mx, my} is inside {@code box}. */
	public static boolean inside(int[] box, double mx, double my) {
		return box != null && mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3];
	}

	/** The index of the box under {@code mx, my}, or -1. */
	public static int at(int[][] boxes, double mx, double my) {
		for (int i = 0; i < boxes.length; i++) {
			if (inside(boxes[i], mx, my)) {
				return i;
			}
		}
		return -1;
	}

	/** Whether two boxes share a pixel. */
	public static boolean overlap(int[] a, int[] b) {
		return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}

	/**
	 * A row of boxes {@code widths} wide from {@code x0} that wraps to the next line at {@code right}: the list screens'
	 * tabs and the strip's icon cells. Each box is {x, y, w, h}, {@code h} tall, lines {@code lineStep} apart.
	 */
	public static int[][] flow(int[] widths, int x0, int y0, int right, int gap, int h, int lineStep) {
		int[][] out = new int[widths.length][];
		int x = x0;
		int y = y0;
		for (int i = 0; i < widths.length; i++) {
			int w = Math.min(widths[i], right - x0);
			if (x + w > right && x > x0) {
				x = x0;
				y += lineStep;
			}
			out[i] = new int[]{x, y, w, h};
			x += w + gap;
		}
		return out;
	}

	/** Boxes {@code widths} wide set right-aligned to {@code right}, in order, {@code gap} apart (the footer's page arrows). */
	public static int[][] rightRow(int[] widths, int right, int y, int gap, int h) {
		int[][] out = new int[widths.length][];
		int x = right;
		for (int i = widths.length - 1; i >= 0; i--) {
			x -= widths[i];
			out[i] = new int[]{x, y, widths[i], h};
			x -= gap;
		}
		return out;
	}

	private CopperLayout() {
	}
}
