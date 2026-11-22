package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, 2026-11-22), not registered: the protected cases of {@link QaCreatePortableTrains1122eCompatTests}, which
 * fail today. A stranger's contraption (a Mechanical Piston outside Frank's protected village pushing a chest and a
 * moving Portable Storage Interface) docks with Frank's stationary interface in the village's last column, and his
 * hoppers move all 32 emeralds from his chest into it and its diamonds into his barrel. Village protection promises a
 * machine outside a protected village can't insert into or extract from anything inside it. Nothing in
 * {@code compat/create/mixin} looks at Create's Portable Storage Interface ({@code PortableStorageInterfaceMovement},
 * {@code PortableStorageInterfaceBlockEntity.startTransferringTo}). Register this class once that is fixed.
 */
public class QaCreatePortableTrains1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A stranger's contraption docking at Frank's interface from outside his protected village moves nothing in or out. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_psi_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_psi_protected")
	public void aStrangersContraptionDockedAtAProtectedVillagesInterfaceTradesNothing(GameTestHelper helper) {
		QaCreatePortableTrains1122eCompatTests.psiDock(helper, true, false);
	}

	/** The hall and Frank's interface saved and loaded while the stranger's contraption stands docked: still nothing moves. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_psi_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_psi_reloaded")
	public void aStrangersDockedContraptionStillTradesNothingAfterAReload(GameTestHelper helper) {
		QaCreatePortableTrains1122eCompatTests.psiDock(helper, true, true);
	}
}
