package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA bugs found from the spec (docs/wiki/work-horn.md, lane-c-1123-0653); these fail until fixed. With one grown
 * villager the village's answer reads "Oakvale answers the horn: 1 villager rush to work!": the verb doesn't follow
 * the count ("1 villager rushes to work!").
 */
public class QaWorkHorn1123cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** One grown villager answers: "<village> answers the horn: 1 villager rushes to work!". */
	//$ gametest_ticks_batch AREA '100' '"qaHornOneBug"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornOneBug")
	public void oneVillagerRushesInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 1000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.removeBlock(hall, false);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			VillageNeeds.forget();
		});
		Villager one = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		one.setNoAi(true);
		helper.runAfterDelay(5, () -> {
			WorkHorn.Result result = WorkHorn.blow(level, null, hall);
			String said = result.message() == null ? "" : result.message().getString();
			helper.assertTrue(result.outcome() == WorkHorn.Outcome.RUSH && said.endsWith(" answers the horn: 1 villager rushes to work!"), "said: '" + said + "'");
			helper.succeed();
		});
	}
}
