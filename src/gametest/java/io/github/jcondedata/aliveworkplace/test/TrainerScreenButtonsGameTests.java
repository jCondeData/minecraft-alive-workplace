package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * The Trainer screen's Challenge / Trade / Close stay inside its right side (B1131: they were laid out at their roomy
 * text widths with no fit, so longer translated labels ran past the panel's edge, as the status card's did in B1103).
 * Checks {@link StatusCard#buttonRow}, the layout the client's TrainerScreen draws from, with the screen's own labels
 * ({@link TrainerScreens#BUTTON_KEYS}) in English and three times as long (other languages), on the right side's widest
 * width (panel 440 - side 112 - 6 - 10 - 8), on a 320-pixel window's (304 - 136) and on a narrow one. Label widths are 6
 * pixels a character (vanilla's widest common letters) put through the Hall's button width (TableStyle.buttonWidth: at
 * least 44, the label plus 12).
 */
public class TrainerScreenButtonsGameTests implements FabricGameTest {
	private static final int WIDEST = 440 - 112 - 6 - 10 - 8;
	private static final int SMALL = 320 - 16 - 112 - 6 - 10 - 8;

	private static int need(String label) {
		return Math.max(44, label.length() * 6 + 12);
	}

	private static List<String> labels(int times) {
		List<String> out = new ArrayList<>();
		for (String key : TrainerScreens.BUTTON_KEYS) {
			out.add(Component.translatable(key).getString().repeat(times));
		}
		return out;
	}

	/** Every box is inside [0, inner), none is cut below what it needs unless wider than the side, and they keep their order. */
	private static void check(List<String> labels, int inner, List<String> problems) {
		int[] need = labels.stream().mapToInt(TrainerScreenButtonsGameTests::need).toArray();
		int[][] boxes = StatusCard.buttonRow(need, inner);
		if (boxes.length != labels.size()) {
			problems.add(labels + ": " + boxes.length + " boxes");
			return;
		}
		for (int i = 0; i < boxes.length; i++) {
			int[] b = boxes[i];
			if (b[0] < 0 || b[2] <= 0 || b[0] + b[2] > inner) {
				problems.add(labels.get(i) + " at x " + b[0] + " w " + b[2] + " leaves the panel's " + inner + " pixels");
			}
			if (b[2] < Math.min(need[i], inner)) {
				problems.add(labels.get(i) + " is cut to " + b[2] + " though it needs " + need[i]);
			}
			if (i > 0) {
				int[] a = boxes[i - 1];
				if (b[1] < a[1] || (b[1] == a[1] && b[0] < a[0] + a[2] + StatusCard.BUTTON_GAP)) {
					problems.add(labels.get(i - 1) + " and " + labels.get(i) + " overlap or swap places");
				}
			}
		}
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void trainerButtonsStayInsideThePanel(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<String> english = labels(1);
		helper.assertTrue(english.equals(List.of("Challenge", "Trade", "Close")),
			"the Trainer screen's button labels aren't the English ones: " + english);
		for (int inner : new int[]{WIDEST, SMALL, 100, 50}) {
			check(english, inner, problems);
			check(labels(3), inner, problems);
			check(labels(5), inner, problems);
		}
		int[] englishNeed = english.stream().mapToInt(TrainerScreenButtonsGameTests::need).toArray();
		int[] longNeed = labels(5).stream().mapToInt(TrainerScreenButtonsGameTests::need).toArray();
		// on the widest panel the English buttons keep the roomy look they had (label width + 16), on one row
		int[][] roomy = StatusCard.buttonRow(englishNeed, WIDEST);
		helper.assertTrue(StatusCard.buttonRows(roomy) == 1, "the English buttons don't fit one row on the widest panel");
		for (int i = 0; i < roomy.length; i++) {
			helper.assertTrue(roomy[i][2] == englishNeed[i] + StatusCard.BUTTON_ROOM,
				english.get(i) + " lost its room: " + roomy[i][2] + " for " + englishNeed[i]);
		}
		// five-times-long labels wrap in a small window rather than running off it
		helper.assertTrue(StatusCard.buttonRows(StatusCard.buttonRow(longNeed, SMALL)) > 1,
			"five-times-long labels didn't wrap in a small window");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
