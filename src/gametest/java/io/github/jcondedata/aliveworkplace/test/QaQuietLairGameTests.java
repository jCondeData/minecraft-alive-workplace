package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * QA (lane D, 2026-10-24), ROADMAP 32.21 from the spec: at threat level 0 there are "no raids, lairs, sieges or
 * disasters at all", and on Peaceful "nothing comes". ThreatLevelGameTests proves no new lair makes camp at level 0
 * and that a lair by a village set At peace leaves at the next dawn; these prove a lair already standing when the
 * server turns the threat level to 0, or the world to Peaceful, is gone by the next dawn, and that no new one comes
 * on the days after however the dice fall.
 */
public class QaQuietLairGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A random whose every float is 0: any chance above 0 comes. */
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

	/** A hall (village radius 16) with four villagers and a bandit camp by it; config, clock, difficulty and threats put back at the end. */
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

	/**
	 * A bandit camp stands; the server's threat level goes to 0. By the next dawn it has packed up and left in peace
	 * (its band gone, the chronicle says so, not counted as broken up), and on the four days after no lair comes.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLevelZeroLair"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLevelZeroLair")
	public void aLairStandingWhenTheThreatLevelGoesToZeroLeavesByTheNextDawn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.runAfterDelay(2, () -> {
			at(level, 90, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(Lairs.band(level, lair).size() > 1, "the camp has no band");
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left at level 2");

			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			helper.assertTrue(Threats.quiet(level, hall), "level 0 isn't quiet");
			Lairs.round(level, hall, true, always());
			at(level, 91, 100);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands the dawn after the threat level went to 0");
			helper.assertTrue(Lairs.band(level, lair).isEmpty() && level.getEntity(lair.captain()) == null, "the band stayed: " + Lairs.band(level, lair).size());
			helper.assertTrue(last(entity).equals("The raiders of the Bandit camp packed up and left the village in peace"), "the chronicle: " + last(entity));
			helper.assertTrue(Lairs.resting(level, hall) == 0, "leaving at level 0 counts as broken up");
			for (long day = 91; day < 95; day++) {
				at(level, day, 6000);
				Lairs.round(level, hall, true, always());
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a new lair came at level 0");
			helper.succeed();
		});
	}

	/**
	 * A bandit camp stands; the world is set to Peaceful. By the next dawn no lair is recorded by the village, and on the
	 * four days after none comes however the dice fall.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPeacefulLair"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPeacefulLair")
	public void aLairStandingWhenTheWorldGoesPeacefulIsGoneByTheNextDawnAndNoneComes(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "no lair recorded");
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.assertTrue(level.getDifficulty() == Difficulty.PEACEFUL && Threats.quiet(level, hall), "Peaceful isn't quiet");
			Lairs.round(level, hall, true, always());
			at(level, 96, 100);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a lair still stands the dawn after the world went Peaceful");
			for (long day = 96; day < 100; day++) {
				at(level, day, 6000);
				Lairs.round(level, hall, true, always());
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a new lair came on Peaceful");
			helper.succeed();
		});
	}
}
