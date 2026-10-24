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

/** QA bug tests (lane-c-1024-0953), failing on purpose: not registered, not committed. */
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

	private static Villager person(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		return v;
	}

}
