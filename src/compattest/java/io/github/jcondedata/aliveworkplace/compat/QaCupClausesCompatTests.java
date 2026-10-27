package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCupMatches;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * QA for ROADMAP 28.22: the Grand Cup's Species and Item Clause (the spec: "Species and Item Clause"), on the teams that
 * go. A delegate's team has six different species, each holding a different item (or none); a player's two of one
 * species: only the first goes.
 */
public class QaCupClausesCompatTests implements FabricGameTest {
	private static CupThemes.Theme grand(GameTestHelper helper) {
		CupThemes.Theme theme = CupThemes.get(AliveWorkplace.id("grand_cup"));
		helper.assertTrue(theme != null && theme.rules().contains("Species Clause") && theme.rules().contains("Item Clause"), "the Grand Cup's clauses: " + theme);
		return theme;
	}

	/** Leaders and host trainers of every tier: six species, never one twice; held items, never one twice (Item Clause). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupClausesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupClausesCompat")
	public void aDelegatesGrandCupTeamKeepsBothClauses(GameTestHelper helper) {
		CupThemes.Theme t = grand(helper);
		List<String> broken = new ArrayList<>();
		for (String who : List.of("mira", "dara", "oren", "ren", "pip", "wren")) {
			for (int tier = 1; tier <= 5; tier++) {
				List<Pokemon> team = CobblemonTrainers.team(UUID.nameUUIDFromBytes(who.getBytes()), tier, t);
				Set<String> species = new HashSet<>();
				Set<String> items = new HashSet<>();
				for (Pokemon p : team) {
					if (!species.add(p.getSpecies().getName())) {
						broken.add(who + " (tier " + tier + ") brings two " + p.getSpecies().getName());
					}
					if (!p.heldItem().isEmpty() && !items.add(BuiltInRegistries.ITEM.getKey(p.heldItem().getItem()).toString())) {
						broken.add(who + " (tier " + tier + ")'s " + p.getSpecies().getName() + " holds a second " + BuiltInRegistries.ITEM.getKey(p.heldItem().getItem()));
					}
				}
			}
		}
		helper.assertTrue(broken.isEmpty(), "the Grand Cup's clauses broken: " + broken);
		helper.succeed();
	}

	/** A player with two Gastly in the party: the first goes, the second stays home (Species Clause). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupClausesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupClausesCompat")
	public void aPlayersSecondOfOneSpeciesStaysHome(GameTestHelper helper) {
		CupThemes.Theme t = grand(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String s : List.of("gastly", "machop", "gastly")) {
			party.add(PokemonProperties.Companion.parse(s + " level=40", " ", "=").create());
		}
		List<Component> leftOut = new ArrayList<>();
		List<String> going = CobblemonCupMatches.eligible(party, t, leftOut).stream().map(p -> p.getSpecies().getName()).toList();
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.assertTrue(going.equals(List.of("Gastly", "Machop")) && leftOut.size() == 1,
			"Species Clause: going " + going + ", left out " + leftOut.stream().map(Component::getString).toList());
		helper.succeed();
	}
}
