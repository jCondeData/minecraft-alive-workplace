package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane C, 2026-11-22), failing tests kept apart (B1447, open): the Mover takes a house down from its old spot without telling the
 * blocks round it, so a lamp a player set outside the wall, lit by a redstone block in that wall, stays lit with nothing
 * beside it once the house has gone (the house of {@link QaMoverSettle1122cGameTests}, moved a quarter turned). The same for a call-off: the new spot is taken down without telling the lamp beside it.
 */
public class QaMoverSettle1122cBugTests implements FabricGameTest {
	static final String AREA = QaMoverSettle1122cGameTests.AREA;

	/** The house gone from its old spot: the lamp outside its west wall, lit by the wall's redstone block, goes dark. */
	//$ gametest_batch AREA '"qa_c1122_settle_old_lamp"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_old_lamp")
	public void theLampOutsideTheOldSpotGoesDarkOnceTheHouseHasGone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverSettle1122cGameTests.moveThen(helper, "qa_settle_old_lamp", Rotation.CLOCKWISE_90, QaMoverSettle1122cGameTests.SETTLE, (s, where) -> {
			helper.assertTrue(level.getBlockState(helper.absolutePos(QaMoverSettle1122cGameTests.WALL_POWER)).isAir(), "the old wall's redstone block is still there");
			BlockState lamp = level.getBlockState(helper.absolutePos(QaMoverSettle1122cGameTests.OUT_LAMP));
			helper.assertTrue(lamp.is(Blocks.REDSTONE_LAMP) && !lamp.getValue(RedstoneLampBlock.LIT),
				"the lamp outside the old spot at " + helper.absolutePos(QaMoverSettle1122cGameTests.OUT_LAMP).toShortString() + " is " + lamp + " with no wall beside it any more");
		});
	}

	/**
	 * Called off once the walls stand at the new spot, after a player set a lamp beside the wall's redstone block there (it
	 * lit as he placed it): with the house carried back, that lamp goes dark (B1447 notes RESTORE_DOWN as untested).
	 */
	//$ gametest_batch AREA '"qa_c1122_settle_cancel_new_lamp"'
	@GameTest(template = AREA, batch = "qa_c1122_settle_cancel_new_lamp")
	public void calledOffTheLampAPlayerSetBesideTheNewSpotGoesDark(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup[] s = new MoverGameTests.Setup[1];
		BlockPos[] lamp = new BlockPos[1];
		QaMoverSettle1122cGameTests.build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = QaMoverSettle1122cGameTests.capture(helper, "qa_settle_cancel_new_lamp", Rotation.CLOCKWISE_90);
			lamp[0] = QaMoverSettle1122cGameTests.newSpotLamp(s[0], Rotation.CLOCKWISE_90);
			level.setBlock(lamp[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			MoveSite site = MoverGameTests.work(level, s[0], x -> x.stage() == MoveSite.Stage.DECORATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.DECORATION, "it didn't get half way up: " + site.stage());
			BlockPos wall = QaMoverSettle1122cGameTests.moved(s[0], Rotation.CLOCKWISE_90, QaMoverSettle1122cGameTests.WALL_POWER);
			helper.assertTrue(level.getBlockState(wall).is(Blocks.REDSTONE_BLOCK), "setup: the wall's redstone block isn't up at " + wall.toShortString());
			// as a player places it: the lamp reads its power as it goes down (RedstoneLampBlock.getStateForPlacement)
			level.setBlock(lamp[0], Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, level.hasNeighborSignal(lamp[0])), Block.UPDATE_ALL);
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(level.getBlockState(lamp[0]).getValue(RedstoneLampBlock.LIT), "setup: the lamp placed beside the wall's redstone block didn't light");
			PendingMoves.get(level).remove(s[0].from()); // the Move page's Call off
			MoveSite site = MoverGameTests.work(level, s[0], x -> false);
			helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage() + (site.canceled() ? " canceled" : ""));
		});
		helper.runAfterDelay(8 + QaMoverSettle1122cGameTests.SETTLE, () -> {
			BlockState now = level.getBlockState(lamp[0]);
			helper.assertTrue(now.is(Blocks.REDSTONE_LAMP) && !now.getValue(RedstoneLampBlock.LIT), "the lamp beside the new spot at "
				+ lamp[0].toShortString() + " is " + now + " with the house carried back to its old spot");
			helper.succeed();
		});
	}
}
