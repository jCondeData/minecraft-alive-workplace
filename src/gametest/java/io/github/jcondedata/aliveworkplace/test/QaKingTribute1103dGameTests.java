package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane D, 2026-11-03) for B866, the Bandit King's tribute (31.16: a third of the treasury): the chronicle and chat
 * say what the treasury lost as the hall's treasury writes it. {@link #tribute} outlasts the storm with a given treasury
 * and a player listening near the hall; the bug tests ({@link QaKingTribute1103dBugTests}) check the odd amounts, and
 * the test here checks a whole one still reads in whole emeralds.
 */
public class QaKingTribute1103dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final long DAY = VillageNeeds.DAY;
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	/** What the tribute left behind: the treasury (hundredths), the chronicle's tribute line and the chat line. */
	record Tribute(int treasuryAfter, String chronicle, String chat) {
	}

	/** The storm outlasted with {@code treasury} (hundredths of an emerald) in the hall and a player named {@code name} near it. */
	static Tribute tribute(GameTestHelper helper, int treasury, String name) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
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
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		QaLostCaravanGameTests.listener(helper, seen, hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5, name);
		long day = Chronicle.day(level) + 1;
		level.setDayTime((day - 1) * DAY + 1000);
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "setup: the Bandit King's arc didn't load");
		ArcState s = Arcs.start(level, hall, arc);
		Arcs.next(level, hall);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "setup: the storm didn't begin: chapter " + s.chapter);
		entity.setTreasury(treasury);
		seen.clear();
		level.setDayTime((day + 10 - 1) * DAY + 1000);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "setup: the arc didn't end in the tribute");
		String chronicle = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString())
			.filter(l -> l.startsWith("The Bandit King took ")).findFirst().orElse("(none)");
		List<String> chat = QaLostCaravanGameTests.texts(seen, false);
		String said = chat.stream().filter(l -> l.startsWith("The Bandit King takes his tribute")).findFirst().orElse("(none; heard " + chat + ")");
		return new Tribute(entity.treasury(), chronicle, said);
	}

	/** A whole amount (9 emeralds, 3 taken) still reads in whole emeralds, in the chronicle and in chat. */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeWhole900_1103d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeWhole900_1103d")
	public void qaAWholeTributeReadsInWholeEmeralds(GameTestHelper helper) {
		Tribute t = tribute(helper, 900, "qaKingWhole900");
		helper.assertTrue(t.treasuryAfter() == 600, "the treasury after: " + t.treasuryAfter());
		helper.assertValueEqual(t.chronicle(), "The Bandit King took 3 emeralds in tribute and left his fort a ruin.", "the chronicle");
		helper.assertValueEqual(t.chat(), "The Bandit King takes his tribute, 3 emeralds from the treasury, and rides off with his band.", "chat");
		QaLostCaravanGameTests.english(helper, List.of(t.chronicle(), t.chat()));
		helper.succeed();
	}
}
