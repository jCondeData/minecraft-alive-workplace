package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane E, 2026-10-27), player-facing sentences, B596/B597's kind on the feud lines of the Pacts tab: in a war's
 * last hour the feud reads "At war: 0 days and 0 hours left", and in a feud's last hour before it fades "Fades in 0
 * days and 0 hours without a war" (Feuds.fillTab, screen.aliveworkplace.feud.at_war / .fades, no last-hour line). The
 * pact offer (B596) and the realm invitation (B597) now read "less than an hour left". Filed as B637; lane C's
 * (2026-10-28, B672) two tests of the same lines, from QaWordsVillage1028BugTests on tests/words-village-1028, are kept
 * here with it, and the fix's own check of the exact last-hour lines and the hour just before.
 */
public class QaFeudLastHourBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Two villages of two owners on the caravans' list, pacts and feuds on and empty, PvP on; all put back after. */
	private static Land two(GameTestHelper helper) {
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
		return land;
	}

	/** Every lore line on the hall's Pacts tab, opened the way a player does. */
	private static List<String> tabLines(Land land) {
		ChoiceMenu menu = VillageHallScreen.forTest(land.owner(), land.hall(0));
		menu.press(VillageHallScreen.ROUTES, land.owner());
		menu.press(TradePage.Tab.PACTS.slot(), land.owner());
		List<String> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemLore lore = menu.icon(slot).get(DataComponents.LORE);
			if (lore != null) {
				lore.lines().forEach(l -> out.add(l.getString()));
			}
		}
		return out;
	}

	//$ gametest_ticks_batch AREA '100' '"qaFeudLastHour"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudLastHour")
	public void aWarsAndAFeudsLastHourDontReadZeroDaysAndZeroHours(GameTestHelper helper) {
		Land land = two(helper);
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();

		// a war begun this tick with 500 ticks (half an hour) left (a war's start can't be below 0: that means none)
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now, now, now + 500, false, 0, 0, 0));
		List<String> war = tabLines(land);
		helper.assertTrue(war.stream().anyMatch(l -> l.startsWith("At war")), "setup: no war line on the Pacts tab: " + war);
		List<String> wrong = new ArrayList<>(war.stream().filter(l -> l.startsWith("At war") && l.contains("0 days and 0 hours")).toList());

		// a feud without a war, 500 ticks before it fades
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - Feuds.FADE + 500));
		List<String> fading = tabLines(land);
		helper.assertTrue(fading.stream().anyMatch(l -> l.startsWith("Fades in")), "setup: no fading line on the Pacts tab: " + fading);
		wrong.addAll(fading.stream().filter(l -> l.startsWith("Fades in") && l.contains("0 days and 0 hours")).toList());
		helper.assertTrue(wrong.isEmpty(), "in their last hour the feud lines read " + wrong
			+ " (expected e.g. 'At war: less than an hour left', 'Fades in less than an hour without a war', as the pact offer reads)");
		helper.succeed();
	}

	// ---- lane C's tests of the same lines (B672, QaWordsVillage1028BugTests on tests/words-village-1028) ----------------

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

	private static List<String> feudLore(Land land) {
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().startsWith("Feud with")) {
				return RealmGameTests.lore(menu.icon(slot));
			}
		}
		return List.of("(no feud line)");
	}

	/**
	 * Like B596's offers: a feud with under an hour before it fades (500 ticks, then 1) shouldn't read "Fades in 0 days
	 * and 0 hours without a war", which is false; something true, such as "Fades within the hour without a war".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWordsVillageBugFade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWordsVillageBugFade")
	public void aFeudFadingWithinTheHourDoesntSayZeroDaysAndZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		for (long left : new long[]{500, 1}) {
			long now = server.overworld().getGameTime();
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - left)));
			List<String> lore = feudLore(land);
			boolean says = lore.stream().anyMatch(l -> l.startsWith("Fades"));
			if (!says || lore.stream().anyMatch(l -> l.contains("0 days and 0 hours"))) {
				wrong.add(left + " ticks before it fades: " + lore);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** Likewise a war in its last hour (500 ticks left, then 1): not "At war: 0 days and 0 hours left". */
	//$ gametest_ticks_batch AREA '100' '"qaWordsVillageBugWar"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWordsVillageBugWar")
	public void aWarInItsLastHourDoesntSayZeroDaysAndZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		for (long left : new long[]{500, 1}) {
			long now = server.overworld().getGameTime();
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 100, now + left, false, 0, 0, 0));
			List<String> lore = feudLore(land);
			boolean says = lore.stream().anyMatch(l -> l.startsWith("At war"));
			if (!says || lore.stream().anyMatch(l -> l.contains("0 days and 0 hours"))) {
				wrong.add(left + " ticks of war left: " + lore);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- the fix's exact lines -----------------------------------------------------------------------------------------

	/**
	 * The fix (B637, B672): under an hour the feud reads "At war: less than an hour left" or "Fades in less than an hour
	 * without a war"; an hour or more keeps the counted line ("0 days and 1 hour").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFeudLastHourExact"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeudLastHourExact")
	public void theFeudsLastHourLinesReadLessThanAnHour(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 100, now + 999, false, 0, 0, 0));
		List<String> war = feudLore(land);
		helper.assertTrue(war.contains("At war: less than an hour left"), "999 ticks of war left: " + war);
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 100, now + 1000, false, 0, 0, 0));
		war = feudLore(land);
		helper.assertTrue(war.contains("At war: 0 days and 1 hour left"), "1000 ticks of war left: " + war);
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - 999)));
		List<String> fade = feudLore(land);
		helper.assertTrue(fade.contains("Fades in less than an hour without a war"), "999 ticks before it fades: " + fade);
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - 1000)));
		fade = feudLore(land);
		helper.assertTrue(fade.contains("Fades in 0 days and 1 hour without a war"), "1000 ticks before it fades: " + fade);
		// the day's edges: a tick short of a day and exactly one day
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 100, now + 23999, false, 0, 0, 0));
		war = feudLore(land);
		helper.assertTrue(war.contains("At war: 0 days and 23 hours left"), "23999 ticks of war left: " + war);
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 100, now + 24000, false, 0, 0, 0));
		war = feudLore(land);
		helper.assertTrue(war.contains("At war: 1 day and 0 hours left"), "24000 ticks of war left: " + war);
		Feuds.Data.get(server).clear();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - 24000)));
		fade = feudLore(land);
		helper.assertTrue(fade.contains("Fades in 1 day and 0 hours without a war"), "24000 ticks before it fades: " + fade);
		helper.succeed();
	}
}
