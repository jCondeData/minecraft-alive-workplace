package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-14) of B1230's fix: while a called-off move is still being taken back down off its new spot S (RESTORE_DOWN),
 * a builder handed a blueprint whose footprint reaches S is refused with "That spot isn't free yet: the move of <building>
 * there was called off, and the Mover is still taking it back down." (moving-buildings.md); once the building stands
 * where it was, S is taken. Written from ROADMAP B1230 and the wiki. The village, builder and hand-over from
 * QaMoveSpotBuild1114bGameTests: A (Test Hut, x 3-7, z 3-7) moving to S (x 11-15, z 3-7); every hand-over is the real
 * click on the builder.
 */
public class QaMoveSpotCancel1114gGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	static String backLine(String building) {
		return "That spot isn't free yet: the move of " + building + " there was called off, and the Mover is still taking it back down.";
	}

	static void assertRefusedBack(GameTestHelper helper, QaMoveSpotBuild1114bGameTests.Hand h, String what) {
		String hut = QaMoveSpotBuild1114bGameTests.hut();
		helper.assertTrue(!h.taken() && h.said().contains(backLine(hut)), what + ": expected refused with \"" + backLine(hut) + "\"; taken "
			+ h.taken() + ", he said " + h.said());
	}

	/** A's move begun and run until some of A stands at S, then called off and one step taken: RESTORE_DOWN, blocks at S. */
	static MoveSite calledOffWithBlocksAtS(GameTestHelper helper, QaMoveSpotBuild1114bGameTests.Setup s) {
		MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
		QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site,
			m -> m.stage() == MoveSite.Stage.STRUCTURE && QaMoveSpotSaves1111eGameTests.count(helper)[1] > 5);
		helper.assertTrue(MoveSites.get(s.v().level()).cancel(s.v().level(), s.v().a()), "setup: A's move couldn't be called off");
		MoveSites.get(s.v().level()).step(s.v().level(), site);
		int atS = QaMoveSpotSaves1111eGameTests.count(helper)[1];
		helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.RESTORE_DOWN && atS > 0,
			"setup: called off with A's blocks at S: canceled " + site.canceled() + ", " + site.stage() + ", " + atS + " blocks at S");
		return site;
	}

	/**
	 * S's edges from both sides while it's put back: one shared corner block (north-east, south-west, north-west) and one
	 * shared column on the east are refused with the put-back line; a block clear east, south, and diagonally off the
	 * north-east corner is taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gEdges"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gEdges")
	public void theRestoringSpotsEdgesFromBothSides(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = calledOffWithBlocksAtS(helper, s);
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 15, -1), "north-east corner, one block (15, 3) shared");
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 7, 7), "south-west corner, one block (11, 7) shared");
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 7, -1), "north-west corner, one block (11, 3) shared");
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 15, 3), "east, sharing S's column x 15");
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 16, 3), "a block clear east of S (x 16-20)");
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 10, 8), "a block clear south of S (z 8-12)");
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 16, -2), "diagonally off the north-east corner");
			helper.assertTrue(site.stage() == MoveSite.Stage.RESTORE_DOWN, "the hand-overs changed the put-back: " + site.stage());
			helper.succeed();
		});
	}

	/**
	 * Saved and loaded half way through taking A back down off S: the loaded move still keeps S (refused with the put-back
	 * line); the loaded move run to its end puts A back whole, and then S is taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gReload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gReload")
	public void theRestoringSpotSurvivesASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = calledOffWithBlocksAtS(helper, s);
			QaMoveSpotBuild1114bGameTests.reload(s.v().level());
			MoveSite loaded = MoveSites.get(s.v().level()).forBuilding(s.v().a()).orElseThrow(() -> new AssertionError("the called-off move didn't survive the reload"));
			helper.assertTrue(loaded != site && loaded.canceled() && loaded.stage() == MoveSite.Stage.RESTORE_DOWN,
				"after the reload the move is canceled " + loaded.canceled() + " at " + loaded.stage());
			assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "after a reload mid put-back, a hut on S");
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), loaded, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "the reloaded put-back over, a hut on S");
			helper.succeed();
		});
	}

	/**
	 * A Mover dies while A is being taken back down off S, and nobody works the move for a while: A's blocks still stand
	 * at S, so S is still refused (also a sneak take-down there); the builder must not clear them himself.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gMoverGone"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gMoverGone")
	public void aStalledPutBackStillKeepsTheSpot(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = calledOffWithBlocksAtS(helper, s);
			Villager mover = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 15));
			mover.setNoAi(true);
			mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER).setLevel(2));
			helper.assertTrue(mover.getVillagerData().getProfession() == ModVillagers.MOVER, "setup: the villager isn't a Mover");
			mover.kill();
			helper.runAfterDelay(40, () -> {
				int atS = QaMoveSpotSaves1111eGameTests.count(helper)[1];
				helper.assertTrue(MoveSites.get(s.v().level()).forBuilding(s.v().a()).isPresent() && atS > 0,
					"setup: the stalled put-back is gone or S is empty (" + atS + " blocks)");
				assertRefusedBack(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 13, 5), "Mover dead, " + atS + " of A's blocks at S, a hut over S");
				QaMoveSpotBuild1114bGameTests.Hand down = QaMoveSpotBuild1114bGameTests.hand(helper, s, new BlockPos(11, 2, 3), Rotation.NONE, Mirror.NONE, true);
				assertRefusedBack(helper, down, "Mover dead, a sneak take-down on S");
				helper.assertTrue(QaMoveSpotSaves1111eGameTests.count(helper)[1] == atS, "A's blocks at S changed with nobody working the move");
				helper.assertTrue(site.stage() == MoveSite.Stage.RESTORE_DOWN, "the stalled move moved on: " + site.stage());
				helper.succeed();
			});
		});
	}

	/** The refusal as a player reads it: one line, the exact words of move_spot_back with the building's name, never "will build". */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gLine"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gLine")
	public void theRefusalReadsExactly(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			calledOffWithBlocksAtS(helper, s);
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(h.said().equals(java.util.List.of(
				"That spot isn't free yet: the move of Test Hut there was called off, and the Mover is still taking it back down.")),
				"the refusal read " + h.said());
			helper.assertTrue(!h.taken(), "refused, yet a site was made");
			helper.succeed();
		});
	}
}
