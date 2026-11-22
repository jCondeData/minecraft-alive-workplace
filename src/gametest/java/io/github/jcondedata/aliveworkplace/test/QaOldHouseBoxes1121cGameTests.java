package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.abs;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.kept;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.keptFree;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.neighbor;
import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.oldVillage;
import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.people.Homes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA lane C (2026-11-21, lane-c-1121-2153): the fixes of B1435 (ground protection and the Steward's plots), B1436 (homes)
 * and B1433 (an upgrade blueprint lines up) for a house standing as an OLDER drawing of its id, tested from builder.md
 * ("Its neighbors read it as it stands too ... each also keeps today's drawing's box, so they never read less than
 * before"; "An upgrade blueprint ... clicked anywhere on such a build ... lines up with it") at the boundaries the fixes'
 * own tests ({@code QaOldHouseNeighbors1121cGameTests}, {@code QaOldDrawings1121cGameTests}) don't cover: each box from
 * both sides (the old house's last row and corner inside, the row and column just outside it outside, the spot above its
 * top), the part of TODAY's box the old house doesn't fill (still read), the house's own placement not being "another
 * build" for itself, and a home still read after the standing-drawing cache has run out and after a second restart.
 * Built on {@link RebuildDrawings1117e}: old house 5x5 (local x 0-4, z 0-4, height 2), today's drawing 7x3 (x 0-6, z 0-2).
 */
public class QaOldHouseBoxes1121cGameTests implements FabricGameTest {

	private static final BlockState BED = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);

	/** A bed with its foot at {@code foot} (local to the house) and its head one block east. */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		put(helper, at(foot), BED.setValue(BedBlock.PART, BedPart.FOOT));
		put(helper, at(foot.east()), BED.setValue(BedBlock.PART, BedPart.HEAD));
		return foot.east();
	}

	private static boolean homeOf(GameTestHelper helper, Stage s, BlockPos local) {
		Optional<Homes.Home> home = Homes.at(s.level(), abs(helper, local));
		return home.isPresent() && home.get().structure().equals(s.id());
	}

	// ---- B1436: homes -------------------------------------------------------------------------------------------------

	/**
	 * Builder.md "never read less than before": a bed in the part of today's 7x3 box the old house doesn't fill (local x 5-6,
	 * row 1) is still in that home, beside the old house's own bed on its row 3.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBox1121cHomeUnion"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBox1121cHomeUnion")
	public void aBedInTodaysBoxBesideAnOldHouseIsStillInThatHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "homeunion");
			BlockPos head = bed(helper, new BlockPos(5, 1, 1));
			helper.assertTrue(homeOf(helper, s, head), "a bed at local " + head + " (inside today's 7x3 box) gives no home: " + Homes.at(s.level(), abs(helper, head)));
			helper.assertTrue(homeOf(helper, s, BED_HEAD), "the old house's own bed on its row 3 gives no home");
			helper.succeed();
		});
	}

	/**
	 * The other side: a bed just behind the old house's back wall (row 5) and one just right of today's box (x 7-8, row 1)
	 * are in no home of this house.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBox1121cHomeOutside"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBox1121cHomeOutside")
	public void aBedJustOutsideTheOldHouseIsNotInThatHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "homeout");
			BlockPos behind = bed(helper, new BlockPos(1, 1, 5));
			BlockPos beside = bed(helper, new BlockPos(7, 1, 1));
			helper.assertFalse(homeOf(helper, s, behind), "a bed behind the old house's back wall (local " + behind + ") reads as in that house");
			helper.assertFalse(homeOf(helper, s, beside), "a bed just right of today's box (local " + beside + ") reads as in that house");
			helper.assertTrue(homeOf(helper, s, BED_HEAD), "(control) the old house's own bed gives no home");
			helper.succeed();
		});
	}

	/**
	 * The bed's home holds after the standing-drawing lookup's cache has run out (Refreshes keeps it "a couple of seconds")
	 * and after a second restart: read from the house's blocks again, the house is still its old drawing.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaBox1121cHomeLater"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaBox1121cHomeLater")
	public void theOldHousesBedIsStillItsHomeAfterTheCacheAndASecondRestart(GameTestHelper helper) {
		Leftovers.clear(helper);
		Stage[] s = {null};
		helper.runAfterDelay(2, () -> {
			s[0] = oldVillage(helper, "homelater");
			helper.assertTrue(homeOf(helper, s[0], BED_HEAD), "(setup) the old house's bed gives no home");
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(homeOf(helper, s[0], BED_HEAD), "100+ ticks later the old house's bed on its row 3 gives no home");
			reload(helper, s[0].level(), null);
			helper.assertTrue(homeOf(helper, s[0], BED_HEAD), "after a second restart the old house's bed on its row 3 gives no home");
			helper.succeed();
		});
	}

	// ---- B1435: ground protection -------------------------------------------------------------------------------------

	/**
	 * B838 with B1435, both sides of the old house's box: a neighbor behind it may not dig the old back corner (4,0,4), the
	 * back wall's corner (0,1,4), the ground 3 below the back row, nor today's box's spot the old house doesn't fill
	 * (6,0,1); it may level just behind the back wall (row 5), just right of today's box (x 7) and above the house's top. The house's own placement is never "another build" on its own ground.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBox1121cGround"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBox1121cGround")
	public void theGroundRoundAnOldHouseIsKeptExactlyToItsBoxes(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "groundedge");
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			BlueprintData.Placement other = neighbor(helper, s);
			List<String> wrong = new ArrayList<>();
			for (BlockPos local : List.of(new BlockPos(4, 0, 4), new BlockPos(0, 1, 4), new BlockPos(2, -3, 4), new BlockPos(6, 0, 1))) {
				if (!manager.underAnotherBuild(s.level(), other, abs(helper, local))) {
					wrong.add(local + " open");
				}
			}
			for (BlockPos local : List.of(new BlockPos(2, 0, 5), new BlockPos(7, 0, 1), new BlockPos(2, 2, 4))) {
				if (manager.underAnotherBuild(s.level(), other, abs(helper, local))) {
					wrong.add(local + " kept");
				}
			}
			if (manager.underAnotherBuild(s.level(), s.placement(), abs(helper, new BlockPos(2, 0, 4)))) {
				wrong.add("the house's own back floor is 'another build' for itself");
			}
			helper.assertTrue(wrong.isEmpty(), "a neighbor's levelling round the old 5x5 house (today's drawing 7x3) reads wrong at " + wrong
				+ " (inside spots must be kept, outside ones open)");
			helper.succeed();
		});
	}

	// ---- B1435: the Steward's plots -----------------------------------------------------------------------------------

	/**
	 * Steward.md "exactly 2 free blocks beside it fit, 1 doesn't", both sides behind the old house's back wall (row 4): rows 5
	 * and 6 are kept free, row 7 isn't (by this house); to its left x -1 and -2 are kept, x -3 isn't; today's box's 2 blocks
	 * to the right (x 8, row 1) are still kept.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBox1121cPlots"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBox1121cPlots")
	public void theStewardKeepsExactlyTwoBlocksRoundAnOldHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "plotedge");
			List<BoundingBox> boxes = keptFree(helper, s.level());
			List<String> wrong = new ArrayList<>();
			for (BlockPos local : List.of(new BlockPos(2, 0, 5), new BlockPos(2, 0, 6), new BlockPos(-1, 0, 3), new BlockPos(-2, 0, 3), new BlockPos(8, 0, 1))) {
				if (!kept(boxes, abs(helper, local))) {
					wrong.add(local + " not kept");
				}
			}
			for (BlockPos local : List.of(new BlockPos(2, 0, 7), new BlockPos(-3, 0, 3))) {
				if (kept(boxes, abs(helper, local))) {
					wrong.add(local + " kept");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the Steward's free ground round the old 5x5 house reads wrong at " + wrong + " (kept: " + boxes + ")");
			helper.succeed();
		});
	}

	// ---- B1433: an upgrade lines up -----------------------------------------------------------------------------------

	/** The old house standing after the rebuild, the next level's drawing shipped, the save reloaded; that level's id. */
	private static ResourceLocation upgradeStage(GameTestHelper helper, Stage s) {
		rebuild(s.level(), s.id(), true);
		ResourceLocation next = BlueprintUpgrades.upgradeOf(s.id());
		install(s.level(), next, newBlueprint(next, true));
		reload(helper, s.level(), null);
		return next;
	}

	/**
	 * Inside, at the old house's far back corner (4,1,4, the last block of its box), and on a player's block standing in
	 * today's box where the old house isn't (5,1,1): the next level's blueprint lines up with the house both times.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBox1121cClickIn"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBox1121cClickIn")
	public void anUpgradeClickedOnTheOldHousesBackCornerOrTodaysBoxLinesUp(GameTestHelper helper) {
		ResourceLocation id = id(helper, "clkcorner");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		put(helper, at(new BlockPos(5, 1, 1)), Blocks.GLOWSTONE.defaultBlockState());
		helper.runAfterDelay(2, () -> {
			ResourceLocation next = upgradeStage(helper, s);
			List<String> wrong = new ArrayList<>();
			for (BlockPos clicked : List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 1))) {
				Optional<BlueprintData.Placement> placed = QaOldDrawings1121cGameTests.clickOnto(helper, s.owner(), BlueprintItem.create(next, null), clicked);
				if (placed.isEmpty() || !placed.get().equals(s.placement())) {
					wrong.add(clicked + " -> " + placed.map(p -> p.origin() + " " + p.rotation()).orElse("nothing"));
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the Level 2 blueprint clicked onto the old house didn't line up with it (at " + s.placement().origin()
				+ "): " + wrong);
			helper.succeed();
		});
	}

	/**
	 * The other side: clicked on the ground just behind the old house's back wall (local 2,-1,5, outside both boxes), the next
	 * level's blueprint does NOT snap onto the house (it is placed as a build of its own there, or not at all).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBox1121cClickOut"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBox1121cClickOut")
	public void anUpgradeClickedJustBehindTheOldHouseDoesNotSnapOntoIt(GameTestHelper helper) {
		ResourceLocation id = id(helper, "clkout");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		helper.runAfterDelay(2, () -> {
			ResourceLocation next = upgradeStage(helper, s);
			BlockPos clicked = new BlockPos(2, -1, 5);
			helper.assertFalse(helper.getLevel().getBlockState(helper.absolutePos(at(clicked))).isAir(), "(setup) nothing to click on at " + clicked);
			Optional<BlueprintData.Placement> placed = QaOldDrawings1121cGameTests.clickOnto(helper, s.owner(), BlueprintItem.create(next, null), clicked);
			helper.assertFalse(placed.isPresent() && placed.get().equals(s.placement()), "the Level 2 blueprint clicked on the ground behind the old house "
				+ "(local " + clicked + ") snapped onto the house at " + s.placement().origin());
			helper.succeed();
		});
	}
}
