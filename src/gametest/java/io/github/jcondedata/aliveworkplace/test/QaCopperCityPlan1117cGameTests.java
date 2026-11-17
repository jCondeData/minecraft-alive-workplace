package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout.Charter;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout.Plan;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * QA (lane c, 2026-11-17) for ROADMAP 44.3, from the spec: the City Plan and the Colony Charter in Copperline, with the map
 * and its zone paint kept and nothing the screens did lost. The screens draw and take their clicks in
 * {@link CityPlanLayout}'s boxes, so these tests check them on screens the item's own tests don't (GUI scale 1 at
 * 1920x1080, an ultrawide, 4:3, a tall window, both sides of the compact switch): every tool, button, list and the map
 * inside the window and apart; a bigger screen never gets a smaller map; the zone list's row picking at its edges (no
 * zones, scrolled, the row past the last zone, the pixel under the list); and every new sentence rendered with its
 * numbers, in American English.
 */
public class QaCopperCityPlan1117cGameTests implements FabricGameTest {
	/** GUI sizes: scale 1, 2 and 4 of 1920x1080, 3440x1440 at 2, 1024x768 at 2, 1080x1920 at 3, 854x480 at 1, and the compact switch from both sides. */
	private static final int[][] SCREENS = {{1920, 1080}, {960, 540}, {480, 270}, {1720, 720}, {512, 384}, {360, 640}, {854, 480},
		{400, 240}, {399, 240}, {427, 220}, {427, 219}, {320, 240}};

	private static int width(String key, Object... args) {
		return Component.translatable(key, args).getString().length() * 6;
	}

	private static Plan plan(int sw, int sh) {
		int rail = 0;
		for (String id : CityPlanLayout.RAIL) {
			rail = Math.max(rail, width("screen.aliveworkplace.city_plan." + id));
		}
		return CityPlanLayout.plan(sw, sh, width("screen.aliveworkplace.city_plan.title", "Thornholm"), rail,
			Math.max(44, width("screen.aliveworkplace.city_plan.new") + 12));
	}

	private static Charter charter(int sw, int sh) {
		return CityPlanLayout.charter(sw, sh, width("screen.aliveworkplace.colony_map.title", "Thornholm"),
			Math.max(60, width("screen.aliveworkplace.colony_map.done") + 16));
	}

	private static boolean within(int[] in, int[] out) {
		return in[0] >= out[0] && in[1] >= out[1] && in[0] + in[2] <= out[0] + out[2] && in[1] + in[3] <= out[1] + out[3];
	}

	private static void apart(String at, int[] win, List<String> names, List<int[]> boxes, List<int[]> keepClear, List<String> problems) {
		for (int i = 0; i < boxes.size(); i++) {
			int[] b = boxes.get(i);
			if (b[2] <= 0 || b[3] <= 0 || !within(b, win)) {
				problems.add(at + names.get(i) + Arrays.toString(b) + " is empty or leaves the window " + Arrays.toString(win));
			}
			for (int[] clear : keepClear) {
				if (CopperLayout.overlap(b, clear)) {
					problems.add(at + names.get(i) + " runs under " + Arrays.toString(clear));
				}
			}
			for (int j = 0; j < i; j++) {
				if (CopperLayout.overlap(b, boxes.get(j))) {
					problems.add(at + names.get(i) + " overlaps " + names.get(j));
				}
			}
		}
	}

	/** Every part of the City Plan inside the window and apart, on every screen, and the map never shrinks as the screen grows. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void cityPlanPartsFitOnEveryScreenSize(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			Plan p = plan(s[0], s[1]);
			List<String> names = new ArrayList<>();
			List<int[]> boxes = new ArrayList<>();
			for (int i = 0; i < p.railEntries().length; i++) {
				names.add("tool " + CityPlanLayout.RAIL[i]);
				boxes.add(p.railEntries()[i]);
			}
			String[] parts = {"map", "width/wall", "road style", "ZONES", "New", "zone list", "name", "style", "kind", "Renew", "Delete"};
			int[][] partBoxes = {p.map(), p.option(), p.roadStyle(), p.zonesHead(), p.newButton(), p.list(), p.name(), p.style(), p.kind(),
				p.renew(), p.delete()};
			for (int i = 0; i < parts.length; i++) {
				names.add(parts[i]);
				boxes.add(partBoxes[i]);
			}
			if (p.legend()[3] > 0) {
				names.add("legend");
				boxes.add(p.legend());
			}
			apart(at, p.win(), names, boxes, List.of(p.whisper(), p.close(), p.lintel()), problems);
			if (p.compact() != (s[0] < 400 || s[1] < 220)) {
				problems.add(at + "compact is " + p.compact());
			}
			if (p.map()[2] != p.map()[3] || p.map()[2] % CityPlan.GRID != 0 || p.map()[2] < 2 * CityPlan.GRID) {
				problems.add(at + "the map " + Arrays.toString(p.map()) + " isn't a square of whole cells, 2 pixels or more");
			}
			if (p.listRows() < 1 || p.list()[1] + 1 + p.listRows() * CityPlanLayout.ROW > p.list()[1] + p.list()[3]) {
				problems.add(at + p.listRows() + " zone rows don't fit the list " + Arrays.toString(p.list()));
			}
			for (int i = 0; i < p.railEntries().length; i++) {
				int[] e = p.railEntries()[i];
				if (CopperLayout.at(p.railEntries(), e[0] + e[2] / 2.0, e[1] + e[3] / 2.0) != i) {
					problems.add(at + "tool " + CityPlanLayout.RAIL[i] + " can't be clicked where it's drawn");
				}
			}
		}
		// a bigger screen of the same shape never gets a smaller map, or fewer zone rows
		int[][][] grows = {{{480, 270}, {960, 540}}, {{960, 540}, {1920, 1080}}, {{427, 240}, {854, 480}}};
		for (int[][] g : grows) {
			Plan small = plan(g[0][0], g[0][1]);
			Plan big = plan(g[1][0], g[1][1]);
			if (big.map()[2] < small.map()[2] || big.listRows() < small.listRows()) {
				problems.add(g[1][0] + "x" + g[1][1] + " has a smaller map or fewer rows than " + g[0][0] + "x" + g[0][1] + ": "
					+ big.map()[2] + "/" + big.listRows() + " vs " + small.map()[2] + "/" + small.listRows());
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		helper.succeed();
	}

	/** The zone list's row picking at its edges: no zones, fewer than fit, scrolled, the last row, the pixel under the list. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void zoneRowsPickTheRightZoneAtTheEdges(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			Plan p = plan(s[0], s[1]);
			int[] l = p.list();
			int rows = p.listRows();
			double x = l[0] + 10;
			double firstY = l[1] + 1 + CityPlanLayout.ROW / 2.0;
			if (CityPlanLayout.rowAt(p, 0, 0, x, firstY) != -1) {
				problems.add(at + "a row was picked with no zones");
			}
			if (CityPlanLayout.rowAt(p, 0, 1, x, firstY + CityPlanLayout.ROW) != -1) {
				problems.add(at + "the empty row under the only zone picked something");
			}
			int count = CityPlan.MAX_ZONES;
			for (int r = 0; r < Math.min(rows, count); r++) {
				double y = firstY + r * CityPlanLayout.ROW;
				if (CityPlanLayout.rowAt(p, 0, count, x, y) != r) {
					problems.add(at + "row " + r + " picked " + CityPlanLayout.rowAt(p, 0, count, x, y));
				}
				int scroll = Math.max(0, count - rows);
				if (CityPlanLayout.rowAt(p, scroll, count, x, y) != scroll + r) {
					problems.add(at + "scrolled " + scroll + ", row " + r + " picked " + CityPlanLayout.rowAt(p, scroll, count, x, y));
				}
			}
			if (rows > count && CityPlanLayout.rowAt(p, 0, count, x, firstY + count * CityPlanLayout.ROW) != -1) {
				problems.add(at + "the empty row under the last zone picked something");
			}
			if (CityPlanLayout.rowAt(p, 0, count, x, l[1] + l[3] + 1) != -1 || CityPlanLayout.rowAt(p, 0, count, l[0] - 1, firstY) != -1) {
				problems.add(at + "a click outside the list picked a zone");
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		helper.succeed();
	}

	/** The Colony Charter's map, the spot, the ring and Done inside the window and apart on every screen, Done clickable. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void colonyCharterPartsFitOnEveryScreenSize(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			String at = s[0] + "x" + s[1] + ": ";
			Charter c = charter(s[0], s[1]);
			List<String> names = new ArrayList<>(List.of("map", "THE SPOT", "spot", "THE RING", "Done"));
			List<int[]> boxes = new ArrayList<>(List.of(c.map(), c.spotHead(), c.spot(), c.ringHead(), c.done()));
			if (c.ring()[3] > 0) {
				names.add("ring");
				boxes.add(c.ring());
			}
			apart(at, c.win(), names, boxes, List.of(c.whisper(), c.close(), c.lintel()), problems);
			if (c.map()[2] != c.map()[3] || c.map()[2] < 64 || c.map()[2] > 256) {
				problems.add(at + "the map " + Arrays.toString(c.map()) + " should be a square of 64 to 256 pixels");
			}
			if (c.done()[2] < 30 || c.done()[3] < 10) {
				problems.add(at + "Done " + Arrays.toString(c.done()) + " is too small to click");
			}
			if (c.spot()[3] < 24) {
				problems.add(at + "the spot's panel " + Arrays.toString(c.spot()) + " can't hold two lines");
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		helper.succeed();
	}

	/** Every sentence 44.3 added is in the lang file, renders its numbers, and is American English. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theNewSentencesExistRenderAndAreAmerican(GameTestHelper helper) {
		String[] keys = {"city_plan.tools", "city_plan.tool", "city_plan.view_only_short", "city_plan.whisper.brush", "city_plan.whisper.area",
			"city_plan.whisper.eraser", "city_plan.whisper.road", "city_plan.whisper.wall", "city_plan.whisper.undo", "city_plan.whisper.new",
			"city_plan.whisper.list", "city_plan.whisper.name", "city_plan.whisper.style", "city_plan.whisper.kind", "city_plan.whisper.delete",
			"city_plan.whisper.width", "city_plan.whisper.wall_closed", "city_plan.whisper.close", "colony_map.lintel", "colony_map.spot_head",
			"colony_map.ring_head", "colony_map.pick", "colony_map.whisper.map", "colony_map.whisper.done", "colony_map.whisper.close",
			// the rail's labels and the plan's old texts the screen still uses
			"city_plan.brush", "city_plan.area", "city_plan.eraser", "city_plan.road", "city_plan.wall", "city_plan.undo", "city_plan.new",
			"city_plan.road_style", "city_plan.road_style.zone"};
		List<String> problems = new ArrayList<>();
		for (String k : keys) {
			String key = "screen.aliveworkplace." + k;
			if (!Language.getInstance().has(key)) {
				problems.add(key + " missing");
				continue;
			}
			String s = Component.translatable(key, "A", "B").getString();
			String lower = s.toLowerCase(Locale.ROOT);
			for (String british : new String[]{"colour", "centre", "grey", "cancelled", "favour", "defence", "armour", "neighbour", "licence"}) {
				if (lower.contains(british)) {
					problems.add(key + " uses British '" + british + "'");
				}
			}
			if (s.contains("%")) {
				problems.add(key + " leaves a placeholder: " + s);
			}
		}
		String ring = Component.translatable("screen.aliveworkplace.colony_map.lintel", 64, 192).getString();
		String tool = Component.translatable("screen.aliveworkplace.city_plan.tool",
			Component.translatable("screen.aliveworkplace.city_plan.brush")).getString();
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(ring.equals("Ring: 64 to 192 blocks out"), "the Charter's lintel reads: " + ring);
		helper.assertTrue(tool.startsWith("Tool: ") && tool.length() > 6 && !tool.contains("screen."), "the City Plan's lintel reads: " + tool);
		helper.succeed();
	}
}
