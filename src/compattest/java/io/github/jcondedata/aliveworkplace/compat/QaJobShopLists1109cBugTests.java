package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c, 2026-11-09) bug tests for 42.4's CobbleDollars Shop list. NOT registered: each fails on main until the mod is
 * fixed; the builder registers it with the fix.
 */
public class QaJobShopLists1109cBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * Confirm should buy at the price the gold row shows. The buyer presses Buy on cobblestone at 200 CobbleDollars (the
	 * row turns gold, Confirm); the owner then raises the counter's price to 9 emeralds (900). Nothing on the buyer's screen
	 * changes (it only re-renders when their money changes), so Confirm still reads 200, yet it charges 900.
	 * Expected: charged 200, or refused / the row re-shown at the new price before anything is taken.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopPriceRaised1109c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopPriceRaised1109c")
	public void shopConfirmChargesThePriceTheRowShowed(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 1000);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		int slot = Shops.FIRST_GOODS_SLOT;
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, slot);
		helper.runAfterDelay(2, () -> {
			QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 9));
			helper.runAfterDelay(2, () -> {
				var mark = QaJobShopLists1109cCompatTests.row(helper, menu, slot);
				String shown = mark.price().getString();
				boolean stillGold = mark.on();
				QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, slot);
				long paid = 1000 - CobbleDollarsBank.balance(buyer);
				helper.assertTrue(!(stillGold && shown.equals("200 CobbleDollars") && paid == 900),
					"the gold Confirm row showed " + shown + " but Confirm charged " + paid);
				helper.succeed();
			});
		});
	}
}
