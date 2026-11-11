package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-10, after B1146's fix): 32.14's warning with 32.3's lairs. A village warned that the bandit
 * camp's band strikes tonight ("Chief Harl's band will strike tomorrow night"), whose players then go out and bring the
 * chief down that morning: the camp is broken up, so the attack can't come (VillageRaids starts a lair culture's raid
 * only while its lair stands). Expected, as B1146 put it for an attack called off: nothing braces on the day for an
 * attack that won't come; the Defense page doesn't still show it; at dusk no one is sent in.
 * <p>
 * The control half of each test: with the camp standing the same village is braced from noon (so the setup warns).
 */
public class QaLairFallWarning1110dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos CAMP = new BlockPos(20, 1, 20);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	/** A hall (radius 24), a bandit camp standing by it, and tomorrow's attack from it warned of at dusk of day 11. */
	private static Lairs.Lair warnedByTheCamp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		VillageHalls.RADIUS = 24;
		Lairs.ENABLED = true;
		WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
		VillageRaids.ENABLED = true; // (apply() turns raids off under GameTests; the after() puts the config back)
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(Lairs.BANDIT_TAG)
				|| m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			Lairs.forget(level);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.forget();
			BattleStations.forget();
			Lairs.ENABLED = lairs;
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "setup: no hall");
		ThreatData.get(level).forgetClock(hall);
		helper.assertTrue(Threats.get(Threats.BANDITS).map(c -> c.needsLair()).orElse(false), "setup: the bandits aren't a lair culture");
		level.setDayTime(BASE + 1000);
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), Threats.BANDITS);
		helper.assertTrue(lair != null && Lairs.of(level, hall, Threats.BANDITS).isPresent(), "setup: no bandit camp: " + lair);
		// Dusk of day 11: the clock sets tomorrow night's attack from the camp, and the village is warned of it.
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.BANDITS, NORTH, 13600));
		ThreatData.get(level).warned(hall, 12);
		return Lairs.of(level, hall, Threats.BANDITS).orElseThrow();
	}

	private static void hallRound(ServerLevel level, BlockPos hall) {
		VillageRaids.tick(level, hall, 10, 1, -1, d -> {
		});
		Warnings.round(level, hall, 10, 1);
	}

	private static void bringDownTheChief(GameTestHelper helper, Lairs.Lair lair) {
		ServerLevel level = helper.getLevel();
		Entity chief = level.getEntity(lair.captain());
		helper.assertTrue(chief instanceof Mob, "setup: the camp's chief isn't loaded: " + chief);
		chief.kill();
		helper.assertTrue(Lairs.of(level, lair.hall(), Threats.BANDITS).isEmpty(), "setup: the chief fell but his camp still stands");
	}

	/**
	 * Warned of the camp's attack; at noon of the day the village is braced (control). The players then bring the chief
	 * down, breaking up the camp: from then on nothing is braced for his band, and the Defense page shows no attack.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLairFallNoon1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLairFallNoon1110d")
	public void aWarnedVillageWhoseCampIsBrokenUpShutsNothingOnItsDay(GameTestHelper helper) {
		Lairs.Lair lair = warnedByTheCamp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = lair.hall();
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // day 12, just past noon
		hallRound(level, hall);
		helper.assertTrue(Warnings.ready(level, hall), "setup: the warned village isn't braced at noon with the camp standing: warned "
			+ ThreatData.get(level).warned(hall) + ", day " + Chronicle.day(level) + ", attacks " + ThreatData.get(level).attacks(hall));
		bringDownTheChief(helper, lair);
		level.setDayTime(BASE + VillageNeeds.DAY + 7000); // the afternoon
		hallRound(level, hall);
		List<String> wrong = new ArrayList<>();
		if (Warnings.ready(level, hall)) {
			wrong.add("gates and portcullis kept shut (Warnings.ready)");
		}
		if (Warnings.warned(level, hall).isPresent()) {
			wrong.add("the Defense page still shows the attack " + Warnings.warned(level, hall).get());
		}
		helper.assertTrue(wrong.isEmpty(), "the bandit camp was broken up (its chief fell) so its band can't come, yet the village is still braced for it: " + wrong);
		helper.succeed();
	}

	/**
	 * The chief falls at dusk of the warned day, before the attack's hour: no guards to battle stations, no villager sent
	 * in, and past the attack's hour (no raid comes, as there's no camp to send it) still none.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLairFallDusk1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLairFallDusk1110d")
	public void aCampBrokenUpAtDuskSendsNoOneInForItsBand(GameTestHelper helper) {
		Lairs.Lair lair = warnedByTheCamp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = lair.hall();
		Villager townsman = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		townsman.setNoAi(true);
		townsman.setInvulnerable(true);
		level.setDayTime(BASE + VillageNeeds.DAY + 12100); // dusk of day 12
		helper.assertTrue(Warnings.evening(level, hall), "setup: the warned evening isn't on with the camp standing");
		bringDownTheChief(helper, lair);
		level.setDayTime(BASE + VillageNeeds.DAY + 12600);
		townsman.getBrain().eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
		hallRound(level, hall);
		List<String> wrong = new ArrayList<>();
		if (Warnings.evening(level, hall)) {
			wrong.add("guards at battle stations at dusk (Warnings.evening)");
		}
		if (townsman.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isPresent()) {
			wrong.add("a villager sent indoors at dusk");
		}
		level.setDayTime(BASE + VillageNeeds.DAY + 14000); // past the attack's hour
		hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "setup: a raid came from a broken camp");
		if (Warnings.evening(level, hall)) {
			wrong.add("still at battle stations past the attack's hour with no raid");
		}
		helper.assertTrue(wrong.isEmpty(), "the bandit camp was broken up at dusk, yet the village braces all night for its band: " + wrong);
		helper.succeed();
	}
}
