package io.github.jcondedata.aliveworkplace.ui;

/**
 * Where the City Plan and the Colony Charter put their parts in Copperline (ROADMAP 44.3, docs/design/copperline/,
 * nearest boards HallMap.png and Components.png): the shell from {@link CopperLayout} (plaque, lintel with the close X,
 * the stage, the whisper line); the City Plan's tool rail on the left (Brush, Area, Erase, Road, Wall, then Undo), its
 * map at the stage's left with the road and wall options under it, and the zone column on the right (the ZONES heading
 * with the one copper button, New, the zone list, the chosen zone's name, style, kind, renew and delete, and the legend
 * when there is room); the Charter's map at the stage's left and its column on the right (the spot, the ring, and Done,
 * its copper button). Pure arithmetic in GUI pixels, so the GameTests check it at GUI scale 2 and 3. Every box is
 * {x, y, w, h}.
 */
public final class CityPlanLayout {
	/** The rail's entries in order: the five tools, then Undo. */
	public static final String[] RAIL = {"brush", "area", "eraser", "road", "wall", "undo"};
	public static final int UNDO = 5;
	/** The map is a whole number of cells of this many (CityPlan.GRID). */
	public static final int GRID = 32;
	public static final int BUTTON_H = 14;
	public static final int HEAD_H = 14;
	public static final int ROW = 12;
	/** The legend: its heading and four rows of two kinds. */
	public static final int LEGEND_H = 12 + 4 * 9;
	/** The narrowest the zone column (and the Charter's column) may be. */
	public static final int SIDE_MIN = 150;
	public static final int SIDE_MIN_COMPACT = 120;
	/** The rail's widest label before it is cut. */
	public static final int RAIL_LABEL_MAX = 56;
	/** The road style button under the map: an item icon. */
	public static final int ICON_BUTTON = 22;

	/**
	 * The City Plan's boxes: {@code rail} and its {@code railEntries}; the {@code map} square; under it {@code option} (road
	 * width, or the wall closed or open) and {@code roadStyle}; the column: {@code zonesHead}, {@code newButton}, {@code list},
	 * {@code name}, {@code style} (with the style's icon at its left), {@code kind}, {@code renew}, {@code delete} and the
	 * {@code legend} (zero height when it doesn't fit).
	 */
	public record Plan(int[] win, boolean compact, int[] plaque, int[] close, int[] lintel, int[] rail, int[] railBody, int[][] railEntries,
		int[] stage, int[] map, int[] option, int[] roadStyle, int[] side, int[] zonesHead, int[] newButton, int[] list, int[] name,
		int[] style, int[] kind, int[] renew, int[] delete, int[] legend, int[] whisper) {

		/** How many zone rows the list shows. */
		public int listRows() {
			return Math.max(1, (list[3] - 2) / ROW);
		}

		/** Every button and the name field, for the overlap and click checks: new, style, kind, renew, delete, option, road style. */
		public int[][] buttons() {
			return new int[][]{newButton, style, kind, renew, delete, option, roadStyle};
		}
	}

	/** The City Plan on a screen {@code sw x sh} for a title {@code titleW} wide, the rail's widest label and New's width. */
	public static Plan plan(int sw, int sh, int titleW, int railLabelW, int newW) {
		int[] win = CopperLayout.window(sw, sh);
		boolean compact = CopperLayout.compact(sw, sh);
		int[] plaque = CopperLayout.plaque(win, titleW);
		int[] close = CopperLayout.close(win);
		int[] lintel = CopperLayout.lintel(win, plaque);
		int railW = CopperLayout.railWidth(win, Math.min(railLabelW, RAIL_LABEL_MAX), compact);
		int[] rail = CopperLayout.rail(win, railW);
		int[] railBody = compact ? rail : new int[]{rail[0], rail[1] + 12, rail[2], rail[3] - 12};
		int[][] entries = CopperLayout.railEntries(railBody, RAIL.length, compact);
		int[] stage = CopperLayout.stage(win, railW);

		int sideMin = compact ? SIDE_MIN_COMPACT : SIDE_MIN;
		int room = Math.min(stage[3] - BUTTON_H - 3, stage[2] - sideMin - 6);
		int size = Math.max(GRID, room / GRID * GRID);
		int[] map = {stage[0], stage[1], size, size};
		int[] option = {stage[0], map[1] + size + 3, Math.min(48, size - ICON_BUTTON - 2), BUTTON_H};
		int[] roadStyle = {option[0] + option[2] + 2, option[1], ICON_BUTTON, BUTTON_H};

		int sx = map[0] + size + 6;
		int[] side = {sx, stage[1], stage[0] + stage[2] - sx, stage[3]};
		int sw2 = side[2];
		int nw = Math.min(newW, sw2 / 2);
		int[] newButton = {sx + sw2 - nw, side[1], nw, BUTTON_H};
		int[] zonesHead = {sx, side[1], sw2 - nw - 4, HEAD_H};
		int bottom = side[1] + side[3];
		int fixed = 4 + 4 * BUTTON_H + 3 * 2;
		int listTop = side[1] + HEAD_H + 2;
		boolean legendFits = !compact && bottom - listTop - fixed - LEGEND_H - 4 >= 4 * ROW + 2;
		int legendH = legendFits ? LEGEND_H : 0;
		int settingsTop = bottom - (legendFits ? legendH + 4 : 0) - fixed + 4;
		int[] list = {sx, listTop, sw2, settingsTop - 4 - listTop};
		int y = settingsTop;
		int[] name = {sx, y, sw2, BUTTON_H};
		y += BUTTON_H + 2;
		int[] style = {sx + 18, y, sw2 - 18, BUTTON_H};
		y += BUTTON_H + 2;
		int[] kind = {sx, y, sw2, BUTTON_H};
		y += BUTTON_H + 2;
		int half = (sw2 - 2) / 2;
		int[] renew = {sx, y, half, BUTTON_H};
		int[] delete = {sx + half + 2, y, sw2 - half - 2, BUTTON_H};
		int[] legend = {sx, bottom - legendH, sw2, legendH};
		return new Plan(win, compact, plaque, close, lintel, rail, railBody, entries, stage, map, option, roadStyle, side, zonesHead, newButton,
			list, name, style, kind, renew, delete, legend, CopperLayout.whisper(win));
	}

	/** The list row under {@code mx, my}, given the rows scrolled past, or -1. */
	public static int rowAt(Plan p, int scrolled, int count, double mx, double my) {
		int[] l = p.list();
		if (!CopperLayout.inside(new int[]{l[0], l[1] + 1, l[2] - 6, l[3] - 2}, mx, my)) {
			return -1;
		}
		int i = scrolled + (int) ((my - l[1] - 1) / ROW);
		return i >= 0 && i < count && i < scrolled + p.listRows() ? i : -1;
	}

	/**
	 * The Charter's boxes: the {@code map} square (128 map pixels drawn 1:1 or larger where there's room), the column at its
	 * right ({@code spotHead}, {@code spot} the panel saying where the colony goes, {@code ringHead}, {@code ring} the
	 * legend), and {@code done}, the copper button at the column's foot.
	 */
	public record Charter(int[] win, boolean compact, int[] plaque, int[] close, int[] lintel, int[] stage, int[] map, int[] side, int[] spotHead,
		int[] spot, int[] ringHead, int[] ring, int[] done, int[] whisper) {
	}

	public static Charter charter(int sw, int sh, int titleW, int doneW) {
		int[] win = CopperLayout.window(sw, sh);
		boolean compact = CopperLayout.compact(sw, sh);
		int[] plaque = CopperLayout.plaque(win, titleW);
		int[] close = CopperLayout.close(win);
		int[] lintel = CopperLayout.lintel(win, plaque);
		int[] stage = CopperLayout.stage(win, 0);
		int sideMin = compact ? SIDE_MIN_COMPACT : SIDE_MIN;
		int size = Math.max(64, Math.min(2 * 128, Math.min(stage[3], stage[2] - sideMin - 6)));
		int[] map = {stage[0], stage[1] + (stage[3] - size) / 2, size, size};
		int sx = stage[0] + size + 6;
		int[] side = {sx, stage[1], stage[0] + stage[2] - sx, stage[3]};
		int bottom = side[1] + side[3];
		int[] done = {sx + side[2] - Math.min(side[2], doneW), bottom - BUTTON_H, Math.min(side[2], doneW), BUTTON_H};
		int[] spotHead = {sx, side[1], side[2], 10};
		// the spot's panel holds three lines at most; the ring's text takes what's left
		int spotH = Math.max(24, Math.min(36, (done[1] - 4 - (side[1] + 13) - 13) / 2));
		int[] spot = {sx, side[1] + 13, side[2], spotH};
		int[] ringHead = {sx, spot[1] + spotH + 4, side[2], 10};
		int[] ring = {sx, ringHead[1] + 13, side[2], Math.max(0, done[1] - 4 - (ringHead[1] + 13))};
		return new Charter(win, compact, plaque, close, lintel, stage, map, side, spotHead, spot, ringHead, ring, done, CopperLayout.whisper(win));
	}

	private CityPlanLayout() {
	}
}
