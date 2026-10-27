package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.OldHouses;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1027-0938) on B517's fix, from its Expected: an old house (27.20) measures "the template's box and blocks
 * exactly, trees left out". QaOldHousesBugTests grows a plain oak and a tall trunk; trees grow other shapes in villages:
 * a fancy oak's sideways branches, a dark oak's 2x2 trunk and its side branches, an acacia's leaning trunk, a cherry's
 * forked one, a spruce's leaves all the way down, a birch. Each grows (vanilla's feature, a fixed seed) at the nearest
 * spot one to four blocks west of the plains house, and the house still measures exactly as alone.
 */
public class QaOldHouseTreesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final int SMALL_HOUSE_BLOCKS = 153;
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest_ticks_batch AREA '200' '"qaOldHouseFancyOak"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseFancyOak")
	public void aFancyOakBesideAnOldHouseIsLeftOut(GameTestHelper helper) {
		treeBeside(helper, TreeFeatures.FANCY_OAK, "fancy oak");
	}

	//$ gametest_ticks_batch AREA '200' '"qaOldHouseDarkOak"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseDarkOak")
	public void aDarkOakBesideAnOldHouseIsLeftOut(GameTestHelper helper) {
		treeBeside(helper, TreeFeatures.DARK_OAK, "dark oak");
	}

	//$ gametest_ticks_batch AREA '200' '"qaOldHouseAcacia"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseAcacia")
	public void anAcaciaBesideAnOldHouseIsLeftOut(GameTestHelper helper) {
		treeBeside(helper, TreeFeatures.ACACIA, "acacia");
	}

	//$ gametest_ticks_batch AREA '200' '"qaOldHouseCherry"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseCherry")
	public void aCherryBesideAnOldHouseIsLeftOut(GameTestHelper helper) {
		treeBeside(helper, TreeFeatures.CHERRY, "cherry");
	}

	//$ gametest_ticks_batch AREA '200' '"qaOldHouseSpruce"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseSpruce")
	public void aSpruceBesideAnOldHouseIsLeftOut(GameTestHelper helper) {
		treeBeside(helper, TreeFeatures.SPRUCE, "spruce");
	}

	//$ gametest_ticks_batch AREA '200' '"qaOldHouseBirch"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseBirch")
	public void aBirchBesideAnOldHouseIsLeftOut(GameTestHelper helper) {
		treeBeside(helper, TreeFeatures.BIRCH, "birch");
	}

	private static void treeBeside(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> feature, String name) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		BoundingBox expected = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		ServerLevel level = helper.getLevel();
		ConfiguredFeature<?, ?> tree = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(feature).value();
		BlockPos trunk = null;
		for (int dx = 1; dx <= 4 && trunk == null; dx++) {
			for (int z = 0; z < 7 && trunk == null; z++) {
				BlockPos at = HOUSE.offset(-dx, 0, z);
				if (tree.place(level, level.getChunkSource().getGenerator(), RandomSource.create(517L + dx * 7L + z), helper.absolutePos(at))) {
					trunk = at;
				}
			}
		}
		helper.assertTrue(trunk != null, "setup: the " + name + " didn't grow west of the house");
		// how near the tree comes: its logs and grown leaves next to a block of the house's box
		int near = 0;
		BlockPos lo = helper.absolutePos(HOUSE.offset(-6, -1, -3));
		BlockPos hi = helper.absolutePos(HOUSE.offset(0, 16, 10));
		for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
			BlockState s = level.getBlockState(p);
			boolean grown = s.is(BlockTags.LOGS) || s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT);
			if (!grown || expected.isInside(p)) {
				continue;
			}
			for (Direction d : Direction.values()) {
				if (expected.isInside(p.relative(d))) {
					near++;
					break;
				}
			}
		}
		helper.assertTrue(near > 0, "setup: the " + name + " grown at " + trunk + " doesn't reach the house: the test proves nothing");
		final BlockPos at = trunk;
		final int touching = near;
		QaOldHousesGameTests.surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "with a " + name + " beside it expected the one house, found " + houses);
			OldHouses.House house = houses.get(0);
			helper.assertTrue(house.box().equals(expected) && house.blocks() == SMALL_HOUSE_BLOCKS, "with a " + name + " grown at " + at + " beside it the house measured "
				+ house.box() + " and " + house.blocks() + " blocks; the template fills " + expected + " with " + SMALL_HOUSE_BLOCKS
				+ " (" + touching + " of the tree's blocks touch the house's box)");
			LOGGER.info("qa old house trees: a {} at {} ({} of its blocks against the house's box) left out, the house measured as alone", name, at, touching);
		});
	}

	/**
	 * A trunk against the house's west wall with grown leaves only on its top (a tree whose side leaves are gone): "a
	 * tree's when grown leaves sit on its top", so the house measures as alone.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseLeafTopTrunk"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseLeafTopTrunk")
	public void aTrunkWithLeavesOnlyOnItsTopIsLeftOut(GameTestHelper helper) {
		trunkAgainstTheWall(helper, 6, true, "a 6-log trunk with grown leaves only on its top");
	}

	static void trunkAgainstTheWall(GameTestHelper helper, int height, boolean leafOnTop, String what) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		BoundingBox expected = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		ServerLevel level = helper.getLevel();
		BlockPos wall = null;
		for (int x = 0; x < 7 && wall == null; x++) {
			for (int z = 1; z < 6 && wall == null; z++) {
				BlockPos p = HOUSE.offset(x, 0, z);
				if (helper.getBlockState(p).isCollisionShapeFullBlock(level, helper.absolutePos(p)) && helper.getBlockState(p.west()).isAir()
					&& helper.getBlockState(p.west().north()).isAir() && helper.getBlockState(p.west().south()).isAir()) {
					wall = p;
				}
			}
		}
		helper.assertTrue(wall != null, "setup: no wall block on the house's west side");
		// past the roof's eave (as QaOldHousesBugTests), still touching the house's box
		BlockPos trunk = wall.west();
		for (int out = 0; out < 3 && !columnFree(helper, trunk, height + 1); out++) {
			trunk = trunk.west();
		}
		helper.assertTrue(columnFree(helper, trunk, height + 1), "setup: no free column for the trunk west of " + wall);
		for (int y = 0; y < height; y++) {
			helper.setBlock(trunk.above(y), net.minecraft.world.level.block.Blocks.OAK_LOG);
		}
		if (leafOnTop) {
			helper.setBlock(trunk.above(height), net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
				.setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1));
		}
		boolean touches = false;
		for (int y = 0; y < height; y++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				touches |= expected.isInside(helper.absolutePos(trunk.above(y).relative(d)));
			}
		}
		helper.assertTrue(touches, "setup: the trunk at " + trunk + " doesn't touch the house's box");
		final BlockPos at = trunk;
		QaOldHousesGameTests.surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "with " + what + " beside it expected the one house, found " + houses);
			OldHouses.House house = houses.get(0);
			helper.assertTrue(house.box().equals(expected) && house.blocks() == SMALL_HOUSE_BLOCKS, "with " + what + " at " + at + " against it the house measured "
				+ house.box() + " and " + house.blocks() + " blocks; the template fills " + expected + " with " + SMALL_HOUSE_BLOCKS);
			LOGGER.info("qa old house trees: {} against the wall left out, the house measured as alone", what);
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
}
