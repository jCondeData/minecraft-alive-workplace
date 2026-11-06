package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane-e-1106-2212, Create and saves): create.md's known limits say "What a moved ... Mailbox ... keeps of its
 * place in ... the post ... is not tested". A player's Mailbox pushed two blocks by Create's Mechanical Piston keeps its
 * owner (tested before); here the post: a parcel for its owner still has to arrive in it (mail.md: the post delivers
 * to the player's mailbox). The control: the same parcel into a mailbox nobody moved.
 */
public class QaCreateMovedMailboxPost1106eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TARGET = new BlockPos(7, 2, 8);

	private static void piston(GameTestHelper helper) {
		BlockPos piston = TARGET.east();
		helper.setBlock(piston.east(2), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston.east(), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_piston").defaultBlockState(), "facing", "west"),
			"axis_along_first", "true"));
		helper.setBlock(piston.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
	}

	private static float speed(GameTestHelper helper) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(TARGET.east()));
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	/** A Mailbox at {@link #TARGET} owned by a new player, put up as a player's is (registered with the post). */
	private static UUID mailbox(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		UUID owner = UUID.randomUUID();
		helper.setBlock(TARGET, ModBlocks.MAILBOX);
		MailboxBlockEntity box = (MailboxBlockEntity) helper.getBlockEntity(TARGET);
		box.setOwner(owner, "Jesse");
		PostOffice.get(helper.getLevel().getServer()).register(owner, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(TARGET)));
		return owner;
	}

	private static int diamondsIn(GameTestHelper helper, BlockPos pos) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c)) {
			return -1;
		}
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			n += c.getItem(i).is(Items.DIAMOND) ? c.getItem(i).getCount() : 0;
		}
		return n;
	}

	/** Posts 5 diamonds to {@code owner} and has the post deliver them now; returns whether it did. */
	private static boolean deliver(GameTestHelper helper, UUID owner) {
		PostOffice post = PostOffice.get(helper.getLevel().getServer());
		Parcel parcel = post.post(UUID.randomUUID(), "Ada", owner, "Jesse", GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BlockPos.ZERO)),
			helper.getLevel().getGameTime(), List.of(new ItemStack(Items.DIAMOND, 5)));
		boolean done = post.deliver(helper.getLevel().getServer(), parcel);
		post.remove(parcel);
		return done;
	}

	/** The control: a parcel for the owner of a mailbox nobody moved arrives in it. */
	//$ gametest_ticks_batch AREA '100' '"qa_moved_mailbox_control"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_moved_mailbox_control")
	public void aParcelArrivesInAMailboxNobodyMoved(GameTestHelper helper) {
		UUID owner = mailbox(helper);
		helper.assertTrue(deliver(helper, owner), "the post didn't deliver to a mailbox that stands where it was put up");
		helper.assertTrue(diamondsIn(helper, TARGET) == 5, "the mailbox holds " + diamondsIn(helper, TARGET) + " of the 5 diamonds");
		helper.succeed();
	}

	/** A Mailbox pushed two blocks west by a Mechanical Piston: the next parcel for its owner arrives in it there. */
	//$ gametest_ticks_batch AREA '400' '"qa_moved_mailbox_post"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_moved_mailbox_post")
	public void aParcelArrivesInAMailboxAPistonMoved(GameTestHelper helper) {
		UUID owner = mailbox(helper);
		piston(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(speed(helper) != 0, "the piston never turned, so the test proves nothing");
			BlockPos moved = TARGET.west(2);
			helper.assertTrue(helper.getBlockState(moved).is(ModBlocks.MAILBOX), "setup: the mailbox wasn't pushed two blocks west");
			helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(moved)) instanceof MailboxBlockEntity box && owner.equals(box.owner()),
				"setup: the moved mailbox lost its owner");
			GlobalPos registered = PostOffice.get(helper.getLevel().getServer()).mailboxOf(owner);
			boolean done = deliver(helper, owner);
			helper.assertTrue(done && diamondsIn(helper, moved) == 5, "a parcel for the owner of a Mailbox a Create piston moved two blocks didn't arrive in it"
				+ " (delivered " + done + ", " + diamondsIn(helper, moved) + " of 5 diamonds in it; the post has the owner's mailbox at "
				+ (registered == null ? "none" : registered.pos()) + ", it stands at " + helper.absolutePos(moved) + ")");
			helper.succeed();
		});
	}
}
