package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane D, 2026-11-10) for B1126: who got over a village wall by a siege ladder is saved with the siege (ROADMAP 32.5;
 * wiki sieges.md). Written from the bug's Expected ("a raider over the wall by ladder stays let go after a save and
 * reload") and the spec, beyond QaLadderOver1109dGameTests' one raider and one save: a raider still outside is not
 * taken for one inside; a siege saved before the fix loads with nobody over and goes on; a new siege at the same village
 * starts with nobody over; two saves in a row keep the set, and a raider over the wall that died breaks nothing when
 * the raid is over. Uses QaSiegeConverts1109dGameTests' yard (zombies with the ladders tactic, a Stone Wall, a shut
 * Palisade Gate, a villager in a stone cell); a "restart" is the threat data saved and read back and the ladders' and
 * the director's memory forgotten, as QaLadderOver1109dGameTests does.
 */
public class QaLadderOverSaves1110dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	/** A zombie of the yard's culture with {@code role}, at (x, 2, z) of the test, after the villager. */
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
			zombie.setBaby(false);
		}
		mob.getRandom().setSeed(1L);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A save and load as a restart does: the threat data written and read back, what the ladders and director kept in memory gone. */
	private static void restart(QaSiegeLadderBreach1103dGameTests.Yard yard) {
		ThreatData.get(yard.level()).roundTrip(yard.level().registryAccess());
		Ladders.forget();
		Sieges.forget();
	}

	private static boolean over(QaSiegeLadderBreach1103dGameTests.Yard yard, Mob mob) {
		return Ladders.over(yard.level(), yard.hall(), mob.getUUID());
	}

	/** Whether the follower has been over the wall by the ladder and inside for 60 ticks (marked in {@code overAt}); fails past tick 2000. */
	private static boolean settledInside(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, Mob follower, Mob climber, long[] overAt) {
		long tick = helper.getTick();
		if (overAt[0] < 0 && over(yard, follower) && QaSiegeLadderBreach1103dGameTests.inside(helper, follower)) {
			overAt[0] = tick;
		}
		if (overAt[0] >= 0 && tick - overAt[0] >= 60) {
			return true;
		}
		if (tick > 2000) {
			helper.fail("the follower never got over by the ladder and in (the test would prove nothing): follower "
				+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower) + "; climber "
				+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber));
		}
		return false;
	}

	/**
	 * Two raiders on either side of the wall at the save: the follower came over by the ladder, a third zombie (one who
	 * only follows, held still until the restart) never left the ground outside. After the save and load only the one
	 * inside is let go: it is never handed the ladder and is counted over the wall; the one outside isn't counted over
	 * and is still sent to the standing ladder.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverSavesTwoSides1110d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverSavesTwoSides1110d")
	public void onlyTheRaiderInsideIsLetGoAfterASaveAndTheOneOutsideStillGoesToTheLadder(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		Mob follower = raider(helper, yard, Culture.Role.MELEE, 22.5, 23.5);
		Mob outside = raider(helper, yard, Culture.Role.MELEE, 8.5, 24.5);
		outside.setNoAi(true);
		VillageRaids.track(yard.level(), yard.hall(), yard.culture(), List.of(climber, follower, outside));
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(siege.breach != null && !Sieges.open(siege), "the siege has no shut gate: the test would prove nothing");
		long[] overAt = {-1};
		long[] restarted = {-1};
		long[] sent = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (restarted[0] < 0) {
				if (settledInside(helper, yard, follower, climber, overAt)) {
					helper.assertTrue(!over(yard, outside), "the zombie still outside is counted over the wall before the save: "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, outside));
					helper.assertTrue(QaSiegeLadderBreach1103dGameTests.standing(yard.siege(helper)), "no ladder stands at the save");
					restart(yard);
					outside.setNoAi(false);
					restarted[0] = tick;
				}
				return;
			}
			long since = tick - restarted[0];
			helper.assertTrue(!QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower),
				"after a save and load the zombie inside was handed the ladder again: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			helper.assertTrue(since < 2 * Ladders.EVERY || over(yard, follower),
				"after a save and load the zombie inside isn't counted over the wall: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			if (sent[0] < 0) {
				helper.assertTrue(!over(yard, outside), "after a save and load the zombie still outside is counted over the wall: "
					+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, outside));
				if (QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, outside)) {
					sent[0] = tick;
				} else if (since > 300) {
					helper.fail("300 ticks after a save and load the zombie outside was never sent to the standing ladder: "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, outside));
				}
			}
			if (sent[0] >= 0 && since >= 200) {
				LOGGER.info("qa 1110d two sides: over at tick {}, saved at {}, the one outside sent to the ladder {} ticks after the load, inside never handed it", overAt[0], restarted[0], sent[0] - restarted[0]);
				helper.succeed();
			}
		});
	}

	/**
	 * A siege saved by a version before B1126 has no {@code over_by_ladder}: it loads with nobody over the wall, and the
	 * siege goes on (its rungs still recorded, the gate still shut, the zombie inside counted over again once the ladders
	 * see it on the wall's inner side, and never back out over the wall). The next save writes who is over again.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverSavesOld1110d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverSavesOld1110d")
	public void aSiegeSavedBeforeTheFixLoadsWithNobodyOverAndGoesOn(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		long[] overAt = {-1};
		long[] restarted = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			ServerLevel level = yard.level();
			ThreatData data = ThreatData.get(level);
			if (restarted[0] < 0) {
				if (settledInside(helper, yard, follower, climber, overAt)) {
					CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
					ListTag sieges = Nbt.getList(saved, "sieges", Tag.TAG_COMPOUND);
					helper.assertTrue(!sieges.isEmpty(), "no siege in the save");
					for (int i = 0; i < sieges.size(); i++) {
						Nbt.compoundAt(sieges, i).remove("over_by_ladder"); // (as an older version wrote it)
					}
					data.read(saved);
					Ladders.forget();
					Sieges.forget();
					restarted[0] = tick;
				}
				return;
			}
			long since = tick - restarted[0];
			ThreatData.Siege siege = yard.siege(helper);
			if (since <= 2 * Ladders.EVERY + 1) {
				helper.assertTrue(!over(yard, follower) && !over(yard, climber), "a siege saved without over_by_ladder loaded with someone over the wall: "
					+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			}
			helper.assertTrue(!siege.ladders.isEmpty() && !Sieges.open(siege), "after loading the older save the siege lost its rungs or its shut gate");
			helper.assertTrue(follower.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() < 15,
				"after loading the older save the zombie inside went back out over the wall: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			if (since > 2 * Ladders.EVERY + 1 && over(yard, follower)) {
				CompoundTag again = data.save(new CompoundTag(), level.registryAccess());
				ListTag sieges = Nbt.getList(again, "sieges", Tag.TAG_COMPOUND);
				boolean written = false;
				for (int i = 0; i < sieges.size(); i++) {
					ListTag climbed = Nbt.getList(Nbt.compoundAt(sieges, i), "over_by_ladder", Tag.TAG_COMPOUND);
					for (int j = 0; j < climbed.size(); j++) {
						written |= follower.getUUID().toString().equals(Nbt.getString(Nbt.compoundAt(climbed, j), "id"));
					}
				}
				helper.assertTrue(written, "the next save after loading the older one doesn't hold the zombie over the wall");
				LOGGER.info("qa 1110d older save: loaded with nobody over at tick {}, the zombie inside over again {} ticks later and saved", restarted[0], since);
				helper.succeed();
			} else if (since > 600) {
				helper.fail("600 ticks after loading the older save the zombie inside is still not counted over the wall: "
					+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
			}
		});
	}

	/**
	 * The raid is called off once a zombie came over the wall (its ladders taken away), and a new raid comes to the same
	 * village: the new siege remembers nobody over the wall, before and after a save and load, and its zombies are not
	 * taken for ones inside.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverSavesNewSiege1110d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverSavesNewSiege1110d")
	public void aNewSiegeAtTheSameVillageRemembersNobodyOver(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege first = yard.siege(helper);
		long[] overAt = {-1};
		long[] began = {-1};
		List<Mob> fresh = new ArrayList<>();
		List<BlockPos> rungs = new ArrayList<>();
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			ServerLevel level = yard.level();
			if (began[0] < 0) {
				if (settledInside(helper, yard, follower, climber, overAt)) {
					rungs.addAll(yard.siege(helper).ladders);
					helper.assertTrue(VillageRaids.callOff(level, yard.hall()), "no raid to call off");
					for (BlockPos pos : rungs) {
						helper.assertTrue(!level.getBlockState(pos).is(Blocks.LADDER), "a ladder of the called-off siege is still up at "
							+ QaSiegeLadderBreach1103dGameTests.rel(helper, pos));
					}
					fresh.add(raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5));
					fresh.add(raider(helper, yard, Culture.Role.MELEE, 22.5, 23.5));
					VillageRaids.track(level, yard.hall(), yard.culture(), List.copyOf(fresh));
					ThreatData.Siege second = yard.siege(helper);
					helper.assertTrue(second != first && !second.over, "no new siege began at the village");
					began[0] = tick;
				}
				return;
			}
			long since = tick - began[0];
			ThreatData.Siege siege = yard.siege(helper);
			List<UUID> old = List.of(climber.getUUID(), follower.getUUID());
			for (UUID id : old) {
				helper.assertTrue(!Ladders.over(level, yard.hall(), id), "the new siege remembers a zombie of the last one over the wall");
			}
			helper.assertTrue(siege.overByLadder.stream().noneMatch(old::contains), "the new siege's saved set holds a zombie of the last one: " + siege.overByLadder);
			for (Mob mob : fresh) {
				if (mob.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() > 16) {
					helper.assertTrue(!over(yard, mob), "a zombie of the new siege still outside is counted over the wall: "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, mob));
				}
			}
			if (since == 60) {
				restart(yard);
			}
			if (since >= 120) {
				LOGGER.info("qa 1110d new siege: {} rungs taken away at the call-off, new siege set {} after a save", rungs.size(), siege.overByLadder);
				helper.succeed();
			}
		});
	}

	/**
	 * Two saves and loads in a row keep the zombie inside over the wall (never handed the ladder). Then it dies inside,
	 * and the world is saved and loaded with it in the set: nothing breaks, the climber left is not taken for one inside
	 * unless it got over, and when the raid is called off every ladder of the siege is gone.
	 */
	//$ gametest_ticks_batch AREA '2800' '"qaLadderOverSavesTwice1110d"'
	@GameTest(template = AREA, timeoutTicks = 2800, batch = "qaLadderOverSavesTwice1110d")
	public void twoSavesKeepTheRaiderOverAndOneThatDiedBreaksNothing(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeConverts1109dGameTests.yard(helper);
		Mob[] mobs = QaSiegeConverts1109dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		long[] overAt = {-1};
		long[] first = {-1};
		long[] climberOver = {-1};
		List<BlockPos> rungs = new ArrayList<>();
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			ServerLevel level = yard.level();
			if (first[0] < 0) {
				if (settledInside(helper, yard, follower, climber, overAt)) {
					climberOver[0] = over(yard, climber) ? 1 : 0;
					restart(yard);
					first[0] = tick;
				}
				return;
			}
			long since = tick - first[0];
			if (since == 40) {
				restart(yard); // (the second, before the ladders ran again for long)
			}
			if (since < 200) {
				helper.assertTrue(!QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower),
					"after two saves and loads the zombie inside was handed the ladder again: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				helper.assertTrue(since < 40 + 2 * Ladders.EVERY || over(yard, follower),
					"after two saves and loads the zombie inside isn't counted over the wall: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				if (climberOver[0] == 1) {
					helper.assertTrue(since < 40 + 2 * Ladders.EVERY || over(yard, climber),
						"after two saves and loads the climber that was over the wall isn't any more: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber));
				}
				return;
			}
			if (since == 200) {
				follower.kill(); // (dies inside, still in the set)
				helper.assertTrue(!follower.isAlive(), "the follower didn't die");
				restart(yard);
				return;
			}
			ThreatData.Siege siege = yard.siege(helper);
			helper.assertTrue(!siege.over, "the siege ended when the zombie over the wall died and the world was saved and loaded");
			if (climberOver[0] == 0 && QaSiegeLadderBreach1103dGameTests.upTheLadder(climber, QaSiegeLadderBreach1103dGameTests.foot(siege))) {
				climberOver[0] = 2; // (climbing now; it counts once it is over)
			}
			if (since == 400) {
				helper.assertTrue(climberOver[0] != 0 || !over(yard, climber) || QaSiegeLadderBreach1103dGameTests.inside(helper, climber),
					"a climber that never got over is counted over the wall after a save with a dead zombie in the set: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber));
				rungs.addAll(siege.ladders);
				helper.assertTrue(!rungs.isEmpty(), "no rungs recorded with the siege after the saves");
				helper.assertTrue(VillageRaids.callOff(level, yard.hall()), "no raid to call off");
				for (BlockPos pos : rungs) {
					helper.assertTrue(!level.getBlockState(pos).is(Blocks.LADDER), "a ladder of the siege is still up at "
						+ QaSiegeLadderBreach1103dGameTests.rel(helper, pos) + " after the raid was called off");
				}
				LOGGER.info("qa 1110d twice: kept over through two loads, a dead one in the set, climber state {}, {} rungs gone at the call-off", climberOver[0], rungs.size());
				helper.succeed();
			}
		});
	}
}
