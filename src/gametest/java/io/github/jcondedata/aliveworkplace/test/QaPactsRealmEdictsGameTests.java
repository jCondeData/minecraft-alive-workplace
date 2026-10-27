package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmEdicts;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.CaravanTrade;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA, written from the specs of ROADMAP 33.18 (trade pacts and alliances), 33.22 (realm edicts) and 33.22a (the Common
 * Market's people), aimed at what {@code PactGameTests}, {@code RealmEdictGameTests} and
 * {@code QaPactsBoardStewardGameTests} leave out: the Pacts tab's Decline, End and gifts buttons and the Routes tab's
 * refusal (the player's own entry points); gifts one side only; a pact caravan paid as far as the treasury goes and the
 * money reaching the seller; crossed offers; caravan range and the allies' 1,024 blocks from both sides; the switches
 * off and back on keeping what's saved; the old routes' migration only between strangers; and for realm edicts the
 * 3-day minimum from both sides, who may proclaim, a Duchy's slots and the Royal Hall II's extra one (and a lost slot's
 * lapse), the Royal Highway's upkeep per route with a treasury exactly enough and one short, a reform kept for good
 * across lifting and proclaiming again, members joining and leaving, the Common Market's average and its people,
 * the Levy's dues as far as there's money and room, the Crown's Peace's 128 blocks, and the new sentences.
 */
public class QaPactsRealmEdictsGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- 33.18 helpers --------------------------------------------------------------------------------------------------

	record Two(Land land, ServerPlayer them, List<Component> theirs) {
	}

	/** Two villages (0: the realm-owner's, 1: the neighbour's) on the caravans' list; pacts and alliances on and empty; put back after. */
	static Two two(GameTestHelper helper, VillageRanks.Rank... ranks) {
		Land land = RealmGameTests.land(helper, ranks.length == 0 ? new VillageRanks.Rank[]{VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE} : ranks);
		MinecraftServer server = land.level().getServer();
		boolean pacts = Pacts.ENABLED;
		boolean alliances = Pacts.ALLIANCES;
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Pacts.ALLIANCES = alliances;
			Pacts.Data.get(server).clear();
			Pacts.Data.get(server).setMigrated(true);
			Caravans.Data data = Caravans.Data.get(land.level());
			for (BlockPos hall : land.halls()) {
				data.remove(hall);
			}
		});
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = RealmGameTests.listener(helper, theirs, "qa-neighbour");
		land.entity(1).setOwner(them.getUUID(), "qa-neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		for (int i = 0; i < land.halls().size(); i++) {
			data.setWants(land.hall(i), Component.literal(land.name(i)), List.of());
		}
		return new Two(land, them, theirs);
	}

	static ChoiceMenu tab(Land land, int hall, ServerPlayer player, TradePage.Tab tab) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, land.hall(hall));
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(tab.slot(), player);
		return menu;
	}

	static int find(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().equals(name)) {
				return slot;
			}
		}
		return -1;
	}

	static List<String> letters(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = player.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				out.add(book.pages().get(0).raw().getString());
			}
		}
		return out;
	}

	static boolean said(List<Component> lines, String text) {
		return lines.stream().anyMatch(c -> c.getString().equals(text));
	}

	static void pact(Land land, boolean alliance) {
		Pacts.Data.get(land.level().getServer()).put(new Pacts.Pact(land.at(0), land.at(1), alliance, 0, -1, false, false));
	}

	// ---- 33.18: the player's own buttons ------------------------------------------------------------------------------

	/**
	 * The neighbour declines with the Pacts tab's Decline button: no pact, no route, the offer gone from the tab; the
	 * offerer hears "X declined your offer." and their chronicle notes it; then a new offer may be made.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactDeclineTab"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactDeclineTab")
	public void theTabsDeclineButtonChangesNothingAndANewOfferMayFollow(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), true, land.owner()).ok(), "the alliance offer was refused");
		ChoiceMenu menu = tab(land, 1, two.them(), TradePage.Tab.PACTS);
		int decline = find(menu, "Decline the offer from " + land.name(0));
		helper.assertTrue(decline >= 0 && find(menu, "Accept an alliance with " + land.name(0)) >= 0, "no Accept/Decline for the alliance offer");
		menu.press(decline, two.them());
		helper.assertTrue(Pacts.Data.get(server).pacts().isEmpty() && Pacts.to(server, land.at(1)).isEmpty(), "the decline left a pact or the offer");
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)) && Pacts.allies(server, land.at(1)).isEmpty(), "a route or an ally after declining");
		helper.assertTrue(said(two.theirs(), "You declined the offer from " + land.name(0) + "."), "the decliner wasn't told: " + two.theirs());
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " declined your offer."), "the offerer wasn't told: " + land.heard());
		helper.assertTrue(RealmGameTests.chronicled(land, 0, land.name(1) + " declined the offer"), "the offerer's chronicle");
		ChoiceMenu again = tab(land, 1, two.them(), TradePage.Tab.PACTS);
		helper.assertTrue(find(again, "Accept an alliance with " + land.name(0)) < 0, "the declined offer is still on the tab");
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "no new offer after a decline");
		helper.succeed();
	}

	/**
	 * The Routes tab: another player's village says it needs a pact and a click starts no route (and says why); with a
	 * pact the same click starts it.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactRoutesTab"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactRoutesTab")
	public void theRoutesTabNeedsAPactForAnotherPlayersVillage(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		Caravans.Data data = Caravans.Data.get(land.level());
		ChoiceMenu menu = TradePage.forTest(land.owner(), land.hall(0), TradePage.Tab.ROUTES);
		int slot = find(menu, land.name(1));
		helper.assertTrue(slot >= 0, "the neighbour isn't on the Routes tab");
		helper.assertTrue(RealmGameTests.lore(menu.icon(slot)).contains("Another player's village: needs a trade pact (Pacts tab)"), "the line: " + RealmGameTests.lore(menu.icon(slot)));
		menu.press(slot, land.owner());
		helper.assertTrue(!data.routesFrom(land.hall(0)).contains(land.hall(1)), "a route started without a pact");
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " belongs to another player: offer them a trade pact on the Pacts tab first."), "not told why: " + land.heard());
		pact(land, false);
		menu = TradePage.forTest(land.owner(), land.hall(0), TradePage.Tab.ROUTES);
		slot = find(menu, land.name(1));
		helper.assertTrue(!RealmGameTests.lore(menu.icon(slot)).contains("Another player's village: needs a trade pact (Pacts tab)"), "still says it needs a pact");
		menu.press(slot, land.owner());
		helper.assertTrue(data.routesFrom(land.hall(0)).contains(land.hall(1)), "no route with a pact");
		helper.succeed();
	}

	/**
	 * Gifts, from the tab's pact line: on for one side, its caravans ask nothing while the other side's still pay; the
	 * line says so; a second click turns them off and the cargo is paid for again.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactGifts"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactGifts")
	public void giftsAreOneSidedAndAClickTurnsThemBackOff(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		Caravans.Data data = Caravans.Data.get(level);
		pact(land, false);
		TradeGoods.Good good = TradeGoods.all().get(0);
		land.entity(0).setTreasury(100_000);
		land.entity(1).setTreasury(100_000);
		ChoiceMenu menu = tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int line = find(menu, "Trade pact with " + land.name(1));
		helper.assertTrue(line >= 0 && RealmGameTests.lore(menu.icon(line)).contains("They pay for our cargo at board prices (click: gifts on)"), "the pact line: " + (line < 0 ? "none" : RealmGameTests.lore(menu.icon(line))));
		menu.press(line, land.owner());
		helper.assertTrue(Pacts.gives(level, land.hall(0), land.hall(1)) && !Pacts.gives(level, land.hall(1), land.hall(0)), "gifts weren't one-sided");
		helper.assertTrue(RealmGameTests.heard(land, "Gifts on: caravans to " + land.name(1) + " ask nothing for their cargo."), "not told: " + land.heard());
		menu = tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(RealmGameTests.lore(menu.icon(find(menu, "Trade pact with " + land.name(1)))).contains("Gifts on: our caravans ask nothing (click: off)"), "the line with gifts on");
		helper.assertTrue(Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), data) == 0, "our gift was paid for");
		helper.assertTrue(Caravans.payPact(level, land.hall(0), land.hall(1), List.of(new ItemStack(good.icon(), good.bundle())), data) > 0, "their cargo went unpaid because of our gifts");
		menu.press(find(menu, "Trade pact with " + land.name(1)), land.owner());
		helper.assertTrue(!Pacts.gives(level, land.hall(0), land.hall(1)), "gifts didn't turn off");
		helper.assertTrue(Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), data) > 0, "not paid for after gifts went off");
		helper.succeed();
	}

	/**
	 * A pact caravan's cargo: a buyer one hundredth short of the bundle's price pays all it has; half a bundle costs
	 * half; an item that's no trade good travels free; the seller's next booking puts the money in its treasury.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactPay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactPay")
	public void pactCargoIsPaidAsFarAsTheTreasuryGoesAndReachesTheSeller(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		Caravans.Data data = Caravans.Data.get(level);
		data.takeSales(land.hall(0));
		pact(land, false);
		TradeGoods.Good good = TradeGoods.all().stream().filter(g -> g.bundle() % 2 == 0).findFirst().orElseThrow();
		int price = Board.price(level, land.hall(1), good);
		helper.assertTrue(price > 1, "setup: a price of " + price);
		land.entity(1).setTreasury(price - 1);
		int paid = Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), data);
		helper.assertTrue(paid == price - 1 && land.entity(1).treasury() == 0, "one short: paid " + paid + " of " + price + ", left " + land.entity(1).treasury());
		land.entity(1).setTreasury(100_000);
		int half = Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle() / 2)), data);
		helper.assertTrue(half == price * (good.bundle() / 2) / good.bundle(), "half a bundle: " + half + " of " + price);
		int free = Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(Items.COMMAND_BLOCK, 1)), data);
		helper.assertTrue(free == 0 && land.entity(1).treasury() == 100_000 - half, "an item that's no trade good was charged: " + free);
		land.entity(0).setTreasury(0);
		CaravanTrade.book(level, land.hall(0), data);
		helper.assertTrue(land.entity(0).treasury() == paid + half, "the seller got " + land.entity(0).treasury() + ", wanted " + (paid + half));
		helper.succeed();
	}

	/**
	 * The neighbour ends the trade pact with the tab's End button: at once, routes both ways stop, the other owner gets a
	 * letter, both chronicles note it.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactEndTab"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactEndTab")
	public void theTabsEndButtonEndsAPactAtOnceBothWays(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Caravans.Data data = Caravans.Data.get(land.level());
		pact(land, false);
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)) && data.toggleRoute(land.hall(1), land.hall(0)), "setup: routes both ways");
		ChoiceMenu menu = tab(land, 1, two.them(), TradePage.Tab.PACTS);
		int end = find(menu, "End the trade pact with " + land.name(0));
		helper.assertTrue(end >= 0, "no End button");
		menu.press(end, two.them());
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null, "the pact didn't end at once");
		helper.assertTrue(!data.routesFrom(land.hall(0)).contains(land.hall(1)) && !data.routesFrom(land.hall(1)).contains(land.hall(0)), "a route survived the pact");
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "routes still allowed");
		helper.assertTrue(letters(land.owner()).stream().anyMatch(l -> l.contains("ends the trade pact with " + land.name(0))), "the other owner's letter: " + letters(land.owner()));
		helper.assertTrue(RealmGameTests.chronicled(land, 0, "The trade pact with " + land.name(1) + " ended")
			&& RealmGameTests.chronicled(land, 1, "The trade pact with " + land.name(0) + " ended"), "the chronicles");
		helper.succeed();
	}

	/**
	 * The tab's End on an alliance gives a day's notice: the alliance and its routes stand, the line says "Ends in 24
	 * hours", the other owner's letter says it ends in a day, and a second End is refused.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactAllianceEndTab"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactAllianceEndTab")
	public void theTabsEndButtonGivesAnAllianceADaysNotice(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Caravans.Data data = Caravans.Data.get(land.level());
		pact(land, true);
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		ChoiceMenu menu = tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int end = find(menu, "End the alliance with " + land.name(1) + " (a day's notice)");
		helper.assertTrue(end >= 0, "no End button for the alliance");
		menu.press(end, land.owner());
		Pacts.Pact pact = Pacts.Data.get(server).pact(land.at(0), land.at(1));
		helper.assertTrue(pact != null && pact.endsAt() == server.overworld().getGameTime() + Pacts.NOTICE, "no day's notice: " + pact);
		helper.assertTrue(data.routesFrom(land.hall(0)).contains(land.hall(1)) && !Pacts.allies(server, land.at(1)).isEmpty(), "the alliance stopped during its notice");
		menu = tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int line = find(menu, "Alliance with " + land.name(1));
		helper.assertTrue(line >= 0 && RealmGameTests.lore(menu.icon(line)).contains("Ends in 24 hours"), "the line: " + (line < 0 ? "none" : RealmGameTests.lore(menu.icon(line))));
		helper.assertTrue(letters(two.them()).stream().anyMatch(l -> l.contains("the alliance with " + land.name(1) + " ends in a day")), "the letter: " + letters(two.them()));
		menu.press(find(menu, "End the alliance with " + land.name(1) + " (a day's notice)"), land.owner());
		helper.assertTrue(RealmGameTests.heard(land, "The alliance with " + land.name(1) + " is already ending."), "a second notice wasn't refused: " + land.heard());
		helper.succeed();
	}

	// ---- 33.18: offers ------------------------------------------------------------------------------------------------

	/** Crossed offers: each owner offers the other; one Accept makes one pact and leaves no stale offer on either tab. */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactCrossed"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactCrossed")
	public void crossedOffersMakeOnePactAndLeaveNoStaleOffer(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the first offer");
		helper.assertTrue(Pacts.offer(land.level(), land.hall(1), land.hall(0), false, two.them()).ok(), "the crossed offer");
		ChoiceMenu menu = tab(land, 1, two.them(), TradePage.Tab.PACTS);
		menu.press(find(menu, "Accept a trade pact with " + land.name(0)), two.them());
		helper.assertTrue(Pacts.Data.get(server).pacts().size() == 1, "pacts: " + Pacts.Data.get(server).pacts());
		helper.assertTrue(Pacts.to(server, land.at(0)).isEmpty() && Pacts.to(server, land.at(1)).isEmpty(), "a stale offer: " + Pacts.Data.get(server).offers());
		ChoiceMenu ours = tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		helper.assertTrue(find(ours, "Accept a trade pact with " + land.name(1)) < 0, "the crossed offer still shows Accept");
		helper.succeed();
	}

	/** A lapsed offer: the offerer hears it in chat, and the other tab shows no Accept for it. */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactLapseTold"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactLapseTold")
	public void aLapsedOfferIsToldAndLeavesTheTab(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), true, two.them().getUUID(), now - Pacts.LAPSE - 1));
		Pacts.settle(server);
		helper.assertTrue(RealmGameTests.heard(land, "Your offer to " + land.name(1) + " lapsed: no answer came in 3 days."), "the offerer wasn't told: " + land.heard());
		ChoiceMenu menu = tab(land, 1, two.them(), TradePage.Tab.PACTS);
		helper.assertTrue(find(menu, "Accept an alliance with " + land.name(0)) < 0, "the lapsed offer is on the tab");
		helper.assertTrue(Pacts.Data.get(server).offers().isEmpty(), "the lapsed offer is still saved");
		helper.succeed();
	}

	/** Caravan range from both sides: a village (as last seen) exactly 2,048 blocks off can be offered a pact; one block more is refused, and says why. */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactRange"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactRange")
	public void anOfferReachesExactlyCaravanRangeAndNotABlockMore(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		int range = Caravans.RANGE;
		BlockPos edge = land.hall(0).offset(range, 0, 0);
		BlockPos beyond = land.hall(0).offset(range + 1, 0, 0);
		long now = server.overworld().getGameTime();
		land.data().putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), edge), Component.literal("Edgewater"), Optional.of(two.them().getUUID()), "qa-neighbour", "village", 5, 50, now));
		land.data().putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), beyond), Component.literal("Farhold"), Optional.of(two.them().getUUID()), "qa-neighbour", "village", 5, 50, now));
		helper.assertTrue(Pacts.offer(level, land.hall(0), edge, false, land.owner()).ok(), "a village exactly " + range + " blocks off was out of range");
		Pacts.Result far = Pacts.offer(level, land.hall(0), beyond, false, land.owner());
		helper.assertTrue(!far.ok() && far.message().getString().equals("Farhold is more than " + range + " blocks away, beyond caravan range."), "one block beyond: " + far.message().getString());
		helper.assertTrue(Pacts.to(server, GlobalPos.of(level.dimension(), beyond)).isEmpty(), "an offer waits beyond range");
		helper.succeed();
	}

	/**
	 * An ally's raid 1,024 blocks off brings riders, one block more none; the riders cost the allied village's treasury
	 * nothing (no realm pays for allies).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactAllyReach"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactAllyReach")
	public void anAllyRidesExactly1024BlocksAndNotABlockMore(GameTestHelper helper) {
		Two two = two(helper);
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
		ReliefGameTests.guards(land, 0, 6).forEach(g -> g.setNoAi(true));
		land.entity(0).setTreasury(5000);
		BlockPos edge = land.hall(0).offset(Relief.RANGE, 0, 0);
		BlockPos beyond = land.hall(0).offset(Relief.RANGE + 1, 0, 0);
		Pacts.Data pacts = Pacts.Data.get(server);
		pacts.put(new Pacts.Pact(land.at(0), GlobalPos.of(level.dimension(), edge), true, 0, -1, false, false));
		int riders = Relief.call(level, edge);
		helper.assertTrue(riders > 0, "no riders to an ally exactly " + Relief.RANGE + " blocks off");
		helper.assertTrue(land.entity(0).treasury() == 5000, "the ally's riders cost its treasury " + (5000 - land.entity(0).treasury()));
		Relief.forget();
		pacts.clear();
		pacts.setMigrated(true);
		pacts.put(new Pacts.Pact(land.at(0), GlobalPos.of(level.dimension(), beyond), true, 0, -1, false, false));
		helper.assertTrue(Relief.call(level, beyond) == 0, "riders to an ally a block beyond " + Relief.RANGE);
		helper.succeed();
	}

	// ---- 33.18: switches and migration -----------------------------------------------------------------------------------

	/**
	 * {@code tradePacts} off: no Pacts tab, cargo unpaid, routes need no pact; the pact stays saved and back on it still
	 * allows routes (a stranger without one is refused again).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactOffOn"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactOffOn")
	public void tradePactsOffAndBackOnKeepsThePact(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		land.entity(2).setOwner(two.them().getUUID(), "qa-neighbour");
		Realms.round(level, land.hall(2), land.entity(2), 6);
		pact(land, false);
		TradeGoods.Good good = TradeGoods.all().get(0);
		land.entity(1).setTreasury(100_000);
		Pacts.ENABLED = false;
		helper.assertTrue(!TradePage.tabs().contains(TradePage.Tab.PACTS), "the Pacts tab with tradePacts off");
		helper.assertTrue(Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), Caravans.Data.get(level)) == 0, "paid with tradePacts off");
		helper.assertTrue(Pacts.mayRoute(level, land.hall(0), land.hall(2)), "a route needed a pact with tradePacts off");
		Pacts.settle(server);
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) != null, "the pact wasn't kept while off");
		Pacts.ENABLED = true;
		helper.assertTrue(Pacts.mayRoute(level, land.hall(0), land.hall(1)) && !Pacts.mayRoute(level, land.hall(0), land.hall(2)), "back on: the pact's route, and none without one");
		helper.assertTrue(Caravans.payPact(level, land.hall(1), land.hall(0), List.of(new ItemStack(good.icon(), good.bundle())), Caravans.Data.get(level)) > 0, "unpaid back on");
		helper.succeed();
	}

	/** {@code alliances} off: no allies, but the alliance still allows routes and stays saved; back on, the villages are allies again. */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactAlliancesOffOn"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactAlliancesOffOn")
	public void alliancesOffAndBackOnKeepsTheAlliance(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		pact(land, true);
		Pacts.ALLIANCES = false;
		helper.assertTrue(Pacts.allies(server, land.at(0)).isEmpty(), "allies with alliances off");
		helper.assertTrue(Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)), "no route on an alliance with alliances off");
		Pacts.settle(server);
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) != null && Pacts.Data.get(server).pact(land.at(0), land.at(1)).alliance(), "the alliance wasn't kept");
		helper.assertTrue(!Pacts.accept(land.level(), land.hall(1), land.at(0), two.them()).ok(), "accepted with nothing offered");
		Pacts.ALLIANCES = true;
		helper.assertTrue(Pacts.allies(server, land.at(0)).equals(List.of(land.at(1))), "not allies back on: " + Pacts.allies(server, land.at(0)));
		helper.succeed();
	}

	/**
	 * The world loads a pre-1.7 file: a route to another owner's village becomes a trade pact (even with {@code tradePacts}
	 * off at the time), a route between one owner's villages doesn't; it happens once.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrePactMigrate"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrePactMigrate")
	public void oldRoutesBecomePactsOnlyBetweenStrangersAndOnce(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data data = Caravans.Data.get(level);
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)) && data.toggleRoute(land.hall(0), land.hall(2)), "setup: routes");
		Pacts.Data pacts = Pacts.Data.get(server);
		pacts.setMigrated(false);
		Pacts.ENABLED = false;
		Pacts.settle(server);
		Pacts.ENABLED = true;
		Pacts.Pact made = pacts.pact(land.at(0), land.at(1));
		helper.assertTrue(made != null && !made.alliance(), "the route to the neighbour didn't become a pact");
		helper.assertTrue(pacts.pact(land.at(0), land.at(2)) == null, "a pact between one owner's own villages");
		helper.assertTrue(Pacts.mayRoute(level, land.hall(0), land.hall(1)) && data.routesFrom(land.hall(0)).contains(land.hall(1)), "the old route stopped");
		pacts.clear(); // the lists only: the migration's mark stays
		Pacts.settle(server);
		helper.assertTrue(pacts.pact(land.at(0), land.at(1)) == null, "the migration ran twice");
		data.toggleRoute(land.hall(0), land.hall(1));
		data.toggleRoute(land.hall(0), land.hall(2));
		helper.succeed();
	}

	// ---- 33.22: the Book and its slots ----------------------------------------------------------------------------------

	/** Lifting from the Book: on day 2 refused with the day it may be lifted; on day 3 lifted, every owner told, the member's chronicle. */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictMinDays"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictMinDays")
	public void aRealmEdictStandsThreeDaysNotTwo(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		MinecraftServer server = land.level().getServer();
		String title = Realms.title(server, RealmEdictGameTests.theRealm(land)).getString();
		long day = RealmEdictGameTests.today(land);
		ChoiceMenu menu = EdictBook.forTest(land.owner(), land.hall(0));
		int highway = RealmEdictGameTests.find(menu, "Royal Highway");
		RealmEdictGameTests.click(menu, highway, land.owner());
		RealmEdictGameTests.click(menu, highway, land.owner());
		helper.assertTrue(RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).size() == 1, "not proclaimed by two clicks");
		RealmEdictGameTests.nextDay(land, 2);
		menu = EdictBook.forTest(land.owner(), land.hall(0));
		RealmEdictGameTests.click(menu, RealmEdicts.SLOTS[0], land.owner());
		helper.assertTrue(RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).size() == 1, "lifted on its third day (2 days in force)");
		helper.assertTrue(RealmEdictGameTests.heard(land, "Royal Highway must stay in force at least 3 days: it can be lifted from day " + (day + 3) + "."), "the refusal: " + land.heard());
		RealmEdictGameTests.nextDay(land, 1);
		menu = EdictBook.forTest(land.owner(), land.hall(0));
		RealmEdictGameTests.click(menu, RealmEdicts.SLOTS[0], land.owner());
		helper.assertTrue(RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).isEmpty(), "not lifted after 3 days");
		helper.assertTrue(RealmEdictGameTests.heard(land, title + " lifts the realm edict Royal Highway."), "not told of the lifting: " + land.heard());
		helper.assertTrue(land.entity(1).chronicle().stream().anyMatch(e -> e.text().getString().equals("The realm edict Royal Highway was lifted")), "the member's chronicle");
		helper.succeed();
	}

	/**
	 * Who proclaims: the owner of another member can't at the capital (and its Book's clicks do nothing); the capital
	 * owner's friend can; a member's hall isn't the capital; the same edict twice is refused.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictWho"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictWho")
	public void onlyTheCapitalsRulersProclaimAndOnlyOnce(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		String title = Realms.title(server, RealmEdictGameTests.theRealm(land)).getString();
		ServerPlayer member = RealmGameTests.listener(helper, new ArrayList<>(), "qa-member-owner");
		land.entity(1).setOwner(member.getUUID(), "qa-member-owner");
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "qa-friend");
		Friends.get(server).add(land.owner().getUUID(), friend.getUUID(), "qa-friend");
		Leftovers.after(helper, () -> Friends.get(server).remove(land.owner().getUUID(), friend.getUUID()));
		Edicts.Result theirs = RealmEdicts.proclaim(level, land.hall(0), member, RealmEdictGameTests.edict("royal_highway"));
		helper.assertTrue(!theirs.done() && theirs.message().getString().equals("Only Owner and their friends can rule " + title + "."), "a member's owner proclaimed: " + theirs.message().getString());
		ChoiceMenu book = EdictBook.forTest(member, land.hall(0));
		int highway = RealmEdictGameTests.find(book, "Royal Highway");
		if (highway >= 0) {
			RealmEdictGameTests.click(book, highway, member);
			RealmEdictGameTests.click(book, highway, member);
		}
		helper.assertTrue(RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).isEmpty(), "a member's owner proclaimed from the capital's Book");
		Edicts.Result atMember = RealmEdicts.proclaim(level, land.hall(1), member, RealmEdictGameTests.edict("royal_highway"));
		helper.assertTrue(!atMember.done() && atMember.message().getString().equals(land.name(1) + " isn't the capital of a realm."), "at a member: " + atMember.message().getString());
		helper.assertTrue(RealmEdicts.proclaim(level, land.hall(0), friend, RealmEdictGameTests.edict("royal_highway")).done(), "the capital owner's friend couldn't proclaim");
		RealmData.Realm realm = RealmEdictGameTests.theRealm(land);
		land.data().putRealm(realm.withRank(Realms.Rank.DUCHY.id()));
		Edicts.Result twice = RealmEdicts.proclaim(level, land.hall(0), land.owner(), RealmEdictGameTests.edict("royal_highway"));
		helper.assertTrue(!twice.done() && twice.message().getString().equals("Royal Highway is already in force in " + Realms.title(server, RealmEdictGameTests.theRealm(land)).getString() + "."),
			"proclaimed twice: " + twice.message().getString());
		helper.succeed();
	}

	/**
	 * A Duchy's Book: 2 free slots and 2 locked; a third edict refused; a finished Royal Hall II by the capital opens a
	 * third; when the hall comes down the newest edict lapses at dawn and every owner hears why; a Kingdom with the II
	 * holds four.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictSlots"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictSlots")
	public void aDuchyHasTwoSlotsAndTheRoyalHallIIAddsOne(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		Leftovers.finished(helper);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		land.data().putRealm(RealmEdictGameTests.theRealm(land).withRank(Realms.Rank.DUCHY.id()));
		RealmEdictGameTests.treasury(land, 100_000);
		ChoiceMenu menu = EdictBook.forTest(land.owner(), land.hall(0));
		helper.assertTrue(menu.icon(RealmEdicts.SLOTS[0]).is(Items.MAP) && menu.icon(RealmEdicts.SLOTS[1]).is(Items.MAP), "a Duchy's two free slots");
		helper.assertTrue(menu.icon(RealmEdicts.SLOTS[2]).is(Items.GRAY_DYE)
			&& RealmEdictGameTests.lore(menu.icon(RealmEdicts.SLOTS[2])).contains("Opens when the realm is a Kingdom, or a Duchy with the Royal Hall II"), "the third slot: " + RealmEdictGameTests.lore(menu.icon(RealmEdicts.SLOTS[2])));
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		RealmEdictGameTests.proclaim(helper, land, "royal_highway");
		Edicts.Result third = RealmEdicts.proclaim(level, land.hall(0), land.owner(), RealmEdictGameTests.edict("levy_of_arms"));
		helper.assertTrue(!third.done() && third.message().getString().contains("a Duchy may keep 2 edicts in force"), "a third in a Duchy: " + third.message().getString());
		// the Royal Hall II by the capital: a third slot
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement by = new BlueprintData.Placement(level.dimension().location(), land.hall(0).offset(6, 0, -3), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.ROYAL_HALL_2.id(), by, land.owner().getUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(by));
		RealmEdictGameTests.proclaim(helper, land, "levy_of_arms");
		menu = EdictBook.forTest(land.owner(), land.hall(0));
		String title = Realms.title(server, RealmEdictGameTests.theRealm(land)).getString();
		helper.assertTrue(RealmEdictGameTests.lore(menu.icon(EdictBook.HEADER)).contains("Realm edicts: 3 in force of 3 edicts (" + title + ")"), "the header: " + RealmEdictGameTests.lore(menu.icon(EdictBook.HEADER)));
		helper.assertTrue(menu.icon(RealmEdicts.SLOTS[3]).is(Items.GRAY_DYE), "a fourth slot in a Duchy with the II");
		// the hall comes down: the newest lapses at dawn
		sites.forgetFinished(by);
		RealmEdictGameTests.nextDay(land, 1);
		RealmEdicts.dawn(server, RealmEdictGameTests.today(land));
		List<String> held = RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).stream().map(Edicts.InForce::id).toList();
		helper.assertTrue(held.equals(List.of(RealmEdictGameTests.full("common_market"), RealmEdictGameTests.full("royal_highway"))), "after losing the slot: " + held);
		helper.assertTrue(RealmEdictGameTests.heard(land, "lost the realm edict Levy of Arms: a Duchy has fewer realm slots"), "not told why: " + land.heard());
		// a Kingdom with the II: four
		sites.recordFinished(StarterBlueprints.ROYAL_HALL_2.id(), by, land.owner().getUUID());
		land.data().putRealm(RealmEdictGameTests.theRealm(land).withRank(Realms.Rank.KINGDOM.id()));
		RealmEdictGameTests.proclaim(helper, land, "levy_of_arms");
		RealmEdictGameTests.proclaim(helper, land, "realm_festival");
		menu = EdictBook.forTest(land.owner(), land.hall(0));
		for (int s : RealmEdicts.SLOTS) {
			helper.assertTrue(!menu.icon(s).is(Items.GRAY_DYE) && !menu.icon(s).is(Items.MAP), "a Kingdom with the II: slot " + s + " is " + menu.icon(s));
		}
		helper.assertTrue(!RealmEdicts.proclaim(level, land.hall(0), land.owner(), RealmEdictGameTests.edict("crowns_peace")).done(), "a fifth edict");
		helper.succeed();
	}

	// ---- 33.22: the edicts ----------------------------------------------------------------------------------------------

	/**
	 * Royal Highway's upkeep: a route each way between two members counts once, a second member route once more, a route
	 * to a village outside the realm not at all: 2 emeralds. A treasury of exactly 2 pays it; one hundredth short, the
	 * edict lapses and the treasury keeps all of it.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictHighwayUpkeep"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictHighwayUpkeep")
	public void theHighwaysUpkeepIsAnEmeraldARouteAndExactMoneyPays(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 2, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data data = Caravans.Data.get(level);
		for (int i = 0; i < 4; i++) {
			data.setWants(land.hall(i), Component.literal(land.name(i)), List.of());
		}
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)) && data.toggleRoute(land.hall(1), land.hall(0))
			&& data.toggleRoute(land.hall(1), land.hall(2)) && data.toggleRoute(land.hall(0), land.hall(3)), "setup: routes");
		RealmEdictGameTests.proclaim(helper, land, "royal_highway");
		helper.assertTrue(RealmEdicts.routes(server, RealmEdictGameTests.theRealm(land)) == 2, "routes: " + RealmEdicts.routes(server, RealmEdictGameTests.theRealm(land)));
		RealmEdictGameTests.treasury(land, 200);
		long paid = RealmEdicts.dawn(server, RealmEdictGameTests.today(land) + 1);
		helper.assertTrue(paid == 200 && RealmEdictGameTests.theRealm(land).treasury() == 0, "exactly enough: paid " + paid + ", left " + RealmEdictGameTests.theRealm(land).treasury());
		helper.assertTrue(RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).size() == 1, "lapsed with exactly enough");
		RealmEdictGameTests.treasury(land, 199);
		RealmEdicts.dawn(server, RealmEdictGameTests.today(land) + 2);
		helper.assertTrue(RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).isEmpty(), "still in force one hundredth short");
		helper.assertTrue(RealmEdictGameTests.theRealm(land).treasury() == 199, "a part of the upkeep was taken: " + RealmEdictGameTests.theRealm(land).treasury());
		helper.assertTrue(RealmEdictGameTests.heard(land, "lost the realm edict Royal Highway: the realm treasury hadn't the 2.00 emeralds of its upkeep"), "the lapse: " + land.heard());
		helper.succeed();
	}

	/**
	 * A reform is for good: the Royal Highway reformed, lifted after 3 days and proclaimed again is still reformed (no
	 * upkeep, the boost on). An unreformed edict's count starts again when it's proclaimed again.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictForGood"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictForGood")
	public void aReformOutlivesLiftingAndAnUnreformedCountStartsAgain(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		// the unreformed Common Market: 5 trades, lifted, proclaimed again: 0
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		for (int i = 0; i < 5; i++) {
			RealmEdicts.boardTrade(level, land.hall(1));
		}
		helper.assertTrue(RealmEdicts.count(server, RealmEdictGameTests.theRealm(land), RealmEdictGameTests.full("common_market")) == 5, "setup: 5 trades");
		RealmEdictGameTests.nextDay(land, 3);
		helper.assertTrue(RealmEdicts.lift(level, land.hall(0), land.owner(), RealmEdictGameTests.full("common_market")).done(), "couldn't lift the market");
		RealmEdicts.boardTrade(level, land.hall(1));
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		helper.assertTrue(RealmEdicts.count(server, RealmEdictGameTests.theRealm(land), RealmEdictGameTests.full("common_market")) == 0, "the count didn't start again: "
			+ RealmEdicts.count(server, RealmEdictGameTests.theRealm(land), RealmEdictGameTests.full("common_market")));
		RealmEdictGameTests.nextDay(land, 3);
		helper.assertTrue(RealmEdicts.lift(level, land.hall(0), land.owner(), RealmEdictGameTests.full("common_market")).done(), "couldn't lift the market again");
		// the Royal Highway reformed, lifted, proclaimed again
		RealmEdictGameTests.proclaim(helper, land, "royal_highway");
		RealmEdictGameTests.bring(helper, land, "royal_highway", Items.STONE_BRICKS, 128);
		for (int i = 0; i < 12; i++) {
			RealmEdicts.caravanSent(level, land.hall(i % 2), land.hall(1 - i % 2));
		}
		helper.assertTrue(RealmEdictGameTests.reformed(land, "royal_highway"), "setup: not reformed");
		RealmEdictGameTests.nextDay(land, 3);
		helper.assertTrue(RealmEdicts.lift(level, land.hall(0), land.owner(), RealmEdictGameTests.full("royal_highway")).done(), "couldn't lift the highway");
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(0), land.hall(1)) == 0, "the boost outlived the lifting");
		RealmEdictGameTests.proclaim(helper, land, "royal_highway");
		helper.assertTrue(RealmEdictGameTests.reformed(land, "royal_highway"), "the reform was lost by lifting");
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)), "setup: a route");
		RealmEdictGameTests.treasury(land, 0);
		RealmEdicts.dawn(server, RealmEdictGameTests.today(land) + 1);
		helper.assertTrue(RealmEdicts.inForce(server, RealmEdictGameTests.theRealm(land)).size() == 1, "the reformed highway lapsed for its upkeep after proclaiming again");
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(0), land.hall(1)) == 2, "the boost after proclaiming again");
		helper.succeed();
	}

	/** In force in every member: a village joining gets the Royal Highway's boost with the others; one leaving loses it. */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictJoinLeave"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictJoinLeave")
	public void aRealmEdictReachesAJoinerAndLeavesWithALeaver(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		RealmEdictGameTests.proclaim(helper, land, "royal_highway");
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(1), land.hall(2)) == 0, "a village outside the realm had the boost");
		helper.assertTrue(Realms.add(level, land.hall(0), land.at(2), land.owner()).ok(), "hall 2 didn't join");
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(1), land.hall(2)) == 2 && RealmEdicts.extraStacks(level, land.hall(2), land.hall(0)) == 2, "the joiner didn't get the boost");
		Realms.leave(level, land.hall(2), land.owner());
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(2), land.hall(0)) == 0, "the leaver kept the boost");
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(1), land.hall(0)) == 2, "the members lost it");
		helper.succeed();
	}

	/**
	 * The Common Market (33.22, 33.22a): the members' average price, a member with no price for the good left out, a
	 * village outside the realm keeping its own; the owner of another member and the capital owner's friend buy at the
	 * board's price, a stranger pays 10% more; outside the realm the owner pays the usual 10%; lifted, the levy is back.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictMarket"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictMarket")
	public void theCommonMarketsAverageAndItsPeople(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 2, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		TradeGoods.Good good = TradeGoods.all().get(0);
		Caravans.Data data = Caravans.Data.get(level);
		long today = RealmEdictGameTests.today(land);
		int[] prices = {100, 200, -1, 1000};
		for (int i = 0; i < 4; i++) {
			data.setWants(land.hall(i), Component.literal(land.name(i)), List.of());
			data.setMarket(land.hall(i), new Market(List.of(), List.of(), prices[i] < 0 ? Map.of() : Map.of(good.id(), new Market.Price(prices[i], prices[i], 0)), today, List.of()));
		}
		ServerPlayer other = RealmGameTests.listener(helper, new ArrayList<>(), "qa-market-member");
		land.entity(1).setOwner(other.getUUID(), "qa-market-member");
		ServerPlayer friend = RealmGameTests.listener(helper, new ArrayList<>(), "qa-market-friend");
		Friends.get(server).add(land.owner().getUUID(), friend.getUUID(), "qa-market-friend");
		Leftovers.after(helper, () -> Friends.get(server).remove(land.owner().getUUID(), friend.getUUID()));
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "qa-market-stranger");
		RealmEdictGameTests.proclaim(helper, land, "common_market");
		helper.assertTrue(Board.price(level, land.hall(0), good) == 150 && Board.price(level, land.hall(1), good) == 150, "the average of 1 and 2 (a member without a price left out): "
			+ Board.price(level, land.hall(0), good) + " / " + Board.price(level, land.hall(1), good));
		helper.assertTrue(Board.price(level, land.hall(3), good) == 1000, "a village outside the realm: " + Board.price(level, land.hall(3), good));
		data.setMarket(land.hall(2), new Market(List.of(), List.of(), Map.of(good.id(), new Market.Price(600, 600, 0)), today, List.of()));
		helper.assertTrue(Board.price(level, land.hall(0), good) == 300, "the average of three: " + Board.price(level, land.hall(0), good));
		helper.assertTrue(TradePage.selling(level, land.hall(0), 300, other) == 300, "another member's owner at the capital's board: " + TradePage.selling(level, land.hall(0), 300, other));
		helper.assertTrue(TradePage.selling(level, land.hall(2), 300, friend) == 300, "the capital owner's friend: " + TradePage.selling(level, land.hall(2), 300, friend));
		helper.assertTrue(TradePage.selling(level, land.hall(1), 300, stranger) == 330, "a stranger: " + TradePage.selling(level, land.hall(1), 300, stranger));
		helper.assertTrue(TradePage.selling(level, land.hall(3), 1000, land.owner()) == 1100, "outside the realm: " + TradePage.selling(level, land.hall(3), 1000, land.owner()));
		helper.assertTrue(RealmTreasury.levy(server, RealmEdictGameTests.theRealm(land)) == 15, "the levy");
		RealmEdictGameTests.nextDay(land, 3);
		helper.assertTrue(RealmEdicts.lift(level, land.hall(0), land.owner(), RealmEdictGameTests.full("common_market")).done(), "couldn't lift");
		helper.assertTrue(RealmTreasury.levy(server, RealmEdictGameTests.theRealm(land)) == 10 && Board.price(level, land.hall(0), good) == 100
			&& TradePage.selling(level, land.hall(0), 100, other) == 110, "after lifting: levy " + RealmTreasury.levy(server, RealmEdictGameTests.theRealm(land)) + ", price " + Board.price(level, land.hall(0), good));
		helper.succeed();
	}

	/**
	 * The Levy of Arms' dues: a member with 1.5 emeralds pays the 1.5 it has (not 2, never below zero); a realm treasury
	 * half an emerald from its cap takes only that half.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictDues"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictDues")
	public void theLevysDuesGoAsFarAsTheMoneyAndTheRoom(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		RealmEdictGameTests.treasury(land, 0);
		RealmEdictGameTests.proclaim(helper, land, "levy_of_arms");
		land.entity(1).setTreasury(150);
		long paid = RealmEdicts.dues(level, land.hall(1), land.entity(1), 1);
		helper.assertTrue(paid == 150 && land.entity(1).treasury() == 0 && RealmEdictGameTests.theRealm(land).treasury() == 150,
			"a poor member: paid " + paid + ", left " + land.entity(1).treasury() + ", realm " + RealmEdictGameTests.theRealm(land).treasury());
		long cap = RealmTreasury.capCents(server, RealmEdictGameTests.theRealm(land));
		RealmEdictGameTests.treasury(land, cap - 50);
		land.entity(1).setTreasury(1000);
		paid = RealmEdicts.dues(level, land.hall(1), land.entity(1), 1);
		helper.assertTrue(paid == 50 && land.entity(1).treasury() == 950 && RealmEdictGameTests.theRealm(land).treasury() == cap,
			"a nearly full realm: paid " + paid + ", member " + land.entity(1).treasury());
		helper.assertTrue(RealmEdicts.dues(level, land.hall(1), land.entity(1), 1) == 0 && land.entity(1).treasury() == 950, "dues into a full realm");
		helper.succeed();
	}

	/** The Crown's Peace keeps bandits 128 blocks from a member, not 129; lifted after 3 days, it keeps nothing. */
	//$ gametest_ticks_batch HUGE '100' '"qaPreEdictPeaceReach"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPreEdictPeaceReach")
	public void theCrownsPeaceReaches128BlocksNotOneMore(GameTestHelper helper) {
		Land land = RealmEdictGameTests.realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		// hall 1 is 9 blocks east of hall 0; straight south of hall 1 every other member is farther
		BlockPos at128 = land.hall(1).offset(0, 0, 128);
		BlockPos at129 = land.hall(1).offset(0, 0, 129);
		helper.assertTrue(!RealmEdicts.peaceNear(level, at128), "peace before it's proclaimed");
		RealmEdictGameTests.proclaim(helper, land, "crowns_peace");
		helper.assertTrue(RealmEdicts.peaceNear(level, at128), "no peace 128 blocks from a member");
		helper.assertTrue(!RealmEdicts.peaceNear(level, at129), "peace 129 blocks from a member");
		RealmEdictGameTests.nextDay(land, 3);
		helper.assertTrue(RealmEdicts.lift(level, land.hall(0), land.owner(), RealmEdictGameTests.full("crowns_peace")).done(), "couldn't lift");
		helper.assertTrue(!RealmEdicts.peaceNear(level, at128), "peace after lifting");
		helper.succeed();
	}

	// ---- the sentences --------------------------------------------------------------------------------------------------

	private static final Pattern BRITISH = Pattern.compile(
		"(?i)\\b(colour\\w*|honour\\w*|favour\\w*|neighbour\\w*|defence|armour\\w*|grey|cancelled|jeweller\\w*|centre\\w*|travell\\w*|metre\\w*|organis\\w*|realis\\w*|recognis\\w*|labour\\w*|harbour\\w*|programme)\\b");

	/**
	 * Every pact and realm-edict sentence is in the lang file, in American English; the six realm edicts' names,
	 * descriptions and reforms are translated, and each reform quest is named as the spec names it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPactAndRealmEdictSentenceIsTranslatedInAmericanEnglish(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = QaPactsRealmEdictsGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "no en_us.json on the classpath");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		List<String> british = new ArrayList<>();
		int checked = 0;
		for (String key : lang.keySet()) {
			if (key.contains(".pact.") || key.contains("realm_edict") || key.matches("edict\\.aliveworkplace\\.(royal_highway|common_market|levy_of_arms|realm_festival|crowns_peace|royal_charter).*")) {
				checked++;
				Matcher m = BRITISH.matcher(lang.get(key).getAsString());
				if (m.find()) {
					british.add(key + ": " + m.group());
				}
			}
		}
		helper.assertTrue(checked > 80, "too few pact and realm edict sentences found: " + checked);
		helper.assertTrue(british.isEmpty(), "British spellings: " + british);
		Language language = Language.getInstance();
		Map<String, String> reforms = Map.of("royal_highway", "Pave the highway", "common_market", "Weights and measures", "levy_of_arms", "Arm the levy",
			"realm_festival", "Lanterns for the square", "crowns_peace", "Clear the roads", "royal_charter", "Draft the charter");
		for (Edicts.Edict e : Edicts.realm()) {
			String name = e.name().getString();
			String desc = e.description().getString();
			helper.assertTrue(!name.contains("aliveworkplace") && !desc.contains("aliveworkplace") && !desc.isBlank(), "untranslated: " + name + " / " + desc);
			helper.assertTrue(e.reform().isPresent(), name + " has no reform");
			String reform = e.reform().get().name().getString();
			helper.assertTrue(reform.equals(reforms.get(e.id().getPath())), name + "'s reform is named " + reform);
		}
		helper.assertTrue(language.has("message.aliveworkplace.edict.too_soon") && language.has("chronicle.aliveworkplace.realm_edict.why_slots"), "the server's language lacks the mod's lines");
		helper.succeed();
	}
}
