package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlock;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Mailboxes and postmen on a real (headless) server. */
public class MailGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static MailboxBlockEntity mailbox(GameTestHelper helper, BlockPos pos, UUID owner, String name) {
		helper.setBlock(pos, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(pos);
		box.setOwner(owner, name);
		PostOffice.get(helper.getLevel().getServer()).register(owner, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)));
		return box;
	}

	/** A parcel posted at one mailbox is collected by the postman and put in the other. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void postmanDeliversOnTheRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		BlockPos from = new BlockPos(6, 2, 2);
		BlockPos to = new BlockPos(14, 2, 14);
		mailbox(helper, from, alice, "Alice");
		MailboxBlockEntity bobs = mailbox(helper, to, bob, "Bob");
		PostOffice office = PostOffice.get(level.getServer());
		Parcel parcel = office.post(alice, "Alice", bob, "Bob", GlobalPos.of(level.dimension(), helper.absolutePos(from)), level.getGameTime(),
			List.of(new ItemStack(Items.COBBLESTONE, 5), new ItemStack(Items.APPLE, 2)));

		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);

		helper.succeedWhen(() -> {
			helper.assertTrue(bobs.countItem(Items.COBBLESTONE) == 5 && bobs.countItem(Items.APPLE) == 2, "Bob's mailbox is still empty");
			helper.assertBlockProperty(to, MailboxBlock.HAS_MAIL, true);
			helper.assertTrue(office.parcel(parcel.id()) == null, "the parcel is still on its way");
			helper.assertTrue(postman.getAttachedOrElse(ModAttachments.MAIL_DELIVERED, 0) == 1, "delivery not counted");
		});
	}

	/** Mail handed in for somewhere far away lands in the mailbox at dawn. */
	@GameTest(template = AREA)
	public void nightMailArrivesAtDawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID carol = UUID.randomUUID();
		MailboxBlockEntity carols = mailbox(helper, new BlockPos(4, 2, 4), carol, "Carol");
		PostOffice office = PostOffice.get(level.getServer());
		Parcel parcel = office.post(UUID.randomUUID(), "Dave", carol, "Carol", GlobalPos.of(level.dimension(), BlockPos.ZERO), level.getGameTime(),
			List.of(new ItemStack(Items.DIAMOND, 3)));
		parcel.setStatus(Parcel.Status.IN_TRANSIT);
		office.dawn(level.getServer());
		helper.assertTrue(carols.countItem(Items.DIAMOND) == 3, "the night mail did not arrive");
		helper.assertTrue(office.parcel(parcel.id()) == null, "the parcel is still on its way");
		helper.succeed();
	}
}
