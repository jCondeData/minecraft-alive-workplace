package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;

/**
 * QA (lane D, 2026-11-10), the part of B1116 its fix left ("SandRamps' over set is still memory-only"): after a restart a
 * husk over the wall by a sand ramp is the director's again and is walked to the inside of the shut gate, as before
 * B1116. Fails on main dfc770194 (5 of 5 runs); not registered, so main stays green until it is filed and fixed.
 */
public class QaLadderOverVerify1110dBugTests {
	static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * The ramp after a restart: once a husk is over the wall by the ramp, the world's threat data is saved and read back
	 * and the ramps' and the director's memory is forgotten. The husk inside is still left to its fight: never under
	 * the director's orders in the 300 ticks after, and never sent back to the ramp for more than 100 of them.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaRampOverSaved1110d"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaRampOverSaved1110d")
	public void aHuskOverTheWallByTheRampIsLeftToItsFightAfterASaveAndLoad(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaLadderOverVerify1110dGameTests.rampYard(helper);
		Mob climber = QaLadderOverVerify1110dGameTests.husk(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		Mob follower = QaLadderOverVerify1110dGameTests.husk(helper, yard, Culture.Role.MELEE, 22.5, 23.5);
		VillageRaids.track(yard.level(), yard.hall(), yard.culture(), List.of(climber, follower));
		ThreatData.Siege first = yard.siege(helper);
		helper.assertTrue(first.breach != null && !Sieges.open(first), "the siege has no shut gate as it begins: the test would prove nothing");
		long[] restarted = {-1};
		Mob[] over = {null};
		int[] ordered = {0};
		int[] ramped = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (restarted[0] < 0) {
				Mob m = QaLadderOverVerify1110dGameTests.overByRamp(helper, yard, follower, climber);
				if (m != null) {
					ThreatData.get(yard.level()).roundTrip(yard.level().registryAccess());
					SandRamps.forget();
					Ladders.forget();
					Sieges.forget();
					over[0] = m;
					restarted[0] = tick;
				} else if (tick > 2400) {
					helper.fail("no husk got over by the ramp and in (the test would prove nothing): follower "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; sand " + first.sand.size());
				}
				return;
			}
			Mob m = over[0];
			helper.assertTrue(m.isAlive(), "the husk over the wall died");
			ThreatData.Siege siege = yard.siege(helper);
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (husks here have no axes)");
			long since = tick - restarted[0];
			String director = Sieges.directorState(yard.level(), yard.hall(), m);
			if (since > 20 && director.contains(", ordered true")) {
				ordered[0]++;
			}
			if (director.contains(", ramp true")) {
				ramped[0]++;
			}
			if (since < 320) {
				return;
			}
			helper.assertTrue(ordered[0] == 0 && ramped[0] <= 100, "after a save and load the husk over the wall by the ramp was under the director's orders for "
				+ ordered[0] + " and sent to the ramp for " + ramped[0] + " of 300 ticks: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, m)
				+ ", over by ramp " + SandRamps.over(yard.level(), yard.hall(), m.getUUID()));
			helper.succeed();
		});
	}
}
