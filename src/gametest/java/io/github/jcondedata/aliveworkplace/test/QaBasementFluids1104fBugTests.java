package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertBuilt;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.cottageFour;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Extent;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Setup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

/**
 * Bugs found by QA (lane-b, 2026-11-04, round f) in the fix of B923/B924 ({@code BuilderWork.sealFluids}); setup in
 * {@link QaBasementGround1104GameTests}, passing edges in {@link QaBasementFluids1104fGameTests}. Fail today.
 * <p>
 * Waterlogged blocks in the earth beside a basement (what an aquifer cave holds: glow lichen, pointed dripstone,
 * seagrass; or a buried ruin's waterlogged stair) are not plugged, because the fix only plugs a {@code LiquidBlock}.
 * Each spreads water into the dug pit like a source. The water that has run into the room is then itself plugged with
 * dirt when the dig next to it goes on: the fix means to leave a spot of the room alone ("a spot of the room the dig
 * will reach itself") but checks for it in {@code BuildPlan.targets()}, which holds only the blocks a build places,
 * never the air of a room. So the build "finishes" with dirt standing in the basement's room where the drawing has air.
 * Expected (docs/wiki/basements.md): the basement is dug out and finished as drawn, dry, with the builder alive.
 */
public class QaBasementFluids1104fBugTests implements FabricGameTest {
	/**
	 * Waterlogged glow lichen (west of the basement wall, and under its floor), a waterlogged pointed dripstone (east)
	 * and seagrass (south), at the B923 spots: when done, the whole build is as drawn, no water is in the basement and
	 * the builder is alive.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaFluidsCaveLogged"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaFluidsCaveLogged")
	public void waterloggedCaveBlocksBesideTheBasementLeaveItDryAndWhole(GameTestHelper helper) {
		BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true).setValue(BlockStateProperties.WATERLOGGED, true);
		BlockState dripstone = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
			.setValue(BlockStateProperties.DRIPSTONE_THICKNESS, DripstoneThickness.TIP).setValue(BlockStateProperties.WATERLOGGED, true);
		waterlogged(helper, "cave", List.of(lichen, dripstone, Blocks.SEAGRASS.defaultBlockState(), lichen));
	}

	/** The same with a waterlogged stone-brick stair at each of the four spots. */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaFluidsStairLogged"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaFluidsStairLogged")
	public void waterloggedStairsBesideTheBasementLeaveItDryAndWhole(GameTestHelper helper) {
		BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.WATERLOGGED, true);
		waterlogged(helper, "stairs", List.of(stair, stair, stair, stair));
	}

	/** {@code states} at the four {@link Extent#wetSpots} (west, east, south, under the floor), each holding water. */
	static void waterlogged(GameTestHelper helper, String what, List<BlockState> states) {
		List<BlockPos> spots = new ArrayList<>();
		Setup s = cottageFour(helper, b -> {
			List<BlockPos> rels = Extent.of(b).wetSpots();
			for (int i = 0; i < rels.size(); i++) {
				helper.setBlock(ORIGIN.offset(rels.get(i)), states.get(i));
				spots.add(rels.get(i));
			}
		});
		for (BlockPos rel : spots) {
			BlockState there = helper.getBlockState(ORIGIN.offset(rel));
			helper.assertTrue(there.getFluidState().is(FluidTags.WATER) && there.getFluidState().isSource(),
				"setup: " + rel.toShortString() + " holds no water: " + there);
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage() + ", status "
				+ s.site().status() + "; " + basement(s));
			String state = basement(s);
			helper.assertTrue(state.startsWith("0 wrong, 0 wet"), "finished, but: " + state + "; builder alive " + s.villager().isAlive());
			assertBuilt(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa fluids waterlogged {}: built at tick {}, beside the basement now {}", what, helper.getTick(),
				spots.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).toString()).toList());
		});
	}

	/** "N wrong, M wet" in the basement's box: blocks not as drawn (air drawn counts), and spots holding water or lava. */
	static String basement(Setup s) {
		BlockPos origin = s.site().placement().origin();
		Extent ex = Extent.of(s.blueprint());
		Map<BlockPos, BlockState> drawn = new HashMap<>();
		s.blueprint().blocks().forEach(e -> drawn.put(e.pos(), e.state()));
		List<String> wrong = new ArrayList<>();
		List<String> wet = new ArrayList<>();
		Map<String, Integer> wrongKinds = new HashMap<>();
		for (int x = ex.minX(); x <= ex.maxX(); x++) {
			for (int y = ex.minY(); y < 0; y++) {
				for (int z = ex.minZ(); z <= ex.maxZ(); z++) {
					BlockPos rel = new BlockPos(x, y, z);
					BlockState world = s.level().getBlockState(origin.offset(rel));
					BlockState want = drawn.get(rel);
					if (want == null) {
						continue;
					}
					if (!world.getFluidState().isEmpty() && !(want.hasProperty(BlockStateProperties.WATERLOGGED) && want.getValue(BlockStateProperties.WATERLOGGED))) {
						wet.add(rel.toShortString() + "=" + world.getBlock().getName().getString());
					}
					if (want.isAir() ? !world.isAir() && world.getFluidState().isEmpty() : !world.is(want.getBlock())) {
						wrong.add(rel.toShortString() + "=" + world.getBlock().getName().getString() + " (drawn " + want.getBlock().getName().getString() + ")");
						wrongKinds.merge(world.getBlock().getName().getString() + " for " + want.getBlock().getName().getString(), 1, Integer::sum);
					}
				}
			}
		}
		return wrong.size() + " wrong, " + wet.size() + " wet in the basement; wrong by kind " + wrongKinds + ", e.g. " + wrong.stream().limit(4).toList()
			+ ", wet e.g. " + wet.stream().limit(4).toList();
	}
}
