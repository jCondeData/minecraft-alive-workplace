package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;

/**
 * QA (lane-d-1104-1609), B208 and B216 from the spec: a night attack while another mod (MobsBeGone, the pack's mob
 * blocklist) refuses monster spawns. Covers what B208GameTests and QaThreatSpawnsPeace1101GameTests leave out: only some
 * monsters refused with forcing on, the refusal lifted mid-night, forcing switched off mid-night, a refusal only out at
 * the village edge, the forced raiders' saved data (a save and reload), and the log line naming the night attack.
 */
public class QaNightRefusals1104d2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final long DAY = 24000;

	/** A hall (radius 16) with four villagers, the world refusing {@code refused} as a blocklist would; undone at the end. */
	private static BlockPos village(GameTestHelper helper, Predicate<Entity> refused, boolean force) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		HostileSpawns.forget();
		HostileSpawns.FORCE = force;
		HostileSpawns.REFUSED_FOR_TESTS = refused;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			VillageRaids.forget();
			Lairs.forget(level);
			BanditCamps.forget(level);
			ThreatData.get(level).end(hall);
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

	private static String describe(ServerLevel level, List<Mob> mobs) {
		StringBuilder s = new StringBuilder("[");
		for (Mob m : mobs) {
			s.append(BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath()).append('@').append(m.blockPosition().toShortString())
				.append(in(level, m) ? " in" : " OUT").append(m.isRemoved() ? " removed=" + m.getRemovalReason() : "")
				.append(" persistent=").append(m.isPersistenceRequired()).append("; ");
		}
		return s.append(']').toString();
	}

	/** Night falls and the plain monsters come, with a fixed random. */
	private static VillageRaids.Raid night(GameTestHelper helper, BlockPos hall, int villagers, long seed) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(39 * DAY + 15000);
		return VillageRaids.start(level, hall, villagers, 0, Double.NaN, Threats.on(Threats.MONSTERS).orElseThrow(), RandomSource.create(seed));
	}

	/**
	 * Only the zombies refused, forcing on: the zombies are placed anyway beside the skeletons and spiders that spawned the
	 * usual way, the raid counts every one, and the log says the zombie was placed directly (not "Could not spawn").
	 */
	//$ gametest_ticks_batch AREA '120' '"qaNight1104d2Some"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaNight1104d2Some")
	public void someMonstersRefusedWithForcingOnAllCome(GameTestHelper helper) {
		BlockPos hall = village(helper, e -> e instanceof Zombie, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageRaids.Raid raid = night(helper, hall, 12, 1101);
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == raid.raiders() && raiders.stream().allMatch(m -> in(level, m) && m.isPersistenceRequired()),
				"the raid counts " + raid.raiders() + ": " + describe(level, raiders));
			helper.assertTrue(raiders.stream().anyMatch(m -> m instanceof Zombie), "no zombie came although forcing is on: " + describe(level, raiders));
			helper.assertTrue(raiders.stream().anyMatch(m -> !(m instanceof Zombie)), "only zombies came: " + describe(level, raiders));
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.contains("minecraft:zombie") && said.contains("placed it directly") && !said.startsWith("Could not spawn"),
				"the log line: " + said);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(raiders.stream().allMatch(m -> in(level, m)), "raiders gone after 60 ticks: " + describe(level, raiders));
				helper.succeed();
			});
		});
	}

	/** The refusal lifted mid-night (the other mod's blocklist emptied): the raiders already placed stay, the raid goes on. */
	//$ gametest_ticks_batch AREA '140' '"qaNight1104d2Lifted"'
	@GameTest(template = AREA, timeoutTicks = 140, batch = "qaNight1104d2Lifted")
	public void theRefusalLiftedMidNightLeavesTheRaidAsItWas(GameTestHelper helper) {
		BlockPos hall = village(helper, e -> e instanceof Enemy, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageRaids.Raid raid = night(helper, hall, 8, 1104);
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == raid.raiders(), "the raid counts " + raid.raiders() + ": " + describe(level, raiders));
			HostileSpawns.REFUSED_FOR_TESTS = null;
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(raiders.stream().allMatch(m -> in(level, m)), "raiders gone after the refusal was lifted: " + describe(level, raiders));
				helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid ended when the refusal was lifted");
				helper.assertTrue(VillageRaids.raiders(level, hall).size() == raid.raiders(), "the raid's raiders changed: "
					+ describe(level, VillageRaids.raiders(level, hall)));
				helper.succeed();
			});
		});
	}

	/** forceThreatSpawns switched off mid-night: the raiders it already placed are not taken out of the world. */
	//$ gametest_ticks_batch AREA '140' '"qaNight1104d2SwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 140, batch = "qaNight1104d2SwitchOff")
	public void forcingSwitchedOffMidNightKeepsThoseAlreadyPlaced(GameTestHelper helper) {
		BlockPos hall = village(helper, e -> e instanceof Enemy, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageRaids.Raid raid = night(helper, hall, 8, 2081);
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			HostileSpawns.FORCE = false;
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(raiders.size() == raid.raiders() && raiders.stream().allMatch(m -> in(level, m)),
					"raiders gone after forcing was switched off: " + describe(level, raiders));
				helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid ended when forcing was switched off");
				helper.succeed();
			});
		});
	}

	/**
	 * The world refuses monsters only out past the village's edge (16 blocks from the hall), where raiders gather: those
	 * are placed anyway, and every raider counted is in the world.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaNight1104d2Edge"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaNight1104d2Edge")
	public void monstersRefusedOnlyPastTheVillageEdgeStillCome(GameTestHelper helper) {
		BlockPos hallAbs = helper.absolutePos(HALL);
		BlockPos hall = village(helper, e -> e instanceof Enemy && e.position().distanceToSqr(hallAbs.getCenter()) > 16 * 16, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageRaids.Raid raid = night(helper, hall, 8, 1604);
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == raid.raiders() && raiders.stream().allMatch(m -> in(level, m) && m.isPersistenceRequired()),
				"the raid counts " + raid.raiders() + ": " + describe(level, raiders) + " gathered at " + VillageRaids.lastGathering);
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(raiders.stream().allMatch(m -> in(level, m)), "raiders gone after 40 ticks: " + describe(level, raiders));
				helper.succeed();
			});
		});
	}

	/**
	 * A save and reload mid-attack: a raider placed past the refusal is saved as a persistent raider (it isn't despawned
	 * when its chunk comes back), and the copy read back from that save is still one of the raid's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaNight1104d2Saved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaNight1104d2Saved")
	public void aForcedRaiderIsSavedAsAPersistentRaider(GameTestHelper helper) {
		BlockPos hall = village(helper, e -> e instanceof Enemy, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageRaids.Raid raid = night(helper, hall, 8, 4242);
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			Mob raider = VillageRaids.raiders(level, hall).get(0);
			CompoundTag saved = new CompoundTag();
			helper.assertTrue(raider.save(saved), "the raider couldn't be saved");
			helper.assertTrue(saved.getBoolean("PersistenceRequired"), "the raider isn't saved as persistent: " + saved);
			Entity copy = EntityType.create(saved, level).orElse(null);
			helper.assertTrue(copy instanceof Mob m && m.isPersistenceRequired() && m.getTags().contains(VillageRaids.TAG)
				&& VillageRaids.reaches(hall, m), "the copy read back isn't one of the raid's: " + copy + " tags " + (copy == null ? null : copy.getTags()));
			copy.discard();
			helper.succeed();
		});
	}

	/** The forced line names the night attack and where, so a server owner can tell which village it was. */
	//$ gametest_ticks_batch AREA '80' '"qaNight1104d2Line"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaNight1104d2Line")
	public void theLogLineNamesTheNightAttacksHall(GameTestHelper helper) {
		BlockPos hall = village(helper, e -> e instanceof Enemy, true);
		helper.runAfterDelay(2, () -> {
			VillageRaids.Raid raid = night(helper, hall, 8, 3);
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.startsWith("Another mod refused to let minecraft:") && said.contains("raid on the village hall at " + hall.toShortString())
				&& said.contains("forceThreatSpawns") && said.contains("config/aliveworkplace.json"), "the log line: " + said);
			helper.succeed();
		});
	}
}
