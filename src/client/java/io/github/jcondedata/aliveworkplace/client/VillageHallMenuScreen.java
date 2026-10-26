package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Village Hall's own screen (ROADMAP 30.4a), drawn in the Blueprint Table's style since 26.12 ({@link ChoiceTableScreen}),
 * with a sidebar since 40.1: the village's name and today's date over ten labeled buttons, one per section
 * ({@link HallSections}). The open section is highlighted and fills the right side: its buttons from the hall's main page
 * as labeled rows, a plain sentence on top when it's empty, and the pane reading out what is under the mouse (with
 * nothing there, the advice tips on the Overview, else what the section is for). A page opened from a section fills the
 * right side as before, the sidebar staying; a sidebar button goes back to a section's list from anywhere. The clicks
 * are the server's {@code ChoiceMenu} ones; a sidebar click is the menu's button click with the section's number.
 */
public class VillageHallMenuScreen extends ChoiceTableScreen {
	private static final int SECTIONS = Section.values().length;

	public VillageHallMenuScreen(ChoiceView menu, Inventory inventory, Component title) {
		super(menu, inventory, title, new int[]{0, 1}, 2, 2);
	}

	/** Whether the server set the hall's sidebar up (the main page was laid out; not the Book of Edicts opened alone). */
	private boolean hasSidebar() {
		return menu.state(HallSections.YEAR) > 0;
	}

	/** The section clicked here that the server hasn't confirmed yet (-1: none), shown at once so a click feels instant. */
	private int pending = -1;

	private Section section() {
		int server = menu.state(HallSections.SECTION);
		if (pending >= 0 && pending != server) {
			return Section.of(pending);
		}
		pending = -1;
		return Section.of(server);
	}

	/** Opens {@code section}: shown here at once, and the sidebar's click sent to the server. */
	private void open(Section section) {
		pending = section.ordinal();
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, section.ordinal());
		}
	}

	/** Whether the hall's main page is showing (its buttons carry their sections), not one of its pages. */
	private boolean onMain() {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			if (HallSections.marks(menu.slots.get(slot).getItem()) != 0) {
				return true;
			}
		}
		return false;
	}

	// --- the sidebar's layout ----------------------------------------------------------------------------------------

	private int iconRoom() {
		return panelBox()[3] >= 230 ? 16 : 10;
	}

	@Override
	protected int sidebarWidth() {
		if (!hasSidebar()) {
			return 0;
		}
		int text = 0;
		for (Section s : Section.values()) {
			text = Math.max(text, font.width(s.title()));
		}
		return Math.min(8 + iconRoom() + 4 + text + 8, panelBox()[2] / 3);
	}

	private Component villageName() {
		if (title.getContents() instanceof TranslatableContents t && t.getArgs().length > 0) {
			Object name = t.getArgs()[0];
			return name instanceof Component c ? c : Component.literal(String.valueOf(name));
		}
		return title;
	}

	private Component date() {
		return HallSections.date(menu.state(HallSections.SEASON), menu.state(HallSections.DAY), menu.state(HallSections.YEAR));
	}

	/** The date's lines in the sidebar (it wraps when the sidebar is narrow), at most two. */
	private List<FormattedCharSequence> dateLines(int side) {
		List<FormattedCharSequence> lines = font.split(date(), side - 12);
		return lines.size() > 2 ? lines.subList(0, 2) : lines;
	}

	/** The sidebar buttons' top, and each one's height. */
	private int[] buttons(int side) {
		int[] box = panelBox();
		int first = box[1] + 7 + 10 + dateLines(side).size() * 10 + 4;
		int bottom = box[1] + box[3] - 16;
		int rowH = Math.max(10, Math.min(20, (bottom - first) / SECTIONS));
		return new int[]{first, rowH};
	}

	private int sectionAt(double mx, double my) {
		int side = sidebarWidth();
		if (side == 0) {
			return -1;
		}
		int[] box = panelBox();
		int[] b = buttons(side);
		if (mx < box[0] + 4 || mx >= box[0] + side - 2 || my < b[0]) {
			return -1;
		}
		int i = (int) ((my - b[0]) / b[1]);
		return i < SECTIONS ? i : -1;
	}

	@Override
	protected void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
		int side = sidebarWidth();
		if (side == 0) {
			return;
		}
		int[] box = panelBox();
		int x = box[0];
		int y = box[1];
		g.fill(x + 4, y + 4, x + side - 2, y + box[3] - 16, TableStyle.LIST_BG);
		g.drawString(font, TableStyle.trim(font, villageName(), side - 12), x + 8, y + 7, TableStyle.TITLE, false);
		int dy = y + 17;
		for (FormattedCharSequence line : dateLines(side)) {
			g.drawString(font, line, x + 8, dy, TableStyle.SUBTITLE, false);
			dy += 10;
		}
		int[] b = buttons(side);
		int rowH = b[1];
		int icon = Math.min(16, rowH - 2);
		int hovered = sectionAt(mouseX, mouseY);
		Section open = section();
		for (Section s : Section.values()) {
			int top = b[0] + s.ordinal() * rowH;
			boolean on = s == open;
			if (on) {
				g.fill(x + 4, top, x + side - 2, top + rowH - 1, TableStyle.CELL_ON_BG);
				g.fill(x + 4, top, x + 6, top + rowH - 1, TableStyle.TITLE);
			} else if (s.ordinal() == hovered) {
				g.fill(x + 4, top, x + side - 2, top + rowH - 1, 0x30FFFFFF);
			}
			int ix = x + 8;
			int iy = top + (rowH - 1 - icon) / 2;
			g.pose().pushPose();
			g.pose().translate(ix, iy, 0);
			g.pose().scale(icon / 16f, icon / 16f, 1f);
			g.renderItem(new ItemStack(s.icon), 0, 0);
			g.pose().popPose();
			int tx = ix + icon + 4;
			g.drawString(font, TableStyle.trim(font, s.title(), x + side - 4 - tx), tx, top + (rowH - 8) / 2, on ? TableStyle.TITLE : TableStyle.NAME, false);
		}
	}

	@Override
	protected boolean sidebarClicked(double mx, double my, int button) {
		int i = sectionAt(mx, my);
		if (i < 0 || button != 0) {
			return false;
		}
		open(Section.of(i));
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		}
		return true;
	}

	/** Clicks sidebar button {@code section} as a player would (the screenshot harness). */
	public void choose(Section section) {
		open(section);
	}

	/**
	 * Where button {@code slot} is (the screenshot harness points at it): a main-page button outside the open section
	 * opens the first section it's in, as a player would click there first.
	 */
	@Override
	public int[] centre(int slot) {
		if (hasSidebar() && onMain() && slot >= 0 && slot < ChoiceMenu.SIZE) {
			int marks = HallSections.marks(menu.slots.get(slot).getItem());
			if (marks != 0 && (marks & section().bit()) == 0) {
				open(Section.of(Integer.numberOfTrailingZeros(marks)));
			}
		}
		return super.centre(slot);
	}

	// --- the open section ------------------------------------------------------------------------------------------

	@Override
	protected Component headerTitle() {
		return hasSidebar() ? section().title() : title;
	}

	@Override
	protected boolean showSlot(int slot) {
		if (!hasSidebar() || !onMain()) {
			return true;
		}
		return (HallSections.marks(menu.slots.get(slot).getItem()) & section().bit()) != 0;
	}

	@Override
	protected boolean rowsOnly() {
		return hasSidebar() && onMain();
	}

	@Override
	protected Component listNote() {
		if (!hasSidebar() || !onMain()) {
			return null;
		}
		Section s = section();
		return (menu.state(HallSections.EMPTY) & s.bit()) != 0 ? s.empty() : null;
	}

	@Override
	protected boolean renderIdle(GuiGraphics g, int x, int y, int w, int h) {
		if (!hasSidebar() || !onMain()) {
			return false;
		}
		Section s = section();
		g.enableScissor(x, y, x + w, y + h);
		if (s == Section.OVERVIEW) {
			// "What next?": every tip and how, from the advice button's lines (its first line is the count).
			TableStyle.heading(g, font, Component.translatable("screen.aliveworkplace.hall.advice"), x, y);
			int ly = y + 13;
			ItemStack advice = menu.slots.get(VillageHallScreen.ADVICE).getItem();
			ItemLore lore = advice.get(DataComponents.LORE);
			List<Component> lines = lore == null ? List.of() : lore.lines();
			if (lines.size() <= 1) {
				Component none = lines.isEmpty() ? Component.translatable("screen.aliveworkplace.hall.advice_none") : lines.get(0);
				g.drawWordWrap(font, Component.literal(none.getString()), x, ly, w - 4, TableStyle.BODY);
			} else {
				for (int i = 1; i < lines.size(); i++) {
					boolean tipTitle = i % 2 == 1;
					for (FormattedCharSequence part : font.split(Component.literal(lines.get(i).getString()), w - 4)) {
						g.drawString(font, part, x, ly, tipTitle ? TableStyle.NAME : TableStyle.SUBLINE, false);
						ly += 10;
					}
					if (!tipTitle) {
						ly += 4;
					}
				}
			}
		} else {
			TableStyle.heading(g, font, s.title(), x, y);
			g.drawWordWrap(font, s.about(), x, y + 13, w - 4, TableStyle.BODY);
			int ly = y + 13 + font.split(s.about(), w - 4).size() * 10 + 6;
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.choice.help"), x, ly, w - 4, TableStyle.FAINT);
		}
		g.disableScissor();
		return true;
	}

	// --- the People table (40.2) ------------------------------------------------------------------------------------

	/** A table row's height, and a details button's. */
	private static final int RH = 12;
	private static final int BH = 14;
	private static final int MOOD_HAPPY = 0xFF55FF55;
	private static final int MOOD_UNHAPPY = 0xFFFFAA00;
	private static final int MOOD_NO_BED = 0xFFFF5555;

	/** The open row's villager (null: none), kept by who they are so it stays open as the list moves. */
	private String openUuid;
	private int tableScroll;
	/** The window start last asked of the server (-1: none). */
	private int askedFirst = -1;
	/** What the last frame drew, for clicks: [kind, slot, x, y, w, h] (kind 0 row, 1 strip button, 2 job, 3 bed, 4 find). */
	private final List<int[]> hits = new ArrayList<>();
	private int tableTop, tableH, tableX, tableW;

	@Override
	protected boolean customBody() {
		return hasSidebar() && onMain() && section() == Section.PEOPLE;
	}

	/** The People section's other buttons (the figures, the recall bell, the page tabs): a strip over the table. */
	private List<Integer> stripSlots() {
		List<Integer> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.slots.get(slot).getItem();
			if ((HallSections.marks(icon) & Section.PEOPLE.bit()) != 0 && HallPeople.row(icon) == null && slot != VillageHallScreen.PREVIOUS && slot != VillageHallScreen.NEXT && ChoiceTableScreen.shown(icon)) {
				out.add(slot);
			}
		}
		return out;
	}

	/** The people loaded now (the server's window), in order. */
	private List<Integer> personSlots() {
		List<Integer> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_PERSON; slot < ChoiceMenu.SIZE; slot++) {
			if (HallPeople.row(menu.slots.get(slot).getItem()) != null) {
				out.add(slot);
			}
		}
		return out;
	}

	private int total() {
		return Math.max(menu.state(HallSections.PEOPLE_TOTAL), personSlots().size());
	}

	/** The open row's details: the lines today's resident pane shows, wrapped. */
	private List<FormattedCharSequence> detailLines(ItemStack icon, int w) {
		List<FormattedCharSequence> out = new ArrayList<>();
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				out.addAll(font.split(line, w));
			}
		}
		return out;
	}

	private int detailHeight(ItemStack icon, int w) {
		return 2 + detailLines(icon, w).size() * 10 + 3 + BH + 4 + jobListHeight(icon, w) + bedListHeight(icon, w);
	}

	// --- Bed ▾'s list (40.4) ------------------------------------------------------------------------------------------

	private static HallBeds.State bedStateOf(ItemStack line) {
		CompoundTag mark = HallBeds.mark(line);
		return mark == null ? HallBeds.State.NOTE : HallBeds.State.of(mark.getString("state"));
	}

	private int bedListHeight(ItemStack icon, int w) {
		List<ItemStack> lines = HallBeds.lines(icon);
		if (lines.isEmpty()) {
			return 0;
		}
		int h = 4;
		for (ItemStack line : lines) {
			if (bedStateOf(line) == HallBeds.State.HEADER) {
				h += 11;
				for (Component c : loreOf(line)) {
					h += font.split(c, w - 8).size() * 10;
				}
				h += 3;
			} else {
				h += JH;
			}
		}
		return h + 4;
	}

	/** Draws the bed list from {@code y}; returns a tooltip for the line under the mouse (null: none). */
	private List<Component> renderBedList(GuiGraphics g, ItemStack icon, int x, int y, int w, int mouseX, int mouseY) {
		List<ItemStack> lines = HallBeds.lines(icon);
		if (lines.isEmpty()) {
			return null;
		}
		List<Component> tip = null;
		g.fill(x - 2, y, x + w, y + bedListHeight(icon, w) - 2, 0x70000000);
		int ly = y + 4;
		for (ItemStack line : lines) {
			HallBeds.State state = bedStateOf(line);
			if (state == HallBeds.State.HEADER) {
				g.drawString(font, TableStyle.trim(font, line.getHoverName(), w - 8), x + 2, ly, TableStyle.TITLE, false);
				ly += 11;
				for (Component c : loreOf(line)) {
					for (FormattedCharSequence part : font.split(c, w - 8)) {
						g.drawString(font, part, x + 2, ly, TableStyle.BODY, false);
						ly += 10;
					}
				}
				ly += 3;
				continue;
			}
			boolean over = mouseX >= x && mouseX < x + w - 2 && mouseY >= ly && mouseY < ly + JH && mouseY >= tableTop && mouseY < tableTop + tableH;
			if (over && state.pickable()) {
				g.fill(x, ly - 1, x + w - 2, ly + JH - 1, 0x30FFFFFF);
			}
			g.pose().pushPose();
			g.pose().translate(x + 2, ly, 0);
			g.pose().scale(10 / 16f, 10 / 16f, 1f);
			g.renderItem(line, 0, 0);
			g.pose().popPose();
			int colour = state == HallBeds.State.CURRENT ? TableStyle.TITLE : state.pickable() ? TableStyle.NAME : TableStyle.FAINT;
			g.drawString(font, TableStyle.trim(font, line.getHoverName(), w - 20), x + 16, ly + 1, colour, false);
			CompoundTag mark = HallBeds.mark(line);
			if (state == HallBeds.State.PLAN) {
				hits.add(new int[]{7, 0, x, ly - 1, w - 2, JH});
			} else if (state.pickable() && mark != null) {
				hits.add(new int[]{6, mark.getInt("id"), x, ly - 1, w - 2, JH});
			}
			if (over && !loreOf(line).isEmpty()) {
				tip = loreOf(line);
			}
			ly += JH;
		}
		return tip;
	}

	// --- Job ▾'s list (40.3) ------------------------------------------------------------------------------------------

	/** A line of the job list's height. */
	private static final int JH = 12;

	/** The job list's lines on an open row's button (empty: it isn't open). */
	private static List<ItemStack> jobLines(ItemStack icon) {
		CompoundTag row = HallPeople.row(icon);
		return row != null && row.getBoolean("jobs_open") ? HallJobs.lines(icon) : List.of();
	}

	private static HallJobs.State stateOf(ItemStack line) {
		CompoundTag mark = HallJobs.mark(line);
		return mark == null ? HallJobs.State.NO_BLOCK : HallJobs.State.of(mark.getString("state"));
	}

	private List<Component> loreOf(ItemStack line) {
		ItemLore lore = line.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	/** The last line of a picked job's tooltip ("Click again to pay ..."), drawn under it. */
	private List<FormattedCharSequence> pickedLines(ItemStack line, int w) {
		List<Component> lore = loreOf(line);
		return lore.isEmpty() ? List.of() : font.split(lore.get(lore.size() - 1), w);
	}

	private int jobListHeight(ItemStack icon, int w) {
		List<ItemStack> lines = jobLines(icon);
		if (lines.isEmpty()) {
			return 0;
		}
		int h = 4;
		for (ItemStack line : lines) {
			HallJobs.State state = stateOf(line);
			if (state == HallJobs.State.HEADER) {
				h += 11;
				for (Component c : loreOf(line)) {
					h += font.split(c, w - 8).size() * 10;
				}
				h += 3;
			} else {
				h += JH;
				if (state == HallJobs.State.PICKED || state == HallJobs.State.NO_JOB_PICKED) {
					h += pickedLines(line, w - 24).size() * 10 + 1;
				}
			}
		}
		return h + 4;
	}

	/** Draws the job list from {@code y}; returns a tooltip for the line under the mouse (null: none). */
	private List<Component> renderJobList(GuiGraphics g, ItemStack icon, int x, int y, int w, int mouseX, int mouseY) {
		List<ItemStack> lines = jobLines(icon);
		if (lines.isEmpty()) {
			return null;
		}
		List<Component> tip = null;
		g.fill(x - 2, y, x + w, y + jobListHeight(icon, w) - 2, 0x70000000);
		int ly = y + 4;
		for (ItemStack line : lines) {
			HallJobs.State state = stateOf(line);
			if (state == HallJobs.State.HEADER) {
				g.drawString(font, TableStyle.trim(font, line.getHoverName(), w - 8), x + 2, ly, TableStyle.TITLE, false);
				ly += 11;
				for (Component c : loreOf(line)) {
					for (FormattedCharSequence part : font.split(c, w - 8)) {
						g.drawString(font, part, x + 2, ly, TableStyle.BODY, false);
						ly += 10;
					}
				}
				ly += 3;
				continue;
			}
			boolean picked = state == HallJobs.State.PICKED || state == HallJobs.State.NO_JOB_PICKED;
			List<FormattedCharSequence> under = picked ? pickedLines(line, w - 24) : List.of();
			int lineH = JH + (picked ? under.size() * 10 + 1 : 0);
			boolean over = mouseX >= x && mouseX < x + w - 2 && mouseY >= ly && mouseY < ly + lineH && mouseY >= tableTop && mouseY < tableTop + tableH;
			if (picked) {
				g.fill(x, ly - 1, x + w - 2, ly + lineH - 1, 0x50FFAA00);
			} else if (over && state.pickable()) {
				g.fill(x, ly - 1, x + w - 2, ly + lineH - 1, 0x30FFFFFF);
			}
			g.pose().pushPose();
			g.pose().translate(x + 2, ly, 0);
			g.pose().scale(10 / 16f, 10 / 16f, 1f);
			g.renderItem(line, 0, 0);
			g.pose().popPose();
			int colour = picked ? TableStyle.TITLE : state.pickable() ? TableStyle.NAME : TableStyle.FAINT;
			g.drawString(font, TableStyle.trim(font, line.getHoverName(), w - 20), x + 16, ly + 1, colour, false);
			int uy = ly + JH;
			for (FormattedCharSequence part : under) {
				g.drawString(font, part, x + 16, uy, 0xFF55FF55, false);
				uy += 10;
			}
			CompoundTag mark = HallJobs.mark(line);
			if (state != HallJobs.State.CURRENT && mark != null) {
				hits.add(new int[]{5, mark.getInt("id"), x, ly - 1, w - 2, lineH});
			}
			if (over && !loreOf(line).isEmpty()) {
				tip = loreOf(line);
			}
			ly += lineH;
		}
		return tip;
	}

	/** Whether a row loaded now has its job list open, other than {@code uuid}'s. */
	private boolean jobListOpenElsewhere(String uuid) {
		for (int slot : personSlots()) {
			CompoundTag row = HallPeople.row(menu.slots.get(slot).getItem());
			if (row != null && row.getBoolean("jobs_open") && !row.getString("uuid").equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a row loaded now has its bed list open, other than {@code uuid}'s. */
	private boolean bedListOpenElsewhere(String uuid) {
		for (int slot : personSlots()) {
			CompoundTag row = HallPeople.row(menu.slots.get(slot).getItem());
			if (row != null && row.getBoolean("beds_open") && !row.getString("uuid").equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	private void menuButton(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	/** Asks the server for the window of people that the table's scroll shows, when it isn't loaded. */
	private void keepWindow(int visibleRows) {
		int total = total();
		int loaded = personSlots().size();
		int first = menu.state(HallSections.PEOPLE_FIRST);
		if (total <= loaded || loaded == 0) {
			return;
		}
		int a = Math.max(0, tableScroll / RH - 1);
		int b = Math.min(total, a + visibleRows + 2);
		if (a >= first && b <= first + loaded) {
			askedFirst = -1;
			return;
		}
		int want = Math.max(0, Math.min(total - VillageHallScreen.PER_PAGE, a - 4));
		if (want != askedFirst && minecraft != null && minecraft.gameMode != null) {
			askedFirst = want;
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, HallPeople.SCROLL + want);
		}
	}

	@Override
	protected void renderBody(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		hits.clear();
		ItemStack tooltip = null;
		Component tip = null;
		List<Component> jobTips = null;
		// The strip of the section's other buttons.
		int sx = x;
		for (int slot : stripSlots()) {
			boolean over = mouseX >= sx && mouseX < sx + 18 && mouseY >= y && mouseY < y + 18;
			TableStyle.cell(g, sx, y, false, over);
			g.renderItem(menu.slots.get(slot).getItem(), sx + 1, y + 1);
			hits.add(new int[]{1, slot, sx, y, 18, 18});
			if (over) {
				tooltip = menu.slots.get(slot).getItem();
			}
			sx += 20;
		}
		int ly = y + 22;
		Component note = listNote();
		if (note != null) {
			g.drawWordWrap(font, note, x, ly, w, TableStyle.BODY);
			ly += font.split(note, w).size() * 10 + 4;
		}
		g.drawString(font, TableStyle.trim(font, HallPeople.header(menu.state(HallSections.PEOPLE_TOTAL), menu.state(HallSections.PEOPLE_WORKING),
			menu.state(HallSections.PEOPLE_JOBLESS), menu.state(HallSections.PEOPLE_CHILDREN)), w), x, ly, TableStyle.TITLE, false);
		ly += 11;
		g.drawString(font, TableStyle.trim(font, Component.translatable("screen.aliveworkplace.hall.people.hint"), w), x, ly, TableStyle.FAINT, false);
		ly += 12;
		int[] cols = {x + 4, x + 4 + w * 36 / 100, x + 4 + w * 64 / 100, x + 4 + w * 82 / 100};
		int[] colW = {cols[1] - cols[0] - 4, cols[2] - cols[1] - 4, cols[3] - cols[2] - 4, x + w - 8 - cols[3]};
		String[] heads = {"name", "job", "level", "mood"};
		for (int c = 0; c < 4; c++) {
			g.drawString(font, TableStyle.trim(font, Component.translatable("screen.aliveworkplace.hall.people.col." + heads[c]), colW[c]), cols[c], ly,
				TableStyle.SUBTITLE, false);
		}
		ly += 11;
		TableStyle.rule(g, x, ly - 2, w);
		tableX = x;
		tableW = w;
		tableTop = ly;
		tableH = y + h - ly;
		TableStyle.listBackground(g, x, tableTop, w, tableH);

		List<Integer> people = personSlots();
		int first = menu.state(HallSections.PEOPLE_FIRST);
		int total = total();
		int detailW = w - 24;
		int openAt = -1;
		int openH = 0;
		for (int i = 0; i < people.size(); i++) {
			CompoundTag row = HallPeople.row(menu.slots.get(people.get(i)).getItem());
			if (row != null && row.getString("uuid").equals(openUuid)) {
				openAt = first + i;
				openH = detailHeight(menu.slots.get(people.get(i)).getItem(), detailW);
			}
		}
		int content = total * RH + openH + 4;
		int max = Math.max(0, content - tableH);
		tableScroll = Math.max(0, Math.min(max, tableScroll));
		keepWindow(tableH / RH);
		g.enableScissor(x, tableTop, x + w, tableTop + tableH);
		for (int i = 0; i < total; i++) {
			int top = tableTop + 2 + i * RH - tableScroll + (openAt >= 0 && i > openAt ? openH : 0);
			if (top + RH < tableTop || top > tableTop + tableH) {
				continue;
			}
			int k = i - first;
			if (k < 0 || k >= people.size()) {
				g.drawString(font, "...", cols[0], top + 2, TableStyle.LOADING, false);
				continue;
			}
			int slot = people.get(k);
			ItemStack icon = menu.slots.get(slot).getItem();
			CompoundTag row = HallPeople.row(icon);
			boolean open = i == openAt;
			boolean over = mouseX >= x && mouseX < x + w - 6 && mouseY >= Math.max(top, tableTop) && mouseY < top + RH && mouseY < tableTop + tableH;
			if (open) {
				g.fill(x + 1, top, x + w - 7, top + RH + openH - 1, 0x40FFD66B);
			} else if (over) {
				g.fill(x + 1, top, x + w - 7, top + RH, 0x30FFFFFF);
			}
			hits.add(new int[]{0, slot, x, top, w - 6, RH});
			String kind = row.getString("kind");
			g.drawString(font, TableStyle.trim(font, icon.getHoverName(), colW[0]), cols[0], top + 2, TableStyle.NAME, false);
			Component job = Component.translatable(row.getString("job"));
			int jobColour = kind.equals("builder") ? TableStyle.TITLE : kind.equals("child") || kind.equals("jobless") || kind.equals("nitwit")
				? TableStyle.SUBLINE : TableStyle.BODY;
			g.drawString(font, TableStyle.trim(font, job, colW[1]), cols[1], top + 2, jobColour, false);
			g.drawString(font, TableStyle.trim(font, HallPeople.level(row.getInt("level")), colW[2]), cols[2], top + 2, TableStyle.BODY, false);
			HallPeople.MoodWord mood = moodOf(row);
			int moodColour = switch (mood) {
				case HAPPY -> MOOD_HAPPY;
				case UNHAPPY -> MOOD_UNHAPPY;
				case NO_BED -> MOOD_NO_BED;
				default -> TableStyle.BODY;
			};
			g.drawString(font, TableStyle.trim(font, mood.word(), colW[3]), cols[3], top + 2, moodColour, false);
			if (open) {
				int dy = top + RH + 2;
				for (FormattedCharSequence line : detailLines(icon, detailW)) {
					g.drawString(font, line, x + 12, dy, TableStyle.BODY, false);
					dy += 10;
				}
				dy += 3;
				int bx = x + 12;
				String action = row.getString("job_action");
				boolean listOpen = row.getBoolean("jobs_open");
				if (!row.getBoolean("job_hidden")) { // game rule workplaceRetraining off hides Job ▾ (40.3)
					bx = button(g, Component.translatable("screen.aliveworkplace.hall.people.job_button"), bx, dy, !action.isEmpty(), 2, slot, mouseX, mouseY);
					if (mouseX >= bx - 4 - buttonWidth("job_button") && mouseX < bx - 4 && mouseY >= dy && mouseY < dy + BH) {
						tip = Component.translatable(listOpen ? "screen.aliveworkplace.hall.people.job_open" : action.isEmpty()
							? "screen.aliveworkplace.hall.people.job_none" : "screen.aliveworkplace.hall.people.job_" + action);
					}
				}
				int bedX = bx;
				bx = button(g, Component.translatable("screen.aliveworkplace.hall.people.bed_button"), bx, dy, true, 3, slot, mouseX, mouseY);
				if (mouseX >= bedX && mouseX < bx - 4 && mouseY >= dy && mouseY < dy + BH) {
					tip = Component.translatable(row.getBoolean("beds_open") ? "screen.aliveworkplace.hall.people.bed_open" : "screen.aliveworkplace.hall.people.bed_hint");
				}
				if (!action.equals("assign")) {
					button(g, Component.translatable("screen.aliveworkplace.hall.people.find_button"), bx, dy, true, 4, slot, mouseX, mouseY);
				}
				List<Component> jobTip = renderJobList(g, icon, x + 12, dy + BH + 4, detailW, mouseX, mouseY);
				if (jobTip != null) {
					jobTips = jobTip;
				}
				List<Component> bedTip = renderBedList(g, icon, x + 12, dy + BH + 4 + jobListHeight(icon, detailW), detailW, mouseX, mouseY);
				if (bedTip != null) {
					jobTips = bedTip;
				}
			}
		}
		g.disableScissor();
		TableStyle.scrollbar(g, x + w - 6, tableTop, tableH, tableScroll, max, content);
		if (tooltip != null) {
			g.renderTooltip(font, tooltip, mouseX, mouseY);
		} else if (jobTips != null) {
			g.renderComponentTooltip(font, jobTips, mouseX, mouseY);
		} else if (tip != null) {
			g.renderTooltip(font, tip, mouseX, mouseY);
		}
	}

	private static HallPeople.MoodWord moodOf(CompoundTag row) {
		try {
			return HallPeople.MoodWord.valueOf(row.getString("mood").toUpperCase());
		} catch (IllegalArgumentException e) {
			return HallPeople.MoodWord.NONE;
		}
	}

	private int buttonWidth(String key) {
		return font.width(Component.translatable("screen.aliveworkplace.hall.people." + key)) + 10;
	}

	/** A small button in an open row; returns where the next one goes. */
	private int button(GuiGraphics g, Component text, int x, int y, boolean on, int kind, int slot, int mouseX, int mouseY) {
		int bw = font.width(text) + 10;
		boolean over = on && mouseX >= x && mouseX < x + bw && mouseY >= y && mouseY < y + BH;
		g.fill(x, y, x + bw, y + BH, over ? TableStyle.CELL_HOVER : TableStyle.CELL_BG);
		g.fill(x, y, x + bw, y + 1, 0x40FFFFFF);
		g.drawString(font, text, x + 5, y + 3, on ? TableStyle.NAME : TableStyle.FAINT, false);
		if (on) {
			hits.add(new int[]{kind, slot, x, y, bw, BH});
		}
		return x + bw + 4;
	}

	@Override
	protected boolean bodyClicked(double mx, double my, int button) {
		// Buttons first (they sit on an open row's area), then strip and rows.
		for (int pass = 0; pass < 2; pass++) {
			for (int[] hit : hits) {
				boolean isButton = hit[0] >= 2;
				if ((pass == 0) != isButton || mx < hit[2] || mx >= hit[2] + hit[4] || my < hit[3] || my >= hit[3] + hit[5]) {
					continue;
				}
				if ((hit[0] == 0 || hit[0] == 5 || hit[0] == 6 || hit[0] == 7) && (my < tableTop || my >= tableTop + tableH)) {
					continue;
				}
				click(hit[0], hit[1], button);
				return true;
			}
		}
		return false;
	}

	private void click(int kind, int slot, int button) {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		}
		ItemStack icon = kind >= 5 ? ItemStack.EMPTY : menu.slots.get(slot).getItem();
		CompoundTag row = HallPeople.row(icon);
		switch (kind) {
			case 0 -> {
				String uuid = row == null ? null : row.getString("uuid");
				openUuid = uuid != null && uuid.equals(openUuid) ? null : uuid; // click again closes it
				if (jobListOpenElsewhere(openUuid)) {
					menuButton(HallJobs.CLOSE); // the job list goes with the row it was open on
				}
				if (bedListOpenElsewhere(openUuid)) {
					menuButton(HallBeds.CLOSE); // and so does the bed list (40.4)
				}
			}
			case 1 -> press(slot, button, button == 0 && hasShiftDown());
			// Job ▾: the list of every job opens (or closes) under the row (40.3)
			case 2 -> menuButton(HallJobs.OPEN + slot);
			case 3 -> menuButton(HallBeds.OPEN + slot); // Bed ▾: the village's beds open (or close) under the row (40.4)
			case 4 -> press(slot, 0, false); // Find: they glow
			case 5 -> menuButton(HallJobs.PICK + slot); // a job on Job ▾'s list (slot: the job's number)
			case 6 -> menuButton(HallBeds.PICK + slot); // a bed on Bed ▾'s list (slot: the line's number)
			case 7 -> open(Section.BUILDINGS); // "Short of beds? Plan a house ›"
			default -> {
			}
		}
	}

	@Override
	protected boolean bodyScrolled(double mx, double my, double scrollY) {
		tableScroll = Math.max(0, tableScroll - (int) Math.round(scrollY * RH * 2));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && customBody() && openUuid != null) {
			if (jobListOpenElsewhere(null)) {
				menuButton(HallJobs.CLOSE);
			}
			if (bedListOpenElsewhere(null)) {
				menuButton(HallBeds.CLOSE);
			}
			openUuid = null; // Esc closes the open row first
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	/** Opens the table's row {@code index} of those loaded (the screenshot harness), scrolled into view; false if there's none. */
	public boolean openRow(int index) {
		List<Integer> people = personSlots();
		if (index < 0 || index >= people.size()) {
			return false;
		}
		CompoundTag row = HallPeople.row(menu.slots.get(people.get(index)).getItem());
		openUuid = row == null ? null : row.getString("uuid");
		tableScroll = Math.max(0, (menu.state(HallSections.PEOPLE_FIRST) + index - 1) * RH);
		return openUuid != null;
	}

	/** Opens the row of the villager called {@code name} (the screenshot harness), scrolled into view; false if there's none. */
	public boolean openRowNamed(String name) {
		List<Integer> people = personSlots();
		for (int i = 0; i < people.size(); i++) {
			if (menu.slots.get(people.get(i)).getItem().getHoverName().getString().startsWith(name)) {
				return openRow(i);
			}
		}
		return false;
	}

	/** Clicks Job ▾ on the open row (the screenshot harness), as a player does; false with no row open. */
	public boolean pressJob() {
		for (int slot : personSlots()) {
			CompoundTag row = HallPeople.row(menu.slots.get(slot).getItem());
			if (row != null && row.getString("uuid").equals(openUuid)) {
				click(2, slot, 0);
				return true;
			}
		}
		return false;
	}

	/** Clicks Bed ▾ on the open row (the screenshot harness), as a player does; false with no row open. */
	public boolean pressBed() {
		for (int slot : personSlots()) {
			CompoundTag row = HallPeople.row(menu.slots.get(slot).getItem());
			if (row != null && row.getString("uuid").equals(openUuid)) {
				click(3, slot, 0);
				return true;
			}
		}
		return false;
	}

	/** Clicks the bed on line {@code n} of the open bed list (the screenshot harness), as a player does. */
	public void pickBed(int n) {
		click(6, n, 0);
	}

	/** Clicks the job numbered {@code id} on the open job list (the screenshot harness), as a player does. */
	public void pickJob(int id) {
		click(5, id, 0);
	}

	/** Scrolls the table by {@code rows} (the screenshot harness). */
	public void scrollTable(int rows) {
		tableScroll = Math.max(0, tableScroll + rows * RH);
	}
}
