package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-09) for raiders who got over the wall by a siege ladder while the gate stays shut (ROADMAP 32.5
 * ladders, 32.2 the director; the "related" part of B1085's report, which B1085's fix didn't change). Ladders keeps the
 * ones over the wall in its own {@code over} set, but the director only leaves alone the raiders it counts "through" the
 * breach and the ones a ladder still holds ({@code Ladders.claims}: on the way to or up a ladder). A raider let go past
 * the wall's inner face is neither, so the director gives it FOLLOW orders again, and while no villager is in its sight
 * (all indoors) SiegeGoal walks it to the gate's inner side, where it stands for the rest of the siege.
 *
 * <p>What the player sees: the zombies that climbed into the village gather at the inside of the shut gate instead of
 * going after the villagers in their houses, as a raider through the breach does. Expected (Sieges javadoc: the director
 * walks the others "behind the rams, then through the breach"; a raider already inside is past both, like one through
 * the breach, which "the director leaves be"; SiegeGoal: it holds them "without it their own fighting would walk them
 * round the walls after villagers they can't reach", and one over the wall can reach them): a raider over the wall is
 * left to its fight.
 *
 * <p>Uses QaSiegeConverts1109dGameTests' yard: zombies of a test culture with the ladders tactic, a Stone Wall the
 * ladder goes up (x 14), a shut Palisade Gate east of it (x 19 to 25) and a villager shut in a stone cell (x 10, z 7),
 * out of every zombie's sight.
 */
public class QaLadderOverShutGate1109dBugTests {
	static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * The follower zombie goes over the wall by the ladder, the gate still shut. From 60 ticks after it is counted over,
	 * for 300 ticks: it is never under the director's orders, and at the end it is nearer the villager's cell than the
	 * inside of the shut gate.
	 */
	//$ gametest_ticks_batch AREA '2600' '"qaLadderOverShutGate1109d"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qaLadderOverShutGate1109d")
	public void aRaiderOverTheWallWithTheGateShutIsLeftToItsFight(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] overAt = {-1};
		int[] ordered = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (zombies here have no axes)");
			helper.assertTrue(follower.isAlive(), "the follower died: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			if (overAt[0] < 0) {
				if (Ladders.over(yard.level(), yard.hall(), follower.getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, follower)) {
					overAt[0] = tick;
				} else if (tick > 2000) {
					helper.fail("the follower never got over by the ladder and in (the test would prove nothing): follower "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; rungs " + siege.ladders.size());
				}
				return;
			}
			long since = tick - overAt[0];
			if (since > 60 && Sieges.directorState(yard.level(), yard.hall(), follower).contains(", ordered true")) {
				ordered[0]++;
			}
			if (since < 360) {
				return;
			}
			Vec3 gate = Vec3.atBottomCenterOf(siege.breach).add(-siege.out.getStepX() * 2, 0, -siege.out.getStepZ() * 2);
			Vec3 cell = yard.villager().position();
			double toGate = follower.position().distanceTo(gate);
			double toCell = follower.position().distanceTo(cell);
			String where = String.format(Locale.ROOT, "%.1f blocks from the gate's inside, %.1f from the villager's cell", toGate, toCell);
			helper.assertTrue(ordered[0] == 0, "the zombie over the wall was under the director's orders for " + ordered[0]
				+ " of 300 ticks (" + where + "): " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			helper.assertTrue(toCell < toGate, "300 ticks after it got over the wall, the zombie stands by the shut gate, not after the villager ("
				+ where + "): " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			helper.succeed();
		});
	}
}
