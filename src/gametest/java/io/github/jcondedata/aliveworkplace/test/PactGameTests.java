package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * ROADMAP 33.18, trade pacts and alliances, with two owners: no route to the other's village without a pact; an
 * accepted offer (a letter, Accept on the Pacts tab) allows routes and the cargo is paid for (not with gifts on); a
 * declined or lapsed offer changes nothing; a route saved before 1.7 loads as a pact; an ally's raid brings riders;
 * ending an alliance takes a day, a pact ends at once; the switches; save and reload.
 */
public class PactGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Two villages (0: realm-owner's, 1: neighbour's), both on the caravans' list, pacts on and empty; all put back after. */
	private static Land two(GameTestHelper helper, List<Component> theirs) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean enabled = Pacts.ENABLED;
		boolean alliances = Pacts.ALLIANCES;
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = enabled;
			Pacts.ALLIANCES = alliances;
			Pacts.Data.get(server).clear();
			Caravans.Data data = Caravans.Data.get(land.level());
			data.remove(land.hall(0));
			data.remove(land.hall(1));
		});
		ServerPlayer neighbour = RealmGameTests.listener(helper, theirs, "neighbour");
		them = neighbour;
		land.entity(1).setOwner(neighbour.getUUID(), "neighbour");
		io.github.jcondedata.aliveworkplace.realm.Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		return land;
	}

	private static ServerPlayer them;

	private static ServerPlayer them(MinecraftServer server) {
		return them;
	}

	private static String letter(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = player.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				return book.pages().get(0).raw().getString();
			}
		}
		return null;
	}

	/** No route without a pact; the offer's letter and the Pacts tab's Accept; then routes, and the cargo paid for unless gifts. */
	//$ gametest_ticks_batch AREA '100' '"pactAccept"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pactAccept")
	public void anAcceptedPactAllowsRoutesAndPays(GameTestHelper helper) {
		List<Component> theirs = new ArrayList<>();
		Land land = two(helper, theirs);
		MinecraftServer server = land.level().getServer();
		ServerPlayer them = them(server);
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "a route to another owner's village without a pact");
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the offer was refused");
		String letter = letter(them);
		helper.assertTrue(letter != null && letter.contains("offers " + land.name(1) + " a trade pact") && letter.contains("lapses after 3 days"), "the letter: " + letter);
		helper.assertTrue(theirs.stream().anyMatch(c -> c.getString().contains("a letter is on its way")), "no chat line: " + theirs);
		helper.assertTrue(!Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "offered twice");
		helper.assertTrue(!Pacts.accept(land.level(), land.hall(1), land.at(0), land.owner()).ok(), "the offerer accepted for them");
		// the neighbour's Pacts tab: Accept and Decline
		ChoiceMenu menu = VillageHallScreen.forTest(them, land.hall(1));
		menu.press(VillageHallScreen.ROUTES, them);
		menu.press(TradePage.Tab.PACTS.slot(), them);
		ItemStack accept = menu.icon(VillageHallScreen.FIRST_ROW);
		helper.assertTrue(accept.getHoverName().getString().equals("Accept a trade pact with " + land.name(0)), "no Accept line: " + accept);
		helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 1).getHoverName().getString().equals("Decline the offer from " + land.name(0)), "no Decline line");
		menu.press(VillageHallScreen.FIRST_ROW, them);
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)) && Pacts.mayRoute(land.level(), land.hall(1), land.hall(0)), "no routes after accepting");
		helper.assertTrue(RealmGameTests.chronicled(land, 0, "Made a trade pact with " + land.name(1)), "the offerer's chronicle");
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " accepted the trade pact with " + land.name(0) + "."), "the offerer wasn't told: " + land.heard());
		// the cargo is paid for at the board's price
		TradeGoods.Good good = TradeGoods.all().get(0);
		land.entity(1).setTreasury(100_000);
		Caravans.Data data = Caravans.Data.get(land.level());
		int paid = Caravans.payPact(land.level(), land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), data);
		helper.assertTrue(paid > 0 && land.entity(1).treasury() == 100_000 - paid, "not paid for: " + paid + ", treasury " + land.entity(1).treasury());
		// gifts on: nothing asked
		helper.assertTrue(Pacts.toggleGifts(land.level(), land.hall(0), land.at(1), land.owner()), "gifts didn't turn on");
		helper.assertTrue(Caravans.payPact(land.level(), land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), data) == 0, "paid with gifts on");
		// a trade pact ends at once, by letter, and its routes stop
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)), "no route");
		helper.assertTrue(Pacts.end(land.level(), land.hall(1), land.at(0), them).ok(), "couldn't end");
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null && !data.routesFrom(land.hall(0)).contains(land.hall(1)), "the pact or its route stayed");
		helper.succeed();
	}

	/** A declined offer and a lapsed one change nothing. */
	//$ gametest_ticks_batch AREA '100' '"pactDecline"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pactDecline")
	public void aDeclinedOrLapsedOfferChangesNothing(GameTestHelper helper) {
		List<Component> theirs = new ArrayList<>();
		Land land = two(helper, theirs);
		MinecraftServer server = land.level().getServer();
		ServerPlayer them = them(server);
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), true, land.owner()).ok(), "the alliance offer was refused");
		helper.assertTrue(Pacts.decline(land.level(), land.hall(1), land.at(0), them).ok(), "couldn't decline");
		helper.assertTrue(Pacts.Data.get(server).pacts().isEmpty() && Pacts.Data.get(server).offers().isEmpty(), "the decline left something");
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "a route after declining");
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " declined your offer."), "not told of the decline: " + land.heard());
		// an offer 3 days old lapses
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, them.getUUID(), now - Pacts.LAPSE - 1));
		Pacts.settle(server);
		helper.assertTrue(Pacts.Data.get(server).offers().isEmpty() && !Pacts.accept(land.level(), land.hall(1), land.at(0), them).ok(), "the lapsed offer could be accepted");
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "a route after a lapse");
		helper.assertTrue(RealmGameTests.chronicled(land, 0, "The offer to " + land.name(1) + " lapsed unanswered"), "the chronicle of the lapse");
		helper.succeed();
	}

	/** A route set up before 1.7 between two owners becomes a pact when the world loads; nothing stops. */
	//$ gametest_ticks_batch AREA '100' '"pactMigrate"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pactMigrate")
	public void anOldRouteLoadsAsAPact(GameTestHelper helper) {
		Land land = two(helper, new ArrayList<>());
		MinecraftServer server = land.level().getServer();
		Caravans.Data data = Caravans.Data.get(land.level());
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)), "setup: no route");
		Pacts.Data.get(server).setMigrated(false);
		Pacts.settle(server);
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && !pact.alliance(), "the old route didn't become a pact");
		helper.assertTrue(data.routesFrom(land.hall(0)).contains(land.hall(1)) && Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "the route stopped");
		data.toggleRoute(land.hall(0), land.hall(1));
		helper.succeed();
	}

	/** An ally's raid brings riders from the other village, and both owners hear. */
	//$ gametest_ticks_batch AREA '100' '"pactAllyRelief"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pactAllyRelief")
	public void anAllysRaidBringsRelief(GameTestHelper helper) {
		List<Component> theirs = new ArrayList<>();
		Land land = two(helper, theirs);
		MinecraftServer server = land.level().getServer();
		boolean relief = Relief.ENABLED;
		Relief.ENABLED = true;
		Relief.forget();
		Leftovers.after(helper, () -> {
			Relief.ENABLED = relief;
			Relief.forget();
		});
		ReliefGameTests.guards(land, 0, 6);
		helper.assertTrue(Relief.call(land.level(), land.hall(1)) == 0, "riders came without an alliance");
		Relief.forget();
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), true, 0, -1, false, false));
		Pacts.ALLIANCES = false;
		helper.assertTrue(Relief.call(land.level(), land.hall(1)) == 0, "riders came with alliances off");
		Relief.forget();
		Pacts.ALLIANCES = true;
		int riders = Relief.call(land.level(), land.hall(1));
		helper.assertTrue(riders > 0 && !Relief.onTheRoad(land.level(), land.hall(1)).isEmpty(), "no riders from the ally: " + riders);
		helper.assertTrue(theirs.stream().anyMatch(c -> c.getString().contains("from your ally " + land.name(0))), "the raided owner wasn't told: " + theirs);
		helper.assertTrue(RealmGameTests.heard(land, land.name(0) + " sends " + riders + " riders to help your ally " + land.name(1) + " against a raid."), "the ally's owner wasn't told: " + land.heard());
		helper.succeed();
	}

	/** Ending an alliance takes a day's notice: it stands until then, then ends with both chronicles; it survives save and reload. */
	//$ gametest_ticks_batch AREA '100' '"pactAllianceEnd"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pactAllianceEnd")
	public void endingAnAllianceTakesADay(GameTestHelper helper) {
		Land land = two(helper, new ArrayList<>());
		MinecraftServer server = land.level().getServer();
		Pacts.Data data = Pacts.Data.get(server);
		data.put(new Pacts.Pact(land.at(0), land.at(1), true, 0, -1, true, false));
		helper.assertTrue(Pacts.end(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "couldn't give notice");
		Pacts.settle(server);
		Pacts.Pact pact = data.pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.endsAt() == server.overworld().getGameTime() + Pacts.NOTICE, "the alliance ended at once: " + pact);
		helper.assertTrue(!Pacts.allies(server, land.at(1)).isEmpty(), "no longer allies during the notice");
		helper.assertTrue(!Pacts.end(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "notice given twice");
		// save and reload keeps the pact, its notice and gifts, and the offers
		data.addOffer(new Pacts.Offer(land.at(1), land.at(0), false, land.owner().getUUID(), 5));
		CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
		Pacts.Data back = Pacts.Data.load(tag, server.registryAccess());
		helper.assertTrue(back.pacts().equals(data.pacts()) && back.offers().equals(data.offers()), "save and reload: " + back.pacts() + " " + back.offers());
		helper.assertTrue(Pacts.Data.load(new CompoundTag(), server.registryAccess()).pacts().isEmpty(), "an old save has pacts");
		// a day later it ends
		data.put(new Pacts.Pact(pact.a(), pact.b(), true, 0, server.overworld().getGameTime() - 1, true, false));
		Pacts.settle(server);
		helper.assertTrue(data.pact(land.at(0), land.at(1)) == null && Pacts.allies(server, land.at(1)).isEmpty(), "the alliance didn't end after its day");
		helper.assertTrue(RealmGameTests.chronicled(land, 1, "The alliance with " + land.name(0) + " ended"), "the other's chronicle");
		helper.succeed();
	}

	/** {@code tradePacts} off: routes to anyone, no offers; {@code alliances} off: no alliance offered. */
	//$ gametest_ticks_batch AREA '100' '"pactSwitches"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pactSwitches")
	public void theSwitches(GameTestHelper helper) {
		Land land = two(helper, new ArrayList<>());
		Pacts.ALLIANCES = false;
		helper.assertTrue(!Pacts.offer(land.level(), land.hall(0), land.hall(1), true, land.owner()).ok(), "an alliance offered with alliances off");
		Pacts.ENABLED = false;
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "routes need a pact with tradePacts off");
		helper.assertTrue(!Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "a pact offered with tradePacts off");
		helper.assertTrue(!TradePage.tabs().contains(TradePage.Tab.PACTS), "the Pacts tab shows with tradePacts off");
		helper.succeed();
	}
}
