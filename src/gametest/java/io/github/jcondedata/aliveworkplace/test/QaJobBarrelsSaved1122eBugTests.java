package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (2026-11-22): a case of B1463/B1477 where the fix disagreed with the bugs' own words (helpers and the
 * passing cases in {@link QaJobBarrelsSaved1122eGameTests}): WorkerLimits.standBeside never asked whether a villager can
 * get to the workstation (B1491; fixed 2026-11-23 with B1523: {@code WorkerLimits.canTake}). The other case found with
 * it, a lectern reached only from its top (B1492, open), stays on branch tests/job-barrels-sealed-1122e until it is fixed.
 */
public class QaJobBarrelsSaved1122eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = QaJobBarrelsSaved1122eGameTests.HUGE;
	private static final BlockPos BARREL = QaJobBarrelsSaved1122eGameTests.BARREL;
	private static final BlockPos BESIDE = QaJobBarrelsSaved1122eGameTests.BESIDE;

	/**
	 * B1477's Expected ("only a workstation a villager can actually take hides the barrel"): a lectern 5 blocks from the
	 * barrel inside a sealed glass room (3 x 3 floor, walls 3 high, a glass roof): there is floor to stand on beside it,
	 * but no villager can ever get in, so it stays free for good. The barrel is no workstation's stock a villager could
	 * take instead: the village gets its Fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eSealedRoom"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eSealedRoom")
	public void qaALecternInASealedRoomDoesntStopTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos lectern = BARREL.offset(5, 0, 0);
		// room inside x 14..16, z 9..11 (the lectern in its middle), walls round it at x 13..17, z 8..12, y 2..4, roof at y 5
		for (int x = 13; x <= 17; x++) {
			for (int z = 8; z <= 12; z++) {
				boolean wall = x == 13 || x == 17 || z == 8 || z == 12;
				for (int y = 2; y <= 5; y++) {
					if (wall || y == 5) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(lectern, Blocks.LECTERN);
		Villager v = QaJobBarrelsSaved1122eGameTests.jobless(helper, helper.absolutePos(BESIDE));
		QaJobBarrelsSaved1122eGameTests.becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a lectern in a sealed glass room 5 blocks away");
	}
}
