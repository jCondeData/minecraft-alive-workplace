package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA lane C (lane-c-1123-1255), B1491, B1523 and B1525 from their specs, at the edges the earlier tests leave open: "only
 * a workstation a villager can actually take hides the other one" (B1477), and lane A's words for "can take": a spot to
 * stand at it, and a walk there "through doors and gates, stepping up one and dropping three". Each case asks the jobless
 * villager's job search (his AI off, so he can't walk in the meantime) whether it sees the smoker (or, for the lectern,
 * the barrel): a barrel he can take hides the smoker from him; one he can't (behind an iron door, a fence, a 4-block
 * drop) doesn't. Set-up: {@link QaJobBarrels1122cGameTests#setup} (fisherman cap on, 1 per village, random jobs off).
 * <p>
 * The pen: glass walls x 11..17, z 9..13, 3 high with a glass roof; the villager inside at (12,2,11), a free smoker
 * inside at (16,2,11) (4 from him), a free barrel just outside the west wall at (9,2,11) (3 from him, 7 from the smoker).
 */
public class QaJobFixes1123c2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = QaFisherSmoker1123cGameTests.HUGE;
	static final BlockPos BARREL = new BlockPos(9, 2, 11);
	static final BlockPos SMOKER = new BlockPos(16, 2, 11);
	static final BlockPos INSIDE = new BlockPos(12, 2, 11);
	/** The pen's west wall, in line with the barrel and the villager: where a door or gate goes. */
	static final BlockPos GAP = new BlockPos(11, 2, 11);

	static void pen(GameTestHelper helper, Block wall, int high, boolean roof) {
		for (int x = 11; x <= 17; x++) {
			for (int z = 9; z <= 13; z++) {
				if (x == 11 || x == 17 || z == 9 || z == 13) {
					for (int y = 2; y < 2 + high; y++) {
						helper.setBlock(new BlockPos(x, y, z), wall);
					}
				}
				if (roof) {
					helper.setBlock(new BlockPos(x, 2 + high, z), Blocks.GLASS);
				}
			}
		}
	}

	static void door(GameTestHelper helper, BlockPos lower, Block door, boolean open) {
		BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, open);
		helper.setBlock(lower, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(lower.above(), s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	static Villager still(GameTestHelper helper, BlockPos at) {
		Villager v = QaFisherSmoker1123cGameTests.jobless(helper, at);
		v.setNoAi(true);
		return v;
	}

	/** After 2 ticks (the villager on the ground), whether his job search sees {@code what}, as {@code sees} says. */
	static void sees(GameTestHelper helper, Villager v, BlockPos what, boolean sees, String why) {
		helper.runAfterDelay(2, () -> {
			check(helper, v, what, sees, why);
			v.discard();
			helper.succeed();
		});
	}

	private static void check(GameTestHelper helper, Villager v, BlockPos what, boolean sees, String why) {
		List<BlockPos> seen = QaFisherSmoker1123cGameTests.search(helper, v);
		helper.assertTrue(seen.contains(helper.absolutePos(what)) == sees, why + (sees ? ": his job search doesn't see " : ": his job search sees ")
			+ helper.getBlockState(what).getBlock().getName().getString() + " at " + what + ": " + QaFisherSmoker1123cGameTests.rel(helper, seen));
	}

	static void penned(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(SMOKER, Blocks.SMOKER);
	}

	/** A closed oak door in the pen's wall: he opens it and walks to the barrel, so it's his to take and hides the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2OakDoor"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2OakDoor")
	public void aBarrelThroughAClosedOakDoorHidesTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.GLASS, 3, true);
		door(helper, GAP, Blocks.OAK_DOOR, false);
		sees(helper, still(helper, INSIDE), SMOKER, false, "a free barrel through a closed oak door, 3 from him");
	}

	/** An open fence gate in a pen of oak fences: he walks out through it, so the barrel hides the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2OpenGate"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2OpenGate")
	public void aBarrelThroughAnOpenGateHidesTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.OAK_FENCE, 1, false);
		helper.setBlock(GAP, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST).setValue(FenceGateBlock.OPEN, true));
		sees(helper, still(helper, INSIDE), SMOKER, false, "a free barrel through an open fence gate");
	}

	/** A pen of oak fences, no gate: fences are too tall to step over, so the barrel outside doesn't hide the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2Fence"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2Fence")
	public void aBarrelBehindAFenceDoesntHideTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.OAK_FENCE, 1, false);
		sees(helper, still(helper, INSIDE), SMOKER, true, "a fence all round him, the barrel outside it");
	}

	/** The pen's walls one block high (glass, no roof): he steps up one and out, so the barrel is his and hides the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2StepUp"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2StepUp")
	public void aBarrelOverAOneBlockWallHidesTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.GLASS, 1, false);
		sees(helper, still(helper, INSIDE), SMOKER, false, "a wall one block high between him and the barrel");
	}

	/**
	 * The pen's walls three blocks high, no roof: he can't climb out (from the smoker's top the wall is still two up), so
	 * the barrel doesn't hide the smoker. (Two high isn't enough: he hops onto the smoker and over, as the walk check
	 * rightly says.)
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2ThreeHigh"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2ThreeHigh")
	public void aBarrelOverAThreeBlockWallDoesntHideTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.GLASS, 3, false);
		sees(helper, still(helper, INSIDE), SMOKER, true, "a wall three blocks high (no roof) between him and the barrel");
	}

	/**
	 * A stone platform (x 11..18, z 9..13) {@code drop} blocks high; he stands on its west edge with a free smoker on it 6
	 * east of him, the barrel on the ground 2 west of the platform (nearer him than the smoker, in its reach).
	 */
	private static Villager platform(GameTestHelper helper, int drop) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		for (int x = 11; x <= 18; x++) {
			for (int z = 9; z <= 13; z++) {
				for (int y = 2; y < 2 + drop; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(new BlockPos(17, 2 + drop, 11), Blocks.SMOKER);
		return still(helper, new BlockPos(11, 2 + drop, 11));
	}

	/** A drop of 3 to the barrel: he jumps down, so it's his to take and hides the smoker up on the platform. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2Drop3"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2Drop3")
	public void aBarrelThreeBlocksDownHidesTheSmoker(GameTestHelper helper) {
		Villager v = platform(helper, 3);
		sees(helper, v, new BlockPos(17, 5, 11), false, "a free barrel a 3-block drop below him");
	}

	/** A drop of 4: a villager doesn't jump that far, so the barrel isn't his and doesn't hide the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2Drop4"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2Drop4")
	public void aBarrelFourBlocksDownDoesntHideTheSmoker(GameTestHelper helper) {
		Villager v = platform(helper, 4);
		sees(helper, v, new BlockPos(17, 6, 11), true, "a free barrel only down a 4-block drop");
	}

	/**
	 * The way out changes between two searches: through an oak door the barrel hides the smoker; the door bricked up with
	 * glass, his next search sees the smoker; the door put back, it's hidden again (no answer kept from an older search).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2Bricked"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2Bricked")
	public void brickingUpTheDoorBetweenSearchesShowsTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.GLASS, 3, true);
		door(helper, GAP, Blocks.OAK_DOOR, false);
		Villager v = still(helper, INSIDE);
		helper.runAfterDelay(2, () -> {
			check(helper, v, SMOKER, false, "first search, the door there");
			helper.setBlock(GAP.above(), Blocks.AIR);
			helper.setBlock(GAP, Blocks.GLASS);
			helper.setBlock(GAP.above(), Blocks.GLASS);
			check(helper, v, SMOKER, true, "second search, the door bricked up with glass");
			door(helper, GAP, Blocks.OAK_DOOR, false);
			check(helper, v, SMOKER, false, "third search, the door back");
			v.discard();
			helper.succeed();
		});
	}

	/**
	 * B1491 through a door: a free lectern in a glass room with a closed oak door, a free barrel 5 south of it outside;
	 * a jobless villager can walk in and take the lectern, so the barrel stays its store and out of his search.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFix1123c2LecternDoor"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFix1123c2LecternDoor")
	public void aLecternBehindAnOakDoorKeepsTheBarrelItsStore(GameTestHelper helper) {
		lecternRoom(helper, Blocks.OAK_DOOR);
		sees(helper, still(helper, new BlockPos(10, 2, 9)), new BlockPos(14, 2, 9), false, "a free lectern he can walk to through an oak door");
	}

	static void lecternRoom(GameTestHelper helper, Block door) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		for (int x = 12; x <= 16; x++) {
			for (int z = 12; z <= 16; z++) {
				if (x == 12 || x == 16 || z == 12 || z == 16) {
					for (int y = 2; y <= 4; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
				helper.setBlock(new BlockPos(x, 5, z), Blocks.GLASS);
			}
		}
		helper.setBlock(new BlockPos(14, 2, 14), Blocks.LECTERN);
		BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		helper.setBlock(new BlockPos(14, 2, 12), s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(14, 3, 12), s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(new BlockPos(14, 2, 9), Blocks.BARREL);
	}
}
