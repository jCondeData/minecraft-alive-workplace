package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.CapitalVotes;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmInvites;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * QA, adversarial tests written from the spec of ROADMAP 33.11 (founding a realm), 33.12 (other players' villages,
 * choosing the capital) and 33.13 (the realm treasury), aimed at what {@link RealmGameTests}, {@link RealmInviteGameTests}
 * and {@link RealmTreasuryGameTests} leave out: a friend founding, the 2,048-block reach and one dimension from both
 * sides, the 12-village cap against invitations, the rank thresholds from both sides and the rank-up line reaching
 * another owner, the crown passing with the treasury (and a capital giving it up), two realms inviting one village, the
 * time left read in the singular, a candidate's hall broken mid-vote, the 5 and 20% levies, a friend taking and a
 * member's owner only giving, the cap moving with the rank, and the switch off on the treasury. Each test turns realms on
 * alone in its batch ({@link RealmGameTests#land}).
 */
public class QaRealmFoundingGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static ServerPlayer neighbour(GameTestHelper helper, Land land, List<Component> heard, String name, int... halls) {
		ServerPlayer them = RealmGameTests.listener(helper, heard, name);
		for (int i : halls) {
			land.entity(i).setOwner(them.getUUID(), name);
			Realms.round(land.level(), land.hall(i), land.entity(i), 5 + i);
		}
		return them;
	}

	private static boolean heard(List<Component> heard, String line) {
		return heard.stream().anyMatch(c -> c.getString().equals(line));
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** A village of {@code owner}'s that no chunk shows, {@code dx, dz} from hall 0, as its last round saw it; forgotten after. */
	private static GlobalPos known(GameTestHelper helper, Land land, int dx, int dz, String name, UUID owner) {
		GlobalPos at = GlobalPos.of(land.level().dimension(), land.hall(0).offset(dx, 0, dz));
		land.data().putKnown(new RealmData.Known(at, Component.literal(name), Optional.of(owner), "Owner", "hamlet", 4, 50, 0));
		Leftovers.after(helper, () -> land.data().forgetKnown(at));
		return at;
	}

	private static RealmData.Realm realmOf(Land land, int hall) {
		return land.data().realmOf(land.at(hall));
	}

	/** Hall {@code hall}'s next day's takings from ten workers, well kept: 3 emeralds. */
	private static void aDay(Land land, int hall) {
		land.entity(hall).setNeeds(new VillageNeeds.Needs(10, 10, 10, 10, 10, 10, 10, 1f));
		land.entity(hall).setLastTaxDay(Chronicle.day(land.level()) - 1);
		Treasury.round(land.level(), land.hall(hall), land.entity(hall), 10);
	}

	/** 33.11: "the hall's owner (or a friend)" founds the realm from the tab and adds the owner's other village; once no longer a friend, they can't. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFFriend"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFFriend")
	public void aFriendFoundsTheRealmFromTheTab(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer friend = RealmGameTests.listener(helper, theirs, "friendly");
		Friends friends = Friends.get(land.level().getServer());
		friends.add(land.owner().getUUID(), friend.getUUID(), "friendly");
		Leftovers.after(helper, () -> friends.remove(land.owner().getUUID(), friend.getUUID()));
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, friend);
		menu.press(Realms.FOUND, friend);
		helper.assertTrue(realmOf(land, 0) != null, "a friend couldn't found a realm: " + theirs);
		String title = "the Barony of " + land.name(0);
		helper.assertTrue(RealmGameTests.chronicled(land, 0, "friendly founded " + title + ", with this village as its capital"), "the chronicle: " + land.entity(0).chronicle());
		helper.assertTrue(menu.icon(Realms.MEMBERS + 1).getHoverName().getString().equals("Add " + land.name(1)), "the owner's village not offered to the friend: "
			+ menu.icon(Realms.MEMBERS + 1).getHoverName().getString());
		menu.press(Realms.MEMBERS + 1, friend);
		helper.assertTrue(realmOf(land, 1) != null, "the friend couldn't add the owner's village: " + theirs);
		friends.remove(land.owner().getUUID(), friend.getUUID());
		Realms.Result result = Realms.add(land.level(), land.hall(0), land.at(2), friend);
		helper.assertTrue(result.refusal() == Realms.Refusal.NOT_YOURS && result.message().getString().equals("Only Owner and their friends can rule the County of " + land.name(0) + ".")
			|| result.refusal() == Realms.Refusal.NOT_YOURS && result.message().getString().equals("Only Owner and their friends can rule " + title + "."),
			"a former friend added a village: " + result.refusal() + " " + result.message().getString());
		helper.assertTrue(realmOf(land, 2) == null, "the third village joined");
		helper.succeed();
	}

	/** 33.11: "any village within 2,048 blocks ... all in one dimension": exactly 2,048 joins, 2,049 doesn't, on a straight line and a diagonal; another dimension never. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFRange")
	public void theReachIs2048BlocksInOneDimension(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN);
		RealmGameTests.found(helper, land);
		UUID me = land.owner().getUUID();
		GlobalPos edge = known(helper, land, -2048, 0, "Edge", me);
		GlobalPos past = known(helper, land, -2049, 0, "Past", me);
		GlobalPos diagonalIn = known(helper, land, -1448, -1448, "Corner", me); // 2,047.8 blocks
		GlobalPos diagonalOut = known(helper, land, -1449, -1449, "Beyond", me); // 2,049.2 blocks
		Realms.Result result = Realms.add(land.level(), land.hall(0), past, land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.TOO_FAR && result.message().getString().equals("Past is more than 2048 blocks from the capital."), "2,049 blocks: " + result.message().getString());
		helper.assertTrue(Realms.add(land.level(), land.hall(0), diagonalOut, land.owner()).refusal() == Realms.Refusal.TOO_FAR, "2,049 blocks on a diagonal");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), edge, land.owner()).ok(), "exactly 2,048 blocks refused");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), diagonalIn, land.owner()).ok(), "2,047.8 blocks on a diagonal refused");
		helper.assertTrue(realmOf(land, 0).members().size() == 3 && realmOf(land, 0).has(edge) && realmOf(land, 0).has(diagonalIn), "the members: " + realmOf(land, 0).members());
		// the Nether, right under the capital: another dimension, never
		GlobalPos nether = GlobalPos.of(Level.NETHER, land.hall(0));
		land.data().putKnown(new RealmData.Known(nether, Component.literal("Netherhold"), Optional.of(me), "Owner", "town", 9, 50, 0));
		Leftovers.after(helper, () -> land.data().forgetKnown(nether));
		result = Realms.add(land.level(), land.hall(0), nether, land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.OTHER_WORLD
			&& result.message().getString().equals("Netherhold is in another dimension: a realm's villages are all in one."), "the Nether: " + result.message().getString());
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), nether, land.owner()).refusal() == Realms.Refusal.OTHER_WORLD, "invited from the Nether");
		helper.assertTrue(land.data().realmOf(nether) == null, "the Nether village joined");
		helper.succeed();
	}

	/** 33.11 "at most 12 villages": the 12th joins and the 13th doesn't; an invitation sent with room can't be accepted once full, nor a new one sent. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFCap")
	public void twelveVillagesAtMostEvenByInvitation(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, "neighbour", 1);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "inviting with room");
		for (int i = 0; i < 11; i++) {
			GlobalPos at = known(helper, land, -1400, -1400 + 16 * i, "Hamlet " + i, land.owner().getUUID());
			Realms.Result added = Realms.add(land.level(), land.hall(0), at, land.owner());
			helper.assertTrue(added.ok(), "village " + (i + 2) + " of 12 refused: " + added.message().getString());
		}
		helper.assertTrue(land.data().realm(id).members().size() == 12, "not 12: " + land.data().realm(id).members().size());
		GlobalPos thirteenth = known(helper, land, -1300, -1400, "Thirteenth", land.owner().getUUID());
		Realms.Result result = Realms.add(land.level(), land.hall(0), thirteenth, land.owner());
		String title = "The County of " + land.name(0);
		helper.assertTrue(result.refusal() == Realms.Refusal.FULL && result.message().getString().equals(title + " already holds 12 villages, as many as a realm may."),
			"the 13th: " + result.refusal() + " " + result.message().getString());
		// the neighbour answers the invitation sent while there was room
		Realms.Result late = RealmInvites.accept(land.level(), land.hall(1), id, them);
		helper.assertTrue(late.refusal() == Realms.Refusal.FULL && land.data().realmOf(land.at(1)) == null, "a 13th joined by invitation: " + late.message().getString());
		helper.assertTrue(land.data().realm(id).members().size() == 12, "past 12: " + land.data().realm(id).members().size());
		helper.succeed();
	}

	/** 33.11's ranks from both sides of each threshold, and a rank up told to every member's owner, not only the capital's. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFRanks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFRanks")
	public void ranksFromBothSidesAndTheRankUpReachesEveryOwner(GameTestHelper helper) {
		VillageRanks.Rank v = VillageRanks.Rank.VILLAGE;
		VillageRanks.Rank t = VillageRanks.Rank.TOWN;
		VillageRanks.Rank c = VillageRanks.Rank.CITY;
		helper.assertTrue(Realms.count(2, t, 0) == Realms.Rank.BARONY && Realms.count(3, t, 0) == Realms.Rank.COUNTY, "County at 3 with a Town");
		helper.assertTrue(Realms.count(4, v, 3) == Realms.Rank.BARONY, "a Village capital with three Cities made a County");
		helper.assertTrue(Realms.count(4, c, 1) == Realms.Rank.COUNTY && Realms.count(5, c, 1) == Realms.Rank.DUCHY, "Duchy at 5 with a City");
		helper.assertTrue(Realms.count(5, t, 4) == Realms.Rank.COUNTY && Realms.count(12, t, 11) == Realms.Rank.COUNTY, "a Town capital with City members rose past a County");
		helper.assertTrue(Realms.count(7, c, 7) == Realms.Rank.DUCHY && Realms.count(8, c, 2) == Realms.Rank.KINGDOM, "Kingdom at 8, not 7");
		helper.assertTrue(Realms.count(8, c, 1) == Realms.Rank.DUCHY, "a Kingdom with only the capital a City");
		Land land = RealmGameTests.land(helper, t, v, VillageRanks.Rank.HAMLET);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, "neighbour", 1);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok() && RealmInvites.accept(land.level(), land.hall(1), id, them).ok(), "joining");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "the third");
		String name = land.name(0);
		helper.assertTrue("county".equals(land.data().realm(id).rank()), "three with a Town: " + land.data().realm(id).rank());
		helper.assertTrue(heard(theirs, "Rejoice! the Barony of " + name + " has risen to the County of " + name + "."), "the neighbour (a member's owner) wasn't told of the rank up: " + theirs);
		helper.assertTrue(RealmGameTests.chronicled(land, 1, "The realm rose to the County of " + name), "no rank-up chronicle in the neighbour's village: " + land.entity(1).chronicle());
		helper.succeed();
	}

	/** 33.11 + 33.13: a broken capital passes the crown and the treasury, levy and movements go with it; the levy keeps coming in; it all survives reload. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFCrownTreasury"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFCrownTreasury")
	public void theCrownPassesWithTheTreasury(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok() && Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "adding");
		helper.assertTrue(RealmTreasury.setLevy(land.level(), land.hall(0), land.owner(), 20).ok(), "levy 20");
		helper.assertTrue(RealmTreasury.give(land.level(), land.hall(0), land.owner(), new ItemStack(Items.EMERALD, 10), true).ok(), "giving 10");
		land.level().destroyBlock(land.hall(0), false);
		RealmData.Realm realm = land.data().realm(id);
		helper.assertTrue(realm != null && realm.capital().equals(land.at(1)), "the crown didn't pass: " + realm);
		helper.assertTrue(realm.treasury() == 1000 && realm.levy() == 20 && realm.ledger().size() == 1, "the treasury didn't go with the crown: " + realm.treasury() + " " + realm.levy() + " " + realm.ledger());
		// the hamlet's next day pays 20% into the same treasury
		aDay(land, 2);
		helper.assertTrue(land.data().realm(id).treasury() == 1060 && land.entity(2).treasury() == 240, "the levy after the crown passed: " + land.data().realm(id).treasury() + " / " + land.entity(2).treasury());
		// decisions now with the new capital: its tab sets the levy
		helper.assertTrue(RealmTreasury.setLevy(land.level(), land.hall(1), land.owner(), 5).ok(), "the new capital can't set the levy");
		RealmData back = RealmData.load(land.data().save(new CompoundTag(), land.level().registryAccess()), land.level().registryAccess());
		RealmData.Realm reloaded = back.realm(id);
		helper.assertTrue(reloaded != null && reloaded.capital().equals(land.at(1)) && reloaded.treasury() == 1060 && reloaded.levy() == 5 && reloaded.ledger().size() == 2
			&& reloaded.members().size() == 2, "after reload: " + reloaded);
		helper.succeed();
	}

	/** 33.11 "a member's owner can leave at any time": the capital's owner leaving gives up the crown; it passes to the Town, the treasury with it. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFAbdicate"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFAbdicate")
	public void theCapitalLeavesAndGivesUpTheCrown(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.TOWN);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok() && Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "adding");
		helper.assertTrue(RealmTreasury.give(land.level(), land.hall(0), land.owner(), new ItemStack(Items.EMERALD, 4), true).ok(), "giving 4");
		String county = "the County of " + land.name(0);
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, land.owner());
		menu.press(Realms.LEAVE, land.owner());
		RealmData.Realm realm = land.data().realm(id);
		helper.assertTrue(land.data().realmOf(land.at(0)) == null, "the old capital is still in the realm");
		helper.assertTrue(realm != null && realm.capital().equals(land.at(2)) && realm.members().size() == 2 && "barony".equals(realm.rank()), "the crown didn't pass to the Town: " + realm);
		helper.assertTrue(realm.treasury() == 400, "the treasury stayed behind: " + realm.treasury());
		helper.assertTrue(RealmGameTests.heard(land, land.name(0) + " has given up the crown of " + county + "."), "no abdicated line: " + land.heard());
		helper.assertTrue(RealmGameTests.heard(land, "The crown of " + county + " passes to " + land.name(2) + ": it is now the Barony of " + land.name(2) + "."), "no crown line: " + land.heard());
		helper.succeed();
	}

	/** 33.12: two realms invite one village; accepting one takes the other's invitation away; a realm that ends and a hall that's broken take theirs too. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFTwoInvites"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFTwoInvites")
	public void twoRealmsInviteOneVillage(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		List<Component> rivals = new ArrayList<>();
		ServerPlayer rival = neighbour(helper, land, rivals, "rival", 1);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, "villager", 2, 3, 4);
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.found(land.level(), land.hall(1), rival).ok(), "the rival's realm");
		UUID a = realmOf(land, 0).id();
		UUID b = realmOf(land, 1).id();
		// the rival can't invite into our realm
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(2), rival).refusal() == Realms.Refusal.NOT_YOURS, "the rival invited into our realm");
		for (UUID realm : List.of(a, b)) {
			BlockPos capital = realm.equals(a) ? land.hall(0) : land.hall(1);
			ServerPlayer ruler = realm.equals(a) ? land.owner() : rival;
			helper.assertTrue(RealmInvites.invite(land.level(), capital, land.at(2), ruler).ok() && RealmInvites.invite(land.level(), capital, land.at(3), ruler).ok(), "inviting");
		}
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(4), land.owner()).ok(), "inviting the fifth");
		ChoiceMenu tab = RealmGameTests.realmTab(helper, land, 2, them);
		helper.assertTrue(tab.icon(Realms.INVITES).getHoverName().getString().startsWith("Join ") && tab.icon(Realms.INVITES + 2).getHoverName().getString().startsWith("Join "),
			"both invitations aren't on the tab: " + tab.icon(Realms.INVITES).getHoverName().getString() + " / " + tab.icon(Realms.INVITES + 2).getHoverName().getString());
		helper.assertTrue(RealmInvites.accept(land.level(), land.hall(2), a, them).ok(), "accepting ours");
		helper.assertTrue(land.data().invite(b, land.at(2)) == null, "the rival's invitation is still open");
		Realms.Result second = RealmInvites.accept(land.level(), land.hall(2), b, them);
		helper.assertTrue(!second.ok() && realmOf(land, 2).id().equals(a), "joined two realms: " + second.message().getString());
		Realms.Result again = RealmInvites.invite(land.level(), land.hall(1), land.at(2), rival);
		helper.assertTrue(again.refusal() == Realms.Refusal.TAKEN && again.message().getString().equals(land.name(2) + " is already in the County of " + land.name(0) + ".")
			|| again.refusal() == Realms.Refusal.TAKEN && again.message().getString().equals(land.name(2) + " is already in the Barony of " + land.name(0) + "."),
			"inviting a member of another realm: " + again.refusal() + " " + again.message().getString());
		// the rival's realm ends (its only hall broken): its invitation to village 3 goes with it
		land.level().destroyBlock(land.hall(1), false);
		helper.assertTrue(land.data().realm(b) == null, "the rival's realm didn't end");
		helper.assertTrue(land.data().invite(b, land.at(3)) == null, "an ended realm's invitation stayed");
		ChoiceMenu third = RealmGameTests.realmTab(helper, land, 3, them);
		helper.assertTrue(third.icon(Realms.INVITES).getHoverName().getString().equals("Join the Barony of " + land.name(0))
			&& !third.icon(Realms.INVITES + 2).getHoverName().getString().startsWith("Join "), "the tab after the rival's realm ended: " + third.icon(Realms.INVITES + 2));
		// the fifth's hall is broken: our invitation to it goes
		land.level().destroyBlock(land.hall(4), false);
		helper.assertTrue(land.data().invite(a, land.at(4)) == null, "an invitation to a broken hall stayed");
		helper.succeed();
	}

	/** 33.12 "within 3 days": the time left on the tabs, read with one day and one hour (singular) and with several. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFTimeLeft"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFTimeLeft")
	public void theTimeLeftReadsRight(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, "neighbour", 1);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "inviting");
		long[][] cases = {{24000 + 1000, 1, 1}, {2 * 24000 + 1000, 2, 1}, {24000 + 5000, 1, 5}, {23000, 0, 23}};
		String[] expected = {"Waiting for an answer: 1 day and 1 hour left", "Waiting for an answer: 2 days and 1 hour left",
			"Waiting for an answer: 1 day and 5 hours left", "Waiting for an answer: 0 days and 23 hours left"};
		for (int i = 0; i < cases.length; i++) {
			land.data().putInvite(new RealmData.Invite(id, land.at(1), them.getUUID(), now(server) - (RealmInvites.LAPSE - cases[i][0])));
			List<String> join = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 1, them).icon(Realms.INVITES));
			helper.assertTrue(join.contains(expected[i]), "the Join button with " + cases[i][1] + " d " + cases[i][2] + " h left: " + join);
			List<String> sent = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(Realms.MEMBERS + 1));
			helper.assertTrue(sent.contains(expected[i]), "the capital's invited line with " + cases[i][1] + " d " + cases[i][2] + " h left: " + sent);
		}
		helper.succeed();
	}

	/** 33.12: the candidate's hall broken mid-vote: it leaves, and when the day is up the capital stays and the vote is void. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFVoteBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFVoteBroken")
	public void theCandidatesHallBrokenMidVote(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, "neighbour", 1, 2);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		for (int i = 1; i <= 2; i++) {
			helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(i), land.owner()).ok() && RealmInvites.accept(land.level(), land.hall(i), id, them).ok(), "joining " + i);
		}
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), them).ok(), "putting forward");
		helper.assertTrue(CapitalVotes.vote(land.level(), land.hall(2), them, true).ok(), "voting for");
		// the capital itself can't be put forward
		helper.assertTrue(!CapitalVotes.putForward(land.level(), land.hall(0), land.owner()).ok(), "the capital put forward as capital");
		long started = land.data().vote(id).started();
		land.level().destroyBlock(land.hall(1), false);
		helper.assertTrue(!land.data().realm(id).has(land.at(1)), "the broken candidate is still a member");
		Realms.settle(server, started + CapitalVotes.DAY);
		RealmData.Realm realm = land.data().realm(id);
		helper.assertTrue(realm != null && realm.capital().equals(land.at(0)) && land.data().vote(id) == null, "the capital moved to a broken hall, or the vote is still open: " + realm);
		helper.assertTrue(realm.members().size() == 2, "the members: " + realm.members());
		helper.assertTrue(land.heard().stream().anyMatch(c -> c.getString().startsWith("The vote on ") && c.getString().endsWith(" is void: it can't be the capital any more.")),
			"no void line: " + land.heard());
		helper.succeed();
	}

	/** 33.13 "0, 5, 10 or 20%": stepped on the capital's tab, 20% takes 0.6 of 3 emeralds and 5% takes 0.15; the icon says so. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFLevies"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFLevies")
	public void theFiveAndTwentyPercentLevies(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "adding");
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, land.owner());
		menu.press(RealmTreasury.LEVY, land.owner()); // 10 -> 20
		aDay(land, 1);
		helper.assertTrue(realmOf(land, 0).treasury() == 60 && land.entity(1).treasury() == 240, "20%: " + realmOf(land, 0).treasury() + " / " + land.entity(1).treasury());
		menu.press(RealmTreasury.LEVY, land.owner()); // 20 -> 0
		menu.press(RealmTreasury.LEVY, land.owner()); // 0 -> 5
		helper.assertTrue(realmOf(land, 0).levy() == 5, "the step after 0: " + realmOf(land, 0).levy());
		helper.assertTrue(RealmGameTests.heard(land, "The levy of the Barony of " + land.name(0) + " is now 5%."), "no 5% line: " + land.heard());
		helper.assertTrue(RealmGameTests.lore(menu.icon(RealmTreasury.LEVY)).contains("Each member pays 5% of its daily takings into the realm treasury"),
			"the levy icon: " + RealmGameTests.lore(menu.icon(RealmTreasury.LEVY)));
		aDay(land, 1);
		helper.assertTrue(realmOf(land, 0).treasury() == 75 && land.entity(1).treasury() == 240 + 285, "5%: " + realmOf(land, 0).treasury() + " / " + land.entity(1).treasury());
		helper.assertTrue(RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RealmTreasury.TREASURY)).contains("Holds 0.75 of 256 emeralds"), "the treasury icon, opened again");
		// a capital that has takings of its own pays into the realm too
		long before = land.entity(0).treasury();
		aDay(land, 0);
		long kept = land.entity(0).treasury() - before;
		long paid = realmOf(land, 0).treasury() - 75;
		helper.assertTrue(paid > 0 && paid == (kept + paid) * 5 / 100, "the capital's own takings: kept " + kept + ", paid " + paid);
		helper.succeed();
	}

	/** 33.13 "only the capital's owner, their friends and operators may take": a friend takes; another owner's member gives the stack but can't take; the lines read in the singular and plural. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFTakers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFTakers")
	public void aFriendTakesAndAMembersOwnerOnlyGives(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, "neighbour", 1);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok() && RealmInvites.accept(land.level(), land.hall(1), id, them).ok(), "joining");
		String title = "the Barony of " + land.name(0);
		ChoiceMenu members = RealmGameTests.realmTab(helper, land, 1, them);
		helper.assertTrue(RealmGameTests.lore(members.icon(RealmTreasury.TREASURY)).contains("Only the capital's owner and their friends take from it"), "the member's owner's tooltip");
		members.setCarried(new ItemStack(Items.EMERALD, 5));
		members.clicked(RealmTreasury.TREASURY, 0, ClickType.QUICK_MOVE, them);
		helper.assertTrue(members.getCarried().isEmpty() && land.data().realm(id).treasury() == 500, "the member's owner's stack: " + land.data().realm(id).treasury());
		helper.assertTrue(heard(theirs, "You gave 5 emeralds to the treasury of " + title + "; it holds 5.00 emeralds."), "the plural given line: " + theirs);
		members.clicked(RealmTreasury.TREASURY, 1, ClickType.PICKUP, them);
		helper.assertTrue(land.data().realm(id).treasury() == 500 && them.getInventory().countItem(Items.EMERALD) == 0, "the member's owner took from the treasury");
		helper.assertTrue(heard(theirs, "Only Owner and their friends can take from the treasury of " + title + "."), "no refusal: " + theirs);
		// a friend of the capital's owner takes
		List<Component> friends = new ArrayList<>();
		ServerPlayer friend = RealmGameTests.listener(helper, friends, "friendly");
		Friends.get(land.level().getServer()).add(land.owner().getUUID(), friend.getUUID(), "friendly");
		Leftovers.after(helper, () -> Friends.get(land.level().getServer()).remove(land.owner().getUUID(), friend.getUUID()));
		ChoiceMenu friendTab = RealmGameTests.realmTab(helper, land, 0, friend);
		helper.assertTrue(RealmGameTests.lore(friendTab.icon(RealmTreasury.TREASURY)).contains("Right-click to take an emerald; shift-right-click to take a stack"), "the friend's tooltip");
		friendTab.clicked(RealmTreasury.TREASURY, 1, ClickType.PICKUP, friend);
		helper.assertTrue(land.data().realm(id).treasury() == 400 && friend.getInventory().countItem(Items.EMERALD) == 1, "a friend couldn't take: " + friends);
		helper.assertTrue(heard(friends, "You took 1 emerald from the treasury of " + title + "; it holds 4.00 emeralds."), "the singular taken line: " + friends);
		RealmData.Movement last = land.data().realm(id).ledger().get(land.data().realm(id).ledger().size() - 1);
		helper.assertTrue(last.cents() == -100 && last.reason().getString().equals("Taken by friendly"), "the movement: " + last);
		helper.succeed();
	}

	/** 33.13's cap follows the rank: a full Barony takes nothing; risen to a County it takes up to 512; let down again, it takes no levy above the cap. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFCapRank"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFCapRank")
	public void theCapFollowsTheRank(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "adding");
		RealmData.Realm realm = land.data().realm(id);
		land.data().putRealm(realm.withTreasury(25600, realm.ledger()));
		helper.assertTrue(!RealmTreasury.give(land.level(), land.hall(0), land.owner(), new ItemStack(Items.EMERALD, 1), false).ok(), "a full Barony took a gift");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok() && "county".equals(land.data().realm(id).rank()), "a County");
		ItemStack five = new ItemStack(Items.EMERALD, 5);
		helper.assertTrue(RealmTreasury.give(land.level(), land.hall(0), land.owner(), five, true).ok() && land.data().realm(id).treasury() == 26100, "a County's room: " + land.data().realm(id).treasury());
		helper.assertTrue(RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RealmTreasury.TREASURY)).contains("Holds 261.00 of 512 emeralds"), "the County's icon");
		helper.assertTrue(Realms.letGo(land.level(), land.hall(0), land.at(2), land.owner()).ok() && "barony".equals(land.data().realm(id).rank()), "back to a Barony");
		aDay(land, 1);
		helper.assertTrue(land.data().realm(id).treasury() == 26100 && land.entity(1).treasury() == 300, "a levy went into a treasury above its cap: "
			+ land.data().realm(id).treasury() + " / " + land.entity(1).treasury());
		helper.assertTrue(RealmTreasury.take(land.level(), land.hall(0), land.owner(), false).ok() && land.data().realm(id).treasury() == 26000, "taking from above the cap");
		helper.succeed();
	}

	/** 33.13 with {@code realms} off: nothing given or taken, the treasury kept; back on, it's all there. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmFTreasurySwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmFTreasurySwitch")
	public void theTreasuryWithTheSwitchOff(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		RealmGameTests.found(helper, land);
		UUID id = realmOf(land, 0).id();
		helper.assertTrue(RealmTreasury.give(land.level(), land.hall(0), land.owner(), new ItemStack(Items.EMERALD, 3), true).ok(), "giving");
		Realms.ENABLED = false;
		ItemStack two = new ItemStack(Items.EMERALD, 2);
		Realms.Result give = RealmTreasury.give(land.level(), land.hall(0), land.owner(), two, true);
		helper.assertTrue(give.refusal() == Realms.Refusal.OFF && give.message().getString().equals("Realms are turned off on this server.") && two.getCount() == 2, "given while off: " + give.message().getString());
		Realms.Result take = RealmTreasury.take(land.level(), land.hall(0), land.owner(), true);
		helper.assertTrue(take.refusal() == Realms.Refusal.OFF && land.owner().getInventory().countItem(Items.EMERALD) == 0, "taken while off");
		helper.assertTrue(RealmTreasury.setLevy(land.level(), land.hall(0), land.owner(), 20).refusal() == Realms.Refusal.OFF || land.data().realm(id).levy() == 10, "the levy changed while off");
		helper.assertTrue(land.data().realm(id).treasury() == 300, "the treasury wasn't kept: " + land.data().realm(id).treasury());
		Realms.ENABLED = true;
		helper.assertTrue(RealmTreasury.take(land.level(), land.hall(0), land.owner(), true).ok() && land.owner().getInventory().countItem(Items.EMERALD) == 3, "the treasury didn't come back");
		helper.succeed();
	}
}
