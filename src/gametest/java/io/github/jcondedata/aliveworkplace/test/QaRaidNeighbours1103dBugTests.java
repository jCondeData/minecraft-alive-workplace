package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaRaidNeighbours1103dGameTests.*;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Husk;

/**
 * QA (lane D, 2026-11-03): B849's rule ("a raider nearer another village with a raid under way is that raid's") holds
 * for {@code threat end} but not for the hall's own round, which still counts every raider within its reach
 * ({@code VillageRaids.raiders}, not {@code own}): two raided villages side by side, one village's raiders leaving at
 * dawn take the neighbour's with them, and a raid whose own raiders are gone isn't fought off while the neighbour's
 * raider stands in its reach. Both fail on main 8f90fdd; see the bug filed with them.
 */
public class QaRaidNeighbours1103dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * Morning (day time 1000). The first village's bandits (hours: night) have been at it over a minute, so its round sends
	 * them off; the second village's desert raiders stay until noon. The desert raider 3 blocks from its own hall (22
	 * from the first's, within its reach) is the second raid's: it stays, and the second raid goes on, not ended as
	 * fought off in its next round.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidNeighboursDawn1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidNeighboursDawn1103d")
	public void aNeighboursDawnDepartureLeavesTheUntilNoonRaidsRaiderAndItGoesOn(GameTestHelper helper) {
		setUp(helper, 1000, REL_A, REL_B);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, REL_A, BANDITS, 2000);
		BlockPos hallB = raid(helper, REL_B, DESERT, 2000);
		long began = ThreatData.get(level).raid(hallB).orElseThrow().began();
		Husk theirs = raider(helper, new BlockPos(6, 2, 6));
		Husk ours = raider(helper, new BlockPos(24, 2, 24));
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(ours), "setup: expected the second village's raider within the first's reach");
		VillageRaids.tick(level, hallA, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(), "setup: expected the first village's bandits gone at dawn, raid: " + ThreatData.get(level).raid(hallA));
		helper.assertTrue(theirs.isRemoved(), "setup: expected the first village's own bandit sent off at dawn");
		helper.assertTrue(!ours.isRemoved() && ours.isAlive(),
			"the second village's desert raider (3 blocks from its hall, 22 from the first's; its hours until noon) was sent off when the first village's bandits left at dawn");
		VillageRaids.tick(level, hallB, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallB).map(r -> r.began() == began).orElse(false),
			"the second village's desert raid ended in its round after the neighbour's bandits left at dawn: " + ThreatData.get(level).raid(hallB)
				+ ", past: " + ThreatData.get(level).past(hallB));
		helper.succeed();
	}

	/**
	 * Night. Both villages have a bandit raid; the first's raiders are all gone, and only the second village's raider
	 * stands about, 3 blocks from its own hall and within the first's reach. That raider is the second raid's, so the
	 * first village's round finds its raid fought off: it ends, remembered as won (not fled), and the second goes on.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidNeighboursWon1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidNeighboursWon1103d")
	public void aRaidWhoseOwnRaidersAreGoneIsFoughtOffThoughTheNeighboursRaiderIsInItsReach(GameTestHelper helper) {
		setUp(helper, 18000, REL_A, REL_B);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, REL_A, BANDITS, 100);
		BlockPos hallB = raid(helper, REL_B, BANDITS, 100);
		Husk ours = raider(helper, new BlockPos(24, 2, 24));
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(ours), "setup: expected the second village's raider within the first's reach");
		VillageRaids.tick(level, hallA, 0, 0, 0, day -> { });
		List<ThreatData.Past> past = ThreatData.get(level).past(hallA);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(),
			"the first village's raid, with none of its own raiders left, wasn't fought off because the second village's raider (3 blocks from its own hall) stood within its reach: "
				+ ThreatData.get(level).raid(hallA));
		helper.assertTrue(past.size() == 1 && !past.get(0).fled(), "expected the first village's raid remembered once as fought off, past: " + past);
		helper.assertTrue(!ours.isRemoved() && ThreatData.get(level).raid(hallB).isPresent(), "expected the second village's raider and raid untouched");
		helper.succeed();
	}
}
