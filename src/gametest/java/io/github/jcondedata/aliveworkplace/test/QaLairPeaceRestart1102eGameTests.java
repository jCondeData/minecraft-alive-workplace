package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * QA (lane E, saves, 2026-11-02; B767 across a restart): "a chief that Peaceful took away is never counted as fallen".
 * The fix remembers which chiefs Peaceful took ({@code taken_by_peace} in the lairs' saved file). A server stopped after
 * a moment of Peaceful and started again on Normal before the hall's next round (every 30 s) must still let the camp
 * leave in peace, not break it up as if its chief fell (no "fell" in the chronicle, no rest days). The restart is the
 * lairs' file saved and read back ({@link Lairs.Data#roundTrip}), as the server does.
 */
public class QaLairPeaceRestart1102eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final long DAY = 24000;
	private static final String LEFT = "packed up and left the village in peace";

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static RandomSource always() {
		return new LegacyRandomSource(7L) {
			@Override
			public float nextFloat() {
				return 0f;
			}

			@Override
			public double nextDouble() {
				return 0d;
			}
		};
	}

	/** A hall (village radius 16) with four villagers; config, clock, difficulty and threats put back at the end. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		Difficulty difficulty = level.getDifficulty();
		VillageHalls.RADIUS = 16;
		Lairs.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(difficulty, true);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Lairs.forget(level);
			BanditCamps.forget(level);
			VillageRaids.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	private static String last(VillageHallBlockEntity entity) {
		List<Chronicle.Entry> chronicle = entity.chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	/** Peaceful takes the chief, the world goes back to Normal, the server restarts, then the hall's round. */
	private static void blipThenRestart(GameTestHelper helper, BlockPos hall, UUID captain, String what) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(level.getEntity(captain) != null, what + " has no chief standing");
		level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(level.getEntity(captain) == null, "Peaceful didn't take the chief away; the test proves nothing");
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			Lairs.Data.get(level).roundTrip(level.registryAccess()); // the server stops and starts again before the hall's round
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), what + " lingers without its chief after the restart");
			helper.assertTrue(last(entity).contains(LEFT) && Lairs.resting(level, hall) == 0, "after a restart, a moment of Peaceful counted "
				+ what + "'s chief as fallen: the chronicle reads '" + last(entity) + "', resting " + Lairs.resting(level, hall));
			helper.succeed();
		});
	}

	/** The bandit camp the bug was found with. */
	//$ gametest_ticks_batch AREA '80' '"qa1102ePeaceRestartCamp"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1102ePeaceRestartCamp")
	public void aBanditChiefPeacefulTookIsNoKillAfterARestart(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no bandit camp");
			blipThenRestart(helper, hall, Lairs.near(level, hall).orElseThrow().captain(), "the bandit camp");
		});
	}

	/** A raider lair (the desert raiders' camp, a husk at its head). */
	//$ gametest_ticks_batch AREA '80' '"qa1102ePeaceRestartDesert"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1102ePeaceRestartDesert")
	public void aRaiderCaptainPeacefulTookIsNoKillAfterARestart(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
			helper.assertTrue(lair != null && Lairs.near(level, hall).isPresent(), "no desert lair");
			blipThenRestart(helper, hall, lair.captain(), "the desert raiders' lair");
		});
	}
}
