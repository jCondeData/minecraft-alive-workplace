package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane D, 2026-11-03) for B842's note: with CobbleDollars installed the hall's treasury reads in CobbleDollars
 * ("Treasury: 300 CobbleDollars"), so the Bandit King's tribute (31.16, a third of the treasury) should read in them
 * too, in the chronicle and in chat. Fails while the line says "1 emerald" whatever the money is.
 */
public class QaKingTributeDollars1103dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	//$ gametest_ticks_batch AREA '60' '"qaKingTributeDollars1103d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaKingTributeDollars1103d")
	public void qaTheTributeReadsInCobbleDollarsLikeTheTreasury(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.assertTrue(Money.cobbleDollars(), "setup: CobbleDollars isn't the money in this run");
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		VillageHalls.RADIUS = 8;
		Arcs.NEAR = 0;
		BlockPos hall = helper.absolutePos(HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			Arcs.stopAll(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		QaNurseMachine1102dCompatTests.Visitor v = QaNurseMachine1102dCompatTests.visitor(helper, "qaKingDollars");
		v.player().moveTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
		long day = Chronicle.day(level) + 1;
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "setup: the Bandit King's arc didn't load");
		ArcState s = Arcs.start(level, hall, arc);
		Arcs.next(level, hall);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "setup: the storm didn't begin: chapter " + s.chapter);
		entity.setTreasury(300);
		String before = TradePage.money(entity.treasury()).getString();
		v.seen().clear();
		level.setDayTime((day + 10 - 1) * VillageNeeds.DAY + 1000);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "setup: the arc didn't end in the tribute");
		int taken = 300 - entity.treasury();
		String said = TradePage.money(taken).getString();
		String chronicle = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString())
			.filter(l -> l.startsWith("The Bandit King took ")).findFirst().orElse("(none)");
		String chat = v.seen().stream().filter(l -> l.startsWith("The Bandit King takes his tribute")).findFirst().orElse("(none; heard " + v.seen() + ")");
		helper.assertTrue(chronicle.contains("took " + said + " in tribute"), "the hall's treasury read " + before + " and lost " + said
			+ ", but the chronicle reads: " + chronicle);
		helper.assertTrue(chat.contains(", " + said + " from the treasury"), "the hall's treasury read " + before + " and lost " + said
			+ ", but chat reads: " + chat);
		helper.succeed();
	}
}
