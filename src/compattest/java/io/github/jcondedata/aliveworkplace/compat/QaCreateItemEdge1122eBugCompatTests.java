package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1122-2213): the protected halves of {@link QaCreateItemEdge1122eCompatTests}, which failed before the fix
 * (3 of 3 runs, controls passing). The promise (wiki create.md): "Item transport stops at the edge too ... a Mechanical Arm
 * outside neither takes from nor puts onto a depot, belt, basin or funnel inside it". A stranger's Weighted Ejector and
 * a stranger's Mechanical Belt outside Frank's protected village both hand diamonds onto his Depot in the village's
 * last column, through Create's direct belt input (DirectBeltInputBehaviour), which no guard covered (B1520, fixed by
 * EjectorTargetMixin and BeltEndMixin; CreateItemEdgeInsideCompatTests checks the owner's own machines inside).
 */
public class QaCreateItemEdge1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** Protected: a stranger's Weighted Ejector outside puts nothing onto Frank's depot. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_ejector_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_ejector_protected")
	public void anEjectorOutsideAProtectedVillageLaunchesNothingOntoItsDepot(GameTestHelper helper) {
		QaCreateItemEdge1122eCompatTests.ejector(helper, true);
	}

	/** Protected: a stranger's belt outside carries nothing onto Frank's depot. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_belt_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_belt_protected")
	public void aBeltOutsideAProtectedVillageCarriesNothingOntoTheDepotAtItsEnd(GameTestHelper helper) {
		QaCreateItemEdge1122eCompatTests.belt(helper, true, true);
	}
}
