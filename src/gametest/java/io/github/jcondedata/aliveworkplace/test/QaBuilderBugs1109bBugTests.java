package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.work.Walker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1109-2137, 2026-11-09), B1030's promise (Paths.standOn: "a path never leads where a worker won't
 * stand"): a worker never stands in a fire (Walker.canStand refuses it), but Paths.walkable takes a fire burning on
 * netherrack (a ruined portal's, the Nether's) as a spot for a builder's path or a Steward's road, because a fire can be
 * replaced. Fails on main: the path takes the fire's spot.
 */
public class QaBuilderBugs1109bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * B1030 (a path never leads where a worker won't stand): a fire burning on netherrack is no place for a worker to
	 * stand, and no spot for a path either.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPathNeverLeadsIntoAFireAWorkerWontStandIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = new BlockPos(4, 2, 4);
		helper.setBlock(ground, Blocks.NETHERRACK);
		helper.setBlock(ground.above(), Blocks.FIRE);
		BlockPos feet = helper.absolutePos(ground.above());
		helper.assertTrue(level.getBlockState(feet).is(Blocks.FIRE), "(setup) no fire");
		boolean worker = Walker.canStand(level, feet);
		BlockPos path = Paths.walkable(level, feet);
		helper.assertFalse(worker, "a worker may stand in a fire");
		helper.assertFalse(feet.equals(path), "a path may lead into a fire on netherrack, where a worker won't stand (worker " + worker + ")");
		helper.succeed();
	}

}
