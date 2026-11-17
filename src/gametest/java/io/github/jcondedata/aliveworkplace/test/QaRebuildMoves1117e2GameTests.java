package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;
import static io.github.jcondedata.aliveworkplace.test.RebuildMoves1117e2.*;

import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.move.MoveFit;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA lane E, second pass (2026-11-17, lane-e-1117-1013): the features that read a finished building's drawing by its id
 * (the Mover through the Hall's Move page, 45.x; a sneak take-down; the Move page's footprint and fit check), each run
 * against a house of {@link RebuildDrawings1117e}'s OLD drawing. These are the controls, with the drawing NOT rebuilt,
 * which prove the harness: each passes today. QaRebuildMoves1117e2BugTests (not registered) runs the same bodies after
 * the drawing under the house's id was rebuilt from scratch (tools/blueprints/REBUILD.md: "standing buildings ... keep
 * their old drawing until a refresh"), and fails.
 */
public class QaRebuildMoves1117e2GameTests implements FabricGameTest {

	/**
	 * Wiki (moving-buildings.md, 45.3-45.5): the Mover "takes it down ... and puts it up again at the new spot with nothing
	 * lost ... the old spot is left cleared"; containers arrive "with exactly the items it held"; a resident's bed is
	 * "theirs again" at the new spot. A finished house of the old drawing (chest with 7 emeralds, a lectern, a bed someone
	 * sleeps in) is moved through the Move page (pick, map click, Confirm) by a hired Mover, the moves saved and loaded back
	 * every 7 steps.
	 */
	static void moveBody(GameTestHelper helper, boolean rebuilt) {
		ResourceLocation id = id(helper, rebuilt ? "mvrb" : "mv");
		Stage s = stage(helper, id, true);
		cleanUpMoves(helper, s);
		standOld(helper, s, true, false);
		Villager mover = mover(helper);
		Villager[] sleeper = {null};
		helper.runAfterDelay(2, () -> {
			sleeper[0] = sleeper(helper);
			if (rebuilt) {
				rebuild(s.level(), id, true);
				reload(helper, s.level(), null);
			}
			BlockPos to = confirmMove(helper, s);
			moveWithReloads(helper, s, mover);
			assertMovedWhole(helper, s, to, sleeper[0]);
			helper.succeed();
		});
	}

	/**
	 * Wiki (moving-buildings.md, 45.1/45.2): the picker shows "where the building stands now" and plans the building's own
	 * footprint. Opened on the old house, the picker's building box is the house as it stands: x 0-4, z 0-4 from its corner
	 * (5 x 5), 2 tall.
	 */
	static void footprintBody(GameTestHelper helper, boolean rebuilt) {
		ResourceLocation id = id(helper, rebuilt ? "fprb" : "fp");
		Stage s = stage(helper, id, true);
		cleanUpMoves(helper, s);
		standOld(helper, s, true, false);
		helper.runAfterDelay(2, () -> {
			if (rebuilt) {
				rebuild(s.level(), id, true);
				reload(helper, s.level(), null);
			}
			pick(helper, s, s.placement().origin());
			MovePage.Picking p = MovePage.picking(s.owner()).orElseThrow();
			BoundingBox want = BoundingBox.fromCorners(helper.absolutePos(HOUSE), helper.absolutePos(at(new BlockPos(4, 1, 4))));
			helper.assertTrue(p.old().equals(want), "the Move page's 'now' box is " + p.old() + ", not the house's " + want);
			helper.assertTrue(p.plan().size().equals(new Vec3i(5, 2, 5)), "the picker plans a " + p.plan().size() + " building, not the 5x2x5 house");
			helper.assertTrue(p.plan().beds().size() == 1, "the picker's plan has " + p.plan().beds().size() + " beds, not the house's 1");
			helper.succeed();
		});
	}

	/**
	 * Wiki (moving-buildings.md, 45.2): a spot is refused when "it would overlap another building (named)". The village's
	 * Test Hut is moved onto the old house's back row (where its bed and back wall stand): refused as a building in the way.
	 */
	static void fitBody(GameTestHelper helper, boolean rebuilt) {
		ResourceLocation id = id(helper, rebuilt ? "ftrb" : "ft");
		Stage s = stage(helper, id, true);
		cleanUpMoves(helper, s);
		standOld(helper, s, true, false);
		helper.runAfterDelay(2, () -> {
			standHut(helper);
			if (rebuilt) {
				rebuild(s.level(), id, true);
				reload(helper, s.level(), null);
			}
			ChoiceMenu menu = pick(helper, s, helper.absolutePos(HUT_ORIGIN));
			// the hut's 5x5 spot x 4-8, z 10-14: over the house's back row (z 10) only
			MoveFit.Fit fit = clickMap(helper, s, menu, 6, 12);
			helper.assertTrue(fit.min().getZ() == helper.absolutePos(at(new BlockPos(0, 0, 4))).getZ(), "(setup) the hut's spot starts at z " + fit.min().getZ());
			helper.assertTrue(fit.reason() == MoveFit.Reason.BUILDING, "a spot over the old house's back row (its bed) is " + fit.reason()
				+ (fit.what() == null ? "" : " (" + fit.what().getString() + ")") + ", not refused as a building in the way");
			helper.succeed();
		});
	}

	/**
	 * Guide/wiki (builder.md): sneak-hand the placed blueprint over a finished building: the builder takes it down; blocks
	 * that hold things (chest, lectern) stay standing with their items. A finished house of the old drawing is taken down.
	 */
	static void takeDownBody(GameTestHelper helper, boolean rebuilt) {
		ResourceLocation id = id(helper, rebuilt ? "tdrb" : "td");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			if (rebuilt) {
				rebuild(s.level(), id, true);
				reload(helper, s.level(), null);
			}
			builder[0] = builder(helper, s);
			takeDown(helper, s, builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null, "not handed over yet");
			assertTakenDown(helper, s, builder[0]);
		});
	}

	//$ gametest_ticks_batch AREA '400' '"qaRebuildMv1117e2Ctl"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRebuildMv1117e2Ctl")
	public void aHouseOfItsOwnDrawingIsMovedWholeThroughTheMovePage(GameTestHelper helper) {
		moveBody(helper, false);
	}

	//$ gametest_ticks_batch AREA '200' '"qaRebuildFp1117e2Ctl"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRebuildFp1117e2Ctl")
	public void theMovePagePlansAHouseOfItsOwnDrawingAsItStands(GameTestHelper helper) {
		footprintBody(helper, false);
	}

	//$ gametest_ticks_batch AREA '200' '"qaRebuildFit1117e2Ctl"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRebuildFit1117e2Ctl")
	public void aSpotOverTheBackOfAHouseOfItsOwnDrawingIsRefused(GameTestHelper helper) {
		fitBody(helper, false);
	}

	//$ gametest_ticks_batch AREA '3000' '"qaRebuildTd1117e2Ctl"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuildTd1117e2Ctl")
	public void aHouseOfItsOwnDrawingIsTakenDownKeepingItsChest(GameTestHelper helper) {
		takeDownBody(helper, false);
	}
}
