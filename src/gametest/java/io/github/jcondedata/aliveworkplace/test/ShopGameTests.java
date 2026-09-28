package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

/** Player-run shops on a real (headless) server. */
public class ShopGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Offers come from the price list and the stock; a sale moves the goods out and the payment in. */
	@GameTest(template = AREA)
	public void shopkeeperSellsFromTheChests(GameTestHelper helper) {
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		counter.setItem(1, new ItemStack(Items.BREAD, 4));           // priced, but none in stock
		counter.setItem(ShopCounterBlockEntity.COLUMNS + 1, new ItemStack(Items.EMERALD, 1));
		counter.setItem(2, new ItemStack(Items.OAK_LOG, 8));         // in stock, but no price
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 64));

		Villager shopkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), shopkeeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		// Talking to the shopkeeper builds the offers (the mixin on Villager.mobInteract).
		net.minecraft.server.level.ServerPlayer customer = helper.makeMockServerPlayerInLevel();
		customer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		shopkeeper.mobInteract(customer, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(shopkeeper.isTrading(), "the shopkeeper did not start trading");
		helper.assertTrue(shopkeeper.getOffers().size() == 1, shopkeeper.getOffers().size() + " offers, expected just the cobblestone");
		MerchantOffer offer = shopkeeper.getOffers().get(0);
		helper.assertTrue(offer.getResult().is(Items.COBBLESTONE) && offer.getResult().getCount() == 16, "wrong goods");
		helper.assertTrue(offer.getMaxUses() == 4, "64 in stock / 16 a sale should allow 4 sales, not " + offer.getMaxUses());

		shopkeeper.notifyTrade(offer); // what the trade screen calls after a purchase (the mixin on AbstractVillager)
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 48, chest.countItem(Items.COBBLESTONE) + " cobblestone left");
		helper.assertTrue(chest.countItem(Items.EMERALD) == 1, "payment not in the chest");
		helper.assertTrue(counter.goods(0).getCount() == 16, "the price list lost its sample");
		helper.assertTrue(shopkeeper.getAttachedOrElse(ModAttachments.SHOP_SALES, 0) == 1, "sale not counted");
		var log = counter.sales();
		helper.assertTrue(log.size() == 1 && log.get(0).goods().is(Items.COBBLESTONE) && log.get(0).paid().is(Items.EMERALD)
			&& log.get(0).buyer().equals(customer.getGameProfile().getName()), "sales log: " + log);
		helper.assertFalse(SupplyContainers.find(helper.getLevel(), helper.absolutePos(COUNTER), null).contains(helper.absolutePos(COUNTER)),
			"the price list must not count as stock");
		helper.succeed();
	}
}
