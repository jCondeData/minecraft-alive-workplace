package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * QA (lane c, 2026-11-14) for B1204, written from the bug report: between Call off and the Mover having put the building
 * back, Confirm on a new spot is refused with "The Mover is still putting it back: pick a new spot once it stands again",
 * the building's row says the same, nothing is saved; once it stands again a new Confirm is taken and carried out.
 * QaMoveUnderWay1111eBugTests covers a call-off at STRUCTURE, half put back, through a reload. These add: the moment right
 * after Call off (no step yet, and through a reload there), a call-off while it still comes down, the last block before
 * it stands (Confirm pressed again and again, turned too), the Hall left open while the put-back ends, and another
 * building of the village that must not be held back by it (its move kept when A's put-back ends).
 */
public class QaMovePutBack1114cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** A's first new spot (x 11-15, z 3-7) and the spot picked during the put-back (x 11-15, z 15-19). */
	private static final BlockPos S = new BlockPos(13, 2, 5);
	private static final BlockPos T = new BlockPos(13, 2, 17);
	private static final String KEY = "message.aliveworkplace.move.putting_back";
	private static final String PUTTING_BACK = "The Mover is still putting it back: pick a new spot once it stands again";

	private static int standing(GameTestHelper helper) {
		return QaMoveSpotSaves1111eGameTests.count(helper)[0];
	}

	/** A's move to S begun and worked until {@code stop}, then called off through A's picker; the canceled site. */
	static MoveSite callOff(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, java.util.function.Predicate<MoveSite> stop, boolean stepAfter) {
		PendingMoves.Move move = QaHallMove1111c2GameTests.plan(helper, v, v.a(), S, 0);
		MoveSites sites = MoveSites.get(v.level());
		MoveSite site = sites.begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
		for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			sites.step(v.level(), site);
		}
		helper.assertTrue(site.stage() != MoveSite.Stage.DONE, "setup: A's move ended before the call-off");
		ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
		menu.press(MovePage.CALL_OFF, v.player());
		menu.removed(v.player());
		MovePage.stop(v.player());
		if (stepAfter) {
			sites.step(v.level(), site); // the Mover's next step: he sees the move called off and starts putting it back
			helper.assertTrue(site.canceled() && site.stage() != MoveSite.Stage.DONE, "setup: Call off didn't leave a put-back: canceled " + site.canceled() + ", " + site.stage());
		}
		return site;
	}

	private static MoveSite callOff(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, java.util.function.Predicate<MoveSite> stop) {
		return callOff(helper, v, stop, true);
	}

	/** A's row says it's put back, and Confirm on T (after {@code turns} Rotate presses) is gray, refused word for word, and saves nothing. */
	static void assertRefused(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, int turns, String when) {
		int row = QaHallMove1111c2GameTests.rowOf(helper, v, v.a());
		ChoiceMenu list = QaHallMove1111c2GameTests.list(v);
		helper.assertTrue(QaHallMove1111c2GameTests.lore(list.icon(row)).contains(PUTTING_BACK), when + ": A's row: " + QaHallMove1111c2GameTests.lore(list.icon(row)));
		list.removed(v.player());
		ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
		QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
		for (int i = 0; i < turns; i++) {
			menu.press(MovePage.ROTATE, v.player());
		}
		helper.assertTrue(!menu.icon(MovePage.CONFIRM).is(Items.PISTON) && QaHallMove1111c2GameTests.lore(menu.icon(MovePage.CONFIRM)).contains(PUTTING_BACK),
			when + ": Confirm on T: " + menu.icon(MovePage.CONFIRM) + " " + QaHallMove1111c2GameTests.lore(menu.icon(MovePage.CONFIRM)));
		for (int press = 0; press < 3; press++) {
			int seen = v.seen().size();
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(v.seen().size() > seen && QaHallMove1111c2GameTests.actionBar(v).equals(PUTTING_BACK),
				when + ": Confirm press " + (press + 1) + " said \"" + QaHallMove1111c2GameTests.actionBar(v) + "\"");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), when + ": a refused Confirm saved " + PendingMoves.get(v.level()).forBuilding(v.a()));
		}
		helper.assertTrue(!QaHallMove1111c2GameTests.words(menu).contains(QaHallMove1111c2GameTests.planned(helper.absolutePos(T))), when + ": the page says it's planned");
		menu.removed(v.player());
		MovePage.stop(v.player());
	}

	private static MoveSite reload(QaHallMove1111c2GameTests.Village v) {
		CompoundTag saved = MoveSites.get(v.level()).save(new CompoundTag(), v.level().registryAccess());
		v.level().getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, v.level().registryAccess()));
		return MoveSites.get(v.level()).forBuilding(v.a()).orElseThrow(() -> new AssertionError("the put-back didn't survive the reload"));
	}

	private static void finish(QaHallMove1111c2GameTests.Village v, MoveSite site) {
		for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoveSites.get(v.level()).step(v.level(), site);
		}
	}

	/** Once A stands again: Confirm on T is ready, taken ("Move planned"), and that move is the one carried out. */
	private static void assertTakenAndCarriedOut(GameTestHelper helper, QaHallMove1111c2GameTests.Village v, int before, String when) {
		helper.assertTrue(standing(helper) == before && MoveSites.get(v.level()).forBuilding(v.a()).isEmpty(), when + ": A put back " + standing(helper) + " of " + before);
		int row = QaHallMove1111c2GameTests.rowOf(helper, v, v.a());
		ChoiceMenu list = QaHallMove1111c2GameTests.list(v);
		helper.assertTrue(!QaHallMove1111c2GameTests.lore(list.icon(row)).contains(PUTTING_BACK), when + ": A's row once it stands: " + QaHallMove1111c2GameTests.lore(list.icon(row)));
		list.removed(v.player());
		PendingMoves.Move again = QaHallMove1111c2GameTests.plan(helper, v, v.a(), T, 0);
		MovePage.stop(v.player());
		MoveSite second = MoveSites.get(v.level()).begin(v.level(), again).orElseThrow(() -> new AssertionError(when + ": A's move to T didn't start"));
		finish(v, second);
		helper.assertTrue(!second.canceled() && second.skipped() == 0 && second.newMin().equals(again.to()) && standing(helper) == 0,
			when + ": A's move to T ended canceled " + second.canceled() + ", skipped " + second.skipped() + ", " + standing(helper) + " blocks left at its old spot");
	}

	/** The sentence the player reads, from en_us.json, is the one the bug report gives. */
	//$ gametest_batch '"aliveworkplace_test:build_area"' '"qaPutBack1114cWords"'
	@GameTest(template = "aliveworkplace_test:build_area", batch = "qaPutBack1114cWords")
	public void thePuttingBackSentenceReadsAsWritten(GameTestHelper helper) {
		String line = Component.translatable(KEY).getString();
		helper.assertTrue(line.equals(PUTTING_BACK), "en_us says \"" + line + "\"");
		helper.assertTrue(!line.equals(Component.translatable("message.aliveworkplace.move.under_way").getString()), "the put-back line is the under-way line");
		helper.succeed();
	}

	/**
	 * Called off at STRUCTURE and looked at as soon as the Mover has seen it (his first step after, at most one block
	 * back): refused (Confirm pressed three times, turned a quarter too). The same after a server restart at that moment. Then put back: taken.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114cAtOnce"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114cAtOnce")
	public void refusedRightAfterCallOffAndAfterAReloadThere(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int before = standing(helper);
				MoveSite site = callOff(helper, v, s -> s.stage() == MoveSite.Stage.STRUCTURE);
				helper.assertTrue(standing(helper) <= 1, "setup: A wasn't all taken down at STRUCTURE: " + standing(helper));
				assertRefused(helper, v, 1, "right after Call off");
				site = reload(v);
				helper.assertTrue(site.canceled(), "the reload forgot the call-off");
				assertRefused(helper, v, 0, "after a reload right after Call off");
				finish(v, site);
				assertTakenAndCarriedOut(helper, v, before, "put back after the reload");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/** Called off while A still comes down (part packed, nothing up at S): refused until it stands again, then taken. */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114cComingDown"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114cComingDown")
	public void refusedWhenCalledOffWhileItStillComesDown(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int before = standing(helper);
				MoveSite site = callOff(helper, v, s -> s.stage() == MoveSite.Stage.DISMANTLE && standing(helper) <= before / 2);
				helper.assertTrue(standing(helper) > 0 && standing(helper) < before, "setup: A half down: " + standing(helper) + " of " + before);
				assertRefused(helper, v, 0, "called off half taken down");
				finish(v, site);
				assertTakenAndCarriedOut(helper, v, before, "put back after a call-off while it came down");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/**
	 * The last moment: every block of A but one is back. Still refused. The Hall is left open on T (Confirm gray) while the
	 * Mover puts the last block back; Confirm pressed then, on the same open page, is taken, and that move is carried out.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114cLastBlock"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114cLastBlock")
	public void refusedWithOneBlockLeftAndTakenOnThePageLeftOpenOnceItStands(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int before = standing(helper);
				MoveSite site = callOff(helper, v, s -> s.stage() == MoveSite.Stage.STRUCTURE);
				for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE && standing(helper) < before - 1; i++) {
					MoveSites.get(v.level()).step(v.level(), site);
				}
				helper.assertTrue(site.stage() != MoveSite.Stage.DONE && standing(helper) == before - 1,
					"setup: one block of A left to put back: " + standing(helper) + " of " + before + ", " + site.stage());
				assertRefused(helper, v, 2, "one block left");

				ChoiceMenu menu = QaHallMove1111c2GameTests.openPickerFor(helper, v, v.a());
				QaHallMove1111c2GameTests.clickMap(helper, v, menu, T);
				helper.assertTrue(!menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Confirm ready with one block left");
				finish(v, site);
				helper.assertTrue(standing(helper) == before, "A put back " + standing(helper) + " of " + before);
				menu.press(MovePage.CONFIRM, v.player());
				String said = QaHallMove1111c2GameTests.actionBar(v);
				helper.assertTrue(!said.equals(PUTTING_BACK), "Confirm on the page left open, A standing again, still said \"" + said + "\"");
				PendingMoves.Move again = PendingMoves.get(v.level()).forBuilding(v.a())
					.orElseThrow(() -> new AssertionError("Confirm on the page left open, A standing again, planned nothing: \"" + said + "\""));
				helper.assertTrue(said.startsWith("Move planned: a Mover will put the " + QaHallMove1111c2GameTests.name() + " up again at "), "Confirm said \"" + said + "\"");
				menu.removed(v.player());
				MovePage.stop(v.player());
				MoveSite second = MoveSites.get(v.level()).begin(v.level(), again).orElseThrow(() -> new AssertionError("A's move to T didn't start"));
				finish(v, second);
				helper.assertTrue(!second.canceled() && second.skipped() == 0 && standing(helper) == 0,
					"A's move to T ended canceled " + second.canceled() + ", skipped " + second.skipped() + ", " + standing(helper) + " left at its old spot");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}

	/**
	 * Only A waits: while A is put back, hut B's row doesn't say so, B's Confirm on T is taken ("Move planned"), and when
	 * A's put-back ends B's move is still saved (the end of A's site clears A's move only).
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPutBack1114cOtherHut"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPutBack1114cOtherHut")
	public void anotherBuildingIsNotHeldBackAndKeepsItsMove(GameTestHelper helper) {
		QaHallMove1111c2GameTests.Village v = QaHallMove1111c2GameTests.stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			try {
				int before = standing(helper);
				MoveSite site = callOff(helper, v, s -> s.stage() == MoveSite.Stage.STRUCTURE);
				int rowB = QaHallMove1111c2GameTests.rowOf(helper, v, v.b());
				ChoiceMenu list = QaHallMove1111c2GameTests.list(v);
				List<String> loreB = QaHallMove1111c2GameTests.lore(list.icon(rowB));
				helper.assertTrue(!loreB.contains(PUTTING_BACK), "B's row while A is put back: " + loreB);
				list.removed(v.player());
				PendingMoves.Move b = QaHallMove1111c2GameTests.plan(helper, v, v.b(), T, 0);
				MovePage.stop(v.player());
				helper.assertTrue(QaHallMove1111c2GameTests.actionBar(v).startsWith("Move planned: a Mover will put the "), "B's Confirm said \"" + QaHallMove1111c2GameTests.actionBar(v) + "\"");
				helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "B's Confirm planned a move for A");
				finish(v, site);
				helper.assertTrue(standing(helper) == before, "A put back " + standing(helper) + " of " + before);
				helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.b()).map(m -> m.to().equals(b.to())).orElse(false),
					"B's move to T was dropped when A's put-back ended: " + PendingMoves.get(v.level()).forBuilding(v.b()));
				helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "A has a move after its put-back");
				helper.succeed();
			} catch (AssertionError e) {
				helper.fail(e.getMessage());
			}
		});
	}
}
