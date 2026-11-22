package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, 2026-11-22), B1487, fixed by ElevatorCabinMixin (lane-a-1122-1823): a stranger's Create Elevator Pulley
 * outside a protected village whose cabin reaches over the edge takes the village's block into its cabin when it is put
 * together, and when the pulley is broken the block can't go back (the village refuses it) and drops as an item at the
 * machine, outside: an assemble-and-break way to take blocks out of any protected village. The promise (B993, B808):
 * "a contraption whose machine stands outside a protected village must not carry blocks into it, or out of it".
 * The set-up and the open-village control are in {@link QaCreateGantryPulley1122eCompatTests#elevator}.
 */
public class QaCreateGantryPulley1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A stranger's elevator outside doesn't take a block of the protected village into its cabin, nor drop it outside. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122g_elevator_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122g_elevator_protected")
	public void aStrangersElevatorDoesntTakeABlockOutOfAProtectedVillage(GameTestHelper helper) {
		QaCreateGantryPulley1122eCompatTests.elevator(helper, true);
	}
}
