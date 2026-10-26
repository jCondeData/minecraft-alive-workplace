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
		layout();
		return List.copyOf(rows);
	}
}
