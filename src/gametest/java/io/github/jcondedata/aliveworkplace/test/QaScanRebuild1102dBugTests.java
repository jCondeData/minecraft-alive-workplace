package io.github.jcondedata.aliveworkplace.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1102-0638): Scan Tool round trips that fail on main (bugs B816 sugar cane, B817 sea pickles, B818 tall seagrass), with the helpers of
 * {@link QaScanRebuild1102dGameTests}. Merge this branch with the fixes.
 */
public class QaScanRebuild1102dBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Sugar cane three tall on sand beside water: the cane needs the water beside its foot. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102dCane"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102dCane")
	public void scannedSugarCaneByWaterIsBuiltThreeTall(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
		}
		s.put(1, 1, 1, Blocks.SAND);
		s.put(0, 1, 1, Blocks.STONE);
		s.put(2, 1, 1, Blocks.STONE);
		s.put(1, 1, 0, Blocks.STONE);
		s.put(1, 1, 2, Blocks.WATER); // the water beside the cane's foot, walled
		s.put(0, 1, 2, Blocks.STONE);
		s.put(2, 1, 2, Blocks.STONE);
		s.put(0, 1, 0, Blocks.STONE);
		s.put(2, 1, 0, Blocks.STONE);
		for (int dy = 2; dy <= 4; dy++) {
			s.put(1, dy, 1, Blocks.SUGAR_CANE);
		}
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SUGAR_CANE, 3), false);
	}

	/** Three sea pickles in a walled pool are asked as three and stand as three, in water. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102dPickles"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102dPickles")
	public void threeScannedSeaPicklesInAPoolAreThreeAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= 2; dy++) {
					if (dx != 1 || dz != 1) {
						s.put(dx, dy, dz, Blocks.STONE);
					}
				}
			}
		}
		s.put(1, 1, 1, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3).setValue(SeaPickleBlock.WATERLOGGED, true));
		s.put(1, 2, 1, Blocks.WATER);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SEA_PICKLE, 3), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = helper.getLevel().getBlockState(b.at(1, 1, 1));
			helper.assertTrue(p.is(Blocks.SEA_PICKLE) && p.getValue(SeaPickleBlock.PICKLES) == 3 && p.getValue(SeaPickleBlock.WATERLOGGED),
				"the three pickles in water were built as " + p);
		});
	}

	/** Tall seagrass (no item of its own: seagrass and bone meal) in a walled pool two deep stands again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102dTallSeagrass"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102dTallSeagrass")
	public void scannedTallSeagrassInAPoolIsBuiltAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.SAND);
				for (int dy = 1; dy <= 3; dy++) {
					if (dx != 1 || dz != 1) {
						s.put(dx, dy, dz, Blocks.STONE);
					}
				}
			}
		}
		s.put(1, 1, 1, Blocks.TALL_SEAGRASS.defaultBlockState());
		s.put(1, 2, 1, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(net.minecraft.world.level.block.TallSeagrassBlock.HALF,
			net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		s.put(1, 3, 1, Blocks.WATER);
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(), false);
	}
}
