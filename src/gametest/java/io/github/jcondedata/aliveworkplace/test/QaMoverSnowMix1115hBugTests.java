package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA bug probes (lane b, 2026-11-15, h): blocks that are several of their item by faces, not by a count property, on a
 * Mover's Crate. moving-buildings.md: "nothing packed is ever deleted"; "a block that is several of its item drops all
 * of them". A glow lichen on three faces is three glow lichen (one placed per face; shears get all three).
 */
public class QaMoverSnowMix1115hBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A glow lichen on 3 faces, worked out as dropped on a crate: 3 glow lichen, not 1. */
	//$ gametest_batch HUGE '"qaH1115hBugLichen"'
	@GameTest(template = HUGE, batch = "qaH1115hBugLichen")
	public void aGlowLichenOnThreeFacesOnACrateIsThreeGlowLichen(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
			.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true).setValue(MultifaceBlock.getFaceProperty(Direction.EAST), true);
		List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(helper.getLevel(), helper.absolutePos(new BlockPos(5, 3, 5)), lichen);
		int n = drops.stream().filter(s -> s.is(Items.GLOW_LICHEN)).mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(n == 3, "a glow lichen on 3 faces on a crate dropped as " + drops + ": " + n + " glow lichen, not 3 (2 lost)");
		helper.succeed();
	}

	/**
	 * A glow lichen on 3 faces (the floor and two walls) where the chest stood, which lands on his crate, a full move: 3
	 * glow lichen lie there (3 were placed), none left standing.
	 */
	//$ gametest_batch HUGE '"qaH1115hBugLichenMove"'
	@GameTest(template = HUGE, batch = "qaH1115hBugLichenMove")
	public void aGlowLichenOnThreeFacesOnHisCrateInAMoveIsThreeGlowLichen(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qah1115hbuglichen");
		BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
			.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true).setValue(MultifaceBlock.getFaceProperty(Direction.EAST), true);
		QaMoverCrateVerify1114fGameTests.place(helper, lichen);
		helper.runAfterDelay(2, () -> {
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a 3-faced glow lichen on his crate");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.GLOW_LICHEN) == 0, "glow lichen left standing");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.GLOW_LICHEN, 3, "a glow lichen on 3 faces on his crate (2 lost)");
			helper.succeed();
		});
	}
}
