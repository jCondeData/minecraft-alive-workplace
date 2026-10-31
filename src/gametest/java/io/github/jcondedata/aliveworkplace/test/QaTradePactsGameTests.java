package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * QA, written from the spec of ROADMAP 33.18 (trade pacts and alliances), aimed at what {@code PactGameTests},
 * {@code QaPactsBoardStewardGameTests} and {@code QaPactsRealmEdictsGameTests} leave out: the server's own clock
 * lapsing an offer (a tick before 3 days it stands) and ending an alliance's notice (no manual settle); the waiting
 * line's time left for one and several; the tab icon's counts; notice from the other side and its chronicles; a trade
 * pact upgraded to an alliance keeping its routes; crossed offers of different kinds; a village with no owner; a
 * pre-1.7 world really reloaded (saved data swapped in) making its routes pacts once; an ally's realm member riding at
 * 1,024 blocks and not 1,025, with the lines for one rider; an alliance offer waiting while alliances are off.
 */
public class QaTradePactsGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	static ChoiceMenu pactsTab(Land land, int hall, net.minecraft.server.level.ServerPlayer player) {
		return QaPactsRealmEdictsGameTests.tab(land, hall, player, TradePage.Tab.PACTS);
	}

	static int find(ChoiceMenu menu, String name) {
		return QaPactsRealmEdictsGameTests.find(menu, name);
	}

	static long chronicleCount(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().filter(e -> e.text().getString().equals(line)).count();
	}

	/** The Pacts tab's icon lines (as seen on the trade page's row of tabs). */
	static List<String> tabIcon(Land land, int hall, net.minecraft.server.level.ServerPlayer player) {
		ChoiceMenu menu = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, land.hall(hall));
		menu.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ROUTES, player);
		return RealmGameTests.lore(menu.icon(TradePage.Tab.PACTS.slot()));
	}

	/**
	 * The server's own clock: an offer a tick short of 3 days old still stands (Accept on the tab); with no manual
	 * settle, a second or two later it has lapsed by itself: gone from the tab, the offerer told once, the chronicle
	 * noting it once, and Accept refused with the reason.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qaTpLapseClock"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qaTpLapseClock")
	public void anOfferLapsesByItselfOnTheServerClock(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - Pacts.LAPSE + 1));
		Pacts.settle(server);
		helper.assertTrue(Pacts.to(server, land.at(1)).size() == 1, "an offer a tick short of 3 days lapsed");
		helper.assertTrue(find(pactsTab(land, 1, two.them()), "Accept a trade pact with " + land.name(0)) >= 0, "no Accept a tick short of 3 days");
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Pacts.Data.get(server).offers().isEmpty(), "the server's clock didn't lapse the offer: " + Pacts.Data.get(server).offers());
			helper.assertTrue(find(pactsTab(land, 1, two.them()), "Accept a trade pact with " + land.name(0)) < 0, "the lapsed offer is still on the tab");
			String told = "Your offer to " + land.name(1) + " lapsed: no answer came in 3 days.";
			helper.assertTrue(land.heard().stream().filter(c -> c.getString().equals(told)).count() == 1, "the offerer wasn't told exactly once: " + land.heard());
			helper.assertTrue(chronicleCount(land, 0, "The offer to " + land.name(1) + " lapsed unanswered") == 1, "the chronicle of the lapse: " + land.entity(0).chronicle());
			Pacts.Result late = Pacts.accept(land.level(), land.hall(1), land.at(0), two.them());
			helper.assertTrue(!late.ok() && late.message().getString().equals("There is no offer from " + land.name(0) + " waiting, or it has lapsed."),
				"accepting after the lapse: " + late.message().getString());
			helper.assertTrue(Pacts.Data.get(server).pacts().isEmpty() && !Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "the lapsed offer changed something");
			helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "no new offer after the lapse");
			helper.succeed();
		});
	}

	/** The waiting line tells the time left: "1 day and 1 hour" for one of each, "2 days and 5 hours" for several; who offered. */
	//$ gametest_ticks_batch HUGE '100' '"qaTpWaitingLine"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpWaitingLine")
	public void theWaitingLineReadsRightForOneAndSeveral(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data data = Pacts.Data.get(server);
		data.addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - (Pacts.LAPSE - 25_000)));
		data.addOffer(new Pacts.Offer(land.at(2), land.at(1), true, two.them().getUUID(), now - (Pacts.LAPSE - 53_000)));
		ChoiceMenu menu = pactsTab(land, 1, two.them());
		int one = find(menu, "Accept a trade pact with " + land.name(0));
		int several = find(menu, "Accept an alliance with " + land.name(2));
		helper.assertTrue(one >= 0 && several >= 0, "no Accept lines");
		List<String> a = RealmGameTests.lore(menu.icon(one));
		List<String> b = RealmGameTests.lore(menu.icon(several));
		helper.assertTrue(a.contains("Waiting for an answer: 1 day and 1 hour left"), "one day and one hour: " + a);
		helper.assertTrue(b.contains("Waiting for an answer: 2 days and 5 hours left"), "two days and five hours: " + b);
		helper.assertTrue(a.contains("Offered by Owner"), "who offered: " + a);
		helper.succeed();
	}

	/** The Pacts tab's icon counts several pacts and offers: "2 pacts, 2 offers waiting". */
	//$ gametest_ticks_batch HUGE '100' '"qaTpTabCountSeveral"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpTabCountSeveral")
	public void theTabIconCountsSeveralPactsAndOffers(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE,
			VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Pacts.Data data = Pacts.Data.get(server);
		long now = server.overworld().getGameTime();
		data.put(new Pacts.Pact(land.at(0), land.at(1), false, now, -1, false, false));
		data.put(new Pacts.Pact(land.at(2), land.at(1), true, now, -1, false, false));
		data.addOffer(new Pacts.Offer(land.at(3), land.at(1), false, two.them().getUUID(), now));
		data.addOffer(new Pacts.Offer(land.at(4), land.at(1), true, two.them().getUUID(), now));
		List<String> lines = tabIcon(land, 1, two.them());
		helper.assertTrue(lines.contains("2 pacts, 2 offers waiting"), "the tab icon: " + lines);
		helper.succeed();
	}

	/**
	 * The neighbour gives notice on an alliance from their tab: both chronicles note the notice; the other side's End
	 * can't cut the day short; the notice is exactly a day; then the server's clock ends it by itself: routes stop, both
	 * chronicles, both owners told.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qaTpNoticeClock"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qaTpNoticeClock")
	public void anAllianceNoticeFromTheOtherSideEndsOnTheServerClock(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Caravans.Data caravans = Caravans.Data.get(land.level());
		Pacts.Data data = Pacts.Data.get(server);
		QaPactsRealmEdictsGameTests.pact(land, true);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)) && caravans.toggleRoute(land.hall(1), land.hall(0)), "setup: routes");
		ChoiceMenu theirs = pactsTab(land, 1, two.them());
		int end = find(theirs, "End the alliance with " + land.name(0) + " (a day's notice)");
		helper.assertTrue(end >= 0, "no End on the neighbour's tab");
		long pressed = server.overworld().getGameTime();
		theirs.press(end, two.them());
		Pacts.Pact pact = data.pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.alliance() && pact.endsAt() == pressed + 24000L, "not exactly a day's notice: " + pact + " at " + pressed);
		helper.assertTrue(RealmGameTests.chronicled(land, 1, "Gave notice to end the alliance with " + land.name(0)), "the notice-giver's chronicle: " + land.entity(1).chronicle());
		helper.assertTrue(RealmGameTests.chronicled(land, 0, land.name(1) + " gave notice to end our alliance"), "the other chronicle: " + land.entity(0).chronicle());
		// the other side presses End too: refused, the day stands
		ChoiceMenu ours = pactsTab(land, 0, land.owner());
		int line = find(ours, "Alliance with " + land.name(1));
		helper.assertTrue(line >= 0 && RealmGameTests.lore(ours.icon(line)).contains("Ends in 24 hours"), "our line during the notice: " + (line < 0 ? "none" : RealmGameTests.lore(ours.icon(line))));
		ours.press(find(ours, "End the alliance with " + land.name(1) + " (a day's notice)"), land.owner());
		helper.assertTrue(data.pact(land.at(0), land.at(1)) != null && data.pact(land.at(0), land.at(1)).endsAt() == pressed + 24000L, "the other side cut the notice short");
		helper.assertTrue(RealmGameTests.heard(land, "The alliance with " + land.name(1) + " is already ending."), "the other side's End wasn't refused: " + land.heard());
		helper.assertTrue(caravans.routesFrom(land.hall(0)).contains(land.hall(1)), "a route stopped during the notice");
		// the day nearly up: the server's own clock ends it
		long now = server.overworld().getGameTime();
		data.put(new Pacts.Pact(pact.a(), pact.b(), true, pact.since(), now + 5, pact.giftsA(), pact.giftsB()));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(data.pact(land.at(0), land.at(1)) == null && Pacts.allies(server, land.at(0)).isEmpty(), "the alliance outlived its notice on the server's clock");
			helper.assertTrue(!caravans.routesFrom(land.hall(0)).contains(land.hall(1)) && !caravans.routesFrom(land.hall(1)).contains(land.hall(0)), "a route survived the alliance");
			helper.assertTrue(chronicleCount(land, 0, "The alliance with " + land.name(1) + " ended") == 1
				&& chronicleCount(land, 1, "The alliance with " + land.name(0) + " ended") == 1, "the chronicles of the end");
			helper.assertTrue(RealmGameTests.heard(land, "The alliance with " + land.name(1) + " has ended."), "the owner wasn't told: " + land.heard());
			helper.assertTrue(QaPactsRealmEdictsGameTests.said(two.theirs(), "The alliance with " + land.name(0) + " has ended."), "the neighbour wasn't told: " + two.theirs());
			helper.succeed();
		});
	}

	/**
	 * A trade pact partner offered an alliance (the wiki's decision): accepting makes the pact an alliance, one pact
	 * only, its routes keep running; a trade pact offer on top of an alliance is refused with the reason.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaTpUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpUpgrade")
	public void aPactUpgradedToAnAllianceKeepsItsRoutes(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Caravans.Data caravans = Caravans.Data.get(land.level());
		QaPactsRealmEdictsGameTests.pact(land, false);
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), true, land.owner()).ok(), "an alliance offer to a pact partner was refused");
		ChoiceMenu menu = pactsTab(land, 1, two.them());
		int accept = find(menu, "Accept an alliance with " + land.name(0));
		helper.assertTrue(accept >= 0, "no Accept for the alliance");
		menu.press(accept, two.them());
		helper.assertTrue(Pacts.Data.get(server).pacts().size() == 1 && Pacts.Data.get(server).pact(land.at(0), land.at(1)).alliance(), "not one alliance: " + Pacts.Data.get(server).pacts());
		helper.assertTrue(caravans.routesFrom(land.hall(0)).contains(land.hall(1)) && Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "the route stopped on the upgrade");
		helper.assertTrue(Pacts.allies(server, land.at(1)).equals(List.of(land.at(0))), "not allies: " + Pacts.allies(server, land.at(1)));
		Pacts.Result again = Pacts.offer(land.level(), land.hall(1), land.hall(0), false, two.them());
		helper.assertTrue(!again.ok() && again.message().getString().equals("There is already a pact with " + land.name(0) + "."), "a trade pact over an alliance: " + again.message().getString());
		helper.succeed();
	}

	/**
	 * Both sides offer at once, of different kinds (a trade pact one way, an alliance the other): the owner accepts the
	 * alliance on their tab; one pact, an alliance, and no offer left on either side.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaTpCrossedKinds"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpCrossedKinds")
	public void crossedOffersOfDifferentKindsMakeOneAlliance(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the trade pact offer");
		helper.assertTrue(Pacts.offer(land.level(), land.hall(1), land.hall(0), true, two.them()).ok(), "the crossed alliance offer");
		helper.assertTrue(Pacts.to(server, land.at(0)).size() == 1 && Pacts.to(server, land.at(1)).size() == 1, "both offers should wait");
		ChoiceMenu ours = pactsTab(land, 0, land.owner());
		int accept = find(ours, "Accept an alliance with " + land.name(1));
		helper.assertTrue(accept >= 0, "no Accept for the crossed alliance");
		ours.press(accept, land.owner());
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(Pacts.Data.get(server).pacts().size() == 1 && pact != null && pact.alliance(), "not one alliance: " + Pacts.Data.get(server).pacts());
		helper.assertTrue(Pacts.Data.get(server).offers().isEmpty(), "a stale offer: " + Pacts.Data.get(server).offers());
		helper.assertTrue(find(pactsTab(land, 1, two.them()), "Accept a trade pact with " + land.name(0)) < 0, "the other side still shows Accept");
		helper.assertTrue(RealmGameTests.chronicled(land, 0, "Made an alliance with " + land.name(1)) && RealmGameTests.chronicled(land, 1, "Made an alliance with " + land.name(0)),
			"the chronicles of the alliance");
		helper.succeed();
	}

	/** A village with no owner: no offer button, an offer refused, routes need no pact, and an old route to it isn't made a pact. */
	//$ gametest_ticks_batch HUGE '100' '"qaTpNoOwner"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpNoOwner")
	public void aVillageWithNoOwnerNeedsNoPactAndGetsNoOffer(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		land.entity(1).setOwner(null, "");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		helper.assertTrue(Realms.ownerOf(server, land.at(1)) == null, "setup: the village still has an owner");
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		helper.assertTrue(find(menu, "Offer " + land.name(1) + " a trade pact") < 0 && find(menu, "Offer " + land.name(1) + " an alliance") < 0, "an offer button for a village with no owner");
		helper.assertTrue(!Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok() && Pacts.Data.get(server).offers().isEmpty(), "an offer to nobody");
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "a route to a village with no owner needs a pact");
		Caravans.Data caravans = Caravans.Data.get(land.level());
		helper.assertTrue(caravans.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		Pacts.Data.get(server).setMigrated(false);
		Pacts.settle(server);
		helper.assertTrue(Pacts.Data.get(server).pacts().isEmpty() && caravans.routesFrom(land.hall(0)).contains(land.hall(1)), "the migration made a pact with nobody or stopped the route");
		caravans.toggleRoute(land.hall(0), land.hall(1));
		helper.succeed();
	}

	/**
	 * A world from before 1.7 really loads: the caravans' saved data (a route each way to the neighbour) reloaded and no
	 * pacts file at all. Within a second or two, with no manual settle, the routes are a trade pact, nothing stopped and
	 * the cargo stays free (gifts on for each sender, B700); saved and reloaded again, the migration doesn't run a second time.
	 */
	//$ gametest_ticks_batch HUGE '160' '"qaTpReload"'
	@GameTest(template = HUGE, timeoutTicks = 160, batch = "qaTpReload")
	public void aPre17WorldReloadedMakesItsRoutesPactsOnce(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		HolderLookup.Provider registries = server.registryAccess();
		Caravans.Data before = Caravans.Data.get(level);
		helper.assertTrue(before.toggleRoute(land.hall(0), land.hall(1)) && before.toggleRoute(land.hall(1), land.hall(0)), "setup: routes");
		level.getDataStorage().set("aliveworkplace_caravans", Caravans.Data.load(before.save(new CompoundTag(), registries), registries));
		server.overworld().getDataStorage().set("aliveworkplace_pacts", Pacts.Data.load(new CompoundTag(), registries));
		helper.assertTrue(Pacts.Data.get(server).pacts().isEmpty(), "a world from before 1.7 starts with pacts");
		helper.runAfterDelay(45, () -> {
			Caravans.Data caravans = Caravans.Data.get(level);
			Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
			helper.assertTrue(pact != null && !pact.alliance() && Pacts.Data.get(server).pacts().size() == 1, "the old routes didn't become one trade pact: " + Pacts.Data.get(server).pacts());
			helper.assertTrue(caravans.routesFrom(land.hall(0)).contains(land.hall(1)) && caravans.routesFrom(land.hall(1)).contains(land.hall(0)), "a route stopped after the reload");
			helper.assertTrue(Pacts.mayRoute(level, land.hall(0), land.hall(1)) && Pacts.mayRoute(level, land.hall(1), land.hall(0)), "routes not allowed after the reload");
			TradeGoods.Good good = TradeGoods.all().get(0);
			land.entity(1).setTreasury(100_000);
			int paid = Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), caravans);
			helper.assertTrue(paid == 0, "M33 design: a migrated route's cargo stays free (gifts on for the sender), but the receiver paid " + paid); // B700
			// saved and reloaded once more: the pact stays, and the migration's mark too
			Pacts.Data back = Pacts.Data.load(Pacts.Data.get(server).save(new CompoundTag(), registries), registries);
			helper.assertTrue(back.pacts().equals(Pacts.Data.get(server).pacts()), "the pact didn't survive a save: " + back.pacts());
			server.overworld().getDataStorage().set("aliveworkplace_pacts", back);
			back.clear();
			helper.runAfterDelay(45, () -> {
				helper.assertTrue(Pacts.Data.get(server).pacts().isEmpty(), "the migration ran again after a save and reload: " + Pacts.Data.get(server).pacts());
				Caravans.Data now = Caravans.Data.get(level);
				now.toggleRoute(land.hall(0), land.hall(1));
				now.toggleRoute(land.hall(1), land.hall(0));
				helper.succeed();
			});
		});
	}

	/**
	 * "An alliance is a pact whose villages (and their realms) send relief riders": a member of our realm (not itself
	 * allied) rides to our ally's raid exactly 1,024 blocks off, one rider for 3 guards, and the lines say "1 rider";
	 * an ally a block further gets none.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaTpRealmAllyReach"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpRealmAllyReach")
	public void ourRealmMemberRidesToAnAllyAt1024ButNot1025(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		boolean relief = Relief.ENABLED;
		Relief.ENABLED = true;
		Relief.forget();
		Leftovers.after(helper, () -> {
			Relief.ENABLED = relief;
			Relief.forget();
		});
		helper.assertTrue(Realms.found(level, land.hall(0), land.owner()).refusal() == null, "setup: no realm founded");
		Realms.Result add = Realms.add(level, land.hall(0), land.at(2), land.owner());
		helper.assertTrue(add.refusal() == null && land.data().realmOf(land.at(2)) != null, "setup: village 2 didn't join: " + add.message().getString());
		ReliefGameTests.guards(land, 2, 3).forEach(g -> g.setNoAi(true));
		long now = server.overworld().getGameTime();
		BlockPos edge = land.hall(2).offset(Relief.RANGE, 0, 0);
		BlockPos beyond = land.hall(2).offset(Relief.RANGE + 1, 0, 0);
		land.data().putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), edge), Component.literal("Edgewater"), Optional.of(two.them().getUUID()), "qa-neighbour", "village", 5, 50, now));
		land.data().putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), beyond), Component.literal("Farhold"), Optional.of(two.them().getUUID()), "qa-neighbour", "village", 5, 50, now));
		Pacts.Data pacts = Pacts.Data.get(server);
		pacts.put(new Pacts.Pact(land.at(0), GlobalPos.of(level.dimension(), edge), true, now, -1, false, false));
		int riders = Relief.call(level, edge);
		helper.assertTrue(riders == 1, "our realm member's riders to the ally exactly " + Relief.RANGE + " blocks off: " + riders);
		helper.assertTrue(land.heard().stream().anyMatch(c -> c.getString().startsWith(land.name(2) + " sends 1 rider to help your ally ") && c.getString().endsWith(" against a raid.")),
			"the sender's owner's line for 1 rider: " + land.heard());
		helper.assertTrue(two.theirs().stream().anyMatch(c -> c.getString().startsWith("1 rider from your ally " + land.name(2) + " rides to help ")),
			"the raided owner's line for 1 rider: " + two.theirs());
		helper.assertTrue(land.entity(2).chronicle().stream().anyMatch(e -> e.text().getString().startsWith("Sent 1 rider to help our ally ")), "the sender's chronicle: " + land.entity(2).chronicle());
		Relief.forget();
		pacts.clear();
		pacts.setMigrated(true);
		pacts.put(new Pacts.Pact(land.at(0), GlobalPos.of(level.dimension(), beyond), true, now, -1, false, false));
		helper.assertTrue(Relief.call(level, beyond) == 0, "riders to an ally a block beyond " + Relief.RANGE);
		helper.succeed();
	}

	/**
	 * The other owner is offline with no mailbox: the offer's letter waits for them at the post office (a parcel to them
	 * holding the letter), and their hall's tab still shows Accept and Decline.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaTpOfflineLetter"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpOfflineLetter")
	public void anOfflineOwnersLetterWaitsAtThePostOffice(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		java.util.UUID away = java.util.UUID.randomUUID();
		land.entity(1).setOwner(away, "qa-away");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		io.github.jcondedata.aliveworkplace.mail.PostOffice office = io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server);
		Leftovers.after(helper, () -> office.parcels().stream().filter(p -> p.to().equals(away)).toList().forEach(office::remove));
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the offer to an offline owner was refused");
		List<io.github.jcondedata.aliveworkplace.mail.Parcel> waiting = office.parcels().stream().filter(p -> p.to().equals(away)).toList();
		helper.assertTrue(waiting.size() == 1, "no letter waiting at the post office: " + waiting.size());
		ItemStack letter = waiting.get(0).items().get(0);
		var book = letter.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
		String text = book == null ? null : book.pages().get(0).raw().getString();
		helper.assertTrue(text != null && text.contains("offers " + land.name(1) + " a trade pact") && text.contains("The offer lapses after 3 days."), "the waiting letter: " + text);
		helper.assertTrue(waiting.get(0).status() == io.github.jcondedata.aliveworkplace.mail.Parcel.Status.IN_TRANSIT, "the letter's status: " + waiting.get(0).status());
		helper.assertTrue(Pacts.to(server, land.at(1)).size() == 1, "the offer doesn't wait for the offline owner");
		helper.succeed();
	}

	/** An alliance offer waiting when {@code alliances} is turned off can't be accepted and stays; back on, Accept works. */
	//$ gametest_ticks_batch HUGE '100' '"qaTpAlliancesOffWaiting"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaTpAlliancesOffWaiting")
	public void anAllianceOfferWaitsOutAlliancesOff(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), true, land.owner()).ok(), "the alliance offer");
		boolean alliances = Pacts.ALLIANCES;
		Leftovers.after(helper, () -> Pacts.ALLIANCES = alliances);
		Pacts.ALLIANCES = false;
		Pacts.Result off = Pacts.accept(land.level(), land.hall(1), land.at(0), two.them());
		helper.assertTrue(!off.ok() && Pacts.Data.get(server).pacts().isEmpty() && Pacts.to(server, land.at(1)).size() == 1, "an alliance accepted with alliances off");
		helper.assertTrue(!off.message().getString().contains("aliveworkplace."), "untranslated: " + off.message().getString());
		Pacts.ALLIANCES = true;
		ChoiceMenu menu = pactsTab(land, 1, two.them());
		int accept = find(menu, "Accept an alliance with " + land.name(0));
		helper.assertTrue(accept >= 0, "the offer left the tab");
		menu.press(accept, two.them());
		helper.assertTrue(Pacts.allies(server, land.at(0)).equals(List.of(land.at(1))), "no alliance back on");
		helper.succeed();
	}
}
