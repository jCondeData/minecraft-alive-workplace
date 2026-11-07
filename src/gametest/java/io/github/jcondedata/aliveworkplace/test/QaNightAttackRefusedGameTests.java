package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-05) for B216 / B208, from the Expected: "night attacks still come even when another mod refuses those
 * monsters" (Cobbleverse's MobsBeGone refuses zombies and the rest at addEntity). B208GameTests proves a wholly refused
 * attack started by hand; these add a blocklist that refuses only some of the roster (zombies: the attack comes whole,
 * the refused ones placed directly and the log names them), the operator's {@code /workplace threat start monsters}
 * through the real command with everything refused (it says how many come, and that many are in the world), and with
 * {@code forceThreatSpawns} off the same command brings nobody, records no raid, and the log says why.
 */
public class QaNightAttackRefusedGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final long DAY = 24000;

	/** A hall with eight villagers at night, the world refusing what {@code refused} names; all of it undone at the end. */
	private static BlockPos village(GameTestHelper helper, boolean force, Predicate<Entity> refused) {
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
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		for (int i = 0; i < 8; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i % 4, 2, 3 + i / 4));
		}
		level.setDayTime(39 * DAY + 15000);
		return hall;
	}

	private static boolean in(ServerLevel level, Entity e) {
		return e != null && e.isAlive() && level.getEntity(e.getUUID()) == e;
	}

	private static String describe(ServerLevel level, List<Mob> mobs) {
		StringBuilder s = new StringBuilder("[");
		for (Mob m : mobs) {
			s.append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath()).append('@').append(m.blockPosition().toShortString())
				.append(in(level, m) ? " in" : " OUT").append(m.isRemoved() ? " removed=" + m.getRemovalReason() : "").append(" persistent=")
				.append(m.isPersistenceRequired()).append("; ");
		}
		return s.append(']').toString();
	}

	/** Runs {@code command} as an operator standing at {@code at}; what it said goes into {@code said}. */
	private static int run(ServerLevel level, BlockPos at, String command, List<Component> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		CommandSourceStack source = new CommandSourceStack(out, Vec3.atBottomCenterOf(at), Vec2.ZERO, level, 4, "qa", Component.literal("qa"),
			level.getServer(), null);
		level.getServer().getCommands().performPrefixedCommand(source, command);
		return said.size();
	}

	/**
	 * A blocklist that names only zombies (half the monsters' roster): the night attack still comes whole, every raider
	 * in the world and persistent, zombies among them, and the log names the zombie as placed directly.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNightPartRefused"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNightPartRefused")
	public void aNightAttackWithOnlyZombiesRefusedStillComesWhole(GameTestHelper helper) {
		BlockPos hall = village(helper, true, e -> e instanceof Zombie);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Zombie plain = EntityType.ZOMBIE.create(level);
			plain.moveTo(helper.absoluteVec(new Vec3(8, 2, 8)));
			helper.assertTrue(!level.addFreshEntity(plain) && plain.isRemoved(), "setup: the world didn't refuse a zombie");
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0, Double.NaN, Threats.on(Threats.MONSTERS).orElseThrow(), RandomSource.create(216));
			helper.assertTrue(raid != null && raid.raiders() == 5, "the attack on a village of 8 (3 and one for every 4 villagers): " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == raid.raiders() && raiders.stream().allMatch(m -> in(level, m) && m.isPersistenceRequired()),
				"the raiders: " + describe(level, raiders) + " of " + raid.raiders());
			boolean zombies = raiders.stream().anyMatch(m -> m instanceof Zombie);
			helper.assertTrue(zombies, "setup: the fixed draw brought no zombie: " + describe(level, raiders));
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.contains("minecraft:zombie") && said.contains("placed it directly") && said.contains("forceThreatSpawns"),
				"the log line for the refused zombie: " + said);
			helper.runAfterDelay(100, () -> {
				helper.assertTrue(raiders.stream().allMatch(m -> in(level, m)), "raiders gone after 100 ticks: " + describe(level, raiders));
				helper.succeed();
			});
		});
	}

	/**
	 * The operator's command with every monster refused: "Monsters are raiding <village>: N of them.", and N raiders are
	 * in the world, persistent, the raid recorded.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNightCommandRefused"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNightCommandRefused")
	public void theThreatCommandBringsARefusedNightAttack(GameTestHelper helper) {
		BlockPos hall = village(helper, true, e -> e instanceof Enemy);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			List<Component> said = new ArrayList<>();
			run(level, hall.above(), "workplace threat start monsters", said);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(!raiders.isEmpty() && raiders.stream().allMatch(m -> in(level, m) && m.isPersistenceRequired()),
				"the raiders the command brought: " + describe(level, raiders) + "; it said " + said.stream().map(Component::getString).toList());
			String village = VillageHalls.name(level, hall).getString();
			String expected = "Monsters are raiding " + village + ": " + raiders.size() + " of them.";
			helper.assertTrue(said.size() == 1 && said.get(0).getString().equals(expected), "the command said " + said.stream().map(Component::getString).toList()
				+ ", not \"" + expected + "\"");
			helper.assertTrue(ThreatData.get(level).raid(hall).map(r -> r.raiders() == raiders.size()).orElse(false), "the raid recorded: " + ThreatData.get(level).raid(hall));
			helper.succeed();
		});
	}

	/** forceThreatSpawns off, every monster refused: the command brings nobody, no raid is recorded, and the log says why. */
	//$ gametest_ticks_batch AREA '200' '"qaNightCommandOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNightCommandOff")
	public void withForcingOffTheCommandBringsNobodyAndTheLogSaysWhy(GameTestHelper helper) {
		BlockPos hall = village(helper, false, e -> e instanceof Enemy);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			List<Component> said = new ArrayList<>();
			run(level, hall.above(), "workplace threat start monsters", said);
			helper.assertTrue(VillageRaids.raiders(level, hall).isEmpty(), "raiders came with forcing off: " + describe(level, VillageRaids.raiders(level, hall)));
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "a raid of nobody was recorded: " + ThreatData.get(level).raid(hall));
			String log = HostileSpawns.lastWarning();
			helper.assertTrue(log.startsWith("Could not spawn minecraft:") && log.contains("for the") && log.contains("forceThreatSpawns is off"),
				"the log line: " + log);
			helper.assertTrue(said.size() == 1 && !said.get(0).getString().contains("are raiding"), "the command said " + said.stream().map(Component::getString).toList());
			helper.succeed();
		});
	}
}
