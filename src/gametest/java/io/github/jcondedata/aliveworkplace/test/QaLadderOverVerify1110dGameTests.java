package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-10) verifying B1116 from its spec (ROADMAP Bugs; wiki sieges.md): a raider that got over the wall
 * by a siege ladder (32.5) or a sand ramp (32.9) is past the wall as one through the breach is and is left to its own
 * fight, "while it is not back outside the gate line"; back outside, it is the director's again. Beyond
 * QaLadderOverShutGate1109dBugTests (never ordered; nearer the cell than the gate; chases a villager that can flee):
 * the raider over the wall actually reaches and hurts a villager that stands still in the open yard; one put back
 * outside the gate line is under orders again (and the director still orders raiders that never got over); the same
 * for the ramp. Uses QaSiegeConverts1109dGameTests' yard (zombies, ladders) and QaLadderOverShutGate1109dBugTests'
 * husk ramp culture set-up (same yard, husks with the sand_ramps tactic).
 */
public class QaLadderOverVerify1110dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ramp_over_verify_1110d");

	/**
	 * A zombie over the wall by the ladder, the gate shut; the villager's cell is opened and the villager can't run
	 * (no AI). Within 400 ticks of getting in, the zombie hurts it, and it is never under the director's orders.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverHurts1110d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverHurts1110d")
	public void aRaiderOverTheWallByTheLadderHurtsAVillagerInTheOpen(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		hurtsInTheOpen(helper, yard, siege, 2000, () -> {
			if (follower.isAlive() && Ladders.over(yard.level(), yard.hall(), follower.getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, follower)) {
				return follower;
			}
			return null;
		}, () -> "follower " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
			+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; rungs " + siege.ladders.size());
	}

	/**
	 * The boundary of the fix: the director still orders the follower while it waits outside (before it is over), lets
	 * it go once it is over the wall, and, put back outside the gate line (teleported in front of the shut gate), takes
	 * it under its orders (or the ladder's) again within 200 ticks: it isn't left loose outside the walls.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverBackOut1110d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverBackOut1110d")
	public void aRaiderOverTheWallByTheLadderPutBackOutsideIsTheDirectorsAgain(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		backOutside(helper, yard, siege, 2000, () -> {
			if (follower.isAlive() && Ladders.over(yard.level(), yard.hall(), follower.getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, follower)) {
				return follower;
			}
			return null;
		}, follower, () -> "follower " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
			+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; rungs " + siege.ladders.size());
	}

	/** The ramp's case of the first: a husk over the wall by the sand ramp hurts a villager standing still in the open yard. */
	//$ gametest_ticks_batch AREA '3200' '"qaRampOverHurts1110d"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaRampOverHurts1110d")
	public void aHuskOverTheWallByTheRampHurtsAVillagerInTheOpen(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = rampYard(helper);
		Mob climber = husk(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		Mob follower = husk(helper, yard, Culture.Role.MELEE, 22.5, 23.5);
		VillageRaids.track(yard.level(), yard.hall(), yard.culture(), List.of(climber, follower));
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(siege.breach != null && !Sieges.open(siege), "the siege has no shut gate as it begins: the test would prove nothing");
		hurtsInTheOpen(helper, yard, siege, 2400, () -> overByRamp(helper, yard, follower, climber),
			() -> "follower " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
				+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; sand " + siege.sand.size());
	}

	/** The ramp's case of the boundary: a husk over the wall by the ramp, put back outside the gate line, is ordered again. */
	//$ gametest_ticks_batch AREA '3200' '"qaRampOverBackOut1110d"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaRampOverBackOut1110d")
	public void aHuskOverTheWallByTheRampPutBackOutsideIsTheDirectorsAgain(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = rampYard(helper);
		Mob climber = husk(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		Mob follower = husk(helper, yard, Culture.Role.MELEE, 22.5, 23.5);
		VillageRaids.track(yard.level(), yard.hall(), yard.culture(), List.of(climber, follower));
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(siege.breach != null && !Sieges.open(siege), "the siege has no shut gate as it begins: the test would prove nothing");
		backOutside(helper, yard, siege, 2400, () -> overByRamp(helper, yard, follower, climber), follower,
			() -> "follower " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
				+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; sand " + siege.sand.size());
	}

	// -- shared steps --

	private static void hurtsInTheOpen(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, ThreatData.Siege siege, long latest,
		java.util.function.Supplier<Mob> overNow, java.util.function.Supplier<String> why) {
		long[] overAt = {-1};
		Mob[] over = {null};
		int[] ordered = {0};
		Villager villager = yard.villager();
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (no axes here)");
			if (overAt[0] < 0) {
				Mob m = overNow.get();
				if (m != null) {
					over[0] = m;
					overAt[0] = tick;
					for (int x = 9; x <= 11; x++) { // (the cell opened; the villager stays where it stands)
						for (int z = 6; z <= 8; z++) {
							for (int y = 2; y <= 4; y++) {
								helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
							}
						}
					}
					villager.setNoAi(true);
				} else if (tick > latest) {
					helper.fail("no raider got over the wall and in (the test would prove nothing): " + why.get());
				}
				return;
			}
			Mob m = over[0];
			helper.assertTrue(m.isAlive(), "the raider over the wall died: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, m));
			long since = tick - overAt[0];
			if (since > 20 && Sieges.directorState(yard.level(), yard.hall(), m).contains(", ordered true")) {
				ordered[0]++;
			}
			helper.assertTrue(ordered[0] == 0, "the raider over the wall was put under the director's orders with a villager in the open yard: "
				+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, m));
			if (!villager.isAlive() || villager.getLastHurtByMob() == m) {
				helper.succeed();
				return;
			}
			if (since >= 400) {
				helper.fail(String.format(Locale.ROOT, "400 ticks after it got over the wall, the raider never hurt the villager standing still in the open yard"
					+ " (%.1f blocks from it): ", m.position().distanceTo(villager.position())) + QaSiegeLadderBreach1103dGameTests.state(helper, yard, m)
					+ "; target " + m.getTarget());
			}
		});
	}

	private static void backOutside(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, ThreatData.Siege siege, long latest,
		java.util.function.Supplier<Mob> overNow, Mob outsider, java.util.function.Supplier<String> why) {
		long[] overAt = {-1};
		long[] outAt = {-1};
		Mob[] over = {null};
		int[] orderedBefore = {0};
		int[] orderedOver = {0};
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (no axes here)");
			if (overAt[0] < 0) {
				if (outsider.isAlive() && Sieges.directorState(yard.level(), yard.hall(), outsider).contains(", ordered true")) {
					orderedBefore[0]++;
				}
				Mob m = overNow.get();
				if (m != null) {
					over[0] = m;
					overAt[0] = tick;
				} else if (tick > latest) {
					helper.fail("no raider got over the wall and in (the test would prove nothing): " + why.get());
				}
				return;
			}
			Mob m = over[0];
			helper.assertTrue(m.isAlive(), "the raider over the wall died: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, m));
			long since = tick - overAt[0];
			String director = Sieges.directorState(yard.level(), yard.hall(), m);
			if (outAt[0] < 0) {
				if (since > 20 && director.contains(", ordered true")) {
					orderedOver[0]++;
				}
				if (since < 80) {
					return;
				}
				helper.assertTrue(m != outsider || orderedBefore[0] > 0, "the raider waiting outside before it got over was never under the director's orders: "
					+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, m));
				helper.assertTrue(orderedOver[0] == 0, "the raider over the wall was under the director's orders for " + orderedOver[0] + " ticks: "
					+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, m));
				// (put back outside: in front of the shut gate, well past its line)
				m.getNavigation().stop();
				m.setTarget(null);
				m.teleportTo(base.getX() + 22.5, base.getY() + 2, base.getZ() + 24.5);
				outAt[0] = tick;
				return;
			}
			long out = tick - outAt[0];
			if (director.contains(", ordered true") || director.contains(", ladder true") || director.contains(", ramp true")) {
				helper.succeed();
				return;
			}
			if (out >= 200) {
				helper.fail("put back outside the gate line, the raider over the wall was neither under the director's orders nor at a ladder or ramp"
					+ " for 200 ticks: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, m) + ", over by ramp " + SandRamps.over(yard.level(), yard.hall(), m.getUUID()));
			}
		});
	}

	static Mob overByRamp(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, Mob... husks) {
		for (Mob m : husks) {
			if (m.isAlive() && SandRamps.over(yard.level(), yard.hall(), m.getUUID()) && QaSiegeLadderBreach1103dGameTests.inside(helper, m)) {
				return m;
			}
		}
		return null;
	}

	/** The yard with husks of a culture with the sand_ramps tactic (QaLadderOverShutGate1109dBugTests' ramp set-up). */
	static QaSiegeLadderBreach1103dGameTests.Yard rampYard(GameTestHelper helper) {
		Map<ResourceLocation, Culture> before = new LinkedHashMap<>();
		Threats.all().forEach(c -> before.put(c.id(), c));
		QaSiegeLadderBreach1103dGameTests.Yard pillagers = QaSiegeLadderBreach1103dGameTests.yard(helper);
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 50, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 50, \"role\": \"melee\"}],"
			+ " \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(before);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(before));
		return new QaSiegeLadderBreach1103dGameTests.Yard(pillagers.level(), pillagers.hall(), culture, pillagers.villager());
	}

	static Mob husk(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, Culture.Role role, double x, double z) {
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(yard.level(), member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(yard.level(), yard.level().getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		mob.ejectPassengers();
		mob.stopRiding();
		if (mob instanceof net.minecraft.world.entity.monster.Zombie zombie) {
			zombie.setBaby(false);
		}
		mob.getRandom().setSeed(1L);
		Threats.outfit(yard.level(), mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		yard.level().addFreshEntityWithPassengers(mob);
		return mob;
	}
}
