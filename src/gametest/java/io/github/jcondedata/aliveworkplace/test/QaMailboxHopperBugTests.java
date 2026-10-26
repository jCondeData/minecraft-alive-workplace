package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
}
