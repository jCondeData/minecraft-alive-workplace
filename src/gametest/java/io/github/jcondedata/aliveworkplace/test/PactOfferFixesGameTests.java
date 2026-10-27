package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.wonder.LighthousePowers;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Fixes to the Pacts tab's offers (ROADMAP 33.18): B582, a trade-pact partner can be offered an alliance from the tab
 * (from QA's {@code QaPactsRealmEdictsBugTests}); B595, the tab and the offer use the same caravan range, the
 * village's own (twice as far with the Lighthouse, 35.12), and say it as "4,096" (from QA's {@code QaTradePactsBugTests}).
 */
public class PactOfferFixesGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B582, the pacts page's decision "Offering an alliance to a trade-pact partner is allowed: accepting turns the pact
	 * into an alliance": the Pacts tab, the only place a player offers anything, shows "Offer X an alliance" (and no
	 * second "Offer X a trade pact") for a village it has a trade pact with; pressing it sends the offer; accepting it
	 * makes the pact an alliance without stopping its routes, and then no alliance offer is shown any more.
	 */
	//$ gametest_ticks_batch HUGE '100' '"pactToAllianceTab"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "pactToAllianceTab")
	public void aTradePactPartnerCanBeOfferedAnAllianceOnThePactsTab(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		QaPactsRealmEdictsGameTests.pact(land, false);
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(menu, "Offer " + land.name(1) + " a trade pact") < 0, "a pact partner is offered a second trade pact");
		int offer = QaPactsRealmEdictsGameTests.find(menu, "Offer " + land.name(1) + " an alliance");
		helper.assertTrue(offer >= 0, "the Pacts tab offers no alliance to a trade-pact partner, so a pact can't become an alliance without ending it");
		menu.press(offer, land.owner());
		helper.assertTrue(Pacts.to(server, land.at(1)).size() == 1 && Pacts.to(server, land.at(1)).get(0).alliance(), "no alliance offer waiting");
		ChoiceMenu again = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(again, "Offer " + land.name(1) + " an alliance") < 0, "the offer, once sent, is still offered");
		helper.assertTrue(Pacts.accept(land.level(), land.hall(1), land.at(0), two.them()).ok(), "couldn't accept");
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)).alliance() && Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "not an alliance");
		ChoiceMenu allied = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(allied, "Alliance with " + land.name(1)) >= 0
			&& QaPactsRealmEdictsGameTests.find(allied, "Offer " + land.name(1) + " an alliance") < 0, "an ally is still offered an alliance");
		helper.succeed();
	}

	/**
	 * B595: with the Lighthouse (35.12: caravans reach twice as far), the Pacts tab lists "Offer Farport a trade pact" for
	 * another player's village 3,000 blocks off, and pressing it makes the offer. Without the Lighthouse the tab doesn't
	 * list it and an offer is refused with the range written "2,048"; with it, a village 5,000 off is refused at "4,096".
	 */
	//$ gametest_ticks_batch HUGE '100' '"pactLighthouseRange"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "pactLighthouseRange")
	public void anOfferButtonTheTabShowsWithTheLighthouseIsntRefusedAsTooFar(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		BlockPos far = land.hall(0).offset(3000, 0, 0);
		BlockPos farther = land.hall(0).offset(5000, 0, 0);
		Caravans.Data caravans = Caravans.Data.get(level);
		WonderManager manager = WonderManager.get(level);
		WonderSite[] site = new WonderSite[1];
		Leftovers.after(helper, () -> {
			if (site[0] != null) {
				manager.remove(site[0].id());
			}
			Wonders.forgetPowers();
			caravans.remove(far);
			caravans.remove(farther);
		});
		neighbor(land, two, far, "Farport");
		neighbor(land, two, farther, "Distant");

		ChoiceMenu plain = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(plain, "Offer Farport a trade pact") < 0, "without the Lighthouse Farport, 3,000 blocks off, is listed");
		Pacts.Result refused = Pacts.offer(level, land.hall(0), far, false, land.owner());
		helper.assertTrue(!refused.ok() && refused.message().getString().equals("Farport is more than 2,048 blocks away, beyond caravan range."),
			"without the Lighthouse: " + refused.message().getString());

		site[0] = manager.begin(AliveWorkplace.id("lighthouse"), land.hall(0), UUID.randomUUID(), "Jesse", 3);
		manager.finish(site[0].id(), 7);
		helper.assertTrue(LighthousePowers.caravanRange(level, land.hall(0)) == 2 * Caravans.RANGE, "setup: no Lighthouse range");
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int offer = QaPactsRealmEdictsGameTests.find(menu, "Offer Farport a trade pact");
		helper.assertTrue(offer >= 0, "the tab doesn't list Farport with the Lighthouse");
		helper.assertTrue(QaPactsRealmEdictsGameTests.find(menu, "Offer Distant a trade pact") < 0, "Distant, 5,000 blocks off, is listed");
		menu.press(offer, land.owner());
		helper.assertTrue(Pacts.to(server, GlobalPos.of(level.dimension(), far)).size() == 1,
			"the tab's Offer button for a village in Lighthouse range made no offer; the player heard: " + land.heard().stream().map(Component::getString).toList());
		Pacts.Result tooFar = Pacts.offer(level, land.hall(0), farther, true, land.owner());
		helper.assertTrue(!tooFar.ok() && tooFar.message().getString().equals("Distant is more than 4,096 blocks away, beyond caravan range."),
			"with the Lighthouse: " + tooFar.message().getString());
		helper.succeed();
	}

	/** Another owner's village at {@code pos}, known to the realm records and to the caravans. */
	private static void neighbor(Land land, Two two, BlockPos pos, String name) {
		ServerLevel level = land.level();
		Caravans.Data.get(level).setWants(pos, Component.literal(name), List.of());
		land.data().putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), pos), Component.literal(name), Optional.of(two.them().getUUID()),
			"pact-neighbor", "village", 5, 50, level.getServer().overworld().getGameTime()));
	}
}
