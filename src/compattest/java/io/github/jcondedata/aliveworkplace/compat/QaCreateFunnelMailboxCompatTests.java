package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-e-1026-1343), with Create, B525: a Mailbox's mail is its owner's (docs/guide/mailbox.md: "Only you, your
 * friends and operators can open your mailbox"). An extracting andesite funnel on top of a chest pulls its items out
 * and drops them (the control); the same funnel on someone's mailbox must take none of their mail.
 */
public class QaCreateFunnelMailboxCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '200' '"qa_create_funnel_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_create_funnel_mailbox")
	public void aCreateFunnelOnAMailboxTakesNoMail(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos boxAt = new BlockPos(4, 2, 4);
		helper.setBlock(boxAt, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxAt);
		box.setOwner(UUID.randomUUID(), "Frank");
		helper.assertTrue(box.receive(new ItemStack(Items.DIAMOND, 3)).isEmpty(), "the post couldn't deliver 3 diamonds");
		helper.setBlock(boxAt.above(), QaCreateFunnelCounterCompatTests.extractingFunnel());

		BlockPos chestAt = new BlockPos(12, 2, 4);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
		helper.setBlock(chestAt.above(), QaCreateFunnelCounterCompatTests.extractingFunnel());

		helper.runAfterDelay(120, () -> {
			helper.assertTrue(chest.isEmpty(), "the control: the funnel on the chest should have pulled its 3 diamonds out, it holds " + chest.getItem(0));
			int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(boxAt)).inflate(2)).size();
			helper.assertTrue(box.countItem(Items.DIAMOND) == 3,
				"a Create funnel on Frank's mailbox took his mail: it holds " + box.countItem(Items.DIAMOND) + " of his 3 diamonds, " + dropped + " items dropped by it");
			helper.succeed();
		});
	}
}
