package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA of B1300's fix (lane-c-1121-1555), written from the spec, not the fix: moving-buildings.md, "nothing packed is ever
 * deleted" and "a block that is several of its item drops all of them". A multiface block (a glow lichen, a sculk vein)
 * is one of its item per face, as a player places it: on a Mover's Crate it comes back as exactly that many, never
 * fewer (lost) and never more (made from nothing). QaMoverSnowMix1115hBugTests covers a glow lichen on 3 faces; these add
 * the boundaries (1 face, all 6), the block whose own loot already counts its faces (a sculk vein: no double count), a
 * waterlogged lichen (its water is no item) and a full move with 6 faces and with a waterlogged one.
 */
public class QaMoverMultiface1121cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static BlockState faces(Block block, Direction... on) {
		BlockState state = block.defaultBlockState();
		for (Direction d : Direction.values()) {
			state = state.setValue(MultifaceBlock.getFaceProperty(d), false);
		}
		for (Direction d : on) {
			state = state.setValue(MultifaceBlock.getFaceProperty(d), true);
		}
		return state;
	}

	/** What a crate's drop of {@code state} gives: asserts it is exactly {@code n} of {@code item} and nothing else. */
	private static void assertDrops(GameTestHelper helper, BlockState state, Item item, int n, String what) {
		List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)), state);
		int mine = 0;
		List<ItemStack> other = new ArrayList<>();
		for (ItemStack s : drops) {
			if (s.is(item)) {
				mine += s.getCount();
			} else if (!s.isEmpty()) {
				other.add(s);
			}
		}
		helper.assertTrue(mine == n && other.isEmpty(), what + " on a crate dropped as " + drops + ": " + mine + " " + item + " (wanted " + n
			+ ")" + (other.isEmpty() ? "" : " and other things " + other));
	}

	/** The lower boundary: one face is one glow lichen, not topped up to two. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aGlowLichenOnOneFaceOnACrateIsOneGlowLichen(GameTestHelper helper) {
		assertDrops(helper, faces(Blocks.GLOW_LICHEN, Direction.DOWN), Items.GLOW_LICHEN, 1, "a glow lichen on 1 face");
		assertDrops(helper, faces(Blocks.GLOW_LICHEN, Direction.WEST), Items.GLOW_LICHEN, 1, "a glow lichen on 1 wall");
		helper.succeed();
	}

	/** The upper boundary: on all 6 faces (the most a block has) it is 6 glow lichen. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aGlowLichenOnAllSixFacesOnACrateIsSixGlowLichen(GameTestHelper helper) {
		assertDrops(helper, faces(Blocks.GLOW_LICHEN, Direction.values()), Items.GLOW_LICHEN, 6, "a glow lichen on 6 faces");
		assertDrops(helper, faces(Blocks.GLOW_LICHEN, Direction.UP, Direction.SOUTH), Items.GLOW_LICHEN, 2, "a glow lichen on the ceiling and a wall");
		helper.succeed();
	}

	/**
	 * A sculk vein's own Silk Touch loot already counts its faces: 1, 3 and 6 faces are 1, 3 and 6 sculk veins, never
	 * doubled by a top-up on top of its loot.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aSculkVeinOnACrateIsOneSculkVeinPerFaceNeverDoubled(GameTestHelper helper) {
		assertDrops(helper, faces(Blocks.SCULK_VEIN, Direction.DOWN), Items.SCULK_VEIN, 1, "a sculk vein on 1 face");
		assertDrops(helper, faces(Blocks.SCULK_VEIN, Direction.DOWN, Direction.NORTH, Direction.EAST), Items.SCULK_VEIN, 3, "a sculk vein on 3 faces");
		assertDrops(helper, faces(Blocks.SCULK_VEIN, Direction.values()), Items.SCULK_VEIN, 6, "a sculk vein on 6 faces");
		helper.succeed();
	}

	/** A waterlogged glow lichen (under water, on 2 faces) is 2 glow lichen: its water is no item and no extra lichen. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aWaterloggedGlowLichenOnACrateIsOneGlowLichenPerFace(GameTestHelper helper) {
		BlockState wet = faces(Blocks.GLOW_LICHEN, Direction.DOWN, Direction.EAST).setValue(BlockStateProperties.WATERLOGGED, true);
		assertDrops(helper, wet, Items.GLOW_LICHEN, 2, "a waterlogged glow lichen on 2 faces");
		BlockState wetVein = faces(Blocks.SCULK_VEIN, Direction.DOWN, Direction.NORTH, Direction.UP, Direction.WEST).setValue(BlockStateProperties.WATERLOGGED, true);
		assertDrops(helper, wetVein, Items.SCULK_VEIN, 4, "a waterlogged sculk vein on 4 faces");
		helper.succeed();
	}

	/** A full move: a glow lichen on all 6 faces where the chest stood lands on his crate; 6 glow lichen lie there, none left standing. */
	//$ gametest_batch HUGE '"qaMoverMultiface1121cSix"'
	@GameTest(template = HUGE, batch = "qaMoverMultiface1121cSix")
	public void aGlowLichenOnSixFacesOnHisCrateInAMoveIsSixGlowLichen(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qamultiface1121csix");
		QaMoverCrateVerify1114fGameTests.place(helper, faces(Blocks.GLOW_LICHEN, Direction.values()));
		helper.runAfterDelay(2, () -> {
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.GLOW_LICHEN, 0, "(setup) no glow lichen lying about");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a 6-faced glow lichen on his crate");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.GLOW_LICHEN) == 0, "glow lichen left standing");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.GLOW_LICHEN, 6, "a glow lichen on 6 faces on his crate");
			helper.succeed();
		});
	}

	/**
	 * A full move: a waterlogged sculk vein on 3 faces on his crate: 3 sculk veins, none standing, and no water left
	 * behind where the crate stood.
	 */
	//$ gametest_batch HUGE '"qaMoverMultiface1121cWetVein"'
	@GameTest(template = HUGE, batch = "qaMoverMultiface1121cWetVein")
	public void aWaterloggedSculkVeinOnHisCrateInAMoveIsThreeSculkVeins(GameTestHelper helper) {
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qamultiface1121cwetvein");
		QaMoverCrateVerify1114fGameTests.place(helper, faces(Blocks.SCULK_VEIN, Direction.DOWN, Direction.NORTH, Direction.EAST)
			.setValue(BlockStateProperties.WATERLOGGED, true));
		helper.runAfterDelay(2, () -> {
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a waterlogged 3-faced sculk vein on his crate");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SCULK_VEIN) == 0, "sculk vein left standing");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SCULK_VEIN, 3, "a waterlogged sculk vein on 3 faces on his crate");
			helper.succeed();
		});
	}
}
