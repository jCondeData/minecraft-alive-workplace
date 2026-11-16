package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.TrainerLayout;
import io.github.jcondedata.aliveworkplace.ui.TrainerLayout.Boxes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * The Trainer screen in Copperline (ROADMAP 48.2, the Trainer board) through {@link TrainerLayout}, the boxes the screen
 * draws in and takes its clicks with, at GUI scale 2 (427x240, 480x270, 640x360) and 3 (320x180), with the English labels
 * and three and five times as long (other languages): the rail, the portrait, the name, the record and the stage inside
 * the window and apart, clear of the whisper line and the close X; Challenge, Trade and Close inside the rail, apart, in
 * order, each clickable at its middle and never narrower than it needs (B1131) unless the rail is narrower; Challenge the
 * one copper button; the team's cards in the stage, apart, in even rows; and the screen's new sentences in the lang file.
 */
public class CopperlineTrainerGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {640, 360}, {320, 180}};

	private static int width(String s) {
		return s.length() * 6;
	}

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private static int[] need(int times) {
		int[] need = new int[TrainerScreens.BUTTON_KEYS.length];
		for (int i = 0; i < need.length; i++) {
			need[i] = Math.max(44, width(Component.translatable(TrainerScreens.BUTTON_KEYS[i]).getString().repeat(times)) + 12);
		}
		return need;
	}

	private static Boxes layout(int sw, int sh, int times) {
		return TrainerLayout.of(sw, sh, width("Trainer Leader Hale".repeat(times)), need(times));
	}

	private static void check(String at, Boxes b, int[] need, List<String> problems) {
		int[] win = b.win();
		if (!within(b.plaque(), new int[]{0, 0, win[0] * 2 + win[2], win[1] * 2 + win[3]})) {
			problems.add(at + "the plaque " + box(b.plaque()) + " leaves the screen");
		}
		if (b.lintelText()[2] < 40 || CopperLayout.overlap(b.lintelText(), b.close())) {
			problems.add(at + "the lintel has no room for the rank or runs under the close X " + box(b.lintelText()));
		}
		List<int[]> parts = List.of(b.rail(), b.stage());
		for (int[] p : parts) {
			if (!within(p, win) || p[2] <= 0 || p[3] <= 0) {
				problems.add(at + box(p) + " leaves the window " + box(win) + " or is empty");
			}
			if (CopperLayout.overlap(p, b.whisper()) || CopperLayout.overlap(p, b.close())) {
				problems.add(at + box(p) + " runs under the whisper line or the close X");
			}
		}
		if (CopperLayout.overlap(b.rail(), b.stage())) {
			problems.add(at + "the rail " + box(b.rail()) + " overlaps the stage " + box(b.stage()));
		}
		if (!within(b.content(), b.stage()) || b.content()[2] < 150) {
			problems.add(at + "the stage's content " + box(b.content()) + " leaves the stage or is too narrow");
		}
		// The rail's parts: inside it, apart; the record and today have room for at least their two headings and values.
		List<int[]> rail = new ArrayList<>(List.of(b.portrait(), b.nameBox(), b.railText()));
		for (int[] p : rail) {
			if (!within(p, b.rail())) {
				problems.add(at + box(p) + " leaves the rail " + box(b.rail()));
			}
		}
		for (int i = 0; i < rail.size(); i++) {
			for (int j = 0; j < i; j++) {
				if (CopperLayout.overlap(rail.get(i), rail.get(j))) {
					problems.add(at + box(rail.get(i)) + " overlaps " + box(rail.get(j)) + " in the rail");
				}
			}
		}
		if (b.nameBox()[2] < 50) {
			problems.add(at + "the name beside the portrait has only " + b.nameBox()[2] + " pixels");
		}
		// In English the record and today both fit (two headings and values); with labels three or five times as long the
		// buttons may take three rows on a 320x180 screen, and the record still fits.
		if (b.railText()[3] < (need[0] <= 44 + 12 * 6 ? 40 : 20)) {
			problems.add(at + "the record and today have only " + b.railText()[3] + " pixels in the rail");
		}
		// The buttons: three, in Challenge-Trade-Close order, inside the rail under its text, apart, clickable, as wide as they need.
		int[][] buttons = b.buttons();
		if (buttons.length != 3) {
			problems.add(at + buttons.length + " buttons");
			return;
		}
		int inner = b.rail()[2] - 8;
		for (int i = 0; i < buttons.length; i++) {
			int[] x = buttons[i];
			if (!within(x, b.rail()) || CopperLayout.overlap(x, b.railText()) || CopperLayout.overlap(x, b.portrait())) {
				problems.add(at + "button " + i + " " + box(x) + " leaves the rail or covers its text");
			}
			if (x[2] < Math.min(need[i], inner)) {
				problems.add(at + "button " + i + " is cut to " + x[2] + " though it needs " + need[i]);
			}
			if (CopperLayout.at(buttons, x[0] + x[2] / 2.0, x[1] + x[3] / 2.0) != i) {
				problems.add(at + "button " + i + " can't be clicked at its middle");
			}
			if (i > 0) {
				int[] a = buttons[i - 1];
				if (x[1] < a[1] || (x[1] == a[1] && x[0] < a[0] + a[2] + StatusCard.BUTTON_GAP)) {
					problems.add(at + "buttons " + (i - 1) + " and " + i + " overlap or swap places");
				}
			}
		}
		// The team: two to eight cards inside the stage's content, apart, at least the narrowest card wide where that fits.
		int[] c = b.content();
		for (int n = 1; n <= 8; n++) {
			int[][] cards = TrainerLayout.teamCards(c[0], c[1], c[2] - 2, n);
			int cols = TrainerLayout.columns(c[2] - 2, n);
			for (int i = 0; i < n; i++) {
				if (cards[i][0] < c[0] || cards[i][0] + cards[i][2] > c[0] + c[2] || cards[i][2] < Math.min(TrainerLayout.CARD_MIN_W, c[2])) {
					problems.add(at + n + " cards: card " + i + " " + box(cards[i]) + " leaves the stage or is too narrow");
				}
				for (int j = 0; j < i; j++) {
					if (CopperLayout.overlap(cards[i], cards[j])) {
						problems.add(at + n + " cards: card " + i + " overlaps card " + j);
					}
				}
			}
			int rows = (n + cols - 1) / cols;
			if (rows * cols - n >= cols) {
				problems.add(at + n + " cards in " + cols + " columns leave a row empty");
			}
			if (TrainerLayout.teamHeight(c[2] - 2, n) != cards[n - 1][1] + cards[n - 1][3] - c[1]) {
				problems.add(at + n + " cards: the grid's height doesn't match its last card");
			}
		}
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void trainerScreenFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3, 5}) {
				check(s[0] + "x" + s[1] + " x" + times + ": ", layout(s[0], s[1], times), need(times), problems);
			}
		}
		Boxes small = layout(320, 180, 1);
		helper.assertTrue(small.compact(), "320x180 isn't laid out compact");
		helper.assertTrue(!layout(427, 240, 1).compact(), "427x240 is laid out compact");
		// In English the buttons take two rows at most on the narrowest rail, and Challenge keeps its whole label.
		helper.assertTrue(StatusCard.buttonRows(StatusCard.buttonRow(need(1), small.rail()[2] - 8)) <= 2,
			"the English buttons take more than two rows at 320x180");
		// On a roomy screen the team of six sits in two even rows of three, as on the board.
		Boxes wide = layout(640, 360, 1);
		helper.assertValueEqual(TrainerLayout.columns(wide.content()[2] - 2, 6), 3, "columns for a team of six at 640x360");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void trainerScreenHasOneCopperButton(GameTestHelper helper) {
		// Challenge (the main action) is the copper one; Trade and Close are neutral, so copper stays rationed to one button.
		helper.assertValueEqual(TrainerLayout.COPPER, 0, "the copper button's index");
		helper.assertValueEqual(Component.translatable(TrainerScreens.BUTTON_KEYS[TrainerLayout.COPPER]).getString(), "Challenge",
			"the copper button's label");
		int copper = 0;
		for (int i = 0; i < TrainerScreens.BUTTON_KEYS.length; i++) {
			copper += TrainerLayout.copper(i) ? 1 : 0;
		}
		helper.assertValueEqual(copper, 1, "copper buttons");
		// The close X on the lintel and the Close button are both there and apart from the buttons and each other.
		for (int[] s : SCREENS) {
			Boxes b = layout(s[0], s[1], 1);
			for (int[] x : b.buttons()) {
				helper.assertTrue(!CopperLayout.overlap(x, b.close()), "a button runs under the close X at " + s[0] + "x" + s[1]);
			}
		}
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void trainerScreenNewSentencesAreInTheLangFile(GameTestHelper helper) {
		String[] keys = {"cant_challenge", "whisper.trainer", "whisper.record", "whisper.card", "whisper.card_scaled", "whisper.unseen",
			"whisper.party", "whisper.rules", "whisper.challenge", "whisper.trade", "whisper.trade_off", "whisper.close"};
		for (String key : keys) {
			String full = "screen.aliveworkplace.trainer." + key;
			String text = Component.translatable(full, "Hale", "3").getString();
			helper.assertTrue(!text.equals(full) && !text.isBlank(), full + " isn't in the lang file");
		}
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.whisper.challenge", "Hale").getString(),
			"Start a battle with Hale now.", "Challenge's whisper");
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.cant_challenge").getString(), "Can't challenge now",
			"the refusal box's heading");
		helper.succeed();
	}
}
