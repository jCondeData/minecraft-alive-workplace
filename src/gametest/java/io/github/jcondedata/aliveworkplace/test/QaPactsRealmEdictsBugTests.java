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

/**
 * QA bug tests for ROADMAP 33.18 (fail on the current code; not registered: they travel on a tests/ branch until fixed).
 */
public class QaPactsRealmEdictsBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The pacts page's decision "Offering an alliance to a trade-pact partner is allowed: accepting turns the pact into an
	 * alliance": the Pacts tab, the only place a player offers anything, must then show "Offer X an alliance" for a
	 * village it has a trade pact with, and pressing it must send the offer; accepting it makes the pact an alliance
	 * without stopping its routes.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaBugPactToAlliance"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaBugPactToAlliance")
	public void aTradePactPartnerCanBeOfferedAnAllianceOnThePactsTab(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		QaPactsRealmEdictsGameTests.pact(land, false);
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int offer = QaPactsRealmEdictsGameTests.find(menu, "Offer " + land.name(1) + " an alliance");
		helper.assertTrue(offer >= 0, "the Pacts tab offers no alliance to a trade-pact partner, so a pact can't become an alliance without ending it");
		menu.press(offer, land.owner());
		helper.assertTrue(Pacts.to(server, land.at(1)).size() == 1 && Pacts.to(server, land.at(1)).get(0).alliance(), "no alliance offer waiting");
		helper.assertTrue(Pacts.accept(land.level(), land.hall(1), land.at(0), two.them()).ok(), "couldn't accept");
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)).alliance() && Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "not an alliance");
		helper.succeed();
	}
}
