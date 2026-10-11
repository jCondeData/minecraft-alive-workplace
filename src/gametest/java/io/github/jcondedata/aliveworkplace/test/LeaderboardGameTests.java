package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Leaderboard;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ROADMAP 33.19, the weekly leaderboard, with a clock the test sets (Sunday 19:00 UTC is due): three halls with known
 * numbers come out in the right order with the right lines, the winners' chronicles and ribbons, the standings so far by
 * command; a village left off on the Trade page (and one without an owner) isn't listed; a tie goes to the bigger
 * village; a week missed while the server was down is announced once; the week's numbers survive save and reload; and
 * the switch.
 */
public class LeaderboardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String[] NAMES = {"Ashford", "Brook", "Corth"};
	private static final String[] OWNERS = {"Alex", "Bea", "Cal"};
	private static final int[] VILLAGERS = {12, 20, 8};

	static Clock at(String instant) {
		return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
	}

	/**
	 * Three villages (Ashford 12 villagers, Brook 20, Corth 8), owned by Alex, Bea and Cal (one listening player), the
	 * leaderboard on, Sunday 19:00 UTC, its records empty and the week begun last Sunday; all put back after.
	 */
	static Land three(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean enabled = Leaderboard.ENABLED;
		Clock clock = Leaderboard.CLOCK;
		DayOfWeek day = Leaderboard.DAY;
		int hour = Leaderboard.HOUR;
		Leaderboard.ENABLED = true;
		Leaderboard.DAY = DayOfWeek.SUNDAY;
		Leaderboard.HOUR = 19;
		Leaderboard.CLOCK = at("2026-10-04T12:00:00Z"); // a Sunday morning
		RealmData data = land.data();
		data.clearLeaderboard();
		Leftovers.after(helper, () -> {
			Leaderboard.ENABLED = enabled;
			Leaderboard.CLOCK = clock;
			Leaderboard.DAY = day;
			Leaderboard.HOUR = hour;
			RealmData.get(server).clearLeaderboard();
		});
		for (int i = 0; i < 3; i++) {
			land.entity(i).setCustomName(Component.literal(NAMES[i]));
			land.entity(i).setOwner(land.owner().getUUID(), OWNERS[i]);
			Realms.round(land.level(), land.hall(i), land.entity(i), VILLAGERS[i]);
		}
		Leaderboard.settle(server, false); // the week begins: last Sunday 19:00
		helper.assertTrue(data.weekStarted() == Instant.parse("2026-09-27T19:00:00Z").toEpochMilli(), "the week began at " + data.weekStarted());
		return land;
	}

	private static long count(Land land, String line) {
		return land.heard().stream().filter(c -> c.getString().equals(line)).count();
	}

	private static boolean chronicled(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CUP && e.text().getString().equals(line));
	}

	/** Known numbers in, Sunday 19:00 comes: every board in the right order with the right lines; winners noted and ribboned. */
	//$ gametest_ticks_batch AREA '100' '"leaderboardOrder"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "leaderboardOrder")
	public void threeHallsComeOutInOrder(GameTestHelper helper) {
		Land land = three(helper);
		MinecraftServer server = land.level().getServer();
		RealmData data = land.data();
		int[] cents = {500, 250, 1200};
		int[][] wellbeing = {{80, 60}, {90, 90}, {50, 50}};
		int[] legends = {2, 0, 1};
		for (int i = 0; i < 3; i++) {
			Leaderboard.earned(land.level(), land.hall(i), cents[i]);
			for (int w : wellbeing[i]) {
				Leaderboard.round(land.level(), land.hall(i), w, legends[i], 0);
			}
		}
		// Ashford's realm, with Corth: 2 villages, 20 villagers
		data.putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), land.at(0),
			List.of(new RealmData.Member(land.at(0), 0), new RealmData.Member(land.at(2), 0)), Optional.empty(), "barony", 0));
		Leaderboard.CLOCK = at("2026-10-04T18:59:00Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(land.heard().stream().noneMatch(c -> c.getString().startsWith("The week's leaderboard")), "announced before 19:00: " + land.heard());
		// the standings so far, by command
		server.getCommands().performPrefixedCommand(land.owner().createCommandSourceStack(), "workplace leaderboard");
		helper.assertTrue(count(land, "The leaderboard so far this week (read out on Sunday at 19:00):") == 1, "no standings by command: " + land.heard());
		helper.assertTrue(count(land, "Biggest: 1. Brook (Bea) 20 villagers, 2. Ashford (Alex) 12 villagers, 3. Corth (Cal) 8 villagers") == 1,
			"the command's Biggest line: " + land.heard());
		land.heard().clear();
		Leaderboard.CLOCK = at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		List<String> expected = List.of(
			"The week's leaderboard! How the villages did this week:",
			"Biggest: 1. Brook (Bea) 20 villagers, 2. Ashford (Alex) 12 villagers, 3. Corth (Cal) 8 villagers",
			"Richest: 1. Corth (Cal) 12 emeralds, 2. Ashford (Alex) 5 emeralds, 3. Brook (Bea) 2.5 emeralds",
			"Happiest: 1. Brook (Bea) 90% wellbeing, 2. Ashford (Alex) 70% wellbeing, 3. Corth (Cal) 50% wellbeing",
			"Most Legends: 1. Ashford (Alex) 2 Legends, 2. Corth (Cal) 1 Legend",
			"Greatest realm: 1. the Barony of Ashford (Alex) 2 villages, 20 villagers");
		List<String> heard = land.heard().stream().map(Component::getString).toList();
		helper.assertTrue(heard.equals(expected), "the lines: " + heard);
		// the winners' chronicles and ribbons
		helper.assertTrue(chronicled(land, 1, "Won the week's leaderboard: Biggest village, with 20 villagers"), "Brook's chronicle: " + land.entity(1).chronicle());
		helper.assertTrue(chronicled(land, 2, "Won the week's leaderboard: Richest village, with 12 emeralds"), "Corth's chronicle");
		helper.assertTrue(chronicled(land, 0, "Won the week's leaderboard: Greatest realm, with 2 villages"), "Ashford's chronicle");
		ChoiceMenu menu = VillageHallScreen.forTest(land.owner(), land.hall(1));
		ItemStack name = menu.icon(VillageHallScreen.NAME);
		helper.assertTrue(TradePage.marks(name).contains(Leaderboard.RIBBON), "Brook's name icon wears no ribbon");
		helper.assertTrue(RealmGameTests.lore(name).contains("Ribbon of the week: Biggest village (20 villagers)"), "the ribbon's line: " + RealmGameTests.lore(name));
		// a new week: earnings back to naught, nothing announced twice; a week later the ribbon is gone
		helper.assertTrue(data.tally(land.at(2)).earned() == 0 && data.tally(land.at(0)).legends() == 2, "the week's numbers: " + data.tally(land.at(2)));
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, expected.get(0)) == 1, "announced twice");
		Leaderboard.CLOCK = at("2026-10-11T19:00:10Z");
		helper.assertTrue(Leaderboard.ribbons(land.level(), land.hall(1)).isEmpty(), "the ribbon outlasted its week");
		helper.succeed();
	}

	/** A village its owner left off on the Trade page isn't listed, nor one without an owner; a stranger can't choose for it. */
	//$ gametest_ticks_batch AREA '100' '"leaderboardLeftOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "leaderboardLeftOff")
	public void aVillageLeftOffIsntListed(GameTestHelper helper) {
		Land land = three(helper);
		MinecraftServer server = land.level().getServer();
		ChoiceMenu menu = VillageHallScreen.forTest(land.owner(), land.hall(1));
		menu.press(VillageHallScreen.ROUTES, land.owner());
		ItemStack toggle = menu.icon(Leaderboard.TOGGLE);
		helper.assertTrue(toggle.is(Items.GOLD_INGOT) && toggle.getHoverName().getString().equals("On the weekly leaderboard"), "no toggle: " + toggle);
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "stranger");
		helper.assertTrue(Leaderboard.toggle(land.level(), land.hall(1), stranger).getString().equals("Only the owner of Brook can choose that."), "a stranger chose");
		menu.press(Leaderboard.TOGGLE, land.owner());
		helper.assertTrue(land.data().leftOff(land.at(1)), "the toggle didn't leave Brook off");
		helper.assertTrue(menu.icon(Leaderboard.TOGGLE).getHoverName().getString().equals("Left off the weekly leaderboard"), "the toggle didn't change");
		land.entity(2).setOwner(null, "");
		Realms.round(land.level(), land.hall(2), land.entity(2), VILLAGERS[2]);
		List<Leaderboard.Standing> biggest = Leaderboard.standings(server, Leaderboard.Board.BIGGEST);
		helper.assertTrue(biggest.size() == 1 && biggest.get(0).hall().equals(land.at(0)), "listed: " + biggest);
		land.heard().clear(); // the toggle's own line
		Leaderboard.CLOCK = at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(!land.heard().isEmpty() && land.heard().stream().noneMatch(c -> c.getString().contains("Brook") || c.getString().contains("Corth")), "listed: " + land.heard());
		// back on
		menu.press(Leaderboard.TOGGLE, land.owner());
		helper.assertTrue(!land.data().leftOff(land.at(1)) && Leaderboard.standings(server, Leaderboard.Board.BIGGEST).size() == 2, "Brook didn't come back");
		helper.succeed();
	}

	/** A tie goes to the bigger village; the takings count as earned. */
	//$ gametest_ticks_batch AREA '100' '"leaderboardTie"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "leaderboardTie")
	public void aTieGoesToTheBiggerVillage(GameTestHelper helper) {
		Land land = three(helper);
		MinecraftServer server = land.level().getServer();
		Leaderboard.earned(land.level(), land.hall(0), 300);
		Leaderboard.earned(land.level(), land.hall(1), 300);
		Leaderboard.round(land.level(), land.hall(0), 75, 1, 0);
		Leaderboard.round(land.level(), land.hall(1), 75, 1, 0);
		for (Leaderboard.Board board : List.of(Leaderboard.Board.RICHEST, Leaderboard.Board.HAPPIEST, Leaderboard.Board.LEGENDS)) {
			List<Leaderboard.Standing> top = Leaderboard.standings(server, board);
			helper.assertTrue(top.size() >= 2 && top.get(0).hall().equals(land.at(1)) && top.get(1).hall().equals(land.at(0)), board + ": " + top);
		}
		// a day's takings, from the hall's own round, count toward the Richest
		long day = Chronicle.day(land.level());
		land.entity(2).setLastTaxDay(day - 1);
		int before = land.entity(2).treasury();
		Treasury.round(land.level(), land.hall(2), land.entity(2), 4);
		long earned = land.data().tally(land.at(2)).earned();
		helper.assertTrue(earned > 0 && earned == land.entity(2).treasury() - before, "the takings: earned " + earned + ", treasury " + before + " -> " + land.entity(2).treasury());
		helper.succeed();
	}

	/** A week missed while the server was down is announced at the next start, once. */
	//$ gametest_ticks_batch AREA '100' '"leaderboardMissed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "leaderboardMissed")
	public void aMissedWeekIsAnnouncedOnce(GameTestHelper helper) {
		Land land = three(helper);
		MinecraftServer server = land.level().getServer();
		Leaderboard.CLOCK = at("2026-10-07T09:00:00Z"); // the server was down over Sunday evening
		Leaderboard.settle(server, true);
		String late = "The week's leaderboard, missed while the server was down! How the villages did:";
		helper.assertTrue(count(land, late) == 1, "not announced at the start: " + land.heard());
		helper.assertTrue(count(land, "Biggest: 1. Brook (Bea) 20 villagers, 2. Ashford (Alex) 12 villagers, 3. Corth (Cal) 8 villagers") == 1, "the lines: " + land.heard());
		Leaderboard.settle(server, false);
		Leaderboard.settle(server, true);
		helper.assertTrue(count(land, late) == 1 && land.heard().stream().noneMatch(c -> c.getString().startsWith("The week's leaderboard!")), "announced again: " + land.heard());
		helper.assertTrue(land.data().weekStarted() == Instant.parse("2026-10-04T19:00:00Z").toEpochMilli(), "the new week: " + land.data().weekStarted());
		helper.succeed();
	}

	/** The week's numbers, the villages left off, the ribbons and the held lines survive save and reload; an old file has none. */
	//$ gametest_ticks_batch AREA '100' '"leaderboardSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "leaderboardSave")
	public void theWeeksNumbersSurviveSaveAndReload(GameTestHelper helper) {
		Land land = three(helper);
		MinecraftServer server = land.level().getServer();
		RealmData data = land.data();
		Leaderboard.earned(land.level(), land.hall(0), 777);
		Leaderboard.round(land.level(), land.hall(0), 64, 3, 2);
		data.setLeftOff(land.at(2), true);
		data.setRibbons(List.of(new RealmData.Ribbon(land.at(1), "biggest", 20, 12345L, 7, false)));
		data.setHeldLines(List.of(Component.literal("held")));
		CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
		RealmData back = RealmData.load(tag, server.registryAccess());
		helper.assertTrue(back.tallies().equals(data.tallies()), "the numbers: " + back.tallies() + " vs " + data.tallies());
		helper.assertTrue(back.tally(land.at(0)).equals(new RealmData.Tally(land.at(0), 777, 64, 1, 3, 2)), "Ashford's numbers: " + back.tally(land.at(0)));
		helper.assertTrue(back.leftOff(land.at(2)) && !back.leftOff(land.at(0)), "left off");
		helper.assertTrue(back.ribbons().equals(data.ribbons()) && back.heldLines().size() == 1 && back.weekStarted() == data.weekStarted(), "ribbons, lines, week");
		RealmData old = RealmData.load(new CompoundTag(), server.registryAccess());
		helper.assertTrue(old.tallies().isEmpty() && old.ribbons().isEmpty() && old.weekStarted() == 0, "an old file has leaderboard data");
		helper.succeed();
	}

	/** {@code weeklyLeaderboard} off: nothing counted or announced, no toggle, the command says so. */
	//$ gametest_ticks_batch AREA '100' '"leaderboardOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "leaderboardOff")
	public void theSwitchOff(GameTestHelper helper) {
		Land land = three(helper);
		MinecraftServer server = land.level().getServer();
		Leaderboard.ENABLED = false;
		Leaderboard.earned(land.level(), land.hall(0), 500);
		Leaderboard.round(land.level(), land.hall(0), 80, 1, 1);
		helper.assertTrue(land.data().tally(land.at(0)).earned() == 0 && land.data().tally(land.at(0)).rounds() == 0, "counted while off");
		Leaderboard.CLOCK = at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(land.heard().isEmpty(), "announced while off: " + land.heard());
		ChoiceMenu menu = VillageHallScreen.forTest(land.owner(), land.hall(0));
		menu.press(VillageHallScreen.ROUTES, land.owner());
		helper.assertTrue(menu.icon(Leaderboard.TOGGLE).isEmpty(), "the toggle shows while off");
		server.getCommands().performPrefixedCommand(land.owner().createCommandSourceStack(), "workplace leaderboard");
		helper.assertTrue(count(land, "The weekly leaderboard is switched off on this server.") == 1, "the command: " + land.heard());
		// switched back on later: the week that passed while off isn't read out
		Leaderboard.ENABLED = true;
		Leaderboard.settle(server, false);
		helper.assertTrue(land.heard().stream().noneMatch(c -> c.getString().startsWith("The week's leaderboard")), "a stale week read out: " + land.heard());
		helper.succeed();
	}
}
