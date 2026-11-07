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
	//$ gametest_ticks_batch AREA '2400' '"nurseMinuteBoundary"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "nurseMinuteBoundary")
	public void nurseTreatsAgainExactlyAfterAMinute(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		// Two patients treated on the same tick: one asks a tick early, the other on the minute, so the early ask can
		// never spend the on-time one's treatment.
		ServerPlayer early = helper.makeMockServerPlayerInLevel();
		ServerPlayer onTime = helper.makeMockServerPlayerInLevel();
		Nurses.resetCooldowns();
		for (ServerPlayer player : java.util.List.of(early, onTime)) {
			player.setGameMode(GameType.SURVIVAL);
			player.setHealth(5f);
			Nurses.treat(player, nurse);
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), "not healed: " + player.getHealth());
		}
		long start = helper.getLevel().getGameTime();
		// Her minute is the pace-adjusted one at the treatment (BuilderLevels.delay, as Nurses.treat uses). A busy full
		// suite moves her pace (mood, the village) while the patients wait: B864, a slower pace later refused a patient
		// who had waited the minute he was given. The wait now only ever shortens: ready at min(minute, today's minute).
		int minute = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse);
		helper.runAfterDelay(minute - 1, () -> {
			long waited = helper.getLevel().getGameTime() - start;
			helper.assertTrue(waited == minute - 1, "setup: " + waited + " ticks went by, not " + (minute - 1));
			int now = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse);
			early.setHealth(5f); // a minute of natural regeneration (or a nurse's tending) may have healed him meanwhile
			Nurses.treat(early, nurse);
			boolean due = Math.min(minute, now) <= waited;
			helper.assertTrue((early.getHealth() == early.getMaxHealth()) == due, (due ? "not treated" : "treated")
				+ " a tick before the minute was up: health " + early.getHealth() + " (minute " + minute + " ticks at the treatment, "
				+ now + " now, " + waited + " waited)");
		});
		helper.runAfterDelay(minute, () -> {
			long waited = helper.getLevel().getGameTime() - start;
			onTime.setHealth(5f);
			Nurses.treat(onTime, nurse);
			helper.assertTrue(onTime.getHealth() == onTime.getMaxHealth(), "not treated once the minute was up: health " + onTime.getHealth()
				+ " (minute " + minute + " ticks at the treatment, " + io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse)
				+ " now, " + waited + " waited)");
			helper.succeed();
		});
	}

	/**
	 * B864: a nurse who slows down after treating you (here she falls ill, twice as slow) still treats you once the
	 * minute you were given is up; one who gets faster lets you in sooner.
	 */
	//$ gametest_ticks_batch AREA '2400' '"nurseSlowerAfter"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "nurseSlowerAfter")
	public void nurseSlowingDownNeverStretchesTheMinuteGiven(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager nurse = nurse(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Nurses.resetCooldowns();
		player.setHealth(5f);
		Nurses.treat(player, nurse);
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "not healed: " + player.getHealth());
		long start = helper.getLevel().getGameTime();
		int minute = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse);
		io.github.jcondedata.aliveworkplace.people.Sickness.fallIll(helper.getLevel(), nurse);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(nurse), "setup: the nurse didn't fall ill");
		int slower = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse);
		helper.assertTrue(slower > minute, "setup: falling ill didn't slow her: " + minute + " ticks, then " + slower);
		helper.runAfterDelay(minute, () -> {
			player.setHealth(5f);
			Nurses.treat(player, nurse);
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), "refused after the minute he was given: health " + player.getHealth()
				+ " (minute " + minute + " ticks at the treatment, " + io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse)
				+ " now, " + (helper.getLevel().getGameTime() - start) + " waited)");
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
