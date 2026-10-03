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
 * QA (qa-1003-2134): B28/B30's cause in the jobs B30 didn't name. A nurse, shopkeeper or ferryman stuck for over a
 * minute must keep their block (they still use vanilla SetWalkTargetFromBlockMemory, which gives it up).
 */
public class QaStuckJobsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos OWN = new BlockPos(3, 2, 3);
	private static final BlockPos FREE = new BlockPos(8, 2, 7);
	private static final BlockPos STAND = new BlockPos(9, 2, 9);

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void qaB30AStuckNurseKeepsTheirStation(GameTestHelper helper) {
		keeps(helper, ModBlocks.NURSE_STATION, ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE, false);
	}

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void qaB30AStuckShopkeeperKeepsTheirCounter(GameTestHelper helper) {
		keeps(helper, ModBlocks.SHOP_COUNTER, ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER, false);
	}

	//$ gametest_ticks AREA '300'
	@GameTest(template = AREA, timeoutTicks = 300)
	public void qaB30AStuckFerrymanKeepsTheirPost(GameTestHelper helper) {
		keeps(helper, ModBlocks.TRAVEL_POST, ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN, false);
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
}
