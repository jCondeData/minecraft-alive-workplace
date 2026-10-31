package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * QA (lane-d-1031-0408), from ROADMAP 31.4's spec: "An arc marked {@code threat} doesn't start in a village set At peace
 * or at threat level 0 once 32.21 has landed" (32.21 is on main and verified). Tested through the real daily roll
 * ({@link Arcs#tryStart}) with the Grey Cough (31.18, {@code "threat": true}) as the only arc switched on, its trigger
 * met (a Village of 12 with {@code villagerSickness} on): 300 rolls At peace start nothing, and the same village
 * starts it once peace is lifted; the same at threat level 0 against level 1.
 */
public class QaThreatArcsPeace1031GameTests implements FabricGameTest {
	static final String TALL = QaSicknessGameTests.TALL;

	/** A Village of 12, sickness on: the Grey Cough's trigger is met. */
	static VillageHallBlockEntity sickVillage(GameTestHelper helper) {
		QaSicknessGameTests.settings(helper);
		VillageHallBlockEntity entity = QaSicknessGameTests.hall(helper);
		QaStoryArcsPeace1031GameTests.onlyArc(helper, SicknessStory.ARC);
		QaSicknessGameTests.village(helper, 12);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		helper.assertTrue(QaSicknessGameTests.arc(helper).threat(), "the Sickness isn't marked threat");
		return entity;
	}

	//$ gametest_ticks_batch TALL '200' '"qaThreatArcPeace"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaThreatArcPeace")
	public void theGreyCoughNeverStartsInAVillageAtPeace(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageHallBlockEntity entity = sickVillage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		Threats.LEVEL = 2;
		entity.setAtPeace(true, Chronicle.day(level));
		RandomSource random = RandomSource.create(3104);
		ArcState s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 300);
		helper.assertTrue(s == null, "the Grey Cough (a threat arc) started in a village set At peace");
		entity.setAtPeace(false, Chronicle.day(level));
		s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 300);
		helper.assertTrue(s != null && s.id.equals(SicknessStory.ARC.toString()), "with peace lifted it never started in 300 rolls");
		helper.succeed();
	}

	//$ gametest_ticks_batch TALL '200' '"qaThreatArcLevel0"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaThreatArcLevel0")
	public void theGreyCoughNeverStartsAtThreatLevelZero(GameTestHelper helper) {
		Leftovers.clear(helper);
		sickVillage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		RandomSource random = RandomSource.create(3105);
		Threats.LEVEL = 0;
		ArcState s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 300);
		helper.assertTrue(s == null, "the Grey Cough (a threat arc) started at threat level 0");
		Threats.LEVEL = 1;
		s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 300);
		helper.assertTrue(s != null && s.id.equals(SicknessStory.ARC.toString()), "at threat level 1 it never started in 300 rolls");
		helper.succeed();
	}
}
