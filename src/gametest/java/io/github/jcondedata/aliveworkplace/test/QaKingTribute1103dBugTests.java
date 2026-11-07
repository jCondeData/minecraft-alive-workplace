package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.trade.TradePage;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane D, 2026-11-03), bug tests (fail until fixed) for the Bandit King's tribute (31.16: "he takes a third of the
 * treasury"): the line should say what the treasury lost, as the hall's treasury reads it ("Treasury: 2.99 emeralds"
 * becomes "Treasury: 2 emeralds"). With 2.99 emeralds the King takes 0.99 but the chronicle and chat say "took 0
 * emeralds"; with 5.99 he takes 1.99 and they say "1 emerald".
 */
public class QaKingTribute1103dBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static void check(GameTestHelper helper, int treasury) {
		QaKingTribute1103dGameTests.Tribute t = QaKingTribute1103dGameTests.tribute(helper, treasury, "qaKingBug" + treasury);
		int taken = treasury - t.treasuryAfter();
		String said = TradePage.money(taken).getString();
		helper.assertTrue(t.chronicle().contains("took " + said + " in tribute"), "the treasury lost " + said + " but the chronicle reads: "
			+ t.chronicle());
		helper.assertTrue(t.chat().contains(", " + said + " from the treasury"), "the treasury lost " + said + " but chat reads: " + t.chat());
		helper.succeed();
	}

	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeBug299_1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeBug299_1103d")
	public void qaATributeOfNinetyNineHundredthsDoesNotReadZero(GameTestHelper helper) {
		check(helper, 299);
	}

	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeBug599_1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeBug599_1103d")
	public void qaATributeOfOneNinetyNineReadsWhatWasTaken(GameTestHelper helper) {
		check(helper, 599);
	}
}
