package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA (lane-c-1116-1852) of B1326 from its spec, with CobbleDollars: the realm treasury's hint marks thousands as every
 * other money line does, at each rate a server may set: under a thousand for the stack too (15: "960"), the stack just
 * past it (16: "1,024"), the single emerald at the edge (999 and 1,000), big rates and the largest one (no int overflow
 * in the stack's sum). The trainer's prize line on both sides of 1,000. MoneyGroupedCompatTests has the default rate.
 */
public class QaTreasuryHint1116cCompatTests implements FabricGameTest {
	static String hint(int rate) {
		int before = Money.DOLLARS_PER_EMERALD;
		try {
			Money.DOLLARS_PER_EMERALD = rate;
			return RealmTreasury.giveHint().getString();
		} finally {
			Money.DOLLARS_PER_EMERALD = before;
		}
	}

	static String expected(String one, String stack) {
		return "Click to give " + one + " CobbleDollars (shift-click: " + stack + "), or click with emeralds";
	}

	/** The hint at rates around each thousands boundary and at the largest rate. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theTreasuryHintMarksThousandsAtEveryRate(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		helper.assertValueEqual(hint(1), expected("1", "64"), "at 1 a CobbleDollar");
		helper.assertValueEqual(hint(15), expected("15", "960"), "at 15");
		helper.assertValueEqual(hint(16), expected("16", "1,024"), "at 16");
		helper.assertValueEqual(hint(999), expected("999", "63,936"), "at 999");
		helper.assertValueEqual(hint(1_000), expected("1,000", "64,000"), "at 1,000");
		helper.assertValueEqual(hint(250_000), expected("250,000", "16,000,000"), "at 250,000");
		helper.assertValueEqual(hint(Integer.MAX_VALUE), expected("2,147,483,647", "137,438,953,408"), "at the largest rate");
		helper.succeed();
	}

	/** A trainer's prize under, at and over a thousand. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aTrainersPrizeMarksThousandsAtTheEdge(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		helper.assertValueEqual(Trainers.prizeLine(Component.literal("Ash"), 999, 10).getString(), "You beat Ash! +999 CobbleDollars", "999");
		helper.assertValueEqual(Trainers.prizeLine(Component.literal("Ash"), 1000, 10).getString(), "You beat Ash! +1,000 CobbleDollars", "1,000");
		helper.assertValueEqual(Trainers.prizeLine(Component.literal("Ash"), 1_234_567, 10).getString(), "You beat Ash! +1,234,567 CobbleDollars", "a million");
		helper.succeed();
	}
}
