package io.github.jcondedata.aliveworkplace.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Blueprint Table's look (ROADMAP 26.12), shared by the screens drawn like it: the Table itself, the Village Hall's
 * screen and the Storehouse board. A dark panel with a slate-blue edge, a gold title with a grey-blue count on the
 * right, a dark list with white names over grey-blue second lines, gold headings, framed icon cells that light up under
 * the mouse (gold when chosen) and vanilla's list scrollbar and selection box.
 */
public final class TableStyle {
	public static final int PANEL_BG = 0xE0101418;
	public static final int PANEL_EDGE = 0xFF3A4C66;
	public static final int LIST_BG = 0x80000000;
	/** A box drawn over the panel (the full materials list). */
	public static final int OVERLAY_BG = 0xF0161B22;
	public static final int TITLE = 0xFFFFD66B;
	public static final int SUBTITLE = 0xFF9AA8BA;
	public static final int NAME = 0xFFFFFFFF;
	public static final int SUBLINE = 0xFF7F8C9E;
	public static final int FAINT = 0xFF5E6B7C;
	public static final int LOADING = 0xFF777777;
	public static final int BODY = 0xFFCCCCCC;
	public static final int CELL_HOVER = 0xFF8A9BB4;
	public static final int CELL_BG = 0xFF1C2430;
	public static final int CELL_ON_BG = 0xFF4A3A14;
	/** A list row's height, as the Table's library. */
	public static final int ROW = 22;
	/** An icon cell (18 wide and a 2-pixel gap), as the Table's style row. */
	public static final int CELL = 20;
	/** The panel's largest size; it shrinks to fit the screen with 8 pixels round it. */
	public static final int MAX_W = 440;
	public static final int MAX_H = 290;

	private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("widget/scroller");
	private static final ResourceLocation SCROLLER_BACKGROUND = ResourceLocation.withDefaultNamespace("widget/scroller_background");

	/** The panel's width on a screen {@code screenWidth} wide. */
	public static int panelWidth(int screenWidth) {
		return Math.min(screenWidth - 16, MAX_W);
	}

	public static int panelHeight(int screenHeight) {
		return Math.min(screenHeight - 16, MAX_H);
	}

	/** The left list's width in a panel {@code panelW} wide. */
	public static int listWidth(int panelW) {
		return Math.min(180, panelW / 2 - 12);
	}

	/** The panel: its edge, then its background. */
	public static void panel(GuiGraphics g, int left, int top, int w, int h) {
		g.fill(left - 1, top - 1, left + w + 1, top + h + 1, PANEL_EDGE);
		g.fill(left, top, left + w, top + h, PANEL_BG);
	}

	/** The title in gold at the panel's top left and, if any, a grey-blue line at its top right. */
	public static void header(GuiGraphics g, Font font, Component title, Component right, int left, int top, int w) {
		int rw = right == null || right.getString().isEmpty() ? 0 : font.width(right);
		g.drawString(font, font.plainSubstrByWidth(title.getString(), w - 16 - (rw > 0 ? rw + 8 : 0)), left + 8, top + 8, TITLE, false);
		if (rw > 0) {
			g.drawString(font, right, left + w - 8 - rw, top + 8, SUBTITLE, false);
		}
	}

	/** A list's dark background. */
	public static void listBackground(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, LIST_BG);
	}

	/** A heading in gold. */
	public static void heading(GuiGraphics g, Font font, Component text, int x, int y) {
		g.drawString(font, text, x, y, TITLE, false);
	}

	/** A thin line in the panel's edge colour. */
	public static void rule(GuiGraphics g, int x, int y, int w) {
		g.fill(x, y, x + w, y + 1, PANEL_EDGE);
	}

	/** An 18x18 icon cell: framed in the edge colour, lighter under the mouse, gold with a brown inside when chosen. */
	public static void cell(GuiGraphics g, int x, int y, boolean on, boolean over) {
		g.fill(x, y, x + CELL - 2, y + CELL - 2, on ? TITLE : over ? CELL_HOVER : PANEL_EDGE);
		g.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, on ? CELL_ON_BG : CELL_BG);
	}

	/** Vanilla's list selection box round a row whose content starts at {@code top} and is {@code h} tall. */
	public static void selection(GuiGraphics g, int x, int top, int w, int h, boolean focused) {
		g.fill(x, top - 2, x + w, top + h + 2, focused ? 0xFFFFFFFF : 0xFF808080);
		g.fill(x + 1, top - 1, x + w - 1, top + h + 1, 0xFF000000);
	}

	/** A row's two lines, as the Table's library draws them: the name in white (or its own colours), the second line grey-blue. */
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

	/** Vanilla's list scrollbar, 6 wide, at {@code x}: {@code scroll} of {@code max} pixels scrolled through {@code content} tall. */
	public static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int max, int content) {
		if (max <= 0) {
			return;
		}
		int bar = Math.max(32, Math.min(h - 8, h * h / Math.max(1, content)));
		bar = Math.min(bar, h);
		int by = y + (h - bar) * scroll / max;
		g.blitSprite(SCROLLER_BACKGROUND, x, y, 6, h);
		g.blitSprite(SCROLLER, x, by, 6, bar);
	}

	/** A thin scrollbar for a box (the Table's full materials list). */
	public static void thinScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int max, int content) {
		if (max <= 0) {
			return;
		}
		int barH = Math.max(10, h * h / Math.max(1, content));
		int barY = y + (h - barH) * scroll / max;
		g.fill(x, y, x + 3, y + h, 0x40000000);
		g.fill(x, barY, x + 3, barY + barH, 0xFF8090A8);
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
	public static final int ROW_OVER = 0x30FFFFFF;
	public static final int ROW_ON = 0x40FFD66B;
	/** A text button's inside, lighter under the mouse; a gray (unusable) one's text. */
	public static final int BUTTON_BG = 0xFF1C2430;
	public static final int BUTTON_OVER_BG = 0xFF2A3546;
	public static final int OFF = 0xFF5E6B7C;

	/** How wide a text button with {@code label} is (at least 44 pixels). */
	public static int buttonWidth(Font font, Component label) {
		return Math.max(44, font.width(label) + 12);
	}

	/**
	 * A text button (the list's Teach, a footer's page arrow): a framed box in the edge color, lighter under the mouse,
	 * gold with a brown inside when {@code on}, gray text when {@code off}; its label centered.
	 */
	public static void button(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean on, boolean over, boolean off) {
		g.fill(x, y, x + w, y + h, on ? TITLE : over && !off ? CELL_HOVER : PANEL_EDGE);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? CELL_ON_BG : over && !off ? BUTTON_OVER_BG : BUTTON_BG);
		String text = trim(font, label.getString(), w - 6);
		g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, off ? OFF : on ? TITLE : NAME, false);
	}

	/** How wide a tab with {@code label} (and an icon, if {@code icon}) is, at most {@code max}. */
	public static int tabWidth(Font font, Component label, boolean icon, int max) {
		return Math.min(max, font.width(label) + 12 + (icon ? 18 : 0));
	}

	/**
	 * A tab over a list (the Tutor's party): a framed box {@link #TAB_H} tall, gold when it's the open one, lighter under
	 * the mouse; its label (cut to fit) after the icon's room when {@code iconRoom}. The caller draws the icon at
	 * {@code x + 3, y + 1}.
	 */
	public static void tab(GuiGraphics g, Font font, Component label, int x, int y, int w, boolean iconRoom, boolean on, boolean over) {
		g.fill(x, y, x + w, y + TAB_H, on ? TITLE : over ? CELL_HOVER : PANEL_EDGE);
		g.fill(x + 1, y + 1, x + w - 1, y + TAB_H - 1, on ? CELL_ON_BG : over ? BUTTON_OVER_BG : BUTTON_BG);
		int tx = x + 6 + (iconRoom ? 16 : 0);
		g.drawString(font, trim(font, label.getString(), x + w - 4 - tx), tx, y + 5, on ? TITLE : NAME, false);
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
		if (on) {
			g.fill(columns[0] - 2, y, x1, y + LIST_ROW, ROW_ON);
		} else if (over) {
			g.fill(columns[0] - 2, y, x1, y + LIST_ROW, ROW_OVER);
		}
		int ty = y + 6;
		cell(g, font, name, columns[1], ty, columns[2] - columns[1] - 6, off, NAME);
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

	/** The footer hint at a panel's foot ({@code y}), in faint grey-blue, cut to {@code w}. */
	public static void footerHint(GuiGraphics g, Font font, Component hint, int x, int y, int w) {
		g.drawString(font, trim(font, hint.getString(), w), x, y, FAINT, false);
	}

	/** The money line, right-aligned to {@code right} at the panel's top ({@code y}); returns its width (for pointing at it). */
	public static int money(GuiGraphics g, Font font, Component money, int right, int y) {
		int w = font.width(money);
		g.drawString(font, money, right - w, y, BODY, false);
		return w;
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
	public static final int BAR_BG = 0xFF0C1016;

	/** A card's top line: {@code title} in gold at the left, {@code tag} in grey-blue right-aligned, both cut to fit. */
	public static void cardHeader(GuiGraphics g, Font font, Component title, Component tag, int x, int y, int w) {
		int tagW = tag.getString().isEmpty() ? 0 : Math.min(font.width(tag), w / 2);
		g.drawString(font, trim(font, title, w - tagW - (tagW > 0 ? 8 : 0)), x, y, TITLE, false);
		if (tagW > 0) {
			String text = trim(font, tag.getString(), tagW);
			g.drawString(font, text, x + w - font.width(text), y, SUBTITLE, false);
		}
	}

	/** A thin bar {@code fraction} full (0 to 1) in {@code color}, framed in the edge color. */
	public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
		g.fill(x, y, x + w, y + h, PANEL_EDGE);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BAR_BG);
		int inner = Math.round((w - 2) * Math.max(0f, Math.min(1f, fraction)));
		if (inner > 0) {
			g.fill(x + 1, y + 1, x + 1 + inner, y + h - 1, color);
		}
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
