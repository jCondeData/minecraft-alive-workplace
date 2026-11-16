package io.github.jcondedata.aliveworkplace.ui;

import io.github.jcondedata.aliveworkplace.guide.GuideBook;
import java.util.List;

/**
 * Where the Guide Book puts its parts in Copperline (ROADMAP 44.4, docs/design/copperline/, nearest board
 * Chronicle.png): the shell from {@link CopperLayout} (plaque, lintel with the close X, the stage, the whisper line); the
 * rail on the left (Contents, then the chapters with their page counts; a two-column icon grid at 320x180); on the stage
 * the page's title, its picture (16:9, as big as the words under it leave room for) and its words, and the page arrows:
 * Back and Next (the copper button) along the stage's foot, or beside the title on a compact screen. The contents page
 * has the book's intro and the chapters with the page each starts on. Pure arithmetic in GUI pixels, so the GameTests
 * check it at GUI scale 2 and 3 and open every page through {@link #click}. Every box is {x, y, w, h}.
 */
public final class GuideLayout {
	public static final int BUTTON_H = 14;
	/** The page title's line. */
	public static final int TITLE_H = 12;
	/** A line of the page's words. */
	public static final int LINE = 9;
	/** A chapter row on the contents page. */
	public static final int ROW = 12;
	/** The rail's widest label before it is cut. */
	public static final int RAIL_LABEL_MAX = 60;
	/** A page arrow on a compact screen ("<" or ">"). */
	public static final int ARROW_W = 18;
	/** The pictures' shape (the 384x216 screenshots). */
	public static final int PICTURE_W = 384;
	public static final int PICTURE_H = 216;

	/**
	 * The book's boxes: {@code rail} (its heading over {@code railBody}) and its {@code railEntries} (Contents, then each
	 * chapter); the {@code stage}; the page {@code title}'s line; the {@code back} and {@code next} buttons and the page
	 * {@code count} between them (zero wide on a compact screen, where the lintel says it); {@code bottom}, the lowest the
	 * page's picture and words may reach.
	 */
	public record Book(int[] win, boolean compact, int[] plaque, int[] close, int[] lintel, int[] rail, int[] railBody, int[][] railEntries,
		int[] stage, int[] title, int[] back, int[] next, int[] count, int bottom, int[] whisper) {

		public int[][] buttons() {
			return new int[][]{back, next};
		}
	}

	/**
	 * The book on a screen {@code sw x sh}: a title {@code titleW} wide, the rail's widest label, {@code railCount} rail
	 * entries (Contents and the chapters), and the Back and Next buttons' widths.
	 */
	public static Book book(int sw, int sh, int titleW, int railLabelW, int railCount, int backW, int nextW) {
		int[] win = CopperLayout.window(sw, sh);
		boolean compact = CopperLayout.compact(sw, sh);
		int[] plaque = CopperLayout.plaque(win, titleW);
		int[] close = CopperLayout.close(win);
		int[] lintel = CopperLayout.lintel(win, plaque);
		int railW = CopperLayout.railWidth(win, Math.min(railLabelW, RAIL_LABEL_MAX), compact);
		int[] rail = CopperLayout.rail(win, railW);
		int[] railBody = compact ? rail : new int[]{rail[0], rail[1] + 12, rail[2], rail[3] - 12};
		int[][] entries = CopperLayout.railEntries(railBody, railCount, compact);
		int[] stage = CopperLayout.stage(win, railW);
		int right = stage[0] + stage[2];
		int stageBottom = stage[1] + stage[3];
		int[] back;
		int[] next;
		int[] count;
		int[] title;
		int bottom;
		if (compact) {
			next = new int[]{right - ARROW_W, stage[1], ARROW_W, BUTTON_H};
			back = new int[]{next[0] - 2 - ARROW_W, stage[1], ARROW_W, BUTTON_H};
			count = new int[]{back[0], stage[1], 0, BUTTON_H};
			title = new int[]{stage[0], stage[1], back[0] - 4 - stage[0], TITLE_H};
			bottom = stageBottom;
		} else {
			int bw = Math.min(backW, stage[2] / 3);
			int nw = Math.min(nextW, stage[2] / 3);
			back = new int[]{stage[0], stageBottom - BUTTON_H, bw, BUTTON_H};
			next = new int[]{right - nw, stageBottom - BUTTON_H, nw, BUTTON_H};
			int cx = back[0] + bw + 4;
			count = new int[]{cx, back[1], next[0] - 4 - cx, BUTTON_H};
			title = new int[]{stage[0], stage[1], stage[2], TITLE_H};
			bottom = back[1] - 4;
		}
		return new Book(win, compact, plaque, close, lintel, rail, railBody, entries, stage, title, back, next, count, bottom,
			CopperLayout.whisper(win));
	}

	/** How wide the page's words (and the contents' intro) are wrapped. */
	public static int textWidth(Book b) {
		return b.stage()[2] - 4;
	}

	/** A page's {@code picture} (16:9, centered, inside a 1-pixel frame) and its {@code text} of {@code lines} lines under it. */
	public record Page(int[] picture, int[] text) {
	}

	public static Page page(Book b, int lines) {
		int[] stage = b.stage();
		int top = stage[1] + TITLE_H + 2;
		int textH = lines * LINE;
		int room = b.bottom() - top - 4 - textH;
		int w = stage[2] - 2;
		int h = Math.max(0, Math.min(room, w * PICTURE_H / PICTURE_W));
		int pw = h * PICTURE_W / PICTURE_H;
		int[] picture = {stage[0] + (stage[2] - pw) / 2, top, pw, h};
		int[] text = {stage[0] + 2, top + h + 4, textWidth(b), textH};
		return new Page(picture, text);
	}

	/**
	 * The contents page: the {@code intro} ({@code introLines} lines), the chapters' heading and a row per chapter, in one
	 * column, or two ({@code columns}) when one doesn't fit.
	 */
	public record Contents(int[] intro, int[] head, int[][] rows, int columns) {
	}

	public static Contents contents(Book b, int introLines, int chapters) {
		int[] stage = b.stage();
		int top = stage[1] + TITLE_H + 2;
		int[] intro = {stage[0] + 2, top, textWidth(b), introLines * LINE};
		int[] head = {stage[0], intro[1] + intro[3] + 4, stage[2], 10};
		int rowsTop = head[1] + 12;
		int fit = Math.max(1, (b.bottom() - rowsTop) / ROW);
		int columns = chapters <= fit ? 1 : 2;
		int perColumn = Math.max(1, (chapters + columns - 1) / columns);
		int colW = (stage[2] - (columns - 1) * 4) / columns;
		int[][] rows = new int[chapters][];
		for (int i = 0; i < chapters; i++) {
			rows[i] = new int[]{stage[0] + (i / perColumn) * (colW + 4), rowsTop + (i % perColumn) * ROW, colW, ROW - 1};
		}
		return new Contents(intro, head, rows, columns);
	}

	/**
	 * What a left click at {@code mx, my} does to the {@code reader}: the rail's first entry opens the contents and the
	 * others their chapter's first page; Back and Next turn a page (when there is one that way); on the contents page a
	 * chapter's row opens it. {@code contents} is the contents page's layout (null when another page is open). Whether
	 * the click did something.
	 */
	public static boolean click(Book b, Contents contents, GuideBook.Reader reader, double mx, double my) {
		List<String> chapters = reader.chapters();
		int rail = CopperLayout.at(b.railEntries(), mx, my);
		if (rail == 0) {
			reader.go(0);
			return true;
		}
		if (rail > 0 && rail - 1 < chapters.size()) {
			return reader.openChapter(chapters.get(rail - 1));
		}
		if (reader.page() > 0 && CopperLayout.inside(b.back(), mx, my)) {
			return reader.turn(-1);
		}
		if (reader.page() < reader.pageCount() - 1 && CopperLayout.inside(b.next(), mx, my)) {
			return reader.turn(1);
		}
		if (reader.page() == 0 && contents != null) {
			int row = CopperLayout.at(contents.rows(), mx, my);
			if (row >= 0 && row < chapters.size()) {
				return reader.openChapter(chapters.get(row));
			}
		}
		return false;
	}

	private GuideLayout() {
	}
}
