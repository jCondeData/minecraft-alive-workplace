package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;

/**
 * B47: a house called done without its door. A door needs both its cells; with something in the upper one each time the
 * builder comes to it (a player's scaffold block, a neighbor's pillar), the door is deferred, then given up for the
 * build. Once the block is gone again the builder's last look before DONE (B508) must go back for the door. A block
 * that stays keeps the door out, counted as skipped once, and is never broken. Upkeep (the later repair round) is off,
 * so only the build itself can set the door.
 */
public class B47GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static void upkeepOff(GameTestHelper helper) {
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
	}

	/** The hut's door: the step of its lower half, which carries the upper half as its second block. */
	private static BuildPlan.Step door(QaBuilderDisruptionGameTests.Run r) {
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : r.plan().steps(stage)) {
				if (s.state().getBlock() instanceof DoorBlock && s.secondaryPos() != null) {
					return s;
				}
			}
		}
		throw new GameTestAssertException("setup: the test hut has no door");
	}

	private static boolean pastClear(QaBuilderDisruptionGameTests.Run r) {
		return r.site().stage() != BuildPlan.Stage.CLEAR;
	}

	/**
	 * A player's cobblestone stands in the door's upper cell from the foundation on, and is taken away once the builder
	 * has given the door up. The hut ends exactly as drawn, door and all, with nothing counted skipped.
	 */
	//$ gametest_ticks_batch AREA '6000' '"b47DoorBack"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "b47DoorBack")
	public void aDoorGivenUpUnderABlockIsSetOnceTheBlockIsGone(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		BuildPlan.Step door = door(r);
		BlockPos top = door.secondaryPos();
		boolean[] put = {false};
		boolean[] gone = {false};
		helper.onEachTick(() -> {
			if (!put[0] && pastClear(r)) {
				put[0] = true;
				r.level().setBlockAndUpdate(top, Blocks.COBBLESTONE.defaultBlockState());
			} else if (put[0] && !gone[0] && r.site().leftOut(door.pos())) {
				gone[0] = true;
				helper.assertTrue(r.level().getBlockState(top).is(Blocks.COBBLESTONE), "the builder broke the player's block over the door");
				r.level().setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(gone[0], "setup: the door was never given up under the block: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(r.level().getBlockState(door.pos()).getBlock() instanceof DoorBlock, "the hut was called done without its door (at "
				+ helper.relativePos(door.pos()) + ": " + r.level().getBlockState(door.pos()) + ", skipped " + r.site().skipped() + ")");
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			helper.assertTrue(r.site().skipped() == 0, "the door was set, yet " + r.site().skipped() + " step(s) counted skipped");
			helper.assertFalse(r.site().leftOut(door.pos()), "the door stands, yet is still listed as left out");
		});
	}

	/**
	 * The block over the door stays for the whole build: the door stays out, counted as skipped exactly once, and the
	 * player's block is never broken for it.
	 */
	//$ gametest_ticks_batch AREA '6000' '"b47DoorOut"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "b47DoorOut")
	public void aDoorUnderABlockThatStaysIsSkippedOnce(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		BuildPlan.Step door = door(r);
		BlockPos top = door.secondaryPos();
		boolean[] put = {false};
		helper.onEachTick(() -> {
			if (!put[0] && pastClear(r)) {
				put[0] = true;
				r.level().setBlockAndUpdate(top, Blocks.COBBLESTONE.defaultBlockState());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(put[0], "setup: the block was never put over the door");
			helper.assertTrue(r.level().getBlockState(top).is(Blocks.COBBLESTONE), "the player's block over the door is gone: " + r.level().getBlockState(top));
			helper.assertTrue(r.level().getBlockState(door.pos()).isAir(), "something stands in the doorway: " + r.level().getBlockState(door.pos()));
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of(door.pos()));
			helper.assertTrue(r.site().skipped() == 1, "the door left out should be counted skipped once, not " + r.site().skipped());
		});
	}
}
