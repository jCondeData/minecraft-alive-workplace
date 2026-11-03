package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-03): the height edge of B849's rule ("a raider nearer another village with a raid under way, within
 * that village's reach, is that raid's"). A village's reach is 48 blocks up and down. Nightly mutation run 41623267594
 * found that nothing tested it: VillageRaids.own's {@code <= 48} widened to {@code <= 49} survived.
 * <p>
 * Two villages with raids under way, A and B. A raider stands 40 blocks above A's hall (inside A's reach) and nearer B's
 * hall, which is straight above or below it. B 48 blocks away is within B's reach: the raider is B's raid's, and calling
 * off A's raid leaves it. B 49 blocks away is out of B's reach: the raider is A's, and calling off A's raid sends it away.
 * Halls here are only raid records (callOff needs no hall block), so the heights fit in the test area.
 */
public class QaRaidVertical1103dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BANDITS = AliveWorkplace.id("bandits");
	private static final BlockPos RAIDER = new BlockPos(15, 2, 15);

	private static Husk setUp(GameTestHelper helper, int bAbove, BlockPos[] halls) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 12; // reach 44 blocks each way across, 48 up and down
		BlockPos raiderAt = helper.absolutePos(RAIDER);
		halls[0] = raiderAt.offset(-25, -40, -25); // 25, 40 and 25 blocks off: 2850 squared, inside A's reach
		halls[1] = raiderAt.above(bAbove); // 48 or 49 straight above: 2304 or 2401 squared, nearer than A
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			for (BlockPos hall : halls) {
				ThreatData.get(level).end(hall);
				ThreatData.get(level).forgetPast(hall);
				VillageRaids.forget(hall);
			}
			VillageHalls.RADIUS = radius;
		});
		for (BlockPos hall : halls) {
			ThreatData.get(level).begin(new ThreatData.Under(hall, BANDITS, 3, level.getGameTime() - 100));
			helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a raid under way at " + hall.toShortString());
		}
		Husk husk = helper.spawn(EntityType.HUSK, RAIDER);
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		helper.assertTrue(VillageRaids.raiders(level, halls[0]).contains(husk), "setup: the raider 40 blocks above the first village's hall isn't among its raiders");
		helper.assertTrue(raiderAt.distSqr(halls[1]) < raiderAt.distSqr(halls[0]), "setup: the raider isn't nearer the second village's hall");
		return husk;
	}

	/** 49 blocks below the second village's hall: out of its reach, so the raider is the first raid's and goes with it. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidVertical49_1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidVertical49_1103d")
	public void aRaider49BlocksBelowAnotherRaidedHallIsStillThisRaidsAndGoes(GameTestHelper helper) {
		BlockPos[] halls = new BlockPos[2];
		Husk husk = setUp(helper, 49, halls);
		helper.assertTrue(VillageRaids.callOff(helper.getLevel(), halls[0]), "setup: expected the first village's raid called off");
		helper.assertTrue(husk.isRemoved(),
			"a raider 49 blocks below another raided village's hall (out of its reach, which is 48 up and down) stayed when its own village's raid was called off");
		helper.succeed();
	}

	/** 48 blocks below the second village's hall: within its reach and nearer it, so the raider is that raid's and stays. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidVertical48_1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidVertical48_1103d")
	public void aRaider48BlocksBelowAnotherRaidedHallIsThatRaidsAndStays(GameTestHelper helper) {
		BlockPos[] halls = new BlockPos[2];
		Husk husk = setUp(helper, 48, halls);
		helper.assertTrue(VillageRaids.callOff(helper.getLevel(), halls[0]), "setup: expected the first village's raid called off");
		helper.assertFalse(husk.isRemoved(),
			"a raider 48 blocks below another raided village's hall (within its reach, and nearer it) was sent away with the first village's raid");
		helper.assertTrue(ThreatData.get(helper.getLevel()).raid(halls[1]).isPresent(), "the second village's raid ended with the first's");
		helper.succeed();
	}
}
