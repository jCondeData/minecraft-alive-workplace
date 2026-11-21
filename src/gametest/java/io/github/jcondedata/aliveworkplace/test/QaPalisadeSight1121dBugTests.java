package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;

/** B1382 residue (lane D, 2026-11-21): a raider 2 to 5 blocks out from a Palisade's stake line is out of sight
 * of every walk station (Palisade II sees 5); and on the Palisade (I) every arrow an archer on the walk looses sticks in the
 * stake top in front of him (all 40 at y 6.0, 1.3 blocks in front), so he hits neither the raider 8 out nor the one 15 out. */
public class QaPalisadeSight1121dBugTests implements FabricGameTest {
	//$ gametest_ticks_batch "aliveworkplace_test:huge_area" '300' '"qaPalisadeSight1121dNear"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 300, batch = "qaPalisadeSight1121dNear")
	public void qaEveryPalisadeWalkStationSeesARaiderTwoToFiveOut(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.walkSees(helper, StarterBlueprints.PALISADE, new BlockPos(11, 2, 2), Rotation.NONE, QaPalisadeSight1121dGameTests.NEAR_OUT);
	}

	//$ gametest_ticks_batch "aliveworkplace_test:huge_area" '300' '"qaPalisadeSight1121dNear2"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 300, batch = "qaPalisadeSight1121dNear2")
	public void qaEveryPalisadeIIWalkStationSeesARaiderTwoToFiveOut(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.walkSees(helper, StarterBlueprints.PALISADE_2, new BlockPos(11, 2, 2), Rotation.NONE, QaPalisadeSight1121dGameTests.NEAR_OUT);
	}

	//$ gametest_ticks_batch "aliveworkplace_test:huge_area" '1000' '"qaPalisadeSight1121dShoot"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 1000, batch = "qaPalisadeSight1121dShoot")
	public void qaAnArcherOnThePalisadesWalkHitsRaidersEightAndFifteenOut(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.shootsFromTheWalk(helper, StarterBlueprints.PALISADE);
	}
}
