package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/**
 * B745: pistons and a protected village. The hall is at (11, 2, 11) with a reach of 16, so x = 27 is the village's last
 * column and x = 28 is outside (on the hall's row, z = 11). A piston outside can't push into the village, pull out of it or put its head inside;
 * one inside works as before, and with protection switched off on the server an outside one pushes as in vanilla.
 */
public class PistonProtectionGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	static void piston(GameTestHelper helper, Block block, BlockPos pos, Direction facing) {
		helper.setBlock(pos, block.defaultBlockState().setValue(PistonBaseBlock.FACING, facing));
	}

	/** The owner's own piston inside the village still pushes (inside to inside). */
	//$ gametest_ticks_batch AREA '60' '"pistonInsideStillPushes"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "pistonInsideStillPushes")
	public void pistonInsideStillPushes(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		BlockPos at = new BlockPos(20, 2, 5);
		piston(helper, Blocks.PISTON, at, Direction.EAST);
		helper.setBlock(at.east(), Blocks.OAK_PLANKS);
		helper.setBlock(at.west(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, at.east(2));
			helper.assertBlockPresent(Blocks.PISTON_HEAD, at.east());
			helper.succeed();
		});
	}

	/** Protection switched off on the server: the outside piston pushes the edge block in, as in vanilla. */
	//$ gametest_ticks_batch AREA '60' '"pistonOutsidePushesWithProtectionOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "pistonOutsidePushesWithProtectionOff")
	public void pistonOutsidePushesWithProtectionOff(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		VillageProtection.ENABLED = false; // put back by protectedHall's cleanup
		BlockPos wall = new BlockPos(27, 2, 11);
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		piston(helper, Blocks.PISTON, wall.east(), Direction.WEST);
		helper.setBlock(wall.east(2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, wall.west());
			helper.assertBlockPresent(Blocks.PISTON_HEAD, wall);
			helper.succeed();
		});
	}

	/** An outside piston facing in with nothing in front doesn't put its head inside. */
	//$ gametest_ticks_batch AREA '60' '"pistonOutsideKeepsItsHeadOut"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "pistonOutsideKeepsItsHeadOut")
	public void pistonOutsideKeepsItsHeadOut(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		BlockPos at = new BlockPos(28, 2, 11);
		piston(helper, Blocks.PISTON, at, Direction.WEST);
		helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertBlockPresent(Blocks.AIR, at.west());
			helper.assertTrue(!helper.getBlockState(at).getValue(PistonBaseBlock.EXTENDED), "the outside piston extended into the village");
			helper.succeed();
		});
	}

	/** A sticky piston outside, its head just outside the edge, can't pull the village's edge block out. */
	//$ gametest_ticks_batch AREA '80' '"stickyPistonOutsideCantPullOut"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "stickyPistonOutsideCantPullOut")
	public void stickyPistonOutsideCantPullOut(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		BlockPos wall = new BlockPos(27, 2, 11);
		BlockPos at = wall.east(2); // 29: its head goes to 28, outside
		piston(helper, Blocks.STICKY_PISTON, at, Direction.WEST);
		helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertBlockPresent(Blocks.PISTON_HEAD, wall.east());
			helper.setBlock(wall, Blocks.OAK_PLANKS);
			helper.setBlock(at.east(), Blocks.AIR); // unpowered: it retracts
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(helper.getBlockState(wall).is(Blocks.OAK_PLANKS),
					"the sticky piston outside pulled the village's block out: at the wall " + helper.getBlockState(wall)
						+ ", a step out " + helper.getBlockState(wall.east()));
				helper.assertBlockPresent(Blocks.AIR, wall.east());
				helper.succeed();
			});
		});
	}
}
