package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1024-0353) bug tests for the village economy, each committed with its fix (B350, B351). Expectations as in
 * {@link QaEconomyGameTests}.
 */
public class QaEconomyBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * With CobbleDollars the shop's own screen buys "with the price items from their inventory". A column priced 3
	 * diamonds: a buyer with 2 in his bag and 1 in his off hand has 3, but was told "That costs 3x Diamond" and couldn't
	 * buy (B350). Expected: the off hand counts, as it does for emeralds, and pays.
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
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 0 && p.getOffhandItem().isEmpty() && p.getInventory().countItem(Items.OAK_LOG) == 8
				&& shop.chest().countItem(Items.DIAMOND) == 3,
			"after the sale: diamonds " + p.getInventory().countItem(Items.DIAMOND) + ", off hand " + p.getOffhandItem() + ", logs "
				+ p.getInventory().countItem(Items.OAK_LOG) + ", chest diamonds " + shop.chest().countItem(Items.DIAMOND));
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
