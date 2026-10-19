package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Pillager;

/**
 * B208 (the owner's live Cobbleverse server): bandit camps stood empty and were broken up at once, and night attacks
 * never came, because the pack's MobsBeGone refuses pillagers, vindicators, zombies and the rest at {@code addEntity}.
 * Here the world refuses every hostile mob the same way: a camp's chief and band and a night attack's raiders are put
 * in the world anyway, persistent and still there later; with {@code forceThreatSpawns} off no camp or raid is made
 * (nor counted as defeated) and the log says why; on Peaceful nothing is spawned and the players near are told.
 */
public class B208GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;

	/** A hall with four villagers, the world refusing hostile mobs as MobsBeGone does; all of it undone at the end. */
	private static BlockPos village(GameTestHelper helper, boolean force) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		HostileSpawns.forget();
		HostileSpawns.FORCE = force;
		HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof Enemy;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		level.setDayTime(39 * DAY + 1000);
		return hall;
	}

	private static boolean in(ServerLevel level, Entity e) {
		return e != null && e.isAlive() && level.getEntity(e.getUUID()) == e;
	}

	/** A camp made while the world refuses its bandits still has its chief and band, persistent, and stands later. */
	//$ gametest_ticks_batch AREA '200' '"b208Camp"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b208Camp")
	public void b208ACampsBanditsRefusedByAnotherModStillSpawn(GameTestHelper helper) {
		BlockPos hall = village(helper, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			// The refusal is real: a pillager added the usual way is refused and gone.
			Pillager plain = EntityType.PILLAGER.create(level);
			plain.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 2, 8)));
			helper.assertTrue(!level.addFreshEntity(plain) && plain.isRemoved(), "the world didn't refuse a pillager as MobsBeGone does");

			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			Entity chief = level.getEntity(lair.captain());
			helper.assertTrue(chief instanceof Mob m && in(level, m) && m.isPersistenceRequired(), "the chief isn't in the world: " + chief);
			List<Mob> band = new ArrayList<>(Lairs.band(level, lair));
			band.removeIf(m -> m.getUUID().equals(lair.captain()));
			helper.assertTrue(band.size() == 6 && band.stream().allMatch(m -> in(level, m) && m.isPersistenceRequired()), "the band: " + band);
			helper.assertTrue(HostileSpawns.lastWarning().contains("MobsBeGone") && HostileSpawns.lastWarning().contains("forceThreatSpawns"),
				"no clear log line: " + HostileSpawns.lastWarning());
			helper.runAfterDelay(100, () -> {
				Lairs.round(level, hall, false);
				helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp was broken up with its chief alive");
				helper.assertTrue(in(level, level.getEntity(lair.captain())), "the chief is gone after 100 ticks");
				helper.assertTrue(band.stream().allMatch(m -> in(level, m)), "the band is gone after 100 ticks");
				helper.succeed();
			});
		});
	}

	/** A night attack while the world refuses its monsters still comes: its raiders are in the world and stay. */
	//$ gametest_ticks_batch AREA '200' '"b208Night"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b208Night")
	public void b208ANightAttacksMonstersRefusedByAnotherModStillCome(GameTestHelper helper) {
		BlockPos hall = village(helper, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			level.setDayTime(39 * DAY + 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == raid.raiders() && raiders.stream().allMatch(m -> in(level, m) && m.isPersistenceRequired()),
				"the raiders: " + raiders + " of " + raid.raiders());
			helper.runAfterDelay(100, () -> {
				helper.assertTrue(raiders.stream().filter(m -> in(level, m)).count() == raiders.size(), "raiders gone after 100 ticks");
				helper.succeed();
			});
		});
	}

	/** With forceThreatSpawns off, refused bandits make no camp (and none is broken up), no raid comes, and the log says why. */
	//$ gametest_ticks_batch AREA '100' '"b208Off"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b208Off")
	public void b208WithForcingOffARefusedCampIsNeitherMadeNorDefeated(GameTestHelper helper) {
		BlockPos hall = village(helper, false);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) == null, "a camp was made without its chief");
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == 0, "a camp was recorded, or broken up");
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.startsWith("Could not spawn minecraft:") && said.contains("forceThreatSpawns is off"), "the log line: " + said);
			level.setDayTime(39 * DAY + 15000);
			helper.assertTrue(VillageRaids.start(level, hall, 8, 0) == null && VillageRaids.raiders(level, hall).isEmpty(), "a raid of nobody began");
			helper.succeed();
		});
	}

	/** On Peaceful a foe isn't spawned at all: the log says why and a player near is told, once a day. */
	//$ gametest_ticks_batch AREA '100' '"b208Peaceful"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b208Peaceful")
	public void b208OnPeacefulPlayersNearAreToldWhyNothingComes(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		HostileSpawns.forget();
		Leftovers.after(helper, HostileSpawns::forget);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, seen, "b208");
		player.teleportTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4, 2, 4)).x, helper.absolutePos(new BlockPos(4, 2, 4)).getY(),
			helper.absoluteVec(new net.minecraft.world.phys.Vec3(4, 2, 4)).z);
		helper.runAfterDelay(2, () -> {
			for (int i = 0; i < 2; i++) {
				Pillager pillager = EntityType.PILLAGER.create(level);
				pillager.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(6, 2, 6)));
				helper.assertTrue(!HostileSpawns.add(level, pillager, "test camp", Difficulty.PEACEFUL) && pillager.isRemoved(), "a pillager spawned on Peaceful");
			}
			helper.assertTrue(HostileSpawns.lastWarning().contains("Peaceful"), "the log line: " + HostileSpawns.lastWarning());
			long told = seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.threat.peaceful")).count();
			helper.assertTrue(told == 1, "the player was told " + told + " times: " + seen);
			helper.succeed();
		});
	}
}
