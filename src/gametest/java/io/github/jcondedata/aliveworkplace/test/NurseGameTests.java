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
	//$ gametest AREA
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

	/** B851: the wait is rounded up to whole seconds, so a full minute left reads 60, never 61. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void nurseWaitRoundsUpToWholeSeconds(GameTestHelper helper) {
		int[][] cases = {{1200, 60}, {1199, 60}, {1181, 60}, {1180, 59}, {21, 2}, {20, 1}, {1, 1}};
		for (int[] c : cases) {
			helper.assertTrue(Nurses.secondsLeft(c[0]) == c[1], c[0] + " ticks left reads " + Nurses.secondsLeft(c[0]) + " seconds, not " + c[1]);
		}
		helper.succeed();
	}

	/** The minute's edge: one tick before it she treats nothing; on the tick it ends she treats again. */
	//$ gametest_ticks_batch AREA '1300' '"nurseMinuteBoundary"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "nurseMinuteBoundary")
	public void nurseTreatsAgainExactlyAfterAMinute(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Nurses.resetCooldowns();
		player.setHealth(5f);
		Nurses.treat(player, nurse);
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "not healed: " + player.getHealth());
		long start = helper.getLevel().getGameTime();
		// her minute is the pace-adjusted one (BuilderLevels.delay, as Nurses.treat uses), which a busy full suite can move
		int minute = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse);
		player.setHealth(5f);
		helper.runAfterDelay(minute - 1, () -> {
			helper.assertTrue(helper.getLevel().getGameTime() - start == minute - 1, "setup: "
				+ (helper.getLevel().getGameTime() - start) + " ticks went by");
			player.setHealth(5f); // a minute of natural regeneration (or a nurse's tending) may have healed him meanwhile
			Nurses.treat(player, nurse);
			helper.assertTrue(player.getHealth() == 5f, "treated a tick before the minute was up (minute " + minute + " ticks, now "
				+ io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse) + ")");
		});
		helper.runAfterDelay(minute, () -> {
			player.setHealth(5f);
			Nurses.treat(player, nurse);
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), "not treated once the minute was up: " + player.getHealth() + " (minute " + minute + " ticks, now "
				+ io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse) + ")");
			helper.succeed();
		});
	}

	/** Hurt villagers near a nurse at work get better. */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void nurseTendsHurtVillagers(GameTestHelper helper) {
		nurse(helper);
		Villager patient = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		patient.setHealth(4f);
		helper.succeedWhen(() -> helper.assertTrue(patient.getHealth() >= 8f, "patient at " + patient.getHealth()));
	}
}
