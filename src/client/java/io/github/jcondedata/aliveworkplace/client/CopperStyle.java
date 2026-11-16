package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Copperline (ROADMAP 47.1, docs/design/copperline/): the palette, the sprites and every shared part, drawn the same on
 * every screen. {@link TableStyle} (and so every screen in the Hall's look) draws through it. The parts are the ones on
 * {@code boards/Components.png}: buttons, rail tabs, list rows, chips and tags, bars, toggles, the slider, the number and
 * text fields, the scroll bar, item slots, the confirm dialog, the "What happens" and refusal boxes, the empty state, the
 * tooltip and the toast; and the shell (plaque, lintel, rail, stage, whisper line) whose places
 * {@link CopperLayout} works out. Each draws in the box given; the caller lays out and takes the clicks.
 *
 * <p>The rules: copper is rationed (one main button per screen, plus the mark on the current rail entry or row); hover
 * always adds a Lantern edge; every status is a glyph, a word and a color; text is the game's font, shadowed on titles,
 * button labels, tooltips and toasts, plain on panel body text; cut-off text ends with an ellipsis.
 */
public final class CopperStyle {
	// --- the palette (README) ----------------------------------------------------------------------------------------
	public static final int VOID = 0xFF0B0C10;
	public static final int DEEP_900 = 0xFF16171D;
	public static final int DEEP_800 = 0xFF22242E;
	public static final int DEEP_700 = 0xFF2E313D;
	public static final int DEEP_600 = 0xFF3D4150;
	public static final int SLATE = 0xFF50556A;
	public static final int TUFF = 0xFFA3A8B8;
	public static final int QUARTZ = 0xFFF2EFE6;
	public static final int COPPER = 0xFFE0864A;
	public static final int COPPER_LIGHT = 0xFFF7B47C;
	public static final int COPPER_DARK = 0xFF9C4F2B;
	public static final int LANTERN = 0xFFFFD25E;
	public static final int VERDIGRIS = 0xFF4FC3A1;
	public static final int REDSTONE = 0xFFFF6B5E;
	public static final int LAPIS = 0xFF7FA8FF;
	public static final int AMETHYST = 0xFFC59BFF;
	/** Disabled text: Tuff at 70% over Deepslate 900 (about 4:1, README). */
	public static final int OFF_TEXT = 0xFF7A7F8E;
	/** Every Copperline color, in the README's order (for the parts screen and the tests' palette check). */
	public static final int[] PALETTE = {VOID, DEEP_900, DEEP_800, DEEP_700, DEEP_600, SLATE, TUFF, QUARTZ, COPPER, COPPER_LIGHT,
		COPPER_DARK, LANTERN, VERDIGRIS, REDSTONE, LAPIS, AMETHYST};

	/** A status: its glyph, its color and the word's role (good Verdigris, bad Redstone, info Lapis, rare Amethyst, warning Lantern). */
	public enum Status {
		GOOD("good", VERDIGRIS), BAD("bad", REDSTONE), INFO("info", LAPIS), RARE("star", AMETHYST), WARNING("warning", LANTERN);

		public final ResourceLocation glyph;
		public final int color;

		Status(String glyph, int color) {
			this.glyph = glyph(glyph);
			this.color = color;
		}
	}

	// --- the sprites (tools/textures/art/copperline.py) ---------------------------------------------------------------
	public static final ResourceLocation WINDOW = sprite("window");
	public static final ResourceLocation PLAQUE = sprite("plaque");
	public static final ResourceLocation PANEL = sprite("panel");
	public static final ResourceLocation WELL = sprite("well");
	public static final ResourceLocation WELL_FOCUS = sprite("well_focus");
	public static final ResourceLocation WELL_ERROR = sprite("well_error");
	public static final ResourceLocation TOOLTIP = sprite("tooltip");
	public static final ResourceLocation TOAST = sprite("toast");
	public static final ResourceLocation BUTTON = sprite("button");
	public static final ResourceLocation BUTTON_HOVER = sprite("button_hover");
	public static final ResourceLocation BUTTON_PRESSED = sprite("button_pressed");
	public static final ResourceLocation BUTTON_OFF = sprite("button_off");
	public static final ResourceLocation COPPER_BUTTON = sprite("copper");
	public static final ResourceLocation COPPER_HOVER = sprite("copper_hover");
	public static final ResourceLocation COPPER_PRESSED = sprite("copper_pressed");
	public static final ResourceLocation DANGER = sprite("danger");
	/** The ten Hall section icons, in HallSections.Section's order. */
	public static final List<String> SECTION_ICONS = List.of("overview", "people", "homes", "buildings", "defense", "quests", "trade",
		"calendar", "laws", "map");

	public static ResourceLocation sprite(String name) {
		return AliveWorkplace.id("copperline/" + name);
	}

	/** An 8 px status glyph (up, down, steady, good, bad, warning, lock, star, coin, info, plus, minus, more, ...). */
	public static ResourceLocation glyph(String name) {
		return sprite("glyph/" + name);
	}

	/** A 16 px icon (the Hall's sections, the coin). */
	public static ResourceLocation icon(String name) {
		return sprite("icon/" + name);
	}

	public static void glyph(GuiGraphics g, String name, int x, int y) {
		g.blitSprite(glyph(name), x, y, 8, 8);
	}

	public static void icon(GuiGraphics g, String name, int x, int y, int size) {
		g.blitSprite(icon(name), x, y, size, size);
	}

	// --- text -------------------------------------------------------------------------------------------------------

	/** {@code text} cut to {@code w} pixels, ending with an ellipsis. */
	public static String trim(Font font, String text, int w) {
		if (font.width(text) <= w) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, w - font.width("…"))) + "…";
	}

	/** A line of text with a shadow (titles, button labels, tooltips, toasts), cut to {@code w}. */
	public static void label(GuiGraphics g, Font font, String text, int x, int y, int w, int color) {
		g.drawString(font, trim(font, text, w), x, y, color, true);
	}

	// --- the shell ------------------------------------------------------------------------------------------------------

	/** The window: the Void outline, the copper line along its top and the Deepslate 900 inside. */
	public static void window(GuiGraphics g, int x, int y, int w, int h) {
		g.blitSprite(WINDOW, x - 2, y - 2, w + 4, h + 4);
	}

	/** The plaque overhanging the window's top edge, with the screen's title in Quartz on copper. Returns its box. */
	public static int[] plaque(GuiGraphics g, Font font, Component title, int[] win) {
		int[] p = CopperLayout.plaque(win, font.width(title));
		g.blitSprite(PLAQUE, p[0], p[1], p[2], p[3]);
		String text = trim(font, title.getString(), p[2] - 14);
		g.drawString(font, text, p[0] + 7, p[1] + 5, VOID, false);
		g.drawString(font, text, p[0] + 6, p[1] + 4, QUARTZ, true);
		return p;
	}

	/** The lintel: a dark bar under the plaque, the text at its right ({@code right}, where you are or the coins) and the close X. */
	public static void lintel(GuiGraphics g, Font font, int[] win, int[] plaque, Component right, boolean overClose) {
		int[] l = CopperLayout.lintel(win, plaque);
		g.fill(win[0], l[1] + l[3], win[0] + win[2], l[1] + l[3] + 1, DEEP_600);
		if (right != null && !right.getString().isEmpty() && l[2] > 8) {
			String text = trim(font, right.getString(), l[2]);
			g.drawString(font, text, l[0] + l[2] - font.width(text), l[1] + 4, TUFF, false);
		}
		close(g, CopperLayout.close(win), overClose);
	}

	/** The close X: a small danger-tinted square, its edge Lantern under the mouse. */
	public static void close(GuiGraphics g, int[] box, boolean over) {
		g.blitSprite(over ? BUTTON_HOVER : BUTTON, box[0], box[1], box[2], box[3]);
		int cx = box[0] + (box[2] - 8) / 2;
		int cy = box[1] + (box[3] - 8) / 2;
		g.blitSprite(glyph("bad"), cx, cy, 8, 8);
	}

	/**
	 * The whisper line: what the hovered thing does, in plain words, on the left; the key hint ("Esc close") on the right.
	 * On a compact screen the key hint keeps only its key.
	 */
	public static void whisper(GuiGraphics g, Font font, Component text, int x, int y, int w, boolean compact) {
		g.fill(x - 2, y - 2, x + w + 2, y + 10, DEEP_900);
		g.fill(x - 2, y - 3, x + w + 2, y - 2, DEEP_600);
		Component key = Component.translatable("screen.aliveworkplace.copperline.key.esc");
		Component word = Component.translatable("screen.aliveworkplace.copperline.key.close");
		int keyW = font.width(key) + 6;
		int hintW = keyW + (compact ? 0 : 4 + font.width(word));
		int kx = x + w - hintW;
		boolean room = kx > x + 40;
		int textW = room ? kx - 6 - x : w;
		if (text != null && !text.getString().isEmpty()) {
			g.drawString(font, trim(font, text.getString(), textW), x, y, TUFF, false);
		}
		if (room) {
			g.fill(kx, y - 2, kx + keyW, y + 9, DEEP_700);
			g.fill(kx, y + 9, kx + keyW, y + 10, VOID);
			g.drawString(font, key, kx + 3, y, QUARTZ, false);
			if (!compact) {
				g.drawString(font, word, kx + keyW + 4, y, TUFF, false);
			}
		}
	}

	/**
	 * A rail entry: an icon, a label and a count (0: none) in a row; under the mouse a Lantern edge on its left; the current
	 * one Deepslate 700 with the copper mark. On a compact rail only the icon, in a cell.
	 */
	public static void railEntry(GuiGraphics g, Font font, int[] box, String icon, ItemStack item, Component label, int count,
		boolean current, boolean over, boolean compact) {
		int x = box[0];
		int y = box[1];
		int w = box[2];
		int h = box[3];
		if (current) {
			g.fill(x, y, x + w, y + h, DEEP_700);
			g.fill(x, y, x + 2, y + h, COPPER);
		} else if (over) {
			g.fill(x, y, x + w, y + h, DEEP_800);
			g.fill(x, y, x + 2, y + h, LANTERN);
		}
		int size = Math.max(8, Math.min(16, h - 2));
		int ix = compact ? x + (w - size) / 2 + 1 : x + 5;
		int iy = y + (h - size) / 2;
		if (icon != null) {
			g.blitSprite(icon(icon), ix, iy, size, size);
		} else if (item != null && !item.isEmpty()) {
			g.pose().pushPose();
			g.pose().translate(ix, iy, 0);
			g.pose().scale(size / 16f, size / 16f, 1f);
			g.renderItem(item, 0, 0);
			g.pose().popPose();
		}
		if (compact) {
			if (count > 0) {
				String n = count > 9 ? "9+" : String.valueOf(count);
				g.drawString(font, n, x + w - font.width(n), y, LANTERN, true);
			}
			return;
		}
		int tx = ix + size + 4;
		String n = count > 0 ? String.valueOf(count) : "";
		int nw = n.isEmpty() ? 0 : font.width(n) + 4;
		g.drawString(font, trim(font, label.getString(), x + w - 3 - nw - tx), tx, y + (h - 8) / 2, current ? QUARTZ : over ? QUARTZ : TUFF, current);
		if (!n.isEmpty()) {
			g.drawString(font, n, x + w - 3 - font.width(n), y + (h - 8) / 2, LANTERN, true);
		}
	}

	// --- buttons ------------------------------------------------------------------------------------------------------

	public enum Kind { NEUTRAL, COPPER, DANGER }

	/** A button's sprite for its kind and state (pressed: held down or the chosen one). */
	public static ResourceLocation buttonSprite(Kind kind, boolean over, boolean pressed, boolean off) {
		if (off) {
			return BUTTON_OFF;
		}
		return switch (kind) {
			case COPPER -> pressed ? COPPER_PRESSED : over ? COPPER_HOVER : COPPER_BUTTON;
			case DANGER -> over ? BUTTON_HOVER : DANGER;
			default -> pressed ? BUTTON_PRESSED : over ? BUTTON_HOVER : BUTTON;
		};
	}

	/**
	 * A button with its label centered (shadowed; dark on copper and danger), an optional 8 px glyph before it, cut to fit.
	 * Off: flat with gray text.
	 */
	public static void button(GuiGraphics g, Font font, Component label, String glyph, int x, int y, int w, int h, Kind kind,
		boolean over, boolean pressed, boolean off) {
		if (kind == Kind.DANGER && over && !off) {
			g.blitSprite(DANGER, x, y, w, h);
			g.renderOutline(x, y, w, h, LANTERN);
		} else {
			g.blitSprite(buttonSprite(kind, over, pressed, off), x, y, w, h);
		}
		int gw = glyph == null ? 0 : 10;
		String text = label == null ? "" : trim(font, label.getString(), w - 6 - gw);
		int tw = gw + font.width(text) - (text.isEmpty() && gw > 0 ? 2 : 0);
		int tx = x + (w - tw) / 2;
		int ty = y + (h - 8) / 2 + (pressed && !off ? 1 : 0);
		if (glyph != null) {
			g.blitSprite(glyph(glyph), tx, ty, 8, 8);
		}
		if (text.isEmpty()) {
			return;
		}
		if (off) {
			g.drawString(font, text, tx + gw, ty, OFF_TEXT, false);
		} else if (kind == Kind.NEUTRAL) {
			g.drawString(font, text, tx + gw, ty, QUARTZ, true);
		} else {
			g.drawString(font, text, tx + gw, ty, VOID, false);
		}
	}

	// --- lists, tabs, chips, bars -------------------------------------------------------------------------------------

	/** A list's column header row: Tuff words over a Deepslate 600 rule. */
	public static void headerRow(GuiGraphics g, int x, int y, int w) {
		g.fill(x, y + 11, x + w, y + 12, DEEP_600);
	}

	/**
	 * A list row's band: normal (every other one a shade lighter when {@code stripe}), hover (Deepslate 700 with a Lantern
	 * edge on its left), selected (Deepslate 600 with the copper mark and a copper frame), locked (nothing; the caller draws
	 * the lock and gray text).
	 */
	public static void rowBand(GuiGraphics g, int x, int y, int w, int h, boolean stripe, boolean over, boolean selected) {
		if (selected) {
			g.fill(x, y, x + w, y + h, DEEP_600);
			g.renderOutline(x, y, w, h, COPPER_DARK);
			g.fill(x, y, x + 2, y + h, COPPER);
		} else if (over) {
			g.fill(x, y, x + w, y + h, DEEP_700);
			g.fill(x, y, x + 2, y + h, LANTERN);
		} else if (stripe) {
			g.fill(x, y, x + w, y + h, DEEP_800);
		}
	}

	/** A tab over a list: a button-shaped box, the open one copper-marked underneath, Lantern edged under the mouse. */
	public static void tab(GuiGraphics g, int x, int y, int w, int h, boolean open, boolean over) {
		g.blitSprite(open ? PANEL : over ? BUTTON_HOVER : BUTTON, x, y, w, h);
		if (open) {
			g.fill(x + 1, y + h - 2, x + w - 1, y + h, COPPER);
		}
	}

	/** A chip or tag: a word in its color on a darker fill (HOME, READY, LOW); returns its width. */
	public static int chip(GuiGraphics g, Font font, String text, int x, int y, int color) {
		int w = font.width(text) + 6;
		g.fill(x, y, x + w, y + 11, DEEP_600);
		g.fill(x, y + 10, x + w, y + 11, VOID);
		g.drawString(font, text, x + 3, y + 2, color, false);
		return w;
	}

	/** A coin count chip: the coin glyph and the number in Quartz in a sunk box; returns its width. */
	public static int coins(GuiGraphics g, Font font, String amount, int x, int y) {
		int w = font.width(amount) + 15;
		g.blitSprite(WELL, x, y, w, 12);
		g.blitSprite(glyph("coin"), x + 3, y + 2, 8, 8);
		g.drawString(font, amount, x + 12, y + 2, QUARTZ, true);
		return w;
	}

	/** A progress bar {@code fraction} full in {@code color}, in a Void well, every 4th pixel notched so it reads in steps. */
	public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
		g.fill(x, y, x + w, y + h, VOID);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, DEEP_900);
		int inner = Math.round((w - 2) * Math.max(0f, Math.min(1f, fraction)));
		if (inner > 0) {
			g.fill(x + 1, y + 1, x + 1 + inner, y + h - 1, color);
			if (h > 3) {
				g.fill(x + 1, y + 1, x + 1 + inner, y + 2, 0x40FFFFFF);
			}
		}
	}

	/** A toggle (off, on, locked): a 20x9 well with its knob at the left (off, Slate) or right (on, Verdigris); locked is flat. */
	public static void toggle(GuiGraphics g, int x, int y, boolean on, boolean locked, boolean over) {
		g.blitSprite(locked ? BUTTON_OFF : over ? WELL_FOCUS : WELL, x, y, 20, 10);
		if (locked) {
			g.fill(x + 2, y + 2, x + 9, y + 8, DEEP_600);
			return;
		}
		if (on) {
			g.fill(x + 2, y + 2, x + 18, y + 8, 0xFF1E5E50);
			g.fill(x + 11, y + 2, x + 18, y + 8, VERDIGRIS);
			g.fill(x + 4, y + 4, x + 5, y + 6, VERDIGRIS);
		} else {
			g.fill(x + 2, y + 2, x + 9, y + 8, TUFF);
			g.fill(x + 2, y + 7, x + 9, y + 8, SLATE);
		}
	}

	/** A slider: a Void track, copper up to the handle at {@code fraction}, a Quartz handle (Lantern edged under the mouse). */
	public static void slider(GuiGraphics g, int x, int y, int w, float fraction, boolean over) {
		float f = Math.max(0f, Math.min(1f, fraction));
		g.fill(x, y + 3, x + w, y + 6, VOID);
		int hx = x + Math.round((w - 4) * f);
		g.fill(x + 1, y + 4, hx, y + 5, COPPER);
		g.fill(hx, y, hx + 4, y + 9, over ? LANTERN : VOID);
		g.fill(hx + 1, y + 1, hx + 3, y + 8, QUARTZ);
	}

	/** A number field: [-] value [+]; the value in a well (focus: Lantern). Returns the three boxes: minus, field, plus. */
	public static int[][] number(GuiGraphics g, Font font, String value, int x, int y, int fieldW, boolean focus, int over) {
		int[][] boxes = {{x, y, 12, 12}, {x + 15, y, fieldW, 12}, {x + 18 + fieldW, y, 12, 12}};
		button(g, font, null, "minus", boxes[0][0], y, 12, 12, Kind.NEUTRAL, over == 0, false, false);
		g.blitSprite(focus ? WELL_FOCUS : WELL, boxes[1][0], y, fieldW, 12);
		g.drawString(font, trim(font, value, fieldW - 6), boxes[1][0] + 3, y + 2, QUARTZ, false);
		button(g, font, null, "plus", boxes[2][0], y, 12, 12, Kind.NEUTRAL, over == 2, false, false);
		return boxes;
	}

	/**
	 * A text field (normal, focus, error) with an optional glyph (search) and a hint shown while empty; with an error, the
	 * error line under it (a bad glyph and the sentence in Redstone). Returns the height used.
	 */
	public static int textField(GuiGraphics g, Font font, String text, Component hint, String glyph, int x, int y, int w, boolean focus,
		Component error) {
		g.blitSprite(error != null ? WELL_ERROR : focus ? WELL_FOCUS : WELL, x, y, w, 14);
		int tx = x + 4;
		if (glyph != null) {
			g.blitSprite(glyph(glyph), tx, y + 3, 8, 8);
			tx += 10;
		}
		if (text.isEmpty() && hint != null) {
			g.drawString(font, trim(font, hint.getString(), x + w - 4 - tx), tx, y + 3, OFF_TEXT, false);
		} else {
			String shown = trim(font, text + (focus ? "_" : ""), x + w - 4 - tx);
			g.drawString(font, shown, tx, y + 3, QUARTZ, false);
		}
		if (error == null) {
			return 14;
		}
		g.blitSprite(glyph("bad"), x, y + 17, 8, 8);
		g.drawString(font, trim(font, error.getString(), w - 10), x + 10, y + 17, REDSTONE, false);
		return 26;
	}

	/** The scroll bar: a 4-wide Void track and a Deepslate 600 thumb (Tuff while dragged or under the mouse). */
	public static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int max, int content, boolean active) {
		if (max <= 0) {
			return;
		}
		int bar = Math.max(10, Math.min(h, h * h / Math.max(1, content)));
		int by = y + (h - bar) * scroll / max;
		g.fill(x, y, x + 4, y + h, VOID);
		g.fill(x, by, x + 4, by + bar, active ? TUFF : DEEP_600);
		g.fill(x, by, x + 1, by + bar, active ? QUARTZ : SLATE);
	}

	/** An item slot: an 18x18 well with the item and its count; the chosen one framed in Lantern. */
	public static void slot(GuiGraphics g, Font font, ItemStack stack, int x, int y, boolean chosen) {
		g.blitSprite(chosen ? WELL_FOCUS : WELL, x, y, 18, 18);
		if (!stack.isEmpty()) {
			g.renderItem(stack, x + 1, y + 1);
			g.renderItemDecorations(font, stack, x + 1, y + 1);
		}
	}

	// --- boxes ------------------------------------------------------------------------------------------------------------

	/** A panel or card (Deepslate 800, a lighter top). */
	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.blitSprite(PANEL, x, y, w, h);
	}

	/** A well (sunk: lists, fields). */
	public static void well(GuiGraphics g, int x, int y, int w, int h) {
		g.blitSprite(WELL, x, y, w, h);
	}

	/**
	 * A note box with a colored edge on its left, a heading (with a glyph) and wrapped body text: "What happens" (Lapis, no
	 * glyph), a refusal (Redstone, the bad glyph). Returns its height.
	 */
	public static int noteBox(GuiGraphics g, Font font, Component heading, Component body, String glyph, int edge, int headColor,
		int x, int y, int w) {
		List<FormattedCharSequence> lines = font.split(body, w - 12);
		int h = 6 + 11 + lines.size() * 10 + 4;
		g.fill(x, y, x + w, y + h, DEEP_800);
		g.renderOutline(x, y, w, h, VOID);
		g.fill(x, y, x + 2, y + h, edge);
		int hx = x + 6;
		if (glyph != null) {
			g.blitSprite(glyph(glyph), hx, y + 5, 8, 8);
			hx += 10;
		}
		g.drawString(font, trim(font, heading.getString(), x + w - 4 - hx), hx, y + 5, headColor, true);
		int ly = y + 17;
		for (FormattedCharSequence line : lines) {
			g.drawString(font, line, x + 6, ly, QUARTZ, false);
			ly += 10;
		}
		return h;
	}

	public static int whatHappens(GuiGraphics g, Font font, Component body, int x, int y, int w) {
		return noteBox(g, font, Component.translatable("screen.aliveworkplace.copperline.what_happens"), body, null, LAPIS, QUARTZ, x, y, w);
	}

	public static int refusal(GuiGraphics g, Font font, Component heading, Component body, int x, int y, int w) {
		return noteBox(g, font, heading, body, "bad", REDSTONE, REDSTONE, x, y, w);
	}

	/**
	 * A confirm dialog: a window with a copper top, the question, what will happen in a Redstone-edged box, and two buttons
	 * at its foot (keep: neutral; do it: danger). Returns the two buttons' boxes {keep, confirm}.
	 */
	public static int[][] confirm(GuiGraphics g, Font font, Component question, Component consequence, Component keep, Component doIt,
		int x, int y, int w, int over) {
		List<FormattedCharSequence> lines = font.split(consequence, w - 22);
		int boxH = lines.size() * 10 + 8;
		int h = 8 + 12 + boxH + 6 + 14 + 6;
		g.blitSprite(WINDOW, x, y, w, h);
		g.drawString(font, trim(font, question.getString(), w - 16), x + 8, y + 7, QUARTZ, true);
		int by = y + 20;
		g.fill(x + 6, by, x + w - 6, by + boxH, DEEP_800);
		g.fill(x + 6, by, x + 8, by + boxH, REDSTONE);
		int ly = by + 4;
		for (FormattedCharSequence line : lines) {
			g.drawString(font, line, x + 12, ly, QUARTZ, false);
			ly += 10;
		}
		int bw2 = Math.max(44, font.width(doIt) + 22);
		int bw1 = Math.max(44, font.width(keep) + 12);
		int fy = y + h - 20;
		int[] confirm = {x + w - 8 - bw2, fy, bw2, 14};
		int[] keepBox = {confirm[0] - 4 - bw1, fy, bw1, 14};
		button(g, font, keep, null, keepBox[0], fy, bw1, 14, Kind.NEUTRAL, over == 0, false, false);
		button(g, font, doIt, "bad", confirm[0], fy, bw2, 14, Kind.DANGER, over == 1, false, false);
		return new int[][]{keepBox, confirm};
	}

	/** The empty state: a dashed frame, an icon, a title, a line and one button. Returns the button's box. */
	public static int[] empty(GuiGraphics g, Font font, String icon, Component title, Component line, Component action, int x, int y, int w, int h,
		boolean over) {
		for (int i = x; i < x + w; i += 4) {
			g.fill(i, y, Math.min(i + 2, x + w), y + 1, SLATE);
			g.fill(i, y + h - 1, Math.min(i + 2, x + w), y + h, SLATE);
		}
		for (int j = y; j < y + h; j += 4) {
			g.fill(x, j, x + 1, Math.min(j + 2, y + h), SLATE);
			g.fill(x + w - 1, j, x + w, Math.min(j + 2, y + h), SLATE);
		}
		int cy = y + Math.max(4, (h - 60) / 2);
		if (icon != null) {
			g.blitSprite(icon(icon), x + (w - 16) / 2, cy, 16, 16);
		}
		String t = trim(font, title.getString(), w - 8);
		g.drawString(font, t, x + (w - font.width(t)) / 2, cy + 20, QUARTZ, true);
		String l = trim(font, line.getString(), w - 8);
		g.drawString(font, l, x + (w - font.width(l)) / 2, cy + 32, TUFF, false);
		if (action == null) {
			return null;
		}
		int bw = Math.min(w - 8, Math.max(44, font.width(action) + 12));
		int[] box = {x + (w - bw) / 2, cy + 44, bw, 14};
		button(g, font, action, null, box[0], box[1], bw, 14, Kind.NEUTRAL, over, false, false);
		return box;
	}

	/** A tooltip: a title in Quartz, lines in Tuff, an optional status line (its glyph and word in its color). */
	public static void tooltip(GuiGraphics g, Font font, Component title, List<Component> lines, Status status, Component statusWord, int x, int y) {
		int w = font.width(title);
		for (Component line : lines) {
			w = Math.max(w, font.width(line));
		}
		if (status != null) {
			w = Math.max(w, font.width(statusWord) + 10);
		}
		w += 10;
		int h = 6 + 10 + lines.size() * 10 + (status != null ? 10 : 0) + 3;
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		g.blitSprite(TOOLTIP, x, y, w, h);
		g.drawString(font, title, x + 5, y + 5, QUARTZ, true);
		int ly = y + 15;
		for (Component line : lines) {
			g.drawString(font, line, x + 5, ly, TUFF, true);
			ly += 10;
		}
		if (status != null) {
			g.blitSprite(status.glyph, x + 5, ly, 8, 8);
			g.drawString(font, statusWord, x + 15, ly, status.color, true);
		}
		g.pose().popPose();
	}

	/** A toast: the Lantern edge, a 16 px icon, a title and a line. */
	public static void toast(GuiGraphics g, Font font, String icon, Component title, Component line, int x, int y, int w) {
		g.blitSprite(TOAST, x, y, w, 30);
		if (icon != null) {
			g.blitSprite(icon(icon), x + 8, y + 7, 16, 16);
		}
		g.drawString(font, trim(font, title.getString(), w - 34), x + 28, y + 6, QUARTZ, true);
		g.drawString(font, trim(font, line.getString(), w - 34), x + 28, y + 17, TUFF, false);
	}

	private CopperStyle() {
	}
}
