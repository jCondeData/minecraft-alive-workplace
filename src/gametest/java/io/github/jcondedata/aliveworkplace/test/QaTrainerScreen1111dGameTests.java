package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-11, lane d), written from the specs of B1131 (the Trainer screen's Challenge / Trade / Close fitted inside
 * its panel "at any GUI scale and language") and 43.1 (the team's Pokémon not seen yet hidden until the player beats the
 * trainer once; the wiki's decision: the first half, rounded up, shows). The feature's own test checks four widths; these
 * check each layout rule at its boundary from both sides (roomy fits exactly / one pixel short, tight fits exactly / one
 * pixel short, a side narrower than one button), then every width from 1 to 400 pixels, and the reveal's half for every
 * team size from 0 to 6, before and after a win.
 */
public class QaTrainerScreen1111dGameTests implements FabricGameTest {
	/** The Hall's button width for a label {@code chars} letters long at 6 pixels a letter (TableStyle.buttonWidth). */
	private static int need(int chars) {
		return Math.max(44, chars * 6 + 12);
	}

	/** Every box is inside [0, inner), never cut below what it needs (unless the side is narrower), and in order without overlap. */
	private static void inside(int[] need, int inner, List<String> problems) {
		int[][] boxes = StatusCard.buttonRow(need, inner);
		if (boxes.length != need.length) {
			problems.add(inner + "px: " + boxes.length + " boxes for " + need.length + " buttons");
			return;
		}
		for (int i = 0; i < boxes.length; i++) {
			int[] b = boxes[i];
			if (b[0] < 0 || b[2] <= 0 || b[0] + b[2] > inner) {
				problems.add(inner + "px: button " + i + " at x " + b[0] + " w " + b[2] + " leaves the side");
			}
			if (b[2] < Math.min(need[i], inner)) {
				problems.add(inner + "px: button " + i + " cut to " + b[2] + " though it needs " + need[i]);
			}
			if (i > 0) {
				int[] a = boxes[i - 1];
				if (b[1] < a[1] || (b[1] == a[1] && b[0] < a[0] + a[2] + StatusCard.BUTTON_GAP)) {
					problems.add(inner + "px: buttons " + (i - 1) + " and " + i + " overlap or swap places");
				}
			}
		}
	}

	private static int end(int[][] boxes) {
		int[] last = boxes[boxes.length - 1];
		return last[0] + last[2];
	}

	/**
	 * B1131's layout at each boundary, both sides: Challenge (9 letters, 66 px), Trade and Close (44 px each) need 170 px
	 * with the gaps, and 218 px roomy. 218: one row, each button its roomy width. 217: one row, at least what each needs,
	 * filling the side to the pixel and none roomier than roomy. 170: one row at exactly what they need. 169: wrapped,
	 * nothing cut. 65 (narrower than Challenge alone): three rows, Challenge cut to the side. Then every width 1-400 with
	 * the English sizes and with labels three and five times as long.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theButtonsFitAtEveryBoundaryAndWidth(GameTestHelper helper) {
		helper.assertValueEqual(TrainerScreens.BUTTON_KEYS.length, 3, "the Trainer screen's button count");
		int[] need = {need(9), need(5), need(5)};
		int tight = need[0] + need[1] + need[2] + 2 * StatusCard.BUTTON_GAP;
		int roomy = tight + 3 * StatusCard.BUTTON_ROOM;
		helper.assertValueEqual(tight, 170, "the English buttons' tight width");

		int[][] atRoomy = StatusCard.buttonRow(need, roomy);
		helper.assertValueEqual(StatusCard.buttonRows(atRoomy), 1, "rows at exactly the roomy width");
		for (int i = 0; i < 3; i++) {
			helper.assertValueEqual(atRoomy[i][2], need[i] + StatusCard.BUTTON_ROOM, "button " + i + "'s width at exactly the roomy width");
		}
		helper.assertValueEqual(end(atRoomy), roomy, "the roomy row's right edge");

		int[][] oneShort = StatusCard.buttonRow(need, roomy - 1);
		helper.assertValueEqual(StatusCard.buttonRows(oneShort), 1, "rows one pixel short of roomy");
		helper.assertValueEqual(end(oneShort), roomy - 1, "one pixel short of roomy, the row doesn't fill the side");
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(oneShort[i][2] >= need[i] && oneShort[i][2] <= need[i] + StatusCard.BUTTON_ROOM,
				"button " + i + " one pixel short of roomy is " + oneShort[i][2] + " wide (needs " + need[i] + ")");
		}

		int[][] atTight = StatusCard.buttonRow(need, tight);
		helper.assertValueEqual(StatusCard.buttonRows(atTight), 1, "rows at exactly the tight width");
		for (int i = 0; i < 3; i++) {
			helper.assertValueEqual(atTight[i][2], need[i], "button " + i + "'s width at exactly the tight width");
		}
		helper.assertValueEqual(end(atTight), tight, "the tight row's right edge");

		int[][] tightShort = StatusCard.buttonRow(need, tight - 1);
		helper.assertTrue(StatusCard.buttonRows(tightShort) > 1, "one pixel short of tight, the buttons didn't wrap");
		List<String> problems = new ArrayList<>();
		inside(need, tight - 1, problems);

		int narrow = need[0] - 1;
		int[][] tiny = StatusCard.buttonRow(need, narrow);
		helper.assertValueEqual(StatusCard.buttonRows(tiny), 3, "rows on a side narrower than Challenge");
		helper.assertValueEqual(tiny[0][2], narrow, "Challenge's width on a side narrower than it");
		inside(need, narrow, problems);

		int[][] sizes = {need, {need(27), need(15), need(15)}, {need(45), need(25), need(25)}, {need(1), need(1), need(1)}};
		for (int[] n : sizes) {
			for (int inner = 1; inner <= 400; inner++) {
				inside(n, inner, problems);
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " layout problems, first: " + problems.subList(0, Math.min(5, problems.size())));
		helper.succeed();
	}

	/**
	 * 43.1: before the first win the player sees the first half of the team (rounded up, the wiki's decision) and the rest
	 * hidden; after a win, all of it. Every team size 0-6 both ways: never more than the team, never nothing of a team, and
	 * a one-Pokémon team shows its only one.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTeamsFirstHalfShowsUntilAWin(GameTestHelper helper) {
		int[] before = {0, 1, 1, 2, 2, 3, 3};
		for (int size = 0; size <= 6; size++) {
			helper.assertValueEqual(TrainerScreens.shown(false, size), before[size], "shown of a team of " + size + " before a win");
			helper.assertValueEqual(TrainerScreens.shown(true, size), size, "shown of a team of " + size + " after a win");
			if (size > 1) {
				helper.assertTrue(TrainerScreens.shown(false, size) < size, "a team of " + size + " hides nothing before a win");
			}
		}
		helper.succeed();
	}
}
