package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B525: hoppers can't reach a Mailbox (QaMailboxHopperBugTests), but the post still delivers, the owner still takes
 * mail out on the mailbox's screen, the screen still refuses items put into the inbox, and the mail survives a save.
 */
public class MailboxHopperGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** The post delivers, the owner opens the mailbox by using it and takes the mail out; the inbox stays take-only. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theOwnerStillTakesMailOnTheMailboxScreen(GameTestHelper helper) {
		BlockPos boxPos = new BlockPos(2, 2, 2);
		helper.setBlock(boxPos, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxPos);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(boxPos);
		helper.getBlockState(boxPos).useWithoutItem(helper.getLevel(), owner,
			new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
		helper.assertTrue(owner.getUUID().equals(box.owner()), "the first player to use the mailbox should own it");
		helper.assertTrue(owner.containerMenu instanceof MailboxMenu menu && menu.mailbox() == box,
			"using the mailbox should open its screen, not " + owner.containerMenu);

		helper.assertTrue(box.receive(new ItemStack(Items.DIAMOND, 3)).isEmpty(), "the post couldn't deliver 3 diamonds");
		MailboxMenu menu = (MailboxMenu) owner.containerMenu;
		menu.setCarried(ItemStack.EMPTY);
		menu.clicked(MailboxMenu.OUT, 0, ClickType.PICKUP, owner);
		helper.assertTrue(menu.getCarried().is(Items.DIAMOND) && menu.getCarried().getCount() == 3,
			"the owner should take the 3 diamonds out, not " + menu.getCarried());
		helper.assertTrue(box.isEmpty(), "the mailbox should be empty after the owner took the mail");

		menu.clicked(MailboxMenu.OUT, 0, ClickType.PICKUP, owner);
		helper.assertTrue(box.isEmpty(), "the inbox should stay take-only on the screen too");
		helper.assertTrue(menu.getCarried().getCount() == 3, "the refused diamonds should stay on the cursor");
		helper.succeed();
	}

	/** Mail in a mailbox (and its owner) survives a save and reload. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void mailSurvivesASave(GameTestHelper helper) {
		BlockPos boxPos = new BlockPos(2, 2, 2);
		helper.setBlock(boxPos, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(boxPos);
		java.util.UUID frank = java.util.UUID.randomUUID();
		box.setOwner(frank, "Frank");
		box.receive(new ItemStack(Items.DIAMOND, 3));
		box.receive(new ItemStack(Items.PAPER, 1));
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = box.saveWithoutMetadata(registries);

		MailboxBlockEntity copy = new MailboxBlockEntity(box.getBlockPos(), box.getBlockState());
		copy.loadWithComponents(tag, registries);
		helper.assertTrue(copy.countItem(Items.DIAMOND) == 3 && copy.countItem(Items.PAPER) == 1,
			"the reloaded mailbox should hold 3 diamonds and a paper");
		helper.assertTrue(frank.equals(copy.owner()) && "Frank".equals(copy.ownerName()), "the reloaded mailbox should still be Frank's");
		helper.assertTrue(copy.getSlotsForFace(Direction.DOWN).length == 0, "the reloaded mailbox should still offer no slot to hoppers");
		helper.succeed();
	}
}
