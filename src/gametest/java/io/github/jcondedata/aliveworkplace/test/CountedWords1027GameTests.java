package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Counted sentences (24.5) fixed on 2026-10-27, each read for one and for several: a Wonder's goods quest for our
 * luxuries (B592), the Classes page's "Counted over" line (B594), and the Pacts tab's count, an alliance's last hour
 * and an offer's last hour (B596).
 */
public class CountedWords1027GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static void check(List<String> wrong, String got, String want) {
		if (!got.equals(want)) {
			wrong.add("\"" + got + "\" (want \"" + want + "\")");
		}
	}

	/** B592: the Wonders' goods quests ask for 2 of our luxuries in the plural, and for 1 in the singular. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wonderGoodsQuestForTwoLuxuriesReadsPlural(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		String[][] cases = {{"aliveworkplace:emerald_brooch", "Emerald Brooches", "Emerald Brooch"}, {"aliveworkplace:gold_circlet", "Gold Circlets", "Gold Circlet"},
			{"aliveworkplace:illuminated_book", "Illuminated Books", "Illuminated Book"}, {"aliveworkplace:amethyst_ring", "Amethyst Rings", "Amethyst Ring"},
			{"aliveworkplace:vintage_wine", "Bottles of Vintage Wine", "Vintage Wine"}, {"aliveworkplace:gazette", "Village Gazettes", "Village Gazette"}};
		for (String[] c : cases) {
			check(wrong, new Objectives.Bring(c[0], 2, "station", Optional.of(BlockPos.ZERO)).line().getString(), "Bring 2 " + c[1]);
			check(wrong, new Objectives.Bring(c[0], 1, "station", Optional.of(BlockPos.ZERO)).line().getString(), "Bring 1 " + c[2]);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static String countedLine(SocialClass c, ClassNeeds.Need need, int of) {
		ClassesPage.NeedCount count = new ClassesPage.NeedCount(need, false, of, of);
		ItemStack button = ClassesPage.classButton(new ClassesPage.Row(c, of, List.of(count), List.of()));
		return button.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString)
			.filter(l -> l.startsWith("Counted over")).findFirst().orElse("(none)");
	}

	/** B594: a class button counted over one household reads in the singular, over two in the plural, with and without a class below. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void countedOverOneHouseholdReadsInTheSingular(GameTestHelper helper) {
		SocialClass peasant = SocialClasses.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "peasant"));
		SocialClass artisan = SocialClasses.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "artisan"));
		helper.assertTrue(peasant != null && artisan != null && SocialClasses.step(artisan, -1) == peasant && SocialClasses.step(peasant, -1) == null,
			"setup: the shipped ladder isn't Peasant, Artisan");
		ClassNeeds.Need need = artisan.needs().get(0); // the line reads the same whatever the need
		List<String> wrong = new ArrayList<>();
		check(wrong, countedLine(artisan, need, 1), "Counted over the 1 household who is Artisan or Peasant");
		check(wrong, countedLine(artisan, need, 2), "Counted over the 2 households who are Artisan or Peasant");
		check(wrong, countedLine(peasant, need, 1), "Counted over its 1 household");
		check(wrong, countedLine(peasant, need, 3), "Counted over its 3 households");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static List<String> tabIcon(Land land, int hall, net.minecraft.server.level.ServerPlayer player) {
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, hall, player, TradePage.Tab.PACTS);
		return RealmGameTests.lore(menu.icon(TradePage.Tab.PACTS.slot()));
	}

	/** B596: the Pacts tab's icon reads "1 pact, 1 offer waiting" and "0 feuds", and in the plural for two. */
	//$ gametest_ticks_batch HUGE '100' '"countedWordsTabCount"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "countedWordsTabCount")
	public void theTabIconReadsRightForOnePactAndOneOffer(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), false, now, -1, false, false));
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(2), land.at(1), false, two.them().getUUID(), now));
		List<String> one = tabIcon(land, 1, two.them());
		helper.assertTrue(one.contains("1 pact, 1 offer waiting") && one.contains("0 feuds"), "one pact and one offer: " + one);
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(2), land.at(1), false, now, -1, false, false));
		List<String> mixed = tabIcon(land, 1, two.them());
		helper.assertTrue(mixed.contains("2 pacts, 1 offer waiting"), "two pacts: " + mixed);
		List<String> wrong = new ArrayList<>();
		check(wrong, Words.countedTwo("screen.aliveworkplace.pact.tab_count", 2, 1, 2, 1).getString(), "2 pacts, 1 offer waiting");
		check(wrong, Words.countedTwo("screen.aliveworkplace.pact.tab_count", 1, 3, 1, 3).getString(), "1 pact, 3 offers waiting");
		check(wrong, Words.counted("screen.aliveworkplace.feud.tab_count", 1, 1).getString(), "1 feud");
		check(wrong, Words.counted("screen.aliveworkplace.feud.tab_count", 2, 2).getString(), "2 feuds");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B596: an alliance with under an hour of notice left reads "Ends within the hour", and with 1,500 ticks "Ends in 1 hour". */
	//$ gametest_ticks_batch HUGE '100' '"countedWordsEndsInZero"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "countedWordsEndsInZero")
	public void anAllianceEndingWithinTheHourDoesntSayZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), true, 0, now + 500, false, false));
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		int line = QaPactsRealmEdictsGameTests.find(menu, "Alliance with " + land.name(1));
		helper.assertTrue(line >= 0, "no alliance line");
		List<String> lore = RealmGameTests.lore(menu.icon(line));
		helper.assertTrue(lore.contains("Ends within the hour"), "500 ticks of notice left: " + lore);
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), true, 0, now + 1500, false, false));
		menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		lore = RealmGameTests.lore(menu.icon(QaPactsRealmEdictsGameTests.find(menu, "Alliance with " + land.name(1))));
		helper.assertTrue(lore.contains("Ends in 1 hour"), "1,500 ticks of notice left: " + lore);
		helper.succeed();
	}

	/** B596: an offer with under an hour left reads "less than an hour left", not "0 days and 0 hours left". */
	//$ gametest_ticks_batch HUGE '100' '"countedWordsWaitingZero"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "countedWordsWaitingZero")
	public void anOfferLapsingWithinTheHourDoesntSayZeroDaysAndZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - (Pacts.LAPSE - 500)));
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		int accept = QaPactsRealmEdictsGameTests.find(menu, "Accept a trade pact with " + land.name(0));
		helper.assertTrue(accept >= 0, "no Accept line");
		List<String> lore = RealmGameTests.lore(menu.icon(accept));
		helper.assertTrue(lore.contains("Waiting for an answer: less than an hour left"), "500 ticks left on the offer: " + lore);
		helper.succeed();
	}
}
