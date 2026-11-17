package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
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
 * QA (lane-e-1117-0113) for 44.3, the City Plan and the Colony Charter in Copperline, written from the spec ("the map
 * and its zone paint stay; the frame, title, buttons and lists change") and copperline.md. The builder's tests check
 * three screens; these check the screens on both sides of the compact boundary (400x220), a big monitor and an
 * ultrawide, and the zone list at its limit: with all {@link CityPlan#MAX_ZONES} zones, every zone is the one a click
 * on its row picks at some scroll, no click picks a zone past the end or a row the list doesn't draw, and the map
 * stays whole cells everywhere. The new sentences are rendered with real arguments.
 */
public class QaCityPlanCopper1117eGameTests implements FabricGameTest {
	/** Both sides of the compact boundary, a 4K screen at scale 2 and 4, an ultrawide at scale 4, a short laptop. */
	private static final int[][] SCREENS = {{400, 220}, {399, 220}, {400, 219}, {1920, 1080}, {960, 540}, {860, 270}, {683, 192}, {320, 180}};

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private static Plan plan(int sw, int sh) {
		return CityPlanLayout.plan(sw, sh, 120, 40, 44);
	}

	/**
	 * At every screen: the map is a square of whole cells inside the stage, nothing overlaps it, the tools and buttons
	 * are inside the window and clickable, and the Charter's map, spot, ring and Done are inside the window and apart.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void bothScreensHoldTogetherOnTheCompactBoundaryAndBigScreens(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			Plan p = plan(s[0], s[1]);
			int[] m = p.map();
			if (m[2] != m[3] || m[2] % CityPlan.GRID != 0 || m[2] < 3 * CityPlan.GRID || !within(m, p.stage())) {
				problems.add(at + "the map " + box(m) + " isn't a square of whole cells in the stage " + box(p.stage()));
			}
			List<int[]> parts = new ArrayList<>(List.of(p.rail(), m, p.list(), p.zonesHead(), p.name()));
			parts.addAll(List.of(p.buttons()));
			if (p.legend()[3] > 0) {
				parts.add(p.legend());
			}
			for (int i = 0; i < parts.size(); i++) {
				int[] a = parts.get(i);
				if (!within(a, p.win()) || a[2] <= 0 || a[3] <= 0 || CopperLayout.overlap(a, p.whisper()) || CopperLayout.overlap(a, p.close())) {
					problems.add(at + box(a) + " leaves the window " + box(p.win()) + ", is empty, or runs under the whisper or the X");
				}
				for (int j = 0; j < i; j++) {
					if (CopperLayout.overlap(a, parts.get(j))) {
						problems.add(at + box(a) + " overlaps " + box(parts.get(j)));
					}
				}
			}
			int[][] rail = p.railEntries();
			for (int i = 0; i < rail.length; i++) {
				if (!within(rail[i], p.rail()) || CopperLayout.at(rail, rail[i][0] + rail[i][2] / 2.0, rail[i][1] + rail[i][3] / 2.0) != i) {
					problems.add(at + "tool " + CityPlanLayout.RAIL[i] + " " + box(rail[i]) + " is outside the rail or can't be clicked");
				}
			}
			Charter c = CityPlanLayout.charter(s[0], s[1], 120, 60);
			List<int[]> cp = List.of(c.map(), c.spotHead(), c.spot(), c.ringHead(), c.ring(), c.done());
			for (int i = 0; i < cp.size(); i++) {
				int[] a = cp.get(i);
				if (!within(a, c.win()) || a[2] <= 0 || a[3] < 0 || CopperLayout.overlap(a, c.whisper()) || CopperLayout.overlap(a, c.close())) {
					problems.add(at + "charter part " + i + " " + box(a) + " leaves the window " + box(c.win()) + " or runs under the whisper or the X");
				}
				for (int j = 0; j < i; j++) {
					if (CopperLayout.overlap(a, cp.get(j))) {
						problems.add(at + "charter part " + i + " " + box(a) + " overlaps " + box(cp.get(j)));
					}
				}
			}
			if (c.map()[2] != c.map()[3] || c.map()[2] < 64) {
				problems.add(at + "the charter map " + box(c.map()) + " isn't square or is under 64 px");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A full plan (16 zones): at each scroll the screen allows (0 to 16 minus the rows shown), a click on each drawn row
	 * picks that zone, a click under the last drawn row or on the scroll bar picks nothing, and every one of the 16 zones
	 * can be picked at some scroll. With fewer zones than rows, a click on an empty row picks nothing.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyZoneOfAFullPlanCanBePickedAndNoRowPastTheEnd(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int count = CityPlan.MAX_ZONES;
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			Plan p = plan(s[0], s[1]);
			int rows = p.listRows();
			int[] l = p.list();
			boolean[] reached = new boolean[count];
			int maxScroll = Math.max(0, count - rows);
			for (int scroll = 0; scroll <= maxScroll; scroll++) {
				for (int r = 0; r < rows; r++) {
					double y = l[1] + 1 + r * CityPlanLayout.ROW + CityPlanLayout.ROW / 2.0;
					if (y >= l[1] + l[3] - 1) {
						break;
					}
					int got = CityPlanLayout.rowAt(p, scroll, count, l[0] + 10, y);
					int want = scroll + r < count ? scroll + r : -1;
					if (got != want) {
						problems.add(at + "scroll " + scroll + ", row " + r + " picks " + got + ", expected " + want);
					}
					if (got >= 0) {
						reached[got] = true;
					}
				}
				// the strip under the last whole row (a row the list doesn't draw) picks nothing
				for (double y = l[1] + 1 + rows * CityPlanLayout.ROW + 0.5; y < l[1] + l[3]; y += 1) {
					int got = CityPlanLayout.rowAt(p, scroll, count, l[0] + 10, y);
					if (got != -1) {
						problems.add(at + "scroll " + scroll + ", a click at y " + y + " under the last drawn row picks " + got);
						break;
					}
				}
				if (CityPlanLayout.rowAt(p, scroll, count, l[0] + l[2] - 2, l[1] + 4) != -1) {
					problems.add(at + "a click on the scroll bar picks a zone");
				}
				if (CityPlanLayout.rowAt(p, scroll, count, l[0] + 10, l[1] + l[3] + 2) != -1) {
					problems.add(at + "a click under the list picks a zone");
				}
			}
			for (int i = 0; i < count; i++) {
				if (!reached[i]) {
					problems.add(at + "zone " + i + " can't be picked at any scroll (rows " + rows + ")");
				}
			}
			// two zones: the third row is empty
			if (rows >= 3 && CityPlanLayout.rowAt(p, 0, 2, l[0] + 10, l[1] + 1 + 2 * CityPlanLayout.ROW + 3) != -1) {
				problems.add(at + "an empty row under two zones picks one");
			}
			if (CityPlanLayout.rowAt(p, 0, 0, l[0] + 10, l[1] + 4) != -1) {
				problems.add(at + "an empty plan's list picks a zone");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
		helper.succeed();
	}

	/**
	 * The new sentences with their real arguments, in American English: the lintel's tool word for each tool, the width
	 * line's numbers matching the road widths the plan uses, and no raw key or placeholder left.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theNewLinesReadWithTheirRealArguments(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (String id : CityPlanLayout.RAIL) {
			if (id.equals("undo")) {
				continue;
			}
			String name = Component.translatable("screen.aliveworkplace.city_plan." + id).getString();
			String line = Component.translatable("screen.aliveworkplace.city_plan.tool", Component.translatable("screen.aliveworkplace.city_plan." + id)).getString();
			if (!line.equals("Tool: " + name) || name.startsWith("screen.")) {
				problems.add(id + " reads " + line);
			}
		}
		String width = Component.translatable("screen.aliveworkplace.city_plan.whisper.width").getString();
		if (!width.contains("lane " + CityPlan.Road.LANE + " block") || !width.contains("street " + CityPlan.Road.STREET)
			|| !width.contains("avenue " + CityPlan.Road.AVENUE)) {
			problems.add("the width line doesn't match the road widths: " + width);
		}
		for (String key : List.of("whisper.brush", "whisper.area", "whisper.eraser", "whisper.road", "whisper.wall", "whisper.undo", "whisper.new",
			"whisper.list", "whisper.name", "whisper.style", "whisper.kind", "whisper.delete", "whisper.wall_closed", "whisper.close")) {
			String s = Component.translatable("screen.aliveworkplace.city_plan." + key).getString();
			if (s.startsWith("screen.") || s.contains("%") || s.matches(".*\\b(colour|grey|cancelled|centre)\\b.*") || !s.endsWith(".")) {
				problems.add(key + " reads " + s);
			}
		}
		for (String key : List.of("whisper.map", "whisper.done", "whisper.close", "pick")) {
			String s = Component.translatable("screen.aliveworkplace.colony_map." + key).getString();
			if (s.startsWith("screen.") || s.contains("%") || !s.endsWith(".")) {
				problems.add("charter " + key + " reads " + s);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
