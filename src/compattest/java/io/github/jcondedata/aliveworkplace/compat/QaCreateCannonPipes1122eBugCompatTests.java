package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1122-2213): the protected halves of {@link QaCreateCannonPipes1122eCompatTests}, which fail today (found
 * bugs): a stranger's Create Schematicannon standing outside Frank's protected village prints its schematic's blocks into
 * the village, in "don't replace" mode into air and in "replace any" mode over Frank's oak planks; and a stranger's
 * open-ended Fluid Pipe with a Mechanical Pump outside pours a water source into, and drains one out of, a cup on the
 * village's last column. Each set-up first asks VillageProtection.mayMachineChange, which says no. The controls (the
 * village open) and the guards (Frank's own cannon and pipe inside his protected village) pass and live in the other
 * class. Register this class together with the fix.
 */
public class QaCreateCannonPipes1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A stranger's Schematicannon outside a protected village prints nothing into it. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cannon_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cannon_protected")
	public void aStrangersSchematicannonPrintsNothingIntoAProtectedVillage(GameTestHelper helper) {
		QaCreateCannonPipes1122eCompatTests.strangersCannon(helper, true, false);
	}

	/** A stranger's Schematicannon in "replace any" outside a protected village leaves Frank's planks standing. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cannon_replace_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cannon_replace_protected")
	public void aStrangersSchematicannonReplacesNothingInAProtectedVillage(GameTestHelper helper) {
		QaCreateCannonPipes1122eCompatTests.strangersCannon(helper, true, true);
	}

	/** A stranger's open pipe outside a protected village pours no water into it. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pipe_fill_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pipe_fill_protected")
	public void aStrangersOpenPipePoursNoWaterIntoAProtectedVillage(GameTestHelper helper) {
		QaCreateCannonPipes1122eCompatTests.checkLayout(helper);
		QaCreateCannonPipes1122eCompatTests.openPipe(helper, true, QaCreateCannonPipes1122eCompatTests.CUP, true, false, "");
	}

	/** A stranger's open pipe outside a protected village drains no water source inside it. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pipe_drain_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pipe_drain_protected")
	public void aStrangersOpenPipeDrainsNoWaterFromAProtectedVillage(GameTestHelper helper) {
		QaCreateCannonPipes1122eCompatTests.checkLayout(helper);
		QaCreateCannonPipes1122eCompatTests.openPipe(helper, true, QaCreateCannonPipes1122eCompatTests.CUP, false, false, "");
	}
}
