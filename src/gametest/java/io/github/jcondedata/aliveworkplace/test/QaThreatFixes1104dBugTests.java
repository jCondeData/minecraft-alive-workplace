package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;

/**
 * QA (lane D, 2026-11-04), a bug test (fails on main; register it with the fix). B907's fix lets go of a follower on
 * its way to a siege ladder when the gate is broken open, but only on the ladders' own step (every 10 ticks), and only
 * while it isn't yet up the ladder (more than half a block above the foot). A follower on the ground within two blocks
 * of the foot when the gate goes reaches the ladder and starts up before that step comes, so it is never let go: it
 * climbs the whole ladder and goes over the wall by it (a probe run: 5.2 blocks up, over by the ladder, 56 ticks after
 * the gate broke, from 1.8 blocks off the foot). The wiki (sieges.md): "Once the gate is open, one still on its way to
 * a ladder turns and goes in by the gate instead."
 */
public class QaThreatFixes1104dBugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The near side of "on its way": the follower sent up the standing ladder is on the ground within two blocks of its
	 * foot (the existing bug test breaks the gate only while it is more than two away) when the gate is broken open. It
	 * still hasn't started up, so it turns: never more than two blocks up the ladder, and in by the gate.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1104dLadderNearFoot"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1104dLadderNearFoot")
	public void aFollowerTwoBlocksFromTheLadderFootTurnsForTheOpenGate(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeLadderBreach1103dGameTests.yard(helper);
		Mob follower = QaSiegeLadderBreach1103dGameTests.raiders(helper, yard)[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		BlockPos[] foot = {null};
		double[] highest = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (opened[0] < 0) {
				if (QaSiegeLadderBreach1103dGameTests.standing(siege) && QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower) && follower.onGround()) {
					BlockPos at = QaSiegeLadderBreach1103dGameTests.foot(siege);
					double dx = follower.getX() - (at.getX() + 0.5);
					double dz = follower.getZ() - (at.getZ() + 0.5);
					double d2 = dx * dx + dz * dz;
					if (d2 <= 4 && d2 > 0.25 && follower.getY() < at.getY() + 0.5) {
						foot[0] = at;
						opened[0] = tick;
						QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
					}
				}
				if (opened[0] < 0 && tick > 1100) {
					helper.fail("the follower was never on the ground within two blocks of a standing ladder's foot (the test would prove nothing): "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				}
				return;
			}
			QaThreatFixes1104dGameTests.climbsNoMoreAndGoesInByTheGate(helper, yard, follower, tick, opened[0], foot[0], highest, "the follower two blocks from the foot");
		});
	}
}
