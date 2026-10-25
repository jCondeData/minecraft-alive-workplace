package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.CapitalVotes;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmInvites;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-d-1025-2208), written from the spec of ROADMAP 33.20 (feuds, and war where PvP is on), not from the code:
 * the hours left on the war button and the war's refusal at every hour boundary (1, 999, 1000, 1001 ticks; the nightly
 * mutation run left {@code (left - 999) / 1000} alive at Feuds.java:604), the capital vote's "About N hours left" at the
 * same boundaries (RealmTab.java:225); who may declare on whom (one's own village, a friend's, an ally, a realm's
 * member); war refused one tick too soon and allowed at exactly a day, with the switch off; peace offered once and
 * accepted, ending a war with it; the fade at exactly 7 days, also after a war; each chronicle line written once; save
 * and reload; and every sentence translated.
 */
public class QaFeudGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Village 0 the realm-owner's; village 1 the neighbour's; any further village the realm-owner's too. Pacts and feuds on and empty, PvP on. */
	private record Two(Land land, ServerPlayer them, List<Component> theirs) {
	}

	private static Two two(GameTestHelper helper, VillageRanks.Rank... ranks) {
		Land land = RealmGameTests.land(helper, ranks);
		MinecraftServer server = land.level().getServer();
		boolean pacts = Pacts.ENABLED;
		boolean alliances = Pacts.ALLIANCES;
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Pacts.ALLIANCES = alliances;
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			Caravans.Data data = Caravans.Data.get(land.level());
			for (int i = 0; i < ranks.length; i++) {
				data.remove(land.hall(i));
			}
		});
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = RealmGameTests.listener(helper, theirs, "neighbour");
		land.entity(1).setOwner(them.getUUID(), "neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		for (int i = 0; i < ranks.length; i++) {
			data.setWants(land.hall(i), Component.literal(land.name(i)), List.of());
		}
		return new Two(land, them, theirs);
	}

	private static ChoiceMenu pactsTab(Land land, int hall, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, land.hall(hall));
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.PACTS.slot(), player);
		return menu;
	}

	private static int find(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().equals(name)) {
				return slot;
			}
		}
		return -1;
	}

	private static String lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? "" : String.join(" | ", lore.lines().stream().map(Component::getString).toList());
	}

	private static long count(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.FEUD && e.text().getString().equals(line)).count();
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static String last(List<Component> heard) {
		return heard.isEmpty() ? "(nothing)" : heard.get(heard.size() - 1).getString();
	}

	/**
	 * The war button on the declarer's Pacts tab, and its refusal when pressed, with 1, 999, 1000 and 1001 ticks to go
	 * before the feud has stood a day: "(1 hour left)" three times, then "(2 hours left)"; one tick before the day it is
	 * still refused; at exactly a day the button says what it does and pressing it raises the war from the next dawn.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFeudWarHours"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudWarHours")
	public void theWarButtonCountsHoursLeftAtEachBoundary(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "setup: couldn't declare");
		String name = "Raise the feud with " + land.name(1) + " to war";
		long[] lefts = {1, 999, 1000, 1001};
		String[] hours = {"(1 hour left)", "(1 hour left)", "(1 hour left)", "(2 hours left)"};
		for (int i = 0; i < lefts.length; i++) {
			long now = now(server);
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.WAR_AFTER - lefts[i])));
			ChoiceMenu menu = pactsTab(land, 0, land.owner());
			int war = find(menu, name);
			String button = "Can't be pressed yet: the feud must stand a day " + hours[i] + ".";
			helper.assertTrue(war >= 0 && lore(menu.icon(war)).contains(button), lefts[i] + " ticks left: expected \"" + button + "\", got "
				+ (war < 0 ? "no button" : lore(menu.icon(war))));
			menu.press(war, land.owner());
			String refusal = "The feud with " + land.name(1) + " must stand a day before it can become a war " + hours[i] + ".";
			helper.assertTrue(last(land.heard()).equals(refusal), lefts[i] + " ticks left: pressing it said \"" + last(land.heard()) + "\", expected \"" + refusal + "\"");
			helper.assertTrue(!Feuds.feud(server, land.at(0), land.at(1)).warRaised(), lefts[i] + " ticks left: the war was raised too soon");
		}
		// exactly a day: the button says what it does, and pressing it raises the war
		long now = now(server);
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - Feuds.WAR_AFTER));
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int war = find(menu, name);
		helper.assertTrue(war >= 0 && lore(menu.icon(war)).contains("Starts at the next dawn and lasts 3 days, or until peace.")
			&& !lore(menu.icon(war)).contains("Can't be pressed"), "at a day the button reads: " + (war < 0 ? "no button" : lore(menu.icon(war))));
		// the rival's tab has no war button at all
		helper.assertTrue(find(pactsTab(land, 1, two.them()), "Raise the feud with " + land.name(0) + " to war") < 0, "the side that didn't declare has a war button");
		menu.press(war, land.owner());
		Feuds.Feud f = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(f.warRaised(), "pressing the button at exactly a day raised no war: " + last(land.heard()));
		helper.assertTrue(last(land.heard()).equals("War with " + land.name(1) + " from the next dawn, for 3 days or until peace. Your guards will fight their owners in your village."),
			"the war's line: " + last(land.heard()));
		helper.assertTrue(two.theirs().stream().anyMatch(c -> c.getString().equals(land.name(0) + " has raised the feud to war: it starts at the next dawn. A letter is on its way.")),
			"the rival wasn't told: " + two.theirs());
		ChoiceMenu after = pactsTab(land, 0, land.owner());
		helper.assertTrue(find(after, name) < 0, "a war button after the war was raised");
		int feud = find(after, "Feud with " + land.name(1));
		helper.assertTrue(feud >= 0 && (f.warStarts() == now || lore(after.icon(feud)).contains("War from the next dawn")), "the feud's line after raising: " + (feud < 0 ? "none" : lore(after.icon(feud))));
		// raising again is refused, with its reason
		Pacts.Result again = Feuds.raiseWar(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(!again.ok() && again.message().getString().equals("War with " + land.name(1) + " has already been raised: only peace is left."), "raised twice: " + again.message().getString());
		helper.succeed();
	}

	/** feudWars off: the war button says so, and pressing it refuses with the reason, even with PvP on and a day gone. */
	//$ gametest_ticks_batch AREA '100' '"qaFeudWarsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudWarsOff")
	public void withFeudWarsOffTheButtonSaysWhy(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now(server) - 2 * Feuds.WAR_AFTER));
		Feuds.WARS = false;
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int war = find(menu, "Raise the feud with " + land.name(1) + " to war");
		helper.assertTrue(war >= 0 && lore(menu.icon(war)).contains("Can't be pressed: wars are off on this server (feudWars)."), "the button: " + (war < 0 ? "none" : lore(menu.icon(war))));
		menu.press(war, land.owner());
		helper.assertTrue(last(land.heard()).equals("No war with " + land.name(1) + ": wars are off on this server (feudWars)."), "pressing it said: " + last(land.heard()));
		helper.assertTrue(!Feuds.feud(server, land.at(0), land.at(1)).warRaised(), "a war with feudWars off");
		helper.succeed();
	}

	/** The capital vote's line on the Realm tab with 1, 999, 1000 and 1001 ticks to go: "About 1 hour left" three times, then "About 2 hours left". */
	//$ gametest_ticks_batch AREA '100' '"qaFeudVoteHours"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudVoteHours")
	public void theVoteLineCountsHoursLeftAtEachBoundary(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		MinecraftServer server = land.level().getServer();
		ServerPlayer them = RealmGameTests.listener(helper, new ArrayList<>(), "neighbour");
		for (int i = 1; i <= 2; i++) {
			land.entity(i).setOwner(them.getUUID(), "neighbour");
			Realms.round(land.level(), land.hall(i), land.entity(i), 5 + i);
		}
		RealmGameTests.found(helper, land);
		UUID id = land.data().realmOf(land.at(0)).id();
		for (int i = 1; i <= 2; i++) {
			helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(i), land.owner()).ok(), "inviting " + i);
			helper.assertTrue(RealmInvites.accept(land.level(), land.hall(i), id, them).ok(), "accepting " + i);
		}
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), them).ok(), "putting forward");
		RealmData.Vote vote = land.data().vote(id);
		helper.assertTrue(vote != null, "setup: no vote");
		long[] lefts = {1, 999, 1000, 1001};
		String[] lines = {"About 1 hour left", "About 1 hour left", "About 1 hour left", "About 2 hours left"};
		for (int i = 0; i < lefts.length; i++) {
			long now = now(server);
			land.data().putVote(new RealmData.Vote(vote.realm(), vote.candidate(), now - (CapitalVotes.DAY - lefts[i]), vote.ballots()));
			for (int hall : new int[] {0, 1}) {
				ServerPlayer viewer = hall == 0 ? land.owner() : them;
				List<String> lore = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, hall, viewer).icon(Realms.VOTE));
				helper.assertTrue(lore.contains(lines[i]), lefts[i] + " ticks left on hall " + hall + ": expected \"" + lines[i] + "\" in " + lore);
			}
		}
		helper.succeed();
	}

	/**
	 * Who may declare a feud on whom: not on one's own village (or the same one), not on a friend's either way round,
	 * not twice (either side), but yes on an ally, whose alliance then ends; each refusal says why.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFeudWho"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudWho")
	public void whoMayDeclareAFeudOnWhom(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer them = two.them();
		Pacts.Result own = Feuds.declare(land.level(), land.hall(0), land.hall(2), land.owner());
		helper.assertTrue(!own.ok() && own.message().getString().equals(land.name(2) + " is yours or a friend's: there's no feuding with it."), "on one's own village: " + own.message().getString());
		helper.assertTrue(!Feuds.declare(land.level(), land.hall(0), land.hall(0), land.owner()).ok(), "a feud with itself");
		helper.assertTrue(find(pactsTab(land, 0, land.owner()), "Declare a feud on " + land.name(2)) < 0, "a Declare button for one's own village");
		// a friend's village, either way round
		Friends friends = Friends.get(server);
		friends.add(them.getUUID(), land.owner().getUUID(), "realm-owner");
		Leftovers.after(helper, () -> friends.remove(them.getUUID(), land.owner().getUUID()));
		Pacts.Result friend = Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner());
		helper.assertTrue(!friend.ok() && friend.message().getString().equals(land.name(1) + " is yours or a friend's: there's no feuding with it."), "on a friend: " + friend.message().getString());
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)), "a feud with a friend");
		friends.remove(them.getUUID(), land.owner().getUUID());
		// an ally: allowed, and the alliance ends
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), true, now(server), -1, false, false));
		helper.assertTrue(Pacts.allies(server, land.at(0)).contains(land.at(1)), "setup: no alliance");
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int declare = find(menu, "Declare a feud on " + land.name(1));
		helper.assertTrue(declare >= 0, "no Declare a feud button for an ally");
		menu.press(declare, land.owner());
		helper.assertTrue(Feuds.feuding(server, land.at(0), land.at(1)), "no feud on the ally: " + last(land.heard()));
		helper.assertTrue(last(land.heard()).equals("A letter has gone to neighbour: you are in a feud with " + land.name(1) + ". Pacts and caravans between you have ended."),
			"the declarer's line: " + last(land.heard()));
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null && !Pacts.allies(server, land.at(0)).contains(land.at(1)), "the alliance stayed in a feud");
		// not twice, from either side
		Pacts.Result again = Feuds.declare(land.level(), land.hall(1), land.hall(0), them);
		helper.assertTrue(!again.ok() && again.message().getString().equals("There is already a feud with " + land.name(0) + "."), "the rival declared back: " + again.message().getString());
		helper.assertTrue(Feuds.Data.get(server).feuds().size() == 1, "two feuds: " + Feuds.Data.get(server).feuds());
		helper.assertTrue(Feuds.feud(server, land.at(1), land.at(0)).by().equals(land.at(0)), "the declaring side changed");
		// a third village of the declarer's, not in a realm, isn't part of the feud
		helper.assertTrue(!Feuds.feuding(server, land.at(2), land.at(1)) && Feuds.boardPercent(land.level(), land.hall(2), them, true) == 100, "an outside village joined the feud");
		helper.succeed();
	}

	/** A feud on a village in a realm is a feud with the whole realm: its other member's board moves too, and the realm's title is named. */
	//$ gametest_ticks_batch AREA '100' '"qaFeudRealm"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudRealm")
	public void aFeudOnARealmsVillageIsWithTheWholeRealm(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ChoiceMenu realm = RealmGameTests.found(helper, land);
		int add = find(realm, "Add " + land.name(2));
		helper.assertTrue(add >= 0, "setup: no Add button for village 2");
		realm.press(add, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(2)) != null, "setup: village 2 didn't join");
		String title = "the Barony of " + land.name(0);
		helper.assertTrue(Feuds.declare(land.level(), land.hall(1), land.hall(2), two.them()).ok(), "couldn't declare on the realm's member");
		helper.assertTrue(Feuds.feuding(server, land.at(1), land.at(0)) && Feuds.feuding(server, land.at(1), land.at(2)), "the feud isn't with the whole realm");
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(0), two.them(), true) == 125 && Feuds.boardPercent(land.level(), land.hall(2), two.them(), false) == 75,
			"a realm member's board didn't move");
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(1), land.owner(), true) == 125, "the declarer's board didn't move for the realm's owner");
		helper.assertTrue(count(land, 1, "Declared a feud on " + title) == 1, "the declarer's chronicle: " + land.entity(1).chronicle());
		helper.succeed();
	}

	/**
	 * Peace: offered once (twice is refused, and the tab shows it waiting), accepted by the other side from its tab;
	 * each line in each chronicle once; then there's no feud to make peace in. The rival offering when peace was already
	 * offered to them accepts it. A war still on ends with the peace and is recorded once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFeudPeace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudPeace")
	public void peaceIsOfferedOnceAndAcceptedOnce(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer them = two.them();
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "couldn't declare");
		helper.assertTrue(!Feuds.acceptPeace(land.level(), land.hall(1), land.at(0), them).ok(), "accepted peace nobody offered");
		Pacts.Result offer = Feuds.offerPeace(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(offer.ok() && offer.message().getString().equals("A letter has gone offering " + land.name(1) + " peace. The feud stands until they accept."), "the offer: " + offer.message().getString());
		Pacts.Result twice = Feuds.offerPeace(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(!twice.ok() && twice.message().getString().equals("Peace has already been offered to " + land.name(1) + ": it's their turn to answer."), "offered twice: " + twice.message().getString());
		helper.assertTrue(find(pactsTab(land, 0, land.owner()), "Peace offered to " + land.name(1) + ": waiting") >= 0, "the offer isn't shown waiting");
		helper.assertTrue(count(land, 0, "Offered " + land.name(1) + " peace") == 1, "the offer's chronicle line: " + land.entity(0).chronicle());
		ChoiceMenu theirs = pactsTab(land, 1, them);
		int accept = find(theirs, "Accept peace with " + land.name(0));
		helper.assertTrue(accept >= 0, "no Accept button on the rival's tab");
		theirs.press(accept, them);
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)), "the feud stayed");
		helper.assertTrue(last(two.theirs()).equals("Peace with " + land.name(0) + ": the feud is over."), "the acceptor's line: " + last(two.theirs()));
		helper.assertTrue(count(land, 0, "Made peace with " + land.name(1)) == 1 && count(land, 1, "Made peace with " + land.name(0)) == 1, "peace not written once in each chronicle");
		Pacts.Result none = Feuds.offerPeace(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(!none.ok() && none.message().getString().equals("There is no feud with " + land.name(1) + "."), "peace without a feud: " + none.message().getString());
		// a war on: the rival offers, the declarer offering in turn accepts; the war's end is recorded once
		long now = now(server);
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, 0, now + 3 * 24000L, false, 0, 0, 0));
		helper.assertTrue(Feuds.offerPeace(land.level(), land.hall(1), land.at(0), them).ok(), "the rival couldn't offer peace in a war");
		helper.assertTrue(Feuds.offerPeace(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "offering back didn't accept");
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)), "the war's feud stayed after peace");
		String over0 = "The war with " + land.name(1) + " ended. Guards lost: ours 0, theirs 0";
		String over1 = "The war with " + land.name(0) + " ended. Guards lost: ours 0, theirs 0";
		helper.assertTrue(count(land, 0, over0) == 1 && count(land, 1, over1) == 1, "the war's end not written once: " + land.entity(0).chronicle());
		helper.assertTrue(count(land, 0, "Made peace with " + land.name(1)) == 2, "the second peace's line");
		Feuds.settle(server);
		helper.assertTrue(count(land, 0, over0) == 1, "the war's end written again after settling");
		helper.succeed();
	}

	/**
	 * Fading: a feud without a war one tick short of 7 days stays, at exactly 7 days it fades (written once); a feud at
	 * war never fades however old; a war ends one tick late, not early, written once; its feud then fades 7 days after
	 * the war's end, not before.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFeudFade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudFade")
	public void aFeudFadesAtExactlySevenDaysAndAWarEndsOnTime(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Feuds.Data data = Feuds.Data.get(server);
		long now = now(server);
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - Feuds.FADE + 1));
		Feuds.settle(server);
		helper.assertTrue(Feuds.feuding(server, land.at(0), land.at(1)), "faded a tick early");
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - Feuds.FADE));
		Feuds.settle(server);
		Feuds.settle(server);
		String faded = "The feud with " + land.name(1) + " faded";
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)), "didn't fade at 7 days");
		helper.assertTrue(count(land, 0, faded) == 1 && count(land, 1, "The feud with " + land.name(0) + " faded") == 1, "the fade not written once in each chronicle");
		helper.assertTrue(two.theirs().stream().anyMatch(c -> c.getString().equals("The feud with " + land.name(0) + " has faded: a week went by without a war.")), "the rival wasn't told: " + two.theirs());
		// at war: never fades, however old the feud
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - 10 * 24000L, 0, now + 1, false, 1, 2, 0));
		Feuds.settle(server);
		Feuds.Feud f = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(f != null && !f.warDone(), "a war ended a tick early or its feud faded: " + f);
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - 10 * 24000L, 0, now, false, 1, 2, 0));
		Feuds.settle(server);
		Feuds.settle(server);
		f = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(f != null && f.warDone(), "the war didn't end on time, or its feud ended with it: " + f);
		String over = "The war with " + land.name(1) + " ended. Guards lost: ours 1, theirs 2";
		helper.assertTrue(count(land, 0, over) == 1 && count(land, 1, "The war with " + land.name(0) + " ended. Guards lost: ours 2, theirs 1") == 1,
			"the war's end not written once each: " + land.entity(0).chronicle());
		helper.assertTrue(last(two.theirs()).equals("The war with " + land.name(0) + " is over. Guards lost: ours 2, theirs 1."), "the rival's line: " + last(two.theirs()));
		// after the war, it fades 7 days after the war's end
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - 20 * 24000L, 0, now - Feuds.FADE + 1, true, 1, 2, 0));
		Feuds.settle(server);
		helper.assertTrue(Feuds.feuding(server, land.at(0), land.at(1)), "faded before 7 days after the war");
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - 20 * 24000L, 0, now - Feuds.FADE, true, 1, 2, 0));
		Feuds.settle(server);
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)) && count(land, 0, faded) == 2 && count(land, 0, over) == 1, "didn't fade 7 days after the war, or recorded the war again");
		helper.succeed();
	}

	/** The feud's lines on the tab read right (days and hours, singular and plural), and save and reload keeps every field of two feuds. */
	//$ gametest_ticks_batch AREA '100' '"qaFeudLines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudLines")
	public void theFeudsLinesReadRightAndSurviveAReload(GameTestHelper helper) {
		Two two = two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		Feuds.Data data = Feuds.Data.get(server);
		String feud = "Feud with " + land.name(1);
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "couldn't declare");
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int slot = find(menu, feud);
		helper.assertTrue(slot >= 0 && lore(menu.icon(slot)).contains("Fades in 7 days and 0 hours without a war")
			&& lore(menu.icon(slot)).contains("Each board pays their people 25% less and charges them 25% more"), "the new feud's lines: " + (slot < 0 ? "none" : lore(menu.icon(slot))));
		long now = now(server);
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - 25000)));
		menu = pactsTab(land, 0, land.owner());
		slot = find(menu, feud);
		helper.assertTrue(slot >= 0 && lore(menu.icon(slot)).contains("Fades in 1 day and 1 hour without a war"), "a day and an hour: " + (slot < 0 ? "none" : lore(menu.icon(slot))));
		data.put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, 0, now + 25000, false, 3, 1, 0));
		menu = pactsTab(land, 0, land.owner());
		slot = find(menu, feud);
		helper.assertTrue(slot >= 0 && lore(menu.icon(slot)).contains("At war: 1 day and 1 hour left") && lore(menu.icon(slot)).contains("Guards lost: ours 3, theirs 1"),
			"the war's lines: " + (slot < 0 ? "none" : lore(menu.icon(slot))));
		ChoiceMenu theirs = pactsTab(land, 1, two.them());
		int rival = find(theirs, "Feud with " + land.name(0));
		helper.assertTrue(rival >= 0 && lore(theirs.icon(rival)).contains("Guards lost: ours 1, theirs 3"), "the rival's count: " + (rival < 0 ? "none" : lore(theirs.icon(rival))));
		// save and reload: two feuds, every field
		data.clear();
		Feuds.Feud a = new Feuds.Feud(land.at(0), land.at(1), 7, 24000, 96000, true, 4, 5, 2);
		Feuds.Feud b = new Feuds.Feud(land.at(2), land.at(1), 11);
		data.put(a);
		data.put(b);
		helper.assertTrue(data.isDirty(), "a new feud didn't mark the save dirty");
		CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
		Feuds.Data back = Feuds.Data.load(tag, server.registryAccess());
		helper.assertTrue(back.feuds().size() == 2 && back.feuds().contains(a) && back.feuds().contains(b), "save and reload: " + back.feuds());
		helper.succeed();
	}

	/** Every sentence the feuds show a player is in en_us.json, with each plural form. */
	//$ gametest_ticks_batch AREA '40' '"qaFeudLang"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaFeudLang")
	public void everyFeudSentenceIsTranslated(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		String m = "message.aliveworkplace.feud.";
		String s = "screen.aliveworkplace.feud.";
		String c = "chronicle.aliveworkplace.feud.";
		List<String> keys = new ArrayList<>(List.of(m + "letter", m + "declared", m + "declared_told", m + "peace_letter", m + "peace_offered", m + "peace_offered_told",
			m + "peace_made", m + "peace_told", m + "faded_told", m + "war_letter", m + "war_raised", m + "war_told", m + "war_over", m + "refused_too_soon.one",
			c + "declared", c + "declared_on", c + "peace_offered", c + "peace", c + "faded", c + "war_declared", c + "war_declared_on", c + "war_over",
			s + "feud", s + "board", s + "board_rival", s + "lost", s + "war_at_dawn", s + "offer_peace", s + "offer_peace_hint", s + "peace_waiting", s + "accept_peace",
			s + "accept_peace_hint", s + "raise_war", s + "war_hint", s + "war_pvp", s + "war_wars_off", s + "war_too_soon", s + "war_too_soon.one", s + "war_rules",
			s + "declare", s + "declare_hint", "screen.aliveworkplace.realm.vote_left", "screen.aliveworkplace.realm.vote_left.one",
			"chatter.aliveworkplace.feud.0", "chatter.aliveworkplace.feud.1", "chatter.aliveworkplace.feud.2"));
		for (String why : List.of("off", "not_yours", "not_stranger", "too_far", "already", "crowns_peace", "no_feud", "peace_offered", "no_peace", "not_declarer",
			"war_raised", "wars_off", "pvp", "too_soon")) {
			keys.add(m + "refused_" + why);
		}
		for (String two : List.of("at_war", "fades")) {
			for (String form : List.of("", ".one", ".rest_one", ".one.rest_one")) {
				keys.add(s + two + form);
			}
		}
		for (String key : keys) {
			if (!lang.has(key)) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "untranslated: " + missing);
		helper.assertTrue(Component.translatable(m + "refused_too_soon.one", "Elmford", 1).getString().endsWith("(1 hour left).")
			&& Component.translatable(s + "war_too_soon", 2).getString().endsWith("(2 hours left)."), "the plural forms are swapped");
		helper.succeed();
	}
}
