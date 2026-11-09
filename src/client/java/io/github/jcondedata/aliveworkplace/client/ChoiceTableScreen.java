package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * A server's {@link ChoiceMenu} drawn in the Blueprint Table's style (ROADMAP 26.12): the Village Hall's screen and the
 * Storehouse board. The buttons in the strip rows (the hall's figures and toolbar, the board's top row) sit as framed
 * icon cells under the gold title; every other button is a row of the list on the left, its icon, its name and its
 * first line; the pane on the right reads out whatever is under the mouse, holds it while the mouse moves into the pane
 * (or shows the last button clicked once the mouse leaves the panel; {@link io.github.jcondedata.aliveworkplace.work.ReadingFocus}),
 * name and every line, scrolled with the wheel there or with Shift over the list, and with nothing chosen it shows the
 * strip's first row "at a glance".
 *
 * <p>Nothing here decides anything: a click on a row or a cell is the chest click the server's menu always took (a
 * left click, a right click, a shift-click), the server runs the button and sends the page back. The slots stay the
 * client's {@link ChoiceView}, in the server's order, but none is drawn where the chest put it.
 */
public class ChoiceTableScreen extends AbstractContainerScreen<ChoiceView> {
	/** The Trade page's marks: six 8x8 marks in a row (star, short of, up, down, steady, ribbon), the open tab's 16x2 bar under them. */
	public static final ResourceLocation MARKS = AliveWorkplace.id("textures/gui/trade_marks.png");
	private static final int MARKS_W = 64;
	private static final int MARKS_H = 16;
	private static final int LINE = 10;
	/** The gap between two strip rows' cells (the hall's figures and its toolbar). */
	private static final int GROUP_GAP = 8;

	private final int[] stripRows;
	private final int listFrom;
	/** The last chest row of the list's first part (the hall's page tabs), ruled off from the rest; -1: one part. */
	private final int splitAfterRow;

	private int left, top, panelW, panelH;
	private int listX, listY, listW, listH;
	private int paneX, paneY, paneW, paneH;
	/** The strip's cells (button slots) and where each is drawn. */
	private final List<int[]> cells = new ArrayList<>();
	/** The list's rows (button slots), in order. */
	private final List<Integer> rows = new ArrayList<>();
	private int listScroll;
	/** The height of the plain sentence over the list ({@link #listNote}; 0 without one). */
	private int noteH;
	private boolean draggingBar;
	/** What the pane reads out (36.5): hovered, held while the mouse goes into the pane, or the button last clicked. */
	private final io.github.jcondedata.aliveworkplace.work.ReadingFocus reading = new io.github.jcondedata.aliveworkplace.work.ReadingFocus();
	/** The pinned button's name when it was clicked (another name there: the button changed). */
	private String pinnedName = "";
	private int detailScroll;
	/** The button the pane read out last frame (its scroll starts again for another). */
	private int lastShown = -2;
	private int detailMax;
	private int closeX, closeY;

	protected ChoiceTableScreen(ChoiceView menu, Inventory inventory, Component title, int[] stripRows, int listFrom, int splitAfterRow) {
		super(menu, inventory, title);
		this.stripRows = stripRows;
		this.listFrom = listFrom;
		this.splitAfterRow = splitAfterRow;
	}

	/** Whether {@code icon} is a button to show: not nothing, not a pane put there to fill a row or divide one. */
	public static boolean shown(ItemStack icon) {
		return !icon.isEmpty() && !icon.has(DataComponents.HIDE_TOOLTIP);
	}

	@Override
	protected void init() {
		panelW = TableStyle.panelWidth(width);
		panelH = TableStyle.panelHeight(height);
		imageWidth = panelW;
		imageHeight = panelH;
		super.init();
		left = leftPos;
		top = topPos;
	}

	// --- layout (every frame: the server may send another page at any time) ---------------------------------------

	private void layout() {
		cells.clear();
		int side = sidebarWidth();
		int x = left + 8 + side;
		int y = top + 20;
		int right = left + panelW - 8;
		boolean any = false;
		boolean allRows = rowsOnly();
		for (int r = 0; r < stripRows.length && !allRows; r++) {
			boolean groupStarted = false;
			for (int c = 0; c < 9; c++) {
				int slot = stripRows[r] * 9 + c;
				if (!shown(icon(slot)) || !showSlot(slot)) {
					continue;
				}
				if (!groupStarted && any) {
					x += GROUP_GAP;
				}
				if (x + TableStyle.CELL - 2 > right) {
					x = left + 8 + side;
					y += TableStyle.CELL;
				}
				cells.add(new int[]{slot, x, y});
				x += TableStyle.CELL;
				groupStarted = true;
				any = true;
			}
		}
		rows.clear();
		for (int slot = allRows ? 0 : listFrom * 9; slot < ChoiceMenu.SIZE; slot++) {
			if (shown(icon(slot)) && showSlot(slot)) {
				rows.add(slot);
			}
		}
		listX = left + 8 + side;
		listY = any ? y + TableStyle.CELL + 4 : top + 22;
		listW = TableStyle.listWidth(panelW - side);
		listH = top + panelH - 16 - listY;
		paneX = listX + listW + 10;
		paneY = listY;
		paneW = left + panelW - 8 - paneX;
		paneH = listH;
		Component note = listNote();
		noteH = note == null ? 0 : font.split(note, listW - 12).size() * LINE + 6;
		listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
		int pinned = reading.pinned();
		if (pinned >= 0 && (!shown(icon(pinned)) || !icon(pinned).getHoverName().getString().equals(pinnedName))) {
			reading.unpin(); // another page, or the button changed: nothing to read out
		}
		reading.keepOnly(slot -> shown(icon(slot)));
	}

	private ItemStack icon(int slot) {
		return menu.slots.get(slot).getItem();
	}

	private int maxListScroll() {
		return Math.max(0, noteH + rows.size() * TableStyle.ROW + 4 - listH);
	}

	private int rowTop(int index) {
		return listY + 4 + noteH + index * TableStyle.ROW - listScroll;
	}

	private boolean overList(double mx, double my) {
		return mx >= listX && mx < listX + listW && my >= listY && my < listY + listH;
	}

	private boolean overBar(double mx, double my) {
		return maxListScroll() > 0 && mx >= listX + listW - 6 && mx < listX + listW && my >= listY && my < listY + listH;
	}

	/** The button under the mouse (a list row or a strip cell), or -1. */
	private int slotAt(double mx, double my) {
		for (int[] cell : cells) {
			if (mx >= cell[1] && mx < cell[1] + TableStyle.CELL - 2 && my >= cell[2] && my < cell[2] + TableStyle.CELL - 2) {
				return cell[0];
			}
		}
		if (overList(mx, my) && !overBar(mx, my) && mx >= listX + 6 && mx < listX + listW - 6) {
			for (int i = 0; i < rows.size(); i++) {
				int t = rowTop(i);
				if (my >= t - 2 && my < t + TableStyle.ROW - 2) {
					return rows.get(i);
				}
			}
		}
		return -1;
	}

	// --- drawing ---------------------------------------------------------------------------------------------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (listMode()) {
			renderListMode(g, mouseX, mouseY, partialTick);
			return;
		}
		layout();
		renderBlurredBackground(partialTick);
		renderMenuBackground(g);
		TableStyle.panel(g, left, top, panelW, panelH);
		int side = sidebarWidth();
		TableStyle.header(g, font, headerTitle(), subtitle(), left + side, top, panelW - side);
		renderSidebar(g, mouseX, mouseY);
		if (customBody()) {
			// A body drawn by the screen itself (the hall's People table, 40.2) in place of the list and the pane.
			renderBody(g, left + 8 + side, top + 22, panelW - 16 - side, panelH - 16 - 22, mouseX, mouseY);
			Component bodyHint = Component.translatable("screen.aliveworkplace.choice.hint");
			g.drawString(font, font.plainSubstrByWidth(bodyHint.getString(), panelW - 16), left + 8, top + panelH - 12, TableStyle.FAINT, false);
			return;
		}
		int hovered = slotAt(mouseX, mouseY);
		int pinned = reading.pinned();
		for (int[] cell : cells) {
			int slot = cell[0];
			TableStyle.cell(g, cell[1], cell[2], slot == pinned, slot == hovered);
			drawIcon(g, icon(slot), cell[1] + 1, cell[2] + 1);
		}
		renderList(g, mouseX, mouseY, hovered);
		int focus = reading.focus(hovered, overReading(mouseX, mouseY));
		if (focus != lastShown) {
			detailScroll = 0;
			lastShown = focus;
		}
		if (focus >= 0) {
			renderDetail(g, focus, mouseX, mouseY);
		} else if (!renderIdle(g, paneX, paneY, paneW, paneH)) {
			renderOverview(g);
		}
		Component hint = Component.translatable("screen.aliveworkplace.choice.hint");
		g.drawString(font, font.plainSubstrByWidth(hint.getString(), panelW - 16), left + 8, top + panelH - 12, TableStyle.FAINT, false);
	}

	/** The line at the title's right (none by default). */
	protected Component subtitle() {
		return Component.empty();
	}

	/** The gold title at the panel's top left (the menu's title by default). */
	protected Component headerTitle() {
		return title;
	}

	// --- hooks for a sidebar (the Village Hall's, 40.1) ---------------------------------------------------------------

	/** The width a sidebar takes at the panel's left (none by default); everything else moves right of it. */
	protected int sidebarWidth() {
		return 0;
	}

	/** Draws the sidebar (nothing by default). */
	protected void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
	}

	/** Takes a click on the sidebar; true when it did. */
	protected boolean sidebarClicked(double mx, double my, int button) {
		return false;
	}

	/** Whether button {@code slot} is shown at all (every button by default). */
	protected boolean showSlot(int slot) {
		return true;
	}

	/** Whether every button shown is a list row, the strip rows' too (no by default). */
	protected boolean rowsOnly() {
		return false;
	}

	/** Whether the screen is the list screen (ROADMAP 42.1; {@link #renderListMode}) in place of the strip, list and pane (no by default). */
	protected boolean listMode() {
		return false;
	}

	/** A plain sentence over the list's rows (none by default). */
	protected Component listNote() {
		return null;
	}

	/** Draws the pane with nothing chosen in the box given; false to leave it to "At a glance" (the default). */
	protected boolean renderIdle(GuiGraphics g, int x, int y, int w, int h) {
		return false;
	}

	/** Whether the screen draws the right side itself ({@link #renderBody}) in place of the list and the pane (no by default). */
	protected boolean customBody() {
		return false;
	}

	/** Draws the right side in the box given, when {@link #customBody} says so. */
	protected void renderBody(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
	}

	/** Takes a click on a body drawn by the screen; true when it did. */
	protected boolean bodyClicked(double mx, double my, int button) {
		return false;
	}

	/** Takes the wheel over a body drawn by the screen; true when it did. */
	protected boolean bodyScrolled(double mx, double my, double scrollY) {
		return false;
	}

	/** The panel's left edge, top edge, width and height (for a sidebar). */
	protected int[] panelBox() {
		return new int[]{left, top, panelW, panelH};
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		// drawn in render
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		// the title is drawn in render
	}

	private void renderList(GuiGraphics g, int mouseX, int mouseY, int hovered) {
		int pinned = reading.pinned();
		TableStyle.listBackground(g, listX, listY, listW, listH);
		Component note = listNote();
		if (rows.isEmpty() && note == null) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.choice.empty"), listX + 6, listY + 6, listW - 12, TableStyle.LOADING);
			return;
		}
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		if (note != null) {
			g.drawWordWrap(font, note, listX + 6, listY + 6 - listScroll, listW - 12, TableStyle.BODY);
		}
		int x0 = listX + 6;
		int x1 = listX + listW - 6;
		for (int i = 0; i < rows.size(); i++) {
			int t = rowTop(i);
			if (t + TableStyle.ROW < listY || t - 2 > listY + listH) {
				continue;
			}
			int slot = rows.get(i);
			ItemStack stack = icon(slot);
			if (slot == pinned) {
				TableStyle.selection(g, x0, t, x1 - x0, TableStyle.ROW - 4, true);
			} else if (slot == hovered) {
				g.fill(x0, t - 2, x1, t + TableStyle.ROW - 2, 0x30FFFFFF);
			}
			if (i > 0 && splitAfterRow >= 0 && rows.get(i - 1) / 9 <= splitAfterRow && slot / 9 > splitAfterRow) {
				TableStyle.rule(g, x0, t - 3, x1 - x0); // the page tabs above, the people below
			}
			drawIcon(g, stack, x0 + 2, t + 1);
			TableStyle.rowText(g, font, stack.getHoverName(), firstLine(stack), x0 + 24, t, x1 - x0 - 28);
		}
		g.disableScissor();
		int max = maxListScroll();
		TableStyle.scrollbar(g, listX + listW - 6, listY, listH, listScroll, max, rows.size() * TableStyle.ROW + 4);
	}

	/** A button's icon, its count and its marks (the Trade page's, the leaderboard's ribbon). */
	private void drawIcon(GuiGraphics g, ItemStack stack, int x, int y) {
		g.renderItem(stack, x, y);
		g.renderItemDecorations(font, stack, x, y);
		Set<String> marks = TradePage.marks(stack);
		if (!marks.isEmpty()) {
			g.pose().pushPose();
			g.pose().translate(0, 0, 300); // over the icon and its count
			marks(g, x, y, marks);
			g.pose().popPose();
		}
	}

	/**
	 * The pane on the right reading out button {@code slot}: its icon and name, a rule, then every line of it, scrolled
	 * by the wheel (over the pane, or over the list with Shift), and an arrow at its foot while there's more below.
	 */
	private void renderDetail(GuiGraphics g, int slot, int mouseX, int mouseY) {
		int pinned = reading.pinned();
		ItemStack stack = icon(slot);
		int x = paneX;
		int y = paneY;
		drawIcon(g, stack, x, y + 1);
		int nameW = paneW - 22 - (slot == pinned ? 10 : 0);
		List<String> name = pack(stack.getHoverName().getString(), nameW);
		int ny = y + (name.size() > 1 ? 0 : 5);
		for (int i = 0; i < Math.min(2, name.size()); i++) {
			g.drawString(font, Component.literal(name.get(i)).withStyle(stack.getHoverName().getStyle()), x + 20, ny + i * LINE, TableStyle.NAME, false);
		}
		closeX = -1;
		if (slot == pinned) {
			closeX = x + paneW - 8;
			closeY = y + 1;
			boolean over = mouseX >= closeX - 2 && mouseX < closeX + 8 && mouseY >= closeY - 2 && mouseY < closeY + 10;
			g.drawString(font, "×", closeX, closeY, over ? TableStyle.NAME : TableStyle.SUBTITLE, false);
		}
		y += 22;
		TableStyle.rule(g, x, y, paneW);
		y += 4;
		List<FormattedCharSequence> lines = new ArrayList<>();
		if (name.size() > 2) {
			// A long name (a chronicle entry, 36.5): what doesn't fit beside the icon starts the reading.
			for (String part : pack(String.join(" ", name.subList(2, name.size())), paneW - 6)) {
				lines.add(Component.literal(part).withStyle(stack.getHoverName().getStyle()).getVisualOrderText());
			}
		}
		for (Component line : lore(stack)) {
			List<FormattedCharSequence> parts = font.split(line, paneW - 6);
			if (parts.isEmpty()) {
				lines.add(FormattedCharSequence.EMPTY);
			} else {
				lines.addAll(parts);
			}
		}
		int bodyH = paneY + paneH - y;
		int content = lines.size() * LINE;
		detailMax = Math.max(0, content - bodyH);
		detailScroll = Math.max(0, Math.min(detailScroll, detailMax));
		g.enableScissor(x, y, x + paneW, y + bodyH);
		for (int i = 0; i < lines.size(); i++) {
			int ly = y + i * LINE - detailScroll;
			if (ly + LINE < y || ly > y + bodyH) {
				continue;
			}
			g.drawString(font, lines.get(i), x, ly, TableStyle.BODY, false);
		}
		g.disableScissor();
		TableStyle.thinScrollbar(g, x + paneW - 3, y, bodyH, detailScroll, detailMax, content);
		if (detailScroll < detailMax) {
			moreBelow(g, x + paneW - 14, paneY + paneH - 5);
		}
	}

	/** {@code text} in lines no wider than {@code width}: whole words where they fit, a word too long for a line cut. */
	private List<String> pack(String text, int width) {
		List<String> out = new ArrayList<>();
		String now = "";
		for (String word : text.split(" ")) {
			String tried = now.isEmpty() ? word : now + " " + word;
			if (font.width(tried) <= width) {
				now = tried;
				continue;
			}
			if (!now.isEmpty()) {
				out.add(now);
			}
			now = word;
			while (font.width(now) > width && width > 0) {
				String head = font.plainSubstrByWidth(now, width);
				if (head.isEmpty()) {
					break;
				}
				out.add(head);
				now = now.substring(head.length());
			}
		}
		if (!now.isEmpty()) {
			out.add(now);
		}
		return out;
	}

	/** The "more below" arrow: a small downward triangle, 7 wide and 4 tall, its tip at {@code tipY}. */
	private static void moreBelow(GuiGraphics g, int left, int tipY) {
		for (int row = 0; row < 4; row++) {
			g.fill(left + row, tipY - 3 + row, left + 7 - row, tipY - 2 + row, TableStyle.NAME);
		}
	}

	/** Whether the mouse is over the panel (the strip, the list, the pane and the gaps): where a hovered row stays read out. */
	private boolean overReading(double mx, double my) {
		return mx >= left && mx < left + panelW && my >= top && my < top + panelH;
	}

	/** Whether the mouse is over the reading pane. */
	private boolean overPane(double mx, double my) {
		return mx >= paneX && mx < paneX + paneW && my >= paneY && my < paneY + paneH;
	}

	/** With nothing chosen: the strip's first row read out, name and first line, under "At a glance". */
	private void renderOverview(GuiGraphics g) {
		detailMax = 0;
		List<Integer> glance = new ArrayList<>();
		for (int[] cell : cells) {
			if (cell[0] / 9 == stripRows[0] && !lore(icon(cell[0])).isEmpty()) {
				glance.add(cell[0]);
			}
		}
		int x = paneX;
		int y = paneY;
		if (glance.isEmpty()) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.choice.help"), x, y, paneW, TableStyle.LOADING);
			return;
		}
		TableStyle.heading(g, font, Component.translatable("screen.aliveworkplace.choice.overview"), x, y);
		y += 12;
		int room = paneY + paneH - y - 12;
		g.enableScissor(x, y, x + paneW, paneY + paneH);
		int whole = 0;
		for (int slot : glance) {
			whole += Math.max(TableStyle.ROW, 11 + wrapped(icon(slot), paneW - 20).size() * LINE + 3);
		}
		if (whole <= room) {
			// Few enough to read whole (the Storehouse board's): each name with every line of it.
			for (int slot : glance) {
				ItemStack stack = icon(slot);
				drawIcon(g, stack, x, y + 1);
				g.drawString(font, TableStyle.trim(font, stack.getHoverName(), paneW - 20), x + 20, y + 1, TableStyle.NAME, false);
				int ly = y + 11;
				for (FormattedCharSequence line : wrapped(stack, paneW - 20)) {
					g.drawString(font, line, x + 20, ly, TableStyle.SUBLINE, false);
					ly += LINE;
				}
				y = Math.max(y + TableStyle.ROW, ly + 3);
			}
		} else if (glance.size() * TableStyle.ROW <= room) {
			// Each with its first line, as the list's rows.
			for (int slot : glance) {
				ItemStack stack = icon(slot);
				drawIcon(g, stack, x, y + 1);
				TableStyle.rowText(g, font, stack.getHoverName(), firstLine(stack), x + 20, y, paneW - 20);
				y += TableStyle.ROW;
			}
		} else {
			// Too many for that (the hall's nine figures at a small GUI): two columns of icons and names.
			int colW = (paneW - 4) / 2;
			int perCol = (glance.size() + 1) / 2;
			int step = Math.max(LINE + 1, Math.min(19, room / Math.max(1, perCol)));
			for (int i = 0; i < glance.size(); i++) {
				ItemStack stack = icon(glance.get(i));
				int cx = x + (i / perCol) * (colW + 4);
				int cy = y + (i % perCol) * step;
				if (step >= 17) {
					drawIcon(g, stack, cx, cy);
					g.drawString(font, TableStyle.trim(font, stack.getHoverName(), colW - 20), cx + 19, cy + 4, TableStyle.NAME, false);
				} else {
					g.drawString(font, TableStyle.trim(font, stack.getHoverName(), colW), cx, cy, TableStyle.NAME, false);
				}
			}
			y += perCol * step;
		}
		g.disableScissor();
		g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.choice.help"), x, Math.min(y + 4, paneY + paneH - 9), paneW, TableStyle.FAINT);
	}

	/** Every line of {@code stack}, broken to {@code w} pixels, in the second line's plain grey-blue. */
	private List<FormattedCharSequence> wrapped(ItemStack stack, int w) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component line : lore(stack)) {
			out.addAll(font.split(Component.literal(line.getString()), w));
		}
		return out;
	}

	private static List<Component> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	private static String firstLine(ItemStack stack) {
		List<Component> lore = lore(stack);
		return lore.isEmpty() ? "" : lore.get(0).getString();
	}

	/** Draws an icon's marks over it; {@code x}, {@code y} is the icon's top-left corner. */
	static void marks(GuiGraphics g, int x, int y, Set<String> marks) {
		if (marks.contains(TradePage.STAR)) {
			mark(g, x - 1, y - 1, 0);
		} else if (marks.contains(TradePage.SHORT)) {
			mark(g, x - 1, y - 1, 1);
		}
		if (marks.contains(TradePage.UP)) {
			mark(g, x + 9, y + 9, 2);
		} else if (marks.contains(TradePage.DOWN)) {
			mark(g, x + 9, y + 9, 3);
		} else if (marks.contains(TradePage.STEADY)) {
			mark(g, x + 9, y + 9, 4);
		}
		if (marks.contains(io.github.jcondedata.aliveworkplace.realm.Leaderboard.RIBBON)) {
			mark(g, x + 9, y - 1, 5);
		}
		if (marks.contains(TradePage.OPEN)) {
			g.blit(MARKS, x, y + 15, 16, 2, 0f, 8f, 16, 2, MARKS_W, MARKS_H);
		}
	}

	private static void mark(GuiGraphics g, int x, int y, int index) {
		g.blit(MARKS, x, y, 8, 8, index * 8f, 0f, 8, 8, MARKS_W, MARKS_H);
	}

	// --- input -----------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (listMode()) {
			return listClicked(mx, my, button);
		}
		layout();
		if (sidebarClicked(mx, my, button)) {
			return true;
		}
		if (customBody()) {
			return bodyClicked(mx, my, button) || mx >= left && mx < left + panelW && my >= top && my < top + panelH;
		}
		if (button == 0 && overBar(mx, my)) {
			draggingBar = true;
			return true;
		}
		if (button == 0 && reading.pinned() >= 0 && closeX >= 0 && mx >= closeX - 2 && mx < closeX + 8 && my >= closeY - 2 && my < closeY + 10) {
			reading.unpin();
			return true;
		}
		int slot = slotAt(mx, my);
		if (slot >= 0 && (button == 0 || button == 1)) {
			press(slot, button, button == 0 && hasShiftDown());
			return true;
		}
		// Nothing else here takes a click (the chest's slots aren't where its screen would put them).
		return mx >= left && mx < left + panelW && my >= top && my < top + panelH;
	}

	/** Clicks button {@code slot} as the chest would have (also used by the screenshot harness), and reads it out. */
	public void press(int slot, int button, boolean shift) {
		slotClicked(menu.slots.get(slot), slot, button, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
		reading.pin(slot);
		pinnedName = icon(slot).getHoverName().getString();
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		draggingBar = false;
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingBar && listMode()) {
			listLayout();
			int max = lmMaxScroll();
			int content = lmRows.size() * TableStyle.LIST_ROW + 4;
			int bar = Math.max(32, Math.min(lmH - 8, lmH * lmH / Math.max(1, content)));
			double per = Math.max(1.0, (double) max / Math.max(1, lmH - bar));
			lmScroll = (int) Math.max(0, Math.min(max, lmScroll + dy * per));
			return true;
		}
		if (draggingBar) {
			int max = maxListScroll();
			int content = rows.size() * TableStyle.ROW + 4;
			int bar = Math.max(32, Math.min(listH - 8, listH * listH / Math.max(1, content)));
			double per = Math.max(1.0, (double) max / Math.max(1, listH - bar));
			listScroll = (int) Math.max(0, Math.min(max, listScroll + dy * per));
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (listMode()) {
			listLayout();
			if (mx >= lmX && mx < lmX + lmW && my >= lmY && my < lmY + lmH) {
				lmScroll = Math.max(0, Math.min(lmMaxScroll(), lmScroll - (int) Math.round(scrollY * TableStyle.LIST_ROW / 2.0)));
				return true;
			}
			return false;
		}
		layout();
		if (customBody()) {
			return bodyScrolled(mx, my, scrollY);
		}
		boolean list = overList(mx, my);
		boolean pane = overPane(mx, my);
		if (io.github.jcondedata.aliveworkplace.work.ReadingFocus.wheelReads(list, pane, hasShiftDown())) {
			scrollReading(scrollY);
			return true;
		}
		if (list) {
			listScroll = Math.max(0, Math.min(maxListScroll(), listScroll - (int) Math.round(scrollY * TableStyle.ROW / 2.0)));
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && reading.pinned() >= 0) {
			reading.unpin(); // as the Table's full materials list: Esc puts it away first
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The button the pane reads out with nothing under the mouse (-1: none; for the screenshot harness and tests). */
	public int pinned() {
		return reading.pinned();
	}

	/** The wheel turned over the reading ({@code scrollY} notches, up positive), as over the pane (also the screenshot harness). */
	public void scrollReading(double scrollY) {
		detailScroll = Math.max(0, Math.min(detailMax, detailScroll - (int) Math.round(scrollY * LINE * 2)));
	}

	/** How far the reading is scrolled, and how far it can go (for the screenshot harness). */
	public int[] readingScroll() {
		return new int[]{detailScroll, detailMax};
	}

	/** The button the pane reads out for the mouse at x, y, as a frame would (for the screenshot harness). */
	public int readingAt(int mouseX, int mouseY) {
		layout();
		return reading.focus(slotAt(mouseX, mouseY), overReading(mouseX, mouseY));
	}

	/** Where button {@code slot}'s middle is on the screen, in GUI pixels (the screenshot harness points at it); a list row is scrolled into view. */
	public int[] centre(int slot) {
		if (listMode()) {
			return listCentre(slot);
		}
		layout();
		for (int[] cell : cells) {
			if (cell[0] == slot) {
				return new int[]{cell[1] + 8, cell[2] + 8};
			}
		}
		int i = rows.indexOf(slot);
		if (i >= 0) {
			int t = rowTop(i);
			if (t - 2 < listY || t + TableStyle.ROW - 2 > listY + listH) {
				listScroll = Math.max(0, Math.min(maxListScroll(), i * TableStyle.ROW + 4 - (listH - TableStyle.ROW) / 2));
				t = rowTop(i);
			}
			return new int[]{listX + 6 + 10, t + 9};
		}
		// Not a button shown: the footer, where nothing is read out.
		return new int[]{left + panelW / 2, top + panelH - 6};
	}

	/** The middle of the reading pane, in GUI pixels (the screenshot harness moves the mouse there). */
	public int[] paneCentre() {
		layout();
		return new int[]{paneX + paneW / 2, paneY + paneH / 2};
	}

	/** Buttons in the list, in order (for the screenshot harness). */
	public List<Integer> listSlots() {
		if (listMode()) {
			listLayout();
			return List.copyOf(lmRows);
		}
		layout();
		return List.copyOf(rows);
	}

	// --- list mode (ROADMAP 42.1) --------------------------------------------------------------------------------------
	// The list screen for the menus that were a plain chest (ChoiceMenu.openList): the title and the money at the top,
	// a tab row, a list of rows (icon, name, second line, price) with a button on each, gray rows that can't be picked,
	// a footer hint with the page arrows. What each icon is comes from its mark (work/ChoiceList); a shown button without
	// one is a plain row. Every click is still the chest click the server's menu takes, on the same slot; every icon's
	// tooltip shows when it's pointed at. The drawing pieces are TableStyle's shared list-menu section.

	/** The tabs, the rows and the page arrows (button slots), and the menu's own line's slot (-1: none). */
	private final List<Integer> lmTabs = new ArrayList<>();
	private final List<Integer> lmRows = new ArrayList<>();
	private final List<Integer> lmPages = new ArrayList<>();
	private int lmInfo = -1;
	/** Where each tab and page arrow is: slot, x, y, width, height. */
	private final List<int[]> lmBoxes = new ArrayList<>();
	/** The list's box, how tall its headings are, its scroll and the buttons' width. */
	private int lmX, lmY, lmW, lmH, lmHead, lmScroll, lmButtonW;
	/** The money line's box (pointing at it shows the menu's own tooltip). */
	private int lmMoneyX, lmMoneyW;

	private void listLayout() {
		lmTabs.clear();
		lmRows.clear();
		lmPages.clear();
		lmBoxes.clear();
		lmInfo = -1;
		lmButtonW = 0;
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack stack = icon(slot);
			if (!shown(stack) || !showSlot(slot)) {
				continue;
			}
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(stack);
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Kind kind = mark == null ? io.github.jcondedata.aliveworkplace.work.ChoiceList.Kind.ROW : mark.kind();
			switch (kind) {
				case TAB -> lmTabs.add(slot);
				case PAGE -> lmPages.add(slot);
				case INFO -> lmInfo = lmInfo < 0 ? slot : lmInfo;
				case CELL -> {
					// (drawn inside the row that names it, 42.4)
				}
				default -> {
					lmRows.add(slot);
					int[] cells = io.github.jcondedata.aliveworkplace.work.ChoiceList.cells(stack);
					if (cells.length > 0) {
						lmButtonW = Math.max(lmButtonW, cellsWidth(cells));
					} else if (mark != null && mark.button() != null) {
						lmButtonW = Math.max(lmButtonW, TableStyle.buttonWidth(font, mark.button()));
					}
				}
			}
		}
		// Rows with an order go where it says (ROADMAP 42.3), slot order among equals (the sort is stable).
		lmRows.sort(java.util.Comparator.comparingInt(slot -> {
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark m = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(icon(slot));
			return m == null ? 0 : m.order();
		}));
		int x = left + 8;
		int y = top + 22;
		int right = left + panelW - 8;
		for (int slot : lmTabs) {
			Component label = icon(slot).getHoverName();
			int w = TableStyle.tabWidth(font, label, true, Math.max(40, (panelW - 16) / Math.max(1, Math.min(lmTabs.size(), 6)) - 4));
			if (x + w > right && x > left + 8) {
				x = left + 8;
				y += TableStyle.TAB_H + 3;
			}
			lmBoxes.add(new int[]{slot, x, y, w, TableStyle.TAB_H});
			x += w + 4;
		}
		lmX = left + 8;
		lmY = lmTabs.isEmpty() ? top + 22 : y + TableStyle.TAB_H + 5;
		lmW = panelW - 16;
		lmH = top + panelH - 18 - lmY;
		io.github.jcondedata.aliveworkplace.work.ChoiceList.Info info = lmInfo < 0 ? null : io.github.jcondedata.aliveworkplace.work.ChoiceList.info(icon(lmInfo));
		lmHead = info != null && !info.columns().isEmpty() ? 13 : 0;
		// The page arrows at the footer's right, the next one last.
		int px = right;
		List<Integer> pages = new ArrayList<>(lmPages);
		pages.sort(java.util.Comparator.comparing(this::pageNext)); // the previous one first
		for (int i = pages.size() - 1; i >= 0; i--) {
			int slot = pages.get(i);
			int w = TableStyle.buttonWidth(font, icon(slot).getHoverName());
			px -= w;
			lmBoxes.add(new int[]{slot, px, top + panelH - 16, w, 13});
			px -= 4;
		}
		lmScroll = Math.max(0, Math.min(lmScroll, lmMaxScroll()));
	}

	private boolean pageNext(int slot) {
		io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(icon(slot));
		return mark != null && mark.on();
	}

	private int lmMaxScroll() {
		return Math.max(0, lmHead + lmRows.size() * TableStyle.LIST_ROW + 4 - lmH);
	}

	private int lmRowTop(int index) {
		return lmY + 2 + lmHead + index * TableStyle.LIST_ROW - lmScroll;
	}

	private int[] lmColumns() {
		return TableStyle.listColumns(lmX, lmW - 6, lmButtonW);
	}

	/** The button under the mouse in list mode: a tab, a page arrow or a row (-1: none). */
	private int listSlotAt(double mx, double my) {
		for (int[] box : lmBoxes) {
			if (mx >= box[1] && mx < box[1] + box[3] && my >= box[2] && my < box[2] + box[4]) {
				return box[0];
			}
		}
		if (mx >= lmX && mx < lmX + lmW - 6 && my >= lmY + lmHead + 2 && my < lmY + lmH) {
			for (int i = 0; i < lmRows.size(); i++) {
				int t = lmRowTop(i);
				if (my >= t && my < t + TableStyle.LIST_ROW) {
					for (int[] cell : cellBoxes(lmRows.get(i), t)) {
						if (mx >= cell[1] && mx < cell[1] + cell[3] && my >= cell[2] && my < cell[2] + cell[4]) {
							return cell[0];
						}
					}
					return lmRows.get(i);
				}
			}
		}
		return -1;
	}

	/**
	 * Draws the list screen: the panel, the gold title and the money, the tabs, the list with its column headings and
	 * rows, the footer hint and page arrows, and the tooltip of whatever is under the mouse.
	 */
	private void renderListMode(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		listLayout();
		renderBlurredBackground(partialTick);
		renderMenuBackground(g);
		TableStyle.panel(g, left, top, panelW, panelH);
		io.github.jcondedata.aliveworkplace.work.ChoiceList.Info info = lmInfo < 0 ? null : io.github.jcondedata.aliveworkplace.work.ChoiceList.info(icon(lmInfo));
		Component money = info == null || info.money() == null ? Component.empty() : info.money();
		lmMoneyW = money.getString().isEmpty() ? 0 : font.width(money);
		lmMoneyX = left + panelW - 8 - lmMoneyW;
		TableStyle.header(g, font, headerTitle(), Component.empty(), left, top, panelW - (lmMoneyW > 0 ? lmMoneyW + 8 : 0));
		if (lmMoneyW > 0) {
			TableStyle.money(g, font, money, left + panelW - 8, top + 8);
		}
		int hovered = listSlotAt(mouseX, mouseY);
		for (int[] box : lmBoxes) {
			int slot = box[0];
			ItemStack stack = icon(slot);
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(stack);
			if (lmTabs.contains(slot)) {
				TableStyle.tab(g, font, stack.getHoverName(), box[1], box[2], box[3], true, mark != null && mark.on(), slot == hovered);
				g.renderItem(stack, box[1] + 3, box[2] + 1);
			} else {
				TableStyle.button(g, font, stack.getHoverName(), box[1], box[2], box[3], box[4], false, slot == hovered, false);
			}
		}
		TableStyle.listBackground(g, lmX, lmY, lmW, lmH);
		int[] columns = lmColumns();
		if (lmHead > 0) {
			TableStyle.columnHeadings(g, font, info.columns(), columns, lmY + 4);
		}
		if (lmRows.isEmpty()) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.choice.empty"), lmX + 6, lmY + lmHead + 6, lmW - 12, TableStyle.LOADING);
		}
		g.enableScissor(lmX, lmY + lmHead, lmX + lmW, lmY + lmH);
		for (int i = 0; i < lmRows.size(); i++) {
			int t = lmRowTop(i);
			if (t + TableStyle.LIST_ROW < lmY || t > lmY + lmH) {
				continue;
			}
			int slot = lmRows.get(i);
			ItemStack stack = icon(slot);
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(stack);
			boolean off = mark != null && mark.off();
			boolean on = mark != null && mark.on();
			Component second = mark != null ? mark.second() : firstLine(stack).isEmpty() ? null : Component.literal(firstLine(stack));
			TableStyle.listRow(g, font, columns, lmX + lmW - 8, t, stack.getHoverName(), second, mark == null ? null : mark.price(), off, on, slot == hovered);
			g.renderItem(stack, columns[0], t + 2);
			List<int[]> cellBoxes = cellBoxes(slot, t);
			if (!cellBoxes.isEmpty()) {
				for (int[] cell : cellBoxes) {
					drawCell(g, cell, cell[0] == hovered, off);
				}
			} else if (mark != null && mark.button() != null) {
				TableStyle.button(g, font, mark.button(), columns[4], t + 3, lmButtonW, TableStyle.BUTTON_H, on, slot == hovered, off);
			}
		}
		g.disableScissor();
		TableStyle.scrollbar(g, lmX + lmW - 6, lmY, lmH, lmScroll, lmMaxScroll(), lmHead + lmRows.size() * TableStyle.LIST_ROW + 4);
		Component hint = info != null && info.hint() != null ? info.hint() : Component.translatable("screen.aliveworkplace.list.hint");
		int pagesW = 0;
		for (int[] box : lmBoxes) {
			if (lmPages.contains(box[0])) {
				pagesW = Math.max(pagesW, left + panelW - 8 - box[1] + 6);
			}
		}
		TableStyle.footerHint(g, font, hint, left + 8, top + panelH - 13, panelW - 16 - pagesW);
		// The tooltip of whatever is under the mouse, as the chest showed it: a row, a tab, a page arrow, or the menu's
		// own (the money line).
		if (hovered >= 0) {
			g.renderTooltip(font, icon(hovered), mouseX, mouseY);
		} else if (lmInfo >= 0 && lmMoneyW > 0 && mouseX >= lmMoneyX && mouseX < lmMoneyX + lmMoneyW && mouseY >= top + 6 && mouseY < top + 18) {
			g.renderTooltip(font, icon(lmInfo), mouseX, mouseY);
		}
	}

	private boolean listClicked(double mx, double my, int button) {
		listLayout();
		if (button == 0 && lmMaxScroll() > 0 && mx >= lmX + lmW - 6 && mx < lmX + lmW && my >= lmY && my < lmY + lmH) {
			draggingBar = true;
			return true;
		}
		int slot = listSlotAt(mx, my);
		if (slot >= 0 && (button == 0 || button == 1)) {
			// The chest click as before (a gray row's too: the server decides, as it did for the chest's icon).
			press(slot, button, button == 0 && hasShiftDown());
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(icon(slot));
			if (mark == null || !mark.off()) {
				net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
					net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
			}
			return true;
		}
		return mx >= left && mx < left + panelW && my >= top && my < top + panelH;
	}

	// A row's cells (ROADMAP 42.4, ChoiceList.cells): small buttons inside the row at its right, each its own slot.

	/** How wide cell {@code slot} is: a small text button, or an icon cell when it has no label. */
	private int cellWidth(int slot) {
		io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(icon(slot));
		return mark != null && mark.button() != null ? Math.max(TableStyle.CELL, font.width(mark.button()) + 10) : TableStyle.CELL - 2;
	}

	private int cellsWidth(int[] cells) {
		int w = 0;
		for (int cell : cells) {
			w += cellWidth(cell) + 3;
		}
		return Math.max(0, w - 3);
	}

	/** The shown cells of row {@code row} whose top is {@code t}: slot, x, y, width, height. */
	private List<int[]> cellBoxes(int row, int t) {
		List<int[]> out = new ArrayList<>();
		int x = lmColumns()[4];
		for (int cell : io.github.jcondedata.aliveworkplace.work.ChoiceList.cells(icon(row))) {
			if (cell < 0 || cell >= ChoiceMenu.SIZE || !shown(icon(cell))) {
				continue;
			}
			int w = cellWidth(cell);
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(icon(cell));
			boolean text = mark != null && mark.button() != null;
			out.add(new int[]{cell, x, text ? t + 3 : t + 1, w, text ? TableStyle.BUTTON_H : TableStyle.CELL - 2});
			x += w + 3;
		}
		return out;
	}

	/** Draws a row's cell (box from {@link #cellBoxes}): its label as a small button, or its icon in a framed cell. */
	private void drawCell(GuiGraphics g, int[] box, boolean over, boolean off) {
		ItemStack stack = icon(box[0]);
		io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(stack);
		boolean on = mark != null && mark.on();
		if (mark != null && mark.button() != null) {
			TableStyle.button(g, font, mark.button(), box[1], box[2], box[3], box[4], on, over, off);
		} else {
			TableStyle.cell(g, box[1], box[2], on, over);
			g.renderItem(stack, box[1] + 1, box[2] + 1);
		}
	}

	/** Where button {@code slot}'s middle is in list mode (a row's button if it has one); a row is scrolled into view. */
	private int[] listCentre(int slot) {
		listLayout();
		for (int[] box : lmBoxes) {
			if (box[0] == slot) {
				return new int[]{box[1] + box[3] / 2, box[2] + box[4] / 2};
			}
		}
		int i = lmRows.indexOf(slot);
		int inRow = -1;
		for (int r = 0; i < 0 && r < lmRows.size(); r++) {
			for (int cell : io.github.jcondedata.aliveworkplace.work.ChoiceList.cells(icon(lmRows.get(r)))) {
				if (cell == slot) {
					i = r;
					inRow = lmRows.get(r);
				}
			}
		}
		if (i >= 0) {
			int t = lmRowTop(i);
			if (t < lmY + lmHead || t + TableStyle.LIST_ROW > lmY + lmH) {
				lmScroll = Math.max(0, Math.min(lmMaxScroll(), i * TableStyle.LIST_ROW - (lmH - lmHead - TableStyle.LIST_ROW) / 2));
				t = lmRowTop(i);
			}
			if (inRow >= 0) {
				for (int[] cell : cellBoxes(inRow, t)) {
					if (cell[0] == slot) {
						return new int[]{cell[1] + cell[3] / 2, cell[2] + cell[4] / 2};
					}
				}
			}
			io.github.jcondedata.aliveworkplace.work.ChoiceList.Mark mark = io.github.jcondedata.aliveworkplace.work.ChoiceList.mark(icon(slot));
			int[] columns = lmColumns();
			return mark != null && mark.button() != null
				? new int[]{columns[4] + lmButtonW / 2, t + 3 + TableStyle.BUTTON_H / 2}
				: new int[]{columns[1] + 10, t + TableStyle.LIST_ROW / 2};
		}
		if (slot == lmInfo && lmMoneyW > 0) {
			return new int[]{lmMoneyX + lmMoneyW / 2, top + 12};
		}
		return new int[]{left + panelW / 2, top + panelH - 6};
	}
}
