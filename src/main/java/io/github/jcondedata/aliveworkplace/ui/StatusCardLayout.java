package io.github.jcondedata.aliveworkplace.ui;

import io.github.jcondedata.aliveworkplace.card.StatusCard;

/**
 * Where the villager status card puts its parts in Copperline (ROADMAP 48.3, docs/design/copperline/boards/VillagerCard.png,
 * "On click, pinned"): a card with a copper top edge; at its top the villager's portrait (the real model) in a well, the
 * name beside it with the job and level under it, and the close X at the right; the status line(s) under them; the body
 * (a heading over labeled lines, or the line for when there are none); a divider; the reason a grayed-out button can't be
 * used in a Redstone-edged box; the button row; and the hint at the foot. The card's foot (hint, buttons, reason) is laid
 * out from the bottom up so it always fits: when the screen is too short for everything, the body gives up its room
 * (and scrolls off under a scissor) instead of the buttons leaving the card (B1103's rule). The buttons are fitted with
 * {@link StatusCard#buttonRow}; the first usable one is the card's one copper button. Pure arithmetic in GUI pixels, so
 * GameTests check it at GUI scale 2 and 3. Every box is {x, y, w, h}.
 */
public final class StatusCardLayout {
	public static final int CARD_W = 200;
	public static final int PAD = 8;
	public static final int BUTTON_H = 14;
	public static final int PORTRAIT_W = 26;
	public static final int PORTRAIT_H = 30;
	public static final int CLOSE = 11;
	/** The copper edge along the card's top. */
	public static final int EDGE = 2;

	/**
	 * The boxes: {@code card}, {@code portrait}, {@code name} (the name's line), {@code tag} (job and level), {@code close}
	 * the X, {@code subtitle} (the status lines), {@code body} (heading and lines; may be shorter than they need),
	 * {@code divider} (a 1-pixel line), {@code refusal} (h 0 when no button is grayed out), {@code buttons} (one box per
	 * button, in order), {@code hint} (h 0 when there's none); {@code cut} whether the body didn't get all the room it needs.
	 */
	public record Boxes(int[] card, int[] portrait, int[] name, int[] tag, int[] close, int[] subtitle, int[] body, int[] divider,
		int[] refusal, int[][] buttons, int[] hint, boolean cut) {
	}

	/** The card's width on a screen {@code sw} wide: 200, or the screen less 8 pixels each side. */
	public static int width(int sw) {
		return Math.min(CARD_W, sw - 16);
	}

	/** The room inside the card's padding, for wrapping text and fitting buttons. */
	public static int inner(int sw) {
		return width(sw) - PAD * 2;
	}

	/** The refusal box's text width (inside its edge and padding). */
	public static int refusalTextWidth(int sw) {
		return inner(sw) - 10;
	}

	/** The index of the card's one copper button: the first usable one that isn't Close, or -1. */
	public static int copper(boolean[] usable, boolean[] close) {
		for (int i = 0; i < usable.length; i++) {
			if (usable[i] && !close[i]) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The layout on a screen {@code sw x sh} GUI pixels, for {@code subtitleLines} status lines, a body {@code bodyH} pixels
	 * tall, {@code refusalLines} lines of a grayed-out button's reason (0: none), a hint or not, and the buttons needing
	 * {@code buttonNeed} each (the Hall's button width: the label plus 12, at least 44).
	 */
	public static Boxes of(int sw, int sh, int subtitleLines, int bodyH, int refusalLines, boolean hint, int[] buttonNeed) {
		int w = width(sw);
		int inner = w - PAD * 2;
		int[][] fit = StatusCard.buttonRow(buttonNeed, inner);
		int rows = buttonNeed.length == 0 ? 0 : StatusCard.buttonRows(fit);
		int buttonsH = rows == 0 ? 0 : rows * BUTTON_H + (rows - 1) * StatusCard.BUTTON_ROW_GAP;
		int refusalH = refusalLines > 0 ? refusalLines * 10 + 6 : 0;
		int hintH = hint ? 10 : 0;

		int head = EDGE + 4 + PORTRAIT_H + 5;
		int subH = subtitleLines * 10;
		int foot = 2 + 1 + 3 + (refusalH > 0 ? refusalH + 4 : 0) + buttonsH + (hint ? 4 + hintH : 0) + 5;
		int natural = head + subH + (subH > 0 ? 4 : 0) + bodyH + foot;
		int h = Math.min(natural, sh - 4);
		int x = (sw - w) / 2;
		int y = Math.max(2, (sh - h) / 2);
		int left = x + PAD;

		int[] card = {x, y, w, h};
		int[] portrait = {left, y + EDGE + 4, PORTRAIT_W, PORTRAIT_H};
		int[] close = {x + w - PAD - CLOSE, y + EDGE + 4, CLOSE, CLOSE};
		int nx = portrait[0] + PORTRAIT_W + 6;
		int[] name = {nx, portrait[1] + 3, close[0] - 4 - nx, 9};
		int[] tag = {nx, portrait[1] + 15, x + w - PAD - nx, 9};
		int[] subtitle = {left, y + head, inner, subH};

		// The foot, from the bottom up.
		int bottom = y + h - 5;
		int[] hintBox = {left, bottom - hintH, inner, hintH};
		int by = bottom - (hint ? hintH + 4 : 0) - buttonsH;
		int[][] buttons = new int[fit.length][];
		for (int i = 0; i < fit.length; i++) {
			buttons[i] = new int[]{left + fit[i][0], by + fit[i][1] * (BUTTON_H + StatusCard.BUTTON_ROW_GAP), fit[i][2], BUTTON_H};
		}
		int ry = by - (refusalH > 0 ? refusalH + 4 : 0);
		int[] refusal = {left, ry, inner, refusalH};
		int[] divider = {left, ry - 3 - 1, inner, 1};
		int bodyTop = subtitle[1] + subH + (subH > 0 ? 4 : 0);
		int room = Math.max(0, divider[1] - 2 - bodyTop);
		int[] body = {left, bodyTop, inner, Math.min(bodyH, room)};
		return new Boxes(card, portrait, name, tag, close, subtitle, body, divider, refusal, buttons, hintBox, room < bodyH);
	}

	/** A gap between a tooltip and what it sits beside. */
	public static final int TIP_GAP = 4;
	/** The item icon at a line's left (TableStyle.cardLine draws it at the row's x, 16 pixels). */
	public static final int ICON = 16;

	/** A Copperline tooltip's height: its title, {@code lines} lines and the status line if there is one (CopperStyle.tooltip). */
	public static int tooltipHeight(int lines, boolean status) {
		return 6 + 10 + lines * 10 + (status ? 10 : 0) + 3;
	}

	/** The width a grayed-out button's reason wraps at in its tooltip: 180, or less on a narrow screen. */
	public static int tooltipWrap(int sw) {
		return Math.max(40, Math.min(180, sw - 20));
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(v, Math.max(lo, hi)));
	}

	/**
	 * Where a grayed-out button's reason tooltip ({@code w x h}) goes for the mouse at {@code mx, my} on a screen
	 * {@code sw x sh} (B1357): right of the mouse as vanilla's, flipped left of it when it doesn't fit, and above the mouse
	 * (below if there's no room above) when neither side has room; always fully on screen. Returns {x, y}.
	 */
	public static int[] buttonTooltip(int sw, int sh, int mx, int my, int w, int h) {
		int x;
		int y;
		if (mx + 8 + w <= sw) {
			x = mx + 8;
			y = my - 12;
		} else if (mx - 8 - w >= 0) {
			x = mx - 8 - w;
			y = my - 12;
		} else {
			x = mx - w / 2;
			y = my - 12 - h >= 0 ? my - 12 - h : my + 12;
		}
		return new int[]{clamp(x, 0, sw - w), clamp(y, 0, sh - h)};
	}

	/**
	 * Where an item line's tooltip ({@code w x h}) goes for the line's row {@code row} ({x, y, w, h}) on the card {@code card}
	 * on a screen {@code sw x sh} (B1357; the spec: beside the card at that row, so it never covers the row it describes):
	 * right of the card, else left of it, else just above the row, else just below it; always fully on screen, and when
	 * none of those fit, still off the row's item icon. Returns {x, y}.
	 */
	public static int[] lineTooltip(int sw, int sh, int[] card, int[] row, int w, int h) {
		int y = clamp(row[1], 0, sh - h);
		if (card[0] + card[2] + TIP_GAP + w <= sw) {
			return new int[]{card[0] + card[2] + TIP_GAP, y};
		}
		if (card[0] - TIP_GAP - w >= 0) {
			return new int[]{card[0] - TIP_GAP - w, y};
		}
		int x = clamp(row[0], 0, sw - w);
		if (row[1] - 2 - h >= 0) {
			return new int[]{x, row[1] - 2 - h};
		}
		if (row[1] + row[3] + 2 + h <= sh) {
			return new int[]{x, row[1] + row[3] + 2};
		}
		// No room anywhere clear of the row: over its text, right of its icon.
		return new int[]{clamp(row[0] + ICON + 2, 0, sw - w), y};
	}

	private StatusCardLayout() {
	}
}
