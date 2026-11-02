package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-02, lane-c-1102-0053) of 31.22's Militia Drill: with no Training Dummy in reach the guards alone drill
 * (at the hall), as the spec says, but the chronicle still reads "Drill day in X: 1 villager sparred at the dummies.",
 * which tells the player of dummies the village doesn't have. Expected: a line that doesn't name dummies when there were
 * none (for example "... sparred by the hall.").
 */
public class QaDrillNoDummiesBug1102Tests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** No dummy in reach, a Guard by the hall at noon: the drill line counts him and doesn't speak of dummies. */
	//$ gametest_ticks_batch HUGE '100' '"qaDrillNoDummiesWords1102"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaDrillNoDummiesWords1102")
	public void aDrillWithNoDummiesDoesNotSayTheySparredAtTheDummies(GameTestHelper helper) {
		QaVillageFixesVerify1102GameTests.noDummyScene(helper, (level, hall) -> {
			QaVillageFixesVerify1102GameTests.standing(helper, QaVillageEconomySaves1101GameTests.HALL.offset(1, 0, 0), ModVillagers.GUARD, 0);
			List<String> lines = QaVillageFixesVerify1102GameTests.drillDay(level, hall);
			String village = VillageHalls.name(level, hall).getString();
			helper.assertTrue(lines.size() == 1 && lines.get(0).startsWith("Drill day in " + village + ": 1 villager "),
				"set-up: the drill line reads " + lines);
			helper.assertTrue(!lines.get(0).contains("dummies"), "with no Training Dummy in the village the chronicle reads \"" + lines.get(0)
				+ "\": it speaks of dummies the village doesn't have");
		});
	}
}
