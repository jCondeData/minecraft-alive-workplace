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
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31, lane-c-1031-2153), village protection and pistons. Guide village-protection.md: inside a protected
 * village only the owner, friends and operators may "break or place blocks". A stranger sets a piston down just outside
 * the edge, facing in (set down while standing inside, which a visitor may), and powers it with a redstone block, also outside (every click outside, so allowed): the piston
 * shoves the village's wall block one step deeper in and its head now stands inside. A row of pistons strips a wall.
 */
public class QaProtectedPistons1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
	}

	/** Hall at (11, 2, 11), reach 16: the village's plank at x = 27 (inside); the stranger's piston at x = 28 (outside). */
	//$ gametest_ticks_batch AREA '60' '"qaProtectedPistons1031"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaProtectedPistons1031")
	public void qaAStrangersPistonOutsideCantShoveTheVillagesBlocks(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos wall = new BlockPos(27, 2, 11); // the village's: 16 from the hall
		BlockPos piston = wall.east(); // 17: outside
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		// a visitor standing inside (allowed), looking east: the piston he sets down outside faces him, into the village
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(24, 2, 13)));
		stranger.moveTo(feet.x, feet.y, feet.z, -90f, 0f);
		stranger.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(piston)));
		click(helper, stranger, piston.below(), Direction.UP, new ItemStack(Items.PISTON));
		helper.assertTrue(helper.getBlockState(piston).is(Blocks.PISTON), "setup: the stranger couldn't set his piston down outside: " + helper.getBlockState(piston));
		helper.assertTrue(helper.getBlockState(piston).getValue(PistonBaseBlock.FACING) == Direction.WEST,
			"setup: the stranger's piston should face the village: " + helper.getBlockState(piston));
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, helper.absolutePos(wall)).isPresent()
			&& VillageProtection.keeper(helper.getLevel(), stranger, helper.absolutePos(piston)).isEmpty(), "setup: the wall should be inside, the piston outside");

		click(helper, stranger, piston.east().below(), Direction.UP, new ItemStack(Items.REDSTONE_BLOCK)); // power, outside too
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(wall).is(Blocks.OAK_PLANKS) && !helper.getBlockState(wall.west()).is(Blocks.OAK_PLANKS),
				"a stranger's piston outside the protected village shoved its wall block in: at the wall " + helper.getBlockState(wall)
					+ ", a step in " + helper.getBlockState(wall.west()));
			helper.succeed();
		});
	}
}
