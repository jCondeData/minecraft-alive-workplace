package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * B124: a worker's level line in its status names the villager's own job ("Apprentice Miner · 10/70 XP…"), not
 * "builder" for every job, and a builder's still says Builder, at a middle level and at the top one.
 */
public class StatusLineJobGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest AREA
	@GameTest(template = AREA)
	public void theLevelLineNamesTheWorkersOwnJob(GameTestHelper helper) {
		Villager miner = worker(helper, ModVillagers.MINER, 2, 10);
		String line = BuilderLevels.describe(miner).getString();
		helper.assertTrue(line.startsWith("Apprentice Miner · 10/70 XP to the next level"), "miner: " + line);
		helper.assertFalse(line.toLowerCase().contains("builder"), "the miner's line names a builder: " + line);

		Villager farmer = worker(helper, VillagerProfession.FARMER, 1, 3);
		String farmerLine = BuilderLevels.describe(farmer).getString();
		helper.assertTrue(farmerLine.startsWith("Novice Farmer · 3/10 XP"), "farmer: " + farmerLine);

		Villager builder = worker(helper, ModVillagers.BUILDER, 2, 10);
		String builderLine = BuilderLevels.describe(builder).getString();
		helper.assertTrue(builderLine.startsWith("Apprentice Builder · 10/70 XP to the next level · "), "builder: " + builderLine);

		Villager master = worker(helper, ModVillagers.MINER, 5, 250);
		String masterLine = BuilderLevels.describe(master).getString();
		helper.assertTrue(masterLine.startsWith("Master Miner (top level) · "), "master miner: " + masterLine);
		helper.succeed();
	}

	private static Villager worker(GameTestHelper helper, VillagerProfession job, int level, int xp) {
		Villager v = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		v.setVillagerXp(xp);
		return v;
	}
}
