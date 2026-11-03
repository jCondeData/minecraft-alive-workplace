package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane D, 2026-11-03) for B842, the Bandit King's tribute (31.16: "after 10 days he takes a third of the treasury")
 * at its boundaries, read in both places a player sees it: the hall's chronicle and the chat line to players near the
 * village. 0 emeralds taken reads plural ("0 emeralds"), 1 singular, 2 and 3 plural; the treasury keeps two thirds
 * (rounded in the village's favor: a third of 2.99 is 0.99 taken).
 */
public class QaKingTribute1103dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final long DAY = VillageNeeds.DAY;
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	/** What a player saw when the King outlasted the storm. */
	record Tribute(String chronicle, String chat, int treasuryAfter) {
	}

	/** The storm outlasted with {@code treasury} (hundredths of an emerald) in the hall, a player standing by it. */
	static Tribute tribute(GameTestHelper helper, int treasury, String player) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		VillageHalls.RADIUS = 8;
		Arcs.NEAR = 0;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		QaNurseWait1103dGameTests.Visitor v = QaNurseWait1103dGameTests.visitor(helper, player);
		v.player().moveTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
		long day = Chronicle.day(level) + 1;
		level.setDayTime((day - 1) * DAY + 1000);
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "setup: the Bandit King's arc didn't load");
		ArcState s = Arcs.start(level, hall, arc);
		Arcs.next(level, hall);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "setup: the storm didn't begin: chapter " + s.chapter);
		entity.setTreasury(treasury);
		v.seen().clear();
		level.setDayTime((day + 10 - 1) * DAY + 1000);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "setup: the arc didn't end in the tribute");
		String chronicle = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString())
			.filter(l -> l.startsWith("The Bandit King took ")).findFirst().orElse("(none)");
		String chat = v.seen().stream().filter(l -> l.startsWith("The Bandit King takes his tribute")).findFirst().orElse("(none; heard " + v.seen() + ")");
		return new Tribute(chronicle, chat, entity.treasury());
	}

	static void assertLines(GameTestHelper helper, Tribute t, String count) {
		helper.assertValueEqual(t.chronicle(), "The Bandit King took " + count + " in tribute and left his fort a ruin.", "the chronicle's tribute line");
		helper.assertValueEqual(t.chat(), "The Bandit King takes his tribute, " + count + " from the treasury, and rides off with his band.",
			"the chat's tribute line");
	}

	/** An empty treasury: nothing taken, "0 emeralds" (English plural for zero), and the treasury stays empty. */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeZero1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeZero1103d")
	public void qaAnEmptyTreasuryReadsZeroEmeralds(GameTestHelper helper) {
		Tribute t = tribute(helper, 0, "qaKing0");
		assertLines(helper, t, "0 emeralds");
		helper.assertValueEqual(t.treasuryAfter(), 0, "the treasury after");
		helper.succeed();
	}

	/** Exactly 3 emeralds: 1 taken, singular in the chronicle and in chat; 2 left. */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeThree1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeThree1103d")
	public void qaThreeEmeraldsGiveOneInTheSingularBothPlaces(GameTestHelper helper) {
		Tribute t = tribute(helper, 300, "qaKing3");
		assertLines(helper, t, "1 emerald");
		helper.assertValueEqual(t.treasuryAfter(), 200, "the treasury after (hundredths)");
		helper.succeed();
	}

	/** Exactly 6 emeralds: 2 taken, plural both places; 4 left. */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeSix1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeSix1103d")
	public void qaSixEmeraldsGiveTwoInThePluralBothPlaces(GameTestHelper helper) {
		Tribute t = tribute(helper, 600, "qaKing6");
		assertLines(helper, t, "2 emeralds");
		helper.assertValueEqual(t.treasuryAfter(), 400, "the treasury after (hundredths)");
		helper.succeed();
	}

	/** 9 emeralds: 3 taken, plural both places; 6 left. */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeNine1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeNine1103d")
	public void qaNineEmeraldsGiveThreeInThePlural(GameTestHelper helper) {
		Tribute t = tribute(helper, 900, "qaKing9");
		assertLines(helper, t, "3 emeralds");
		helper.assertValueEqual(t.treasuryAfter(), 600, "the treasury after (hundredths)");
		helper.succeed();
	}

	/** A third, rounded down to the hundredth: 2.99 loses 0.99, 5.99 loses 1.99 (the treasury's own sums). */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeCents1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeCents1103d")
	public void qaTheTributeIsAThirdOfFractionalTreasuries(GameTestHelper helper) {
		Tribute t = tribute(helper, 299, "qaKing299");
		helper.assertValueEqual(t.treasuryAfter(), 200, "2.99 emeralds after the tribute (hundredths)");
		helper.succeed();
	}

	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeCents2_1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeCents2_1103d")
	public void qaTheTributeOfFiveNinetyNineLeavesFour(GameTestHelper helper) {
		Tribute t = tribute(helper, 599, "qaKing599");
		helper.assertValueEqual(t.treasuryAfter(), 400, "5.99 emeralds after the tribute (hundredths)");
		helper.succeed();
	}
}
