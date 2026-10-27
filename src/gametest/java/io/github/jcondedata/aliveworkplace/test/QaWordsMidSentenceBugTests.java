package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA lane E, 2026-10-27 (player-facing sentences, B497's kind): a stand-in name written for the start of a sentence
 * ("A builder") put in the middle of one, and a possessive put after a plural stand-in.
 */
public class QaWordsMidSentenceBugTests implements FabricGameTest {
	/**
	 * The hall's Repairs page, a repair whose builder isn't loaded: "Being repaired by A builder: 40%" (RepairsPage passes
	 * {@code screen.aliveworkplace.piece_looks.a_builder}, "A builder", after "by"). Expected "by a builder".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaARepairByAnUnloadedBuilderReadsLowerCase(GameTestHelper helper) {
		String text = Component.translatable("screen.aliveworkplace.repairs.repairing",
			Component.translatable("screen.aliveworkplace.piece_looks.a_builder"), 40).getString();
		helper.assertTrue(!text.contains(" A builder"), "the Repairs page reads \"" + text + "\", a capital A mid-sentence");
		helper.succeed();
	}

	/**
	 * A raid den won with the helpers gone (none of them found when the win is counted): the chronicle reads "Ash and the
	 * village's trainers's band beat the tier 3 Fire raid den" (RaidHelpers.won passes {@code raid_helpers.the_band},
	 * "the village's trainers", into "%2$s's band"). Expected plain English ("Ash and the village's trainers beat...").
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaARaidWonWithoutHelpersHasNoDoublePossessive(GameTestHelper helper) {
		String text = Component.translatable("chronicle.aliveworkplace.raid_helpers.won", "Ash",
			Component.translatable("message.aliveworkplace.raid_helpers.the_band"), 3, "Fire").getString();
		helper.assertTrue(!text.contains("s's "), "the chronicle reads \"" + text + "\"");
		helper.succeed();
	}
}
