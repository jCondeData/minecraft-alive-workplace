package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-03), verifying B849 (a village's raid called off sends away only its own raiders: "a raider nearer
 * another village with a raid under way is that raid's") at the boundaries its test doesn't reach, two raided villages
 * side by side (halls 25 blocks apart each way, the reach the hall radius 12 + 32):
 * the other side of the rule: a raider nearer a village with no raid under way is still this raid's and goes. The
 * boundaries that fail (the hall's own round) are in QaRaidNeighbours1103dBugTests (branch tests/raid-neighbours); this class holds their
 * shared setup.
 */
public class QaRaidNeighbours1103dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	static final ResourceLocation BANDITS = AliveWorkplace.id("bandits");
	static final BlockPos REL_A = new BlockPos(2, 2, 2);
	static final BlockPos REL_B = new BlockPos(27, 2, 27);

	/** Clears leftovers; puts back the day, the radius and the halls' raids afterwards. */
	static void setUp(GameTestHelper helper, long dayTime, BlockPos... halls) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, box.inflate(4), e -> true).forEach(ItemEntity::discard);
			for (BlockPos rel : halls) {
				BlockPos hall = helper.absolutePos(rel);
				ThreatData.get(level).end(hall);
				ThreatData.get(level).forgetPast(hall);
				VillageRaids.forget(hall);
			}
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		VillageHalls.RADIUS = 12;
		level.setDayTime((level.getDayTime() / 24000) * 24000 + dayTime);
		for (BlockPos rel : halls) {
			helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
			ThreatData.get(level).forgetPast(helper.absolutePos(rel));
		}
		helper.assertTrue(Threats.get(DESERT).isPresent() && Threats.get(BANDITS).isPresent(),
			"setup: expected desert_raiders.json and bandits.json loaded");
	}

	static BlockPos raid(GameTestHelper helper, BlockPos rel, ResourceLocation culture, long ago) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(rel);
		ThreatData.get(level).begin(new ThreatData.Under(hall, culture, 3, level.getGameTime() - ago));
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a raid under way at " + rel.toShortString());
		return hall;
	}

	static Husk raider(GameTestHelper helper, BlockPos at) {
		Husk husk = helper.spawn(EntityType.HUSK, at);
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		return husk;
	}

	/**
	 * The other side of B849's rule: the second village has a hall but no raid. A raider 3 blocks from its hall, within
	 * the first's reach, is the first raid's (no other raid under way claims it), so calling off the first raid sends it
	 * away too.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidNeighboursNoRaid1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidNeighboursNoRaid1103d")
	public void aRaiderNearerAVillageWithNoRaidIsStillThisRaidsAndGoesWhenCalledOff(GameTestHelper helper) {
		setUp(helper, 18000, REL_A, REL_B);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, REL_A, BANDITS, 100);
		helper.assertTrue(ThreatData.get(level).raid(helper.absolutePos(REL_B)).isEmpty(), "setup: expected no raid on the second village");
		Husk near = raider(helper, new BlockPos(6, 2, 6));
		Husk far = raider(helper, new BlockPos(24, 2, 24));
		helper.assertTrue(VillageRaids.callOff(level, hallA), "setup: expected the first village's raid called off");
		helper.assertTrue(near.isRemoved(), "the raider by the first village's hall stayed after its raid was called off");
		helper.assertTrue(far.isRemoved(),
			"a raider within the first village's reach, nearer a village with no raid under way, stayed after the first village's raid was called off");
		helper.succeed();
	}
}
