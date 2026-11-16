package io.github.jcondedata.aliveworkplace.ui;

/**
 * Copperline round a screen with real item slots (ROADMAP 44.1: the Mailbox, the Drop Box and the Shop Counter): the
 * window, the plaque and lintel over the slots, a label gutter on the left (the counter's "Sells" and "Price"), a side
 * panel on the right (the mailbox's address and Send, the Drop Box's and the counter's "What happens") and the whisper
 * line under the hotbar. The slots stay where the menu puts them (a vanilla chest's places for the Drop Box and the
 * counter), so every box here is relative to the menu's slot frame: x and y 0 at the screen's {@code leftPos, topPos},
 * the frame {@link #FRAME_W} wide. Pure arithmetic, so the GameTests check that no slot sits under the lintel, the
 * panel or the whisper line. Every box is {x, y, w, h}.
 */
public final class CopperSlots {
	/** A chest screen's slot frame width (vanilla's 176: 9 slots of 18 from x 8). */
	public static final int FRAME_W = 176;
	/** How far the window's top sits over the slot frame's top (room for the lintel). */
	public static final int TOP = 16;
	/** How far the window's foot sits under the slot frame's foot (room for the whisper line). */
	public static final int FOOT = 14;
	/** The margin right of the side panel, inside the window. */
	public static final int PANEL_MARGIN = 7;
	/** The side panel's width on all three screens. */
	public static final int PANEL_W = 128;
	/** The label gutter left of slot rows that are named (the counter's "Sells" and "Price", with the coin glyph). */
	public static final int LABEL_GUTTER = 46;
	/** A slot's well: one pixel round the 16x16 item. */
	public static final int WELL = 18;

	/** The window round a slot frame {@code frameH} tall, with a {@code gutter} on the left and a {@code panelW} panel (0: none). */
	public static int[] window(int frameH, int gutter, int panelW) {
		int w = gutter + FRAME_W + (panelW > 0 ? panelW + PANEL_MARGIN : 0);
		return new int[]{-gutter, -TOP, w, TOP + frameH + FOOT};
	}

	/** The side panel, right of the slot frame, from under the lintel to over the whisper line. */
	public static int[] panel(int frameH, int panelW) {
		return new int[]{FRAME_W, 4, panelW, frameH - 10};
	}

	/** The lintel bar's box (its rule is its last pixel row). */
	public static int[] lintel(int frameH, int gutter, int panelW) {
		int[] win = window(frameH, gutter, panelW);
		return new int[]{win[0], win[1] + CopperLayout.LINTEL_Y, win[2], CopperLayout.LINTEL_H + 1};
	}

	/** The whisper line's box along the window's foot. */
	public static int[] whisper(int frameH, int gutter, int panelW) {
		int[] win = window(frameH, gutter, panelW);
		return new int[]{win[0] + 4, frameH - 3, win[2] - 8, 13};
	}

	/** A slot's well, for a slot at {@code x, y}. */
	public static int[] well(int x, int y) {
		return new int[]{x - 1, y - 1, WELL, WELL};
	}

	/** Where the window goes on a {@code screenW x screenH} screen: {leftPos, topPos} of the slot frame, the window centered. */
	public static int[] origin(int screenW, int screenH, int frameH, int gutter, int panelW) {
		int[] win = window(frameH, gutter, panelW);
		int x = (screenW - win[2]) / 2 - win[0];
		int y = (screenH - win[3] + CopperLayout.PLAQUE_RISE) / 2 - win[1];
		return new int[]{x, y};
	}

	/** The box, moved by {@code dx, dy}. */
	public static int[] at(int[] box, int dx, int dy) {
		return new int[]{box[0] + dx, box[1] + dy, box[2], box[3]};
	}

	private CopperSlots() {
	}
}
