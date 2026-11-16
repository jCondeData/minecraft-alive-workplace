package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.client.CopperStyle;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * SCENE=copperline (ROADMAP 47.1): Copperline's shared pieces, like docs/design/copperline/boards/Components.png. The
 * parts screen ({@link CopperlinePartsScreen}) at GUI scale 2: the shell with the Hall's rail, every part in every state,
 * the boxes; then at GUI scale 3 (320x180), where the rail turns into a two-column icon grid.
 */
final class CopperlineScene {
	private int tick;
	private CopperlinePartsScreen screen;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
		}
		if (tick == 40) {
			screen = new CopperlinePartsScreen();
			mc.setScreen(screen);
		}
		if (tick == 70 && screen != null) {
			int missing = 0;
			for (ResourceLocation id : sprites()) {
				if (mc.getGuiSprites().getSprite(id).contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
					missing++;
					Showcase.problem("missing Copperline sprite " + id);
				}
			}
			Showcase.check(missing == 0, "every Copperline sprite loaded (" + sprites().length + " nine-slices, glyphs and icons)");
			Showcase.check(!screen.compact() && screen.railEntries()[0][0] == screen.railEntries()[1][0],
				"at GUI scale " + (int) mc.getWindow().getGuiScale() + " the rail is one column of entries with words");
			ScreenshotHarness.shot(mc, "01_shell");
			screen.show(CopperlinePartsScreen.Page.PARTS);
		}
		if (tick == 90 && screen != null) {
			ScreenshotHarness.shot(mc, "02_parts");
			screen.show(CopperlinePartsScreen.Page.BOXES);
		}
		if (tick == 110 && screen != null) {
			ScreenshotHarness.shot(mc, "03_boxes");
			screen.show(CopperlinePartsScreen.Page.SHELL);
			mc.options.guiScale().set(3);
			mc.resizeDisplay();
		}
		if (tick == 130 && screen != null) {
			int[][] entries = screen.railEntries();
			int[] win = screen.window();
			boolean grid = screen.compact() && entries[0][1] == entries[1][1] && entries[0][0] != entries[1][0];
			boolean inside = true;
			for (int[] e : entries) {
				inside &= e[0] >= win[0] && e[1] >= win[1] && e[0] + e[2] <= win[0] + win[2] && e[1] + e[3] <= win[1] + win[3];
			}
			Showcase.check(grid && inside, "at 320x180 (GUI scale 3, now " + screen.width + "x" + screen.height
				+ ") the rail is a two-column icon grid inside the window");
			Showcase.check(CopperLayout.plaque(win, 60)[1] >= 0, "the plaque stays on the screen at 320x180");
			ScreenshotHarness.shot(mc, "04_compact");
		}
		if (tick == 150) {
			mc.setScreen(null);
			mc.stop();
		}
	}

	private static ResourceLocation[] sprites() {
		return new ResourceLocation[]{CopperStyle.WINDOW, CopperStyle.PLAQUE, CopperStyle.PANEL, CopperStyle.WELL, CopperStyle.WELL_FOCUS,
			CopperStyle.WELL_ERROR, CopperStyle.TOOLTIP, CopperStyle.TOAST, CopperStyle.BUTTON, CopperStyle.BUTTON_HOVER,
			CopperStyle.BUTTON_PRESSED, CopperStyle.BUTTON_OFF, CopperStyle.COPPER_BUTTON, CopperStyle.COPPER_HOVER,
			CopperStyle.COPPER_PRESSED, CopperStyle.DANGER, CopperStyle.glyph("good"), CopperStyle.glyph("bad"), CopperStyle.glyph("info"),
			CopperStyle.glyph("star"), CopperStyle.glyph("warning"), CopperStyle.glyph("coin"), CopperStyle.glyph("lock"),
			CopperStyle.icon("overview"), CopperStyle.icon("people"), CopperStyle.icon("homes"), CopperStyle.icon("buildings"),
			CopperStyle.icon("defense"), CopperStyle.icon("quests"), CopperStyle.icon("trade"), CopperStyle.icon("calendar"),
			CopperStyle.icon("laws"), CopperStyle.icon("map")};
	}
}
