package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * The status card's buttons stay inside the card (B1103: In chat / Hall page / Close ran past its right edge at their
 * roomy widths). Checks {@link StatusCard#buttonRow}, the layout the client's StatusCardScreen draws from, with the
 * English labels and with labels three times as long (other languages), on the card's usual inner width (200 - 2 * 8)
 * and on a narrow one. Label widths are 6 pixels a character (vanilla's widest common letters) put through the Hall's
 * button width (TableStyle.buttonWidth: at least 44, the label plus 12).
 */
public class StatusCardButtonsGameTests implements FabricGameTest {
	private static final int INNER = 200 - 2 * 8;

	private static int need(String label) {
		return Math.max(44, label.length() * 6 + 12);
	}

	private static List<String> labels(int times) {
		List<String> out = new ArrayList<>();
		for (String key : new String[]{"screen.aliveworkplace.card.job.chat", "screen.aliveworkplace.card.job.hall",
			"screen.aliveworkplace.card.close"}) {
			String text = Component.translatable(key).getString();
			out.add(text.repeat(times));
		}
		return out;
	}

	/** Every box is inside [0, inner), no two in a row overlap, and they keep the labels' order. */
	private static void check(GameTestHelper helper, List<String> labels, int inner, List<String> problems) {
		int[] need = labels.stream().mapToInt(StatusCardButtonsGameTests::need).toArray();
		int[][] boxes = StatusCard.buttonRow(need, inner);
		if (boxes.length != labels.size()) {
			problems.add(labels + ": " + boxes.length + " boxes");
			return;
		}
		for (int i = 0; i < boxes.length; i++) {
			int[] b = boxes[i];
			if (b[0] < 0 || b[2] <= 0 || b[0] + b[2] > inner) {
				problems.add(labels.get(i) + " at x " + b[0] + " w " + b[2] + " leaves the card's " + inner + " pixels");
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
	public void statusCardButtonsStayInsideTheCard(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<String> english = labels(1);
		helper.assertTrue(english.get(0).equals("In chat") && english.get(2).equals("Close"),
			"the card's button labels aren't the English ones: " + english);
		for (int inner : new int[]{INNER, 120, 60}) {
			check(helper, english, inner, problems);
			check(helper, labels(3), inner, problems);
		}
		// one long label and none at all
		check(helper, List.of("x".repeat(80)), INNER, problems);
		helper.assertTrue(StatusCard.buttonRow(new int[0], INNER).length == 0, "no buttons gave boxes");
		// the English labels stay on one row on the usual card; three-times-long ones wrap
		int[] englishNeed = english.stream().mapToInt(StatusCardButtonsGameTests::need).toArray();
		int[] longNeed = labels(3).stream().mapToInt(StatusCardButtonsGameTests::need).toArray();
		helper.assertTrue(StatusCard.buttonRows(StatusCard.buttonRow(englishNeed, INNER)) == 1,
			"the English buttons don't fit one row");
		helper.assertTrue(StatusCard.buttonRows(StatusCard.buttonRow(longNeed, INNER)) > 1,
			"three-times-long labels didn't wrap");
		// with room to spare a button keeps its roomy width (the Hall's look)
		int[][] roomy = StatusCard.buttonRow(new int[]{44, 44}, INNER);
		helper.assertTrue(roomy[0][2] == 44 + StatusCard.BUTTON_ROOM && roomy[1][0] == roomy[0][2] + StatusCard.BUTTON_GAP,
			"two short buttons lost their room: " + roomy[0][2] + ", " + roomy[1][0]);
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
