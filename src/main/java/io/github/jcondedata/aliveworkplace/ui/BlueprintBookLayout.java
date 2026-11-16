package io.github.jcondedata.aliveworkplace.ui;

/**
 * Where the Blueprint Table's screen puts its parts in Copperline (ROADMAP 48.1, docs/design/copperline/boards/Blueprints.png,
 * "the Blueprint Book"): the shell from {@link CopperLayout} (plaque, lintel with the Library and Stored tabs and the close
 * X, the rail, the stage, the whisper line), the style rail on the left (as drawn and each style the build takes), the
 * search box and the list in the stage's left column with its NAME and SIZE heading, the build's detail (its 3D preview,
 * size, materials) on the right, and one row of buttons along the stage's foot, the first of them the one copper button
 * (Get Blueprint in the library, Take One Out in the Stored tab, Upload in the upload view). Pure arithmetic in GUI pixels
 * so the GameTests check it at GUI scale 2 and 3. Every box is {x, y, w, h}.
 */
public final class BlueprintBookLayout {
	/** What the stage shows: the library (the book), the blueprints stored in the table (36.13), or the upload view. */
	public enum View { LIBRARY, STORED, FILES }

	/** Each view's buttons, by lang key, in the order drawn; the first is the view's one copper button. */
	public static final String[] LIBRARY_BUTTONS = {"screen.aliveworkplace.table.take", "screen.aliveworkplace.table.upload_view"};
	public static final String[] STORED_BUTTONS = {"screen.aliveworkplace.table.take_out", "screen.aliveworkplace.table.store_held",
		"screen.aliveworkplace.table.store_all"};
	public static final String[] FILES_BUTTONS = {"screen.aliveworkplace.table.upload", "screen.aliveworkplace.table.open_folder", "gui.back"};
	/** The copper button's index in every view's row. */
	public static final int COPPER = 0;

	public static final int SEARCH_H = 14;
	public static final int HEAD_H = 12;
	public static final int BUTTON_H = 14;
	public static final int TAB_H = 13;
	/** The rail's "STYLE" heading's height (none on a compact rail). */
	public static final int RAIL_HEAD = 12;
	/** A library row (one line: the name, the size at the right) and a stored row (an item icon and two lines). */
	public static final int ROW = 12;
	public static final int STORED_ROW = 20;
	public static final int SLOT = 18;
	/** The widest rail label before it is cut. */
	public static final int RAIL_LABEL_MAX = 64;

	public static String[] buttons(View view) {
		return switch (view) {
			case LIBRARY -> LIBRARY_BUTTONS;
			case STORED -> STORED_BUTTONS;
			case FILES -> FILES_BUTTONS;
		};
	}

	/**
	 * The boxes: {@code win} the window, {@code tabs} the lintel's tabs, {@code lintelText} the room left for the count text,
	 * {@code rail} and {@code railBody} (under its heading), {@code stage}, {@code search}, {@code header} the NAME/SIZE row,
	 * {@code list}, {@code detail} the right column, {@code buttons} the row along the stage's foot, {@code whisper}.
	 */
	public record Boxes(int[] win, boolean compact, int[] plaque, int[][] tabs, int[] lintelText, int[] close, int[] rail, int[] railBody,
		int[] stage, int[] search, int[] header, int[] list, int[] detail, int[][] buttons, int[] whisper) {
	}

	/**
	 * The layout on a screen {@code sw x sh} GUI pixels, for a title {@code titleW} wide, the rail's widest label
	 * {@code railLabelW}, the tabs {@code tabW} wide and the view's buttons wanting {@code buttonW} each.
	 */
	public static Boxes of(int sw, int sh, int titleW, int railLabelW, int[] tabW, View view, int[] buttonW) {
		int[] win = CopperLayout.window(sw, sh);
		boolean compact = CopperLayout.compact(sw, sh);
		int[] plaque = CopperLayout.plaque(win, titleW);
		int[] close = CopperLayout.close(win);
		int[] lintel = CopperLayout.lintel(win, plaque);
		// The tabs sit on the lintel after the plaque, each at most a quarter of what's left; the count text gets the rest.
		int[][] tabs = new int[tabW.length][];
		int tx = plaque[0] + plaque[2] + 4;
		int tabMax = Math.max(30, (close[0] - 4 - tx) / 4);
		for (int i = 0; i < tabW.length; i++) {
			int w = Math.min(tabW[i], tabMax);
			tabs[i] = new int[]{tx, win[1] + 3, w, TAB_H};
			tx += w + 2;
		}
		int textX = tx + 4;
		int[] lintelText = {textX, lintel[1], Math.max(0, close[0] - 4 - textX), lintel[3]};

		// The rail's words are cut with "..." past 64 pixels, so the list keeps its room (the board's rail is a fifth of the window).
		// Only the library has the style rail; the Stored tab and the upload view give its room to the list.
		int railW = view != View.LIBRARY ? 0 : CopperLayout.railWidth(win, Math.min(railLabelW, RAIL_LABEL_MAX), compact) - (compact ? 0 : 16);
		int[] rail = railW == 0 ? new int[]{win[0] + 2, win[1] + CopperLayout.STAGE_Y, 0, 0} : CopperLayout.rail(win, railW);
		int[] railBody = compact || railW == 0 ? rail : new int[]{rail[0], rail[1] + RAIL_HEAD, rail[2], rail[3] - RAIL_HEAD};
		int[] stage = CopperLayout.stage(win, railW);
		int stageRight = stage[0] + stage[2];
		int stageBottom = stage[1] + stage[3];

		int buttonsY = stageBottom - BUTTON_H;
		int bottom = buttonsY - 4;
		int listW = compact ? stage[2] * 46 / 100 : Math.min(176, stage[2] * 45 / 100);
		int[] search = {stage[0], stage[1], listW, SEARCH_H};
		int[] header;
		int[] list;
		if (view == View.LIBRARY) {
			header = new int[]{stage[0], stage[1] + SEARCH_H + 3, listW, HEAD_H};
			list = new int[]{stage[0], header[1] + HEAD_H + 1, listW, bottom - (header[1] + HEAD_H + 1)};
		} else {
			header = new int[]{stage[0], stage[1], listW, 0};
			list = new int[]{stage[0], stage[1], listW, bottom - stage[1]};
		}
		int dx = stage[0] + listW + 6;
		int[] detail = {dx, stage[1], stageRight - dx, bottom - stage[1]};

		// The buttons: from the detail column's left if they fit there, else from the stage's; cut down evenly if even that is short.
		int gap = 4;
		int need = -gap;
		for (int w : buttonW) {
			need += w + gap;
		}
		int bx = need <= detail[2] ? detail[0] : stage[0];
		int room = stageRight - bx;
		int[] widths = buttonW.clone();
		if (need > room) {
			int each = Math.max(24, (room - gap * (widths.length - 1)) / Math.max(1, widths.length));
			for (int i = 0; i < widths.length; i++) {
				widths[i] = Math.min(widths[i], each);
			}
		}
		int[][] buttons = new int[widths.length][];
		for (int i = 0; i < widths.length; i++) {
			buttons[i] = new int[]{bx, buttonsY, widths[i], BUTTON_H};
			bx += widths[i] + gap;
		}
		return new Boxes(win, compact, plaque, tabs, lintelText, close, rail, railBody, stage, search, header, list, detail, buttons,
			CopperLayout.whisper(win));
	}

	/** The rail's style entries ({@code count}: as drawn and each style), from {@link CopperLayout#railEntries}. */
	public static int[][] styleEntries(Boxes boxes, int count) {
		return CopperLayout.railEntries(boxes.railBody(), count, boxes.compact());
	}

	/** The list's rows: how many fit, and the row at {@code my} given the rows scrolled past ({@code -1}: none). */
	public static int visibleRows(int[] list, int rowH) {
		return Math.max(1, (list[3] - 2) / rowH);
	}

	public static int rowAt(int[] list, int rowH, int scrolled, int count, double mx, double my) {
		if (!CopperLayout.inside(new int[]{list[0], list[1] + 1, list[2] - 6, list[3] - 2}, mx, my)) {
			return -1;
		}
		int i = scrolled + (int) ((my - list[1] - 1) / rowH);
		return i >= 0 && i < count && i < scrolled + visibleRows(list, rowH) ? i : -1;
	}

	private BlueprintBookLayout() {
	}
}
