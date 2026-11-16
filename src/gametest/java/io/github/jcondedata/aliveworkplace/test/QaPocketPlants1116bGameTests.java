package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (2026-11-16, qa-lane-b-1116-0038) past B1280 (0c7c4f1c8), from docs/wiki/builder.md "Stages": the floor of the box
 * counts as a way in only where the spot under it holds water or lets it through and that water reaches past the box's
 * footprint or up to its height; a pocket in the pond's bed shut in by stone does not, so the room ends dry with the
 * drawing's pool kept. QaBuilderPocketDrain1115gGameTests covers plain water, air and lava pockets; these fill the pocket
 * with blocks that hold water without being water: kelp, waterlogged stairs, and a bubble column over magma. Each pocket
 * is shut in by stone on every side but its top, right under the drawn pool. Nothing here is random.
 */
public class QaPocketPlants1116bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Kelp two tall (kelp plant under its top) in a pocket two deep under the pool: dry but the pool. */
	//$ gametest_ticks_batch HUGE '12000' '"qa1116bKelpPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1116bKelpPocket")
	public void aPoolOverKelpShutInTheBedEndsDryButThePool(GameTestHelper helper) {
		QaBuilderPocketDrain1115gGameTests.poolOver(helper, "1116bkelp", () -> {
			helper.setBlock(new BlockPos(8, 4, 8), Blocks.KELP_PLANT);
			helper.setBlock(new BlockPos(8, 5, 8), Blocks.KELP);
			helper.assertTrue(helper.getBlockState(new BlockPos(8, 5, 8)).is(Blocks.KELP), "setup: the kelp didn't stay");
		});
	}

	/** Waterlogged stone stairs filling a pocket two deep under the pool (water that lets nothing out): dry but the pool. */
	//$ gametest_ticks_batch HUGE '12000' '"qa1116bStairsPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1116bStairsPocket")
	public void aPoolOverWaterloggedStairsShutInTheBedEndsDryButThePool(GameTestHelper helper) {
		QaBuilderPocketDrain1115gGameTests.poolOver(helper, "1116bstairs", QaBuilderPocketDrain1115gGameTests.put(helper,
			Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(BlockStateProperties.WATERLOGGED, true),
			new BlockPos(8, 5, 8), new BlockPos(8, 4, 8)));
	}

	/** A bubble column two tall over a magma block, shut in under the pool: dry but the pool. */
	//$ gametest_ticks_batch HUGE '12000' '"qa1116bBubblePocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1116bBubblePocket")
	public void aPoolOverABubbleColumnShutInTheBedEndsDryButThePool(GameTestHelper helper) {
		QaBuilderPocketDrain1115gGameTests.poolOver(helper, "1116bbubble", () -> {
			helper.setBlock(new BlockPos(8, 3, 8), Blocks.MAGMA_BLOCK);
			helper.setBlock(new BlockPos(8, 4, 8), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BlockStateProperties.DRAG, true));
			helper.setBlock(new BlockPos(8, 5, 8), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BlockStateProperties.DRAG, true));
		});
	}
}
