package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guide.GuideBook;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.GuideLayout;
import io.github.jcondedata.aliveworkplace.ui.SettingsEditor;
import io.github.jcondedata.aliveworkplace.ui.SettingsLayout;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * The Guide Book and the Settings screen in Copperline (ROADMAP 44.4) through {@link GuideLayout}, {@link GuideBook.Reader},
 * {@link SettingsLayout} and {@link SettingsEditor}, the boxes both screens draw in and the logic their clicks go
 * through, at GUI scale 2 (427x240, 480x270) and 3 (320x180): every part inside the window and apart, clear of the
 * whisper line and the close X; every page of the book opened with Next, its chapter in the rail and the contents' rows,
 * its picture big enough and its words fitting; every setting in a section, its label fitting beside its control; a
 * switch, a small number and a slider changed by clicks, a page reset, and the changes saved and read back; and the new
 * sentences in the lang file.
 */
public class CopperlineGuideSettingsGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {320, 180}};

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** A line's width in vanilla's default font (each glyph's advance, its 1-pixel gap included; the server has no font). */
	private static int width(String s) {
		int w = 0;
		for (char c : s.toCharArray()) {
			w += switch (c) {
				case '!', '\'', ',', '.', ':', ';', '|', 'i' -> 2;
				case '`', 'l' -> 3;
				case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
				case '<', '>', 'f', 'k' -> 5;
				case '@', '~' -> 7;
				case '…' -> 8;
				default -> 6;
			};
		}
		return w;
	}

	/** How many lines {@code s} wraps to at {@code w} pixels, breaking at spaces as the game does. */
	private static int lines(String s, int w) {
		int n = 1;
		int line = 0;
		for (String word : s.split(" ")) {
			int ww = width(word);
			if (line == 0) {
				line = ww;
			} else if (line + 4 + ww <= w) {
				line += 4 + ww;
			} else {
				n++;
				line = ww;
			}
		}
		return n;
	}

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

	/** The parts must sit in the window, apart from each other, clear of the whisper line and the close X. */
	private static void apart(String at, int[] win, int[] whisper, int[] close, List<int[]> parts, List<String> problems) {
		for (int[] p : parts) {
			if (!within(p, win) || p[2] <= 0 || p[3] <= 0) {
				problems.add(at + box(p) + " leaves the window " + box(win) + " or is empty");
			}
			if (CopperLayout.overlap(p, whisper) || CopperLayout.overlap(p, close)) {
				problems.add(at + box(p) + " runs under the whisper line or the close X");
			}
		}
		for (int i = 0; i < parts.size(); i++) {
			for (int j = 0; j < i; j++) {
				if (CopperLayout.overlap(parts.get(i), parts.get(j))) {
					problems.add(at + box(parts.get(i)) + " overlaps " + box(parts.get(j)));
				}
			}
		}
	}

	private static void railChecks(String at, int[] rail, int[][] entries, boolean compact, List<String> problems) {
		for (int i = 0; i < entries.length; i++) {
			if (!within(entries[i], rail) || !clickable(entries, i) || entries[i][3] < 10) {
				problems.add(at + "rail entry " + i + " " + box(entries[i]) + " leaves the rail, can't be clicked or is too small");
			}
		}
		if (entries.length > 1 && (entries[0][1] == entries[1][1]) != compact) {
			problems.add(at + (compact ? "the rail isn't an icon grid" : "the rail isn't one column"));
		}
	}

	// --- the Guide Book ------------------------------------------------------------------------------------------------

	private static final List<GuideBook.Entry> EVERY_PAGE = GuideBook.shown(true, true);

	private static GuideLayout.Book book(int sw, int sh, int times, int chapters) {
		int rail = width(text("guide.aliveworkplace.contents").repeat(times));
		for (String c : GuideBook.CHAPTERS) {
			rail = Math.max(rail, width(text("screen.aliveworkplace.guide.chapter." + c).repeat(times)));
		}
		return GuideLayout.book(sw, sh, width(text("item.aliveworkplace.guide_book").repeat(times)), rail, chapters + 1,
			width(("< " + text("screen.aliveworkplace.guide.back")).repeat(times)) + 12,
			Math.max(44, width((text("screen.aliveworkplace.guide.next") + " >").repeat(times)) + 16));
	}

	/** The book at GUI scale 2 and 3: the shell, the arrows, every page's picture and words, and the contents page. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void guideFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int chapters = GuideBook.CHAPTERS.size();
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3}) {
				GuideLayout.Book b = book(s[0], s[1], times, chapters);
				String at = s[0] + "x" + s[1] + " x" + times + ": ";
				if (b.compact() != (s[0] == 320)) {
					problems.add(at + "compact is " + b.compact());
				}
				List<int[]> shell = new ArrayList<>(List.of(b.rail(), b.title(), b.back(), b.next()));
				if (b.count()[2] > 0) {
					shell.add(b.count());
				}
				apart(at, b.win(), b.whisper(), b.close(), shell, problems);
				railChecks(at, b.rail(), b.railEntries(), b.compact(), problems);
				if (b.back()[2] < 16 || b.next()[2] < 16 || !clickable(b.buttons(), 0) || !clickable(b.buttons(), 1)) {
					problems.add(at + "Back " + box(b.back()) + " or Next " + box(b.next()) + " is too narrow or can't be clicked");
				}
				if (times > 1) {
					continue;
				}
				// every page: the picture over its words, both on the stage above the arrows, the picture big enough to see
				int minPicture = b.compact() ? 50 : 90;
				for (GuideBook.Entry e : EVERY_PAGE) {
					int n = lines(text("guide.aliveworkplace.page." + e.id() + ".text"), GuideLayout.textWidth(b));
					if (n > 6) {
						problems.add(at + e.id() + "'s words take " + n + " lines");
					}
					GuideLayout.Page p = GuideLayout.page(b, Math.min(6, n));
					int[] frame = {p.picture()[0] - 1, p.picture()[1] - 1, p.picture()[2] + 2, p.picture()[3] + 2};
					List<int[]> parts = new ArrayList<>(List.of(frame, p.text(), b.title()));
					if (!b.compact()) {
						parts.add(b.back());
						parts.add(b.next());
					}
					apart(at + e.id() + " ", b.win(), b.whisper(), b.close(), parts, problems);
					if (!within(frame, b.stage()) || p.text()[1] + p.text()[3] > b.bottom()) {
						problems.add(at + e.id() + "'s picture " + box(frame) + " or words " + box(p.text()) + " leave the stage");
					}
					if (p.picture()[3] < minPicture || Math.abs(p.picture()[2] * GuideLayout.PICTURE_H - p.picture()[3] * GuideLayout.PICTURE_W) > GuideLayout.PICTURE_W) {
						problems.add(at + e.id() + "'s picture " + box(p.picture()) + " is smaller than " + minPicture + " or not 16:9");
					}
				}
				// the contents page: the intro, the heading and every chapter's row on the stage, each row clickable
				GuideLayout.Contents c = GuideLayout.contents(b, lines(text("guide.aliveworkplace.intro"), GuideLayout.textWidth(b)), chapters);
				List<int[]> parts = new ArrayList<>(List.of(c.intro(), c.head(), b.title()));
				parts.addAll(List.of(c.rows()));
				apart(at + "contents ", b.win(), b.whisper(), b.close(), parts, problems);
				for (int i = 0; i < c.rows().length; i++) {
					if (!within(c.rows()[i], b.stage()) || c.rows()[i][1] + c.rows()[i][3] > b.bottom() || !clickable(c.rows(), i)) {
						problems.add(at + "the contents row " + i + " " + box(c.rows()[i]) + " leaves the stage or can't be clicked");
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every page opens, the way a player gets there: Next from the contents to the last page (each with its words and its
	 * picture), Back to the contents, every chapter from the rail and from the contents' rows, the contents from the rail;
	 * nothing turns past either end. With Cobblemon and the City Plan and without.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyGuidePageOpens(GameTestHelper helper) {
		var mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		Language lang = Language.getInstance();
		List<String> problems = new ArrayList<>();
		for (GuideBook.Entry e : GuideBook.ALL) {
			if (mod.findPath("assets/aliveworkplace/textures/gui/guide/" + e.id() + ".png").isEmpty()) {
				problems.add(e.id() + " has no picture");
			}
			if (!lang.has("guide.aliveworkplace.page." + e.id() + ".title") || !lang.has("guide.aliveworkplace.page." + e.id() + ".text")) {
				problems.add(e.id() + " has no words");
			}
		}
		int opened = 0;
		for (boolean full : new boolean[]{true, false}) {
			List<GuideBook.Entry> shown = GuideBook.shown(full, full);
			for (int[] s : SCREENS) {
				GuideBook.Reader r = new GuideBook.Reader(shown, 0);
				String at = (full ? "full " : "plain ") + s[0] + "x" + s[1] + ": ";
				GuideLayout.Book b = book(s[0], s[1], 1, r.chapters().size());
				GuideLayout.Contents c = GuideLayout.contents(b, lines(text("guide.aliveworkplace.intro"), GuideLayout.textWidth(b)), r.chapters().size());
				double[] next = {b.next()[0] + b.next()[2] / 2.0, b.next()[1] + b.next()[3] / 2.0};
				double[] back = {b.back()[0] + b.back()[2] / 2.0, b.back()[1] + b.back()[3] / 2.0};
				if (GuideLayout.click(b, c, r, back[0], back[1]) || r.page() != 0) {
					problems.add(at + "Back turned from the contents");
				}
				for (int page = 1; page < r.pageCount(); page++) {
					if (!GuideLayout.click(b, page == 1 ? c : null, r, next[0], next[1]) || r.page() != page) {
						problems.add(at + "Next didn't open page " + page + " (on " + r.page() + ")");
						break;
					}
					opened++;
				}
				if (GuideLayout.click(b, null, r, next[0], next[1]) || r.page() != shown.size()) {
					problems.add(at + "Next turned past the last page");
				}
				while (r.page() > 0) {
					int before = r.page();
					GuideLayout.click(b, null, r, back[0], back[1]);
					if (r.page() != before - 1) {
						problems.add(at + "Back from page " + before + " opened " + r.page());
						break;
					}
				}
				List<String> chapters = r.chapters();
				if (chapters.contains("pokemon") != full || shown.stream().anyMatch(e -> e.id().equals("city_plan")) != full) {
					problems.add(at + "the Pokémon chapter or the City Plan's page is shown " + !full);
				}
				for (int i = 0; i < chapters.size(); i++) {
					int[] e = b.railEntries()[i + 1];
					GuideLayout.click(b, null, r, e[0] + e[2] / 2.0, e[1] + e[3] / 2.0);
					if (r.page() != r.firstPageOf(chapters.get(i)) || !chapters.get(i).equals(r.chapter())) {
						problems.add(at + "the rail's " + chapters.get(i) + " opened page " + r.page());
					}
					int[] home = b.railEntries()[0];
					GuideLayout.click(b, null, r, home[0] + home[2] / 2.0, home[1] + home[3] / 2.0);
					if (r.page() != 0) {
						problems.add(at + "the rail's Contents opened page " + r.page());
					}
					int[] row = c.rows()[i];
					GuideLayout.click(b, c, r, row[0] + row[2] / 2.0, row[1] + row[3] / 2.0);
					if (r.page() != r.firstPageOf(chapters.get(i))) {
						problems.add(at + "the contents' " + chapters.get(i) + " row opened page " + r.page());
					}
					r.go(0);
				}
				int[] mid = b.stage();
				if (GuideLayout.click(b, null, new GuideBook.Reader(shown, 3), mid[0] + mid[2] / 2.0, mid[1] + mid[3] / 2.0)) {
					problems.add(at + "a click on a page's picture did something");
				}
			}
		}
		GuideBook.Reader clamped = new GuideBook.Reader(EVERY_PAGE, 999);
		helper.assertTrue(clamped.page() == EVERY_PAGE.size(), "a book left open past its end opens at " + clamped.page());
		helper.assertTrue(opened > 100, "only " + opened + " pages opened");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// --- the Settings screen -------------------------------------------------------------------------------------------

	private static List<String> allFields() {
		List<String> names = new ArrayList<>();
		for (Field field : WorkplaceConfig.class.getDeclaredFields()) {
			int mods = field.getModifiers();
			if (Modifier.isPublic(mods) && !Modifier.isStatic(mods) && (field.getType() == boolean.class || field.getType() == int.class)) {
				names.add(field.getName());
			}
		}
		return names;
	}

	private static SettingsLayout.Screen screen(int sw, int sh, int times, int sections) {
		int rail = 0;
		for (String id : SettingsLayout.SECTIONS) {
			rail = Math.max(rail, width(text("screen.aliveworkplace.settings.section." + id).repeat(times)));
		}
		return SettingsLayout.screen(sw, sh, width(text("aliveworkplace.config.title").repeat(times)), rail, sections,
			width(text("screen.aliveworkplace.settings.reset").repeat(times)) + 12,
			Math.max(44, width(text("screen.aliveworkplace.settings.done").repeat(times)) + 16));
	}

	/** Every setting (an expansion's still switched off included) is in one of the rail's sections, never left in Other. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everySettingHasItsSection(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<String> names = allFields();
		for (String name : names) {
			if (SettingsLayout.sectionOf(name).equals(SettingsLayout.OTHER)) {
				problems.add(name + " is in no section (SettingsLayout's list)");
			}
		}
		List<String> shown = WorkplaceConfig.optionNames();
		int total = 0;
		for (String section : SettingsLayout.shownSections(shown)) {
			List<String> in = SettingsLayout.optionsIn(section, shown, WorkplaceConfig::isSwitch);
			total += in.size();
			if (in.isEmpty()) {
				problems.add("the " + section + " section is empty");
			}
			// the numbers first, then the switches
			boolean seenSwitch = false;
			for (String n : in) {
				if (WorkplaceConfig.isSwitch(n)) {
					seenSwitch = true;
				} else if (seenSwitch) {
					problems.add(n + " is listed after a switch in " + section);
				}
			}
		}
		helper.assertTrue(total == shown.size(), total + " settings in the sections, " + shown.size() + " on the screen");
		helper.assertTrue(!SettingsLayout.shownSections(shown).contains(SettingsLayout.OTHER), "an Other section shows");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The Settings screen at GUI scale 2 and 3: the shell, the list, Reset and Done, and every setting's label beside its control. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void settingsFitAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int sections = SettingsLayout.SECTIONS.size();
		int wordW = Math.max(width(text("screen.aliveworkplace.settings.on")), width(text("screen.aliveworkplace.settings.off")));
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3}) {
				SettingsLayout.Screen b = screen(s[0], s[1], times, sections);
				String at = s[0] + "x" + s[1] + " x" + times + ": ";
				if (b.compact() != (s[0] == 320)) {
					problems.add(at + "compact is " + b.compact());
				}
				apart(at, b.win(), b.whisper(), b.close(), List.of(b.rail(), b.list(), b.reset(), b.done()), problems);
				railChecks(at, b.rail(), b.railEntries(), b.compact(), problems);
				if (b.listRows() < 6 || b.reset()[2] < 30 || b.done()[2] < 30) {
					problems.add(at + "only " + b.listRows() + " rows, or Reset " + box(b.reset()) + " or Done " + box(b.done()) + " too narrow");
				}
				if (times > 1) {
					continue;
				}
				for (int v = 0; v < b.listRows(); v++) {
					int[] row = SettingsLayout.row(b, v);
					if (!within(row, b.list()) || CopperLayout.overlap(row, SettingsLayout.scrollbar(b))) {
						problems.add(at + "row " + v + " " + box(row) + " leaves the list or runs under the scroll bar");
					}
				}
				int[] row = SettingsLayout.row(b, b.listRows() - 1);
				for (String name : allFields()) {
					boolean isSwitch = WorkplaceConfig.isSwitch(name);
					WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(name);
					boolean small = range != null && SettingsLayout.small(range);
					int label = width(text("aliveworkplace.config." + name));
					int room = SettingsLayout.labelRoom(row, isSwitch, small, b.compact(), wordW);
					if (label > room) {
						problems.add(at + name + "'s label is " + label + " wide, its row has " + room);
					}
					List<int[]> controls = isSwitch ? List.of(SettingsLayout.toggle(row))
						: small ? List.of(SettingsLayout.number(row)) : List.of(SettingsLayout.slider(row, b.compact()), SettingsLayout.value(row));
					for (int[] c : controls) {
						if (!within(c, row)) {
							problems.add(at + name + "'s control " + box(c) + " leaves its row " + box(row));
						}
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Clicks a box's middle. */
	private static boolean clickOn(SettingsEditor editor, SettingsLayout.Screen b, int[] box) {
		return editor.click(b, box[0] + box[2] / 2.0, box[1] + box[3] / 2.0);
	}

	/** Opens {@code name}'s section from the rail and scrolls it into sight; its row. */
	private static int[] find(SettingsEditor editor, SettingsLayout.Screen b, String name) {
		int section = editor.sections().indexOf(SettingsLayout.sectionOf(name));
		clickOn(editor, b, b.railEntries()[section]);
		for (int i = 0; i < 40 && editor.rowOf(b, name) == null; i++) {
			editor.scrollBy(b, 1);
		}
		return editor.rowOf(b, name);
	}

	/**
	 * Changing settings the way a player does, at GUI scale 2 and 3: a rail click opens Village and a click on the Festivals
	 * row turns it off; [+] and [-] step the threat level; a click at a slider's ends sets the guard radius to its range's
	 * ends and a drag moves it; "Reset this page" puts Defense back; then the file saved on closing is read back with
	 * the changes, and the settings not touched keep their values.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void changingASettingIsSaved(GameTestHelper helper) {
		for (int[] s : SCREENS) {
			Path dir;
			try {
				dir = Files.createTempDirectory("aliveworkplace-settings");
			} catch (IOException e) {
				throw new GameTestAssertException("no temp dir: " + e);
			}
			String at = s[0] + "x" + s[1] + ": ";
			WorkplaceConfig config = WorkplaceConfig.load(dir);
			SettingsEditor editor = new SettingsEditor(config);
			SettingsLayout.Screen b = screen(s[0], s[1], 1, editor.sections().size());
			helper.assertTrue(config.festivals && config.guardRadius != 8, at + "the defaults aren't what this test expects");

			int[] festivals = find(editor, b, "festivals");
			helper.assertTrue(festivals != null && editor.sectionId().equals("village"), at + "Festivals isn't in Village's list");
			helper.assertTrue(clickOn(editor, b, festivals) && !config.festivals, at + "a click on the Festivals row didn't turn it off");

			int[] threat = find(editor, b, "threatLevel");
			helper.assertTrue(threat != null && editor.sectionId().equals("defense"), at + "the threat level isn't in Defense's list");
			int level = config.threatLevel;
			int[][] number = SettingsLayout.number(threat);
			clickOn(editor, b, number[2]);
			helper.assertTrue(config.threatLevel == Math.min(3, level + 1), at + "[+] made the threat level " + config.threatLevel);
			clickOn(editor, b, number[0]);
			clickOn(editor, b, number[0]);
			helper.assertTrue(config.threatLevel == Math.max(0, Math.min(3, level + 1) - 2), at + "[-] twice made the threat level " + config.threatLevel);
			for (int i = 0; i < 6; i++) {
				clickOn(editor, b, number[0]);
			}
			helper.assertTrue(config.threatLevel == 0, at + "[-] went under 0: " + config.threatLevel);

			int[] guard = find(editor, b, "guardRadius");
			int[] slider = SettingsLayout.slider(guard, b.compact());
			double y = slider[1] + 4;
			editor.click(b, slider[0] + slider[2] - 1, y);
			editor.release();
			helper.assertTrue(config.guardRadius == 64, at + "a click at the slider's right end set " + config.guardRadius);
			editor.click(b, slider[0], y);
			helper.assertTrue(config.guardRadius == 8, at + "a click at the slider's left end set " + config.guardRadius);
			editor.drag(b, slider[0] + slider[2] / 2.0);
			editor.release();
			helper.assertTrue(config.guardRadius > 20 && config.guardRadius < 52, at + "dragging to the middle set " + config.guardRadius);
			helper.assertTrue(!editor.drag(b, slider[0]), at + "a drag after letting go still moved the slider");
			int middle = config.guardRadius;
			editor.step("guardRadius", 1);
			helper.assertTrue(config.guardRadius == middle + 1, at + "the Right key didn't step the guard radius");
			// a click on a slider's label (not the slider) changes nothing
			editor.click(b, guard[0] + 6, y);
			helper.assertTrue(config.guardRadius == middle + 1, at + "a click on the label moved the slider");

			// Reset this page: Defense back to the defaults, Village's Festivals still off
			clickOn(editor, b, b.reset());
			WorkplaceConfig defaults = new WorkplaceConfig();
			helper.assertTrue(config.guardRadius == defaults.guardRadius && config.threatLevel == defaults.threatLevel,
				at + "Reset this page left the guard radius " + config.guardRadius + " and the threat level " + config.threatLevel);
			helper.assertTrue(!config.festivals, at + "Reset on Defense turned Festivals back on");
			editor.step("guardRadius", -2);
			int savedGuard = config.guardRadius;

			// closing the screen saves the file; it is read back with the changes
			config.save(dir);
			WorkplaceConfig read = WorkplaceConfig.load(dir);
			helper.assertTrue(!read.festivals && read.guardRadius == savedGuard && read.threatLevel == defaults.threatLevel,
				at + "the saved file has festivals " + read.festivals + ", guardRadius " + read.guardRadius + ", threatLevel " + read.threatLevel);
			helper.assertTrue(read.villagerNames == defaults.villagerNames && read.supplyRadius == defaults.supplyRadius,
				at + "settings not touched changed");
		}
		helper.succeed();
	}

	/** Every section and the screens' new words are in the lang file, in American English. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theNewSentences(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		for (String id : SettingsLayout.SECTIONS) {
			if (!lang.has("screen.aliveworkplace.settings.section." + id)) {
				missing.add("section " + id);
			}
		}
		for (String c : GuideBook.CHAPTERS) {
			if (!lang.has("screen.aliveworkplace.guide.chapter." + c)) {
				missing.add("chapter " + c);
			}
		}
		for (String k : List.of("sections", "numbers", "switches", "lintel", "reset", "done", "on", "off", "whisper.section", "whisper.reset",
			"whisper.done", "whisper.switch", "whisper.number", "whisper.slider")) {
			if (!lang.has("screen.aliveworkplace.settings." + k)) {
				missing.add("settings." + k);
			}
		}
		for (String k : List.of("back", "next", "lintel.page", "lintel.contents", "whisper.page", "whisper.contents", "whisper.chapter",
			"whisper.back", "whisper.next", "whisper.close")) {
			if (!lang.has("screen.aliveworkplace.guide." + k)) {
				missing.add("guide." + k);
			}
		}
		helper.assertTrue(missing.isEmpty(), "untranslated: " + missing);
		helper.assertTrue(text("screen.aliveworkplace.settings.section.defense").equals("Defense"), "Defense isn't spelled the American way");
		helper.assertTrue(text("screen.aliveworkplace.settings.reset").equals("Reset this page"), "Reset's word is wrong");
		helper.assertTrue(text("screen.aliveworkplace.settings.lintel", text("screen.aliveworkplace.settings.section.village"), 29)
			.equals("Village · 29 settings"), "the Settings lintel is wrong");
		helper.assertTrue(text("screen.aliveworkplace.guide.lintel.page", text("guide.aliveworkplace.chapter.builders"), 5, 36)
			.equals("Builders · Page 5 of 36"), "the Guide's lintel is wrong");
		helper.succeed();
	}
}
