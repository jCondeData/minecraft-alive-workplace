package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane C, 2026-11-01), B637 verified from its spec: the Pacts tab's feud lines in a war's last hour and a feud's
 * last hour before it fades read "At war: less than an hour left" / "Fades in less than an hour without a war", and
 * every other remaining time reads a true, correctly pluralised count. Adds to QaFeudLastHourBugTests (500, 999,
 * 1000, 23999 and 24000 ticks): one tick left, an hour and a tick, the hour's far edge (1999, 2000), singular and plural
 * days with singular and plural hours, a feud fading after its war, the rival village's own tab, and the line as time
 * runs out (the war ends, the feud fades) with no "0 days and 0 hours" ever.
 */
public class QaFeudLastHour1101Tests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static void feudsOn(GameTestHelper helper, MinecraftServer server) {
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Feuds.Data.get(server).clear();
		});
	}

	/** The feud icon's lore on {@code hall}'s Pacts tab as {@code player} opens it, or "(no feud line)". */
	private static List<String> feudLore(Land land, int hall, ServerPlayer player) {
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, hall, player, TradePage.Tab.PACTS);
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().startsWith("Feud with")) {
				return RealmGameTests.lore(menu.icon(slot));
			}
		}
		return List.of("(no feud line)");
	}

	private static List<String> warLore(Land land, MinecraftServer server, long left) {
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).clear();
		// a war begun this tick (a start below 0 means none), raised two days after the feud
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now, now + left, false, 0, 0, 0));
		return feudLore(land, 0, land.owner());
	}

	private static List<String> fadeLore(Land land, MinecraftServer server, long left) {
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - left)));
		return feudLore(land, 0, land.owner());
	}

	private static void expect(List<String> wrong, String what, List<String> lore, String line) {
		if (!lore.contains(line)) {
			wrong.add(what + ": expected '" + line + "', got " + lore);
		}
	}

	/** The war line at each boundary of the last hour and of the count's singular and plural forms. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1101War"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1101War")
	public void theWarLineReadsTrueAtEveryEdge(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		expect(wrong, "1 tick of war left", warLore(land, server, 1), "At war: less than an hour left");
		expect(wrong, "1001 ticks of war left", warLore(land, server, 1001), "At war: 0 days and 1 hour left");
		expect(wrong, "1999 ticks of war left", warLore(land, server, 1999), "At war: 0 days and 1 hour left");
		expect(wrong, "2000 ticks of war left", warLore(land, server, 2000), "At war: 0 days and 2 hours left");
		expect(wrong, "25000 ticks of war left", warLore(land, server, 25000), "At war: 1 day and 1 hour left");
		expect(wrong, "26000 ticks of war left", warLore(land, server, 26000), "At war: 1 day and 2 hours left");
		expect(wrong, "49000 ticks of war left", warLore(land, server, 49000), "At war: 2 days and 1 hour left");
		expect(wrong, "48000 ticks of war left", warLore(land, server, 48000), "At war: 2 days and 0 hours left");
		expect(wrong, "74999 ticks of war left", warLore(land, server, 74999), "At war: 3 days and 2 hours left");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** The fading line at the same edges, for a feud that never went to war. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1101Fade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1101Fade")
	public void theFadingLineReadsTrueAtEveryEdge(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		expect(wrong, "1 tick before it fades", fadeLore(land, server, 1), "Fades in less than an hour without a war");
		expect(wrong, "1001 ticks before it fades", fadeLore(land, server, 1001), "Fades in 0 days and 1 hour without a war");
		expect(wrong, "1999 ticks before it fades", fadeLore(land, server, 1999), "Fades in 0 days and 1 hour without a war");
		expect(wrong, "2000 ticks before it fades", fadeLore(land, server, 2000), "Fades in 0 days and 2 hours without a war");
		expect(wrong, "25000 ticks before it fades", fadeLore(land, server, 25000), "Fades in 1 day and 1 hour without a war");
		expect(wrong, "49000 ticks before it fades", fadeLore(land, server, 49000), "Fades in 2 days and 1 hour without a war");
		expect(wrong, "a new feud (7 days)", fadeLore(land, server, Feuds.FADE), "Fades in 7 days and 0 hours without a war");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** A feud whose war is over fades from the war's end: its last hour reads the same, and the rival's tab agrees. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1101AfterWar"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1101AfterWar")
	public void aFeudAfterItsWarAndTheRivalsTabReadTheLastHourToo(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		long now = server.overworld().getGameTime();
		long warEnds = now - (Feuds.FADE - 1);
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), warEnds - 3 * 24000L, warEnds - 2 * 24000L, warEnds, true, 1, 2, 0));
		expect(wrong, "1 tick before a feud fades after its war", feudLore(land, 0, land.owner()), "Fades in less than an hour without a war");
		expect(wrong, "the same, on the rival's tab", feudLore(land, 1, two.them()), "Fades in less than an hour without a war");
		Feuds.Data.get(server).clear();
		warEnds = now - (Feuds.FADE - 1000);
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), warEnds - 3 * 24000L, warEnds - 2 * 24000L, warEnds, true, 1, 2, 0));
		expect(wrong, "an hour before a feud fades after its war", feudLore(land, 0, land.owner()), "Fades in 0 days and 1 hour without a war");
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now, now + 500, false, 0, 0, 0));
		expect(wrong, "500 ticks of war left, on the rival's tab", feudLore(land, 1, two.them()), "At war: less than an hour left");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * As time runs out: a war with one tick left ends and its feud then reads its fading line (about seven days from
	 * the war's end); a feud one tick from fading is gone. At no point does a line read "0 days and 0 hours".
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFeud1101RunsOut"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFeud1101RunsOut")
	public void whenTheTimeRunsOutTheLinesMoveOnAndNeverReadZero(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> war = warLore(land, server, 1);
		helper.assertTrue(war.contains("At war: less than an hour left"), "1 tick of war left: " + war);
		helper.runAfterDelay(60, () -> {
			List<String> after = feudLore(land, 0, land.owner());
			helper.assertTrue(after.contains("Fades in 6 days and 23 hours without a war"),
				"three seconds after its war's last tick the feud should read its fading line, got " + after);
			helper.assertFalse(after.stream().anyMatch(l -> l.startsWith("At war") || l.contains("0 days and 0 hours")), "after the war: " + after);
			List<String> fading = fadeLore(land, server, 1);
			helper.assertTrue(fading.contains("Fades in less than an hour without a war"), "1 tick before it fades: " + fading);
			helper.runAfterDelay(60, () -> {
				List<String> gone = feudLore(land, 0, land.owner());
				helper.assertTrue(gone.equals(List.of("(no feud line)")), "three seconds after it should have faded the feud still reads " + gone);
				helper.succeed();
			});
		});
	}
}
