package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, lane-e-1122-1313), failing today: a Mechanical Harvester riding a stranger's real Create train reaps the
 * ripe wheat of a protected village it runs through (4 of 4 wheat reaped, 2026-11-22). The promise (B993/B808,
 * village-hall.md): a machine outside a protected village changes nothing inside it. The open-village control, and the
 * set-up, are {@link QaCreateTrainActors1122eCompatTests}'s.
 */
public class QaCreateTrainActors1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A Mechanical Harvester on a stranger's train reaps none of Frank's ripe wheat in his protected village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122b_train_harvester_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122b_train_harvester_protected")
	public void aStrangersHarvesterTrainReapsNothingInAProtectedVillage(GameTestHelper helper) {
		QaCreateTrainActors1122eCompatTests.actorTrain(helper, QaCreateTrainActors1122eCompatTests.Actor.HARVESTER, true, false);
	}
}
