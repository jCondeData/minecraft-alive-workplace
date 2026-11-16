package io.github.jcondedata.aliveworkplace.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Hall look's shared pieces, drawn in Copperline since ROADMAP 47.1 ({@link CopperStyle}, docs/design/copperline/):
 * the Blueprint Table, the Village Hall's screen, the Storehouse board, the list menus, the Trainer screen, the status
 * cards and the Pokémon Trader all draw through here, so every one of them switched at once. The window with its copper
 * line, the copper plaque over its top edge naming the screen, the lintel (where you are or the coins, and the close X),
 * wells for lists, Deepslate rows that take a Lantern edge under the mouse and the copper mark when chosen, the neutral
 * and copper buttons, and the whisper line at the foot with "Esc close". The names of the colors below are the old ones
 * (the screens use them); their values are the Copperline palette's.
 */
public final class TableStyle {
	public static final int PANEL_BG = CopperStyle.DEEP_900;
	/** Lines and frames (Deepslate 600). */
	public static final int PANEL_EDGE = CopperStyle.DEEP_600;
	public static final int LIST_BG = CopperStyle.DEEP_800;
	/** A box drawn over the panel (the full materials list). */
	public static final int OVERLAY_BG = CopperStyle.DEEP_800;
	/** Titles and headings, and the chosen thing's text: Copper Light. */
	public static final int TITLE = CopperStyle.COPPER_LIGHT;
	public static final int SUBTITLE = CopperStyle.TUFF;
	public static final int NAME = CopperStyle.QUARTZ;
	public static final int SUBLINE = CopperStyle.TUFF;
	public static final int FAINT = CopperStyle.TUFF;
	public static final int LOADING = CopperStyle.TUFF;
	public static final int BODY = CopperStyle.QUARTZ;
	/** Under the mouse: the Lantern edge. */
	public static final int CELL_HOVER = CopperStyle.LANTERN;
	public static final int CELL_BG = CopperStyle.DEEP_800;
	public static final int CELL_ON_BG = CopperStyle.DEEP_600;
	/** A list row's height, as the Table's library. */
	public static final int ROW = 22;
	/** An icon cell (18 wide and a 2-pixel gap), as the Table's style row. */
	public static final int CELL = 20;
	/** The panel's largest size; it shrinks to fit the screen with 8 pixels round it. */
	public static final int MAX_W = io.github.jcondedata.aliveworkplace.ui.CopperLayout.MAX_W;
	public static final int MAX_H = io.github.jcondedata.aliveworkplace.ui.CopperLayout.MAX_H;

	/** The panel's width on a screen {@code screenWidth} wide. */
	public static int panelWidth(int screenWidth) {
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.windowWidth(screenWidth);
	}

	public static int panelHeight(int screenHeight) {
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.windowHeight(screenHeight);
	}

	/** The left list's width in a panel {@code panelW} wide. */
	public static int listWidth(int panelW) {
		return Math.min(180, panelW / 2 - 12);
	}

	/** Whether the game's screen is a small one (GUI scale 3 on a 960x540 window: the 320x180 rule). */
	public static boolean compact() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		return mc != null && mc.getWindow() != null
			&& io.github.jcondedata.aliveworkplace.ui.CopperLayout.compact(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
	}

	/** The panel: Copperline's window. */
	public static void panel(GuiGraphics g, int left, int top, int w, int h) {
		CopperStyle.window(g, left, top, w, h);
	}

	/** The title on the copper plaque over the panel's top edge and, if any, {@code right} on the lintel; the close X at its end. */
	public static void header(GuiGraphics g, Font font, Component title, Component right, int left, int top, int w) {
		header(g, font, title, right, left, top, w, false);
	}

	/** {@link #header} with the close X lit when the mouse is over it ({@link #overClose}). */
	public static void header(GuiGraphics g, Font font, Component title, Component right, int left, int top, int w, boolean overClose) {
		int[] win = {left, top, w, 0};
		int[] plaque = CopperStyle.plaque(g, font, title, win);
		CopperStyle.lintel(g, font, win, plaque, right, overClose);
	}

	/** The close X's box on a panel at {@code left, top}, {@code w} wide. */
	public static int[] closeBox(int left, int top, int w) {
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.close(new int[]{left, top, w, 0});
	}

	public static boolean overClose(int left, int top, int w, double mx, double my) {
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.inside(closeBox(left, top, w), mx, my);
	}

	/** Where the lintel's text ends on the right (the close X's left, less a gap). */
	public static int lintelRight(int left, int top, int w) {
		return closeBox(left, top, w)[0] - 4;
	}

	/** A list's background: a well. */
	public static void listBackground(GuiGraphics g, int x, int y, int w, int h) {
		CopperStyle.well(g, x, y, w, h);
	}

	/** A heading in Copper Light. */
	public static void heading(GuiGraphics g, Font font, Component text, int x, int y) {
		g.drawString(font, text, x, y, TITLE, false);
	}

	/** A thin line in Deepslate 600. */
	public static void rule(GuiGraphics g, int x, int y, int w) {
		g.fill(x, y, x + w, y + 1, PANEL_EDGE);
	}

	/** An 18x18 icon cell: a well, a Lantern edge under the mouse, a panel with the copper mark when chosen. */
	public static void cell(GuiGraphics g, int x, int y, boolean on, boolean over) {
		int s = CELL - 2;
		if (on) {
			g.blitSprite(CopperStyle.PANEL, x, y, s, s);
			g.fill(x + 1, y + s - 2, x + s - 1, y + s, CopperStyle.COPPER);
		} else {
			g.blitSprite(over ? CopperStyle.WELL_FOCUS : CopperStyle.WELL, x, y, s, s);
		}
	}

	/** The chosen row's band round a row whose content starts at {@code top} and is {@code h} tall: Deepslate 600, the copper mark. */
	public static void selection(GuiGraphics g, int x, int top, int w, int h, boolean focused) {
		CopperStyle.rowBand(g, x, top - 2, w, h + 4, false, false, true);
	}

	/** A row under the mouse: Deepslate 700 with the Lantern edge. */
	public static void hoverBand(GuiGraphics g, int x, int top, int w, int h) {
		CopperStyle.rowBand(g, x, top, w, h, false, true, false);
	}

	/** A row's two lines: the name in Quartz (or its own colors), the second line in Tuff. */
	public static void rowText(GuiGraphics g, Font font, Component name, String second, int x, int y, int w) {
		g.drawString(font, trim(font, name, w), x, y + 1, NAME, false);
		if (second != null && !second.isEmpty()) {
			g.drawString(font, trim(font, second, w), x, y + 10, SUBLINE, false);
		}
	}

	/** {@code text} cut to {@code w} pixels with an ellipsis. */
	public static String trim(Font font, String text, int w) {
		if (font.width(text) <= w) {
			return text;
		}
		return font.plainSubstrByWidth(text, w - font.width("…")) + "…";
	}

	/** {@code text} cut to {@code w} pixels, keeping its colours. */
	public static net.minecraft.util.FormattedCharSequence trim(Font font, Component text, int w) {
		if (font.width(text) <= w) {
			return text.getVisualOrderText();
		}
		String ellipsis = "…";
		net.minecraft.network.chat.FormattedText cut = font.substrByWidth(text, w - font.width(ellipsis));
		return net.minecraft.locale.Language.getInstance().getVisualOrder(
			net.minecraft.network.chat.FormattedText.composite(cut, net.minecraft.network.chat.FormattedText.of(ellipsis)));
	}

	/** The list scroll bar in the 6 pixels at {@code x}: {@code scroll} of {@code max} pixels scrolled through {@code content} tall. */
	public static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int max, int content) {
		if (max <= 0) {
			return;
		}
		int bar = Math.max(32, Math.min(h - 8, h * h / Math.max(1, content)));
		bar = Math.min(bar, h);
		int by = y + (h - bar) * scroll / max;
		g.fill(x + 1, y + 1, x + 5, y + h - 1, CopperStyle.VOID);
		g.fill(x + 1, by + 1, x + 5, by + bar - 1, CopperStyle.DEEP_600);
		g.fill(x + 1, by + 1, x + 2, by + bar - 1, CopperStyle.SLATE);
	}

	/** A thin scrollbar for a box (the Table's full materials list). */
	public static void thinScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int max, int content) {
		if (max <= 0) {
			return;
		}
		int barH = Math.max(10, h * h / Math.max(1, content));
		int barY = y + (h - barH) * scroll / max;
		g.fill(x, y, x + 3, y + h, CopperStyle.VOID);
		g.fill(x, barY, x + 3, barY + barH, CopperStyle.DEEP_600);
		g.fill(x, barY, x + 1, barY + barH, CopperStyle.SLATE);
	}

	// --- shared list-menu pieces (ROADMAP 42.1) ----------------------------------------------------------------------
	// The list screen's parts ({@link ChoiceTableScreen#listMode}), for every screen drawn in this look that shows a list
	// with buttons: the Move Tutor's today, the other menus (42.2 to 42.4) and the Trainer screen (lanes D and E) next.
	// Each draws in the box given and returns nothing it doesn't have to; the caller lays out and takes the clicks.

	/** A list-menu row's height (one line of text and a 16-pixel icon). */
	public static final int LIST_ROW = 20;
	/** A text button's height. */
	public static final int BUTTON_H = 14;
	/** A tab's height. */
	public static final int TAB_H = 18;
	/** A row under the mouse, and a chosen row (waiting for the second click), behind its text. */
	public static final int ROW_OVER = CopperStyle.DEEP_700;
	public static final int ROW_ON = CopperStyle.DEEP_600;
	/** A text button's inside, lighter under the mouse; a gray (unusable) one's text. */
	public static final int BUTTON_BG = CopperStyle.DEEP_700;
	public static final int BUTTON_OVER_BG = CopperStyle.DEEP_600;
	public static final int OFF = CopperStyle.OFF_TEXT;

	/** How wide a text button with {@code label} is (at least 44 pixels). */
	public static int buttonWidth(Font font, Component label) {
		return Math.max(44, font.width(label) + 12);
	}

	/**
	 * A text button (the list's Teach, a footer's page arrow): Copperline's neutral button, the Lantern edge under the
	 * mouse, the copper button when {@code on} (the chosen one), flat with gray text when {@code off}; its label centered.
	 */
	public static void button(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean on, boolean over, boolean off) {
		CopperStyle.button(g, font, label, null, x, y, w, h, on ? CopperStyle.Kind.COPPER : CopperStyle.Kind.NEUTRAL, over && !off, false, off);
	}

	/** How wide a tab with {@code label} (and an icon, if {@code icon}) is, at most {@code max}. */
	public static int tabWidth(Font font, Component label, boolean icon, int max) {
		return Math.min(max, font.width(label) + 12 + (icon ? 18 : 0));
	}

	/**
	 * A tab over a list (the Tutor's party): {@link #TAB_H} tall, the open one a panel with the copper mark, the Lantern
	 * edge under the mouse; its label (cut to fit) after the icon's room when {@code iconRoom}. The caller draws the icon at
	 * {@code x + 3, y + 1}.
	 */
	public static void tab(GuiGraphics g, Font font, Component label, int x, int y, int w, boolean iconRoom, boolean on, boolean over) {
		CopperStyle.tab(g, x, y, w, TAB_H, on, over);
		int tx = x + 6 + (iconRoom ? 16 : 0);
		g.drawString(font, trim(font, label.getString(), x + w - 4 - tx), tx, y + 5, on ? CopperStyle.QUARTZ : CopperStyle.TUFF, on);
	}

	/**
	 * Where a list-menu row's columns start in a list from {@code x}, {@code w} wide, with buttons {@code buttonW} wide
	 * (0: none): the icon, the name, the second line, the price and the button.
	 */
	public static int[] listColumns(int x, int w, int buttonW) {
		int button = buttonW > 0 ? x + w - 10 - buttonW : x + w - 6;
		int inner = button - (x + 26);
		return new int[]{x + 4, x + 26, x + 26 + inner * 46 / 100, x + 26 + inner * 72 / 100, button};
	}

	/** The column headings over a list-menu's rows, in the second line's grey-blue, at the columns {@link #listColumns} gave. */
	public static void columnHeadings(GuiGraphics g, Font font, java.util.List<Component> headings, int[] columns, int y) {
		for (int i = 0; i < headings.size() && i + 1 < columns.length - 1; i++) {
			int w = columns[i + 2] - columns[i + 1] - 6;
			g.drawString(font, trim(font, headings.get(i).getString(), w), columns[i + 1], y, SUBTITLE, false);
		}
	}

	/**
	 * A list-menu row's band and text ({@code y} its top, {@link #LIST_ROW} tall): gold behind it when {@code on}, light
	 * under the mouse; the name in white (or its own colors), the second line and the price at their columns in their own
	 * colors (grey-blue where they have none); everything gray when {@code off}. The caller draws the icon and the button.
	 */
	public static void listRow(GuiGraphics g, Font font, int[] columns, int x1, int y, Component name, Component second, Component price,
		boolean off, boolean on, boolean over) {
		CopperStyle.rowBand(g, columns[0] - 2, y, x1 - columns[0] + 2, LIST_ROW, false, over && !on, on);
		int ty = y + 6;
		// A row with no second line and no price has the room up to the button for its name (42.4: the Shape Planner's rows).
		boolean alone = (second == null || second.getString().isEmpty()) && (price == null || price.getString().isEmpty());
		cell(g, font, name, columns[1], ty, (alone ? columns[4] : columns[2]) - columns[1] - 6, off, NAME);
		cell(g, font, second, columns[2], ty, columns[3] - columns[2] - 6, off, SUBLINE);
		cell(g, font, price, columns[3], ty, columns[4] - columns[3] - 6, off, BODY);
	}

	private static void cell(GuiGraphics g, Font font, Component text, int x, int y, int w, boolean off, int color) {
		if (text == null || text.getString().isEmpty() || w <= 0) {
			return;
		}
		if (off) {
			g.drawString(font, trim(font, text.getString(), w), x, y, OFF, false);
		} else {
			g.drawString(font, trim(font, text, w), x, y, color, false);
		}
	}

	/** The whisper line at a panel's foot ({@code y}): {@code hint} on the left, cut to fit, and "Esc close" on the right of {@code w}. */
	public static void footerHint(GuiGraphics g, Font font, Component hint, int x, int y, int w) {
		CopperStyle.whisper(g, font, hint, x, y, w, compact());
	}

	/** The money line, right-aligned to {@code right} at the panel's top ({@code y}); returns its width (for pointing at it). */
	public static int money(GuiGraphics g, Font font, Component money, int right, int y) {
		int w = font.width(money);
		g.blitSprite(CopperStyle.glyph("coin"), right - w - 10, y, 8, 8);
		g.drawString(font, money, right - w, y, BODY, false);
		return w + 10;
	}

	// --- shared small-card pieces (ROADMAP 43.3; 42.5's job-status cards reuse them) -----------------------------------
	// A small card ({@link StatusCardScreen}, as {@code docs/design/M42/trainer_c.png}): the name in gold with a tag on the
	// right, a grey-blue line, a gold heading over labeled lines with an icon, a value and a bar, then a button row
	// ({@link #button}) and a footer hint ({@link #footerHint}). The caller lays out and takes the clicks.

	/** A small card's width, its padding and a labeled line's height (a 16-pixel icon, its text and a bar under it). */
	public static final int CARD_W = 200;
	public static final int CARD_PAD = 8;
	public static final int CARD_LINE = 18;
	/** A bar's empty part. */
	public static final int BAR_BG = CopperStyle.DEEP_900;

	/** A card's top line: {@code title} in gold at the left, {@code tag} in grey-blue right-aligned, both cut to fit. */
	public static void cardHeader(GuiGraphics g, Font font, Component title, Component tag, int x, int y, int w) {
		int tagW = tag.getString().isEmpty() ? 0 : Math.min(font.width(tag), w / 2);
		g.drawString(font, trim(font, title, w - tagW - (tagW > 0 ? 8 : 0)), x, y, TITLE, true);
		if (tagW > 0) {
			String text = trim(font, tag.getString(), tagW);
			g.drawString(font, text, x + w - font.width(text), y, SUBTITLE, false);
		}
	}

	/** A thin bar {@code fraction} full (0 to 1) in {@code color}: Copperline's progress bar. */
	public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
		CopperStyle.progress(g, x, y, w, h, fraction, color);
	}

	/**
	 * A card's labeled line ({@code y} its top, {@link #CARD_LINE} tall): the icon (if any) in a framed cell, the label in
	 * white, the value right-aligned in {@code color}, and when {@code bar} is 0 to 1 a bar that full under the text.
	 */
	public static void cardLine(GuiGraphics g, Font font, net.minecraft.world.item.ItemStack icon, Component label, Component value, int color,
		float bar, int x, int y, int w) {
		int tx = x;
		if (!icon.isEmpty()) {
			g.renderItem(icon, x, y + 1);
			tx = x + 20;
		}
		int valueW = Math.min(font.width(value), (x + w - tx) / 2);
		int textY = bar >= 0 ? y + 2 : y + 5;
		g.drawString(font, trim(font, label, x + w - tx - valueW - 6), tx, textY, NAME, false);
		String v = trim(font, value.getString(), valueW);
		g.drawString(font, v, x + w - font.width(v), textY, color, false);
		if (bar >= 0) {
			bar(g, tx, y + 12, x + w - tx, 4, bar, color);
		}
	}

	private TableStyle() {
	}
}
