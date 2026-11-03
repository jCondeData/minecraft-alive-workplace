package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmEdicts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-03), written from the spec of ROADMAP 33.22a (docs/wiki/realms.md, the Common Market): the realm's own
 * people (the owner of any member village, that owner's friends, operators) buy from a member's board at the board's
 * price, strangers pay 10 percent more; the board charges each buyer their own price, the Prices tab's first line tells
 * each viewer theirs, and "You can't pay ..." names that buyer's price. What the earlier QA leaves out: the owner of a
 * second member and that owner's friend buying through the board's own screen at a village they don't own, side by side
 * with a stranger at the same board and price; an operator who owns nothing (and the same player once deopped); the
 * owner of a village outside the realm; the spread back at the board, the tab and the refusal once the edict is lifted.
 */
public class QaCommonMarket1103cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static final String PLAIN = "What the village pays for a bundle of each good, and what it sells one for (10% more). "
		+ "Prices move a third of the way toward supply and demand each dawn";
	private static final String REALM = "What the village pays for a bundle of each good. Under the Common Market the realm's people buy at that price too; "
		+ "strangers pay 10% more. Prices move a third of the way toward supply and demand each dawn";

	/** A realm of {@code members} members (hall 0 the capital), {@code outside} more halls outside it; a Storehouse and chest of logs by hall 1. */
	private static Land land(GameTestHelper helper, int members, int outside) {
		VillageRanks.Rank[] ranks = new VillageRanks.Rank[1 + members + outside];
		ranks[0] = VillageRanks.Rank.TOWN;
		for (int i = 1; i < ranks.length; i++) {
			ranks[i] = VillageRanks.Rank.HAMLET;
		}
		Land land = RealmEdictGameTests.realm(helper, members, ranks);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		helper.setBlock(new BlockPos(12, 2, 6), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(12, 2, 8), Blocks.CHEST);
		return land;
	}

	/** Every hall's timber at {@code cents} (so the members' average is {@code cents} too) and the chest refilled. */
	private static void prices(GameTestHelper helper, Land land, int cents) {
		Container chest = helper.getBlockEntity(new BlockPos(12, 2, 8));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		for (BlockPos hall : land.halls()) {
			BoardTradeGameTests.price(helper, hall, BoardTradeGameTests.TIMBER, cents);
		}
	}

	/** The board of hall 1 as {@code player} sees it: the owner through the hall's screen, anyone else the stranger's page. */
	private static ChoiceMenu board(Land land, ServerPlayer player) {
		if (player == land.owner()) {
			ChoiceMenu menu = VillageHallScreen.forTest(player, land.hall(1));
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(TradePage.Tab.PRICES.slot(), player);
			return menu;
		}
		return TradePage.forStrangerTest(player, land.hall(1));
	}

	private static String tabLine(Land land, ServerPlayer player) {
		return RealmEdictGameTests.lore(board(land, player).icon(TradePage.Tab.PRICES.slot())).get(0);
	}

	/** {@code player}, holding exactly {@code emeralds}, buys one bundle at hall 1's board; {logs, emeralds left} and the good's first line after. */
	private record Bought(int logs, int emeralds, String said) {
	}

	private static Bought buy(GameTestHelper helper, Land land, ServerPlayer player, int cents, int emeralds) {
		prices(helper, land, cents);
		player.getInventory().clearContent();
		if (emeralds > 0) {
			player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		}
		ChoiceMenu menu = board(land, player);
		int slot = BoardTradeGameTests.slot(helper, menu, "Test Timber");
		menu.clicked(slot, 1, ClickType.PICKUP, player);
		Bought bought = new Bought(BoardTradeGameTests.has(player, Items.OAK_LOG), BoardTradeGameTests.has(player, Items.EMERALD),
			RealmEdictGameTests.lore(menu.icon(slot)).get(0));
		player.getInventory().clearContent();
		return bought;
	}

	/**
	 * At hall 1's board (owned by the capital's owner): the owner of a second member and that owner's friend (neither
	 * owns hall 1, nor is the capital owner's friend) each hold exactly 10 emeralds at a board price of 10, read the
	 * realm line on the Prices tab and buy a bundle for exactly 10; a stranger at the same board and price with the same
	 * 10 emeralds reads the plain line, is refused with "You can't pay 11 emeralds ..." and keeps all 10; with 11 he buys.
	 * The stranger's price is the member's plus 10%, to the cent, at several prices.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1103cMarketMembers"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1103cMarketMembers")
	public void aSecondMembersOwnerAndTheirFriendBuyAtTheBoardsPrice(GameTestHelper helper) {
		Land land = land(helper, 2, 0);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		ServerPlayer other = RealmGameTests.listener(helper, new ArrayList<>(), "qa1103c-member");
		land.entity(2).setOwner(other.getUUID(), "qa1103c-member");
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "qa1103c-friend");
		Friends.get(server).add(other.getUUID(), friend.getUUID(), "qa1103c-friend");
		Leftovers.after(helper, () -> Friends.get(server).remove(other.getUUID(), friend.getUUID()));
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "qa1103c-stranger");
		helper.assertFalse(other.hasPermissions(2) || friend.hasPermissions(2) || stranger.hasPermissions(2), "setup: an operator; the check would prove nothing");
		helper.assertFalse(Friends.get(server).mayDirect(land.owner().getUUID(), friend.getUUID()), "setup: the friend is the capital owner's friend too");
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		prices(helper, land, 1000);
		TradeGoods.Good timber = TradeGoods.get(BoardTradeGameTests.TIMBER);
		helper.assertTrue(Board.price(level, land.hall(1), timber) == 1000, "setup: the common price " + Board.price(level, land.hall(1), timber));

		// each viewer's Prices tab
		helper.assertTrue(tabLine(land, other).equals(REALM), "the second member's owner's Prices tab: " + tabLine(land, other));
		helper.assertTrue(tabLine(land, friend).equals(REALM), "their friend's Prices tab: " + tabLine(land, friend));
		helper.assertTrue(tabLine(land, stranger).equals(PLAIN), "the stranger's Prices tab: " + tabLine(land, stranger));

		// the 10 percent to the cent
		for (int cents : new int[]{5, 100, 999, 1000, 1234, 1235}) {
			int member = TradePage.selling(level, land.hall(1), cents, other);
			int theirFriend = TradePage.selling(level, land.hall(1), cents, friend);
			int theStranger = TradePage.selling(level, land.hall(1), cents, stranger);
			helper.assertTrue(member == cents && theirFriend == cents, "the realm's people at " + cents + ": " + member + " / " + theirFriend);
			helper.assertTrue(theStranger == Math.round(member * 110 / 100.0), "the stranger at " + cents + ": " + theStranger + " (the member pays " + member + ")");
		}

		// the same 10 emeralds at the same board
		Bought b = buy(helper, land, other, 1000, 10);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "the second member's owner with exactly 10: " + b);
		helper.assertTrue(b.said().endsWith("for 10 emeralds."), "the second member's owner's trade: " + b.said());
		b = buy(helper, land, friend, 1000, 10);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "their friend with exactly 10: " + b);
		b = buy(helper, land, stranger, 1000, 10);
		helper.assertTrue(b.logs() == 0 && b.emeralds() == 10, "the stranger with 10 bought: " + b);
		helper.assertTrue(b.said().equals("You can't pay 11 emeralds for a bundle of Test Timber."), "the stranger, short: " + b.said());
		b = buy(helper, land, stranger, 1000, 11);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "the stranger with exactly 11: " + b);
		// one short of the board's price: the member is told the board's price, not the stranger's
		b = buy(helper, land, friend, 1000, 9);
		helper.assertTrue(b.logs() == 0 && b.emeralds() == 9 && b.said().equals("You can't pay 10 emeralds for a bundle of Test Timber."), "the friend, one short: " + b);
		helper.succeed();
	}

	/**
	 * An operator who owns no village and is nobody's friend is one of the realm's people: the realm line, the board's
	 * price, a bundle for exactly 1 emerald. Deopped, the same player is a stranger: the plain line, 1.1 emeralds, and
	 * "You can't pay 1.1 emeralds ..." with 1.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1103cMarketOperator"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1103cMarketOperator")
	public void anOperatorWhoOwnsNothingBuysAtTheBoardsPrice(GameTestHelper helper) {
		Land land = land(helper, 1, 0);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		ServerPlayer op = RealmGameTests.listener(helper, new ArrayList<>(), "qa1103c-op");
		helper.assertFalse(op.hasPermissions(2), "setup: already an operator");
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		helper.assertTrue(TradePage.selling(level, land.hall(1), 100, op) == 110, "not yet an operator: " + TradePage.selling(level, land.hall(1), 100, op));
		server.getPlayerList().getOps().add(new ServerOpListEntry(op.getGameProfile(), 2, false));
		Leftovers.after(helper, () -> server.getPlayerList().deop(op.getGameProfile()));
		helper.assertTrue(op.hasPermissions(2), "not an operator");
		for (BlockPos hall : land.halls()) {
			helper.assertTrue(!op.getUUID().equals(((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).owner()), "setup: the op owns a hall");
		}
		helper.assertTrue(TradePage.selling(level, land.hall(1), 100, op) == 100, "the operator's price: " + TradePage.selling(level, land.hall(1), 100, op));
		helper.assertTrue(tabLine(land, op).equals(REALM), "the operator's Prices tab: " + tabLine(land, op));
		Bought b = buy(helper, land, op, 100, 1);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0 && b.said().endsWith("for 1 emerald."), "the operator with exactly 1: " + b);
		server.getPlayerList().deop(op.getGameProfile());
		helper.assertFalse(op.hasPermissions(2), "still an operator");
		helper.assertTrue(tabLine(land, op).equals(PLAIN), "the deopped player's Prices tab: " + tabLine(land, op));
		b = buy(helper, land, op, 100, 1);
		helper.assertTrue(b.logs() == 0 && b.emeralds() == 1 && b.said().equals("You can't pay 1.1 emeralds for a bundle of Test Timber."), "deopped with 1: " + b);
		b = buy(helper, land, op, 100, 2);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "deopped with 2 (1.1 charged as 2): " + b);
		helper.succeed();
	}

	/**
	 * The owner of a village outside the realm (hall 2, never joined) and that owner's friend are strangers at a
	 * member's board: the plain line, 10% more, refused with the stranger's price when they hold only the board's.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1103cMarketOutsider"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1103cMarketOutsider")
	public void theOwnerOfAVillageOutsideTheRealmIsAStranger(GameTestHelper helper) {
		Land land = land(helper, 1, 1);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		ServerPlayer outsider = RealmGameTests.listener(helper, new ArrayList<>(), "qa1103c-outsider");
		land.entity(2).setOwner(outsider.getUUID(), "qa1103c-outsider");
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "qa1103c-outsider-friend");
		Friends.get(server).add(outsider.getUUID(), friend.getUUID(), "qa1103c-outsider-friend");
		Leftovers.after(helper, () -> Friends.get(server).remove(outsider.getUUID(), friend.getUUID()));
		helper.assertTrue(!Realms.members(RealmEdictGameTests.theRealm(land)).contains(land.at(2)), "setup: hall 2 is in the realm");
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		prices(helper, land, 1000);
		for (ServerPlayer p : List.of(outsider, friend)) {
			String who = p == outsider ? "the outsider" : "the outsider's friend";
			helper.assertTrue(TradePage.selling(level, land.hall(1), 1000, p) == 1100, who + "'s price: " + TradePage.selling(level, land.hall(1), 1000, p));
			helper.assertTrue(tabLine(land, p).equals(PLAIN), who + "'s Prices tab: " + tabLine(land, p));
			Bought b = buy(helper, land, p, 1000, 10);
			helper.assertTrue(b.logs() == 0 && b.emeralds() == 10 && b.said().equals("You can't pay 11 emeralds for a bundle of Test Timber."), who + " with 10: " + b);
		}
		// and the realm's owner, at the same board, buys with the same 10
		Bought b = buy(helper, land, land.owner(), 1000, 10);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "the realm's owner with 10: " + b);
		helper.succeed();
	}

	/**
	 * Lifted (after its 3 days), the Common Market's people pay the spread again at the board itself: the owner and
	 * another member's owner read the plain line, holding the board's price are refused with "You can't pay 11
	 * emeralds ...", and pay 11. Proclaimed again, they buy at the board's price once more.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1103cMarketLifted"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1103cMarketLifted")
	public void liftedTheRealmsPeoplePayTheSpreadAgain(GameTestHelper helper) {
		Land land = land(helper, 2, 0);
		ServerLevel level = land.level();
		ServerPlayer other = RealmGameTests.listener(helper, new ArrayList<>(), "qa1103c-lift-member");
		land.entity(2).setOwner(other.getUUID(), "qa1103c-lift-member");
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		Bought b = buy(helper, land, other, 1000, 10);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "under the edict, the member's owner with 10: " + b);
		RealmEdictGameTests.nextDay(land, 3);
		helper.assertTrue(RealmEdicts.lift(level, land.hall(0), land.owner(), RealmEdictGameTests.full("common_market")).done(), "couldn't lift the market");
		for (ServerPlayer p : List.of(land.owner(), other)) {
			String who = p == other ? "the member's owner" : "the owner";
			prices(helper, land, 1000);
			helper.assertTrue(tabLine(land, p).equals(PLAIN), who + "'s Prices tab after lifting: " + tabLine(land, p));
			b = buy(helper, land, p, 1000, 10);
			helper.assertTrue(b.logs() == 0 && b.emeralds() == 10 && b.said().equals("You can't pay 11 emeralds for a bundle of Test Timber."), who + " with 10 after lifting: " + b);
			b = buy(helper, land, p, 1000, 11);
			helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, who + " with 11 after lifting: " + b);
		}
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		prices(helper, land, 1000);
		helper.assertTrue(tabLine(land, other).equals(REALM), "proclaimed again, the member's owner's tab: " + tabLine(land, other));
		b = buy(helper, land, other, 1000, 10);
		helper.assertTrue(b.logs() == 16 && b.emeralds() == 0, "proclaimed again, the member's owner with 10: " + b);
		helper.succeed();
	}
}
