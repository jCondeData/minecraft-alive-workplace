package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.OldHouses;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Bug tests for ROADMAP 27.20 (QA, lane-c-1026-0353): the spec says the fill goes over built blocks, "not terrain,
 * plants or natural trees", but a natural tree's logs that touch no leaves (the lower trunk) are taken into the house
 * (OldHouses.Measure.step: a log counts as a tree's only when it touches non-persistent leaves itself). Register next to
 * QaOldHousesGameTests together with the fix.
 */
public class QaOldHousesBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final int SMALL_HOUSE_BLOCKS = 153;

	/**
	 * A tall oak that grew against the plains house's wall (natural leaves at its top, poppies and grass round it): the
	 * house measures the same box and the same 153 blocks as alone.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseTreeBug"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseTreeBug")
	public void oldHouseTreeTrunkCountedAsHouse_tallOak(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		BoundingBox expected = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		// the westmost full block of the house's bottom layer, for the trunk to stand against
		BlockPos wall = null;
		for (int x = 0; x < 7 && wall == null; x++) {
			for (int z = 1; z < 6 && wall == null; z++) {
				BlockPos p = HOUSE.offset(x, 0, z);
				if (helper.getBlockState(p).isCollisionShapeFullBlock(v.level(), helper.absolutePos(p)) && helper.getBlockState(p.west()).isAir()
					&& helper.getBlockState(p.west().north()).isAir() && helper.getBlockState(p.west().south()).isAir()) {
					wall = p;
				}
			}
		}
		helper.assertTrue(wall != null,"setup: no wall block on the house's west side: " + wall);
		BlockPos trunk = wall.west();
		// the trunk stands in the open beside the wall: past the roof's eave, which hangs a block out over that side (a trunk
		// put through the eave would replace one of the house's own 153 blocks)
		for (int out = 0; out < 3 && !columnFree(helper, trunk, 9); out++) {
			trunk = trunk.west();
		}
		helper.assertTrue(columnFree(helper, trunk, 9), "setup: no free column for the trunk west of " + wall);
		for (int y = 0; y < 9; y++) {
			helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
		}
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
		BlockPos top = trunk.above(8);
		helper.setBlock(top.above(), leaves);
		helper.setBlock(top.west(), leaves);
		helper.setBlock(top.north(), leaves);
		helper.setBlock(top.south(), leaves);
		helper.setBlock(trunk.west(), Blocks.POPPY);
		helper.setBlock(trunk.offset(-1, 0, 1), Blocks.SHORT_GRASS);
		QaOldHousesGameTests.surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "expected the one house, found " + houses);
			OldHouses.House house = houses.get(0);
			helper.assertTrue(house.box().equals(expected), "with a tree against it the house measured " + house.box() + ", the template fills " + expected);
			helper.assertTrue(house.blocks() == SMALL_HOUSE_BLOCKS, "with a tree against it the house measured " + house.blocks() + " blocks, not " + SMALL_HOUSE_BLOCKS);
		});
	}

	private static boolean columnFree(GameTestHelper helper, BlockPos bottom, int height) {
		for (int y = 0; y < height; y++) {
			if (!helper.getBlockState(bottom.above(y)).isAir()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A vanilla oak (TreeFeatures.OAK, fixed seed) grown two blocks west of the house, as trees stand in villages: its
	 * leaves touch the house's own logs, which must still count as the house's (it measures as alone).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseVanillaOakBug"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseVanillaOakBug")
	public void oldHouseLogsTouchingATreesLeavesDropOut_vanillaOak(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		BoundingBox expected = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		ServerLevel level = helper.getLevel();
		var oak = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.OAK).value();
		// the nearest spot west of the house a vanilla oak grows at (it needs a free block round its trunk)
		BlockPos trunk = null;
		for (int dx = 2; dx <= 3 && trunk == null; dx++) {
			for (int z = 0; z < 7 && trunk == null; z++) {
				BlockPos at = HOUSE.offset(-dx, 0, z);
				if (oak.place(level, level.getChunkSource().getGenerator(), RandomSource.create(27_20L), helper.absolutePos(at))) {
					trunk = at;
				}
			}
		}
		helper.assertTrue(trunk != null, "setup: the oak didn't grow");
		int touching = 0;
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(HOUSE), helper.absolutePos(HOUSE.offset(6, 6, 6)))) {
			if (level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)) {
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
					BlockState n = level.getBlockState(p.relative(d));
					if (n.getBlock() instanceof LeavesBlock && !n.getValue(LeavesBlock.PERSISTENT)) {
						touching++;
						break;
					}
				}
			}
		}
		helper.assertTrue(touching > 0, "setup: the oak at " + trunk + " touches none of the house's logs");
		final int touched = touching;
		helper.assertTrue(helper.getBlockState(trunk).is(Blocks.OAK_LOG) && helper.getBlockState(trunk.above()).is(Blocks.OAK_LOG), "setup: no trunk at " + trunk);
		QaOldHousesGameTests.surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "expected the one house, found " + houses);
			OldHouses.House house = houses.get(0);
			helper.assertTrue(house.box().equals(expected) && house.blocks() == SMALL_HOUSE_BLOCKS, "with a vanilla oak against it the house measured "
				+ house.box() + " and " + house.blocks() + " blocks; the template fills " + expected + " with " + SMALL_HOUSE_BLOCKS + " (the oak's leaves touch " + touched + " of its logs)");
		});
	}
}
