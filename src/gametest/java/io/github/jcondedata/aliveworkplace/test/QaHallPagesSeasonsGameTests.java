package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * QA (qa-1004-0733) for ROADMAP 22.5 (room on the Village Hall's screen) and 22.6 (one season calendar), written from
 * the items' Done when: six more pages fit and open through a real click, the villager list still pages correctly a
 * row lower, the calendar is one for the whole world and wired to the server's clock, years roll over for every
 * allowed season length with one festival a season, and the calendar's sentences are all translated.
 */
public class QaHallPagesSeasonsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);
	private static final Item[] HEADERS = {Items.APPLE, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.MELON_SLICE, Items.COOKIE};

	/** Opens the hall as a player standing next to it does, and returns its menu. */
	private static ChoiceMenu openHall(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		VillageHallScreen.open(player, hall);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	/**
	 * 22.5: "room for at least six more pages": six pages registered with one call each get their own tab after the
	 * calendar, a real click opens each with its header and content, back returns to the main screen with every old
	 * button in place; a duplicate id and a page past the row's room are refused.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallPagesSixMore"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallPagesSixMore")
	public void sixMorePagesFitAndOpen(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		int before = HallPages.all().size();
		List<String> ids = new ArrayList<>();
		try {
			for (int i = 0; i < HEADERS.length; i++) {
				String id = "qa_page_" + i;
				Item header = HEADERS[i];
				HallPages.register(id, (l, h) -> new ItemStack(header), (l, h) -> new ItemStack(header),
					(menu, l, h, viewer) -> menu.button(VillageHallScreen.FIRST_ROW, new ItemStack(Items.DIAMOND), null));
				ids.add(id);
			}
		} catch (IllegalStateException e) {
			ids.forEach(HallPages::unregister);
			throw new net.minecraft.gametest.framework.GameTestAssertException("no room for six more pages after " + before + ": " + e.getMessage());
		}
		helper.runAfterDelay(2, () -> {
			try {
				ChoiceMenu menu = openHall(helper, player);
				Set<Integer> slots = new HashSet<>();
				for (int i = 0; i < ids.size(); i++) {
					int slot = HallPages.slot(ids.get(i));
					helper.assertTrue(slot >= VillageHallScreen.PAGE_ROW && slot < VillageHallScreen.PAGE_ROW + 9 && slots.add(slot),
						"page " + ids.get(i) + " has no tab of its own in the page row: slot " + slot);
					helper.assertTrue(menu.icon(slot).is(HEADERS[i]), "tab " + slot + " shows " + menu.icon(slot));
				}
				helper.assertTrue(HallPages.slot(Seasons.PAGE) == VillageHallScreen.PAGE_ROW, "the calendar isn't still the first tab");
				for (int i = 0; i < ids.size(); i++) {
					click(menu, HallPages.slot(ids.get(i)), player);
					helper.assertTrue(menu.icon(4).is(HEADERS[i]), "page " + ids.get(i) + " opened with header " + menu.icon(4));
					helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.DIAMOND), "page " + ids.get(i) + "'s content is missing");
					helper.assertTrue(menu.icon(0).is(Items.ARROW), "no back button on " + ids.get(i));
					click(menu, 0, player);
					helper.assertTrue(menu.icon(VillageHallScreen.NAME).is(Items.NAME_TAG) && menu.icon(VillageHallScreen.QUESTS).is(Items.MAP)
						&& menu.icon(VillageHallScreen.CHRONICLE).is(Items.WRITTEN_BOOK) && menu.icon(VillageHallScreen.ROUTES).is(Items.CHEST_MINECART)
						&& menu.icon(VillageHallScreen.ADVICE).is(Items.COMPASS) && menu.icon(VillageHallScreen.FESTIVAL).is(Items.FIREWORK_ROCKET),
						"back from " + ids.get(i) + ": an old button moved");
				}
				boolean duplicate = false;
				try {
					HallPages.register(ids.get(0), (l, h) -> ItemStack.EMPTY, (l, h) -> ItemStack.EMPTY, (m, l, h, v) -> { });
				} catch (IllegalStateException e) {
					duplicate = true;
				}
				helper.assertTrue(duplicate, "a second page with the same id was accepted");
				int extra = 0;
				boolean full = false;
				try {
					while (extra < 20) {
						HallPages.register("qa_fill_" + extra, (l, h) -> ItemStack.EMPTY, (l, h) -> ItemStack.EMPTY, (m, l, h, v) -> { });
						extra++;
					}
				} catch (IllegalStateException e) {
					full = true;
				}
				helper.assertTrue(full && HallPages.all().size() == HallPages.MAX, "the row took " + HallPages.all().size() + " pages, room for "
					+ HallPages.MAX);
				player.closeContainer();
			} finally {
				ids.forEach(HallPages::unregister);
				for (int i = 0; i < 20; i++) {
					HallPages.unregister("qa_fill_" + i);
				}
			}
			helper.assertTrue(HallPages.all().size() == before, "pages left behind: " + HallPages.all().size());
			helper.succeed();
		});
	}

	/**
	 * 22.5 moved the villager list a row lower: a village one villager past a full page still lists everyone, the
	 * first page full, the rest behind the next-page button, and back again.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallPeoplePages"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallPeoplePages")
	public void everyVillagerIsListedAcrossPages(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		int perPage = VillageHallScreen.PER_PAGE; // 25: the list's 27 slots less the arrows in its bottom corners (30.4)
		int spawned = 0;
		for (int x = 1; x <= 15 && spawned < perPage + 1; x += 2) {
			for (int z = 1; z <= 15 && spawned < perPage + 1; z += 2) {
				if (x == HALL.getX() && z == HALL.getZ()) {
					continue;
				}
				Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
				villager.setNoAi(true);
				spawned++;
			}
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(5, () -> {
			ChoiceMenu menu = openHall(helper, player);
			int first = listed(menu);
			helper.assertTrue(first == perPage, "the first page lists " + first + " villagers, a full page is " + perPage);
			helper.assertTrue(perPage == 25, "people a page: " + perPage);
			helper.assertTrue(menu.icon(53).is(Items.SPECTRAL_ARROW), "no next-page button in slot 53: " + menu.icon(53));
			helper.assertTrue(menu.icon(9).is(Items.LECTERN), "slot 9 isn't the Book of Edicts: " + menu.icon(9));
			click(menu, 53, player);
			int second = listed(menu);
			helper.assertTrue(first + second >= perPage + 1, "only " + (first + second) + " of " + (perPage + 1) + "+ villagers listed");
			helper.assertTrue(menu.icon(45).is(Items.ARROW), "no previous-page button in slot 45 on page 2: " + menu.icon(45));
			helper.assertTrue(menu.icon(VillageHallScreen.PAGE_ROW).is(Items.CLOCK), "the page row is gone on page 2");
			click(menu, 45, player);
			helper.assertTrue(listed(menu) == perPage, "back on page 1: " + listed(menu));
			player.closeContainer();
			helper.succeed();
		});
	}

	private static int listed(ChoiceMenu menu) {
		int n = 0;
		for (int slot = VillageHallScreen.FIRST_PERSON; slot < ChoiceMenu.SIZE; slot++) {
			if (!menu.icon(slot).isEmpty() && slot != VillageHallScreen.PREVIOUS && slot != VillageHallScreen.NEXT) {
				n++;
			}
		}
		return n;
	}

	/**
	 * 22.6: "the same for the whole world": the Nether reads the overworld's date, and the calendar runs off the server's
	 * own clock (it has caught up with today without anyone calling it).
	 */
	//$ gametest_ticks 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE' '100'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void oneCalendarForTheWholeWorld(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel().getServer().overworld();
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no Nether in the test server");
		helper.assertTrue(Seasons.today(nether).equals(Seasons.today(overworld)), "the Nether's date " + Seasons.today(nether)
			+ " isn't the overworld's " + Seasons.today(overworld));
		// Polled, so a test elsewhere moving the clock to a new day can't make this flaky: the calendar looks every 20 ticks.
		helper.succeedWhen(() -> helper.assertTrue(Seasons.lastDay(overworld) == Chronicle.day(overworld),
			"the server's clock didn't move the calendar: last day " + Seasons.lastDay(overworld) + ", today " + Chronicle.day(overworld)));
	}

	/**
	 * 22.6 rollover for every kind of season length the config allows (1 to 120): two years walked a day at a time fire
	 * a new day every day, a new season exactly at each season's first day in order, and one festival per season on a
	 * day inside that season.
	 */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void twoYearsDayByDayForEveryLength(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int length : new int[] {1, 2, 3, 7, 8, 9, 120}) {
			Seasons.Data data = new Seasons.Data(1);
			int days = 0;
			List<Seasons.Season> seasons = new ArrayList<>();
			int festivals = 0;
			Set<String> festivalSeasons = new HashSet<>();
			for (long day = 2; day <= 8L * length + 1; day++) {
				Seasons.Date date = Seasons.date(day, length);
				helper.assertTrue(date.dayOfSeason() >= 1 && date.dayOfSeason() <= length, length + "-day seasons: day " + day + " is " + date);
				Seasons.Fired fired = Seasons.fire(level, data, day, length);
				helper.assertTrue(fired.newDay(), length + "-day seasons: day " + day + " fired no new day");
				days++;
				helper.assertTrue(fired.newSeason() == (date.dayOfSeason() == 1), length + "-day seasons: day " + day + " (" + date
					+ ") new season " + fired.newSeason());
				if (fired.newSeason()) {
					seasons.add(date.season());
				}
				if (fired.festival()) {
					festivals++;
					helper.assertTrue(festivalSeasons.add(date.year() + " " + date.season()), length + "-day seasons: a second festival in " + date);
				}
				helper.assertTrue(!Seasons.fire(level, data, day, length).newDay(), length + "-day seasons: day " + day + " fired twice");
			}
			helper.assertTrue(days == 8 * length, length + "-day seasons: " + days + " new days in two years");
			helper.assertTrue(seasons.equals(List.of(Seasons.Season.SUMMER, Seasons.Season.AUTUMN, Seasons.Season.WINTER, Seasons.Season.SPRING,
				Seasons.Season.SUMMER, Seasons.Season.AUTUMN, Seasons.Season.WINTER, Seasons.Season.SPRING)), length + "-day seasons: " + seasons);
			helper.assertTrue(festivals == 8, length + "-day seasons: " + festivals + " festivals in eight seasons");
			Seasons.Date nextYear = Seasons.date(4L * length + 1, length);
			helper.assertTrue(nextYear.year() == 2 && nextYear.season() == Seasons.Season.SPRING && nextYear.dayOfSeason() == 1,
				length + "-day seasons: the year doesn't turn after winter: " + nextYear);
		}
		helper.succeed();
	}

	/** Every sentence the calendar shows a player is translated and gives today's day of the season. */
	//$ gametest_ticks_batch AREA '100' '"qaCalendarWords"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCalendarWords")
	public void theCalendarReadsInEnglish(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openHall(helper, player);
			Seasons.Date today = Seasons.today(level);
			ItemStack tab = menu.icon(VillageHallScreen.PAGE_ROW);
			String name = tab.getHoverName().getString();
			helper.assertTrue(name.contains(today.season().title().getString()) && name.contains("day " + today.dayOfSeason() + " of " + today.length()),
				"the calendar tab says '" + name + "' on " + today);
			List<String> words = new ArrayList<>(text(tab));
			click(menu, VillageHallScreen.PAGE_ROW, player);
			words.addAll(text(menu.icon(4)));
			int seasons = 0;
			for (int slot : Seasons.SEASON_SLOTS) {
				helper.assertTrue(!menu.icon(slot).isEmpty(), "season slot " + slot + " is empty");
				words.addAll(text(menu.icon(slot)));
				seasons++;
			}
			helper.assertTrue(seasons == 4, "seasons shown: " + seasons);
			for (String line : words) {
				helper.assertTrue(!line.contains("aliveworkplace.") && !line.contains("%"), "untranslated calendar text: '" + line + "'");
			}
			player.closeContainer();
			helper.succeed();
		});
	}

	private static List<String> text(ItemStack stack) {
		List<String> out = new ArrayList<>();
		out.add(stack.getHoverName().getString());
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				out.add(line.getString());
			}
		}
		return out;
	}
}
