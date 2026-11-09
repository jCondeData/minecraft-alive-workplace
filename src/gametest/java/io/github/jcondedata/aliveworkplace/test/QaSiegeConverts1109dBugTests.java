package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-09) bug test for B1085 (fixed: registered with the fix): a raider that converts in vanilla
 * ({@code Mob.convertTo}: a zombie drowning) after it got in through a broken gate is no longer counted "through" by the
 * siege's director, which keeps that list by UUID ({@code Sieges.Director#through}) and was not part of the B1072 swap
 * ({@code ConvertedMobs.swap} swaps lairs, bounties and arcs only).
 *
 * <p>What the player sees: the raider that was in the village, by a house it was attacking (no villager in its sight),
 * turns round after it drowns and walks back toward the gate, to the point six blocks in from it, before the director
 * counts it through again and lets it go back to the fight. Expected (ROADMAP 32.2, Sieges javadoc: "the raiders who
 * went in through the breach (the director leaves them be)"): the drowned it became is still left to its fight and isn't
 * walked back toward the gate. Fix idea: swap {@code through} (and {@code Ladders}/{@code SandRamps} "over") to the new
 * UUID in {@code ConvertedMobs.swap}.
 */
public class QaSiegeConverts1109dBugTests {
	static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A zombie that went in through the broken gate drowns deep inside the village, by the villager's cell (out of its
	 * sight): the siege's director "leaves them be" once they are through, so the drowned isn't walked back toward the gate
	 * (3 blocks or more, for 300 ticks); it stays where the fight is. (The control, the zombie that doesn't drown, is
	 * QaSiegeConverts1109dGameTests#aRaiderThroughTheGateIsLeftToItsFight.)
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaSiegeConvertsThroughBug1109d"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaSiegeConvertsThroughBug1109d")
	public void aRaiderThatDrownsAfterGettingThroughIsLeftToItsFight(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		mobs[0].discard(); // (no climber: no ladder)
		Mob[] follower = {mobs[1]};
		ThreatData.Siege siege = yard.siege(helper);
		QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
		long[] wasThrough = {-1};
		long[] turned = {-1};
		Vec3[] at = {null};
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (turned[0] < 0) {
				boolean through = Sieges.through(yard.level(), yard.hall(), follower[0].getUUID());
				if (through && wasThrough[0] < 0) {
					wasThrough[0] = tick;
				}
				// Deep in: west of x 14.5 (8 blocks past the point 6 in from the gate where the director counts it through), by the cell, well past where the director counts it through (6 in from the gate).
				if (through && QaSiegeLadderBreach1103dGameTests.inside(helper, follower[0]) && follower[0].getX() - base.getX() < 14.5) {
					follower[0] = QaSiegeConverts1109dGameTests.drown(helper, follower[0]);
					follower[0].setTarget(yard.villager());
					at[0] = follower[0].position();
					turned[0] = tick;
				} else if (tick > 1200) {
					helper.fail("the zombie never got deep inside through the gate (the test would prove nothing): through since " + wasThrough[0] + "; "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower[0]));
				}
				return;
			}
			double back = follower[0].getX() - at[0].x;
			helper.assertTrue(back < 3, "the drowned walked " + String.format(java.util.Locale.ROOT, "%.1f", back)
				+  " blocks back toward the gate " + (tick - turned[0]) + " ticks after it drowned: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower[0]));
			if (tick - turned[0] > 300) {
				helper.assertTrue(QaSiegeLadderBreach1103dGameTests.inside(helper, follower[0]), "the drowned left the yard: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower[0]));
				helper.succeed();
			}
		});
	}
}
