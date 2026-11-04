package io.github.jcondedata.aliveworkplace.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1104-2137), bugs found verifying B952 and B937; they fail on main 'ee399df8c'. Not registered on main: the
 * builder registers each with its fix. Helpers in {@link QaStruckAndTop1104GameTests}.
 */
public class QaStruckAndTop1104BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A builder at the bottom of a basement pit (5 by 5, 4 deep, open to the sky), his bench on the pit's rim within the
	 * bolt's reach: a real bolt turns him. As on a death, his whole bag and one blueprint must survive. Today (B952 gap)
	 * no spot out of the bolt's reach is found (the bench is in it, the pit's floor is all within 3 blocks of him, the
	 * ground round it 4 over his feet), so everything is dropped where he stood and the bolt burns it all.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaStruckPit"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaStruckPit")
	public void aRealBoltOnABuilderInADeepPitBesideHisBenchLeavesHisBagAndBlueprint(GameTestHelper helper) {
		// the ground round the pit reaches past where a spot out of the bolt's reach is looked for, as in a real hillside
		QaStruckAndTop1104GameTests.struckInPlace(helper, new BlockPos(15, 6, 12), List.of(new BlockPos(15, 2, 15)), new int[] {0}, h -> {
			Map<BlockPos, net.minecraft.world.level.block.state.BlockState> was = new HashMap<>();
			for (int x = 3; x <= 27; x++) {
				for (int z = 3; z <= 27; z++) {
					boolean pit = x >= 13 && x <= 17 && z >= 13 && z <= 17;
					for (int y = -3; y <= 5; y++) {
						if (y < 1) {
							// solid rock under the test area's floor too (put back after), as under a real basement
							BlockPos abs = h.absolutePos(new BlockPos(x, y, z));
							was.put(abs, h.getLevel().getBlockState(abs));
						}
						h.setBlock(new BlockPos(x, y, z), pit && y >= 2 ? Blocks.AIR : Blocks.STONE);
					}
				}
			}
			Leftovers.after(h, () -> was.forEach((abs, state) -> h.getLevel().setBlock(abs, state, 2)));
		});	}

	/**
	 * B952 gap: a builder on top of a 3 by 3 tower five blocks high (a chimney or a tall wall he is building), his bench on
	 * the ground beside it within 3 blocks of him: a real bolt turns him. As on a death, his whole bag and one blueprint
	 * must survive. Today nothing is found out of the bolt's reach (the bench is in it, the ground lies more than 3 under
	 * his feet), so everything is dropped where he stood and the bolt burns it all.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaStruckTower"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaStruckTower")
	public void aRealBoltOnABuilderAtopATowerOverHisBenchLeavesHisBagAndBlueprint(GameTestHelper helper) {
		QaStruckAndTop1104GameTests.struckInPlace(helper, new BlockPos(15, 2, 12), List.of(new BlockPos(15, 7, 15)), new int[] {0}, h -> {
			for (int x = 14; x <= 16; x++) {
				for (int z = 14; z <= 16; z++) {
					for (int y = 2; y <= 6; y++) {
						h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		});
	}

	/**
	 * B937 gap: a blueprint laid on a lone block at y 319 (the world's top layer), lower ground round it, so none of the
	 * hut can stand. The hand-over takes it ("those blocks will be left out and need no materials"), but the builder then
	 * asks for cobblestone to fill a foundation under a hut that will never be there, and waits for it for ever.
	 * Expected: refused, or ended at once with no material used and the blueprint back.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaWhollyOverPillar"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaWhollyOverPillar")
	public void aBlueprintLaidWhollyOverTheTopOnAPillarUsesNothingAndKeepsTheBlueprint(GameTestHelper helper) {
		QaStruckAndTop1104GameTests.whollyOver(helper, false);
	}
}
