package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * Copperline's shell (ROADMAP 47.1) at GUI scale 2 (427x240 and the harness's 480x270) and 3 (320x180), through
 * {@link CopperLayout}, the places every screen on TableStyle draws from and takes its clicks with: the window, the
 * plaque over its top edge, the lintel and its close X, the Village Hall's rail (one column of words, or at 320x180 a
 * two-column icon grid), the stage and the whisper line. Nothing goes off the screen or overlaps, and every rail entry,
 * tab, page arrow and button is clickable (the point at its middle finds it). Label widths are 6 pixels a character
 * (vanilla's widest common letters), with the English labels and three times as long (other languages).
 */
public class CopperlineShellGameTests implements FabricGameTest {
	/** GUI-pixel screens: GUI scale 2 on 854x480 and on the harness's 960x540, GUI scale 3 on 960x540. */
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {320, 180}};

	private static String text(String key) {
		return Component.translatable(key).getString();
	}

	private static int widest(int times) {
		int w = 0;
		for (Section s : Section.values()) {
			w = Math.max(w, s.title().getString().repeat(times).length() * 6);
		}
		return w;
	}

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	/** {@code box}'s middle finds it among {@code boxes} (index {@code i}). */
	private static boolean clickable(int[][] boxes, int i) {
		int[] b = boxes[i];
		return CopperLayout.at(boxes, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0) == i;
	}

	private static void shell(int sw, int sh, int labelW, String title, List<String> problems) {
		String at = sw + "x" + sh + ": ";
		int[] screen = {0, 0, sw, sh};
		int[] win = CopperLayout.window(sw, sh);
		boolean compact = CopperLayout.compact(sw, sh);
		if (compact != (sw == 320)) {
			problems.add(at + "compact is " + compact);
		}
		if (!within(win, screen)) {
			problems.add(at + "the window " + box(win) + " leaves the screen");
		}
		int[] plaque = CopperLayout.plaque(win, title.length() * 6);
		if (!within(plaque, screen) || plaque[1] >= win[1]) {
			problems.add(at + "the plaque " + box(plaque) + " leaves the screen or doesn't rise over the window's top " + win[1]);
		}
		int[] close = CopperLayout.close(win);
		int[] lintel = CopperLayout.lintel(win, plaque);
		int[] whisper = CopperLayout.whisper(win);
		if (!within(close, win) || CopperLayout.overlap(close, plaque) || CopperLayout.overlap(close, lintel)) {
			problems.add(at + "the close X " + box(close) + " is outside the window or under the plaque or the lintel's text");
		}
		if (!CopperLayout.inside(close, close[0] + close[2] / 2.0, close[1] + close[3] / 2.0)) {
			problems.add(at + "the close X can't be clicked");
		}
		if (lintel[2] < 40 || CopperLayout.overlap(lintel, plaque)) {
			problems.add(at + "the lintel's text " + box(lintel) + " has no room or runs under the plaque");
		}
		if (!within(whisper, win)) {
			problems.add(at + "the whisper line " + box(whisper) + " leaves the window");
		}
		int railW = CopperLayout.railWidth(win, labelW, compact);
		int[] rail = CopperLayout.rail(win, railW);
		int[] stage = CopperLayout.stage(win, railW);
		for (int[][] pair : new int[][][]{{rail, stage}, {rail, whisper}, {stage, whisper}, {rail, close}, {stage, close}, {stage, lintel}}) {
			if (CopperLayout.overlap(pair[0], pair[1])) {
				problems.add(at + box(pair[0]) + " overlaps " + box(pair[1]));
			}
		}
		if (!within(rail, win) || !within(stage, win) || stage[2] < 150 || stage[3] < 100) {
			problems.add(at + "the rail " + box(rail) + " or the stage " + box(stage) + " leaves the window or is too small");
		}
		int[][] entries = CopperLayout.railEntries(rail, Section.values().length, compact);
		for (int i = 0; i < entries.length; i++) {
			if (!within(entries[i], rail)) {
				problems.add(at + Section.of(i).id() + "'s rail entry " + box(entries[i]) + " leaves the rail " + box(rail));
			}
			if (!clickable(entries, i)) {
				problems.add(at + Section.of(i).id() + "'s rail entry can't be clicked");
			}
			if (entries[i][3] < (compact ? 16 : 10)) {
				problems.add(at + Section.of(i).id() + "'s rail entry is only " + entries[i][3] + " tall");
			}
			for (int j = 0; j < i; j++) {
				if (CopperLayout.overlap(entries[i], entries[j])) {
					problems.add(at + Section.of(i).id() + " overlaps " + Section.of(j).id() + " on the rail");
				}
			}
		}
		boolean grid = entries[0][1] == entries[1][1] && entries[0][0] < entries[1][0];
		if (grid != compact) {
			problems.add(at + (compact ? "the rail isn't a two-column icon grid" : "the rail isn't one column"));
		}
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void copperlineShellFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		helper.assertTrue(text("screen.aliveworkplace.copperline.key.esc").equals("Esc") && text("screen.aliveworkplace.copperline.key.close").equals("close"),
			"the whisper line's key hint isn't \"Esc close\"");
		helper.assertTrue(text("screen.aliveworkplace.copperline.what_happens").equals("What happens"), "the \"What happens\" box's heading is wrong");
		for (int[] s : SCREENS) {
			shell(s[0], s[1], widest(1), Section.HOMES.title().getString(), problems);
			shell(s[0], s[1], widest(3), Section.HOMES.title().getString().repeat(3), problems);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The list screens' tabs (wrapping rows) and page arrows (right-aligned in the whisper line), the status card's and the
	 * Trainer's buttons, laid out as the screens do at each size: inside the window, apart, each one clickable.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void copperlineScreensButtonsWorkAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3}) {
				String at = s[0] + "x" + s[1] + " x" + times + ": ";
				int[] win = CopperLayout.window(s[0], s[1]);
				int right = win[0] + win[2] - 8;
				// The list screens' tabs (as the Move Tutor's party): label + 12 + an icon's 18, at most a sixth of the width.
				int[] tabW = new int[6];
				for (int i = 0; i < tabW.length; i++) {
					int max = Math.max(40, (win[2] - 16) / 6 - 4);
					tabW[i] = Math.min(max, Section.of(i).title().getString().repeat(times).length() * 6 + 12 + 18);
				}
				int[][] tabs = CopperLayout.flow(tabW, win[0] + 8, win[1] + 22, right, 4, 18, 21);
				int[] close = CopperLayout.close(win);
				for (int i = 0; i < tabs.length; i++) {
					if (!within(tabs[i], win) || !clickable(tabs, i) || CopperLayout.overlap(tabs[i], close)) {
						problems.add(at + "tab " + i + " " + box(tabs[i]) + " leaves the window, can't be clicked or is under the close X");
					}
				}
				// The page arrows: "‹ Previous" and "Next ›" (buttonWidth: at least 44, the label plus 12).
				String[] pages = {"< " + "Previous".repeat(times), "Next".repeat(times) + " >"};
				int[] pageW = {Math.max(44, pages[0].length() * 6 + 12), Math.max(44, pages[1].length() * 6 + 12)};
				int[][] arrows = CopperLayout.rightRow(pageW, right, win[1] + win[3] - 16, 4, 13);
				for (int i = 0; i < arrows.length; i++) {
					if (!within(arrows[i], win) || !clickable(arrows, i)) {
						problems.add(at + "page arrow " + i + " " + box(arrows[i]) + " leaves the window or can't be clicked");
					}
				}
				if (arrows[0][0] + arrows[0][2] > arrows[1][0]) {
					problems.add(at + "the page arrows overlap or swap places");
				}
				// The Trainer's buttons on its right side, and a status card's, through StatusCard.buttonRow as the screens use it.
				int trainerSide = win[2] - 112 - 6 - 10 - 8;
				buttons(at + "Trainer: ", TrainerScreens.BUTTON_KEYS, times, trainerSide, problems);
				buttons(at + "status card: ", new String[]{"screen.aliveworkplace.card.job.chat", "screen.aliveworkplace.card.job.hall",
					"screen.aliveworkplace.card.close"}, times, Math.min(200, win[2]) - 16, problems);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	private static void buttons(String at, String[] keys, int times, int inner, List<String> problems) {
		int[] need = new int[keys.length];
		for (int i = 0; i < keys.length; i++) {
			need[i] = Math.max(44, text(keys[i]).repeat(times).length() * 6 + 12);
		}
		int[][] row = StatusCard.buttonRow(need, inner);
		int[][] boxes = new int[row.length][];
		for (int i = 0; i < row.length; i++) {
			boxes[i] = new int[]{row[i][0], row[i][1] * 20, row[i][2], 14};
			if (row[i][0] < 0 || row[i][0] + row[i][2] > inner) {
				problems.add(at + text(keys[i]) + " leaves its " + inner + " pixels");
			}
		}
		for (int i = 0; i < boxes.length; i++) {
			if (!clickable(boxes, i)) {
				problems.add(at + text(keys[i]) + " can't be clicked (another button covers its middle)");
			}
		}
	}
}
