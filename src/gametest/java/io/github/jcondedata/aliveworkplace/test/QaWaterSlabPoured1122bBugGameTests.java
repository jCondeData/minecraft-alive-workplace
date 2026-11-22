package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaWaterSlab1122bGameTests.*;

import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA lane B (2026-11-22, lane-b-1122-0338), B1377's "so the build finishes as drawn" when the water was there first: the
 * player built the basin and poured the sources before handing over the drawing, so the slab's spot is a source and the
 * drawn-air spot in front of it holds water. The build ends with the slab dry but a flowing-water block (level 1) left
 * standing in the drawn-air spot, still there 60 ticks after the builder is done (fails 2 of 2). Likely B1377's quiet
 * placement (no neighbor updates, fluid tick dropped) of the slab beside a source, which leaves the puddle it fed with no
 * tick to drain; not proven.
 */
public class QaWaterSlabPoured1122bBugGameTests implements FabricGameTest {

	/**
	 * The player had built the basin and poured the three sources before giving the builder the drawing; the water ran as
	 * water does, so the slab's spot is a source and the drawn-air spot past it holds water. The builder clears it and
	 * finishes as drawn: the slab dry, the drawn-air spot empty, nothing spilt.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaWaterSlabPoured1122bBug"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaWaterSlabPoured1122bBug")
	public void aSlabInAPoolThePlayerFilledFirstIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = drawing(L_WATER, SLAB);
		ResourceLocation id = draw(helper, "poured", drawn);
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			BlockState want = drawn.apply(p);
			if (!want.is(Blocks.WATER) && !p.equals(DRY)) {
				level.setBlock(helper.absolutePos(ORIGIN.offset(p)), want, Block.UPDATE_ALL);
			}
		}
		for (BlockPos p : L_WATER) {
			level.setBlock(helper.absolutePos(ORIGIN.offset(p)), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		}
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(level.getFluidState(helper.absolutePos(ORIGIN.offset(DRY))).isSource(),
				"(setup) the slab's spot didn't fill with a source: " + helper.getBlockState(ORIGIN.offset(DRY)));
			helper.assertTrue(!helper.getBlockState(ORIGIN.offset(OPEN)).getFluidState().isEmpty(),
				"(setup) the drawn-air spot past it holds no water: " + helper.getBlockState(ORIGIN.offset(OPEN)));
			Job job = start(helper, id, new ItemStack(Items.SMOOTH_STONE_SLAB));
			finishesAsDrawn(helper, job, drawn);
		});
	}
}
