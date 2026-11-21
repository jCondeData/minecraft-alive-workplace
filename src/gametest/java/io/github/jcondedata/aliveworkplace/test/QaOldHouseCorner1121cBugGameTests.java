package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.abs;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.neighbor;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.oldVillage;
import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.people.Homes;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA bug test (lane C, 2026-11-21, lane-c-1121-2153; found testing B1435/B1436's fix): Refreshes.standingBox reads a
 * finished house standing as an older drawing as the one box holding BOTH drawings' boxes. When the two differ in width and
 * depth (old 5x5, today's 7x3) that box also takes in a corner that belongs to neither (local x 5-6, z 3-4, outdoors beside
 * the old house's back rows). A bed a player puts there reads as in that house (Homes.at: the home and its tier mood,
 * B1436 expected "the home the bed is in, the house as it stands"), and a neighbor's levelling leaves that open ground
 * alone. Expected: neither drawing's box covers the corner, so it is no part of the house.
 */
public class QaOldHouseCorner1121cBugGameTests implements FabricGameTest {

	//$ gametest_ticks_batch AREA '200' '"qaCorner1121cHome"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCorner1121cHome")
	public void aBedOutsideBothDrawingsBesideAnOldHouseIsNotInThatHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "corner");
			BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
			put(helper, at(new BlockPos(5, 1, 3)), bed.setValue(BedBlock.PART, BedPart.FOOT));
			put(helper, at(new BlockPos(6, 1, 3)), bed.setValue(BedBlock.PART, BedPart.HEAD));
			Optional<Homes.Home> home = Homes.at(s.level(), abs(helper, new BlockPos(6, 1, 3)));
			helper.assertFalse(home.isPresent() && home.get().structure().equals(s.id()), "a bed outdoors at local 5-6,1,3 (outside the old 5x5 house "
				+ "and outside today's 7x3 box) reads as in that house: " + home);
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '200' '"qaCorner1121cGround"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCorner1121cGround")
	public void theGroundOutsideBothDrawingsBesideAnOldHouseIsOpenToANeighbor(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "cornerground");
			helper.assertFalse(BuildSiteManager.get(s.level()).underAnotherBuild(s.level(), neighbor(helper, s), abs(helper, new BlockPos(6, 0, 4))),
				"the open ground at local 6,0,4 (outside the old 5x5 house and today's 7x3 box) is kept from a neighbor's levelling as the house's");
			helper.succeed();
		});
	}
}
