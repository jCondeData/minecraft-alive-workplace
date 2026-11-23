package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/**
 * QA (lane-d-1123-0709), B1527 verified from the spec: reputation.md "Fighting in a raid: the third raider you kill in
 * it, +20, once a raid"; "Raiders are ours ... and vanilla's pillagers while their raid is on". The edges the fix's own
 * tests leave out: kills by a player's arrow (one flown for real), the last raider of a raid's last wave, a vanilla raid's
 * other raiders (vindicator, witch, ravager), and a raider saved and loaded mid-raid (what a chunk reload or a restart
 * does to it: it rejoins its raid by id). Bounties, lairs and threats have no death rule that asks a dying raider about a
 * vanilla raid (their wiki pages promise nothing of the kind; their death hooks read only our tags), so nothing to add there.
 */
public class QaRaidKills1123dGameTests implements FabricGameTest {
	private static final String AREA = ReputationGameTests.AREA;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaRaidKills1123d");

	/** A raid at a bell near the hall started at tick 30 by {@code player}, nothing joined yet; then {@code then}. */
	private static void withRaid(GameTestHelper helper, ServerPlayer player, java.util.function.Consumer<Raid> then) {
		VillageRaids.forget();
		Leftovers.after(helper, VillageRaids::forget);
		BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
		Raid[] raid = new Raid[1];
		Leftovers.after(helper, () -> {
			if (raid[0] != null) {
				raid[0].stop();
			}
		});
		helper.runAfterDelay(30, () -> {
			raid[0] = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, player);
			helper.assertTrue(VillageRaids.active(helper.absolutePos(ReputationGameTests.HALL)).isEmpty(), "(setup) a village raid of ours is on too");
			then.accept(raid[0]);
		});
	}

	private static List<Mob> pillagers(GameTestHelper helper, int n) {
		List<Mob> p = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			p.add(QaVanillaRaid1123dGameTests.pillager(helper, new BlockPos(3 + i, 2, 3)));
		}
		return p;
	}

	private static void join(GameTestHelper helper, Raid raid, int wave, Mob m) {
		raid.joinRaid(wave, (Raider) m, null, false);
		helper.assertTrue(((Raider) m).getCurrentRaid() == raid && raid.isActive(), "(setup) the raider isn't in the active raid");
	}

	/** Checks {@code ready} every tick (by {@code runAfterDelay(1)}: an {@code onEachTick} started inside a scheduled callback re-runs that callback) up to {@code ticks}; then {@code then}, else {@code timeout}. */
	static void poll(GameTestHelper helper, int ticks, java.util.function.BooleanSupplier ready, Runnable then, Runnable timeout) {
		helper.runAfterDelay(1, () -> {
			if (ready.getAsBoolean()) {
				then.run();
			} else if (ticks <= 1) {
				timeout.run();
			} else {
				poll(helper, ticks - 1, ready, then, timeout);
			}
		});
	}

	private static int points(GameTestHelper helper, ServerPlayer p) {
		return ReputationGameTests.points(helper, p);
	}

	private static Object field(Raid raid, String name) {
		try {
			Field f = Raid.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.get(raid);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void set(Raid raid, String name, Object value) {
		try {
			Field f = Raid.class.getDeclaredField(name);
			f.setAccessible(true);
			f.set(raid, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Arrows: two joined pillagers fall to the player's arrows, the third to a real arrow he shot that flies into it. The
	 * third pays the raid's +20 (the source's attacker is the arrow's owner, the player).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRaidKills1123dArrows"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidKills1123dArrows")
	public void qaAVanillaRaidsThirdPillagerShotWithAnArrowPaysTwenty(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 3);
			withRaid(helper, me, raid -> {
				for (Mob m : p) {
					join(helper, raid, 1, m);
				}
				for (int i = 0; i < 2; i++) {
					Arrow arrow = EntityType.ARROW.create(level);
					arrow.setOwner(me);
					arrow.moveTo(p.get(i).getX(), p.get(i).getY() + 1, p.get(i).getZ() - 1);
					p.get(i).hurt(level.damageSources().arrow(arrow, me), 1000f);
					helper.assertTrue(!p.get(i).isAlive(), "(setup) an arrowed pillager didn't die");
				}
				helper.assertTrue(points(helper, me) == 0, "two arrow kills already paid " + points(helper, me) + " (raid " + raid.getId() + ", alive " + raid.getTotalRaidersAlive() + ", third alive " + p.get(2).isAlive() + ")");
				Mob third = p.get(2);
				third.setHealth(1f);
				Arrow arrow = EntityType.ARROW.create(level);
				arrow.setOwner(me);
				arrow.moveTo(third.getX(), third.getY() + 1.0, third.getZ() - 1.3);
				arrow.setDeltaMovement(0, 0, 0.8);
				level.addFreshEntity(arrow);
				Leftovers.after(helper, arrow::discard);
				poll(helper, 60, () -> !third.isAlive(), () -> {
					helper.assertTrue(raid.isActive(), "(setup) the raid ended");
					helper.assertTrue(points(helper, me) == 20, "the third vanilla raid pillager, shot dead with the player's arrow, paid " + points(helper, me)
						+ ", not 20 (killed by " + third.getLastDamageSource() + ")");
					LOG.info("[qa1123d] two arrow kills and one real arrow in flight: the third paid 20");
					helper.succeed();
				}, () -> helper.fail("(setup) the arrow never hit the third pillager"));
			});
		});
	}

	/**
	 * The raid's last wave (all its waves spawned): its three raiders are the last of the raid. The third, the raid's very
	 * last raider, pays the +20 (the raid is still on when it dies); the raid then ends in victory and pays nothing more.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaRaidKills1123dLastWave"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaRaidKills1123dLastWave")
	public void qaTheLastRaiderOfAVanillaRaidsLastWavePaysTwenty(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 3);
			withRaid(helper, me, raid -> {
				int waves = (Integer) field(raid, "numGroups");
				set(raid, "groupsSpawned", waves);
				set(raid, "started", true);
				helper.assertTrue(raid.getGroupsSpawned() == waves && waves > 0, "(setup) the raid isn't in its last wave");
				for (Mob m : p) {
					join(helper, raid, waves, m);
				}
				p.get(0).hurt(level.damageSources().playerAttack(me), 1000f);
				p.get(1).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(points(helper, me) == 0, "paid before the third: " + points(helper, me));
				helper.assertTrue(raid.getTotalRaidersAlive() == 1 && raid.isActive() && !raid.isOver(), "(setup) the last raider isn't the raid's last one alive");
				p.get(2).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(points(helper, me) == 20, "the last raider of the vanilla raid's last wave paid " + points(helper, me)
					+ ", not 20 (raid active " + raid.isActive() + ", alive " + raid.getTotalRaidersAlive() + ")");
				helper.runAfterDelay(80, () -> {
					helper.assertTrue(raid.isVictory(), "(setup) the vanilla raid didn't end in victory: " + field(raid, "status"));
					helper.assertTrue(points(helper, me) == 20, "the raid's victory changed the standing: " + points(helper, me));
					LOG.info("[qa1123d] last wave of {}: the raid's last raider paid 20, victory after", waves);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * A vanilla raid's raiders are not only pillagers: a vindicator, a witch and a ravager joined to it are the three kills,
	 * the ravager last, and it pays the +20.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRaidKills1123dKinds"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidKills1123dKinds")
	public void qaAVanillaRaidsVindicatorWitchAndRavagerPayTwenty(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> kinds = new ArrayList<>();
			List<EntityType<? extends Mob>> types = List.of(EntityType.VINDICATOR, EntityType.WITCH, EntityType.RAVAGER);
			for (int i = 0; i < types.size(); i++) {
				Mob m = types.get(i).create(level);
				BlockPos at = helper.absolutePos(new BlockPos(2 + 3 * i, 2, 3));
				m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
				m.setNoAi(true);
				m.setPersistenceRequired();
				level.addFreshEntity(m);
				Leftovers.after(helper, m::discard);
				kinds.add(m);
			}
			withRaid(helper, me, raid -> {
				for (Mob m : kinds) {
					join(helper, raid, 1, m);
				}
				kinds.get(0).hurt(level.damageSources().playerAttack(me), 1000f);
				kinds.get(1).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(!kinds.get(0).isAlive() && !kinds.get(1).isAlive(), "(setup) the vindicator or witch lived");
				helper.assertTrue(points(helper, me) == 0, "paid before the third: " + points(helper, me));
				kinds.get(2).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(!kinds.get(2).isAlive(), "(setup) the ravager lived");
				helper.assertTrue(points(helper, me) == 20, "a vanilla raid's vindicator, witch and ravager killed paid " + points(helper, me) + ", not 20");
				helper.succeed();
			});
		});
	}

	/**
	 * Save and reload mid-raid: after two kills, the third pillager is saved and loaded again (a chunk reload or a restart:
	 * a new entity from its save, which rejoins its raid by id). Killed then, it is the third raider of the same raid: +20.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRaidKills1123dReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidKills1123dReload")
	public void qaAVanillaRaidsPillagerSavedAndLoadedMidRaidStillMakesTheThirdKill(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 3);
			withRaid(helper, me, raid -> {
				for (Mob m : p) {
					join(helper, raid, 1, m);
				}
				p.get(0).hurt(level.damageSources().playerAttack(me), 1000f);
				p.get(1).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(points(helper, me) == 0, "paid before the third: " + points(helper, me));
				Mob old = p.get(2);
				CompoundTag tag = new CompoundTag();
				old.saveWithoutId(tag);
				helper.assertTrue(tag.contains("RaidId"), "(setup) the pillager's save has no raid id");
				old.discard();
				Mob copy = EntityType.PILLAGER.create(level);
				copy.load(tag);
				level.addFreshEntity(copy);
				Leftovers.after(helper, copy::discard);
				helper.runAfterDelay(5, () -> {
					helper.assertTrue(((Raider) copy).getCurrentRaid() == raid, "the reloaded pillager isn't back in its raid: " + ((Raider) copy).getCurrentRaid());
					copy.hurt(level.damageSources().playerAttack(me), 1000f);
					helper.assertTrue(!copy.isAlive(), "(setup) the reloaded pillager lived");
					helper.assertTrue(points(helper, me) == 20, "the third raider, saved and loaded mid-raid, paid " + points(helper, me) + ", not 20");
					helper.succeed();
				});
			});
		});
	}
}
