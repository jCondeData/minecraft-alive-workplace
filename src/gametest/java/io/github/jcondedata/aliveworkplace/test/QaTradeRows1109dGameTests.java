package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA lane D, 2026-11-09, 43.2 (the Pokemon Trader screen in the Hall's look): "a click anywhere on an offer's row picks
 * it". Every pixel of the panel, and a margin round it, is asked which row it picks: each row's whole card from its first
 * to its last pixel picks that row, and nothing else on the panel (the gaps, the list's frame, the party, the panel's
 * edge, the space below the fourth row) picks any.
 */
public class QaTradeRows1109dGameTests implements FabricGameTest {
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyPixelOfARowPicksItAndNothingElseDoes(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[] hits = new int[PokemonTradeView.MAX_OFFERS];
		for (int x = -20; x < PokemonTradeView.WIDTH + 20; x++) {
			for (int y = -20; y < PokemonTradeView.HEIGHT + 20; y++) {
				int expected = -1;
				for (int row = 0; row < PokemonTradeView.MAX_OFFERS; row++) {
					int top = PokemonTradeView.cardY(row);
					if (x >= PokemonTradeView.CARD_X && x < PokemonTradeView.CARD_X + PokemonTradeView.CARD_W && y >= top && y < top + PokemonTradeView.ROW_H) {
						expected = row;
					}
				}
				int got = PokemonTradeView.offerAt(x, y);
				if (got != expected && problems.size() < 8) {
					problems.add(x + "," + y + " picks " + got + ", expected " + expected);
				}
				if (got >= 0 && got < hits.length) {
					hits[got]++;
				}
			}
		}
		int area = PokemonTradeView.CARD_W * PokemonTradeView.ROW_H;
		for (int row = 0; row < hits.length; row++) {
			if (hits[row] != area) {
				problems.add("row " + row + " is picked from " + hits[row] + " pixels, not its card's " + area);
			}
			int top = PokemonTradeView.cardY(row);
			if (top < PokemonTradeView.LIST_Y || top + PokemonTradeView.ROW_H > PokemonTradeView.LIST_Y + PokemonTradeView.LIST_H
				|| PokemonTradeView.CARD_X < PokemonTradeView.LIST_X || PokemonTradeView.CARD_X + PokemonTradeView.CARD_W > PokemonTradeView.LIST_X + PokemonTradeView.LIST_W) {
				problems.add("row " + row + "'s card isn't inside the offers list");
			}
		}
		// the party box on the right never picks an offer
		if (PokemonTradeView.CARD_X + PokemonTradeView.CARD_W > PokemonTradeView.PARTY_X) {
			problems.add("the offer cards reach into the party's box");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
