package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-14), B1230's fix, a bug it leaves: a move called off before anything went up at its new spot S still counts
 * as "being taken back down" there until the Mover's next swing, so a builder handed a hut on S is told "the Mover is
 * still taking it back down" though S is empty. Failed on main before B1264's fix; helpers in QaMoveSpotCancel1114gGameTests
 * and QaMoveSpotBuild1114bGameTests.
 */
public class QaMoveSpotCancel1114gBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * Called off while A is still coming down at its old spot, before anything went up at S: nothing of A is at S, so the
	 * player is never told the Mover is "still taking it back down" there, and once A stands again S is taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gEarly"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gEarly")
	public void calledOffBeforeAnythingWentUpNeverClaimsAPutBackAtS(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[0] < before[0] / 2);
			helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE && QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0,
				"setup: A half taken down, nothing at S: " + site.stage() + ", " + QaMoveSpotSaves1111eGameTests.count(helper)[1] + " at S");
			helper.assertTrue(MoveSites.get(s.v().level()).cancel(s.v().level(), s.v().a()), "setup: A's move couldn't be called off");
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(!h.said().contains(QaMoveSpotCancel1114gGameTests.backLine(QaMoveSpotBuild1114bGameTests.hut())),
				"called off before anything went up at S (S empty), yet the player is told the Mover is still taking it back down there: " + h.said());
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "A back whole, a hut on S");
			helper.succeed();
		});
	}

	/** A's move half taken down at its old spot, nothing at S yet, then called off (B1264's setup). */
	private static MoveSite calledOffEarly(GameTestHelper helper, QaMoveSpotBuild1114bGameTests.Setup s, int[] before) {
		MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
		QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[0] < before[0] / 2);
		helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE && QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0,
			"setup: A half taken down, nothing at S: " + site.stage() + ", " + QaMoveSpotSaves1111eGameTests.count(helper)[1] + " at S");
		helper.assertTrue(MoveSites.get(s.v().level()).cancel(s.v().level(), s.v().a()), "setup: A's move couldn't be called off");
		return site;
	}

	/**
	 * B1264's fix: called off early, then saved and loaded before the Mover's next swing: the loaded move is still being
	 * put back (canceled, not done), S is taken by a builder straight away, and the loaded move run to its end puts A back
	 * whole.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gEarlyReload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gEarlyReload")
	public void calledOffEarlySurvivesASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			calledOffEarly(helper, s, before);
			QaMoveSpotBuild1114bGameTests.reload(s.v().level());
			MoveSite loaded = MoveSites.get(s.v().level()).forBuilding(s.v().a())
				.orElseThrow(() -> new AssertionError("the called-off move didn't survive the reload"));
			helper.assertTrue(loaded.canceled() && loaded.stage() != MoveSite.Stage.DONE && !loaded.puttingBack(),
				"after the reload: canceled " + loaded.canceled() + " at " + loaded.stage() + ", putting back at S " + loaded.puttingBack());
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "reloaded, nothing at S, a hut on S");
			QaMoveSpotBuild1114bGameTests.stepUntil(s.v().level(), loaded, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			helper.succeed();
		});
	}

	/**
	 * A save from before B1264's fix: the early-called-off move was saved in RESTORE_DOWN with nothing at S. Loaded, S is
	 * still not kept (taken by a builder, never the put-back line), and the move still runs to its end with A back whole.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveCancel1114gOldSave"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveCancel1114gOldSave")
	public void anOldSaveInTakeDownWithNothingAtSDoesntKeepS(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			calledOffEarly(helper, s, before);
			ServerLevel level = s.v().level();
			CompoundTag sites = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			ListTag list = sites.getList("sites", net.minecraft.nbt.Tag.TAG_COMPOUND);
			helper.assertTrue(list.size() == 1, "setup: one site saved, got " + list.size());
			list.getCompound(0).putString("stage", MoveSite.Stage.RESTORE_DOWN.name()); // as cancel() saved it before the fix
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(sites, level.registryAccess()));
			MoveSite loaded = MoveSites.get(level).forBuilding(s.v().a()).orElseThrow(() -> new AssertionError("the old save didn't load"));
			helper.assertTrue(loaded.canceled() && loaded.stage() == MoveSite.Stage.RESTORE_DOWN && !loaded.puttingBack(),
				"the old save loaded as canceled " + loaded.canceled() + " at " + loaded.stage() + ", putting back at S " + loaded.puttingBack());
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(!h.said().contains(QaMoveSpotCancel1114gGameTests.backLine(QaMoveSpotBuild1114bGameTests.hut())),
				"an old save with nothing at S still told the Mover is taking it back down there: " + h.said());
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, h, "old save, nothing at S, a hut on S");
			QaMoveSpotBuild1114bGameTests.stepUntil(level, loaded, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			helper.succeed();
		});
	}
}
