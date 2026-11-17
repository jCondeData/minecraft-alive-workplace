package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * B1354 (found by lane-d-1117-0107 in 48.4): at 320x180 (GUI scale 3) the Pokemon Trader's window sat 3 pixels from the
 * screen's top and the Copperline plaque rises 6 over it, so the plaque's border was cut off. The window now keeps the
 * Hall's 8-pixel margin; the test from QaChronicleCopper1117dBugGameTests (cd1bb371a), plus the margin on every size and
 * the status lines that still fit.
 */
public class QaTraderPlaqueB1354GameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{320, 180}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}};

	private static int[] window(int[] screen) {
		return new int[]{(screen[0] - PokemonTradeView.WIDTH) / 2, (screen[1] - PokemonTradeView.HEIGHT) / 2, PokemonTradeView.WIDTH,
			PokemonTradeView.HEIGHT};
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void thePokemonTradersPlaqueStaysOnA320x180Screen(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] screen : new int[][]{{320, 180}, {427, 240}}) {
			int[] win = window(screen);
			int[] plaque = CopperLayout.plaque(win, 6 * "Pokemon Trader Ash".length());
			if (plaque[1] < 0 || plaque[0] < 0 || plaque[0] + plaque[2] > screen[0]) {
				problems.add(screen[0] + "x" + screen[1] + ": the plaque [" + plaque[0] + "," + plaque[1] + " " + plaque[2] + "x" + plaque[3]
					+ "] starts " + (-plaque[1]) + " pixels above the screen's top");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTradersWindowKeepsTheHallsMarginOnEverySize(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] screen : SCREENS) {
			int[] win = window(screen);
			int[] hall = CopperLayout.window(screen[0], screen[1]);
			// as the Hall's window: at least MARGIN pixels round it, and never taller or wider than the Hall's on that screen
			if (win[0] < CopperLayout.MARGIN || win[1] < CopperLayout.MARGIN || win[0] + win[2] > screen[0] - CopperLayout.MARGIN
				|| win[1] + win[3] > screen[1] - CopperLayout.MARGIN || win[2] > hall[2] || win[3] > hall[3]) {
				problems.add(screen[0] + "x" + screen[1] + ": the window " + java.util.Arrays.toString(win) + " leaves less than "
					+ CopperLayout.MARGIN + " pixels round it (the Hall's is " + java.util.Arrays.toString(hall) + ")");
			}
			int[] plaque = CopperLayout.plaque(win, 1000);
			if (plaque[1] < 0 || plaque[0] < 0 || plaque[0] + plaque[2] > screen[0]) {
				problems.add(screen[0] + "x" + screen[1] + ": the widest plaque " + java.util.Arrays.toString(plaque) + " leaves the screen");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theShorterWindowStillShowsFourOffersAndSixStatusLines(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] whisper = CopperLayout.whisper(new int[]{0, 0, PokemonTradeView.WIDTH, PokemonTradeView.HEIGHT});
		int last = PokemonTradeView.cardY(PokemonTradeView.MAX_OFFERS - 1) + PokemonTradeView.ROW_H;
		if (last > PokemonTradeView.LIST_Y + PokemonTradeView.LIST_H || last > whisper[1] - 2) {
			problems.add("the fourth offer's row ends at " + last + ", past the list or the whisper line at " + whisper[1]);
		}
		if (PokemonTradeView.ROW_H < 26) {
			problems.add("an offer row is " + PokemonTradeView.ROW_H + " pixels, too short for its cell, name and price");
		}
		// the screen draws a status line while y + 9 <= STATUS_BOTTOM, 10 pixels apart
		int lines = 0;
		for (int y = PokemonTradeView.STATUS_Y; y + 9 <= PokemonTradeView.STATUS_BOTTOM; y += 10) {
			lines++;
		}
		if (lines < 6) {
			problems.add("only " + lines + " status lines fit under the party (6 did before B1354)");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
