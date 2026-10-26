package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1026-1343), from B349's promise (docs/guide/shop-counter.md: only the owner, friends and operators change
 * the price list): the hopper tests ({@code ShopCounterHopperGameTests}) prove vanilla's hopper code can't reach it, but
 * Create's funnels and chutes, and every pipe mod on Fabric, reach a block through Fabric's transfer API
 * ({@code ItemStorage.SIDED}) instead. From every side, that way takes no sample or price out and puts nothing in.
 */
public class QaShopCounterTransferGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final Direction[] SIDES = Direction.values();

	//$ gametest_ticks_batch AREA '40' '"qaShopTransfer"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaShopTransfer")
	public void theTransferApiTakesAndPutsNothingOnACounterFromAnySide(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterAt = new BlockPos(2, 2, 2);
		helper.setBlock(counterAt, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterAt);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));

		// The control: the same calls on a chest take and put, so the calls themselves work.
		BlockPos chestAt = new BlockPos(5, 2, 2);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.EMERALD, 2));
		Storage<ItemVariant> chestStore = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(chestAt), Direction.DOWN);
		helper.assertTrue(chestStore != null, "the control chest has no item storage");
		helper.assertTrue(move(chestStore, Items.EMERALD, false) == 1 && move(chestStore, Items.ROTTEN_FLESH, true) == 1,
			"the control: the transfer API should take an emerald from a chest and put rotten flesh in");

		StringBuilder wrong = new StringBuilder();
		for (Direction side : SIDES) {
			Storage<ItemVariant> store = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(counterAt), side);
			if (store == null) {
				continue;
			}
			long sample = move(store, Items.COBBLESTONE, false);
			long price = move(store, Items.EMERALD, false);
			long put = move(store, Items.ROTTEN_FLESH, true);
			if (sample + price + put != 0) {
				wrong.append(side).append(": took ").append(sample).append(" cobblestone and ").append(price)
					.append(" emerald, put in ").append(put).append(" rotten flesh; ");
			}
		}
		helper.assertTrue(wrong.length() == 0, "the transfer API (Create funnels, chutes, pipes) changes Frank's price list: " + wrong);
		helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16,
			"the sample should still be 16 cobblestone, not " + counter.goods(0));
		helper.assertTrue(counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 2,
			"the price should still be 2 emeralds, not " + counter.price(0));
		helper.assertTrue(counter.countItem(Items.ROTTEN_FLESH) == 0, "Frank's counter holds " + counter.countItem(Items.ROTTEN_FLESH) + " rotten flesh");
		helper.succeed();
	}

	/** One of {@code item} put in ({@code insert}) or taken out, committed; how many moved. */
	private static long move(Storage<ItemVariant> store, Item item, boolean insert) {
		try (Transaction tx = Transaction.openOuter()) {
			long moved = insert ? store.insert(ItemVariant.of(item), 1, tx) : store.extract(ItemVariant.of(item), 1, tx);
			tx.commit();
			return moved;
		}
	}
}
