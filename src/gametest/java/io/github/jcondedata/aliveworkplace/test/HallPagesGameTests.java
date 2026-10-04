package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ROADMAP 22.5 and 22.6: the Village Hall's page row (a tab per registered page, with room for more) and the village
 * calendar it shows first. Every page that was on the hall before still opens from its old button and comes back.
 */
public class HallPagesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	/** Every page of the hall opens through the new layout, shows its header and goes back to the main screen. */
	//$ gametest_ticks_batch AREA '100' '"hallPagesEveryPageOpens"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallPagesEveryPageOpens")
	public void everyPageOpens(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			mainScreen(helper, menu, "at first");
			helper.assertTrue(!menu.icon(VillageHallScreen.FIRST_PERSON).isEmpty(), "the villager isn't listed under the page row");
			int[] buttons = {VillageHallScreen.QUESTS, VillageHallScreen.CHRONICLE, VillageHallScreen.ROUTES, VillageHallScreen.ADVICE};
			Item[] headers = {Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, Items.CHEST_MINECART, Items.COMPASS};
			for (int i = 0; i < buttons.length; i++) {
				menu.press(buttons[i], player);
				helper.assertTrue(menu.icon(4).is(headers[i]), "button " + buttons[i] + " opened " + menu.icon(4));
				helper.assertTrue(menu.icon(0).is(Items.ARROW), "no way back from page " + buttons[i]);
				menu.press(0, player);
				mainScreen(helper, menu, "back from page " + buttons[i]);
			}
			for (HallPages.Page page : HallPages.all()) {
				menu.press(HallPages.slot(page.id()), player);
				helper.assertTrue(!menu.icon(4).isEmpty(), "page " + page.id() + " has no header");
				helper.assertTrue(menu.icon(0).is(Items.ARROW), "no way back from page " + page.id());
				menu.press(0, player);
				mainScreen(helper, menu, "back from page " + page.id());
			}
			helper.succeed();
		});
	}

	private static void mainScreen(GameTestHelper helper, ChoiceMenu menu, String when) {
		helper.assertTrue(menu.icon(0).is(Items.NAME_TAG), when + ": the name isn't first: " + menu.icon(0));
		helper.assertTrue(menu.icon(VillageHallScreen.QUESTS).is(Items.MAP), when + ": quests moved: " + menu.icon(VillageHallScreen.QUESTS));
		helper.assertTrue(menu.icon(VillageHallScreen.CHRONICLE).is(Items.WRITTEN_BOOK), when + ": the chronicle moved");
		helper.assertTrue(menu.icon(VillageHallScreen.ROUTES).is(Items.CHEST_MINECART), when + ": the routes moved");
		helper.assertTrue(menu.icon(VillageHallScreen.ADVICE).is(Items.COMPASS), when + ": the advice moved");
		helper.assertTrue(menu.icon(VillageHallScreen.MAP).is(Items.FILLED_MAP), when + ": the map moved");
		helper.assertTrue(menu.icon(VillageHallScreen.RECALL).is(Items.BELL), when + ": the bell moved");
		helper.assertTrue(menu.icon(VillageHallScreen.FESTIVAL).is(Items.FIREWORK_ROCKET), when + ": the festival moved");
		helper.assertTrue(menu.icon(VillageHallScreen.PAGE_ROW).is(Items.CLOCK), when + ": the calendar isn't the first tab: "
			+ menu.icon(VillageHallScreen.PAGE_ROW));
		for (int slot = VillageHallScreen.PAGE_ROW + HallPages.all().size(); slot < VillageHallScreen.PAGE_ROW + 9; slot++) {
			helper.assertTrue(menu.icon(slot).is(Items.LIGHT_GRAY_STAINED_GLASS_PANE), when + ": free tab " + slot + " is " + menu.icon(slot));
		}
	}

	/** A new page is one call: it gets the next tab, opens with its header and content, and the row holds nine. */
	//$ gametest_ticks_batch AREA '100' '"hallPagesRegister"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallPagesRegister")
	public void aNewPageIsOneCall(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			List<String> added = new ArrayList<>();
			try {
				int before = HallPages.all().size();
				HallPages.register("test_cup", (level, h) -> named(Items.GOLD_INGOT, "Cup"), (level, h) -> named(Items.GOLD_BLOCK, "The Cup"),
					(menu, level, h, viewer) -> menu.button(VillageHallScreen.FIRST_ROW, named(Items.DIAMOND, "Winner"), null));
				added.add("test_cup");
				helper.assertTrue(HallPages.slot("test_cup") == VillageHallScreen.PAGE_ROW + before, "slot " + HallPages.slot("test_cup"));
				ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
				helper.assertTrue(menu.icon(HallPages.slot("test_cup")).is(Items.GOLD_INGOT), "tab: " + menu.icon(HallPages.slot("test_cup")));
				menu.press(HallPages.slot("test_cup"), player);
				helper.assertTrue(menu.icon(4).is(Items.GOLD_BLOCK), "header: " + menu.icon(4));
				helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.DIAMOND), "content: " + menu.icon(VillageHallScreen.FIRST_ROW));
				helper.assertTrue(menu.icon(9).is(Items.GRAY_STAINED_GLASS_PANE), "no divider under the header");
				menu.press(0, player);
				helper.assertTrue(menu.icon(0).is(Items.NAME_TAG), "back didn't go home");
				boolean twice = false;
				try {
					HallPages.register("test_cup", (level, h) -> ItemStack.EMPTY, (level, h) -> ItemStack.EMPTY, (m, level, h, v) -> {
					});
				} catch (IllegalStateException e) {
					twice = true;
				}
				helper.assertTrue(twice, "the same page was registered twice");
				// At least six more pages fit (ROADMAP 22.5): fill the row.
				helper.assertTrue(HallPages.MAX - before >= 7, "room for " + (HallPages.MAX - before) + " pages besides the calendar");
				while (HallPages.all().size() < HallPages.MAX) {
					String id = "test_fill_" + HallPages.all().size();
					HallPages.register(id, (level, h) -> named(Items.PAPER, id), (level, h) -> named(Items.PAPER, id), (m, level, h, v) -> {
					});
					added.add(id);
				}
				boolean full = false;
				try {
					HallPages.register("test_overflow", (level, h) -> ItemStack.EMPTY, (level, h) -> ItemStack.EMPTY, (m, level, h, v) -> {
					});
					added.add("test_overflow");
				} catch (IllegalStateException e) {
					full = true;
				}
				helper.assertTrue(full, "a tenth page was let in");
				menu = VillageHallScreen.forTest(player, hall);
				helper.assertTrue(menu.icon(VillageHallScreen.PAGE_ROW + 8).is(Items.PAPER), "the ninth tab: " + menu.icon(VillageHallScreen.PAGE_ROW + 8));
			} finally {
				added.forEach(HallPages::unregister);
			}
			helper.assertTrue(HallPages.slot("test_cup") == -1, "unregister");
			helper.succeed();
		});
	}

	private static ItemStack named(Item item, String name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	/** The calendar's tab and page: today's season and day, the festival, and the four seasons with today's lit. */
	//$ gametest_ticks_batch AREA '100' '"hallPagesCalendar"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallPagesCalendar")
	public void theCalendarPage(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
			Seasons.Date today = Seasons.today(helper.getLevel());
			ItemStack tab = menu.icon(HallPages.slot(Seasons.PAGE));
			String name = tab.getHoverName().getString();
			String expected = today.season().title().getString() + ", day " + today.dayOfSeason() + " of " + today.length();
			helper.assertTrue(name.equals(expected), "tab: '" + name + "', expected '" + expected + "'");
			String lore = lore(tab);
			helper.assertTrue(lore.contains("Year " + today.year()), "no year: " + lore);
			helper.assertTrue(lore.contains(today.season().festival().getString()), "no festival: " + lore);
			helper.assertTrue(lore.contains(today.season().next().title().getString()), "no next season: " + lore);
			helper.assertTrue(!lore.contains("%") && !lore.contains("season.aliveworkplace") && !lore.contains("screen.aliveworkplace"), "raw text: " + lore);
			menu.press(HallPages.slot(Seasons.PAGE), player);
			helper.assertTrue(menu.icon(4).is(Items.CLOCK), "header: " + menu.icon(4));
			for (Seasons.Season season : Seasons.Season.values()) {
				ItemStack icon = menu.icon(Seasons.SEASON_SLOTS[season.ordinal()]);
				helper.assertTrue(icon.is(season.icon), season + ": " + icon);
				boolean lit = Boolean.TRUE.equals(icon.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
				helper.assertTrue(lit == (season == today.season()), season + " lit: " + lit + ", today is " + today.season());
				String text = lore(icon);
				helper.assertTrue(text.contains(season.festival().getString()), season + " lore: " + text);
				helper.assertTrue(!text.contains("%") && !text.contains("screen.aliveworkplace"), "raw text: " + text);
			}
			helper.succeed();
		});
	}

	private static String lore(ItemStack icon) {
		StringBuilder out = new StringBuilder();
		var lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(line -> out.append(line.getString()).append('|'));
		}
		return out.toString();
	}

	/** Days fall into four seasons of the configured length; the year turns after winter; the festival is mid-season. */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void seasonsRollOver(GameTestHelper helper) {
		check(helper, Seasons.date(1, 8), Seasons.Season.SPRING, 1, 1);
		check(helper, Seasons.date(8, 8), Seasons.Season.SPRING, 8, 1);
		check(helper, Seasons.date(9, 8), Seasons.Season.SUMMER, 1, 1);
		check(helper, Seasons.date(17, 8), Seasons.Season.AUTUMN, 1, 1);
		check(helper, Seasons.date(32, 8), Seasons.Season.WINTER, 8, 1);
		check(helper, Seasons.date(33, 8), Seasons.Season.SPRING, 1, 2);
		check(helper, Seasons.date(0, 8), Seasons.Season.SPRING, 1, 1);
		helper.assertTrue(Seasons.date(5, 8).isFestival() && !Seasons.date(4, 8).isFestival() && !Seasons.date(6, 8).isFestival(), "festival on day 5");
		helper.assertTrue(Seasons.date(13, 8).isFestival(), "summer's festival on day 13");
		helper.assertTrue(Seasons.date(3, 8).daysToFestival() == 2 && Seasons.date(7, 8).daysToFestival() == -1, "days to the festival");
		helper.assertTrue(Seasons.date(8, 8).daysLeft() == 1 && Seasons.date(1, 8).daysLeft() == 8, "days left");
		// Other lengths.
		check(helper, Seasons.date(3, 1), Seasons.Season.AUTUMN, 1, 1);
		check(helper, Seasons.date(5, 1), Seasons.Season.SPRING, 1, 2);
		helper.assertTrue(Seasons.date(3, 1).isFestival(), "a one-day season is its festival");
		check(helper, Seasons.date(31, 30), Seasons.Season.SUMMER, 1, 1);
		helper.assertTrue(Seasons.festivalDayOf(30) == 16 && Seasons.festivalDayOf(2) == 2 && Seasons.festivalDayOf(7) == 4, "middle days");
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Seasons.Date date, Seasons.Season season, int day, long year) {
		helper.assertTrue(date.season() == season && date.dayOfSeason() == day && date.year() == year,
			"day " + date.day() + " of " + date.length() + "-day seasons: " + date + ", expected " + season + " day " + day + " year " + year);
	}

	/** {@code seasonDays} in the config: 16 when missing, kept within 1 to 120, and the calendar follows it; a
	 * file from before the change (no {@code configVersion}) holding the old default 8 moves to 16, and any other choice
	 * is kept (owner, 22.6a). */
	//$ gametest_batch 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE' '"seasonDaysConfig"'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE, batch = "seasonDaysConfig")
	public void theSeasonLengthIsConfigured(GameTestHelper helper) {
		helper.assertTrue(WorkplaceConfig.parse("{}").seasonDays == 16, "default " + WorkplaceConfig.parse("{}").seasonDays);
		helper.assertTrue(new WorkplaceConfig().seasonDays == 16, "a new config: " + new WorkplaceConfig().seasonDays);
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 8}").seasonDays == 16, "an old file's default 8 should become 16");
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 8, \"configVersion\": 2}").seasonDays == 8,
			"8 chosen in a new file should stay 8");
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 5}").seasonDays == 5, "an old file's own choice stays");
		helper.assertTrue(WorkplaceConfig.parse(new com.google.gson.Gson().toJson(WorkplaceConfig.parse("{\"seasonDays\": 8, \"configVersion\": 2}")))
			.seasonDays == 8, "a saved 8 should read back as 8");
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 12}").seasonDays == 12, "12");
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 0}").seasonDays == 1, "too short");
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 9999}").seasonDays == 120, "too long");
		try {
			WorkplaceConfig config = WorkplaceConfig.parse("{\"seasonDays\": 3}");
			config.apply();
			helper.assertTrue(Seasons.DAYS == 3, "not applied: " + Seasons.DAYS);
			Seasons.Date today = Seasons.today(helper.getLevel());
			helper.assertTrue(today.length() == 3 && today.dayOfSeason() <= 3, "today with 3-day seasons: " + today);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * The events: each new day fires once, a new season on its first day (and when days were skipped into another
	 * season), the festival on its day; a world's first day fires only the day; a day already seen (as after a reload)
	 * fires nothing, and the last day seen survives saving.
	 */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void theCalendarFiresEachDayOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Seasons.Data data = new Seasons.Data();
		fired(helper, Seasons.fire(level, data, 3, 8), true, false, false, "a world's first day (mid-spring)");
		fired(helper, Seasons.fire(level, data, 3, 8), false, false, false, "the same day again");
		fired(helper, Seasons.fire(level, data, 4, 8), true, false, false, "day 4");
		fired(helper, Seasons.fire(level, data, 5, 8), true, false, true, "day 5, the Blossom Fair");
		fired(helper, Seasons.fire(level, data, 9, 8), true, true, false, "skipped into summer");
		fired(helper, Seasons.fire(level, data, 33, 8), true, true, false, "skipped a whole year into the next spring");
		fired(helper, Seasons.fire(level, data, 41, 8), true, true, false, "spring to summer, skipped to its first day");
		fired(helper, Seasons.fire(level, data, 65, 8), true, true, false, "summer to the next year's spring");
		// Saved and loaded: the day already seen fires nothing; the next one does.
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		Seasons.Data loaded = Seasons.Data.load(saved, level.registryAccess());
		helper.assertTrue(loaded.lastDay() == 65, "last day after loading: " + loaded.lastDay());
		fired(helper, Seasons.fire(level, loaded, 65, 8), false, false, false, "after a reload, the same day");
		fired(helper, Seasons.fire(level, loaded, 66, 8), true, false, false, "after a reload, the next day");
		Seasons.Data old = Seasons.Data.load(new CompoundTag(), level.registryAccess());
		helper.assertTrue(old.lastDay() == 0, "a world saved before the calendar: " + old.lastDay());
		// Listeners hear it.
		List<String> heard = new ArrayList<>();
		Seasons.Data quiet = new Seasons.Data(16);
		boolean[] listening = {true};
		Seasons.onNewDay((l, d) -> {
			if (listening[0] && l == level) {
				heard.add("day " + d.day());
			}
		});
		Seasons.onNewSeason((l, d) -> {
			if (listening[0] && l == level) {
				heard.add("season " + d.season());
			}
		});
		Seasons.onFestival((l, d) -> {
			if (listening[0] && l == level) {
				heard.add("festival " + d.season().festival().getString());
			}
		});
		try {
			Seasons.fire(level, quiet, 17, 8);
			Seasons.fire(level, quiet, 21, 8);
		} finally {
			listening[0] = false;
		}
		helper.assertTrue(heard.equals(List.of("day 17", "season AUTUMN", "day 21", "festival the Harvest Feast")), "heard " + heard);
		helper.succeed();
	}

	private static void fired(GameTestHelper helper, Seasons.Fired fired, boolean day, boolean season, boolean festival, String when) {
		helper.assertTrue(fired.newDay() == day && fired.newSeason() == season && fired.festival() == festival, when + ": " + fired);
	}
}
