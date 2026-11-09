package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.work.Walker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Tilt;

/**
 * QA (lane-b-1108-0337, B1030): B1017 asked for one ground rule for workers ({@link Walker#canStand}) and builders'
 * paths ({@link Paths#standOn}, used by {@link Paths#walkable}). They still disagree on two blocks whose top is a little
 * lower than a block and flat all across: a big dripleaf (15/16; it tips anyone standing on it into the gap below, so
 * it is no ground for anyone) and an end portal frame (13/16). The path's rule takes both for ground, the worker's
 * takes neither. Passing grounds: QaWalkerGround1108GameTests. Fixed (B1030): both rules are now one
 * ({@link Walker#isGround}); a big dripleaf, in every tilt, is ground for neither, and an end portal frame (flat all
 * across) is ground for both.
 */
public class QaWalkerGround1108BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest AREA
	@GameTest(template = AREA)
	public void workersAndPathsAgreeOnADripleafAndAnEndPortalFrame(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState[] grounds = {Blocks.BIG_DRIPLEAF.defaultBlockState(), Blocks.END_PORTAL_FRAME.defaultBlockState()};
		StringBuilder disagree = new StringBuilder();
		StringBuilder all = new StringBuilder();
		for (int i = 0; i < grounds.length; i++) {
			BlockPos ground = new BlockPos(1 + 3 * i, 2, 3);
			helper.setBlock(ground, grounds[i]);
			BlockPos abs = helper.absolutePos(ground);
			BlockState placed = level.getBlockState(abs);
			boolean walker = Walker.canStand(level, abs.above());
			boolean paths = Paths.standOn(level, abs, placed);
			all.append(placed.getBlock().getDescriptionId()).append(walker ? "+" : "-").append(paths ? "+" : "-").append(' ');
			if (walker != paths) {
				disagree.append(placed).append(": worker ").append(walker).append(", path ").append(paths).append("; ");
			}
		}
		QaBasementGround1104GameTests.LOGGER.info("qa walker ground 1108 bug (worker, path): {}", all);
		helper.assertTrue(disagree.isEmpty(), "the worker's and the path's ground rules disagree: " + disagree);
		BlockPos spot = new BlockPos(10, 2, 6);
		BlockPos abs = helper.absolutePos(spot);
		for (Tilt tilt : new Tilt[] {Tilt.NONE, Tilt.UNSTABLE, Tilt.PARTIAL}) {
			helper.setBlock(spot, Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.TILT, tilt));
			BlockState leaf = level.getBlockState(abs);
			helper.assertFalse(Walker.canStand(level, abs.above()) || Paths.standOn(level, abs, leaf), leaf + " is taken for ground");
		}
		helper.setBlock(spot, Blocks.END_PORTAL_FRAME.defaultBlockState());
		helper.assertTrue(Walker.canStand(level, abs.above()) && Paths.standOn(level, abs, level.getBlockState(abs)),
			"an end portal frame (flat all across, 13/16) isn't ground");
		helper.succeed();
	}
}
