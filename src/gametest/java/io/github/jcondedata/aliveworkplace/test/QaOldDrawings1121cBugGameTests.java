package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;
import static io.github.jcondedata.aliveworkplace.test.RebuildMoves1117e2.takeDown;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane C (2026-11-21, lane-c-1121-1255): cases of {@link QaOldDrawings1121cGameTests} that fail on main because of the
 * mod (a finished house's box or record still read from today's drawing: B1374's follow-up, and B1372's "a take-down
 * that removed nothing doesn't forget the record" when the old drawing isn't in the history). Not on main while they fail.
 */
public class QaOldDrawings1121cBugGameTests implements FabricGameTest {

	/**
	 * builder.md: an upgrade blueprint clicked onto the finished building it upgrades lines up with it. Clicked onto the
	 * old house's back wall (outside the rebuilt drawing's 7x3 box, inside the 5x5 house that stands), it should line up
	 * with the house as it does on the front wall (control in QaOldDrawings1121cGameTests).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOld1121cClickBack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOld1121cClickBack")
	public void anUpgradeBlueprintClickedOnTheOldHousesBackLinesUpWithIt(GameTestHelper helper) {
		QaOldDrawings1121cGameTests.upgradeClick(helper, "clkback", new BlockPos(1, 1, 4));
	}

	/**
	 * B1372 ("at the least a take-down that removed nothing doesn't forget the record"): the rebuilt drawing ships WITHOUT
	 * the old one in the history, so nothing can tell what the house was built from. The sneak take-down then removes
	 * nothing; the house must stay listed in the hall (it can still be moved, repaired or refreshed).
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaOld1121cTdNothing"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaOld1121cTdNothing")
	public void aTakeDownThatRemovesNothingKeepsTheHousesRecord(GameTestHelper helper) {
		ResourceLocation id = id(helper, "tdnone");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			install(s.level(), id, newBlueprint(id, false)); // no history: the old drawing is unknown
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			takeDown(helper, s, builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null && sites(s) == 0 && !ModAttachments.BUILDER_JOB.has(builder[0]), "still taking it down");
			int left = QaOldDrawings1121cGameTests.oldBlocks(helper).size() + where(helper, Blocks.RED_BED).size();
			helper.assertTrue(left == 0 || QaOldDrawings1121cGameTests.listed(s), "the take-down left " + left + " of the house's 42 blocks standing and the hall forgot the house");
		});
	}
}
