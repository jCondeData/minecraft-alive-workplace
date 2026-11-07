package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-c-1103-0353) of B753 at the config's edge: the Steward's streets keep off a Wonder's ground "round the hall".
 * With villageHallRadius at its lowest (16), a Wonder laid out just past 16 blocks from the hall, still well inside the
 * village's reach, is kept off like any building (the nightly mutation run of 2026-11-02 found nothing checked this:
 * Roads.buildings' Wonder reach of radius + 32 turned to radius - 32 survived, as every test ran at radius 64).
 */
public class QaRoadWonderSmallRadius1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	/** At radius 16 a waiting Wonder about 16.1 blocks from the hall is still gone round, and forgotten it isn't. */
	//$ gametest_ticks_batch AREA '40' '"qaRoadWonderSmallRadius1103"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRoadWonderSmallRadius1103")
	public void qaAtTheSmallestVillageRadiusAStreetStillGoesRoundAWonder(GameTestHelper helper) {
		BlockPos hall = QaRoadGameTests.ground(helper, new BlockPos(2, 2, 26), 14);
		Leftovers.wonderGround(helper, hall);
		int radius = VillageHalls.RADIUS; // set after the clearing, which reaches as far as the radius in force
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		WonderSite site = RoadWonderGameTests.folly(helper, hall, false);
		RoadWonderGameTests.clearOf(helper, RoadWonderGameTests.route(helper, hall), "waiting (village radius 16)");
		WonderManager.get(helper.getLevel()).remove(site.id());
		RoadWonderGameTests.straight(helper, RoadWonderGameTests.route(helper, hall));
		LOG.info("[QA 1103] B753: at village radius 16 the street went round a Wonder 16.1 blocks from the hall, and straight once it was gone");
		helper.succeed();
	}
}
