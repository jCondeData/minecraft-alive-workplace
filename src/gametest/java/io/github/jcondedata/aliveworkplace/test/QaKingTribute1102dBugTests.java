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
 * QA (lane D, 2026-11-02), bug test B842 (fixed: the .one forms) for the Bandit King's tribute (31.16: "after 10 days he takes a third of the
 * treasury") at a count of one, kin of B406/B824/B833: a treasury of 3 emeralds loses 1 and the chronicle reads "The
 * Bandit King took 1 emeralds in tribute and left his fort a ruin." (chronicle and chat keys
 * bandit_king.tribute have no .one form). Expected "1 emerald". The plural control (a treasury of 6, 2 taken) passes.
 */
public class QaKingTribute1102dBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final long DAY = VillageNeeds.DAY;

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		VillageHalls.RADIUS = 8;
		Arcs.NEAR = 0;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
	}

	/** The storm outlasted with {@code treasury} (hundredths of an emerald) in the hall; the chronicle's tribute line. */
	static String tribute(GameTestHelper helper, int treasury) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, new BlockPos(5, 2, 5));
		BlockPos hall = helper.absolutePos(new BlockPos(5, 2, 5));
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "setup: the Bandit King's arc didn't load");
		ArcState s = Arcs.start(level, hall, arc);
		Arcs.next(level, hall);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "setup: the storm didn't begin: chapter " + s.chapter);
		entity.setTreasury(treasury);
		setDay(level, day + 10);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "setup: the arc didn't end in the tribute");
		return chronicle(entity).stream().filter(l -> l.startsWith("The Bandit King took ")).findFirst().orElse("(none)");
	}

	/** A treasury of 3 emeralds: the King takes 1, and the chronicle says "1 emerald". */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeOne1102d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeOne1102d")
	public void theKingsTributeOfOneEmeraldReadsInTheSingular(GameTestHelper helper) {
		String line = tribute(helper, 300);
		helper.assertTrue(line.equals("The Bandit King took 1 emerald in tribute and left his fort a ruin."), "the tribute line: " + line);
		helper.succeed();
	}

	/** The control: a treasury of 6 emeralds, 2 taken, reads in the plural. */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTributeTwo1102d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTributeTwo1102d")
	public void theKingsTributeOfTwoEmeraldsReadsInThePlural(GameTestHelper helper) {
		String line = tribute(helper, 600);
		helper.assertTrue(line.equals("The Bandit King took 2 emeralds in tribute and left his fort a ruin."), "the tribute line: " + line);
		helper.succeed();
	}
}
