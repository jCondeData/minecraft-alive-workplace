package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane qa-lane-c-1108-0352) for 36.17's rule, from docs/wiki/random-jobs.md: "each job gets the weight 1 / (1 + n),
 * where n is how many villagers within 48 blocks of the block already do it". Florists standing 48 blocks from a
 * composter (east, west, south and north) are counted; ones 49 blocks off are not; a jobless villager and the one
 * drawing are never counted. Villagers are NoAI so they stay where they're put, and are taken away at the end.
 */
public class QaRandomJobsReach1108cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BLOCK = new BlockPos(8, 2, 8);

	/** Four Florists exactly 48 blocks off along each side count 4; four more at 49 add nothing. */
	//$ gametest_ticks_batch AREA '200' '"qaRandomJobsReach48"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRandomJobsReach48")
	public void floristsFortyEightBlocksOffCountAndFortyNineDoNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Villager> spawned = new ArrayList<>();
		Leftovers.after(helper, () -> spawned.forEach(Villager::discard));
		BlockPos at = helper.absolutePos(BLOCK);
		VillagerProfession florist = ModVillagers.FLORIST;
		List<VillagerProfession> jobs = List.of(VillagerProfession.FARMER, florist);
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		// The test areas lie in the positive direction, so the chunks 48 blocks west and north aren't loaded with their
		// entities: force them for the test.
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		java.util.Set<net.minecraft.world.level.ChunkPos> forced = new java.util.HashSet<>();
		for (int[] side : sides) {
			for (int d : new int[] {RandomJobs.RADIUS, RandomJobs.RADIUS + 1}) {
				net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(at.offset(side[0] * d, 0, side[1] * d));
				if (forced.add(chunk) && level.setChunkForced(chunk.x, chunk.z, true)) {
					Leftovers.after(helper, () -> level.setChunkForced(chunk.x, chunk.z, false));
				}
			}
		}
		boolean[] started = {false};
		helper.onEachTick(() -> {
			if (started[0] || !forced.stream().allMatch(c -> level.areEntitiesLoaded(c.toLong()))) {
				return; // the forced chunks load over a few ticks
			}
			started[0] = true;
			for (int[] side : sides) {
				spawned.add(still(helper, at.offset(side[0] * RandomJobs.RADIUS, 0, side[1] * RandomJobs.RADIUS), florist));
			}
			Villager drawer = still(helper, at.offset(1, 0, 1), VillagerProfession.FARMER);
			spawned.add(drawer);
			spawned.add(still(helper, at.offset(2, 0, 1), VillagerProfession.NONE));
			helper.runAfterDelay(1, () -> {
				Map<VillagerProfession, Integer> near = RandomJobs.counts(level, at, drawer, jobs);
				helper.assertTrue(near.getOrDefault(florist, 0) == 4, "Florists 48 blocks off on each side counted: " + near.getOrDefault(florist, 0) + " (expected 4); "
					+ spawned.stream().map(v -> v.blockPosition().subtract(at) + (level.getEntity(v.getUUID()) == null ? " not in the level" : "")).toList());
				helper.assertTrue(near.getOrDefault(VillagerProfession.FARMER, 0) == 0, "the drawing villager counted himself: " + near);
				for (int[] side : sides) {
					spawned.add(still(helper, at.offset(side[0] * (RandomJobs.RADIUS + 1), 0, side[1] * (RandomJobs.RADIUS + 1)), florist));
				}
				helper.runAfterDelay(1, () -> {
					Map<VillagerProfession, Integer> again = RandomJobs.counts(level, at, drawer, jobs);
					long seen = spawned.stream().filter(v -> level.getEntity(v.getUUID()) != null).count();
					helper.assertTrue(seen == spawned.size(), "setup: only " + seen + " of " + spawned.size() + " villagers are in the level");
					helper.assertTrue(again.getOrDefault(florist, 0) == 4, "Florists 49 blocks off were counted too: " + again.getOrDefault(florist, 0) + " (expected 4)");
					spawned.forEach(Villager::discard);
					helper.succeed();
				});
			});
		});
	}

	/** A NoAI villager of {@code job} standing on the middle of the block {@code abs} (an absolute position). */
	private static Villager still(GameTestHelper helper, BlockPos abs, VillagerProfession job) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		Vec3 pos = Vec3.atBottomCenterOf(abs);
		v.moveTo(pos.x, pos.y, pos.z, 0, 0);
		v.setNoAi(true);
		v.setNoGravity(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(job == VillagerProfession.NONE ? 1 : 2));
		helper.getLevel().addFreshEntity(v);
		return v;
	}
}
