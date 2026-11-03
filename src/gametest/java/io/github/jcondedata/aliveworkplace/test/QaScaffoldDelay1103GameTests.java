package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * QA (lane-b-1103-0338, 2026-11-03) for B831 on scaffolding, from the bug's Expected and the scaffolding wiki ("puts each
 * scaffold under its feet, stepping up on it, at the build speed (workplaceBuildDelay)"; "takes the scaffolding down
 * ... a block at a time"): an admin who slows the builder right down mid-climb or mid-take-down (a huge rule) stops
 * the scaffolding too, and putting the rule back lets him finish at once, with every scaffold back in the chest and
 * none left standing. Same 14-high test pillar as {@link QaScaffoldingGameTests}.
 */
public class QaScaffoldDelay1103GameTests {
	private static final int HUGE = 30_000_000;

	private static void setDelay(ServerLevel level, int delay) {
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
	}

	/**
	 * Raised to 30,000,000 once the second scaffold of a column is up: in the next 400 ticks at most one more scaffold
	 * (the one already due) goes up. Lowered back to 2: the pillar is finished, the scaffolding taken down, all 64 back.
	 */
	//$ gametest_ticks_batch 'QaScaffoldingGameTests.BIG' '12000' '"qa1103ScaffoldDelayClimb"'
	@GameTest(template = QaScaffoldingGameTests.BIG, timeoutTicks = 12000, batch = "qa1103ScaffoldDelayClimb")
	public void aHugeDelayMidClimbStopsTheScaffoldingAndPuttingItBackFinishesThePillar(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		ServerLevel level = s.level();
		int[] raisedAt = {-1};
		int[] atRaise = {-1};
		int[] loweredAt = {-1};
		int[] most = {0};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			int up = BuildSiteManager.get(level).get(s.site().id()) == null ? 0 : s.site().scaffolds().size();
			if (raisedAt[0] < 0 && up >= 2) {
				setDelay(level, HUGE);
				raisedAt[0] = t;
				atRaise[0] = up;
			} else if (raisedAt[0] >= 0 && loweredAt[0] < 0) {
				most[0] = Math.max(most[0], up);
				if (t >= raisedAt[0] + 400) {
					helper.assertTrue(most[0] <= atRaise[0] + 1, "raised to 30,000,000 at tick " + raisedAt[0] + " with " + atRaise[0]
						+ " scaffolds up, but " + most[0] + " stood within 400 ticks (at most the one already due was expected)");
					setDelay(level, 2);
					loweredAt[0] = t;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt[0] >= 0, "no scaffolding column of 2 ever went up: stage " + s.site().stage() + " status " + s.site().status());
			helper.assertTrue(loweredAt[0] >= 0, "waiting out the huge delay (raised at " + raisedAt[0] + ")");
			QaScaffoldingGameTests.assertFinished(helper, s);
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int total = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING)
				+ QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(total == QaScaffoldingGameTests.SCAFFOLDING, QaScaffoldingGameTests.SCAFFOLDING + " scaffolding went in, " + total
				+ " came out (rule lowered back to 2 at " + loweredAt[0] + ")");
			helper.assertTrue(helper.getTick() - loweredAt[0] < 8000, "the rest took " + (helper.getTick() - loweredAt[0]) + " ticks at delay 2");
		});
	}

	/**
	 * Raised to 30,000,000 once the take-down has begun (the column is shorter than at its tallest): in the next 400
	 * ticks at most one more scaffold comes down. Lowered back to 2: the rest comes down and all 64 are back in the chest.
	 */
	//$ gametest_ticks_batch 'QaScaffoldingGameTests.BIG' '12000' '"qa1103ScaffoldDelayTakeDown"'
	@GameTest(template = QaScaffoldingGameTests.BIG, timeoutTicks = 12000, batch = "qa1103ScaffoldDelayTakeDown")
	public void aHugeDelayMidTakeDownStopsItAndPuttingItBackTakesTheRestDown(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		ServerLevel level = s.level();
		int[] tallest = {0};
		int[] raisedAt = {-1};
		int[] atRaise = {-1};
		int[] fewest = {Integer.MAX_VALUE};
		int[] loweredAt = {-1};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			if (BuildSiteManager.get(level).get(s.site().id()) == null) {
				return;
			}
			int up = s.site().scaffolds().size();
			if (raisedAt[0] < 0) {
				tallest[0] = Math.max(tallest[0], up);
				if (tallest[0] >= 3 && up > 0 && up < tallest[0]) {
					setDelay(level, HUGE);
					raisedAt[0] = t;
					atRaise[0] = up;
				}
			} else if (loweredAt[0] < 0) {
				fewest[0] = Math.min(fewest[0], up);
				if (t >= raisedAt[0] + 400) {
					helper.assertTrue(fewest[0] >= atRaise[0] - 1, "raised to 30,000,000 at tick " + raisedAt[0] + " with " + atRaise[0]
						+ " scaffolds left, but only " + fewest[0] + " stood within 400 ticks (at most the one already due should come down)");
					setDelay(level, 2);
					loweredAt[0] = t;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt[0] >= 0, "the take-down never began: tallest column " + tallest[0] + ", stage " + s.site().stage()
				+ " status " + s.site().status());
			helper.assertTrue(loweredAt[0] >= 0, "waiting out the huge delay (raised at " + raisedAt[0] + ")");
			QaScaffoldingGameTests.assertFinished(helper, s);
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int total = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING)
				+ QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(total == QaScaffoldingGameTests.SCAFFOLDING, QaScaffoldingGameTests.SCAFFOLDING + " scaffolding went in, " + total
				+ " came out (rule lowered back to 2 at " + loweredAt[0] + ")");
		});
	}
}
