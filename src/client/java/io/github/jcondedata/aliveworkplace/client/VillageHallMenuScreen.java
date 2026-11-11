package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
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
		if (moveMap()) {
			if (slot == MovePage.CENTRE || slot == MovePage.VIEW) { // the pick's middle on the map (the map's, before it's drawn)
				CompoundTag view = moveView();
				int[] box = view == null ? new int[0] : view.getIntArray(view.getInt("fit") == 1 ? "old" : "spot");
				int[] at = box.length == 4 ? moveMapPoint((box[0] + box[2]) / 2, (box[1] + box[3]) / 2) : null;
				return at != null ? at : new int[]{moveMapX + moveMapS / 2, moveMapY + moveMapS / 2};
			}
			for (int[] hit : moveHits) {
				if (hit[0] == slot) {
					return new int[]{hit[1] + hit[3] / 2, hit[2] + hit[4] / 2};
				}
			}
		}
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
		if (moveMap() || moveList()) {
			return Component.translatable("screen.aliveworkplace.move"); // the Move page, wherever it was opened from (B1163)
		}
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
		if (moveList()) {
			return Component.translatable("screen.aliveworkplace.move.step1"); // step 1 of the Move page (B1163)
		}
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
	/** The open row's Job ▾/Bed ▾ line, as a table scroll (the screenshot harness's {@link #scrollToLists}). */
	private int openButtonsAt = -1;
	/** The window start last asked of the server (-1: none). */
	private int askedFirst = -1;
	/** What the last frame drew, for clicks: [kind, slot, x, y, w, h] (kind 0 row, 1 strip button, 2 job, 3 bed, 4 find). */
	private final List<int[]> hits = new ArrayList<>();
	private int tableTop, tableH, tableX, tableW;

	@Override
	protected boolean customBody() {
		return moveMap() || hasSidebar() && onMain() && section() == Section.PEOPLE;
	}

	// --- the Move page's picker (45.2, reworked for B1163): the steps, the village map, and labeled buttons -------------
	//
	// Three steps across the top (1 Pick a building, 2 Pick a new spot, 3 Confirm), the current one gold, with the step's
	// plain instruction under them. Left: the village map (the land as a vanilla map, roads light, the village's edge, every
	// other building as a brick box, the Hall's bell, where the building stands now in yellow and the new spot in green or
	// red with its front in gold), a legend, and "North is up". Right: what is moving, where it is now and where it would go,
	// why a spot is refused, Rotate, Confirm and Cancel as labeled buttons, and what the Mover will do and about how long.

	private static final net.minecraft.resources.ResourceLocation MOVE_MAP = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("move_map");
	private static final int MOVE_NOW = 0xFFFFD66B;
	private static final int MOVE_FITS = 0xFF55FF55;
	private static final int MOVE_REFUSED = 0xFFFF5555;
	private static final int MOVE_FRONT = 0xFFFFAA00;
	private static final int MOVE_BUILDING = 0xFFA0522D;
	private static final int MOVE_ROAD = 0xFFE3C99A;
	/** What the picker drew last, for clicks: [slot, x, y, w, h]; slot -1 is the map. */
	private final List<int[]> moveHits = new ArrayList<>();
	private net.minecraft.client.renderer.texture.DynamicTexture moveTexture;
	private byte[] moveColors;
	private int moveMapX, moveMapY, moveMapS;
	/** The map's window: its first pixel across and down, and pixels a side (64, zoomed in, or the whole 128). */
	private int moveOrigin, moveWindow = MovePage.MAP;
	/** The zoom the player chose with the mouse wheel (0: picked by what has to show). */
	private int moveZoom;

	/** Whether the Move page's picker is showing (its map cells carry {@link MovePage#MAP_MARK}). */
	private boolean moveMap() {
		net.minecraft.world.item.component.CustomData data = menu.slots.get(MovePage.CENTRE).getItem().get(DataComponents.CUSTOM_DATA);
		return data != null && data.contains(MovePage.MAP_MARK);
	}

	/** Whether the Move page's list (step 1) is showing. */
	private boolean moveList() {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			net.minecraft.world.item.component.CustomData data = menu.slots.get(slot).getItem().get(DataComponents.CUSTOM_DATA);
			if (data != null && data.contains(MovePage.LIST_MARK)) {
				return true;
			}
		}
		return false;
	}

	private CompoundTag moveView() {
		net.minecraft.world.item.component.CustomData data = menu.slots.get(MovePage.VIEW).getItem().get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getCompound(MovePage.VIEW_MARK);
	}

	private int moveStep() {
		net.minecraft.world.item.component.CustomData data = menu.slots.get(MovePage.GUIDE).getItem().get(DataComponents.CUSTOM_DATA);
		return data == null ? 2 : Math.max(2, data.copyTag().getInt(MovePage.STEP_MARK));
	}

	/** The map's texture from the view's colours, roads light and land outside the village darkened; made again when the land changes. */
	private void moveTexture(CompoundTag view) {
		byte[] colors = view.getByteArray("colors");
		if (moveTexture != null && java.util.Arrays.equals(colors, moveColors) || minecraft == null) {
			return;
		}
		byte[] kinds = view.getByteArray("kinds");
		int n = MovePage.MAP;
		int step = Math.max(1, view.getInt("step"));
		long r = (long) view.getInt("radius") * view.getInt("radius");
		com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(n, n, false);
		for (int z = 0; z < n; z++) {
			for (int x = 0; x < n; x++) {
				int i = x + z * n;
				int packed = colors.length == n * n ? colors[i] & 0xFF : 0;
				int argb = packed == 0 ? 0xFFD6BE96 : abgrToArgb(net.minecraft.world.level.material.MapColor.getColorFromPackedId(packed));
				if (kinds.length == n * n && kinds[i] == 1) {
					argb = MOVE_ROAD;
				}
				long dx = view.getInt("ox") + (long) x * step - view.getInt("hallX");
				long dz = view.getInt("oz") + (long) z * step - view.getInt("hallZ");
				if (dx * dx + dz * dz > r) {
					argb = 0xFF000000 | ((argb >> 17 & 0x7F) << 16) | ((argb >> 9 & 0x7F) << 8) | (argb >> 1 & 0x7F); // outside: half as bright
				}
				image.setPixelRGBA(x, z, abgrToArgb(argb) | 0xFF000000);
			}
		}
		if (moveTexture != null) {
			minecraft.getTextureManager().release(MOVE_MAP);
		}
		moveTexture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
		minecraft.getTextureManager().register(MOVE_MAP, moveTexture);
		moveColors = colors;
	}

	/** ABGR to ARGB and back (the same swap). */
	private static int abgrToArgb(int c) {
		return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
	}

	@Override
	public void removed() {
		if (moveTexture != null && minecraft != null) {
			minecraft.getTextureManager().release(MOVE_MAP);
			moveTexture = null;
			moveColors = null;
		}
		super.removed();
	}

	private void renderMoveMap(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		moveHits.clear();
		CompoundTag view = moveView();
		int stepNow = moveStep();
		// the steps
		int gap = 10;
		int tw = (w - 2 * gap) / 3;
		for (int i = 0; i < 3; i++) {
			int tx = x + i * (tw + gap);
			Component label = Component.translatable("screen.aliveworkplace.move.tab" + (i + 1));
			TableStyle.button(g, font, label, tx, y, tw, TableStyle.BUTTON_H, i + 1 == stepNow, false, i + 1 > stepNow);
			if (i < 2) {
				g.drawString(font, ">", tx + tw + (gap - font.width(">")) / 2, y + 3, TableStyle.SUBTITLE, false);
			}
		}
		int ly = y + TableStyle.BUTTON_H + 4;
		ItemStack guide = menu.slots.get(MovePage.GUIDE).getItem();
		for (FormattedCharSequence part : font.split(Component.literal(guide.getHoverName().getString()), w)) {
			g.drawString(font, part, x, ly, stepNow == 3 ? MOVE_FITS : TableStyle.TITLE, false);
			ly += 10;
		}
		ly += 3;
		// the map
		int s = Math.min(h - (ly - y) - 34, w - 170);
		s = s >= 192 ? 192 : s >= 128 ? 128 : Math.max(64, s);
		moveMapX = x;
		moveMapY = ly;
		moveMapS = s;
		List<Component> tip = null;
		if (view != null && !view.isEmpty()) {
			moveTexture(view);
			moveWindow = moveZoom != 0 ? moveZoom : fitsHalf(view) ? MovePage.MAP / 2 : MovePage.MAP;
			moveOrigin = (MovePage.MAP - moveWindow) / 2;
			g.fill(x - 1, ly - 1, x + s + 1, ly + s + 1, TableStyle.PANEL_EDGE);
			g.blit(MOVE_MAP, x, ly, s, s, moveOrigin, moveOrigin, moveWindow, moveWindow, MovePage.MAP, MovePage.MAP);
			g.enableScissor(x, ly, x + s, ly + s);
			int[] others = view.getIntArray("others");
			List<Component> names = loreOf(menu.slots.get(MovePage.VIEW).getItem());
			for (int i = 0; i + 3 < others.length; i += 4) {
				int[] r = moveRect(view, others[i], others[i + 1], others[i + 2], others[i + 3]);
				outlined(g, r, 0xC0000000 | (MOVE_BUILDING & 0xFFFFFF), 0xFF2A140A);
			}
			int[] old = view.getIntArray("old");
			int[] spot = view.getIntArray("spot");
			int fit = view.getInt("fit");
			int[] oldR = old.length == 4 ? moveRect(view, old[0], old[1], old[2], old[3]) : null;
			if (oldR != null) {
				outlined(g, oldR, 0x80FFD66B, MOVE_NOW);
			}
			int[] spotR = spot.length == 4 && fit != 1 ? moveRect(view, spot[0], spot[1], spot[2], spot[3]) : null;
			if (spotR != null) {
				int c = fit == 0 ? MOVE_FITS : MOVE_REFUSED;
				outlined(g, spotR, 0x90000000 | (c & 0xFFFFFF), c);
				// its front edge, in gold
				switch (net.minecraft.core.Direction.from2DDataValue(view.getInt("front"))) {
					case NORTH -> g.fill(spotR[0], spotR[1], spotR[2], spotR[1] + 2, MOVE_FRONT);
					case SOUTH -> g.fill(spotR[0], spotR[3] - 2, spotR[2], spotR[3], MOVE_FRONT);
					case WEST -> g.fill(spotR[0], spotR[1], spotR[0] + 2, spotR[3], MOVE_FRONT);
					default -> g.fill(spotR[2] - 2, spotR[1], spotR[2], spotR[3], MOVE_FRONT);
				}
			}
			// the Hall's bell, at three quarters' size
			int[] hall = moveRect(view, view.getInt("hallX"), view.getInt("hallZ"), view.getInt("hallX"), view.getInt("hallZ"));
			g.pose().pushPose();
			g.pose().translate((hall[0] + hall[2]) / 2f - 6, (hall[1] + hall[3]) / 2f - 6, 0);
			g.pose().scale(0.75f, 0.75f, 1f);
			g.renderItem(new ItemStack(net.minecraft.world.item.Items.BELL), 0, 0);
			g.pose().popPose();
			// names: the other buildings small, Now and New spot over their boxes
			for (int i = 0; i + 3 < others.length && i / 4 < names.size(); i += 4) {
				int[] r = moveRect(view, others[i], others[i + 1], others[i + 2], others[i + 3]);
				smallLabel(g, names.get(i / 4).getString(), (r[0] + r[2]) / 2, r[1] - 6, 0xFFFFFFFF);
			}
			if (oldR != null) { // under its box, so it never sits on the new spot's label above
				smallLabel(g, Component.translatable("screen.aliveworkplace.move.legend.now").getString(), (oldR[0] + oldR[2]) / 2, oldR[3] + 1, MOVE_NOW);
			}
			if (spotR != null) {
				smallLabel(g, Component.translatable("screen.aliveworkplace.move.legend.new").getString(), (spotR[0] + spotR[2]) / 2, spotR[1] - 6,
					fit == 0 ? MOVE_FITS : MOVE_REFUSED);
			}
			g.disableScissor();
			moveHits.add(new int[]{-1, x, ly, s, s});
			if (mouseX >= x && mouseX < x + s && mouseY >= ly && mouseY < ly + s) {
				tip = mapTip(view, names, mouseX, mouseY);
			}
		}
		// the legend and the hint under the map
		int gy = ly + s + 4;
		int gx = x;
		String[] keys = {"now", "new", "building", "road", "front"};
		int[] colors = {MOVE_NOW, MOVE_FITS, MOVE_BUILDING, MOVE_ROAD, MOVE_FRONT};
		for (int i = 0; i < keys.length; i++) {
			Component label = Component.translatable("screen.aliveworkplace.move.legend." + keys[i]);
			int lw = 9 + font.width(label) + 6;
			if (gx + lw > x + s + 4 && gx > x) {
				gx = x;
				gy += 10;
			}
			g.fill(gx, gy + 1, gx + 6, gy + 7, colors[i]);
			g.drawString(font, label, gx + 9, gy, TableStyle.BODY, false);
			gx += lw;
		}
		gy += 11;
		for (FormattedCharSequence part : font.split(Component.translatable("screen.aliveworkplace.move.map.hint"), s + 4)) {
			g.drawString(font, part, x, gy, TableStyle.FAINT, false);
			gy += 9;
		}
		// the right side: what moves, where, why, the buttons and what happens
		int rx = x + s + 10;
		int rw = x + w - rx;
		int ry = ly;
		ItemStack header = menu.slots.get(MovePage.HEADER).getItem();
		ry = wrapped(g, Component.translatable("screen.aliveworkplace.move.moving", header.getHoverName().getString()), rx, ry, rw, TableStyle.TITLE);
		List<Component> lines = loreOf(guide);
		int fit = view == null ? 1 : view.getInt("fit");
		if (lines.size() > 1) {
			ry = swatched(g, lines.get(0), MOVE_NOW, rx, ry, rw, TableStyle.BODY);
			ry = swatched(g, lines.get(1), fit == 0 ? MOVE_FITS : fit == 2 ? MOVE_REFUSED : TableStyle.FAINT, rx, ry, rw, TableStyle.BODY);
		}
		ItemStack verdict = menu.slots.get(MovePage.VERDICT).getItem();
		if (fit == 2 && !verdict.isEmpty()) {
			ry = wrapped(g, verdict.getHoverName(), rx, ry, rw, MOVE_REFUSED);
		}
		ry += 3;
		int bh = TableStyle.BUTTON_H + 1;
		String front = view == null ? "north" : net.minecraft.core.Direction.from2DDataValue(view.getInt("front")).getName();
		ry = moveButton(g, MovePage.ROTATE, Component.translatable("screen.aliveworkplace.move.rotate_button",
			Component.translatable("screen.aliveworkplace.move.direction." + front)), false, false, rx, ry, rw, bh, mouseX, mouseY);
		boolean ready = menu.slots.get(MovePage.CONFIRM).getItem().is(net.minecraft.world.item.Items.PISTON);
		ry = moveButton(g, MovePage.CONFIRM, Component.translatable("screen.aliveworkplace.move.confirm"), ready, !ready, rx, ry, rw, bh, mouseX, mouseY);
		ry = moveButton(g, MovePage.CANCEL, Component.translatable("screen.aliveworkplace.move.cancel"), false, false, rx, ry, rw, bh, mouseX, mouseY);
		if (!menu.slots.get(MovePage.CALL_OFF).getItem().isEmpty()) {
			ry = moveButton(g, MovePage.CALL_OFF, Component.translatable("screen.aliveworkplace.move.call_off"), false, false, rx, ry, rw, bh, mouseX, mouseY);
		}
		ry += 4;
		TableStyle.heading(g, font, Component.translatable("screen.aliveworkplace.move.what"), rx, ry);
		ry += 11;
		if (lines.size() > 4) {
			ry = wrapped(g, lines.get(2), rx, ry, rw, TableStyle.BODY);
			ry = wrapped(g, lines.get(3), rx, ry, rw, TableStyle.SUBTITLE);
			ry = wrapped(g, lines.get(4), rx, ry, rw, lines.get(4).getString().equals(Component.translatable("screen.aliveworkplace.move.mover_here").getString())
				? MOVE_FITS : 0xFFFFFF55);
		}
		if (tip != null) { // the buttons say what they do on their faces: only the map has a tooltip
			g.renderComponentTooltip(font, tip, mouseX, mouseY);
		}
	}

	/** Whether the Hall, where it stands now, the new spot and every building fit the middle half of the map (it zooms in). */
	private static boolean fitsHalf(CompoundTag view) {
		int step = Math.max(1, view.getInt("step"));
		int lo = view.getInt("ox") + MovePage.MAP / 4 * step + 2 * step;
		int hi = view.getInt("ox") + MovePage.MAP * 3 / 4 * step - 2 * step;
		int loZ = view.getInt("oz") + MovePage.MAP / 4 * step + 2 * step;
		int hiZ = view.getInt("oz") + MovePage.MAP * 3 / 4 * step - 2 * step;
		java.util.List<int[]> boxes = new ArrayList<>();
		boxes.add(view.getIntArray("old"));
		if (view.getInt("fit") != 1) {
			boxes.add(view.getIntArray("spot"));
		}
		int[] others = view.getIntArray("others");
		for (int i = 0; i + 3 < others.length; i += 4) {
			boxes.add(new int[]{others[i], others[i + 1], others[i + 2], others[i + 3]});
		}
		for (int[] b : boxes) {
			if (b.length == 4 && (b[0] < lo || b[2] > hi || b[1] < loZ || b[3] > hiZ)) {
				return false;
			}
		}
		return true;
	}

	/** Blocks (inclusive corners) to a rectangle on the map: [x0, y0, x1, y1], at least 2 pixels a side. */
	private int[] moveRect(CompoundTag view, int minX, int minZ, int maxX, int maxZ) {
		int step = Math.max(1, view.getInt("step"));
		double scale = moveMapS / (double) (moveWindow * step);
		int ox = view.getInt("ox") + moveOrigin * step;
		int oz = view.getInt("oz") + moveOrigin * step;
		int x0 = moveMapX + (int) Math.floor((minX - ox) * scale);
		int y0 = moveMapY + (int) Math.floor((minZ - oz) * scale);
		int x1 = moveMapX + (int) Math.ceil((maxX + 1 - ox) * scale);
		int y1 = moveMapY + (int) Math.ceil((maxZ + 1 - oz) * scale);
		return new int[]{x0, y0, Math.max(x1, x0 + 2), Math.max(y1, y0 + 2)};
	}

	private static void outlined(GuiGraphics g, int[] r, int fill, int edge) {
		g.fill(r[0], r[1], r[2], r[3], edge);
		if (r[2] - r[0] > 2 && r[3] - r[1] > 2) {
			g.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, fill);
		}
	}

	/** A label at two thirds' size, centred on {@code cx}, with a shadow so it reads on any land. */
	private void smallLabel(GuiGraphics g, String text, int cx, int top, int color) {
		g.pose().pushPose();
		g.pose().translate(cx, top, 200);
		g.pose().scale(0.67f, 0.67f, 1f);
		g.drawString(font, text, -font.width(text) / 2, 0, color, true);
		g.pose().popPose();
	}

	private int wrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
		for (FormattedCharSequence part : font.split(Component.literal(text.getString()), w)) {
			g.drawString(font, part, x, y, color, false);
			y += 10;
		}
		return y;
	}

	/** A line after a small square in {@code swatch} (the map's colour for it). */
	private int swatched(GuiGraphics g, Component text, int swatch, int x, int y, int w, int color) {
		g.fill(x, y + 1, x + 6, y + 7, swatch);
		return wrapped(g, text, x + 9, y, w - 9, color);
	}

	private int moveButton(GuiGraphics g, int slot, Component label, boolean on, boolean off, int x, int y, int w, int h, int mouseX, int mouseY) {
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h - 1;
		TableStyle.button(g, font, label, x, y, w, h - 1, on, over, off);
		moveHits.add(new int[]{slot, x, y, w, h - 1});
		return y + h + 1;
	}

	/** What is under the mouse on the map: the new spot, where it stands now, a building, the Hall, a road, water or ground. */
	private List<Component> mapTip(CompoundTag view, List<Component> names, int mouseX, int mouseY) {
		int[] at = moveBlockAt(view, mouseX, mouseY);
		int bx = at[0];
		int bz = at[1];
		List<Component> out = new ArrayList<>();
		int[] spot = view.getIntArray("spot");
		int[] old = view.getIntArray("old");
		int[] others = view.getIntArray("others");
		ItemStack header = menu.slots.get(MovePage.HEADER).getItem();
		long dx = bx - view.getInt("hallX");
		long dz = bz - view.getInt("hallZ");
		int kind = view.getByteArray("kinds").length == MovePage.MAP * MovePage.MAP ? view.getByteArray("kinds")[at[3] * MovePage.MAP + at[2]] : 0;
		if (view.getInt("fit") != 1 && inside(spot, 0, bx, bz)) {
			out.add(Component.translatable("screen.aliveworkplace.move.map.spot", header.getHoverName().getString()).withColor(view.getInt("fit") == 0 ? MOVE_FITS : MOVE_REFUSED));
		} else if (inside(old, 0, bx, bz)) {
			out.add(Component.translatable("screen.aliveworkplace.move.map.now").withColor(MOVE_NOW));
		} else if (dx * dx + dz * dz <= 4) {
			out.add(Component.translatable("screen.aliveworkplace.move.map.hall", menu.slots.get(MovePage.VIEW).getItem().getHoverName().getString()));
		} else {
			Component name = null;
			for (int i = 0; i + 3 < others.length; i += 4) {
				if (inside(others, i, bx, bz) && i / 4 < names.size()) {
					name = names.get(i / 4);
				}
			}
			long r = view.getInt("radius");
			out.add(name != null ? name : dx * dx + dz * dz > r * r ? Component.translatable("screen.aliveworkplace.move.map.outside")
				: Component.translatable(kind == 1 ? "screen.aliveworkplace.move.map.road" : kind == 2 ? "screen.aliveworkplace.move.map.water"
				: "screen.aliveworkplace.move.map.ground"));
		}
		out.add(Component.translatable("screen.aliveworkplace.move.map.click").withColor(TableStyle.SUBLINE));
		out.add(Component.translatable("screen.aliveworkplace.move.map.zoom").withColor(TableStyle.SUBLINE));
		return out;
	}

	private static boolean inside(int[] box, int i, int x, int z) {
		return box.length >= i + 4 && x >= box[i] && x <= box[i + 2] && z >= box[i + 1] && z <= box[i + 3];
	}

	/** The block under the mouse on the map: [x, z, pixel x, pixel z]. */
	private int[] moveBlockAt(CompoundTag view, double mouseX, double mouseY) {
		int px = Math.max(0, Math.min(MovePage.MAP - 1, moveOrigin + (int) ((mouseX - moveMapX) * moveWindow / moveMapS)));
		int pz = Math.max(0, Math.min(MovePage.MAP - 1, moveOrigin + (int) ((mouseY - moveMapY) * moveWindow / moveMapS)));
		int step = Math.max(1, view.getInt("step"));
		return new int[]{view.getInt("ox") + px * step + step / 2, view.getInt("oz") + pz * step + step / 2, px, pz};
	}

	private boolean moveMapClicked(double mx, double my, int button) {
		for (int[] hit : moveHits) {
			if (mx >= hit[1] && mx < hit[1] + hit[3] && my >= hit[2] && my < hit[2] + hit[4]) {
				if (minecraft != null) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
				}
				if (hit[0] < 0) {
					int[] at = moveBlockAt(moveView(), mx, my);
					menuButton(MovePage.MAP_CLICK + at[3] * MovePage.MAP + at[2]);
				} else {
					press(hit[0], button, false);
				}
				return true;
			}
		}
		return false;
	}

	/** Where on the screen the map shows block ({@code x}, {@code z}) (the screenshot harness points there), or null off the map. */
	public int[] moveMapPoint(int x, int z) {
		CompoundTag view = moveView();
		if (view == null || view.isEmpty() || moveMapS == 0) {
			return null;
		}
		int step = Math.max(1, view.getInt("step"));
		double scale = moveMapS / (double) (moveWindow * step);
		return new int[]{moveMapX + (int) ((x - view.getInt("ox") - moveOrigin * step + 0.5) * scale),
			moveMapY + (int) ((z - view.getInt("oz") - moveOrigin * step + 0.5) * scale)};
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

	/**
	 * The old list's click hints ("Click: make them glow", "Right-click: change their job", "Shift-click: their life
	 * story", "Click: give them a job"): none is true in the table, where a click opens the row and the row's own buttons
	 * do the rest (B547), so the details leave them out.
	 */
	private static final java.util.Set<String> LIST_HINTS = java.util.Set.of("screen.aliveworkplace.hall.click_to_find",
		"screen.aliveworkplace.hall.worker_click", "screen.aliveworkplace.hall.life_story_hint", "screen.aliveworkplace.hall.jobless_click");

	private static boolean hint(Component line, String key) {
		return line.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
	}

	/** Whether the row's villager has a life story to open: the hall writes its hint only for a named villager (31.7). */
	private static boolean hasStory(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore != null && lore.lines().stream().anyMatch(l -> hint(l, "screen.aliveworkplace.hall.life_story_hint"));
	}

	/** The open row's details: the lines today's resident pane shows, wrapped, without the old list's click hints. */
	private List<FormattedCharSequence> detailLines(ItemStack icon, int w) {
		List<FormattedCharSequence> out = new ArrayList<>();
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (LIST_HINTS.stream().noneMatch(key -> hint(line, key))) {
					out.addAll(font.split(line, w));
				}
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
		if (moveMap()) {
			renderMoveMap(g, x, y, w, h, mouseX, mouseY);
			return;
		}
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
			// The open row is drawn while any of it shows: its details and Job/Bed lists hang under its name line (B546)
			if (top + RH + (i == openAt ? openH : 0) < tableTop || top > tableTop + tableH) {
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
				openButtonsAt = tableScroll + dy - tableTop; // where the Job/Bed buttons sit in the table's content
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
					bx = button(g, Component.translatable("screen.aliveworkplace.hall.people.find_button"), bx, dy, true, 4, slot, mouseX, mouseY);
				}
				if (hasStory(icon)) { // Story: their life story, then Family, as shift-click opened it from the old list (B547)
					int storyX = bx;
					bx = button(g, Component.translatable("screen.aliveworkplace.hall.people.story_button"), bx, dy, true, 8, slot, mouseX, mouseY);
					if (mouseX >= storyX && mouseX < bx - 4 && mouseY >= dy && mouseY < dy + BH) {
						tip = Component.translatable("screen.aliveworkplace.hall.people.story_hint");
					}
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
		if (moveMap()) {
			return moveMapClicked(mx, my, button);
		}
		// Buttons first (they sit on an open row's area), then strip and rows.
		for (int pass = 0; pass < 2; pass++) {
			for (int[] hit : hits) {
				boolean isButton = hit[0] >= 2;
				if ((pass == 0) != isButton || mx < hit[2] || mx >= hit[2] + hit[4] || my < hit[3] || my >= hit[3] + hit[5]) {
					continue;
				}
				// the table's rows, buttons and list lines take clicks only inside it: a part scrolled out of view is hidden (B546)
				if ((hit[0] == 0 || hit[0] >= 2) && (my < tableTop || my >= tableTop + tableH)) {
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
			case 8 -> press(slot, 0, true); // Story: the shift-click that opens their life story (B547)
			default -> {
			}
		}
	}

	@Override
	protected boolean bodyScrolled(double mx, double my, double scrollY) {
		if (moveMap()) {
			if (mx >= moveMapX && mx < moveMapX + moveMapS && my >= moveMapY && my < moveMapY + moveMapS) {
				moveZoom = scrollY > 0 ? MovePage.MAP / 2 : MovePage.MAP; // the wheel zooms the map in and out
			}
			return true;
		}
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

	/** Clicks Story on the open row (the screenshot harness), as a player does; false with no row open or no story. */
	public boolean pressStory() {
		for (int slot : personSlots()) {
			ItemStack icon = menu.slots.get(slot).getItem();
			CompoundTag row = HallPeople.row(icon);
			if (row != null && row.getString("uuid").equals(openUuid) && hasStory(icon)) {
				click(8, slot, 0);
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

	/** Scrolls the table so the open row's Job ▾/Bed ▾ line and the list under it are on top (the screenshot harness). */
	public void scrollToLists() {
		if (openUuid != null && openButtonsAt >= 0) {
			tableScroll = Math.max(0, openButtonsAt - 4); // the render clamps it to the table's foot
		}
	}

	/** Scrolls the table by {@code rows} (the screenshot harness). */
	public void scrollTable(int rows) {
		tableScroll = Math.max(0, tableScroll + rows * RH);
	}
}
