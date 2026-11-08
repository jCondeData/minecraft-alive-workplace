package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateBearingProtection1108eCompatTests.LANDING;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateBearingProtection1108eCompatTests.around;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateBearingProtection1108eCompatTests.halfTurn;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1108-0413): a failing test for B1032 (B993's kind). NOT registered in the compat test mod's fabric.mod.json
 * (main stays green); the builder registers it with the fix. A stranger's Mechanical Bearing outside a protected village
 * swings its arm half a turn and, stopped there, sets an oak plank down in the village's last column, as it does with the
 * village open (the control, in {@link QaCreateBearingProtection1108eCompatTests}).
 */
public class QaCreateBearingProtection1108eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** With protection on, the same half turn sets nothing down inside the village. */
	//$ gametest_ticks_batch AREA '600' '"qa_cbear_protected"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cbear_protected")
	public void aMechanicalBearingOutsideAProtectedVillageSetsNothingDownInside(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		halfTurn(helper, hall, angle -> helper.assertTrue(helper.getBlockState(LANDING).isAir(),
			"a stranger's bearing outside the protected village (stopped at " + angle + " degrees) set a block down in it (B993's kind): " + around(helper)));
	}
}
