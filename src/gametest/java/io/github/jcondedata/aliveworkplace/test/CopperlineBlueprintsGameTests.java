package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableSearch;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout.Boxes;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout.View;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Blueprint Table in Copperline (ROADMAP 48.1, the Blueprint Book board) through {@link BlueprintBookLayout}, the
 * boxes the screen draws in and takes its clicks with, at GUI scale 2 (427x240, 480x270) and 3 (320x180), with the English
 * labels and three times as long: every part inside the window and apart, every button, tab, style and row clickable, the
 * three views' button rows each with exactly one copper button (Get Blueprint, Take One Out, Upload) and the table's
 * other buttons (Store All, Store Held, Upload a File..., Open Folder, Back) still there; the search box and the list's
 * empty state.
 */
public class CopperlineBlueprintsGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {320, 180}};

	private static String text(String key) {
		return Component.translatable(key).getString();
	}

	private static int width(String s) {
		return s.length() * 6;
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

	private static Boxes layout(int sw, int sh, int times, View view) {
		String[] keys = BlueprintBookLayout.buttons(view);
		int[] need = new int[keys.length];
		for (int i = 0; i < keys.length; i++) {
			need[i] = Math.max(44, width(text(keys[i]).repeat(times)) + 12);
		}
		int[] tabs = {width(text("screen.aliveworkplace.table.tab_library").repeat(times)) + 12,
			width(Component.translatable("screen.aliveworkplace.table.tab_stored", 999).getString().repeat(times)) + 12};
		return BlueprintBookLayout.of(sw, sh, width(text("block.aliveworkplace.blueprint_table").repeat(times)),
			width("Dark Oak and Deepslate".repeat(times)), tabs, view, need);
	}

	private static void check(String at, Boxes b, View view, List<String> problems) {
		int[] win = b.win();
		if (!within(b.plaque(), new int[]{0, 0, win[0] * 2 + win[2], win[1] * 2 + win[3]})) {
			problems.add(at + "the plaque " + box(b.plaque()) + " leaves the screen");
		}
		List<int[]> parts = new ArrayList<>(List.of(b.list(), b.detail()));
		if (view == View.LIBRARY) {
			parts.add(b.rail());
			parts.add(b.search());
			parts.add(b.header());
		}
		for (int[] p : parts) {
			if (!within(p, win) || p[2] <= 0 || p[3] <= 0) {
				problems.add(at + box(p) + " leaves the window " + box(win) + " or is empty");
			}
			if (CopperLayout.overlap(p, b.whisper()) || CopperLayout.overlap(p, b.close())) {
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
		if (b.detail()[2] < 100 || b.list()[2] < 100) {
			problems.add(at + "the list " + box(b.list()) + " or the detail " + box(b.detail()) + " is too narrow");
		}
		if (view == View.LIBRARY && b.detail()[3] < 30 + 12 + 10 + 10 + 11 + 18 + 11) {
			problems.add(at + "the detail " + box(b.detail()) + " has no room for a preview, the name, size, materials and their word");
		}
		if (BlueprintBookLayout.visibleRows(b.list(), BlueprintBookLayout.ROW) < 5) {
			problems.add(at + "the list shows fewer than 5 rows");
		}
		// The buttons: inside the stage, under the list and the detail, apart, clickable, none cut to nothing.
		int[][] buttons = b.buttons();
		for (int i = 0; i < buttons.length; i++) {
			if (!within(buttons[i], b.stage()) || buttons[i][2] < 24) {
				problems.add(at + "button " + i + " " + box(buttons[i]) + " leaves the stage or is too narrow");
			}
			if (!clickable(buttons, i)) {
				problems.add(at + "button " + i + " can't be clicked");
			}
			for (int[] p : parts) {
				if (CopperLayout.overlap(buttons[i], p)) {
					problems.add(at + "button " + i + " " + box(buttons[i]) + " overlaps " + box(p));
				}
			}
		}
		// The tabs on the lintel: after the plaque, before the close X, clickable; the count text in the room left.
		int[][] tabs = b.tabs();
		for (int i = 0; i < tabs.length; i++) {
			if (!within(tabs[i], win) || CopperLayout.overlap(tabs[i], b.plaque()) || CopperLayout.overlap(tabs[i], b.close()) || !clickable(tabs, i)) {
				problems.add(at + "tab " + i + " " + box(tabs[i]) + " leaves the window, is under the plaque or the close X, or can't be clicked");
			}
			if (CopperLayout.overlap(tabs[i], b.lintelText()) && b.lintelText()[2] > 0) {
				problems.add(at + "tab " + i + " is under the lintel's count text");
			}
			if (tabs[i][1] + tabs[i][3] > b.stage()[1]) {
				problems.add(at + "tab " + i + " hangs into the stage");
			}
		}
		if (view != View.LIBRARY) {
			if (b.rail()[2] != 0 || b.list()[0] > win[0] + 8) {
				problems.add(at + "the " + view + " view has a rail " + box(b.rail()) + " or its list doesn't start at the window's left");
			}
			return;
		}
		// The style rail: as drawn and eight styles, each clickable, inside the rail; a two-column icon grid at 320x180.
		int[][] styles = BlueprintBookLayout.styleEntries(b, 9);
		for (int i = 0; i < styles.length; i++) {
			if (!within(styles[i], b.rail()) || !clickable(styles, i) || styles[i][3] < 10) {
				problems.add(at + "style " + i + " " + box(styles[i]) + " leaves the rail, can't be clicked or is too small");
			}
		}
		boolean grid = styles[0][1] == styles[1][1];
		if (grid != b.compact()) {
			problems.add(at + (b.compact() ? "the style rail isn't an icon grid" : "the style rail isn't one column"));
		}
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void blueprintTableFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3}) {
				for (View view : View.values()) {
					Boxes b = layout(s[0], s[1], times, view);
					if (b.compact() != (s[0] == 320)) {
						problems.add(s[0] + "x" + s[1] + ": compact is " + b.compact());
					}
					check(s[0] + "x" + s[1] + " x" + times + " " + view + ": ", b, view, problems);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** One copper button per view, the table's main action; every other button of the table is still there, neutral. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void blueprintTableHasOneCopperButtonPerView(GameTestHelper helper) {
		helper.assertTrue(BlueprintBookLayout.COPPER == 0, "the copper button isn't the row's first");
		helper.assertTrue(BlueprintBookLayout.buttons(View.LIBRARY)[BlueprintBookLayout.COPPER].equals("screen.aliveworkplace.table.take"),
			"the library's copper button isn't Get Blueprint (the board's Place)");
		helper.assertTrue(BlueprintBookLayout.buttons(View.STORED)[BlueprintBookLayout.COPPER].equals("screen.aliveworkplace.table.take_out"),
			"the Stored tab's copper button isn't Take One Out");
		helper.assertTrue(BlueprintBookLayout.buttons(View.FILES)[BlueprintBookLayout.COPPER].equals("screen.aliveworkplace.table.upload"),
			"the upload view's copper button isn't Upload");
		List<String> all = new ArrayList<>();
		for (View v : View.values()) {
			all.addAll(List.of(BlueprintBookLayout.buttons(v)));
		}
		for (String key : List.of("screen.aliveworkplace.table.store_all", "screen.aliveworkplace.table.store_held", "screen.aliveworkplace.table.upload_view",
			"screen.aliveworkplace.table.open_folder", "gui.back")) {
			helper.assertTrue(all.contains(key), "the table lost its " + text(key) + " button");
		}
		helper.assertTrue(all.size() == all.stream().distinct().count(), "a button is in two views");
		// Every button says in the whisper line what it does, in the lang file.
		for (String key : all) {
			String w = "screen.aliveworkplace.table.whisper." + key.substring(key.lastIndexOf('.') + 1);
			helper.assertTrue(!text(w).equals(w), "no whisper line for " + text(key) + " (" + w + ")");
		}
		helper.assertTrue(text("screen.aliveworkplace.table.whisper.take").equals("Get this build on a blueprint to place where it should stand."),
			"Get Blueprint's whisper line is wrong");
		helper.succeed();
	}

	/**
	 * The list's rows: the row under the mouse is the one drawn there (scrolled or not), the scroll bar's strip and the
	 * empty space past the last row are none; the empty library's words, the materials' status words, the headings.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void blueprintTableRowsAndEmptyState(GameTestHelper helper) {
		Boxes b = layout(320, 180, 1, View.LIBRARY);
		int[] l = b.list();
		int rowH = BlueprintBookLayout.ROW;
		int rows = BlueprintBookLayout.visibleRows(l, rowH);
		helper.assertTrue(BlueprintBookLayout.rowAt(l, rowH, 0, 30, l[0] + 10, l[1] + 1 + rowH * 2 + 3) == 2, "the third row isn't found under the mouse");
		helper.assertTrue(BlueprintBookLayout.rowAt(l, rowH, 5, 30, l[0] + 10, l[1] + 1 + 3) == 5, "a scrolled list's top row isn't the sixth");
		helper.assertTrue(BlueprintBookLayout.rowAt(l, rowH, 0, 2, l[0] + 10, l[1] + 1 + rowH * 3 + 3) == -1, "a click under the last of two rows picked one");
		helper.assertTrue(BlueprintBookLayout.rowAt(l, rowH, 0, 30, l[0] + l[2] - 2, l[1] + 4) == -1, "a click on the scroll bar picked a row");
		helper.assertTrue(BlueprintBookLayout.rowAt(l, rowH, 0, 30, l[0] + 10, l[1] + 1 + rowH * rows + 1) == -1 || l[1] + 1 + rowH * rows + 1 >= l[1] + l[3],
			"a click below the rows that fit picked a hidden one");
		// The empty states and the materials' word: glyph, word and color (the word is in the lang file, American English).
		helper.assertTrue(text("screen.aliveworkplace.table.empty_title").equals("No blueprints yet"), "the empty library's title is wrong");
		helper.assertTrue(text("screen.aliveworkplace.table.empty_line").equals("Upload a .litematic or .schem file."), "the empty library's line is wrong");
		helper.assertTrue(text("screen.aliveworkplace.table.stored_empty_line").equals("Hold one and press Store Held."), "the empty Stored tab's line is wrong");
		helper.assertTrue(text("screen.aliveworkplace.table.carried_all").equals("You carry all of it"), "the materials' good word is wrong");
		helper.assertTrue(Component.translatable("screen.aliveworkplace.table.carried_missing_one", "Glass").getString().equals("Not carried: Glass"),
			"the materials' warning word is wrong");
		helper.assertTrue(Component.translatable("screen.aliveworkplace.table.carried_missing_more", "Glass").getString().equals("Not carried: Glass and more"),
			"the materials' warning word for several is wrong");
		helper.assertTrue(text("screen.aliveworkplace.table.rail_style").equals("Style") && text("screen.aliveworkplace.table.col_name").equals("Name")
			&& text("screen.aliveworkplace.table.col_size").equals("Size"), "the rail's or the list's headings are wrong");
		helper.assertTrue(Component.translatable("screen.aliveworkplace.table.whisper.style", "Sandstone").getString().equals("Sandstone: click to build it in this style"),
			"a style's whisper line is wrong");
		// The search box still narrows the list (36.10), and nothing typed lists everything.
		List<TablePayloads.Entry> entries = List.of(
			new TablePayloads.Entry(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "starter_cottage"), 9, 7, 7, 300, 0, "", List.of()),
			new TablePayloads.Entry(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "market_stall"), 5, 4, 3, 60, 0, "", List.of()));
		helper.assertTrue(TableSearch.filter(entries, "").size() == 2, "an empty search box doesn't list every build");
		helper.assertTrue(TableSearch.filter(entries, "market").size() == 1, "\"market\" doesn't narrow the list to the Market Stall");
		helper.assertTrue(TableSearch.filter(entries, "zzz").isEmpty(), "a search for nothing lists something");
		helper.succeed();
	}
}
