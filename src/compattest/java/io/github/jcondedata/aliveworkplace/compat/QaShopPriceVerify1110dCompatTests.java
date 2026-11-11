package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-d, 2026-11-10) verifying the fix for B1107: the CobbleDollars shop's Confirm charges only the price (and lot)
 * its row showed. Written from the bug's spec ("Confirm charges the price shown, or refuses and re-shows the row at the
 * new price before taking anything") and its fix note (a raise or a cut takes nothing, says the new price and keeps the
 * row gold at it; a lot taken off the counter says it isn't for sale anymore), through the player's right-click and the
 * chest-click packets the list screen sends. The server's per-tick redraw is the menu's {@code broadcastChanges}, which
 * the server calls for an open screen every tick; the tests call it where a tick passes for that buyer only.
 * Every amount is checked exactly: the buyer's and owner's CobbleDollars, the goods in hand and in the chest.
 */
public class QaShopPriceVerify1110dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final int SLOT = Shops.FIRST_GOODS_SLOT;

	private static ItemStack cobble(int n) {
		return new ItemStack(Items.COBBLESTONE, n);
	}

	private static ItemStack emeralds(int n) {
		return new ItemStack(Items.EMERALD, n);
	}

	private static String priceChanged(int count, String goods, String price) {
		return "The price changed: " + count + " " + goods + " now costs " + price + ". Click Confirm again to buy at that price.";
	}

	/** B1147: the sentence when the lot itself changed (other goods or another lot size), whatever the price did. */
	private static String goodsChanged(int count, String goods, String price) {
		return "This counter now sells " + count + " " + goods + " for " + price + ". Click Confirm again to buy that.";
	}

	private static ChoiceList.Mark row(GameTestHelper helper, ChoiceMenu menu) {
		return QaJobShopLists1109cCompatTests.row(helper, menu, SLOT);
	}

	/** The server's tick for this buyer's open screen (what redraws it when the counter changes). */
	private static void tick(ServerPlayer player) {
		player.containerMenu.broadcastChanges();
	}

	/**
	 * Two buyers at one counter both press Buy at 200; the owner raises the price to 900. Buyer A's screen is redrawn
	 * (a server tick): her gold row reads 900 and her Confirm charges exactly 900. Buyer B's screen wasn't redrawn yet:
	 * his Confirm takes nothing and tells him the new price; his row is gold at 900 and only his second Confirm charges
	 * 900. The owner gets 1,800, the chest gives up exactly two lots.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopTwoBuyers1110d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopTwoBuyers1110d")
	public void twoBuyersEachPayWhatTheirOwnScreenShowed(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(48));
		List<String> seenA = new ArrayList<>();
		List<String> seenB = new ArrayList<>();
		ServerPlayer a = TutorListMenuCompatTests.player(helper, seenA);
		ServerPlayer b = TutorListMenuCompatTests.player(helper, seenB);
		CobbleDollarsBank.add(a, 2000);
		CobbleDollarsBank.add(b, 2000);
		long aBefore = CobbleDollarsBank.balance(a);
		long bBefore = CobbleDollarsBank.balance(b);
		long ownerBefore = CobbleDollarsBank.balance(owner);
		ChoiceMenu menuA = QaJobShopLists1109cCompatTests.open(helper, a, keeper, false);
		ChoiceMenu menuB = QaJobShopLists1109cCompatTests.open(helper, b, keeper, false);
		helper.assertTrue(menuA != menuB, "both buyers got the same screen");
		QaJobShopLists1109cCompatTests.click(a, menuA.containerId, SLOT);
		QaJobShopLists1109cCompatTests.click(b, menuB.containerId, SLOT);
		helper.assertTrue(row(helper, menuA).on() && row(helper, menuB).on(), "Buy didn't turn both rows gold");
		helper.assertTrue(row(helper, menuB).price().getString().equals("200 CobbleDollars"), "B's row: " + row(helper, menuB).price().getString());

		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(9));
		tick(a); // A's screen redraws; B's hasn't yet
		helper.assertTrue(row(helper, menuA).on() && row(helper, menuA).price().getString().equals("900 CobbleDollars"),
			"A's redrawn row should be gold at 900: " + row(helper, menuA).price().getString() + " gold=" + row(helper, menuA).on());
		helper.assertTrue(row(helper, menuB).price().getString().equals("200 CobbleDollars"), "B's screen changed without a tick");

		QaJobShopLists1109cCompatTests.click(a, menuA.containerId, SLOT);
		helper.assertTrue(aBefore - CobbleDollarsBank.balance(a) == 900, "A's screen showed 900 but she paid " + (aBefore - CobbleDollarsBank.balance(a)));
		helper.assertTrue(a.getInventory().countItem(Items.COBBLESTONE) == 16, "A got " + a.getInventory().countItem(Items.COBBLESTONE) + " cobblestone");

		QaJobShopLists1109cCompatTests.click(b, menuB.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(b) == bBefore, "B's screen showed 200 but Confirm took " + (bBefore - CobbleDollarsBank.balance(b)));
		helper.assertTrue(b.getInventory().countItem(Items.COBBLESTONE) == 0, "B got goods on a refused Confirm");
		String told = priceChanged(16, "Cobblestone", "900 CobbleDollars");
		helper.assertTrue(seenB.contains(told), "B read: " + seenB);
		helper.assertTrue(!seenA.contains(told), "A, whose screen showed 900, was told the price changed: " + seenA);
		helper.assertTrue(row(helper, menuB).on() && row(helper, menuB).price().getString().equals("900 CobbleDollars"),
			"B's row after the refusal: " + row(helper, menuB).price().getString() + " gold=" + row(helper, menuB).on());

		QaJobShopLists1109cCompatTests.click(b, menuB.containerId, SLOT);
		helper.assertTrue(bBefore - CobbleDollarsBank.balance(b) == 900, "B's second Confirm at 900 charged " + (bBefore - CobbleDollarsBank.balance(b)));
		helper.assertTrue(b.getInventory().countItem(Items.COBBLESTONE) == 16, "B got " + b.getInventory().countItem(Items.COBBLESTONE));
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 16,
			"the chest should hold 16, holds " + QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE));
		helper.assertTrue(CobbleDollarsBank.balance(owner) - ownerBefore == 1800, "the owner got " + (CobbleDollarsBank.balance(owner) - ownerBefore));
		helper.succeed();
	}

	/**
	 * The owner raises the price and puts it back before the buyer confirms, with the buyer's screen never redrawn: the
	 * row showed 200 all along and the counter asks 200 again, so Confirm buys at once for 200. With the screen redrawn
	 * at 900 in between, putting the price back is a change from what's on screen: Confirm takes nothing, says 200, and
	 * the next Confirm charges 200. Never anything but the price on screen.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopPriceBack1110d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopPriceBack1110d")
	public void priceRaisedThenPutBackChargesTheShownPrice(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(64));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 2000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(9));
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(before - CobbleDollarsBank.balance(buyer) == 200, "shown 200, price back at 200, charged " + (before - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 16, "got " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("The price changed")), "told of a change the screen never showed: " + seen);

		// now with the screen redrawn at 900 in between
		long mid = CobbleDollarsBank.balance(buyer);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(9));
		tick(buyer);
		helper.assertTrue(row(helper, menu).on() && row(helper, menu).price().getString().equals("900 CobbleDollars"), "redrawn row: " + row(helper, menu).price().getString());
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == mid, "the screen showed 900, the counter asks 200: Confirm took " + (mid - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(seen.contains(priceChanged(16, "Cobblestone", "200 CobbleDollars")), "the buyer read: " + seen);
		helper.assertTrue(row(helper, menu).on() && row(helper, menu).price().getString().equals("200 CobbleDollars"), "row: " + row(helper, menu).price().getString());
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(mid - CobbleDollarsBank.balance(buyer) == 200, "second Confirm at 200 charged " + (mid - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 32, "got " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.succeed();
	}

	/**
	 * The owner shrinks the lot (16 to 8 cobblestone at the same 200) between Buy and Confirm: the row showed 16, so Confirm
	 * takes nothing and names the new lot; the next Confirm hands over exactly 8 for 200. Then partial stock: two buyers
	 * choose the last whole lot (20 in the chest, lots of 16 after the owner puts it back); the first buys it, the second's
	 * Confirm says it's sold out and takes nothing; the 4 left stay in the chest.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopLotShrunk1110d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopLotShrunk1110d")
	public void smallerLotOrPartialStockNeverOverchargesOrGivesFreeGoods(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(28));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 1000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(8), emeralds(2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before, "the row showed 16 for 200; Confirm on the 8-lot took " + (before - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "goods handed over on a refused Confirm");
		helper.assertTrue(seen.contains(goodsChanged(8, "Cobblestone", "200 CobbleDollars")), "the buyer read: " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("The price changed")), "a smaller lot at the same price said the price changed: " + seen);
		helper.assertTrue(row(helper, menu).on(), "the row should stay gold at the new lot");
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(before - CobbleDollarsBank.balance(buyer) == 200, "the 8-lot cost " + (before - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 8, "got " + buyer.getInventory().countItem(Items.COBBLESTONE) + ", not the 8 shown");
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 20, "chest: " + QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE));

		// partial stock: 20 left, lots of 16 again; two buyers want the one whole lot
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		List<String> seen2 = new ArrayList<>();
		ServerPlayer other = TutorListMenuCompatTests.player(helper, seen2);
		CobbleDollarsBank.add(other, 1000);
		long otherBefore = CobbleDollarsBank.balance(other);
		long buyerMid = CobbleDollarsBank.balance(buyer);
		tick(buyer);
		ChoiceMenu menu2 = QaJobShopLists1109cCompatTests.open(helper, other, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.click(other, menu2.containerId, SLOT);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(buyerMid - CobbleDollarsBank.balance(buyer) == 200 && buyer.getInventory().countItem(Items.COBBLESTONE) == 24,
			"the first buyer: paid " + (buyerMid - CobbleDollarsBank.balance(buyer)) + ", has " + buyer.getInventory().countItem(Items.COBBLESTONE));
		QaJobShopLists1109cCompatTests.click(other, menu2.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(other) == otherBefore, "only 4 left, yet the second buyer paid " + (otherBefore - CobbleDollarsBank.balance(other)));
		helper.assertTrue(other.getInventory().countItem(Items.COBBLESTONE) == 0, "the second buyer got " + other.getInventory().countItem(Items.COBBLESTONE) + " free");
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 4, "chest: " + QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE));
		helper.assertTrue(seen2.contains("Cobblestone is sold out."), "the second buyer read: " + seen2);
		helper.succeed();
	}

	/**
	 * A buyer with 300 chooses a lot at 200; the owner raises it to 900. Confirm takes nothing and says 900; the row is
	 * gold at 900 in red (she can't pay it); a second Confirm says "That costs 900 CobbleDollars." and still takes
	 * nothing: her 300 and the chest are untouched.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopTooPoor1110d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopTooPoor1110d")
	public void raisedPriceTheBuyerCantPayTakesNothing(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(32));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 300);
		long before = CobbleDollarsBank.balance(buyer);
		long ownerBefore = CobbleDollarsBank.balance(owner);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(9));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before, "Confirm after the raise took " + (before - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(seen.contains(priceChanged(16, "Cobblestone", "900 CobbleDollars")), "the buyer read: " + seen);
		ChoiceList.Mark mark = row(helper, menu);
		helper.assertTrue(mark.on() && mark.price().getString().equals("900 CobbleDollars") && QaJobShopLists1109cCompatTests.colored(mark.price(), ChatFormatting.RED),
			"the row should be gold at a red 900: " + mark.price().getString() + " gold=" + mark.on() + " style=" + mark.price().getStyle());
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before, "a buyer with 300 lost " + (before - CobbleDollarsBank.balance(buyer)) + " on a 900 lot");
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "she got goods she couldn't pay for");
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 32, "the chest lost stock");
		helper.assertTrue(CobbleDollarsBank.balance(owner) == ownerBefore, "the owner was paid for nothing");
		helper.assertTrue(seen.contains("That costs 900 CobbleDollars."), "the buyer read: " + seen);
		helper.succeed();
	}

	/**
	 * Confirm clicked twice in a row (two packets, same tick): one lot, one charge. The second click only chooses the row
	 * again (gold, Confirm), it never buys a second lot.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopDoubleConfirm1110d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopDoubleConfirm1110d")
	public void confirmClickedTwiceChargesOnce(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(64));
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 1000);
		long before = CobbleDollarsBank.balance(buyer);
		long ownerBefore = CobbleDollarsBank.balance(owner);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(before - CobbleDollarsBank.balance(buyer) == 200, "Confirm twice charged " + (before - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 16, "got " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 48, "chest: " + QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE));
		helper.assertTrue(CobbleDollarsBank.balance(owner) - ownerBefore == 200, "owner got " + (CobbleDollarsBank.balance(owner) - ownerBefore));
		helper.assertTrue(row(helper, menu).on(), "the extra click should only choose the row again");
		helper.succeed();
	}

	/**
	 * Buy at 200, then the buyer closes the screen; the owner raises the price to 900. A click still carrying the old
	 * screen's id buys nothing. Reopened, the row reads 900 and isn't chosen (nothing carried over); Buy then Confirm
	 * charges the 900 shown.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopReopen1110d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopReopen1110d")
	public void closingAndReopeningShowsTheNewPriceFresh(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(32));
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(buyer, 1000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		int oldId = menu.containerId;
		QaJobShopLists1109cCompatTests.click(buyer, oldId, SLOT);
		buyer.connection.handleContainerClose(new ServerboundContainerClosePacket(oldId));
		helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "the screen didn't close");
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(9));
		QaJobShopLists1109cCompatTests.click(buyer, oldId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before, "a click on the closed screen took " + (before - CobbleDollarsBank.balance(buyer)));
		ChoiceMenu again = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		ChoiceList.Mark mark = row(helper, again);
		helper.assertTrue(!mark.on() && mark.price().getString().equals("900 CobbleDollars"),
			"reopened row: " + mark.price().getString() + " gold=" + mark.on());
		QaJobShopLists1109cCompatTests.click(buyer, again.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before, "the first click on the reopened screen bought");
		QaJobShopLists1109cCompatTests.click(buyer, again.containerId, SLOT);
		helper.assertTrue(before - CobbleDollarsBank.balance(buyer) == 900, "Buy+Confirm at 900 charged " + (before - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(buyer.getInventory().countItem(Items.COBBLESTONE) == 16, "got " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.succeed();
	}

	/**
	 * The lot in a chosen row replaced by different goods at the same price (cobblestone to a diamond, 200 both): Confirm
	 * takes nothing, says "This counter now sells 1 Diamond for 200 CobbleDollars." (B1147: not "The price changed"), and the next Confirm hands over the diamond, not cobblestone. An item-priced lot
	 * (2 diamonds) raised to 5: Confirm takes no diamonds. A lot taken off the counter: "Cobblestone isn't for sale
	 * anymore.", the row is gone and nothing moves.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopLotSwapped1110d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopLotSwapped1110d")
	public void swappedItemPricedAndWithdrawnLots(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, cobble(32));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(1, new ItemStack(Items.DIAMOND, 3));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(2, new ItemStack(Items.IRON_INGOT, 16));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 1000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.DIAMOND, 1), emeralds(2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before && buyer.getInventory().countItem(Items.DIAMOND) == 0
			&& buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "Confirm on a swapped lot bought something");
		helper.assertTrue(seen.contains(goodsChanged(1, "Diamond", "200 CobbleDollars")), "the buyer read: " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("The price changed")), "only the goods changed, but the buyer read: " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(before - CobbleDollarsBank.balance(buyer) == 200 && buyer.getInventory().countItem(Items.DIAMOND) == 1
			&& buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "second Confirm: paid " + (before - CobbleDollarsBank.balance(buyer))
			+ ", diamonds " + buyer.getInventory().countItem(Items.DIAMOND) + ", cobblestone " + buyer.getInventory().countItem(Items.COBBLESTONE));

		// an item price raised: column 1 sells 8 iron for 2 diamonds, then 5
		buyer.getInventory().add(new ItemStack(Items.DIAMOND, 9)); // now 10
		QaJobShopLists1109cCompatTests.lot(helper, 1, new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.DIAMOND, 2));
		tick(buyer);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT + 1);
		QaJobShopLists1109cCompatTests.lot(helper, 1, new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.DIAMOND, 5));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT + 1);
		helper.assertTrue(buyer.getInventory().countItem(Items.DIAMOND) == 10 && buyer.getInventory().countItem(Items.IRON_INGOT) == 0,
			"Confirm after an item-price raise: diamonds " + buyer.getInventory().countItem(Items.DIAMOND) + ", iron " + buyer.getInventory().countItem(Items.IRON_INGOT));
		helper.assertTrue(seen.stream().anyMatch(line -> line.startsWith("The price changed: 8 Iron Ingot now costs "))
			&& seen.stream().noneMatch(line -> line.startsWith("This counter now sells 8 Iron Ingot")),
			"only the price changed (same 8 iron), but the buyer read: " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT + 1);
		helper.assertTrue(buyer.getInventory().countItem(Items.DIAMOND) == 5 && buyer.getInventory().countItem(Items.IRON_INGOT) == 8,
			"second Confirm at 5 diamonds: diamonds " + buyer.getInventory().countItem(Items.DIAMOND) + ", iron " + buyer.getInventory().countItem(Items.IRON_INGOT));

		// withdrawn: column 0 back to cobblestone, chosen, then taken off the counter
		QaJobShopLists1109cCompatTests.lot(helper, 0, cobble(16), emeralds(2));
		tick(buyer);
		long mid = CobbleDollarsBank.balance(buyer);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, ItemStack.EMPTY, ItemStack.EMPTY);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == mid && buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "a withdrawn lot sold");
		helper.assertTrue(seen.contains("Cobblestone isn't for sale anymore."), "the buyer read: " + seen);
		helper.assertTrue(QaJobShopLists1109cCompatTests.rows(menu).get(SLOT) == null, "the withdrawn lot's row is still on screen");
		helper.assertTrue(QaJobShopLists1109cCompatTests.chest(helper).countItem(Items.COBBLESTONE) == 32, "the chest lost cobblestone");
		helper.succeed();
	}

	/** B1107's two new sentences and B1147's goods-changed one exist, in American English, with their arguments in order. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void priceChangedAndWithdrawnWordsReadRight(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String changed = "message.aliveworkplace.shop.price_changed";
		String gone = "message.aliveworkplace.shop.withdrawn";
		helper.assertTrue(lang.has(changed) && lang.has(gone), "missing keys");
		helper.assertTrue(lang.getOrDefault(changed).equals("The price changed: %s %s now costs %s. Click Confirm again to buy at that price."),
			"price_changed reads: " + lang.getOrDefault(changed));
		helper.assertTrue(lang.getOrDefault(gone).equals("%s isn't for sale anymore."), "withdrawn reads: " + lang.getOrDefault(gone));
		String swapped = "message.aliveworkplace.shop.goods_changed";
		helper.assertTrue(lang.has(swapped), "missing key " + swapped);
		helper.assertTrue(net.minecraft.network.chat.Component.translatable(swapped, 1, new ItemStack(Items.DIAMOND).getHoverName(),
			io.github.jcondedata.aliveworkplace.work.Money.describe(200, 0)).getString()
			.equals("This counter now sells 1 Diamond for 200 CobbleDollars. Click Confirm again to buy that."),
			"goods_changed rendered: " + lang.getOrDefault(swapped));
		helper.assertTrue(net.minecraft.network.chat.Component.translatable(changed, 16, new ItemStack(Items.COBBLESTONE).getHoverName(),
			io.github.jcondedata.aliveworkplace.work.Money.describe(1250, 0)).getString()
			.equals("The price changed: 16 Cobblestone now costs 1,250 CobbleDollars. Click Confirm again to buy at that price."), "rendered wrong");
		helper.succeed();
	}
}
