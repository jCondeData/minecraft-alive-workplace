package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.*;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.test.MoverGameTests.Setup;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA lane E (2026-11-22, lane-e-1122-1913): B1216's promise for a called-off move (its commit and
 * docs/wiki/moving-buildings.md: once the whole building stands again, "at the new spot, or back at the old one after a
 * cancel", every moved block reads its power and tells its neighbors). The house of QaMoverRedstone1121eGameTests: while
 * it stands half way up at the new spot, a player puts a fresh lamp beside where its west wall's redstone block stood at
 * the old spot (dark: nothing powers it then). The move is called off; the house comes back and the lamp must light,
 * as it does at the new spot. Written after a mutation spot-check (the old spot not settled after a call-off) survived
 * the suite.
 */
public class QaMoverCallOff1122eGameTests implements FabricGameTest {

	/** Called off half way up: back at the old spot, the wall's redstone block lights a lamp put beside it while it was away. */
	//$ gametest_batch AREA '"qa_e1122_mover_calloff_new_lamp"'
	@GameTest(template = AREA, batch = "qa_e1122_mover_calloff_new_lamp")
	public void aLampPutBesideTheOldSpotLightsWhenACalledOffHouseComesBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = capture(helper, "qa_calloff_new_lamp");
			MoveSite site = MoverGameTests.work(level, s[0], x -> x.stage() == MoveSite.Stage.DECORATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.DECORATION, "(setup) it didn't get half way up: " + site.stage());
			BlockPos wall = helper.absolutePos(WALL_POWER_WEST);
			helper.assertTrue(level.getBlockState(wall).isAir(), "(setup) the old spot's west wall still stands: " + level.getBlockState(wall));
			// the player's fresh lamp beside the empty spot (the old one broken and placed again, as a player would)
			BlockPos lamp = helper.absolutePos(OUT_LAMP);
			level.setBlock(lamp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
			helper.assertTrue(!level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "(setup) the fresh lamp is lit with nothing beside it");
			PendingMoves.get(level).remove(s[0].from()); // the Move page's Call off
			site = MoverGameTests.work(level, s[0], x -> false);
			helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage() + (site.canceled() ? " canceled" : ""));
			helper.assertTrue(level.getBlockState(wall).is(Blocks.REDSTONE_BLOCK), "the west wall's redstone block isn't back at "
				+ WALL_POWER_WEST.toShortString() + ": " + level.getBlockState(wall));
		});
		helper.runAfterDelay(5 + SETTLE, () -> {
			BlockState lamp = level.getBlockState(helper.absolutePos(OUT_LAMP));
			helper.assertTrue(lamp.is(Blocks.REDSTONE_LAMP) && lamp.getValue(RedstoneLampBlock.LIT), "the lamp put beside the old spot at "
				+ OUT_LAMP.toShortString() + " stays dark with the called-off house's redstone block back beside it: " + lamp);
			String wrong = poweredInside(helper, level, p -> helper.absolutePos(p));
			helper.assertTrue(wrong == null, "back at the old spot: " + wrong);
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[qa1122e] call-off: the fresh lamp beside the old spot lit, the house's wiring as it was");
			helper.succeed();
		});
	}
}
