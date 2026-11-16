package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.colony.ColonyMap;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout.Charter;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout.Plan;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * The City Plan and the Colony Charter in Copperline (ROADMAP 44.3) through {@link CityPlanLayout}, the boxes both screens
 * draw in and take their clicks with, at GUI scale 2 (427x240, 480x270) and 3 (320x180), with the English labels and
 * three times as long: every part inside the window and apart, clear of the whisper line and the close X; every tool,
 * Undo, button and zone row clickable where it is drawn; the map a whole number of cells, big enough to paint on; and the
 * new sentences in the lang file.
 */
public class CopperlineCityPlanGameTests implements FabricGameTest {
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {320, 180}};

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
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

	private static Plan plan(int sw, int sh, int times) {
		int rail = 0;
		for (String id : CityPlanLayout.RAIL) {
			rail = Math.max(rail, width(text("screen.aliveworkplace.city_plan." + id).repeat(times)));
		}
		return CityPlanLayout.plan(sw, sh, width(text("screen.aliveworkplace.city_plan.title", "Thornholm").repeat(times)), rail,
			Math.max(44, width(text("screen.aliveworkplace.city_plan.new").repeat(times)) + 12));
	}

	private static Charter charter(int sw, int sh, int times) {
		return CityPlanLayout.charter(sw, sh, width(text("screen.aliveworkplace.colony_map.title", "Thornholm").repeat(times)),
			Math.max(60, width(text("screen.aliveworkplace.colony_map.done").repeat(times)) + 16));
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

	private static void checkPlan(String at, Plan b, List<String> problems) {
		List<int[]> parts = new ArrayList<>(List.of(b.rail(), b.map(), b.list(), b.zonesHead(), b.name()));
		parts.addAll(List.of(b.buttons()));
		if (b.legend()[3] > 0) {
			parts.add(b.legend());
		}
		apart(at, b.win(), b.whisper(), b.close(), parts, problems);
		if (!within(b.plaque(), new int[]{0, 0, b.win()[0] * 2 + b.win()[2], b.win()[1] * 2 + b.win()[3]})) {
			problems.add(at + "the plaque " + box(b.plaque()) + " leaves the screen");
		}
		// the map: whole cells, at least 3 pixels each, so a cell can be clicked
		if (b.map()[2] % CityPlan.GRID != 0 || b.map()[2] < 3 * CityPlan.GRID || b.map()[2] != b.map()[3]) {
			problems.add(at + "the map " + box(b.map()) + " isn't a square of whole cells 3 pixels or more");
		}
		if (b.listRows() < 3) {
			problems.add(at + "only " + b.listRows() + " zone rows fit");
		}
		if (b.side()[2] < (b.compact() ? CityPlanLayout.SIDE_MIN_COMPACT : CityPlanLayout.SIDE_MIN)) {
			problems.add(at + "the zone column " + box(b.side()) + " is too narrow");
		}
		int[][] buttons = b.buttons();
		for (int i = 0; i < buttons.length; i++) {
			if (!clickable(buttons, i) || buttons[i][2] < 20) {
				problems.add(at + "button " + i + " " + box(buttons[i]) + " can't be clicked or is too narrow");
			}
		}
		// the five tools and Undo: each inside the rail and clickable; a two-column icon grid at 320x180
		int[][] rail = b.railEntries();
		for (int i = 0; i < rail.length; i++) {
			if (!within(rail[i], b.rail()) || !clickable(rail, i) || rail[i][3] < 10) {
				problems.add(at + "rail entry " + CityPlanLayout.RAIL[i] + " " + box(rail[i]) + " leaves the rail, can't be clicked or is too small");
			}
		}
		if ((rail[0][1] == rail[1][1]) != b.compact()) {
			problems.add(at + (b.compact() ? "the tool rail isn't an icon grid" : "the tool rail isn't one column"));
		}
	}

	/** The City Plan at GUI scale 2 and 3, with the English labels and three times as long. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void cityPlanFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3}) {
				Plan b = plan(s[0], s[1], times);
				if (b.compact() != (s[0] == 320)) {
					problems.add(s[0] + "x" + s[1] + ": compact is " + b.compact());
				}
				checkPlan(s[0] + "x" + s[1] + " x" + times + ": ", b, problems);
			}
		}
		// the legend shows where there's room: at GUI scale 2, not at 320x180
		helper.assertTrue(plan(427, 240, 1).legend()[3] > 0, "no legend at 427x240");
		helper.assertTrue(plan(320, 180, 1).legend()[3] == 0, "a legend squeezed in at 320x180");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The zone rows: the one under the mouse is the one drawn there, scrolled or not; the scroll bar and the space past the last row are none. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void cityPlanRowsAndRailPickTheRightThing(GameTestHelper helper) {
		for (int[] s : SCREENS) {
			Plan b = plan(s[0], s[1], 1);
			int[] l = b.list();
			int row = CityPlanLayout.ROW;
			String at = s[0] + "x" + s[1] + ": ";
			helper.assertTrue(CityPlanLayout.rowAt(b, 0, 10, l[0] + 20, l[1] + 1 + row * 2 + 3) == 2, at + "the third zone isn't found under the mouse");
			helper.assertTrue(CityPlanLayout.rowAt(b, 4, 10, l[0] + 20, l[1] + 1 + 3) == 4, at + "a scrolled list's top row isn't the fifth zone");
			helper.assertTrue(CityPlanLayout.rowAt(b, 0, 2, l[0] + 20, l[1] + 1 + row * 2 + 3) == -1, at + "a click under the last of two zones picked one");
			helper.assertTrue(CityPlanLayout.rowAt(b, 0, 10, l[0] + l[2] - 2, l[1] + 4) == -1, at + "a click on the scroll bar picked a zone");
			helper.assertTrue(CityPlanLayout.rowAt(b, 0, 10, b.map()[0] + 5, b.map()[1] + 5) == -1, at + "a click on the map picked a zone");
			// a click on the map is on no button and no rail entry (it paints)
			int[] m = b.map();
			for (double[] p : new double[][]{{m[0] + 1, m[1] + 1}, {m[0] + m[2] - 1, m[1] + m[3] - 1}, {m[0] + m[2] / 2.0, m[1] + m[3] / 2.0}}) {
				helper.assertTrue(CopperLayout.at(b.buttons(), p[0], p[1]) == -1 && CopperLayout.at(b.railEntries(), p[0], p[1]) == -1
					&& !CopperLayout.inside(b.name(), p[0], p[1]), at + "a click on the map at " + p[0] + "," + p[1] + " lands on a button");
			}
		}
		helper.assertTrue(CityPlanLayout.RAIL.length == 6 && CityPlanLayout.RAIL[CityPlanLayout.UNDO].equals("undo"), "Undo isn't the rail's last entry");
		helper.succeed();
	}

	/** The Charter at GUI scale 2 and 3: the map as big as fits (at least 96), the column, Done clickable. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void colonyCharterFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			for (int times : new int[]{1, 3}) {
				Charter b = charter(s[0], s[1], times);
				String at = s[0] + "x" + s[1] + " x" + times + ": ";
				apart(at, b.win(), b.whisper(), b.close(), List.of(b.map(), b.spotHead(), b.spot(), b.ringHead(), b.ring(), b.done()), problems);
				if (b.map()[2] < 96 || b.map()[2] != b.map()[3]) {
					problems.add(at + "the map " + box(b.map()) + " is too small or not square");
				}
				if (b.spot()[3] < 24 || b.ring()[3] < 20 || b.side()[2] < CityPlanLayout.SIDE_MIN_COMPACT) {
					problems.add(at + "the column " + box(b.side()) + " has no room for the spot " + box(b.spot()) + " or the ring " + box(b.ring()));
				}
				if (b.done()[2] < 44 || !clickable(new int[][]{b.done(), b.map(), b.close()}, 0)) {
					problems.add(at + "Done " + box(b.done()) + " is too narrow or can't be clicked");
				}
				// the map's middle is the mother village's hall, and a click there is on the map
				int[] m = b.map();
				double mid = ColonyMap.toScreen(0, 0, m[0], m[2]);
				if (Math.abs(mid - (m[0] + m[2] / 2.0)) > 1 || ColonyMap.toWorld(0, m[0] + m[2] / 2.0, m[0], m[2]) != 0) {
					problems.add(at + "the hall isn't in the map's middle");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Every tool, button and part says in the whisper line what it does, in American English. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theWhisperLinesAndHeadings(GameTestHelper helper) {
		for (String id : CityPlanLayout.RAIL) {
			String key = "screen.aliveworkplace.city_plan.whisper." + id;
			helper.assertTrue(!text(key).equals(key), "no whisper line for the " + id + " tool");
		}
		for (String id : List.of("new", "list", "name", "style", "kind", "delete", "width", "wall_closed", "close")) {
			String key = "screen.aliveworkplace.city_plan.whisper." + id;
			helper.assertTrue(!text(key).equals(key), "no whisper line for " + id);
		}
		helper.assertTrue(text("screen.aliveworkplace.city_plan.whisper.wall").equals("Click points around the village. Double-click to end."),
			"the wall's whisper line is wrong");
		helper.assertTrue(text("screen.aliveworkplace.city_plan.tool", text("screen.aliveworkplace.city_plan.brush")).equals("Tool: Brush"),
			"the lintel's tool word is wrong");
		helper.assertTrue(text("screen.aliveworkplace.city_plan.view_only_short").equals("View only"), "the view-only word is wrong");
		helper.assertTrue(text("screen.aliveworkplace.city_plan.tools").equals("Tools"), "the rail's heading is wrong");
		helper.assertTrue(text("screen.aliveworkplace.colony_map.lintel", ColonyMap.MIN, ColonyMap.MAX).equals("Ring: 256 to 1024 blocks out"),
			"the Charter's lintel is wrong");
		helper.assertTrue(text("screen.aliveworkplace.colony_map.spot_head").equals("The spot") && text("screen.aliveworkplace.colony_map.ring_head").equals("The ring"),
			"the Charter's headings are wrong");
		helper.assertTrue(text("screen.aliveworkplace.colony_map.pick").equals("No spot chosen yet."), "the Charter's empty spot is wrong");
		for (String id : List.of("map", "done", "close")) {
			String key = "screen.aliveworkplace.colony_map.whisper." + id;
			helper.assertTrue(!text(key).equals(key), "no Charter whisper line for " + id);
		}
		helper.succeed();
	}
}
