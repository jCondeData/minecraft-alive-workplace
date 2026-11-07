package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (2026-11-02), from docs/wiki/builder.md ("Speed. Exactly workplaceBuildDelay ticks per block") and the B822 bug's
 * own reading of the rule: a large value is how an admin slows builders right down or all but stops them. Turning it
 * back down must then let them build again at the new speed, not after the old 17-day wait for the block in hand.
 */
public class QaBuilderDelay1102BugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Lowered from 30,000,000 to 4 after the first block: the Builder is building again within 200 ticks. */
	//$ gametest_ticks_batch AREA '1200' '"qaDelayBugLowered"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaDelayBugLowered")
	public void theRuleLoweredMidBuildLetsTheBuilderBuildAgain(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 30_000_000);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderConfig1102GameTests.BENCH,
			QaBuilderConfig1102GameTests.CHEST, QaBuilderConfig1102GameTests.HUT, QaBuilderConfig1102GameTests.hutMaterials());
		List<Integer> ticks = new ArrayList<>();
		int[] last = {r.plan().unfinished(level).size()};
		int[] changedAt = {-1};
		helper.onEachTick(() -> {
			int now = r.plan().unfinished(level).size();
			if (now < last[0]) {
				ticks.add((int) helper.getTick());
			}
			last[0] = now;
			if (changedAt[0] < 0 && (ticks.size() >= 1 || helper.getTick() >= 300)) {
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, level.getServer());
				changedAt[0] = (int) helper.getTick();
			}
		});
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(changedAt[0] >= 0 && changedAt[0] < 500, "the rule was never lowered");
			long after = ticks.stream().filter(t -> t > changedAt[0] && t <= changedAt[0] + 200).count();
			helper.assertTrue(after >= 3, "workplaceBuildDelay lowered from 30,000,000 to 4 at tick " + changedAt[0] + ", but the Builder placed only "
				+ after + " blocks in the next 200 ticks (blocks at " + ticks + "): he still waits out the old delay; "
				+ QaBuilderConfig1102GameTests.where(helper, r));
			helper.succeed();
		});
	}
}
