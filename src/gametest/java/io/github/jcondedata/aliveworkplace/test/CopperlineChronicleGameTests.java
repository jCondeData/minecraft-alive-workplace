package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.ui.ChronicleLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.ListMenuLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The Chronicle, the shared list menu and the Pokémon Trader in Copperline's shell (ROADMAP 48.4, boards/Chronicle.png):
 * <ul>
 * <li>{@link ChronicleLayout} at GUI scale 2 (427x240, 480x270, 640x360) and 3 (320x180) inside the Hall's body, with
 * group words one and three times as long: the Show column and the list inside the body and apart, all seven filters
 * shown, inside the column, apart and clear of Back; the rows in order and apart, an open entry taller than a closed
 * one, and the list scrolling only when its rows don't fit;</li>
 * <li>the Chronicle page a player opens in a real hall: the entries newest first with their dates, every kind in exactly
 * one group, the Show filters' counts, a filter's click showing only its group (and the empty group's line), All
 * bringing every entry back, and Back still the first button;</li>
 * <li>{@link ListMenuLayout}: one to twelve tabs on the rail of icon cells at each size (the list keeps all but the cells' 50
 * pixels), inside the rail and the window, apart and
 * clickable, the list right of the rail and clear of the close X, the page arrows in the whisper line and clear of the list;
 * no tabs, no rail;</li>
 * <li>the Pokémon Trader's book on the lintel, clear of the plaque and the close X, and the list under the lintel and over
 * the whisper line; the new sentences in the lang file.</li>
 * </ul>
 */
public class CopperlineChronicleGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {640, 360}, {320, 180}};
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private static boolean clickable(int[][] boxes, int i) {
		int[] b = boxes[i];
		return CopperLayout.at(boxes, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0) == i;
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void chronicleFitsTheHallAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int longestGroup = 0;
		int countW = 3 * 6; // "100": a hall keeps Chronicle.MAX entries
		for (Chronicle.Group g : Chronicle.Group.values()) {
			longestGroup = Math.max(longestGroup, g.title().getString().length() * 6);
		}
		for (int[] screen : SCREENS) {
			boolean compact = CopperLayout.compact(screen[0], screen[1]);
			int[] win = CopperLayout.window(screen[0], screen[1]);
			for (int times : new int[]{1, 3}) {
				String at = screen[0] + "x" + screen[1] + " x" + times + ": ";
				int railW = CopperLayout.railWidth(win, 60 * times, true); // the Hall's section rail: icons beside the Chronicle
				int[] body = CopperLayout.body(win, railW);
				int columnW = ChronicleLayout.columnWidth(body, longestGroup * times, countW, compact);
				int[] column = ChronicleLayout.column(body, columnW);
				int[] list = ChronicleLayout.list(body, columnW);
				if (!within(column, body) || !within(list, body) || CopperLayout.overlap(column, list)) {
					problems.add(at + "the column " + box(column) + " or the list " + box(list) + " leaves the body " + box(body) + " or they overlap");
				}
				if (list[2] < (compact ? 120 : 200)) {
					problems.add(at + "the list is only " + list[2] + " wide");
				}
				int[][] filters = ChronicleLayout.filters(column, Chronicle.Group.values().length, compact);
				int[] back = ChronicleLayout.back(column);
				if (filters.length != Chronicle.Group.values().length) {
					problems.add(at + "only " + filters.length + " of the seven filters fit");
				}
				if (!within(back, column)) {
					problems.add(at + "Back " + box(back) + " leaves the column");
				}
				for (int i = 0; i < filters.length; i++) {
					if (!within(filters[i], column) || CopperLayout.overlap(filters[i], back) || !clickable(filters, i) || filters[i][3] < ChronicleLayout.FILTER_MIN) {
						problems.add(at + "filter " + i + " " + box(filters[i]) + " leaves the column, is under Back, can't be clicked or is too short");
					}
				}
				// three days: a header and two entries each, the second entry open over 4 lines
				boolean[] header = {true, false, false, true, false, false, true, false, false};
				int[] heights = new int[header.length];
				for (int i = 0; i < header.length; i++) {
					heights[i] = header[i] ? 0 : ChronicleLayout.entryHeight(i == 2 ? 4 : 1, i == 2);
				}
				if (heights[2] <= ChronicleLayout.ENTRY_H || ChronicleLayout.entryHeight(1, true) < ChronicleLayout.ENTRY_H) {
					problems.add(at + "an open entry of 4 lines (" + heights[2] + ") isn't taller than a closed one");
				}
				int[] tops = ChronicleLayout.rows(list, header, heights, 0);
				for (int i = 0; i + 1 < header.length; i++) {
					int h = header[i] ? ChronicleLayout.DAY_H : heights[i];
					if (tops[i] + h > tops[i + 1] || tops[i] < list[1]) {
						problems.add(at + "row " + i + " at " + tops[i] + " (" + h + " tall) runs into row " + (i + 1) + " at " + tops[i + 1]);
					}
				}
				int content = tops[header.length];
				int max = ChronicleLayout.maxScroll(list, content);
				if ((content > list[3]) != (max > 0)) {
					problems.add(at + "the list scrolls " + max + " for " + content + " pixels of rows in " + list[3]);
				}
				int[] scrolled = ChronicleLayout.rows(list, header, heights, max);
				int last = header.length - 1;
				if (max > 0 && scrolled[last] + heights[last] > list[1] + list[3]) {
					problems.add(at + "scrolled to the end, the last entry is still under the list's foot");
				}
				if (ChronicleLayout.textWidth(list) + ChronicleLayout.TEXT_X > list[2]) {
					problems.add(at + "an entry's text runs past the list");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	private static CompoundTag mark(ItemStack icon, String key) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(key) ? null : data.copyTag().getCompound(key);
	}

	private static List<String> entries(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (mark(menu.icon(slot), VillageHallScreen.CHRONICLE_ENTRY) != null) {
				out.add(menu.icon(slot).getHoverName().getString());
			}
		}
		return out;
	}

	//$ gametest_ticks_batch AREA '100' '"copperlineChronicle"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "copperlineChronicle")
	public void chronicleFiltersByGroupWithDates(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		helper.runAfterDelay(2, () -> {
			List<String> problems = new ArrayList<>();
			for (Chronicle.Kind kind : Chronicle.Kind.values()) {
				Chronicle.Group group = Chronicle.Group.of(kind);
				int in = 0;
				for (Chronicle.Group g : Chronicle.Group.values()) {
					in += g != Chronicle.Group.ALL && g.has(kind) ? 1 : 0;
				}
				if (group == Chronicle.Group.ALL || in != 1 || !Chronicle.Group.ALL.has(kind)) {
					problems.add(kind + " is in " + in + " groups besides All");
				}
			}
			int before = entity.chronicle().size();
			Chronicle.atHall(helper.getLevel(), hall, Chronicle.Kind.BIRTH, Component.literal("Ada was born"), 3);
			Chronicle.atHall(helper.getLevel(), hall, Chronicle.Kind.BUILT, Component.literal("The Storehouse reached level 3"), 47);
			Chronicle.atHall(helper.getLevel(), hall, Chronicle.Kind.RAID, Component.literal("Raid at the south gate"), 47);
			Chronicle.atHall(helper.getLevel(), hall, Chronicle.Kind.FESTIVAL, Component.literal("The Harvest Fair"), 50);
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderChronicle(m, helper.getLevel(), hall));
			List<String> all = entries(menu);
			int total = before + 4;
			CompoundTag head = mark(menu.icon(4), VillageHallScreen.CHRONICLE_HEAD);
			if (head == null || head.getInt("total") != total) {
				problems.add("the title's count is " + (head == null ? "missing" : head.getInt("total")) + ", not " + total);
			}
			if (all.size() < 4 || !all.get(0).equals("The Harvest Fair") || !all.get(1).equals("Raid at the south gate")
				|| !all.get(2).equals("The Storehouse reached level 3")) {
				problems.add("the entries aren't newest first: " + all);
			}
			CompoundTag raid = null;
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
				if (menu.icon(slot).getHoverName().getString().equals("Raid at the south gate")) {
					raid = mark(menu.icon(slot), VillageHallScreen.CHRONICLE_ENTRY);
				}
			}
			Seasons.Date date = Seasons.date(47, Seasons.DAYS);
			if (raid == null || raid.getLong("day") != 47 || raid.getInt("season") != date.season().ordinal() || raid.getInt("of") != date.dayOfSeason()
				|| raid.getInt("group") != Chronicle.Group.DEFENSE.ordinal()) {
				problems.add("the raid's date or group is wrong: " + raid);
			}
			int[] counts = new int[Chronicle.Group.values().length];
			for (Chronicle.Group g : Chronicle.Group.values()) {
				CompoundTag f = mark(menu.icon(VillageHallScreen.CHRONICLE_FILTERS + g.ordinal()), VillageHallScreen.CHRONICLE_FILTER);
				if (f == null || f.getInt("group") != g.ordinal() || f.getBoolean("open") != (g == Chronicle.Group.ALL)) {
					problems.add("the " + g + " filter is missing or wrongly marked: " + f);
					continue;
				}
				counts[g.ordinal()] = f.getInt("count");
			}
			int sum = 0;
			for (int i = 1; i < counts.length; i++) {
				sum += counts[i];
			}
			if (counts[0] != total || sum != total || counts[Chronicle.Group.DEFENSE.ordinal()] < 1 || counts[Chronicle.Group.FESTIVALS.ordinal()] < 1) {
				problems.add("the filters' counts don't add up: " + java.util.Arrays.toString(counts) + " for " + total);
			}
			menu.press(VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.DEFENSE.ordinal(), player);
			List<String> defense = entries(menu);
			if (!defense.contains("Raid at the south gate") || defense.contains("The Harvest Fair") || defense.contains("Ada was born")
				|| defense.size() != counts[Chronicle.Group.DEFENSE.ordinal()]) {
				problems.add("Defense shows " + defense);
			}
			CompoundTag open = mark(menu.icon(VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.DEFENSE.ordinal()), VillageHallScreen.CHRONICLE_FILTER);
			if (open == null || !open.getBoolean("open")) {
				problems.add("Defense isn't marked open after its click");
			}
			if (counts[Chronicle.Group.TRADE.ordinal()] == 0) {
				menu.press(VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.TRADE.ordinal(), player);
				String none = menu.icon(VillageHallScreen.FIRST_ROW + 4).getHoverName().getString();
				if (!entries(menu).isEmpty() || !none.equals("No Trade entries yet")) {
					problems.add("an empty Trade filter reads '" + none + "' with " + entries(menu));
				}
			}
			menu.press(VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.ALL.ordinal(), player);
			if (!entries(menu).equals(all)) {
				problems.add("All doesn't bring every entry back: " + entries(menu));
			}
			if (!menu.icon(0).getHoverName().getString().equals("Back")) {
				problems.add("the first button isn't Back: " + menu.icon(0).getHoverName().getString());
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void listMenusAndTheTraderSitInTheShell(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] screen : SCREENS) {
			boolean compact = CopperLayout.compact(screen[0], screen[1]);
			int[] win = CopperLayout.window(screen[0], screen[1]);
			int[] close = CopperLayout.close(win);
			int[] whisper = CopperLayout.whisper(win);
			for (int tabs : new int[]{0, 1, 6, 12}) {
				for (int times : new int[]{1, 3}) {
					String at = screen[0] + "x" + screen[1] + " " + tabs + " tabs x" + times + ": ";
					ListMenuLayout.Boxes b = ListMenuLayout.layout(win, tabs, 54 * times, new int[]{50, 44}, compact);
					if (b.tabs().length != tabs || (tabs == 0) != (b.rail()[2] == 0)) {
						problems.add(at + b.tabs().length + " tabs laid out, rail " + box(b.rail()));
					}
					for (int i = 0; i < b.tabs().length; i++) {
						int[] t = b.tabs()[i];
						if (!within(t, b.rail()) || !within(t, win) || !clickable(b.tabs(), i) || t[3] < 9) {
							problems.add(at + "tab " + i + " " + box(t) + " leaves the rail " + box(b.rail()) + ", is too short or can't be clicked");
						}
					}
					int[] list = b.list();
					if (!within(list, win) || CopperLayout.overlap(list, b.rail()) || CopperLayout.overlap(list, close) || CopperLayout.overlap(list, whisper)
						|| list[2] < win[2] - 16 - 52 || list[3] < 60) {
						problems.add(at + "the list " + box(list) + " leaves the window, is under the rail, close X or whisper line, or gives the rail more than its icon cells");
					}
					for (int[] p : b.pages()) {
						if (!within(p, win) || CopperLayout.overlap(p, list) || p[1] < whisper[1] - 2) {
							problems.add(at + "a page arrow " + box(p) + " leaves the whisper line or covers the list");
						}
					}
					if (b.pages().length == 2 && (b.pages()[0][0] >= b.pages()[1][0] || CopperLayout.overlap(b.pages()[0], b.pages()[1]))) {
						problems.add(at + "the page arrows are out of order or overlap");
					}
					if (ListMenuLayout.whisperWidth(win, b.pages()) < 80) {
						problems.add(at + "the whisper line has only " + ListMenuLayout.whisperWidth(win, b.pages()) + " pixels beside the arrows");
					}
				}
			}
		}
		// The Pokémon Trader's window: the book on the lintel, clear of the plaque (at its widest, half the window) and
		// the close X; the offers under the lintel, the status lines over the whisper line.
		int[] win = {0, 0, PokemonTradeView.WIDTH, PokemonTradeView.HEIGHT};
		int[] book = {PokemonTradeView.INFO_X - 1, PokemonTradeView.INFO_Y - 1, 18, 18};
		int[] plaque = CopperLayout.plaque(win, 1000);
		if (CopperLayout.overlap(book, CopperLayout.close(win)) || CopperLayout.overlap(book, plaque) || !within(book, win)) {
			problems.add("the trader's book " + box(book) + " is under the close X or the plaque, or leaves the window");
		}
		if (CopperLayout.lintel(win, plaque)[0] + 40 > PokemonTradeView.INFO_X - 6) {
			problems.add("the trader's lintel has no room for today's offers");
		}
		int[] whisper = CopperLayout.whisper(win);
		if (PokemonTradeView.LIST_Y < CopperLayout.LINTEL_Y + CopperLayout.LINTEL_H + 1 || PokemonTradeView.LIST_Y + PokemonTradeView.LIST_H > whisper[1] - 2
			|| PokemonTradeView.STATUS_BOTTOM > whisper[1] - 2) {
			problems.add("the trader's list or status lines run into the lintel or the whisper line " + box(whisper));
		}
		for (String key : List.of("screen.aliveworkplace.hall.chronicle.show", "screen.aliveworkplace.hall.chronicle.where",
			"screen.aliveworkplace.hall.chronicle.where.one", "screen.aliveworkplace.hall.chronicle.count", "screen.aliveworkplace.hall.chronicle.count.one",
			"screen.aliveworkplace.hall.chronicle.day_header", "screen.aliveworkplace.hall.chronicle.hint", "screen.aliveworkplace.hall.chronicle.show_whisper",
			"screen.aliveworkplace.hall.chronicle.show_all_whisper", "screen.aliveworkplace.hall.chronicle.back_whisper",
			"screen.aliveworkplace.hall.chronicle.entry_whisper", "screen.aliveworkplace.hall.chronicle.open_whisper",
			"screen.aliveworkplace.hall.chronicle.close_whisper", "screen.aliveworkplace.hall.chronicle.filter_count",
			"screen.aliveworkplace.hall.chronicle.filter_count.one", "screen.aliveworkplace.hall.chronicle.none_shown",
			"screen.aliveworkplace.pokemon_trader.offers", "screen.aliveworkplace.pokemon_trader.offers.one",
			"screen.aliveworkplace.pokemon_trader.offers_none", "screen.aliveworkplace.pokemon_trader.offer_whisper",
			"screen.aliveworkplace.pokemon_trader.close_whisper", "screen.aliveworkplace.list.tab_whisper")) {
			if (Component.translatable(key).getString().equals(key)) {
				problems.add(key + " isn't in the lang file");
			}
		}
		for (Chronicle.Group g : Chronicle.Group.values()) {
			String key = "screen.aliveworkplace.hall.chronicle.group." + g.name().toLowerCase(java.util.Locale.ROOT);
			if (g.title().getString().equals(key)) {
				problems.add(key + " isn't in the lang file");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
