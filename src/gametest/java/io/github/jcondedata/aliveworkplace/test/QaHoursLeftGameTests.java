package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-c-1025-0953) on the two "hours left" lines of the realm (33.x, read on the hall's tabs the way a player
 * reads them), at the hour boundaries both sides: the capital vote's "About N hours left" (it lasts a day) and the war
 * button's "the feud must stand a day (N hours left)". An hour is 1000 ticks, and a part hour counts as a whole one.
 * The nightly mutation run of 2026-10-25 left `(left + 999) / 1000` to `(left - 999) / 1000` alive in the count each
 * line is worded by (RealmTab.java:225, Feuds.java:604): "2 hour left", "1 hours left".
 */
public class QaHoursLeftGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * A capital vote with 1500, 1001, 1000 and 24000 ticks to go: "About 2 hours left", "About 2 hours left", "About 1
	 * hour left" and "About 24 hours left" on the capital owner's Realm tab.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaVoteHoursLeft"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaVoteHoursLeft")
	public void theVotesHoursLeftCountAPartHourAsAWholeOne(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = RealmGameTests.listener(helper, theirs, "neighbour");
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
		helper.assertTrue(vote != null, "setup: no vote opened");
		long[] lefts = {1500, 1001, 1000, 24000};
		String[] lines = {"About 2 hours left", "About 2 hours left", "About 1 hour left", "About 24 hours left"};
		for (int i = 0; i < lefts.length; i++) {
			long now = server.overworld().getGameTime();
			land.data().putVote(new RealmData.Vote(vote.realm(), vote.candidate(), now - (CapitalVotes.DAY - lefts[i]), vote.ballots()));
			List<String> lore = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(Realms.VOTE));
			helper.assertTrue(lore.contains(lines[i]), lefts[i] + " ticks left: expected \"" + lines[i] + "\" in " + lore);
		}
		helper.succeed();
	}

	/**
	 * A feud 500 ticks short of a day, then 1500 and 1000 short: the war button reads "(1 hour left)", "(2 hours left)"
	 * and "(1 hour left)".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFeudHoursLeft"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudHoursLeft")
	public void theWarButtonsHoursLeftCountAPartHourAsAWholeOne(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean pacts = Pacts.ENABLED;
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Pacts.ENABLED = true;
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			Caravans.Data data = Caravans.Data.get(land.level());
			data.remove(land.hall(0));
			data.remove(land.hall(1));
		});
		ServerPlayer them = RealmGameTests.listener(helper, new ArrayList<>(), "neighbour");
		land.entity(1).setOwner(them.getUUID(), "neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "setup: couldn't declare the feud");
		long[] lefts = {500, 1500, 1000};
		String[] hours = {"(1 hour left)", "(2 hours left)", "(1 hour left)"};
		for (int i = 0; i < lefts.length; i++) {
			long now = server.overworld().getGameTime();
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.WAR_AFTER - lefts[i])));
			ChoiceMenu menu = VillageHallScreen.forTest(land.owner(), land.hall(0));
			menu.press(VillageHallScreen.ROUTES, land.owner());
			menu.press(TradePage.Tab.PACTS.slot(), land.owner());
			int war = find(menu, "Raise the feud with " + land.name(1) + " to war");
			String expected = "Can't be pressed yet: the feud must stand a day " + hours[i] + ".";
			helper.assertTrue(war >= 0 && lore(menu.icon(war)).contains(expected), lefts[i] + " ticks left: expected \"" + expected + "\" on the war button, got "
				+ (war < 0 ? "no button" : lore(menu.icon(war))));
		}
		helper.succeed();
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
}
