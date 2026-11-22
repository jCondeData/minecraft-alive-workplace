package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (2026-11-22): two cases of B1463/B1477 where the fix disagrees with the bugs' own words, failing on main
 * 395673fc2 (helpers and the passing cases in {@link QaJobBarrelsSaved1122eGameTests}). WorkerLimits.standBeside asks
 * Walker.canStand, which never counts a lectern's top as ground and never asks whether a villager can get there.
 */
public class QaJobBarrelsSaved1122eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = QaJobBarrelsSaved1122eGameTests.HUGE;
	private static final BlockPos BARREL = QaJobBarrelsSaved1122eGameTests.BARREL;
	private static final BlockPos BESIDE = QaJobBarrelsSaved1122eGameTests.BESIDE;

	/**
	 * A lectern 4 blocks from the barrel with a stone block against each of its 4 sides, nothing above it: a villager can
	 * hop onto the stones and stand on its top, and vanilla takes a workstation from its top (the control,
	 * QaJobBarrelsSaved1122eGameTests#qaAJoblessVillagerTakesALecternReachableOnlyFromItsTop, shows he does), so it is a
	 * free workstation a jobless villager can take: the barrel is its stock, and the villager becomes the Librarian there.
	 * Fails 4 of 5 runs: the villager takes the barrel beside him (a pass is a villager who happened to go for the
	 * lectern first).
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eTopOnly"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eTopOnly")
	public void qaALecternReachableOnlyFromItsTopIsTakenAndItsBarrelIsStock(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos lectern = BARREL.offset(4, 0, 0);
		helper.setBlock(lectern, Blocks.LECTERN);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(lectern.relative(d), Blocks.STONE);
		}
		Villager v = QaJobBarrelsSaved1122eGameTests.jobless(helper, helper.absolutePos(BESIDE));
		QaJobBarrelsSaved1122eGameTests.becomesLibrarianAt(helper, v, helper.absolutePos(lectern), "a lectern walled on 4 sides, open on top");
	}

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
