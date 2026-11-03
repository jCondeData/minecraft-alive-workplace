package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.BedLadders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA (2026-11-03), not registered: a failing test that shows a mod bug in B304's fix. {@code BedLadders.reachesBed}
 * (via {@code standable}) lets the walk from a ladder's top to the bed through any {@code DoorBlock}, iron doors too,
 * which a villager can't open; so a free bed behind an iron door up a ladder is claimed, and the villager who climbs to
 * it can't get through (the B304 symptom: standing in the room, then the bed given up).
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

}
