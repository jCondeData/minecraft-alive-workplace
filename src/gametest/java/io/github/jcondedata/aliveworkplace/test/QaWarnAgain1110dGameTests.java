package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-10): B1146's fix (a warned attack called off stands the village down) from the other side. The fix
 * clears the hall's warning with the attacks and makes {@code Warnings.ready} false in a quiet village; neither may
 * reach past that:
 * <ul>
 * <li>32.21's level 1 (gentle) still lets raids come, so a village warned at level 1 is braced on the day as at 2, and
 * its villagers are sent in at dusk;</li>
 * <li>after the call-off the village is warned again: the threat level back at 2 (or At peace lifted), the clock's next
 * attack is told by the same tower, and the village braces for it on its day.</li>
 * </ul>
 * Uses the village of {@link QaThreatSwitches1110dBugTests} (a Lookout Tower with an archer posted by it).
 */
public class QaWarnAgain1110dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_switch_besiegers");

	/** Warned at threat level 1 of tomorrow's attack: braced from noon of the day, the villagers sent in at dusk. */
	//$ gametest_ticks_batch AREA '60' '"qaWarnGentle1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarnGentle1110d")
	public void aVillageWarnedAtTheGentleLevelStillBracesOnItsDay(GameTestHelper helper) {
		BlockPos hall = QaThreatSwitches1110dBugTests.warnedVillage(helper);
		ServerLevel level = helper.getLevel();
		Villager townsman = QaThreatSwitches1110dBugTests.townsman(helper);
		QaThreatSwitches1110dBugTests.threatLevel(1);
		helper.assertTrue(!Threats.quiet(level, hall), "setup: threat level 1 counts as quiet");
		QaThreatSwitches1110dBugTests.setAttack(level, hall);
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "not warned at level 1: " + ThreatData.get(level).warned(hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon of day 12
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(Warnings.ready(level, hall), "at level 1 the warned village isn't braced from noon of the day: warned " + ThreatData.get(level).warned(hall)
			+ ", day " + Chronicle.day(level) + ", attacks " + ThreatData.get(level).attacks(hall));
		helper.assertTrue(Warnings.warned(level, hall).isPresent(), "at level 1 the Defense page doesn't show the warned attack");
		level.setDayTime(BASE + VillageNeeds.DAY + 12600); // dusk, before the attack's hour
		townsman.getBrain().eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(Warnings.evening(level, hall), "at level 1 the guards aren't sent to battle stations at dusk of the warned day");
		helper.assertTrue(townsman.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isPresent(), "at level 1 no villager is sent in at dusk of the warned day");
		helper.succeed();
	}

	/**
	 * Warned at level 2, called off at level 0 on the day; the level back at 2 the same evening, the clock's next attack
	 * (the day after) is told by the tower and braced for at its noon.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWarnAgainLevel1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarnAgainLevel1110d")
	public void aVillageIsWarnedAgainOnceTheThreatLevelIsBack(GameTestHelper helper) {
		BlockPos hall = QaThreatSwitches1110dBugTests.warnedVillage(helper);
		ServerLevel level = helper.getLevel();
		QaThreatSwitches1110dBugTests.threatLevel(2);
		QaThreatSwitches1110dBugTests.setAttack(level, hall);
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "setup: not warned at level 2: " + ThreatData.get(level).warned(hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon of day 12
		QaThreatSwitches1110dBugTests.threatLevel(0);
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) < 0 && ThreatData.get(level).attacks(hall).isEmpty(),
			"setup: not called off at level 0: warned " + ThreatData.get(level).warned(hall) + ", attacks " + ThreatData.get(level).attacks(hall));
		comesBack(helper, hall, () -> QaThreatSwitches1110dBugTests.threatLevel(2));
	}

	/** As above, but the village set At peace on the warned morning and the peace lifted that evening. */
	//$ gametest_ticks_batch AREA '60' '"qaWarnAgainPeace1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarnAgainPeace1110d")
	public void aVillageIsWarnedAgainOnceItsPeaceIsLifted(GameTestHelper helper) {
		BlockPos hall = QaThreatSwitches1110dBugTests.warnedVillage(helper);
		ServerLevel level = helper.getLevel();
		QaThreatSwitches1110dBugTests.threatLevel(2);
		QaThreatSwitches1110dBugTests.setAttack(level, hall);
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "setup: not warned at level 2: " + ThreatData.get(level).warned(hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 1000); // the morning of day 12
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setAtPeace(true, Chronicle.day(level));
		level.setDayTime(BASE + VillageNeeds.DAY + 6100);
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) < 0, "setup: the warning isn't called off At peace: " + ThreatData.get(level).warned(hall));
		comesBack(helper, hall, () -> entity.setAtPeace(false, Chronicle.day(level)));
	}

	/**
	 * Dusk of day 12: {@code lift} puts raids back; the clock sets day 13's attack; the hall's round warns of it at once,
	 * and at noon of day 13 the village is braced; on day 12's evening nothing is braced (that attack was called off).
	 */
	private static void comesBack(GameTestHelper helper, BlockPos hall, Runnable lift) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(BASE + VillageNeeds.DAY + 12100); // dusk of day 12
		lift.run();
		helper.assertTrue(!Threats.quiet(level, hall), "setup: raids aren't back");
		ThreatData.get(level).rolled(hall, 12, new ThreatData.Attack(13, BESIEGERS, Math.PI / 2, 13600));
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 13, "the tower doesn't warn of the next attack once raids are back: warned "
			+ ThreatData.get(level).warned(hall) + ", attacks " + ThreatData.get(level).attacks(hall));
		helper.assertTrue(!Warnings.evening(level, hall), "the called-off attack's evening is still on");
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 6100); // noon of day 13
		QaThreatSwitches1110dBugTests.hallRound(level, hall);
		helper.assertTrue(Warnings.ready(level, hall), "the village isn't braced at noon of the next warned attack's day: warned " + ThreatData.get(level).warned(hall)
			+ ", day " + Chronicle.day(level) + ", attacks " + ThreatData.get(level).attacks(hall));
		helper.succeed();
	}
}
