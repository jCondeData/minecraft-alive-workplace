package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
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
 * Drowned pirates (ROADMAP 32.8): faults QA (lane-d-1102-1008) found, each failing on main today. NOT registered in
 * fabric.mod.json: register it (or move a test into QaPirates1102dGameTests) once its fault is fixed.
 */
public class QaPirates1102dBugTests {
	private static final String TALL = "aliveworkplace_test:tall_area";

	/**
	 * The raid the village's own clock set for tonight (every real pirate raid: the clock passes the side it rolled, so
	 * {@code VillageRaids.start} takes its foretold-side branch, never the SHORE branch) comes ashore at the beach nearest
	 * the ship, as 32.8 says ("the crew wade ashore at the beach nearest the ship, not from the land side"). Today they
	 * are put down 7 or more blocks from that beach, 24 blocks above the hall's height, over the water.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaPirates1102dBugClockBeach"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPirates1102dBugClockBeach")
	public void qaTheClocksPirateRaidWadesAshoreAtTheBeachNearestTheShip(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		QaPirates1102dGameTests.raidsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaPirates1102dGameTests.anchor(helper, hall, 70);
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(helper.absolutePos(QaPirates1102dGameTests.BEACH).equals(beach), "the beach nearest the ship: " + beach);
			QaPirates1102dGameTests.setTonight(level, hall, lair, 70, 13500);
			QaPiratesThreatLevelGameTests.at(level, 70, 13600);
			VillageRaids.tick(level, hall, 52, 0, 0, d -> { });
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == 8, "tonight's raid: " + raiders.size());
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
	 * A raid of one pirate (the ship's band nearly all out, or worn down): the chat line a player reads says "1 of Captain
	 * Silas Brine's drowned pirates wade ashore at ...!", where one pirate "wades". The other cultures' lair lines have
	 * the same shape ("1 of X's raiders are attacking", "1 of X's warband march on").
	 */
	//$ gametest_ticks_batch TALL '100' '"qaPirates1102dBugOne"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaPirates1102dBugOne")
	public void qaARaidOfOnePirateSaysWades(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaPirates1102dGameTests.anchor(helper, hall, 86);
			Lairs.sent(level, hall, 7); // 7 out already: one at home to send
			QaPiratesThreatLevelGameTests.at(level, 86, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, QaPiratesThreatLevelGameTests.pirates(helper), RandomSource.create(5L));
			helper.assertTrue(raid != null && VillageRaids.raiders(level, hall).size() == 1, "a raid of one: " + VillageRaids.raiders(level, hall).size());
			Lairs.Lair now = Lairs.near(level, hall).orElseThrow();
			String chat = Lairs.message(now, "raid", true, 1, VillageHalls.name(level, hall), Lairs.captainName(now)).getString();
			helper.assertFalse(chat.matches(".*\\b1 of .* pirates wade .*"), "the raid's chat line for one pirate: " + chat);
			helper.succeed();
		});
	}
}
