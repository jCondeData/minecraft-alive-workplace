package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-c-1116-1852) of B1326 from its spec, the side without CobbleDollars (MoneyGroupedCompatTests and
 * QaTreasuryHint1116cCompatTests have the side with them): the realm treasury's tooltip on the Realm tab, opened the way
 * a player opens it, tells how to give in emeralds, whatever the CobbleDollars rate is set to, and never mentions
 * CobbleDollars; and Money.grouped marks thousands at every boundary (999, 1,000, 999,999, 1,000,000, a long past an int).
 */
public class QaTreasuryHint1116cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Without CobbleDollars the treasury's give line is the emerald one, at the default rate and at a huge one. */
	//$ gametest_ticks_batch AREA '100' '"qa1116cTreasuryHintEmeralds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1116cTreasuryHintEmeralds")
	public void withoutCobbleDollarsTheTreasuryTellsHowToGiveEmeralds(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: CobbleDollars is here; this test is the side without it");
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "setup: the hamlet didn't join");
		int rate = Money.DOLLARS_PER_EMERALD;
		Leftovers.after(helper, () -> Money.DOLLARS_PER_EMERALD = rate);
		for (int r : new int[] {100, 1_000_000}) {
			Money.DOLLARS_PER_EMERALD = r;
			ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 1, land.owner());
			List<String> lore = RealmGameTests.lore(menu.icon(RealmTreasury.TREASURY));
			helper.assertTrue(lore.contains("Click with emeralds to give one; shift-click to give the stack"), "at " + r + " a CobbleDollar the give line: " + lore);
			helper.assertTrue(lore.stream().noneMatch(l -> l.contains("CobbleDollars")), "CobbleDollars on the tooltip without the mod: " + lore);
			helper.assertValueEqual(RealmTreasury.giveHint().getString(), "Click with emeralds to give one; shift-click to give the stack", "the hint at " + r);
		}
		helper.succeed();
	}

	/** Thousands marked at each boundary, never in the wrong place, and a sum past an int's size whole. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void moneyGroupsThousandsAtEveryBoundary(GameTestHelper helper) {
		long[] in = {0, 1, 999, 1_000, 9_999, 64_000, 999_999, 1_000_000, 64L * Integer.MAX_VALUE};
		String[] out = {"0", "1", "999", "1,000", "9,999", "64,000", "999,999", "1,000,000", "137,438,953,408"};
		for (int i = 0; i < in.length; i++) {
			helper.assertValueEqual(Money.grouped(in[i]), out[i], "grouped " + in[i]);
		}
		helper.succeed();
	}
}
