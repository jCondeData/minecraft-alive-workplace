package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-09) for raiders that convert in vanilla in the middle of a siege (B1054, B1072; ROADMAP 32.5
 * ladders, 32.2 the siege director). {@code Mob.convertTo} (a zombie that drowns, a husk under water, a skeleton frozen
 * in powder snow) gives the raider a new UUID; the fix carries its tags and swaps it into lair and bounty lists, but the
 * siege's director ("through" the gate) and the ladders ("over" the wall, a ladder assignment) keep raiders by UUID in
 * memory. These tests build QaSiegeLadderBreach1103dGameTests' walled yard (a Stone Wall the ladder goes up, a
 * Palisade Gate as the breach, a villager shut in a cell) and lay siege to it with zombies of a test culture, then drown
 * one ({@code convertTo(DROWNED)}, what {@code Zombie.doUnderWaterConversion} calls) at each step of the siege.
 */
public class QaSiegeConverts1109dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation CULTURE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_siege_converts_zombies");

	/** The test culture: zombies, half of them climbers, who set ladders. The yard's own pillager culture is put back as it was. */
	static Culture zombies(GameTestHelper helper, Map<ResourceLocation, Culture> before) {
		Culture culture = Culture.read(CULTURE, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:zombie\", \"share\": 50, \"role\": \"climber\"}, {\"entity\": \"minecraft:zombie\", \"share\": 50, \"role\": \"melee\"}],"
			+ " \"tactics\": [\"ladders\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>();
		Threats.all().forEach(c -> with.put(c.id(), c));
		with.put(CULTURE, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(before));
		return culture;
	}

	/** The yard, at night (zombies burn by day), with the zombie culture on it. */
	static QaSiegeLadderBreach1103dGameTests.Yard yard(GameTestHelper helper) {
		Map<ResourceLocation, Culture> before = new LinkedHashMap<>();
		Threats.all().forEach(c -> before.put(c.id(), c));
		QaSiegeLadderBreach1103dGameTests.Yard pillagers = QaSiegeLadderBreach1103dGameTests.yard(helper);
		Culture culture = zombies(helper, before);
		pillagers.level().setDayTime(18000);
		return new QaSiegeLadderBreach1103dGameTests.Yard(pillagers.level(), pillagers.hall(), culture, pillagers.villager());
	}

	private static Mob raider(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, Culture.Role role, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		if (mob.getVehicle() != null) {
			net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
			mob.stopRiding();
			vehicle.discard();
		}
		if (mob instanceof net.minecraft.world.entity.monster.Zombie zombie) {
			zombie.setBaby(false); // (a fixed shape: no baby zombies squeezing through the crenels)
		}
		mob.getRandom().setSeed(1L);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A climber outside the Stone Wall and a follower outside the gate, raiders of one raid; the siege begins with the gate shut. */
	static Mob[] raiders(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard) {
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		Mob follower = raider(helper, yard, Culture.Role.MELEE, 22.5, 23.5);
		VillageRaids.track(yard.level(), yard.hall(), yard.culture(), List.of(climber, follower));
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(siege.breach != null, "the siege has no breach (the Palisade Gate isn't its gate): the test would prove nothing");
		helper.assertTrue(!Sieges.open(siege), "the gate isn't shut as the siege begins");
		return new Mob[] {climber, follower};
	}

	/** {@code mob} drowns into a drowned the way vanilla turns it (convertTo, gear kept); fails if nothing came of it. */
	static Mob drown(GameTestHelper helper, Mob mob) {
		Mob drowned = mob.convertTo(EntityType.DROWNED, true);
		helper.assertTrue(drowned != null && drowned.isAlive(), "convertTo made no drowned");
		helper.assertTrue(!drowned.getUUID().equals(mob.getUUID()), "the drowned kept the zombie's UUID: the test would prove nothing");
		helper.assertTrue(drowned.getTags().contains(Threats.RAIDER_TAG), "the drowned isn't a raider any more (B1054)");
		return drowned;
	}

	static String state(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, Mob mob) {
		return QaSiegeLadderBreach1103dGameTests.state(helper, yard, mob);
	}

	/**
	 * A climber zombie drowns at the ladder's foot while the rungs go up: the ladder isn't left held for the zombie that
	 * is gone (Ladders no longer claims its UUID), and the drowned it became takes the work up, finishes the ladder and
	 * goes over the wall by it.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaSiegeConvertsClimber1109d"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaSiegeConvertsClimber1109d")
	public void aClimberThatDrownsAtTheLadderFinishesItAndGoesOver(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob[] climber = {mobs[0]};
		Mob follower = mobs[1];
		follower.discard(); // (one raider is enough here; the raid holds while the climber lives)
		ThreatData.Siege siege = yard.siege(helper);
		long[] turned = {-1};
		UUID[] old = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (turned[0] < 0) {
				int rungs = siege.ladders.size();
				if (rungs >= 1 && rungs <= 3) {
					old[0] = climber[0].getUUID();
					climber[0] = drown(helper, climber[0]);
					turned[0] = tick;
				} else if (rungs > 3 || tick > 1200) {
					helper.fail("the climber never got a ladder part way up (the test would prove nothing): rungs " + rungs + "; climber " + state(helper, yard, climber[0]));
				}
				return;
			}
			if (tick - turned[0] > 20) {
				helper.assertTrue(!Sieges.directorState(yard.level(), yard.hall(), mobsOf(yard, old[0])).contains(", ladder true"),
					"the ladder is still held for the zombie that drowned " + (tick - turned[0]) + " ticks ago");
			}
			if (QaSiegeLadderBreach1103dGameTests.inside(helper, climber[0]) && Ladders.over(yard.level(), yard.hall(), climber[0].getUUID())) {
				helper.assertTrue(QaSiegeLadderBreach1103dGameTests.standing(siege), "over, but the ladder isn't up: rungs " + siege.ladders.size());
				helper.succeed();
			} else if (tick - turned[0] > 900) {
				helper.fail("the drowned didn't finish the ladder and get over the wall 900 ticks after it drowned: rungs " + siege.ladders.size()
					+ "; drowned " + state(helper, yard, climber[0]));
			}
		});
	}

	/** A stand-in mob with {@code id}, for directorState's claims lookup by UUID (only the id is read). */
	private static Mob mobsOf(QaSiegeLadderBreach1103dGameTests.Yard yard, UUID id) {
		Mob probe = EntityType.ZOMBIE.create(yard.level());
		probe.setUUID(id);
		return probe;
	}

	/**
	 * The control for QaSiegeConverts1109dBugTests: a zombie that went in through the broken gate and on, deep inside by
	 * the villager's cell (out of its sight), stays counted through and is left to its fight: in 300 ticks it never walks
	 * 3 blocks or more back toward the gate and is never under the director's orders.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaSiegeConvertsThroughControl1109d"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaSiegeConvertsThroughControl1109d")
	public void aRaiderThroughTheGateIsLeftToItsFight(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		mobs[0].discard(); // (no climber: no ladder)
		Mob zombie = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
		long[] deep = {-1};
		Vec3[] at = {null};
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			boolean through = Sieges.through(yard.level(), yard.hall(), zombie.getUUID());
			if (deep[0] < 0) {
				if (through && QaSiegeLadderBreach1103dGameTests.inside(helper, zombie) && zombie.getX() - base.getX() < 14.5) {
					deep[0] = tick;
					at[0] = zombie.position();
				} else if (tick > 1200) {
					helper.fail("the zombie never got deep inside through the gate (the test would prove nothing): " + state(helper, yard, zombie));
				}
				return;
			}
			helper.assertTrue(through, "the zombie, deep inside, is no longer counted through: " + state(helper, yard, zombie));
			helper.assertTrue(!Sieges.directorState(yard.level(), yard.hall(), zombie).contains(", ordered true"),
				"the zombie, through the gate, is under the director's orders: " + state(helper, yard, zombie));
			double back = zombie.getX() - at[0].x;
			helper.assertTrue(back < 3, "the zombie walked " + String.format(java.util.Locale.ROOT, "%.1f", back) + " blocks back toward the gate: " + state(helper, yard, zombie));
			if (tick - deep[0] > 300) {
				helper.succeed();
			}
		});
	}

	/**
	 * A zombie that went over the wall by the ladder (gate still shut) drowns inside. The drowned has a new UUID that the
	 * ladders don't count over, so Ladders gives it the standing ladder again (seen: about 30 ticks, a short walk to the
	 * wall behind the ladder, then let go as over once more). What a player must not see: it held there for good (100
	 * ticks at most), or back out over the wall; 300 ticks on it is counted over again and still inside.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaSiegeConvertsOver1109d"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaSiegeConvertsOver1109d")
	public void aRaiderThatDrownsAfterClimbingOverIsNotSentBackToTheLadder(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob climber = mobs[0];
		Mob[] follower = {mobs[1]};
		ThreatData.Siege siege = yard.siege(helper);
		long[] turned = {-1};
		long[] overAt = {-1};
		int[] heldTicks = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (zombies here have no axes)");
			if (turned[0] < 0) {
				boolean over = Ladders.over(yard.level(), yard.hall(), follower[0].getUUID());
				if (over && overAt[0] < 0) {
					overAt[0] = tick;
				}
				// Inside, a while after it dropped down (wherever the siege has walked it since).
				if (over && QaSiegeLadderBreach1103dGameTests.inside(helper, follower[0]) && tick - overAt[0] > 60) {
					follower[0] = drown(helper, follower[0]);
					follower[0].setTarget(yard.villager());
					turned[0] = tick;
				} else if (tick > 2000) {
					helper.fail("the follower never got over by the ladder and in (the test would prove nothing): over since " + overAt[0]
						+ "; follower " + state(helper, yard, follower[0]) + "; climber " + state(helper, yard, climber) + "; rungs " + siege.ladders.size());
				}
				return;
			}
			if (Sieges.directorState(yard.level(), yard.hall(), follower[0]).contains(", ladder true")) {
				heldTicks[0]++;
			}
			helper.assertTrue(heldTicks[0] < 100, "the drowned, over the wall before it drowned, has been held to the ladder again for " + heldTicks[0]
				+ " ticks (it should be let go at once): " + state(helper, yard, follower[0]));
			helper.assertTrue(!QaSiegeLadderBreach1103dGameTests.upTheLadder(follower[0], QaSiegeLadderBreach1103dGameTests.foot(siege))
				&& follower[0].getZ() - helper.absolutePos(BlockPos.ZERO).getZ() < 15, "the drowned went back out over the wall: " + state(helper, yard, follower[0]));
			if (tick - turned[0] > 300) {
				helper.assertTrue(Ladders.over(yard.level(), yard.hall(), follower[0].getUUID()), "300 ticks after it drowned, the drowned still isn't counted over the wall: "
					+ state(helper, yard, follower[0]));
				helper.succeed(); // (inside the wall: checked every tick above; the director keeps it by the shut gate's inside, as it does the zombie)
			}
		});
	}

	/**
	 * The siege ends when the drowned (the raid's last raider) is killed: the raid is over, and the director takes the
	 * ladder away (no rung left standing, none held for a mob that is gone).
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaSiegeConvertsEnds1109d"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaSiegeConvertsEnds1109d")
	public void killingTheDrownedClimberEndsTheSiegeAndTakesTheLadderAway(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob[] climber = {mobs[0]};
		mobs[1].discard();
		ThreatData.Siege siege = yard.siege(helper);
		long[] killed = {-1};
		List<BlockPos> rungs = new java.util.ArrayList<>();
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (killed[0] < 0) {
				if (siege.ladders.size() >= 2) {
					climber[0] = drown(helper, climber[0]);
					rungs.addAll(siege.ladders);
					climber[0].kill();
					killed[0] = tick;
				} else if (tick > 1200) {
					helper.fail("the climber never set two rungs (the test would prove nothing): " + state(helper, yard, climber[0]));
				}
				return;
			}
			// The hall's round (VillageHallBlockEntity runs it; this yard has no hall block): the raid is checked on.
			if ((tick - killed[0]) % 20 == 0) {
				VillageRaids.tick(yard.level(), yard.hall(), 8, 0, yard.level().getDayTime() / 24000, d -> { });
			}
			boolean over = Sieges.siege(yard.level(), yard.hall()).map(s -> s.over).orElse(true);
			if (!over || !Ladders.rungs(yard.level(), yard.hall()).isEmpty()) {
				helper.assertTrue(tick - killed[0] < 400, "400 ticks after the drowned, the raid's last raider, was killed: siege over " + over
					+ ", rungs recorded " + Ladders.rungs(yard.level(), yard.hall()).size());
				return;
			}
			for (BlockPos pos : rungs) {
				helper.assertTrue(!yard.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LADDER),
					"a rung is left standing at " + QaSiegeLadderBreach1103dGameTests.rel(helper, pos) + " after the siege ended");
			}
			helper.assertTrue(Ladders.rungs(yard.level(), yard.hall()).isEmpty(), "the siege still records rungs");
			helper.succeed();
		});
	}
}
