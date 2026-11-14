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
 * QA bug test (lane d, 2026-11-11) for B1189 (fixed 2026-11-14): B1147's goods_changed sentence on a lot the buyer
 * can't buy. The owner swaps a chosen row's goods (16 Cobblestone for 200) to goods the shop's chests don't hold (4 Iron
 * Ingot, same 200). Confirm rightly takes nothing, but says "This counter now sells 4 Iron Ingot for 200 CobbleDollars.
 * Click Confirm again to buy that." while the redrawn list leaves the row out (a lot with no stock isn't listed), so
 * there is no Confirm to click and the second click on that slot does nothing at all, with no "sold out" line. A row
 * whose own stock ran out between Buy and Confirm says "Cobblestone is sold out." instead. Expected: the buyer isn't
 * told to Confirm again for a lot the screen no longer shows (e.g. "Iron Ingot is sold out.").
 */
public class QaHallGoneShopVerify1111dBugCompatTests implements FabricGameTest {
	private static final String SHOP = CompatGameTests.AREA;
	private static final int SLOT = Shops.FIRST_GOODS_SLOT;

	//$ gametest_ticks_batch SHOP '100' '"qaShopSwapNoStockBug1111d"'
	@GameTest(template = SHOP, timeoutTicks = 100, batch = "qaShopSwapNoStockBug1111d")
	public void goodsSwappedToSomethingOutOfStockDontAskForAConfirmThatIsntThere(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 2000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before, "setup: Confirm on the swapped lot took " + (before - CobbleDollarsBank.balance(buyer)));
		boolean rowShown = QaJobShopLists1109cCompatTests.rows(menu).get(SLOT) != null;
		boolean toldToConfirm = seen.stream().anyMatch(s -> s.endsWith("Click Confirm again to buy that."));
		helper.assertTrue(rowShown || !toldToConfirm, "the buyer was told to Confirm again for a lot the screen no longer shows (no stock): read " + seen
			+ ", row on screen " + rowShown);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(seen.contains("Iron Ingot is sold out."), "the second click says nothing about the lot; the buyer read: " + seen);
		helper.succeed();
	}
}
