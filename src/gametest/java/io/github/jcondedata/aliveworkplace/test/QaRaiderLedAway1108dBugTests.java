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
import net.minecraft.world.entity.monster.Husk;

/**
 * Found by lane D (2026-11-08), from wiki raids.md ("The raid is over when the raiders are dead, or at dawn"; "a village
 * whose raiders are all dead has fought its raid off"; at their hour "those alive ... leave"). A raid's last raider led
 * out of the village's reach (RADIUS + 32 from the hall: a player kiting it away, or it chasing someone) is counted by no
 * raid any more: the hall's next round ends the raid as fought off ("The village fought off the raid (3 monsters)", 3
 * came, 3 fell) while it lives, and since the raid is over nobody sends it away at dawn either: a raider never despawns,
 * so it stays in the world for good, still tagged a raider of no raid.
 * Expected: a raid isn't fought off while its raider lives, and by the morning the raider has left like the others.
 */
public class QaRaiderLedAway1108dBugTests {
	private static final BlockPos HALL = new BlockPos(29, 2, 29);

	//$ gametest_ticks_batch 'QaThreatFixes1104dGameTests.HUGE' '100' '"qaRaiderLedAway1108d"'
	@GameTest(template = QaThreatFixes1104dGameTests.HUGE, timeoutTicks = 100, batch = "qaRaiderLedAway1108d")
	public void aRaiderLedOutOfTheVillagesReachIsNotFoughtOffAndLeavesAtDawn(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, HALL, halls);
		VillageHalls.RADIUS = 0; // the reach is then 32 blocks from the hall (put back by setUp's cleanup)
		BlockPos hall = helper.absolutePos(HALL);
		QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
		// One block past the reach, west of the hall: the raid's last raider, led away.
		Husk husk = QaRaidOwnership1104cGameTests.raider(helper, HALL.offset(-33, 0, 0));
		husk.setPersistenceRequired(); // as every raider is (Threats.outfit)
		helper.assertTrue(level.isLoaded(husk.blockPosition()) && husk.isAlive(), "setup: the led-away raider isn't in loaded ground");
		helper.assertFalse(VillageRaids.reaches(hall, husk), "setup: the raider is still within the village's reach");
		QaRaidOwnership1104cGameTests.tick(level, hall);
		List<ThreatData.Past> past = ThreatData.get(level).past(hall);
		helper.assertFalse(husk.isAlive() && past.stream().anyMatch(p -> !p.fled() && p.fell() >= p.came()),
			"the village's night round booked its raid as fought off, every raider fallen, while its raider lives just out of the reach: " + past);
		level.setDayTime((level.getDayTime() / 24000) * 24000 + QaRaidOwnership1104cGameTests.MORNING + 24000);
		QaRaidOwnership1104cGameTests.tick(level, hall);
		helper.assertTrue(husk.isRemoved() || !husk.isAlive(),
			"morning came and the raid's raider is still in the world (tags " + husk.getTags() + ", never despawns: " + husk.isPersistenceRequired()
				+ "); raid under way: " + ThreatData.get(level).raid(hall).isPresent() + ", past: " + ThreatData.get(level).past(hall));
		helper.succeed();
	}
}
