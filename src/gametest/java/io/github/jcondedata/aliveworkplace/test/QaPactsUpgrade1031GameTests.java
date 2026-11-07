package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * QA of B701's fix (33.18: cargo is paid for "unless the sending owner turns on gifts"; a trade pact becoming an alliance
 * keeps its start date and each side's gifts), from the side B701's own test leaves: the neighbour, who gives nothing,
 * offers the alliance and the giving owner accepts it on his own Pacts tab. Each side's gifts must stay with that side.
 */
public class QaPactsUpgrade1031GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Owner 0 gives, owner 1 doesn't; owner 1 offers the alliance, owner 0 accepts: 0 still gives, 1 still doesn't, same start day. */
	//$ gametest_ticks_batch HUGE '100' '"qa1031UpgradeOtherWay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031UpgradeOtherWay")
	public void qaTheNeighbourOffersTheAllianceAndEachSideKeepsItsGifts(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), false, 5, -1, true, false));
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)) && !Pacts.gives(level, land.hall(1), land.hall(0)),
			"setup: owner 0 gives, owner 1 doesn't");

		helper.assertTrue(Pacts.offer(level, land.hall(1), land.hall(0), true, two.them()).ok(), "the neighbour's alliance offer was refused");
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int accept = QaPactsRealmEdictsGameTests.find(menu, "Accept an alliance with " + land.name(1));
		helper.assertTrue(accept >= 0, "no Accept for the neighbour's alliance on owner 0's Pacts tab");
		menu.press(accept, land.owner());

		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.alliance(), "not an alliance: " + pact);
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)), "owner 0's gifts turned off when the neighbour's alliance was accepted: " + pact);
		helper.assertTrue(!Pacts.gives(level, land.hall(1), land.hall(0)), "the neighbour gives now though he never turned gifts on: " + pact);
		helper.assertTrue(pact.since() == 5, "the pact's start day changed from 5 to " + pact.since());
		helper.succeed();
	}

	/** An alliance whose giving side turns gifts off later: off for that side only, the alliance kept. */
	//$ gametest_ticks_batch HUGE '100' '"qa1031AllyGiftsOff"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031AllyGiftsOff")
	public void qaAnAllyTurnsHisGiftsOffAndOnlyHisChange(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), true, 5, -1, true, true));
		Pacts.toggleGifts(level, land.hall(1), land.at(0), two.them()); // the neighbour's own switch
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.alliance() && pact.since() == 5, "the alliance changed: " + pact);
		helper.assertTrue(!Pacts.gives(level, land.hall(1), land.hall(0)), "the neighbour turned his gifts off but still gives: " + pact);
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)), "owner 0's gifts went off with the neighbour's: " + pact);
		helper.succeed();
	}
}
