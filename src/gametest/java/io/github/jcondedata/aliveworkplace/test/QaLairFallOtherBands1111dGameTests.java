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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * QA (lane D, 2026-11-11), B1155's fix from its spec: breaking up a lair calls off the attacks of that lair's band and
 * its warning, while other bands' attacks and warnings stay. Covers a village warned of another culture's attack (still
 * braced after the camp falls), the camp broken up the day before the warned day, and a later attack of another band
 * kept on the clock.
 */
public class QaLairFallOtherBands1111dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos CAMP = new BlockPos(20, 1, 20);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	/** A hall (radius 24) and a bandit camp standing by it, at day 10; the clock's attacks are set by each test. */
	private static Lairs.Lair camp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		VillageHalls.RADIUS = 24;
		Lairs.ENABLED = true;
		WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
		VillageRaids.ENABLED = true;
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
		level.setDayTime(BASE + 1000);
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), Threats.BANDITS);
		helper.assertTrue(lair != null && Lairs.of(level, hall, Threats.BANDITS).isPresent(), "setup: no bandit camp: " + lair);
		level.setDayTime(BASE + 12100); // the camp stands; the tests roll the clock
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

	private static List<ResourceLocation> cultures(ServerLevel level, BlockPos hall) {
		return ThreatData.get(level).attacks(hall).stream().map(ThreatData.Attack::culture).toList();
	}

	/** Warned of monsters on day 12 (bandits come on day 13): the camp falls at noon of day 12; the monsters' warning stays. */
	//$ gametest_ticks_batch AREA '60' '"qaLairFallMonstersWarned1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLairFallMonstersWarned1111d")
	public void anotherBandsWarningStaysWhenTheCampFalls(GameTestHelper helper) {
		Lairs.Lair lair = camp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = lair.hall();
		level.setDayTime(BASE + 12100); // dusk of day 11
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.MONSTERS, NORTH, 13600));
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(13, Threats.BANDITS, NORTH, 13600));
		ThreatData.get(level).warned(hall, 12);
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon of day 12
		hallRound(level, hall);
		helper.assertTrue(Warnings.ready(level, hall), "setup: the village warned of monsters isn't braced at noon");
		bringDownTheChief(helper, lair);
		level.setDayTime(BASE + VillageNeeds.DAY + 7000);
		hallRound(level, hall);
		List<String> wrong = new ArrayList<>();
		if (!Warnings.ready(level, hall)) {
			wrong.add("no longer braced for tonight's monsters");
		}
		if (Warnings.warned(level, hall).isEmpty()) {
			wrong.add("the Defense page no longer shows tonight's monster attack");
		}
		if (!cultures(level, hall).contains(Threats.MONSTERS)) {
			wrong.add("the monsters' attack left the clock: " + cultures(level, hall));
		}
		if (cultures(level, hall).contains(Threats.BANDITS)) {
			wrong.add("the broken camp's band still has an attack on the clock: " + cultures(level, hall));
		}
		helper.assertTrue(wrong.isEmpty(), "the bandit camp fell, which should only call off the bandits' attack: " + wrong);
		helper.succeed();
	}

	/** Warned at dusk of day 11 of the bandits' attack on day 12; the chief falls that same evening: nothing braces on day 12. */
	//$ gametest_ticks_batch AREA '60' '"qaLairFallDayBefore1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLairFallDayBefore1111d")
	public void aCampBrokenUpTheDayBeforeBracesNothingOnTheDay(GameTestHelper helper) {
		Lairs.Lair lair = camp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = lair.hall();
		level.setDayTime(BASE + 12100); // dusk of day 11
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.BANDITS, NORTH, 13600));
		ThreatData.get(level).warned(hall, 12);
		helper.assertTrue(Warnings.warned(level, hall).isPresent(), "setup: the Defense page doesn't show the warned attack");
		level.setDayTime(BASE + 14000);
		bringDownTheChief(helper, lair);
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon of day 12
		hallRound(level, hall);
		List<String> wrong = new ArrayList<>();
		if (Warnings.ready(level, hall)) {
			wrong.add("gates and portcullis kept shut at noon");
		}
		if (Warnings.warned(level, hall).isPresent()) {
			wrong.add("the Defense page shows the attack " + Warnings.warned(level, hall).get());
		}
		level.setDayTime(BASE + VillageNeeds.DAY + 12600); // dusk of day 12
		hallRound(level, hall);
		if (Warnings.evening(level, hall)) {
			wrong.add("battle stations at dusk");
		}
		helper.assertTrue(wrong.isEmpty(), "the camp was broken up the evening before its warned attack, yet the village braces on the day: " + wrong);
		helper.succeed();
	}

	/** Warned of the bandits on day 12, monsters due on day 14: the camp falls; the bandits' day is quiet, the monsters' attack stays. */
	//$ gametest_ticks_batch AREA '60' '"qaLairFallLaterMonsters1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLairFallLaterMonsters1111d")
	public void aLaterAttackOfAnotherBandStaysOnTheClock(GameTestHelper helper) {
		Lairs.Lair lair = camp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = lair.hall();
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.BANDITS, NORTH, 13600));
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(14, Threats.MONSTERS, NORTH, 13600));
		ThreatData.get(level).warned(hall, 12);
		level.setDayTime(BASE + VillageNeeds.DAY + 3000); // morning of day 12
		bringDownTheChief(helper, lair);
		level.setDayTime(BASE + VillageNeeds.DAY + 6100);
		hallRound(level, hall);
		List<String> wrong = new ArrayList<>();
		if (Warnings.ready(level, hall)) {
			wrong.add("braced on the broken camp's day");
		}
		if (!cultures(level, hall).equals(List.of(Threats.MONSTERS))) {
			wrong.add("the clock holds " + cultures(level, hall) + ", not only day 14's monsters");
		}
		helper.assertTrue(wrong.isEmpty(), "breaking up the camp should call off its own attack and keep the monsters': " + wrong);
		helper.succeed();
	}

	/** Bandits and monsters both set for day 12, warned: the camp falls at noon; the monsters still come, so the village stays braced. */
	//$ gametest_ticks_batch AREA '60' '"qaLairFallSameDay1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLairFallSameDay1111d")
	public void aWarningSharedWithAnotherBandStaysWhenTheCampFalls(GameTestHelper helper) {
		Lairs.Lair lair = camp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = lair.hall();
		level.setDayTime(BASE + 12100); // dusk of day 11
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.BANDITS, NORTH, 13600));
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, Threats.MONSTERS, NORTH, 14000));
		ThreatData.get(level).warned(hall, 12);
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon of day 12
		bringDownTheChief(helper, lair);
		level.setDayTime(BASE + VillageNeeds.DAY + 7000);
		hallRound(level, hall);
		List<String> wrong = new ArrayList<>();
		if (!Warnings.ready(level, hall)) {
			wrong.add("no longer braced, though the monsters still come tonight");
		}
		if (!cultures(level, hall).equals(List.of(Threats.MONSTERS))) {
			wrong.add("the clock holds " + cultures(level, hall) + ", not only tonight's monsters");
		}
		helper.assertTrue(wrong.isEmpty(), "the camp fell on a day two bands were to strike: " + wrong);
		helper.succeed();
	}
}
