package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
}
