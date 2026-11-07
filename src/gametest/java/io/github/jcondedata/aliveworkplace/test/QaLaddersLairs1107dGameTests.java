package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane d, 2026-11-07): a mutation check of {@code threat/Ladders}, {@code threat/Lairs} and
 * {@code threat/HostileSpawns}, with tests written from the lairs page (docs/wiki/lairs.md, "The camp has a strength"):
 * "A raid takes its raiders from it: never more than the usual raid size, never more than the camp has at home. Those
 * alive when the raid ends go back and rejoin; the dead are gone." Each test puts a lair far off (no camp built, its
 * ground never loaded, so nobody is mustered) and walks its strength through a raid going out and coming back.
 */
public class QaLaddersLairs1107dGameTests implements FabricGameTest {
	private static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");

	private static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(0, 1, 0));
	}

	/** A lair of {@code culture} by {@code hall} far out of loaded ground, strength {@code strength}, nobody out. */
	private static Lairs.Lair put(ServerLevel level, BlockPos hall, ResourceLocation culture, int strength) {
		long today = Chronicle.day(level);
		Lairs.Lair lair = new Lairs.Lair(hall.offset(20000, 0, 20000), hall, new UUID(11, 7), today, culture, strength, today, 0, 0, 0);
		Lairs.putForTest(level, lair);
		return lair;
	}

	private static Lairs.Lair now(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		return Lairs.near(level, hall).orElseThrow(() -> new AssertionError("the lair is gone"));
	}

	/**
	 * Eight at the bandit camp, five go raiding: three are at home and the camp sends no more than that to the next raid.
	 * Three of the five come back alive: they rejoin (strength 6, nobody out, 6 at home) and the two dead are lost.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLairBack1107d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLairBack1107d")
	public void survivorsOfARaidRejoinTheCampAndTheDeadAreLost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		try {
			put(level, hall, Threats.BANDITS, 8);
			Lairs.sent(level, hall, 5);
			Lairs.Lair out = now(helper, level, hall);
			helper.assertTrue(out.out() == 5 && out.home() == 3 && out.strength() == 8, "after 5 left: out " + out.out() + ", home " + out.home() + ", strength " + out.strength());
			helper.assertTrue(Lairs.raidSize(out, 10) == 3, "a raid of 10 from a camp with 3 at home takes " + Lairs.raidSize(out, 10));
			Lairs.back(level, hall, Threats.BANDITS, 3);
			Lairs.Lair back = now(helper, level, hall);
			helper.assertTrue(back.strength() == 6, "3 of 5 came back to a camp of 8: strength " + back.strength() + ", not 6");
			helper.assertTrue(back.out() == 0 && back.home() == 6, "after the raid: out " + back.out() + ", home " + back.home());
			helper.assertTrue(back.lost() == 2, "2 fell, the camp lost " + back.lost());
			helper.assertTrue(Lairs.raidSize(back, 4) == 4, "a raid of 4 from a camp with 6 at home takes " + Lairs.raidSize(back, 4));
		} finally {
			QaLairGrowthGameTests.drop(level, hall);
		}
		helper.succeed();
	}

	/**
	 * Only those who went come back: a raid's end that counts more survivors than went out (the captain counted, a
	 * raider from elsewhere) never makes the camp stronger than it was, and the end of another culture's raid, or a
	 * second end of the same raid, changes nothing.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLairBack1107d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLairBack1107d")
	public void aRaidsEndNeverMakesTheCampStrongerThanItWas(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		try {
			put(level, hall, Threats.BANDITS, 7);
			Lairs.sent(level, hall, 4);
			Lairs.back(level, hall, WARBAND, 0);
			Lairs.Lair other = now(helper, level, hall);
			helper.assertTrue(other.strength() == 7 && other.out() == 4 && other.lost() == 0,
				"another culture's raid ended and the bandits' camp changed: strength " + other.strength() + ", out " + other.out() + ", lost " + other.lost());
			Lairs.back(level, hall, Threats.BANDITS, 9);
			Lairs.Lair back = now(helper, level, hall);
			helper.assertTrue(back.strength() == 7 && back.out() == 0 && back.lost() == 0,
				"4 went out and 9 'survived': strength " + back.strength() + " (not 7), out " + back.out() + ", lost " + back.lost());
			Lairs.back(level, hall, Threats.BANDITS, 0);
			Lairs.Lair again = now(helper, level, hall);
			helper.assertTrue(again.strength() == 7 && again.lost() == 0, "a second end of the raid took raiders: strength " + again.strength() + ", lost " + again.lost());
		} finally {
			QaLairGrowthGameTests.drop(level, hall);
		}
		helper.succeed();
	}

	/** The halls listed {@code unswept} in a saved lairs file. */
	private static java.util.List<BlockPos> unswept(net.minecraft.nbt.CompoundTag tag) {
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		net.minecraft.nbt.ListTag list = tag.getList("unswept", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			out.add(BlockPos.of(list.getCompound(i).getLong("hall")));
		}
		return out;
	}

	/**
	 * B209 (lairs.md, "Camps from before B209"): a save without the {@code b209} mark lists every village that ever broke
	 * up a camp as unswept, and the village stays unswept until the hall's round has looked at all its ring. A world from
	 * before B209 saved again before that round (a restart soon after the update) must still have the village to sweep:
	 * the list is saved with the {@code b209} mark, and read back it is the same. Kills the survivor at Lairs.java:1080
	 * (the unswept list never written).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLairUnswept1107d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLairUnswept1107d")
	public void aVillageToSweepFromBeforeB209StaysListedOverARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper).offset(30000, 0, 30000); // (never loaded: no round could sweep it)
		Lairs.Data data = Lairs.Data.get(level);
		net.minecraft.nbt.CompoundTag kept = data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
		try {
			net.minecraft.nbt.CompoundTag old = new net.minecraft.nbt.CompoundTag();
			net.minecraft.nbt.ListTag broken = new net.minecraft.nbt.ListTag();
			net.minecraft.nbt.CompoundTag rest = new net.minecraft.nbt.CompoundTag();
			rest.putLong("hall", hall.asLong());
			rest.putLong("day", Chronicle.day(level) - 30);
			broken.add(rest);
			old.put("broken_up", broken);
			old.put("camps", new net.minecraft.nbt.ListTag());
			data.read(old); // a lairs file from before B209: no mark, no unswept list
			net.minecraft.nbt.CompoundTag first = data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
			helper.assertTrue(first.getBoolean("b209"), "the file saved after the update has no b209 mark");
			helper.assertTrue(unswept(first).contains(hall), "the village that broke up a camp before B209 isn't saved as unswept: " + unswept(first));
			data.read(first); // the restart
			net.minecraft.nbt.CompoundTag second = data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
			helper.assertTrue(unswept(second).equals(java.util.List.of(hall)), "after a restart the villages to sweep are " + unswept(second) + ", not only " + hall);
		} finally {
			data.read(kept);
		}
		helper.succeed();
	}

	/**
	 * "A lair whose captain is gone is broken up" at the hall's round (Lairs.round), also while a raid of the camp's own
	 * culture is under way: only a captain who leads that raid himself is away at the village (the Warlord at a City,
	 * 32.7); the bandits' chief stays at his camp, so his camp's ground is where he is looked for. His camp in loaded
	 * ground, he is gone (taken without dying), the village isn't quiet: the camp is broken up and the village rests.
	 * Kills the survivor at Lairs.java:241 (a raid of the same culture taken as the captain leading it).
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:big_area"' '40' '"qaLairChiefGoneInRaid1107d"'
	@GameTest(template = "aliveworkplace_test:big_area", timeoutTicks = 40, batch = "qaLairChiefGoneInRaid1107d")
	public void aCampWhoseChiefIsGoneBreaksUpEvenWhileItsRaidIsOn(GameTestHelper helper) {
		BlockPos hall = QaThreatLevels1031dGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = QaThreatLevels1031dGameTests.camp(helper, hall);
			helper.assertTrue(lair.culture().equals(Threats.BANDITS) && !Lairs.leads(level, lair), "setup: not a bandit camp whose chief stays home");
			helper.assertTrue(!Threats.quiet(level, hall), "setup: the village is quiet");
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.track(level, hall, Threats.get(Threats.BANDITS).orElseThrow(), java.util.List.of());
			helper.assertTrue(io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).raid(hall).isPresent(), "setup: no raid under way");
			net.minecraft.world.entity.Entity chief = level.getEntity(lair.captain());
			helper.assertTrue(chief != null, "setup: the chief isn't at his camp");
			chief.discard();
			Lairs.round(level, hall, true, QaThreatLevels1031dGameTests.always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the chief is gone from his camp, a bandit raid is on, and the camp still stands");
			helper.assertTrue(Lairs.resting(level, hall) > 0, "the camp went but the village doesn't rest");
			helper.succeed();
		});
	}

	/**
	 * A camp sends no more than it has: two raids of 5 leaving a camp of 6 never have more than 6 out, and with all of
	 * them dead the camp has nobody left (strength 0), so it sends no raid until it grows.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLairBack1107d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLairBack1107d")
	public void aCampSendsNoMoreThanItHasAndAllLostLeavesItEmpty(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		try {
			put(level, hall, Threats.BANDITS, 6);
			Lairs.sent(level, hall, 5);
			Lairs.sent(level, hall, 5);
			Lairs.Lair out = now(helper, level, hall);
			helper.assertTrue(out.out() == 6 && out.home() == 0, "a camp of 6 has " + out.out() + " out and " + out.home() + " at home");
			helper.assertTrue(Lairs.raidSize(out, 5) == 0, "a camp with nobody at home sends " + Lairs.raidSize(out, 5));
			Lairs.sent(level, hall, 0);
			helper.assertTrue(now(helper, level, hall).out() == 6, "sending nobody changed who is out");
			Lairs.back(level, hall, Threats.BANDITS, 0);
			Lairs.Lair empty = now(helper, level, hall);
			helper.assertTrue(empty.strength() == 0 && empty.lost() == 6 && empty.home() == 0,
				"all 6 fell: strength " + empty.strength() + ", lost " + empty.lost() + ", home " + empty.home());
			helper.assertTrue(Lairs.raidSize(empty, 5) == 0, "an empty camp sends " + Lairs.raidSize(empty, 5));
		} finally {
			QaLairGrowthGameTests.drop(level, hall);
		}
		helper.succeed();
	}
}
