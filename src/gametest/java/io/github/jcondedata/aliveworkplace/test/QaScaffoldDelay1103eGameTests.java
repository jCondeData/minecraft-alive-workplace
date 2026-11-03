package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * QA (lane-e-1103-0412, config extremes) for B831 on the scaffolding path, written from the bug's Expected ("back at 4
 * he builds again at 4 ticks a block") and the wiki (a big workplaceBuildDelay is how an admin slows builders right
 * down): the rule raised to its largest value, {@link Integer#MAX_VALUE}, while the builder is putting scaffolding up
 * or taking it down stops him; lowered again, with no restart of his work, he carries on at once and the pillar ends
 * finished, with no scaffold left standing and none lost. Same 14-high test pillar as {@link QaScaffoldingGameTests}.
 */
public class QaScaffoldDelay1103eGameTests {
	private static final String BIG = QaScaffoldingGameTests.BIG;
	private static final int HOLD = 300;
	private static final int BACK = 2;

	private static void setDelay(ServerLevel level, int delay) {
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
	}

	/** Each tick something on the site moved: a scaffold put up or taken down, or a drawn block finished. */
	private static List<Integer> watch(GameTestHelper helper, QaScaffoldingGameTests.Site s) {
		List<Integer> events = new ArrayList<>();
		int[] last = {s.site().scaffolds().size(), s.plan().unfinished(s.level()).size()};
		helper.onEachTick(() -> {
			int scaffolds = s.site().scaffolds().size();
			int unfinished = s.plan().unfinished(s.level()).size();
			if (scaffolds != last[0] || unfinished < last[1]) {
				events.add((int) helper.getTick());
			}
			last[0] = scaffolds;
			last[1] = unfinished;
		});
		return events;
	}

	private static long between(List<Integer> ticks, int from, int to) {
		return ticks.stream().filter(t -> t > from && t <= to).count();
	}

	private static void restoreRule(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> setDelay(level, 8));
	}

	/**
	 * Raised to the largest delay once two scaffolds stand, held for 300 ticks (at most the two moves already due come,
	 * no more), then lowered back to 2: the next move comes within 200 ticks and the pillar is finished with all 64
	 * scaffolding back in the chest.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qa1103eScaffoldUpDelay"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qa1103eScaffoldUpDelay")
	public void theLargestDelayLoweredWhileScaffoldingGoesUpLetsHimCarryOn(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		restoreRule(helper);
		hold(helper, s, () -> s.site().scaffolds().size() >= 2, "two scaffolds stood");
	}

	/**
	 * Raised to the largest delay as the first scaffold comes down, held for 300 ticks, then lowered back to 2: the
	 * taking down goes on within 200 ticks, and the pillar ends finished with no scaffold standing and all 64 back.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qa1103eScaffoldDownDelay"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qa1103eScaffoldDownDelay")
	public void theLargestDelayLoweredWhileScaffoldingComesDownLetsHimCarryOn(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		restoreRule(helper);
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], s.site().scaffolds().size()));
		hold(helper, s, () -> most[0] >= 2 && s.site().scaffolds().size() < most[0], "the first scaffold came down");
	}

	private static void hold(GameTestHelper helper, QaScaffoldingGameTests.Site s, java.util.function.BooleanSupplier when, String what) {
		ServerLevel level = s.level();
		List<Integer> events = watch(helper, s);
		int[] raisedAt = {-1};
		int[] loweredAt = {-1};
		int[] scaffoldsWhenRaised = {-1};
		long[] held = {-1};
		helper.onEachTick(() -> {
			int t = (int) helper.getTick();
			if (raisedAt[0] < 0 && when.getAsBoolean()) {
				setDelay(level, Integer.MAX_VALUE);
				raisedAt[0] = t;
				scaffoldsWhenRaised[0] = s.site().scaffolds().size();
			} else if (raisedAt[0] >= 0 && loweredAt[0] < 0 && t >= raisedAt[0] + HOLD) {
				held[0] = between(events, raisedAt[0], t);
				setDelay(level, BACK);
				loweredAt[0] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt[0] >= 0, "setup: never " + what + " (scaffolds " + s.site().scaffolds().size() + ", stage " + s.site().stage() + ")");
			helper.assertTrue(loweredAt[0] >= 0, "waiting for the rule to go back down");
			helper.assertTrue(held[0] <= 2, held[0] + " moves in the " + HOLD + " ticks at workplaceBuildDelay " + Integer.MAX_VALUE
				+ " (raised at " + raisedAt[0] + " when " + what + "; moves at " + events + "); at most the 2 already due were expected");
			List<Integer> after = events.stream().filter(t -> t > loweredAt[0]).toList();
			helper.assertTrue(!after.isEmpty() || helper.getTick() < loweredAt[0] + 200, "lowered back to " + BACK + " at " + loweredAt[0]
				+ " (raised at " + raisedAt[0] + " with " + scaffoldsWhenRaised[0] + " scaffolds standing) but nothing moved in 200 ticks: stage "
				+ s.site().stage() + ", status " + s.site().status() + ", scaffolds " + s.site().scaffolds().size());
			helper.assertTrue(!after.isEmpty() && after.get(0) - loweredAt[0] <= 200, "lowered back to " + BACK + " at " + loweredAt[0]
				+ " but the next move only came at " + (after.isEmpty() ? "never" : after.get(0)));
			QaScaffoldingGameTests.assertFinished(helper, s);
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int total = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING)
				+ QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(total == QaScaffoldingGameTests.SCAFFOLDING, QaScaffoldingGameTests.SCAFFOLDING + " scaffolding went in, "
				+ total + " came out");
		});
	}
}
