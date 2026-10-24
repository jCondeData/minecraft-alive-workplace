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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * Bug test (lane D QA, 2026-10-24; the passing checks are QaQuietLairGameTests). ROADMAP 32.21 from the spec: at threat level 0 there are "no raids, lairs, sieges or
 * disasters at all", and on Peaceful "nothing comes". ThreatLevelGameTests proves no new lair makes camp at level 0
 * and that a lair by a village set At peace leaves at the next dawn; these prove a lair already standing when the
 * server turns the threat level to 0, or the world to Peaceful, is gone by the next dawn, and that no new one comes
 * on the days after however the dice fall. On Peaceful the game takes the camp's chief away at once, and the hall's
 * round then breaks the camp up as if he fell ("Chief ... fell and the bandit camp was broken up", rest days set),
 * where the wiki's Peaceful settings page says level 0 and Peaceful send a standing lair away in peace at dawn.
 */
public class QaPeacefulLairGameTests implements FabricGameTest {
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
	 * A bandit camp stands; the world is set to Peaceful. The world takes its monsters away at once, as the game does on
	 * Peaceful, yet the camp isn't "broken up" by nobody: by the next dawn it has packed up and left in peace (as the
	 * wiki's Peaceful settings page says level 0 and Peaceful do), and on the four days after none comes.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPeacefulLairInPeace"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPeacefulLairInPeace")
	public void aLairStandingWhenTheWorldGoesPeacefulLeavesInPeaceByTheNextDawn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "no lair recorded");
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.assertTrue(level.getDifficulty() == Difficulty.PEACEFUL && Threats.quiet(level, hall), "Peaceful isn't quiet");
			helper.runAfterDelay(10, () -> {
				Lairs.round(level, hall, true, always());
				at(level, 96, 100);
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a lair still stands the dawn after the world went Peaceful");
				helper.assertTrue(last(entity).equals("The raiders of the Bandit camp packed up and left the village in peace") && Lairs.resting(level, hall) == 0,
					"on Peaceful the camp didn't leave in peace: the chronicle says '" + last(entity) + "', resting " + Lairs.resting(level, hall));
				for (long day = 96; day < 100; day++) {
					at(level, day, 6000);
					Lairs.round(level, hall, true, always());
				}
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a new lair came on Peaceful");
				helper.succeed();
			});
		});
	}

	/**
	 * B364's other side: a chief a player brings down just before the world goes Peaceful still fell. The camp is broken
	 * up at once (the chronicle names the player, the village rests), and Peaceful's rounds after don't turn that into a
	 * camp that "left in peace".
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPeacefulLairKilled"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPeacefulLairKilled")
	public void aChiefAPlayerKillsJustBeforePeacefulStillFell(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			Mob chief = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(chief != null && chief.isAlive(), "no chief");
			chief.hurt(level.damageSources().playerAttack(player), 1000f);
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(10, () -> {
				Lairs.round(level, hall, true, always());
				at(level, 96, 100);
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands");
				String line = last(entity);
				helper.assertTrue(line.contains(player.getGameProfile().getName()) && line.contains("broke up"), "the kill isn't remembered: '" + line + "'");
				helper.assertTrue(Lairs.resting(level, hall) > 0, "no rest after the chief fell");
				helper.succeed();
			});
		});
	}

	/**
	 * B364's guard: a chief gone without dying while the world isn't quiet (taken away, despawned) still breaks the camp
	 * up at the next round, as before, so a camp with nobody at its head doesn't linger; and a camp saved and loaded
	 * while the world is Peaceful still leaves in peace once its chief is gone.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPeacefulLairGone"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPeacefulLairGone")
	public void aChiefGoneOutsidePeacefulStillBreaksTheCampUp(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			level.getEntity(lair.captain()).discard();
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) > 0, "a camp without its chief lingers off Peaceful");
			// A second camp, saved and loaded, then Peaceful takes its chief: it leaves in peace, no rest.
			Lairs.forget(level);
			at(level, 100, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no second camp");
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp didn't load back");
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(10, () -> {
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp lingers without its chief on Peaceful");
				helper.assertTrue(last(entity).equals("The raiders of the Bandit camp packed up and left the village in peace") && Lairs.resting(level, hall) == 0,
					"after a load, Peaceful: '" + last(entity) + "', resting " + Lairs.resting(level, hall));
				helper.succeed();
			});
		});
	}
}
