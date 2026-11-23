package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1123-1313): the cases from {@link QaCreateEdgeMore1123eCompatTests} that fail on today's code (protection
 * holes). Not registered in fabric.mod.json while they fail; register them with the fix. Each has its open-village
 * control, passing, in {@link QaCreateEdgeMore1123eCompatTests}.
 */
public class QaCreateEdgeMore1123eBugCompatTests implements FabricGameTest {
	private static final String AREA = QaCreateEdgeMore1123eCompatTests.AREA;

	/**
	 * create.md, "Fluids stop at the edge too" and "one standing outside breaks, moves and places nothing inside": Frank's
	 * 3x3 Fluid Tank straddling his protected village's edge (its controller and four of its cells inside) keeps its water
	 * when a stranger's pump outside pulls through a Fluid Pipe on one of its outside cells (B1546's family).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123x_straddle_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123x_straddle_protected")
	public void aStrangersPumpDrainsNothingFromATankStraddlingAProtectedVillagesEdge(GameTestHelper helper) {
		QaCreateEdgeMore1123eCompatTests.straddle(helper, true, false);
	}
}
