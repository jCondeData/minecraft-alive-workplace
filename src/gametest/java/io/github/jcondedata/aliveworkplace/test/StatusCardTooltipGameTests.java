package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * The status card's tooltips stay on screen (B1357: at 320x180 the grayed-out Hall page button's reason ended past the
 * right edge wherever the mouse was on it, and an item line's tooltip with no room right of the card landed over the
 * card's left edge and the row's own icon). Checks {@link StatusCardLayout#buttonTooltip} and
 * {@link StatusCardLayout#lineTooltip}, which the client's StatusCardScreen draws from, on the builder's real job card (In
 * chat, Hall page grayed out with its reason, eight need lines and a hint) laid out by {@link StatusCardLayout#of}, with
 * the English text measured at 6 pixels a character (vanilla's widest common letters), at GUI scale 3 and 2 of small
 * windows and a sweep of sizes (the list borrowed from QA's QaCopperCard1117eGameTests).
 */
public class StatusCardTooltipGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{320, 180}, {320, 240}, {360, 200}, {399, 220}, {427, 240}, {480, 270}, {640, 360},
		{683, 192}, {683, 384}, {960, 540}, {1280, 720}, {1920, 1080}, {330, 186}};
	private static final int LINES = 8;

	private static int width(String s) {
		return s.length() * 6;
	}

	private static int need(String label) {
		return Math.max(44, width(label) + 12);
	}

	/** {@code text} word-wrapped at {@code w} pixels. */
	private static List<String> wrap(String text, int w) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (width(next) > w && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		out.add(line.toString());
		return out;
	}

	private static boolean overlaps(int[] a, int[] b) {
		return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}

	private static boolean onScreen(int[] t, int sw, int sh) {
		return t[0] >= 0 && t[1] >= 0 && t[0] + t[2] <= sw && t[1] + t[3] <= sh;
	}

	private static String box(int[] b) {
		return "{" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "}";
	}

	private static StatusCardLayout.Boxes card(int sw, int sh, String reason) {
		String chat = Component.translatable("screen.aliveworkplace.card.job.chat").getString();
		String hall = Component.translatable("screen.aliveworkplace.card.job.hall").getString();
		int refusalLines = wrap(reason, StatusCardLayout.refusalTextWidth(sw)).size();
		return StatusCardLayout.of(sw, sh, 2, 12 + LINES * 18, refusalLines, true, new int[]{need(chat), need(hall)});
	}

	/** The Hall page button's reason, for every mouse spot on the button, is fully on screen at every size. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void statusCardButtonReasonStaysOnScreen(GameTestHelper helper) {
		String hall = Component.translatable("screen.aliveworkplace.card.job.hall").getString();
		String reason = Component.translatable("screen.aliveworkplace.card.job.no_hall", "Mira").getString();
		String off = Component.translatable("screen.aliveworkplace.card.off").getString();
		helper.assertTrue(hall.equals("Hall page") && reason.equals("There's no Village Hall near Mira."),
			"the card's English text changed: " + hall + " / " + reason);
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (int[] s : SCREENS) {
			int sw = s[0];
			int sh = s[1];
			StatusCardLayout.Boxes b = card(sw, sh, reason);
			int[] button = b.buttons()[1];
			List<String> lines = wrap(reason, StatusCardLayout.tooltipWrap(sw));
			int w = width(hall);
			for (String l : lines) {
				w = Math.max(w, width(l));
			}
			w = Math.max(w, width(off) + 10) + 10;
			int h = StatusCardLayout.tooltipHeight(lines.size(), true);
			if (w > sw) {
				problems.add(sw + "x" + sh + ": the tooltip is " + w + " wide, wider than the screen");
				continue;
			}
			for (int mx = button[0]; mx < button[0] + button[2]; mx++) {
				for (int my = button[1]; my < button[1] + button[3]; my += 3) {
					int[] at = StatusCardLayout.buttonTooltip(sw, sh, mx, my, w, h);
					int[] t = {at[0], at[1], w, h};
					checked++;
					if (!onScreen(t, sw, sh)) {
						problems.add(sw + "x" + sh + " mouse " + mx + "," + my + ": tooltip " + box(t) + " leaves the screen");
					}
				}
			}
		}
		helper.assertTrue(checked > 1000, "only " + checked + " mouse spots checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems, first: " + (problems.isEmpty() ? "" : problems.get(0)));
		// The bug's own case, by hand: at 320x180 the Hall page button spans about x 140-218 and its reason is about
		// 175 wide, so it can't go right of the mouse and must come back on screen.
		int[] at = StatusCardLayout.buttonTooltip(320, 180, 210, 160, 175, 39);
		helper.assertTrue(at[0] >= 0 && at[0] + 175 <= 320 && at[1] >= 0 && at[1] + 39 <= 180,
			"the bug's case at 320x180 put the tooltip at " + at[0] + "," + at[1]);
		helper.succeed();
	}

	/**
	 * An item line's tooltip is fully on screen at every size, never over the row's item icon, and at 320x180 and 427x240
	 * (no room either side of the card) off the row it describes altogether.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void statusCardLineTooltipStaysOnScreenAndOffItsRow(GameTestHelper helper) {
		String reason = Component.translatable("screen.aliveworkplace.card.job.no_hall", "Mira").getString();
		// Oak Log / Needs Oak Log / its value with a status glyph; and a long modded item name.
		String[][] tips = {{"Oak Log", "Needs Oak Log", "12 of 32"}, {"Polished Blackstone Brick Stairs", "Needs Polished Blackstone Brick Stairs", "0 of 64"}};
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (int[] s : SCREENS) {
			int sw = s[0];
			int sh = s[1];
			StatusCardLayout.Boxes b = card(sw, sh, reason);
			int[] body = b.body();
			int top = body[1] + 12;
			for (String[] tip : tips) {
				int w = Math.max(Math.max(width(tip[0]), width(tip[1])), width(tip[2]) + 10) + 10;
				int h = StatusCardLayout.tooltipHeight(1, true);
				for (int i = 0; i < LINES; i++) {
					int rowY = top + i * 18;
					if (rowY + 18 > body[1] + body[3]) {
						break; // a row the short screen cuts isn't drawn or hovered
					}
					int[] row = {body[0], rowY, body[2], 18};
					int[] icon = {body[0], rowY + 1, StatusCardLayout.ICON, StatusCardLayout.ICON};
					int[] at = StatusCardLayout.lineTooltip(sw, sh, b.card(), row, w, h);
					int[] t = {at[0], at[1], w, h};
					checked++;
					String where = sw + "x" + sh + " " + tip[0] + " row " + i + ": tooltip " + box(t);
					if (!onScreen(t, sw, sh)) {
						problems.add(where + " leaves the screen");
					}
					if (overlaps(t, icon)) {
						problems.add(where + " covers the row's icon " + box(icon));
					}
					if ((sw == 320 && sh == 180 || sw == 427 && sh == 240) && overlaps(t, row)) {
						problems.add(where + " covers its row " + box(row));
					}
				}
			}
		}
		helper.assertTrue(checked > 50, "only " + checked + " rows checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems, first: " + (problems.isEmpty() ? "" : problems.get(0)));
		// The bug's own case at 320x180: the card spans x 60-260, an 88-wide tooltip fits neither side; it must not land at
		// x 0 beside the row (over the card's edge and the icon at x 68) but above or below the row.
		int[] cardBox = {60, 10, 200, 160};
		int[] row = {68, 80, 184, 18};
		int[] at = StatusCardLayout.lineTooltip(320, 180, cardBox, row, 88, 29);
		helper.assertTrue(!overlaps(new int[]{at[0], at[1], 88, 29}, row) && at[0] >= 0 && at[0] + 88 <= 320,
			"the bug's case at 320x180 put the line tooltip at " + at[0] + "," + at[1]);
		helper.succeed();
	}
}
