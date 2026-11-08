package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.Slot;

/**
 * The Pokémon Trader screen's layout in the Village Hall's look (ROADMAP 43.2), which the client draws from
 * {@link PokemonTradeView}'s numbers: every button sits inside the panel, no two framed cells overlap, each offer's cell
 * and ball sit in their own row, a click anywhere on a row picks that row's offer (and nowhere else does), the status
 * lines stay under the party and above the footer, the panel fits the smallest screen, and the footer hint is in en_us.json.
 */
public class PokemonTradeLayoutGameTests implements FabricGameTest {

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void tradeScreenLayoutHoldsTogether(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		PokemonTradeView view = new PokemonTradeView(0, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory());
		helper.assertTrue(PokemonTradeView.WIDTH <= 320 - 16 && PokemonTradeView.HEIGHT <= 240 - 16,
			"the panel doesn't fit a 320x240 GUI screen: " + PokemonTradeView.WIDTH + "x" + PokemonTradeView.HEIGHT);
		List<int[]> cells = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			Slot slot = view.slots.get(i);
			if (slot.x < -100) {
				continue;
			}
			// a framed cell is the slot's 16 pixels and a pixel round it
			int[] cell = {i, slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17};
			if (cell[1] < 0 || cell[2] < 0 || cell[3] > PokemonTradeView.WIDTH || cell[4] > PokemonTradeView.HEIGHT - 14) {
				problems.add("slot " + i + " is outside the panel at " + slot.x + "," + slot.y);
			}
			for (int[] other : cells) {
				if (cell[1] < other[3] && other[1] < cell[3] && cell[2] < other[4] && other[2] < cell[4]) {
					problems.add("slots " + other[0] + " and " + i + " overlap");
				}
			}
			cells.add(cell);
		}
		for (int row = 0; row < PokemonTradeView.MAX_OFFERS; row++) {
			for (int slot : new int[]{PokemonTradeView.OFFERS + row, PokemonTradeView.BALLS + row}) {
				Slot s = view.slots.get(slot);
				int hit = PokemonTradeView.offerAt(s.x + 8, s.y + 8);
				if (hit != row) {
					problems.add("slot " + slot + "'s middle is on row " + hit + ", not " + row);
				}
			}
			int top = PokemonTradeView.cardY(row);
			if (top + PokemonTradeView.ROW_H > PokemonTradeView.LIST_Y + PokemonTradeView.LIST_H) {
				problems.add("row " + row + " runs past the list");
			}
			if (PokemonTradeView.offerAt(PokemonTradeView.CARD_X + PokemonTradeView.CARD_W / 2, top + 20) != row) {
				problems.add("the cost line of row " + row + " doesn't pick it");
			}
			if (row + 1 < PokemonTradeView.MAX_OFFERS && PokemonTradeView.offerAt(PokemonTradeView.CARD_X + 40, top + PokemonTradeView.ROW_H) != -1) {
				problems.add("the gap under row " + row + " picks an offer");
			}
		}
		Slot party = view.slots.get(PokemonTradeView.PARTY);
		int[][] outside = {{PokemonTradeView.CARD_X - 1, PokemonTradeView.CARD_Y + 5}, {PokemonTradeView.CARD_X + 5, PokemonTradeView.CARD_Y - 1},
			{party.x + 8, party.y + 8}, {PokemonTradeView.CARD_X + 5, PokemonTradeView.cardY(PokemonTradeView.MAX_OFFERS)}, {-5, -5}};
		for (int[] at : outside) {
			if (PokemonTradeView.offerAt(at[0], at[1]) != -1) {
				problems.add(at[0] + "," + at[1] + " picks an offer");
			}
		}
		Slot lastParty = view.slots.get(PokemonTradeView.PARTY + 5);
		if (PokemonTradeView.STATUS_Y < lastParty.y + 17 + 4) {
			problems.add("the status lines start inside the party");
		}
		if (PokemonTradeView.STATUS_BOTTOM - PokemonTradeView.STATUS_Y < 40 || PokemonTradeView.STATUS_BOTTOM > PokemonTradeView.HEIGHT - 14) {
			problems.add("the status lines have no room for four lines above the footer");
		}
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
			.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String key : List.of("screen.aliveworkplace.pokemon_trader.hint", "screen.aliveworkplace.pokemon_trader.party",
				"screen.aliveworkplace.pokemon_trader.no_offers", "screen.aliveworkplace.pokemon_trader.no_party")) {
				if (!lang.has(key)) {
					problems.add("the screen draws " + key + ", which isn't in en_us.json");
				}
			}
		} catch (Exception e) {
			problems.add("can't read en_us.json: " + e);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
