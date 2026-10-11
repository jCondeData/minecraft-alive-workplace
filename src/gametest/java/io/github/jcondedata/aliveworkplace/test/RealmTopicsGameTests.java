package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmResearch;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The realm's eight research topics (ROADMAP 33.16): the Realm row lists them with their price and what each needs; a
 * save with a removed interim topic loads quietly; and each topic's effect at level 1 (and 2 where it has one) in the
 * system it changes: caravans, the board's spread, the noon post, Travel Tickets, the relief riders' count for 33.17,
 * colonies and settlers, wellbeing (pace still capped) and the dawn granary caravan. Each test is alone in its batch.
 */
public class RealmTopicsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final List<String> EIGHT = List.of("charters", "common_coin", "concord", "ferry_charter", "granaries", "levies", "royal_post", "royal_roads");

	/** The realm knows {@code levels} (and nothing else). */
	static void know(RealmGameTests.Land land, Map<String, Integer> levels) {
		RealmData.Realm realm = land.data().realmOf(land.at(0));
		land.data().putStudy(land.data().study(realm.id()).withResearch(new Research.State(Map.copyOf(levels), Optional.empty(), 0, false)));
		RealmResearch.forget();
	}

	static String lore(ItemStack stack) {
		return String.join(" | ", RealmGameTests.lore(stack));
	}

	/** The Realm row lists exactly the eight, each with its price and what it needs first; an old save's interim topic is dropped quietly. */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsRow"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsRow")
	public void theRealmRowShowsTheEight(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		List<String> mods = RealmResearch.all().stream().filter(t -> t.id().getNamespace().equals("aliveworkplace")).map(RealmResearch.Topic::key).toList();
		helper.assertTrue(mods.equals(EIGHT), "the mod's realm topics: " + mods);
		for (RealmResearch.Topic t : RealmResearch.all()) {
			if (t.id().getNamespace().equals("aliveworkplace")) {
				helper.assertTrue(t.paper() == 16 && t.books() == 2 && t.booksFrom() == 2 && t.emeralds() == 8, "the price of " + t.key() + ": " + t);
				helper.assertTrue(t.items(1).equals(Map.of(Items.PAPER, 16)) && t.items(2).equals(Map.of(Items.PAPER, 16, Items.BOOK, 2)),
					"levels 1 and 2 of " + t.key() + ": " + t.items(1) + " " + t.items(2));
			}
		}
		helper.assertTrue(RealmResearch.get("royal_roads").get().levels() == 2 && RealmResearch.get("royal_post").get().levels() == 1
			&& RealmResearch.get("granaries").get().needs().equals(Map.of("concord", 1, "royal_roads", 2)), "levels and needs");
		// an old save: the interim King's Peace chosen and paid, Realm Feasts known
		RealmData.Realm realm = land.data().realmOf(land.at(0));
		land.data().putStudy(land.data().study(realm.id()).withResearch(new Research.State(Map.of("realm_feasts", 1, "royal_roads", 1),
			Optional.of("kings_peace"), 40, true)));
		RealmResearch.forget();
		Research.State state = RealmResearch.state(land.level().getServer(), realm);
		helper.assertTrue(state.current().isEmpty() && !state.paid() && state.progress() == 0, "the removed topic wasn't dropped: " + state);
		helper.assertTrue(RealmResearch.effects(land.level(), land.hall(1)).size() == 1, "effects: " + RealmResearch.effects(land.level(), land.hall(1)));
		// the capital's Realm row
		ResearchScreen.View view = new ResearchScreen.View();
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(0), view);
		menu.press(ResearchScreen.REALM_TAB, land.owner());
		List<RealmResearch.Topic> all = new ArrayList<>(RealmResearch.all());
		Map<String, Item> icons = Map.of("royal_roads", Items.DIRT_PATH, "common_coin", Items.GOLD_NUGGET, "royal_post", Items.FEATHER, "ferry_charter", Items.OAK_BOAT,
			"levies", Items.IRON_HORSE_ARMOR, "charters", Items.MAP, "concord", Items.BELL, "granaries", Items.HAY_BLOCK);
		for (String key : EIGHT) {
			ItemStack icon = menu.icon(ResearchScreen.TOPIC_SLOTS[all.indexOf(RealmResearch.get(key).get())]);
			helper.assertTrue(icon.is(icons.get(key)), key + "'s icon: " + icon);
		}
		ItemStack roads = menu.icon(ResearchScreen.TOPIC_SLOTS[all.indexOf(RealmResearch.get("royal_roads").get())]);
		helper.assertTrue(roads.getHoverName().getString().equals("Royal Roads") && lore(roads).contains("Caravans between the realm's villages arrive 25% sooner a level")
			&& lore(roads).contains("Level 1 of 2") && lore(roads).contains("Next level: 16× Paper, 2× Book, and 8 emeralds from the realm treasury"), "Royal Roads: " + lore(roads));
		ItemStack granaries = menu.icon(ResearchScreen.TOPIC_SLOTS[all.indexOf(RealmResearch.get("granaries").get())]);
		helper.assertTrue(lore(granaries).contains("Next level: 16× Paper, and 8 emeralds from the realm treasury")
			&& lore(granaries).contains("First: Concord " + BuilderLevels.levelName(1).getString())
			&& lore(granaries).contains("First: Royal Roads " + BuilderLevels.levelName(2).getString()), "Granaries: " + lore(granaries));
		ItemStack coin = menu.icon(ResearchScreen.TOPIC_SLOTS[all.indexOf(RealmResearch.get("common_coin").get())]);
		helper.assertTrue(!lore(coin).contains("First:") && lore(coin).contains("Click: the realm researches this next"), "Common Coin is open after Royal Roads I: " + lore(coin));
		helper.succeed();
	}

	/** Royal Roads: a caravan between the realm's villages arrives 25% sooner a level; with realms off, as before. */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsRoads"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsRoads")
	public void royalRoadsSpeedCaravans(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		Caravans.Data data = Caravans.Data.get(land.level());
		long base = Caravans.trip(land.level(), land.hall(0), land.hall(1), data);
		helper.assertTrue(base == Caravans.travelTicks(Math.sqrt(land.hall(0).distSqr(land.hall(1))), data.roadFinished(land.hall(0), land.hall(1))), "without research: " + base);
		know(land, Map.of("royal_roads", 1));
		long one = Caravans.trip(land.level(), land.hall(0), land.hall(1), data);
		know(land, Map.of("royal_roads", 2));
		long two = Caravans.trip(land.level(), land.hall(1), land.hall(0), data);
		helper.assertTrue(one == base * 3 / 4 && two == base / 2, "Royal Roads I and II: " + base + " -> " + one + " -> " + two);
		Realms.ENABLED = false;
		helper.assertTrue(Caravans.trip(land.level(), land.hall(0), land.hall(1), data) == base, "with realms off");
		Realms.ENABLED = true;
		helper.succeed();
	}

	/** Common Coin: the board sells 3 points nearer its price a level (110% -> 107% -> 104%), in the tooltip too. */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsCoin"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsCoin")
	public void commonCoinNarrowsTheSpread(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		helper.assertTrue(TradePage.sellPercent(land.level(), land.hall(1)) == 110 && TradePage.selling(land.level(), land.hall(1), 100) == 110, "without research");
		know(land, Map.of("royal_roads", 1, "common_coin", 1));
		helper.assertTrue(TradePage.sellPercent(land.level(), land.hall(1)) == 107 && TradePage.selling(land.level(), land.hall(1), 100) == 107, "Common Coin I: " + TradePage.sellPercent(land.level(), land.hall(1)));
		know(land, Map.of("royal_roads", 1, "common_coin", 2));
		helper.assertTrue(TradePage.sellPercent(land.level(), land.hall(0)) == 104 && TradePage.selling(land.level(), land.hall(0), 200) == 208, "Common Coin II: " + TradePage.sellPercent(land.level(), land.hall(0)));
		helper.assertTrue(TradePage.selling(100) == 110, "the plain price changed");
		helper.succeed();
	}

	/** Royal Post: a parcel between mailboxes in the realm's villages arrives at noon; without it, it waits for dawn. */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsPost"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsPost")
	public void royalPostDeliversAtNoon(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		BlockPos fromBox = land.hall(0).offset(2, 0, 0);
		BlockPos toBox = land.hall(1).offset(2, 0, 0);
		level.setBlockAndUpdate(fromBox, ModBlocks.MAILBOX.defaultBlockState());
		level.setBlockAndUpdate(toBox, ModBlocks.MAILBOX.defaultBlockState());
		UUID to = UUID.randomUUID();
		PostOffice office = PostOffice.get(server);
		office.register(to, GlobalPos.of(level.dimension(), toBox));
		Parcel parcel = office.post(land.owner().getUUID(), "Owner", to, "Friend", GlobalPos.of(level.dimension(), fromBox), level.getGameTime(),
			List.of(new ItemStack(Items.APPLE, 3)));
		parcel.setStatus(Parcel.Status.IN_TRANSIT);
		Leftovers.after(helper, () -> {
			office.remove(parcel);
			office.unregister(to, GlobalPos.of(level.dimension(), toBox));
		});
		helper.assertTrue(office.noon(server) == 0 && office.parcel(parcel.id()) != null, "delivered at noon without Royal Post");
		know(land, Map.of("royal_roads", 1, "royal_post", 1));
		helper.assertTrue(office.noon(server) == 1 && office.parcel(parcel.id()) == null, "not delivered at noon with Royal Post");
		MailboxBlockEntity box = (MailboxBlockEntity) level.getBlockEntity(toBox);
		helper.assertTrue(((Container) box).countItem(Items.APPLE) == 3, "the apples aren't in the mailbox");
		helper.succeed();
	}

	/** Ferry Charter: half fare (at least an emerald) between the realm's posts for a member's owner and their friends; full fare for others. */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsFerry"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsFerry")
	public void ferryCharterHalvesTickets(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		MinecraftServer server = land.level().getServer();
		GlobalPos a = GlobalPos.of(land.level().dimension(), land.hall(0).offset(3, 0, 0));
		GlobalPos b = GlobalPos.of(land.level().dimension(), land.hall(1).offset(3, 0, 0));
		UUID owner = land.owner().getUUID();
		UUID friend = UUID.randomUUID();
		UUID stranger = UUID.randomUUID();
		Friends.get(server).add(owner, friend, "friend");
		Leftovers.after(helper, () -> Friends.get(server).remove(owner, friend));
		helper.assertTrue(RealmResearch.fare(server, a, b, owner, 6) == 6, "a discount without the Ferry Charter");
		know(land, Map.of("royal_roads", 1, "royal_post", 1, "ferry_charter", 1));
		helper.assertTrue(RealmResearch.fare(server, a, b, owner, 6) == 3 && RealmResearch.fare(server, a, b, friend, 7) == 4,
			"half fare: " + RealmResearch.fare(server, a, b, owner, 6) + ", " + RealmResearch.fare(server, a, b, friend, 7));
		helper.assertTrue(RealmResearch.fare(server, a, b, owner, 1) == 1, "less than an emerald");
		helper.assertTrue(RealmResearch.fare(server, a, b, stranger, 6) == 6, "a stranger got the discount");
		helper.assertTrue(TravelNetwork.fare(server, a, b, owner) == Math.max(1, (int) Math.round(TravelNetwork.fare(a, b) / 2.0)), "the ferryman's fare");
		Realms.ENABLED = false;
		helper.assertTrue(RealmResearch.fare(server, a, b, owner, 6) == 6, "with realms off");
		Realms.ENABLED = true;
		helper.succeed();
	}

	/** Levies: relief bands one rider bigger per village a level (the count 33.17's bands read). */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsLevies"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsLevies")
	public void leviesCountRiders(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		helper.assertTrue(RealmResearch.reliefRiders(land.level(), land.hall(1)) == 0, "riders without Levies");
		know(land, Map.of("levies", 1));
		helper.assertTrue(RealmResearch.reliefRiders(land.level(), land.hall(1)) == 1 && RealmResearch.level(land.level(), land.hall(1), "levies") == 1, "Levies I");
		know(land, Map.of("levies", 2));
		helper.assertTrue(RealmResearch.reliefRiders(land.level(), land.hall(0)) == 2 && RealmResearch.level(land.level(), land.hall(0), "levies") == 2, "Levies II");
		Realms.ENABLED = false;
		helper.assertTrue(RealmResearch.reliefRiders(land.level(), land.hall(0)) == 0, "with realms off");
		Realms.ENABLED = true;
		helper.succeed();
	}

	/** Charters: a charter costs a quarter less a level (32 -> 24 -> 16 emeralds) and the settlers bring 16 more of each supply. */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsCharters"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsCharters")
	public void chartersCheapenColonies(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		MinecraftServer server = land.level().getServer();
		RealmData.Order order = new RealmData.Order(land.at(0), land.hall(0).offset(200, 0, 0), Optional.empty(), RealmData.GATHERING, 0, 0, 3200)
			.withSupplies(Map.of(Items.BIRCH_LOG, 64, Items.BREAD, 32));
		helper.assertTrue(Colonies.cost(land.level(), land.hall(0)) == 32 && Settlers.load(server, order).equals(order.supplies()), "without Charters");
		know(land, Map.of("royal_roads", 2, "charters", 1));
		helper.assertTrue(Colonies.cost(land.level(), land.hall(0)) == 24, "Charters I: " + Colonies.cost(land.level(), land.hall(0)));
		land.entity(0).setTreasury(100 * 100);
		helper.assertTrue(Colonies.treasuryShare(land.entity(0)) == 24, "the treasury pays " + Colonies.treasuryShare(land.entity(0)));
		Map<Item, Integer> load = Settlers.load(server, order);
		helper.assertTrue(load.get(Items.BIRCH_LOG) == 80 && load.get(Items.BREAD) == 48 && load.get(Items.OAK_PLANKS) == 16 && load.get(Items.COBBLESTONE) == 16
			&& load.get(Items.TORCH) == 16 && load.get(Items.GLASS_PANE) == 16 && load.get(Items.WHITE_BED) == 16, "the settlers' load: " + load);
		know(land, Map.of("royal_roads", 2, "charters", 2));
		helper.assertTrue(Colonies.cost(land.level(), land.hall(0)) == 16 && Settlers.load(server, order).get(Items.BREAD) == 48,
			"Charters II: " + Colonies.cost(land.level(), land.hall(0)) + " " + Settlers.load(server, order));
		helper.succeed();
	}

	/** Concord: wellbeing 5% higher a level in every member village; the pace it makes still tops out at 25% faster. */
	//$ gametest_ticks_batch AREA '100' '"realmTopicsConcord"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTopicsConcord")
	public void concordRaisesWellbeing(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		float before = TreeEffects.wellbeing(land.entity(1));
		know(land, Map.of("levies", 1, "concord", 1));
		float one = TreeEffects.wellbeing(land.entity(1));
		know(land, Map.of("levies", 1, "concord", 2));
		float two = TreeEffects.wellbeing(land.entity(0));
		helper.assertTrue(Math.abs(one - before - 0.05f) < 1e-4 && Math.abs(two - before - 0.10f) < 1e-4, "Concord I and II: " + before + " -> " + one + " -> " + two);
		helper.assertTrue(VillageNeeds.factor(1f + two) == VillageNeeds.factor(1f) && Math.abs(VillageNeeds.factor(1f) - 0.8f) < 1e-4, "pace past its 25% top");
		helper.succeed();
	}

	/**
	 * Granaries: at dawn a member under 16 meals is sent 32 by caravan from the fullest store, which keeps the rest; the
	 * caravan needs no route (outside the route cap); a member with enough gets nothing; without Granaries, nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"realmTopicsGranaries"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "realmTopicsGranaries")
	public void granariesFeedTheHungry(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		// a third village far off (its own store), joined to the realm
		BlockPos far = land.hall(0).offset(80, 0, 0); // beyond both stores' reach (VillageHalls.RADIUS 64)
		List<BlockPos> placed = List.of(far, land.hall(0).offset(0, 0, -3), land.hall(0).offset(1, 0, -3), far.offset(0, 0, -3), far.offset(1, 0, -3));
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.clearRoad(far);
			data.clearRoad(land.hall(0));
			placed.forEach(p -> level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()));
		});
		level.setBlockAndUpdate(far, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity farHall = (VillageHallBlockEntity) level.getBlockEntity(far);
		farHall.setOwner(land.owner().getUUID(), "Owner");
		farHall.setRank(VillageRanks.Rank.HAMLET);
		Realms.round(level, far, farHall, 5);
		helper.assertTrue(Realms.add(level, land.hall(0), GlobalPos.of(level.dimension(), far), land.owner()).ok(), "the far village didn't join");
		// the capital's store: a smoker and a chest of 64 bread; the far village's: 4 bread
		level.setBlockAndUpdate(placed.get(1), Blocks.SMOKER.defaultBlockState());
		level.setBlockAndUpdate(placed.get(2), Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(placed.get(3), Blocks.SMOKER.defaultBlockState());
		level.setBlockAndUpdate(placed.get(4), Blocks.CHEST.defaultBlockState());
		Container rich = (Container) level.getBlockEntity(placed.get(2));
		Container poor = (Container) level.getBlockEntity(placed.get(4));
		rich.setItem(0, new ItemStack(Items.BREAD, 64));
		poor.setItem(0, new ItemStack(Items.BREAD, 4));
		helper.assertTrue(RealmResearch.meals(level, land.hall(0)) == 64 && RealmResearch.meals(level, far) == 4,
			"the stores: " + RealmResearch.meals(level, land.hall(0)) + ", " + RealmResearch.meals(level, far));
		helper.assertTrue(RealmResearch.granaries(server) == 0 && rich.countItem(Items.BREAD) == 64, "sent without Granaries");
		know(land, Map.of("royal_roads", 2, "levies", 1, "concord", 1, "granaries", 1));
		int sent = RealmResearch.granaries(server);
		helper.assertTrue(sent == 1 && rich.countItem(Items.BREAD) == 32, "Granaries: " + sent + " caravans, " + rich.countItem(Items.BREAD) + " bread left");
		List<Caravans.Shipment> road = data.onTheRoad().stream().filter(s -> s.to().equals(far)).toList();
		helper.assertTrue(road.size() == 1 && road.get(0).from().equals(land.hall(0)) && road.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 32
			&& road.get(0).arrives() > level.getGameTime(), "the granary caravan: " + road);
		helper.assertTrue(data.routesFrom(land.hall(0)).isEmpty(), "it took a route: " + data.routesFrom(land.hall(0)));
		// a member with enough gets nothing
		data.clearRoad(far);
		poor.setItem(0, new ItemStack(Items.BREAD, 20));
		helper.assertTrue(RealmResearch.granaries(server) == 0 && rich.countItem(Items.BREAD) == 32, "sent to a member with 20 meals");
		helper.succeed();
	}
}
