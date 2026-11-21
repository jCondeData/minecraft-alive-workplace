package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** B1439 (lane D, 2026-11-21): on the Palisade (I) every arrow an archer on the walk loosed stuck in the stake top in front
 * of him (all 40 at y 6.0, 1.3 blocks in front), so he hit neither the raider 8 out nor the one 15 out. The bug's other
 * cases (a raider 2 to 5 out of sight of every walk station, the stakes' foot) wait for the owner's call on the
 * Palisade's look: they are on the branch tests/wall-sight-1121d (ROADMAP Notes, B1439). */
public class QaPalisadeSight1121dBugTests implements FabricGameTest {
	//$ gametest_ticks_batch '"aliveworkplace_test:huge_area"' '1000' '"qaPalisadeSight1121dShoot"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 1000, batch = "qaPalisadeSight1121dShoot")
	public void qaAnArcherOnThePalisadesWalkHitsRaidersEightAndFifteenOut(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.shootsFromTheWalk(helper, StarterBlueprints.PALISADE);
	}
}
