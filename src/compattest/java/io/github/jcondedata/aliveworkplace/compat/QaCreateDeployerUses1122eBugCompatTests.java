package io.github.jcondedata.aliveworkplace.compat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-e-1122-1613): the two protected bucket cases of {@link QaCreateDeployerUses1122eCompatTests}, which fail on
 * main 6eab2022b (B-number in ROADMAP Bugs: a stranger's Create Deployer outside a protected village pours water into it
 * and scoops its water out). VillageProtection: only the owner, friends and operators may "empty buckets" inside; a
 * stranger's machine is a stranger. The open-village controls (in the main class) pass, so the deployer really acts.
 */
public class QaCreateDeployerUses1122eBugCompatTests {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A stranger's deployer with a water bucket outside a protected village pours no water inside it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_pour_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_pour_protected")
	public void aStrangersDeployerPoursNoWaterIntoAProtectedVillage(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.pour(helper, true);
	}

	/** A stranger's deployer with an empty bucket outside a protected village can't scoop the water at its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_scoop_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_scoop_protected")
	public void aStrangersDeployerScoopsNoWaterFromAProtectedVillage(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.scoop(helper, true);
	}
}
