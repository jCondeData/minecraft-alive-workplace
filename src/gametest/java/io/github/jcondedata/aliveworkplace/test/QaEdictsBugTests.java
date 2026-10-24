package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA tests that fail because of a mod bug (filed in ROADMAP Bugs by lane-c-1024-0653). Not registered: register it
 * (src/gametest/resources/fabric.mod.json) once the bug is fixed, or move its tests into QaEdictsGameTests.
 */
public class QaEdictsBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** 30.17: a full Village's refusal counts its one guild in the singular ("may have 1 guild", not "1 guilds"). */
	//$ gametest_ticks_batch AREA '100' '"qaGuildOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuildOne")
	public void qaAFullVillageSaysOneGuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos pos = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			helper.getLevel().removeBlock(pos, false);
			VillageHalls.RADIUS = radius;
			Guilds.forget();
			CivicEffects.forget();
			VillageNeeds.forget();
		});
		Villager builder = master(helper, new BlockPos(4, 2, 4), "Dara", ModVillagers.BUILDER);
		Villager miner = master(helper, new BlockPos(6, 2, 4), "Bram", ModVillagers.MINER);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(pos);
			hall.setGuilds(List.of());
			hall.setRank(VillageRanks.Rank.VILLAGE);
			Guilds.forget();
			String village = VillageHalls.name(level, pos).getString();
			helper.assertTrue(Guilds.grant(level, builder).outcome() == Guilds.Outcome.GRANTED, "a Village's first");
			Guilds.Offer full = Guilds.grant(level, miner);
			helper.assertTrue(full.outcome() == Guilds.Outcome.FULL && full.message().getString().equals(village + " is a Village and may have 1 guild; it has them all."),
				"said: " + full.message().getString());
			helper.succeed();
		});
	}

	private static Villager master(GameTestHelper helper, BlockPos at, String name, net.minecraft.world.entity.npc.VillagerProfession job) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setCustomName(Component.literal(name));
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(5));
		return villager;
	}
}
