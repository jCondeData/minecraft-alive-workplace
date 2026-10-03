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
 * QA (qa-1003-2134) for B28/B30, from the spec "a worker keeps its block while it stands": every job keeps its block
 * when stuck for over a minute, even with a free block of its kind closer by; and the other side of "while it stands":
 * a worker whose block is broken lets it go.
 */
public class QaB30JobSitesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos OWN = new BlockPos(3, 2, 3);
	private static final BlockPos FREE = new BlockPos(8, 2, 7);
	private static final BlockPos STAND = new BlockPos(9, 2, 9);

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void qaB30AStuckMinerKeepsTheirBenchWithAFreeOneCloser(GameTestHelper helper) {
		keeps(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER, true);
	}

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void qaB30AStuckPostmanKeepsTheirDeskWithAFreeOneCloser(GameTestHelper helper) {
		keeps(helper, ModBlocks.POSTAL_DESK, ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN, true);
	}

	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void qaB30AMinerWhoseBenchIsBrokenLetsItGo(GameTestHelper helper) {
		letsGo(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
	}

	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void qaB30ATrainerWhosePostIsBrokenLetsItGo(GameTestHelper helper) {
		letsGo(helper, ModBlocks.TRAINING_POST, ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
	}

	/**
	 * A worker employed at OWN, 12 blocks off, stuck for over a minute at work time (with, if {@code rival}, a free
	 * block of the same kind 3 blocks from them): they keep OWN and its place, and the free block stays free.
	 */
	private static void keeps(GameTestHelper helper, Block block, ResourceKey<PoiType> poi, VillagerProfession profession, boolean rival) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(OWN, block);
		if (rival) {
			helper.setBlock(FREE, block);
		}
		Villager worker = helper.spawn(EntityType.VILLAGER, STAND);
		Jobs.employ(level, worker, helper.absolutePos(OWN), poi, profession);
		worker.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, level.getGameTime() - 1300);
		GlobalPos own = GlobalPos.of(level.dimension(), helper.absolutePos(OWN));
		String[] lost = {null};
		helper.onEachTick(() -> {
			Optional<GlobalPos> site = worker.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			if (lost[0] == null && !site.equals(Optional.of(own))) {
				lost[0] = "tick " + helper.getTick() + ": " + site.map(g -> g.pos().toShortString()).orElse("none");
			}
		});
		helper.runAtTickTime(260, () -> {
			helper.assertTrue(lost[0] == null, "the " + profession.name() + " lost their block at " + lost[0]);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(OWN)) == 0, "the block's place was freed");
			if (rival) {
				helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(FREE)) > 0,
					"the free block nearby was taken");
			}
			helper.succeed();
		});
	}

	/** A worker standing by their block when it's broken: within a few seconds they no longer hold it. */
	private static void letsGo(GameTestHelper helper, Block block, ResourceKey<PoiType> poi, VillagerProfession profession) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(OWN, block);
		Villager worker = helper.spawn(EntityType.VILLAGER, OWN.east(2));
		Jobs.employ(level, worker, helper.absolutePos(OWN), poi, profession);
		helper.runAtTickTime(20, () -> helper.destroyBlock(OWN));
		helper.runAtTickTime(360, () -> {
			Optional<GlobalPos> site = worker.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			helper.assertTrue(site.isEmpty(), "the " + profession.name() + " still holds their broken block: "
				+ site.map(g -> g.pos().toShortString()).orElse(""));
			helper.succeed();
		});
	}
}
