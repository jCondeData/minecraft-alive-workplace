package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31), village protection against the Delivery Note. Guide village-protection.md: inside a protected village
 * only the owner, their friends and operators may "open chests and other blocks with a screen"; "Anyone else is told
 * whose village it is". A Delivery Note's first click on a chest names it as the courier route's source, so a postman
 * then empties it into the note's other container: as good as opening it. A stranger's note must not take a chest in a
 * protected village. Today DeliveryNoteItem.beforeBlock (registered by Mail.init, before VillageProtection.init) marks
 * the chest and ends the click before the protection check is asked.
 */
public class QaProtectedNoteBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
	}

	/** A stranger's Delivery Note can't name the owner's chest in his protected village; the owner's own note can. */
	//$ gametest_ticks_batch AREA '60' '"qaProtectedNote1031"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaProtectedNote1031")
	public void qaAStrangersDeliveryNoteCantTakeAChestInAProtectedVillage(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos chestAt = ProtectionSpecGameTests.HALL.offset(3, 0, 3);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 10));

		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		use(helper, stranger, chestAt, note);
		RouteData theirs = DeliveryNoteItem.data(stranger.getMainHandItem());
		helper.assertTrue(theirs.from().isEmpty(),
			"a stranger's Delivery Note took the owner's chest in his protected village as its source: " + theirs.from()
				+ " (a postman would then carry its 10 diamonds out)");

		// the owner's own note still works there
		ItemStack own = new ItemStack(ModItems.DELIVERY_NOTE);
		use(helper, owner, chestAt, own);
		helper.assertTrue(DeliveryNoteItem.data(owner.getMainHandItem()).from().isPresent(), "the owner's own note didn't take his chest");
		helper.succeed();
	}
}
