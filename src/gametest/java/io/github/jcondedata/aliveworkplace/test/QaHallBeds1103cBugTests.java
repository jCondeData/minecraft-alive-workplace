package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.BedLadders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA (2026-11-03), B876: {@code BedLadders.reachesBed} (via {@code standable}) let the walk from a ladder's top to the
 * bed through any {@code DoorBlock}, iron doors too, which a villager can't open; so a free bed behind an iron door up a
 * ladder was claimed, and the villager who climbed to it couldn't get through (the B304 symptom: standing in the room,
 * then the bed given up). Now only the doors vanilla's pathing lets a villager through count: wooden ones, and any open
 * one. The shut oak door's case is {@code QaHallBeds1103cGameTests.aBedBehindAShutOakDoorUpALadderIsClaimable}.
 */
public class QaHallBeds1103cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA_HUGE = "aliveworkplace_test:huge_area";

	/** Expected: an iron door (a villager can't open one) shuts the walk from the ladder's top to the bed, as a wall does. */
	//$ gametest_ticks_batch AREA_HUGE '200' '"qaHbIronDoor"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 200, batch = "qaHbIronDoor")
	public void aBedBehindAnIronDoorUpALadderIsNotClaimable(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bed = QaHallBeds1103cGameTests.walledTowerBed(helper, 13, Blocks.IRON_DOOR);
		Villager villager = QaHallBeds1103cGameTests.still(helper, new BlockPos(15, 2, 9));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(level.getPoiManager().getType(bed).isPresent(), "setup: the bed isn't a home yet"))
			.thenExecute(() -> {
				helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(13, 6, 1))).is(Blocks.IRON_DOOR), "setup: the iron door's top half is gone");
				Path p = BedLadders.pathUpALadder(villager, QaHallBeds1103cGameTests.home(level, bed));
				helper.assertTrue(p == null, "a bed behind a shut iron door (no villager opens one) was claimable up its ladder");
			})
			.thenSucceed();
	}

	/** An iron door left open (by a lever, say) is a gap a villager walks through: the bed behind it is claimable. */
	//$ gametest_ticks_batch AREA_HUGE '200' '"qaHbOpenIronDoor"'
	@GameTest(template = AREA_HUGE, timeoutTicks = 200, batch = "qaHbOpenIronDoor")
	public void aBedBehindAnOpenIronDoorUpALadderIsClaimable(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bed = QaHallBeds1103cGameTests.walledTowerBed(helper, 13, Blocks.IRON_DOOR);
		for (int y = 5; y <= 6; y++) {
			BlockPos half = new BlockPos(13, y, 1);
			helper.setBlock(half, helper.getBlockState(half).setValue(DoorBlock.OPEN, true));
		}
		Villager villager = QaHallBeds1103cGameTests.still(helper, new BlockPos(15, 2, 9));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(level.getPoiManager().getType(bed).isPresent(), "setup: the bed isn't a home yet"))
			.thenExecute(() -> {
				helper.assertTrue(helper.getBlockState(new BlockPos(13, 5, 1)).is(Blocks.IRON_DOOR)
					&& helper.getBlockState(new BlockPos(13, 5, 1)).getValue(DoorBlock.OPEN), "setup: the iron door isn't standing open");
				Path p = BedLadders.pathUpALadder(villager, QaHallBeds1103cGameTests.home(level, bed));
				helper.assertTrue(p != null && p.getTarget().equals(bed), "a bed behind an open iron door, up a ladder, isn't claimable: " + p);
				for (int y = 5; y <= 6; y++) { // shut again: not claimable
					BlockPos half = new BlockPos(13, y, 1);
					helper.setBlock(half, helper.getBlockState(half).setValue(DoorBlock.OPEN, false));
				}
				helper.assertTrue(BedLadders.pathUpALadder(villager, QaHallBeds1103cGameTests.home(level, bed)) == null, "the bed was claimable with its iron door shut again");
			})
			.thenSucceed();
	}
}
