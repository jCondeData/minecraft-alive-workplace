package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * QA verification of B699 (lane-a's fix), written from the bug's spec, not the fix: "story arcs marked threat (the
 * Sickness's Grey Cough, the Bandit King) ... Expected: tryStart skips a threat arc when Threats.quiet(level, hall) (a
 * non-threat story still starts there)", and the fix's own promise: a threat arc skipped while quiet takes "no roll, no
 * cooldown, so it can start once the village is no longer quiet". QaThreatArcsPeace1031GameTests covers the Grey Cough
 * At peace and at level 0; these add the other threat arc (the Bandit King), a threat arc next to a story at peace,
 * and that a skipped threat arc spends neither the village's cooldown ({@code arcCooldownDays}) nor the server's
 * {@code arcsAtOnce} slot.
 */
public class QaThreatArcsVerify1031GameTests implements FabricGameTest {
	static final String TALL = QaSicknessGameTests.TALL;

	/** A Town with 2 bandit camps broken and banditCamps on: the Bandit King's trigger is met; only his arc may start. */
	private static VillageHallBlockEntity kingsTown(GameTestHelper helper) {
		QaSicknessGameTests.settings(helper);
		boolean camps = BanditCamps.ENABLED;
		Leftovers.after(helper, () -> BanditCamps.ENABLED = camps);
		VillageHallBlockEntity entity = QaSicknessGameTests.hall(helper);
		QaStoryArcsPeace1031GameTests.onlyArc(helper, BanditKingStory.ARC);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		BanditCamps.ENABLED = true;
		for (int i = 0; i < 10 && Stories.banditCampsBroken(level, hall) < 2; i++) {
			Stories.banditCampBroken(level, hall);
		}
		entity.setRank(VillageRanks.Rank.TOWN);
		Arcs.Arc arc = Arcs.find("bandit_king").orElseThrow();
		helper.assertTrue(arc.threat(), "the Bandit King isn't marked threat");
		helper.assertTrue(arc.conditions().stream().allMatch(c -> c.met(level, hall)), "setup: the Bandit King's trigger isn't met");
		return entity;
	}

	private static boolean is(ArcState s, net.minecraft.resources.ResourceLocation id) {
		return s != null && s.id.equals(id.toString());
	}

	/** The Bandit King (the other threat arc) never starts in a Town set At peace; with peace lifted (threat level 2) he does. */
	//$ gametest_ticks_batch TALL '200' '"qaVerifyKingPeace"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaVerifyKingPeace")
	public void theBanditKingNeverStartsInATownAtPeace(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageHallBlockEntity entity = kingsTown(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		Threats.LEVEL = 2;
		entity.setAtPeace(true, Chronicle.day(level));
		RandomSource random = RandomSource.create(31041);
		ArcState s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 400);
		helper.assertTrue(s == null, "the Bandit King (a threat arc) started in a Town set At peace: " + (s == null ? null : s.id));
		entity.setAtPeace(false, Chronicle.day(level));
		s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 400);
		helper.assertTrue(is(s, BanditKingStory.ARC), "with peace lifted at threat level 2 the Bandit King never started in 400 rolls: " + (s == null ? null : s.id));
		helper.succeed();
	}

	/** The Bandit King never starts at threat level 0; at the default level (2) the same Town gets him. */
	//$ gametest_ticks_batch TALL '200' '"qaVerifyKingLevel0"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaVerifyKingLevel0")
	public void theBanditKingNeverStartsAtThreatLevelZero(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageHallBlockEntity entity = kingsTown(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		entity.setAtPeace(false, Chronicle.day(level));
		RandomSource random = RandomSource.create(31042);
		Threats.LEVEL = 0;
		ArcState s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 400);
		helper.assertTrue(s == null, "the Bandit King (a threat arc) started at threat level 0: " + (s == null ? null : s.id));
		Threats.LEVEL = 2;
		s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 400);
		helper.assertTrue(is(s, BanditKingStory.ARC), "at threat level 2 the Bandit King never started in 400 rolls: " + (s == null ? null : s.id));
		helper.succeed();
	}

	/**
	 * At peace, with both a threat arc (the Grey Cough) and a story that is no threat (the test pack's Test of Three)
	 * switched on, every start in 30 days of rolls is the story: the threat arc being passed by never blocks the other.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaVerifyThreatBesideStory"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaVerifyThreatBesideStory")
	public void atPeaceOnlyTheStoryStartsNeverTheThreatBesideIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageHallBlockEntity entity = QaThreatArcsPeace1031GameTests.sickVillage(helper);
		Set<String> others = new HashSet<>();
		for (Arcs.Arc a : Arcs.all()) {
			if (!a.id().equals(SicknessStory.ARC) && !a.id().equals(QaStoryArcsPeace1031GameTests.THREE)) {
				others.add(a.id().toString());
			}
		}
		Arcs.DISABLED = others;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		Threats.LEVEL = 2;
		entity.setAtPeace(true, Chronicle.day(level));
		RandomSource random = RandomSource.create(31043);
		int stories = 0;
		for (int i = 0; i < 30; i++) {
			ArcState s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 50);
			helper.assertTrue(s == null || is(s, QaStoryArcsPeace1031GameTests.THREE), "At peace a threat arc started beside a story: " + (s == null ? null : s.id));
			if (s != null) {
				stories++;
				Arcs.stopAll(level, hall);
			}
		}
		helper.assertTrue(stories >= 20, "At peace the story (no threat) started only " + stories + " times in 30 tries of 50 rolls");
		helper.succeed();
	}

	/**
	 * A threat arc passed by while the village is At peace spends nothing: with the real cooldown (8 days) and a server
	 * cap of exactly one more arc, 300 days of quiet rolls start nothing and leave the arc count as it was, and the same
	 * day peace is lifted the Grey Cough starts (no cooldown began, no slot was taken).
	 */
	//$ gametest_ticks_batch TALL '200' '"qaVerifyThreatNoCooldown"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaVerifyThreatNoCooldown")
	public void aThreatArcPassedByAtPeaceTakesNoCooldownNorSlot(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageHallBlockEntity entity = QaThreatArcsPeace1031GameTests.sickVillage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		Stories.Data.get(level).remove(hall); // a hall that never had an arc: no cooldown left over from earlier tests here
		Arcs.COOLDOWN_DAYS = 8;
		int running = Arcs.runningCount(level.getServer());
		Arcs.AT_ONCE = running + 1;
		int started = Arcs.STARTED.get();
		Threats.LEVEL = 2;
		long today = Chronicle.day(level);
		entity.setAtPeace(true, today);
		ArcState s = QaStoryArcsPeace1031GameTests.roll(level, hall, RandomSource.create(31044), 300);
		helper.assertTrue(s == null, "the Grey Cough started At peace: " + (s == null ? null : s.id));
		helper.assertTrue(Arcs.running(level, hall) == null, "an arc runs in the quiet village: " + Arcs.running(level, hall));
		helper.assertTrue(Arcs.runningCount(level.getServer()) == running, "quiet rolls changed the server's running arcs: " + running + " -> " + Arcs.runningCount(level.getServer()));
		helper.assertTrue(Arcs.STARTED.get() == started, "arcs began during the quiet rolls: " + (Arcs.STARTED.get() - started));
		entity.setAtPeace(false, today);
		helper.assertTrue(Chronicle.day(level) == today, "setup: the day moved on");
		s = QaStoryArcsPeace1031GameTests.roll(level, hall, RandomSource.create(31045), 300);
		helper.assertTrue(is(s, SicknessStory.ARC), "the same day peace was lifted the Grey Cough didn't start (a cooldown or the arcsAtOnce slot was spent while quiet): "
			+ (s == null ? null : s.id));
		helper.succeed();
	}
}
