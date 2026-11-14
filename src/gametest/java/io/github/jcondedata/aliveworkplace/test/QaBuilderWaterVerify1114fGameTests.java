package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNoFluid;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-14, verifying B1243's fix), written from the bug's expected behavior (docs/wiki/builder.md "Stages": CLEAR
 * takes away what is in the way, the finished build stands as drawn; B1243: water where the drawing has air is taken
 * away, as a player scoops it with a bucket, the room dry), not from the code. QaBuilderWater1114bBugTests covers the
 * test hut on a pond's bed and sunk in a lake. These add: waterlogged slabs and stairs standing in the sunk room; lava
 * standing in the room (fire spread on); a save and load in the middle of CLEAR and again right after it; and the lake
 * round a sunk hut: only what borders the build may be touched, the rest of the lake stays water with no dirt in it.
 * Setup helpers (bench, chest and barrels, free materials off) are QaBuilderTerrain1102GameTests'.
 */
public class QaBuilderWaterVerify1114fGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	private static void fireOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
	}

	/** A stone plateau two high and a pond two deep (y 2-3) exactly the hut's footprint (x 6-10, z 6-10), the hut on its bed. */
	private static QaBuilderTerrain1102GameTests.Run pondInFootprint(GameTestHelper helper, net.minecraft.world.level.block.Block fluid) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 3, 6, 10, fluid);
		return start(helper, 4, new BlockPos(6, 2, 6));
	}

	/** The room's 3x3 just above the floor (y 3) filled with waterlogged oak slabs (bottom) and stairs, in the pond. */
	private static void waterloggedFurniture(GameTestHelper helper) {
		BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.WATERLOGGED, true);
		for (int x = 7; x <= 9; x++) {
			for (int z = 7; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 3, z), (x + z) % 2 == 0 ? slab : stair);
			}
		}
	}

	private static List<String> wet(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		List<String> out = new ArrayList<>();
		BoundingBox box = r.plan().bounds();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!r.level().getFluidState(p).isEmpty()) {
				out.add(helper.relativePos(p).toShortString() + "=" + r.level().getBlockState(p));
			}
		}
		return out;
	}

	/**
	 * The hut on a pond's bed with waterlogged slabs and stairs filling the room's lowest layer: CLEAR takes the slabs
	 * and stairs away, and their water too; the finished hut is as drawn with no water in its box at all, the builder alive.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114fWaterlogged"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114fWaterlogged")
	public void waterloggedSlabsAndStairsInTheSunkRoomEndDry(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = pondInFootprint(helper, Blocks.WATER);
		waterloggedFurniture(helper);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			List<String> wet = wet(helper, r);
			helper.assertTrue(wet.isEmpty(), "water left in the finished hut's box: " + wet);
			LOGGER.info("qa 1114f waterlogged: dry at tick {}", helper.getTick());
		});
	}

	/**
	 * Lava two deep exactly in the hut's footprint and the hut on its bed, fire spread on: the room ends with no lava,
	 * the wooden hut stands as drawn with no fire in or round it, and the builder lives.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114fLavaRoom"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114fLavaRoom")
	public void lavaStandingInTheSunkRoomIsTakenAwayAndNothingBurns(GameTestHelper helper) {
		fireOn(helper);
		QaBuilderTerrain1102GameTests.Run r = pondInFootprint(helper, Blocks.LAVA);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			BoundingBox box = r.plan().bounds().inflatedBy(3);
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				helper.assertFalse(r.level().getBlockState(p).is(BlockTags.FIRE), "fire at " + helper.relativePos(p) + " by the finished hut");
			}
			LOGGER.info("qa 1114f lava room: dry at tick {}", helper.getTick());
		});
	}

	/**
	 * The hut on a pond's bed with waterlogged slabs and stairs in the room, the world saved and loaded in the middle of
	 * CLEAR (after 2 of the 9 are taken) and again on the first tick of a later stage: the loaded site still ends dry and
	 * as drawn.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114fReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114fReload")
	public void reloadedMidClearAndRightAfterItTheSunkRoomStillEndsDry(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = pondInFootprint(helper, Blocks.WATER);
		waterloggedFurniture(helper);
		watchDeath(helper, r);
		int[] reloads = {0};
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (now == null || reloads[0] >= 2) {
				return;
			}
			int left = 0;
			for (int x = 7; x <= 9; x++) {
				for (int z = 7; z <= 9; z++) {
					BlockState s = helper.getBlockState(new BlockPos(x, 3, z));
					if (s.is(Blocks.OAK_SLAB) || s.is(Blocks.OAK_STAIRS)) {
						left++;
					}
				}
			}
			boolean midClear = reloads[0] == 0 && now.stage() == BuildPlan.Stage.CLEAR && left <= 7;
			boolean afterClear = reloads[0] == 1 && now.stage() != BuildPlan.Stage.CLEAR;
			if (midClear || afterClear) {
				reloads[0]++;
				LOGGER.info("qa 1114f reload {} at stage {} with {} of 9 left, wet {}", reloads[0], now.stage(), left, wet(helper, r).size());
				r.builder()[0] = reload(helper, r.level(), r.builder()[0]);
				helper.assertTrue(r.site() != null && r.site() != now, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloads[0] == 2, "reloaded " + reloads[0] + " times, not mid-CLEAR and after it");
			assertFinished(helper, r);
			List<String> wet = wet(helper, r);
			helper.assertTrue(wet.isEmpty(), "water left in the finished hut's box after the reloads: " + wet);
		});
	}

	/**
	 * A lake two deep (x 8-22, z 8-22, y 2-3, cut into a stone plateau) and the hut sunk in it, its doorway under
	 * the water line: the hut ends dry and as drawn, and the lake three blocks or more from the hut's box (past its leveled ring) is untouched: every
	 * spot there is still a water source (nothing drained), with no dirt put in it.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114fLakeKept"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114fLakeKept")
	public void theLakeRoundASunkHutIsLeftBeyondItsBorder(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 8, 22, 2, 3, 8, 22, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 4, new BlockPos(13, 2, 13));
		BoundingBox box = r.plan().bounds();
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			List<String> touched = new ArrayList<>();
			for (int x = 8; x <= 22; x++) {
				for (int z = 8; z <= 22; z++) {
					for (int y = 2; y <= 3; y++) {
						BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
						int dx = Math.max(box.minX() - abs.getX(), abs.getX() - box.maxX());
						int dz = Math.max(box.minZ() - abs.getZ(), abs.getZ() - box.maxZ());
						if (Math.max(dx, dz) <= 2) {
							continue; // the hut's box and the two-block ring round it (the plug, the leveled ring)
						}
						BlockState s = helper.getBlockState(new BlockPos(x, y, z));
						if (!s.is(Blocks.WATER) || !s.getFluidState().isSource()) {
							touched.add("(" + x + "," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
						}
					}
				}
			}
			helper.assertTrue(touched.isEmpty(), touched.size() + " spot(s) of the lake 3+ blocks from the hut were drained or filled: "
				+ touched.stream().limit(12).toList());
			LOGGER.info("qa 1114f lake kept: hut dry, lake whole at tick {}", helper.getTick());
		});
	}
}
