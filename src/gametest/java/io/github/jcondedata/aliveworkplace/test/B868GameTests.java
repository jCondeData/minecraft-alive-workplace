package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Bug B868: a spot of the build box filled again the tick after the Builder digs it (sand dropped back, a cobblestone
 * generator, a player) kept him in CLEAR for ever: the cursor only moved on once the spot read clear, so he dug the same
 * block thousands of times. Now a spot dug 16 times on one list is deferred like any step that can't be done yet, then
 * skipped (counted once in skipped) if it is still being refilled on the retry, and the build goes on.
 * (The first test is QA's QaBuilderRestartClear1103bBugTests.aSpotRefilledEveryTickNeverLetsClearEnd, from
 * tests/builder-clear-1103b.)
 */
public class B868GameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos SPOT = QaBuilderRestartClear1103bGameTests.SPOT_A;
	/** BuilderWork.MOST_REDIGS: digs of one spot on a list before it is deferred. */
	private static final int MOST = 16;

	/**
	 * Sand on the hut's floor spot (8, 2, 8), put back each time it is dug while the site is in CLEAR, at most
	 * {@code refills} times (-1: for ever). With {@code reloadAtDig} &gt; 0 the world is saved and loaded at that dig.
	 * Returns the run and fills {@code out} with: digs, the tick CLEAR was left, skipped and CLEAR passes then.
	 */
	private static QaBuilderTerrain1102GameTests.Run refill(GameTestHelper helper, int refills, int reloadAtDig, long[] out) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		helper.setBlock(SPOT, Blocks.SAND);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		out[1] = -1;
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (s == null || out[1] >= 0) {
				return;
			}
			if (s.stage() != BuildPlan.Stage.CLEAR) {
				out[1] = helper.getTick();
				out[2] = s.skipped();
				out[3] = s.clearPasses();
				LOGGER.info("b868: CLEAR left at {} after {} digs, skipped {}, passes {}", out[1], out[0], out[2], out[3]);
				return;
			}
			if (helper.getBlockState(SPOT).isAir() && (refills < 0 || out[0] < refills)) {
				helper.setBlock(SPOT, Blocks.SAND);
				out[0]++;
				if (out[0] == reloadAtDig) {
					r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
					out[4] = 1;
				}
			}
			helper.assertTrue(helper.getTick() < 9000, "the builder is still in CLEAR at tick " + helper.getTick() + ": he dug the refilled sand at "
				+ SPOT.toShortString() + " " + out[0] + " times and never moved on (" + s.clearPasses() + " passes): " + QaBuilderTerrain1102GameTests.state(helper, r));
		});
		return r;
	}

	/** Sand put back the tick after each dig, for ever: CLEAR ends, the spot counted once in skipped, the hut finishes. */
	//$ gametest_ticks_batch HUGE '12000' '"b868Refill"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b868Refill")
	public void aSpotRefilledEveryTickNoLongerKeepsTheBuilderInClear(GameTestHelper helper) {
		long[] out = new long[5];
		QaBuilderTerrain1102GameTests.Run r = refill(helper, -1, 0, out);
		helper.succeedWhen(() -> {
			helper.assertTrue(out[1] >= 0, "CLEAR not left yet after " + out[0] + " digs");
			helper.assertTrue(out[0] <= 2 * MOST + 2, "the refilled spot was dug " + out[0] + " times: deferred after " + MOST + ", given up after "
				+ MOST + " more on the retry");
			helper.assertTrue(out[2] == 1, "the refilled spot left in CLEAR should be counted once in skipped, was " + out[2]);
			helper.assertTrue(out[3] == 0, "a refilled spot given up sent CLEAR back over its list " + out[3] + " times for what fell in");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	/** The same, saved and loaded mid-CLEAR (at the 8th dig): CLEAR still ends, the hut finishes. */
	//$ gametest_ticks_batch HUGE '12000' '"b868RefillReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b868RefillReload")
	public void aRefilledSpotThroughASaveAndReloadStillLetsClearEnd(GameTestHelper helper) {
		long[] out = new long[5];
		QaBuilderTerrain1102GameTests.Run r = refill(helper, -1, 8, out);
		helper.succeedWhen(() -> {
			helper.assertTrue(out[4] == 1, "setup: never reloaded");
			helper.assertTrue(out[1] >= 0, "CLEAR not left yet after " + out[0] + " digs");
			// a load gives the spot its digs afresh (they aren't saved): at most one extra round of them
			helper.assertTrue(out[0] <= 3 * MOST + 2, "the refilled spot was dug " + out[0] + " times across a reload");
			helper.assertTrue(out[2] == 1, "the refilled spot left in CLEAR should be counted once in skipped, was " + out[2]);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	/** Sand put back only three times (as a short pile coming down): dug out as ever, nothing deferred or skipped. */
	//$ gametest_ticks_batch HUGE '12000' '"b868FewRefills"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b868FewRefills")
	public void aSpotRefilledAFewTimesIsStillDugOutAndNothingIsSkipped(GameTestHelper helper) {
		long[] out = new long[5];
		QaBuilderTerrain1102GameTests.Run r = refill(helper, 3, 0, out);
		helper.succeedWhen(() -> {
			helper.assertTrue(out[0] == 3, "setup: the sand was put back " + out[0] + " times, not 3");
			helper.assertTrue(out[1] >= 0, "CLEAR not left yet");
			helper.assertTrue(out[2] == 0, "sand put back only 3 times was skipped (" + out[2] + ") rather than dug out");
			helper.assertTrue(out[3] == 0, "CLEAR went back over its list " + out[3] + " times");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		});
	}

	/** The refilled spots given up are saved (so later passes over CLEAR leave them be), and an older save has none. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void refilledSpotsSurviveASaveAndOlderSavesHaveNone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "Jesse", HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: test hut blueprint missing");
		helper.assertTrue(site.stage() == BuildPlan.Stage.CLEAR && !plan.steps(BuildPlan.Stage.CLEAR).isEmpty(), "setup: no CLEAR list");
		BlockPos pos = plan.steps(BuildPlan.Stage.CLEAR).get(0).pos();
		CompoundTag none = site.save();
		helper.assertTrue(!none.contains("refilled"), "a site with no refilled spot wrote one: " + none.get("refilled"));
		helper.assertTrue(!BuildSite.load(none).refilled(pos), "a site saved before B868 loaded with a refilled spot");

		for (int i = 0; i < MOST; i++) {
			site.dug(pos);
		}
		helper.assertTrue(site.digs(pos) == MOST, "digs counted " + site.digs(pos) + ", not " + MOST);
		site.deferRefilled(pos); // first time: deferred, not given up
		helper.assertTrue(!site.refilled(pos) && site.skipped() == 0 && site.digs(pos) == 0,
			"deferred once: refilled " + site.refilled(pos) + ", skipped " + site.skipped() + ", digs " + site.digs(pos));
		while (site.current(plan) != null) {
			site.advance();
		}
		site.finishList(); // the retry of the deferred spot
		helper.assertTrue(site.isRetrying() && site.current(plan) != null && site.current(plan).pos().equals(pos), "setup: not retrying the spot");
		site.deferRefilled(pos);
		helper.assertTrue(site.refilled(pos) && site.skipped() == 1, "on the retry: refilled " + site.refilled(pos) + ", skipped " + site.skipped());
		BuildSite back = BuildSite.load(site.save());
		helper.assertTrue(back.refilled(pos), "the refilled spot didn't come back from a save");
		helper.assertTrue(back.skipped() == 1, "skipped came back as " + back.skipped());
		helper.succeed();
	}
}
