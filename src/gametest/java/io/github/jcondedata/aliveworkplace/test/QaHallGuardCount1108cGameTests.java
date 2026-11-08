package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane c, 2026-11-08) for the hall's count of the village's guards: the number behind the hall's "Guards: N"
 * button, its "Nobody guards the village" warning, the gates, the raids and warnings, and the Steward's guard check
 * (32.x, 27.x). Only villagers working as Guards count; a mason and a farmer at work don't. Written for a survivor of a
 * local mutation run on VillageHalls.census (line 94's {@code job == ModVillagers.GUARD} turned into {@code !=}: the
 * suite's guard and hall tests all passed with every other worker counted as a guard).
 */
public class QaHallGuardCount1108cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true); // they stay where they're put, and keep their jobs
		return v;
	}

	/** A guard, a mason and a farmer at work and one jobless villager: the hall counts one guard; with the guard let go, none. */
	//$ gametest_ticks_batch AREA '200' '"qaHallGuardCount1108c"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallGuardCount1108c")
	public void onlyWorkingGuardsCountAsTheVillagesGuards(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos post = new BlockPos(8, 2, 8);
		BlockPos cutter = new BlockPos(12, 2, 8);
		BlockPos composter = new BlockPos(16, 2, 8);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager guard = villager(helper, 8, 10);
		Villager mason = villager(helper, 12, 10);
		Villager farmer = villager(helper, 16, 10);
		Villager idle = villager(helper, 12, 20);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			Jobs.employ(level, mason, helper.absolutePos(cutter), PoiTypes.MASON, VillagerProfession.MASON);
			Jobs.employ(level, farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
			VillageHalls.Census census = VillageHalls.census(level, hall);
			helper.assertTrue(census.workers().size() == 3 && census.jobless().size() == 1,
				"setup: expected 3 at work and 1 jobless, the hall counts " + census.workers().size() + " and " + census.jobless().size());
			helper.assertTrue(census.guards() == 1, "expected 1 guard (a guard, a mason and a farmer at work), the hall counts " + census.guards());
			// The guard let go of his post: nobody guards the village, though the mason and the farmer still work.
			guard.setVillagerData(guard.getVillagerData().setProfession(VillagerProfession.NONE));
			guard.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
			VillageHalls.Census after = VillageHalls.census(level, hall);
			helper.assertTrue(after.guards() == 0 && after.workers().size() == 2,
				"expected no guard and 2 at work once the guard has no job, the hall counts " + after.guards() + " guards and " + after.workers().size() + " at work");
			helper.succeed();
		});
	}
}
