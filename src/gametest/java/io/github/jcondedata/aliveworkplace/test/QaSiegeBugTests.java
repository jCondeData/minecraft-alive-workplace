package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane D, 2026-10-24): failing tests for siege bugs found while testing 32.4 and 32.6. Not registered in
 * fabric.mod.json: the builder registers each with its fix.
 */
public class QaSiegeBugTests implements FabricGameTest {
	/**
	 * The morning's siege report counts one raider as "1 raiders came": the chronicle line reads
	 * "The siege: 1 raiders came and 0 fell, ..." where a player expects "1 raider came" (the Defense page already
	 * counts its raiders with {@code Words.counted}).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSiegeBugOne"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSiegeBugOne")
	public void aSiegeOfOneRaiderSaysOneRaider(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(new BlockPos(0, 2, 0));
		ThreatData.Siege siege = new ThreatData.Siege(hall, helper.getLevel().getGameTime(), 23500);
		siege.came = 1;
		String report = SiegeReport.text(helper.getLevel(), siege).getString();
		helper.assertTrue(report.startsWith("The siege: 1 raider came and 0 fell"), "the report: " + report);
		helper.succeed();
	}
}
