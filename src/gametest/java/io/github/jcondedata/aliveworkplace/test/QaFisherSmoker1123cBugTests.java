package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane C (2026-11-23), B1496's fix against its words, the cases that fail on main (helpers and set-up from
 * {@link QaFisherSmoker1123cGameTests}). The fix: "a jobless villager nearer a free barrel he can take (village has room,
 * not another workshop's stock) than a smoker or furnace in that barrel's reach doesn't see the oven"; the wiki
 * (villager-jobs.md): "a villager nearer the smoker (the Ranch's Rancher) still takes the smoker".
 * A barrel no one can take (boxed in) or that he can't walk to (he is penned in) hid the smoker and left him jobless
 * (B1523; fixed 2026-11-23 with B1525: {@code WorkerLimits.canTake}). The fourth case found with these, a villager
 * beside the smoker who sometimes turns Fisherman at the barrel 6 across (B1524, open), stays on branch
 * tests/fisher-smoker-1123c until it is fixed.
 */
public class QaFisherSmoker1123cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = QaFisherSmoker1123cGameTests.HUGE;
	private static final BlockPos BARREL = QaFisherSmoker1123cGameTests.BARREL;

	private static Villager jobless(GameTestHelper helper, BlockPos rel) {
		return QaFisherSmoker1123cGameTests.jobless(helper, rel);
	}

	private static List<BlockPos> search(GameTestHelper helper, Villager v) {
		return QaFisherSmoker1123cGameTests.search(helper, v);
	}

	private static String rel(GameTestHelper helper, List<BlockPos> seen) {
		return QaFisherSmoker1123cGameTests.rel(helper, seen);
	}

	private static void becomes(GameTestHelper helper, Villager v, BlockPos job, VillagerProfession prof, VillagerProfession never, String what) {
		QaFisherSmoker1123cGameTests.becomes(helper, v, job, prof, never, what);
	}

	/**
	 * The barrel boxed in by glass on its 4 sides and its top (nowhere to stand at it: no villager can take it), the
	 * villager 3 blocks south of it, a free smoker 6 east of the barrel on open ground. He can't take the barrel, so he must
	 * not stay jobless: within 2400 ticks he is the smoker's Butcher.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaFisherSmoker1123cBugBoxed"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaFisherSmoker1123cBugBoxed")
	public void qaABarrelBoxedInDoesntLeaveTheVillagerJoblessForever(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(BARREL.relative(d), Blocks.GLASS);
		}
		helper.setBlock(BARREL.above(), Blocks.GLASS);
		BlockPos smoker = BARREL.offset(6, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, BARREL.offset(0, 0, 3));
		becomes(helper, v, helper.absolutePos(smoker), VillagerProfession.BUTCHER, VillagerProfession.FISHERMAN, "the barrel boxed in, a smoker 6 across");
	}

	/**
	 * The same boxed-in barrel (no villager can take it), the villager 3 south of it (his AI off), the smoker 6 east of the
	 * barrel: the barrel isn't one he can take, so his job search sees the smoker.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaFisherSmoker1123cBugBoxedSearch"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaFisherSmoker1123cBugBoxedSearch")
	public void qaABarrelBoxedInDoesntHideTheSmokerFromHisSearch(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(BARREL.relative(d), Blocks.GLASS);
		}
		helper.setBlock(BARREL.above(), Blocks.GLASS);
		BlockPos smoker = BARREL.offset(6, 0, 0);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, BARREL.offset(0, 0, 3));
		v.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> seen = search(helper, v);
			helper.assertTrue(seen.contains(helper.absolutePos(smoker)),
				"a barrel boxed in on 4 sides and the top (no one can take it) hides the smoker from the villager 3 blocks from it: " + rel(helper, seen));
			v.discard();
			helper.succeed();
		});
	}

	/**
	 * The villager penned in (glass walls 2 high) with a free smoker 4 blocks from him; a free barrel just outside the pen,
	 * 3 blocks from him and 7 from the smoker. He can't walk to the barrel, so it isn't one he can take: he must take the
	 * smoker within 2400 ticks.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaFisherSmoker1123cBugPen"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaFisherSmoker1123cBugPen")
	public void qaAPennedVillagerWhoCantReachTheBarrelTakesTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		for (int x = 11; x <= 17; x++) {
			for (int z = 9; z <= 13; z++) {
				if (x == 11 || x == 17 || z == 9 || z == 13) {
					helper.setBlock(new BlockPos(x, 2, z), Blocks.GLASS);
					helper.setBlock(new BlockPos(x, 3, z), Blocks.GLASS);
					helper.setBlock(new BlockPos(x, 4, z), Blocks.GLASS);
				}
				helper.setBlock(new BlockPos(x, 5, z), Blocks.GLASS); // a roof: he can't get out over the walls
			}
		}
		BlockPos barrel = new BlockPos(9, 2, 11);
		helper.setBlock(barrel, Blocks.BARREL);
		BlockPos smoker = new BlockPos(16, 2, 11);
		helper.setBlock(smoker, Blocks.SMOKER);
		Villager v = jobless(helper, new BlockPos(12, 2, 11));
		becomes(helper, v, helper.absolutePos(smoker), VillagerProfession.BUTCHER, VillagerProfession.FISHERMAN, "penned in with the smoker, the barrel outside");
	}
}
