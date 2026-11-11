package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-11, verifying B1181 and B1192 across saves). moving-buildings.md: "Called off on the Move page, even
 * half way, what went up at the new spot comes down again and everything goes back up where it was: the building's row
 * opens on the planned spot at every stage of the move, with Call off, even once nothing is left at the old spot. Once the
 * Mover has started, Confirm is gray and a new spot is refused ("The Mover is already moving it: call the move off first
 * to pick another spot"); call it off, then pick again." These walk that through a server restart at each stage of the
 * move (the site read back from its save), and the far side of "call it off, then pick again": once the call-off is over,
 * a new spot is taken and the Mover carries it out. Hut A (x 3-7, z 3-7) moves to S (x 11-15, z 3-7); the other spot T is
 * x 11-15, z 15-19. Helpers from QaHallMove1111c2GameTests (lane C) and QaMoveSpotSaves1111eGameTests.
 */
public class QaMoveUnderWay1111eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** S's middle: x 11-15, z 3-7. */
	private static final BlockPos S = new BlockPos(13, 2, 5);
	/** T's middle: x 11-15, z 15-19. */
	private static final BlockPos T = new BlockPos(13, 2, 17);
	private static final String UNDER_WAY = "The Mover is already moving it: call the move off first to pick another spot";

	/** Saves the moves' records and loads them back, as a server restart does; the loaded ones replace the live ones. */
	private static void reload(ServerLevel level) {
		CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
		CompoundTag sites = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(sites, level.registryAccess()));
	}

	/** Runs {@code body} later, turning the shared helpers' plain AssertionErrors into a failed test instead of a crashed server. */
	private static void later(GameTestHelper helper, Runnable body) {
		helper.runAfterDelay(2, () -> {
			try {
				body.run();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	private static void run(ServerLevel level, MoveSite site, Predicate<MoveSite> until) {
		for (int i = 0; i < 100_000 && !until.test(site) && site.stage() != MoveSite.Stage.DONE; i++) {
			MoveSites.get(level).step(level, site);
		}
	}

	private static MoveSite readBack(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, MoveSite.Stage at) {
		reload(v.level());
		MoveSite again = MoveSites.get(v.level()).forBuilding(v.a()).orElseThrow(() -> new AssertionError("A's move at " + at + " didn't survive the reload"));
		helper.assertTrue(again.stage() == at, "A's move read back at " + again.stage() + ", saved at " + at);
		return again;
	}

	/**
	 * A's move run to {@code at}, the world saved and read back: A's row opens on S with Call off, Confirm on T is gray and
	 * refused word for word, the saved move still says S; then Call off brings every block back to A's old spot.
	 */
	private static void callOffAfterAReload(GameTestHelper helper, MoveSite.Stage at) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		later(helper, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			PendingMoves.Move move = QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			run(v.level(), site, s -> s.stage() == at);
			helper.assertTrue(site.stage() == at, "setup: A's move never reached " + at + " (ended " + site.stage() + ")");
			MoveSite again = readBack(helper, v, at);

			ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
			MovePage.Picking p = MovePage.picking(v.player()).orElseThrow();
			BlockPos s = helper.absolutePos(S);
			helper.assertTrue(p.x() == s.getX() && p.z() == s.getZ(), "after a reload at " + at + " A's picker opened on " + p.x() + ", " + p.z()
				+ ", not on its planned spot " + s.getX() + ", " + s.getZ());
			helper.assertTrue(menu.icon(MovePage.CALL_OFF).is(Items.COBWEB), "after a reload at " + at + " A's picker has no Call off: " + menu.icon(MovePage.CALL_OFF));
			QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE)
				&& QaHallMove1111c2GameTests.lore(menu.icon(MovePage.CONFIRM)).contains(UNDER_WAY),
				"after a reload at " + at + " Confirm on T: " + menu.icon(MovePage.CONFIRM) + " " + QaHallMove1111c2GameTests.lore(menu.icon(MovePage.CONFIRM)));
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(QaHallMove1111c2GameTests.actionBar(v).equals(UNDER_WAY), "Confirm on T mid-move said \"" + QaHallMove1111c2GameTests.actionBar(v) + "\"");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).map(PendingMoves.Move::to).orElse(null) instanceof BlockPos to
				&& to.equals(again.newMin()), "after a refused Confirm the saved move " + PendingMoves.get(v.level()).forBuilding(v.a()) + " and the Mover's " + again.newMin() + " parted");
			menu.removed(v.player());
			MovePage.stop(v.player());

			menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
			menu.press(MovePage.CALL_OFF, v.player());
			menu.removed(v.player());
			MovePage.stop(v.player());
			run(v.level(), again, x -> false);
			helper.assertTrue(again.canceled() && again.stage() == MoveSite.Stage.DONE && again.skipped() == 0,
				"A's move called off at " + at + " after a reload ended " + again.stage() + ", canceled " + again.canceled() + ", skipped " + again.skipped());
			int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(now[0] == before[0] && now[1] == 0, "called off at " + at + " after a reload: A's old spot has " + now[0] + " blocks (of "
				+ before[0] + ") and S still " + now[1]);
			helper.assertTrue(MoveSites.get(v.level()).forBuilding(v.a()).isEmpty() && PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(),
				"the called-off move is still on the books");
			helper.succeed();
		});
	}

	/** Called off after a restart while the Mover is still taking A down. */
	//$ gametest_ticks_batch AREA '200' '"qaMoveUnderWay1111eDown"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveUnderWay1111eDown")
	public void calledOffAfterAReloadWhileTakingDown(GameTestHelper helper) {
		callOffAfterAReload(helper, MoveSite.Stage.DISMANTLE);
	}

	/** Called off after a restart once A is all down and its floor is going in at S (nothing left at the old spot). */
	//$ gametest_ticks_batch AREA '200' '"qaMoveUnderWay1111eFoundation"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveUnderWay1111eFoundation")
	public void calledOffAfterAReloadAtTheFoundation(GameTestHelper helper) {
		callOffAfterAReload(helper, MoveSite.Stage.FOUNDATION);
	}

	/** Called off after a restart with A's walls going up at S. */
	//$ gametest_ticks_batch AREA '200' '"qaMoveUnderWay1111eStructure"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveUnderWay1111eStructure")
	public void calledOffAfterAReloadAtTheStructure(GameTestHelper helper) {
		callOffAfterAReload(helper, MoveSite.Stage.STRUCTURE);
	}

	/**
	 * "Call it off, then pick again": A's move called off at STRUCTURE and put back whole; A's row then opens on its old
	 * spot as a building like any other, T fits with Confirm ready, and the Mover carries A to T.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveUnderWay1111ePickAgain"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveUnderWay1111ePickAgain")
	public void onceTheCallOffIsOverANewSpotIsTakenAndCarriedOut(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		later(helper, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			PendingMoves.Move move = QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			run(v.level(), site, s -> s.stage() == MoveSite.Stage.STRUCTURE);
			MoveSites.get(v.level()).cancel(v.level(), v.a());
			run(v.level(), site, s -> false);
			helper.assertTrue(QaMoveSpotSaves1111eGameTests.count(helper)[0] == before[0], "setup: A isn't back whole after the call-off");

			PendingMoves.Move again = QaHallMove1111c2GameTests.plan(helper, v, v.a(), T, 0);
			helper.assertTrue(again.to().equals(v.a().offset(8, 0, 12)), "A's second move: " + again);
			MoveSite second = MoveSites.get(v.level()).begin(v.level(), again).orElseThrow(() -> new AssertionError("A's second move didn't start"));
			helper.assertTrue(second != site && !second.canceled() && second.newMin().equals(again.to()), "A's second move started as " + second.newMin() + ", canceled " + second.canceled());
			run(v.level(), second, s -> false);
			helper.assertTrue(second.stage() == MoveSite.Stage.DONE && !second.canceled() && second.skipped() == 0, "A's second move ended " + second.stage() + ", skipped " + second.skipped());
			int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
			int atT = 0;
			for (int x = 11; x <= 15; x++) {
				for (int z = 15; z <= 19; z++) {
					for (int y = 2; y <= 6; y++) {
						atT += v.level().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir() ? 0 : 1;
					}
				}
			}
			helper.assertTrue(now[0] == 0 && now[1] == 0 && atT == before[0], "after the second move: old spot " + now[0] + ", S " + now[1] + ", T " + atT + " (of " + before[0] + ")");
			helper.succeed();
		});
	}
}
