package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1024-0353) bug tests for the village economy: each fails on today's code for the reason in its name and
 * goes to the builder with its fix. Not registered in fabric.mod.json until then. Expectations as in
 * {@link QaEconomyGameTests}.
 */
public class QaEconomyBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * "What's for sale is always what's in the chests." A buyer has the trade screen open on a lot of 16 cobblestone
	 * (32 in the chest); the owner empties the chest; the buyer still takes 16 cobblestone that came from nowhere (the
	 * sale takes nothing from the chest), so goods are duplicated. Expected: no goods without stock.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaEcoBugEmptied"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaEcoBugEmptied")
	public void stockTakenWhileTheTradeScreenIsOpenMakesNoGoodsFromNothing(GameTestHelper helper) {
		QaEconomyGameTests.Shop shop = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1),
			new ItemStack(Items.COBBLESTONE, 32));
		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		QaStageFourGameTests.pressOn(p, shop.keeper(), c.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the right-click didn't open the trades: " + p.containerMenu);
		MerchantMenu menu = (MerchantMenu) p.containerMenu;
		shop.chest().clearContent(); // the owner takes his stock back while the buyer looks
		menu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		int got = QaEconomyGameTests.carried(p, Items.COBBLESTONE);
		p.closeContainer();
		helper.assertTrue(got == 0, "with the chest emptied the buyer still got " + got + " cobblestone from nowhere (the chest has "
			+ shop.chest().countItem(Items.COBBLESTONE) + ")");
		helper.succeed();
	}

	/**
	 * "Only you, your friends and operators can change the prices." A hopper under someone's Shop Counter pulls the
	 * price list out of it (the goods' sample and the price), and one above pushes its items in as new prices. Expected:
	 * the counter's list stays as its owner set it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaEcoBugHopper"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaEcoBugHopper")
	public void aHopperCannotEmptyOrFillAShopCountersPriceList(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterPos = new BlockPos(3, 3, 3);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.HOPPER);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 20));
		helper.setBlock(new BlockPos(3, 4, 3), Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.DOWN));
		Container above = helper.getBlockEntity(new BlockPos(3, 4, 3));
		above.setItem(0, new ItemStack(Items.DIRT, 4));
		helper.runAfterDelay(80, () -> {
			Container below = helper.getBlockEntity(new BlockPos(3, 2, 3));
			helper.assertTrue(counter.goods(0).is(Items.DIAMOND_SWORD) && counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 20,
				"the hopper below took the price list: goods " + counter.goods(0) + ", price " + counter.price(0) + "; the hopper holds "
					+ below.countItem(Items.DIAMOND_SWORD) + " swords, " + below.countItem(Items.EMERALD) + " emeralds");
			helper.assertTrue(above.countItem(Items.DIRT) == 4 && counter.countItem(Items.DIRT) == 0,
				"the hopper above put " + counter.countItem(Items.DIRT) + " dirt into the price list");
			helper.succeed();
		});
	}

	/**
	 * With CobbleDollars the shop's own screen buys "with the price items from their inventory". A column priced 3
	 * diamonds: a buyer with 2 in his bag and 1 in his off hand has 3, but is told "That costs 3× Diamond" and can't buy.
	 * Expected: the off hand counts, as it does for emeralds.
	 */
	//$ gametest_batch AREA '"qaEcoBugOffhandPay"'
	@GameTest(template = AREA, batch = "qaEcoBugOffhandPay")
	public void theOffHandsPriceItemsCountWhenBuying(GameTestHelper helper) {
		QaEconomyGameTests.Shop shop = QaEconomyGameTests.shop(helper, new ItemStack(Items.OAK_LOG, 8), new ItemStack(Items.DIAMOND, 3),
			new ItemStack(Items.OAK_LOG, 64));
		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		p.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 2));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 1));
		boolean bought = Shops.buy(p, shop.keeper(), 0);
		helper.assertTrue(bought, "3 diamonds (2 in the bag, 1 in the off hand) didn't buy a 3-diamond lot: " + QaStageFourGameTests.keys(c.seen()));
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 0 && p.getInventory().countItem(Items.OAK_LOG) == 8 && shop.chest().countItem(Items.DIAMOND) == 3,
			"after the sale: diamonds " + p.getInventory().countItem(Items.DIAMOND) + ", logs " + p.getInventory().countItem(Items.OAK_LOG));
		helper.succeed();
	}

	/**
	 * The Storehouse's orders page: "Hold an item and click it (top right) to order it". Bread held in the off hand (main
	 * hand empty) gets no "Order Bread" button, though the same bread in the main hand does. Expected: as from the main
	 * hand (B341/B342 made the other held-item clicks work from the off hand).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaEcoBugOffhandOrder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaEcoBugOffhandOrder")
	public void anItemInTheOffHandCanBeOrdered(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos storehouse = new BlockPos(6, 2, 6);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(6, 2, 8), Blocks.CHEST);
		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		QaStageFourGameTests.press(helper, p, storehouse);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
		ChoiceMenu board = (ChoiceMenu) p.containerMenu;
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD, 3));
		QaStageSixGameTests.click(p, StorehouseBoard.ORDERS_SLOT, 0, ClickType.PICKUP);
		ItemStack add = board.icon(StorehouseBoard.ORDERS_SLOT);
		p.closeContainer();
		helper.assertTrue(add.is(Items.BREAD), "bread in the off hand gets no order button: " + add + " (" + add.getHoverName().getString() + ")");
		helper.succeed();
	}
}
