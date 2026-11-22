package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, lane-e-1122-1613): found bugs B1502 and B1503, fixed by PloughMixin and RollerFillMixin (lane-a-1122-1823). Two block-placing paths of Create
 * actors on a stranger's train in Frank's protected village that no protection check sees.
 *
 * <p>1. A Mechanical Plough on a stranger's real
 * Create train tills the grass and dirt of Frank's protected village (it breaks no torch or flower there: that part is
 * {@link QaCreateTrainPloughRoller1122eCompatTests#aStrangersPloughTrainBreaksNoTorchOrFlowerInAProtectedVillage}).
 * Create's plough tills through {@code PloughMovementBehaviour.visitNewPosition}: a diamond hoe's {@code useOn} by its
 * {@code PloughFakePlayer} on the block under each cell it passes, which no protection check sees (MovingBreakerMixin guards
 * only {@code canBreak}). Expected: inside a protected village a stranger's machine tills nothing (VillageProtection's
 * javadoc; a stranger's machine is a stranger, StandIns). The open-village control is
 * {@link QaCreateTrainPloughRoller1122eCompatTests#aPloughTrainWorksAnOpenVillage}.
 *
 * <p>2. A Mechanical Roller set to fill (straight fill) on such a train fills the holes in its lane inside the protected
 * village with cobblestone from the train's chest: Create paves through {@code RollerMovementBehaviour.tryFill}, a plain
 * {@code level.setBlockAndUpdate}. (Its clearing goes through {@code canBreak} and is kept out; in tunnel-pave mode a
 * cell it can't clear isn't paved either, which is why
 * {@link QaCreateTrainPloughRoller1122eCompatTests#aStrangersRollerTrainChangesNothingInAProtectedVillage} passes.)
 * Expected: it places nothing inside. The control is
 * {@link QaCreateTrainPloughRoller1122eCompatTests#aFillingRollerTrainFillsAnOpenVillage}.
 */
public class QaCreateTrainPloughRoller1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A Plough on a stranger's train breaks no torch or flower and tills no ground in Frank's protected village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122c_train_plough_tills_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122c_train_plough_tills_protected")
	public void aStrangersPloughTrainChangesNothingInAProtectedVillage(GameTestHelper helper) {
		QaCreateTrainPloughRoller1122eCompatTests.actorTrain(helper, QaCreateTrainPloughRoller1122eCompatTests.Actor.PLOUGH, true);
	}

	/** A Roller set to fill on a stranger's train fills no hole in Frank's protected village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122c_train_roller_fill_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122c_train_roller_fill_protected")
	public void aStrangersFillingRollerTrainFillsNothingInAProtectedVillage(GameTestHelper helper) {
		QaCreateTrainPloughRoller1122eCompatTests.actorTrain(helper, QaCreateTrainPloughRoller1122eCompatTests.Actor.ROLLER_FILL, true);
	}
}
