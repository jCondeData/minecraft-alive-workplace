package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-b-1103-0338, 2026-11-03) for scaffolding (35.6, 35.6a), from docs/wiki/scaffolding.md, written against the
 * mutants of Scaffolds that the scaffolding tests let through (a local spot-check, 7 survivors of 8):
 * <ul>
 * <li>"A site removed with scaffolds still recorded ... has them all taken down at once, into the supply chests by its
 * bench (else dropped there)": with the chest gone, they land by the bench, not at the column's foot across the site
 * (kills Scaffolds.dismantle's "bench() != null" flipped);</li>
 * <li>"at the foot steps out of the column onto the free spot beside it ... (from inside a scaffold its path out used to
 * fail, and it stood there five seconds and then hopped)": a builder never stands in a column's foot for seconds (a
 * guard only: stepOut always failing still passes on this pillar, where the builder rarely leaves a column from its foot).</li>
 * </ul>
 * Same 14-high test pillar as {@link QaScaffoldingGameTests}.
 */
public class QaScaffoldMutants1103GameTests {
	/**
	 * The chest broken, then the build canceled with scaffolding up: every scaffold the cancel took down lies within 3
	 * blocks of the bench (the column stands 8 or more blocks away by the pillar), with the chest's spilled ones and the
	 * builder's bag making up all 64.
	 */
	//$ gametest_ticks_batch 'QaScaffoldingGameTests.BIG' '6000' '"qa1103ScaffoldDropByBench"'
	@GameTest(template = QaScaffoldingGameTests.BIG, timeoutTicks = 6000, batch = "qa1103ScaffoldDropByBench")
	public void canceledWithNoChestTheScaffoldingLandsByTheBench(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		long[] brokenAt = {-1};
		int[] up = {0};
		boolean[] canceled = {false};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && s.site().scaffolds().size() >= 3) {
				brokenAt[0] = helper.getTick();
				s.level().destroyBlock(helper.absolutePos(QaScaffoldingGameTests.CHEST), true);
			} else if (brokenAt[0] >= 0 && !canceled[0] && helper.getTick() >= brokenAt[0] + 20) {
				canceled[0] = true;
				up[0] = s.site().scaffolds().size();
				helper.assertTrue(up[0] > 0, "the scaffolding came down before the cancel");
				Builders.cancel(s.level(), s.site());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(canceled[0], "never canceled: scaffolds " + s.site().scaffolds().size());
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "the build wasn't canceled");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			BlockPos bench = helper.absolutePos(QaScaffoldingGameTests.BENCH);
			AABB near = new AABB(bench).inflate(3);
			int byBench = 0;
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, near)) {
				byBench += e.getItem().is(Items.SCAFFOLDING) ? e.getItem().getCount() : 0;
			}
			int all = QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
			int bag = QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(byBench + bag == QaScaffoldingGameTests.SCAFFOLDING, (all - byBench) + " scaffolding lie away from the bench after the cancel took "
				+ up[0] + " down with no chest left (by the bench " + byBench + ", bag " + bag + ", on the ground anywhere " + all + "); all "
				+ QaScaffoldingGameTests.SCAFFOLDING + " should be by the bench or in the bag");
		});
	}

	/**
	 * Wiki: the builder at a column's foot steps out beside it rather than stand inside the scaffold. While the pillar
	 * is built and its scaffolding taken down, the builder never spends 60 ticks in a row with its feet in a scaffold at
	 * ground level (the bug it fixed stood there 100 ticks, then hopped).
	 */
	//$ gametest_ticks_batch 'QaScaffoldingGameTests.BIG' '12000' '"qa1103ScaffoldStepOut"'
	@GameTest(template = QaScaffoldingGameTests.BIG, timeoutTicks = 12000, batch = "qa1103ScaffoldStepOut")
	public void theBuilderNeverStandsLongInAColumnsFoot(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		int[] run = {0};
		int[] longest = {0};
		int[] most = {0};
		BlockPos[] where = {null};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			most[0] = Math.max(most[0], BuildSiteManager.get(s.level()).get(s.site().id()) == null ? 0 : s.site().scaffolds().size());
			BlockPos feet = s.builder().blockPosition();
			boolean inFoot = helper.relativePos(feet).getY() == 2 && s.level().getBlockState(feet).is(Blocks.SCAFFOLDING);
			run[0] = inFoot ? run[0] + 1 : 0;
			if (run[0] > longest[0]) {
				longest[0] = run[0];
				where[0] = helper.relativePos(feet);
			}
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding went up");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			helper.assertTrue(longest[0] < 60, "the builder stood " + longest[0] + " ticks in a row inside a column's foot at " + where[0]
				+ " (it should step out beside it)");
		});
	}
}
