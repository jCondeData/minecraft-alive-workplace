package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
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
 * pact offer (B596) and the realm invitation (B597) now read "less than an hour left".
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
}
