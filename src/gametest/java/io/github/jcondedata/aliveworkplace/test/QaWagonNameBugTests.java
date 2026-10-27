package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.Founder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA lane E, 2026-10-27 (player-facing names): a Founder's Wagon names the Village Hall in its camp "New &lt;mother
 * village&gt;" (Founder.stockCamp, newName). A wagon that carries no village (one from the creative menu or a command,
 * or a Founder who lives in no village) names it with the stand-in {@code message.aliveworkplace.legend.the_wilds},
 * "the wilds", and the new village is called "New the wilds" for good: the hall keeps the name it's placed with.
 */
public class QaWagonNameBugTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAWagonWithoutAVillageNamesItsHallInEnglish(GameTestHelper helper) {
		net.minecraft.core.BlockPos camp = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
		String named = Founder.newName("Oakbrook", camp).getString();
		helper.assertTrue(named.equals("New Oakbrook"), "setup: a wagon from Oakbrook names its hall " + named);
		String unnamed = Founder.newName("", camp).getString();
		helper.assertTrue(!unnamed.matches("New [a-z].*") && !unnamed.contains("New the "),
			"a wagon from no village names its hall \"" + unnamed + "\"");
		String madeUp = io.github.jcondedata.aliveworkplace.hall.VillageHalls.madeUpName(camp).getString();
		helper.assertTrue(unnamed.equals(madeUp), "a wagon from no village names its hall \"" + unnamed + "\", not the made-up \"" + madeUp + "\"");
		helper.succeed();
	}
}
