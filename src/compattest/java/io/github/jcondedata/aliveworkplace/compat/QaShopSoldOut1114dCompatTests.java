package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lane D QA (lane-d-1114-0708), B1189's fix from both sides of its line: "when the lot now on the counter has less than
 * one lot in the shop's chests, confirmAsShown un-chooses the row and shows the sold-out line, taking nothing; covers a
 * price change onto an emptied lot too". So goods swapped to 4 Iron Ingot with exactly 4 in the chest still say "Click
 * Confirm again" and the second Confirm buys them; with 3 it is sold out and nothing is taken; and a price raised on a
 * lot whose chest was emptied meanwhile says sold out, never "The price changed".
 */
public class QaShopSoldOut1114dCompatTests implements FabricGameTest {
	private static final String SHOP = CompatGameTests.AREA;
	private static final int SLOT = Shops.FIRST_GOODS_SLOT;

	private static long paid(long before, ServerPlayer p) {
		return before - CobbleDollarsBank.balance(p);
	}

	/** A shop selling 16 Cobblestone for 2 emeralds (200), its chest holding 32 cobblestone and {@code iron} iron ingots. */
	private static Object[] setUp(GameTestHelper helper, int iron, List<String> seen) {
		RaidHelperCompatTests.clearAround(helper);
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		if (iron > 0) {
			QaJobShopLists1109cCompatTests.chest(helper).setItem(1, new ItemStack(Items.IRON_INGOT, iron));
		}
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 2000);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		return new Object[] {buyer, menu};
	}

	//$ gametest_ticks_batch SHOP '100' '"qaShopSoldOutExactlyOne1114d"'
	@GameTest(template = SHOP, timeoutTicks = 100, batch = "qaShopSoldOutExactlyOne1114d")
	public void swappedToGoodsWithExactlyOneLotInStockAsksToConfirmAgainAndSells(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		Object[] s = setUp(helper, 4, seen);
		ServerPlayer buyer = (ServerPlayer) s[0];
		ChoiceMenu menu = (ChoiceMenu) s[1];
		long before = CobbleDollarsBank.balance(buyer);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 0, "Confirm on the swapped lot took " + paid(before, buyer));
		helper.assertTrue(seen.contains("This counter now sells 4 Iron Ingot for 200 CobbleDollars. Click Confirm again to buy that."),
			"with exactly one lot (4 of 4) in the chest the buyer should be asked to Confirm again; read " + seen);
		helper.assertTrue(seen.stream().noneMatch(l -> l.endsWith("is sold out.")), "one lot in stock is not sold out; read " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 200 && buyer.getInventory().countItem(Items.IRON_INGOT) == 4,
			"second Confirm: paid " + paid(before, buyer) + ", iron " + buyer.getInventory().countItem(Items.IRON_INGOT) + "; read " + seen);
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.IRON_INGOT) == 0, "the chest still holds iron");
		helper.succeed();
	}

	//$ gametest_ticks_batch SHOP '100' '"qaShopSoldOutOneShort1114d"'
	@GameTest(template = SHOP, timeoutTicks = 100, batch = "qaShopSoldOutOneShort1114d")
	public void swappedToGoodsOneShortOfALotIsSoldOutAndTakesNothing(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		Object[] s = setUp(helper, 3, seen);
		ServerPlayer buyer = (ServerPlayer) s[0];
		ChoiceMenu menu = (ChoiceMenu) s[1];
		long before = CobbleDollarsBank.balance(buyer);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(seen.contains("Iron Ingot is sold out."), "3 of a 4-ingot lot in the chest: the buyer read " + seen);
		helper.assertTrue(seen.stream().noneMatch(l -> l.endsWith("Click Confirm again to buy that.")), "told to Confirm again: " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 0 && buyer.getInventory().countItem(Items.IRON_INGOT) == 0
			&& buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"a sold-out lot: paid " + paid(before, buyer) + ", iron " + buyer.getInventory().countItem(Items.IRON_INGOT));
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.IRON_INGOT) == 3, "the chest's 3 ingots moved");
		helper.succeed();
	}

	//$ gametest_ticks_batch SHOP '100' '"qaShopSoldOutPriceRaised1114d"'
	@GameTest(template = SHOP, timeoutTicks = 100, batch = "qaShopSoldOutPriceRaised1114d")
	public void aPriceRaisedOnALotWhoseChestWasEmptiedIsSoldOutNotAPriceChange(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		Object[] s = setUp(helper, 0, seen);
		ServerPlayer buyer = (ServerPlayer) s[0];
		ChoiceMenu menu = (ChoiceMenu) s[1];
		long before = CobbleDollarsBank.balance(buyer);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 15)); // one short of a lot
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 3));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(seen.contains("Cobblestone is sold out."), "price raised, 15 of 16 left: the buyer read " + seen);
		helper.assertTrue(seen.stream().noneMatch(l -> l.startsWith("The price changed")), "told the price changed for a sold-out lot: " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(paid(before, buyer) == 0 && buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"a sold-out lot at a new price: paid " + paid(before, buyer) + ", cobblestone " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.succeed();
	}
}
