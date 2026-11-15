package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-15, lane-b-1115-0337's tester), failing: a drawing with its own pool, sunk in a pond, ends with its room
 * flooded. docs/wiki/builder.md "Stages" (B1243): "the water or lava standing in its rooms ... is taken away at once
 * ... and a pool the drawing has stay[s]". QaBuilderDrainEdges1115bGameTests#aShutRoomWithNoDoorSunkInAPondEndsDry is
 * the same basin and pond without the pool, and passes.
 */
public class QaBuilderDrainEdges1115bBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/**
	 * A shut 5x4x5 cobblestone basin room with a one-block pool in the middle of its floor, clicked onto the bed of a
	 * pond three deep exactly its footprint (so the whole room stands under water): the drawing's pool keeps its water
	 * source, every other spot of the box ends dry, the basin stands as drawn and its cobblestone adds up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bDrainPool"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bDrainPool")
	public void aDrawnPoolInASunkRoomKeepsItsWaterAndTheRestEndsDry(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = QaBuilderDrainEdges1115bGameTests.basin(helper, true);
		fill(helper, 0, 29, 2, 4, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 4, 6, 10, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderDrainEdges1115bGameTests.startWith(helper, 5, new BlockPos(6, 2, 6), id);
		BlockPos pool = helper.absolutePos(new BlockPos(8, 2, 8));
		helper.assertTrue(r.plan().targets().containsKey(pool) && !r.plan().targets().get(pool).getFluidState().isEmpty(),
			"setup: the basin's drawing has no pool at " + pool + ": " + r.plan().targets().get(pool));
		watchDeath(helper, r);
		boolean[] logged = {false};
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (!logged[0] && now != null && now.stage() != BuildPlan.Stage.CLEAR) {
				logged[0] = true;
				LOGGER.info("qa 1115b drain pool: first tick of {}: {} wet spot(s) in the box besides the pool: {}", now.stage(),
					QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool).size(), QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool));
			}
		});
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			BlockState there = r.level().getBlockState(pool);
			helper.assertTrue(there.is(Blocks.WATER) && there.getFluidState().isSource(), "the drawing's pool lost its water: " + there);
			List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool);
			helper.assertTrue(wet.isEmpty(), "water left in the finished basin besides its pool: " + wet);
			QaBuilderDrainEdges1115bGameTests.materialsAddUp(helper, r);
		});
	}
}
