package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaOldHouseNeighbors1121cGameTests.*;
import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.VillageMachines;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA lane C (2026-11-21, lane-c-1121-1255): the older-drawing cases of {@link QaOldHouseNeighbors1121cGameTests} that fail
 * on main (B1374's unchanged follow-ups): a house standing as an OLDER 5x5 drawing under an id whose today's drawing is
 * 7x3 is read at today's box by the homes, the village fire, the machines' stores, a neighbor's levelling and the
 * Steward's plot search. The controls (a house of today's drawing) pass in that class.
 */
public class QaOldHouseNeighbors1121cBugGameTests implements FabricGameTest {

	/**
	 * The old house's own bed (on its row 3, outside today's 7x3 box) gives whoever sleeps there that house as their home,
	 * as the People table and the home mood read it (grander-homes.md: "The home a bed gives them").
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cHomeOld"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cHomeOld")
	public void theBedOfAnOldDrawingHouseIsInThatHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "homeold");
			Optional<Homes.Home> home = Homes.at(s.level(), abs(helper, BED_HEAD));
			helper.assertTrue(home.isPresent() && home.get().structure().equals(s.id()),
				"the bed in the old house (5x5; bed on its row 3) gives no home: " + home + " (today's drawing is 7x3)");
			helper.succeed();
		});
	}

	/** A chest in the old house's back half stands inside a village building, so machine work uses it (create.md). */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cStoreOld"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cStoreOld")
	public void aChestInTheBackOfAnOldDrawingHouseIsTheVillagesStore(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "storeold");
			put(helper, at(OLD_BACK_CHEST), Blocks.CHEST.defaultBlockState());
			helper.assertTrue(VillageMachines.village(s.level(), abs(helper, OLD_BACK_CHEST)),
				"a chest inside the old house's row 3 doesn't count as inside a village building (today's 7x3 box ends at row 2)");
			helper.succeed();
		});
	}

	/**
	 * The old house's oak walls are the builders' work (village-fire.md: the fire burns the walls the builders placed, and
	 * the builders put back what burned): its back wall (outside today's box) and its front-left wall (inside today's box,
	 * where today's drawing has spruce) both count as builder-made, and a fire on the back wall burns this house.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cFireOld"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cFireOld")
	public void theWallsOfAnOldDrawingHouseAreTheBuildersForTheFire(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "fireold");
			ServerLevel level = s.level();
			List<String> wrong = new ArrayList<>();
			BlockPos front = new BlockPos(0, 1, 1);
			if (!VillageFire.builderMade(level, abs(helper, OLD_BACK_WALL))) {
				wrong.add("its back wall (oak, row 4) isn't the builders'");
			}
			if (!VillageFire.builderMade(level, abs(helper, front))) {
				wrong.add("its west wall (oak, row 1, where today's drawing has spruce) isn't the builders'");
			}
			Optional<BuildSiteManager.Finished> burning = VillageFire.buildBurning(level, abs(helper, OLD_BACK_WALL).above());
			if (burning.isEmpty() || !burning.get().placement().equals(s.placement())) {
				wrong.add("a fire on its back wall burns no building (" + burning + ")");
			}
			helper.assertTrue(wrong.isEmpty(), "the old house (oak, 5x5) for the village fire: " + String.join("; ", wrong));
			helper.succeed();
		});
	}

	/**
	 * The ground under the old house's back wall is that house's (builder.md: LANDSCAPE leaves the ground in and under a
	 * neighboring finished building alone), so a site behind it never digs or fills it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cGroundOld"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cGroundOld")
	public void theGroundUnderTheBackOfAnOldDrawingHouseIsKeptFromANeighbor(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "groundold");
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			List<String> open = new ArrayList<>();
			for (BlockPos local : List.of(OLD_BACK_FLOOR, OLD_BACK_WALL, BED_HEAD, OLD_BACK_FLOOR.below())) {
				if (!manager.underAnotherBuild(s.level(), neighbor(helper, s), abs(helper, local))) {
					open.add(local + " (" + s.level().getBlockState(abs(helper, local)).getBlock().getName().getString() + ")");
				}
			}
			helper.assertTrue(open.isEmpty(), "a neighbor's levelling may dig or fill the old house (5x5) at " + open + " (today's drawing ends at row 2)");
			helper.succeed();
		});
	}

	/**
	 * Steward.md: a plot keeps 2 free blocks beside every building ("exactly 2 free blocks beside it fit, 1 doesn't"). Behind
	 * the old house (back wall on row 4) rows 5 and 6 must stay free of his plots; today's 7x3 box plus 2 ends at row 4.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNb1121cPlotOld"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNb1121cPlotOld")
	public void theStewardsPlotsKeepTwoBlocksBehindAnOldDrawingHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			Stage s = oldVillage(helper, "plotold");
			List<BoundingBox> boxes = keptFree(helper, s.level());
			List<String> open = new ArrayList<>();
			for (int row = 3; row <= 6; row++) {
				BlockPos local = new BlockPos(2, 0, row);
				if (!kept(boxes, abs(helper, local))) {
					open.add("row " + row);
				}
			}
			helper.assertTrue(open.isEmpty(), "the Steward's plot search leaves the old house's ground free at " + open
				+ " (its back wall is row 4; kept: " + boxes + ")");
			helper.succeed();
		});
	}
}
