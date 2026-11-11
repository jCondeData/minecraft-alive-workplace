package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.StatusCard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * QA (lane C, 2026-11-11) of B1103's fix: the status card's buttons stay inside the card. Written from the bug's expected
 * behavior (every button inside the card: shrink, wrap to a second row, or widen) and the layout's promise (in one row at
 * their roomy width when that fits; else one row at what they need, the room left shared out; else wrapped, a button wider
 * than the card cut to it), checked over 2000 seeded random button sets (1 to 6 buttons, 0 to 260 pixels each) on card
 * widths from 20 to 260, plus the exact boundaries: one pixel either side of "roomy fits" and of "fits in one row".
 */
public class QaStatusCardRows1111cGameTests implements FabricGameTest {
	private static final int GAP = StatusCard.BUTTON_GAP;
	private static final int ROOM = StatusCard.BUTTON_ROOM;

	private static int sum(int[] need) {
		return Arrays.stream(need).sum() + GAP * Math.max(0, need.length - 1);
	}

	/** Every promise of the layout for {@code need} on {@code inner} pixels; the broken ones go in {@code problems}. */
	private static void check(int[] need, int inner, List<String> problems) {
		String what = Arrays.toString(need) + " on " + inner + ": ";
		int[][] boxes = StatusCard.buttonRow(need, inner);
		if (boxes.length != need.length) {
			problems.add(what + boxes.length + " boxes for " + need.length + " buttons");
			return;
		}
		int tight = sum(need);
		int roomy = tight + ROOM * need.length;
		int rows = StatusCard.buttonRows(boxes);
		for (int i = 0; i < boxes.length; i++) {
			int[] b = boxes[i];
			if (b[0] < 0 || b[1] < 0 || b[0] + b[2] > inner) {
				problems.add(what + "button " + i + " at x " + b[0] + " width " + b[2] + " row " + b[1] + " leaves the card");
			}
			if (b[2] < Math.min(need[i], inner)) {
				problems.add(what + "button " + i + " cut to " + b[2] + " though " + Math.min(need[i], inner) + " fits");
			}
			if (i > 0) {
				int[] a = boxes[i - 1];
				boolean sameRow = a[1] == b[1];
				if (b[1] < a[1] || b[1] > a[1] + 1 || (sameRow && b[0] < a[0] + a[2] + GAP)) {
					problems.add(what + "buttons " + (i - 1) + " and " + i + " overlap, swap or skip a row");
				}
				if (!sameRow && b[0] != 0) {
					problems.add(what + "button " + i + " starts a row at x " + b[0]);
				}
				// a wrap only when the button didn't fit after the one before it
				if (!sameRow && a[0] + a[2] + GAP + Math.min(need[i], inner) <= inner) {
					problems.add(what + "button " + i + " wrapped though it fit beside button " + (i - 1));
				}
			}
		}
		if (need.length == 0) {
			return;
		}
		if (boxes[0][0] != 0 || boxes[0][1] != 0) {
			problems.add(what + "the first button isn't at the card's left on the first row");
		}
		if (tight <= inner && rows != 1) {
			problems.add(what + rows + " rows though the buttons fit one (" + tight + " pixels)");
		}
		if (roomy <= inner) {
			for (int i = 0; i < boxes.length; i++) {
				if (boxes[i][2] != need[i] + ROOM) {
					problems.add(what + "with room to spare button " + i + " is " + boxes[i][2] + " wide, not " + (need[i] + ROOM));
				}
			}
		} else if (tight <= inner) {
			int[] last = boxes[boxes.length - 1];
			if (last[0] + last[2] != inner) {
				problems.add(what + "one row at what they need doesn't share the room out to the card's edge: it ends at " + (last[0] + last[2]));
			}
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (int i = 0; i < boxes.length; i++) {
				int extra = boxes[i][2] - need[i];
				min = Math.min(min, extra);
				max = Math.max(max, extra);
			}
			if (max - min > 1 || min < 0 || max > ROOM) {
				problems.add(what + "the room left isn't shared out evenly: extras from " + min + " to " + max);
			}
		} else if (rows < 2 && need.length > 1) {
			problems.add(what + "too wide for one row but not wrapped");
		}
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void randomButtonSetsAlwaysFitTheCard(GameTestHelper helper) {
		RandomSource random = RandomSource.create(1103L);
		List<String> problems = new ArrayList<>();
		for (int t = 0; t < 2000 && problems.size() < 10; t++) {
			int[] need = new int[1 + random.nextInt(6)];
			for (int i = 0; i < need.length; i++) {
				need[i] = random.nextInt(4) == 0 ? random.nextInt(261) : 44 + random.nextInt(60);
			}
			check(need, 20 + random.nextInt(241), problems);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** One pixel either side of each boundary: the roomy width just fitting, the tight one just fitting, a single button as wide as the card. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void eachLayoutBoundaryFromBothSides(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] three = {50, 62, 44};
		int roomy = sum(three) + ROOM * 3;
		int tight = sum(three);
		for (int inner : new int[]{roomy + 1, roomy, roomy - 1, tight + 1, tight, tight - 1}) {
			check(three, inner, problems);
		}
		int[][] atRoomy = StatusCard.buttonRow(three, roomy);
		helper.assertTrue(atRoomy[0][2] == 50 + ROOM && atRoomy[2][0] + atRoomy[2][2] == roomy, "roomy exactly fitting isn't roomy: " + Arrays.deepToString(atRoomy));
		int[][] oneShort = StatusCard.buttonRow(three, roomy - 1);
		helper.assertTrue(StatusCard.buttonRows(oneShort) == 1 && oneShort[2][0] + oneShort[2][2] == roomy - 1,
			"a pixel short of roomy isn't one row to the edge: " + Arrays.deepToString(oneShort));
		helper.assertTrue(StatusCard.buttonRows(StatusCard.buttonRow(three, tight)) == 1, "exactly fitting tight wrapped");
		helper.assertTrue(StatusCard.buttonRows(StatusCard.buttonRow(three, tight - 1)) == 2, "a pixel too narrow didn't wrap to two rows");
		// one button as wide as the card, one wider, and one with nothing beside it
		for (int w : new int[]{183, 184, 185, 400}) {
			check(new int[]{w}, 184, problems);
			check(new int[]{w, 44}, 184, problems);
		}
		helper.assertTrue(StatusCard.buttonRow(new int[]{400}, 184)[0][2] == 184, "a label wider than the card isn't cut to it");
		helper.assertTrue(StatusCard.buttonRows(new int[0][]) == 1, "no buttons isn't counted as one row (the height the card keeps for its button row)");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
