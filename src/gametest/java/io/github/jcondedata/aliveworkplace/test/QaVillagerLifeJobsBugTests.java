package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** QA bug tests (lane-c-1024-0953), fixed: B380 (strangers grieving for each other over a shared parent's name). */
public class QaVillagerLifeJobsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * 36.16 "lower mood/work for their partner and family": Mira and Odo are of two families whose mothers were two
	 * different, unnamed Farmers (as Families.born records them with villagerNames off: display name "Farmer", each with
	 * her own id). Mira dies: Odo is no kin of hers and should not grieve.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaBugMourningSameNamedMothers"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaBugMourningSameNamedMothers")
	public void qaBugStrangersWhoseMothersShareANameDoNotGrieve(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager mira = person(helper, new BlockPos(5, 2, 6), "Mira");
		Villager odo = person(helper, new BlockPos(8, 2, 6), "Odo");
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL))).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(mira, today - 30);
			LifeStages.setAdultSince(odo, today - 30);
			ModAttachments.PARENTS.set(mira, new Families.Parents(Component.literal("Farmer"), Component.literal("Mason"), "farmer", "mason", true,
				false, false, UUID.randomUUID(), UUID.randomUUID()));
			ModAttachments.PARENTS.set(odo, new Families.Parents(Component.literal("Farmer"), Component.literal("Cleric"), "farmer", "cleric", true,
				false, false, UUID.randomUUID(), UUID.randomUUID()));
			mira.kill();
			helper.assertTrue(!Mourning.grieving(odo, today) && Mourning.pace(odo) == 1f,
				"Odo grieves Mira (" + ModAttachments.GRIEF.get(odo) + "): two different mothers who share a name make them family");
			helper.succeed();
		});
	}

	/**
	 * B380, the other side: with villagerNames off every parent is recorded as "Farmer", and the ids still find the real
	 * family. Mira dies: her mother (a Farmer, by id), her brother Pip (the same mother's id) and her daughter Kit (Mira's
	 * id) grieve; Odo, whose mother is another Farmer, does not, and nor does Ned, an old save's child of "Farmer" by
	 * name only (a job title is no one's name).
	 */
	//$ gametest_ticks_batch BIG '100' '"qaBugMourningFamilyById"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaBugMourningFamilyById")
	public void qaBugRealFamilyGrievesByIdWhenEveryParentIsAFarmer(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager mira = person(helper, new BlockPos(5, 2, 6), "Mira");
		Villager mother = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 6)); // unnamed: shown as "Farmer"
		mother.setNoAi(true);
		mother.setVillagerData(mother.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		Villager pip = person(helper, new BlockPos(9, 2, 6), "Pip");
		Villager kit = person(helper, new BlockPos(5, 2, 8), "Kit");
		Villager odo = person(helper, new BlockPos(7, 2, 8), "Odo");
		Villager ned = person(helper, new BlockPos(9, 2, 8), "Ned");
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL))).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
			long today = Chronicle.day(level);
			for (Villager v : List.of(mira, mother, pip, kit, odo, ned)) {
				LifeStages.setAdultSince(v, today - 30);
			}
			Component farmer = Component.translatable("entity.minecraft.villager.farmer");
			helper.assertTrue(mother.getDisplayName().getString().equals(farmer.getString()), "the mother is shown as " + mother.getDisplayName().getString());
			ModAttachments.PARENTS.set(mira, new Families.Parents(mother.getDisplayName(), farmer, "farmer", "farmer", true, false, false,
				mother.getUUID(), UUID.randomUUID()));
			ModAttachments.PARENTS.set(pip, new Families.Parents(mother.getDisplayName(), farmer, "farmer", "farmer", true, false, false,
				mother.getUUID(), UUID.randomUUID()));
			ModAttachments.PARENTS.set(kit, new Families.Parents(mira.getDisplayName(), farmer, "farmer", "farmer", true, false, false,
				mira.getUUID(), UUID.randomUUID()));
			ModAttachments.PARENTS.set(odo, new Families.Parents(farmer, farmer, "farmer", "farmer", true, false, false,
				UUID.randomUUID(), UUID.randomUUID()));
			ModAttachments.PARENTS.set(ned, new Families.Parents(farmer, farmer, "farmer", "farmer", true));
			mira.kill();
			for (Villager v : List.of(mother, pip, kit)) {
				String name = v.getName().getString();
				helper.assertTrue(Mourning.grieving(v, today) && Mourning.pace(v) == Mourning.PACE, name + " doesn't grieve Mira (" + ModAttachments.GRIEF.get(v) + ")");
			}
			helper.assertTrue(!Mourning.grieving(odo, today) && Mourning.pace(odo) == 1f, "Odo grieves Mira: his mother is another Farmer");
			helper.assertTrue(!Mourning.grieving(ned, today) && Mourning.pace(ned) == 1f, "Ned grieves Mira: \"Farmer\" by name only is no kin");
			helper.succeed();
		});
	}

	private static Villager person(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		return v;
	}

}
