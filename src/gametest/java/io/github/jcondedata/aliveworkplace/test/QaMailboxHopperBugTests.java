package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * QA (lane-e-1026-1016): a Mailbox's mail is its owner's. The guide (docs/guide/mailbox.md): "Only you, your friends and
 * operators can open your mailbox", and the inbox is take-only because "mail only comes in through the post"
 * (MailboxMenu). B349 closed the same hole on the Shop Counter. So a stranger's hopper under someone's mailbox must not
 * pull their parcels out, and a hopper on top must not push items into the inbox as if they were mail.
 */
public class QaMailboxHopperBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A hopper under a mailbox pulls no mail out in 60 ticks, while the same hopper under a chest beside it does. */
	//$ gametest_ticks_batch AREA '120' '"qaMailHopperBelow"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaMailHopperBelow")
	public void aHopperUnderAMailboxTakesNoMail(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos boxAt = new BlockPos(2, 3, 2);
		BlockPos hopperAt = new BlockPos(2, 2, 2);
		helper.setBlock(hopperAt, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		helper.setBlock(boxAt, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxAt);
		box.setOwner(UUID.randomUUID(), "Frank");
		helper.assertTrue(box.receive(new ItemStack(Items.DIAMOND, 3)).isEmpty(), "the post couldn't deliver 3 diamonds");

		// The control: the same hopper under a chest empties it, so the hoppers really run.
		BlockPos chestAt = new BlockPos(5, 3, 2);
		helper.setBlock(new BlockPos(5, 2, 2), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 2));

		helper.runAfterDelay(60, () -> {
			HopperBlockEntity hopper = helper.getBlockEntity(hopperAt);
			helper.assertTrue(chest.isEmpty(), "the control hopper should have emptied the chest");
			helper.assertTrue(hopper.isEmpty(), "a hopper under Frank's mailbox took his mail: " + hopper.getItem(0));
			helper.assertTrue(box.countItem(Items.DIAMOND) == 3, "Frank's mailbox should still hold his 3 diamonds, not " + box.countItem(Items.DIAMOND));
			helper.succeed();
		});
	}

	/** A hopper on top of a mailbox pushes nothing into the inbox; it keeps its items. */
	//$ gametest_ticks_batch AREA '120' '"qaMailHopperAbove"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaMailHopperAbove")
	public void aHopperOnAMailboxPostsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos boxAt = new BlockPos(2, 2, 2);
		BlockPos hopperAt = new BlockPos(2, 3, 2);
		helper.setBlock(boxAt, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxAt);
		box.setOwner(UUID.randomUUID(), "Frank");
		helper.setBlock(hopperAt, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		HopperBlockEntity hopper = helper.getBlockEntity(hopperAt);
		hopper.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 5));

		helper.runAfterDelay(60, () -> {
			helper.assertTrue(hopper.countItem(Items.ROTTEN_FLESH) == 5,
				"a hopper on Frank's mailbox posted " + (5 - hopper.countItem(Items.ROTTEN_FLESH)) + " rotten flesh into it");
			helper.assertTrue(box.isEmpty(), "Frank's inbox should be empty, not hold " + box.getItem(0));
			helper.succeed();
		});
	}

	/**
	 * Create's funnels and chutes, and every pipe mod on Fabric, reach a block through Fabric's transfer API
	 * ({@code ItemStorage.SIDED}), not through the hopper's own code. From every side, that way takes no
	 * mail out of someone's mailbox and posts nothing into the inbox; a chest beside it is the control.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaMailTransfer"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaMailTransfer")
	public void theTransferApiTakesNoMailAndPostsNothingFromAnySide(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos boxAt = new BlockPos(2, 2, 2);
		helper.setBlock(boxAt, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxAt);
		box.setOwner(UUID.randomUUID(), "Frank");
		helper.assertTrue(box.receive(new ItemStack(Items.DIAMOND, 3)).isEmpty(), "the post couldn't deliver 3 diamonds");

		// The control: the same calls on a chest take and put, so the calls themselves work.
		BlockPos chestAt = new BlockPos(5, 2, 2);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
		Storage<ItemVariant> chestStore = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(chestAt), Direction.UP);
		helper.assertTrue(chestStore != null, "the control chest has no item storage");
		helper.assertTrue(move(chestStore, false) == 1 && move(chestStore, true) == 1,
			"the control: the transfer API should take a diamond from a chest and put rotten flesh in");

		StringBuilder wrong = new StringBuilder();
		// Every face. A query from no side (null) is Fabric's unsided access, which ignores a WorldlyContainer's faces by
		// design; automation always asks from a face (Tom's connector: QaTomsPrivateBoxesCompatTests).
		for (Direction side : Direction.values()) {
			Storage<ItemVariant> store = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(boxAt), side);
			if (store == null) {
				continue;
			}
			long taken = move(store, false);
			long posted = move(store, true);
			if (taken != 0 || posted != 0) {
				wrong.append(side).append(": took ").append(taken).append(" diamond, posted ").append(posted).append(" rotten flesh; ");
			}
		}
		helper.assertTrue(wrong.length() == 0, "the transfer API (Create funnels, chutes, pipes) reaches Frank's mail: " + wrong);
		helper.assertTrue(box.countItem(Items.DIAMOND) == 3, "Frank's mailbox should still hold his 3 diamonds, not " + box.countItem(Items.DIAMOND));
		helper.assertTrue(box.countItem(Items.ROTTEN_FLESH) == 0, "Frank's inbox holds " + box.countItem(Items.ROTTEN_FLESH) + " rotten flesh");
		helper.succeed();
	}

	/** One diamond taken out ({@code insert} false) or one rotten flesh put in, committed; how many moved. */
	private static long move(Storage<ItemVariant> store, boolean insert) {
		try (Transaction tx = Transaction.openOuter()) {
			long moved = insert ? store.insert(ItemVariant.of(Items.ROTTEN_FLESH), 1, tx) : store.extract(ItemVariant.of(Items.DIAMOND), 1, tx);
			tx.commit();
			return moved;
		}
	}
}
