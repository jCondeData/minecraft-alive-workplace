package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/** Nurses on a real (headless) server. */
public class NurseGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(2, 2, 2);

	static Villager nurse(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.NURSE_STATION);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(STATION), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		return nurse;
	}

	/** A hurt, poisoned player is healed and cured; asking again right away gets them nothing. */
	@GameTest(template = AREA)
	public void nurseTreatsAPlayer(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setHealth(5f);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
		Nurses.resetCooldowns();
		Nurses.treat(player, nurse);
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "not healed: " + player.getHealth());
		helper.assertFalse(player.hasEffect(MobEffects.POISON), "still poisoned");
		player.setHealth(5f);
		Nurses.treat(player, nurse);
		helper.assertTrue(player.getHealth() == 5f, "treated again straight away");
		helper.succeed();
	}

	/** Hurt villagers near a nurse at work get better. */
	@GameTest(template = AREA, timeoutTicks = 400)
	public void nurseTendsHurtVillagers(GameTestHelper helper) {
		nurse(helper);
		Villager patient = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		patient.setHealth(4f);
		helper.succeedWhen(() -> helper.assertTrue(patient.getHealth() >= 8f, "patient at " + patient.getHealth()));
	}
}
