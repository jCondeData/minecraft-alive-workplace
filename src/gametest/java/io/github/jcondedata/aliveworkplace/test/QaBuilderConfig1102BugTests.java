package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (2026-11-02), from docs/wiki/builder.md section 3: workplaceBuildDelay is "Ticks per block for a level 1 Builder",
 * "8 (min 1)", with no top. A large value is how an admin slows builders right down (or all but stops them), so a
 * novice Builder at 30,000,000 ticks a block (about 17 real days) or at the rule's largest value must not put up more
 * than the one block he starts on within a few hundred ticks. Neither may ever build faster than at the default 8.
 */
public class QaBuilderConfig1102BugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static void slow(GameTestHelper helper, int delay) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, delay);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		int before = r.plan().unfinished(level).size();
		helper.runAtTickTime(400, () -> {
			int now = r.plan().unfinished(level).size();
			helper.assertTrue(r.live() != null, "with workplaceBuildDelay " + delay + " the hut was finished within 400 ticks");
			helper.assertTrue(before - now <= 1, "with workplaceBuildDelay " + delay + " (ticks per block) the Builder put up " + (before - now)
				+ " blocks of the hut in 400 ticks; at most 1 was expected");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '600' '"qaCfgBugDelayHuge"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaCfgBugDelayHuge")
	public void aBuildDelayOfThirtyMillionTicksDoesNotBuildEveryTick(GameTestHelper helper) {
		slow(helper, 30_000_000);
	}

	//$ gametest_ticks_batch AREA '600' '"qaCfgBugDelayMax"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaCfgBugDelayMax")
	public void theLargestBuildDelayDoesNotBuildEveryTick(GameTestHelper helper) {
		slow(helper, Integer.MAX_VALUE);
	}

	/** B822 at the root: the time per block never shrinks as the rule grows, at any level, up to the rule's largest value. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBiggerBuildDelayIsNeverFaster(GameTestHelper helper) {
		int[] bases = {1, 8, 100, 21_474_837, 21_474_840, 30_000_000, 1_000_000_000, Integer.MAX_VALUE};
		for (int level = 1; level <= 5; level++) {
			int last = -1;
			for (int base : bases) {
				int d = BuilderLevels.delay(base, level);
				helper.assertTrue(d >= last && d >= 0, "level " + level + ": workplaceBuildDelay " + base + " gives " + d + " ticks a block, under the "
					+ last + " of a smaller delay");
				last = d;
			}
			helper.assertTrue(last >= Integer.MAX_VALUE / 100 * 40, "level " + level + ": the largest delay gives only " + last + " ticks a block");
		}
		helper.succeed();
	}
}
