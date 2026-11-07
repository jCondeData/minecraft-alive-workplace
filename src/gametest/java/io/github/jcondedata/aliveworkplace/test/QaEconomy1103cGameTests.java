package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane c, 2026-11-03), written from docs/wiki/board-trade.md (33.5), price-board.md (33.2-33.4) and feuds.md
 * (33.20), through the player's own entry point (the hall's screen, its minecart, the Prices tab, a click), aimed at
 * what the feature classes leave out:
 * <ul>
 * <li>a feud's 25% applied once on a shift-click of several bundles (not compounded), settled once, the price moving
 * by the board's own price; a rival's friend is a rival too; feuds off puts the price back;</li>
 * <li>the refusal lines a rival reads say the feud price ("You can't pay 2.2 emeralds"), and a feud that takes a sale
 * under one emerald is refused, not given away;</li>
 * <li>rounding: a 0.55-emerald bundle still costs a whole emerald, a shift-click is charged up once;</li>
 * <li>the treasury cap on what a purchase pays in; the treasury switch off; the economy switched off with the page
 * open; the chest emptied and the price changed with the page open;</li>
 * <li>a huge shift-click (a full inventory of logs) stopping at the chest's room, paying exactly the sum of the 2%
 * steps rounded down;</li>
 * <li>every line {@link Board#message} can say, rendered with real arguments, translated.</li>
 * </ul>
 */
public class QaEconomy1103cGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** The hall's screen, its minecart, then the Prices tab: the way a player gets to the board. */
	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		helper.assertTrue(menu.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD), "no Prices tab: " + menu.icon(TradePage.Tab.PRICES.slot()));
		menu.press(TradePage.Tab.PRICES.slot(), player);
		return menu;
	}

	private static void shiftClick(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
	}

	private static void rightClick(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 1, ClickType.PICKUP, player);
	}

	private static Market.Price price(GameTestHelper helper, BlockPos hall) {
		return Caravans.Data.get(helper.getLevel()).market(hall).prices().get(TIMBER);
	}

	private static String first(ChoiceMenu menu, int slot) {
		List<String> lore = BoardTradeGameTests.lore(menu.icon(slot));
		return lore.isEmpty() ? "" : lore.get(0);
	}

	private static int logs(ServerPlayer player) {
		return BoardTradeGameTests.has(player, Items.OAK_LOG);
	}

	private static int emeralds(ServerPlayer player) {
		return BoardTradeGameTests.has(player, Items.EMERALD);
	}

	/** The spec's 2% step a bundle sold (a cent at least, never under half the base). */
	private static int down(int base, int price) {
		return Math.max(base / 2, price - Math.max(1, (int) Math.round(price * 0.02)));
	}

	/** A plain board in a big area: hall, Storehouse and chest, emerald money, the test goods; alone in its batch. */
	private static BlockPos plain(GameTestHelper helper) {
		return BoardTradeGameTests.hall(helper, new BlockPos(9, 2, 9), true);
	}

	/**
	 * Two villages of two owners (village 0: {@code land.owner()}'s, who declares a feud on village 1, owned by a
	 * neighbour); village 1 has a Storehouse and chest; feuds on and empty; put back after.
	 */
	private record Feud(Land land, BlockPos hall, ServerPlayer rival, ServerPlayer neutral) {
	}

	private static Feud feud(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean pacts = Pacts.ENABLED;
		boolean feuds = Feuds.ENABLED;
		Pacts.ENABLED = true;
		Feuds.ENABLED = true;
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Feuds.ENABLED = feuds;
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			Caravans.Data data = Caravans.Data.get(land.level());
			data.remove(land.hall(0));
			data.remove(land.hall(1));
		});
		ServerPlayer them = RealmGameTests.listener(helper, new ArrayList<>(), "neighbour");
		land.entity(1).setOwner(them.getUUID(), "neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		BlockPos hall = land.hall(1);
		// the hall's reach is wider than the area: Storehouses an earlier test left here would count too
		net.minecraft.world.entity.ai.village.poi.PoiManager pois = land.level().getPoiManager();
		pois.findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI), p -> true, hall,
				VillageHalls.RADIUS, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList().forEach(p -> {
				land.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				if (pois.getType(p).isPresent()) pois.remove(p);
			});
		helper.setBlock(helper.relativePos(hall).offset(4, 0, 0), ModBlocks.STOREHOUSE);
		helper.setBlock(helper.relativePos(hall).offset(4, 0, 2), Blocks.CHEST);
		// The Storehouse's reach goes past the area too: a chest an earlier batch left just outside it would be counted as
		// a second Storehouse chest (failed 1 of 10 in the nightly repeats, 2026-11-04), so only ours stays.
		BlockPos ours = helper.absolutePos(helper.relativePos(hall).offset(4, 0, 2));
		Caravans.storehouse(land.level(), hall).stream().filter(p -> !p.equals(ours))
			.forEach(p -> {
				if (land.level().getBlockEntity(p) instanceof Container c) c.clearContent(); // nothing spilled into the next test
				land.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			});
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "couldn't declare the feud");
		ServerPlayer neutral = RealmGameTests.listener(helper, new ArrayList<>(), "passer-by");
		return new Feud(land, hall, land.owner(), neutral);
	}

	private static Container feudChest(GameTestHelper helper, Feud f) {
		List<BlockPos> chests = Caravans.storehouse(f.land().level(), f.hall());
		helper.assertTrue(chests.size() == 1, "setup: the Storehouse's chests: " + chests);
		return (Container) f.land().level().getBlockEntity(chests.get(0));
	}

	// ---- feuds at the board ------------------------------------------------------------------------------------------

	/**
	 * A rival shift-selling three bundles is paid 75% of each bundle's price (150 + 147 + 144 = 4.41, paid 4), not 75%
	 * compounded; the treasury pays what was paid; the price moves by the board's own price (2.00, 1.96, 1.92, 1.88), as
	 * for anyone. A neutral player's same sale pays 5.88, so 5.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEco1103cFeudSell"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEco1103cFeudSell")
	public void aRivalsShiftSaleIsPaidAQuarterLessOnceForEveryBundle(GameTestHelper helper) {
		Feud f = feud(helper);
		helper.runAfterDelay(2, () -> {
			Container chest = feudChest(helper, f);
			VillageHallBlockEntity entity = f.land().entity(1);
			entity.setTreasury(5000);
			BoardTradeGameTests.price(helper, f.hall(), TIMBER, 200);
			f.rival().getInventory().add(new ItemStack(Items.OAK_LOG, 48));
			ChoiceMenu menu = board(helper, f.rival(), f.hall());
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			shiftClick(menu, timber, f.rival());
			helper.assertTrue(logs(f.rival()) == 0 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 48,
				"the rival kept " + logs(f.rival()) + " logs; the chest got " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(emeralds(f.rival()) == 4 && entity.treasury() == 4600,
				"the rival was paid " + emeralds(f.rival()) + " (wanted 4: 1.5 + 1.47 + 1.44), treasury " + entity.treasury());
			helper.assertTrue(price(helper, f.hall()).cents() == 188 && price(helper, f.hall()).nudge() == -3,
				"the price after the rival's sale: " + price(helper, f.hall()) + " (wanted 188, three steps from 2.00)");

			BoardTradeGameTests.price(helper, f.hall(), TIMBER, 200);
			f.neutral().getInventory().add(new ItemStack(Items.OAK_LOG, 48));
			ChoiceMenu theirs = board(helper, f.neutral(), f.hall());
			shiftClick(theirs, timber, f.neutral());
			helper.assertTrue(emeralds(f.neutral()) == 5 && entity.treasury() == 4100,
				"the neutral player was paid " + emeralds(f.neutral()) + " (wanted 5 for 5.88), treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * A rival shift-buying three bundles is charged 125% of each "We sell" (1.38 + 1.40 + 1.43 = 4.21), settled once: 5
	 * emeralds, all of them to the treasury; the price moves 2% a bundle from the board's own 1.00.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEco1103cFeudBuy"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEco1103cFeudBuy")
	public void aRivalsShiftPurchaseIsChargedAQuarterMoreSettledOnce(GameTestHelper helper) {
		Feud f = feud(helper);
		helper.runAfterDelay(2, () -> {
			Container chest = feudChest(helper, f);
			VillageHallBlockEntity entity = f.land().entity(1);
			entity.setTreasury(0);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			BoardTradeGameTests.price(helper, f.hall(), TIMBER, 100);
			f.rival().getInventory().add(new ItemStack(Items.EMERALD, 20));
			ChoiceMenu menu = board(helper, f.rival(), f.hall());
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			shiftClick(menu, timber, f.rival());
			helper.assertTrue(logs(f.rival()) == 48 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 16,
				"the rival got " + logs(f.rival()) + " logs; the chest kept " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(emeralds(f.rival()) == 15 && entity.treasury() == 500,
				"the rival paid " + (20 - emeralds(f.rival())) + " (wanted 5 for 4.21), treasury " + entity.treasury());
			helper.assertTrue(price(helper, f.hall()).cents() == 106 && price(helper, f.hall()).nudge() == 3, "the price after: " + price(helper, f.hall()));
			helper.assertTrue(first(menu, timber).endsWith("for 5 emeralds. It has no more to spare.") || first(menu, timber).endsWith("for 5 emeralds."),
				"what the tooltip says: " + first(menu, timber));
			helper.succeed();
		});
	}

	/**
	 * A rival who can't pay is told the price they'd be charged (2.2 emeralds, not the board's 1.76); a feud that takes a
	 * 1.3-emerald bundle under one emerald (0.98) refuses the sale with the hint and keeps the logs with the rival;
	 * shift-selling two pays the one whole emerald they come to.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEco1103cFeudRefused"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEco1103cFeudRefused")
	public void aRivalIsRefusedAtTheFeudPriceAndNeverSellsForNothing(GameTestHelper helper) {
		Feud f = feud(helper);
		helper.runAfterDelay(2, () -> {
			Container chest = feudChest(helper, f);
			VillageHallBlockEntity entity = f.land().entity(1);
			entity.setTreasury(5000);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 32));
			BoardTradeGameTests.price(helper, f.hall(), TIMBER, 160);
			f.rival().getInventory().add(new ItemStack(Items.EMERALD, 2));
			ChoiceMenu menu = board(helper, f.rival(), f.hall());
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, f.rival());
			helper.assertTrue(emeralds(f.rival()) == 2 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 32 && entity.treasury() == 5000,
				"a rival with 2 emeralds bought a 2.2-emerald bundle: " + emeralds(f.rival()) + " emeralds, chest " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(first(menu, timber).equals("You can't pay 2.2 emeralds for a bundle of Test Timber."), "the refusal: " + first(menu, timber));

			// selling at 1.3: the feud's 75% is 0.98, under a whole emerald
			chest.clearContent();
			BoardTradeGameTests.price(helper, f.hall(), TIMBER, 130);
			f.rival().getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			menu = board(helper, f.rival(), f.hall());
			menu.press(timber, f.rival());
			helper.assertTrue(logs(f.rival()) == 16 && emeralds(f.rival()) == 2 && entity.treasury() == 5000 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 0,
				"a 0.98-emerald sale was made: the rival has " + logs(f.rival()) + " logs, " + emeralds(f.rival()) + " emeralds, treasury " + entity.treasury());
			helper.assertTrue(first(menu, timber).equals("A bundle of Test Timber comes to less than a whole emerald here: shift-click to sell several at once."),
				"the refusal: " + first(menu, timber));
			helper.assertTrue(price(helper, f.hall()).cents() == 130, "a refused sale moved the price: " + price(helper, f.hall()));

			// two bundles: 0.98 + 0.95 = 1.93, paid 1
			f.rival().getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			shiftClick(menu, timber, f.rival());
			helper.assertTrue(logs(f.rival()) == 0 && emeralds(f.rival()) == 3 && entity.treasury() == 4900,
				"two bundles at 0.98 and 0.95: the rival has " + logs(f.rival()) + " logs, " + emeralds(f.rival()) + " emeralds, treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * The rival owner's friend gets the rival's prices (1.6 becomes 2.2 with the spread and the feud: 3 emeralds, where a
	 * neutral buyer pays 2); with {@code feuds} switched off the feud ends quietly and the friend pays the board's price.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEco1103cFeudFriend"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEco1103cFeudFriend")
	public void aRivalsFriendPaysTheRivalsPriceUntilFeudsAreOff(GameTestHelper helper) {
		Feud f = feud(helper);
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "rivals-friend");
		Friends friends = Friends.get(f.land().level().getServer());
		friends.add(f.rival().getUUID(), friend.getUUID(), "rivals-friend");
		Leftovers.after(helper, () -> friends.remove(f.rival().getUUID(), friend.getUUID()));
		helper.runAfterDelay(2, () -> {
			Container chest = feudChest(helper, f);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 48));
			BoardTradeGameTests.price(helper, f.hall(), TIMBER, 160);
			friend.getInventory().add(new ItemStack(Items.EMERALD, 10));
			ChoiceMenu menu = board(helper, friend, f.hall());
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, friend);
			helper.assertTrue(logs(friend) == 16 && emeralds(friend) == 7, "the rival's friend paid " + (10 - emeralds(friend)) + " for a 1.76 bundle (wanted 3: 2.2 with the feud)");

			Feuds.ENABLED = false;
			BoardTradeGameTests.price(helper, f.hall(), TIMBER, 160);
			menu = board(helper, friend, f.hall());
			rightClick(menu, timber, friend);
			helper.assertTrue(logs(friend) == 32 && emeralds(friend) == 5, "with feuds off the friend paid " + (7 - emeralds(friend)) + " (wanted 2 for 1.76)");
			helper.succeed();
		});
	}

	// ---- rounding, the treasury and its switch ---------------------------------------------------------------------

	/**
	 * At its floor (0.5 emerald) a bundle sells for 0.55 and still costs a whole emerald; a shift-click of the two left
	 * (0.56 + 0.57 = 1.13) is charged up once, to 2; the treasury gets every emerald paid.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cCheap"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cCheap")
	public void aCheapBundleCostsAWholeEmeraldAndAShiftClickIsChargedUpOnce(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 50);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(0);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			player.getInventory().add(new ItemStack(Items.EMERALD, 5));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("We sell: 0.55 emeralds"), "the tooltip: " + BoardTradeGameTests.lore(menu.icon(timber)));
			menu.press(timber, player);
			helper.assertTrue(logs(player) == 16 && emeralds(player) == 4 && entity.treasury() == 100,
				"a 0.55 bundle: " + logs(player) + " logs, paid " + (5 - emeralds(player)) + ", treasury " + entity.treasury());
			helper.assertTrue(price(helper, hall).cents() == 51, "the price after a bundle bought at 0.5: " + price(helper, hall));
			// carrying a bundle now, so a right click buys; shift + right is a shift-click here: use the shift-click with
			// the logs set aside
			player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.OAK_LOG), 64, player.inventoryMenu.getCraftSlots());
			shiftClick(menu, timber, player);
			helper.assertTrue(logs(player) == 32 && emeralds(player) == 2 && entity.treasury() == 300,
				"two bundles at 0.56 and 0.57: " + logs(player) + " logs, paid " + (4 - emeralds(player)) + " (wanted 2), treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/** What a purchase pays in stops at the treasury's cap: the buyer pays 2, the treasury tops out at its cap. */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cCap"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cCap")
	public void aPurchaseNeverFillsTheTreasuryPastItsCap(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			int cap = Treasury.cap(entity) * 100;
			entity.setTreasury(cap - 50);
			BoardTradeGameTests.chest(helper).setItem(0, new ItemStack(Items.OAK_LOG, 32));
			player.getInventory().add(new ItemStack(Items.EMERALD, 5));
			ChoiceMenu menu = board(helper, player, hall);
			menu.press(BoardTradeGameTests.slot(helper, menu, "Test Timber"), player);
			helper.assertTrue(logs(player) == 16 && emeralds(player) == 3, "the buyer got " + logs(player) + " logs and paid " + (5 - emeralds(player)));
			helper.assertTrue(entity.treasury() == cap, "the treasury holds " + entity.treasury() + " cents, its cap is " + cap);
			helper.succeed();
		});
	}

	/**
	 * With {@code villageTreasury} off the village puts by no takings, but the board still pays from and into the
	 * treasury: a 1.5 sale pays 1 out of it, a 1.62 purchase puts 2 in.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cTreasuryOff"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cTreasuryOff")
	public void withTheTreasurySwitchOffTheBoardStillPaysFromAndIntoIt(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		boolean was = Treasury.ENABLED;
		Treasury.ENABLED = false;
		Leftovers.after(helper, () -> Treasury.ENABLED = was);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(logs(player) == 0 && emeralds(player) == 1 && entity.treasury() == 900,
				"the sale with the treasury off: " + logs(player) + " logs, " + emeralds(player) + " emeralds, treasury " + entity.treasury());
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(1, new ItemStack(Items.OAK_LOG, 32));
			player.getInventory().add(new ItemStack(Items.EMERALD, 4));
			menu.press(timber, player);
			// 1.47 now: sells at 1.62, charged 2
			helper.assertTrue(logs(player) == 16 && emeralds(player) == 3 && entity.treasury() == 1100,
				"the purchase with the treasury off: " + logs(player) + " logs, " + emeralds(player) + " emeralds, treasury " + entity.treasury());
			helper.succeed();
		});
	}

	// ---- things changing under an open page --------------------------------------------------------------------------

	/** {@code villageEconomy} switched off while the page is open: a click sells or buys nothing, and says so. */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cEconomyOff"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cEconomyOff")
	public void theEconomySwitchedOffWithThePageOpenTradesNothing(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		boolean was = Economy.ENABLED;
		Leftovers.after(helper, () -> Economy.ENABLED = was);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 48));
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			player.getInventory().add(new ItemStack(Items.EMERALD, 5));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			Economy.ENABLED = false;
			menu.press(timber, player);
			shiftClick(menu, timber, player);
			rightClick(menu, timber, player);
			helper.assertTrue(logs(player) == 16 && emeralds(player) == 5 && entity.treasury() == 1000 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 48,
				"traded with the economy off: " + logs(player) + " logs, " + emeralds(player) + " emeralds, treasury " + entity.treasury()
					+ ", chest " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(price(helper, hall).cents() == 150 && price(helper, hall).nudge() == 0, "the price moved with the economy off: " + price(helper, hall));
			helper.succeed();
		});
	}

	/**
	 * The chest emptied after the page opened (it said a bundle to spare): the click takes nothing and charges nothing,
	 * and says the village has none to spare; the price stays.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cEmptied"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cEmptied")
	public void aChestEmptiedWithThePageOpenSellsNothingAndChargesNothing(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(0);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 32));
			player.getInventory().add(new ItemStack(Items.EMERALD, 5));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("Bundles to spare in the Storehouse: 1"), "the tooltip before: " + BoardTradeGameTests.lore(menu.icon(timber)));
			chest.clearContent();
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 20));
			shiftClick(menu, timber, player);
			helper.assertTrue(logs(player) == 0 && emeralds(player) == 5 && entity.treasury() == 0 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 20,
				"after the chest was emptied: " + logs(player) + " logs, " + emeralds(player) + " emeralds, treasury " + entity.treasury());
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			helper.assertTrue(first(menu, timber).equals(village + " has no bundle of Test Timber to spare: its Storehouse keeps 16 of everything back."),
				"what it said: " + first(menu, timber));
			helper.assertTrue(price(helper, hall).cents() == 100 && price(helper, hall).nudge() == 0, "the price moved: " + price(helper, hall));
			helper.succeed();
		});
	}

	/**
	 * The price changed (the dawn count) after the page opened at 1.00: a sale is paid at the new 2.00, not the stale
	 * tooltip's, and the tooltip then reads the new price less a step.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cRepriced"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cRepriced")
	public void aPriceChangedWithThePageOpenTradesAtTheNewPrice(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("We pay: 1 emerald"), "the tooltip before: " + BoardTradeGameTests.lore(menu.icon(timber)));
			BoardTradeGameTests.price(helper, hall, TIMBER, 200);
			menu.press(timber, player);
			helper.assertTrue(logs(player) == 0 && emeralds(player) == 2 && entity.treasury() == 800,
				"paid " + emeralds(player) + " at the new 2.00 (wanted 2), treasury " + entity.treasury());
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("We pay: 1.96 emeralds"), "the tooltip after: " + BoardTradeGameTests.lore(menu.icon(timber)));
			helper.succeed();
		});
	}

	// ---- a huge shift-click ----------------------------------------------------------------------------------------

	/**
	 * A full inventory of logs (36 × 64 = 144 bundles) shift-sold: the village takes only what its one chest has room
	 * for (or its treasury can pay), pays exactly the sum of the 2% steps rounded down, the treasury goes down by exactly
	 * what was paid and never below 0, the price stops at half its base, and the line says why it stopped.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cHuge"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cHuge")
	public void aHugeShiftSaleStopsAtTheChestOrTreasuryAndPaysExactly(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			int treasury = Treasury.cap(entity) * 100;
			entity.setTreasury(treasury);
			for (int i = 0; i < 36; i++) {
				player.getInventory().setItem(i, new ItemStack(Items.OAK_LOG, 64));
			}
			Container chest = BoardTradeGameTests.chest(helper);
			int room = chest.getContainerSize() * 64 / 16;
			// the spec's numbers: each bundle at the price the one before left, while the treasury can pay and the chest has room
			int bundles = 0;
			long owed = 0;
			int p = 100;
			boolean byTreasury = false;
			while (bundles < 144) {
				if (bundles >= room) {
					break;
				}
				if (owed + p > treasury) {
					byTreasury = true;
					break;
				}
				owed += p;
				bundles++;
				p = down(100, p);
			}
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			shiftClick(menu, timber, player);
			int paid = (int) (owed / 100);
			helper.assertTrue(logs(player) == 36 * 64 - bundles * 16 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == bundles * 16,
				"wanted " + bundles + " bundles sold; the player kept " + logs(player) + ", the chest got " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(emeralds(player) == paid && entity.treasury() == treasury - paid * 100 && entity.treasury() >= 0,
				"paid " + emeralds(player) + " (wanted " + paid + " for " + owed + " cents), treasury " + entity.treasury() + " of " + treasury);
			helper.assertTrue(price(helper, hall).cents() == p && price(helper, hall).nudge() == -bundles && p >= 50,
				"the price after: " + price(helper, hall) + " (wanted " + p + ", nudge " + (-bundles) + ")");
			String why = byTreasury ? "Its treasury can't pay for more." : "Its Storehouse has no room for more.";
			helper.assertTrue(first(menu, timber).endsWith(why), "the line: " + first(menu, timber) + " (wanted it to end " + why + ")");
			helper.succeed();
		});
	}

	// ---- every line ------------------------------------------------------------------------------------------------

	/**
	 * Every line a click can say (each refusal, and a trade with each reason it stopped), rendered with real arguments:
	 * translated, every argument filled in, and the money read the board's way.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaEco1103cLines"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaEco1103cLines")
	public void everyLineAClickCanSayIsTranslatedAndFilledIn(GameTestHelper helper) {
		BlockPos hall = plain(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			BoardTradeGameTests.entity(helper, hall).setTreasury(340);
			TradeGoods.Good good = TradeGoods.all().stream().filter(g -> g.id().equals(TIMBER)).findFirst().orElseThrow();
			String village = VillageHalls.name(level, hall).getString();
			List<String> seen = new ArrayList<>();
			for (Board.Stop stop : Board.Stop.values()) {
				for (boolean sale : new boolean[]{true, false}) {
					seen.add(Board.message(level, hall, new Board.Result(sale, good, 0, 0, 0, stop), player).getString());
					seen.add(Board.message(level, hall, new Board.Result(sale, good, 2, 32, 300, stop), player).getString());
				}
			}
			for (String line : seen) {
				helper.assertTrue(!line.contains("aliveworkplace.") && !line.contains("%") && !line.isBlank(), "an untranslated or unfilled line: " + line);
			}
			helper.assertTrue(seen.contains("You sell 32 × Test Timber to " + village + " for 3 emeralds. Its Storehouse has no room for more."), "the sale line: " + seen);
			helper.assertTrue(seen.contains("You buy 32 × Test Timber from " + village + " for 3 emeralds. You can't carry more."), "the purchase line: " + seen);
			helper.assertTrue(seen.contains(village + "'s treasury can't pay for a bundle of Test Timber: it holds 3.4 emeralds."), "the treasury line: " + seen);
			helper.assertTrue(seen.contains("You don't carry a bundle of Test Timber (16)."), "the no-goods line: " + seen);
			helper.assertTrue(seen.contains("You can't pay 1.1 emeralds for a bundle of Test Timber."), "the money line: " + seen);
			helper.assertTrue(seen.contains("The market needs a Storehouse: " + village + " has none with a chest yet."), "the Storehouse line: " + seen);
			helper.succeed();
		});
	}
}
