package io.github.jcondedata.aliveworkplace.test;

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
 * QA (lane C, 2026-11-03), B856: a Village Hall's raid round counts every raider within its reach as its raid's
 * ({@code VillageRaids.raiders}, not {@code VillageRaids.own}), so a neighbour village's raider keeps this village's raid
 * going after its own raiders are gone. Expected (32.21, B849's rule): the round counts only this raid's raiders.
 * Fails on the code of 2026-11-03; not registered until B856 is fixed.
 */
public class QaRaidCallOff1103cBugTests implements FabricGameTest {
	/**
	 * Two raided villages, halls 25 blocks apart on each axis. The first village's only raider falls; the second's stands
	 * 4 blocks from its own hall (within the first's reach, but nearer its own). At the first village's next round its
	 * raid ends as fought off (booked: 3 came, 3 fell, not fled); the second's goes on with its raider.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidRoundOwnRaiders1103c"'
	@GameTest(template = QaRaidCallOff1103cGameTests.AREA, timeoutTicks = 100, batch = "qaRaidRoundOwnRaiders1103c")
	public void aRaidedVillagesRoundEndsItsRaidWhenOnlyTheNeighboursRaidersAreLeft(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		QaRaidCallOff1103cGameTests.setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = QaRaidCallOff1103cGameTests.raid(helper, relA, 100);
		BlockPos hallB = QaRaidCallOff1103cGameTests.raid(helper, relB, 100);
		long began = ThreatData.get(level).raid(hallB).orElseThrow().began();
		Husk theirs = QaRaidCallOff1103cGameTests.raider(helper, new BlockPos(6, 2, 6));
		Husk ours = QaRaidCallOff1103cGameTests.raider(helper, new BlockPos(22, 2, 22));
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(ours), "setup: expected the second village's raider within the first's reach");
		theirs.kill();
		VillageRaids.tick(level, hallA, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(),
			"the first village's raid went on at its round with only the neighbour's raider about (4 blocks from its own hall, 28 from the first's): "
				+ ThreatData.get(level).raid(hallA));
		List<ThreatData.Past> past = ThreatData.get(level).past(hallA);
		helper.assertTrue(past.size() == 1 && !past.get(0).fled() && past.get(0).fell() == 3,
			"expected the first village's raid booked as fought off (3 fell, not fled), booked " + past);
		helper.assertTrue(ours.isAlive() && ThreatData.get(level).raid(hallB).map(r -> r.began() == began).orElse(false),
			"the second village's raid or raider didn't go on: " + ThreatData.get(level).raid(hallB));
		helper.succeed();
	}
}
