package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCreatePsi1122eCompatTests.Cart;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, 2026-11-22): a stranger's Create cart with a Portable Storage Interface docking at the owner's PSI inside a
 * protected village (set-up and controls in {@link QaCreatePsi1122eCompatTests}). B994's expectation: "a funnel (or
 * chute, Mechanical Arm, Portable Storage Interface) outside a protected village takes nothing from a container inside
 * it"; village-hall.md: with protection on only the owner, friends and operators may take from containers there.
 * Unregistered while it fails (register it with the fix).
 */
public class QaCreatePsi1122eBugTests implements FabricGameTest {
	private static final String AREA = QaCreatePsi1122eCompatTests.AREA;

	/** A stranger's cart, put together outside, docking at Frank's PSI in his protected village takes none of his diamonds. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122x_psi_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122x_psi_protected")
	public void aStrangersCartDockingAtAPsiInAProtectedVillageTakesNothing(GameTestHelper helper) {
		QaCreatePsi1122eCompatTests.run(helper, Cart.STRANGER, true, false, false);
	}

	/**
	 * A stranger's Mechanical Piston outside the village pushes its PSI up to Frank's PSI at the village's last column (it
	 * never enters the village): it takes none of his diamonds.
	 */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122x_psi_piston_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122x_psi_piston_protected")
	public void aStrangersPistonPsiOutsideAProtectedVillageTakesNothingAtItsEdge(GameTestHelper helper) {
		QaCreatePsi1122eCompatTests.pistonRun(helper, true, false);
	}

	/** The same, with the stranger's cart saved and loaded on its way (still outside the village). */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122x_psi_protected_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122x_psi_protected_reloaded")
	public void aStrangersCartSavedAndLoadedOnTheWayStillTakesNothingAtAProtectedVillagesPsi(GameTestHelper helper) {
		QaCreatePsi1122eCompatTests.run(helper, Cart.STRANGER, true, true, false);
	}
}
