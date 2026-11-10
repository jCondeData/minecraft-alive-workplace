package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;

/**
 * QA (lane D, 2026-11-09) for raiders who got over the wall by a siege ladder while the gate stays shut (ROADMAP 32.5;
 * wiki sieges.md: "They climb it, cross the wall's top and drop inside"; "Every rung is recorded in the siege's saved
 * data"; since B1126 who is over the wall is saved with it too). After a restart a raider already inside must not be taken
 * for one still to climb. Uses QaSiegeConverts1109dGameTests' yard (zombies with the ladders tactic, a Stone Wall, a
 * shut Palisade Gate, a villager shut in a stone cell). The director walking such a raider to the gate's inside is
 * B1116 (QaLadderOverShutGate1109dBugTests).
 */
public class QaLadderOver1109dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A restart mid-siege (the server stops while a zombie that came over the wall by the ladder is inside; the ladders'
	 * and the director's memory is gone, the siege and its rungs are saved): the zombie already inside isn't sent back to
	 * the ladder (held to it for 100 ticks at most in the 400 after) and never goes back up it or out over the wall.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverRestart1109d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverRestart1109d")
	public void aRaiderOverTheWallIsNotSentBackToTheLadderAfterARestart(GameTestHelper helper) {
		restart(helper, false);
	}

	/**
	 * B1126: the same restart with the world's threat data saved and read back, as a real one does. Who got over the wall
	 * by a ladder is saved with the siege, so the zombie inside is still counted over the wall once the ladders run again
	 * and is never handed the ladder (before the fix it was, for 40 to 100 ticks and more, until it walked back onto the
	 * wall's inner side and was let go again).
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverSaved1126"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverSaved1126")
	public void aRaiderOverTheWallStaysOverItAfterASaveAndLoad(GameTestHelper helper) {
		restart(helper, true);
	}

	private static void restart(GameTestHelper helper, boolean saved) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] restarted = {-1};
		long[] overAt = {-1};
		int[] held = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (restarted[0] < 0) {
				if (overAt[0] < 0 && Ladders.over(yard.level(), yard.hall(), follower.getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, follower)) {
					overAt[0] = tick;
				}
				if (overAt[0] >= 0 && tick - overAt[0] >= 60) {
					if (saved) {
						ThreatData.get(yard.level()).roundTrip(yard.level().registryAccess());
					}
					Ladders.forget();
					Sieges.forget();
					restarted[0] = tick;
				} else if (tick > 2000) {
					helper.fail("the follower never got over by the ladder and in (the test would prove nothing): follower "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; rungs " + siege.ladders.size());
				}
				return;
			}
			if (QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower)) {
				held[0]++;
			}
			if (saved) {
				helper.assertTrue(held[0] == 0, "after a save and load the zombie inside was handed the ladder again: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				helper.assertTrue(tick - restarted[0] < 2 * Ladders.EVERY || Ladders.over(yard.level(), yard.hall(), follower.getUUID()),
					"after a save and load the zombie inside isn't counted over the wall: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			}
			helper.assertTrue(held[0] <= 100, "after the restart the zombie inside has been held to the ladder again for " + held[0]
				+ " ticks: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			helper.assertTrue(!QaSiegeLadderBreach1103dGameTests.upTheLadder(follower, QaSiegeLadderBreach1103dGameTests.foot(siege))
				&& follower.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() < 15,
				"after the restart the zombie inside went back up the ladder or out over the wall: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			if (tick - restarted[0] >= 400) {
				helper.succeed();
			}
		});
	}
}
