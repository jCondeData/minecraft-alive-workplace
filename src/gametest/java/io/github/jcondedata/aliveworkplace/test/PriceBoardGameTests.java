package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.trade.TradeTalk;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ROADMAP 33.4, the price board: the hall's minecart opens the Trade page (Routes and Prices tabs; the later tabs stay
 * hidden), the Prices tab lists every good with its two prices, an arrow since yesterday, a star on what the village is
 * known for and a mark on what it's short of, the tooltip names the dearer and the cheaper village on its routes, the
 * hall's name icon says what it's known for and short of, villagers talk about it, and the Village Ledger reaches the
 * page from afar. Also what's likeliest to break: the economy switched off, a save and reload, no routes, no count yet
 * and no goods at all. The goods are the gametest data pack's six.
 */
public class PriceBoardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final ResourceLocation TIMBER = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_timber");
	private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_grain");
	private static final ResourceLocation FISH = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_fish");
	private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_stone");

	/** A hall at {@link #HALL} with a small village radius and only the six test goods, alone in its batch; put back when the test ends. */
	private static BlockPos hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Caravans.Data.get(helper.getLevel()).remove(hall));
		return hall;
	}

	/** A price as the board writes it: {@code emeralds} as the roadmap reads, or its CobbleDollars with the pack (the compat suite). */
	private static String e(int cents, String emeralds) {
		return io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()
			? Math.round(cents * (double) io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD / 100) + " CobbleDollars" : emeralds;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** Another village on the caravans' list, {@code dx} blocks east of the test's hall (only its entry; no hall stands there). */
	private static BlockPos village(GameTestHelper helper, String name, int dx) {
		BlockPos at = helper.absolutePos(HALL).east(dx);
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		data.setWants(at, Component.literal(name), List.of());
		Leftovers.after(helper, () -> data.remove(at));
		return at;
	}

	/** Today's market for {@code hall}: nothing recounts it (its prices already moved today). */
	private static void market(GameTestHelper helper, BlockPos hall, List<ResourceLocation> knownFor, List<ResourceLocation> shortOf,
							   Map<ResourceLocation, Market.Price> prices) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		if (data.village(hall) == null) {
			data.setWants(hall, VillageHalls.name(level, hall), List.of());
		}
		data.setMarket(hall, new Market(knownFor, shortOf, prices, Chronicle.day(level), List.of()));
		helper.assertTrue(!data.market(hall).isEmpty(), "the market wasn't kept");
	}

	private static Map<ResourceLocation, Market.Price> prices(Object... goodNowYesterday) {
		Map<ResourceLocation, Market.Price> out = new LinkedHashMap<>();
		for (int i = 0; i < goodNowYesterday.length; i += 3) {
			out.put((ResourceLocation) goodNowYesterday[i], new Market.Price((Integer) goodNowYesterday[i + 1], (Integer) goodNowYesterday[i + 2], 0));
		}
		return out;
	}

	/** The icon called {@code name} in the page's rows, or null. */
	private static ItemStack named(ChoiceMenu menu, String name) {
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (menu.icon(s).getHoverName().getString().equals(name)) {
				return menu.icon(s);
			}
		}
		return null;
	}

	private static List<String> lore(ItemStack icon) {
		List<String> out = new ArrayList<>();
		var lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

	/** Opens the hall's screen for nobody, clicks the minecart, then the Prices tab: the way a player gets there. */
	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		helper.assertTrue(menu.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD), "no Prices tab: " + menu.icon(TradePage.Tab.PRICES.slot()));
		menu.press(TradePage.Tab.PRICES.slot(), player);
		return menu;
	}

	/**
	 * The page as the hall renders it: the minecart opens Trade on Routes, with Prices beside it and the later tabs
	 * hidden; Prices stars what the village is known for, marks what it's short of and shows each price's arrow and both
	 * prices; the way back leads to the hall, whose name icon says "Known for: ... Short of: ...".
	 */
	//$ gametest_ticks_batch AREA '100' '"priceBoardPage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardPage")
	public void thePricesTabStarsMarksAndShowsArrows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			market(helper, hall, List.of(TIMBER), List.of(FISH), prices(TIMBER, 80, 90, FISH, 150, 120, GRAIN, 100, 100));
			String village = VillageHalls.name(level, hall).getString();
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			helper.assertTrue(lore(menu.icon(VillageHallScreen.ROUTES)).contains("On the Trade page: Routes, Prices"),
				"the minecart doesn't name the tabs: " + lore(menu.icon(VillageHallScreen.ROUTES)));
			menu.press(VillageHallScreen.ROUTES, player);
			ItemStack routes = menu.icon(TradePage.Tab.ROUTES.slot());
			helper.assertTrue(routes.is(Items.CHEST_MINECART) && TradePage.marks(routes).contains(TradePage.OPEN), "the minecart didn't open Routes: " + routes);
			helper.assertTrue(routes.getHoverName().getString().equals("Trade routes of " + village), "the Routes tab's name: " + routes.getHoverName().getString());
			helper.assertTrue(menu.icon(TradePage.BACK).is(Items.ARROW), "no way back");
			ItemStack tab = menu.icon(TradePage.Tab.PRICES.slot());
			helper.assertTrue(tab.is(Items.EMERALD) && !TradePage.marks(tab).contains(TradePage.OPEN), "the Prices tab: " + tab);
			for (TradePage.Tab later : List.of(TradePage.Tab.PACTS, TradePage.Tab.REALM, TradePage.Tab.COLONIES)) {
				helper.assertTrue(menu.icon(later.slot()).isEmpty(), later + " shows before its item: " + menu.icon(later.slot()));
			}
			helper.assertTrue(TradePage.tabs().equals(List.of(TradePage.Tab.ROUTES, TradePage.Tab.PRICES)), "tabs: " + TradePage.tabs());

			menu.press(TradePage.Tab.PRICES.slot(), player);
			tab = menu.icon(TradePage.Tab.PRICES.slot());
			helper.assertTrue(TradePage.marks(tab).contains(TradePage.OPEN) && tab.getHoverName().getString().equals("Prices in " + village),
				"Prices isn't open: " + tab.getHoverName().getString() + " " + TradePage.marks(tab));
			helper.assertTrue(!TradePage.marks(menu.icon(TradePage.Tab.ROUTES.slot())).contains(TradePage.OPEN), "Routes is still lit");
			// every good, in the goods' order, from the first row
			List<TradeGoods.Good> goods = TradeGoods.all();
			helper.assertTrue(goods.size() == 6, "the six test goods: " + goods.size());
			for (int i = 0; i < goods.size(); i++) {
				ItemStack icon = menu.icon(VillageHallScreen.FIRST_ROW + i);
				helper.assertTrue(icon.is(goods.get(i).icon()) && icon.getHoverName().getString().equals(goods.get(i).name().getString()),
					"slot " + i + " isn't " + goods.get(i).id() + ": " + icon);
			}
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + goods.size()).isEmpty(), "something after the last good");

			ItemStack timber = named(menu, "Test Timber");
			helper.assertTrue(timber != null && timber.is(Items.OAK_LOG), "no timber: " + timber);
			helper.assertTrue(TradePage.marks(timber).equals(Set.of(TradePage.STAR, TradePage.DOWN)), "timber (known for, fell): " + TradePage.marks(timber));
			helper.assertTrue(lore(timber).equals(List.of("A bundle of 16", "We sell: " + e(88, "0.88 emeralds"), "We pay: " + e(80, "0.8 emeralds"), "Down from " + e(90, "0.9 emeralds") + " yesterday",
				village + " is known for it", "No trade routes to compare prices with")), "timber's tooltip: " + lore(timber));

			ItemStack fish = named(menu, "Test Fish");
			helper.assertTrue(fish != null && TradePage.marks(fish).equals(Set.of(TradePage.SHORT, TradePage.UP)), "fish (short of, rose): " + fish);
			helper.assertTrue(lore(fish).contains("Up from " + e(120, "1.2 emeralds") + " yesterday") && lore(fish).contains(village + " is short of it")
				&& lore(fish).contains("We pay: " + e(150, "1.5 emeralds")) && lore(fish).contains("We sell: " + e(165, "1.65 emeralds")), "fish's tooltip: " + lore(fish));

			ItemStack grain = named(menu, "Test Grain");
			helper.assertTrue(grain != null && TradePage.marks(grain).equals(Set.of(TradePage.STEADY)), "grain (steady, neither): " + grain);
			helper.assertTrue(lore(grain).contains("Steady since yesterday") && lore(grain).contains("We pay: " + e(100, "1 emerald")), "grain's tooltip: " + lore(grain));
			// a good the count gave no price yet stands at its base, steady
			ItemStack stone = named(menu, "Test Stone");
			int base = TradeGoods.get(STONE).basePrice();
			helper.assertTrue(stone != null && TradePage.marks(stone).equals(Set.of(TradePage.STEADY))
				&& lore(stone).contains("We pay: " + TradePage.money(base).getString()), "stone without a price: " + (stone == null ? null : lore(stone)));

			// a click on a good changes nothing (trading is 33.5), and the way back leads to the hall
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(named(menu, "Test Timber") != null, "a click on a good left the page");
			menu.press(TradePage.BACK, player);
			helper.assertTrue(menu.icon(0).is(Items.NAME_TAG) && menu.icon(VillageHallScreen.ROUTES).is(Items.CHEST_MINECART), "not back at the hall: " + menu.icon(0));
			helper.assertTrue(lore(menu.icon(0)).contains("Known for: Test Timber. Short of: Test Fish"), "the name icon: " + lore(menu.icon(0)));
			helper.assertTrue(TradePage.marks(menu.icon(0)).isEmpty() && TradePage.marks(menu.icon(VillageHallScreen.ROUTES)).isEmpty(), "marks on the hall's own icons");

			// known for two and short of none; short of one only
			market(helper, hall, List.of(TIMBER, STONE), List.of(), prices(TIMBER, 80, 80));
			helper.assertTrue(lore(VillageHallScreen.forTest(player, hall).icon(0)).contains("Known for: Test Timber, Test Stone"), "known for two: "
				+ lore(VillageHallScreen.forTest(player, hall).icon(0)));
			market(helper, hall, List.of(), List.of(FISH), prices(TIMBER, 80, 80));
			helper.assertTrue(lore(VillageHallScreen.forTest(player, hall).icon(0)).contains("Short of: Test Fish"), "short of one: "
				+ lore(VillageHallScreen.forTest(player, hall).icon(0)));
			helper.succeed();
		});
	}

	/**
	 * The tooltip names the dearest and the cheapest village among those the village has routes with, either way; a
	 * village without a route, one with no price yet and one that pays the same aren't named.
	 */
	//$ gametest_ticks_batch AREA '100' '"priceBoardPartners"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardPartners")
	public void theTooltipNamesTheDearestAndCheapestVillageOnItsRoutes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			market(helper, hall, List.of(), List.of(), prices(TIMBER, 100, 100, FISH, 100, 100, GRAIN, 100, 100));
			BlockPos ashford = village(helper, "Ashford", 40);
			BlockPos ridgeway = village(helper, "Ridgeway", 60);
			BlockPos midby = village(helper, "Midby", 80);
			BlockPos farholt = village(helper, "Farholt", 100);
			BlockPos newbury = village(helper, "Newbury", 120);
			market(helper, ashford, List.of(), List.of(), prices(TIMBER, 140, 140, FISH, 100, 100));
			market(helper, ridgeway, List.of(), List.of(), prices(TIMBER, 72, 72, FISH, 100, 100));
			market(helper, midby, List.of(), List.of(), prices(TIMBER, 120, 120));
			market(helper, farholt, List.of(), List.of(), prices(TIMBER, 190, 190)); // dearest of all, but no route
			helper.assertTrue(data.toggleRoute(hall, ashford) && data.toggleRoute(ridgeway, hall) && data.toggleRoute(hall, midby)
				&& data.toggleRoute(hall, newbury), "no routes"); // we send to Ashford, Midby and Newbury; Ridgeway sends to us

			ChoiceMenu menu = board(helper, player, hall);
			List<String> timber = lore(named(menu, "Test Timber"));
			helper.assertTrue(timber.contains("Dearer in Ashford: " + e(140, "1.4 emeralds")), "the dearest: " + timber);
			helper.assertTrue(timber.contains("Cheaper in Ridgeway: " + e(72, "0.72 emeralds")), "the cheapest: " + timber);
			helper.assertTrue(timber.stream().noneMatch(l -> l.contains("Farholt") || l.contains("Midby") || l.contains("Newbury")), "others named: " + timber);
			// the same price on every route: neither line, and it says so
			List<String> fish = lore(named(menu, "Test Fish"));
			helper.assertTrue(fish.stream().noneMatch(l -> l.startsWith("Dearer") || l.startsWith("Cheaper"))
				&& fish.contains("No dearer or cheaper on our trade routes"), "fish at one price everywhere: " + fish);
			// no partner has a price for grain yet
			List<String> grain = lore(named(menu, "Test Grain"));
			helper.assertTrue(grain.stream().noneMatch(l -> l.startsWith("Dearer") || l.startsWith("Cheaper")), "grain compared with no prices: " + grain);

			// only a dearer one: a village on a route leaves the list
			data.remove(ridgeway);
			timber = lore(named(board(helper, player, hall), "Test Timber"));
			helper.assertTrue(timber.contains("Dearer in Ashford: " + e(140, "1.4 emeralds")) && timber.stream().noneMatch(l -> l.startsWith("Cheaper")), "after Ridgeway went: " + timber);
			// the routes stopped: nothing to compare with
			data.toggleRoute(hall, ashford);
			data.toggleRoute(hall, midby);
			data.toggleRoute(hall, newbury);
			timber = lore(named(board(helper, player, hall), "Test Timber"));
			helper.assertTrue(timber.contains("No trade routes to compare prices with") && timber.stream().noneMatch(l -> l.startsWith("Dearer")), "no routes: " + timber);
			helper.succeed();
		});
	}

	/** End to end: three lumberjacks by a hall in a forest, the hall's own round counts, and the board stars timber. */
	//$ gametest_ticks_batch AREA '100' '"priceBoardCount"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardCount")
	public void theHallsOwnCountShowsOnTheBoard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(HALL);
		String old = level.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, at, "minecraft:forest");
		Leftovers.after(helper, () -> fill(level, at, old));
		BlockPos hall = hall(helper);
		for (int i = 0; i < 3; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + 2 * i, 2, 4));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.LUMBERJACK));
			v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(4 + 2 * i, 1, 4))));
		}
		ServerPlayer player = player(helper);
		helper.runAfterDelay(20, () -> {
			Market m = Caravans.Data.get(level).market(hall);
			helper.assertTrue(m.priceDay() == Chronicle.day(level) && m.knownFor().contains(TIMBER), "the hall's round didn't count: " + m);
			ChoiceMenu menu = board(helper, player, hall);
			ItemStack timber = named(menu, "Test Timber");
			helper.assertTrue(timber != null && TradePage.marks(timber).contains(TradePage.STAR), "timber isn't starred: " + timber);
			helper.assertTrue(lore(timber).contains("We pay: " + TradePage.money(m.prices().get(TIMBER).cents()).getString()), "timber's price: " + lore(timber));
			helper.assertTrue(lore(VillageHallScreen.forTest(player, hall).icon(0)).stream().anyMatch(l -> l.startsWith("Known for: ") && l.contains("Test Timber")),
				"the name icon: " + lore(VillageHallScreen.forTest(player, hall).icon(0)));
			helper.succeed();
		});
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	/**
	 * With {@code villageEconomy} off the minecart opens the routes page as it always was (no tabs, no marks), the name
	 * icon says nothing of goods even with prices saved, and nobody talks of trade; back on, the saved prices show again.
	 */
	//$ gametest_ticks_batch AREA '100' '"priceBoardOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardOff")
	public void withTheEconomyOffTheMinecartOpensTheOldRoutesPage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		villager.setNoAi(true);
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		helper.runAfterDelay(2, () -> {
			market(helper, hall, List.of(TIMBER), List.of(FISH), prices(TIMBER, 60, 90, FISH, 150, 120));
			helper.assertTrue(Chatter.topics(level, villager, hall).contains(TradeTalk.GLUT), "on: no trade talk: " + Chatter.topics(level, villager, hall));
			WorkplaceConfig.parse("{\"villageEconomy\": false}").apply();
			helper.assertTrue(!Economy.ENABLED && !TradePage.shown(), "the switch didn't turn it off");
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			helper.assertTrue(lore(menu.icon(0)).stream().noneMatch(l -> l.startsWith("Known for") || l.startsWith("Short of")), "the name icon: " + lore(menu.icon(0)));
			helper.assertTrue(lore(menu.icon(VillageHallScreen.ROUTES)).stream().noneMatch(l -> l.contains("Trade page")), "the minecart names tabs: "
				+ lore(menu.icon(VillageHallScreen.ROUTES)));
			menu.press(VillageHallScreen.ROUTES, player);
			helper.assertTrue(menu.icon(4).is(Items.CHEST_MINECART) && TradePage.marks(menu.icon(4)).isEmpty(), "not the old routes page: " + menu.icon(4));
			helper.assertTrue(menu.icon(TradePage.Tab.PRICES.slot()).isEmpty(), "a Prices tab with the economy off: " + menu.icon(TradePage.Tab.PRICES.slot()));
			helper.assertTrue(menu.icon(0).is(Items.ARROW), "no way back");
			menu.press(0, player);
			helper.assertTrue(menu.icon(0).is(Items.NAME_TAG), "not back at the hall");
			// asked for outright, Prices falls back to Routes
			ChoiceMenu asked = TradePage.forTest(player, hall, TradePage.Tab.PRICES);
			helper.assertTrue(named(asked, "Test Timber") == null && asked.icon(TradePage.Tab.PRICES.slot()).isEmpty(), "the Prices tab opened with the economy off");
			helper.assertTrue(TradeTalk.lines(level, hall).isEmpty() && Chatter.topics(level, villager, hall).stream().noneMatch(TradeTalk::is),
				"trade talk with the economy off: " + Chatter.topics(level, villager, hall));
			helper.assertTrue(TradePage.knownAndShort(level, hall) == null, "the name line with the economy off");

			new WorkplaceConfig().apply();
			helper.assertTrue(Economy.ENABLED, "the economy didn't come back on");
			ItemStack timber = named(board(helper, player, hall), "Test Timber");
			helper.assertTrue(timber != null && lore(timber).contains("We pay: " + e(60, "0.6 emeralds")), "the saved price didn't come back: " + timber);
			helper.succeed();
		});
	}

	/** A market saved and loaded again draws the same board: the same marks and the same tooltip on every good. */
	//$ gametest_ticks_batch AREA '100' '"priceBoardReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardReload")
	public void theBoardIsTheSameAfterASaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			market(helper, hall, List.of(TIMBER, STONE), List.of(FISH), prices(TIMBER, 55, 61, FISH, 187, 150, GRAIN, 100, 100, STONE, 99, 105));
			BlockPos ashford = village(helper, "Ashford", 40);
			market(helper, ashford, List.of(), List.of(TIMBER), prices(TIMBER, 163, 150));
			helper.assertTrue(data.toggleRoute(hall, ashford), "no route");
			List<String> before = snapshot(board(helper, player, hall));
			String nameLine = TradePage.knownAndShort(level, hall).getString();

			Caravans.Data loaded = Caravans.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(loaded.market(hall).equals(data.market(hall)) && loaded.partners(hall).contains(ashford), "the reload lost something");
			// wipe what's in memory, then put back only what came out of the save
			Market ours = loaded.market(hall);
			Market theirs = loaded.market(ashford);
			data.setMarket(hall, Market.EMPTY);
			data.setMarket(ashford, Market.EMPTY);
			helper.assertTrue(!snapshot(board(helper, player, hall)).equals(before), "the board didn't notice the market going");
			data.setMarket(hall, ours);
			data.setMarket(ashford, theirs);
			List<String> after = snapshot(board(helper, player, hall));
			helper.assertTrue(after.equals(before), "the board changed over a reload:\n" + before + "\n" + after);
			helper.assertTrue(after.stream().anyMatch(l -> l.contains("Dearer in Ashford: " + e(163, "1.63 emeralds"))), "the partner's price went: " + after);
			helper.assertTrue(TradePage.knownAndShort(level, hall).getString().equals(nameLine) && nameLine.equals("Known for: Test Timber, Test Stone. Short of: Test Fish"),
				"the name line: " + nameLine);
			helper.succeed();
		});
	}

	/** Every good on the page as text: its name, its marks and its tooltip. */
	private static List<String> snapshot(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
			if (!menu.icon(s).isEmpty()) {
				out.add(menu.icon(s).getHoverName().getString() + " " + new java.util.TreeSet<>(TradePage.marks(menu.icon(s))) + " " + lore(menu.icon(s)));
			}
		}
		return out;
	}

	/**
	 * Before the hall's first count the board lists every good at its base price, steady and unmarked, and says the count
	 * is to come; with every good switched off it says there are none; a good whose file went stays out of the name line.
	 */
	//$ gametest_ticks_batch AREA '100' '"priceBoardEmpty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardEmpty")
	public void aVillageWithNoCountOrNoGoodsStillHasABoard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(hall, VillageHalls.name(level, hall), List.of());
			data.setMarket(hall, Market.EMPTY);
			ChoiceMenu menu = board(helper, player, hall);
			helper.assertTrue(lore(menu.icon(TradePage.Tab.PRICES.slot())).contains("Not worked out yet: the hall counts its goods once a day"),
				"the tab doesn't say the count is to come: " + lore(menu.icon(TradePage.Tab.PRICES.slot())));
			for (TradeGoods.Good good : TradeGoods.all()) {
				ItemStack icon = named(menu, good.name().getString());
				helper.assertTrue(icon != null && TradePage.marks(icon).equals(Set.of(TradePage.STEADY))
					&& lore(icon).contains("We pay: " + TradePage.money(good.basePrice()).getString()), good.id() + " before the count: " + icon);
			}
			helper.assertTrue(TradePage.knownAndShort(level, hall) == null && TradeTalk.lines(level, hall).isEmpty(), "known for something before the count");

			// a good whose file is gone stays in the saved lists but shows nowhere
			ResourceLocation gone = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "gone");
			market(helper, hall, List.of(gone, TIMBER), List.of(gone), prices(gone, 100, 50, TIMBER, 100, 100));
			helper.assertTrue(TradePage.knownAndShort(level, hall).getString().equals("Known for: Test Timber"), "a gone good named: "
				+ TradePage.knownAndShort(level, hall).getString());
			helper.assertTrue(lore(board(helper, player, hall).icon(TradePage.Tab.PRICES.slot())).stream().noneMatch(l -> l.startsWith("Not worked out")),
				"the tab still says the count is to come");

			// no goods at all (a data pack switched every one off)
			Map<ResourceLocation, com.google.gson.JsonElement> files = TradeGoods.files(level.getServer().getResourceManager());
			TradeGoods.load(Map.of());
			try {
				ChoiceMenu none = board(helper, player, hall);
				ItemStack note = none.icon(VillageHallScreen.FIRST_ROW + 4);
				helper.assertTrue(note.is(Items.PAPER) && note.getHoverName().getString().equals("No trade goods"), "no note for no goods: " + note);
				helper.assertTrue(TradePage.knownAndShort(level, hall) == null && TradeTalk.lines(level, hall).isEmpty(), "goods named with none loaded");
			} finally {
				TradeGoods.load(files); // the test's own clean-up then narrows and restores them as usual
				TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
			}
			helper.succeed();
		});
	}

	/**
	 * Villagers talk about trade: a good the village is known for that fetches more on a route, a glut at home, and
	 * something dear, each in three lines that name the good (and the village); nothing when none of it holds.
	 */
	//$ gametest_ticks_batch AREA '100' '"priceBoardTalk"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardTalk")
	public void villagersTalkAboutWhatSellsWellWhatsPlentyAndWhatsDear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		villager.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			// every price at its base, nothing known for or short of: nothing to say
			market(helper, hall, List.of(), List.of(), prices(TIMBER, 100, 100, FISH, TradeGoods.get(FISH).basePrice(), TradeGoods.get(FISH).basePrice()));
			helper.assertTrue(TradeTalk.lines(level, hall).isEmpty(), "talk with nothing to say: " + TradeTalk.lines(level, hall).stream().map(TradeTalk.Line::topic).toList());
			helper.assertTrue(Chatter.topics(level, villager, hall).stream().noneMatch(TradeTalk::is), "topics with nothing to say");

			int fishBase = TradeGoods.get(FISH).basePrice();
			market(helper, hall, List.of(TIMBER), List.of(FISH), prices(TIMBER, 80, 90, FISH, fishBase * 3 / 2, fishBase));
			BlockPos ashford = village(helper, "Ashford", 40);
			market(helper, ashford, List.of(), List.of(), prices(TIMBER, 140, 140));
			// known for timber below its base: a glut; fish short and over its base: dear; no route yet, so nothing sells well elsewhere
			List<String> topics = Chatter.topics(level, villager, hall);
			helper.assertTrue(topics.contains(TradeTalk.GLUT) && topics.contains(TradeTalk.DEAR) && !topics.contains(TradeTalk.SELLS_WELL), "without a route: " + topics);
			helper.assertTrue(data.toggleRoute(hall, ashford), "no route");
			topics = Chatter.topics(level, villager, hall);
			helper.assertTrue(topics.contains(TradeTalk.SELLS_WELL) && topics.contains(TradeTalk.GLUT) && topics.contains(TradeTalk.DEAR), "with a route: " + topics);

			String[][] lines = {
				{TradeTalk.SELLS_WELL, "Our Test Timber sells well in Ashford.", "They pay a fine price for our Test Timber over in Ashford.",
					"Ashford can't get enough of our Test Timber."},
				{TradeTalk.GLUT, "We've more Test Timber than we can use.", "Test Timber is going cheap here. The Storehouse is full of it.",
					"Somebody ought to send our Test Timber off with a caravan."},
				{TradeTalk.DEAR, "Test Fish is dear this week.", "Have you seen the price of Test Fish?", "We're short of Test Fish again. It costs a fortune."}};
			for (String[] topic : lines) {
				TradeTalk.Line line = TradeTalk.line(level, hall, topic[0]);
				helper.assertTrue(line != null, "no line for " + topic[0]);
				for (int variant = 0; variant < 3; variant++) {
					String said = Component.translatable("chatter.aliveworkplace." + topic[0] + "." + variant, line.args()).getString();
					helper.assertTrue(said.equals(topic[variant + 1]), topic[0] + "." + variant + ": " + said);
				}
			}
			// what a villager really says: one of the lines above turns up among what they say to a player
			Set<String> trade = new java.util.HashSet<>();
			for (String[] topic : lines) {
				trade.addAll(List.of(topic).subList(1, 4));
			}
			boolean said = false;
			for (int i = 0; i < 400 && !said; i++) {
				Component line = Chatter.line(level, villager, hall, player);
				said = line != null && trade.contains(line.getString());
			}
			helper.assertTrue(said, "in 400 lines nobody spoke of trade");

			// a good dear without being short of: a fifth over its base counts, a tenth doesn't
			market(helper, hall, List.of(), List.of(), prices(TIMBER, 110, 100));
			helper.assertTrue(TradeTalk.line(level, hall, TradeTalk.DEAR) == null, "a tenth over the base is dear");
			market(helper, hall, List.of(), List.of(), prices(TIMBER, 120, 100));
			helper.assertTrue(TradeTalk.line(level, hall, TradeTalk.DEAR) != null, "a fifth over the base isn't dear");
			helper.succeed();
		});
	}

	/** A Village Ledger opens the hall from afar, and its minecart reaches the Trade page and the Prices tab from there. */
	//$ gametest_ticks_batch AREA '100' '"priceBoardLedger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceBoardLedger")
	public void theVillageLedgerReachesTheTradePageFromAfar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
		player.setItemInHand(InteractionHand.MAIN_HAND, ledger);
		helper.runAfterDelay(2, () -> {
			market(helper, hall, List.of(TIMBER), List.of(), prices(TIMBER, 80, 90));
			VillageLedgerItem.bind(level, player, ledger, hall);
			BlockPos far = helper.absolutePos(new BlockPos(16, 2, 16)); // well past the 8 blocks the hall's own screen allows
			player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			helper.assertTrue(player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hall)) > 64, "the player isn't far from the hall");
			helper.assertTrue(ledger.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "the bound ledger didn't open");
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "no hall screen: " + player.containerMenu);
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			menu.press(VillageHallScreen.ROUTES, player);
			helper.assertTrue(TradePage.marks(menu.icon(TradePage.Tab.ROUTES.slot())).contains(TradePage.OPEN) && menu.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD),
				"the ledger's minecart didn't open the Trade page: " + menu.icon(TradePage.Tab.ROUTES.slot()));
			menu.press(TradePage.Tab.PRICES.slot(), player);
			ItemStack timber = named(menu, "Test Timber");
			helper.assertTrue(timber != null && TradePage.marks(timber).contains(TradePage.STAR), "no starred timber from afar: " + timber);
			helper.assertTrue(player.containerMenu == menu && menu.stillValid(player), "the page closed on the player from afar");
			menu.press(TradePage.BACK, player);
			helper.assertTrue(menu.icon(0).is(Items.NAME_TAG), "not back at the hall from afar");
			player.closeContainer();
			helper.succeed();
		});
	}

	/** Prices as players read them, and what the village sells at: 10% over what it pays, to the cent. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pricesReadInEmeraldsToTheCent(GameTestHelper helper) {
		String[][] decimals = {{"88", "0.88"}, {"140", "1.4"}, {"100", "1"}, {"5", "0.05"}, {"200", "2"}, {"1005", "10.05"}, {"50", "0.5"}, {"0", "0"}};
		for (String[] d : decimals) {
			helper.assertTrue(TradePage.decimal(Integer.parseInt(d[0])).equals(d[1]), d[0] + " cents reads " + TradePage.decimal(Integer.parseInt(d[0])));
		}
		helper.assertTrue(TradePage.money(100).getString().equals(e(100, "1 emerald")) && TradePage.money(88).getString().equals(e(88, "0.88 emeralds"))
			&& TradePage.money(200).getString().equals(e(200, "2 emeralds")), "money: " + TradePage.money(100).getString() + ", " + TradePage.money(88).getString());
		helper.assertTrue(TradePage.selling(80) == 88 && TradePage.selling(100) == 110 && TradePage.selling(5) == 6 && TradePage.selling(72) == 79,
			"10% over: " + TradePage.selling(80) + " " + TradePage.selling(5) + " " + TradePage.selling(72));
		// marks are kept in order, once each, and no other icon has any
		ItemStack icon = new ItemStack(Items.OAK_LOG);
		helper.assertTrue(TradePage.marks(icon).isEmpty(), "marks on a plain stack");
		TradePage.mark(icon, TradePage.STAR);
		TradePage.mark(icon, TradePage.UP);
		TradePage.mark(icon, TradePage.STAR);
		helper.assertTrue(List.copyOf(TradePage.marks(icon)).equals(List.of(TradePage.STAR, TradePage.UP)), "marks: " + TradePage.marks(icon));
		helper.succeed();
	}
}
