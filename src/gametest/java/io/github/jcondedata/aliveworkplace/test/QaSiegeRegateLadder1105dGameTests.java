package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-11-05): a gate put back in the breach while a ladder siege is still on (a builder mends walls and
 * gates first in a siege, or a player sets the gate back by hand). The Palisade Gate's own case, where the ram and the
 * others stand outside, is B966 (QaSiegeRegate1105dGameTests on tests/siege-regate-1105d); here a follower that let go of
 * its ladder for the open gate still gets in once the gate is shut again.
 */
public class QaSiegeRegateLadder1105dGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The ladders' side of it. The ladder stands and the follower is sent up it; the gate is broken open (by "a player")
	 * while it is still more than two blocks from the foot, so it lets go of the ladder for the gate (B907); then the gate
	 * is set back, shut. With the way shut again the follower must still get in: up the ladder ("follow up the same ladder
	 * while the gate is still shut") or through a gate broken again, within 40 seconds.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qa1105dRegateLadder"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qa1105dRegateLadder")
	public void aGateSetBackAfterTheFollowerLetGoOfTheLadderStillLetsItIn(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeLadderBreach1103dGameTests.yard(helper);
		Mob follower = QaSiegeLadderBreach1103dGameTests.raiders(helper, yard)[1];
		ThreatData.Siege siege = yard.siege(helper);
		java.util.Map<BlockPos, BlockState> gates = new java.util.LinkedHashMap<>();
		siege.gates.forEach(g -> gates.put(g.pos, yard.level().getBlockState(g.pos)));
		long[] at = {-1, -1}; // broken open, set back
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (at[0] < 0) {
				if (QaSiegeLadderBreach1103dGameTests.standing(siege) && QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower) && follower.onGround()) {
					BlockPos foot = QaSiegeLadderBreach1103dGameTests.foot(siege);
					double dx = follower.getX() - (foot.getX() + 0.5);
					double dz = follower.getZ() - (foot.getZ() + 0.5);
					if (dx * dx + dz * dz > 4 && follower.getY() < foot.getY() + 0.5) {
						at[0] = tick;
						QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
					}
				} else if (tick > 1100) {
					helper.fail("the follower was never on its way to a standing ladder: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				}
				return;
			}
			if (at[1] < 0) {
				if (!QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower)) {
					at[1] = tick;
					gates.forEach((pos, state) -> yard.level().setBlockAndUpdate(pos, state)); // a builder's repair, or a player's hand
				} else if (tick - at[0] > 200) {
					helper.fail("the follower never let go of the ladder when the gate was broken open: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				}
				return;
			}
			if (QaSiegeLadderBreach1103dGameTests.inside(helper, follower)) {
				helper.succeed();
			} else if (tick - at[1] > 800) {
				helper.fail("40 s after the gate was set back the follower is still outside: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower)
					+ "; way " + (Sieges.open(siege) ? "open" : "shut") + "; ladder foot " + QaSiegeLadderBreach1103dGameTests.rel(helper, QaSiegeLadderBreach1103dGameTests.foot(siege))
					+ ", standing " + QaSiegeLadderBreach1103dGameTests.standing(siege) + "; the siege's gates "
					+ siege.gates.stream().map(g -> QaSiegeLadderBreach1103dGameTests.rel(helper, g.pos) + " " + g.state).toList());
			}
		});
	}
}
