package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1026-1343), with Tom's Simple Storage from the pack: its inventory connector joins every inventory that
 * touches it into one store that any storage terminal opens, and the village's Storehouse takes from and fills it. A
 * Mailbox (B525: "only you, your friends and operators can open your mailbox") and a Shop Counter (B349: only they change
 * the prices) touching a connector must not be joined: nothing of theirs shows in the connector, nothing comes out
 * through it and nothing goes in. A chest touching the same connector is the control.
 */
public class QaTomsPrivateBoxesCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CONNECTOR = new BlockPos(6, 2, 6);
	private static final BlockPos CHEST = new BlockPos(7, 2, 6);
	private static final BlockPos MAILBOX = new BlockPos(5, 2, 6);
	private static final BlockPos COUNTER = new BlockPos(6, 2, 5);

	//$ gametest_ticks_batch AREA '200' '"qa_toms_private"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_toms_private")
	public void aConnectorJoinsNeitherAMailboxNorAShopCounter(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
		helper.setBlock(MAILBOX, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(MAILBOX);
		box.setOwner(UUID.randomUUID(), "Frank");
		helper.assertTrue(box.receive(new ItemStack(Items.DIAMOND, 3)).isEmpty(), "the post couldn't deliver 3 diamonds");
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Gina");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		helper.setBlock(CONNECTOR, TomsStorageCompatTests.block("inventory_connector"));
		BlockPos at = helper.absolutePos(CONNECTOR);
		level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), Block.UPDATE_ALL);

		helper.runAfterDelay(60, () -> {
			Storage<ItemVariant> store = ItemStorage.SIDED.find(level, at, null);
			helper.assertTrue(store != null, "Tom's connector has no item storage");
			helper.assertTrue(amount(store, Items.IRON_INGOT) == 5, "the control: the connector should show the chest's 5 iron, not " + amount(store, Items.IRON_INGOT));
			long diamonds = amount(store, Items.DIAMOND);
			long cobble = amount(store, Items.COBBLESTONE);
			long emeralds = amount(store, Items.EMERALD);
			helper.assertTrue(diamonds == 0, "Tom's connector shows Frank's mail: " + diamonds + " diamonds");
			helper.assertTrue(cobble == 0 && emeralds == 0, "Tom's connector shows Gina's price list: " + cobble + " cobblestone, " + emeralds + " emeralds");

			long taken;
			long put;
			try (Transaction tx = Transaction.openOuter()) {
				taken = store.extract(ItemVariant.of(Items.DIAMOND), 3, tx) + store.extract(ItemVariant.of(Items.EMERALD), 2, tx)
					+ store.extract(ItemVariant.of(Items.COBBLESTONE), 16, tx);
				tx.commit();
			}
			// fill the control chest's 26 free slots and more: whatever is left over must not land in the mailbox or counter
			try (Transaction tx = Transaction.openOuter()) {
				put = store.insert(ItemVariant.of(Items.ROTTEN_FLESH), 64L * 30, tx);
				tx.commit();
			}
			helper.assertTrue(taken == 0, "Tom's connector took " + taken + " of Frank's mail and Gina's prices");
			helper.assertTrue(box.countItem(Items.DIAMOND) == 3 && box.countItem(Items.ROTTEN_FLESH) == 0,
				"Frank's mailbox holds " + box.countItem(Items.DIAMOND) + " diamonds and " + box.countItem(Items.ROTTEN_FLESH) + " rotten flesh, not 3 and 0");
			helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16 && counter.price(0).is(Items.EMERALD)
				&& counter.price(0).getCount() == 2 && counter.countItem(Items.ROTTEN_FLESH) == 0,
				"Gina's price list changed: " + counter.goods(0) + " for " + counter.price(0) + ", " + counter.countItem(Items.ROTTEN_FLESH) + " rotten flesh");
			helper.assertTrue(put == 64L * 26, "the control: the chest's 26 free slots should take 1664 rotten flesh, not " + put);
			helper.succeed();
		});
	}

	private static long amount(Storage<ItemVariant> store, Item item) {
		long n = 0;
		for (StorageView<ItemVariant> view : store) {
			if (!view.isResourceBlank() && view.getResource().isOf(item)) {
				n += view.getAmount();
			}
		}
		return n;
	}
}
