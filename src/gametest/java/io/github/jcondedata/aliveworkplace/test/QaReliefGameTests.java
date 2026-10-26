package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * QA, from the spec of ROADMAP 33.17 (Relief in a raid), the boundaries the feature's own tests leave out: the 1,024-block
 * reach at and one past (lowered to the test land's 9 and 18 blocks), the 2-guard threshold not counting hired swords,
 * a raided capital helped by its members (never by itself), one relief per raid however many seconds the watch sees it and
 * a fresh one for the next raid (a bandit raid), the switch turned on mid-raid, a raid over before the riders arrive, a
 * rider who falls (the hall line and the horse), a rider saved and loaded mid-relief, and every line a player reads with 1
 * and 2 riders (chat, both chronicles, the hall, the treasury ledger). Each test is alone in its batch.
 */
public class QaReliefGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A raider that lives through the test: tagged, still and unhurtable, standing {@code east} blocks east of the hall. */
	private static Zombie raider(ServerLevel level, BlockPos hall, int east) {
		Zombie zombie = EntityType.ZOMBIE.create(level);
		BlockPos at = hall.relative(Direction.EAST, east);
		zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		zombie.addTag(VillageRaids.TAG);
		zombie.setNoAi(true);
		zombie.setInvulnerable(true);
		zombie.setPersistenceRequired();
		level.addFreshEntity(zombie);
		return zombie;
	}

	/** Relief's ride floor and reach, put back after the test. */
	private static void keepTunables(GameTestHelper helper) {
		long minRide = Relief.MIN_RIDE;
		int range = Relief.RANGE;
		Leftovers.after(helper, () -> {
			Relief.MIN_RIDE = minRide;
			Relief.RANGE = range;
		});
	}

	private static void endRaids(GameTestHelper helper, RealmGameTests.Land land) {
		Leftovers.after(helper, () -> land.halls().forEach(h -> ThreatData.get(land.level()).end(h)));
	}

	private static List<String> ledger(RealmGameTests.Land land) {
		return ReliefGameTests.theRealm(land).ledger().stream().map(m -> m.reason().getString()).toList();
	}

	private static List<String> chronicle(RealmGameTests.Land land, int i) {
		return land.entity(i).chronicle().stream().map(e -> e.text().getString()).toList();
	}

	private static String hallLine(RealmGameTests.Land land, int i) {
		Component line = Relief.hallLine(land.level(), land.hall(i));
		return line == null ? null : line.getString();
	}

	/**
	 * The reach: hamlet 2 is exactly 9 blocks from hamlet 1, so with the reach at 9 it sends and at 8 it doesn't (the
	 * capital, also 9 away, comes either way). The threshold counts the village's own guards only: one guard and two hired
	 * mercenaries send no one; a second real guard sends one rider. Band sizes at each step of 3.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaReliefReachEdge"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaReliefReachEdge")
	public void reachIsInclusiveAndHiredSwordsDontCount(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ServerLevel level = land.level();
		keepTunables(helper);
		ReliefGameTests.treasury(land, 1000);
		helper.assertTrue(land.hall(2).distSqr(land.hall(1)) == 81, "the land changed: hamlets not 9 apart");
		ReliefGameTests.guards(land, 2, 1).forEach(g -> g.setNoAi(true));
		List<Villager> hired = ReliefGameTests.guards(land, 2, 2);
		hired.forEach(g -> {
			g.setNoAi(true);
			ModAttachments.MERCENARY_UNTIL.set(g, level.getGameTime() + 100_000);
		});
		Relief.call(level, land.hall(1));
		List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(bands.stream().noneMatch(b -> b.home().equals(land.at(2))), "1 guard and 2 mercenaries sent a band: " + bands);
		helper.assertTrue(bands.size() == 1 && bands.get(0).capital(), "the capital's band alone: " + bands);

		ReliefGameTests.guards(land, 2, 1).forEach(g -> g.setNoAi(true)); // now 2 real guards
		Relief.forget();
		Relief.RANGE = 9;
		Relief.call(level, land.hall(1));
		bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(bands.stream().anyMatch(b -> b.home().equals(land.at(2)) && b.riders() == 1), "at exactly the reach, 2 guards: " + bands);

		Relief.forget();
		Relief.RANGE = 8;
		Relief.call(level, land.hall(1));
		bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(bands.stream().noneMatch(b -> b.home().equals(land.at(2))), "one block past the reach: " + bands);
		helper.assertTrue(bands.stream().anyMatch(Relief.Band::capital), "the capital didn't come from past the reach: " + bands);

		int[][] sizes = {{2, 1}, {3, 1}, {5, 1}, {6, 2}, {8, 2}, {9, 3}, {12, 3}, {100, 3}};
		for (int[] s : sizes) {
			helper.assertTrue(Relief.band(s[0], 0) == s[1], s[0] + " guards send " + Relief.band(s[0], 0) + ", not " + s[1]);
		}
		helper.assertTrue(Relief.band(9, 2) == 5, "Levies 2 on 9 guards: " + Relief.band(9, 2));
		helper.succeed();
	}

	/**
	 * The capital raided: its members send (hamlet 1 with 2 guards one rider, hamlet 2 with 6 two), the capital sends
	 * nobody to itself however many guards it has. Hamlet 2 is 18 blocks off: in at a reach of 18, out at 17; its ride is
	 * the 30-second floor. With the treasury empty, nobody comes to a raided capital.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaReliefCapital"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaReliefCapital")
	public void aRaidedCapitalIsHelpedByItsMembers(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ServerLevel level = land.level();
		keepTunables(helper);
		ReliefGameTests.treasury(land, 1000);
		ReliefGameTests.guards(land, 0, 9).forEach(g -> g.setNoAi(true));
		ReliefGameTests.guards(land, 1, 2).forEach(g -> g.setNoAi(true));
		ReliefGameTests.guards(land, 2, 6).forEach(g -> g.setNoAi(true));
		int riders = Relief.call(level, land.hall(0));
		List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(0));
		helper.assertTrue(riders == 3 && bands.size() == 2, "riders " + riders + ", bands " + bands);
		helper.assertTrue(bands.stream().noneMatch(b -> b.capital() || b.home().equals(land.at(0))), "the capital sent help to itself: " + bands);
		helper.assertTrue(bands.stream().anyMatch(b -> b.home().equals(land.at(1)) && b.riders() == 1), "hamlet 1's band: " + bands);
		Relief.Band far = bands.stream().filter(b -> b.home().equals(land.at(2))).findFirst().orElse(null);
		helper.assertTrue(far != null && far.riders() == 2 && far.arrive() - far.sent() == Relief.MIN_RIDE, "hamlet 2's band: " + far);
		helper.assertTrue(ReliefGameTests.theRealm(land).treasury() == 700, "treasury " + ReliefGameTests.theRealm(land).treasury());

		Relief.forget();
		Relief.RANGE = 18;
		Relief.call(level, land.hall(0));
		helper.assertTrue(Relief.onTheRoad(level, land.hall(0)).stream().anyMatch(b -> b.home().equals(land.at(2))), "hamlet 2 at exactly the reach: " + Relief.onTheRoad(level, land.hall(0)));
		Relief.forget();
		Relief.RANGE = 17;
		Relief.call(level, land.hall(0));
		bands = Relief.onTheRoad(level, land.hall(0));
		helper.assertTrue(bands.size() == 1 && bands.get(0).home().equals(land.at(1)), "hamlet 2 one past the reach: " + bands);

		Relief.forget();
		Relief.RANGE = 1024;
		ReliefGameTests.treasury(land, 0);
		helper.assertTrue(Relief.call(level, land.hall(0)) == 0 && Relief.onTheRoad(level, land.hall(0)).isEmpty(),
			"unpaid members rode to the capital: " + Relief.onTheRoad(level, land.hall(0)));
		helper.assertTrue(ReliefGameTests.theRealm(land).treasury() == 0, "the treasury went " + ReliefGameTests.theRealm(land).treasury());
		helper.succeed();
	}

	/**
	 * One relief per raid: a night raid on hamlet 1 lasts 120 ticks (six watches) and its realm pays and sends once; the
	 * raid ends, a bandit raid begins, and that raid gets its own relief, paid once again.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaReliefOnce"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaReliefOnce")
	public void oneReliefPerRaidAndANewOneForTheNext(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ServerLevel level = land.level();
		endRaids(helper, land);
		helper.setDayTime(18000);
		ReliefGameTests.treasury(land, 1000);
		ReliefGameTests.guards(land, 2, 6).forEach(g -> g.setNoAi(true));
		Culture bandits = Threats.get(Threats.BANDITS).orElse(null);
		helper.assertTrue(bandits != null, "no bandits culture");
		Zombie first = raider(level, land.hall(1), 3);
		VillageRaids.track(level, land.hall(1), 1);
		int[] tick = {0};
		helper.onEachTick(() -> {
			int t = tick[0]++;
			List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(1));
			long treasury = ReliefGameTests.theRealm(land).treasury();
			if (t >= 30 && t < 120) {
				helper.assertTrue(bands.size() == 2 && bands.stream().mapToInt(Relief.Band::riders).sum() == 3, "tick " + t + ": bands " + bands);
				helper.assertTrue(treasury == 700, "tick " + t + ": the night raid cost " + (1000 - treasury));
			}
			if (t == 120) {
				first.discard();
				ThreatData.get(level).end(land.hall(1));
			}
			if (t == 170) {
				helper.assertTrue(bands.size() == 2 && treasury == 700, "relief came again with no raid: " + bands);
				Zombie second = raider(level, land.hall(1), 3);
				VillageRaids.track(level, land.hall(1), bandits, List.<Mob>of(second));
				helper.assertTrue(VillageRaids.raided(level, land.hall(1), land.hall(1)), "the bandit raid isn't on");
			}
			if (t >= 200 && t < 380) {
				helper.assertTrue(bands.size() == 4 && bands.stream().mapToInt(Relief.Band::riders).sum() == 6, "tick " + t + " (bandit raid): bands " + bands);
				helper.assertTrue(treasury == 400, "tick " + t + ": two raids cost " + (1000 - treasury));
			}
			if (t == 380) {
				helper.succeed();
			}
		});
	}

	/**
	 * With {@code realmRelief} off a raided member gets no one for three watches and pays nothing; switched on while the
	 * raid goes on, the next watch calls. A raided village outside the realm gets no one through the watch either.
	 */
	//$ gametest_ticks_batch AREA '140' '"qaReliefSwitch"'
	@GameTest(template = AREA, timeoutTicks = 140, batch = "qaReliefSwitch")
	public void switchedOnMidRaidTheNextWatchCalls(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 1);
		ServerLevel level = land.level();
		endRaids(helper, land);
		helper.setDayTime(18000);
		ReliefGameTests.treasury(land, 1000);
		Relief.ENABLED = false;
		raider(level, land.hall(1), 3);
		raider(level, land.hall(3), 3);
		VillageRaids.track(level, land.hall(1), 1);
		VillageRaids.track(level, land.hall(3), 1);
		int[] tick = {0};
		helper.onEachTick(() -> {
			int t = tick[0]++;
			if (t < 60) {
				helper.assertTrue(Relief.onTheRoad(level, land.hall(1)).isEmpty(), "help came with realmRelief off");
				helper.assertTrue(ReliefGameTests.theRealm(land).treasury() == 1000, "paid with realmRelief off");
			} else if (t == 60) {
				Relief.ENABLED = true;
			} else if (t >= 85) {
				helper.assertTrue(!Relief.onTheRoad(level, land.hall(1)).isEmpty(), "no call a watch after relief was switched on");
				helper.assertTrue(Relief.onTheRoad(level, land.hall(3)).isEmpty(), "a village in no realm got riders: " + Relief.onTheRoad(level, land.hall(3)));
				if (t == 120) {
					helper.succeed();
				}
			}
		});
	}

	/**
	 * The raid is beaten before the capital's rider gets there: they still arrive, but stay only the minute (here 40 ticks)
	 * and not until dawn, then go with their horse.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaReliefLate"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaReliefLate")
	public void aRaidOverBeforeTheyArriveSendsThemHomeAfterAMinute(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ServerLevel level = land.level();
		keepTunables(helper);
		endRaids(helper, land);
		helper.setDayTime(18000); // dawn is 6,000 ticks away
		ReliefGameTests.treasury(land, 1000);
		Relief.MIN_RIDE = 30;
		Zombie zombie = raider(level, land.hall(1), 3);
		VillageRaids.track(level, land.hall(1), 1);
		helper.assertTrue(Relief.call(level, land.hall(1)) == 1, "not the capital's one rider");
		long arrive = Relief.onTheRoad(level, land.hall(1)).get(0).arrive();
		zombie.discard();
		ThreatData.get(level).end(land.hall(1)); // beaten before they come
		long[] came = {-1};
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			List<Villager> riders = Relief.riders(level, land.hall(1));
			if (came[0] < 0) {
				if (!riders.isEmpty()) {
					came[0] = now;
				} else {
					helper.assertTrue(now <= arrive + 1, "the band never arrived (due " + arrive + ", now " + now + ")");
				}
				return;
			}
			if (now - came[0] == 25) {
				helper.assertTrue(riders.size() == 1, "the rider left too soon or doubled: " + riders.size());
				Long until = ModAttachments.MERCENARY_UNTIL.get(riders.get(0));
				helper.assertTrue(until != null && until <= now + Relief.LINGER, "staying till dawn after the raid: until " + until + ", now " + now);
			}
			if (now - came[0] > Relief.LINGER + 25) {
				helper.assertTrue(riders.isEmpty(), "still here " + (now - came[0]) + " ticks after arriving to no raid");
				helper.assertTrue(level.getEntitiesOfClass(AbstractHorse.class, new AABB(land.hall(1)).inflate(32), h -> h.getTags().contains(Relief.HORSE_TAG)).isEmpty(), "the horse stayed");
				helper.succeed();
			}
		});
	}

	/**
	 * Both bands (the capital's one and hamlet 2's two) arrive together and every line reads right for 1 and 2: the chat to
	 * a player in the village, the raided village's chronicle, the senders' hall lines and the treasury's ledger; hamlet 2
	 * keeps its own six guards. One of hamlet 2's riders falls: its hall says 1 guard away, and the riderless horse goes.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaReliefLines"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaReliefLines")
	public void everyLineReadsRightAndAFallenRidersHorseGoes(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ServerLevel level = land.level();
		keepTunables(helper);
		endRaids(helper, land);
		helper.setDayTime(18000);
		ReliefGameTests.treasury(land, 1000);
		Relief.MIN_RIDE = 30;
		List<Villager> home = ReliefGameTests.guards(land, 2, 6);
		home.forEach(g -> g.setNoAi(true));
		BlockPos h1 = land.hall(1);
		land.owner().teleportTo(h1.getX() + 0.5, h1.getY(), h1.getZ() + 3.5);
		raider(level, h1, 0).moveTo(h1.getX() + 0.5, h1.getY(), h1.getZ() - 2.5);
		VillageRaids.track(level, h1, 1);
		String cap = land.name(0);
		String two = land.name(2);
		String target = land.name(1);
		int[] phase = {0};
		long[] at = {0};
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			List<Villager> riders = Relief.riders(level, h1);
			switch (phase[0]) {
				case 0 -> {
					if (Relief.onTheRoad(level, h1).size() < 2) {
						return;
					}
					List<String> ledger = ledger(land);
					helper.assertTrue(ledger.contains("1 rider from " + cap + " to help " + target), "the ledger, 1 rider: " + ledger);
					helper.assertTrue(ledger.contains("2 riders from " + two + " to help " + target), "the ledger, 2 riders: " + ledger);
					helper.assertTrue(chronicle(land, 0).contains("1 guard rode to help " + target + " in a raid"), "the capital's chronicle: " + chronicle(land, 0));
					phase[0] = 1;
				}
				case 1 -> {
					if (riders.size() < 3) {
						return;
					}
					List<String> heard = land.heard().stream().map(Component::getString).toList();
					helper.assertTrue(heard.contains("1 guard from " + cap + " rides in to help " + target + "!"), "chat, 1 rider: " + heard);
					helper.assertTrue(heard.contains("2 guards from " + two + " ride in to help " + target + "!"), "chat, 2 riders: " + heard);
					List<String> chron = chronicle(land, 1);
					helper.assertTrue(chron.contains("1 guard from " + cap + " rode in to help against the raid"), "hamlet 1's chronicle, 1: " + chron);
					helper.assertTrue(chron.contains("2 guards from " + two + " rode in to help against the raid"), "hamlet 1's chronicle, 2: " + chron);
					helper.assertTrue(("1 guard away helping " + target).equals(hallLine(land, 0)), "the capital's hall: " + hallLine(land, 0));
					helper.assertTrue(("2 guards away helping " + target).equals(hallLine(land, 2)), "hamlet 2's hall: " + hallLine(land, 2));
					helper.assertTrue(home.stream().allMatch(Villager::isAlive), "hamlet 2 lost its own guards to the relief");
					Villager fallen = riders.stream().filter(r -> ModAttachments.RELIEF_RIDER.get(r).home().equals(land.at(2))).findFirst().orElseThrow();
					fallen.kill();
					at[0] = now;
					phase[0] = 2;
				}
				case 2 -> {
					if (now - at[0] < 5) {
						return;
					}
					helper.assertTrue(("1 guard away helping " + target).equals(hallLine(land, 2)), "hamlet 2's hall after a rider fell: " + hallLine(land, 2));
					helper.assertTrue(riders.size() == 2, "riders after one fell: " + riders.size());
					phase[0] = 3;
				}
				case 3 -> {
					long loose = level.getEntitiesOfClass(AbstractHorse.class, new AABB(h1).inflate(32), h -> h.getTags().contains(Relief.HORSE_TAG) && !h.isVehicle()).size();
					if (loose > 0) {
						helper.assertTrue(now - at[0] < 130, "the fallen rider's horse is still here after " + (now - at[0]) + " ticks");
						return;
					}
					phase[0] = 4;
					helper.succeed();
				}
				default -> {
				}
			}
		});
	}

	/**
	 * Saved and loaded mid-relief (a rider written out and read back into the world, without the horse): while the raid
	 * goes on the loaded rider stays and is still the village's relief; when it ends they leave after the minute.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaReliefReload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaReliefReload")
	public void aRiderLoadedMidReliefStillGoesHomeAfterTheRaid(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ServerLevel level = land.level();
		keepTunables(helper);
		endRaids(helper, land);
		helper.setDayTime(18000);
		ReliefGameTests.treasury(land, 1000);
		Relief.MIN_RIDE = 30;
		BlockPos h1 = land.hall(1);
		Zombie zombie = raider(level, h1, 3);
		VillageRaids.track(level, h1, 1);
		Villager[] loaded = {null};
		long[] at = {0};
		int[] phase = {0};
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			List<Villager> riders = Relief.riders(level, h1);
			switch (phase[0]) {
				case 0 -> {
					if (riders.isEmpty()) {
						return;
					}
					Villager rider = riders.get(0);
					CompoundTag saved = rider.saveWithoutId(new CompoundTag());
					if (rider.getVehicle() != null) {
						rider.getVehicle().discard();
					}
					rider.discard();
					Villager copy = EntityType.VILLAGER.create(level);
					copy.load(saved);
					copy.setUUID(UUID.randomUUID());
					copy.stopRiding();
					level.addFreshEntity(copy);
					loaded[0] = copy;
					at[0] = now;
					phase[0] = 1;
				}
				case 1 -> {
					if (now - at[0] < 80) {
						helper.assertTrue(loaded[0].isAlive(), "the loaded rider left mid-raid after " + (now - at[0]) + " ticks");
						return;
					}
					helper.assertTrue(Relief.isRider(loaded[0]) && Relief.riders(level, h1).contains(loaded[0]), "the loaded villager isn't hamlet 1's relief");
					zombie.discard();
					ThreatData.get(level).end(h1);
					at[0] = now;
					phase[0] = 2;
				}
				case 2 -> {
					if (loaded[0].isAlive()) {
						helper.assertTrue(now - at[0] <= Relief.LINGER + 25, "the loaded rider stayed " + (now - at[0]) + " ticks past the raid");
						return;
					}
					helper.assertTrue(now - at[0] >= Relief.LINGER - 20, "the loaded rider left at once, not after the minute: " + (now - at[0]));
					phase[0] = 3;
					helper.succeed();
				}
				default -> {
				}
			}
		});
	}
}
