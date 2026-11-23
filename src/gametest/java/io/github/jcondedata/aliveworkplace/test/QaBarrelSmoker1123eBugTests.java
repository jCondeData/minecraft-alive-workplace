package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (2026-11-23), B1496's fix (d47d10901): a case where it disagrees with its spec, failing on main (3 of 3 runs):
 * a barrel no villager can take still hides a free smoker from a jobless villager standing nearer that barrel, so he
 * stays jobless. Not registered in fabric.mod.json until fixed. Helpers in {@link QaBarrelSmoker1123eGameTests}.
 */
public class QaBarrelSmoker1123eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = QaBarrelSmoker1123eGameTests.HUGE;
	private static final BlockPos BARREL = QaBarrelSmoker1123eGameTests.BARREL;
	private static final BlockPos BESIDE = QaBarrelSmoker1123eGameTests.BESIDE;
	private static final BlockPos SMOKER = QaBarrelSmoker1123eGameTests.SMOKER;

	// ---- 7. a barrel nobody can take -------------------------------------------------------------------------------------

	/**
	 * A barrel walled in with stone on its 4 sides and on top (nowhere to stand to take it), the village with room for a
	 * fisherman, a free smoker 5 east. A jobless villager 2 blocks from the barrel (nearer it than the smoker) can never take
	 * that barrel; it isn't "the free barrel" he waits for, so he becomes the Butcher at the smoker (never jobless forever).
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaBarrelSmoker1123eSealed"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaBarrelSmoker1123eSealed")
	public void qaABarrelNobodyCanTakeDoesntKeepTheVillagerFromTheSmoker(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
			helper.setBlock(BARREL.relative(d), Blocks.STONE);
		}
		helper.setBlock(SMOKER.east(), Blocks.SMOKER);
		Villager v = QaBarrelSmoker1123eGameTests.joblessAbs(helper, helper.absolutePos(BESIDE.south()));
		BlockPos smoker = helper.absolutePos(SMOKER.east());
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "sealed barrel: the villager died");
			helper.assertTrue(QaBarrelSmoker1123eGameTests.holds(v, VillagerProfession.BUTCHER, smoker), "sealed barrel: not the Butcher at the free smoker yet: " + QaBarrelSmoker1123eGameTests.where(helper, v));
			v.discard();
		});
	}
}
