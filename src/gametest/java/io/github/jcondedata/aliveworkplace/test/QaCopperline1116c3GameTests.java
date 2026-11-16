package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA for Copperline's shared pieces (ROADMAP 47.1, docs/design/copperline/README.md), written from the spec, beyond
 * {@link CopperlineShellGameTests} (three screen sizes) and QaHallCopper1116c (the Hall's rail at scale 2 and 3):
 * <ul>
 * <li>the sixteen nine-slices the README names are real GUI sprites: a PNG, a nine-slice .mcmeta that matches its size
 * with borders that leave a middle to stretch, every pixel on the sixteen-color palette and fully opaque or clear;</li>
 * <li>the 8 px status glyphs and the ten Hall section icons: one per section (named by its id), the right size, never
 * blank, never two alike, locked to the palette (plus the art recipe's named material colors);</li>
 * <li>the shell at every GUI-pixel screen size from 320x180 up (not only three): window, plaque, lintel, close X, rail,
 * stage and whisper line inside the screen and apart, the 320x180 rule's icon grid at the small sizes, and any number
 * of rail entries a screen uses (the Storehouse's up to 8, the Hall's 10) clickable and inside the rail;</li>
 * <li>a long or empty title never pushes the plaque over the close X or the lintel's text;</li>
 * <li>the shell's sentences rendered with real arguments, in American English.</li>
 * </ul>
 * The screens themselves (hover, pressed, the off state refusing a click, the confirm dialog's two buttons) are client
 * code a headless server can't open; their places come from {@link CopperLayout}, which these tests drive.
 */
public class QaCopperline1116c3GameTests implements FabricGameTest {
	private static final String SPRITES = "/assets/aliveworkplace/textures/gui/sprites/copperline/";

	/** README, "Palette": the sixteen colors. */
	private static final int[] PALETTE = {0x0B0C10, 0x16171D, 0x22242E, 0x2E313D, 0x3D4150, 0x50556A, 0xA3A8B8, 0xF2EFE6,
		0xE0864A, 0xF7B47C, 0x9C4F2B, 0xFFD25E, 0x4FC3A1, 0xFF6B5E, 0x7FA8FF, 0xC59BFF};
	/** tools/textures/art/copperline.py, MATERIALS: the named material colors the designer's glyph and icon sheets keep. */
	private static final int[] MATERIALS = {0x6F4A2A, 0xB8854F, 0xD9AE74, 0xA8704F, 0xD8A27A, 0xE8C56A, 0xC98A1E, 0x6FBF4A,
		0x3E7F2E, 0x1E5E50, 0x7C55C2, 0x7D8194};
	/** README, "sprites/ui": the 16 nine-slices (button normal/hover/pressed/off, copper normal/hover/pressed, danger, ...). */
	private static final String[] NINE_SLICES = {"window", "plaque", "panel", "well", "well_focus", "well_error", "tooltip", "toast",
		"button", "button_hover", "button_pressed", "button_off", "copper", "copper_hover", "copper_pressed", "danger"};
	/** The status glyphs (README: every status is a glyph, a word and a color) and the shell's own: close, coins, the number field. */
	private static final String[] GLYPHS = {"good", "bad", "info", "star", "warning", "coin", "lock", "plus", "minus", "search",
		"up", "down", "steady", "more", "dot"};

	private static InputStream open(String path) {
		return QaCopperline1116c3GameTests.class.getResourceAsStream(path);
	}

	private static BufferedImage image(String path, List<String> problems) {
		try (InputStream in = open(path)) {
			if (in == null) {
				problems.add(path + " is missing (the game would draw the magenta missing texture)");
				return null;
			}
			BufferedImage img = ImageIO.read(in);
			if (img == null) {
				problems.add(path + " isn't a PNG");
			}
			return img;
		} catch (Exception e) {
			problems.add(path + " can't be read: " + e);
			return null;
		}
	}

	private static Set<Integer> colors(int[]... sets) {
		Set<Integer> out = new HashSet<>();
		for (int[] set : sets) {
			for (int c : set) {
				out.add(c);
			}
		}
		return out;
	}

	/** Every pixel fully clear or fully opaque and on {@code allowed}; returns the opaque pixels' count. */
	private static int onPalette(String name, BufferedImage img, Set<Integer> allowed, List<String> problems) {
		Map<String, Integer> off = new HashMap<>();
		int opaque = 0;
		int partial = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int argb = img.getRGB(x, y);
				int a = argb >>> 24;
				if (a == 0) {
					continue;
				}
				if (a != 255) {
					partial++;
				}
				opaque++;
				int rgb = argb & 0xFFFFFF;
				if (!allowed.contains(rgb)) {
					off.merge(String.format("#%06X", rgb), 1, Integer::sum);
				}
			}
		}
		if (!off.isEmpty()) {
			problems.add(name + " has colors off the Copperline palette: " + off);
		}
		if (partial > 0) {
			problems.add(name + " has " + partial + " half-clear pixels (pixel art is fully opaque or clear)");
		}
		return opaque;
	}

	private static String pixels(BufferedImage img) {
		StringBuilder b = new StringBuilder();
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				b.append(Integer.toHexString(img.getRGB(x, y))).append(',');
			}
		}
		return b.toString();
	}

	/**
	 * README: "sprites/ui the 16 nine-slices". Each is a GUI sprite the game can stretch: a PNG, a .mcmeta whose
	 * gui.scaling is a nine_slice of the PNG's own size, borders that leave at least one pixel in the middle (else a wide
	 * button smears its edge), on the palette.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyNineSliceIsARealStretchableSpriteOnThePalette(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Set<Integer> palette = colors(PALETTE);
		for (String name : NINE_SLICES) {
			String path = SPRITES + name + ".png";
			BufferedImage img = image(path, problems);
			if (img == null) {
				continue;
			}
			try (InputStream in = open(path + ".mcmeta")) {
				if (in == null) {
					problems.add(name + " has no .mcmeta (it would stretch as one picture, not a nine-slice)");
				} else {
					JsonObject scaling = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
						.getAsJsonObject("gui").getAsJsonObject("scaling");
					if (!"nine_slice".equals(scaling.get("type").getAsString())) {
						problems.add(name + "'s scaling is " + scaling.get("type"));
					}
					int w = scaling.get("width").getAsInt();
					int h = scaling.get("height").getAsInt();
					if (w != img.getWidth() || h != img.getHeight()) {
						problems.add(name + "'s .mcmeta says " + w + "x" + h + ", its PNG is " + img.getWidth() + "x" + img.getHeight());
					}
					JsonObject border = scaling.getAsJsonObject("border");
					int l = border.get("left").getAsInt();
					int r = border.get("right").getAsInt();
					int t = border.get("top").getAsInt();
					int bo = border.get("bottom").getAsInt();
					if (l + r >= img.getWidth() || t + bo >= img.getHeight() || l < 1 || r < 1 || t < 1 || bo < 1) {
						problems.add(name + "'s borders " + l + "/" + t + "/" + r + "/" + bo + " leave no middle in " + img.getWidth() + "x" + img.getHeight()
							+ " or no edge (README: a 1-2 px outline)");
					}
				}
			} catch (Exception e) {
				problems.add(name + ".mcmeta can't be read: " + e);
			}
			onPalette(name, img, palette, problems);
		}
		// The states of one piece differ (a hover adds the Lantern edge, a press moves the light, off goes flat).
		Map<String, String> seen = new HashMap<>();
		for (String name : NINE_SLICES) {
			BufferedImage img = image(SPRITES + name + ".png", new ArrayList<>());
			if (img != null) {
				String twin = seen.putIfAbsent(pixels(img), name);
				if (twin != null && (name.startsWith("button") || name.startsWith("copper") || name.startsWith("well") || name.equals("danger"))) {
					problems.add(name + " is the same picture as " + twin + " (a state that looks like another)");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * README: "the ten Hall section icons and the 8 px glyphs". One icon per Hall section, named by its id (the rail draws
	 * icon/<id>), 16x16; the glyphs 8x8; none blank, no two icons alike; locked to the palette and the recipe's named
	 * material colors (wood, wheat, grass, skin).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everySectionIconAndStatusGlyphIsDrawnOnThePalette(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Set<Integer> allowed = colors(PALETTE, MATERIALS);
		helper.assertTrue(Section.values().length == 10, "the Hall has " + Section.values().length + " sections, the spec says ten");
		Map<String, String> seen = new HashMap<>();
		for (Section s : Section.values()) {
			String name = "icon/" + s.id();
			BufferedImage img = image(SPRITES + name + ".png", problems);
			if (img == null) {
				continue;
			}
			if (img.getWidth() != 16 || img.getHeight() != 16) {
				problems.add(name + " is " + img.getWidth() + "x" + img.getHeight() + ", not 16x16");
			}
			if (onPalette(name, img, allowed, problems) < 24) {
				problems.add(name + " is (nearly) blank");
			}
			String twin = seen.putIfAbsent(pixels(img), name);
			if (twin != null) {
				problems.add(name + " is the same picture as " + twin);
			}
		}
		for (String g : GLYPHS) {
			String name = "glyph/" + g;
			BufferedImage img = image(SPRITES + name + ".png", problems);
			if (img == null) {
				continue;
			}
			if (img.getWidth() != 8 || img.getHeight() != 8) {
				problems.add(name + " is " + img.getWidth() + "x" + img.getHeight() + ", not 8x8");
			}
			if (onPalette(name, img, allowed, problems) < 4) {
				problems.add(name + " is (nearly) blank");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static void entries(String at, int[] rail, int count, boolean grid, List<String> problems) {
		int[][] e = CopperLayout.railEntries(rail, count, grid);
		for (int i = 0; i < count; i++) {
			if (!within(e[i], rail)) {
				problems.add(at + count + " entries: entry " + i + " " + box(e[i]) + " leaves the rail " + box(rail));
				return;
			}
			if (CopperLayout.at(e, e[i][0] + e[i][2] / 2.0, e[i][1] + e[i][3] / 2.0) != i) {
				problems.add(at + count + " entries: entry " + i + " can't be clicked");
				return;
			}
			if (e[i][3] < (grid ? 10 : 9) || e[i][2] < 16) {
				problems.add(at + count + " entries: entry " + i + " is only " + e[i][2] + "x" + e[i][3]);
				return;
			}
		}
		if (grid && count > 1 && !(e[0][1] == e[1][1] && e[1][0] > e[0][0])) {
			problems.add(at + "the icon grid isn't two columns");
		}
	}

	/**
	 * The shell at every screen size from 320x180 (GUI scale 3 on 960x540, the 320x180 rule) to 1000x560, in steps, and
	 * both sides of where it turns compact: everything inside the screen and the window, nothing overlapping, the rail an
	 * icon grid on the small screens and one column on GUI scale 2 (427x240 and up), the Hall's ten sections and every
	 * count a screen's rail uses (1-12) inside it and clickable.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theShellHoldsAtEveryScreenSizeFromThreeTwentyByOneEighty(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<int[]> sizes = new ArrayList<>();
		for (int w = 320; w <= 1000; w += 13) {
			for (int h = 180; h <= 560; h += 11) {
				sizes.add(new int[]{w, h});
			}
		}
		for (int[] s : new int[][]{{320, 180}, {320, 240}, {320, 200}, {341, 192}, {399, 239}, {400, 220}, {400, 219}, {427, 240},
			{426, 240}, {455, 256}, {480, 270}, {640, 360}, {960, 540}}) {
			sizes.add(s);
		}
		int widest = 0;
		for (Section sec : Section.values()) {
			widest = Math.max(widest, sec.title().getString().length() * 6);
		}
		for (int[] s : sizes) {
			int sw = s[0];
			int sh = s[1];
			String at = sw + "x" + sh + ": ";
			int[] screen = {0, 0, sw, sh};
			boolean compact = CopperLayout.compact(sw, sh);
			if ((sw == 320 && sh <= 240) && !compact) {
				problems.add(at + "isn't compact (the 320x180 rule)");
			}
			if (sw >= 427 && sh >= 240 && compact) {
				problems.add(at + "is compact though it's GUI scale 2's size or more");
			}
			int[] win = CopperLayout.window(sw, sh);
			if (!within(win, screen) || win[0] < CopperLayout.MARGIN || win[1] < CopperLayout.MARGIN) {
				problems.add(at + "the window " + box(win) + " leaves the screen or its margin");
				continue;
			}
			for (int titleW : new int[]{0, 30, widest, 400, 5000}) {
				int[] plaque = CopperLayout.plaque(win, titleW);
				int[] close = CopperLayout.close(win);
				int[] lintel = CopperLayout.lintel(win, plaque);
				if (!within(plaque, screen) || plaque[0] + plaque[2] > win[0] + win[2]) {
					problems.add(at + "a " + titleW + " px title's plaque " + box(plaque) + " leaves the screen or the window");
				}
				if (CopperLayout.overlap(plaque, close) || CopperLayout.overlap(lintel, close) || CopperLayout.overlap(lintel, plaque)) {
					problems.add(at + "a " + titleW + " px title's plaque or lintel covers the close X " + box(close));
				}
				if (lintel[2] < 40) {
					problems.add(at + "a " + titleW + " px title leaves the lintel only " + lintel[2] + " px for where you are and the coins");
				}
				if (!within(close, win) || close[2] < 9) {
					problems.add(at + "the close X " + box(close) + " leaves the window or is too small to hit");
				}
			}
			int[] whisper = CopperLayout.whisper(win);
			if (!within(whisper, win) || whisper[2] < 100) {
				problems.add(at + "the whisper line " + box(whisper) + " leaves the window or is too short");
			}
			for (boolean grid : new boolean[]{compact, true}) {
				int railW = CopperLayout.railWidth(win, widest, grid);
				int[] rail = CopperLayout.rail(win, railW);
				int[] stage = CopperLayout.stage(win, railW);
				String where = at + (grid ? "icon rail" : "rail") + ": ";
				if (!within(rail, win) || !within(stage, win)) {
					problems.add(where + "the rail " + box(rail) + " or the stage " + box(stage) + " leaves the window " + box(win));
				}
				if (CopperLayout.overlap(rail, stage) || CopperLayout.overlap(rail, whisper) || CopperLayout.overlap(stage, whisper)) {
					problems.add(where + "the rail, the stage and the whisper line overlap");
				}
				if (stage[2] < 150 || stage[3] < 100) {
					problems.add(where + "the stage " + box(stage) + " is too small");
				}
				int[] body = CopperLayout.body(win, railW);
				if (!within(body, win) || body[2] <= 0 || body[3] <= 0 || CopperLayout.overlap(body, rail)) {
					problems.add(where + "the body " + box(body) + " leaves the window or runs onto the rail");
				}
				if (grid && compact && rail[0] + rail[2] > win[0] + win[2] / 3) {
					problems.add(where + "the icon rail takes " + rail[2] + " of " + win[2] + " px");
				}
				for (int count = 1; count <= 12; count++) {
					entries(where, rail, count, grid, problems);
				}
			}
			if (problems.size() > 30) {
				break;
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
		helper.succeed();
	}

	/**
	 * The point lookups every screen takes its clicks with: a box's edges (left and top in, right and bottom out), a null
	 * box, an empty list, and a point between two rail entries finding neither.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aClickFindsOnlyTheBoxUnderIt(GameTestHelper helper) {
		int[] b = {10, 20, 30, 12};
		helper.assertTrue(CopperLayout.inside(b, 10, 20) && CopperLayout.inside(b, 39.9, 31.9), "the box's own edge pixels don't count");
		helper.assertTrue(!CopperLayout.inside(b, 40, 25) && !CopperLayout.inside(b, 20, 32) && !CopperLayout.inside(b, 9.99, 25),
			"a click just past the box finds it");
		helper.assertTrue(!CopperLayout.inside(null, 0, 0), "a missing box finds a click");
		helper.assertTrue(CopperLayout.at(new int[0][], 5, 5) == -1, "an empty list finds something");
		int[] win = CopperLayout.window(480, 270);
		int[] rail = CopperLayout.rail(win, CopperLayout.railWidth(win, 80, false));
		int[][] e = CopperLayout.railEntries(rail, 10, false);
		// The 1-pixel gap under each row.
		int gapY = e[3][1] + e[3][3];
		helper.assertTrue(CopperLayout.at(e, e[3][0] + 5, gapY) == -1 || gapY == e[4][1], "the gap between two rail rows picks one");
		helper.assertTrue(CopperLayout.at(e, e[3][0] + 5, e[3][1]) == 3 && CopperLayout.at(e, e[3][0] + 5, e[3][1] + e[3][3] - 1) == 3,
			"a rail row's top or bottom pixel doesn't pick it");
		helper.assertTrue(CopperLayout.at(e, rail[0] + rail[2] + 1, e[3][1] + 2) == -1, "a click right of the rail picks a section");
		int[] c = CopperLayout.close(win);
		helper.assertTrue(CopperLayout.inside(c, c[0] + c[2] - 1, c[1] + c[3] - 1) && !CopperLayout.inside(c, c[0] + c[2], c[1]),
			"the close X's box is off by one");
		helper.succeed();
	}

	/**
	 * The flowing tab rows and the right-aligned footer buttons with nothing in them, one wider than the room, and many:
	 * never past the right edge, never on top of each other.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void tabsAndFooterButtonsAtTheirEdges(GameTestHelper helper) {
		helper.assertTrue(CopperLayout.flow(new int[0], 0, 0, 100, 4, 18, 21).length == 0, "no tabs gives boxes");
		helper.assertTrue(CopperLayout.rightRow(new int[0], 100, 0, 4, 13).length == 0, "no buttons gives boxes");
		List<String> problems = new ArrayList<>();
		int[] win = CopperLayout.window(320, 180);
		int x0 = win[0] + 8;
		int right = win[0] + win[2] - 8;
		int[] widths = {500, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 300, 12};
		int[][] tabs = CopperLayout.flow(widths, x0, 30, right, 4, 18, 21);
		for (int i = 0; i < tabs.length; i++) {
			if (tabs[i][0] < x0 || tabs[i][0] + tabs[i][2] > right) {
				problems.add("tab " + i + " " + box(tabs[i]) + " leaves " + x0 + "-" + right);
			}
			for (int j = 0; j < i; j++) {
				if (CopperLayout.overlap(tabs[i], tabs[j])) {
					problems.add("tab " + i + " covers tab " + j);
				}
			}
		}
		int[][] row = CopperLayout.rightRow(new int[]{44, 60, 44}, right, 150, 4, 13);
		if (row[2][0] + row[2][2] != right || row[0][0] + row[0][2] + 4 != row[1][0] || row[1][0] + row[1][2] + 4 != row[2][0]) {
			problems.add("the footer buttons aren't right-aligned 4 px apart: " + box(row[0]) + box(row[1]) + box(row[2]));
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The shell's sentences, rendered with real arguments: the rail's whisper "Homes & Food: <what it's for>" for every
	 * section, the Hall's lintel "village, dot, date", the key hint and the "What happens" heading; no raw key, no %s left,
	 * no empty section word; and every Copperline sentence in American English (owner, 2026-10-26).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theShellsSentencesReadInPlainAmericanEnglish(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Section s : Section.values()) {
			String title = s.title().getString();
			String about = s.about().getString();
			if (title.isBlank() || title.startsWith("screen.") || about.isBlank() || about.startsWith("screen.")) {
				problems.add(s.id() + "'s word or what it's for is untranslated: \"" + title + "\" / \"" + about + "\"");
			}
			String whisper = Component.translatable("screen.aliveworkplace.copperline.rail_whisper", s.title(), s.about()).getString();
			if (!whisper.equals(title + ": " + about) || whisper.contains("%")) {
				problems.add(s.id() + "'s rail whisper reads \"" + whisper + "\"");
			}
		}
		Component date = HallSections.date(2, 47, 1);
		String where = Component.translatable("screen.aliveworkplace.copperline.where", Component.literal("Oakhaven"), date).getString();
		if (!where.equals("Oakhaven \u00b7 " + date.getString()) || where.contains("%") || date.getString().startsWith("screen.")) {
			problems.add("the Hall's lintel reads \"" + where + "\"");
		}
		String esc = Component.translatable("screen.aliveworkplace.copperline.key.esc").getString() + " "
			+ Component.translatable("screen.aliveworkplace.copperline.key.close").getString();
		if (!esc.equals("Esc close")) {
			problems.add("the key hint reads \"" + esc + "\" (README: `Esc close`)");
		}
		try (InputStream in = open("/assets/aliveworkplace/lang/en_us.json")) {
			JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			String[] british = {"colour", "grey", "cancelled", "defence", "armour", "centre", "favour", "honour", "organis", "realis",
				"jeweller", "neighbour", "behaviour", "metre", "catalogue"};
			int keys = 0;
			for (String key : lang.keySet()) {
				if (!key.startsWith("screen.aliveworkplace.copperline.")) {
					continue;
				}
				keys++;
				String text = lang.get(key).getAsString();
				String lower = text.toLowerCase();
				for (String b : british) {
					if (lower.contains(b)) {
						problems.add(key + " says \"" + b + "\": " + text);
					}
				}
				if (text.isBlank() || text.contains("  ") || !text.equals(text.strip())) {
					problems.add(key + " is blank or has stray spaces: \"" + text + "\"");
				}
			}
			if (keys < 6) {
				problems.add("only " + keys + " Copperline keys in en_us.json");
			}
		} catch (Exception e) {
			problems.add("en_us.json can't be read: " + e);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
