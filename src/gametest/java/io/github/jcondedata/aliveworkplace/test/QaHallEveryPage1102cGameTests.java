package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.PlayerBank;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-c, 2026-11-02): every section of the Village Hall's sidebar (ROADMAP 40.1, {@code docs/design/M40.md}),
 * opened through the hall's own right-click and the sidebar's clicks, and every page one click away that only shows
 * information (the page row's tabs, Buildings' looks, Quests, the Chronicle, Trade's routes, Advice, the Realm book,
 * Defense, the bank, the map's drawing, a jobless villager's job page and life story), in a hall with nobody around it
 * and in one with a single jobless villager and one bed. Every name and tooltip line is read for a raw translation key,
 * a leftover format marker, "null", "NaN" or a negative number; and each section in the empty hall shows a line of
 * text (40.1: "a section with nothing in it says so plainly"). Buttons that pay, hire, call or change something are
 * never clicked.
 */
public class QaHallEveryPage1102cGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** Main-page buttons whose click only opens a page of information. */
	private static final Set<Integer> INFO = Set.of(VillageHallScreen.BUILDS, VillageHallScreen.QUESTS, VillageHallScreen.CHRONICLE,
		VillageHallScreen.ROUTES, VillageHallScreen.ADVICE, VillageHallScreen.BOOK, VillageHallScreen.GUARDS, PlayerBank.BUTTON);

	private static final Pattern RAW_KEY = Pattern.compile("(aliveworkplace\\.|\\bscreen\\.[a-z]|\\bmessage\\.[a-z]|\\bitem\\.minecraft\\.|\\bblock\\.minecraft\\.|\\bentity\\.minecraft\\.)");
	private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?[sd]");
	private static final Pattern NULLS = Pattern.compile("\\bnull\\b|\\bNaN\\b|\\bInfinity\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEGATIVE = Pattern.compile("(^|[\\s(:,/])-\\d");

	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Section section) {
		player.closeContainer();
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, section.ordinal()), "the sidebar didn't take " + section);
		helper.assertTrue(HallSections.current(menu) == section, "the sidebar opened " + HallSections.current(menu) + ", not " + section);
		return menu;
	}

	private static String name(ItemStack icon) {
		return icon.getItem().toString();
	}

	/** Every name and tooltip line on the menu's buttons, as "where: text". */
	private static int read(ChoiceMenu menu, String where, Map<String, String> wrong, int[] counted) {
		int buttons = 0;
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty() || icon.has(DataComponents.HIDE_TOOLTIP)) {
				continue;
			}
			buttons++;
			String at = where + " slot " + slot + " (" + name(icon) + ")";
			check(at + " name", icon.getHoverName().getString(), wrong, counted);
			for (Component line : icon.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
				check(at + " lore", line.getString(), wrong, counted);
			}
		}
		return buttons;
	}

	private static void check(String where, String text, Map<String, String> wrong, int[] counted) {
		counted[0]++;
		List<String> why = new ArrayList<>();
		if (RAW_KEY.matcher(text).find()) {
			why.add("raw key");
		}
		if (FORMAT.matcher(text).find()) {
			why.add("format marker");
		}
		if (NULLS.matcher(text).find()) {
			why.add("null/NaN");
		}
		if (NEGATIVE.matcher(text).find()) {
			why.add("negative number");
		}
		if (!why.isEmpty()) {
			wrong.putIfAbsent("'" + text + "' " + why, where);
		}
	}

	/**
	 * Walks every section and every information page one click away; returns how many pages were read. A press that
	 * throws is recorded as wrong too.
	 */
	private static int walk(GameTestHelper helper, ServerPlayer player, String village, Map<String, String> wrong, int[] counted,
		List<String> blank, Villager person) {
		int pages = 0;
		BlockPos hall = helper.absolutePos(HALL);
		// The checks themselves: a key with no English line reads as the raw key here, and is caught.
		Map<String, String> canary = new LinkedHashMap<>();
		check("canary", Component.translatable("screen.aliveworkplace.hall.no_such_line_qa").getString(), canary, new int[1]);
		check("canary", Component.translatable("screen.aliveworkplace.hall.villagers", "%s").getString(), canary, new int[1]);
		helper.assertTrue(canary.size() == 2, "the checks miss a raw key or a format marker: " + canary);
		// The sidebar's own words: each section's title, its one line about it, its empty sentence, and the date.
		for (Section s : Section.values()) {
			check("sidebar " + s + " title", s.title().getString(), wrong, counted);
			check("sidebar " + s + " about", s.about().getString(), wrong, counted);
			if (s.canBeEmpty()) {
				check("sidebar " + s + " empty sentence", s.empty().getString(), wrong, counted);
			}
		}
		for (Section s : Section.values()) {
			ChoiceMenu menu = open(helper, player, s);
			check(village + " " + s + " date", HallSections.date(menu.state(HallSections.SEASON), menu.state(HallSections.DAY),
				menu.state(HallSections.YEAR)).getString(), wrong, counted);
			read(menu, village + " main page (all sections)", wrong, counted);
			pages++;
			List<Integer> slots = HallSections.slots(menu, s);
			boolean says = HallSections.isEmpty(menu, s);
			if (slots.isEmpty() && !says) {
				blank.add(village + " " + s + " (no button and no empty sentence)");
			}
			for (int slot : slots) {
				boolean tab = slot >= VillageHallScreen.PAGE_ROW && slot < VillageHallScreen.PAGE_ROW + 9;
				if (!tab && !INFO.contains(slot)) {
					continue;
				}
				ChoiceMenu fresh = open(helper, player, s);
				String label = fresh.icon(slot).getHoverName().getString();
				String where = village + " " + s + " > '" + label + "'";
				try {
					fresh.press(slot, player);
				} catch (RuntimeException e) {
					wrong.put("threw " + e, where);
					continue;
				}
				ChoiceMenu now = player.containerMenu instanceof ChoiceMenu m ? m : fresh;
				int buttons = read(now, where, wrong, counted);
				if (buttons == 0) {
					blank.add(where + " (a page with nothing on it)");
				}
				pages++;
			}
			if (s == Section.MAP) {
				check(village + " Map drawing", VillageMaps.draw(helper.getLevel(), hall, player).getString(), wrong, counted);
			}
		}
		if (person != null) {
			// A jobless villager's row: a plain click opens their job page, a shift-click their life story.
			ChoiceMenu menu = open(helper, player, Section.PEOPLE);
			int row = QaHallScreenGameTests.slotOf(helper, menu, person);
			read(menu, village + " People row", wrong, counted);
			menu.press(row, player);
			read(menu, village + " People > job page", wrong, counted);
			pages++;
			menu = open(helper, player, Section.PEOPLE);
			menu.clicked(QaHallScreenGameTests.slotOf(helper, menu, person), 0, ClickType.QUICK_MOVE, player);
			read(menu, village + " People > life story", wrong, counted);
			pages++;
		}
		return pages;
	}

	private static void report(GameTestHelper helper, String village, int pages, int[] counted, Map<String, String> wrong, List<String> blank) {
		StringBuilder out = new StringBuilder();
		wrong.forEach((text, where) -> out.append("\n  ").append(where).append(": ").append(text));
		helper.assertTrue(wrong.isEmpty(), village + ": " + wrong.size() + " bad strings of " + counted[0] + " read on " + pages + " pages:" + out);
		helper.assertTrue(blank.isEmpty(), village + ": sections/pages with no text: " + blank);
	}

	/** A hall with nobody and nothing round it: every section says something, and nothing a player reads is broken. */
	//$ gametest_ticks_batch AREA '200' '"qaHallEveryPageEmpty"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallEveryPageEmpty")
	public void everyPageOfAnEmptyHallReadsCleanly(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerPlayer player = QaHallScreenGameTests.payer(helper, new ArrayList<>(), 0);
		helper.runAfterDelay(3, () -> {
			Map<String, String> wrong = new LinkedHashMap<>();
			List<String> blank = new ArrayList<>();
			int[] counted = {0};
			int pages = walk(helper, player, "empty hall", wrong, counted, blank, null);
			report(helper, "empty hall", pages, counted, wrong, blank);
			helper.succeed();
		});
	}

	/** A hall with one jobless villager and one bed: the same walk, plus her row's job page and life story. */
	//$ gametest_ticks_batch AREA '200' '"qaHallEveryPageOne"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallEveryPageOne")
	public void everyPageOfAOneVillagerHallReadsCleanly(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		QaHallScreenGameTests.bed(helper, new BlockPos(10, 2, 10));
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", new BlockPos(12, 2, 15));
		ServerPlayer player = QaHallScreenGameTests.payer(helper, new ArrayList<>(), 0);
		helper.runAfterDelay(3, () -> {
			Map<String, String> wrong = new LinkedHashMap<>();
			List<String> blank = new ArrayList<>();
			int[] counted = {0};
			int pages = walk(helper, player, "one-villager hall", wrong, counted, blank, dara);
			report(helper, "one-villager hall", pages, counted, wrong, blank);
			helper.succeed();
		});
	}
}
