package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * QA (lane D, 2026-11-02), verifying B823 (40a065f59) from 32.8's text: the crew of a ship at anchor "wade ashore"
 * together at the beach nearest the ship. The clock's raid carries a side; at the beach the crew come ashore close
 * together, as an untold pirate raid does (within a block of where they wade in), not spread over the told side's
 * wider scatter. Kills the mutant that drops the fix's {@code foretold = false}, which every other pirate test lets live.
 */
public class QaPirateLanding1102dGameTests {
	private static final String TALL = "aliveworkplace_test:tall_area";

	/** The clock's pirate raid: all 8 of the crew stand within a block of where they wade ashore. */
	//$ gametest_ticks_batch TALL '100' '"qaPirateLandingTogether1102d"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPirateLandingTogether1102d")
	public void theClocksPirateCrewWadesAshoreTogetherAtTheBeach(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		QaPirates1102dGameTests.raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaPirates1102dGameTests.anchor(helper, hall, 70);
			QaPirates1102dGameTests.setTonight(level, hall, lair, 70, 13500);
			QaPiratesThreatLevelGameTests.at(level, 70, 13600);
			// A village of the default width's spread (the told side's scatter is 3 blocks from a radius of 40 up; the test
			// coast's radius of 16 gives 1, the same as wading, and would hide the difference). coast() puts the radius back.
			VillageHalls.RADIUS = 40;
			VillageRaids.lastGathering = null;
			List<Long> raided = new ArrayList<>();
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			BlockPos wade = VillageRaids.lastGathering;
			helper.assertTrue(raided.equals(List.of(70L)) && raiders.size() == 8 && wade != null,
				"setup: tonight's raid: " + raided + ", " + raiders.size() + " raiders, wading in at " + wade);
			List<String> spread = new ArrayList<>();
			for (Mob m : raiders) {
				BlockPos at = m.blockPosition();
				if (Math.abs(at.getX() - wade.getX()) > 1 || Math.abs(at.getZ() - wade.getZ()) > 1) {
					spread.add(at.subtract(wade).toShortString());
				}
			}
			helper.assertTrue(spread.isEmpty(), spread.size() + " of 8 pirates of the clock's raid came ashore more than a block from where the crew wades in"
				+ " (offsets from it): " + spread);
			helper.succeed();
		});
	}
}
