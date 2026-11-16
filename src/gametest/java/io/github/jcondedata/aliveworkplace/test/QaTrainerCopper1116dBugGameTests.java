package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ui.TrainerLayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA (2026-11-16, lane d), a bug test for 48.2 (the Trainer screen in Copperline). Milestone 48: "nothing a screen does
 * today may be lost ... only how it looks and reads". Before Copperline every team cell showed its level whole ("Lv 40",
 * orange when brought down to the level cap). In Copperline a team card draws its model in a well as tall as the card
 * (TrainerLayout.CARD_H - 4, 3 pixels in from the card's left, the text 3 pixels after it and 3 before the card's right),
 * and a capped level gets the 8-pixel warning glyph and 2 pixels before it. With four cards in a row (a team of four, a
 * mid-tier trainer, at 427x240, the board's own size, and at 480x270 and 640x360) the card is 64 to 69 pixels wide, which
 * leaves 29 to 34 pixels for the level and 19 to 24 for a capped one: "Lv 40" (28 pixels in the game's font) is cut to
 * "L…" on every capped card, and "Lv 100" on every uncapped one at 427x240. On the 320x180 screen (GUI scale 3) the party's
 * rows give the level 16% of the stage (29 pixels), so a party Pokémon at "Lv 100" (34) runs into its status glyph.
 */
public class QaTrainerCopper1116dBugGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {640, 360}, {320, 180}};

	/** The width of {@code s} in the game's default font (the vanilla ASCII advances; letters and digits 6, space 4). */
	private static int width(String s) {
		int w = 0;
		for (char ch : s.toCharArray()) {
			w += switch (ch) {
				case ' ' -> 4;
				case 'i', '.', ',', ':', ';', '!', '|', '\'' -> 2;
				case 'l', '`' -> 3;
				case 't', 'I', '[', ']', '"', '*' -> 4;
				case 'f', 'k', '<', '>', '(', ')', '{', '}' -> 5;
				default -> 6;
			};
		}
		return w;
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aTeamCardShowsItsLevelWhole(GameTestHelper helper) {
		String capped = Component.translatable("screen.aliveworkplace.trainer.level", 40).getString();
		String top = Component.translatable("screen.aliveworkplace.trainer.level", 100).getString();
		int well = TrainerLayout.CARD_H - 4;
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			TrainerLayout.Boxes b = TrainerLayout.of(s[0], s[1], 120, new int[]{66, 44, 44});
			int[] c = b.content();
			int w = c[2] - 2;
			for (int n = 1; n <= 6; n++) {
				int cw = TrainerLayout.teamCards(c[0], c[1], w, n)[0][2];
				int text = cw - 3 - well - 3 - 3;
				if (width(capped) > text - 10) {
					problems.add(s[0] + "x" + s[1] + ", a team of " + n + ": a capped \"" + capped + "\" (" + width(capped) + " px) is cut to " + (text - 10) + " px");
				}
				if (width(top) > text) {
					problems.add(s[0] + "x" + s[1] + ", a team of " + n + ": \"" + top + "\" (" + width(top) + " px) is cut to " + text + " px");
				}
			}
			// The party's rows: the level from 42% of the stage's width, the status glyph at 58%.
			int levelRoom = w * 58 / 100 - w * 42 / 100;
			if (width(top) > levelRoom) {
				problems.add(s[0] + "x" + s[1] + ": a party row's \"" + top + "\" (" + width(top) + " px) runs into its status glyph " + levelRoom + " px on");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
