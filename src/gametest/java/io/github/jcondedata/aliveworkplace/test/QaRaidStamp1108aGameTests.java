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
 * B1063: a raider is tied to the raid it came with (its raid stamp), so a leftover of an older raid keeps no later raid
 * going, while a raid's own raider led far away still does (B1055).
 */
public class QaRaidStamp1108aGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A raider the raid held in its reach is led 200 blocks off (well beyond where an unstamped stray still counts): it is
	 * still that raid's, so the raid goes on; killed out there, the raid is fought off.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampLedFar1108a"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampLedFar1108a")
	public void aRaidsOwnRaiderLedFarAwayStillKeepsItGoing(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, QaRaidStrays1108dGameTests.HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(QaRaidStrays1108dGameTests.HALL);
		BlockPos far = hall.offset(200, 10, 0);
		Husk husk = QaRaidStrays1108dGameTests.raiderAt(helper, far);
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(husk), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, husk, "the raider");
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			husk.moveTo(hall.getX() + 3.5, hall.getY(), hall.getZ() + 0.5, 0f, 0f); // in the village, as it came
			QaRaidOwnership1104cGameTests.tick(level, hall);
			ThreatData.Under raid = ThreatData.get(level).raid(hall).orElse(null);
			helper.assertTrue(raid != null, "setup: the raid ended while its raider stood by the hall");
			helper.assertValueEqual(VillageRaids.stampOn(husk), VillageRaids.stampOf(raid), "the raid stamp of a raider the raid held");
			husk.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0f, 0f); // led far away
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(),
				"the raid ended while its own raider, led 200 blocks off, still lives: " + ThreatData.get(level).past(hall));
			helper.assertTrue(husk.isAlive() && !husk.isRemoved(), "the raid's round sent its own raider away mid-raid");
			husk.kill();
			QaRaidOwnership1104cGameTests.tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall, "the raid's only raider was killed 200 blocks off");
			helper.succeed();
		});
	}

	/**
	 * A leftover stamped by an older raid on the same village (one that ended while it stood in unloaded ground) stands
	 * just out of the reach, near enough that an unstamped stray would count: the new raid isn't its raid, so killing the
	 * new raid's raiders fights it off, and the leftover is left be.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRaidStampLeftover1108a"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRaidStampLeftover1108a")
	public void aLeftoverOfAnEndedRaidNearbyDoesNotKeepTheNextRaidGoing(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, QaRaidStrays1108dGameTests.HALL, halls);
		VillageHalls.RADIUS = 0;
		BlockPos hall = helper.absolutePos(QaRaidStrays1108dGameTests.HALL);
		Husk leftover = QaRaidStrays1108dGameTests.raiderAt(helper, hall.offset(-40, 0, 0));
		VillageRaids.stamp(leftover, new ThreatData.Under(hall, io.github.jcondedata.aliveworkplace.threat.Threats.MONSTERS, 3, 7)); // an old raid's
		QaRaidStrays1108dGameTests.whenHeld(helper, List.of(leftover), () -> {
			QaRaidStrays1108dGameTests.assertHeld(helper, leftover, "the leftover");
			QaRaidOwnership1104cGameTests.raid(helper, hall, halls);
			Husk raider = QaRaidOwnership1104cGameTests.raider(helper, QaRaidStrays1108dGameTests.HALL.offset(3, 0, 0));
			QaRaidOwnership1104cGameTests.tick(level, hall);
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: the raid ended while its raider stood by the hall");
			raider.kill();
			QaRaidOwnership1104cGameTests.tick(level, hall);
			QaRaidStrays1108dGameTests.assertFoughtOff(helper, hall, "the new raid's raider was killed; the only other raider-tagged mob came with an older raid");
			helper.assertTrue(leftover.isAlive() && !leftover.isRemoved(), "the new raid's round sent away an older raid's leftover");
			helper.succeed();
		});
	}
}
