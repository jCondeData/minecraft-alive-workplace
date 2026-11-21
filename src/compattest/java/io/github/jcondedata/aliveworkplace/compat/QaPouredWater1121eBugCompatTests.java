package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaPouredWater1121eCompatTests.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (2026-11-21) failure from {@link QaPouredWater1121eCompatTests} (B1410's fix verified from its spec: "only water
 * that would actually flood a dry neighbor stays still"). BuilderWork.couldFloodDrawnDry counts a source beside a dry
 * stair even when the stair's solid back faces it, which vanilla never lets water through: a stream drawn running is
 * poured still and never runs. Not registered in fabric.mod.json (it fails on main); the builder registers it with the fix.
 */
public class QaPouredWater1121eBugCompatTests implements FabricGameTest {
	/**
	 * A player's stream: a source walled in behind a stone brick stair's solid back, the stair, then a second source that
	 * runs on down an open channel, all over a stone floor. Water can't pass the stair's solid back, so in vanilla the
	 * stair has only one source it can take water from and never waterlogs, and the second source runs down the channel.
	 * Saved and built by a builder: the stream runs as in the player's original and the stair stays dry.
	 */
	//$ gametest_ticks_batch HUGE_AREA '6000' '"qa1121e_poured_stair_back"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 6000, batch = "qa1121e_poured_stair_back")
	public void aStreamBesideAStairsSolidBackRuns(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		BlockState stone = Blocks.STONE.defaultBlockState();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 3; z++) {
				blocks.put(new BlockPos(x, 0, z), stone);
				if (z != 1 || x == 0 || x == 6) {
					blocks.put(new BlockPos(x, 1, z), stone);
				}
			}
		}
		BlockPos stair = new BlockPos(2, 1, 1);
		List<BlockPos> sources = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1));
		BlockPos channel = new BlockPos(4, 1, 1);
		blocks.put(stair, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)); // solid back to the west
		ContraptionCompatTests.Run run = playersBuild(helper, "stair_back", blocks, sources, new Vec3i(7, 2, 3), BUILD_AT, Rotation.NONE,
			() -> {
				helper.assertTrue(!helper.getBlockState(ORIGINAL.offset(channel)).getFluidState().isEmpty(),
					"setup: the player's own stream doesn't run down the channel");
				helper.assertFalse(helper.getBlockState(ORIGINAL.offset(stair)).getValue(BlockStateProperties.WATERLOGGED),
					"setup: the player's own stair is waterlogged");
			});
		helper.succeedWhen(() -> {
			assertFinished(helper, run);
			for (BlockPos s : sources) {
				helper.assertTrue(run.level.getFluidState(run.world(s)).isSource(), "no water poured at " + s.toShortString() + ": "
					+ run.level.getBlockState(run.world(s)));
			}
			assertDry(helper, run, stair);
			helper.assertTrue(!run.level.getFluidState(run.world(channel)).isEmpty(), "the stream hangs: the water poured at "
				+ sources.get(1).toShortString() + " never ran down the open channel beside it (at " + channel.toShortString() + " is "
				+ run.level.getBlockState(run.world(channel)) + "), though the stair's solid back keeps the other source from it");
			LOGGER.info("qa 1121e poured water: stream runs past the stair's back, stair dry, at tick {}", helper.getTick());
		});
	}

}
