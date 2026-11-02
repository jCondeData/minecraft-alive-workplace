package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * Where the drowned pirates come ashore (32.8, B823): a raid the clock (or a Seer) set carries a side, and the crew of a
 * ship at anchor still wade ashore at the beach nearest the ship; with no ship, the told side holds as before. The first
 * test is QA's (lane-d-1102-1008, QaPirates1102dBugTests on tests/pirates-1102).
 */
public class PirateLandingGameTests {
	private static final String TALL = "aliveworkplace_test:tall_area";

	/** The clock's raid for tonight (every real pirate raid) comes ashore at the beach nearest the ship. */
	//$ gametest_ticks_batch TALL '100' '"pirateLandingClockBeach"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "pirateLandingClockBeach")
	public void theClocksPirateRaidWadesAshoreAtTheBeachNearestTheShip(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		QaPirates1102dGameTests.raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaPirates1102dGameTests.anchor(helper, hall, 70);
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(helper.absolutePos(QaPirates1102dGameTests.BEACH).equals(beach), "the beach nearest the ship: " + beach);
			QaPirates1102dGameTests.setTonight(level, hall, lair, 70, 13500);
			QaPiratesThreatLevelGameTests.at(level, 70, 13600);
			List<Long> raided = new ArrayList<>();
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raided.equals(List.of(70L)) && raiders.size() == 8, "tonight's raid: " + raided + ", " + raiders.size() + " raiders");
			List<String> away = new ArrayList<>();
			for (Mob m : raiders) {
				if (QaPirates1102dGameTests.horizontal(beach, m.getX(), m.getZ()) > 5 || m.getZ() <= beach.getZ()) {
					away.add(m.blockPosition().subtract(hall).toShortString());
				}
			}
			helper.assertTrue(away.isEmpty(), away.size() + " of 8 pirates of the clock's raid came ashore away from the beach nearest the ship (at "
				+ beach.subtract(hall).toShortString() + " from the hall; theirs from the hall): " + away);
			helper.succeed();
		});
	}

	/**
	 * No ship at anchor (no beach to wade to): a pirate raid told for the east gathers on the east, on dry ground at the
	 * village's edge, as a foretold raid always did.
	 */
	//$ gametest_ticks_batch TALL '100' '"pirateLandingNoShip"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "pirateLandingNoShip")
	public void withNoShipAToldPirateRaidKeepsToTheToldSide(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Lairs.near(level, hall).isEmpty(), "a ship before the test anchored one");
			QaPiratesThreatLevelGameTests.at(level, 70, 15000);
			VillageRaids.lastGathering = null;
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, 0.0, QaPiratesThreatLevelGameTests.pirates(helper), RandomSource.create(7L));
			BlockPos gather = VillageRaids.lastGathering;
			helper.assertTrue(raid != null && gather != null && !VillageRaids.raiders(level, hall).isEmpty(), "no raid without a ship: " + raid);
			BlockPos off = gather.subtract(hall);
			helper.assertTrue(off.getX() >= 8 && Math.abs(off.getZ()) <= 3, "the raid told for the east gathered at " + off.toShortString() + " from the hall");
			helper.assertTrue(level.getFluidState(gather).isEmpty() && level.getFluidState(gather.below()).isEmpty(), "they gathered in the water: " + gather);
			helper.succeed();
		});
	}
}
