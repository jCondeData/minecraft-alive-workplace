package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveRules;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.test.MoverGameTests.Setup;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane E, 2026-11-10, third run): the Mover's two switches (45.3 {@code movers}, 45.6 {@code moveBuildings})
 * thrown while a move is under way, and a save and reload between. From the wiki's moving-buildings page: "With
 * {@code moveBuildings} off a move already confirmed still goes ahead (stopping one half way would leave a building in
 * pieces); {@code movers} off is the switch that stops the Movers", and Switches: "{@code movers} off ... Movers already
 * hired do nothing, and confirmed moves wait". 45.3: a save and reload in each stage loses nothing. MoverGameTests covers
 * {@code movers} off before a move starts; these throw the switches half way.
 */
public class QaMoverSwitches1110eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** {@code moveBuildings} switched off half way through taking the house down: the confirmed move still ends whole. */
	//$ gametest_batch AREA '"qaE1110MoveOffHalfWay"'
	@GameTest(template = AREA, batch = "qaE1110MoveOffHalfWay")
	public void moveBuildingsOffHalfWayStillFinishesTheConfirmedMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = MoverGameTests.setup(helper, "qa_e1110_off_half");
		helper.runAfterDelay(2, () -> {
			int all = s.before().blocks().size();
			MoveSite site = MoverGameTests.work(level, s, x -> x.packed() >= all / 2);
			int packedThen = site.packed();
			boolean was = MoveRules.ENABLED;
			MoveRules.ENABLED = false;
			MoveSite.Stage end;
			int skipped;
			try {
				site = MoverGameTests.work(level, s, x -> false);
				end = site.stage();
				skipped = site.skipped();
			} finally {
				MoveRules.ENABLED = was;
			}
			helper.assertTrue(packedThen > 0 && packedThen < all, "the switch wasn't thrown half way down: " + packedThen + " of " + all + " packed");
			helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "with moveBuildings off the confirmed move ended " + end + ", skipped " + skipped
				+ " (the wiki: a move already confirmed still goes ahead)");
			MoverGameTests.assertMoved(helper, level, s, List.of());
			helper.assertTrue(MoverGameTests.loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/**
	 * {@code movers} switched off with the house half put up at its new spot: the Mover does nothing, through a save and
	 * reload too; switched back on, he finishes it whole, nothing lost or doubled.
	 */
	//$ gametest_batch AREA '"qaE1110MoversOffHalfUp"'
	@GameTest(template = AREA, batch = "qaE1110MoversOffHalfUp")
	public void moversOffWithTheHouseHalfUpWaitsThroughAReloadAndThenFinishes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = MoverGameTests.setup(helper, "qa_e1110_movers_off");
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
			for (int i = 0; i < 6 && site.stage() == MoveSite.Stage.STRUCTURE; i++) {
				MoverWork.work(level, s.mover(), site); // a few blocks up at the new spot
			}
			MoveSite.Stage halfUp = site.stage();
			int packedThen = site.packed();
			boolean was = MoverWork.ENABLED;
			MoverWork.ENABLED = false;
			boolean worked = false;
			MoveSite.Stage offStage;
			int offPacked;
			boolean waits;
			try {
				for (int i = 0; i < 40; i++) {
					worked |= MoverWork.work(level, s.mover(), site);
				}
				site = MoverGameTests.reload(level, s);
				for (int i = 0; i < 40; i++) {
					worked |= MoverWork.work(level, s.mover(), site);
				}
				offStage = site.stage();
				offPacked = site.packed();
				waits = PendingMoves.get(level).forBuilding(s.from()).isPresent();
			} finally {
				MoverWork.ENABLED = was;
			}
			helper.assertTrue(halfUp == MoveSite.Stage.STRUCTURE, "the switch wasn't thrown with the house half up: " + halfUp);
			helper.assertFalse(worked, "movers off: the Mover still worked");
			helper.assertTrue(offStage == halfUp && offPacked == packedThen, "movers off (and reloaded): the move went from " + halfUp + " with "
				+ packedThen + " packed to " + offStage + " with " + offPacked);
			helper.assertTrue(waits, "movers off: the confirmed move no longer waits");
			site = MoverGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 0, "back on, the move ended " + site.stage() + ", skipped "
				+ site.skipped());
			MoverGameTests.assertMoved(helper, level, s, List.of());
			helper.assertTrue(MoverGameTests.loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}
}
