package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;

/**
 * B1085: a raider that converts in vanilla ({@code Mob.convertTo}: a zombie drowning) gets a new UUID, and a siege's
 * director (who went in through the breach, who is under its orders) and its ladders (who is over the wall) keep raiders
 * by UUID. The new mob takes the old one's place there ({@code ConvertedMobs.swap}): a drowned that got in is still
 * counted through (or over) the moment it turns, is never put under the director's orders again, and isn't given the
 * ladder again. Built on QaSiegeConverts1109dGameTests' walled yard (zombies of a test culture, a fixed seed per mob).
 */
public class B1085GameTests {
	static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A zombie goes in through the broken gate and drowns once the director counts it through: the drowned is counted
	 * through at once (the zombie's UUID no longer is), and for 300 ticks (past the director's next count of its raiders)
	 * it is never under the director's orders.
	 */
	//$ gametest_ticks_batch AREA '1800' '"b1085Through"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "b1085Through")
	public void aRaiderThatDrownsAfterGettingThroughIsStillThrough(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		mobs[0].discard(); // (no climber: no ladder)
		Mob[] raider = {mobs[1]};
		ThreatData.Siege siege = yard.siege(helper);
		QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
		long[] turned = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (turned[0] < 0) {
				if (Sieges.through(yard.level(), yard.hall(), raider[0].getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, raider[0])) {
					UUID old = raider[0].getUUID();
					raider[0] = QaSiegeConverts1109dGameTests.drown(helper, raider[0]);
					raider[0].setTarget(yard.villager());
					turned[0] = tick;
					helper.assertTrue(Sieges.through(yard.level(), yard.hall(), raider[0].getUUID()),
						"the drowned isn't counted through the moment it turned: " + QaSiegeConverts1109dGameTests.state(helper, yard, raider[0]));
					helper.assertTrue(!Sieges.through(yard.level(), yard.hall(), old), "the zombie that drowned is still counted through");
				} else if (tick > 1200) {
					helper.fail("the zombie never got through the gate (the test would prove nothing): "
						+ QaSiegeConverts1109dGameTests.state(helper, yard, raider[0]));
				}
				return;
			}
			// (A fight could take it back out through the open gateway; out there it is the director's again, B175.)
			boolean inside = QaSiegeLadderBreach1103dGameTests.inside(helper, raider[0]);
			helper.assertTrue(!inside || !Sieges.directorState(yard.level(), yard.hall(), raider[0]).contains(", ordered true"),
				"the drowned, through the gate, is under the director's orders " + (tick - turned[0]) + " ticks after it drowned: "
					+ QaSiegeConverts1109dGameTests.state(helper, yard, raider[0]));
			if (tick - turned[0] > 300) {
				helper.assertTrue(!inside || Sieges.through(yard.level(), yard.hall(), raider[0].getUUID()),
					"300 ticks after it drowned inside, the drowned isn't counted through: " + QaSiegeConverts1109dGameTests.state(helper, yard, raider[0]));
				helper.succeed();
			}
		});
	}

	/**
	 * A zombie climbs over the wall by the ladder (the gate shut) and drowns inside: the drowned is counted over the moment
	 * it turns, and in the 100 ticks after, the ladder never takes it again.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b1085Over"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b1085Over")
	public void aRaiderThatDrownsAfterClimbingOverIsStillOver(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob[] follower = {mobs[1]};
		ThreatData.Siege siege = yard.siege(helper);
		long[] turned = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (turned[0] < 0) {
				if (Ladders.over(yard.level(), yard.hall(), follower[0].getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, follower[0])) {
					UUID old = follower[0].getUUID();
					follower[0] = QaSiegeConverts1109dGameTests.drown(helper, follower[0]);
					follower[0].setTarget(yard.villager());
					turned[0] = tick;
					helper.assertTrue(Ladders.over(yard.level(), yard.hall(), follower[0].getUUID()),
						"the drowned isn't counted over the wall the moment it turned: " + QaSiegeConverts1109dGameTests.state(helper, yard, follower[0]));
					helper.assertTrue(!Ladders.over(yard.level(), yard.hall(), old), "the zombie that drowned is still counted over the wall");
				} else if (tick > 2000) {
					helper.fail("the follower never got over by the ladder and in (the test would prove nothing): follower "
						+ QaSiegeConverts1109dGameTests.state(helper, yard, follower[0]) + "; climber " + QaSiegeConverts1109dGameTests.state(helper, yard, climber)
						+ "; rungs " + siege.ladders.size());
				}
				return;
			}
			helper.assertTrue(!QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower[0]),
				"the drowned, over the wall before it drowned, is held to the ladder again " + (tick - turned[0]) + " ticks after it drowned: "
					+ QaSiegeConverts1109dGameTests.state(helper, yard, follower[0]));
			if (tick - turned[0] > 100) {
				helper.assertTrue(Ladders.over(yard.level(), yard.hall(), follower[0].getUUID()),
					"100 ticks after it drowned, the drowned isn't counted over the wall: " + QaSiegeConverts1109dGameTests.state(helper, yard, follower[0]));
				helper.succeed();
			}
		});
	}
}
