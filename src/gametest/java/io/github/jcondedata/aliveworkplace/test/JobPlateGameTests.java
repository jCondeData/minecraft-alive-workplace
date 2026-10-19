package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.JobPlate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * B221: the name plate over a villager the player looks at reads its job and level ("Fisherman, Journeyman"), after its
 * own name when it has one, for our jobs and vanilla's alike; a jobless villager or a nitwit shows nothing new, and the
 * villager's saved name is never changed.
 */
public class JobPlateGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest AREA
	@GameTest(template = AREA)
	public void aWorkingVillagersPlateReadsItsJobAndLevel(GameTestHelper helper) {
		Villager fisher = villager(helper, VillagerProfession.FISHERMAN, 3);
		helper.assertTrue(JobPlate.shows(fisher), "a fisherman looked at gets a plate");
		String plate = JobPlate.plate(fisher, fisher.getDisplayName()).getString();
		helper.assertTrue(plate.equals("Fisherman, Journeyman"), "level-3 fisherman: " + plate);

		Villager builder = villager(helper, ModVillagers.BUILDER, 1);
		String builderPlate = JobPlate.plate(builder, builder.getDisplayName()).getString();
		helper.assertTrue(builderPlate.equals("Builder, Novice"), "our own job: " + builderPlate);

		Villager farmer = villager(helper, VillagerProfession.FARMER, 5);
		String farmerPlate = JobPlate.plate(farmer, farmer.getDisplayName()).getString();
		helper.assertTrue(farmerPlate.equals("Farmer, Master"), "a vanilla job at the top level: " + farmerPlate);
		helper.succeed();
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void aNamedVillagersPlateKeepsItsNameFirst(GameTestHelper helper) {
		Villager dara = villager(helper, VillagerProfession.FISHERMAN, 3);
		dara.setCustomName(Component.literal("Dara"));
		String plate = JobPlate.plate(dara, dara.getDisplayName()).getString();
		helper.assertTrue(plate.equals("Dara — Fisherman, Journeyman"), "named fisherman: " + plate);
		helper.assertTrue(dara.getCustomName() != null && dara.getCustomName().getString().equals("Dara"),
			"the saved name is untouched: " + dara.getCustomName());
		helper.assertTrue(dara.getDisplayName().getString().equals("Dara"), "the display name elsewhere (chat, death) is untouched: "
			+ dara.getDisplayName().getString());
		helper.succeed();
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void aJoblessVillagerShowsNoJob(GameTestHelper helper) {
		Villager jobless = villager(helper, VillagerProfession.NONE, 1);
		helper.assertFalse(JobPlate.shows(jobless), "a jobless villager gets no plate of ours");
		helper.assertTrue(JobPlate.job(jobless) == null, "no job text for a jobless villager");
		String plate = JobPlate.plate(jobless, jobless.getDisplayName()).getString();
		helper.assertTrue(plate.equals("Villager"), "a jobless villager's plate is vanilla's: " + plate);

		Villager named = villager(helper, VillagerProfession.NONE, 1);
		named.setCustomName(Component.literal("Pip"));
		helper.assertTrue(JobPlate.plate(named, named.getDisplayName()).getString().equals("Pip"), "a named jobless villager: just its name");

		Villager nitwit = villager(helper, VillagerProfession.NITWIT, 1);
		helper.assertFalse(JobPlate.shows(nitwit), "a nitwit has no job to show");
		helper.assertTrue(JobPlate.plate(nitwit, nitwit.getDisplayName()).getString().equals("Nitwit"), "a nitwit's plate is vanilla's");

		Pig pig = helper.spawn(EntityType.PIG, 2, 2, 2);
		pig.setNoAi(true);
		helper.assertFalse(JobPlate.shows(pig), "not a villager: no plate of ours");
		helper.assertTrue(JobPlate.plate(pig, pig.getDisplayName()).getString().equals("Pig"), "not a villager: name unchanged");
		helper.succeed();
	}

	private static Villager villager(GameTestHelper helper, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}
}
