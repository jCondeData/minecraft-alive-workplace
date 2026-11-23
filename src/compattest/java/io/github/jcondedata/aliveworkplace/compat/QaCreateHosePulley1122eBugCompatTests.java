package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateHosePulley1122eCompatTests.STRANGER_X;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateHosePulley1122eCompatTests.drain;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateHosePulley1122eCompatTests.fill;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1122-1613), found bugs, NOT registered in fabric.mod.json until fixed: the protected cases of
 * {@link QaCreateHosePulley1122eCompatTests} (the set-up, controls and the owner's guard are there). A stranger's Create
 * Hose Pulley at x = 12, just outside Frank's protected village (hall at (3, 2, 15), 8 columns across), with a Mechanical
 * Pump on its pipe side, drains the water sources of a basin running into the village (x 9..12, z 14..16, y 2), and fed
 * water pours sources into the village's cells of the same basin when dry. Expected (VillageProtection: a stranger may not
 * empty buckets or place or break blocks inside, B719: a stranger outside can't pour into or scoop out of a village; a
 * stranger's machine is a stranger, StandIns): no source inside the village changes.
 */
public class QaCreateHosePulley1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A stranger's hose pulley outside a protected village, pumped, leaves the water sources inside it. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_hose_drain_protected"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_hose_drain_protected")
	public void aStrangersHosePulleyDrainsNoWaterFromAProtectedVillage(GameTestHelper helper) {
		drain(helper, true, STRANGER_X, false, "-");
	}

	/** A stranger's hose pulley outside a protected village, fed water, pours no source inside it. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_hose_fill_protected"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_hose_fill_protected")
	public void aStrangersHosePulleyPoursNoWaterIntoAProtectedVillage(GameTestHelper helper) {
		fill(helper, true, STRANGER_X, false, "-");
	}
}
