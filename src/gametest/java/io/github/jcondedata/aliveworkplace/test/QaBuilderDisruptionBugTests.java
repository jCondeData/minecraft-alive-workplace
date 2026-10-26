package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-26), the builder disturbed mid-build: what fails against the spec, kept apart from
 * {@link QaBuilderDisruptionGameTests} for the owner's lane to decide on.
 */
public class QaBuilderDisruptionBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/**
	 * CLAUDE.md "How the builder works": "Done" checks use {@code MaterialRules.matches}; ROADMAP 23.1: "every build
	 * finishes" (the build tests' own yardstick, {@code BuilderGameTests.assertBuilt}: no step unfinished when the site
	 * ends). Mid-STRUCTURE a player breaks a plank the builder already placed; upkeep off, so only the build itself can put
	 * it back. When the site ends (DONE), the plank should stand: a builder who calls a hut finished with a hole in its
	 * wall leaves it to a repair question the owner never expected.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaDisruptBugBreak"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaDisruptBugBreak")
	public void aPlankBrokenMidBuildIsBackWhenTheBuildEnds(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
		BlockPos[] broken = new BlockPos[1];
		helper.onEachTick(() -> {
			if (broken[0] != null || r.site().stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			List<BuildPlan.Step> planks = QaBuilderDisruptionGameTests.placed(r).stream().filter(s -> s.state().is(Blocks.OAK_PLANKS)).toList();
			if (planks.size() >= 3) {
				broken[0] = planks.get(0).pos().immutable();
				r.level().destroyBlock(broken[0], false);
				LOGGER.info("qa disruption bug break: plank at {} broken, {}", helper.relativePos(broken[0]), QaBuilderDisruptionGameTests.state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: no plank was ever placed to break");
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(r.level().getBlockState(broken[0]).is(Blocks.OAK_PLANKS), "the build ended (" + QaBuilderDisruptionGameTests.state(helper, r)
				+ ") with the plank a player broke at " + helper.relativePos(broken[0]) + " still missing: " + r.level().getBlockState(broken[0]));
		});
	}

	/**
	 * B508: the build goes back for a broken block only so often. A plank broken again each time it's put back: the build
	 * still ends, and the plank is counted as skipped rather than claimed done; the count of times it went back is saved.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaDisruptBugBreakAgain"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaDisruptBugBreakAgain")
	public void aPlankBrokenEveryTimeEndsTheBuildAsSkipped(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
		BlockPos[] broken = new BlockPos[1];
		int[] breaks = new int[1];
		helper.onEachTick(() -> {
			if (broken[0] == null) {
				if (r.site().stage() != BuildPlan.Stage.STRUCTURE) {
					return;
				}
				List<BuildPlan.Step> planks = QaBuilderDisruptionGameTests.placed(r).stream().filter(s -> s.state().is(Blocks.OAK_PLANKS)).toList();
				if (planks.size() >= 3) {
					broken[0] = planks.get(0).pos().immutable();
				}
			}
			if (broken[0] != null && r.level().getBlockState(broken[0]).is(Blocks.OAK_PLANKS)) {
				r.level().destroyBlock(broken[0], false);
				breaks[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: no plank was ever placed to break");
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(breaks[0] >= 2, "the plank was put back only " + (breaks[0] - 1) + " times, broken " + breaks[0] + " times");
			helper.assertTrue(r.site().skipped() >= 1, "the build ended with the plank gone and skipped " + r.site().skipped()
				+ ": a hole claimed done");
			BuildSite back = BuildSite.load(r.site().save());
			helper.assertTrue(back != null && back.rechecks() == r.site().rechecks() && back.rechecks() > 0,
				"rechecks not saved: " + r.site().rechecks() + " -> " + (back == null ? "null" : back.rechecks()));
			LOGGER.info("qa disruption bug break again: broken {} times, rechecks {}, skipped {}", breaks[0], r.site().rechecks(), r.site().skipped());
		});
	}
}
