package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCupMatches;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonPartners;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * QA for ROADMAP 28.22 with the real Cobblemon, from the spec: each theme's type rule from both sides (a two-type
 * Pokémon with one of the Cup's types goes, one with neither stays home and is told why, word for word); the Little
 * Cup's boundary (a first stage that evolves goes at any level; a middle stage, one that never evolves and a final stage
 * stay home); the Workers' Cup's reasons at 0 and 1 days and its counter never twice for one day; the Grand Cup's bans
 * with their reasons.
 */
public class QaCupThemesCompatTests implements FabricGameTest {
	private static CupThemes.Theme theme(GameTestHelper helper, String name) {
		CupThemes.Theme theme = CupThemes.get(AliveWorkplace.id(name));
		helper.assertTrue(theme != null, name + " isn't loaded: " + CupThemes.loaded().keySet());
		return theme;
	}

	private static Pokemon make(String spec) {
		return PokemonProperties.Companion.parse(spec, " ", "=").create();
	}

	/** Fills a fresh player's party with {@code specs} and returns what the filter takes; {@code why} gets the left-out lines. */
	private static List<String> takes(GameTestHelper helper, CupThemes.Theme theme, List<Pokemon> party, List<String> why) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.forEach(store::add);
		List<Component> leftOut = new ArrayList<>();
		List<String> going = CobblemonCupMatches.eligible(store, theme, leftOut).stream().map(p -> p.getSpecies().getName().toLowerCase(Locale.ROOT)).toList();
		leftOut.forEach(c -> why.add(c.getString()));
		helper.getLevel().getServer().getPlayerList().remove(player);
		return going;
	}

	private static void types(GameTestHelper helper, String themeId, List<String> go, List<String> stay) {
		CupThemes.Theme t = theme(helper, themeId);
		List<Pokemon> party = new ArrayList<>();
		stay.forEach(s -> party.add(make(s + " level=30")));
		go.forEach(s -> party.add(make(s + " level=30")));
		List<String> why = new ArrayList<>();
		List<String> going = takes(helper, t, party, why);
		helper.assertTrue(going.equals(go), themeId + " takes " + going + ", expected " + go);
		List<String> expected = new ArrayList<>();
		for (Pokemon p : party.subList(0, stay.size())) {
			expected.add(p.getDisplayName(false).getString() + ": not one of the Cup's types");
		}
		helper.assertTrue(why.equals(expected), themeId + " says " + why + ", expected " + expected);
	}

	/** Each typed theme, both sides of its type rule: a two-type Pokémon with one of the Cup's types goes; one with neither stays home, told why. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupThemesCompat")
	public void eachThemesTypesFromBothSides(GameTestHelper helper) {
		types(helper, "blossom_cup", List.of("butterfree", "togepi"), List.of("zubat"));
		types(helper, "sun_cup", List.of("charizard", "chinchou"), List.of("pidgey"));
		types(helper, "harvest_cup", List.of("pidgey", "onix"), List.of("squirtle"));
		types(helper, "lantern_cup", List.of("sableye", "natu"), List.of("machop"));
		types(helper, "frost_cup", List.of("steelix", "swinub"), List.of("pidgey"));
		helper.succeed();
	}

	/** The Little Cup: a first stage that evolves goes whatever its level; a middle stage (Pikachu, after Pichu), one that never evolves and a final stage stay home. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupThemesCompat")
	public void theLittleCupsStageFromBothSides(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "little_cup");
		List<Pokemon> party = List.of(make("pikachu level=20"), make("ditto level=20"), make("charizard level=60"), make("eevee level=100"), make("bulbasaur level=1"));
		List<String> why = new ArrayList<>();
		List<String> going = takes(helper, t, party, why);
		helper.assertTrue(going.equals(List.of("eevee", "bulbasaur")), "the Little Cup takes " + going);
		helper.assertTrue(why.equals(List.of("Pikachu: only first-stage Pokémon that can still evolve may come", "Ditto: only first-stage Pokémon that can still evolve may come",
			"Charizard: only first-stage Pokémon that can still evolve may come")), "why: " + why);
		helper.succeed();
	}

	/** The Workers' Cup's reasons at 0 and 1 days (one day, not "1 days"); a second count the same day adds nothing; 3 days goes. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupThemesCompat")
	public void theWorkersCupsDaysFromBothSides(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "workers_cup");
		Pokemon none = make("gastly level=30");
		Pokemon one = make("machop level=30");
		helper.assertTrue(CobblemonPartners.countDay(one, 10) && !CobblemonPartners.countDay(one, 10), "counted twice on day 10");
		helper.assertTrue(CobblemonPartners.partnerDays(one) == 1, "one day: " + CobblemonPartners.partnerDays(one));
		Pokemon three = make("geodude level=30");
		for (long d = 10; d < 13; d++) {
			CobblemonPartners.countDay(three, d);
			CobblemonPartners.countDay(three, d);
		}
		helper.assertTrue(CobblemonPartners.partnerDays(three) == 3, "three days counted as " + CobblemonPartners.partnerDays(three));
		List<String> why = new ArrayList<>();
		List<String> going = takes(helper, t, List.of(none, one, three), why);
		helper.assertTrue(going.equals(List.of("geodude")), "the Workers' Cup takes " + going);
		helper.assertTrue(why.equals(List.of("Gastly: it has helped a villager at work on 0 days, and this Cup asks for 3",
			"Machop: it has helped a villager at work on 1 day, and this Cup asks for 3")), "why: " + why);
		helper.succeed();
	}

	/** The Grand Cup: any Pokémon a village trainer may use, so legendary, mythical, Ultra Beast and paradox Pokémon stay home, each told why. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupThemesCompat")
	public void theGrandCupsBansSayWhy(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "grand_cup");
		List<Pokemon> party = List.of(make("mewtwo level=70"), make("mew level=70"), make("nihilego level=70"), make("greattusk level=70"), make("garchomp level=70"));
		List<String> why = new ArrayList<>();
		List<String> going = takes(helper, t, party, why);
		helper.assertTrue(going.equals(List.of("garchomp")), "the Grand Cup takes " + going);
		helper.assertTrue(why.size() == 4 && why.get(0).equals("Mewtwo: legendary Pokémon are banned") && why.get(1).equals("Mew: mythical Pokémon are banned")
			&& why.get(2).equals("Nihilego: Ultra Beast Pokémon are banned") && why.get(3).endsWith(": paradox Pokémon are banned"), "why: " + why);
		helper.succeed();
	}
}
