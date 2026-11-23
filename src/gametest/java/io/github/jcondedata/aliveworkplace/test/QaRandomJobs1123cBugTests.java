package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane-c-1123-0653) failing tests from docs/wiki/random-jobs.md: "Put four composters down by four villagers without
 * a job: they don't all become farmers. Each takes a composter and becomes a Farmer, an Orchard Keeper, a Florist or a
 * Composter." The composter's Shift tooltip still tells the player "A villager without a job takes it as a Farmer".
 */
public class QaRandomJobs1123cBugTests implements FabricGameTest {

	/** With {@code randomStationJobs} on, the composter's Shift tooltip doesn't say a jobless villager takes it only as a Farmer. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theComposterTooltipDoesNotPromiseOnlyAFarmer(GameTestHelper helper) {
		helper.assertTrue(RandomJobs.ENABLED, "setup: randomStationJobs is off");
		List<String> lines = Stations.tooltip(new ItemStack(Items.COMPOSTER), true).stream().map(Component::getString).collect(Collectors.toList());
		for (String line : lines) {
			if (line.startsWith("A villager without a job takes it as")) {
				helper.assertTrue(line.contains("Florist") && line.contains("Orchard Keeper"),
					"the composter's tooltip says '" + line + "', but a jobless villager may become any of its jobs: " + lines);
			}
		}
		helper.succeed();
	}
}
