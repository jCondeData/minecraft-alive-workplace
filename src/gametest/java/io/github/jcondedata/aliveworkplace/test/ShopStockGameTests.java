package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * B348: a shopkeeper's vanilla trade screen sells only what the shop's chests hold at the moment of the trade, through
 * the player's own clicks (a real connection, {@link QaStageSixGameTests#client}).
 */
public class ShopStockGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos KEEPER = new BlockPos(4, 2, 2);
	/** The trade screen's slots: the two payments and the goods. */
	private static final int PAY = 0;
	private static final int RESULT = 2;

	record Shop(ShopCounterBlockEntity counter, Container chest, Villager keeper) {
	}

	/** A counter selling 16 cobblestone for an emerald, a chest by it holding {@code stock} cobblestone, and its shopkeeper. */
	private static Shop shop(GameTestHelper helper, int stock) {
		Leftovers.clear(helper);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; stock > 0; i++, stock -= 64) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, Math.min(64, stock)));
		}
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		return new Shop(counter, chest, keeper);
	}

	private static ServerPlayer buyer(GameTestHelper helper) {
		return QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("shopStock")).player();
	}

	/** The buyer right-clicks the shopkeeper; the trade screen must open. */
	private static MerchantMenu open(GameTestHelper helper, ServerPlayer p, Shop shop) {
		QaStageFourGameTests.pressOn(p, shop.keeper(), new java.util.ArrayList<>());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the right-click didn't open the trades: " + p.containerMenu);
		return (MerchantMenu) p.containerMenu;
	}

	/** The buyer puts {@code emeralds} in the payment slot and shift-clicks the goods; returns the cobblestone they then hold. */
	private static int payAndTake(ServerPlayer p, MerchantMenu menu, int emeralds) {
		menu.getSlot(PAY).set(new ItemStack(Items.EMERALD, emeralds));
		return take(p);
	}

	private static int take(ServerPlayer p) {
		QaStageSixGameTests.click(p, RESULT, 0, ClickType.QUICK_MOVE);
		return carried(p, Items.COBBLESTONE);
	}

	private static int carried(ServerPlayer p, Item item) {
		return p.getInventory().countItem(item) + (p.containerMenu.getCarried().is(item) ? p.containerMenu.getCarried().getCount() : 0);
	}

	/** The reported case: the owner empties the chest while the buyer looks; the emerald then buys nothing. */
	//$ gametest_batch AREA '"shopStockEmptied"'
	@GameTest(template = AREA, batch = "shopStockEmptied")
	public void stockTakenWhileTheTradeScreenIsOpenMakesNoGoodsFromNothing(GameTestHelper helper) {
		Shop shop = shop(helper, 32);
		ServerPlayer p = buyer(helper);
		MerchantMenu menu = open(helper, p, shop);
		shop.chest().clearContent();
		int got = payAndTake(p, menu, 1);
		boolean soldOut = shop.keeper().getOffers().get(0).isOutOfStock();
		p.closeContainer();
		helper.assertTrue(got == 0, "with the chest emptied the buyer still got " + got + " cobblestone from nowhere");
		helper.assertTrue(soldOut, "the emptied lot doesn't show as sold out");
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 1 && shop.chest().countItem(Items.EMERALD) == 0,
			"the emerald should come back to the buyer (bag " + p.getInventory().countItem(Items.EMERALD) + ", chest " + shop.chest().countItem(Items.EMERALD) + ")");
		helper.succeed();
	}

	/** The goods already show in the result slot when the owner empties the chest: the click takes nothing, the emerald stays. */
	//$ gametest_batch AREA '"shopStockAfterPay"'
	@GameTest(template = AREA, batch = "shopStockAfterPay")
	public void stockTakenAfterThePaymentIsInHandsOverNothing(GameTestHelper helper) {
		Shop shop = shop(helper, 32);
		ServerPlayer p = buyer(helper);
		MerchantMenu menu = open(helper, p, shop);
		menu.getSlot(PAY).set(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.COBBLESTONE), "the goods didn't show for the emerald: " + menu.getSlot(RESULT).getItem());
		shop.chest().clearContent();
		int got = take(p);
		ItemStack left = menu.getSlot(PAY).getItem().copy();
		ItemStack result = menu.getSlot(RESULT).getItem().copy();
		boolean soldOut = shop.keeper().getOffers().get(0).isOutOfStock();
		p.closeContainer();
		helper.assertTrue(got == 0, "the emptied shop still handed over " + got + " cobblestone");
		helper.assertTrue(left.is(Items.EMERALD) && left.getCount() == 1, "the emerald was spent on nothing: " + left);
		helper.assertTrue(result.isEmpty() && soldOut, "the lot still shows (" + result + "), sold out " + soldOut);
		helper.succeed();
	}

	/** 24 in the chest is one lot of 16: two emeralds buy one lot, and the second emerald stays unspent. */
	//$ gametest_batch AREA '"shopStockPartial"'
	@GameTest(template = AREA, batch = "shopStockPartial")
	public void lessThanALotLeftIsNotSold(GameTestHelper helper) {
		Shop shop = shop(helper, 24);
		ServerPlayer p = buyer(helper);
		MerchantMenu menu = open(helper, p, shop);
		int got = payAndTake(p, menu, 2);
		ItemStack left = menu.getSlot(PAY).getItem().copy();
		p.closeContainer();
		helper.assertTrue(got == 16, "expected one lot of 16 cobblestone, got " + got);
		helper.assertTrue(left.is(Items.EMERALD) && left.getCount() == 1, "the second emerald should be left unspent: " + left);
		helper.assertTrue(shop.chest().countItem(Items.COBBLESTONE) == 8 && shop.chest().countItem(Items.EMERALD) == 1,
			"the chest should hold the 8 left and 1 emerald: " + shop.chest().countItem(Items.COBBLESTONE) + " cobblestone, "
				+ shop.chest().countItem(Items.EMERALD) + " emeralds");
		helper.succeed();
	}

	/** Emptied, then restocked: talking to the shopkeeper again sells the new stock. */
	//$ gametest_batch AREA '"shopStockRestocked"'
	@GameTest(template = AREA, batch = "shopStockRestocked")
	public void aRestockedShopSellsAgain(GameTestHelper helper) {
		Shop shop = shop(helper, 32);
		ServerPlayer p = buyer(helper);
		MerchantMenu menu = open(helper, p, shop);
		shop.chest().clearContent();
		int first = payAndTake(p, menu, 1);
		p.closeContainer();
		helper.assertTrue(first == 0, "the empty shop sold " + first);
		shop.chest().setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		menu = open(helper, p, shop);
		int got = payAndTake(p, menu, 1);
		p.closeContainer();
		helper.assertTrue(got == 16, "the restocked shop sold " + got + " cobblestone, expected 16");
		helper.assertTrue(shop.chest().countItem(Items.COBBLESTONE) == 0 && shop.chest().countItem(Items.EMERALD) == 1,
			"the sale didn't come out of the chest: " + shop.chest().countItem(Items.COBBLESTONE) + " cobblestone, "
				+ shop.chest().countItem(Items.EMERALD) + " emeralds");
		helper.succeed();
	}

	/** A normal sale through the screen: 16 cobblestone out of the chest, the emerald into it. */
	//$ gametest_batch AREA '"shopStockNormal"'
	@GameTest(template = AREA, batch = "shopStockNormal")
	public void aNormalSaleTakesTheGoodsFromTheChest(GameTestHelper helper) {
		Shop shop = shop(helper, 32);
		ServerPlayer p = buyer(helper);
		MerchantMenu menu = open(helper, p, shop);
		int got = payAndTake(p, menu, 1);
		p.closeContainer();
		helper.assertTrue(got == 16, "bought " + got + " cobblestone, expected 16");
		helper.assertTrue(shop.chest().countItem(Items.COBBLESTONE) == 16 && shop.chest().countItem(Items.EMERALD) == 1,
			"the chest should hold 16 cobblestone and the emerald: " + shop.chest().countItem(Items.COBBLESTONE) + ", "
				+ shop.chest().countItem(Items.EMERALD));
		helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 0, "the emerald came back after the sale");
		helper.succeed();
	}
}
