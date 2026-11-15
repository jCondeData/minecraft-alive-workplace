package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;

/**
 * QA bug tests (2026-11-15, qa-lane-b-1115-0938), NOT registered: they fail on main. Register them with their fixes.
 * <ul>
 * <li>B1272's follow-up (30a5bd766): a shut room's drawn pool counts as a way in when the world spot under it holds
 * water, even a pocket in the pond's bed shut in by stone, so the sunk room floods again (builder.md "Stages": the
 * room ends dry and a pool in a shut room's floor stays).</li>
 * <li>B1274's kin: 8 snow layers on his crate drop as 1 snow block and 7 snow (vanilla's Silk Touch loot gives a snow
 * block for 8 layers, and MoveSite.eachOfMany then adds a snow item for each of the other 7 layers), not 8 snow
 * (moving-buildings.md: a block that is several of its item drops all of them, nothing doubled).</li>
 * </ul>
 * Helpers are QaBuilderMoverVerify1115bGameTests'.
 */
public class QaBuilderMoverVerify1115bBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The pool basin sunk in a pond, the bed under the pool's one spot a water-filled pocket (a hole in the bed the pond
	 * filled), the rest of the bed solid stone: the pocket is shut in by stone, so the room must still end dry but the
	 * pool. Seen: 18 water sources fill the room and the site counts as done (74 wet spots at the first STRUCTURE tick).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaV1115bPoolPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaV1115bPoolPocket")
	public void aDrawnPoolOverAWaterPocketInTheBedKeepsItsWaterAndTheRestEndsDry(GameTestHelper helper) {
		QaBuilderMoverVerify1115bGameTests.poolCase(helper, new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, Blocks.WATER.defaultBlockState()),
			Blocks.STONE, true, "poolpocket", () -> helper.setBlock(new BlockPos(8, 2, 8), Blocks.WATER));
	}

	/** 8 snow layers as the crate's drop works them out: 8 snow and nothing else. Seen: 1 snow block and 7 snow. */
	//$ gametest_batch HUGE '"qaV1115bSnowEightDrop"'
	@GameTest(template = HUGE, batch = "qaV1115bSnowEightDrop")
	public void eightSnowLayersDropAsEightSnow(GameTestHelper helper) {
		List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)),
			Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8));
		helper.assertTrue(QaBuilderMoverVerify1115bGameTests.count(drops, Items.SNOW) == 8 && QaBuilderMoverVerify1115bGameTests.total(drops) == 8,
			"8 snow layers dropped as " + drops + ", not 8 snow");
		helper.succeed();
	}

	/** Eight snow layers (a full block's height) on his crate, a full move: eight snow, no snowballs. Seen: 7 snow. */
	//$ gametest_batch HUGE '"qaV1115bSnowEight"'
	@GameTest(template = HUGE, batch = "qaV1115bSnowEight")
	public void eightSnowLayersOnHisCrateAreEightSnow(GameTestHelper helper) {
		QaBuilderMoverVerify1115bGameTests.snowMove(helper, 8, "qav1115bsnow8");
	}
}
