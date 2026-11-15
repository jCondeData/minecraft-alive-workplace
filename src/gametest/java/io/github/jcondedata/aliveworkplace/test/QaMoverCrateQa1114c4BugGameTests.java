package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA bug test (lane c, 2026-11-14, fourth pass), NOT registered: fails on main. A door packed so its lower half lands
 * on the Mover's Crate: the door item drops once (good), but its upper half is still put up right over the crate, alone,
 * a floating half door. Fails with "1 crimson door half(s) left standing without the other (the lower half dropped, the
 * upper put up alone); 1 door item(s)". Same house and Mover as MoverCrateNoRoomB1234GameTests.
 */
public class QaMoverCrateQa1114c4BugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	/**
	 * A crimson door whose lower half stands where the chest stood (so it lands on his crate; its upper half lands right
	 * over it): one crimson door in the end, not two, and no half door left standing by itself.
	 */
	//$ gametest_batch AREA '"qa1114c4Door"'
	@GameTest(template = AREA, batch = "qa1114c4Door")
	public void aDoorWhoseLowerHalfLandsOnHisCrateIsOneDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qa1114c4door");
		BlockState door = Blocks.CRIMSON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
		BlockPos lower = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		level.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(lower).is(Blocks.CRIMSON_DOOR) && level.getBlockState(lower.above()).is(Blocks.CRIMSON_DOOR), "setup: no door");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a door's lower half on his crate");
			int items = QaMoverCrateDrop1114eGameTests.count(helper, Items.CRIMSON_DOOR);
			int halves = QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.CRIMSON_DOOR);
			BlockState over = level.getBlockState(QaMoverCrateBorrow1114c3GameTests.crate(helper).above());
			helper.assertTrue(halves == 0, halves + " crimson door half(s) left standing without the other (the lower half dropped); right over his crate: "
				+ over + "; " + items + " door item(s)");
			helper.assertTrue(items == 1, items + " crimson door items, not 1 (one door packed)");
			helper.succeed();
		});
	}

	/**
	 * A cyan bed whose head stands where the chest stood (so it lands on his crate; its foot lands right north of it): one
	 * cyan bed in the end, not two, and no half bed left standing by itself.
	 */
	//$ gametest_batch AREA '"qa1114c4Bed"'
	@GameTest(template = AREA, batch = "qa1114c4Bed")
	public void aBedWhoseHeadLandsOnHisCrateIsOneBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qa1114c4bed");
		BlockState bed = Blocks.CYAN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		BlockPos head = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		level.setBlock(head.north(), bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(head).is(Blocks.CYAN_BED) && level.getBlockState(head.north()).is(Blocks.CYAN_BED), "setup: no bed");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a bed's head on his crate");
			int items = QaMoverCrateDrop1114eGameTests.count(helper, Items.CYAN_BED);
			int halves = QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.CYAN_BED);
			BlockState beside = level.getBlockState(QaMoverCrateBorrow1114c3GameTests.crate(helper).north());
			helper.assertTrue(halves == 0, halves + " cyan bed half(s) left standing without the other (the head dropped); right north of his crate: "
				+ beside + "; " + items + " bed item(s)");
			helper.assertTrue(items == 1, items + " cyan bed items, not 1 (one bed packed)");
			helper.succeed();
		});
	}
}
