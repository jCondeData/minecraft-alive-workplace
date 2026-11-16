package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.client.CopperStyle;
import io.github.jcondedata.aliveworkplace.client.CopperStyle.Kind;
import io.github.jcondedata.aliveworkplace.client.TableStyle;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * SCENE=copperline (ROADMAP 47.1): Copperline's shared parts in every state, like docs/design/copperline/boards/
 * Components.png, drawn with the same CopperStyle and CopperLayout code every screen uses. Dev only (the screenshot
 * harness opens it). Three pages: the shell (plaque, lintel with coins and close, the Hall's rail, the stage, the whisper
 * line), the parts (buttons, rail tabs, toggles, slider, number field, list rows, bars, slots, chips, text fields and the
 * scroll bar) and the boxes (tooltip, toast, confirm dialog, "What happens", refusal, empty state).
 */
public final class CopperlinePartsScreen extends Screen {
	public enum Page { SHELL, PARTS, BOXES }

	private Page page = Page.SHELL;
	/** The rail entry drawn as hovered on the shell page (the scene points at it). */
	private int railHover = Section.PEOPLE.ordinal();

	public CopperlinePartsScreen() {
		super(t("title"));
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("screen.aliveworkplace.copperline.parts." + key, args);
	}

	public void show(Page page) {
		this.page = page;
	}

	public Page page() {
		return page;
	}

	public int[] window() {
		return CopperLayout.window(width, height);
	}

	public boolean compact() {
		return CopperLayout.compact(width, height);
	}

	public int railWidth() {
		int text = 0;
		for (Section s : Section.values()) {
			text = Math.max(text, font.width(s.title()));
		}
		return CopperLayout.railWidth(window(), text, compact());
	}

	/** The shell page's rail entries (the same places as the Village Hall's rail). */
	public int[][] railEntries() {
		return CopperLayout.railEntries(CopperLayout.rail(window(), railWidth()), Section.values().length, compact());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g, mouseX, mouseY, partialTick);
		int[] win = window();
		CopperStyle.window(g, win[0], win[1], win[2], win[3]);
		Component title = page == Page.SHELL ? Section.OVERVIEW.title() : t("title");
		int[] plaque = CopperStyle.plaque(g, font, title, win);
		Component where = page == Page.SHELL ? t("where") : t("page." + page.name().toLowerCase());
		int[] lintel = CopperLayout.lintel(win, plaque);
		// coins on the lintel, then where you are
		String amount = "1,284";
		int cw = font.width(amount) + 15;
		CopperStyle.coins(g, font, amount, lintel[0] + lintel[2] - cw, lintel[1] + 1);
		int[] narrowed = {win[0], win[1], win[2] - cw - 4, win[3]};
		CopperStyle.lintel(g, font, narrowed, plaque, where, false);
		CopperStyle.close(g, CopperLayout.close(win), CopperLayout.inside(CopperLayout.close(win), mouseX, mouseY));
		int[] wh = CopperLayout.whisper(win);
		Component whisper = switch (page) {
			case SHELL -> Component.translatable("screen.aliveworkplace.copperline.rail_whisper", Section.of(railHover).title(), Section.of(railHover).about());
			case PARTS -> t("whisper.parts");
			case BOXES -> t("whisper.boxes");
		};
		TableStyle.footerHint(g, font, whisper, wh[0] + 4, wh[1] + 3, wh[2] - 8);
		switch (page) {
			case SHELL -> shell(g, win, mouseX, mouseY);
			case PARTS -> parts(g, CopperLayout.stage(win, 0));
			case BOXES -> boxes(g, CopperLayout.stage(win, 0));
		}
	}

	private void shell(GuiGraphics g, int[] win, int mouseX, int mouseY) {
		int side = railWidth();
		int[] rail = CopperLayout.rail(win, side);
		g.fill(rail[0] + rail[2], rail[1], rail[0] + rail[2] + 1, rail[1] + rail[3], CopperStyle.DEEP_600);
		int[][] entries = railEntries();
		int live = CopperLayout.at(entries, mouseX, mouseY);
		if (live >= 0) {
			railHover = live;
		}
		int[] counts = {0, 2, 0, 0, 0, 3, 0, 0, 0, 0};
		for (Section s : Section.values()) {
			CopperStyle.railEntry(g, font, entries[s.ordinal()], CopperStyle.SECTION_ICONS.get(s.ordinal()), null, s.title(),
				counts[s.ordinal()], s == Section.OVERVIEW, s.ordinal() == railHover && s != Section.OVERVIEW, compact());
		}
		int[] stage = CopperLayout.stage(win, side);
		int x = stage[0];
		int y = stage[1];
		int w = stage[2];
		g.drawString(font, t("village"), x, y + 2, CopperStyle.QUARTZ, true);
		int cx = x + font.width(t("village")) + 8;
		cx += CopperStyle.chip(g, font, t("rank").getString(), cx, y, CopperStyle.LANTERN) + 4;
		if (cx + 40 < x + w) {
			CopperStyle.progress(g, cx, y + 3, Math.min(50, x + w - cx), 5, 0.68f, CopperStyle.COPPER);
		}
		y += 16;
		y += CopperStyle.whatHappens(g, font, t("what_happens_body"), x, y, w) + 4;
		int emptyH = stage[1] + stage[3] - y;
		if (emptyH >= 40) {
			CopperStyle.empty(g, font, "quests", t("empty.title"), t("empty.line"), emptyH >= 62 ? t("empty.action") : null, x, y, w, emptyH, false);
		}
	}

	private void heading(GuiGraphics g, Component text, int x, int y, int w) {
		g.drawString(font, text, x, y, CopperStyle.TUFF, false);
		int tw = font.width(text) + 4;
		if (tw < w) {
			g.fill(x + tw, y + 4, x + w, y + 5, CopperStyle.DEEP_600);
		}
	}

	private int bw(Component label, String glyph) {
		return font.width(label) + 12 + (glyph != null ? 10 : 0);
	}

	private void parts(GuiGraphics g, int[] stage) {
		int gap = 10;
		int colW = (stage[2] - gap) / 2;
		int x = stage[0];
		int y = stage[1];
		// Buttons: neutral normal, hover, pressed, off; copper normal, hover, pressed; danger, small, glyph-only, locked.
		heading(g, t("buttons"), x, y, colW);
		y += 11;
		int bx = x;
		Component[] states = {t("normal"), t("hover"), t("pressed"), t("off")};
		for (int i = 0; i < 4; i++) {
			int w = bw(states[i], null);
			CopperStyle.button(g, font, states[i], null, bx, y, w, 14, Kind.NEUTRAL, i == 1, i == 2, i == 3);
			bx += w + 3;
		}
		y += 17;
		bx = x;
		Component[] copper = {t("place"), t("hover"), t("pressed")};
		for (int i = 0; i < 3; i++) {
			int w = bw(copper[i], null);
			CopperStyle.button(g, font, copper[i], null, bx, y, w, 14, Kind.COPPER, i == 1, i == 2, false);
			bx += w + 3;
		}
		y += 17;
		bx = x;
		int dw = bw(t("demolish"), "bad");
		CopperStyle.button(g, font, t("demolish"), "bad", bx, y, dw, 14, Kind.DANGER, false, false, false);
		bx += dw + 3;
		int sw = bw(t("small"), null);
		CopperStyle.button(g, font, t("small"), null, bx, y + 1, sw, 12, Kind.NEUTRAL, false, false, false);
		bx += sw + 3;
		CopperStyle.button(g, font, null, "bad", bx, y + 1, 12, 12, Kind.DANGER, false, false, false);
		bx += 15;
		int lw = bw(t("rank_lock"), "lock");
		CopperStyle.button(g, font, t("rank_lock"), "lock", bx, y, lw, 14, Kind.NEUTRAL, false, false, true);
		y += 19;
		// Rail tabs: normal, hover with a count, current with a count.
		heading(g, t("rail_tabs"), x, y, colW);
		y += 11;
		int tw = Math.min(colW, 120);
		CopperStyle.railEntry(g, font, new int[]{x, y, tw, 15}, "trade", null, Section.TRADE.title(), 0, false, false, false);
		CopperStyle.railEntry(g, font, new int[]{x, y + 16, tw, 15}, "quests", null, Section.QUESTS.title(), 3, false, true, false);
		CopperStyle.railEntry(g, font, new int[]{x, y + 32, tw, 15}, "people", null, Section.PEOPLE.title(), 2, true, false, false);
		y += 51;
		// Toggles, a slider, a number field.
		heading(g, t("toggles"), x, y, colW);
		y += 11;
		int tx = x;
		Component[] toggles = {t("off"), t("on"), t("locked")};
		for (int i = 0; i < 3; i++) {
			CopperStyle.toggle(g, tx, y, i == 1, i == 2, false);
			g.drawString(font, toggles[i], tx + 23, y + 1, i == 2 ? CopperStyle.OFF_TEXT : CopperStyle.QUARTZ, false);
			tx += 26 + font.width(toggles[i]) + 6;
		}
		y += 14;
		g.drawString(font, t("work_speed"), x, y + 1, CopperStyle.QUARTZ, false);
		int lx = x + font.width(t("work_speed")) + 6;
		CopperStyle.slider(g, lx, y, Math.max(20, colW - (lx - x) - 32), 0.6f, true);
		g.blitSprite(CopperStyle.WELL, x + colW - 28, y - 1, 28, 11);
		g.drawString(font, "150%", x + colW - 26, y + 1, CopperStyle.QUARTZ, false);
		y += 14;
		g.drawString(font, t("beds"), x, y + 2, CopperStyle.QUARTZ, false);
		CopperStyle.number(g, font, "4", x + colW - 54, y, 24, true, 2);

		// Second column: list rows, bars and chips, fields and the scroll bar.
		x = stage[0] + colW + gap;
		y = stage[1];
		heading(g, t("list_rows"), x, y, colW);
		y += 11;
		int pc = x + colW - 76;
		int tc = x + colW - 34;
		g.drawString(font, t("goods"), x + 4, y + 2, CopperStyle.TUFF, false);
		g.drawString(font, t("price"), pc, y + 2, CopperStyle.TUFF, false);
		g.drawString(font, t("trend"), tc, y + 2, CopperStyle.TUFF, false);
		CopperStyle.headerRow(g, x, y, colW);
		y += 13;
		String[][] rows = {{"bread", "6", "up", "+2"}, {"iron", "18", "down", "-3"}, {"wool", "4", "steady", "0"}, {"glass", "9", "up", "+1"}};
		for (int i = 0; i < rows.length; i++) {
			boolean over = i == 2;
			boolean selected = i == 3;
			CopperStyle.rowBand(g, x, y, colW, 12, i % 2 == 1, over, selected);
			g.drawString(font, t("row." + rows[i][0]), x + 4, y + 2, CopperStyle.QUARTZ, selected);
			g.blitSprite(CopperStyle.glyph("coin"), pc, y + 2, 8, 8);
			g.drawString(font, rows[i][1], pc + 10, y + 2, CopperStyle.QUARTZ, false);
			g.blitSprite(CopperStyle.glyph(rows[i][2]), tc, y + 2, 8, 8);
			int trend = rows[i][2].equals("up") ? CopperStyle.VERDIGRIS : rows[i][2].equals("down") ? CopperStyle.REDSTONE : CopperStyle.TUFF;
			g.drawString(font, rows[i][3], tc + 10, y + 2, trend, false);
			y += 12;
		}
		g.blitSprite(CopperStyle.glyph("lock"), x + 4, y + 2, 8, 8);
		g.drawString(font, t("row.amethyst"), x + 14, y + 2, CopperStyle.OFF_TEXT, false);
		g.drawString(font, t("rank5"), tc - 8, y + 2, CopperStyle.OFF_TEXT, false);
		y += 15;
		heading(g, t("progress"), x, y, colW);
		y += 11;
		int[] bars = {CopperStyle.COPPER, CopperStyle.VERDIGRIS, CopperStyle.LANTERN, CopperStyle.REDSTONE};
		float[] fill = {0.64f, 1f, 0.3f, 0.12f};
		for (int i = 0; i < 4; i++) {
			CopperStyle.progress(g, x + i * (colW / 4), y, colW / 4 - 4, 5, fill[i], bars[i]);
		}
		y += 8;
		CopperStyle.slot(g, font, new ItemStack(Items.WHEAT, 48), x, y, false);
		CopperStyle.slot(g, font, new ItemStack(Items.BREAD, 3), x + 20, y, true);
		int chx = x + 42;
		chx += CopperStyle.coins(g, font, "1,284", chx, y + 3) + 3;
		chx += CopperStyle.chip(g, font, t("chip.home").getString(), chx, y + 3, CopperStyle.TUFF) + 3;
		chx += CopperStyle.chip(g, font, t("chip.ready").getString(), chx, y + 3, CopperStyle.VERDIGRIS) + 3;
		if (chx + 20 < x + colW) {
			CopperStyle.chip(g, font, t("chip.low").getString(), chx, y + 3, CopperStyle.REDSTONE);
		}
		y += 22;
		heading(g, t("fields"), x, y, colW);
		y += 11;
		int fw = colW - 8;
		CopperStyle.textField(g, font, "", t("find"), "search", x, y, fw, false, null);
		CopperStyle.textField(g, font, "Mir", null, "search", x, y + 16, fw, true, null);
		CopperStyle.textField(g, font, "Oakhaven!!", null, null, x, y + 32, fw, false, t("letters_only"));
		CopperStyle.scrollbar(g, x + colW - 5, y, 46, 10, 40, 120, false);
	}

	private void boxes(GuiGraphics g, int[] stage) {
		int gap = 10;
		int colW = (stage[2] - gap) / 2;
		int x = stage[0];
		int y = stage[1];
		heading(g, t("tooltip"), x, y, colW);
		y += 11;
		CopperStyle.tooltip(g, font, t("tooltip.title"), List.of(t("tooltip.line")), CopperStyle.Status.WARNING, t("tooltip.status"), x, y);
		y += 40;
		heading(g, t("toast"), x, y, colW);
		y += 11;
		CopperStyle.toast(g, font, "quests", t("toast.title"), t("toast.line"), x, y, colW);
		y += 36;
		heading(g, t("confirm"), x, y, colW);
		y += 11;
		CopperStyle.confirm(g, font, t("confirm.question"), t("confirm.body"), t("confirm.keep"), t("confirm.do"), x, y, colW, -1);

		x = stage[0] + colW + gap;
		y = stage[1];
		heading(g, t("notes"), x, y, colW);
		y += 11;
		y += CopperStyle.whatHappens(g, font, t("what_happens_body"), x, y, colW) + 4;
		y += CopperStyle.refusal(g, font, t("refusal.title"), t("refusal.body"), x, y, colW) + 6;
		heading(g, t("empty"), x, y, colW);
		y += 11;
		int h = Math.min(80, stage[1] + stage[3] - y);
		CopperStyle.empty(g, font, "quests", t("empty.title"), t("empty.line"), t("empty.action"), x, y, colW, h, false);
		// The statuses: a glyph, a word and a color each.
		int sx = stage[0];
		int sy = stage[1] + stage[3] - 10;
		for (CopperStyle.Status s : CopperStyle.Status.values()) {
			g.blitSprite(s.glyph, sx, sy, 8, 8);
			Component word = t("status." + s.name().toLowerCase());
			g.drawString(font, word, sx + 10, sy, s.color, false);
			sx += 16 + font.width(word);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
