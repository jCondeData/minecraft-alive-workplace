package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (2026-11-22, qa-lane-b-1122-1538) past B1485's fix, from the bug's Expected ("every block of the Quarry as
 * drawn") and the builder's spec (DONE with every block as drawn; a step that can't be done is deferred, then
 * skipped): a Builder given exactly the rebuilt Quarry's material list (as the held blueprint lists it, empty chests)
 * builds it whole with nothing skipped, and its hoist crank's lever is there: a lever hung on a wall face
 * ({@code face=wall}), the block behind it giving that face a sturdy hold, and it stays when its neighbours update.
 * The fix's note puts it on the hoist drum's west end at (13, 1, 10), facing west.
 */
public class QaQuarryLever1122bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	/** The Quarry's corner (19 x 17 x 14: x 9-27, z 9-22, y 2-18), the bench and chests west of it (QaDecorations1104f's). */
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);
	/** The lever in the drawing. */
	private static final BlockPos LEVER = new BlockPos(13, 1, 10);

	//$ gametest_ticks_batch HUGE '36000' '"qa1122bQuarryLever"'
	@GameTest(template = HUGE, timeoutTicks = 36000, batch = "qa1122bQuarryLever")
	public void aBuilderBuildsTheQuarrysLeverOnAHoldFromExactlyItsList(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.QUARRY, ORIGIN);
		helper.succeedWhen(() -> {
			QaDecorations1104fGameTests.assertExactlyBuilt(helper, job);
			ServerLevel level = helper.getLevel();
			BlockPos at = job.placement().origin().offset(LEVER);
			BlockState lever = level.getBlockState(at);
			helper.assertTrue(lever.is(Blocks.LEVER), "the Quarry's lever at " + LEVER.toShortString() + " is " + lever);
			helper.assertTrue(lever.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL, "the Quarry's lever isn't hung on a wall: " + lever);
			Direction facing = lever.getValue(BlockStateProperties.HORIZONTAL_FACING);
			BlockPos behind = at.relative(facing.getOpposite());
			BlockState hold = level.getBlockState(behind);
			helper.assertTrue(hold.isFaceSturdy(level, behind, facing), "the block behind the Quarry's lever (" + hold + ") gives its " + facing.getName()
				+ " face no sturdy hold");
			helper.assertTrue(lever.canSurvive(level, at), "the Quarry's lever can't stay where it is: " + lever + " on " + hold);
			BlockState updated = Block.updateFromNeighbourShapes(lever, level, at);
			helper.assertTrue(updated.is(Blocks.LEVER), "the Quarry's lever drops on a neighbour update: " + updated);
			LOGGER.info("qa 1122b quarry: built whole from exactly its list; lever {} held by {} behind it", lever, hold);
		});
	}
}
