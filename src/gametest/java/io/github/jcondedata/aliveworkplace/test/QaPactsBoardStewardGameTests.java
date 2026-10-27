package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardJobs;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA, written from the specs of ROADMAP 33.5 (trading at the board), 33.18 (trade pacts and alliances), 33.20 (feuds),
 * 27.8 (the Steward's desk) and 27.9 (Steward jobs), aimed at what the feature classes leave out: every boundary from
 * both sides (exactly enough emeralds, treasury and chest room, and one short), a third player who isn't either owner,
 * a friend who acts for the owner, the player's own buttons (the Pacts tab's offer, the desk's Approve all and Show me),
 * a feud's 25% at a real board trade, and the new sentences being translated.
 */
public class QaPactsBoardStewardGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;

	// ---- 33.5: the board's boundaries -------------------------------------------------------------------------------

	/** The hall's screen, its minecart, then the Prices tab. */
	private static ChoiceMenu prices(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.PRICES.slot(), player);
		return menu;
	}

	/**
	 * A purchase at 1 emerald a bundle's price is 1.1 emeralds, charged 2: a player with exactly 2 buys and is left with
	 * none; then with 1 emerald (one short) nothing moves, neither the chest nor the treasury.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaBoardBuyExact"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaBoardBuyExact")
	public void aBuyerWithExactlyEnoughBuysAndOneShortDoesNot(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, new BlockPos(9, 2, 9), true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			player.getInventory().add(new ItemStack(Items.EMERALD, 2));
			ChoiceMenu menu = prices(player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 16 && BoardTradeGameTests.has(player, Items.EMERALD) == 0
				&& BoardTradeGameTests.count(chest, Items.OAK_LOG) == 48 && entity.treasury() == 200, "exactly 2 emeralds for 1.1: the player has "
				+ BoardTradeGameTests.has(player, Items.OAK_LOG) + " logs, " + BoardTradeGameTests.has(player, Items.EMERALD) + " emeralds, treasury " + entity.treasury());
			// one short: the next bundle (1.12, charged 2) with 1 emerald
			player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.OAK_LOG), 64, player.inventoryMenu.getCraftSlots());
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 0 && BoardTradeGameTests.has(player, Items.EMERALD) == 1
				&& BoardTradeGameTests.count(chest, Items.OAK_LOG) == 48 && entity.treasury() == 200, "one emerald short, it still sold: "
				+ BoardTradeGameTests.has(player, Items.OAK_LOG) + " logs, " + BoardTradeGameTests.count(chest, Items.OAK_LOG) + " in the chest");
			String said = BoardTradeGameTests.lore(menu.icon(timber)).get(0);
			helper.assertTrue(said.startsWith("You can't pay") && !said.contains("aliveworkplace."), "it didn't say why: " + said);
			helper.succeed();
		});
	}

	/** A sale of a bundle worth exactly 1 emerald: a treasury of exactly 1 emerald pays it (and is left empty); 0.99 can't. */
	//$ gametest_ticks_batch BIG '100' '"qaBoardTreasuryExact"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaBoardTreasuryExact")
	public void aTreasuryOfExactlyThePricePaysAndOneCentShortDoesNot(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, new BlockPos(9, 2, 9), true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			Container chest = BoardTradeGameTests.chest(helper);
			entity.setTreasury(100);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
			ChoiceMenu menu = prices(player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(entity.treasury() == 0 && BoardTradeGameTests.has(player, Items.EMERALD) == 1 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 16,
				"a treasury of exactly 1 emerald for a 1-emerald bundle: treasury " + entity.treasury() + ", paid " + BoardTradeGameTests.has(player, Items.EMERALD));
			// one cent short
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			entity.setTreasury(99);
			menu.press(timber, player);
			helper.assertTrue(entity.treasury() == 99 && BoardTradeGameTests.has(player, Items.EMERALD) == 1 && BoardTradeGameTests.has(player, Items.OAK_LOG) == 16
				&& BoardTradeGameTests.count(chest, Items.OAK_LOG) == 16, "a treasury one cent short still paid: treasury " + entity.treasury());
			String said = BoardTradeGameTests.lore(menu.icon(timber)).get(0);
			helper.assertTrue(said.contains("treasury can't pay") && !said.contains("aliveworkplace."), "it didn't say the treasury stopped it: " + said);
			helper.succeed();
		});
	}

	/** Room for exactly one bundle (16 logs) takes it; room for 15 takes nothing and gives everything back. */
	//$ gametest_ticks_batch BIG '100' '"qaBoardRoomExact"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaBoardRoomExact")
	public void roomForExactlyABundleTakesItAndOneShortTakesNothing(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, new BlockPos(9, 2, 9), true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(2000);
			Container chest = BoardTradeGameTests.chest(helper);
			for (int i = 1; i < chest.getContainerSize(); i++) {
				chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 48));
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
			ChoiceMenu menu = prices(player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.count(chest, Items.OAK_LOG) == 64 && BoardTradeGameTests.has(player, Items.OAK_LOG) == 16
				&& BoardTradeGameTests.has(player, Items.EMERALD) == 1 && entity.treasury() == 1900, "room for exactly 16: the chest holds "
				+ BoardTradeGameTests.count(chest, Items.OAK_LOG) + ", the player " + BoardTradeGameTests.has(player, Items.OAK_LOG));
			// room for 15
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 49));
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.count(chest, Items.OAK_LOG) == 49 && BoardTradeGameTests.has(player, Items.OAK_LOG) == 16 && entity.treasury() == 1900,
				"room for 15 took logs: the chest holds " + BoardTradeGameTests.count(chest, Items.OAK_LOG) + ", the player " + BoardTradeGameTests.has(player, Items.OAK_LOG));
			String said = BoardTradeGameTests.lore(menu.icon(timber)).get(0);
			helper.assertTrue(said.contains("no room") && !said.contains("aliveworkplace."), "it didn't say why: " + said);
			helper.succeed();
		});
	}

	// ---- 33.18 and 33.20: two owners --------------------------------------------------------------------------------

	private record Two(Land land, ServerPlayer them, List<Component> theirs) {
	}

	/** Two villages of two owners on the caravans' list; pacts, alliances and feuds on and empty, PvP on; put back after. */
	private static Two two(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean pacts = Pacts.ENABLED;
		boolean alliances = Pacts.ALLIANCES;
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Pacts.ALLIANCES = alliances;
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			Caravans.Data data = Caravans.Data.get(land.level());
			data.remove(land.hall(0));
			data.remove(land.hall(1));
		});
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = RealmGameTests.listener(helper, theirs, "neighbour");
		land.entity(1).setOwner(them.getUUID(), "neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		return new Two(land, them, theirs);
	}

	private static ChoiceMenu pactsTab(Land land, int hall, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, land.hall(hall));
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.PACTS.slot(), player);
		return menu;
	}

	private static int find(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().equals(name)) {
				return slot;
			}
		}
		return -1;
	}

	private static String letter(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = player.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				return book.pages().get(0).raw().getString();
			}
		}
		return null;
	}

	/**
	 * The owner offers a pact the way a player does, with the Pacts tab's "Offer … a trade pact" button: the offer waits,
	 * a letter reaches the other owner, and the button is gone (no second offer); with alliances off there's no
	 * alliance button.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPactOfferButton"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPactOfferButton")
	public void anOfferMadeWithTheTabsButtonReachesTheOtherOwner(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int pact = find(menu, "Offer " + land.name(1) + " a trade pact");
		helper.assertTrue(pact >= 0, "no Offer a trade pact button on the Pacts tab");
		helper.assertTrue(find(menu, "Offer " + land.name(1) + " an alliance") >= 0, "no Offer an alliance button with alliances on");
		menu.press(pact, land.owner());
		helper.assertTrue(Pacts.to(server, land.at(1)).size() == 1 && !Pacts.to(server, land.at(1)).get(0).alliance(), "no offer waiting: " + Pacts.to(server, land.at(1)));
		String letter = letter(two.them());
		helper.assertTrue(letter != null && letter.contains("trade pact") && !letter.contains("aliveworkplace."), "the letter: " + letter);
		ChoiceMenu again = pactsTab(land, 0, land.owner());
		helper.assertTrue(find(again, "Offer " + land.name(1) + " a trade pact") < 0, "the offer button is still there with an offer waiting");
		// alliances off: no alliance button
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Pacts.ALLIANCES = false;
		ChoiceMenu off = pactsTab(land, 0, land.owner());
		helper.assertTrue(find(off, "Offer " + land.name(1) + " an alliance") < 0 && find(off, "Offer " + land.name(1) + " a trade pact") >= 0,
			"with alliances off the buttons are wrong");
		helper.succeed();
	}

	/**
	 * A third player, neither owner nor friend: can't accept (on the Pacts tab or otherwise), decline, offer for, or end a
	 * pact, and says why. The owner's friend may answer for them.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPactStranger"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPactStranger")
	public void aThirdPlayerCantAnswerOfferOrEndAPact(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "passer-by");
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "pal");
		Friends.get(server).add(two.them().getUUID(), friend.getUUID(), "pal");
		Leftovers.after(helper, () -> Friends.get(server).remove(two.them().getUUID(), friend.getUUID()));
		helper.assertTrue(!Pacts.offer(land.level(), land.hall(0), land.hall(1), false, stranger).ok(), "a stranger offered a pact for village 0");
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the owner's offer was refused");
		// the stranger presses Accept on village 1's tab (if they can see it) and calls accept/decline
		ChoiceMenu menu = pactsTab(land, 1, stranger);
		int accept = find(menu, "Accept a trade pact with " + land.name(0));
		if (accept >= 0) {
			menu.press(accept, stranger);
		}
		Pacts.Result refused = Pacts.accept(land.level(), land.hall(1), land.at(0), stranger);
		helper.assertTrue(!refused.ok() && Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null, "a stranger accepted the pact");
		helper.assertTrue(refused.message().getString().startsWith("Only the village's owner or their friends"), "it didn't say why: " + refused.message().getString());
		helper.assertTrue(!Pacts.decline(land.level(), land.hall(1), land.at(0), stranger).ok() && Pacts.to(server, land.at(1)).size() == 1, "a stranger declined the offer");
		// the owner's friend answers for them
		helper.assertTrue(Pacts.accept(land.level(), land.hall(1), land.at(0), friend).ok(), "the owner's friend couldn't accept");
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "no route after the friend accepted");
		// the stranger can't end it, nor turn gifts on
		helper.assertTrue(!Pacts.end(land.level(), land.hall(1), land.at(0), stranger).ok() && Pacts.Data.get(server).pact(land.at(0), land.at(1)) != null,
			"a stranger ended the pact");
		helper.assertTrue(!Pacts.toggleGifts(land.level(), land.hall(1), land.at(0), stranger) && !Pacts.gives(land.level(), land.hall(1), land.hall(0)),
			"a stranger turned gifts on");
		helper.succeed();
	}

	/** An offer is open for 3 days: one an hour short of them can still be accepted; one a tick past them lapses. */
	//$ gametest_ticks_batch HUGE '100' '"qaPactLapse"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPactLapse")
	public void anOfferStandsThreeDaysAndNotATickLonger(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data data = Pacts.Data.get(server);
		data.addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - Pacts.LAPSE + 1000));
		Pacts.settle(server);
		helper.assertTrue(Pacts.to(server, land.at(1)).size() == 1, "an offer an hour short of 3 days lapsed");
		ChoiceMenu menu = pactsTab(land, 1, two.them());
		int accept = find(menu, "Accept a trade pact with " + land.name(0));
		helper.assertTrue(accept >= 0, "no Accept line for an offer that hasn't lapsed");
		menu.press(accept, two.them());
		helper.assertTrue(data.pact(land.at(0), land.at(1)) != null, "an offer an hour short of 3 days couldn't be accepted");
		// a tick past 3 days
		data.clear();
		data.setMigrated(true);
		data.addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - Pacts.LAPSE - 1));
		helper.assertTrue(Pacts.to(server, land.at(1)).isEmpty(), "an offer past 3 days still waits on the tab");
		helper.assertTrue(!Pacts.accept(land.level(), land.hall(1), land.at(0), two.them()).ok() && data.pact(land.at(0), land.at(1)) == null,
			"an offer past 3 days was accepted");
		helper.assertTrue(Pacts.LAPSE == 3 * 24000L, "the lapse isn't 3 days: " + Pacts.LAPSE);
		helper.succeed();
	}

	/** Your own other village, or a friend's, needs no pact: routes are allowed, and an offer is refused with the reason. */
	//$ gametest_ticks_batch HUGE '100' '"qaPactOwn"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPactOwn")
	public void yourOwnOrAFriendsVillageNeedsNoPact(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		land.entity(1).setOwner(land.owner().getUUID(), "Owner");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "a route between one owner's villages needs a pact");
		Pacts.Result own = Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner());
		helper.assertTrue(!own.ok() && own.message().getString().contains("needs no pact"), "a pact with your own village: " + own.message().getString());
		// a friend's village
		land.entity(1).setOwner(two.them().getUUID(), "neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Friends.get(server).add(land.owner().getUUID(), two.them().getUUID(), "neighbour");
		Friends.get(server).add(two.them().getUUID(), land.owner().getUUID(), "Owner");
		Leftovers.after(helper, () -> {
			Friends.get(server).remove(land.owner().getUUID(), two.them().getUUID());
			Friends.get(server).remove(two.them().getUUID(), land.owner().getUUID());
		});
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "a route to a friend's village needs a pact");
		helper.assertTrue(!Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "a pact offered to a friend's village");
		helper.succeed();
	}

	/** An alliance given notice still stands a tick before its day is up (allies, routes), and ends after it. */
	//$ gametest_ticks_batch HUGE '100' '"qaAllianceNotice"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaAllianceNotice")
	public void anAllianceStandsUntilItsDayOfNoticeIsUp(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Pacts.Data data = Pacts.Data.get(server);
		data.put(new Pacts.Pact(land.at(0), land.at(1), true, 0, -1, false, false));
		Pacts.Result notice = Pacts.end(land.level(), land.hall(1), land.at(0), two.them());
		helper.assertTrue(notice.ok() && notice.message().getString().contains("ends in a day"), "notice: " + notice.message().getString());
		String letter = letter(land.owner());
		helper.assertTrue(letter != null && letter.contains("ends in a day"), "the other owner's letter: " + letter);
		long now = server.overworld().getGameTime();
		data.put(new Pacts.Pact(land.at(0), land.at(1), true, 0, now + 1, false, false));
		Pacts.settle(server);
		helper.assertTrue(data.pact(land.at(0), land.at(1)) != null && !Pacts.allies(server, land.at(0)).isEmpty() && Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)),
			"the alliance ended a tick early");
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int shown = find(menu, "Alliance with " + land.name(1));
		helper.assertTrue(shown >= 0 && RealmGameTests.lore(menu.icon(shown)).contains("Ends within the hour"), "the tab doesn't say it's ending (B596: within the hour, not \"Ends in 0 hours\")");
		data.put(new Pacts.Pact(land.at(0), land.at(1), true, 0, now, false, false));
		Pacts.settle(server);
		helper.assertTrue(data.pact(land.at(0), land.at(1)) == null && Pacts.allies(server, land.at(0)).isEmpty(), "the alliance outlived its notice");
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "a route after the alliance ended");
		helper.succeed();
	}

	/**
	 * A feud at a real board trade (33.5 with 33.20): the rival owner buying at village 1's board is charged 25% more
	 * (3.3 emeralds become 4.13, charged 5) and selling is paid 25% less (4 become 3); a neutral player trades at the
	 * board's price.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFeudBoard"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFeudBoard")
	public void aRivalPaysAQuarterMoreAndGetsAQuarterLessAtTheBoard(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		BlockPos hall = land.hall(1);
		// The hall's reach is wider than the test area: a Storehouse left by an earlier test at this spot would be
		// counted too (this test runs alone in its batch, so clearing them disturbs nobody).
		net.minecraft.world.entity.ai.village.poi.PoiManager pois = land.level().getPoiManager();
		pois.findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI), p -> true, hall,
				VillageHalls.RADIUS, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList().forEach(p -> {
				land.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				if (pois.getType(p).isPresent()) pois.remove(p);
			});
		helper.setBlock(helper.relativePos(hall).offset(4, 0, 0), ModBlocks.STOREHOUSE);
		helper.setBlock(helper.relativePos(hall).offset(4, 0, 2), Blocks.CHEST);
		ServerPlayer rival = land.owner();
		ServerPlayer neutral = RealmGameTests.listener(helper, new ArrayList<>(), "passer-by");
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "couldn't declare the feud");
		helper.runAfterDelay(2, () -> {
			List<BlockPos> chests = Caravans.storehouse(land.level(), hall);
			helper.assertTrue(chests.size() == 1, "setup: the Storehouse's chests: " + chests);
			Container chest = (Container) land.level().getBlockEntity(chests.get(0));
			VillageHallBlockEntity entity = land.entity(1);
			entity.setTreasury(5000);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			// buying: the rival
			BoardTradeGameTests.price(helper, hall, TIMBER, 300);
			rival.getInventory().add(new ItemStack(Items.EMERALD, 10));
			ChoiceMenu menu = prices(rival, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, rival);
			helper.assertTrue(BoardTradeGameTests.has(rival, Items.OAK_LOG) == 16 && BoardTradeGameTests.has(rival, Items.EMERALD) == 5,
				"the rival paid " + (10 - BoardTradeGameTests.has(rival, Items.EMERALD)) + " for a 3.3-emerald bundle, wanted 5 (25% more)");
			// buying: a neutral player
			BoardTradeGameTests.price(helper, hall, TIMBER, 300);
			neutral.getInventory().add(new ItemStack(Items.EMERALD, 10));
			ChoiceMenu theirs = prices(neutral, hall);
			theirs.press(timber, neutral);
			helper.assertTrue(BoardTradeGameTests.has(neutral, Items.OAK_LOG) == 16 && BoardTradeGameTests.has(neutral, Items.EMERALD) == 6,
				"the neutral player paid " + (10 - BoardTradeGameTests.has(neutral, Items.EMERALD)) + ", wanted 4");
			// selling: the rival gets 3 for a 4-emerald bundle, the neutral player 4
			BoardTradeGameTests.price(helper, hall, TIMBER, 400);
			int before = BoardTradeGameTests.has(rival, Items.EMERALD);
			menu = prices(rival, hall);
			menu.press(timber, rival);
			helper.assertTrue(BoardTradeGameTests.has(rival, Items.OAK_LOG) == 0 && BoardTradeGameTests.has(rival, Items.EMERALD) - before == 3,
				"the rival was paid " + (BoardTradeGameTests.has(rival, Items.EMERALD) - before) + " for a 4-emerald bundle, wanted 3 (25% less)");
			BoardTradeGameTests.price(helper, hall, TIMBER, 400);
			before = BoardTradeGameTests.has(neutral, Items.EMERALD);
			theirs = prices(neutral, hall);
			theirs.press(timber, neutral);
			helper.assertTrue(BoardTradeGameTests.has(neutral, Items.OAK_LOG) == 0 && BoardTradeGameTests.has(neutral, Items.EMERALD) - before == 4,
				"the neutral player was paid " + (BoardTradeGameTests.has(neutral, Items.EMERALD) - before) + ", wanted 4");
			helper.succeed();
		});
	}

	/** A third player can't declare a feud for someone's village, offer or accept peace, or raise a war; and says why. */
	//$ gametest_ticks_batch HUGE '100' '"qaFeudStranger"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFeudStranger")
	public void aThirdPlayerCantDeclareMakePeaceOrRaiseWar(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "passer-by");
		Pacts.Result declare = Feuds.declare(land.level(), land.hall(0), land.hall(1), stranger);
		helper.assertTrue(!declare.ok() && !Feuds.feuding(server, land.at(0), land.at(1)), "a stranger declared a feud for village 0");
		helper.assertTrue(declare.message().getString().startsWith("Only the village's owner or their friends"), "it didn't say why: " + declare.message().getString());
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "the owner couldn't declare");
		helper.assertTrue(!Feuds.offerPeace(land.level(), land.hall(1), land.at(0), stranger).ok(), "a stranger offered peace for village 1");
		helper.assertTrue(Feuds.offerPeace(land.level(), land.hall(1), land.at(0), two.them()).ok(), "the rival couldn't offer peace");
		helper.assertTrue(!Feuds.acceptPeace(land.level(), land.hall(0), land.at(1), stranger).ok() && Feuds.feuding(server, land.at(0), land.at(1)),
			"a stranger accepted peace for village 0");
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - Feuds.WAR_AFTER));
		helper.assertTrue(!Feuds.raiseWar(land.level(), land.hall(0), land.at(1), stranger).ok() && !Feuds.feud(server, land.at(0), land.at(1)).warRaised(),
			"a stranger raised the war");
		helper.succeed();
	}

	/** The villagers' grumbles about rivals are three lines, and every pact and feud sentence a player reads is translated. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPactAndFeudSentenceIsTranslated(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(lang.has("chatter.aliveworkplace.feud." + i) && lang.getOrDefault("chatter.aliveworkplace.feud." + i).contains("%s"),
				"grumble " + i + " missing or without the rival's name");
		}
		List<String> keys = new ArrayList<>();
		for (String k : List.of("off", "not_yours", "not_stranger", "too_far", "already", "offered", "no_offer", "no_pact", "ending", "feud")) {
			keys.add("message.aliveworkplace.pact.refused_" + k);
		}
		for (String k : List.of("off", "not_yours", "not_stranger", "too_far", "already", "no_feud", "peace_offered", "no_peace", "not_declarer",
			"war_raised", "wars_off", "pvp", "too_soon")) {
			keys.add("message.aliveworkplace.feud.refused_" + k);
		}
		for (String kind : List.of("pact", "alliance")) {
			for (String k : List.of("letter_", "offered_told_", "offered_", "accepted_told_", "accepted_", "end_letter_", "ended_told_")) {
				keys.add("message.aliveworkplace.pact." + k + kind);
			}
			keys.add("chronicle.aliveworkplace.pact.offered_" + kind);
			keys.add("chronicle.aliveworkplace.pact.made_" + kind);
			keys.add("chronicle.aliveworkplace.pact.ended_" + kind);
			keys.add("screen.aliveworkplace.pact.accept_" + kind);
			keys.add("screen.aliveworkplace.pact.end_" + kind);
			keys.add("screen.aliveworkplace.pact.offer_" + kind);
			keys.add("screen.aliveworkplace.pact." + kind);
		}
		for (String k : List.of("letter", "declared", "declared_told", "peace_letter", "peace_offered", "peace_offered_told", "peace_made", "peace_told",
			"faded_told", "war_letter", "war_raised", "war_told", "war_over")) {
			keys.add("message.aliveworkplace.feud." + k);
		}
		for (String k : List.of("declared", "declared_on", "peace_offered", "peace", "faded", "war_declared", "war_declared_on", "war_over")) {
			keys.add("chronicle.aliveworkplace.feud." + k);
		}
		for (String k : List.of("tab_count", "feud", "board", "board_rival", "at_war", "lost", "war_at_dawn", "fades", "offer_peace", "offer_peace_hint",
			"peace_waiting", "accept_peace", "accept_peace_hint", "raise_war", "war_hint", "war_pvp", "war_wars_off", "war_too_soon", "war_rules", "declare",
			"declare_hint")) {
			keys.add("screen.aliveworkplace.feud." + k);
		}
		for (String k : List.of("tab", "tab_count", "waiting", "from", "decline", "gifts_on", "gifts_off", "ending", "owner", "none", "none_hint", "route_needs_pact")) {
			keys.add("screen.aliveworkplace.pact." + k);
		}
		for (String k : List.of("declined", "declined_told", "lapsed", "notice", "ended", "gifts_on", "gifts_off", "route_needs_pact", "ally_riders_sent",
			"ally_riders_coming")) {
			keys.add("message.aliveworkplace.pact." + k);
		}
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "untranslated: " + key);
		}
		helper.succeed();
	}

	// ---- 27.8: the Steward's desk ------------------------------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation RULE = AliveWorkplace.id("test/qa_desk_well");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private record Desk(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, Villager builder) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	/** As in StewardDeskGameTests: flat grass, a hall, a Blueprint Table and its builder, a Steward, a Homes zone east. */
	private static Desk desk(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Desk(level, hall, owner, steward, builder);
	}

	private static StewardDesk.Proposal propose(GameTestHelper helper, Desk d) {
		return propose(helper, d, RULE);
	}

	private static StewardDesk.Proposal propose(GameTestHelper helper, Desk d, ResourceLocation rule) {
		Plots.Verdict verdict = Plots.check(d.level(), d.hall(), d.entity().plan(), "homes", WELL, helper.absolutePos(SPOT), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(verdict.plot().isPresent(), "setup: the spot doesn't fit: " + verdict);
		Plots.Plot plot = verdict.plot().get();
		StewardWishes.Wish wish = new StewardWishes.Wish(rule, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(3L));
		Optional<StewardDesk.Proposal> p = StewardDesk.offer(d.level(), d.hall(), wish, plot.blueprint(), plot.placement(), plot.zone(), false);
		helper.assertTrue(p.isPresent(), "setup: not proposed");
		return p.get();
	}

	/**
	 * In a protected village the owner's friend has the plan's rights at the desk and a stranger hasn't: the stranger's
	 * Approve (on the proposal's page) and Approve all start nothing; the friend's Approve all, shown once there are two
	 * proposals, starts the builds for the hall's owner; the stranger can't cancel them.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaDeskApproveAll"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaDeskApproveAll")
	public void approveAllWorksForAFriendAndNotForAStranger(GameTestHelper helper) {
		Desk d = desk(helper);
		d.entity().setProtected(true);
		StewardDesk.Proposal proposal = propose(helper, d);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		ServerPlayer friend = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = d.level().getServer();
		Friends.get(server).add(d.owner().getUUID(), friend.getUUID(), "Friend");
		Leftovers.after(helper, () -> Friends.get(server).remove(d.owner().getUUID(), friend.getUUID()));
		ChoiceMenu menu = VillageHallScreen.forTest(stranger, d.hall());
		StewardDeskPage.renderProposal(menu, d.level(), d.hall(), proposal.id());
		menu.press(StewardDeskPage.APPROVE, stranger);
		helper.assertTrue(StewardDesk.openSites(d.level(), d.hall()).isEmpty(), "a stranger's Approve started a build");
		helper.assertTrue(StewardDesk.approveAll(d.level(), d.hall(), stranger).isEmpty() && StewardDesk.openSites(d.level(), d.hall()).isEmpty(),
			"a stranger's Approve all started a build");
		propose(helper, d, AliveWorkplace.id("test/qa_desk_other"));
		ChoiceMenu theirs = VillageHallScreen.forTest(friend, d.hall());
		theirs.press(VillageHallScreen.ADVICE, friend);
		helper.assertTrue(theirs.icon(StewardDeskPage.APPROVE_ALL).is(Items.EMERALD_BLOCK), "no Approve all with two proposals: " + theirs.icon(StewardDeskPage.APPROVE_ALL));
		String name = theirs.icon(StewardDeskPage.APPROVE_ALL).getHoverName().getString();
		helper.assertTrue(!name.contains("aliveworkplace."), "Approve all is a raw key: " + name);
		theirs.press(StewardDeskPage.APPROVE_ALL, friend);
		List<BuildSite> open = StewardDesk.openSites(d.level(), d.hall());
		helper.assertTrue(!open.isEmpty(), "the friend's Approve all started nothing");
		helper.assertTrue(open.stream().anyMatch(site -> site.structure().equals(proposal.blueprint()) && site.placement().equals(proposal.placement())),
			"the first proposal's build wasn't started: " + open.stream().map(BuildSite::structure).toList());
		helper.assertTrue(open.stream().allMatch(site -> site.owner().equals(d.owner().getUUID())), "a build not owned by the hall's owner");
		helper.assertTrue(!StewardDesk.cancel(d.level(), d.hall(), stranger, open.get(0).id()) && StewardDesk.openSites(d.level(), d.hall()).size() == open.size(),
			"a stranger cancelled a build");
		helper.succeed();
	}

	/** Show me lights the proposal's outline for 30 seconds: still glowing a second before, gone just after. */
	//$ gametest_ticks_batch HUGE '700' '"qaDeskShowMe"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qaDeskShowMe")
	public void showMeGlowsForThirtySeconds(GameTestHelper helper) {
		Desk d = desk(helper);
		StewardDesk.Proposal proposal = propose(helper, d);
		ChoiceMenu menu = VillageHallScreen.forTest(d.owner(), d.hall());
		StewardDeskPage.renderProposal(menu, d.level(), d.hall(), proposal.id());
		menu.press(StewardDeskPage.SHOW, d.owner());
		helper.assertTrue(BlueprintOutline.glowing(d.owner()), "Show me didn't light the outline");
		helper.assertTrue(StewardDesk.SHOW_TICKS == 600, "Show me isn't 30 seconds: " + StewardDesk.SHOW_TICKS);
		helper.runAfterDelay(580, () -> helper.assertTrue(BlueprintOutline.glowing(d.owner()), "the outline went out before 30 seconds"));
		helper.runAfterDelay(610, () -> {
			helper.assertTrue(!BlueprintOutline.glowing(d.owner()), "the outline still glows after 30 seconds");
			helper.assertTrue(StewardDesk.of(d.level(), d.hall()).get(proposal.id()).isPresent() && StewardDesk.openSites(d.level(), d.hall()).isEmpty(),
				"Show me answered the proposal");
			helper.succeed();
		});
	}

	// ---- 27.9: jobs --------------------------------------------------------------------------------------------------

	/**
	 * Only jobless villagers get jobs: a mason is left alone; and a free block goes to one villager only: two jobless
	 * and one free grindstone make one job, and the other villager's job asks for a building instead.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaJobsOneBlock"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaJobsOneBlock")
	public void aFreeBlockGoesToOneJoblessVillagerOnly(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		try {
			VillageHalls.RADIUS = 14;
			Leftovers.clear(helper);
			ServerLevel level = helper.getLevel();
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
					helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
					for (int y = 2; y < 8; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
					}
				}
			}
			BlockPos hallRel = new BlockPos(15, 2, 15);
			helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
			BlockPos grindstone = new BlockPos(11, 2, 13);
			helper.setBlock(grindstone, Blocks.GRINDSTONE);
			Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 13));
			mason.setNoAi(true);
			mason.setVillagerData(mason.getVillagerData().setProfession(VillagerProfession.MASON).setLevel(2));
			Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 15));
			a.setNoAi(true);
			Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 15));
			b.setNoAi(true);
			StewardJobs.Plan plan = StewardJobs.plan(level, helper.absolutePos(hallRel));
			helper.assertTrue(plan.jobs().stream().noneMatch(j -> j.villager().equals(mason.getUUID())), "the mason was given another job: " + plan.jobs());
			helper.assertTrue(plan.jobs().size() == 1 && plan.jobs().get(0).station().equals(helper.absolutePos(grindstone)), "one block, jobs: " + plan.jobs());
			helper.assertTrue(!plan.wanted().isEmpty(), "the job left without a block proposed no building: " + plan.wanted());
			List<StewardJobs.Job> given = StewardJobs.give(level, null, plan.jobs());
			helper.assertTrue(given.size() == 1 && mason.getVillagerData().getProfession() == VillagerProfession.MASON, "given: " + given);
			boolean aGot = a.getVillagerData().getProfession() != VillagerProfession.NONE;
			boolean bGot = b.getVillagerData().getProfession() != VillagerProfession.NONE;
			helper.assertTrue(aGot != bGot, "jobs given to both or neither: " + a.getVillagerData().getProfession() + ", " + b.getVillagerData().getProfession());
			helper.assertTrue(StewardJobs.plan(level, helper.absolutePos(hallRel)).jobs().isEmpty(), "the taken grindstone was offered again");
		} finally {
			VillageHalls.RADIUS = before;
		}
		helper.succeed();
	}
}
