package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.ui.ChronicleLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The Village Hall's Chronicle page in Copperline (ROADMAP 48.4, boards/Chronicle.png), drawn in the Hall's body: the
 * "Show" column (All and the six groups, each with its count; the open one copper-marked; a click asks the server for that
 * group), Back at its foot, and the stage: the entries newest first under day headers ("DAY 47 · AUTUMN 5"), each a
 * chevron, its icon and its text on one line; a click opens an entry to its whole text and closes it again. The places
 * are {@link ChronicleLayout}'s. The server sends the page as the hall's buttons (VillageHallScreen.renderChronicle), so a
 * chest screen still shows it as before.
 */
final class ChronicleView {
	private final VillageHallMenuScreen screen;
	/** The open entry's slot (-1: none) and its text, so a refreshed page keeps it open only if it's the same entry. */
	private int openSlot = -1;
	private String openText = "";
	private int scroll;
	/** How far the last frame's list could scroll (the screenshot harness reads it). */
	private int maxScroll;
	/** What the last frame drew, for clicks: [kind (0 filter, 1 entry, 2 back), slot, x, y, w, h]. */
	private final List<int[]> hits = new ArrayList<>();
	private Component whisper;

	ChronicleView(VillageHallMenuScreen screen) {
		this.screen = screen;
	}

	private static CompoundTag mark(ItemStack icon, String key) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(key)) {
			return null;
		}
		return data.copyTag().getCompound(key);
	}

	/** Whether {@code menu} shows the chronicle page (its title carries the chronicle's mark). */
	static boolean showing(ChoiceView menu) {
		return mark(menu.slots.get(4).getItem(), VillageHallScreen.CHRONICLE_HEAD) != null;
	}

	/** The lintel's words: "Oakhaven · 128 entries since day 1" ("128 entries" on a small screen). */
	static Component where(ChoiceView menu, Component village, boolean compact) {
		CompoundTag head = mark(menu.slots.get(4).getItem(), VillageHallScreen.CHRONICLE_HEAD);
		int total = head == null ? 0 : head.getInt("total");
		long first = head == null ? 1 : head.getLong("first");
		if (compact) {
			return io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.hall.chronicle.count", total, total);
		}
		return io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.hall.chronicle.where", total, village, total, first);
	}

	/** The whisper line's words for what the last frame had under the mouse (null: nothing of the chronicle's). */
	Component whisper() {
		return whisper;
	}

	/** The Show filters' slots and marks, in group order. */
	private List<int[]> filters(ChoiceView menu) {
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i < Chronicle.Group.values().length; i++) {
			int slot = VillageHallScreen.CHRONICLE_FILTERS + i;
			CompoundTag tag = mark(menu.slots.get(slot).getItem(), VillageHallScreen.CHRONICLE_FILTER);
			if (tag != null) {
				out.add(new int[]{slot, tag.getInt("group"), tag.getInt("count"), tag.getBoolean("open") ? 1 : 0});
			}
		}
		return out;
	}

	/** The rows the stage lists: [slot (-1 for a day header), index of the entry the header is for]. */
	private List<int[]> rows(ChoiceView menu) {
		List<int[]> out = new ArrayList<>();
		long lastDay = Long.MIN_VALUE;
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.slots.get(slot).getItem();
			if (!ChoiceView.shown(icon)) {
				continue;
			}
			CompoundTag tag = mark(icon, VillageHallScreen.CHRONICLE_ENTRY);
			long day = tag == null ? lastDay : tag.getLong("day");
			if (tag != null && day != lastDay) {
				out.add(new int[]{-1, slot});
				lastDay = day;
			}
			out.add(new int[]{slot, slot});
		}
		return out;
	}

	private static Component dayHeader(ItemStack icon) {
		CompoundTag tag = mark(icon, VillageHallScreen.CHRONICLE_ENTRY);
		if (tag == null) {
			return Component.empty();
		}
		return Component.translatable("screen.aliveworkplace.hall.chronicle.day_header", tag.getLong("day"),
			io.github.jcondedata.aliveworkplace.hall.Seasons.Season.values()[Math.floorMod(tag.getInt("season"), 4)].title(), tag.getInt("of"));
	}

	private boolean open(ItemStack icon, int slot) {
		return slot == openSlot && icon.getHoverName().getString().equals(openText);
	}

	void render(GuiGraphics g, Font font, ChoiceView menu, int[] body, int mouseX, int mouseY, boolean compact) {
		hits.clear();
		whisper = null;
		List<int[]> filters = filters(menu);
		int labelW = 0;
		int countW = 0;
		for (int[] f : filters) {
			labelW = Math.max(labelW, font.width(Chronicle.Group.of(f[1]).title()));
			countW = Math.max(countW, font.width(String.valueOf(f[2])));
		}
		int columnW = ChronicleLayout.columnWidth(body, labelW, countW, compact);
		int[] column = ChronicleLayout.column(body, columnW);
		g.fill(column[0], column[1], column[0] + column[2], column[1] + column[3], CopperStyle.VOID);
		if (!compact) {
			g.drawString(font, Component.translatable("screen.aliveworkplace.hall.chronicle.show"), column[0] + 5, column[1] + 3, CopperStyle.TUFF, false);
		}
		int[][] boxes = ChronicleLayout.filters(column, filters.size(), compact);
		for (int i = 0; i < boxes.length; i++) {
			int[] f = filters.get(i);
			Chronicle.Group group = Chronicle.Group.of(f[1]);
			boolean over = io.github.jcondedata.aliveworkplace.ui.CopperLayout.inside(boxes[i], mouseX, mouseY);
			CopperStyle.railEntry(g, font, boxes[i], group.icon, null, group.title(), f[2], f[3] == 1, over, compact);
			hits.add(new int[]{0, f[0], boxes[i][0], boxes[i][1], boxes[i][2], boxes[i][3]});
			if (over) {
				whisper = Component.translatable(group == Chronicle.Group.ALL ? "screen.aliveworkplace.hall.chronicle.show_all_whisper"
					: "screen.aliveworkplace.hall.chronicle.show_whisper", group.title(), f[2]);
			}
		}
		int[] back = ChronicleLayout.back(column);
		ItemStack backIcon = menu.slots.get(0).getItem();
		if (ChoiceView.shown(backIcon)) {
			boolean over = io.github.jcondedata.aliveworkplace.ui.CopperLayout.inside(back, mouseX, mouseY);
			CopperStyle.button(g, font, compact ? Component.literal("<") : backIcon.getHoverName(), null, back[0], back[1], back[2], back[3],
				CopperStyle.Kind.NEUTRAL, over, false, false);
			hits.add(new int[]{2, 0, back[0], back[1], back[2], back[3]});
			if (over) {
				whisper = Component.translatable("screen.aliveworkplace.hall.chronicle.back_whisper");
			}
		}

		int[] list = ChronicleLayout.list(body, columnW);
		CopperStyle.well(g, list[0], list[1], list[2], list[3]);
		List<int[]> rows = rows(menu);
		int textW = ChronicleLayout.textWidth(list);
		boolean[] header = new boolean[rows.size()];
		int[] heights = new int[rows.size()];
		List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			int slot = rows.get(i)[0];
			header[i] = slot < 0;
			if (slot < 0) {
				wrapped.add(List.of());
				continue;
			}
			ItemStack icon = menu.slots.get(slot).getItem();
			List<FormattedCharSequence> lines = font.split(icon.getHoverName(), textW);
			wrapped.add(lines);
			heights[i] = ChronicleLayout.entryHeight(lines.size(), open(icon, slot));
		}
		int[] tops = ChronicleLayout.rows(list, header, heights, 0);
		int content = tops[rows.size()];
		int max = ChronicleLayout.maxScroll(list, content);
		scroll = Math.max(0, Math.min(max, scroll));
		maxScroll = max;
		tops = ChronicleLayout.rows(list, header, heights, scroll);
		g.enableScissor(list[0] + 1, list[1] + 1, list[0] + list[2] - 1, list[1] + list[3] - 1);
		int right = list[0] + list[2] - ChronicleLayout.BAR - 2;
		for (int i = 0; i < rows.size(); i++) {
			int y = tops[i];
			int h = header[i] ? ChronicleLayout.DAY_H : heights[i];
			if (y + h < list[1] || y > list[1] + list[3]) {
				continue;
			}
			if (header[i]) {
				Component day = dayHeader(menu.slots.get(rows.get(i)[1]).getItem());
				String words = day.getString().toUpperCase(java.util.Locale.ROOT);
				g.drawString(font, words, list[0] + 4, y + 3, CopperStyle.TUFF, false);
				int rx = list[0] + 4 + font.width(words) + 4;
				if (rx < right) {
					g.fill(rx, y + 7, right, y + 8, CopperStyle.DEEP_600);
				}
				continue;
			}
			int slot = rows.get(i)[0];
			ItemStack icon = menu.slots.get(slot).getItem();
			boolean entry = mark(icon, VillageHallScreen.CHRONICLE_ENTRY) != null;
			boolean isOpen = entry && open(icon, slot);
			boolean over = entry && mouseX >= list[0] && mouseX < right && mouseY >= Math.max(y, list[1]) && mouseY < Math.min(y + h, list[1] + list[3]);
			if (isOpen) {
				g.fill(list[0] + 2, y, right, y + h, CopperStyle.DEEP_800);
				g.renderOutline(list[0] + 2, y, right - list[0] - 2, h, CopperStyle.COPPER_DARK);
				g.fill(list[0] + 2, y, list[0] + 4, y + h, CopperStyle.COPPER);
			} else {
				CopperStyle.rowBand(g, list[0] + 2, y, right - list[0] - 2, h, false, over, false);
			}
			if (entry) {
				CopperStyle.glyph(g, isOpen ? "open" : "more", list[0] + 5, y + 3);
			}
			g.pose().pushPose();
			g.pose().translate(list[0] + 13, y + 1, 0);
			g.pose().scale(0.75f, 0.75f, 1f);
			g.renderItem(icon, 0, 0);
			g.pose().popPose();
			List<FormattedCharSequence> lines = wrapped.get(i);
			int tx = list[0] + ChronicleLayout.TEXT_X;
			if (isOpen) {
				for (int l = 0; l < lines.size(); l++) {
					g.drawString(font, lines.get(l), tx, y + 3 + l * ChronicleLayout.LINE, CopperStyle.QUARTZ, false);
				}
			} else {
				String text = CopperStyle.trim(font, icon.getHoverName().getString(), right - tx - 2);
				g.drawString(font, text, tx, y + 3, entry ? CopperStyle.QUARTZ : CopperStyle.TUFF, false);
			}
			if (entry && ChronicleLayout.shown(list, y, Math.min(h, ChronicleLayout.ENTRY_H))) {
				hits.add(new int[]{1, slot, list[0], y, right - list[0], h});
			}
			if (over) {
				whisper = Component.translatable(isOpen ? "screen.aliveworkplace.hall.chronicle.close_whisper"
					: lines.size() > 1 ? "screen.aliveworkplace.hall.chronicle.open_whisper" : "screen.aliveworkplace.hall.chronicle.entry_whisper",
					dayHeader(icon));
			}
		}
		g.disableScissor();
		CopperStyle.scrollbar(g, list[0] + list[2] - ChronicleLayout.BAR, list[1] + 1, list[3] - 2, scroll, max, content, false);
		if (whisper == null) {
			whisper = Component.translatable("screen.aliveworkplace.hall.chronicle.hint");
		}
	}

	/** Takes a click on the page; true when it did. */
	boolean clicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		for (int[] hit : hits) {
			if (mx < hit[2] || mx >= hit[2] + hit[4] || my < hit[3] || my >= hit[3] + hit[5]) {
				continue;
			}
			if (hit[0] == 1) {
				ItemStack icon = screen.getMenu().slots.get(hit[1]).getItem();
				if (open(icon, hit[1])) {
					openSlot = -1;
					openText = "";
				} else {
					openSlot = hit[1];
					openText = icon.getHoverName().getString();
				}
				screen.clickSound();
			} else {
				if (hit[0] == 0) {
					scroll = 0;
					openSlot = -1;
				}
				screen.press(hit[1], 0, false);
			}
			return true;
		}
		return false;
	}

	void scrolled(double scrollY) {
		scroll = Math.max(0, scroll - (int) Math.round(scrollY * ChronicleLayout.ENTRY_H * 2));
	}

	/** Where button {@code slot} was drawn last (the screenshot harness), or null. */
	int[] centre(int slot) {
		for (int[] hit : hits) {
			if (hit[1] == slot) {
				return new int[]{hit[2] + hit[4] / 2, hit[3] + Math.min(hit[5], ChronicleLayout.ENTRY_H) / 2};
			}
		}
		return null;
	}

	/** The open entry's slot, or -1 (the screenshot harness). */
	int openSlot() {
		return openSlot;
	}

	/** How far the list is scrolled, and how far the last frame let it go (the screenshot harness). */
	int[] scroll() {
		return new int[]{scroll, maxScroll};
	}

	/** Opens the entry in {@code slot} as a click on it would (the screenshot harness). */
	void open(int slot) {
		openSlot = slot;
		openText = screen.getMenu().slots.get(slot).getItem().getHoverName().getString();
	}
}
