package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.trainer.RaidAssist;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane d, 2026-11-07): mutation check of {@code compat/raiddens/RaidDensRaids}, the reflection into Cobblemon Raid
 * Dens 0.11.3's running raids (ROADMAP 38.1a, docs/design/M38.md "As built in 38.1a": the hired helpers find the
 * player's raid, wear its boss down to a quarter and go home when it is won or lost). The earlier tests stood in for the
 * raid with a fake {@link RaidAssist.Raid}, so nothing read a real Raid Dens raid. Here a real {@code RaidInstance} is
 * put in {@code RaidHelper.ACTIVE_RAIDS} (made without its constructor, which needs a live boss and battle; only the
 * fields the reflection reads are filled: the players in it, no battles, the boss's health and the raid's state) and
 * taken out again before the test returns, so Raid Dens' own tick never sees it. Each test runs alone in its batch.
 */
public class QaRaidDensMutants1107dCompatTests implements FabricGameTest {
	static final String HELPER = "com.necro.raid.dens.common.raids.helpers.RaidHelper";
	static final String INSTANCE = "com.necro.raid.dens.common.raids.RaidInstance";

	@SuppressWarnings("unchecked")
	static Map<UUID, Object> active() throws ReflectiveOperationException {
		return (Map<UUID, Object>) Class.forName(HELPER).getField("ACTIVE_RAIDS").get(null);
	}

	static void set(Class<?> c, Object o, String name, Object value) throws ReflectiveOperationException {
		Field f = c.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, value);
	}

	static Object get(Object o, String name) throws ReflectiveOperationException {
		Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}

	/** A real Raid Dens raid with {@code players} in it, its boss at {@code health} of {@code max}, in {@code state}; listed under {@code id}. */
	static Object raid(UUID id, List<ServerPlayer> players, float health, float max, String state) throws ReflectiveOperationException {
		Class<?> c = Class.forName(INSTANCE);
		Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		theUnsafe.setAccessible(true);
		Object unsafe = theUnsafe.get(null);
		Object raid = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, c);
		set(c, raid, "raid", id);
		set(c, raid, "activePlayers", new ArrayList<>(players));
		set(c, raid, "battles", new ArrayList<>());
		set(c, raid, "currentHealth", health);
		set(c, raid, "maxHealth", max);
		state(raid, state);
		active().put(id, raid);
		return raid;
	}

	static void state(Object raid, String state) throws ReflectiveOperationException {
		Class<?> states = Class.forName("com.necro.raid.dens.common.raids.RaidState");
		set(raid.getClass(), raid, "raidState", states.getMethod("valueOf", String.class).invoke(null, state));
	}

	static float health(Object raid) throws ReflectiveOperationException {
		return (Float) get(raid, "currentHealth");
	}

	static void remove(UUID... ids) {
		try {
			for (UUID id : ids) {
				active().remove(id);
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't reach Raid Dens' raids: " + e);
		}
	}

	static Optional<RaidAssist.Raid> raidOf(ServerPlayer player) {
		return RaidAssist.EXTENSION.call(a -> a.raidOf(player), Optional.<RaidAssist.Raid>empty());
	}

	/**
	 * A hired helper by the den looks for the player's real Raid Dens raid (not another player's raid), joins it, reads
	 * the boss's health (300 of 400, already hurt by the player) and strikes it: the real boss loses the share of its
	 * full health the helpers dealt.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDensRealStrike"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDensRealStrike")
	public void theHelpersFindAndStrikeARealRaidDensBoss(GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(RaidAssist.EXTENSION.present(), "Raid Dens 0.11.3's raids weren't reached by reflection");
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), RaidHelperCompatTests.PRICE);
		ServerPlayer other = RaidHelperCompatTests.player(helper, at[1].offset(-3, 0, 0), 0);
		UUID theirs = UUID.randomUUID();
		UUID mine = UUID.randomUUID();
		try {
			raid(theirs, List.of(other), 300f, 300f, "IN_PROGRESS");
			helper.assertTrue(raidOf(player).isEmpty(), "a player in no Raid Dens raid was found in one");
			Object boss = raid(mine, List.of(player), 300f, 400f, "IN_PROGRESS");
			Optional<RaidAssist.Raid> found = raidOf(player);
			helper.assertTrue(found.isPresent() && found.get().id().equals(mine),
				"the player's own Raid Dens raid wasn't found: " + found.map(RaidAssist.Raid::id));
			helper.assertTrue(found.get().health() == 300f && found.get().maxHealth() == 400f && found.get().state() == RaidAssist.State.RUNNING,
				"the raid read as health " + found.get().health() + " of " + found.get().maxHealth() + ", " + found.get().state());
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			hire.nextLook = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(hire.joined && mine.equals(hire.raid), "the helpers' look didn't join the player's raid: joined " + hire.joined
				+ ", raid " + hire.raid);
			hire.nextStrike = level.getGameTime();
			RaidHelpers.tick(level, hire);
			float left = health(boss);
			helper.assertTrue(hire.dealt > 0f && left < 300f && Math.abs(left - (300f - 400f * hire.dealt)) < 0.01f,
				"the helpers dealt " + hire.dealt + " and the real boss went from 300 to " + left + " of 400");
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isPresent() && !RaidHelpers.homeward(level, master.getUUID()),
				"a running raid ended the hiring");
		} finally {
			remove(theirs, mine);
			RaidHelperCompatTests.cleanUp(helper, at[1], player, other);
		}
		helper.succeed();
	}

	/** Raid Dens' own states read right: SUCCESS is won, FAILED and CANCELLED lost, the rest running; a lost real raid sends the helpers home. */
	//$ gametest_batch 'CompatGameTests.AREA' '"qaRaidDensRealLost"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaRaidDensRealLost")
	public void aRealRaidDensRaidLostSendsTheHelpersHome(GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(RaidAssist.EXTENSION.present(), "Raid Dens 0.11.3's raids weren't reached by reflection");
		BlockPos[] at = RaidHelperCompatTests.village(helper);
		Villager master = RaidHelperCompatTests.trainer(helper, 0, 5);
		ServerPlayer player = RaidHelperCompatTests.player(helper, at[1].offset(3, 0, 0), RaidHelperCompatTests.PRICE);
		UUID mine = UUID.randomUUID();
		try {
			Object boss = raid(mine, List.of(player), 400f, 400f, "NOT_STARTED");
			String[][] cases = {{"NOT_STARTED", "RUNNING"}, {"IN_PROGRESS", "RUNNING"}, {"SUCCESS", "WON"}, {"FAILED", "LOST"}, {"CANCELLED", "LOST"}};
			for (String[] c : cases) {
				state(boss, c[0]);
				Optional<RaidAssist.Raid> raid = RaidAssist.EXTENSION.call(a -> a.raid(level.getServer(), mine), Optional.<RaidAssist.Raid>empty());
				helper.assertTrue(raid.isPresent() && raid.get().state().name().equals(c[1]),
					"Raid Dens' " + c[0] + " read as " + raid.map(r -> r.state().name()) + " (expected " + c[1] + ")");
			}
			state(boss, "IN_PROGRESS");
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = RaidHelperCompatTests.hire(helper, player);
			RaidHelpers.joined(level, hire, player, mine);
			state(boss, "FAILED");
			hire.nextStrike = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(health(boss) == 400f, "the helpers struck a lost raid's boss: " + health(boss));
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID()),
				"after a lost Raid Dens raid the helper should head home");
		} finally {
			remove(mine);
			RaidHelperCompatTests.cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}
}
