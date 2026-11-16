package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.ui.SettingsEditor;
import io.github.jcondedata.aliveworkplace.ui.SettingsLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, 2026-11-16): the Copperline Settings screen (ROADMAP 44.4) at the config's extremes, through the clicks a
 * player makes ({@link SettingsEditor} over {@link SettingsLayout}'s boxes) and the file the screen saves on closing.
 * From the spec ("changing a setting", the config's documented ranges): every switch on the screen can be flipped by a
 * click on its row and the flip survives the save and a fresh load; every number on the screen can be taken to the
 * very bottom and the very top of its range with its own controls ([-]/[+], or the slider clicked and dragged past
 * its ends) and is saved and read back there, never past it; the Left/Right keys stop at the ends; "Reset this page" on
 * every page puts the whole config back to the defaults, saved too. At GUI scale 2 (427x240) and 3 (320x180, the rail
 * as an icon grid). Pure logic on a temporary config folder: the game's own config is never touched.
 */
public class QaSettingsExtremes1116eGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {320, 180}};

	private static SettingsLayout.Screen screen(int sw, int sh, int sections) {
		return SettingsLayout.screen(sw, sh, 70, 50, sections, 90, 44);
	}

	private static Path tempDir() {
		try {
			return Files.createTempDirectory("aliveworkplace-qa-settings");
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	private static boolean clickOn(SettingsEditor editor, SettingsLayout.Screen b, int[] box) {
		return editor.click(b, box[0] + box[2] / 2.0, box[1] + box[3] / 2.0);
	}

	/** Opens {@code name}'s section with a rail click and scrolls (the mouse wheel) until its row shows; its row, or null. */
	private static int[] find(SettingsEditor editor, SettingsLayout.Screen b, String name) {
		int section = editor.sections().indexOf(SettingsLayout.sectionOf(name));
		if (section < 0) {
			return null;
		}
		clickOn(editor, b, b.railEntries()[section]);
		for (int i = 0; i < 200 && editor.rowOf(b, name) == null; i++) {
			editor.scrollBy(b, 1);
		}
		return editor.rowOf(b, name);
	}

	/** Every number on the screen at its range's bottom ({@code top} false) or top, with its own controls only. */
	private static void allNumbersTo(SettingsEditor editor, SettingsLayout.Screen b, boolean top, String at, List<String> problems) {
		for (String name : WorkplaceConfig.optionNames()) {
			if (WorkplaceConfig.isSwitch(name)) {
				continue;
			}
			WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(name);
			if (range == null) {
				problems.add(at + name + " is on the screen with no range, so nothing on it can change it");
				continue;
			}
			int[] row = find(editor, b, name);
			if (row == null) {
				problems.add(at + name + "'s row can't be scrolled into sight");
				continue;
			}
			WorkplaceConfig config = editor.config();
			if (SettingsLayout.small(range)) {
				int[][] boxes = SettingsLayout.number(row);
				for (int i = 0; i < range.max() - range.min() + 3; i++) {
					clickOn(editor, b, boxes[top ? 2 : 0]);
				}
			} else {
				int[] slider = SettingsLayout.slider(row, b.compact());
				double y = slider[1] + slider[3] / 2.0;
				// pressed on the slider's far end, then dragged well past it (off the window)
				editor.click(b, top ? slider[0] + slider[2] - 1 : slider[0], y);
				editor.drag(b, top ? b.win()[0] + b.win()[2] + 400 : b.win()[0] - 400);
				editor.release();
			}
			int want = top ? range.max() : range.min();
			if (config.getInt(name) != want) {
				problems.add(at + name + " went to " + config.getInt(name) + ", its range ends at " + want);
			}
			// the Left/Right key at the end changes nothing
			if (editor.step(name, top ? 1 : -1) || config.getInt(name) != want) {
				problems.add(at + "the " + (top ? "Right" : "Left") + " key moved " + name + " past " + want + " to " + config.getInt(name));
			}
		}
	}

	/** The saved-and-read-back file's number for every option on the screen is {@code top ? max : min}. */
	private static void checkSavedNumbers(WorkplaceConfig read, boolean top, String at, List<String> problems) {
		for (String name : WorkplaceConfig.optionNames()) {
			WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(name);
			if (WorkplaceConfig.isSwitch(name) || range == null) {
				continue;
			}
			int want = top ? range.max() : range.min();
			if (read.getInt(name) != want) {
				problems.add(at + name + " saved at " + want + " reads back as " + read.getInt(name));
			}
		}
	}

	/**
	 * Every switch on the screen flipped by a click on its row (each in its own section, scrolled into sight), saved
	 * as closing the screen does, and read back from the file flipped; the numbers not touched keep their defaults.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everySwitchFlippedOnTheScreenIsSavedFlipped(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		WorkplaceConfig defaults = new WorkplaceConfig();
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			Path dir = tempDir();
			WorkplaceConfig config = WorkplaceConfig.load(dir);
			SettingsEditor editor = new SettingsEditor(config);
			SettingsLayout.Screen b = screen(s[0], s[1], editor.sections().size());
			List<String> flipped = new ArrayList<>();
			for (String name : WorkplaceConfig.optionNames()) {
				if (!WorkplaceConfig.isSwitch(name)) {
					continue;
				}
				int[] row = find(editor, b, name);
				if (row == null) {
					problems.add(at + name + "'s row can't be scrolled into sight");
					continue;
				}
				boolean before = config.getBoolean(name);
				if (!clickOn(editor, b, row) || config.getBoolean(name) == before) {
					problems.add(at + "a click on " + name + "'s row didn't flip it");
					continue;
				}
				flipped.add(name);
			}
			helper.assertTrue(flipped.size() > 10, at + "only " + flipped.size() + " switches on the screen");
			config.save(dir);
			WorkplaceConfig read = WorkplaceConfig.load(dir);
			for (String name : flipped) {
				if (read.getBoolean(name) == defaults.getBoolean(name)) {
					problems.add(at + name + " was turned " + (defaults.getBoolean(name) ? "off" : "on") + " on the screen but reads back "
						+ (read.getBoolean(name) ? "on" : "off"));
				}
			}
			for (String name : WorkplaceConfig.optionNames()) {
				if (!WorkplaceConfig.isSwitch(name) && read.getInt(name) != defaults.getInt(name)) {
					problems.add(at + "flipping switches changed the number " + name + " to " + read.getInt(name));
				}
			}
			// a second save and load (the next time the screen closes) changes nothing
			read.save(dir);
			WorkplaceConfig again = WorkplaceConfig.load(dir);
			for (String name : flipped) {
				if (again.getBoolean(name) != read.getBoolean(name)) {
					problems.add(at + name + " changed on the second save and load");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every number on the screen taken to the bottom of its range with its own controls ([-] clicked past the end, or
	 * the slider pressed at its left end and dragged off the window), saved and read back there; then to the top the
	 * same way, saved and read back there. Never past an end, and the Left/Right key at an end does nothing.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyNumberAtBothEndsOfItsRangeIsSavedThere(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			for (boolean top : new boolean[]{false, true}) {
				String at = s[0] + "x" + s[1] + (top ? " top: " : " bottom: ");
				Path dir = tempDir();
				WorkplaceConfig config = WorkplaceConfig.load(dir);
				SettingsEditor editor = new SettingsEditor(config);
				SettingsLayout.Screen b = screen(s[0], s[1], editor.sections().size());
				allNumbersTo(editor, b, top, at, problems);
				config.save(dir);
				checkSavedNumbers(WorkplaceConfig.load(dir), top, at, problems);
				// and back from the file into a fresh screen, saved again: still at the end
				WorkplaceConfig reopened = WorkplaceConfig.load(dir);
				new SettingsEditor(reopened);
				reopened.save(dir);
				checkSavedNumbers(WorkplaceConfig.load(dir), top, at + "(second save) ", problems);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * With every number at the top of its range and every switch flipped, "Reset this page" clicked on every page of the
	 * rail puts every setting on the screen back to its default, and the file saved then reads back as the defaults.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void resetOnEveryPageBringsBackEveryDefault(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		WorkplaceConfig defaults = new WorkplaceConfig();
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			Path dir = tempDir();
			WorkplaceConfig config = WorkplaceConfig.load(dir);
			SettingsEditor editor = new SettingsEditor(config);
			SettingsLayout.Screen b = screen(s[0], s[1], editor.sections().size());
			allNumbersTo(editor, b, true, at, problems);
			for (String name : WorkplaceConfig.optionNames()) {
				if (WorkplaceConfig.isSwitch(name)) {
					config.setBoolean(name, !defaults.getBoolean(name));
				}
			}
			for (int i = 0; i < editor.sections().size(); i++) {
				clickOn(editor, b, b.railEntries()[i]);
				helper.assertTrue(editor.section() == i, at + "a click on rail entry " + i + " opened " + editor.section());
				editor.scrollBy(b, 5); // Reset works on the page whatever its scroll
				helper.assertTrue(clickOn(editor, b, b.reset()), at + "Reset this page did nothing on " + editor.sectionId());
			}
			config.save(dir);
			WorkplaceConfig read = WorkplaceConfig.load(dir);
			for (String name : WorkplaceConfig.optionNames()) {
				boolean isSwitch = WorkplaceConfig.isSwitch(name);
				Object want = isSwitch ? defaults.getBoolean(name) : defaults.getInt(name);
				Object now = isSwitch ? config.getBoolean(name) : config.getInt(name);
				Object saved = isSwitch ? read.getBoolean(name) : read.getInt(name);
				if (!want.equals(now) || !want.equals(saved)) {
					problems.add(at + name + " is " + now + " after the resets and " + saved + " in the file, its default is " + want);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
