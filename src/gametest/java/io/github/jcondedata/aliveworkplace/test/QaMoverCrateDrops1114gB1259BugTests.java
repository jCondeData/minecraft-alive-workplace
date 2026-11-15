package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug test (2026-11-14, pass g), NOT registered: fails on main (B1259). Moved unchanged out of
 * QaMoverCrateDrops1114gBugTests when B1258's test there was fixed (lane-a-1115-0022), so this one open bug doesn't turn
 * main red; register it with B1259's fix. Same house, Mover and spot as MoverCrateNoRoomB1234GameTests.
 */
public class QaMoverCrateDrops1114gB1259BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

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
