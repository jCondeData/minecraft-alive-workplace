package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.Prices;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 33.5, trading at the board: on the Prices tab a click sells the village a bundle the player carries (the
 * treasury pays, the Storehouse's chest fills), or buys one (out of the chest, which keeps 16 of everything back; the
 * player pays 10% over into the treasury); shift does as many as will go; each bundle moves the day's price 2%. The
 * village never pays more than its treasury holds or takes what its chests have no room for, and says which. Without a
 * Storehouse the tab says the market needs one. The treasury sits on the page, and only the owner, their friends and
 * operators collect it. Also what's likeliest to break: the chest gone mid-trade, a save and reload, the economy off.
 * Money here is emeralds (whole ones: the player is paid down and charged up, the difference stays with the village);
 * the compat suite trades in CobbleDollars ({@code BoardTradeCompatTests}). The stranger's side is in
 * {@code ProtectionSpecGameTests}.
 */
public class BoardTradeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	static final BlockPos CHEST = new BlockPos(13, 2, 11);
	static final ResourceLocation TIMBER = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_timber");
	private static final ResourceLocation REMEDIES = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_remedies");
	private static final ResourceLocation ARMS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_arms");

	/**
	 * A hall at {@code at} with a small village radius, on the caravans' list, with only the six test goods, alone in
	 * its batch; with a Storehouse and its chest if {@code storehouse}. Put back when the test ends.
	 */
	static BlockPos hall(GameTestHelper helper, BlockPos at, boolean storehouse) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> Caravans.Data.get(helper.getLevel()).remove(hall));
		if (storehouse) {
			helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
			helper.setBlock(CHEST, Blocks.CHEST);
		}
		return hall;
	}

	private static BlockPos hall(GameTestHelper helper) {
		return hall(helper, new BlockPos(9, 2, 9), true);
	}

	/** Today's market for {@code hall}: {@code good} at {@code cents} (yesterday's the same), nothing known for or short of. */
	static void price(GameTestHelper helper, BlockPos hall, ResourceLocation good, int cents) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		if (data.village(hall) == null) {
			data.setWants(hall, VillageHalls.name(level, hall), List.of());
		}
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>(data.market(hall).prices());
		prices.put(good, new Market.Price(cents, cents, 0));
		data.setMarket(hall, new Market(List.of(), List.of(), prices, Chronicle.day(level), List.of()));
		helper.assertTrue(data.market(hall).prices().get(good).cents() == cents, "the market wasn't kept");
	}

	private static Market.Price price(GameTestHelper helper, BlockPos hall, ResourceLocation good) {
		return Caravans.Data.get(helper.getLevel()).market(hall).prices().get(good);
	}

	static VillageHallBlockEntity entity(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	static int has(ServerPlayer player, Item item) {
		return player.getInventory().countItem(item);
	}

	/** The slot of the icon called {@code name} in the page's rows. */
	static int slot(GameTestHelper helper, ChoiceMenu menu, String name) {
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (menu.icon(s).getHoverName().getString().equals(name)) {
				return s;
			}
		}
		helper.fail("no " + name + " on the page");
		return -1;
	}

	static List<String> lore(ItemStack icon) {
		List<String> out = new ArrayList<>();
		var lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

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

	/**
	 * A sale: a click with a bundle in the inventory takes 16 logs (of any kind) into the Storehouse's chest and the
	 * treasury pays the board's price, in whole emeralds; shift sells every bundle the player carries.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardSale"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardSale")
	public void aSalePaysFromTheTreasuryAndFillsTheChest(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = entity(helper, hall);
			entity.setTreasury(1000);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 10));
			player.getInventory().add(new ItemStack(Items.BIRCH_LOG, 30));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = slot(helper, menu, "Test Timber");
			List<String> tip = lore(menu.icon(timber));
			helper.assertTrue(tip.contains("Click: sell a bundle (you carry 40). Shift-click: all it will take") && tip.contains("Right-click: buy a bundle")
				&& tip.contains("None to spare (the Storehouse keeps 16 of everything back)"), "the tooltip before: " + tip);

			menu.press(timber, player);
			Container chest = chest(helper);
			helper.assertTrue(count(chest, Items.OAK_LOG) == 10 && count(chest, Items.BIRCH_LOG) == 6, "the chest got " + count(chest, Items.OAK_LOG)
				+ " oak and " + count(chest, Items.BIRCH_LOG) + " birch");
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && has(player, Items.BIRCH_LOG) == 24, "the player kept " + has(player, Items.OAK_LOG) + " oak, "
				+ has(player, Items.BIRCH_LOG) + " birch");
			// 1.5 emeralds: paid down to 1, and the half stays with the village
			helper.assertTrue(has(player, Items.EMERALD) == 1 && entity.treasury() == 900, "paid " + has(player, Items.EMERALD) + ", treasury " + entity.treasury());
			tip = lore(menu.icon(timber));
			helper.assertTrue(tip.get(0).equals("You sell 16 × Test Timber to " + village + " for 1 emerald."), "what the tooltip says of the sale: " + tip);
			helper.assertTrue(tip.contains("We pay: 1.47 emeralds") && tip.contains("Down from 1.5 emeralds yesterday"), "the price after: " + tip);
			helper.assertTrue(lore(menu.icon(TradePage.TREASURY)).size() >= 2 && menu.icon(TradePage.TREASURY).getHoverName().getString().equals("Treasury: 9 emeralds"),
				"the treasury on the page: " + menu.icon(TradePage.TREASURY).getHoverName().getString());

			// shift: the one bundle left (24 logs), at 1.47
			shiftClick(menu, timber, player);
			helper.assertTrue(has(player, Items.BIRCH_LOG) == 8 && count(chest, Items.BIRCH_LOG) == 22, "after the shift-click the player has "
				+ has(player, Items.BIRCH_LOG) + ", the chest " + count(chest, Items.BIRCH_LOG));
			helper.assertTrue(has(player, Items.EMERALD) == 2 && entity.treasury() == 800, "paid " + has(player, Items.EMERALD) + ", treasury " + entity.treasury());
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 144 && price(helper, hall, TIMBER).nudge() == -2, "the price: " + price(helper, hall, TIMBER));
			// with less than a bundle left, the tooltip offers to buy
			tip = lore(menu.icon(timber));
			helper.assertTrue(tip.contains("Click: buy a bundle. Shift-click: all you can") && tip.stream().noneMatch(l -> l.startsWith("Right-click")), "with 8 logs: " + tip);
			helper.succeed();
		});
	}

	/**
	 * A purchase: a click without the goods takes a bundle out of the chest and the player pays 10% over the price, in
	 * whole emeralds, into the treasury; a right click buys even with the goods in the inventory; shift buys all the
	 * chest can spare and the player can pay for.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardPurchase"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardPurchase")
	public void aPurchaseTakesFromTheChestAndPaysIn(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			Container chest = chest(helper);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 48));
			player.getInventory().add(new ItemStack(Items.EMERALD, 5));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = slot(helper, menu, "Test Timber");
			List<String> tip = lore(menu.icon(timber));
			helper.assertTrue(tip.contains("Bundles to spare in the Storehouse: 2") && tip.contains("Click: buy a bundle. Shift-click: all you can")
				&& tip.contains("We sell: 1.1 emeralds"), "the tooltip before: " + tip);

			menu.press(timber, player);
			// 1.1 emeralds: charged up to 2, and the village keeps the difference
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && count(chest, Items.OAK_LOG) == 32, "the player got " + has(player, Items.OAK_LOG)
				+ ", the chest holds " + count(chest, Items.OAK_LOG));
			helper.assertTrue(has(player, Items.EMERALD) == 3 && entity.treasury() == 200, "the player has " + has(player, Items.EMERALD) + " emeralds, the treasury "
				+ entity.treasury());
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 102 && price(helper, hall, TIMBER).nudge() == 1, "the price: " + price(helper, hall, TIMBER));
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You buy 16 × Test Timber from " + village + " for 2 emeralds."), "the tooltip: " + lore(menu.icon(timber)));

			// carrying a bundle, a right click still buys (1.12: 2 emeralds)
			rightClick(menu, timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 32 && count(chest, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 1 && entity.treasury() == 400,
				"after the right click: " + has(player, Items.OAK_LOG) + " logs, " + has(player, Items.EMERALD) + " emeralds, treasury " + entity.treasury());
			// the last 16 stay
			rightClick(menu, timber, player);
			helper.assertTrue(count(chest, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 1 && entity.treasury() == 400, "the chest gave its last 16");
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals(village + " has no bundle of Test Timber to spare: its Storehouse keeps 16 of everything back."),
				"it didn't say why: " + lore(menu.icon(timber)));

			// a player who can't pay (1.14 emeralds, with 1)
			chest.setItem(1, new ItemStack(Items.OAK_LOG, 32));
			rightClick(menu, timber, player);
			helper.assertTrue(count(chest, Items.OAK_LOG) == 48 && has(player, Items.EMERALD) == 1 && price(helper, hall, TIMBER).cents() == 104,
				"sold on credit: " + count(chest, Items.OAK_LOG) + " in the chest, " + has(player, Items.EMERALD) + " emeralds");
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You can't pay 1.14 emeralds for a bundle of Test Timber."), "it didn't say why: " + lore(menu.icon(timber)));

			// shift, without logs in the inventory: both bundles the chest can spare, 1.14 + 1.17 = 2.31, charged 3
			player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.OAK_LOG), 64, player.inventoryMenu.getCraftSlots());
			player.getInventory().add(new ItemStack(Items.EMERALD, 10));
			shiftClick(menu, timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 32 && count(chest, Items.OAK_LOG) == 16, "the shift-click bought " + has(player, Items.OAK_LOG));
			helper.assertTrue(has(player, Items.EMERALD) == 8 && entity.treasury() == 700, "the player has " + has(player, Items.EMERALD) + ", the treasury " + entity.treasury());
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 108 && price(helper, hall, TIMBER).nudge() == 4, "the price: " + price(helper, hall, TIMBER));
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You buy 32 × Test Timber from " + village + " for 3 emeralds."), "the tooltip: " + lore(menu.icon(timber)));
			helper.assertTrue(entity.treasuryTotal() == 700, "what the board earned isn't counted as takings: " + entity.treasuryTotal());
			helper.succeed();
		});
	}

	/**
	 * The Storehouse keeps 16 of every item back, as it does for caravans: 31 logs spare no bundle, 32 one; two kinds
	 * of log each keep their own 16, and a bundle may be made of both.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardKeepsSixteen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardKeepsSixteen")
	public void theStorehouseKeepsSixteenOfEverythingBack(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = entity(helper, hall);
			TradeGoods.Good good = TradeGoods.get(TIMBER);
			Container chest = chest(helper);
			player.getInventory().add(new ItemStack(Items.EMERALD, 20));
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 31));
			helper.assertTrue(Board.spareBundles(good, Board.stock(level, Caravans.storehouse(level, hall))) == 0, "31 logs spare a bundle");
			ChoiceMenu menu = board(helper, player, hall);
			int timber = slot(helper, menu, "Test Timber");
			helper.assertTrue(lore(menu.icon(timber)).contains("None to spare (the Storehouse keeps 16 of everything back)"), "31 logs: " + lore(menu.icon(timber)));
			menu.press(timber, player);
			helper.assertTrue(count(chest, Items.OAK_LOG) == 31 && has(player, Items.EMERALD) == 20 && entity.treasury() == 0, "a bundle out of 31 logs");

			chest.setItem(0, new ItemStack(Items.OAK_LOG, 32));
			menu.press(timber, player);
			helper.assertTrue(count(chest, Items.OAK_LOG) == 16 && has(player, Items.OAK_LOG) == 16, "32 logs: the chest holds " + count(chest, Items.OAK_LOG));
			player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.OAK_LOG), 64, player.inventoryMenu.getCraftSlots());

			// 20 oak and 20 birch: 4 and 4 over, not a bundle
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 20));
			chest.setItem(1, new ItemStack(Items.BIRCH_LOG, 20));
			int emeralds = has(player, Items.EMERALD);
			shiftClick(menu, timber, player);
			helper.assertTrue(count(chest, Items.OAK_LOG) == 20 && count(chest, Items.BIRCH_LOG) == 20 && has(player, Items.EMERALD) == emeralds, "8 logs over made a bundle");
			// 26 oak and 22 birch: 10 and 6 over, one bundle of both, the board's own item (oak) first
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 26));
			chest.setItem(1, new ItemStack(Items.BIRCH_LOG, 22));
			helper.assertTrue(new ArrayList<>(Board.spare(good, Board.stock(level, Caravans.storehouse(level, hall))).keySet()).equals(List.of(Items.OAK_LOG, Items.BIRCH_LOG)),
				"what it sells first: " + Board.spare(good, Board.stock(level, Caravans.storehouse(level, hall))));
			shiftClick(menu, timber, player);
			helper.assertTrue(count(chest, Items.OAK_LOG) == 16 && count(chest, Items.BIRCH_LOG) == 16, "the chest kept " + count(chest, Items.OAK_LOG) + " oak, "
				+ count(chest, Items.BIRCH_LOG) + " birch");
			helper.assertTrue(has(player, Items.OAK_LOG) == 10 && has(player, Items.BIRCH_LOG) == 6, "the bundle: " + has(player, Items.OAK_LOG) + " oak, "
				+ has(player, Items.BIRCH_LOG) + " birch");
			helper.succeed();
		});
	}

	/**
	 * The village never pays more than its treasury holds, and says so: a shift-click stops at what it can pay for, a
	 * click it can't pay for moves nothing, and with emeralds a sale that wouldn't come to a whole emerald isn't made.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardTreasuryLimit"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardTreasuryLimit")
	public void theVillageNeverPaysMoreThanItsTreasuryHolds(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			Container chest = chest(helper);
			entity.setTreasury(250);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 64));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = slot(helper, menu, "Test Timber");
			// four bundles at 1, 0.98, 0.96, 0.94: the treasury's 2.5 pays for two (1.98)
			shiftClick(menu, timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 32 && count(chest, Items.OAK_LOG) == 32, "it took " + count(chest, Items.OAK_LOG) + " logs");
			helper.assertTrue(has(player, Items.EMERALD) == 1 && entity.treasury() == 150, "paid " + has(player, Items.EMERALD) + ", treasury " + entity.treasury());
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You sell 32 × Test Timber to " + village + " for 1 emerald. Its treasury can't pay for more."),
				"it didn't say the treasury stopped it: " + lore(menu.icon(timber)));
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 96, "two bundles: " + price(helper, hall, TIMBER));

			// a treasury that can't pay for one bundle: nothing moves
			entity.setTreasury(50);
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 32 && count(chest, Items.OAK_LOG) == 32 && entity.treasury() == 50 && has(player, Items.EMERALD) == 1
				&& price(helper, hall, TIMBER).cents() == 96, "a sale the treasury couldn't pay for");
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals(village + "'s treasury can't pay for a bundle of Test Timber: it holds 0.5 emeralds."),
				"it didn't say why: " + lore(menu.icon(timber)));

			// a bundle worth less than an emerald: not for nothing; two at once come to one emerald
			entity.setTreasury(1000);
			price(helper, hall, TIMBER, 80);
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 32 && entity.treasury() == 1000 && has(player, Items.EMERALD) == 1, "a bundle went for nothing");
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals("A bundle of Test Timber comes to less than a whole emerald here: shift-click to sell several at once."),
				"it didn't say why: " + lore(menu.icon(timber)));
			shiftClick(menu, timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && has(player, Items.EMERALD) == 2 && entity.treasury() == 900, "0.8 + 0.78: "
				+ has(player, Items.EMERALD) + " emeralds, treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * A bundle moves that day's price 2%: down when sold to the village, up when bought from it, a cent at least, never
	 * past half or twice the base; yesterday's price stands, and the next dawn's move goes on from the moved price.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardPriceMoves"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardPriceMoves")
	public void aBundleMovesThePriceTwoPercent(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.assertTrue(Prices.sold(100, 100) == 98 && Prices.bought(100, 100) == 102 && Prices.sold(300, 300) == 294 && Prices.bought(300, 300) == 306,
			"2%: " + Prices.sold(100, 100) + " " + Prices.bought(100, 100));
		helper.assertTrue(Prices.sold(100, 51) == 50 && Prices.sold(100, 50) == 50 && Prices.bought(100, 199) == 200 && Prices.bought(100, 200) == 200,
			"past half or twice the base: " + Prices.sold(100, 50) + " " + Prices.bought(100, 200));
		helper.assertTrue(Prices.sold(40, 30) == 29 && Prices.bought(40, 30) == 31, "a cent at least: " + Prices.sold(40, 30));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			price(helper, hall, TIMBER, 100);
			entity(helper, hall).setTreasury(1000);
			chest(helper).setItem(0, new ItemStack(Items.OAK_LOG, 64));
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			player.getInventory().add(new ItemStack(Items.EMERALD, 10));
			Map<ResourceLocation, Market.Price> others = new LinkedHashMap<>(Caravans.Data.get(level).market(hall).prices());
			others.remove(TIMBER);
			ChoiceMenu menu = board(helper, player, hall);
			int timber = slot(helper, menu, "Test Timber");
			menu.press(timber, player); // sold to it
			helper.assertTrue(price(helper, hall, TIMBER).equals(new Market.Price(98, 100, -1)), "sold: " + price(helper, hall, TIMBER));
			helper.assertTrue(TradePage.marks(menu.icon(timber)).contains(TradePage.DOWN) && lore(menu.icon(timber)).contains("We pay: 0.98 emeralds")
				&& lore(menu.icon(timber)).contains("We sell: 1.08 emeralds"), "the board after the sale: " + lore(menu.icon(timber)));
			menu.press(timber, player); // no bundle left in the inventory: bought from it
			rightClick(menu, timber, player); // (a plain click would sell the bundle just bought)
			helper.assertTrue(price(helper, hall, TIMBER).equals(new Market.Price(102, 100, 1)), "bought twice: " + price(helper, hall, TIMBER) + " " + lore(menu.icon(timber)));
			helper.assertTrue(TradePage.marks(menu.icon(timber)).contains(TradePage.UP), "no arrow up: " + TradePage.marks(menu.icon(timber)));
			// every other good stands where it was
			Map<ResourceLocation, Market.Price> after = new LinkedHashMap<>(Caravans.Data.get(level).market(hall).prices());
			after.remove(TIMBER);
			helper.assertTrue(after.equals(others), "other prices moved: " + after + " from " + others);

			// the moved price and its steps survive a save and reload
			Caravans.Data data = Caravans.Data.get(level);
			Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(loaded.market(hall).prices().get(TIMBER).equals(new Market.Price(102, 100, 1)), "the reload: " + loaded.market(hall).prices().get(TIMBER));

			// the next dawn moves on from 1.02, with yesterday's now 1.02 and the day's steps cleared
			Market tomorrow = Economy.count(level, hall, VillageHalls.census(level, hall), Chronicle.day(level) + 1);
			Market.Price next = tomorrow.prices().get(TIMBER);
			helper.assertTrue(next.yesterday() == 102 && next.nudge() == 0, "the dawn after: " + next);
			helper.succeed();
		});
	}

	/**
	 * The village never takes more than its chests have room for, and says so: with the chest full nothing moves; with
	 * room for half a bundle nothing moves either (no half bundles); a shift-click stops when the chest is full.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardNoRoom"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardNoRoom")
	public void theVillageNeverTakesMoreThanItsChestsHaveRoomFor(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(2000);
			Container chest = chest(helper);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 64));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 64 && has(player, Items.EMERALD) == 0 && entity.treasury() == 2000 && price(helper, hall, TIMBER).cents() == 150,
				"a sale into a full chest: " + has(player, Items.OAK_LOG) + " logs left, treasury " + entity.treasury());
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals(village + "'s Storehouse has no room for a bundle of Test Timber."),
				"it didn't say why: " + lore(menu.icon(timber)));

			// room for 8 more logs: not a bundle, and the 8 that fitted come back out
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 56));
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 64 && count(chest, Items.OAK_LOG) == 56 && entity.treasury() == 2000, "half a bundle went in: the chest holds "
				+ count(chest, Items.OAK_LOG) + ", the player " + has(player, Items.OAK_LOG));

			// room for two bundles (32 logs): a shift-click with four sells two and says the Storehouse is full
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 32));
			shiftClick(menu, timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 32 && count(chest, Items.OAK_LOG) == 64, "the chest holds " + count(chest, Items.OAK_LOG)
				+ ", the player " + has(player, Items.OAK_LOG));
			// 1.5 + 1.47 = 2.97: 2 emeralds
			helper.assertTrue(has(player, Items.EMERALD) == 2 && entity.treasury() == 1800, "paid " + has(player, Items.EMERALD) + ", treasury " + entity.treasury());
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You sell 32 × Test Timber to " + village + " for 2 emeralds. Its Storehouse has no room for more."),
				"it didn't say the Storehouse stopped it: " + lore(menu.icon(timber)));
			helper.succeed();
		});
	}

	/**
	 * Without a Storehouse the tab says the market needs one, on its own icon and on every good, and a click moves
	 * nothing; the same with a Storehouse that has no chest, and when the chest is broken while the page is open. With
	 * the Storehouse built, the board trades.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardNeedsStorehouse"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardNeedsStorehouse")
	public void withoutAStorehouseTheTabSaysTheMarketNeedsOne(GameTestHelper helper) {
		BlockPos hall = hall(helper, new BlockPos(9, 2, 9), false);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			price(helper, hall, TIMBER, 150);
			VillageHallBlockEntity entity = entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = slot(helper, menu, "Test Timber");
			String needs = "The market needs a Storehouse with a chest";
			helper.assertTrue(lore(menu.icon(TradePage.Tab.PRICES.slot())).contains(needs), "the tab doesn't say so: " + lore(menu.icon(TradePage.Tab.PRICES.slot())));
			for (int s = VillageHallScreen.FIRST_ROW; s < VillageHallScreen.FIRST_ROW + TradeGoods.all().size(); s++) {
				helper.assertTrue(lore(menu.icon(s)).contains(needs) && lore(menu.icon(s)).stream().noneMatch(l -> l.startsWith("Click")),
					menu.icon(s).getHoverName().getString() + " doesn't say so: " + lore(menu.icon(s)));
			}
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 0 && entity.treasury() == 1000, "a trade without a Storehouse");
			helper.assertTrue(lore(menu.icon(timber)).get(0).equals("The market needs a Storehouse: " + village + " has none with a chest yet."),
				"the click didn't say so: " + lore(menu.icon(timber)));

			// a Storehouse without a chest is none
			helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && entity.treasury() == 1000 && lore(menu.icon(timber)).contains(needs), "a trade without a chest");

			// with its chest, the board trades
			helper.setBlock(CHEST, Blocks.CHEST);
			menu = board(helper, player, hall);
			helper.assertTrue(!lore(menu.icon(TradePage.Tab.PRICES.slot())).contains(needs) && !lore(menu.icon(timber)).contains(needs), "it still asks for a Storehouse");
			// the chest broken while the page is open: the click says so and nothing is lost
			helper.setBlock(CHEST, Blocks.AIR);
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 16 && has(player, Items.EMERALD) == 0 && entity.treasury() == 1000, "a trade with the chest gone");
			helper.assertTrue(lore(menu.icon(timber)).contains(needs), "the page didn't notice the chest going: " + lore(menu.icon(timber)));
			helper.setBlock(CHEST, Blocks.CHEST);
			menu.press(timber, player);
			helper.assertTrue(has(player, Items.OAK_LOG) == 0 && has(player, Items.EMERALD) == 1 && entity.treasury() == 900 && count(chest(helper), Items.OAK_LOG) == 16,
				"no trade with the Storehouse built: " + lore(menu.icon(timber)));
			helper.succeed();
		});
	}

	/**
	 * What counts at the board: potions only if they heal (a water bottle isn't a remedy), and no worn tools. A healing
	 * potion sold reaches the chest as it is, and the Remedies icon is a healing potion.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardWhatCounts"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardWhatCounts")
	public void waterBottlesAndWornSwordsDontCount(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			TradeGoods.Good remedies = TradeGoods.get(REMEDIES);
			TradeGoods.Good arms = TradeGoods.get(ARMS);
			ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
			ItemStack regeneration = PotionContents.createItemStack(Items.POTION, Potions.LONG_REGENERATION);
			ItemStack swiftness = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
			helper.assertTrue(Board.counts(remedies, healing) && Board.counts(remedies, regeneration) && !Board.counts(remedies, water)
				&& !Board.counts(remedies, swiftness) && !Board.counts(remedies, new ItemStack(Items.POTION)), "which potions are remedies");
			ItemStack worn = new ItemStack(Items.IRON_SWORD);
			worn.setDamageValue(100);
			helper.assertTrue(Board.counts(arms, new ItemStack(Items.IRON_SWORD)) && !Board.counts(arms, worn) && !Board.counts(arms, new ItemStack(Items.OAK_LOG)),
				"which swords are arms");

			price(helper, hall, REMEDIES, 200);
			VillageHallBlockEntity entity = entity(helper, hall);
			entity.setTreasury(1000);
			player.getInventory().add(water.copy());
			player.getInventory().add(worn.copy());
			ChoiceMenu menu = board(helper, player, hall);
			int slot = slot(helper, menu, "Test Remedies");
			PotionContents shown = menu.icon(slot).get(DataComponents.POTION_CONTENTS);
			helper.assertTrue(menu.icon(slot).is(Items.POTION) && shown != null && shown.is(Potions.HEALING), "the Remedies icon isn't a healing potion: " + shown);
			helper.assertTrue(Board.carried(player, remedies) == 0 && Board.carried(player, arms) == 0, "a water bottle or a worn sword counted");
			menu.press(slot, player); // nothing to sell: it would buy, and there is none
			helper.assertTrue(has(player, Items.POTION) == 1 && entity.treasury() == 1000 && has(player, Items.EMERALD) == 0, "the village bought a water bottle");

			player.getInventory().add(healing.copy());
			menu.press(slot, player);
			Container chest = chest(helper);
			boolean kept = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				PotionContents c = chest.getItem(i).get(DataComponents.POTION_CONTENTS);
				kept |= chest.getItem(i).is(Items.POTION) && c != null && c.is(Potions.HEALING);
			}
			helper.assertTrue(kept && count(chest, Items.POTION) == 1, "the healing potion didn't reach the chest as it was");
			helper.assertTrue(has(player, Items.POTION) == 1 && has(player, Items.EMERALD) == 2 && entity.treasury() == 800, "paid " + has(player, Items.EMERALD)
				+ " for the potion, treasury " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * The treasury on the page, and who collects (33.5): the owner, their friends and operators, in an open village
	 * too; a hall nobody owns stays open to all. With the economy switched off, the board doesn't trade and anyone
	 * collects in an open village, as before.
	 */
	//$ gametest_ticks_batch AREA '100' '"boardTreasury"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardTreasury")
	public void onlyTheOwnerTheirFriendsAndOperatorsCollect(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ServerPlayer op = ProtectionSpecGameTests.player(helper);
		var ops = level.getServer().getPlayerList().getOps();
		ops.add(new net.minecraft.server.players.ServerOpListEntry(op.getGameProfile(), 4, false));
		Leftovers.after(helper, () -> ops.remove(op.getGameProfile()));
		Friends.get(level.getServer()).add(owner.getUUID(), friend.getUUID(), "Friend");
		Leftovers.after(helper, () -> Friends.get(level.getServer()).remove(owner.getUUID(), friend.getUUID()));
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		helper.runAfterDelay(2, () -> {
			price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = entity(helper, hall);
			String village = VillageHalls.name(level, hall).getString();
			// nobody's hall: open to all
			entity.setTreasury(250);
			helper.assertTrue(entity.owner() == null && Treasury.mayCollect(level, entity, stranger), "a hall nobody owns isn't open to all");
			ChoiceMenu menu = board(helper, stranger, hall);
			ItemStack nugget = menu.icon(TradePage.TREASURY);
			helper.assertTrue(nugget.is(Items.GOLD_NUGGET) && nugget.getHoverName().getString().equals("Treasury: 2.5 emeralds")
				&& lore(nugget).equals(List.of("It holds up to 64 emeralds", "The village pays for what it buys out of it, and keeps what it earns here",
					"Click to collect its whole emeralds")), "the treasury on the page: " + nugget.getHoverName().getString() + " " + lore(nugget));
			menu.press(TradePage.TREASURY, stranger);
			helper.assertTrue(has(stranger, Items.EMERALD) == 2 && entity.treasury() == 50, "nobody's hall: collected " + has(stranger, Items.EMERALD));
			helper.assertTrue(menu.icon(TradePage.TREASURY).getHoverName().getString().equals("Treasury: 0.5 emeralds"), "the page after: "
				+ menu.icon(TradePage.TREASURY).getHoverName().getString());
			// the treasury is on the Routes tab too
			helper.assertTrue(TradePage.forTest(stranger, hall, TradePage.Tab.ROUTES).icon(TradePage.TREASURY).is(Items.GOLD_NUGGET), "no treasury on Routes");

			// an owned, open village: the stranger no longer collects, on the page or on the hall's name
			entity.setOwner(owner.getUUID(), "Owner");
			entity.setTreasury(300);
			helper.assertTrue(!entity.isProtected(), "the village is protected");
			menu = board(helper, stranger, hall);
			helper.assertTrue(lore(menu.icon(TradePage.TREASURY)).contains("Only Owner and their friends can collect it"), "the page doesn't say whose: "
				+ lore(menu.icon(TradePage.TREASURY)));
			menu.press(TradePage.TREASURY, stranger);
			ChoiceMenu screen = VillageHallScreen.forTest(stranger, hall);
			helper.assertTrue(lore(screen.icon(VillageHallScreen.NAME)).contains("Treasury: 3 emeralds (only Owner and their friends can collect it)"),
				"the hall's name icon: " + lore(screen.icon(VillageHallScreen.NAME)));
			screen.press(VillageHallScreen.NAME, stranger);
			helper.assertTrue(has(stranger, Items.EMERALD) == 2 && entity.treasury() == 300, "a stranger collected in an open village: " + has(stranger, Items.EMERALD));
			helper.assertTrue(Treasury.collect(level, hall, stranger).getString().equals("Only Owner and their friends can collect the treasury of " + village + "."),
				"what it tells them: " + Treasury.collect(level, hall, stranger).getString());

			// the owner (on the hall's name, as always), a friend (on the page) and an operator do
			screen = VillageHallScreen.forTest(owner, hall);
			helper.assertTrue(lore(screen.icon(VillageHallScreen.NAME)).contains("Treasury: 3 emeralds (click to collect)"), "the owner's name icon: "
				+ lore(screen.icon(VillageHallScreen.NAME)));
			screen.press(VillageHallScreen.NAME, owner);
			helper.assertTrue(has(owner, Items.EMERALD) == 3 && entity.treasury() == 0, "the owner collected " + has(owner, Items.EMERALD));
			entity.setTreasury(200);
			menu = board(helper, friend, hall);
			helper.assertTrue(lore(menu.icon(TradePage.TREASURY)).contains("Click to collect its whole emeralds"), "the friend's page: " + lore(menu.icon(TradePage.TREASURY)));
			menu.press(TradePage.TREASURY, friend);
			helper.assertTrue(has(friend, Items.EMERALD) == 2 && entity.treasury() == 0, "the friend collected " + has(friend, Items.EMERALD));
			entity.setTreasury(100);
			helper.assertTrue(Treasury.mayCollect(level, entity, op), "an operator may not collect");
			Treasury.collect(level, hall, op);
			helper.assertTrue(entity.treasury() == 0, "the operator collected nothing");

			// the economy off (until 1.7): no trading, and anyone collects in an open village as before
			WorkplaceConfig.parse("{\"villageEconomy\": false}").apply();
			entity.setTreasury(400);
			stranger.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			Board.Result off = Board.sell(level, hall, stranger, TradeGoods.get(TIMBER), false);
			helper.assertTrue(!off.traded() && off.stop() == Board.Stop.NOT_OPEN && has(stranger, Items.OAK_LOG) == 16 && entity.treasury() == 400, "a trade with the economy off: " + off);
			helper.assertTrue(!Board.buy(level, hall, stranger, TradeGoods.get(TIMBER), false).traded(), "a purchase with the economy off");
			screen = VillageHallScreen.forTest(stranger, hall);
			helper.assertTrue(lore(screen.icon(VillageHallScreen.NAME)).contains("Treasury: 4 emeralds (click to collect)"), "economy off, the name icon: "
				+ lore(screen.icon(VillageHallScreen.NAME)));
			screen.press(VillageHallScreen.NAME, stranger);
			helper.assertTrue(has(stranger, Items.EMERALD) == 6 && entity.treasury() == 0, "economy off: the stranger collected " + has(stranger, Items.EMERALD));
			helper.succeed();
		});
	}
}
