package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.openTrainer;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.trainer;

import io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.Fight;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * QA (2026-11-09, lane d): ROADMAP 43.1's Trainer screen, a failure that is the mod's, kept out of the suite until it is
 * fixed (not registered in fabric.mod.json). The screen shows "the trainer's team (the real Pokémon models)", but each
 * opening builds the team afresh with an unseeded gender (and shininess): the same trainer's Stufful is female on one
 * opening and male on the next, and the battle may field yet another.
 */
public class QaTrainerScreen1109dBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** Ten openings of the same trainer's screen show the same Pokémon, with the same look (species, gender, shininess). */
	//$ gametest_ticks_batch AREA '200' '"qa43bModels"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bModels")
	public void theTeamsModelsStayTheSameBetweenOpenings(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43bmodels");
		TrainerView first = openTrainer(helper, f.player(), f.trainer()).view();
		f.player().closeContainer();
		for (int n = 1; n < 10; n++) {
			TrainerView again = openTrainer(helper, f.player(), f.trainer()).view();
			f.player().closeContainer();
			for (int i = 0; i < first.team().size(); i++) {
				ItemStack a = first.team().get(i).icon();
				ItemStack b = again.team().get(i).icon();
				helper.assertTrue(ItemStack.isSameItemSameComponents(a, b), "opening " + (n + 1) + ", slot " + i + " shows another model: "
					+ a.getComponentsPatch() + " / " + b.getComponentsPatch());
			}
		}
		helper.succeed();
	}
}
