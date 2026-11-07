package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateFunnelAtProtectedVillage1107eCompatTests.*;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;

/**
 * QA (lane-e-1107-0413), the failing half of {@link QaCreateFunnelAtProtectedVillage1107eCompatTests}: a stranger's
 * Create funnel outside a protected village takes the diamonds out of a chest in its last column.
 */
public class QaCreateFunnelAtProtectedVillage1107eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '300' '"qa_cprot_funnel_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cprot_funnel_protected")
	public void aFunnelOutsideAProtectedVillageTakesNothingFromTheChestAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaCreateIntoProtectedVillage1107eCompatTests.hall(helper, true);
		Container chest = chestAndFunnel(helper);
		helper.runAfterDelay(200, () -> {
			QaCreateIntoProtectedVillage1107eCompatTests.done(helper, hall);
			helper.assertTrue(diamonds(chest) == 16, "a stranger's brass funnel outside Frank's protected village took "
				+ (16 - diamonds(chest)) + " of 16 diamonds out of the chest in its last column (village-hall.md: only the owner,"
				+ " friends and operators may open chests there; B745: machines outside stop at the edge); "
				+ diamondsOnTheGround(helper) + " lie outside");
			helper.succeed();
		});
	}
}
