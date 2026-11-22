package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * QA (lane D, 2026-11-21, lane-d-1121-2208) for B1439's fix, written from the bug's text: "an archer on the Palisade's
 * walk hits raiders 8-15 out as on Palisade II" (before, every arrow stuck in the stake top in front of him). The fix's
 * own test covers a bow and two raiders straight out in front. These go where it doesn't: a guard who traded his bow for
 * a crossbow (a flatter, faster bolt, spread and aim of its own), and raiders at the palisade's two ends, so the shot
 * slants over the stake line, on both palisade levels. (Mutation, 2026-11-21: with the fix's aim taken out these four
 * still pass; the fix's own QaPalisadeSight1121dBugTests fails then. They guard the crossbow and slanting shots, not B1439's
 * exact case.) A real siege each: the archer climbs to the walk and must
 * hit both raiders without coming down. The raiders stay 8 or more out (2 to 5 out is the owner's call, B1439).
 */
public class QaPalisadeAim1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch AREA '1000' '"qaPalisadeAim1121dCrossbow"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaPalisadeAim1121dCrossbow")
	public void qaACrossbowArcherOnThePalisadesWalkHitsRaidersEightAndFifteenOut(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.shootsFromTheWalk(helper, StarterBlueprints.PALISADE, Items.CROSSBOW, 1, 8, 5, 15);
	}

	//$ gametest_ticks_batch AREA '1000' '"qaPalisadeAim1121dCrossbow2"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaPalisadeAim1121dCrossbow2")
	public void qaACrossbowArcherOnPalisadeIIsWalkHitsRaidersEightAndFifteenOut(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.shootsFromTheWalk(helper, StarterBlueprints.PALISADE_2, Items.CROSSBOW, 1, 8, 5, 15);
	}

	/** One raider 10 out in front of the palisade's east end, one 15 out in front of its west end: from most walk stations the shots slant over the stakes. */
	//$ gametest_ticks_batch AREA '1000' '"qaPalisadeAim1121dSlant"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaPalisadeAim1121dSlant")
	public void qaAnArcherOnThePalisadesWalkHitsRaidersOffToTheSide(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.shootsFromTheWalk(helper, StarterBlueprints.PALISADE, Items.BOW, 6, 10, 0, 15);
	}

	//$ gametest_ticks_batch AREA '1000' '"qaPalisadeAim1121dSlant2"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaPalisadeAim1121dSlant2")
	public void qaAnArcherOnPalisadeIIsWalkHitsRaidersOffToTheSide(GameTestHelper helper) {
		QaPalisadeSight1121dGameTests.shootsFromTheWalk(helper, StarterBlueprints.PALISADE_2, Items.BOW, 6, 10, 0, 15);
	}
}
