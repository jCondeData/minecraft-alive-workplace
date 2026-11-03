package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-03), verifying B364 from its text at the boundaries QaPeacefulLairGameTests and the 1101 QA classes
 * leave: "a lair standing when the world is set to Peaceful is 'broken up' as if its captain fell... players near get the
 * camp-broken chat line, the chronicle says '... fell and the bandit camp was broken up', and 4 rest days are set.
 * Expected: Peaceful sends a standing lair away... not counted as broken up."
 * <ul>
 * <li>A plain camp (not forced past a blocklist) on Peaceful, over the round that sends it away and the dawn round after:
 * the player near is told once that it left, never that it broke; the chronicle never says it fell or broke and says it
 * left once; no "A raiders' camp broken" advancement, no broken camp counted for a story, no rest.</li>
 * <li>A chief a player wounded but didn't kill, then taken by Peaceful, isn't credited to the player: the camp leaves in
 * peace and the chronicle doesn't name him.</li>
 * </ul>
 */
public class QaPeacefulLair1103dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;
	private static final String LEFT = "The raiders of the Bandit camp packed up and left the village in peace";

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

	private static List<String> keys(List<Component> seen) {
		return seen.stream().filter(c -> c.getContents() instanceof TranslatableContents).map(c -> ((TranslatableContents) c.getContents()).getKey()).toList();
	}

	/**
	 * A plain camp, Peaceful, the round that sends it away and the dawn round after: the player near is told once that it
	 * left and never that it broke; the chronicle says it left once and never that it fell or broke; no camp-broken
	 * advancement, no broken camp counted for a story, no rest.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1103dPeacefulWhole"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1103dPeacefulWhole")
	public void aPlainCampOnPeacefulIsNeverCountedOrToldAsBrokenUp(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, seen, "qa1103d");
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Vec3 near = helper.absoluteVec(new Vec3(4, 2, 4));
		player.connection.teleport(near.x, near.y, near.z, 0f, 0f);
		AdvancementHolder broke = level.getServer().getAdvancements().get(AliveWorkplace.id("village/camp_broken"));
		helper.assertTrue(broke != null, "no camp_broken advancement to check");
		int brokenBefore = Stories.banditCampsBroken(level, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			int chronicleFrom = entity.chronicle().size();
			int seenFrom = seen.size();
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(10, () -> {
				player.connection.teleport(near.x, near.y, near.z, 0f, 0f);
				Lairs.round(level, hall, true, always());
				at(level, 96, 100);
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands on Peaceful");
				List<String> said = entity.chronicle().subList(chronicleFrom, entity.chronicle().size()).stream().map(e -> e.text().getString()).toList();
				helper.assertTrue(said.stream().noneMatch(s -> s.contains("fell") || s.contains("broke")), "the chronicle counts it broken up: " + said);
				helper.assertTrue(said.stream().filter(LEFT::equals).count() == 1, "the chronicle should say once that it left: " + said);
				List<String> told = keys(seen.subList(seenFrom, seen.size()));
				helper.assertTrue(told.stream().noneMatch(k -> k.contains("broken")), "the player near was told it broke: " + told);
				helper.assertTrue(told.stream().filter("message.aliveworkplace.lair.left"::equals).count() == 1, "the player near should be told once that it left: " + told);
				helper.assertTrue(!player.getAdvancements().getOrStartProgress(broke).isDone(), "the player got 'A raiders' camp broken' for Peaceful");
				helper.assertTrue(Stories.banditCampsBroken(level, hall) == brokenBefore, "a broken camp was counted: " + brokenBefore + " -> " + Stories.banditCampsBroken(level, hall));
				helper.assertTrue(Lairs.resting(level, hall) == 0, "rest days set: " + Lairs.resting(level, hall));
				helper.succeed();
			});
		});
	}

	/**
	 * A chief a player wounded but didn't bring down, then taken by Peaceful, didn't fall to him: the camp leaves in peace,
	 * the chronicle doesn't name the player, no rest.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1103dPeacefulWounded"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1103dPeacefulWounded")
	public void aChiefAPlayerOnlyWoundedThenPeacefulTookIsNotHisKill(GameTestHelper helper) {
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
			chief.hurt(level.damageSources().playerAttack(player), 2f);
			helper.assertTrue(chief.isAlive(), "the chief died of a scratch; the test proves nothing");
			int from = entity.chronicle().size();
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(10, () -> {
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp lingers without its chief on Peaceful");
				List<String> said = entity.chronicle().subList(from, entity.chronicle().size()).stream().map(e -> e.text().getString()).toList();
				String name = player.getGameProfile().getName();
				helper.assertTrue(said.contains(LEFT) && said.stream().noneMatch(s -> s.contains(name) || s.contains("broke") || s.contains("fell")),
					"a wounded chief Peaceful took counted as fallen: " + said);
				helper.assertTrue(Lairs.resting(level, hall) == 0, "rest days set: " + Lairs.resting(level, hall));
				helper.succeed();
			});
		});
	}
}
