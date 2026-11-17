package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane c, 2026-11-17) for the price board (ROADMAP 33.2 to 33.4, docs/wiki/price-board.md), written from the wiki:
 * every boundary in it from both sides (an arrow and a dearer or cheaper village one cent either way, the talk's glut,
 * dear and sells-well thresholds), three known-for and three short-of goods at once, the hall broken under an open
 * board, an entry saved before 1.7, and the Ledger with the minecart reopening on Routes and with the economy off.
 * The goods are the gametest data pack's six (Timber, Fish, Grain and Stone at 100 cents a bundle).
 */
public class QaPriceBoard1117cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final ResourceLocation TIMBER = id("test_timber");
	private static final ResourceLocation GRAIN = id("test_grain");
	private static final ResourceLocation FISH = id("test_fish");
	private static final ResourceLocation STONE = id("test_stone");
	private static final ResourceLocation ARMS = id("test_arms");
	private static final ResourceLocation REMEDIES = id("test_remedies");

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", path);
	}

	/** A hall at {@link #HALL} with a small village radius and only the six test goods, alone in its batch. */
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

	/** A price as the board writes it: {@code emeralds}, or its CobbleDollars with the pack (the compat suite). */
	private static String e(int cents, String emeralds) {
		return io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars()
			? Math.round(cents * (double) io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD / 100) + " CobbleDollars" : emeralds;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** Another village on the caravans' list, {@code dx} blocks east of the hall (its entry only). */
	private static BlockPos village(GameTestHelper helper, String name, int dx) {
		BlockPos at = helper.absolutePos(HALL).east(dx);
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		data.setWants(at, Component.literal(name), List.of());
		Leftovers.after(helper, () -> data.remove(at));
		return at;
	}

	private static void market(GameTestHelper helper, BlockPos hall, List<ResourceLocation> knownFor, List<ResourceLocation> shortOf,
							   Map<ResourceLocation, Market.Price> prices) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		if (data.village(hall) == null) {
			data.setWants(hall, VillageHalls.name(level, hall), List.of());
		}
		data.setMarket(hall, new Market(knownFor, shortOf, prices, Chronicle.day(level), List.of()));
	}

	private static Map<ResourceLocation, Market.Price> prices(Object... goodNowYesterday) {
		Map<ResourceLocation, Market.Price> out = new LinkedHashMap<>();
		for (int i = 0; i < goodNowYesterday.length; i += 3) {
			out.put((ResourceLocation) goodNowYesterday[i], new Market.Price((Integer) goodNowYesterday[i + 1], (Integer) goodNowYesterday[i + 2], 0));
		}
		return out;
	}

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
		if (icon == null) {
			return out;
		}
		var lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

	/** The hall's screen, its minecart, then the Prices tab: the way a player gets there. */
	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		helper.assertTrue(menu.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD), "no Prices tab: " + menu.icon(TradePage.Tab.PRICES.slot()));
		menu.press(TradePage.Tab.PRICES.slot(), player);
		return menu;
	}

	private static String said(TradeTalk.Line line, int variant) {
		return Component.translatable("chatter.aliveworkplace." + line.topic() + "." + variant, line.args()).getString();
	}

	/**
	 * The arrow turns on a single cent either way ("Up from 1 emerald yesterday" at 1.01, "Down from 1 emerald
	 * yesterday" at 0.99, steady at 1), the singular "1 emerald" against the plural, and "We sell" is 10% over "We pay"
	 * rounded to the nearest cent (1.111 to 1.11, 1.089 to 1.09, 0.011 to 0.01).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceBoardArrow1117c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceBoardArrow1117c")
	public void theArrowTurnsOnASingleCentAndSellingRoundsToTheCent(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			market(helper, hall, List.of(), List.of(), prices(TIMBER, 101, 100, FISH, 99, 100, GRAIN, 100, 100, STONE, 1, 2));
			ChoiceMenu menu = board(helper, player, hall);

			ItemStack timber = named(menu, "Test Timber");
			List<String> t = lore(timber);
			helper.assertTrue(timber != null && TradePage.marks(timber).equals(Set.of(TradePage.UP)), "timber one cent up: " + (timber == null ? null : TradePage.marks(timber)));
			helper.assertTrue(t.contains("Up from " + e(100, "1 emerald") + " yesterday") && t.contains("We pay: " + e(101, "1.01 emeralds"))
				&& t.contains("We sell: " + e(111, "1.11 emeralds")), "timber's tooltip: " + t);

			ItemStack fish = named(menu, "Test Fish");
			List<String> f = lore(fish);
			helper.assertTrue(fish != null && TradePage.marks(fish).equals(Set.of(TradePage.DOWN)), "fish one cent down: " + (fish == null ? null : TradePage.marks(fish)));
			helper.assertTrue(f.contains("Down from " + e(100, "1 emerald") + " yesterday") && f.contains("We pay: " + e(99, "0.99 emeralds"))
				&& f.contains("We sell: " + e(109, "1.09 emeralds")), "fish's tooltip: " + f);

			ItemStack grain = named(menu, "Test Grain");
			List<String> g = lore(grain);
			helper.assertTrue(grain != null && TradePage.marks(grain).equals(Set.of(TradePage.STEADY)) && g.contains("Steady since yesterday")
				&& g.stream().noneMatch(l -> l.startsWith("Up from") || l.startsWith("Down from")), "grain steady: " + g);
			helper.assertTrue(g.contains("We pay: " + e(100, "1 emerald")) && g.contains("We sell: " + e(110, "1.1 emeralds")), "grain's prices: " + g);

			List<String> s = lore(named(menu, "Test Stone"));
			helper.assertTrue(s.contains("We pay: " + e(1, "0.01 emeralds")) && s.contains("Down from " + e(2, "0.02 emeralds") + " yesterday")
				&& s.contains("We sell: " + e(1, "0.01 emeralds")), "a cent's price: " + s);
			// nothing known for or short of: no star, no mark, no words for them
			for (String name : List.of("Test Timber", "Test Fish", "Test Grain", "Test Stone")) {
				ItemStack icon = named(menu, name);
				helper.assertTrue(!TradePage.marks(icon).contains(TradePage.STAR) && !TradePage.marks(icon).contains(TradePage.SHORT)
					&& lore(icon).stream().noneMatch(l -> l.endsWith("is known for it") || l.endsWith("is short of it")), name + " marked: " + lore(icon));
			}
			helper.succeed();
		});
	}

	/**
	 * Dearer and cheaper on the routes, at the edge: a village paying one cent more is dearer and one cent less is
	 * cheaper, the same price is neither; of two dearer villages the dearest is named; a village on a route that was
	 * never counted is left out; before this village's own first count it compares at its base price.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceBoardPartners1117c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceBoardPartners1117c")
	public void dearerAndCheaperTurnOnASingleCentAndTheDearestWins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			market(helper, hall, List.of(), List.of(), prices(TIMBER, 100, 100, FISH, 100, 100, GRAIN, 100, 100));
			BlockPos ashford = village(helper, "Ashford", 40);
			BlockPos ridgeway = village(helper, "Ridgeway", 60);
			BlockPos midby = village(helper, "Midby", 80);
			BlockPos bramble = village(helper, "Bramble", 100); // on a route, never counted
			market(helper, ashford, List.of(), List.of(), prices(TIMBER, 101, 101, FISH, 100, 100));
			market(helper, ridgeway, List.of(), List.of(), prices(TIMBER, 99, 99));
			market(helper, midby, List.of(), List.of(), prices(TIMBER, 130, 130, FISH, 150, 150));
			helper.assertTrue(data.toggleRoute(hall, ashford) && data.toggleRoute(ridgeway, hall) && data.toggleRoute(hall, midby)
				&& data.toggleRoute(hall, bramble), "no routes");

			ChoiceMenu menu = board(helper, player, hall);
			List<String> timber = lore(named(menu, "Test Timber"));
			helper.assertTrue(timber.contains("Dearer in Midby: " + e(130, "1.3 emeralds")), "the dearest of two dearer: " + timber);
			helper.assertTrue(timber.contains("Cheaper in Ridgeway: " + e(99, "0.99 emeralds")), "a cent cheaper: " + timber);
			helper.assertTrue(timber.stream().noneMatch(l -> l.contains("Ashford") || l.contains("Bramble")), "a less dear or uncounted village named: " + timber);
			List<String> fish = lore(named(menu, "Test Fish"));
			helper.assertTrue(fish.contains("Dearer in Midby: " + e(150, "1.5 emeralds")) && fish.stream().noneMatch(l -> l.startsWith("Cheaper"))
				&& !fish.contains("No dearer or cheaper on our trade routes"), "fish (Ashford pays the same): " + fish);
			List<String> grain = lore(named(menu, "Test Grain"));
			helper.assertTrue(grain.stream().noneMatch(l -> l.startsWith("Dearer") || l.startsWith("Cheaper")) && !grain.contains("No trade routes to compare prices with"),
				"grain (nobody has a price; there are routes): " + grain);

			// one cent dearer, with Midby off the routes
			data.toggleRoute(hall, midby);
			timber = lore(named(board(helper, player, hall), "Test Timber"));
			helper.assertTrue(timber.contains("Dearer in Ashford: " + e(101, "1.01 emeralds")) && timber.stream().noneMatch(l -> l.contains("Midby")),
				"a cent dearer: " + timber);

			// before this village's first count: it stands at its base (1 emerald) and compares from there
			data.setMarket(hall, Market.EMPTY);
			menu = board(helper, player, hall);
			timber = lore(named(menu, "Test Timber"));
			helper.assertTrue(timber.contains("We pay: " + e(100, "1 emerald")) && timber.contains("Dearer in Ashford: " + e(101, "1.01 emeralds"))
				&& timber.contains("Cheaper in Ridgeway: " + e(99, "0.99 emeralds")), "before the count: " + timber);
			helper.assertTrue(lore(menu.icon(TradePage.Tab.PRICES.slot())).contains("Not worked out yet: the hall counts its goods once a day"),
				"the tab before the count: " + lore(menu.icon(TradePage.Tab.PRICES.slot())));
			helper.succeed();
		});
	}

	/**
	 * Villagers' trade talk at each edge: a known-for good at its base is no glut, a cent under is; a good short of at
	 * its base isn't dear, a cent over is; unmarked, 19% over isn't dear; a known-for good paying the same on a route
	 * doesn't sell well, a cent more does, and of two villages the bigger gap is named; a good the village isn't known for
	 * never sells well, however dear elsewhere.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceBoardTalk1117c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceBoardTalk1117c")
	public void tradeTalkTurnsOnTheSpecsEdgesBothWays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			// glut
			market(helper, hall, List.of(TIMBER), List.of(), prices(TIMBER, 100, 100));
			helper.assertTrue(TradeTalk.line(level, hall, TradeTalk.GLUT) == null, "a glut at the base price");
			market(helper, hall, List.of(TIMBER), List.of(), prices(TIMBER, 99, 100));
			TradeTalk.Line glut = TradeTalk.line(level, hall, TradeTalk.GLUT);
			helper.assertTrue(glut != null && said(glut, 0).equals("We've more Test Timber than we can use."), "no glut a cent under: " + (glut == null ? null : said(glut, 0)));
			// a good not known for, under its base: no glut
			market(helper, hall, List.of(), List.of(), prices(TIMBER, 60, 100));
			helper.assertTrue(TradeTalk.line(level, hall, TradeTalk.GLUT) == null, "a glut of a good the village isn't known for");

			// dear, short of
			market(helper, hall, List.of(), List.of(FISH), prices(FISH, 100, 100));
			helper.assertTrue(TradeTalk.line(level, hall, TradeTalk.DEAR) == null, "short of at the base price is dear");
			market(helper, hall, List.of(), List.of(FISH), prices(FISH, 101, 100));
			TradeTalk.Line dear = TradeTalk.line(level, hall, TradeTalk.DEAR);
			helper.assertTrue(dear != null && said(dear, 1).equals("Have you seen the price of Test Fish?"), "short of a cent over isn't dear: " + (dear == null ? null : said(dear, 1)));
			// dear, not short of: 19% over isn't, 20% is (existing test: 10% and 20%)
			market(helper, hall, List.of(), List.of(), prices(FISH, 119, 100));
			helper.assertTrue(TradeTalk.line(level, hall, TradeTalk.DEAR) == null, "19% over its base is dear");

			// sells well
			BlockPos ashford = village(helper, "Ashford", 40);
			BlockPos ridgeway = village(helper, "Ridgeway", 60);
			helper.assertTrue(data.toggleRoute(hall, ashford) && data.toggleRoute(ridgeway, hall), "no routes");
			market(helper, hall, List.of(TIMBER), List.of(), prices(TIMBER, 100, 100, FISH, 100, 100));
			market(helper, ashford, List.of(), List.of(), prices(TIMBER, 100, 100, FISH, 300, 300));
			helper.assertTrue(TradeTalk.line(level, hall, TradeTalk.SELLS_WELL) == null, "sells well at the same price, or of a good not known for");
			market(helper, ashford, List.of(), List.of(), prices(TIMBER, 101, 101, FISH, 300, 300));
			TradeTalk.Line sells = TradeTalk.line(level, hall, TradeTalk.SELLS_WELL);
			helper.assertTrue(sells != null && said(sells, 0).equals("Our Test Timber sells well in Ashford."), "a cent more: " + (sells == null ? null : said(sells, 0)));
			market(helper, ridgeway, List.of(), List.of(), prices(TIMBER, 150, 150));
			sells = TradeTalk.line(level, hall, TradeTalk.SELLS_WELL);
			helper.assertTrue(sells != null && said(sells, 0).equals("Our Test Timber sells well in Ridgeway.")
				&& said(sells, 2).equals("Ridgeway can't get enough of our Test Timber."), "the biggest gap: " + (sells == null ? null : said(sells, 0) + " / " + said(sells, 2)));
			helper.succeed();
		});
	}

	/**
	 * Three goods known for and three short of at once: the name icon lists all six in the count's order, and on the
	 * board each known-for good has its star (and no red mark) and each short-of good its mark (and no star).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceBoardThree1117c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceBoardThree1117c")
	public void threeKnownForAndThreeShortOfAllShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			market(helper, hall, List.of(STONE, TIMBER, GRAIN), List.of(REMEDIES, FISH, ARMS), prices(TIMBER, 100, 100));
			String village = VillageHalls.name(level, hall).getString();
			List<String> nameIcon = lore(VillageHallScreen.forTest(player, hall).icon(0));
			helper.assertTrue(nameIcon.contains("Known for: Test Stone, Test Timber, Test Grain. Short of: Test Remedies, Test Fish, Test Arms"), "the name icon: " + nameIcon);
			ChoiceMenu menu = board(helper, player, hall);
			for (String name : List.of("Test Stone", "Test Timber", "Test Grain")) {
				ItemStack icon = named(menu, name);
				helper.assertTrue(icon != null && TradePage.marks(icon).contains(TradePage.STAR) && !TradePage.marks(icon).contains(TradePage.SHORT)
					&& lore(icon).contains(village + " is known for it") && lore(icon).stream().noneMatch(l -> l.endsWith("is short of it")), name + ": " + lore(icon));
			}
			for (String name : List.of("Test Remedies", "Test Fish", "Test Arms")) {
				ItemStack icon = named(menu, name);
				helper.assertTrue(icon != null && TradePage.marks(icon).contains(TradePage.SHORT) && !TradePage.marks(icon).contains(TradePage.STAR)
					&& lore(icon).contains(village + " is short of it") && lore(icon).stream().noneMatch(l -> l.endsWith("is known for it")), name + ": " + lore(icon));
			}
			helper.succeed();
		});
	}

	/**
	 * The hall broken under an open board: the page (reached by the player standing at the hall, and from afar with a
	 * Ledger) stops being valid, so it closes; the Ledger then won't open the broken hall.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceBoardBroken1117c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceBoardBroken1117c")
	public void breakingTheHallClosesAnOpenBoard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer near = player(helper);
		ServerPlayer far = player(helper);
		ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
		far.setItemInHand(InteractionHand.MAIN_HAND, ledger);
		helper.runAfterDelay(2, () -> {
			market(helper, hall, List.of(TIMBER), List.of(), prices(TIMBER, 80, 90));
			near.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
			VillageHallScreen.open(near, hall);
			helper.assertTrue(near.containerMenu instanceof ChoiceMenu, "the hall's screen didn't open: " + near.containerMenu);
			ChoiceMenu nearMenu = (ChoiceMenu) near.containerMenu;
			nearMenu.press(VillageHallScreen.ROUTES, near);
			nearMenu.press(TradePage.Tab.PRICES.slot(), near);
			helper.assertTrue(named(nearMenu, "Test Timber") != null && nearMenu.stillValid(near), "no board at the hall");

			VillageLedgerItem.bind(level, far, ledger, hall);
			BlockPos away = helper.absolutePos(new BlockPos(16, 2, 16));
			far.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			helper.assertTrue(ledger.use(level, far, InteractionHand.MAIN_HAND).getResult().consumesAction() && far.containerMenu instanceof ChoiceMenu,
				"the Ledger didn't open: " + far.containerMenu);
			ChoiceMenu farMenu = (ChoiceMenu) far.containerMenu;
			farMenu.press(VillageHallScreen.ROUTES, far);
			farMenu.press(TradePage.Tab.PRICES.slot(), far);
			helper.assertTrue(named(farMenu, "Test Timber") != null && farMenu.stillValid(far), "no board from afar");

			helper.destroyBlock(HALL);
			helper.assertTrue(!level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL), "the hall still stands");
			helper.assertTrue(!nearMenu.stillValid(near), "the board at a broken hall stays open");
			helper.assertTrue(!farMenu.stillValid(far), "the Ledger's board of a broken hall stays open");
			far.closeContainer();
			helper.assertTrue(!ledger.use(level, far, InteractionHand.MAIN_HAND).getResult().consumesAction() && !(far.containerMenu instanceof ChoiceMenu),
				"the Ledger opened a broken hall");
			near.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * A village entry saved before 1.7 (no knownFor, shortOf, prices, priceDay or demand) loads with an empty market,
	 * keeps its route, and draws the board at base prices, steady and unmarked, with the tab saying the count is still to
	 * come and nothing known for on the name icon; the partner's own saved prices still compare.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceBoardOldSave1117c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceBoardOldSave1117c")
	public void anEntrySavedBefore17ShowsBasePrices(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			market(helper, hall, List.of(TIMBER), List.of(FISH), prices(TIMBER, 60, 90, FISH, 180, 150));
			BlockPos ashford = village(helper, "Ashford", 40);
			market(helper, ashford, List.of(), List.of(), prices(TIMBER, 140, 140));
			helper.assertTrue(data.toggleRoute(hall, ashford), "no route");

			CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
			ListTag villages = saved.getList("villages", Tag.TAG_COMPOUND);
			boolean stripped = false;
			for (int i = 0; i < villages.size(); i++) {
				CompoundTag t = villages.getCompound(i);
				if (t.getLong("hall") == hall.asLong()) {
					helper.assertTrue(t.contains("prices") && t.contains("knownFor"), "the 1.7 keys weren't saved: " + t.getAllKeys());
					for (String key : List.of("knownFor", "shortOf", "prices", "priceDay", "demand")) {
						t.remove(key);
					}
					stripped = true;
				}
			}
			helper.assertTrue(stripped, "the hall's entry wasn't saved");
			Caravans.Data loaded = Caravans.Data.load(saved, level.registryAccess());
			helper.assertTrue(loaded.village(hall) != null && loaded.market(hall).isEmpty(), "the old entry: " + loaded.market(hall));
			helper.assertTrue(loaded.partners(hall).contains(ashford) && loaded.market(ashford).prices().get(TIMBER).cents() == 140, "the route or the partner's prices went");

			data.setMarket(hall, loaded.market(hall));
			ChoiceMenu menu = board(helper, player, hall);
			helper.assertTrue(lore(menu.icon(TradePage.Tab.PRICES.slot())).contains("Not worked out yet: the hall counts its goods once a day"),
				"the tab: " + lore(menu.icon(TradePage.Tab.PRICES.slot())));
			for (TradeGoods.Good good : TradeGoods.all()) {
				ItemStack icon = named(menu, good.name().getString());
				helper.assertTrue(icon != null && TradePage.marks(icon).equals(Set.of(TradePage.STEADY))
					&& lore(icon).contains("We pay: " + TradePage.money(good.basePrice()).getString()), good.id() + " from an old save: " + lore(icon));
			}
			helper.assertTrue(lore(named(menu, "Test Timber")).contains("Dearer in Ashford: " + e(140, "1.4 emeralds")), "the partner's price: " + lore(named(menu, "Test Timber")));
			helper.assertTrue(lore(VillageHallScreen.forTest(player, hall).icon(0)).stream().noneMatch(l -> l.startsWith("Known for") || l.startsWith("Short of")),
				"the name icon from an old save: " + lore(VillageHallScreen.forTest(player, hall).icon(0)));
			helper.succeed();
		});
	}

	/**
	 * Through a Ledger from afar: the minecart opens on Routes every time, even after the player last looked at Prices;
	 * with {@code villageEconomy} off the Ledger's minecart opens the old routes page, with no tabs.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPriceBoardLedgerOff1117c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPriceBoardLedgerOff1117c")
	public void theLedgersMinecartOpensOnRoutesAndOldPageWhenOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer player = player(helper);
		ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
		player.setItemInHand(InteractionHand.MAIN_HAND, ledger);
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		helper.runAfterDelay(2, () -> {
			market(helper, hall, List.of(TIMBER), List.of(), prices(TIMBER, 80, 90));
			VillageLedgerItem.bind(level, player, ledger, hall);
			BlockPos away = helper.absolutePos(new BlockPos(16, 2, 16));
			player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			helper.assertTrue(ledger.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction() && player.containerMenu instanceof ChoiceMenu,
				"the Ledger didn't open");
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(TradePage.Tab.PRICES.slot(), player);
			helper.assertTrue(TradePage.marks(menu.icon(TradePage.Tab.PRICES.slot())).contains(TradePage.OPEN), "Prices didn't open");
			menu.press(TradePage.BACK, player);
			menu.press(VillageHallScreen.ROUTES, player);
			helper.assertTrue(TradePage.marks(menu.icon(TradePage.Tab.ROUTES.slot())).contains(TradePage.OPEN)
				&& !TradePage.marks(menu.icon(TradePage.Tab.PRICES.slot())).contains(TradePage.OPEN) && named(menu, "Test Timber") == null,
				"the minecart reopened on Prices, not Routes");
			player.closeContainer();

			WorkplaceConfig.parse("{\"villageEconomy\": false}").apply();
			helper.assertTrue(ledger.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction() && player.containerMenu instanceof ChoiceMenu,
				"the Ledger didn't open with the economy off");
			ChoiceMenu off = (ChoiceMenu) player.containerMenu;
			helper.assertTrue(lore(off.icon(0)).stream().noneMatch(l -> l.startsWith("Known for")), "the name icon with the economy off: " + lore(off.icon(0)));
			off.press(VillageHallScreen.ROUTES, player);
			helper.assertTrue(off.icon(4).is(Items.CHEST_MINECART) && TradePage.marks(off.icon(4)).isEmpty(), "not the old routes page: " + off.icon(4));
			helper.assertTrue(off.icon(TradePage.Tab.PRICES.slot()).isEmpty() && named(off, "Test Timber") == null, "a Prices tab from afar with the economy off");
			player.closeContainer();
			helper.succeed();
		});
	}
}
