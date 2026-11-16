package io.github.jcondedata.aliveworkplace.ui;

import io.github.jcondedata.aliveworkplace.card.StatusCard;

/**
 * Where the Trainer screen puts its parts in Copperline (ROADMAP 48.2, docs/design/copperline/boards/Trainer.png): the
 * shell from {@link CopperLayout} (the plaque with the trainer's title, the lintel with their rank and the close X, the
 * whisper line), the rail on the left (the trainer's portrait with their name and title beside it, the record and today's
 * line under it, and Challenge, Trade and Close at its foot) and the stage (the reward, the team as a grid of cards, the
 * player's party and the rules, scrolled when they don't fit). The buttons are fitted with {@link StatusCard#buttonRow}
 * (B1131: they never run past the rail, wrapping onto more rows) and each row is then widened to fill the rail. Challenge
 * is the screen's one copper button. Pure arithmetic in GUI pixels so the GameTests check it at GUI scale 2 and 3. Every
 * box is {x, y, w, h}.
 */
public final class TrainerLayout {
	/** The copper button's index in the button row (Challenge, as TrainerScreens.BUTTON_KEYS orders them). */
	public static final int COPPER = 0;
	public static final int BUTTON_H = 14;
	/** A team card's height, and its narrowest width before the grid drops a column. */
	public static final int CARD_H = 30;
	public static final int CARD_MIN_W = 64;
	public static final int CARD_GAP = 4;
	/** The stage's scroll bar's room at its right. */
	public static final int SCROLL_W = 6;

	/**
	 * The boxes: {@code win} the window, {@code lintelText} the room for the rank, {@code rail}, {@code portrait} the
	 * trainer's model, {@code nameBox} beside it (name and title), {@code railText} under them (record and today),
	 * {@code buttons} at the rail's foot (Challenge, Trade, Close), {@code stage}, {@code content} the stage less its scroll
	 * bar, {@code whisper}.
	 */
	public record Boxes(int[] win, boolean compact, int[] plaque, int[] lintelText, int[] close, int[] rail, int[] portrait, int[] nameBox,
		int[] railText, int[][] buttons, int[] stage, int[] content, int[] whisper) {
	}

	/** Whether button {@code i} (in TrainerScreens.BUTTON_KEYS's order) is drawn copper: only Challenge, the main action. */
	public static boolean copper(int i) {
		return i == COPPER;
	}

	/** The rail's width: about three tenths of the window, 112 to 136 pixels; 106 on a 320x180 screen. */
	public static int railWidth(int[] win, boolean compact) {
		return compact ? 106 : Math.max(112, Math.min(136, win[2] * 30 / 100));
	}

	/**
	 * The layout on a screen {@code sw x sh} GUI pixels, for a title {@code titleW} wide and the buttons needing
	 * {@code buttonNeed} each (the Hall's button width: the label plus 12, at least 44).
	 */
	public static Boxes of(int sw, int sh, int titleW, int[] buttonNeed) {
		int[] win = CopperLayout.window(sw, sh);
		boolean compact = CopperLayout.compact(sw, sh);
		int[] plaque = CopperLayout.plaque(win, titleW);
		int[] close = CopperLayout.close(win);
		int[] lintelText = CopperLayout.lintel(win, plaque);
		int railW = railWidth(win, compact);
		int[] rail = CopperLayout.rail(win, railW);
		int[] stage = CopperLayout.stage(win, railW);
		int inner = rail[2] - 8;
		int px = rail[0] + 4;
		int py = rail[1] + 4;
		int[] portrait = compact ? new int[]{px, py, 30, 36} : new int[]{px, py, 40, 50};
		int nx = portrait[0] + portrait[2] + 5;
		int[] nameBox = {nx, py, rail[0] + rail[2] - 4 - nx, portrait[3]};

		// The buttons from the rail's foot up: fitted (B1131), then each row widened to the rail's inner width.
		int[][] fit = StatusCard.buttonRow(buttonNeed, inner);
		int rows = StatusCard.buttonRows(fit);
		int buttonsTop = rail[1] + rail[3] - 4 - rows * BUTTON_H - (rows - 1) * StatusCard.BUTTON_ROW_GAP;
		int[][] buttons = new int[fit.length][];
		for (int row = 0; row < rows; row++) {
			int used = 0;
			int count = 0;
			for (int[] f : fit) {
				if (f[1] == row) {
					used += f[2] + (count > 0 ? StatusCard.BUTTON_GAP : 0);
					count++;
				}
			}
			int extra = Math.max(0, inner - used);
			int x = px;
			int k = 0;
			for (int i = 0; i < fit.length; i++) {
				if (fit[i][1] != row) {
					continue;
				}
				int w = fit[i][2] + extra / count + (k < extra % count ? 1 : 0);
				buttons[i] = new int[]{x, buttonsTop + row * (BUTTON_H + StatusCard.BUTTON_ROW_GAP), w, BUTTON_H};
				x += w + StatusCard.BUTTON_GAP;
				k++;
			}
		}
		int ty = portrait[1] + portrait[3] + 5;
		int[] railText = {px, ty, inner, Math.max(0, buttonsTop - 4 - ty)};
		int[] content = {stage[0], stage[1], stage[2] - SCROLL_W, stage[3]};
		return new Boxes(win, compact, plaque, lintelText, close, rail, portrait, nameBox, railText, buttons, stage, content,
			CopperLayout.whisper(win));
	}

	/** How many columns the team's {@code n} cards take in {@code w} pixels: as many as fit, then evened out over the rows. */
	public static int columns(int w, int n) {
		if (n <= 0) {
			return 1;
		}
		int max = Math.max(1, Math.min(n, (w + CARD_GAP) / (CARD_MIN_W + CARD_GAP)));
		int rows = (n + max - 1) / max;
		return (n + rows - 1) / rows;
	}

	/** The team's {@code n} cards in a grid from {@code x, y}, {@code w} wide. */
	public static int[][] teamCards(int x, int y, int w, int n) {
		int cols = columns(w, n);
		int cw = (w - CARD_GAP * (cols - 1)) / cols;
		int[][] out = new int[n][];
		for (int i = 0; i < n; i++) {
			out[i] = new int[]{x + (i % cols) * (cw + CARD_GAP), y + (i / cols) * (CARD_H + CARD_GAP), cw, CARD_H};
		}
		return out;
	}

	/** The grid's height for {@code n} cards in {@code w} pixels. */
	public static int teamHeight(int w, int n) {
		if (n <= 0) {
			return 0;
		}
		int rows = (n + columns(w, n) - 1) / columns(w, n);
		return rows * CARD_H + (rows - 1) * CARD_GAP;
	}

	private TrainerLayout() {
	}
}
