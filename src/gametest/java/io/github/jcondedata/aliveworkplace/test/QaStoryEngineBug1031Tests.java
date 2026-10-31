package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane-c-1031-0653): tests for real bugs found testing the quest engine (31.2), registered once fixed (B707).
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

	/** B707: every trade of vanilla's and the mod's own has a plural, so no requirement line falls back to the singular. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyTradeHasAPlural(GameTestHelper helper) {
		for (ResourceLocation id : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
			boolean ours = "minecraft".equals(id.getNamespace()) || AliveWorkplace.MOD_ID.equals(id.getNamespace());
			if (ours && !"none".equals(id.getPath())) {
				helper.assertTrue(Words.professionPlural(id) != null, "no plural.aliveworkplace.profession key for " + id);
				helper.assertFalse(Language.getInstance().getOrDefault("plural.aliveworkplace.profession." + id.getNamespace() + "." + id.getPath())
					.endsWith("mans"), "the plural for " + id + " ends in -mans");
			}
		}
		helper.succeed();
	}
}
