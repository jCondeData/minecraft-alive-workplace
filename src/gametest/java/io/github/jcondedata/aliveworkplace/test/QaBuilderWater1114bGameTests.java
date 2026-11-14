package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNoFluid;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNothingMadeOrLost;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-14, lane-b-1114-1237's tester): a Builder building the test hut on and near water and lava, written from
 * the spec (CLAUDE.md "How the builder works"; docs/wiki/builder.md "Stages": CLEAR takes away what is in the way,
 * FOUNDATION fills under the build down to solid ground as deep as {@code workplaceFoundationDepth}, every build ends;
 * ROADMAP 23.1 "no item is duplicated or lost"), not from the code. Corners the older terrain tests
 * (QaBuilderTerrain1102GameTests: one pond source by the doorway, three lava blocks in the floor) leave open:
 * <ul>
 * <li>lava filling the hut's whole floor layer, fire spread on;</li>
 * <li>a pond, and a lava pool, two deep under the floor of a hut on a plateau (FOUNDATION over a fluid);</li>
 * <li>the pond under the floor with a save and load mid-FOUNDATION;</li>
 * <li>the pond under the floor with {@code workplaceFoundationDepth} 1: one layer filled, the water below left.</li>
 * </ul>
 * A hut sunk below the water line (a lake, a pond in the footprint) is QaBuilderWater1114bBugTests.
 * The setup helpers (bench, chest and barrels with the hut's list plus 2 of each, free materials off) are the terrain
 * tests'.
 */
public class QaBuilderWater1114bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/** Fills x, z in [x0, x1] x [z0, z1] and y in [y0, y1] with {@code block}. */
	static void fill(GameTestHelper helper, int x0, int x1, int y0, int y1, int z0, int z1, Block block) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					helper.setBlock(new BlockPos(x, y, z), block);
				}
			}
		}
	}

	/** Every block under the hut's footprint (x 6-10, z 6-10) from y {@code from} to {@code to} that is open or fluid. */
	private static List<String> open(GameTestHelper helper, int from, int to) {
		List<String> out = new ArrayList<>();
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				for (int y = from; y <= to; y++) {
					BlockState s = helper.getBlockState(new BlockPos(x, y, z));
					if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) {
						out.add("(" + x + "," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
		}
		return out;
	}

	private static void fireOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
	}

	private static void assertNoFireNear(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		BoundingBox box = r.plan().bounds().inflatedBy(3);
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			helper.assertFalse(r.level().getBlockState(p).is(BlockTags.FIRE), "fire at " + helper.relativePos(p) + " by the finished hut");
		}
	}

	/**
	 * Lava fills the hut's whole floor layer (25 sources, y 2, free to flow out over the ground), fire spread on: the
	 * builder lives, the wooden hut ends as drawn with no lava or fire in or round it, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bLavaFloor"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bLavaFloor")
	public void lavaFillingTheWholeFloorLayerIsBuiltOverAndNothingBurns(GameTestHelper helper) {
		prepare(helper);
		fireOn(helper);
		fill(helper, 10, 14, 2, 2, 10, 14, Blocks.LAVA);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 2, new BlockPos(10, 2, 10));
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			assertNoFireNear(helper, r);
			assertNothingMadeOrLost(helper, r);
			LOGGER.info("qa 1114b lava floor: built over at tick {}", helper.getTick());
		});
	}

	/**
	 * A pond one deep in the ground right beside the hut (x 11-12, z 6-10, in the leveled ring east of the footprint, its
	 * water level with the lawn) on a stone plateau, with 16 dirt in the chest for the leveling: the pond is left as it
	 * is (leveling fills holes, never a player's pond), the hut ends dry and as drawn, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bPondBeside"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bPondBeside")
	public void aPondInTheLeveledRingIsLeftAndNeverFilledWithDirt(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 11, 12, 3, 3, 6, 10, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 4, new BlockPos(6, 4, 6));
		((net.minecraft.world.Container) helper.getBlockEntity(r.supplies().get(0))).setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 16));
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			List<String> gone = new ArrayList<>();
			for (int x = 11; x <= 12; x++) {
				for (int z = 6; z <= 10; z++) {
					BlockState s = helper.getBlockState(new BlockPos(x, 3, z));
					if (!s.is(Blocks.WATER) || !s.getFluidState().isSource()) {
						gone.add("(" + x + ",3," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
			helper.assertTrue(gone.isEmpty(), "the pond beside the hut was filled or drained: " + gone);
			assertNothingMadeOrLost(helper, r);
		});
	}

	// --------------------------------------------------------------------------------------------- under the floor

	/** A stone plateau four high (y 2-5) with a pocket of {@code fluid} two deep (y 4-5) under the whole footprint. */
	private static QaBuilderTerrain1102GameTests.Run plateauWithPocket(GameTestHelper helper, Block fluid) {
		prepare(helper);
		fill(helper, 0, 29, 2, 5, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 4, 5, 6, 10, fluid);
		return start(helper, 6, new BlockPos(6, 6, 6));
	}

	/**
	 * A pond two deep under the whole floor of a hut on a plateau: FOUNDATION fills under the floor down to the solid
	 * stone (the default depth is 12), so no water is left under the hut, the hut is as drawn and every material adds
	 * up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bPondUnder"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bPondUnder")
	public void aPondUnderTheFloorIsFilledDownToSolidGround(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = plateauWithPocket(helper, Blocks.WATER);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			List<String> open = open(helper, 4, 5);
			helper.assertTrue(open.isEmpty(), "the pond under the floor wasn't filled down to solid ground: " + open);
			assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * The same with lava, fire spread on: the pocket is filled, the builder lives, nothing burns, and every material
	 * adds up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bLavaUnder"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bLavaUnder")
	public void aLavaPoolUnderTheFloorIsFilledAndTheBuilderLives(GameTestHelper helper) {
		fireOn(helper);
		QaBuilderTerrain1102GameTests.Run r = plateauWithPocket(helper, Blocks.LAVA);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			List<String> open = open(helper, 4, 5);
			helper.assertTrue(open.isEmpty(), "the lava under the floor wasn't filled down to solid ground: " + open);
			assertNoFireNear(helper, r);
			assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * The pond under the floor, with the world saved and loaded in the middle of FOUNDATION (a few steps done): the
	 * loaded site makes its list again from the terrain, skips what is done, and still fills the whole pond; nothing is
	 * doubled or lost.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bPondReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bPondReload")
	public void aPondUnderTheFloorReloadedMidFoundationIsStillFilled(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = plateauWithPocket(helper, Blocks.WATER);
		watchDeath(helper, r);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (reloaded[0] || now == null || now.stage() != BuildPlan.Stage.FOUNDATION) {
				return;
			}
			int filled = 50 - open(helper, 4, 5).size();
			if (filled >= 3) {
				reloaded[0] = true;
				r.builder()[0] = reload(helper, r.level(), r.builder()[0]);
				helper.assertTrue(r.site() != null && r.site() != now, "the site didn't come back from its save");
				LOGGER.info("qa 1114b pond reload: reloaded mid-FOUNDATION with {} of 50 filled", filled);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded mid-FOUNDATION");
			assertFinished(helper, r);
			List<String> open = open(helper, 4, 5);
			helper.assertTrue(open.isEmpty(), "the pond under the floor wasn't filled after the reload: " + open);
			assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * The pond under the floor with {@code workplaceFoundationDepth} 1: FOUNDATION fills one layer under the floor (y 5)
	 * and leaves the water below it (y 4) as it was; the hut still ends as drawn and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1114bPondDepth1"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1114bPondDepth1")
	public void withFoundationDepthOneOnlyTheTopLayerOfThePondIsFilled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int depth = level.getGameRules().getInt(ModGameRules.FOUNDATION_DEPTH);
		level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(1, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(depth, level.getServer()));
		QaBuilderTerrain1102GameTests.Run r = plateauWithPocket(helper, Blocks.WATER);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			List<String> top = open(helper, 5, 5);
			helper.assertTrue(top.isEmpty(), "the layer under the floor wasn't filled at depth 1: " + top);
			int water = 0;
			for (int x = 6; x <= 10; x++) {
				for (int z = 6; z <= 10; z++) {
					if (helper.getBlockState(new BlockPos(x, 4, z)).getFluidState().is(FluidTags.WATER)) {
						water++;
					}
				}
			}
			helper.assertTrue(water == 25, "depth 1 filled deeper than one layer: " + water + " of 25 water blocks left at y 4");
			assertNothingMadeOrLost(helper, r);
		});
	}
}
