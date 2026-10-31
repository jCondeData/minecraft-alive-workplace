package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31, lane-c-1031-2153), village protection at its edge. Guide village-protection.md: inside the hall's area
 * only the owner, friends and operators may "break or place blocks". A block is placed beside the face that is clicked,
 * so a stranger outside who clicks the inward face of a block just outside the edge places a block just inside it. The
 * clicks go through ServerPlayerGameMode.useItemOn, as the client's use-item-on packet does.
 */
public class QaProtectionEdge1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
	}

	/** Hall at (11, 2, 11), reach 16: x = 27 is inside, x = 28 outside. A stranger clicks the west face of a block at x = 28. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionEdge1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionEdge1031")
	public void qaAStrangerOutsideCantPlaceABlockJustInsideTheEdge(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos post = new BlockPos(28, 2, 11); // outside (17 blocks east of the hall)
		BlockPos inside = post.west(); // 16 blocks east of the hall: inside
		helper.setBlock(post, Blocks.OAK_LOG);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(30, 2, 12)));
		stranger.moveTo(feet.x, feet.y, feet.z, 90f, 0f);
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, helper.absolutePos(inside)).isPresent(),
			"setup: the cell west of the post should be inside the protected village");
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, helper.absolutePos(post)).isEmpty(),
			"setup: the post should be outside the protected village");

		// control: clicking the post's east face (placing outside) works for the stranger
		click(helper, stranger, post, Direction.EAST, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(post.east()).is(Blocks.COBBLESTONE),
			"setup: a stranger's block placed outside the village didn't land: " + helper.getBlockState(post.east()));

		click(helper, stranger, post, Direction.WEST, new ItemStack(Items.TNT, 4));
		helper.assertTrue(!helper.getBlockState(inside).is(Blocks.TNT),
			"a stranger standing outside placed TNT inside the protected village (16 blocks from the hall) by clicking the inward face of a block just outside its edge");
		helper.succeed();
	}
}
