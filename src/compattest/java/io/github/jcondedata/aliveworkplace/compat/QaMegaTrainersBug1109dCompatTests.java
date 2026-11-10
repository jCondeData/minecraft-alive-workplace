package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-09, lane d): fails on the real code (bug on branch tests/trainer-forms-1109, registered only here). A villager trainer's team should be the same in
 * every battle (CobblemonTrainers seeds it by the trainer's UUID), but a species with random forms (here a Master's
 * Wormadam) is created with a random cloak each time, and the form decides physical or special, so its held item (and
 * moves, and its type) change between calls. Not specific to 38.5's Mega Stone.
 */
public class QaMegaTrainersBug1109dCompatTests implements FabricGameTest {
	//$ gametest_batch 'CompatGameTests.AREA' '"qaMegaTrainersBug1109d"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaMegaTrainersBug1109d")
	public void aTrainersTeamIsTheSameEveryTime(GameTestHelper helper) {
		UUID id = UUID.fromString("00000000-1287-488b-0000-054066443ace"); // a Master whose team has a Wormadam
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 20; i++) {
			List<Pokemon> team = CobblemonTrainers.team(id, 5, false);
			seen.add(team.stream().map(p -> p.getSpecies().getName() + "/" + p.getForm().getName() + "@" + p.heldItem().getItem()).toList().toString());
		}
		helper.assertTrue(seen.size() == 1, "one trainer, " + seen.size() + " different teams: " + seen);
		helper.succeed();
	}
}
