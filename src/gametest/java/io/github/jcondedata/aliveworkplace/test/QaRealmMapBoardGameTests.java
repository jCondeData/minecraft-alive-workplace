package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Leaderboard;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmMaps;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (ROADMAP 33.19, 33.21), written from the spec: the leaderboard's week boundary to the second and its day and hour
 * settings, only the top three listed, ties going to the bigger village (whichever order they were counted in), the
 * Greatest realm by members then villagers; the Realm Map refused at a village outside the realm and without an empty
 * map, nothing used. Each test is alone in its batch.
 */
public class QaRealmMapBoardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TITLE = "The week's leaderboard! How the villages did this week:";

	private static long count(Land land, String line) {
		return land.heard().stream().filter(c -> c.getString().equals(line)).count();
	}

	/** Four owned villages, Ashford 12, Brook 20, Corth 8 and Dunmore 5 villagers; the leaderboard on, Sunday 19:00 UTC, the week begun last Sunday. */
	private static Land four(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean enabled = Leaderboard.ENABLED;
		var clock = Leaderboard.CLOCK;
		DayOfWeek day = Leaderboard.DAY;
		int hour = Leaderboard.HOUR;
		Leaderboard.ENABLED = true;
		Leaderboard.DAY = DayOfWeek.SUNDAY;
		Leaderboard.HOUR = 19;
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T12:00:00Z");
		land.data().clearLeaderboard();
		Leftovers.after(helper, () -> {
			Leaderboard.ENABLED = enabled;
			Leaderboard.CLOCK = clock;
			Leaderboard.DAY = day;
			Leaderboard.HOUR = hour;
			RealmData.get(server).clearLeaderboard();
		});
		String[] names = {"Ashford", "Brook", "Corth", "Dunmore"};
		String[] owners = {"Alex", "Bea", "Cal", "Dee"};
		int[] villagers = {12, 20, 8, 5};
		for (int i = 0; i < 4; i++) {
			land.entity(i).setCustomName(Component.literal(names[i]));
			land.entity(i).setOwner(land.owner().getUUID(), owners[i]);
			Realms.round(land.level(), land.hall(i), land.entity(i), villagers[i]);
		}
		Leaderboard.settle(server, false);
		return land;
	}

	/** 18:59:59 on Sunday: nothing; 19:00:00 exactly: read out; Monday 19:00: nothing more; the next Sunday 19:00:00: the second week. */
	//$ gametest_ticks_batch AREA '100' '"qaBoardBoundary"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardBoundary")
	public void qaWeekBoundaryToTheSecond(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T18:59:59Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, TITLE) == 0, "read out a second early");
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:00Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, TITLE) == 1, "not read out at 19:00:00: " + land.heard().stream().map(Component::getString).toList());
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-05T19:00:00Z");
		Leaderboard.settle(server, false);
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-11T18:59:59Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, TITLE) == 1, "read out again before the next Sunday 19:00");
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-11T19:00:00Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, TITLE) == 2 && land.data().weekStarted() == Instant.parse("2026-10-11T19:00:00Z").toEpochMilli(),
			"the second week: " + count(land, TITLE) + " readings, week from " + land.data().weekStarted());
		helper.succeed();
	}

	/** leaderboardDay and leaderboardHour: Wednesday 08:00 reads it out then, not on Sunday at 19:00. */
	//$ gametest_ticks_batch AREA '100' '"qaBoardDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardDay")
	public void qaTheDayAndHourSettings(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper); // the week began Sunday 2026-09-27 19:00
		MinecraftServer server = land.level().getServer();
		Leaderboard.DAY = DayOfWeek.WEDNESDAY;
		Leaderboard.HOUR = 8;
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-09-30T07:59:59Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, TITLE) == 0, "read out before Wednesday 08:00");
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-09-30T08:00:00Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, TITLE) == 1, "not read out on Wednesday at 08:00");
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:00Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, TITLE) == 1, "read out on Sunday 19:00 with the day set to Wednesday");
		server.getCommands().performPrefixedCommand(land.owner().createCommandSourceStack(), "workplace leaderboard");
		helper.assertTrue(land.heard().stream().anyMatch(c -> c.getString().equals("The leaderboard so far this week (read out on Wednesday at 08:00):")), "the command doesn't name Wednesday 08:00: "
			+ land.heard().stream().map(Component::getString).filter(s -> s.startsWith("The leaderboard so far")).toList());
		helper.succeed();
	}

	/** Four villages: only the top three are read out; a tie goes to the bigger village whichever was counted first. */
	//$ gametest_ticks_batch AREA '100' '"qaBoardTopThree"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardTopThree")
	public void qaTopThreeAndTiesGoToTheBigger(GameTestHelper helper) {
		Land land = four(helper);
		MinecraftServer server = land.level().getServer();
		// Dunmore (5) and Corth (8) counted before Ashford (12), all three on 3 emeralds; Brook 4
		Leaderboard.earned(land.level(), land.hall(3), 300);
		Leaderboard.earned(land.level(), land.hall(2), 300);
		Leaderboard.earned(land.level(), land.hall(0), 300);
		Leaderboard.earned(land.level(), land.hall(1), 400);
		List<Leaderboard.Standing> richest = Leaderboard.standings(server, Leaderboard.Board.RICHEST);
		helper.assertTrue(richest.size() == 4 && richest.get(0).hall().equals(land.at(1)) && richest.get(1).hall().equals(land.at(0))
			&& richest.get(2).hall().equals(land.at(2)) && richest.get(3).hall().equals(land.at(3)), "the Richest order: " + richest);
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		List<String> heard = land.heard().stream().map(Component::getString).toList();
		helper.assertTrue(heard.contains("Biggest: 1. Brook (Bea) 20 villagers, 2. Ashford (Alex) 12 villagers, 3. Corth (Cal) 8 villagers"), "Biggest: " + heard);
		helper.assertTrue(heard.contains("Richest: 1. Brook (Bea) 4 emeralds, 2. Ashford (Alex) 3 emeralds, 3. Corth (Cal) 3 emeralds"), "Richest: " + heard);
		helper.assertTrue(heard.stream().noneMatch(s -> s.contains("Dunmore")), "a fourth place read out: " + heard);
		helper.succeed();
	}

	/** Greatest realm: more members first; with as many members, more villagers. */
	//$ gametest_ticks_batch AREA '100' '"qaBoardRealm"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardRealm")
	public void qaGreatestRealmByMembersThenVillagers(GameTestHelper helper) {
		Land land = four(helper);
		MinecraftServer server = land.level().getServer();
		RealmData data = land.data();
		// Ashford with Corth: 2 villages, 20 villagers; Dunmore with Brook: 2 villages, 25 villagers
		data.putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), land.at(0),
			List.of(new RealmData.Member(land.at(0), 0), new RealmData.Member(land.at(2), 0)), Optional.empty(), "barony", 0));
		data.putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), land.at(3),
			List.of(new RealmData.Member(land.at(3), 0), new RealmData.Member(land.at(1), 0)), Optional.empty(), "barony", 0));
		List<Leaderboard.Standing> realms = Leaderboard.standings(server, Leaderboard.Board.REALM);
		helper.assertTrue(realms.size() == 2 && realms.get(0).hall().equals(land.at(3)) && realms.get(0).villagers() == 25
			&& realms.get(1).hall().equals(land.at(0)) && realms.get(1).villagers() == 20, "the Greatest realm: " + realms);
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		List<String> heard = land.heard().stream().map(Component::getString).toList();
		helper.assertTrue(heard.contains("Greatest realm: 1. the Barony of Dunmore (Dee) 2 villages, 25 villagers, 2. the Barony of Ashford (Alex) 2 villages, 20 villagers"),
			"the Greatest realm line: " + heard);
		helper.succeed();
	}

	/** The Realm Map: refused at a village outside the realm (map kept) and at a member's hall without an empty map; drawn at a member's hall. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmMapRefuse"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmMapRefuse")
	public void qaRealmMapRefusesOutsidersAndNoMap(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		boolean map = RealmMaps.ENABLED;
		RealmMaps.ENABLED = true;
		Leftovers.after(helper, () -> RealmMaps.ENABLED = map);
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "hall 1 didn't join");
		// no empty map: refused at a member's hall, nothing given
		Realms.Result none = RealmMaps.draw(land.level(), land.hall(1), land.owner());
		helper.assertTrue(!none.ok() && none.message().getString().equals("Bring an empty map to draw the realm on."), "without a map: " + none.message().getString());
		helper.assertTrue(land.owner().getInventory().countItem(Items.FILLED_MAP) == 0, "a map given without an empty one");
		// a village of the same owner outside the realm: refused, the map kept
		land.owner().getInventory().add(new ItemStack(Items.MAP));
		ChoiceMenu outside = RealmGameTests.realmTab(helper, land, 2, land.owner());
		helper.assertTrue(!outside.icon(RealmMaps.BUTTON).is(Items.FILLED_MAP), "a map button at a village outside the realm");
		Realms.Result out = RealmMaps.draw(land.level(), land.hall(2), land.owner());
		helper.assertTrue(!out.ok() && out.message().getString().equals("There's no realm map to draw here."), "outside the realm: " + out.message().getString());
		helper.assertTrue(land.owner().getInventory().countItem(Items.MAP) == 1 && land.owner().getInventory().countItem(Items.FILLED_MAP) == 0, "the empty map was used outside the realm");
		// at the member's hall it's drawn, with the capital named "(capital)"
		Realms.Result drawn = RealmMaps.draw(land.level(), land.hall(1), land.owner());
		String title = Realms.title(land.level().getServer(), land.data().realmOf(land.at(0))).getString();
		helper.assertTrue(drawn.ok() && drawn.message().getString().equals("Here's a map of " + title + "."), "at the member's hall: " + drawn.message().getString());
		helper.assertTrue(land.owner().getInventory().countItem(Items.MAP) == 0 && land.owner().getInventory().countItem(Items.FILLED_MAP) == 1, "not drawn on the empty map");
		helper.succeed();
	}
}
