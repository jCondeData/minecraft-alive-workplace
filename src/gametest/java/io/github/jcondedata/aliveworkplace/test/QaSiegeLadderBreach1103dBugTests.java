package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;

/**
 * QA (lane D, 2026-11-03), a bug test (fails on main; register it with the fix). A follower sent up a standing siege
 * ladder is let go of it when the gate is broken open while it is still walking there (Ladders.step's release), but
 * release() leaves its navigation alone and the director only re-paths a raider whose navigation is done: it walks on
 * to the ladder's foot anyway, then, sent for the way in, presses into the wall at the ladder and climbs it the vanilla
 * way to the wall's top (y +5), falls back outside and only then walks round to the gate (about 100 ticks lost). The
 * spec: "the other raiders follow up the same ladder while the gate is still shut"; once it is open, they go in by it.
 */
public class QaSiegeLadderBreach1103dBugTests {
	/** The siege ladder tests' area (a plain name: Stonecutter's swap lines can't take a dotted one). */
	private static final String AREA = QaSiegeLadderBreach1103dGameTests.AREA;

	/** After the gate is broken open, the follower that was let go never goes more than two rungs up the ladder. */
	//$ gametest_ticks_batch AREA '1500' '"qaLadderBreachLetGoNoClimb1103d"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaLadderBreachLetGoNoClimb1103d")
	public void aFollowerLetGoOfTheLadderDoesntClimbItAnyway(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeLadderBreach1103dGameTests.yard(helper);
		Mob[] mobs = QaSiegeLadderBreach1103dGameTests.raiders(helper, yard);
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		BlockPos[] foot = {null};
		double[] highest = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (opened[0] < 0) {
				if (QaSiegeLadderBreach1103dGameTests.standing(siege) && QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower) && follower.onGround()) {
					foot[0] = QaSiegeLadderBreach1103dGameTests.foot(siege);
					double dx = follower.getX() - (foot[0].getX() + 0.5);
					double dz = follower.getZ() - (foot[0].getZ() + 0.5);
					if (dx * dx + dz * dz > 4 && follower.getY() < foot[0].getY() + 0.5) {
						opened[0] = tick;
						QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
					}
				} else if (tick > 1100) {
					helper.fail("the follower was never sent up a standing ladder while still on the ground (the test would prove nothing): "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				}
				return;
			}
			if (QaSiegeLadderBreach1103dGameTests.upTheLadder(follower, foot[0])) {
				highest[0] = Math.max(highest[0], follower.getY() - foot[0].getY());
			}
			helper.assertTrue(highest[0] <= 2.0, String.format(java.util.Locale.ROOT, "the follower let go of the ladder still climbed it %.1f blocks, %d ticks after "
				+ "the gate was broken open (it should turn for the gate): ", highest[0], tick - opened[0]) + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower)
				+ "; ladder foot " + QaSiegeLadderBreach1103dGameTests.rel(helper, foot[0]));
			if (QaSiegeLadderBreach1103dGameTests.inside(helper, follower) && Sieges.through(yard.level(), yard.hall(), follower.getUUID())) {
				helper.succeed();
			} else if (tick - opened[0] > 400) {
				helper.fail("the follower isn't in through the gate 400 ticks after it was broken open: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			}
		});
	}
}
