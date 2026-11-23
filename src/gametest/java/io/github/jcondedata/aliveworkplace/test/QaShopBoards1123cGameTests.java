package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1123-0653): the village shops' boards, from docs/wiki/price-board.md and docs/wiki/board-trade.md, on what
 * {@code BoardTradeGameTests}, {@code QaTradeBoardGameTests}, {@code QaPriceBoard1117cGameTests} and
 * {@code ProtectionSpecGameTests} leave unchecked: earnings stopping at the treasury's cap, a shift-click stopped by
 * what the buyer can pay, a good with no price yet, the bundle's edge in the player's pack, several chests by the
 * Storehouse (the 16 kept back of every item across them), the Storehouse broken with the page open, the economy
 * switched off with the page open, a page left open while another player trades, the treasury switch off, a save and
 * reload of the hall after trading, protection switched off and a sneaking stranger, and every sentence the board says.
 * Uses the gametest data pack's goods (Test Timber: logs, 16 a bundle, base 1 emerald; Test Grain: wheat, 20 a bundle,
 * base 1 emerald).
 */
public class QaShopBoards1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final BlockPos SECOND_CHEST = new BlockPos(13, 2, 7);
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;
	private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_grain");
	private static final String NEEDS = "The market needs a Storehouse with a chest";

	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		helper.assertTrue(menu.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD), "no Prices tab: " + menu.icon(TradePage.Tab.PRICES.slot()));
		menu.press(TradePage.Tab.PRICES.slot(), player);
		return menu;
	}

	private static String said(ChoiceMenu menu, int slot) {
		List<String> lore = BoardTradeGameTests.lore(menu.icon(slot));
		return lore.isEmpty() ? "" : lore.get(0);
	}

	private static Market.Price price(GameTestHelper helper, BlockPos hall, ResourceLocation good) {
		return Caravans.Data.get(helper.getLevel()).market(hall).prices().get(good);
	}

	private static int has(ServerPlayer p, net.minecraft.world.item.Item item) {
		return BoardTradeGameTests.has(p, item);
	}

	private static int count(Container c, net.minecraft.world.item.Item item) {
		return BoardTradeGameTests.count(c, item);
	}

	private static void restoreConfigAfter(GameTestHelper helper) {
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
	}

	/**
	 * board-trade.md: buying, "you pay 'We sell' ... into the treasury (up to the treasury's cap; what would go over it
	 * is lost)". A treasury half an emerald under its cap takes a 2-emerald purchase and stands at its cap, not over.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopCap")
	public void aPurchaseFillsTheTreasuryOnlyUpToItsCap(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			int cap = Treasury.cap(entity) * 100;
			entity.setTreasury(cap - 50);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 32));
			player.getInventory().add(new ItemStack(Items.EMERALD, 5));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 3, "the purchase: " + has(player, Items.OAK_LOG) + " logs, "
				+ has(player, Items.EMERALD) + " emeralds left");
			helper.assertTrue(entity.treasury() == cap, "the treasury past its cap of " + cap + ": " + entity.treasury());
			helper.assertTrue(menu.icon(TradePage.TREASURY).getHoverName().getString().equals("Treasury: " + (cap / 100) + " emeralds"),
				"the nugget: " + menu.icon(TradePage.TREASURY).getHoverName().getString());
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: "A buyer is stopped by ... what they can pay"; a shift-click "is settled once for all its bundles"
	 * and charged up to the whole emerald. 1.10 + 1.12 = 2.22 (3 emeralds) goes; a third (1.14, 3.36 in all) doesn't
	 * with 3 emeralds, and the line says why.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopShiftMoney"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopShiftMoney")
	public void aShiftClickBuysOnlyWhatThePlayerCanPayFor(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64)); // three bundles to spare
			player.getInventory().add(new ItemStack(Items.EMERALD, 3));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 32 && count(chest, Items.OAK_LOG) == 32, "the shift-click bought " + has(player, Items.OAK_LOG) + " logs");
			helper.assertTrue(has(player, Items.EMERALD) == 0 && entity.treasury() == 300, "paid: " + has(player, Items.EMERALD) + " left, treasury " + entity.treasury());
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 104 && price(helper, hall, TIMBER).nudge() == 2, "the price: " + price(helper, hall, TIMBER));
			helper.assertTrue(said(menu, timber).equals("You buy 32 × Test Timber from " + village + " for 3 emeralds. You can't pay for more."), "what it said: " + said(menu, timber));
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: "A good that has no price yet trades at its base price"; and a click sells only with a whole
	 * bundle in the pack: 19 wheat (one short of Test Grain's 20) is a purchase, 20 a sale.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopNoPrice"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopNoPrice")
	public void aGoodWithNoPriceYetTradesAtItsBaseAndABundleIsWhole(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150); // listed
			// today's market with a price for Timber alone: Grain has none yet
			Caravans.Data.get(helper.getLevel()).setMarket(hall, new Market(List.of(), List.of(),
				new java.util.LinkedHashMap<>(java.util.Map.of(TIMBER, new Market.Price(150, 150, 0))), io.github.jcondedata.aliveworkplace.hall.Chronicle.day(helper.getLevel()), List.of()));
			helper.assertTrue(price(helper, hall, GRAIN) == null, "Grain already has a price: " + price(helper, hall, GRAIN));
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.WHEAT, 19));
			ChoiceMenu menu = board(helper, player, hall);
			int grain = BoardTradeGameTests.slot(helper, menu, "Test Grain");
			List<String> tip = BoardTradeGameTests.lore(menu.icon(grain));
			helper.assertTrue(tip.contains("We pay: 1 emerald") && tip.contains("We sell: 1.1 emeralds"), "Grain without a price: " + tip);
			helper.assertTrue(tip.contains("Click: buy a bundle. Shift-click: all you can"), "19 wheat isn't a bundle: " + tip);

			player.getInventory().add(new ItemStack(Items.WHEAT, 1));
			menu = board(helper, player, hall);
			tip = BoardTradeGameTests.lore(menu.icon(grain));
			helper.assertTrue(tip.contains("Click: sell a bundle (you carry 20). Shift-click: all it will take"), "20 wheat is a bundle: " + tip);
			menu.press(grain, player);
			helper.assertTrue(has(player, Items.WHEAT) == 0 && count(BoardTradeGameTests.chest(helper), Items.WHEAT) == 20, "the sale: the player kept "
				+ has(player, Items.WHEAT));
			helper.assertTrue(has(player, Items.EMERALD) == 1 && entity.treasury() == 900, "paid " + has(player, Items.EMERALD) + ", treasury " + entity.treasury());
			helper.assertTrue(said(menu, grain).equals("You sell 20 × Test Grain to " + village + " for 1 emerald."), "what it said: " + said(menu, grain));
			Market.Price after = price(helper, hall, GRAIN);
			helper.assertTrue(after != null && after.cents() == 98 && after.nudge() == -1, "Grain after a sale at its base: " + after);
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: a sale goes "into the Storehouses' chests" and a purchase comes "out of the Storehouses' chests";
	 * "the chests keep 16 of every item". With one chest full, a sale fills the other; with 20 oak logs in each (40),
	 * the chests spare one bundle (40 - 16 = 24) and keep 16 between them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopTwoChests"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopTwoChests")
	public void twoChestsByTheStorehouseTradeAsOne(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		helper.setBlock(SECOND_CHEST, Blocks.CHEST);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			Container first = BoardTradeGameTests.chest(helper);
			Container second = helper.getBlockEntity(SECOND_CHEST);
			for (int i = 0; i < first.getContainerSize(); i++) {
				first.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && count(second, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 1 && entity.treasury() == 900,
				"a sale with one chest full: the second holds " + count(second, Items.OAK_LOG) + ", said " + said(menu, timber));

			first.clearContent();
			second.clearContent();
			first.setItem(0, new ItemStack(Items.OAK_LOG, 20));
			second.setItem(0, new ItemStack(Items.OAK_LOG, 20));
			player.getInventory().add(new ItemStack(Items.EMERALD, 5));
			menu = board(helper, player, hall);
			List<String> tip = BoardTradeGameTests.lore(menu.icon(timber));
			helper.assertTrue(tip.contains("Bundles to spare in the Storehouse: 1"), "20 + 20 logs in two chests: " + tip);
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && count(first, Items.OAK_LOG) + count(second, Items.OAK_LOG) == 24,
				"the purchase from two chests: the player got " + has(player, Items.OAK_LOG) + ", the chests hold " + count(first, Items.OAK_LOG) + " + "
					+ count(second, Items.OAK_LOG) + "; said " + said(menu, timber));
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: without a Storehouse "a click says" the market needs one. The Storehouse block broken while the
	 * page is open (its chest still standing): nothing moves and the page says so; built again, the board trades.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopStorehouseBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopStorehouseBroken")
	public void theStorehouseBrokenWithThePageOpenStopsTrading(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			helper.assertTrue(!BoardTradeGameTests.lore(menu.icon(timber)).contains(NEEDS), "it asks for a Storehouse it has");
			helper.setBlock(BoardTradeGameTests.STOREHOUSE, Blocks.AIR);
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 0 && entity.treasury() == 1000
				&& count(BoardTradeGameTests.chest(helper), Items.OAK_LOG) == 0, "a trade with the Storehouse broken");
			helper.assertTrue(said(menu, timber).equals("The market needs a Storehouse: " + village + " has none with a chest yet."), "what it said: " + said(menu, timber));
			helper.setBlock(BoardTradeGameTests.STOREHOUSE, ModBlocks.STOREHOUSE);
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && has(player, Items.EMERALD) == 1 && entity.treasury() == 900, "no trade with the Storehouse built again: "
				+ said(menu, timber));
			helper.succeed();
		});
	}

	/**
	 * price-board.md / board-trade.md: `villageEconomy` off means "no Trade page, so no trading". Switched off while a
	 * player has the page open, a click (plain, right or shift) moves nothing; switched back on, the same page trades.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopEconomyOffOpen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopEconomyOffOpen")
	public void theEconomySwitchedOffWithThePageOpenTradesNothing(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		restoreConfigAfter(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 48));
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			player.getInventory().add(new ItemStack(Items.EMERALD, 10));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			WorkplaceConfig.parse("{\"villageEconomy\": false}").apply();
			VillageHalls.RADIUS = 8;
			menu.press(timber, player);
			menu.clicked(timber, 1, ClickType.PICKUP, player);
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 10 && entity.treasury() == 1000 && count(chest, Items.OAK_LOG) == 48
				&& price(helper, hall, TIMBER).cents() == 150, "traded with the economy off: " + has(player, Items.OAK_LOG) + " logs, " + has(player, Items.EMERALD)
					+ " emeralds, treasury " + entity.treasury() + ", price " + price(helper, hall, TIMBER));
			new WorkplaceConfig().apply();
			VillageHalls.RADIUS = 8;
			menu = board(helper, player, hall); // the page a click re-drew while it was off is the old routes page
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && has(player, Items.EMERALD) == 11 && entity.treasury() == 900, "no trade with the economy back on: "
				+ said(menu, timber));
			helper.succeed();
		});
	}

	/**
	 * board-trade.md: "A shift-click pays each bundle at the price the one before left", and each bundle moves "that
	 * day's price". A page opened before another player traded still trades at today's price: Ann sells at 1.50 (the
	 * price goes to 1.47), then Bob, whose page was already open, sells at 1.47 (to 1.44), and his page shows it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopTwoPlayers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopTwoPlayers")
	public void aPageLeftOpenTradesAtTodaysPriceNotTheOneItShowed(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer ann = ProtectionSpecGameTests.player(helper);
		ServerPlayer bob = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			ann.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			bob.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu bobs = board(helper, bob, hall);
			int timber = BoardTradeGameTests.slot(helper, bobs, "Test Timber");
			helper.assertTrue(BoardTradeGameTests.lore(bobs.icon(timber)).contains("We pay: 1.5 emeralds"), "Bob's page: " + BoardTradeGameTests.lore(bobs.icon(timber)));
			ChoiceMenu anns = board(helper, ann, hall);
			anns.press(timber, ann);
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 147 && entity.treasury() == 900, "Ann's sale: " + price(helper, hall, TIMBER));
			bobs.press(timber, bob);
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 144 && price(helper, hall, TIMBER).nudge() == -2, "Bob's sale on his old page: " + price(helper, hall, TIMBER));
			helper.assertTrue(has(bob, Items.EMERALD) == 1 && entity.treasury() == 800, "Bob paid " + has(bob, Items.EMERALD) + ", treasury " + entity.treasury());
			List<String> tip = BoardTradeGameTests.lore(bobs.icon(timber));
			helper.assertTrue(tip.contains("We pay: 1.44 emeralds") && tip.contains("Down from 1.5 emeralds yesterday"), "Bob's page after: " + tip);
			helper.succeed();
		});
	}

	/**
	 * board-trade.md, Switches: `villageTreasury` off: "the village puts by no takings; the board still pays from and
	 * into whatever the treasury holds".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopTreasuryOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopTreasuryOff")
	public void withTheTreasurySwitchedOffTheBoardStillPaysAndTakes(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		restoreConfigAfter(helper);
		helper.runAfterDelay(2, () -> {
			WorkplaceConfig.parse("{\"villageTreasury\": false}").apply();
			VillageHalls.RADIUS = 8;
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(500);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && has(player, Items.EMERALD) == 1 && entity.treasury() == 400, "the sale with the treasury off: "
				+ said(menu, timber) + ", treasury " + entity.treasury());
			Container chest = BoardTradeGameTests.chest(helper);
			chest.setItem(1, new ItemStack(Items.OAK_LOG, 16)); // 32 in all: one bundle to spare
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			menu.press(timber, player); // 1.47 + 10% = 1.62: charged 2
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 0 && entity.treasury() == 600, "the purchase with the treasury off: "
				+ said(menu, timber) + ", treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * board-trade.md, Saved data: "A trade changes the hall's `treasury` (and `treasuryTotal` ...)". After a sale and a
	 * purchase the hall saved and loaded again holds the same treasury and total, and the caravans' list the moved price.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopReload")
	public void aHallSavedAfterTradingKeepsItsTreasury(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			BoardTradeGameTests.chest(helper).setItem(0, new ItemStack(Items.OAK_LOG, 32));
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			player.getInventory().add(new ItemStack(Items.EMERALD, 4));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player); // sold: -1 emerald
			menu.clicked(timber, 1, ClickType.PICKUP, player); // bought: +2 emeralds
			helper.assertTrue(entity.treasury() == 1100, "after the trades the treasury holds " + entity.treasury());
			int treasury = entity.treasury();
			long total = entity.treasuryTotal();
			Market.Price moved = price(helper, hall, TIMBER);
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			BlockEntity loaded = BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(loaded instanceof VillageHallBlockEntity copy && copy.treasury() == treasury && copy.treasuryTotal() == total,
				"the hall came back with " + (loaded instanceof VillageHallBlockEntity c ? c.treasury() + "/" + c.treasuryTotal() : loaded) + ", not " + treasury + "/" + total);
			Caravans.Data data = Caravans.Data.get(level);
			Caravans.Data back = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(moved.equals(back.market(hall).prices().get(TIMBER)), "the price came back as " + back.market(hall).prices().get(TIMBER) + ", not " + moved);
			helper.succeed();
		});
	}

	private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.SOUTH, 0.5), Direction.SOUTH, at, false));
	}

	/**
	 * board-trade.md: strangers in a protected village get the board "whatever they hold", but "sneaking with a block
	 * in hand is still a refused placement"; `villageProtection` off: "everyone gets the whole hall".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopProtection"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopProtection")
	public void aSneakingStrangerPlacesNothingAndProtectionOffOpensTheWholeHall(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos at = helper.absolutePos(ProtectionSpecGameTests.HALL);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		restoreConfigAfter(helper);
		Leftovers.after(helper, () -> Caravans.Data.get(helper.getLevel()).remove(at));
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, at, TIMBER, 150);
			stranger.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 180f, 0f);
			// with a block in hand, not sneaking: the board
			use(helper, stranger, ProtectionSpecGameTests.HALL, new ItemStack(Items.COBBLESTONE, 8));
			helper.assertTrue(stranger.containerMenu instanceof ChoiceMenu m && m.icon(0).isEmpty() && m.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD),
				"a stranger holding a block didn't get the board: " + stranger.containerMenu);
			stranger.closeContainer();
			// sneaking with a block: no placement, no page
			stranger.setShiftKeyDown(true);
			use(helper, stranger, ProtectionSpecGameTests.HALL, new ItemStack(Items.COBBLESTONE, 8));
			stranger.setShiftKeyDown(false);
			helper.assertBlockPresent(Blocks.AIR, ProtectionSpecGameTests.HALL.south());
			helper.assertTrue(stranger.getMainHandItem().getCount() == 8, "the stranger's block was used: " + stranger.getMainHandItem());
			helper.assertTrue(!(stranger.containerMenu instanceof ChoiceMenu), "sneaking with a block opened " + stranger.containerMenu);
			stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

			// protection switched off: the stranger gets the whole hall's screen
			WorkplaceConfig.parse("{\"villageProtection\": false}").apply();
			VillageHalls.RADIUS = 16;
			use(helper, stranger, ProtectionSpecGameTests.HALL, ItemStack.EMPTY);
			helper.assertTrue(stranger.containerMenu instanceof ChoiceMenu m && m.icon(VillageHallScreen.NAME).is(Items.NAME_TAG),
				"protection off: the stranger didn't get the hall's screen: " + stranger.containerMenu);
			stranger.closeContainer();
			helper.assertTrue(hall.isProtected(), "the setting itself changed");
			helper.succeed();
		});
	}

	private static final List<String> BRITISH = List.of("colour", "armour", "defence", "cancelled", "grey", "jeweller", "favour", "centre", "neighbour", "honour",
		"storey", "cheque");

	private static void plain(GameTestHelper helper, String where, String line, List<String> bad) {
		String lower = line.toLowerCase(Locale.ROOT);
		boolean raw = lower.contains("aliveworkplace") || line.matches(".*%(\\d+\\$)?[sd].*") || line.contains("{") || line.contains("null") || line.isBlank();
		boolean british = BRITISH.stream().anyMatch(w -> lower.matches(".*\\b" + w + "\\w*\\b.*"));
		if (raw || british) {
			bad.add(where + ": \"" + line + "\"");
		}
	}

	private static void icon(GameTestHelper helper, String where, ItemStack icon, List<String> bad) {
		if (icon.isEmpty()) {
			return;
		}
		plain(helper, where + " name", icon.getHoverName().getString(), bad);
		for (String l : BoardTradeGameTests.lore(icon)) {
			plain(helper, where, l, bad);
		}
	}

	/**
	 * Every sentence the board shows, from the specs' "What a player sees": the Prices tab, every good's tooltip, the
	 * treasury nugget, with and without a Storehouse, and the line for every outcome of a sale and a purchase (each
	 * stop, traded or not), with real arguments. None is a raw key, none has a %s or {} left, none is blank, and all are
	 * American English; each refusal names its own reason.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopSentences"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopSentences")
	public void everySentenceAtTheBoardReadsAsPlainEnglish(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, false);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BoardTradeGameTests.price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1240);
			List<String> bad = new ArrayList<>();
			ChoiceMenu menu = board(helper, player, hall);
			for (int s = 0; s < ChoiceMenu.SIZE; s++) {
				icon(helper, "no Storehouse, slot " + s, menu.icon(s), bad);
			}
			helper.setBlock(BoardTradeGameTests.STOREHOUSE, ModBlocks.STOREHOUSE);
			helper.setBlock(BoardTradeGameTests.CHEST, Blocks.CHEST);
			BoardTradeGameTests.chest(helper).setItem(0, new ItemStack(Items.OAK_LOG, 64));
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 40));
			menu = board(helper, player, hall);
			for (int s = 0; s < ChoiceMenu.SIZE; s++) {
				icon(helper, "with a Storehouse, slot " + s, menu.icon(s), bad);
			}
			helper.assertTrue(menu.icon(TradePage.TREASURY).getHoverName().getString().equals("Treasury: 12.4 emeralds"),
				"the nugget: " + menu.icon(TradePage.TREASURY).getHoverName().getString());
			java.util.Set<String> refusals = new java.util.HashSet<>();
			for (TradeGoods.Good good : TradeGoods.all()) {
				for (Board.Stop stop : Board.Stop.values()) {
					for (boolean sale : new boolean[] {true, false}) {
						Board.Result traded = new Board.Result(sale, good, 2, good.bundle() * 2, 300, stop);
						plain(helper, "traded " + good.id() + " " + stop, Board.message(level, hall, traded, player).getString(), bad);
						if (stop != Board.Stop.NONE && stop != Board.Stop.PACK) {
							String no = Board.message(level, hall, new Board.Result(sale, good, 0, 0, 0, stop), player).getString();
							plain(helper, "refused " + good.id() + " " + stop, no, bad);
							if (good.id().equals(TIMBER) && sale) {
								refusals.add(no);
							}
						}
					}
				}
			}
			helper.assertTrue(bad.isEmpty(), "sentences that don't read: " + bad);
			helper.assertTrue(refusals.size() == Board.Stop.values().length - 2, "two refusals say the same thing: " + refusals);
			helper.succeed();
		});
	}
}
