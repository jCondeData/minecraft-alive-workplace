package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
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
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-31), written from the specs of ROADMAP 33.18 (trade pacts and alliances) and 33.22a (the Common
 * Market: the realm's own people buy at the board's price, strangers pay 10 percent more), for what the existing
 * pact and market tests leave out: the 10 percent at whole-emerald edges (9, 10 and 11 emeralds) through the board's
 * own screens with exactly enough money and one emerald short, both sides; a player who stops being a friend or whose
 * village leaves the realm becomes a stranger at once; a pact or offer whose other village's hall is broken can still
 * be ended, and lapses as usual; an offer survives save and reload and is accepted after it.
 */
public class QaPacts1031GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- 33.22a: the Common Market's 10 percent at the whole-emerald edges --------------------------------------------

	/** The realm, the Common Market proclaimed, a Storehouse and chest of logs by hall 1, both halls' timber at {@code cents}. */
	private static Land market(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		helper.setBlock(new BlockPos(12, 2, 6), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(12, 2, 8), Blocks.CHEST);
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		return land;
	}

	private static void prices(GameTestHelper helper, Land land, int cents) {
		Container chest = helper.getBlockEntity(new BlockPos(12, 2, 8));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		BoardTradeGameTests.price(helper, land.hall(0), BoardTradeGameTests.TIMBER, cents);
		BoardTradeGameTests.price(helper, land.hall(1), BoardTradeGameTests.TIMBER, cents);
	}

	/** The owner's Prices tab at hall 1. */
	private static ChoiceMenu ownTab(Land land) {
		ChoiceMenu menu = VillageHallScreen.forTest(land.owner(), land.hall(1));
		menu.press(VillageHallScreen.ROUTES, land.owner());
		menu.press(TradePage.Tab.PRICES.slot(), land.owner());
		return menu;
	}

	/**
	 * One bundle bought from the board of hall 1 by {@code player} holding exactly {@code emeralds}; returns
	 * {logs got, emeralds left}.
	 */
	private static int[] buy(GameTestHelper helper, Land land, ServerPlayer player, boolean own, int cents, int emeralds) {
		prices(helper, land, cents);
		player.getInventory().clearContent();
		if (emeralds > 0) {
			player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		}
		ChoiceMenu menu = own ? ownTab(land) : TradePage.forStrangerTest(player, land.hall(1));
		int slot = BoardTradeGameTests.slot(helper, menu, "Test Timber");
		menu.clicked(slot, 1, ClickType.PICKUP, player);
		return new int[]{BoardTradeGameTests.has(player, Items.OAK_LOG), BoardTradeGameTests.has(player, Items.EMERALD)};
	}

	/**
	 * A board price of 9, 10 and 11 emeralds: the realm's owner pays exactly 9, 10 and 11 (one short buys nothing); a
	 * stranger pays 9.9 (charged 10), 11 and 12.1 (charged 13), and one emerald short of that buys nothing. Each
	 * good's tooltip names that viewer's price.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1031MarketEdges"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1031MarketEdges")
	public void theTenPercentAtNineTenAndElevenEmeralds(GameTestHelper helper) {
		Land land = market(helper);
		ServerLevel level = land.level();
		ServerPlayer owner = land.owner();
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "qa1031-stranger");
		TradeGoods.Good timber = TradeGoods.get(BoardTradeGameTests.TIMBER);
		int[][] cases = {{900, 9, 10, 9}, {1000, 10, 11, 11}, {1100, 11, 13, 12}};
		String[] strangerSell = {"We sell: 9.9 emeralds", "We sell: 11 emeralds", "We sell: 12.1 emeralds"};
		for (int i = 0; i < cases.length; i++) {
			int cents = cases[i][0];
			int ownPays = cases[i][1];
			int strangerPays = cases[i][2];
			prices(helper, land, cents);
			helper.assertTrue(Board.price(level, land.hall(1), timber) == cents, "setup: the common price " + Board.price(level, land.hall(1), timber) + ", wanted " + cents);
			helper.assertTrue(TradePage.selling(level, land.hall(1), cents, owner) == cents, "the owner's price at " + cents + ": " + TradePage.selling(level, land.hall(1), cents, owner));
			helper.assertTrue(TradePage.selling(level, land.hall(1), cents, stranger) == cents * 110 / 100, "the stranger's price at " + cents + ": " + TradePage.selling(level, land.hall(1), cents, stranger));
			// the tooltips
			List<String> ownLore = RealmEdictGameTests.lore(ownTab(land).icon(BoardTradeGameTests.slot(helper, ownTab(land), "Test Timber")));
			ChoiceMenu strangerMenu = TradePage.forStrangerTest(stranger, land.hall(1));
			List<String> strangerLore = RealmEdictGameTests.lore(strangerMenu.icon(BoardTradeGameTests.slot(helper, strangerMenu, "Test Timber")));
			helper.assertTrue(ownLore.contains("We sell: " + ownPays + " emeralds"), "the owner's tooltip at " + cents + ": " + ownLore);
			helper.assertTrue(strangerLore.contains(strangerSell[i]), "the stranger's tooltip at " + cents + ": " + strangerLore);
			// one emerald short: nothing
			int[] got = buy(helper, land, owner, true, cents, ownPays - 1);
			helper.assertTrue(got[0] == 0 && got[1] == ownPays - 1, "the owner, one short at " + cents + ": " + got[0] + " logs, " + got[1] + " emeralds");
			got = buy(helper, land, stranger, false, cents, strangerPays - 1);
			helper.assertTrue(got[0] == 0 && got[1] == strangerPays - 1, "the stranger, one short at " + cents + ": " + got[0] + " logs, " + got[1] + " emeralds");
			// exactly enough: a bundle, nothing left
			got = buy(helper, land, owner, true, cents, ownPays);
			helper.assertTrue(got[0] == 16 && got[1] == 0, "the owner, exactly " + ownPays + " at " + cents + ": " + got[0] + " logs, " + got[1] + " emeralds");
			got = buy(helper, land, stranger, false, cents, strangerPays);
			helper.assertTrue(got[0] == 16 && got[1] == 0, "the stranger, exactly " + strangerPays + " at " + cents + ": " + got[0] + " logs, " + got[1] + " emeralds");
		}
		owner.getInventory().clearContent();
		helper.succeed();
	}

	/**
	 * Who counts as the realm's people changes at once: a friend of the owner buys at the board's price until the
	 * friendship ends, then pays 10% more; another member's owner pays the board's price until their village leaves
	 * the realm, then 10% more at the capital's board too.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031MarketPeople"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031MarketPeople")
	public void aFormerFriendAndALeaverPayTheTenPercent(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 2, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		ServerPlayer other = RealmGameTests.listener(helper, new ArrayList<>(), "qa1031-member");
		land.entity(2).setOwner(other.getUUID(), "qa1031-member");
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "qa1031-friend");
		Friends.get(server).add(land.owner().getUUID(), friend.getUUID(), "qa1031-friend");
		Leftovers.after(helper, () -> Friends.get(server).remove(land.owner().getUUID(), friend.getUUID()));
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		helper.assertTrue(TradePage.selling(level, land.hall(1), 1000, friend) == 1000, "the friend: " + TradePage.selling(level, land.hall(1), 1000, friend));
		helper.assertTrue(TradePage.selling(level, land.hall(0), 1000, other) == 1000, "the other member's owner: " + TradePage.selling(level, land.hall(0), 1000, other));
		Friends.get(server).remove(land.owner().getUUID(), friend.getUUID());
		helper.assertTrue(TradePage.selling(level, land.hall(1), 1000, friend) == 1100, "a friend no more: " + TradePage.selling(level, land.hall(1), 1000, friend));
		Realms.Result left = Realms.leave(level, land.hall(2), other);
		helper.assertTrue(RealmEdictGameTests.theRealm(land) != null && !Realms.members(RealmEdictGameTests.theRealm(land)).contains(land.at(2)), "didn't leave: " + left.message().getString());
		helper.assertTrue(TradePage.selling(level, land.hall(0), 1000, other) == 1100, "a leaver at the capital's board: " + TradePage.selling(level, land.hall(0), 1000, other));
		helper.assertTrue(TradePage.selling(level, land.hall(0), 1000, land.owner()) == 1000, "the realm's own owner after the leave: " + TradePage.selling(level, land.hall(0), 1000, land.owner()));
		helper.succeed();
	}

	/**
	 * The spread drops for the realm's people only under the Common Market: in a realm without it the owner pays 10%
	 * more at a member's board and their Prices tab says so; proclaimed, the owner's tab reads the realm line and pays
	 * the board's price, while a stranger's tab still reads the plain "(10% more)" line, word for word.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031MarketOnlyEdict"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031MarketOnlyEdict")
	public void theRealmsPeoplePayTheSpreadUntilTheCommonMarket(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		ServerLevel level = land.level();
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "qa1031-tab-stranger");
		String plain = "What the village pays for a bundle of each good, and what it sells one for (10% more). Prices move a third of the way toward supply and demand each dawn";
		String realm = "What the village pays for a bundle of each good. Under the Common Market the realm's people buy at that price too; strangers pay 10% more. "
			+ "Prices move a third of the way toward supply and demand each dawn";
		BoardTradeGameTests.price(helper, land.hall(0), BoardTradeGameTests.TIMBER, 100);
		BoardTradeGameTests.price(helper, land.hall(1), BoardTradeGameTests.TIMBER, 100);
		helper.assertTrue(TradePage.selling(level, land.hall(1), 100, land.owner()) == 110, "the owner without the edict: " + TradePage.selling(level, land.hall(1), 100, land.owner()));
		String ownLine = RealmEdictGameTests.lore(ownTab(land).icon(TradePage.Tab.PRICES.slot())).get(0);
		helper.assertTrue(ownLine.equals(plain), "the owner's Prices tab without the edict: " + ownLine);
		ChoiceMenu ownMenu = ownTab(land);
		List<String> ownLore = RealmEdictGameTests.lore(ownMenu.icon(BoardTradeGameTests.slot(helper, ownMenu, "Test Timber")));
		helper.assertTrue(ownLore.contains("We sell: 1.1 emeralds"), "the owner's tooltip without the edict: " + ownLore);
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		helper.assertTrue(TradePage.selling(level, land.hall(1), 100, land.owner()) == 100, "the owner under the edict: " + TradePage.selling(level, land.hall(1), 100, land.owner()));
		ownLine = RealmEdictGameTests.lore(ownTab(land).icon(TradePage.Tab.PRICES.slot())).get(0);
		helper.assertTrue(ownLine.equals(realm), "the owner's Prices tab under the edict: " + ownLine);
		String strangerLine = RealmEdictGameTests.lore(TradePage.forStrangerTest(stranger, land.hall(1)).icon(TradePage.Tab.PRICES.slot())).get(0);
		helper.assertTrue(strangerLine.equals(plain), "the stranger's Prices tab under the edict: " + strangerLine);
		ownMenu = ownTab(land);
		ownLore = RealmEdictGameTests.lore(ownMenu.icon(BoardTradeGameTests.slot(helper, ownMenu, "Test Timber")));
		helper.assertTrue(ownLore.contains("We sell: 1 emerald"), "the owner's tooltip under the edict (singular): " + ownLore);
		helper.succeed();
	}

	// ---- 33.18: the other village's hall broken, save and reload ------------------------------------------------------

	/**
	 * The neighbour's hall is broken while a pact stands: the owner can still end the pact at once on their side (its
	 * route stops), and their own Pacts tab still opens.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031PactRuin"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031PactRuin")
	public void aPactWithABrokenHallCanStillBeEnded(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		QaPactsRealmEdictsGameTests.pact(land, false);
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		level.setBlockAndUpdate(land.hall(1), Blocks.AIR.defaultBlockState());
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(menu != null, "the Pacts tab didn't open");
		Pacts.Result end = Pacts.end(level, land.hall(0), land.at(1), land.owner());
		helper.assertTrue(end.ok(), "couldn't end the pact with a broken hall: " + end.message().getString());
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null && !caravans.routesFrom(land.hall(0)).contains(land.hall(1)),
			"the pact or its route stayed: " + Pacts.Data.get(server).pacts());
		helper.succeed();
	}

	/** An offer to a village whose hall is then broken lapses after 3 days like any other and the offerer hears of it. */
	//$ gametest_ticks_batch HUGE '100' '"qa1031OfferRuin"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031OfferRuin")
	public void anOfferToABrokenHallLapsesAsUsual(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		String name = land.name(1);
		helper.assertTrue(Pacts.offer(level, land.hall(0), land.hall(1), false, land.owner()).ok(), "the offer was refused");
		level.setBlockAndUpdate(land.hall(1), Blocks.AIR.defaultBlockState());
		Pacts.Data data = Pacts.Data.get(server);
		Pacts.Offer offer = data.offers().get(0);
		data.clear();
		data.addOffer(new Pacts.Offer(offer.from(), offer.to(), false, offer.owner(), server.overworld().getGameTime() - Pacts.LAPSE - 1));
		Pacts.settle(server);
		helper.assertTrue(data.offers().isEmpty() && data.pacts().isEmpty(), "the offer stayed or made a pact: " + data.offers() + " " + data.pacts());
		helper.assertTrue(RealmGameTests.chronicled(land, 0, "The offer to " + name + " lapsed unanswered"), "the lapse wasn't chronicled: " + land.entity(0).chronicle());
		helper.succeed();
	}

	/**
	 * An offer saved and loaded keeps its kind, sender and day sent; accepted after the reload it makes the alliance and
	 * the routes may run; the same offer loaded a tick past its 3 days can't be accepted.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031OfferReload"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031OfferReload")
	public void anOfferSavedAndLoadedCanStillBeAccepted(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		helper.assertTrue(Pacts.offer(level, land.hall(0), land.hall(1), true, land.owner()).ok(), "the alliance offer was refused");
		Pacts.Data data = Pacts.Data.get(server);
		Pacts.Offer before = data.offers().get(0);
		Pacts.Data back = Pacts.Data.load(data.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()), server.registryAccess());
		helper.assertTrue(back.offers().equals(List.of(before)), "the offer changed in the save: " + back.offers() + " vs " + before);
		data.clear();
		data.addOffer(back.offers().get(0));
		helper.assertTrue(!Pacts.mayRoute(level, land.hall(0), land.hall(1)), "a route before accepting");
		Pacts.Result ok = Pacts.accept(level, land.hall(1), land.at(0), two.them());
		helper.assertTrue(ok.ok(), "couldn't accept the reloaded offer: " + ok.message().getString());
		Pacts.Pact pact = data.pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.alliance() && Pacts.mayRoute(level, land.hall(0), land.hall(1)) && Pacts.mayRoute(level, land.hall(1), land.hall(0)),
			"no alliance or no routes: " + pact);
		// the same offer, a tick past its 3 days
		data.clear();
		data.addOffer(new Pacts.Offer(before.from(), before.to(), true, before.owner(), server.overworld().getGameTime() - Pacts.LAPSE - 1));
		helper.assertTrue(!Pacts.accept(level, land.hall(1), land.at(0), two.them()).ok() && data.pacts().isEmpty(), "an offer past its 3 days was accepted");
		helper.succeed();
	}
}
