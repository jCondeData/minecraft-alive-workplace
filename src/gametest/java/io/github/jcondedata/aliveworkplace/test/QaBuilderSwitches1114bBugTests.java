package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA bug tests (lane B, 2026-11-14) from {@code docs/wiki/config-switches.md}; each fails on main until its bug is fixed.
 */
public class QaBuilderSwitches1114bBugTests implements FabricGameTest {

	/**
	 * Every game rule in the guide's table has its words on the Edit Game Rules screen ({@code gamerule.<name>} in
	 * en_us.json, as the guide says of workplaceBuildDelay). workplaceVillageFarms has none, so the screen shows the raw key
	 * "gamerule.workplaceVillageFarms".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyGameRuleInTheGuideHasItsWordsOnTheGameRulesScreen(GameTestHelper helper) {
		JsonObject lang = QaBuilderSwitches1114bGameTests.lang();
		List<String> missing = new ArrayList<>();
		for (String name : new String[] {"workplaceFreeMaterials", "workplaceBuildDelay", "workplaceAllowUploads", "workplaceFoundationDepth",
			"workplaceBuildersHelp", "workplaceBuilderOwnership", "workplaceKeepWorkLoaded", "workplaceLevelGround", "workplaceVillageFarms",
			"workplaceRetraining"}) {
			if (!lang.has("gamerule." + name)) {
				missing.add("gamerule." + name);
			}
		}
		helper.assertTrue(missing.isEmpty(), "the Edit Game Rules screen shows raw keys for: " + missing);
		helper.succeed();
	}
}
