package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;

/**
 * B30: the jobs with their own work packages (miner, postman, the desk jobs, trainer) keep their block when they've been
 * stuck for over a minute, as builders do since B28. Vanilla's walk-back behaviour gave the block up and freed its
 * place, and the worker then took the nearest free block of its kind, maybe another worker's.
 */
public class WorkersKeepTheirBlockGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BLOCK = new BlockPos(3, 2, 3);
	private static final BlockPos STAND = new BlockPos(9, 2, 9);

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void aStuckMinerKeepsTheirBench(GameTestHelper helper) {
		keeps(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
	}

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void aStuckPostmanKeepsTheirDesk(GameTestHelper helper) {
		keeps(helper, ModBlocks.POSTAL_DESK, ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
	}

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void aStuckTutorKeepsTheirDesk(GameTestHelper helper) {
		keeps(helper, ModBlocks.TUTORS_DESK, ModVillagers.TUTORS_DESK_POI, ModVillagers.TUTOR);
	}

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void aStuckTrainerKeepsTheirPost(GameTestHelper helper) {
		keeps(helper, ModBlocks.TRAINING_POST, ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
	}

	/**
	 * A worker of {@code profession} employed at a block 12 blocks off, who hasn't reached a walk target for over a
	 * minute: over a few seconds of work time they keep the block and its place, and head back to it.
	 */
	private static void keeps(GameTestHelper helper, Block block, ResourceKey<PoiType> poi, VillagerProfession profession) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000); // work time
		helper.setBlock(BLOCK, block);
		Villager worker = helper.spawn(EntityType.VILLAGER, STAND);
		Jobs.employ(level, worker, helper.absolutePos(BLOCK), poi, profession);
		worker.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, level.getGameTime() - 1300);
		GlobalPos own = GlobalPos.of(level.dimension(), helper.absolutePos(BLOCK));
		int start = STAND.distManhattan(BLOCK);
		String[] lost = {null};
		helper.onEachTick(() -> {
			Optional<GlobalPos> site = worker.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			if (lost[0] == null && !site.equals(Optional.of(own))) {
				lost[0] = "tick " + helper.getTick() + ": " + site.map(g -> g.pos().toShortString()).orElse("none");
			}
		});
		helper.runAtTickTime(260, () -> {
			helper.assertTrue(lost[0] == null, "the " + profession.name() + " lost their block at " + lost[0]);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(BLOCK)) == 0, "the block's place was freed");
			helper.assertTrue(worker.blockPosition().distManhattan(helper.absolutePos(BLOCK)) < start,
				"the " + profession.name() + " didn't head back: at " + worker.blockPosition().toShortString());
			helper.succeed();
		});
	}
}
