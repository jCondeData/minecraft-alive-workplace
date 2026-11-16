package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * B1326: with CobbleDollars every sum a player reads has its thousands marked ("6,400", as B466 made the money lines):
 * the realm treasury's hint said "shift-click: 6400" and a trainer's prize "+2500 CobbleDollars".
 */
public class MoneyGroupedCompatTests implements FabricGameTest {
	/** The realm treasury's hint, at the default 100 CobbleDollars an emerald: a stack's worth reads "6,400". */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTreasuryHintMarksTheThousands(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		int before = Money.DOLLARS_PER_EMERALD;
		String hint;
		try {
			Money.DOLLARS_PER_EMERALD = 100;
			hint = RealmTreasury.giveHint().getString();
		} finally {
			Money.DOLLARS_PER_EMERALD = before;
		}
		helper.assertValueEqual(hint, "Click to give 100 CobbleDollars (shift-click: 6,400), or click with emeralds", "the treasury's hint");
		helper.succeed();
	}

	/** A Gym Leader's top prize (2,500 CobbleDollars) reads with its comma, as the other money lines. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aTrainersPrizeMarksTheThousands(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		helper.assertValueEqual(Trainers.prizeLine(Component.literal("Ash"), 2500, 15).getString(), "You beat Ash! +2,500 CobbleDollars", "the prize line");
		helper.assertValueEqual(Trainers.prizeLine(Component.literal("Ash"), 100, 1).getString(), "You beat Ash! +100 CobbleDollars", "a small prize");
		helper.succeed();
	}
}
