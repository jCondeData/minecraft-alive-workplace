package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA bug tests (2026-11-14, pass g) found while verifying the Mover's Crate drop fixes (B1236, B1246-B1250, B1253). They
 * fail on main. Same house, Mover and spot as MoverCrateNoRoomB1234GameTests (the chest's spot lands on his crate).
 */
public class QaMoverCrateDrops1114gBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * Three turtle eggs in one block on his crate (room above): the block holds 3 eggs, so 3 turtle eggs must lie there
	 * (B1172: nothing packed is ever destroyed). The drop works the block out as one Silk Touch break, and vanilla's
	 * turtle egg loot is one egg per break (a player breaks a 3-egg block three times), so 2 eggs vanish.
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

	/**
	 * His crate stays put while the house moves 2 blocks south-east (B1120); a player's barrel stands on the spot 2
	 * south-east of the crate (where it would have gone). When the move ends, the Mover's job site must still be his
	 * crate. Seen: it is moved to that barrel (a fisherman's workstation), so hasCrate says he lost his crate.
	 */
	//$ gametest_batch AREA '"qaG1114gBarrelAtCrateSpot"'
	@GameTest(template = AREA, batch = "qaG1114gBarrelAtCrateSpot")
	public void aBarrelWhereHisCrateWouldHaveMovedDoesNotTakeHisJobSite(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qag1114gbarrelspot");
		BlockPos crate = helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
		BlockPos moved = crate.offset(2, 0, 2);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(s.to().subtract(s.from()).equals(new BlockPos(2, 0, 2)), "setup: the move isn't 2 south-east: " + s.to().subtract(s.from()));
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			helper.assertTrue(level.getBlockState(moved).isAir(), "setup: the spot 2 south-east of his crate holds " + level.getBlockState(moved));
			level.setBlock(moved, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
			site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			String job = s.mover().getBrain().getMemory(MemoryModuleType.JOB_SITE)
				.map(g -> level.getBlockState(g.pos()).getBlock() + " at " + g.pos().subtract(crate) + " from his crate").orElse("none");
			helper.assertTrue(MoverWork.hasCrate(level, s.mover()), "the Mover's job site after the move is " + job + ", not his crate (which still stands: "
				+ level.getBlockState(crate).getBlock() + ")");
			helper.succeed();
		});
	}
}
