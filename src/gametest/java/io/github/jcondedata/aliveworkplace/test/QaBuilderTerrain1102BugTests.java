package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNoForeign;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNothingFalling;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNothingMadeOrLost;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.count;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.state;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.Run;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;

/**
 * QA (2026-11-02, lane-b-1102-1838's tester): bug tests, failing on the code as it is. A site where sand or gravel stands
 * above the hut's box (a desert dune, a gravel cap on a hill): CLEAR goes top-down through the box, the sand above falls
 * into the rooms after the layer under it has been cleared, and the build is called done with the hut's room full of
 * it (17 of its 18 open blocks; the 18th holds the torch). Spec: CLAUDE.md "CLEAR (top-down)", builder.md "CLEAR takes
 * away what is in the way" and "Unfinished blocks are never claimed done". Helpers: {@link QaBuilderTerrain1102GameTests}.
 */
public class QaBuilderTerrain1102BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A sand dune buries the whole site: sand fills the hut's 5x4x5 box and stands three blocks above it. CLEAR goes
	 * top-down, so each layer taken lets the sand above fall into the box again. The finished hut is as drawn, no sand is
	 * left in it (nor falling), and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaTerrainSandDune"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaTerrainSandDune")
	public void aSandDuneOverTheSiteFallsInAndIsAllDugOut(GameTestHelper helper) {
		prepare(helper);
		BlockPos origin = new BlockPos(6, 2, 6);
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				for (int y = 2; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.SAND);
				}
			}
		}
		Run r = start(helper, 2, origin);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoForeign(helper, r, s -> s.getBlock() instanceof FallingBlock);
			assertNothingFalling(helper, r);
			assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * A gravel cap three deep stands on a dirt-filled site, and the world is saved and loaded back in the middle of CLEAR
	 * (the site from its save, the builder as a fresh entity). The hut ends as drawn, no gravel or dirt is left inside,
	 * and nothing is lost or made.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaTerrainGravelReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaTerrainGravelReload")
	public void aGravelCapOverADirtSiteReloadedMidClearIsAllDugOut(GameTestHelper helper) {
		prepare(helper);
		BlockPos origin = new BlockPos(6, 2, 6);
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				for (int y = 2; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
				}
				for (int y = 6; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.GRAVEL);
				}
			}
		}
		Run r = start(helper, 2, origin);
		watchDeath(helper, r);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (reloaded[0] || now == null || now.stage() != BuildPlan.Stage.CLEAR) {
				return;
			}
			int dirt = count(r, s -> s.is(Blocks.DIRT));
			if (dirt > 0 && dirt <= 60) { // of 100: the top layers are gone, the gravel has come down onto what is left
				reloaded[0] = true;
				r.builder()[0] = reload(helper, r.level(), r.builder()[0]);
				BuildSite back = r.site();
				helper.assertTrue(back != null && back != now, "the site didn't come back from its save");
				LOGGER.info("qa terrain gravel: reloaded mid-CLEAR with {} dirt and {} gravel in the box", dirt, count(r, s -> s.is(Blocks.GRAVEL)));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the build was never reloaded in the middle of CLEAR: " + state(helper, r));
			assertFinished(helper, r);
			assertNoForeign(helper, r, s -> s.getBlock() instanceof FallingBlock || s.is(Blocks.DIRT));
			assertNothingFalling(helper, r);
			assertNothingMadeOrLost(helper, r);
		});
	}

}
