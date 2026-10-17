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

	private TableStyle() {
	}
}
