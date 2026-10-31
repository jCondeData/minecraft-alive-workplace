package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-31), the Shop Counter on a market street: two players' shops 6 blocks apart, each with its own chest by its
 * own counter (guide shop-counter.md: "you set the prices, stock the chests, and other players buy from you"; "the
 * payment lands in those chests"). Frank's shop sells cobblestone and his chest is empty; Bob's chest, by Bob's counter,
 * holds Bob's 32 cobblestone. A buyer at Frank's shopkeeper must not be sold Bob's stock: the counter's goods and takings
 * are every store within 8 blocks of it (Shops.onSale and refreshOffers use SupplyContainers.find round the counter),
 * whoever's shop they stand by.
 */
public class QaShopNeighboursBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Frank's empty shop offers nothing, though Bob's stocked chest by Bob's own counter is 6 blocks off. */
	//$ gametest_ticks_batch AREA '200' '"qaShopNeighbours1031"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaShopNeighbours1031")
	public void qaAShopNeverSellsFromTheNextShopsChest(GameTestHelper helper) {
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1));
		// Bob's shop next door: his counter 6 blocks east of Frank's, his chest right by it with his cobblestone
		BlockPos bobCounter = new BlockPos(8, 2, 2);
		BlockPos bobChest = new BlockPos(8, 2, 4);
		helper.setBlock(bobCounter, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity bob = helper.getBlockEntity(bobCounter);
		bob.setOwner(UUID.randomUUID(), "Bob");
		bob.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		bob.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		helper.setBlock(bobChest, Blocks.CHEST);
		Container bobs = helper.getBlockEntity(bobChest);
		bobs.setItem(0, new ItemStack(Items.COBBLESTONE, 32));

		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));
		QaStageFourGameTests.pressOn(p, frank.keeper(), c.seen());
		if (p.containerMenu instanceof MerchantMenu menu && !menu.getOffers().isEmpty() && !menu.getOffers().get(0).isOutOfStock()) {
			p.getInventory().removeItem(9, 1);
			menu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
			QaStageSixGameTests.click(p, 2, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
			p.closeContainer();
		}
		helper.assertTrue(bobs.countItem(Items.COBBLESTONE) == 32 && QaEconomyGameTests.carried(p, Items.COBBLESTONE) == 0,
			"Frank's shop (empty chest) sold the buyer " + QaEconomyGameTests.carried(p, Items.COBBLESTONE) + " cobblestone out of Bob's chest by Bob's counter ("
				+ bobs.countItem(Items.COBBLESTONE) + " of Bob's 32 left; Frank's chest now holds " + frank.chest().countItem(Items.EMERALD) + " emeralds)");
		helper.succeed();
	}
}
