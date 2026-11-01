package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA, written from the spec of ROADMAP 33.18 (trade pacts and alliances), aimed at what the earlier pact test classes
 * leave out: both chronicles noting an accepted offer (and the offer itself) exactly once; a trade pact's end shutting
 * both Routes tabs and the pay in the same tick, and a new offer allowed right after; an end letter to an offline owner
 * waiting at the post office, for a pact and for an alliance's notice; an ally's riders from exactly two guards and none
 * from one, with both owners told in the singular; an alliance under notice still riding, and not after its day; a
 * lapsed offer made again with a fresh 3 days; and every change to the pacts marked for saving.
 */
public class QaPacts1101GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	static long chronicleCount(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().filter(e -> e.text().getString().equals(line)).count();
	}

	/**
	 * The offer is noted in the offerer's chronicle (with the letter's addressee); the Accept on the other's tab is noted
	 * in both villages' chronicles, once each, and not before it's pressed.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101Chronicles"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101Chronicles")
	public void anAcceptedOfferIsNotedInBothChroniclesOnce(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the offer was refused");
		helper.assertTrue(chronicleCount(land, 0, "Offered " + land.name(1) + " a trade pact (a letter to qa-neighbour)") == 1,
			"the offerer's chronicle of the offer: " + land.entity(0).chronicle().stream().map(e -> e.text().getString()).toList());
		helper.assertTrue(chronicleCount(land, 1, "Made a trade pact with " + land.name(0)) == 0, "a pact chronicled before Accept");
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		int accept = QaPactsRealmEdictsGameTests.find(menu, "Accept a trade pact with " + land.name(0));
		helper.assertTrue(accept >= 0, "no Accept on the tab");
		menu.press(accept, two.them());
		helper.assertTrue(chronicleCount(land, 0, "Made a trade pact with " + land.name(1)) == 1, "the offerer's chronicle of the pact");
		helper.assertTrue(chronicleCount(land, 1, "Made a trade pact with " + land.name(0)) == 1,
			"the accepter's chronicle of the pact: " + land.entity(1).chronicle().stream().map(e -> e.text().getString()).toList());
		helper.assertTrue(QaPactsRealmEdictsGameTests.said(two.theirs(), "A trade pact with " + land.name(0) + ": caravans may run between you now."),
			"the accepter's action bar: " + two.theirs());
		// the alliance offer on top: its own chronicle line, then both chronicles once more
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), true, land.owner()).ok(), "the alliance offer was refused");
		helper.assertTrue(chronicleCount(land, 0, "Offered " + land.name(1) + " an alliance (a letter to qa-neighbour)") == 1, "the alliance offer's chronicle");
		helper.assertTrue(Pacts.accept(land.level(), land.hall(1), land.at(0), two.them()).ok(), "the alliance accept");
		helper.assertTrue(chronicleCount(land, 0, "Made an alliance with " + land.name(1)) == 1 && chronicleCount(land, 1, "Made an alliance with " + land.name(0)) == 1,
			"the alliance's chronicles");
		helper.succeed();
	}

	/**
	 * A trade pact ends at once: in the same tick both Routes tabs say the other village needs a pact and a click starts
	 * no route, its cargo goes unpaid, and a new offer may be made straight away.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101EndAtOnce"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101EndAtOnce")
	public void endingATradePactShutsBothRoutesTabsInTheSameTick(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data data = Caravans.Data.get(level);
		QaPactsRealmEdictsGameTests.pact(land, false);
		land.entity(1).setTreasury(100_000);
		TradeGoods.Good good = TradeGoods.all().get(0);
		helper.assertTrue(Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), data) > 0, "setup: unpaid with the pact");
		long tick = server.overworld().getGameTime();
		helper.assertTrue(Pacts.end(level, land.hall(0), land.at(1), land.owner()).ok(), "couldn't end the pact");
		helper.assertTrue(server.overworld().getGameTime() == tick, "setup: a tick passed");
		for (int side = 0; side < 2; side++) {
			var player = side == 0 ? land.owner() : two.them();
			int other = 1 - side;
			ChoiceMenu routes = TradePage.forTest(player, land.hall(side), TradePage.Tab.ROUTES);
			int slot = QaPactsRealmEdictsGameTests.find(routes, land.name(other));
			helper.assertTrue(slot >= 0, "side " + side + ": the other village isn't on the Routes tab");
			helper.assertTrue(RealmGameTests.lore(routes.icon(slot)).contains("Another player's village: needs a trade pact (Pacts tab)"),
				"side " + side + ": the line after the end: " + RealmGameTests.lore(routes.icon(slot)));
			routes.press(slot, player);
			helper.assertTrue(!data.routesFrom(land.hall(side)).contains(land.hall(other)), "side " + side + ": a route started right after the pact ended");
		}
		helper.assertTrue(QaPactsRealmEdictsGameTests.said(two.theirs(), land.name(0) + " belongs to another player: offer them a trade pact on the Pacts tab first."),
			"the neighbour wasn't told why: " + two.theirs());
		helper.assertTrue(Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), data) == 0, "paid after the pact ended");
		helper.assertTrue(Pacts.offer(level, land.hall(1), land.hall(0), false, two.them()).ok(), "no new offer right after the end");
		helper.succeed();
	}

	/**
	 * The other owner is offline: the letter ending the trade pact, and the one giving an alliance notice, wait for them
	 * at the post office.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101OfflineEnd"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101OfflineEnd")
	public void anEndLetterToAnOfflineOwnerWaitsAtThePostOffice(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		UUID away = UUID.randomUUID();
		land.entity(1).setOwner(away, "qa-away");
		Realms.round(level, land.hall(1), land.entity(1), 6);
		PostOffice office = PostOffice.get(server);
		Leftovers.after(helper, () -> office.parcels().stream().filter(p -> p.to().equals(away)).toList().forEach(office::remove));
		QaPactsRealmEdictsGameTests.pact(land, false);
		helper.assertTrue(Pacts.end(level, land.hall(0), land.at(1), land.owner()).ok(), "couldn't end the pact");
		QaPactsRealmEdictsGameTests.pact(land, true);
		helper.assertTrue(Pacts.end(level, land.hall(0), land.at(1), land.owner()).ok(), "couldn't give the alliance notice");
		List<String> texts = office.parcels().stream().filter(p -> p.to().equals(away))
			.map(p -> {
				var book = p.items().isEmpty() ? null : p.items().get(0).get(DataComponents.WRITTEN_BOOK_CONTENT);
				return book == null ? "(no letter)" : book.pages().get(0).raw().getString();
			}).toList();
		String ours = land.name(0);
		helper.assertTrue(texts.contains("realm-owner, of " + ours + ", ends the trade pact with " + land.name(1) + ". Caravans between the two villages stop."),
			"no end letter waiting: " + texts);
		helper.assertTrue(texts.contains("realm-owner, of " + ours + ", gives notice: the alliance with " + land.name(1) + " ends in a day."),
			"no notice letter waiting: " + texts);
		helper.assertTrue(office.parcels().stream().filter(p -> p.to().equals(away)).allMatch(p -> p.status() == Parcel.Status.IN_TRANSIT), "a letter's status");
		helper.succeed();
	}

	private static void relief(GameTestHelper helper) {
		boolean relief = Relief.ENABLED;
		Relief.ENABLED = true;
		Relief.forget();
		Leftovers.after(helper, () -> {
			Relief.ENABLED = relief;
			Relief.forget();
		});
	}

	/**
	 * An ally sends riders only with at least two guards: one guard, none (and nobody is told); two guards, one rider,
	 * and both owners hear it in the singular, the ally's chronicle too.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101AllyGuards"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101AllyGuards")
	public void anAllyWithOneGuardSendsNoneAndWithTwoSendsOne(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		relief(helper);
		QaPactsRealmEdictsGameTests.pact(land, true);
		ReliefGameTests.guards(land, 0, Relief.MIN_GUARDS - 1).forEach(g -> g.setNoAi(true));
		helper.assertTrue(Relief.call(level, land.hall(1)) == 0, "riders from an ally with " + (Relief.MIN_GUARDS - 1) + " guard");
		helper.assertTrue(two.theirs().stream().noneMatch(c -> c.getString().contains("from your ally")), "told of riders that never came: " + two.theirs());
		Relief.forget();
		ReliefGameTests.guards(land, 0, 1).forEach(g -> g.setNoAi(true));
		int riders = Relief.call(level, land.hall(1));
		helper.assertTrue(riders == 1, "an ally with " + Relief.MIN_GUARDS + " guards sent " + riders);
		helper.assertTrue(RealmGameTests.heard(land, land.name(0) + " sends 1 rider to help your ally " + land.name(1) + " against a raid."), "the ally's owner: " + land.heard());
		helper.assertTrue(QaPactsRealmEdictsGameTests.said(two.theirs(), "1 rider from your ally " + land.name(0) + " rides to help " + land.name(1) + "."), "the raided owner: " + two.theirs());
		helper.assertTrue(ReliefGameTests.chronicleSays(land, 0, "Sent 1 rider to help our ally " + land.name(1) + " against a raid"), "the ally's chronicle");
		helper.succeed();
	}

	/** An alliance under its day's notice still rides to the other's raid; once the day is up and it has ended, no riders come. */
	//$ gametest_ticks_batch HUGE '100' '"qa1101NoticeRides"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101NoticeRides")
	public void anAllianceUnderNoticeStillRidesAndNotAfterItsDay(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		relief(helper);
		List<Villager> guards = ReliefGameTests.guards(land, 1, 6);
		guards.forEach(g -> g.setNoAi(true));
		QaPactsRealmEdictsGameTests.pact(land, true);
		helper.assertTrue(Pacts.end(level, land.hall(0), land.at(1), land.owner()).ok(), "couldn't give notice");
		Pacts.settle(server);
		helper.assertTrue(Relief.call(level, land.hall(0)) > 0, "no riders from an ally under notice (the one given notice to)");
		Relief.forget();
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).put(new Pacts.Pact(pact.a(), pact.b(), true, pact.since(), now, pact.giftsA(), pact.giftsB()));
		Pacts.settle(server);
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null, "the alliance outlived its notice");
		helper.assertTrue(Relief.call(level, land.hall(0)) == 0, "riders from a former ally");
		helper.succeed();
	}

	/**
	 * A lapsed offer may be made again: the new one is accepted with a fresh 3 days on the tab, and a new letter goes out;
	 * the old one can't be answered.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101OfferAgain"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101OfferAgain")
	public void aLapsedOfferMayBeMadeAgainWithAFreshThreeDays(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - Pacts.LAPSE - 1));
		Pacts.settle(server);
		helper.assertTrue(!Pacts.accept(level, land.hall(1), land.at(0), two.them()).ok(), "the lapsed offer was accepted");
		int before = QaPactsRealmEdictsGameTests.letters(two.them()).size();
		Pacts.Result again = Pacts.offer(level, land.hall(0), land.hall(1), false, land.owner());
		helper.assertTrue(again.ok(), "a lapsed offer couldn't be made again: " + again.message().getString());
		helper.assertTrue(QaPactsRealmEdictsGameTests.letters(two.them()).size() == before + 1, "no new letter");
		Pacts.Offer offer = Pacts.Data.get(server).offer(land.at(0), land.at(1));
		helper.assertTrue(offer != null && offer.sent() == server.overworld().getGameTime(), "the new offer's clock: " + offer);
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		int accept = QaPactsRealmEdictsGameTests.find(menu, "Accept a trade pact with " + land.name(0));
		helper.assertTrue(accept >= 0 && RealmGameTests.lore(menu.icon(accept)).contains("Waiting for an answer: 3 days and 0 hours left"),
			"the new offer's line: " + (accept < 0 ? "none" : RealmGameTests.lore(menu.icon(accept))));
		menu.press(accept, two.them());
		helper.assertTrue(Pacts.mayRoute(level, land.hall(0), land.hall(1)), "the new offer's Accept made no pact");
		helper.succeed();
	}

	/**
	 * Every change to the pacts is marked for saving, so a restart keeps it: an offer, a decline, an accept, gifts, an
	 * alliance's notice, its end, a pact's end and a lapse.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101Dirty"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101Dirty")
	public void everyChangeToThePactsIsMarkedForSaving(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Pacts.Data data = Pacts.Data.get(server);
		java.util.function.BiConsumer<String, Runnable> marks = (what, change) -> {
			data.setDirty(false);
			change.run();
			helper.assertTrue(data.isDirty(), what + " wasn't marked for saving");
		};
		marks.accept("an offer", () -> helper.assertTrue(Pacts.offer(level, land.hall(0), land.hall(1), true, land.owner()).ok(), "offer"));
		marks.accept("a decline", () -> helper.assertTrue(Pacts.decline(level, land.hall(1), land.at(0), two.them()).ok(), "decline"));
		helper.assertTrue(Pacts.offer(level, land.hall(0), land.hall(1), true, land.owner()).ok(), "the second offer");
		marks.accept("an accept", () -> helper.assertTrue(Pacts.accept(level, land.hall(1), land.at(0), two.them()).ok(), "accept"));
		marks.accept("gifts on", () -> helper.assertTrue(Pacts.toggleGifts(level, land.hall(1), land.at(0), two.them()), "gifts"));
		marks.accept("an alliance's notice", () -> helper.assertTrue(Pacts.end(level, land.hall(1), land.at(0), two.them()).ok(), "notice"));
		Pacts.Pact pact = data.pact(land.at(0), land.at(1));
		data.put(new Pacts.Pact(pact.a(), pact.b(), true, pact.since(), server.overworld().getGameTime(), pact.giftsA(), pact.giftsB()));
		marks.accept("the alliance's end", () -> Pacts.settle(server));
		helper.assertTrue(data.pact(land.at(0), land.at(1)) == null, "the alliance didn't end");
		QaPactsRealmEdictsGameTests.pact(land, false);
		marks.accept("a pact's end", () -> helper.assertTrue(Pacts.end(level, land.hall(0), land.at(1), land.owner()).ok(), "end"));
		data.addOffer(new Pacts.Offer(land.at(1), land.at(0), false, land.owner().getUUID(), server.overworld().getGameTime() - Pacts.LAPSE - 1));
		marks.accept("a lapse", () -> Pacts.settle(server));
		helper.assertTrue(data.offers().isEmpty(), "the offer didn't lapse");
		helper.succeed();
	}
}
