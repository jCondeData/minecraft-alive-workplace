package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1024-0353): the village economy from the player's side: the Shop Counter, its Shopkeeper, the Storehouse's
 * stock orders and the village store's luxuries.
 *
 * <p>Expectations from docs/guide (shop-counter.md, shopkeeper.md, storehouse.md), CHANGELOG and ROADMAP 34.4, not the
 * code: "Place it and the shop is yours ... Only you, your friends and operators can change the prices"; "the top slot of
 * each column is what one sale hands over, the slot below it the price ... the payment lands in those chests"; "What's
 * for sale is always what's in the chests"; "Mine it with an axe; it drops itself and the items in its price list";
 * "Without CobbleDollars a tag's price is paid in emeralds at the server's rate" (rounded up); "Click the book on the
 * board, hold an item and click it to order it (16, 32, 64...)"; "a due household takes exactly one ... an empty store
 * counts as missed" (34.4).
 */
public class QaEconomyGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos KEEPER = new BlockPos(4, 2, 2);

	record Shop(ShopCounterBlockEntity counter, Container chest, Villager keeper) {
	}

	/** A counter owned by "Frank" selling column 0 ({@code goods} for {@code price}), a chest by it with {@code stock}, and its shopkeeper. */
	static Shop shop(GameTestHelper helper, ItemStack goods, ItemStack price, ItemStack... stock) {
		Leftovers.clear(helper);
		Leftovers.stores(helper, helper.absolutePos(COUNTER));
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, goods);
		counter.setItem(ShopCounterBlockEntity.COLUMNS, price);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < stock.length; i++) {
			chest.setItem(i, stock[i]);
		}
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		return new Shop(counter, chest, keeper);
	}

	static QaStageFiveGameTests.Client buyer(GameTestHelper helper) {
		return QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaEco"));
	}

	static int carried(ServerPlayer p, Item item) {
		return p.getInventory().countItem(item) + (p.containerMenu.getCarried().is(item) ? p.containerMenu.getCarried().getCount() : 0);
	}

	// --- the counter: who may open it, from either hand --------------------------------------------------------------

	/**
	 * "Only you, your friends and operators can change the prices." A stranger's right-click on the counter, holding
	 * emeralds in the main hand, goods in the off hand only, nothing, or sneaking with something, never opens the price
	 * list, and each time says whose shop it is ("This is Frank's shop. Talk to the shopkeeper to buy."). The owner's
	 * plain click holding emeralds opens it; his sneak-click holding goods only in the off hand gives the sales log.
	 */
	//$ gametest_batch AREA '"qaEcoCounterHands"'
	@GameTest(template = AREA, batch = "qaEcoCounterHands")
	public void onlyTheOwnerOpensTheCounterWhateverTheHands(GameTestHelper helper) {
		Shop shop = shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1), new ItemStack(Items.COBBLESTONE, 64));
		List<String> wrong = new ArrayList<>();
		String[] ways = {"emeralds in the main hand", "bread in the off hand only", "empty hands", "sneaking with emeralds", "sneaking with bread in the off hand"};
		for (int i = 0; i < ways.length; i++) {
			QaStageFiveGameTests.Client c = buyer(helper);
			ServerPlayer p = c.player();
			switch (i) {
				case 0 -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 5));
				case 1 -> p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD, 3));
				case 3 -> {
					p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 5));
					p.setShiftKeyDown(true);
				}
				case 4 -> {
					p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD, 3));
					p.setShiftKeyDown(true);
				}
				default -> {
				}
			}
			QaStageFourGameTests.press(helper, p, COUNTER, Direction.NORTH);
			if (p.containerMenu != p.inventoryMenu) {
				wrong.add(ways[i] + ": a stranger opened " + p.containerMenu.getClass().getSimpleName());
				p.closeContainer();
			}
			Component told = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.shop.not_yours");
			if (told == null) {
				wrong.add(ways[i] + ": not told it's Frank's shop, saw " + QaStageFourGameTests.keys(c.seen()));
			} else {
				QaStageFourGameTests.assertRendered(helper, told, "Frank's shop", "Talk to the shopkeeper");
			}
			if (p.getInventory().countItem(Items.EMERALD) + p.getInventory().countItem(Items.BREAD) != (i == 2 ? 0 : i == 1 || i == 4 ? 3 : 5)) {
				wrong.add(ways[i] + ": what he held changed");
			}
			if (shop.counter().goods(0).getCount() != 16 || !shop.counter().price(0).is(Items.EMERALD) || shop.counter().getItem(1).getCount() != 0) {
				wrong.add(ways[i] + ": the price list changed: " + shop.counter().goods(0) + " / " + shop.counter().price(0));
			}
		}
		// The owner: a plain click with emeralds in hand opens the price list; a sneak-click with bread only in the off hand, the log.
		QaStageFiveGameTests.Client o = buyer(helper);
		ServerPlayer owner = o.player();
		shop.counter().setOwner(owner.getUUID(), owner.getGameProfile().getName());
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 5));
		QaStageFourGameTests.press(helper, owner, COUNTER, Direction.NORTH);
		if (!(owner.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)) {
			wrong.add("the owner holding emeralds didn't get the price list: " + owner.containerMenu + ", saw " + QaStageFourGameTests.keys(o.seen()));
		}
		Component how = QaStageFourGameTests.find(o.seen(), "message.aliveworkplace.shop.how_to");
		if (how == null) {
			wrong.add("the owner wasn't told how the rows work: " + QaStageFourGameTests.keys(o.seen()));
		} else {
			QaStageFourGameTests.assertRendered(helper, how, "Top row", "its price");
		}
		owner.closeContainer();
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD, 3));
		owner.setShiftKeyDown(true);
		o.seen().clear();
		QaStageFourGameTests.press(helper, owner, COUNTER, Direction.NORTH);
		Component none = QaStageFourGameTests.find(o.seen(), "message.aliveworkplace.shop.log.none");
		if (QaStageFourGameTests.find(o.seen(), "message.aliveworkplace.shop.log.header_items") == null || none == null) {
			wrong.add("the owner's sneak-click with bread in the off hand gave no sales log: " + QaStageFourGameTests.keys(o.seen()));
		} else {
			QaStageFourGameTests.assertRendered(helper, none, "No sales yet");
		}
		helper.assertTrue(owner.getOffhandItem().is(Items.BREAD) && owner.getOffhandItem().getCount() == 3, "the owner's bread: " + owner.getOffhandItem());
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// --- buying: the vanilla trade screen ------------------------------------------------------------------------------

	/**
	 * A real buyer right-clicks the shopkeeper, puts one emerald in the trade screen and shift-clicks the goods: he gets
	 * 16 cobblestone, the chest loses 16 and holds the emerald. The offer allows as many sales as the stock covers (32 / 16
	 * = 2): after the second the screen offers no more, and the counter's sample is still there.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaEcoTradeScreen"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaEcoTradeScreen")
	public void theTradeScreenSellsOnlyWhatTheChestsHold(GameTestHelper helper) {
		Shop shop = shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1), new ItemStack(Items.COBBLESTONE, 32));
		QaStageFiveGameTests.Client c = buyer(helper);
		ServerPlayer p = c.player();
		p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));
		QaStageFourGameTests.pressOn(p, shop.keeper(), c.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the right-click didn't open the trades: " + p.containerMenu + ", saw " + QaStageFourGameTests.keys(c.seen()));
		MerchantMenu menu = (MerchantMenu) p.containerMenu;
		helper.assertTrue(menu.getOffers().size() == 1 && menu.getOffers().get(0).getMaxUses() == 2,
			"offers: " + menu.getOffers().size() + (menu.getOffers().isEmpty() ? "" : ", max uses " + menu.getOffers().get(0).getMaxUses()));
		for (int sale = 1; sale <= 2; sale++) {
			p.getInventory().removeItem(9, 1);
			menu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
			helper.assertTrue(menu.getSlot(2).getItem().is(Items.COBBLESTONE) && menu.getSlot(2).getItem().getCount() == 16,
				"sale " + sale + ": the result slot shows " + menu.getSlot(2).getItem());
			QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
			helper.assertTrue(carried(p, Items.COBBLESTONE) == 16 * sale, "sale " + sale + ": the buyer has " + carried(p, Items.COBBLESTONE) + " cobblestone");
			helper.assertTrue(shop.chest().countItem(Items.COBBLESTONE) == 32 - 16 * sale && shop.chest().countItem(Items.EMERALD) == sale,
				"sale " + sale + ": the chest has " + shop.chest().countItem(Items.COBBLESTONE) + " cobblestone and " + shop.chest().countItem(Items.EMERALD) + " emeralds");
		}
		helper.assertTrue(menu.getOffers().get(0).isOutOfStock(), "the stock is gone but the offer isn't sold out");
		menu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "a third sale offered with an empty chest: " + menu.getSlot(2).getItem());
		helper.assertTrue(shop.counter().goods(0).getCount() == 16 && ModAttachments.SHOP_SALES.getOrElse(shop.keeper(), 0) == 2,
			"sample " + shop.counter().goods(0) + ", sales " + ModAttachments.SHOP_SALES.getOrElse(shop.keeper(), 0));
		p.closeContainer();
		helper.assertTrue(carried(p, Items.EMERALD) == 4, "closing the screen should give back the unspent emerald: " + carried(p, Items.EMERALD));
		helper.succeed();
	}

	// --- buying: the shop's own screen (CobbleDollars) and its checks ------------------------------------------------

	/**
	 * A column priced 3 diamonds: a buyer with exactly 3 buys and is left with none; one with 2 is told "That costs 3×
	 * Diamond", keeps his 2, and nothing leaves the chest. The chest holds just the 3 diamonds paid, and the sale is logged.
	 */
	//$ gametest_batch AREA '"qaEcoExactPrice"'
	@GameTest(template = AREA, batch = "qaEcoExactPrice")
	public void exactlyEnoughBuysAndOneShortIsRefused(GameTestHelper helper) {
		Shop shop = shop(helper, new ItemStack(Items.OAK_LOG, 8), new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.OAK_LOG, 64));
		QaStageFiveGameTests.Client rich = buyer(helper);
		rich.player().getInventory().setItem(3, new ItemStack(Items.DIAMOND, 3));
		helper.assertTrue(Shops.buy(rich.player(), shop.keeper(), 0), "exactly 3 diamonds didn't buy it: " + QaStageFourGameTests.keys(rich.seen()));
		helper.assertTrue(rich.player().getInventory().countItem(Items.DIAMOND) == 0 && rich.player().getInventory().countItem(Items.OAK_LOG) == 8,
			"the buyer has " + rich.player().getInventory().countItem(Items.DIAMOND) + " diamonds and " + rich.player().getInventory().countItem(Items.OAK_LOG) + " logs");
		Component bought = QaStageFourGameTests.find(rich.seen(), "message.aliveworkplace.shop.bought");
		helper.assertTrue(bought != null, "not told he bought it: " + QaStageFourGameTests.keys(rich.seen()));
		QaStageFourGameTests.assertRendered(helper, bought, "Bought 8", "Oak Log", "3× Diamond");

		QaStageFiveGameTests.Client poor = buyer(helper);
		poor.player().getInventory().setItem(3, new ItemStack(Items.DIAMOND, 2));
		helper.assertFalse(Shops.buy(poor.player(), shop.keeper(), 0), "2 diamonds bought a 3-diamond lot");
		Component costs = QaStageFourGameTests.find(poor.seen(), "message.aliveworkplace.shop.too_poor");
		helper.assertTrue(costs != null, "the short buyer wasn't told the price: " + QaStageFourGameTests.keys(poor.seen()));
		QaStageFourGameTests.assertRendered(helper, costs, "That costs 3× Diamond");
		helper.assertTrue(poor.player().getInventory().countItem(Items.DIAMOND) == 2 && poor.player().getInventory().countItem(Items.OAK_LOG) == 0,
			"the short buyer has " + poor.player().getInventory().countItem(Items.DIAMOND) + " diamonds, " + poor.player().getInventory().countItem(Items.OAK_LOG) + " logs");
		helper.assertTrue(shop.chest().countItem(Items.OAK_LOG) == 56 && shop.chest().countItem(Items.DIAMOND) == 3,
			"the chest: " + shop.chest().countItem(Items.OAK_LOG) + " logs, " + shop.chest().countItem(Items.DIAMOND) + " diamonds");
		helper.assertTrue(shop.counter().totalSales() == 1 && shop.counter().sales().get(0).paid().is(Items.DIAMOND)
			&& shop.counter().sales().get(0).paid().getCount() == 3, "the log: " + shop.counter().sales());
		helper.succeed();
	}

	/**
	 * Two buyers and one lot left (16 in stock, 16 a sale): the first gets it, the second is told it's sold out ("Oak Log
	 * is sold out."), keeps his emerald, and gets nothing. The chest ends empty of logs with one emerald, and talking to
	 * the shopkeeper now offers nothing.
	 */
	//$ gametest_batch AREA '"qaEcoLastLot"'
	@GameTest(template = AREA, batch = "qaEcoLastLot")
	public void twoBuyersForTheLastLot(GameTestHelper helper) {
		Shop shop = shop(helper, new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.GOLD_INGOT, 1), new ItemStack(Items.OAK_LOG, 16));
		QaStageFiveGameTests.Client a = buyer(helper);
		QaStageFiveGameTests.Client b = buyer(helper);
		a.player().getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 1));
		b.player().getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 1));
		boolean first = Shops.buy(a.player(), shop.keeper(), 0);
		boolean second = Shops.buy(b.player(), shop.keeper(), 0);
		helper.assertTrue(first && !second, "first " + first + ", second " + second);
		helper.assertTrue(a.player().getInventory().countItem(Items.OAK_LOG) == 16 && b.player().getInventory().countItem(Items.OAK_LOG) == 0,
			"logs: " + a.player().getInventory().countItem(Items.OAK_LOG) + " and " + b.player().getInventory().countItem(Items.OAK_LOG));
		helper.assertTrue(b.player().getInventory().countItem(Items.GOLD_INGOT) == 1, "the second buyer paid for nothing");
		Component sold = QaStageFourGameTests.find(b.seen(), "message.aliveworkplace.shop.sold_out");
		helper.assertTrue(sold != null, "the second buyer wasn't told: " + QaStageFourGameTests.keys(b.seen()));
		QaStageFourGameTests.assertRendered(helper, sold, "Oak Log is sold out");
		helper.assertTrue(shop.chest().countItem(Items.OAK_LOG) == 0 && shop.chest().countItem(Items.GOLD_INGOT) == 1,
			"the chest: " + shop.chest().countItem(Items.OAK_LOG) + " logs, " + shop.chest().countItem(Items.GOLD_INGOT) + " gold");
		Shops.refreshOffers(helper.getLevel(), shop.keeper());
		helper.assertTrue(shop.keeper().getOffers().isEmpty(), "an empty chest still has offers: " + shop.keeper().getOffers().size());
		helper.succeed();
	}

	/**
	 * A buyer whose inventory is full (two diamonds in one slot, dirt in all the rest) pays one diamond: the 16
	 * cobblestone land at his feet, none lost, and the chest gives up exactly 16.
	 */
	//$ gametest_batch AREA '"qaEcoFullBag"'
	@GameTest(template = AREA, batch = "qaEcoFullBag")
	public void aFullInventoryGetsTheGoodsAtItsFeet(GameTestHelper helper) {
		Shop shop = shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.DIAMOND, 1), new ItemStack(Items.COBBLESTONE, 64));
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
		QaStageFourGameTests.fill(p);
		helper.assertTrue(Shops.buy(p, shop.keeper(), 0), "the sale didn't go through: " + QaStageFourGameTests.keys(seen));
		int dropped = QaStageFourGameTests.dropped(helper, p, Items.COBBLESTONE).stream().mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(dropped + p.getInventory().countItem(Items.COBBLESTONE) == 16, "the buyer got " + dropped + " on the ground and "
			+ p.getInventory().countItem(Items.COBBLESTONE) + " in his bag");
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 1 && shop.chest().countItem(Items.COBBLESTONE) == 48 && shop.chest().countItem(Items.DIAMOND) == 1,
			"diamonds " + p.getInventory().countItem(Items.DIAMOND) + ", chest " + shop.chest().countItem(Items.COBBLESTONE) + " / " + shop.chest().countItem(Items.DIAMOND));
		helper.succeed();
	}

	/**
	 * "Without CobbleDollars a tag's price is paid in emeralds at the server's rate", rounded up. At 100 a piece a tag of
	 * 100 costs 1, 101 costs 2; a buyer with exactly 2 emeralds buys a 101 lot and the 2 go into the chest, one with 1 is
	 * told the price and keeps it. At a rate of 50, 101 costs 3.
	 */
	//$ gametest_batch AREA '"qaEcoTagRate"'
	@GameTest(template = AREA, batch = "qaEcoTagRate")
	public void aPriceTagCostsItsEmeraldsRoundedUpAtTheRate(GameTestHelper helper) {
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("101"));
		Shop shop = shop(helper, new ItemStack(Items.BREAD, 4), tag, new ItemStack(Items.BREAD, 64));
		int rate = Money.DOLLARS_PER_EMERALD;
		Leftovers.after(helper, () -> Money.DOLLARS_PER_EMERALD = rate);
		Money.DOLLARS_PER_EMERALD = 100;
		ItemStack hundred = new ItemStack(ModItems.PRICE_TAG);
		hundred.set(DataComponents.CUSTOM_NAME, Component.literal("100"));
		helper.assertTrue(Shops.emeraldPrice(hundred) == 1 && Shops.emeraldPrice(tag) == 2,
			"at 100 a piece: 100 costs " + Shops.emeraldPrice(hundred) + ", 101 costs " + Shops.emeraldPrice(tag));
		QaStageFiveGameTests.Client exact = buyer(helper);
		exact.player().getInventory().setItem(2, new ItemStack(Items.EMERALD, 2));
		helper.assertTrue(Shops.buy(exact.player(), shop.keeper(), 0), "2 emeralds didn't buy a 101 tag: " + QaStageFourGameTests.keys(exact.seen()));
		helper.assertTrue(exact.player().getInventory().countItem(Items.EMERALD) == 0 && exact.player().getInventory().countItem(Items.BREAD) == 4
			&& shop.chest().countItem(Items.EMERALD) == 2 && shop.chest().countItem(ModItems.PRICE_TAG) == 0,
			"buyer " + exact.player().getInventory().countItem(Items.EMERALD) + " emeralds, chest " + shop.chest().countItem(Items.EMERALD));
		QaStageFiveGameTests.Client shortOne = buyer(helper);
		shortOne.player().getInventory().setItem(2, new ItemStack(Items.EMERALD, 1));
		helper.assertFalse(Shops.buy(shortOne.player(), shop.keeper(), 0), "1 emerald bought a 2-emerald lot");
		helper.assertTrue(shortOne.player().getInventory().countItem(Items.EMERALD) == 1 && shortOne.player().getInventory().countItem(Items.BREAD) == 0,
			"the short buyer lost his emerald or got bread");
		Component costs = QaStageFourGameTests.find(shortOne.seen(), "message.aliveworkplace.shop.too_poor");
		helper.assertTrue(costs != null, "not told the price: " + QaStageFourGameTests.keys(shortOne.seen()));
		QaStageFourGameTests.assertRendered(helper, costs, "That costs", "2");
		Money.DOLLARS_PER_EMERALD = 50;
		helper.assertTrue(Shops.emeraldPrice(tag) == 3, "at 50 a piece 101 should cost 3, not " + Shops.emeraldPrice(tag));
		Shops.refreshOffers(helper.getLevel(), shop.keeper());
		helper.assertTrue(shop.keeper().getOffers().get(0).getCostA().is(Items.EMERALD) && shop.keeper().getOffers().get(0).getCostA().getCount() == 3,
			"the trade screen at 50 asks " + shop.keeper().getOffers().get(0).getCostA());
		helper.succeed();
	}

	// --- the counter broken, and saved ---------------------------------------------------------------------------------

	/**
	 * Mined, the counter "drops itself and the items in its price list" (the 16-cobblestone sample, the emerald price);
	 * the chest's stock stays. The shopkeeper then sells nothing: a buyer's attempt fails and he keeps his emeralds, a
	 * right-click opens no trades, and the shop screen says "This shopkeeper has no counter to sell from."
	 */
	//$ gametest_batch AREA '"qaEcoCounterBroken"'
	@GameTest(template = AREA, batch = "qaEcoCounterBroken")
	public void aBrokenCounterDropsItsListAndClosesTheShop(GameTestHelper helper) {
		Shop shop = shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1), new ItemStack(Items.COBBLESTONE, 64));
		helper.getLevel().destroyBlock(helper.absolutePos(COUNTER), true);
		AABB around = new AABB(helper.absolutePos(COUNTER)).inflate(2);
		Map<Item, Integer> drops = new HashMap<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
			drops.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
		}
		helper.assertTrue(drops.getOrDefault(ModBlocks.SHOP_COUNTER.asItem(), 0) == 1 && drops.getOrDefault(Items.COBBLESTONE, 0) == 16
			&& drops.getOrDefault(Items.EMERALD, 0) == 1, "drops: " + drops);
		helper.assertTrue(shop.chest().countItem(Items.COBBLESTONE) == 64, "the chest's stock changed: " + shop.chest().countItem(Items.COBBLESTONE));
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);

		QaStageFiveGameTests.Client c = buyer(helper);
		ServerPlayer p = c.player();
		p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 3));
		helper.assertFalse(Shops.buy(p, shop.keeper(), 0), "bought from a shopkeeper without a counter");
		QaStageFourGameTests.pressOn(p, shop.keeper(), c.seen());
		helper.assertFalse(p.containerMenu instanceof MerchantMenu && !((MerchantMenu) p.containerMenu).getOffers().isEmpty(),
			"a right-click still offers the old goods");
		p.closeContainer();
		Shops.openMenu(p, shop.keeper());
		Component closed = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.shop.closed");
		helper.assertTrue(closed != null, "the shop screen didn't say it's closed: " + QaStageFourGameTests.keys(c.seen()));
		QaStageFourGameTests.assertRendered(helper, closed, "no counter to sell from");
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 3 && p.getInventory().countItem(Items.COBBLESTONE) == 0
			&& shop.chest().countItem(Items.COBBLESTONE) == 64, "something moved: emeralds " + p.getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}

	/**
	 * Saved and reloaded (the chunk unloading), the counter keeps its owner, the whole price list (a named sample, a Price
	 * Tag), the sales log and its totals; the shopkeeper keeps the count of sales over his head.
	 */
	//$ gametest_batch AREA '"qaEcoCounterReload"'
	@GameTest(template = AREA, batch = "qaEcoCounterReload")
	public void aReloadKeepsThePriceListOwnerAndSales(GameTestHelper helper) {
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("250"));
		ItemStack named = new ItemStack(Items.IRON_SWORD);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Frank's Best"));
		Shop shop = shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.IRON_SWORD));
		ShopCounterBlockEntity counter = shop.counter();
		counter.setItem(8, named);
		counter.setItem(ShopCounterBlockEntity.COLUMNS + 8, tag);
		QaStageFiveGameTests.Client c = buyer(helper);
		c.player().getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
		helper.assertTrue(Shops.buy(c.player(), shop.keeper(), 0), "the sale before the save didn't go through");
		UUID owner = counter.owner();
		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = counter.saveWithFullMetadata(registries);
		BlockEntity loaded = BlockEntity.loadStatic(counter.getBlockPos(), counter.getBlockState(), saved, registries);
		helper.assertTrue(loaded instanceof ShopCounterBlockEntity, "the counter didn't load: " + loaded);
		ShopCounterBlockEntity back = (ShopCounterBlockEntity) loaded;
		helper.assertTrue(owner.equals(back.owner()) && "Frank".equals(back.ownerName()), "owner after a reload: " + back.owner() + " " + back.ownerName());
		helper.assertTrue(ItemStack.matches(back.goods(0), new ItemStack(Items.COBBLESTONE, 16)) && ItemStack.matches(back.price(0), new ItemStack(Items.EMERALD, 1)),
			"column 1 after a reload: " + back.goods(0) + " / " + back.price(0));
		helper.assertTrue(ItemStack.matches(back.goods(8), named) && ItemStack.matches(back.price(8), tag)
			&& io.github.jcondedata.aliveworkplace.shop.PriceTagItem.dollars(back.price(8)) == 250, "column 9 after a reload: " + back.goods(8) + " / " + back.price(8));
		helper.assertTrue(back.totalSales() == 1 && back.sales().size() == 1 && back.sales().get(0).buyer().equals(c.player().getGameProfile().getName())
			&& back.sales().get(0).goods().getCount() == 16 && back.sales().get(0).paid().is(Items.EMERALD), "sales after a reload: " + back.sales());
		CompoundTag villager = shop.keeper().saveWithoutId(new CompoundTag());
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(villager);
		helper.assertTrue(ModAttachments.SHOP_SALES.getOrElse(copy, 0) == 1 && Shops.isShopkeeper(copy), "the shopkeeper after a reload: sales "
			+ ModAttachments.SHOP_SALES.getOrElse(copy, 0) + ", " + copy.getVillagerData().getProfession());
		copy.discard();
		helper.succeed();
	}

	// --- the Storehouse's stock orders ---------------------------------------------------------------------------------

	/**
	 * Stock orders through the real board clicks: holding bread, the book's page offers "Order Bread (keep 16)"; each click
	 * on the order steps it 16, 32, 64, 128, 256 ("Click: keep 32" ... "Click: drop the order"), the next click drops it.
	 * The bread in hand never moves, and a saved and reloaded Storehouse keeps its orders.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaEcoOrders"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaEcoOrders")
	public void stockOrdersStepUpToTheMostThenDropAndSurviveAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos storehouse = new BlockPos(6, 2, 6);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(6, 2, 8), Blocks.CHEST);
		QaStageFiveGameTests.Client c = buyer(helper);
		ServerPlayer p = c.player();
		QaStageFourGameTests.press(helper, p, storehouse);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
		ChoiceMenu board = (ChoiceMenu) p.containerMenu;
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
		QaStageSixGameTests.click(p, StorehouseBoard.ORDERS_SLOT, 0, ClickType.PICKUP);
		helper.assertTrue(board.icon(StorehouseBoard.ORDERS_SLOT).getHoverName().getString().equals("Order Bread (keep 16)"),
			"the add button: " + board.icon(StorehouseBoard.ORDERS_SLOT).getHoverName().getString());
		QaStageSixGameTests.click(p, StorehouseBoard.ORDERS_SLOT, 0, ClickType.PICKUP);
		BlockPos abs = helper.absolutePos(storehouse);
		List<Integer> seenSteps = new ArrayList<>();
		for (int step = 0; step < StockOrders.STEPS.size(); step++) {
			Integer now = StockOrders.of(helper.getLevel(), abs).get(Items.BREAD);
			seenSteps.add(now);
			int at = QaStageSixGameTests.slotOf(board, Items.BREAD);
			helper.assertTrue(at >= StorehouseBoard.FIRST_REQUEST, "the order isn't listed at step " + step);
			String name = board.icon(at).getHoverName().getString();
			helper.assertTrue(name.equals("Keep " + StockOrders.STEPS.get(step) + " Bread"), "step " + step + ": " + name);
			List<String> lore = QaStageSixGameTests.lore(board.icon(at));
			String next = step + 1 < StockOrders.STEPS.size() ? "Click: keep " + StockOrders.STEPS.get(step + 1) : "Click: drop the order";
			helper.assertTrue(lore.contains(next), "step " + step + " lore: " + lore);
			if (step == 2) {
				var registries = helper.getLevel().registryAccess();
				BlockEntity be = helper.getLevel().getBlockEntity(abs);
				BlockEntity loaded = BlockEntity.loadStatic(abs, be.getBlockState(), be.saveWithFullMetadata(registries), registries);
				helper.assertTrue(loaded instanceof io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity sh && Integer.valueOf(64).equals(sh.orders().get(Items.BREAD)),
					"the order after a reload: " + (loaded instanceof io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity sh2 ? sh2.orders() : loaded));
			}
			QaStageSixGameTests.click(p, at, 0, ClickType.PICKUP);
		}
		helper.assertTrue(seenSteps.equals(StockOrders.STEPS), "the steps: " + seenSteps);
		helper.assertTrue(!StockOrders.of(helper.getLevel(), abs).containsKey(Items.BREAD), "after 256 the order should be dropped: " + StockOrders.of(helper.getLevel(), abs));
		helper.assertTrue(p.getMainHandItem().is(Items.BREAD) && p.getMainHandItem().getCount() == 3 && p.containerMenu.getCarried().isEmpty(),
			"the bread in hand moved: " + p.getMainHandItem() + ", carried " + p.containerMenu.getCarried());
		p.closeContainer();
		helper.succeed();
	}

	// --- luxuries from the village store -------------------------------------------------------------------------------

	private static final ResourceLocation TRINKET = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_trinket");
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	/**
	 * Two households (not a couple) both due their trinket, and the store holds just one: one takes it and has it noted,
	 * the other goes without and counts a missed dawn; the store is empty, never below. The next due day with a refilled
	 * store, the one who went without takes theirs.
	 */
	//$ gametest_batch AREA '"qaEcoLastLuxury"'
	@GameTest(template = AREA, batch = "qaEcoLastLuxury")
	public void theLastLuxuryGoesToOneHousehold(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		var level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(AliveWorkplace.id("peasant"), JsonParser.parseString("{\"tier\": 0}"));
		files.put(AliveWorkplace.id("artisan"), JsonParser.parseString("{\"tier\": 1, \"needs\": [{\"type\": \"luxury\", \"id\": \"" + TRINKET + "\"}]}"));
		SocialClasses.load(files);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(19, 2, 19), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(19, 2, 17), Blocks.CHEST);
		Container store = helper.getBlockEntity(new BlockPos(19, 2, 17));
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 1));
		Villager ann = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Villager bob = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 4));
		for (Villager v : List.of(ann, bob)) {
			v.setNoAi(true);
			SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id("artisan")));
		}
		ann.setCustomName(Component.literal("Ann"));
		bob.setCustomName(Component.literal("Bob"));
		SocialClasses.check(level, helper.absolutePos(HALL), List.of(ann, bob), 10, SocialClasses.SLICE);
		Map<ResourceLocation, Long> annHad = ModAttachments.LUXURIES_HAD.getOrElse(ann, Map.of());
		Map<ResourceLocation, Long> bobHad = ModAttachments.LUXURIES_HAD.getOrElse(bob, Map.of());
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 0, "the store has " + store.countItem(Items.DISC_FRAGMENT_5));
		helper.assertTrue(annHad.containsKey(TRINKET) != bobHad.containsKey(TRINKET), "exactly one household should have it: Ann " + annHad + ", Bob " + bobHad);
		Villager without = annHad.containsKey(TRINKET) ? bob : ann;
		Villager with = without == ann ? bob : ann;
		helper.assertTrue(SocialClasses.progress(without).missed() == 1 && SocialClasses.progress(with).missed() == 0,
			"missed: with " + SocialClasses.progress(with) + ", without " + SocialClasses.progress(without));
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 1));
		SocialClasses.check(level, helper.absolutePos(HALL), List.of(without), 11, SocialClasses.SLICE);
		helper.assertTrue(Long.valueOf(11).equals(ModAttachments.LUXURIES_HAD.getOrElse(without, Map.of()).get(TRINKET)) && store.countItem(Items.DISC_FRAGMENT_5) == 0,
			"the next day the one who went without should take it: " + ModAttachments.LUXURIES_HAD.getOrElse(without, Map.of()) + ", store " + store.countItem(Items.DISC_FRAGMENT_5));
		helper.succeed();
	}
}
