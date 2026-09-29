package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonMegas;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Mega Showdown (the pack's Mega Evolution mod): Master trainers carry a Mega Stone and their AI Mega Evolves when the
 * battle allows it. A whole battle can't be played out on the game test server (battles stop before the first turn
 * without a real client); {@code SCENE=battle} in tools/screenshots plays one against a Master in the real client and
 * logs the Mega Evolution.
 */
public class MegaCompatTests implements FabricGameTest {
	/** Every Master's team has exactly one Pokémon holding its own Mega Stone; an Expert's has none. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void mastersCarryAMegaStone(GameTestHelper helper) {
		for (int i = 0; i < 8; i++) {
			UUID trainer = new UUID(0x5eed0000L + i * 7919L, 0xace0000L + i * 104729L);
			List<Pokemon> master = CobblemonTrainers.team(trainer, 5);
			long megas = master.stream().filter(CobblemonMegas::holdsItsStone).count();
			helper.assertTrue(megas == 1, "master team " + master.stream().map(p -> p.getSpecies().getName() + "@" + p.heldItem()).toList());
			List<Pokemon> expert = CobblemonTrainers.team(trainer, 4);
			helper.assertTrue(expert.stream().noneMatch(CobblemonMegas::holdsItsStone), "an expert with a mega stone");
			helper.assertTrue(CobblemonTrainers.team(trainer, 5).stream().map(p -> p.getSpecies().getName()).toList()
				.equals(master.stream().map(p -> p.getSpecies().getName()).toList()), "a master's team should stay the same");
		}
		helper.succeed();
	}

	/** The trainer AI adds the Mega Evolution to its move when the moveset allows it, and not otherwise. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aiMegaEvolvesWhenItCan(GameTestHelper helper) {
		ShowdownMoveset moveset = new ShowdownMoveset();
		MoveActionResponse move = new MoveActionResponse("aurasphere", null, null);
		helper.assertTrue(((MoveActionResponse) CobblemonMegas.withMega(move, moveset)).getGimmickID() == null, "mega without being able to");
		moveset.setCanMegaEvo(true);
		helper.assertTrue("mega".equals(((MoveActionResponse) CobblemonMegas.withMega(move, moveset)).getGimmickID()), "no mega when it can");
		helper.succeed();
	}
}
