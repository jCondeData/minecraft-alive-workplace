package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;

/** QA bug test split out of QaRaidStrays1108dGameTests (lane-d-1108-1608): fails until the bug is fixed. */
public class QaRaidOrphan1108dBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * c. A mob carrying the raider tag that belongs to no raid under way (left over from an older raid that ended while
	 * its chunk was unloaded, as B1055 says happens on the live server) stands in loaded ground 200 blocks off. A new
	 * raid comes and the players kill every one of its raiders: that raid is fought off ("a raid isn't fought off while
	 * ITS raider lives": the leftover isn't its raider).
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidOrphan1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidOrphan1108d")
	public void aLeftoverRaiderOfNoRaidFarAwayDoesNotKeepANewRaidGoing(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, QaRaidStrays1108dGameTests.HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(QaRaidStrays1108dGameTests.HALL);
		Husk orphan = QaRaidStrays1108dGameTests.raiderAt(helper, hall.offset(200, 10, 0));
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(orphan), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, orphan, "the leftover raider");
			helper.assertTrue(ThreatData.get(level).raids().isEmpty(), "setup: a raid was already under way: " + ThreatData.get(level).raids());
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			Husk first = QaRaidOwnership1104cGameTests.raider(helper, QaRaidStrays1108dGameTests.HALL.offset(3, 0, 0));
			Husk second = QaRaidOwnership1104cGameTests.raider(helper, QaRaidStrays1108dGameTests.HALL.offset(-3, 0, 2));
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: the raid ended while two of its raiders stood by the hall");
			first.kill();
			second.kill();
			QaRaidOwnership1104cGameTests.tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall, "every raider of the new raid was killed; the only raider-tagged mob alive is a leftover of no raid, 200 blocks off");
			helper.assertTrue(orphan.isAlive(), "the village's round sent away a leftover that was never its raid's");
			helper.succeed();
		});
	}
}
