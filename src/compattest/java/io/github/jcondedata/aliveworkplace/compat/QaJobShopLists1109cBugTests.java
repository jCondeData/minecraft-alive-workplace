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
 * QA (lane-c, 2026-11-09) bug tests for 42.4's CobbleDollars Shop list, registered with the fix for B1107: Confirm buys
 * only at the price its row showed.
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

	/** B1107: a lowered price is not charged silently either: Confirm takes nothing, the gold row shows the new price, and the next Confirm buys at it. */
	//$ gametest_batch AREA '"shopPriceLowered1107"'
	@GameTest(template = AREA, batch = "shopPriceLowered1107")
	public void shopConfirmAfterAPriceCutReShowsTheRow(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 1000);
		ChoiceMenu menu = Shops.menuForTest(buyer, keeper);
		int slot = Shops.FIRST_GOODS_SLOT;
		menu.press(slot, buyer);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1));
		menu.press(slot, buyer);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 1000, "Confirm on the old price took " + (1000 - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "Confirm on the old price handed over the goods");
		var mark = QaJobShopLists1109cCompatTests.row(helper, menu, slot);
		helper.assertTrue(mark.on() && mark.price().getString().equals("100 CobbleDollars"),
			"the row should be gold at the new price, shows " + mark.price().getString() + " gold=" + mark.on());
		menu.press(slot, buyer);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 900, "the second Confirm should charge the 100 shown, buyer has " + CobbleDollarsBank.balance(buyer));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 16, "the second Confirm didn't hand over the goods");
		helper.succeed();
	}

	/** B1107: a raised price on a screen that wasn't redrawn: Confirm takes nothing; the row is redrawn gold at the new price. */
	//$ gametest_batch AREA '"shopPriceRaised1107"'
	@GameTest(template = AREA, batch = "shopPriceRaised1107")
	public void shopConfirmAfterAPriceRiseTakesNothing(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 1000);
		ChoiceMenu menu = Shops.menuForTest(buyer, keeper);
		int slot = Shops.FIRST_GOODS_SLOT;
		menu.press(slot, buyer);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 9));
		menu.press(slot, buyer);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 1000, "Confirm on the old price took " + (1000 - CobbleDollarsBank.balance(buyer)));
		var mark = QaJobShopLists1109cCompatTests.row(helper, menu, slot);
		helper.assertTrue(mark.on() && mark.price().getString().equals("900 CobbleDollars"), "the row shows " + mark.price().getString() + " gold=" + mark.on());
		helper.succeed();
	}

	/** B1107: the lot taken off the counter, or its stock gone, between Buy and Confirm: nothing is taken and no goods appear. */
	//$ gametest_batch AREA '"shopGoodsGone1107"'
	@GameTest(template = AREA, batch = "shopGoodsGone1107")
	public void shopConfirmWithTheGoodsGoneTakesNothing(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 1000);
		int slot = Shops.FIRST_GOODS_SLOT;
		// the stock emptied out of the chest
		ChoiceMenu menu = Shops.menuForTest(buyer, keeper);
		menu.press(slot, buyer);
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, ItemStack.EMPTY);
		menu.press(slot, buyer);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 1000, "a sold-out Confirm took " + (1000 - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "a sold-out Confirm handed over goods");
		// the lot taken off the counter
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		menu = Shops.menuForTest(buyer, keeper);
		menu.press(slot, buyer);
		QaJobShopLists1109cCompatTests.lot(helper, 0, ItemStack.EMPTY, ItemStack.EMPTY);
		menu.press(slot, buyer);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == 1000, "Confirm on a withdrawn lot took " + (1000 - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "Confirm on a withdrawn lot handed over goods");
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 32, "the chest lost stock");
		helper.succeed();
	}
}
