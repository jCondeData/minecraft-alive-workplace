package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.realm.CapitalVotes;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmInvites;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 33.12, other players' villages and choosing the capital, with two owners: the capital's owner invites a
 * village another player owns (a letter to them, or waiting at the post office), who accepts or declines on its Realm
 * tab within 3 days (it lapses after); a member that's a Town is put forward as capital: one owner of every member moves
 * it at once, otherwise the members' owners vote for a day (a majority moves it with the realm's state, a tie keeps
 * it); realm decisions stay with whoever rules the capital, and anyone can leave; invitations and votes survive save
 * and reload; and the switch. Each test turns realms on, alone in its batch ({@link RealmGameTests#land}).
 */
public class RealmInviteGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Another player, online, listening, who owns the villages {@code halls} of {@code land} (seen again by a round). */
	private static ServerPlayer neighbour(GameTestHelper helper, Land land, List<Component> heard, int... halls) {
		ServerPlayer them = RealmGameTests.listener(helper, heard, "neighbour");
		for (int i : halls) {
			land.entity(i).setOwner(them.getUUID(), "neighbour");
			Realms.round(land.level(), land.hall(i), land.entity(i), 5 + i);
		}
		return them;
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static boolean heard(List<Component> heard, String line) {
		return heard.stream().anyMatch(c -> c.getString().equals(line));
	}

	/** The text of the letter in {@code player}'s inventory, or null. */
	private static String letter(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = player.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				return book.title().raw() + "|" + book.pages().get(0).raw().getString();
			}
		}
		return null;
	}

	/** An accepted invitation joins: the capital's tab invites the neighbour's village, the letter reaches them, they accept on its tab. */
	//$ gametest_ticks_batch AREA '100' '"realmInviteAccept"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmInviteAccept")
	public void anAcceptedInvitationJoins(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, 1);
		ChoiceMenu menu = RealmGameTests.found(helper, land);
		String title = "the Barony of " + land.name(0);
		// a stranger's village can't be added, only invited
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).refusal() == Realms.Refusal.STRANGER, "a stranger's village added");
		ItemStack invite = menu.icon(Realms.MEMBERS + 1);
		helper.assertTrue(invite.is(Items.WRITABLE_BOOK) && invite.getHoverName().getString().equals("Invite " + land.name(1)), "no Invite button: " + invite);
		helper.assertTrue(RealmGameTests.lore(invite).contains("Owner: neighbour"), "the invite's lines: " + RealmGameTests.lore(invite));
		menu.press(Realms.MEMBERS + 1, land.owner());
		helper.assertTrue(RealmGameTests.heard(land, "A letter has gone to neighbour inviting " + land.name(1) + " into the realm. They have 3 days to answer."),
			"no invited line: " + land.heard());
		// the letter, in the neighbour's hands (no mailbox), and a chat line
		String letter = letter(them);
		helper.assertTrue(letter != null && letter.startsWith("Letter from realm-owner|realm-owner, who rules " + title + ", invites " + land.name(1)
			+ " to join the realm") && letter.contains("The invitation lapses after 3 days."), "the letter: " + letter);
		helper.assertTrue(heard(theirs, title + " invites " + land.name(1) + " to join it: a letter is on its way. Answer on its hall's Realm tab."), "no chat line: " + theirs);
		helper.assertTrue(land.data().invite(land.data().realmOf(land.at(0)).id(), land.at(1)) != null, "no invitation kept");
		// the capital's tab now shows it waiting, with no click
		helper.assertTrue(menu.icon(Realms.MEMBERS + 1).is(Items.PAPER) && menu.icon(Realms.MEMBERS + 1).getHoverName().getString().equals(land.name(1) + ": invited"),
			"not shown as invited: " + menu.icon(Realms.MEMBERS + 1));
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).refusal() == Realms.Refusal.INVITED, "invited twice");
		// the owner of the capital can't answer for the neighbour
		UUID id = land.data().realmOf(land.at(0)).id();
		helper.assertTrue(RealmInvites.accept(land.level(), land.hall(1), id, land.owner()).refusal() == Realms.Refusal.NOT_YOURS, "the inviter accepted for them");
		// the neighbour's tab: Join and Decline
		ChoiceMenu tab = RealmGameTests.realmTab(helper, land, 1, them);
		ItemStack join = tab.icon(Realms.INVITES);
		helper.assertTrue(join.getHoverName().getString().equals("Join " + title), "no Join button: " + join);
		helper.assertTrue(RealmGameTests.lore(join).contains("Owner invites you, from " + land.name(0) + ", the capital")
			&& RealmGameTests.lore(join).contains("Waiting for an answer: 3 days and 0 hours left"), "the Join button's lines: " + RealmGameTests.lore(join));
		helper.assertTrue(tab.icon(Realms.INVITES + 1).getHoverName().getString().equals("Decline " + title), "no Decline button");
		tab.press(Realms.INVITES, them);
		helper.assertTrue(land.data().realmOf(land.at(1)) != null && land.data().realmOf(land.at(1)).id().equals(id), "the accepted village didn't join");
		helper.assertTrue(heard(theirs, land.name(1) + " has joined " + title + "."), "no joined line: " + theirs);
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " accepted the invitation and has joined " + title + "."), "the capital wasn't told: " + land.heard());
		helper.assertTrue(land.data().invites().isEmpty(), "the invitation is still kept");
		helper.assertTrue(RealmGameTests.chronicled(land, 1, "Joined " + title), "the member's chronicle");
		helper.assertTrue(RealmGameTests.chronicled(land, 0, land.name(1) + " was invited into the realm (a letter to neighbour)"), "the capital's chronicle");
		// realm decisions stay with the capital's owner; the neighbour may leave
		helper.assertTrue(Realms.setArms(land.level(), land.hall(0), them, new ItemStack(Items.RED_BANNER)).refusal() == Realms.Refusal.NOT_YOURS, "the neighbour set the arms");
		helper.assertTrue(Realms.leave(land.level(), land.hall(1), them).ok() && land.data().realmOf(land.at(1)) == null, "the neighbour couldn't leave");
		helper.succeed();
	}

	/** A declined invitation and a lapsed one don't join; an offline owner's letter waits at the post office. */
	//$ gametest_ticks_batch AREA '100' '"realmInviteDecline"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmInviteDecline")
	public void aDeclinedOrLapsedInvitationDoesnt(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, 1);
		UUID away = UUID.randomUUID(); // an owner who isn't online
		land.entity(2).setOwner(away, "Away");
		Realms.round(land.level(), land.hall(2), land.entity(2), 7);
		RealmGameTests.found(helper, land);
		UUID id = land.data().realmOf(land.at(0)).id();
		String title = "the Barony of " + land.name(0);
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "inviting the neighbour");
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "inviting the one away");
		// the one away has no mailbox and isn't online: the letter waits at the post office
		PostOffice office = PostOffice.get(server);
		Parcel waiting = office.parcels().stream().filter(p -> p.to().equals(away)).findFirst().orElse(null);
		Leftovers.after(helper, () -> office.parcels().stream().filter(p -> p.to().equals(away)).toList().forEach(office::remove));
		helper.assertTrue(waiting != null && waiting.status() == Parcel.Status.IN_TRANSIT && waiting.items().get(0).is(Items.WRITTEN_BOOK), "no letter at the post office: " + waiting);
		// declined from the tab
		ChoiceMenu tab = RealmGameTests.realmTab(helper, land, 1, them);
		tab.press(Realms.INVITES + 1, them);
		helper.assertTrue(land.data().realmOf(land.at(1)) == null && land.data().invite(id, land.at(1)) == null, "the declined village joined, or is still invited");
		helper.assertTrue(heard(theirs, "You declined the invitation to join " + title + "."), "no declined line: " + theirs);
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " declined the invitation to join " + title + "."), "the capital wasn't told: " + land.heard());
		helper.assertTrue(RealmGameTests.chronicled(land, 0, land.name(1) + " declined to join the realm"), "the capital's chronicle");
		helper.assertTrue(tab.icon(Realms.INVITES).isEmpty(), "the tab still shows the invitation: " + tab.icon(Realms.INVITES));
		// nothing lapses before 3 days; after, it does
		long sent = land.data().invite(id, land.at(2)).sent();
		Realms.settle(server, sent + RealmInvites.LAPSE);
		helper.assertTrue(land.data().invite(id, land.at(2)) != null, "lapsed before 3 days");
		Realms.settle(server, sent + RealmInvites.LAPSE + 1);
		helper.assertTrue(land.data().invite(id, land.at(2)) == null && land.data().realmOf(land.at(2)) == null, "the invitation didn't lapse");
		helper.assertTrue(RealmGameTests.heard(land, "The invitation from " + title + " to " + land.name(2) + " has lapsed."), "the capital wasn't told it lapsed: " + land.heard());
		// an answer after 3 days is too late: a lapsed invitation can't be accepted
		land.data().putInvite(new RealmData.Invite(id, land.at(1), them.getUUID(), now(server) - RealmInvites.LAPSE - 20));
		helper.assertTrue(RealmGameTests.realmTab(helper, land, 1, them).icon(Realms.INVITES).isEmpty(), "a lapsed invitation on the tab");
		Realms.Result late = RealmInvites.accept(land.level(), land.hall(1), id, them);
		helper.assertTrue(late.refusal() == Realms.Refusal.LAPSED && land.data().realmOf(land.at(1)) == null, "a lapsed invitation joined: " + late.message().getString());
		helper.succeed();
	}

	/** Two owners: the neighbour's two Towns against the capital's one; the vote moves the capital and the realm's state with it. */
	//$ gametest_ticks_batch AREA '100' '"realmVoteMoves"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmVoteMoves")
	public void aVoteWithAMajorityMovesTheCapital(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, 1, 2);
		RealmGameTests.found(helper, land);
		RealmData.Realm realm = land.data().realmOf(land.at(0));
		UUID id = realm.id();
		for (int i = 1; i <= 2; i++) {
			helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(i), land.owner()).ok(), "inviting " + i);
			helper.assertTrue(RealmInvites.accept(land.level(), land.hall(i), id, them).ok(), "accepting " + i);
		}
		// the realm's state: its name, its arms, its rank, its treasury and levy (33.13; research and edicts will live on it too)
		land.data().putRealm(land.data().realm(id).withName(Optional.of(Component.literal("Eldmark"))).withArms(Optional.of(new VillageBanners.Colours(DyeColor.BLUE,
			net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY))).withLevy(20));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.realm.RealmTreasury.give(land.level(), land.hall(0), land.owner(), new ItemStack(Items.EMERALD, 7), true).ok(), "giving to the treasury");
		helper.assertTrue("county".equals(land.data().realm(id).rank()), "three villages with a Town capital: " + land.data().realm(id).rank());
		// the neighbour puts their village forward on its tab
		ChoiceMenu tab = RealmGameTests.realmTab(helper, land, 1, them);
		ItemStack put = tab.icon(Realms.PUT_FORWARD);
		helper.assertTrue(put.is(Items.BELL) && put.getHoverName().getString().equals("Put " + land.name(1) + " forward as capital"), "no Put forward button: " + put);
		helper.assertTrue(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(Realms.PUT_FORWARD).isEmpty(), "the capital can be put forward");
		tab.press(Realms.PUT_FORWARD, them);
		RealmData.Vote vote = land.data().vote(id);
		helper.assertTrue(vote != null && vote.candidate().equals(land.at(1)), "no vote: " + vote);
		helper.assertTrue(heard(theirs, land.name(1) + " is put forward as capital: the members' owners vote for a day."), "no put forward line: " + theirs);
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " is put forward as the capital of the County of Eldmark, in place of " + land.name(0)
			+ ". Vote on the Realm tab of any of your villages in it within a day."), "the capital's owner wasn't told: " + land.heard());
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(2), them).refusal() == Realms.Refusal.VOTING, "two votes at once");
		// the capital's owner votes against on their tab: 2 for, 1 against
		ChoiceMenu ours = RealmGameTests.realmTab(helper, land, 0, land.owner());
		helper.assertTrue(ours.icon(Realms.VOTE).getHoverName().getString().equals("Vote: move the capital to " + land.name(1) + "?"), "no vote on the tab: " + ours.icon(Realms.VOTE));
		helper.assertTrue(RealmGameTests.lore(ours.icon(Realms.VOTE)).contains("Villages for: 2, against: 1"), "the count: " + RealmGameTests.lore(ours.icon(Realms.VOTE)));
		ours.press(Realms.VOTE_AGAINST, land.owner());
		helper.assertTrue(Boolean.FALSE.equals(land.data().vote(id).ballot(land.at(0))), "the vote against wasn't counted");
		helper.assertTrue(RealmGameTests.lore(ours.icon(Realms.VOTE)).contains("You voted against the move (your villages: 1)"), "the own vote: " + RealmGameTests.lore(ours.icon(Realms.VOTE)));
		// not before the day is up
		long started = land.data().vote(id).started();
		Realms.settle(server, started + CapitalVotes.DAY - 1);
		helper.assertTrue(land.data().realm(id).capital().equals(land.at(0)), "moved before the day was up");
		Realms.settle(server, started + CapitalVotes.DAY);
		realm = land.data().realm(id);
		helper.assertTrue(realm != null && realm.capital().equals(land.at(1)) && realm.members().get(0).hall().equals(land.at(1)), "the capital didn't move: " + realm);
		helper.assertTrue(land.data().vote(id) == null, "the vote is still open");
		helper.assertTrue(realm.name().map(Component::getString).orElse("").equals("Eldmark") && realm.arms().map(VillageBanners.Colours::base).orElse(null) == DyeColor.BLUE
			&& "county".equals(realm.rank()) && realm.members().size() == 3 && realm.treasury() == 700 && realm.levy() == 20 && realm.ledger().size() == 1,
			"the realm's state didn't go with it: " + realm);
		helper.assertTrue(!land.level().getEntitiesOfClass(FireworkRocketEntity.class, new AABB(land.hall(1)).inflate(4, 8, 4)).isEmpty(), "no fireworks over the new capital");
		String moved = "The capital moved from " + land.name(0) + " to " + land.name(1) + ": the realm is now the County of Eldmark";
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(RealmGameTests.chronicled(land, i, moved), "no chronicle line in member " + i + ": " + land.entity(i).chronicle());
		}
		helper.assertTrue(RealmGameTests.heard(land, "The capital of the County of Eldmark moves to " + land.name(1) + ": it is now the County of Eldmark."), "no moved line: " + land.heard());
		// realm decisions now rest with the new capital's owner; the old capital's owner may still leave
		helper.assertTrue(Realms.setArms(land.level(), land.hall(1), them, new ItemStack(Items.RED_BANNER)).ok(), "the new capital's owner can't rule");
		helper.assertTrue(Realms.setArms(land.level(), land.hall(1), land.owner(), new ItemStack(Items.RED_BANNER)).refusal() == Realms.Refusal.NOT_YOURS, "the old capital's owner still rules");
		helper.assertTrue(Realms.leave(land.level(), land.hall(0), land.owner()).ok() && land.data().realmOf(land.at(0)) == null, "the old capital couldn't leave");
		helper.succeed();
	}

	/** A tie (and villages that don't vote, counted for the current capital) keeps the capital. */
	//$ gametest_ticks_batch AREA '100' '"realmVoteTie"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmVoteTie")
	public void aTieKeepsTheCapital(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, 1);
		RealmGameTests.found(helper, land);
		UUID id = land.data().realmOf(land.at(0)).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok() && RealmInvites.accept(land.level(), land.hall(1), id, them).ok(), "joining");
		// a Village can't be put forward
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "adding the Village");
		Realms.Result small = CapitalVotes.putForward(land.level(), land.hall(2), land.owner());
		helper.assertTrue(small.refusal() == Realms.Refusal.RANK && small.message().getString().equals(land.name(2) + " must be a Town to be the capital; it is a Village."),
			"a Village put forward: " + small.message().getString());
		// a stranger can't put another's village forward
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), land.owner()).refusal() == Realms.Refusal.NOT_YOURS, "put forward by someone else");
		// the neighbour's Town: 1 for; the capital's owner votes for too, then changes their mind; the Village didn't vote
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), them).ok(), "putting forward");
		helper.assertTrue(CapitalVotes.vote(land.level(), land.hall(0), land.owner(), true).ok(), "voting for");
		helper.assertTrue(CapitalVotes.tally(land.data().realm(id), land.data().vote(id)).equals(new CapitalVotes.Tally(3, 0)), "the owner's vote counts for both their villages");
		helper.assertTrue(CapitalVotes.vote(land.level(), land.hall(0), land.owner(), false).ok(), "changing the vote");
		// a third player with no village in the realm can't vote
		ServerPlayer outsider = RealmGameTests.listener(helper, new ArrayList<>(), "outsider");
		helper.assertTrue(CapitalVotes.vote(land.level(), land.hall(0), outsider, true).refusal() == Realms.Refusal.NOT_VOTER, "an outsider voted");
		// the Village leaves: 1 for, 1 against, a tie
		helper.assertTrue(Realms.leave(land.level(), land.hall(2), land.owner()).ok(), "the Village leaving");
		Realms.settle(server, land.data().vote(id).started() + CapitalVotes.DAY);
		RealmData.Realm realm = land.data().realm(id);
		helper.assertTrue(realm.capital().equals(land.at(0)) && land.data().vote(id) == null, "a tie moved the capital: " + realm);
		helper.assertTrue(heard(theirs, "The vote is over, villages for " + land.name(1) + ": 1, against: 1. The capital stays at " + land.name(0) + "."), "no vote-over line: " + theirs);
		helper.assertTrue(RealmGameTests.chronicled(land, 1, "The vote went against " + land.name(1) + ": the capital stays at " + land.name(0)), "no chronicle line");
		// nobody but the proposer votes: 1 for, 1 not voting (against), kept again
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), them).ok(), "putting forward again");
		Realms.settle(server, land.data().vote(id).started() + CapitalVotes.DAY);
		helper.assertTrue(land.data().realm(id).capital().equals(land.at(0)), "a village that didn't vote counted for the move");
		helper.succeed();
	}

	/** One player owns every member: putting a Town forward moves the capital at once, with fireworks and no vote. */
	//$ gametest_ticks_batch AREA '100' '"realmMoveAtOnce"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmMoveAtOnce")
	public void oneOwnerMovesTheCapitalAtOnce(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		RealmGameTests.found(helper, land);
		UUID id = land.data().realmOf(land.at(0)).id();
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "adding");
		ChoiceMenu tab = RealmGameTests.realmTab(helper, land, 1, land.owner());
		tab.press(Realms.PUT_FORWARD, land.owner());
		RealmData.Realm realm = land.data().realm(id);
		helper.assertTrue(realm.capital().equals(land.at(1)) && land.data().vote(id) == null, "didn't move at once: " + realm + " " + land.data().vote(id));
		helper.assertTrue(RealmGameTests.heard(land, "You own every village in the realm: " + land.name(1) + " is its capital now."), "no line: " + land.heard());
		helper.assertTrue(!land.level().getEntitiesOfClass(FireworkRocketEntity.class, new AABB(land.hall(1)).inflate(4, 8, 4)).isEmpty(), "no fireworks");
		helper.assertTrue(RealmGameTests.chronicled(land, 0, "The capital moved from " + land.name(0) + " to " + land.name(1) + ": the realm is now the Barony of " + land.name(1)),
			"no chronicle line: " + land.entity(0).chronicle());
		helper.assertTrue(tab.icon(Realms.PUT_FORWARD).isEmpty(), "the new capital still offers itself");
		helper.succeed();
	}

	/** An invitation and a vote in the middle survive save and reload, and still work after; an older file has neither. */
	//$ gametest_ticks_batch AREA '100' '"realmInviteSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmInviteSave")
	public void invitesAndVotesSurviveSaveAndReload(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, 1, 2);
		RealmGameTests.found(helper, land);
		UUID id = land.data().realmOf(land.at(0)).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok() && RealmInvites.accept(land.level(), land.hall(1), id, them).ok(), "joining");
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "the invitation mid-way");
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), them).ok(), "the vote mid-way");
		RealmData data = land.data();
		RealmData back = RealmData.load(data.save(new CompoundTag(), land.level().registryAccess()), land.level().registryAccess());
		helper.assertTrue(back.invites().equals(data.invites()) && back.invites().size() == 1, "the invitations changed: " + data.invites() + " -> " + back.invites());
		helper.assertTrue(back.votes().equals(data.votes()) && Boolean.TRUE.equals(back.vote(id).ballot(land.at(1))), "the vote changed: " + data.votes() + " -> " + back.votes());
		RealmData old = RealmData.load(new CompoundTag(), land.level().registryAccess());
		helper.assertTrue(old.invites().isEmpty() && old.votes().isEmpty(), "an older file has invitations or votes");
		// a realm that ends takes its invitations and vote with it
		RealmData copy = RealmData.load(data.save(new CompoundTag(), land.level().registryAccess()), land.level().registryAccess());
		copy.removeRealm(id);
		helper.assertTrue(copy.invites().isEmpty() && copy.votes().isEmpty(), "an ended realm's invitations or vote stayed");
		// the reloaded vote still ends: 1 for, 1 against (the capital didn't vote): kept
		Realms.settle(server, data.vote(id).started() + CapitalVotes.DAY);
		helper.assertTrue(data.vote(id) == null && data.realm(id).capital().equals(land.at(0)), "the vote didn't end");
		helper.succeed();
	}

	/** With {@code realms} off: no invitation, answer, putting forward or vote, nothing lapses or ends; all of it comes back. */
	//$ gametest_ticks_batch AREA '100' '"realmInviteSwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmInviteSwitch")
	public void theSwitchOff(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = neighbour(helper, land, theirs, 1, 2);
		RealmGameTests.found(helper, land);
		UUID id = land.data().realmOf(land.at(0)).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok() && RealmInvites.accept(land.level(), land.hall(1), id, them).ok(), "joining");
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "inviting");
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), them).ok(), "putting forward");
		Realms.ENABLED = false;
		helper.assertTrue(RealmInvites.accept(land.level(), land.hall(2), id, them).refusal() == Realms.Refusal.OFF, "accepted while off");
		helper.assertTrue(RealmInvites.decline(land.level(), land.hall(2), id, them).refusal() == Realms.Refusal.OFF, "declined while off");
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(2), land.owner()).refusal() == Realms.Refusal.OFF, "invited while off");
		helper.assertTrue(CapitalVotes.vote(land.level(), land.hall(0), land.owner(), true).refusal() == Realms.Refusal.OFF, "voted while off");
		helper.assertTrue(CapitalVotes.putForward(land.level(), land.hall(1), them).refusal() == Realms.Refusal.OFF, "put forward while off");
		long started = land.data().vote(id).started();
		Realms.settle(server, started + RealmInvites.LAPSE + 1);
		helper.assertTrue(land.data().vote(id) != null && land.data().invite(id, land.at(2)) != null, "a vote ended or an invitation lapsed while off");
		Realms.ENABLED = true;
		helper.assertTrue(RealmInvites.accept(land.level(), land.hall(2), id, them).ok() && land.data().realmOf(land.at(2)) != null, "the invitation didn't come back");
		helper.succeed();
	}
}
