package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * Failing QA tests for real bugs found against the spec (ROADMAP 32.21), NOT registered in fabric.mod.json: register
 * the class to reproduce, and move each test into QaPiratesThreatLevelGameTests once its bug is fixed.
 */
public class QaPiratesThreatLevelBugTests {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final BlockPos SHIP = new BlockPos(15, 7, 14);
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");

	/**
	 * Threat level 3 is "a quarter more raiders": a raid from a lair should too. Expected: with threat level 3 set before
	 * the Pirate Ship anchors, the ship's raid on a coastal Town of 52 is a quarter more than the 8 it sends at level 2
	 * (10). Actual: 8, because {@code Lairs.raidSize} caps the raid at the band at home, and the band isn't any bigger at
	 * level 3, so no lair culture (pirates, warband, desert raiders, coven, piglins, bandits) ever sends more on hard.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaBugLevel3Lair"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaBugLevel3Lair")
	public void atThreatLevelThreeALairsRaidBringsAQuarterMore(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			QaPiratesThreatLevelGameTests.rank(level, hall, VillageRanks.Rank.TOWN);
			QaPiratesThreatLevelGameTests.at(level, 90, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(SHIP), PIRATES);
			helper.assertTrue(lair != null, "no pirate ship");
			QaPiratesThreatLevelGameTests.at(level, 90, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, QaPiratesThreatLevelGameTests.pirates(helper), RandomSource.create(5L));
			helper.assertTrue(raid != null, "no pirate raid at level 3");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == 10, "a lair's raid of 8 at threat level 3 sent " + raiders.size() + " (the band at home: " + lair.strength() + ")");
			helper.succeed();
		});
	}
}
