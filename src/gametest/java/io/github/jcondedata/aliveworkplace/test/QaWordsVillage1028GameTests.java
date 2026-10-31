package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmInvites;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-c-1028-0353) for the counted and articled sentences fixed on 2026-10-27, read through the screens a player
 * opens, at their boundaries from both sides: B583 (the Legends page's "Born to a/an ..." for every vowel and "h" trade,
 * and a list of trades), B588 (a Legend guest's terms from the first day to past the last evening, at 11999 and 12000),
 * B592 (every shipped Wonder's goods quest asked for more than once, for 1, 2 and 21), B594 (the Classes page for 0, 1,
 * 2 and 21 households), B596 (the Pacts tab's count for every mix of 0, 1 and 2 pacts, offers and feuds; an alliance's
 * notice at 1, 999, 1000, 1999, 2000 and 21000 ticks; an offer's wait at 0, 1, 999, 1000, 24000 and 72000 ticks and once
 * lapsed; and the feud lines beside them in the hours they read right) and B597 (the Realm invitation at the same ticks).
 */
public class QaWordsVillage1028GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static void check(List<String> wrong, String what, String got, String want) {
		if (!got.equals(want)) {
			wrong.add(what + ": \"" + got + "\" (want \"" + want + "\")");
		}
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	// ---- B583: the Legends page --------------------------------------------------------------------------------------

	private static Legend bornTo(String name, String... trades) {
		StringBuilder list = new StringBuilder();
		for (String t : trades) {
			list.append(list.isEmpty() ? "" : ", ").append('"').append(t).append('"');
		}
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name), JsonParser.parseString("{\"rarity\": \"rare\","
			+ " \"job\": \"minecraft:mason\", \"title\": \"" + name + "\", \"lore\": \"l\", \"arrive\": [{\"way\": \"born\", \"trades\": [" + list + "]}]}")
			.getAsJsonObject());
	}

	/**
	 * B583: "Born to an Undertaker", "an Innkeeper", "an Orchard Keeper" (our own trades starting with a vowel), "a
	 * Habitat Keeper" (an h said aloud, when that job is registered), "a Mason"; a list takes the article of its first
	 * trade: "an Armorer or Cleric", "a Cleric or Armorer".
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaWordsVillageBorn"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaWordsVillageBorn")
	public void bornToReadsAnBeforeEveryVowelTradeAndABeforeTheRest(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> {
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Map<String, String> want = new LinkedHashMap<>();
			Map<ResourceLocation, Legend> roster = new LinkedHashMap<>();
			String[][] cases = {
				{"qa_w_undertaker", "Born to an Undertaker", "aliveworkplace:undertaker"},
				{"qa_w_innkeeper", "Born to an Innkeeper", "aliveworkplace:innkeeper"},
				{"qa_w_orchard", "Born to an Orchard Keeper", "aliveworkplace:orchard_keeper"},
				{"qa_w_mason", "Born to a Mason", "minecraft:mason"},
				{"qa_w_pair", "Born to an Armorer or Cleric", "minecraft:armorer", "minecraft:cleric"},
				{"qa_w_pair_back", "Born to a Cleric or Armorer", "minecraft:cleric", "minecraft:armorer"},
				{"qa_w_three", "Born to an Undertaker, Mason or Cleric", "aliveworkplace:undertaker", "minecraft:mason", "minecraft:cleric"},
				{"qa_w_habitat", "Born to a Habitat Keeper", "aliveworkplace:habitat_keeper"}};
			for (String[] c : cases) {
				boolean registered = true;
				for (int i = 2; i < c.length; i++) {
					registered &= BuiltInRegistries.VILLAGER_PROFESSION.containsKey(ResourceLocation.parse(c[i]));
				}
				if (!registered) {
					continue; // the Habitat Keeper is a Cobblemon job
				}
				String[] trades = java.util.Arrays.copyOfRange(c, 2, c.length);
				Legend legend = bornTo(c[0], trades);
				roster.put(legend.id(), legend);
				want.put(c[0], "  • " + c[1]);
			}
			helper.assertTrue(want.size() >= 7, "setup: our own vowel trades aren't registered: " + want.keySet());
			Legends.setForTest(roster);
			List<ItemStack> cards = LegendsPage.cards(level, hall);
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<String, String> e : want.entrySet()) {
				ItemStack card = cards.stream().filter(c -> name(c).equals(e.getKey())).findFirst().orElse(ItemStack.EMPTY);
				List<String> lines = lore(card);
				if (!lines.contains(e.getValue())) {
					wrong.add(e.getKey() + " wants \"" + e.getValue().strip() + "\": " + lines.stream().filter(l -> l.contains("Born")).toList());
				}
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	// ---- B588: a Legend guest's terms ----------------------------------------------------------------------------------

	/**
	 * B588 from both sides of each boundary: a guest who came on day 1 (last day 3) says "3 days left" on day 1, "2 days
	 * left" on day 2 and "My last day here" on day 3, naming day 3 until the last tick before the evening (11999); from
	 * 12000 and on the day's last tick they leave "once no one is near"; on days 4 and 5 their stay is over.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaWordsVillageGuestTerms"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaWordsVillageGuestTerms")
	public void aGuestsTermsReadRightOnEveryDayAndAtTheEveningsEdge(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		QaLegendGuestsGameTests.setUp(helper, recorded);
		Legend plain = QaLegendGuestsGameTests.rare("qa_words_guest", "", "");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> QaLegendGuestsGameTests.staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(QaLegendGuestsGameTests.HALL);
			QaLegendGuestsGameTests.at(level, 1, 1000);
			Villager guest = LegendGuests.come(level, hall, plain, "hall", hall, RandomSource.create(44L));
			helper.assertTrue(guest != null, "no guest came");
			player.teleportTo(guest.getX() + 2, guest.getY(), guest.getZ());
			Object[][] cases = {
				{1L, 1000L, "3 days left of my stay", "I leave on the evening of day 3"},
				{1L, 23999L, "3 days left of my stay", "I leave on the evening of day 3"},
				{2L, 0L, "2 days left of my stay", "I leave on the evening of day 3"},
				{2L, 13000L, "2 days left of my stay", "I leave on the evening of day 3"},
				{3L, 0L, "My last day here", "I leave on the evening of day 3"},
				{3L, 11999L, "My last day here", "I leave on the evening of day 3"},
				{3L, 12000L, "My last day here", "I leave once no one is near"},
				{3L, 23999L, "My last day here", "I leave once no one is near"},
				{4L, 0L, "My stay is over", "I leave once no one is near"},
				{5L, 6000L, "My stay is over", "I leave once no one is near"}};
			List<String> wrong = new ArrayList<>();
			for (Object[] c : cases) {
				QaLegendGuestsGameTests.at(level, (Long) c[0], (Long) c[1]);
				ChoiceMenu menu = LegendGuests.termsMenuForTest(player, guest);
				String what = "day " + c[0] + " at " + c[1];
				check(wrong, what + " title", QaLegendGuestsGameTests.name(menu, LegendGuests.DAYS_SLOT), (String) c[2]);
				check(wrong, what + " lore", String.join(" / ", QaLegendGuestsGameTests.lore(menu, LegendGuests.DAYS_SLOT)), (String) c[3]);
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		}));
	}

	// ---- B592: Wonder goods quests -------------------------------------------------------------------------------------

	/**
	 * B592 over the whole shipped roster: every Wonder good asked for more than once reads in the plural on its quest
	 * line ("Bring 2 Emerald Brooches"), never as "Bring 2 Emerald Brooch", for 2 and for 21; and in the singular for 1.
	 * A name already plural ("Noble Robes") reads the same either way.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyWonderGoodAskedForMoreThanOnceReadsPlural(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int seen = 0;
		for (Wonder wonder : Wonders.all()) {
			if (!wonder.id().getNamespace().equals("aliveworkplace")) {
				continue; // the shipped Wonders only, not the test pack's
			}
			for (Wonder.Stage stage : wonder.stages()) {
				for (Wonder.Good good : stage.goods()) {
					if (good.item().isEmpty() || good.count() < 2 || !BuiltInRegistries.ITEM.containsKey(good.item().get())) {
						continue;
					}
					String id = good.item().get().toString();
					String singular = BuiltInRegistries.ITEM.get(good.item().get()).getDescription().getString();
					seen++;
					String one = new Objectives.Bring(id, 1, "station", Optional.of(BlockPos.ZERO)).line().getString();
					check(wrong, wonder.id().getPath() + " " + id + " x1", one, "Bring 1 " + singular);
					for (int n : new int[]{good.count(), 2, 21}) {
						String many = new Objectives.Bring(id, n, "station", Optional.of(BlockPos.ZERO)).line().getString();
						if (many.equals("Bring " + n + " " + singular) && !singular.endsWith("s")) {
							wrong.add(wonder.id().getPath() + " " + id + " x" + n + ": \"" + many + "\" reads in the singular");
						}
					}
				}
			}
		}
		helper.assertTrue(seen >= 20, "setup: only " + seen + " Wonder goods asked for more than once");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- B594: the Classes page ----------------------------------------------------------------------------------------

	private static String countedLine(SocialClass c, ClassNeeds.Need need, int of) {
		ClassesPage.NeedCount count = new ClassesPage.NeedCount(need, false, of, of);
		ItemStack button = ClassesPage.classButton(new ClassesPage.Row(c, of, List.of(count), List.of()));
		return lore(button).stream().filter(l -> l.startsWith("Counted over")).findFirst().orElse("(none)");
	}

	/** B594 around one: 0, 2 and 21 households read in the plural, with and without a class below; 1 for the top class too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void countedOverReadsPluralForZeroTwoAndTwentyOne(GameTestHelper helper) {
		SocialClass peasant = SocialClasses.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "peasant"));
		SocialClass artisan = SocialClasses.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "artisan"));
		SocialClass burgher = SocialClasses.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "burgher"));
		helper.assertTrue(peasant != null && artisan != null && burgher != null && SocialClasses.step(burgher, -1) == artisan,
			"setup: the shipped ladder isn't Peasant, Artisan, Burgher");
		ClassNeeds.Need need = artisan.needs().get(0);
		List<String> wrong = new ArrayList<>();
		for (int n : new int[]{0, 2, 21}) {
			check(wrong, "artisan " + n, countedLine(artisan, need, n), "Counted over the " + n + " households who are Artisan or Peasant");
			check(wrong, "peasant " + n, countedLine(peasant, need, n), "Counted over its " + n + " households");
		}
		check(wrong, "burgher 1", countedLine(burgher, burgher.needs().get(0), 1), "Counted over the 1 household who is Burgher or Artisan");
		check(wrong, "burgher 21", countedLine(burgher, burgher.needs().get(0), 21), "Counted over the 21 households who are Burgher or Artisan");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- B596: the Pacts tab -------------------------------------------------------------------------------------------

	/** Feuds and wars on, PvP on and no feuds; put back after. */
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

	private static List<String> tabIcon(Land land, int hall, ServerPlayer player) {
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, hall, player, TradePage.Tab.PACTS);
		return RealmGameTests.lore(menu.icon(TradePage.Tab.PACTS.slot()));
	}

	private static void resetPacts(MinecraftServer server) {
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
	}

	/**
	 * B596, the tab's icon for every mix of none, one and two: "0 pacts, 0 offers waiting", "1 pact, 0 offers waiting",
	 * "0 pacts, 1 offer waiting", "2 pacts, 2 offers waiting"; and "0 feuds", "1 feud", "2 feuds" under it.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageTabCount"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageTabCount")
	public void theTabCountReadsRightForNoneOneAndTwo(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		long now = server.overworld().getGameTime();
		UUID them = two.them().getUUID();
		List<String> wrong = new ArrayList<>();
		java.util.function.BiConsumer<String, String> expect = (what, line) -> {
			List<String> icon = tabIcon(land, 1, two.them());
			if (!icon.contains(line)) {
				wrong.add(what + ": " + icon + " (want \"" + line + "\")");
			}
		};
		expect.accept("nothing", "0 pacts, 0 offers waiting");
		expect.accept("no feuds", "0 feuds");
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), false, now, -1, false, false));
		expect.accept("one pact", "1 pact, 0 offers waiting");
		resetPacts(server);
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, them, now));
		expect.accept("one offer", "0 pacts, 1 offer waiting");
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(2), land.at(1), true, them, now));
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), false, now, -1, false, false));
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(2), land.at(1), true, now, -1, false, false));
		expect.accept("two and two", "2 pacts, 2 offers waiting");
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now));
		expect.accept("one feud", "1 feud");
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(2), land.at(1), now));
		expect.accept("two feuds", "2 feuds");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B596 at the hour's edges: an alliance's notice with 1 or 999 ticks left reads "Ends within the hour", 1000 and 1999
	 * "Ends in 1 hour", 2000 "Ends in 2 hours", 21000 "Ends in 21 hours".
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageEnding"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageEnding")
	public void anAlliancesNoticeReadsRightAtEachHoursEdge(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long[] lefts = {1, 999, 1000, 1999, 2000, 21000};
		String[] want = {"Ends within the hour", "Ends within the hour", "Ends in 1 hour", "Ends in 1 hour", "Ends in 2 hours", "Ends in 21 hours"};
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < lefts.length; i++) {
			long now = server.overworld().getGameTime();
			Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), true, 0, now + lefts[i], false, false));
			ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
			int line = QaPactsRealmEdictsGameTests.find(menu, "Alliance with " + land.name(1));
			List<String> lore = line < 0 ? List.of("(no alliance line)") : RealmGameTests.lore(menu.icon(line));
			if (!lore.contains(want[i])) {
				wrong.add(lefts[i] + " ticks: " + lore + " (want \"" + want[i] + "\")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B596 for an offer's wait, from just sent to lapsed: 72000 ticks left "3 days and 0 hours", 24000 "1 day and 0
	 * hours", 1000 "0 days and 1 hour", 999, 1 and 0 (the lapse tick itself, still shown) "less than an hour left"; one
	 * tick past the lapse the offer is gone from the tab.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageWaiting"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageWaiting")
	public void anOffersWaitReadsRightFromSentToLapsed(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		long[] lefts = {Pacts.LAPSE, 24000, 1000, 999, 1, 0};
		String[] want = {"3 days and 0 hours left", "1 day and 0 hours left", "0 days and 1 hour left", "less than an hour left",
			"less than an hour left", "less than an hour left"};
		String accept = "Accept a trade pact with " + land.name(0);
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < lefts.length; i++) {
			resetPacts(server);
			long now = server.overworld().getGameTime();
			Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - (Pacts.LAPSE - lefts[i])));
			ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
			int slot = QaPactsRealmEdictsGameTests.find(menu, accept);
			List<String> lore = slot < 0 ? List.of("(no Accept line)") : RealmGameTests.lore(menu.icon(slot));
			if (!lore.contains("Waiting for an answer: " + want[i])) {
				wrong.add(lefts[i] + " ticks: " + lore + " (want \"" + want[i] + "\")");
			}
		}
		resetPacts(server);
		long now = server.overworld().getGameTime();
		Pacts.Data.get(server).addOffer(new Pacts.Offer(land.at(0), land.at(1), false, two.them().getUUID(), now - Pacts.LAPSE - 1));
		ChoiceMenu lapsed = QaPactsRealmEdictsGameTests.tab(land, 1, two.them(), TradePage.Tab.PACTS);
		if (QaPactsRealmEdictsGameTests.find(lapsed, accept) >= 0) {
			wrong.add("a lapsed offer is still on the tab: " + RealmGameTests.lore(lapsed.icon(QaPactsRealmEdictsGameTests.find(lapsed, accept))));
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static List<String> feudLore(Land land) {
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().startsWith("Feud with")) {
				return RealmGameTests.lore(menu.icon(slot));
			}
		}
		return List.of("(no feud line)");
	}

	/**
	 * The feud lines beside B596's, in the hours where they read right: a war with 3 days, 1 day 1 hour and 1 hour left,
	 * and a feud fading in 7 days, 1 day 1 hour and 1 hour (singular and plural each side).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageFeudHours"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageFeudHours")
	public void theFeudsWarAndFadeLinesReadRightInTheirHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		long[] war = {3 * 24000L, 25000, 1000};
		String[] warWant = {"At war: 3 days and 0 hours left", "At war: 1 day and 1 hour left", "At war: 0 days and 1 hour left"};
		for (int i = 0; i < war.length; i++) {
			long now = server.overworld().getGameTime();
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now, now + war[i], false, 0, 0, 0));
			List<String> lore = feudLore(land);
			if (!lore.contains(warWant[i])) {
				wrong.add("war, " + war[i] + " ticks: " + lore + " (want \"" + warWant[i] + "\")");
			}
		}
		Feuds.Data.get(server).clear();
		long[] fade = {Feuds.FADE, 25000, 1000};
		String[] fadeWant = {"Fades in 7 days and 0 hours without a war", "Fades in 1 day and 1 hour without a war",
			"Fades in 0 days and 1 hour without a war"};
		for (int i = 0; i < fade.length; i++) {
			long now = server.overworld().getGameTime();
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - fade[i])));
			List<String> lore = feudLore(land);
			if (!lore.contains(fadeWant[i])) {
				wrong.add("fading, " + fade[i] + " ticks: " + lore + " (want \"" + fadeWant[i] + "\")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- B597: the Realm tab's invitation ------------------------------------------------------------------------------

	/**
	 * B597 at the edges the earlier test leaves: 72000 ticks left "3 days and 0 hours", 24000 "1 day and 0 hours", 1000
	 * "0 days and 1 hour", 999, 1 and 0 (the lapse tick, still open) "less than an hour left", on the Join button and the
	 * capital's invited line alike.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageInvite"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageInvite")
	public void theInvitationReadsRightAtEachEdge(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = RealmGameTests.listener(helper, theirs, "qa-words-neighbour");
		land.entity(1).setOwner(them.getUUID(), "qa-words-neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		RealmGameTests.found(helper, land);
		UUID id = land.data().realmOf(land.at(0)).id();
		helper.assertTrue(RealmInvites.invite(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "inviting");
		long[] lefts = {RealmInvites.LAPSE, 24000, 1000, 999, 1, 0};
		String[] want = {"3 days and 0 hours left", "1 day and 0 hours left", "0 days and 1 hour left", "less than an hour left",
			"less than an hour left", "less than an hour left"};
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < lefts.length; i++) {
			long now = server.overworld().getGameTime();
			land.data().putInvite(new RealmData.Invite(id, land.at(1), them.getUUID(), now - (RealmInvites.LAPSE - lefts[i])));
			String line = "Waiting for an answer: " + want[i];
			List<String> join = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 1, them).icon(Realms.INVITES));
			if (!join.contains(line)) {
				wrong.add("Join, " + lefts[i] + " ticks: " + join + " (want \"" + line + "\")");
			}
			List<String> sent = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(Realms.MEMBERS + 1));
			if (!sent.contains(line)) {
				wrong.add("invited, " + lefts[i] + " ticks: " + sent + " (want \"" + line + "\")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
