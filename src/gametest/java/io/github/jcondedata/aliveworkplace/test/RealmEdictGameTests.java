package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.CapitalVotes;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmEdicts;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.trade.Board;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Realm edicts (ROADMAP 33.22): the capital's Book of Edicts shows the realm's slots (a Barony's one, the rest locked
 * with what opens them) and proclaims by two clicks; a member's book shows them to read; and for each of the six edicts
 * its boost and its cost in the system it changes, then its reform (the capital's quest and the count across the realm)
 * turning the cost off for good while the boost stays. Also: save and reload, a new capital keeps the edicts, and the
 * switches ({@code realms}, {@code villageEdicts}) off. Each test is alone in its batch.
 */
public class RealmEdictGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	static Edicts.Edict edict(String id) {
		return Edicts.find(id).orElseThrow(() -> new IllegalStateException("no realm edict " + id));
	}

	/** A Town capital (hall 0) with {@code members} more halls joined, of the given ranks, and the rest outside the realm. */
	static RealmGameTests.Land realm(GameTestHelper helper, int members, VillageRanks.Rank... ranks) {
		RealmGameTests.Land land = RealmGameTests.land(helper, ranks);
		RealmGameTests.found(helper, land);
		for (int i = 1; i <= members; i++) {
			helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(i), land.owner()).ok(), "hall " + i + " didn't join");
		}
		boolean edicts = Edicts.ENABLED;
		RealmEdicts.forget();
		Leftovers.after(helper, () -> {
			Edicts.setEnabled(edicts);
			RealmEdicts.forget();
			Caravans.Data data = Caravans.Data.get(land.level());
			for (BlockPos hall : land.halls()) {
				data.remove(hall);
			}
		});
		return land;
	}

	static RealmData.Realm theRealm(RealmGameTests.Land land) {
		return land.data().realmOf(land.at(0));
	}

	static void treasury(RealmGameTests.Land land, long cents) {
		RealmData.Realm realm = theRealm(land);
		land.data().putRealm(realm.withTreasury(cents, realm.ledger()));
	}

	static void proclaim(GameTestHelper helper, RealmGameTests.Land land, String id) {
		Edicts.Result result = RealmEdicts.proclaim(land.level(), land.hall(0), land.owner(), edict(id));
		helper.assertTrue(result.done(), "couldn't proclaim " + id + ": " + result.message().getString());
	}

	static String full(String id) {
		return edict(id).id().toString();
	}

	static boolean reformed(RealmGameTests.Land land, String id) {
		return RealmEdicts.reformed(land.level().getServer(), theRealm(land), full(id));
	}

	static long today(RealmGameTests.Land land) {
		return Chronicle.day(land.level().getServer().overworld());
	}

	/** The next morning (the dawn round itself is called by the tests). */
	static void nextDay(RealmGameTests.Land land, int days) {
		ServerLevel level = land.level();
		level.setDayTime(level.getDayTime() + 24000L * days);
	}

	/** The realm reform step of {@code id} up at the capital now, or null. */
	static VillageQuests.Quest step(RealmGameTests.Land land, String id) {
		RealmEdicts.round(land.level(), land.hall(0), land.entity(0));
		return land.entity(0).quests().stream().filter(q -> q.reform().map(r -> r.edict().equals(full(id))).orElse(false)).findFirst().orElse(null);
	}

	/** The owner brings {@code count} of {@code item} to the realm step of {@code id} at the capital, as from its quest page. */
	static void bring(GameTestHelper helper, RealmGameTests.Land land, String id, Item item, int count) {
		VillageQuests.Quest quest = step(land, id);
		helper.assertTrue(quest != null && quest.kind() == VillageQuests.Kind.BRING && quest.itemType() == item && quest.count() == count,
			"the step of " + id + ": " + quest);
		land.owner().getInventory().add(new ItemStack(item, count));
		helper.assertTrue(VillageQuests.handIn(land.owner(), land.hall(0), quest.id()) == count, "handed in");
	}

	static boolean heard(RealmGameTests.Land land, String text) {
		return land.heard().stream().anyMatch(c -> c.getString().contains(text));
	}

	static List<String> lore(ItemStack stack) {
		return RealmGameTests.lore(stack);
	}

	static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	static int find(ChoiceMenu menu, String name) {
		for (int s = EdictBook.FIRST_EDICT; s < EdictBook.END_EDICTS; s++) {
			if (menu.icon(s).getHoverName().getString().equals(name)) {
				return s;
			}
		}
		return -1;
	}

	/** A hall's next day's takings from ten workers, well kept; returns what its treasury gained. */
	static int aDay(RealmGameTests.Land land, int i) {
		VillageHallBlockEntity entity = land.entity(i);
		entity.setNeeds(new VillageNeeds.Needs(10, 10, 10, 10, 10, 10, 10, 1f));
		entity.setLastTaxDay(Chronicle.day(land.level()) - 1);
		int before = entity.treasury();
		Treasury.round(land.level(), land.hall(i), entity, 10);
		return entity.treasury() - before;
	}

	// ---- the Book ---------------------------------------------------------------------------------------------------

	/**
	 * The capital's Book: a Barony's one realm slot and three locked ones, the six realm edicts listed after the
	 * village's; two clicks proclaim, the second edict finds no slot; the member's book shows the slot to read and lists no
	 * realm edicts; a village can't proclaim one; after three days a click lifts it.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsBook"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsBook")
	public void theCapitalsBookShowsRealmSlots(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		String title = Realms.title(server, theRealm(land)).getString();
		List<String> six = Edicts.realm().stream().map(e -> e.name().getString()).toList();
		helper.assertTrue(six.equals(List.of("Royal Highway", "Common Market", "Levy of Arms", "Realm Festival", "Crown's Peace", "Royal Charter")), "the realm edicts: " + six);
		helper.assertTrue(Edicts.all().stream().noneMatch(Edicts.Edict::realm), "a realm edict among the village's");
		helper.assertTrue(RealmEdicts.slots(Realms.Rank.BARONY, false) == 1 && RealmEdicts.slots(Realms.Rank.COUNTY, false) == 1
			&& RealmEdicts.slots(Realms.Rank.DUCHY, false) == 2 && RealmEdicts.slots(Realms.Rank.KINGDOM, false) == 3
			&& RealmEdicts.slots(Realms.Rank.KINGDOM, true) == 4 && RealmEdicts.slots(Realms.Rank.BARONY, true) == 2, "slots by rank");
		ChoiceMenu menu = EdictBook.forTest(land.owner(), land.hall(0));
		helper.assertTrue(lore(menu.icon(EdictBook.HEADER)).contains("Realm edicts: 0 in force of 1 edict (" + title + ")"), "header: " + lore(menu.icon(EdictBook.HEADER)));
		ItemStack free = menu.icon(RealmEdicts.SLOTS[0]);
		helper.assertTrue(free.is(Items.MAP) && free.getHoverName().getString().equals("A free realm slot"), "the Barony's slot: " + free);
		ItemStack locked = menu.icon(RealmEdicts.SLOTS[1]);
		helper.assertTrue(locked.is(Items.GRAY_DYE) && lore(locked).contains("Opens when the realm is a Duchy, or with the Royal Hall II"), "the second slot: " + lore(locked));
		helper.assertTrue(lore(menu.icon(RealmEdicts.SLOTS[3])).contains("Opens when the realm is a Kingdom with the Royal Hall II"), "the fourth slot");
		int highway = find(menu, "Royal Highway");
		helper.assertTrue(highway > find(menu, "Long Shifts") && find(menu, "Royal Charter") > highway, "the realm edicts come after the village's");
		helper.assertTrue(lore(menu.icon(highway)).contains("+ caravans between members carry 2 more stacks")
			&& lore(menu.icon(highway)).contains("- the realm treasury pays 1 emerald a day for each route between members")
			&& lore(menu.icon(highway)).contains("Across the realm: 0 of 12 caravans between members"), "Royal Highway's lore: " + lore(menu.icon(highway)));
		click(menu, highway, land.owner());
		helper.assertTrue(RealmEdicts.inForce(server, theRealm(land)).isEmpty(), "one click proclaimed it");
		click(menu, highway, land.owner());
		ItemStack held = menu.icon(RealmEdicts.SLOTS[0]);
		helper.assertTrue(held.is(Items.STONE_BRICKS) && held.getHoverName().getString().equals("Royal Highway")
			&& lore(held).contains("In force in every village of " + title), "the slot after two clicks: " + held + " " + lore(held));
		helper.assertTrue(heard(land, title + " proclaims the realm edict Royal Highway."), "the owner wasn't told: " + land.heard());
		helper.assertTrue(land.entity(1).chronicle().stream().anyMatch(e -> e.text().getString().equals("The realm edict Royal Highway was proclaimed")), "the member's chronicle");
		int market = find(menu, "Common Market");
		click(menu, market, land.owner());
		click(menu, market, land.owner());
		helper.assertTrue(heard(land, "has no free realm slot: a Barony may keep 1 edict in force"), "no slot refusal: " + land.heard());
		// the member's book: the slot to read, no realm edicts in the list
		ChoiceMenu member = EdictBook.forTest(land.owner(), land.hall(1));
		helper.assertTrue(member.icon(RealmEdicts.SLOTS[0]).is(Items.STONE_BRICKS) && lore(member.icon(RealmEdicts.SLOTS[0])).contains("In force in every village of " + title),
			"the member's slot: " + lore(member.icon(RealmEdicts.SLOTS[0])));
		helper.assertTrue(lore(member.icon(EdictBook.HEADER)).contains("Realm edicts from the capital: 1 in force of 1 edict (" + title + ")"), "the member's header");
		helper.assertTrue(find(member, "Royal Highway") < 0 && find(member, "Common Market") < 0, "realm edicts in a member's list");
		Edicts.Result village = Edicts.proclaim(level, land.hall(1), null, edict("common_market"));
		helper.assertTrue(!village.done() && village.message().getString().equals("Common Market is a realm edict: the capital proclaims it in its Book of Edicts."),
			"a village proclaimed a realm edict: " + village.message().getString());
		// lifted only after three days
		click(menu, RealmEdicts.SLOTS[0], land.owner());
		helper.assertTrue(RealmEdicts.inForce(server, theRealm(land)).size() == 1, "lifted on its first day");
		nextDay(land, 3);
		menu = EdictBook.forTest(land.owner(), land.hall(0));
		click(menu, RealmEdicts.SLOTS[0], land.owner());
		helper.assertTrue(RealmEdicts.inForce(server, theRealm(land)).isEmpty() && menu.icon(RealmEdicts.SLOTS[0]).is(Items.MAP), "not lifted after three days");
		helper.succeed();
	}

	// ---- the six --------------------------------------------------------------------------------------------------

	/**
	 * Royal Highway: caravans between members arrive 25% sooner with 2 more stacks; the realm pays an emerald a day for
	 * the route, and an empty treasury lets it lapse. Reformed (128 stone bricks and 12 caravans), the upkeep goes and the
	 * speed stays.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsHighway"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsHighway")
	public void royalHighway(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data data = Caravans.Data.get(level);
		long base = Caravans.trip(level, land.hall(0), land.hall(1), data);
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(0), land.hall(1)) == 0, "stacks before");
		proclaim(helper, land, "royal_highway");
		helper.assertTrue(Caravans.trip(level, land.hall(0), land.hall(1), data) == base * 75 / 100, "trip: " + base + " -> " + Caravans.trip(level, land.hall(0), land.hall(1), data));
		helper.assertTrue(RealmEdicts.extraStacks(level, land.hall(1), land.hall(0)) == 2 && RealmEdicts.extraStacks(level, land.hall(0), land.hall(2)) == 0,
			"2 more stacks between members only");
		helper.assertTrue(Caravans.trip(level, land.hall(0), land.hall(2), data) == Caravans.travelTicks(Math.sqrt(land.hall(0).distSqr(land.hall(2))), false), "a non-member's trip");
		// the cost: an emerald a day for the route
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)), "no route");
		treasury(land, 500);
		helper.assertTrue(RealmEdicts.dawn(server, today(land) + 1) == 100 && theRealm(land).treasury() == 400, "upkeep: " + theRealm(land).treasury());
		helper.assertTrue(theRealm(land).ledger().get(theRealm(land).ledger().size() - 1).reason().getString().equals("Upkeep of Royal Highway"), "the ledger");
		treasury(land, 0);
		RealmEdicts.dawn(server, today(land) + 2);
		helper.assertTrue(RealmEdicts.inForce(server, theRealm(land)).isEmpty(), "still in force with an empty treasury");
		helper.assertTrue(heard(land, "lost the realm edict Royal Highway: the realm treasury hadn't the 1.00 emeralds of its upkeep"), "the lapse: " + land.heard());
		proclaim(helper, land, "royal_highway");
		// the reform: the capital's quest, then 12 caravans between members
		bring(helper, land, "royal_highway", Items.STONE_BRICKS, 128);
		helper.assertTrue(!reformed(land, "royal_highway") && heard(land, "now the realm: 0 of 12 caravans between members"), "reformed before the count: " + land.heard());
		RealmEdicts.caravanSent(level, land.hall(0), land.hall(2));
		helper.assertTrue(RealmEdicts.count(server, theRealm(land), full("royal_highway")) == 0, "a caravan to a non-member counted");
		for (int i = 0; i < 11; i++) {
			RealmEdicts.caravanSent(level, land.hall(i % 2), land.hall(1 - i % 2));
		}
		helper.assertTrue(!reformed(land, "royal_highway"), "reformed at 11");
		RealmEdicts.caravanSent(level, land.hall(1), land.hall(0));
		helper.assertTrue(reformed(land, "royal_highway"), "not reformed at 12");
		helper.assertTrue(heard(land, "has reformed the realm edict Royal Highway with Pave the highway"), "the reform wasn't told");
		RealmEdicts.dawn(server, today(land) + 3);
		helper.assertTrue(RealmEdicts.inForce(server, theRealm(land)).size() == 1 && theRealm(land).treasury() == 0, "the upkeep after the reform");
		helper.assertTrue(Caravans.trip(level, land.hall(0), land.hall(1), data) == base * 75 / 100 && RealmEdicts.extraStacks(level, land.hall(0), land.hall(1)) == 2,
			"the boost went with the cost");
		helper.succeed();
	}

	/**
	 * Common Market: every member's board pays the members' average price, and the realm's people buy without the
	 * spread (a stranger pays it); the levy rises 5 points. Reformed (8 gold, 16 paper and 20 board trades), the levy is
	 * back to 10 and the board stays common.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsMarket"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsMarket")
	public void commonMarket(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "market-stranger");
		TradeGoods.Good good = TradeGoods.all().get(0);
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		data.setMarket(land.hall(0), new Market(List.of(), List.of(), Map.of(good.id(), new Market.Price(100, 100, 0)), Chronicle.day(level), List.of()));
		data.setMarket(land.hall(1), new Market(List.of(), List.of(), Map.of(good.id(), new Market.Price(200, 200, 0)), Chronicle.day(level), List.of()));
		helper.assertTrue(Board.price(level, land.hall(0), good) == 100 && RealmTreasury.levy(server, theRealm(land)) == 10, "before");
		proclaim(helper, land, "common_market");
		helper.assertTrue(Board.price(level, land.hall(0), good) == 150 && Board.price(level, land.hall(1), good) == 150, "the common price: "
			+ Board.price(level, land.hall(0), good) + " / " + Board.price(level, land.hall(1), good));
		helper.assertTrue(TradePage.selling(level, land.hall(1), 150, land.owner()) == 150 && TradePage.selling(level, land.hall(1), 150, stranger) == 165,
			"the spread: " + TradePage.selling(level, land.hall(1), 150, land.owner()) + " / " + TradePage.selling(level, land.hall(1), 150, stranger));
		List<String> icon = lore(TradePage.goodIcon(level, land.hall(1), good, List.of(), land.owner(), null));
		helper.assertTrue(icon.contains("Common Market: the realm's average price") && icon.contains("We sell: 1.5 emeralds"), "the good's tooltip: " + icon);
		helper.assertTrue(RealmTreasury.levy(server, theRealm(land)) == 15, "the levy: " + RealmTreasury.levy(server, theRealm(land)));
		int kept = aDay(land, 1);
		helper.assertTrue(theRealm(land).treasury() == 45 && kept == 255, "a day at 15%: " + theRealm(land).treasury() + " / " + kept);
		// the reform: two steps a day apart, then 20 board trades in members
		bring(helper, land, "common_market", Items.GOLD_INGOT, 8);
		helper.assertTrue(step(land, "common_market") == null, "the second step went up the same day");
		nextDay(land, 1);
		bring(helper, land, "common_market", Items.PAPER, 16);
		for (int i = 0; i < 19; i++) {
			RealmEdicts.boardTrade(level, land.hall(i % 2));
		}
		helper.assertTrue(!reformed(land, "common_market"), "reformed at 19 trades");
		RealmEdicts.boardTrade(level, land.hall(1));
		helper.assertTrue(reformed(land, "common_market"), "not reformed at 20 trades");
		helper.assertTrue(RealmTreasury.levy(server, theRealm(land)) == 10, "the levy after the reform: " + RealmTreasury.levy(server, theRealm(land)));
		helper.assertTrue(Board.price(level, land.hall(0), good) == 150 && TradePage.selling(level, land.hall(1), 150, land.owner()) == 150, "the boost went");
		helper.succeed();
	}

	/**
	 * Levy of Arms: a raid brings bands twice as big (the capital's 2, a member's 4); each member pays 2 emeralds a day
	 * into the realm treasury. Reformed (12 iron swords and 3 raids beaten with relief, counted as each raid ends), the
	 * dues go and the bands stay big.
	 */
	//$ gametest_ticks_batch AREA '200' '"realmEdictsLevy"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "realmEdictsLevy")
	public void levyOfArms(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		RealmEdicts.forget();
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		ReliefGameTests.guards(land, 2, 6).forEach(g -> g.setNoAi(true));
		treasury(land, 5000);
		proclaim(helper, land, "levy_of_arms");
		helper.assertTrue(RealmEdicts.reliefReach(server, theRealm(land)) && RealmEdicts.reliefFactor(server, theRealm(land)) == 2, "the reach and the factor");
		int riders = Relief.call(level, land.hall(1));
		List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(riders == 6 && bands.size() == 2 && bands.stream().anyMatch(b -> b.capital() && b.riders() == 2)
			&& bands.stream().anyMatch(b -> !b.capital() && b.riders() == 4), "bands twice as big: " + riders + " " + bands);
		// the dues
		land.entity(1).setTreasury(1000);
		long before = theRealm(land).treasury();
		int gained = aDay(land, 1);
		helper.assertTrue(gained == 270 - 200 && theRealm(land).treasury() == before + 30 + 200, "the dues: " + gained + ", realm " + (theRealm(land).treasury() - before));
		helper.assertTrue(theRealm(land).ledger().stream().anyMatch(m -> m.reason().getString().equals("Dues from " + land.name(1) + " (Levy of Arms)")), "the ledger");
		bring(helper, land, "levy_of_arms", Items.IRON_SWORD, 12);
		helper.assertTrue(!reformed(land, "levy_of_arms"), "reformed before any raid was beaten");
		int[] seen = {0};
		helper.runAfterDelay(30, () -> {
			seen[0] = RealmEdicts.count(server, theRealm(land), full("levy_of_arms"));
			helper.assertTrue(seen[0] == 1, "the first raid's end wasn't counted: " + seen[0]);
			helper.assertTrue(Relief.call(level, land.hall(1)) > 0, "no relief for the second raid");
		});
		helper.runAfterDelay(60, () -> helper.assertTrue(Relief.call(level, land.hall(1)) > 0 && !reformed(land, "levy_of_arms"), "the third raid"));
		helper.runAfterDelay(90, () -> {
			helper.assertTrue(reformed(land, "levy_of_arms"), "not reformed after 3 raids: " + RealmEdicts.count(server, theRealm(land), full("levy_of_arms")));
			land.entity(1).setTreasury(1000);
			int next = aDay(land, 1);
			helper.assertTrue(next == 270, "dues after the reform: " + next);
			helper.assertTrue(RealmEdicts.reliefFactor(server, theRealm(land)) == 2 && RealmEdicts.reliefReach(server, theRealm(land)), "the boost went");
			helper.succeed();
		});
	}

	/**
	 * Realm Festival: the member holds its festival on the capital's day and the realm treasury pays 10 emeralds for it
	 * (none, and the village disappointed, when it can't); the good mood lasts 3 days. Reformed (32 lanterns and 3 realm
	 * festivals), the festival costs the realm nothing and the mood still lasts.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsFestival"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsFestival")
	public void realmFestival(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true;
		Leftovers.after(helper, () -> Festivals.ENABLED = festivals);
		Villager guest = EntityType.VILLAGER.create(level);
		BlockPos at = land.hall(1).offset(1, 0, 2);
		guest.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		guest.setNoAi(true);
		level.addFreshEntity(guest);
		long t = today(land) + 1;
		while (Math.floorMod(t - Festivals.offset(land.hall(0)), Festivals.EVERY_DAYS) != 0) {
			t++;
		}
		ModAttachments.FESTIVAL_DAY.set(guest, t - 3);
		level.setDayTime((t - 1) * 24000L + 1000);
		helper.assertTrue(!Festivals.enjoyedLately(level, guest), "a festival 3 days ago still cheers");
		proclaim(helper, land, "realm_festival");
		helper.assertTrue(Festivals.offset(level, land.hall(1)) == Festivals.offset(land.hall(0)), "the member keeps its own day");
		helper.assertTrue(Festivals.enjoyedLately(level, guest), "the good mood didn't last 3 days");
		// no money: no festival
		treasury(land, 0);
		Festivals.round(level, land.hall(1), land.entity(1), 8);
		helper.assertTrue(land.entity(1).festivalMissed() == t && land.entity(1).festivalDay() != t, "a festival the realm couldn't pay for");
		// eight days on, paid
		t += Festivals.EVERY_DAYS;
		level.setDayTime((t - 1) * 24000L + 1000);
		treasury(land, 5000);
		Festivals.round(level, land.hall(1), land.entity(1), 8);
		helper.assertTrue(land.entity(1).festivalDay() == t && theRealm(land).treasury() == 4000, "the realm's share: " + theRealm(land).treasury());
		helper.assertTrue(theRealm(land).ledger().stream().anyMatch(m -> m.reason().getString().equals("Festival in " + land.name(1) + " (Realm Festival)")), "the ledger");
		// the reform: 32 lanterns and three realm festivals
		bring(helper, land, "realm_festival", Items.LANTERN, 32);
		Festivals.feast(level, land.hall(1));
		Festivals.feast(level, land.hall(0)); // the same day: one realm festival
		helper.assertTrue(RealmEdicts.count(server, theRealm(land), full("realm_festival")) == 1, "one day's festivals counted twice");
		nextDay(land, 1);
		Festivals.feast(level, land.hall(0));
		nextDay(land, 1);
		Festivals.feast(level, land.hall(1));
		helper.assertTrue(reformed(land, "realm_festival"), "not reformed after 3 realm festivals");
		t += Festivals.EVERY_DAYS;
		level.setDayTime((t - 1) * 24000L + 1000);
		Festivals.round(level, land.hall(1), land.entity(1), 8);
		helper.assertTrue(land.entity(1).festivalDay() == t && theRealm(land).treasury() == 4000, "the festival cost the realm after the reform: " + theRealm(land).treasury());
		ModAttachments.FESTIVAL_DAY.set(guest, t - 3);
		helper.assertTrue(Festivals.enjoyedLately(level, guest), "the boost went");
		helper.succeed();
	}

	/**
	 * Crown's Peace: no feud may be declared on a member and bandits can't camp within 128 blocks of one; the member's
	 * takings are 10% lower. Reformed (16 monsters cleared round a member and 7 days without a raid), the takings are
	 * whole and the peace holds; lifted, a feud can be declared again.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsPeace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsPeace")
	public void crownsPeace(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		boolean pacts = Pacts.ENABLED;
		boolean feuds = Feuds.ENABLED;
		Pacts.ENABLED = true;
		Feuds.ENABLED = true;
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Feuds.ENABLED = feuds;
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
		});
		ServerPlayer them = RealmGameTests.listener(helper, new ArrayList<>(), "peace-neighbour");
		land.entity(2).setOwner(them.getUUID(), "peace-neighbour");
		Realms.round(level, land.hall(2), land.entity(2), 6);
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		data.setWants(land.hall(2), Component.literal(land.name(2)), List.of());
		int whole = aDay(land, 1);
		proclaim(helper, land, "crowns_peace");
		Pacts.Result feud = Feuds.declare(level, land.hall(2), land.hall(1), them);
		helper.assertTrue(!feud.ok() && feud.message().getString().equals("The villages of " + Realms.title(server, theRealm(land)).getString() + " are under the Crown's Peace: no feud can be declared on them."),
			"a feud on a member: " + feud.message().getString());
		helper.assertTrue(RealmEdicts.peaceNear(level, land.hall(1).offset(100, 0, 0)) && !RealmEdicts.peaceNear(level, land.hall(1).offset(400, 0, 0)), "bandit camps near a member");
		int lower = aDay(land, 1);
		long takings = Treasury.takings(10, land.entity(1).needs().wellbeing(), land.entity(1).rank());
		long after = takings * 90 / 100;
		helper.assertTrue(whole == takings - takings / 10 && lower == after - after / 10, "takings under the peace: " + takings + ": " + whole + " -> " + lower);
		// the reform: 16 monsters cleared round a member count at the capital, then 7 days without a raid
		VillageQuests.Quest slay = step(land, "crowns_peace");
		helper.assertTrue(slay != null && slay.kind() == VillageQuests.Kind.SLAY && slay.count() == 16, "the step: " + slay);
		for (int i = 0; i < 16; i++) {
			Zombie zombie = EntityType.ZOMBIE.create(level);
			zombie.moveTo(land.hall(1).getX() + 2.5, land.hall(1).getY(), land.hall(1).getZ() + 0.5, 0f, 0f);
			VillageQuests.onKill(level, zombie, level.damageSources().playerAttack(land.owner()));
		}
		helper.assertTrue(step(land, "crowns_peace") == null && RealmEdicts.progress(server, theRealm(land), full("crowns_peace")).step() == 1, "the kills didn't count at the capital");
		nextDay(land, 3);
		RealmEdicts.raided(server, land.at(1));
		helper.assertTrue(RealmEdicts.count(server, theRealm(land), full("crowns_peace")) == 0, "a raid didn't start the days again");
		nextDay(land, 6);
		RealmEdicts.dawn(server, today(land));
		helper.assertTrue(!reformed(land, "crowns_peace"), "reformed after 6 days");
		nextDay(land, 1);
		RealmEdicts.dawn(server, today(land));
		helper.assertTrue(reformed(land, "crowns_peace"), "not reformed after 7 days without a raid: " + RealmEdicts.count(server, theRealm(land), full("crowns_peace")));
		helper.assertTrue(aDay(land, 1) == whole, "the takings after the reform");
		helper.assertTrue(!Feuds.declare(level, land.hall(2), land.hall(1), them).ok() && RealmEdicts.peaceNear(level, land.hall(1).offset(100, 0, 0)), "the boost went");
		helper.assertTrue(RealmEdicts.lift(level, land.hall(0), land.owner(), full("crowns_peace")).done(), "couldn't lift it");
		helper.assertTrue(Feuds.declare(level, land.hall(2), land.hall(1), them).ok(), "no feud once the peace was lifted");
		helper.succeed();
	}

	/**
	 * Royal Charter: a Town member may found colonies and waits 4 days, not 7; the realm treasury pays 32 emeralds more
	 * for its charter (refused when it hasn't them). Reformed (16 paper, 4 books and 2 colonies at Village rank), the
	 * realm pays nothing and the Town still may.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsCharter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsCharter")
	public void royalCharter(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank rank = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		Colonies.COOLDOWN_DAYS = 7;
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = rank;
			Colonies.COOLDOWN_DAYS = cooldown;
		});
		helper.assertTrue(Colonies.limit(level, land.hall(1)) == Colonies.Refusal.RANK && Colonies.cooldownDays(level, land.hall(1)) == 7, "before");
		proclaim(helper, land, "royal_charter");
		helper.assertTrue(Colonies.rankNeeded(level, land.hall(1)) == VillageRanks.Rank.TOWN && Colonies.limit(level, land.hall(1)) == null, "a Town may found one");
		helper.assertTrue(Colonies.cooldownDays(level, land.hall(1)) == 4, "the wait: " + Colonies.cooldownDays(level, land.hall(1)));
		land.entity(1).setTreasury(3200);
		treasury(land, 0);
		Colonies.Bought refused = Colonies.buy(level, land.hall(1), land.owner());
		helper.assertTrue(refused.refusal() == Colonies.Refusal.REALM_MONEY
			&& refused.message().getString().equals("The Royal Charter asks 32 emeralds of the realm treasury for each colony, and it hasn't that much."),
			"bought without the realm's share: " + refused.message().getString());
		helper.assertTrue(land.entity(1).treasury() == 3200, "the village paid for a refused charter");
		treasury(land, 4000);
		Colonies.Bought bought = Colonies.buy(level, land.hall(1), land.owner());
		helper.assertTrue(bought.refusal() == null && theRealm(land).treasury() == 800 && land.entity(1).treasury() == 0, "the charter: " + bought.message().getString());
		helper.assertTrue(bought.message().getString().endsWith("The realm treasury paid 32 emeralds (Royal Charter)."), "its message: " + bought.message().getString());
		// the reform: two steps, then two colonies at Village rank
		bring(helper, land, "royal_charter", Items.PAPER, 16);
		nextDay(land, 1);
		land.data().recordFounded(land.at(1), land.at(2), today(land));
		helper.assertTrue(RealmEdicts.count(server, theRealm(land), full("royal_charter")) == 1, "one colony at Village rank");
		land.data().recordFounded(land.at(1), land.at(3), today(land));
		bring(helper, land, "royal_charter", Items.BOOK, 4);
		helper.assertTrue(reformed(land, "royal_charter"), "not reformed with 2 colonies: " + RealmEdicts.count(server, theRealm(land), full("royal_charter")));
		helper.assertTrue(RealmEdicts.colonyCost(level, land.hall(1)) == 0 && RealmEdicts.canPayColony(level, land.hall(1)) && theRealm(land).treasury() == 800, "the cost after the reform");
		helper.assertTrue(Colonies.rankNeeded(level, land.hall(1)) == VillageRanks.Rank.TOWN && Colonies.cooldownDays(level, land.hall(1)) == 4, "the boost went");
		helper.succeed();
	}

	// ---- saving, the capital, the switches ------------------------------------------------------------------------------

	/** The edicts, a reform's steps and its count survive save and reload; an older file has none; a new capital keeps them and puts up the steps. */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsSave")
	public void savedAndMovedWithTheCapital(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		proclaim(helper, land, "royal_highway");
		bring(helper, land, "royal_highway", Items.STONE_BRICKS, 128);
		RealmEdicts.caravanSent(level, land.hall(0), land.hall(1));
		RealmData.Laws laws = land.data().laws(theRealm(land).id());
		RealmData back = RealmData.load(land.data().save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(back.laws(theRealm(land).id()).equals(laws), "after reload: " + back.laws(theRealm(land).id()) + " vs " + laws);
		helper.assertTrue(back.laws(theRealm(land).id()).counts().get(full("royal_highway")) == 1 && back.laws(theRealm(land).id()).reforms().get(0).step() == 1, "what was kept");
		RealmData old = RealmData.load(new CompoundTag(), level.registryAccess());
		helper.assertTrue(old.laws(theRealm(land).id()).edicts().isEmpty() && old.laws(theRealm(land).id()).lastRaidDay() == -1, "an older file");
		// a new capital: the edicts go with the realm; the old capital's steps come down
		helper.assertTrue(CapitalVotes.putForward(level, land.hall(1), land.owner()).ok(), "the capital didn't move");
		helper.assertTrue(theRealm(land).capital().equals(land.at(1)) && RealmEdicts.inForce(server, theRealm(land)).size() == 1, "the edicts after the move");
		helper.assertTrue(RealmEdicts.progress(server, theRealm(land), full("royal_highway")).step() == 1, "the reform's progress after the move");
		helper.succeed();
	}

	/** With {@code realms} or {@code villageEdicts} off, realm edicts do nothing and none is proclaimed; what's in force stays saved. */
	//$ gametest_ticks_batch AREA '100' '"realmEdictsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmEdictsOff")
	public void switchedOff(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		proclaim(helper, land, "common_market");
		helper.assertTrue(RealmTreasury.levy(server, theRealm(land)) == 15, "on");
		Realms.ENABLED = false;
		helper.assertTrue(RealmTreasury.levy(server, theRealm(land)) == 10 && RealmEdicts.effects(server, theRealm(land)).isEmpty(), "effects with realms off");
		Edicts.Result off = RealmEdicts.proclaim(level, land.hall(0), land.owner(), edict("royal_highway"));
		helper.assertTrue(!off.done() && off.message().getString().equals("Realms are turned off on this server."), "proclaimed with realms off: " + off.message().getString());
		Realms.ENABLED = true;
		Edicts.setEnabled(false);
		helper.assertTrue(RealmTreasury.levy(server, theRealm(land)) == 10, "effects with edicts off");
		helper.assertTrue(!RealmEdicts.proclaim(level, land.hall(0), land.owner(), edict("royal_highway")).done(), "proclaimed with edicts off");
		Edicts.setEnabled(true);
		helper.assertTrue(RealmEdicts.inForce(server, theRealm(land)).size() == 1 && RealmTreasury.levy(server, theRealm(land)) == 15, "back on");
		helper.succeed();
	}
}
