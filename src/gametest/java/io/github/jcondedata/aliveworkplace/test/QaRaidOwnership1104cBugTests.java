package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Husk;

/**
 * QA (lane C, 2026-11-04): a raider 49 blocks below another raided village's hall belongs to both raids. A village's
 * raiders ({@link VillageRaids#raiders}) are the tagged mobs whose body touches the box 48 blocks up and down round the
 * hall block, so a raider whose feet are 49 below the hall (its head reaches into the box) is that village's; but the
 * rule that keeps a neighbor's raiders apart (VillageRaids.own, "nearer another village with a raid under way, within
 * that village's reach") measures the feet, 48 up and down, so the same raider is also the first village's. Both
 * villages' rounds count it: one raider keeps two raids going ("Two raided villages side by side keep their raids apart",
 * wiki raids.md). Expected: it is one raid's.
 */
public class QaRaidOwnership1104cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Village A's hall at rel (29,29,0); the raider at rel (0,2,29), 2411 squared off; B's hall 49 straight above the raider. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnBug49Below_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnBug49Below_1104c")
	public void aRaider49BlocksBelowAnotherRaidedHallKeepsOnlyOneRaidGoing(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos relHall = new BlockPos(29, 29, 0);
		BlockPos relRaider = new BlockPos(0, 2, 29);
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, relHall, halls);
		BlockPos hallA = helper.absolutePos(relHall);
		BlockPos at = helper.absolutePos(relRaider);
		BlockPos hallB = at.above(49);
		QaRaidOwnership1104cGameTests.raid(helper, hallA, halls);
		QaRaidOwnership1104cGameTests.raid(helper, hallB, halls);
		Husk husk = QaRaidOwnership1104cGameTests.raider(helper, relRaider);
		helper.assertTrue(at.distSqr(hallB) < at.distSqr(hallA), "setup: expected the raider nearer the second hall");
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(husk), "setup: expected the raider within the first village's reach");
		boolean inB = VillageRaids.raiders(level, hallB).contains(husk);
		VillageRaids.tick(level, hallA, 0, 0, 0, day -> { });
		VillageRaids.tick(level, hallB, 0, 0, 0, day -> { });
		boolean aOn = ThreatData.get(level).raid(hallA).isPresent();
		boolean bOn = ThreatData.get(level).raid(hallB).isPresent();
		helper.assertTrue(husk.isAlive() && !husk.isRemoved(), "setup: expected the raider still about at night");
		helper.assertFalse(aOn && bOn,
			"one raider 49 blocks below the second village's hall (counted among the second village's raiders: " + inB
				+ ") kept both villages' raids going in their rounds: it is counted as each raid's own");
		helper.succeed();
	}
}
