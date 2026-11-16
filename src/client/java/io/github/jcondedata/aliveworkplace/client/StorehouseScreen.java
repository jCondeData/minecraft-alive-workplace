package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.BoardRows;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Storehouse board (ROADMAP 26.12), in Copperline since 47.4 ({@code docs/design/copperline/boards/Storehouse.png}):
 * the plaque "Storehouse Board", the lintel with where you are and how many things are needed, the rail with who needs
 * them (Everyone, Builders, Workers, Kitchen, Projects: each with its count), the Show toggles (Missing, I carry) and the
 * store's own entries (its stock, the Steward's shopping list, the stock orders), the table of requests (Item with how
 * many the store holds of how many are asked for, Needed by, Brought by, and Give / Stocked / Have 0), "In your pack"
 * with the one copper button, Give all, and the whisper line saying what a click does and who brings it.
 *
 * <p>Nothing here decides anything: a row's click is the chest click the board always took (Shift-click: Give all), the
 * filters only hide rows on this screen, and every row's whole lore shows in the tooltip of its icon. The stock orders'
 * page keeps the shared table look ({@link ChoiceTableScreen}). On a 320x180 screen the rail is a grid of icons, the
 * toggles step aside and the table drops Needed by (the whisper line names the asker).
 */
public class StorehouseScreen extends ChoiceTableScreen {
	private static final int RH = 22;
	private static final int HEAD = 12;
	private static final int FOOT = 22;

	private BoardRows.Group filter = BoardRows.Group.EVERYONE;
	private boolean missingOnly;
	private boolean carryOnly;
	private int tableScroll;
	/** What the whisper line says for the thing under the mouse (null: nothing of the board's). */
	private Component railWhisper;
	private Component bodyWhisper;

	public StorehouseScreen(ChoiceView menu, Inventory inventory, Component title) {
		super(menu, inventory, title, new int[]{0}, 1, -1);
	}

	private ItemStack stack(int slot) {
		return menu.slots.get(slot).getItem();
	}

	/** The requests page (the board's own); the stock orders' page has its back arrow and keeps the shared look. */
	private boolean board() {
		return !shown(stack(StorehouseBoard.BACK_SLOT));
	}

	private boolean compactScreen() {
		return CopperLayout.compact(width, height);
	}

	@Override
	protected boolean customBody() {
		return board();
	}

	@Override
	protected Component headerTitle() {
		return Component.translatable("screen.aliveworkplace.storehouse.board");
	}

	@Override
	protected Component subtitle() {
		if (!board()) {
			return title;
		}
		int n = requestSlots().size();
		if (compactScreen()) { // a small screen: the count alone fits the lintel
			return io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.needs_short", n, n);
		}
		return io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.storehouse.needs", n, title, n);
	}

	// --- the rows ----------------------------------------------------------------------------------------------------

	/** Every request's button, in the board's order. */
	private List<Integer> requestSlots() {
		List<Integer> out = new ArrayList<>();
		for (int slot = StorehouseBoard.FIRST_REQUEST; slot < ChoiceMenu.SIZE; slot++) {
			if (shown(stack(slot)) && BoardRows.row(stack(slot)) != null) {
				out.add(slot);
			}
		}
		return out;
	}

	/** The rows the rail's filters show. */
	private List<Integer> visibleRows() {
		List<Integer> out = new ArrayList<>();
		for (int slot : requestSlots()) {
			if (BoardRows.shown(BoardRows.row(stack(slot)), filter, missingOnly && !compactScreen(), carryOnly && !compactScreen())) {
				out.add(slot);
			}
		}
		return out;
	}

	private int count(BoardRows.Group group) {
		int n = 0;
		for (int slot : requestSlots()) {
			if (BoardRows.shown(BoardRows.row(stack(slot)), group, false, false)) {
				n++;
			}
		}
		return n;
	}

	// --- the rail ----------------------------------------------------------------------------------------------------

	/** A rail entry: 0 a group's filter, 1 a toggle (0 Missing, 1 I carry), 2 a store button (its slot), 3 a heading. */
	private record Entry(int kind, int value, int[] box, Component label) {
	}

	private List<BoardRows.Group> groupsShown() {
		List<BoardRows.Group> out = new ArrayList<>();
		for (BoardRows.Group g : BoardRows.Group.values()) {
			if (g == BoardRows.Group.EVERYONE || count(g) > 0 || g == filter) {
				out.add(g);
			}
		}
		return out;
	}

	private List<Integer> storeSlots() {
		List<Integer> out = new ArrayList<>();
		for (int slot : new int[]{StorehouseBoard.INFO_SLOT, StorehouseBoard.SHOPPING_SLOT, StorehouseBoard.ORDERS_SLOT}) {
			if (shown(stack(slot))) {
				out.add(slot);
			}
		}
		return out;
	}

	private Component storeLabel(int slot) {
		return Component.translatable(slot == StorehouseBoard.INFO_SLOT ? "screen.aliveworkplace.storehouse.rail.stock"
			: slot == StorehouseBoard.SHOPPING_SLOT ? "screen.aliveworkplace.storehouse.rail.shopping" : "screen.aliveworkplace.storehouse.rail.orders");
	}

	@Override
	protected int sidebarWidth() {
		if (!board()) {
			return 0;
		}
		int text = 0;
		for (BoardRows.Group g : BoardRows.Group.values()) {
			text = Math.max(text, font.width(g.title()));
		}
		for (int slot : storeSlots()) {
			text = Math.max(text, font.width(storeLabel(slot)));
		}
		text = Math.max(text, font.width(Component.translatable("screen.aliveworkplace.storehouse.show.carry")) + 12);
		return CopperLayout.railWidth(panelBox(), text, compactScreen());
	}

	private List<Entry> railEntries() {
		List<Entry> out = new ArrayList<>();
		int side = sidebarWidth();
		if (side == 0) {
			return out;
		}
		int[] rail = CopperLayout.rail(panelBox(), side);
		List<BoardRows.Group> groups = groupsShown();
		List<Integer> store = storeSlots();
		if (compactScreen()) {
			int[][] boxes = CopperLayout.railEntries(rail, groups.size() + store.size(), true);
			for (int i = 0; i < groups.size(); i++) {
				out.add(new Entry(0, groups.get(i).ordinal(), boxes[i], groups.get(i).title()));
			}
			for (int i = 0; i < store.size(); i++) {
				out.add(new Entry(2, store.get(i), boxes[groups.size() + i], storeLabel(store.get(i))));
			}
			return out;
		}
		int x = rail[0] + 2;
		int w = rail[2] - 4;
		int y = rail[1] + 3;
		int bottom = rail[1] + rail[3];
		out.add(new Entry(3, 0, new int[]{x + 3, y, w - 3, 10}, Component.translatable("screen.aliveworkplace.storehouse.rail.needed_by")));
		y += 12;
		for (BoardRows.Group g : groups) {
			out.add(new Entry(0, g.ordinal(), new int[]{x, y, w, 15}, g.title()));
			y += 16;
		}
		y += 5;
		if (y + 12 + 2 * 13 <= bottom) {
			out.add(new Entry(3, 1, new int[]{x + 3, y, w - 3, 10}, Component.translatable("screen.aliveworkplace.storehouse.rail.show")));
			y += 12;
			out.add(new Entry(1, 0, new int[]{x, y, w, 12}, Component.translatable("screen.aliveworkplace.storehouse.show.missing")));
			y += 13;
			out.add(new Entry(1, 1, new int[]{x, y, w, 12}, Component.translatable("screen.aliveworkplace.storehouse.show.carry")));
			y += 18;
		}
		if (!store.isEmpty() && y + 12 + store.size() * 16 <= bottom + 1) {
			out.add(new Entry(3, 2, new int[]{x + 3, y, w - 3, 10}, Component.translatable("screen.aliveworkplace.storehouse.rail.store")));
			y += 12;
			for (int slot : store) {
				out.add(new Entry(2, slot, new int[]{x, y, w, 15}, storeLabel(slot)));
				y += 16;
			}
		}
		return out;
	}

	private static ItemStack groupIcon(BoardRows.Group g) {
		return switch (g) {
			case EVERYONE -> new ItemStack(ModBlocks.STOREHOUSE);
			case BUILDERS -> new ItemStack(ModBlocks.BUILDERS_BENCH);
			case WORKERS -> new ItemStack(Items.IRON_PICKAXE);
			case KITCHEN -> new ItemStack(Items.BREAD);
			case PROJECTS -> new ItemStack(Items.BELL);
		};
	}

	private Entry railAt(double mx, double my) {
		for (Entry e : railEntries()) {
			if (e.kind() != 3 && CopperLayout.inside(e.box(), mx, my)) {
				return e;
			}
		}
		return null;
	}

	@Override
	protected void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
		railWhisper = null;
		int side = sidebarWidth();
		if (side == 0) {
			return;
		}
		int[] rail = CopperLayout.rail(panelBox(), side);
		g.fill(rail[0] + rail[2], rail[1], rail[0] + rail[2] + 1, rail[1] + rail[3], CopperStyle.DEEP_600);
		boolean compact = compactScreen();
		Entry over = railAt(mouseX, mouseY);
		ItemStack tooltip = null;
		for (Entry e : railEntries()) {
			boolean hovered = e.equals(over);
			switch (e.kind()) {
				case 3 -> g.drawString(font, CopperStyle.trim(font, e.label().getString().toUpperCase(Locale.ROOT), e.box()[2]), e.box()[0], e.box()[1] + 1,
					CopperStyle.TUFF, false);
				case 0 -> {
					BoardRows.Group group = BoardRows.Group.values()[e.value()];
					CopperStyle.railEntry(g, font, e.box(), null, groupIcon(group), e.label(), count(group), group == filter, hovered, compact);
					if (hovered) {
						railWhisper = group == BoardRows.Group.EVERYONE ? Component.translatable("screen.aliveworkplace.storehouse.rail.filter_all")
							: Component.translatable("screen.aliveworkplace.storehouse.rail.filter", group.title());
					}
				}
				case 1 -> {
					boolean on = e.value() == 0 ? missingOnly : carryOnly;
					int[] b = e.box();
					if (hovered) {
						g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], CopperStyle.DEEP_800);
						g.fill(b[0], b[1], b[0] + 2, b[1] + b[3], CopperStyle.LANTERN);
					}
					CopperStyle.toggle(g, b[0] + 5, b[1] + 1, on, false, hovered);
					g.drawString(font, CopperStyle.trim(font, e.label().getString(), b[2] - 30), b[0] + 29, b[1] + 2, on || hovered ? CopperStyle.QUARTZ : CopperStyle.TUFF, false);
					if (hovered) {
						railWhisper = Component.translatable(e.value() == 0 ? "screen.aliveworkplace.storehouse.show.missing_hint" : "screen.aliveworkplace.storehouse.show.carry_hint");
					}
				}
				default -> {
					ItemStack icon = stack(e.value());
					// the stock as a chest, so it isn't the stock orders' book twice (design critique, 47.4)
					ItemStack shown = e.value() == StorehouseBoard.INFO_SLOT ? new ItemStack(Items.CHEST) : icon.copyWithCount(1);
					CopperStyle.railEntry(g, font, e.box(), null, shown, e.label(), 0, false, hovered, compact);
					if (hovered) {
						tooltip = icon;
						railWhisper = icon.getHoverName();
					}
				}
			}
		}
		if (tooltip != null) {
			pendingTooltip = tooltip;
		}
	}

	/** A rail or table tooltip to draw over everything once the frame is drawn. */
	private ItemStack pendingTooltip;

	@Override
	protected boolean sidebarClicked(double mx, double my, int button) {
		if (!board() || button != 0) {
			return false;
		}
		Entry e = railAt(mx, my);
		if (e == null) {
			return false;
		}
		switch (e.kind()) {
			case 0 -> {
				filter = BoardRows.Group.values()[e.value()];
				tableScroll = 0;
			}
			case 1 -> {
				if (e.value() == 0) {
					missingOnly = !missingOnly;
				} else {
					carryOnly = !carryOnly;
				}
				tableScroll = 0;
			}
			default -> {
				if (e.value() == StorehouseBoard.ORDERS_SLOT) {
					press(e.value(), 0, false);
				} else {
					return true; // the stock and the shopping list are read in their tooltips
				}
			}
		}
		click();
		return true;
	}

	private void click() {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		}
	}

	// --- the table ---------------------------------------------------------------------------------------------------

	/** The table's places: {x, y, w, h} of the body, the table's top and height, the columns and the footer's top. */
	private int[] body() {
		return CopperLayout.body(panelBox(), sidebarWidth());
	}

	private int tableTop() {
		return body()[1] + HEAD;
	}

	private int tableH() {
		int[] b = body();
		return b[1] + b[3] - FOOT - tableTop();
	}

	private int actionW() {
		int w = 40;
		for (int slot : requestSlots()) {
			w = Math.max(w, font.width(actionLabel(BoardRows.row(stack(slot)))) + 12);
		}
		return Math.min(w, 70);
	}

	/** The columns' left edges: icon, Item, Needed by (-1 on a small screen), Brought by, and the button. */
	private int[] columns() {
		int[] b = body();
		int x = b[0];
		int ax = x + b[2] - 8 - actionW();
		int nameX = x + 26;
		int room = ax - 4 - nameX;
		// each column as wide as its widest cell (from 40 up to its share), the item column at least 30%, what's left over
		// shared out, so "6 blocks away" and "no Glassblower" fit wherever there's room
		int itemW = Math.max(40, headWidth("item") + 6);
		int needW = Math.max(40, headWidth("needed_by") + 6);
		int byW = Math.max(40, headWidth("brought_by") + 6);
		for (int slot : requestSlots()) {
			ItemStack icon = stack(slot);
			CompoundTag row = BoardRows.row(icon);
			List<Component> lore = BoardRows.lore(icon);
			itemW = Math.max(itemW, font.width(BoardRows.what(icon.getHoverName())) + 8);
			for (Component c : BoardRows.neededBy(row, lore)) {
				needW = Math.max(needW, font.width(c) + 6);
			}
			BoardRows.BroughtBy by = BoardRows.broughtBy(row, lore);
			byW = Math.max(byW, Math.max(font.width(by.who()) + (by.nobody() ? 10 : 0), font.width(by.how())) + 6);
		}
		if (compactScreen()) {
			byW = Math.min(byW, room * 50 / 100);
			return new int[]{x + 4, nameX, -1, ax - 4 - byW, ax};
		}
		needW = Math.min(needW, room * 35 / 100);
		byW = Math.min(byW, room * 40 / 100);
		int left = room - needW - byW;
		int itemMin = Math.min(itemW, room * 30 / 100);
		if (left < itemMin) { // too narrow for all three: the other two give up the item column's least share evenly
			int short1 = (itemMin - left + 1) / 2;
			needW = Math.max(40, needW - short1);
			byW = Math.max(40, byW - short1);
			left = room - needW - byW;
		}
		if (left > itemW) { // room to spare: a third to the item, the rest to the other two
			int extra = left - itemW;
			needW += extra / 3;
			byW += extra / 3;
		}
		int byX = ax - 4 - byW;
		return new int[]{x + 4, nameX, byX - needW, byX, ax};
	}

	/** A column heading's width, as drawn (in capitals). */
	private int headWidth(String key) {
		return font.width(Component.translatable("screen.aliveworkplace.storehouse.col." + key).getString().toUpperCase(Locale.ROOT));
	}

	private int rowTop(int i) {
		return tableTop() + 2 + i * RH - tableScroll;
	}

	private int maxScroll() {
		return Math.max(0, visibleRows().size() * RH + 4 - tableH());
	}

	private Component actionLabel(CompoundTag row) {
		return switch (BoardRows.state(row)) {
			case GIVE -> Component.translatable("screen.aliveworkplace.storehouse.btn.give", BoardRows.give(row));
			case STOCKED -> Component.translatable("screen.aliveworkplace.storehouse.btn.stocked");
			default -> Component.translatable("screen.aliveworkplace.storehouse.btn.none");
		};
	}

	/** The footer's Give all button (null: nobody's asking for anything you carry). */
	private int[] giveAllBox() {
		int[] b = body();
		Component label = giveAllLabel();
		int w = font.width(label) + 16;
		return new int[]{b[0] + b[2] - w, b[1] + b[3] - FOOT + 5, w, 16};
	}

	private Component giveAllLabel() {
		// The server counts what a press hands over (rows asking for the same item share what's carried).
		net.minecraft.world.item.component.CustomData data = stack(StorehouseBoard.GIVE_ALL_SLOT).get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data != null && data.contains(StorehouseBoard.GIVE_ALL_COUNT)) {
			return Component.translatable("screen.aliveworkplace.storehouse.give_all", data.copyTag().getInt(StorehouseBoard.GIVE_ALL_COUNT));
		}
		int total = 0;
		for (int slot : requestSlots()) {
			total += BoardRows.give(BoardRows.row(stack(slot)));
		}
		return Component.translatable("screen.aliveworkplace.storehouse.give_all", total);
	}

	private boolean canGiveAll() {
		return shown(stack(StorehouseBoard.GIVE_ALL_SLOT));
	}

	@Override
	protected void renderBody(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		bodyWhisper = null;
		ItemStack tooltip = null;
		List<Integer> all = requestSlots();
		if (all.isEmpty()) {
			Component line = noticeLine();
			CopperStyle.empty(g, font, null, Component.translatable("screen.aliveworkplace.storehouse.empty"),
				line == null ? Component.translatable("screen.aliveworkplace.storehouse.nothing") : line, null, x, y + 4, w, h - 8, false);
			finish(g, mouseX, mouseY);
			return;
		}
		int[] c = columns();
		boolean compact = compactScreen();
		// the column headings over a rule
		String[] heads = {"item", "needed_by", "brought_by"};
		int[] headX = {c[1], c[2], c[3]};
		int[] headEnd = {compact ? c[3] : c[2], c[3], c[4]};
		for (int i = 0; i < 3; i++) {
			if (headX[i] < 0) {
				continue;
			}
			String text = Component.translatable("screen.aliveworkplace.storehouse.col." + heads[i]).getString().toUpperCase(Locale.ROOT);
			g.drawString(font, CopperStyle.trim(font, text, headEnd[i] - headX[i] - 4), headX[i], y + 1, CopperStyle.TUFF, false);
		}
		CopperStyle.headerRow(g, x, y - 1, w);
		int top0 = tableTop();
		int th = tableH();
		TableStyle.listBackground(g, x, top0, w, th);
		List<Integer> rows = visibleRows();
		tableScroll = Math.max(0, Math.min(maxScroll(), tableScroll));
		if (rows.isEmpty()) {
			CopperStyle.empty(g, font, null, Component.translatable("screen.aliveworkplace.storehouse.filtered"),
				Component.translatable("screen.aliveworkplace.storehouse.filtered_hint"), null, x + 4, top0 + 4, w - 8, th - 8, false);
		}
		g.enableScissor(x, top0, x + w, top0 + th);
		boolean inTable = mouseY >= top0 && mouseY < top0 + th;
		for (int i = 0; i < rows.size(); i++) {
			int t = rowTop(i);
			if (t + RH < top0 || t > top0 + th) {
				continue;
			}
			int slot = rows.get(i);
			ItemStack icon = stack(slot);
			CompoundTag row = BoardRows.row(icon);
			List<Component> lore = BoardRows.lore(icon);
			boolean over = inTable && mouseX >= x + 1 && mouseX < x + w - 7 && mouseY >= t && mouseY < t + RH - 1;
			CopperStyle.rowBand(g, x + 1, t, w - 8, RH - 1, i % 2 == 1, over, false);
			// Item: the icon in its slot, the name, the bar and how many the store holds of how many are asked for
			CopperStyle.slot(g, font, icon.copyWithCount(1), c[0], t + 1, false);
			int nameEnd = (compact ? c[3] : c[2]) - 4;
			g.drawString(font, CopperStyle.trim(font, BoardRows.what(icon.getHoverName()).getString(), nameEnd - c[1]), c[1], t + 2, CopperStyle.QUARTZ, false);
			int need = row.getInt("need");
			int store = row.getInt("store");
			BoardRows.Tone tone = BoardRows.tone(store, need);
			int color = switch (tone) {
				case EMPTY -> CopperStyle.REDSTONE;
				case LOW -> CopperStyle.LANTERN;
				case FULL -> CopperStyle.VERDIGRIS;
				case PART -> CopperStyle.QUARTZ;
				default -> CopperStyle.TUFF;
			};
			String counted = store < 0 ? "×" + need : Math.min(store, need) + "/" + need; // more than asked: "64/64", Stocked
			int barW = Math.min(28, Math.max(0, nameEnd - c[1] - font.width(counted) - 4));
			if (store >= 0 && barW >= 10) {
				int barColor = tone == BoardRows.Tone.FULL ? CopperStyle.VERDIGRIS : tone == BoardRows.Tone.LOW ? CopperStyle.LANTERN : CopperStyle.COPPER;
				CopperStyle.progress(g, c[1], t + 13, barW, 5, need <= 0 ? 1f : (float) store / need, barColor);
			}
			int countX = store >= 0 && barW >= 10 ? c[1] + barW + 4 : c[1];
			g.drawString(font, CopperStyle.trim(font, counted, nameEnd - countX), countX, t + 12, color, false);
			if (over && mouseX >= c[0] && mouseX < c[0] + 18) {
				tooltip = icon; // every line of the row, as the board's pane read them out
			}
			// Needed by: the asker's name and job, or their job and how far
			if (c[2] >= 0) {
				Component[] needed = BoardRows.neededBy(row, lore);
				int cw = c[3] - c[2] - 4;
				g.drawString(font, CopperStyle.trim(font, needed[0].getString(), cw), c[2], t + 2, CopperStyle.QUARTZ, false);
				g.drawString(font, CopperStyle.trim(font, needed[1].getString(), cw), c[2], t + 12, CopperStyle.TUFF, false);
			}
			// Brought by: who, what they do; nobody: the warning and the job to hire
			BoardRows.BroughtBy by = BoardRows.broughtBy(row, lore);
			int bw = c[4] - c[3] - 4;
			if (by.nobody()) {
				CopperStyle.glyph(g, "warning", c[3], t + 2);
				g.drawString(font, CopperStyle.trim(font, by.who().getString(), bw - 10), c[3] + 10, t + 2, CopperStyle.REDSTONE, false);
			} else {
				g.drawString(font, CopperStyle.trim(font, by.who().getString(), bw), c[3], t + 2, CopperStyle.QUARTZ, false);
			}
			g.drawString(font, CopperStyle.trim(font, by.how().getString(), bw), c[3], t + 12, CopperStyle.TUFF, false);
			// the button: Give N, Stocked, or Have 0
			int aw = actionW();
			BoardRows.State state = BoardRows.state(row);
			if (state == BoardRows.State.STOCKED) {
				String word = CopperStyle.trim(font, actionLabel(row).getString(), aw - 10);
				int wx = c[4] + aw - font.width(word);
				CopperStyle.glyph(g, "good", wx - 10, t + 6);
				g.drawString(font, word, wx, t + 6, CopperStyle.VERDIGRIS, false);
			} else {
				CopperStyle.button(g, font, actionLabel(row), null, c[4], t + 3, aw, 14, CopperStyle.Kind.NEUTRAL, over, false,
					state == BoardRows.State.NONE);
			}
			if (over) {
				bodyWhisper = rowWhisper(row, lore, icon);
			}
		}
		g.disableScissor();
		CopperStyle.scrollbar(g, x + w - 5, top0 + 1, th - 2, tableScroll, maxScroll(), rows.size() * RH + 4, false);
		// the footer: what you carry that's asked for, and Give all
		int fy = y + h - FOOT + 5;
		int[] all2 = giveAllBox();
		boolean overAll = CopperLayout.inside(all2, mouseX, mouseY);
		CopperStyle.button(g, font, giveAllLabel(), null, all2[0], all2[1], all2[2], all2[3], CopperStyle.Kind.COPPER, overAll, false, !canGiveAll());
		if (overAll) {
			bodyWhisper = Component.translatable("screen.aliveworkplace.storehouse.give_all_hint");
		}
		String pack = Component.translatable("screen.aliveworkplace.storehouse.pack").getString();
		int px = x + 2;
		if (!compact) {
			g.drawString(font, pack, px, fy + 4, CopperStyle.TUFF, false);
			px += font.width(pack) + 6;
		}
		for (int slot : all) {
			CompoundTag row = BoardRows.row(stack(slot));
			int give = BoardRows.give(row);
			if (give <= 0 || px + 18 > all2[0] - 4) {
				continue;
			}
			ItemStack chip = stack(slot).copyWithCount(Math.min(99, give));
			CopperStyle.slot(g, font, chip, px, fy - 1, false);
			if (mouseX >= px && mouseX < px + 18 && mouseY >= fy - 1 && mouseY < fy + 17) {
				tooltip = stack(slot);
			}
			px += 20;
		}
		if (tooltip != null) {
			pendingTooltip = tooltip;
		}
		finish(g, mouseX, mouseY);
	}

	/** The row's whisper: what a click does, then who brings it ("Click to give 16 of yours · Gathered by: the Lumberjack (Bo)"). */
	private Component rowWhisper(CompoundTag row, List<Component> lore, ItemStack icon) {
		Component action = null;
		for (Component line : lore) {
			if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& (t.getKey().equals("screen.aliveworkplace.storehouse.give") || t.getKey().equals("screen.aliveworkplace.storehouse.you_have_none"))) {
				action = line;
			}
		}
		if (BoardRows.state(row) == BoardRows.State.STOCKED) {
			action = Component.translatable("screen.aliveworkplace.storehouse.stocked_whisper", BoardRows.what(icon.getHoverName()));
		}
		int by = row.getInt("by");
		Component who = by >= 0 && by < lore.size() ? lore.get(by) : lore.isEmpty() ? Component.empty() : lore.get(0);
		if (compactScreen() && !lore.isEmpty()) {
			who = lore.get(0); // no Needed by column here: who asked
		}
		if (action == null) {
			return who;
		}
		return Component.translatable("screen.aliveworkplace.storehouse.whisper", Component.literal(action.getString()), Component.literal(who.getString()));
	}

	/** The store's first line ("Storehouse: 3 chests, 120 items, 40 empty slots"), the whisper with nothing under the mouse. */
	private Component noticeLine() {
		List<Component> lore = BoardRows.lore(stack(StorehouseBoard.INFO_SLOT));
		return lore.isEmpty() ? null : Component.literal(lore.get(0).getString());
	}

	private void finish(GuiGraphics g, int mouseX, int mouseY) {
		if (pendingTooltip != null) {
			g.renderTooltip(font, pendingTooltip, mouseX, mouseY);
			pendingTooltip = null;
		}
	}

	@Override
	protected Component whisperText(int mouseX, int mouseY, int hovered) {
		if (!board()) {
			return super.whisperText(mouseX, mouseY, hovered);
		}
		if (railWhisper != null) {
			return railWhisper;
		}
		if (bodyWhisper != null) {
			return bodyWhisper;
		}
		Component notice = noticeLine();
		return notice != null ? notice : super.whisperText(mouseX, mouseY, hovered);
	}

	/** The request row under the mouse (-1: none). */
	private int rowAt(double mx, double my) {
		int[] b = body();
		int top0 = tableTop();
		if (mx < b[0] + 1 || mx >= b[0] + b[2] - 7 || my < top0 || my >= top0 + tableH()) {
			return -1;
		}
		List<Integer> rows = visibleRows();
		for (int i = 0; i < rows.size(); i++) {
			int t = rowTop(i);
			if (my >= t && my < t + RH - 1) {
				return rows.get(i);
			}
		}
		return -1;
	}

	@Override
	protected boolean bodyClicked(double mx, double my, int button) {
		if (button == 0 && canGiveAll() && CopperLayout.inside(giveAllBox(), mx, my)) {
			press(StorehouseBoard.GIVE_ALL_SLOT, 0, false);
			click();
			return true;
		}
		int slot = rowAt(mx, my);
		if (slot < 0 || button > 1) {
			return false;
		}
		if (button == 0 && hasShiftDown() && canGiveAll()) {
			press(StorehouseBoard.GIVE_ALL_SLOT, 0, false); // Shift-click: give all
		} else {
			press(slot, button, false);
		}
		click();
		return true;
	}

	@Override
	protected boolean bodyScrolled(double mx, double my, double scrollY) {
		tableScroll = Math.max(0, Math.min(maxScroll(), tableScroll - (int) Math.round(scrollY * RH)));
		return true;
	}

	// --- for the screenshot harness -------------------------------------------------------------------------------------

	/**
	 * Where button {@code slot} is: a request's row (scrolled into view; its Needed by cell, so the whisper reads it out
	 * and no tooltip covers it), the store's rail entries, or Give all.
	 */
	@Override
	public int[] centre(int slot) {
		if (!board()) {
			return super.centre(slot);
		}
		if (slot == StorehouseBoard.GIVE_ALL_SLOT) {
			int[] box = giveAllBox();
			return new int[]{box[0] + box[2] / 2, box[1] + box[3] / 2};
		}
		for (Entry e : railEntries()) {
			if (e.kind() == 2 && e.value() == slot) {
				return new int[]{e.box()[0] + e.box()[2] / 2, e.box()[1] + e.box()[3] / 2};
			}
		}
		int i = visibleRows().indexOf(slot);
		if (i >= 0) {
			int t = rowTop(i);
			if (t < tableTop() || t + RH > tableTop() + tableH()) {
				tableScroll = Math.max(0, Math.min(maxScroll(), i * RH - (tableH() - RH) / 2));
				t = rowTop(i);
			}
			int[] c = columns();
			int x = c[2] >= 0 ? c[2] + 10 : c[3] + 10;
			return new int[]{x, t + RH / 2};
		}
		int[] b = body();
		return new int[]{b[0] + b[2] / 2, b[1] + b[3] + 4};
	}

	/** Where request {@code slot}'s icon is (pointing there shows every line of the row in its tooltip). */
	public int[] iconCentre(int slot) {
		int[] at = centre(slot);
		int[] c = columns();
		return new int[]{c[0] + 9, at[1]};
	}

	/** The rows the filters show now, in order (for the screenshot harness). */
	public List<Integer> shownRows() {
		return visibleRows();
	}

	/** Picks the rail's group filter as a click there would (the screenshot harness). */
	public void filter(BoardRows.Group group) {
		filter = group;
		tableScroll = 0;
	}
}
