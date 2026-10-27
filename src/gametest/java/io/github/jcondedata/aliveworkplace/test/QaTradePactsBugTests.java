package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * QA of ROADMAP 33.18: sentences a player reads on the Pacts tab that don't read right for one (or for less than an
 * hour). Each test fails until the mod is fixed.
 */
public class QaTradePactsBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** The Pacts tab's icon with one pact and one offer reads "1 pact, 1 offer waiting", not "1 pacts, 1 offers waiting". */
	//$ gametest_ticks_batch HUGE '100' '"qaTpBugTabCountOne"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpBugTabCountOne")
	public void theTabIconReadsRightForOnePactAndOneOffer(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper, io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE,
			io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE, io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), false, now, -1, false, false));
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(2), land.at(1), false, two.them().getUUID(), now));
		List<String> lines = QaTradePactsGameTests.tabIcon(land, 1, two.them());
		helper.assertTrue(lines.contains("1 pact, 1 offer waiting"), "the tab icon with one pact and one offer: " + lines);
		helper.succeed();
	}

	/** An alliance with less than an hour of notice left doesn't say "Ends in 0 hours" (it ends within the hour). */
	//$ gametest_ticks_batch HUGE '100' '"qaTpBugEndsInZero"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpBugEndsInZero")
	public void anAllianceEndingWithinTheHourDoesntSayZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), true, 0, now + 500, false, false));
		ChoiceMenu menu = QaTradePactsGameTests.pactsTab(land, 0, land.owner());
		int line = QaTradePactsGameTests.find(menu, "Alliance with " + land.name(1));
		helper.assertTrue(line >= 0, "no alliance line");
		List<String> lore = RealmGameTests.lore(menu.icon(line));
		helper.assertTrue(lore.stream().anyMatch(l -> l.startsWith("Ends")) && lore.stream().noneMatch(l -> l.contains(" 0 hours")), "500 ticks of notice left: " + lore);
		helper.succeed();
	}

	/**
	 * With the Lighthouse (35.12: caravans reach twice as far), the Pacts tab lists "Offer Farport a trade pact" for
	 * another player's village 3,000 blocks off, so it's within this village's caravan range: pressing the button must
	 * make the offer, not refuse it as "beyond caravan range".
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaTpBugLighthouseRange"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpBugLighthouseRange")
	public void anOfferButtonTheTabShowsWithTheLighthouseIsntRefusedAsTooFar(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		net.minecraft.server.level.ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		io.github.jcondedata.aliveworkplace.wonder.WonderManager manager = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level);
		io.github.jcondedata.aliveworkplace.wonder.WonderSite site = manager.begin(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("lighthouse"), land.hall(0),
			java.util.UUID.randomUUID(), "Jesse", 3);
		manager.finish(site.id(), 7);
		net.minecraft.core.BlockPos far = land.hall(0).offset(3000, 0, 0);
		io.github.jcondedata.aliveworkplace.hall.Caravans.Data caravans = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			io.github.jcondedata.aliveworkplace.wonder.Wonders.forgetPowers();
			caravans.remove(far);
		});
		helper.assertTrue(io.github.jcondedata.aliveworkplace.wonder.LighthousePowers.caravanRange(level, land.hall(0)) == 2 * io.github.jcondedata.aliveworkplace.hall.Caravans.RANGE,
			"setup: no Lighthouse range");
		caravans.setWants(far, net.minecraft.network.chat.Component.literal("Farport"), List.of());
		land.data().putKnown(new io.github.jcondedata.aliveworkplace.realm.RealmData.Known(net.minecraft.core.GlobalPos.of(level.dimension(), far),
			net.minecraft.network.chat.Component.literal("Farport"), java.util.Optional.of(two.them().getUUID()), "qa-neighbour", "village", 5, 50, server.overworld().getGameTime()));
		ChoiceMenu menu = QaTradePactsGameTests.pactsTab(land, 0, land.owner());
		int offer = QaTradePactsGameTests.find(menu, "Offer Farport a trade pact");
		helper.assertTrue(offer >= 0, "setup: the tab doesn't list Farport with the Lighthouse");
		menu.press(offer, land.owner());
		helper.assertTrue(Pacts.to(server, net.minecraft.core.GlobalPos.of(level.dimension(), far)).size() == 1,
			"the tab's Offer button for a village in Lighthouse range made no offer; the player heard: " + land.heard().stream().map(c -> c.getString()).toList());
		helper.succeed();
	}

	/** An offer with less than an hour left doesn't say "0 days and 0 hours left". */
	//$ gametest_ticks_batch HUGE '100' '"qaTpBugWaitingZero"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpBugWaitingZero")
	public void anOfferLapsingWithinTheHourDoesntSayZeroDaysAndZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - (Pacts.LAPSE - 500)));
		ChoiceMenu menu = QaTradePactsGameTests.pactsTab(land, 1, two.them());
		int accept = QaTradePactsGameTests.find(menu, "Accept a trade pact with " + land.name(0));
		helper.assertTrue(accept >= 0, "no Accept line");
		List<String> lore = RealmGameTests.lore(menu.icon(accept));
		helper.assertTrue(lore.stream().anyMatch(l -> l.startsWith("Waiting for an answer")) && lore.stream().noneMatch(l -> l.contains("0 days and 0 hours")),
			"500 ticks left on the offer: " + lore);
		helper.succeed();
	}
}
