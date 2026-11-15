package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA bug tests (2026-11-14, pass g) found while verifying the Mover's Crate drop fixes (B1236, B1246-B1250, B1253); B1258
 * fixed. B1259's test moved to QaMoverCrateDrops1114gB1259BugTests (unregistered until fixed). Same house, Mover and spot as MoverCrateNoRoomB1234GameTests (the chest's spot lands on his crate).
 */
public class QaMoverCrateDrops1114gBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * Three turtle eggs in one block on his crate (room above): the block holds 3 eggs, so 3 turtle eggs must lie there
	 * (B1172: nothing packed is ever destroyed). The drop works the block out as one Silk Touch break, and vanilla's
	 * turtle egg loot is one egg per break (a player breaks a 3-egg block three times), so 2 eggs vanished (B1258).
	 */
	//$ gametest_batch AREA '"qaG1114gTurtleEggs"'
	@GameTest(template = AREA, batch = "qaG1114gTurtleEggs")
	public void threeTurtleEggsOnHisCrateAreDroppedAsThreeEggs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gegg");
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 3));
		helper.runAfterDelay(2, () -> {
			BlockState egg = level.getBlockState(helper.absolutePos(new BlockPos(4, 3, 4)));
			helper.assertTrue(egg.is(Blocks.TURTLE_EGG) && egg.getValue(TurtleEggBlock.EGGS) == 3, "setup: the eggs are now " + egg);
			MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, () -> { });
			MoverCrateNoRoomB1234GameTests.assertEnded(helper, level, s, site, 1, "three turtle eggs on his crate");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.TURTLE_EGG, 3, "the block's three turtle eggs");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.TURTLE_EGG) == 0, "turtle eggs left standing");
			helper.succeed();
		});
	}
}
