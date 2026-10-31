package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane-c-1031-0653): failing tests for real bugs found testing the quest engine (31.2). Not registered on main; they
 * go on a tests/ branch until fixed.
 */
public class QaStoryEngineBug1031Tests implements FabricGameTest {
	/**
	 * job_level's line makes the trade plural by adding an "s": "Fishermans at Apprentice or above: 0 of 2" on every Wonder
	 * that asks for fishermen (4 of the mod's wonder files). Expected: "Fishermen", "Postmen", "Ferrymen".
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '40' '"qaJobLevelPlural"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 40, batch = "qaJobLevelPlural")
	public void jobLevelLineSaysFishermenNotFishermans(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(9, 2, 9), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(9, 2, 9));
		for (String[] job : List.of(new String[] {"minecraft:fisherman", "Fishermen"}, new String[] {"aliveworkplace:postman", "Postmen"},
			new String[] {"aliveworkplace:ferryman", "Ferrymen"}, new String[] {"minecraft:farmer", "Farmers"})) {
			String line = Conditions.parse(JsonParser.parseString("{\"type\": \"job_level\", \"job\": \"" + job[0] + "\", \"level\": 2, \"count\": 2}")
				.getAsJsonObject()).progress(helper.getLevel(), hall).line().getString();
			helper.assertTrue(line.startsWith(job[1] + " at "), "the job_level line for " + job[0] + " reads: " + line);
		}
		helper.succeed();
	}
}
