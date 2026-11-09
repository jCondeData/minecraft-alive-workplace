package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-09) for raiders who got over the wall by a siege ladder while the gate stays shut (ROADMAP 32.5;
 * wiki sieges.md: "They climb it, cross the wall's top and drop inside"). The control for
 * QaLadderOverShutGate1109dBugTests: with a villager in its sight once it is inside, the zombie that came over the wall
 * goes for the villager (the director leaves a raider with a foe in sight to its fight) and never goes back out over the
 * wall. Uses QaSiegeConverts1109dGameTests' yard (zombies with the ladders tactic, a Stone Wall, a shut Palisade Gate, a
 * villager shut in a stone cell at x 10, z 7).
 */
public class QaLadderOver1109dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * The follower zombie gets over the wall by the ladder with the gate shut; then the villager's cell is opened on the
	 * yard side. Within 600 ticks a zombie that came over the wall hurts the villager, and the follower never goes back
	 * up the ladder.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverFights1109d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverFights1109d")
	public void aRaiderOverTheWallGoesForAVillagerInItsSight(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (zombies here have no axes)");
			if (opened[0] < 0) {
				if (Ladders.over(yard.level(), yard.hall(), follower.getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, follower)) {
					// The cell's east and south walls come down: the villager stands in the open yard.
					for (int y = 2; y <= 3; y++) {
						for (int z = 6; z <= 8; z++) {
							helper.setBlock(new BlockPos(11, y, z), Blocks.AIR);
						}
						for (int x = 9; x <= 11; x++) {
							helper.setBlock(new BlockPos(x, y, 8), Blocks.AIR);
						}
					}
					opened[0] = tick;
				} else if (tick > 2000) {
					helper.fail("the follower never got over by the ladder and in (the test would prove nothing): follower "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; rungs " + siege.ladders.size());
				}
				return;
			}
			helper.assertTrue(!QaSiegeLadderBreach1103dGameTests.upTheLadder(follower, QaSiegeLadderBreach1103dGameTests.foot(siege)),
				"the zombie went back up the ladder: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			net.minecraft.world.entity.LivingEntity by = yard.villager().getLastHurtByMob();
			if (by != null) {
				helper.assertTrue(Ladders.over(yard.level(), yard.hall(), by.getUUID()), "the villager was hurt by something that didn't come over the wall: " + by);
				helper.succeed();
			} else if (tick - opened[0] > 600) {
				helper.fail("600 ticks after the villager stood in the open, the zombie over the wall hasn't touched it: "
					+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			}
		});
	}
}
