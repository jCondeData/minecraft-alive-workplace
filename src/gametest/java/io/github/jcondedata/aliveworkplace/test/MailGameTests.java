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

	/** A letter is a written book from the sender; /workplace mail tells both ends where a parcel is. */
	/**
	 * Parcels for a player with no mailbox wait at the post office through the dawn, and are picked up by right-clicking
	 * a Postal Desk.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void parcelsWaitAtThePostalDesk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PostOffice office = PostOffice.get(level.getServer());
		Parcel parcel = office.post(UUID.randomUUID(), "Alex", player.getUUID(), player.getGameProfile().getName(),
			GlobalPos.of(level.dimension(), helper.absolutePos(BlockPos.ZERO)), level.getGameTime(), List.of(new ItemStack(Items.DIAMOND, 3)));
		parcel.setStatus(Parcel.Status.IN_TRANSIT);
		office.dawn(level.getServer());
		helper.assertTrue(office.parcel(parcel.id()) != null, "a parcel for someone with no mailbox should wait, not vanish");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.mail.Mail.tracking(level.getServer(), player.getUUID()).get(0).getString().contains("Postal Desk"),
			"tracking should say where to pick it up");

		BlockPos desk = new BlockPos(0, 1, 0);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(desk);
		state.useWithoutItem(level, player, new net.minecraft.world.phys.BlockHitResult(
			net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(desk)), net.minecraft.core.Direction.UP, helper.absolutePos(desk), false));
		helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "the parcel's diamonds should be in the player's inventory");
		helper.assertTrue(office.parcel(parcel.id()) == null, "the parcel should be gone once picked up");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lettersAndTracking(GameTestHelper helper) {
		ItemStack letter = io.github.jcondedata.aliveworkplace.mail.Mail.letter("Alice", "See you at the market!");
		var content = letter.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(letter.is(Items.WRITTEN_BOOK) && content != null, "a letter should be a written book");
		helper.assertTrue(content.author().equals("Alice") && content.title().raw().equals("Letter from Alice"), "title/author: " + content.title().raw() + " / " + content.author());
		helper.assertTrue(content.pages().get(0).raw().getString().equals("See you at the market!"), "page: " + content.pages().get(0).raw().getString());

		ServerLevel level = helper.getLevel();
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		PostOffice office = PostOffice.get(level.getServer());
		Parcel parcel = office.post(alice, "Alice", bob, "Bob", GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(0, 1, 0))), level.getGameTime(),
			List.of(letter, new ItemStack(Items.APPLE, 2)));
		try {
			var sent = io.github.jcondedata.aliveworkplace.mail.Mail.tracking(level.getServer(), alice);
			var coming = io.github.jcondedata.aliveworkplace.mail.Mail.tracking(level.getServer(), bob);
			helper.assertTrue(sent.size() == 1 && sent.get(0).getString().equals("To Bob: 3 items, waiting in the sender's mailbox for a postman"),
				"Alice sees: " + sent.stream().map(net.minecraft.network.chat.Component::getString).toList());
			helper.assertTrue(coming.size() == 1 && coming.get(0).getString().startsWith("From Alice: 3 items"),
				"Bob sees: " + coming.stream().map(net.minecraft.network.chat.Component::getString).toList());
			helper.assertTrue(io.github.jcondedata.aliveworkplace.mail.Mail.tracking(level.getServer(), UUID.randomUUID()).isEmpty(), "a stranger sees mail");
		} finally {
			office.remove(parcel);
		}
		helper.succeed();
	}

	/** A mailbox next to a worker's chests is not one of them: nobody takes mail out or drops a haul in. */
	@GameTest(template = AREA)
	public void workersLeaveMailboxesAlone(GameTestHelper helper) {
		MailboxBlockEntity box = mailbox(helper, new BlockPos(4, 2, 2), UUID.randomUUID(), "Erin");
		box.receive(new ItemStack(Items.DIAMOND, 2));
		helper.setBlock(new BlockPos(2, 2, 4), net.minecraft.world.level.block.Blocks.CHEST);
		var found = io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), null);
		helper.assertTrue(found.size() == 1 && found.get(0).equals(helper.absolutePos(new BlockPos(2, 2, 4))), "supply containers: " + found);
		helper.succeed();
	}

	/** With no mail about, a postman runs courier routes: cobblestone goes over, the pickaxe stays. */
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void postmanRunsACourierRoute(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos from = new BlockPos(12, 2, 4);
		BlockPos to = new BlockPos(4, 2, 12);
		helper.setBlock(from, net.minecraft.world.level.block.Blocks.CHEST);
		helper.setBlock(to, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container source = helper.getBlockEntity(from);
		source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		source.setItem(1, new ItemStack(Items.COBBLESTONE, 30));
		source.setItem(2, new ItemStack(Items.IRON_PICKAXE));
		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		postman.setAttached(ModAttachments.COURIER_ROUTES, List.of(new io.github.jcondedata.aliveworkplace.mail.RouteData(
			java.util.Optional.of(helper.absolutePos(from)), java.util.Optional.of(helper.absolutePos(to)), List.of())));
		net.minecraft.world.Container target = helper.getBlockEntity(to);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.countItem(Items.COBBLESTONE) == 94, target.countItem(Items.COBBLESTONE) + " cobblestone delivered");
			helper.assertTrue(source.countItem(Items.IRON_PICKAXE) == 1, "the pickaxe should stay");
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
