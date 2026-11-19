package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallBoard;
import io.github.jcondedata.aliveworkplace.hall.HallMap;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
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

	// --- the rail (Copperline, 47.1; the sidebar since 40.1) ----------------------------------------------------------
	// The ten sections down the window's left under the lintel, each its icon, its word and the copper mark on the open
	// one; on a small screen (320x180, GUI scale 3) a two-column grid of icons, the word in the whisper line under the
	// mouse. The village's name and today's date are on the lintel ("where you are"). The places: CopperLayout.

	/** Whether the screen is a small one (the 320x180 rule). */
	/**
	 * Whether the section rail shows only its icons: on a small screen, and on the Chronicle (48.4), whose Show column
	 * stands beside it, so the entries get the room (the design critique's first point: two worded rails squeezed them).
	 */
	private boolean iconRail() {
		return compactRail() || chronicle();
	}

	private boolean compactRail() {
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.compact(width, height);
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
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.railWidth(panelBox(), text, iconRail());
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

	/** Where you are, on the lintel: the village and today's date (just the date on a small screen). */
	@Override
	protected Component subtitle() {
		if (!hasSidebar()) {
			return Component.empty();
		}
		if (moveMode()) { // the Move page: the village and the step (47.3)
			return Component.translatable("screen.aliveworkplace.move.where", villageName(), moveShownStep());
		}
		if (chronicle()) { // the chronicle: the village and how many entries since which day (48.4)
			return ChronicleView.where(menu, villageName(), compactRail());
		}
		return compactRail() ? date() : Component.translatable("screen.aliveworkplace.copperline.where", villageName(), date());
	}

	/** The rail's entries' boxes, in Section order. */
	private int[][] railEntries() {
		int side = sidebarWidth();
		int[] rail = io.github.jcondedata.aliveworkplace.ui.CopperLayout.rail(panelBox(), side);
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.railEntries(rail, SECTIONS, iconRail());
	}

	private int sectionAt(double mx, double my) {
		if (sidebarWidth() == 0 || moveMode()) {
			return -1;
		}
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.at(railEntries(), mx, my);
	}

	@Override
	protected void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
		int side = sidebarWidth();
		if (side == 0) {
			return;
		}
		if (moveMode()) {
			renderMoveRail(g, mouseX, mouseY); // the Move page's three steps in place of the sections (47.3)
			return;
		}
		int[] rail = io.github.jcondedata.aliveworkplace.ui.CopperLayout.rail(panelBox(), side);
		g.fill(rail[0] + rail[2], rail[1], rail[0] + rail[2] + 1, rail[1] + rail[3], CopperStyle.DEEP_600);
		int[][] entries = railEntries();
		int hovered = sectionAt(mouseX, mouseY);
		Section open = section();
		boolean compact = iconRail();
		for (Section s : Section.values()) {
			CopperStyle.railEntry(g, font, entries[s.ordinal()], CopperStyle.SECTION_ICONS.get(s.ordinal()), null, s.title(), 0,
				s == open, s.ordinal() == hovered, compact);
		}
	}

	/** Under the mouse on the rail: the section's word and what it's for (on a small screen the icon alone names nothing). */
	@Override
	protected Component whisperText(int mouseX, int mouseY, int hovered) {
		if (boardWhisper != null && sectionAt(mouseX, mouseY) < 0) {
			return boardWhisper;
		}
		int i = sectionAt(mouseX, mouseY);
		if (i >= 0) {
			Section s = Section.of(i);
			return Component.translatable("screen.aliveworkplace.copperline.rail_whisper", s.title(), s.about());
		}
		return super.whisperText(mouseX, mouseY, hovered);
	}

	@Override
	protected boolean sidebarClicked(double mx, double my, int button) {
		if (moveMode()) {
			return button == 0 && moveRailClicked(mx, my);
		}
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

	/** Where rail entry {@code section}'s middle is (the screenshot harness and the parts scene point at it). */
	public int[] railCentre(Section section) {
		int[] box = railEntries()[section.ordinal()];
		return new int[]{box[0] + box[2] / 2, box[1] + box[3] / 2};
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
		if (chronicle()) {
			int[] at = chronicleView.centre(slot);
			if (at != null) {
				return at;
			}
		}
		if (moveList()) {
			for (int[] row : moveRows) {
				if (row[0] == slot) {
					return new int[]{row[1] + row[3] / 2, row[2] + row[4] / 2};
				}
			}
		}
		if (moveMap()) {
			if (slot == MovePage.GUIDE) { // the step's instruction: nothing there shows a tooltip
				return new int[]{moveMapX + 4, moveMapY - 6};
			}
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
		if (chronicle()) {
			return Component.translatable("screen.aliveworkplace.hall.chronicle");
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

	// --- the Overview and the Map in Copperline (47.2) -----------------------------------------------------------------
	// The Overview (boards/Main.png): the village's name with its rank, the four stat cards, "Needs you" and "At work now";
	// on a small screen (Compact.png) "Needs you" takes the whole width. The places: HallBoard. The Map (HallMap.png): the
	// village from above with the Hall, its buildings and their names, a legend of layers to switch, the building clicked
	// and Draw a map. What the screen drew last, for clicks: [kind, arg, x, y, w, h] (kind 10 open a section, 11 press a
	// button, 12 a villager's row in People, 13 a map layer, 14 the map, 15 a strip button).

	private final List<int[]> boardHits = new ArrayList<>();
	/** The Overview's cards', "Needs you" lines' and "At work now" lines' boxes last drawn (the screenshot harness points at them). */
	private final List<int[]> cardBoxes = new ArrayList<>();
	private final List<int[]> needBoxes = new ArrayList<>();
	private final List<int[]> workerBoxes = new ArrayList<>();
	/** What the whisper line says about the thing under the mouse on a board (null: the usual). */
	private Component boardWhisper;

	private static int toneColor(HallBoard.Tone tone) {
		return switch (tone) {
			case GOOD -> CopperStyle.VERDIGRIS;
			case BAD -> CopperStyle.REDSTONE;
			case INFO -> CopperStyle.LAPIS;
			case WARNING -> CopperStyle.LANTERN;
			default -> CopperStyle.TUFF;
		};
	}

	private static String toneGlyph(HallBoard.Tone tone) {
		return switch (tone) {
			case GOOD -> "good";
			case BAD -> "bad";
			case INFO -> "info";
			case WARNING -> "warning";
			default -> "dot";
		};
	}

	private static boolean over(int[] box, int mouseX, int mouseY) {
		return mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3];
	}

	/** Text at twice the size (titles and big numbers), with its shadow. */
	private void big(GuiGraphics g, String text, int x, int y, int color) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().scale(2f, 2f, 1f);
		g.drawString(font, text, 0, 0, color, true);
		g.pose().popPose();
	}

	/** The Overview's other buttons (the village's name, requests, beds, builds, quests, what next, pages placed nowhere): the cards stand for the rest. */
	private List<Integer> overviewStrip() {
		java.util.Set<Integer> cards = java.util.Set.of(1, 5, VillageHallScreen.FOOD, VillageHallScreen.GUARDS);
		List<Integer> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.slots.get(slot).getItem();
			if ((HallSections.marks(icon) & Section.OVERVIEW.bit()) != 0 && !cards.contains(slot) && HallPeople.row(icon) == null && ChoiceTableScreen.shown(icon)) {
				out.add(slot);
			}
		}
		return out;
	}

	private void renderOverviewBoard(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		boardHits.clear();
		cardBoxes.clear();
		needBoxes.clear();
		workerBoxes.clear();
		boolean small = compactRail();
		boardWhisper = Component.translatable("screen.aliveworkplace.hall.whisper.overview");
		int[][] boxes = HallBoard.overview(new int[]{x, y, w, h}, small);
		ItemStack tooltip = null;
		// the heading: the village's name at twice the size, its rank; the strip of the Overview's other buttons at the right
		int[] head = boxes[0];
		List<Integer> strip = overviewStrip();
		int sx = head[0] + head[2] - strip.size() * 20 + 2;
		for (int slot : strip) {
			boolean on = mouseX >= sx && mouseX < sx + 18 && mouseY >= head[1] && mouseY < head[1] + 18;
			TableStyle.cell(g, sx, head[1], false, on);
			g.renderItem(menu.slots.get(slot).getItem(), sx + 1, head[1] + 1);
			boardHits.add(new int[]{15, slot, sx, head[1], 18, 18});
			if (on) {
				tooltip = menu.slots.get(slot).getItem();
			}
			sx += 20;
		}
		int nameRight = head[0] + head[2] - strip.size() * 20 - 4;
		CopperStyle.icon(g, "overview", head[0], head[1] + (small ? 2 : 4), 16);
		VillageRanks.Rank rank = VillageRanks.Rank.values()[Math.max(0, Math.min(VillageRanks.Rank.values().length - 1, menu.state(HallSections.RANK)))];
		String rankText = rank.title().getString().toUpperCase(java.util.Locale.ROOT);
		int rankW = font.width(rankText) + 16;
		int nameW = Math.max(20, nameRight - head[0] - 20 - rankW - 6);
		// the name at twice the size when it fits, else at the body size (a long name, or a small screen), cut only then
		String full = villageName().getString();
		boolean twice = font.width(full) * 2 <= nameW;
		String name = twice ? full : CopperStyle.trim(font, full, Math.max(20, nameRight - head[0] - 20 - (font.width(full) + rankW + 6 <= nameRight - head[0] - 20 ? rankW + 6 : 0)));
		if (twice) {
			big(g, name, head[0] + 20, head[1] + (small ? 2 : 4), CopperStyle.QUARTZ);
		} else {
			g.drawString(font, name, head[0] + 20, head[1] + (small ? 6 : 8), CopperStyle.QUARTZ, true);
		}
		int rx = head[0] + 20 + font.width(name) * (twice ? 2 : 1) + 6;
		if (rx + rankW <= nameRight) {
			g.fill(rx, head[1] + (small ? 5 : 7), rx + rankW, head[1] + (small ? 16 : 18), CopperStyle.DEEP_700);
			CopperStyle.glyph(g, "star", rx + 3, head[1] + (small ? 7 : 9));
			g.drawString(font, rankText, rx + 13, head[1] + (small ? 7 : 9), CopperStyle.QUARTZ, false);
		}
		// the four stat cards
		List<HallBoard.Card> cards = HallBoard.cards(menu::state);
		for (int i = 0; i < cards.size(); i++) {
			HallBoard.Card card = cards.get(i);
			int[] b = boxes[1 + i];
			boolean on = over(b, mouseX, mouseY);
			CopperStyle.panel(g, b[0], b[1], b[2], b[3]);
			if (on) {
				g.renderOutline(b[0], b[1], b[2], b[3], CopperStyle.LANTERN);
				boardWhisper = Component.translatable("screen.aliveworkplace.hall.whisper.card", card.title(), card.opens().title());
				tooltip = menu.slots.get(card.slot()).getItem();
			}
			CopperStyle.glyph(g, card.glyph(), b[0] + 4, b[1] + 4);
			g.drawString(font, CopperStyle.trim(font, card.title().getString().toUpperCase(java.util.Locale.ROOT), b[2] - 18), b[0] + 14, b[1] + 4, CopperStyle.TUFF, false);
			String bigText = CopperStyle.trim(font, card.big(), (b[2] - 8) / 2);
			big(g, bigText, b[0] + 4, b[1] + 14, CopperStyle.QUARTZ);
			int ux = b[0] + 4 + font.width(bigText) * 2 + 3;
			String unit = card.unit().getString();
			if (!unit.isEmpty() && ux + font.width(unit) <= b[0] + b[2] - 3) {
				g.drawString(font, unit, ux, b[1] + 22, CopperStyle.TUFF, false);
			}
			if (b[3] >= 36) {
				int ly = b[1] + b[3] - 11;
				CopperStyle.glyph(g, card.lineGlyph(), b[0] + 4, ly);
				g.drawString(font, CopperStyle.trim(font, card.line().getString(), b[2] - 18), b[0] + 14, ly, toneColor(card.tone()), false);
			}
			boardHits.add(new int[]{10, card.opens().ordinal(), b[0], b[1], b[2], b[3]});
			cardBoxes.add(b);
		}
		// Needs you
		int[] needsBox = boxes[5];
		listHeading(g, Component.translatable("screen.aliveworkplace.hall.needs"), needsBox);
		List<HallBoard.Need> needs = HallBoard.needs(i -> menu.slots.get(i).getItem(), menu::state, HallBoard.fits(needsBox[3], HallBoard.LINE_H));
		if (needs.isEmpty()) {
			int[] l = HallBoard.line(needsBox, 0, HallBoard.LINE_H);
			CopperStyle.glyph(g, "good", l[0] + 4, l[1] + 3);
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.hall.needs.none").getString(), l[2] - 18), l[0] + 15, l[1] + 3,
				CopperStyle.TUFF, false);
		}
		for (int i = 0; i < needs.size(); i++) {
			HallBoard.Need need = needs.get(i);
			int[] l = HallBoard.line(needsBox, i, HallBoard.LINE_H);
			boolean on = over(l, mouseX, mouseY);
			g.fill(l[0], l[1], l[0] + l[2], l[1] + l[3], on ? CopperStyle.DEEP_700 : CopperStyle.DEEP_800);
			if (on) {
				g.fill(l[0], l[1], l[0] + 2, l[1] + l[3], CopperStyle.LANTERN);
				// an advice line's whole sentence (the line may be cut), else where the click goes
				boardWhisper = need.opens() != null ? Component.translatable("screen.aliveworkplace.hall.whisper.need", need.opens().title())
					: need.text();
			}
			CopperStyle.glyph(g, toneGlyph(need.tone()), l[0] + 4, l[1] + 3);
			g.drawString(font, CopperStyle.trim(font, need.text().getString(), l[2] - 28), l[0] + 15, l[1] + 3,
				need.tone() == HallBoard.Tone.BAD ? CopperStyle.REDSTONE : CopperStyle.QUARTZ, false);
			CopperStyle.glyph(g, "more", l[0] + l[2] - 11, l[1] + 3);
			boardHits.add(need.opens() != null ? new int[]{10, need.opens().ordinal(), l[0], l[1], l[2], l[3]} : new int[]{11, need.slot(), l[0], l[1], l[2], l[3]});
			needBoxes.add(l);
		}
		// At work now (not on a small screen)
		int[] workBox = boxes[6];
		if (workBox[2] > 40) {
			listHeading(g, Component.translatable("screen.aliveworkplace.hall.at_work_now"), workBox);
			List<HallBoard.Worker> workers = HallBoard.atWork(i -> menu.slots.get(i).getItem(), HallBoard.fits(workBox[3], HallBoard.WORK_H));
			if (workers.isEmpty()) {
				int[] l = HallBoard.line(workBox, 0, HallBoard.WORK_H);
				g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.hall.at_work_now.none").getString(), l[2] - 4), l[0] + 2, l[1] + 3,
					CopperStyle.TUFF, false);
			}
			for (int i = 0; i < workers.size(); i++) {
				HallBoard.Worker worker = workers.get(i);
				int[] l = HallBoard.line(workBox, i, HallBoard.WORK_H);
				boolean on = over(l, mouseX, mouseY);
				if (on) {
					g.fill(l[0], l[1], l[0] + l[2], l[1] + l[3], CopperStyle.DEEP_700);
					g.fill(l[0], l[1], l[0] + 2, l[1] + l[3], CopperStyle.LANTERN);
					boardWhisper = Component.translatable("screen.aliveworkplace.hall.whisper.worker", worker.name().getString());
				}
				CopperStyle.slot(g, font, ItemStack.EMPTY, l[0] + 2, l[1], false);
				ItemStack face = menu.slots.get(worker.slot()).getItem().copyWithCount(1);
				g.renderItem(face, l[0] + 3, l[1] + 1);
				g.drawString(font, CopperStyle.trim(font, worker.name().getString(), l[2] - 26), l[0] + 23, l[1] + 1, CopperStyle.QUARTZ, true);
				g.drawString(font, CopperStyle.trim(font, worker.doing().getString(), l[2] - 26), l[0] + 23, l[1] + 10, CopperStyle.TUFF, false);
				boardHits.add(new int[]{12, worker.slot(), l[0], l[1], l[2], l[3]});
				workerBoxes.add(l);
			}
		}
		if (tooltip != null) {
			g.renderTooltip(font, tooltip, mouseX, mouseY);
		}
	}

	/** A list's heading in Tuff capitals with a rule after it (NEEDS YOU ———). */
	private void listHeading(GuiGraphics g, Component text, int[] box) {
		String t = CopperStyle.trim(font, text.getString().toUpperCase(java.util.Locale.ROOT), box[2]);
		g.drawString(font, t, box[0], box[1] + 1, CopperStyle.TUFF, false);
		int rx = box[0] + font.width(t) + 4;
		if (rx < box[0] + box[2]) {
			g.fill(rx, box[1] + 5, box[0] + box[2], box[1] + 6, CopperStyle.DEEP_600);
		}
	}

	private boolean boardClicked(double mx, double my, int button) {
		for (int[] hit : boardHits) {
			if (mx < hit[2] || mx >= hit[2] + hit[4] || my < hit[3] || my >= hit[3] + hit[5]) {
				continue;
			}
			if (minecraft != null) {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
			}
			switch (hit[0]) {
				case 10 -> open(Section.of(hit[1]));
				case 11 -> press(hit[1], 0, false);
				case 12 -> { // a villager at work: their row, open in People
					CompoundTag row = HallPeople.row(menu.slots.get(hit[1]).getItem());
					open(Section.PEOPLE);
					openUuid = row == null ? null : row.getString("uuid");
					tableScroll = Math.max(0, (menu.state(HallSections.PEOPLE_FIRST) + personSlots().indexOf(hit[1]) - 1) * RH);
				}
				case 13 -> hallLayers ^= 1 << hit[1];
				case 14 -> {
					CompoundTag view = HallMap.view(menu.slots.get(VillageHallScreen.MAP).getItem());
					if (view != null) {
						int[] at = moveBlockAt(view, mx, my);
						int picked = HallMap.buildingAt(view, at[0], at[1]);
						hallSelected = picked == hallSelected ? -1 : picked;
					}
				}
				case 15 -> press(hit[1], button, button == 0 && hasShiftDown());
				default -> {
				}
			}
			return true;
		}
		return false;
	}

	/** The middle of the Overview's card, "Needs you" line or "At work now" line {@code i} (kind 0, 1, 2) as last drawn, or null (the screenshot harness). */
	public int[] boardCentre(int kind, int i) {
		List<int[]> boxes = kind == 0 ? cardBoxes : kind == 1 ? needBoxes : workerBoxes;
		if (i < 0 || i >= boxes.size()) {
			return null;
		}
		int[] b = boxes.get(i);
		return new int[]{b[0] + b[2] / 2, b[1] + b[3] / 2};
	}

	/** Clicks the Overview's card, "Needs you" line or "At work now" line {@code i} (kind 0, 1, 2) as a player does (the screenshot harness). */
	public boolean pressBoard(int kind, int i) {
		int[] at = boardCentre(kind, i);
		return at != null && boardClicked(at[0], at[1], 0);
	}

	/** The middle of the Map's building {@code i} on the map as last drawn, or null (the screenshot harness). */
	public int[] buildingCentre(int i) {
		CompoundTag view = HallMap.view(menu.slots.get(VillageHallScreen.MAP).getItem());
		if (view == null || i < 0 || i >= HallMap.buildings(view) || hallMapBox[2] == 0) {
			return null;
		}
		int[] b = HallMap.box(view, i);
		int[] r = moveRect(view, b[0], b[1], b[2], b[3]);
		return new int[]{(r[0] + r[2]) / 2, (r[1] + r[3]) / 2};
	}

	/** Clicks the Overview's stat card {@code i} as a player does (the screenshot harness): it opens its section. */
	public void pressCard(int i) {
		List<HallBoard.Card> cards = HallBoard.cards(menu::state);
		if (i >= 0 && i < cards.size()) {
			open(cards.get(i).opens());
		}
	}

	/** Selects the Map's building {@code i} (the screenshot harness), as a click on it does. */
	public void selectBuilding(int i) {
		hallSelected = i;
	}

	// The Map section: the village map, its legend of layers and the building selected.

	private static final net.minecraft.resources.ResourceLocation HALL_MAP = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("hall_map");
	private net.minecraft.client.renderer.texture.DynamicTexture hallTexture;
	private byte[] hallColors;
	private int hallTextureLayers = -1;
	/** The layers on (a bit per HallMap.LAYERS), the building selected (-1: none) and whether the wheel zoomed in. */
	private int hallLayers = HallMap.allLayers();
	private int hallSelected = -1;
	private boolean hallZoomed;
	private int[] hallMapBox = {0, 0, 0, 0};
	private final java.util.Map<String, Component> hallNames = new java.util.HashMap<>();

	private boolean layer(String id) {
		return (hallLayers & 1 << HallMap.LAYERS.indexOf(id)) != 0;
	}

	private Component buildingName(CompoundTag view, int i) {
		String json = HallMap.nameJson(view, i);
		if (json.isEmpty() || minecraft == null || minecraft.level == null) {
			return Component.translatable("screen.aliveworkplace.hall.map.building");
		}
		return hallNames.computeIfAbsent(json, j -> {
			try {
				Component c = Component.Serializer.fromJson(j, minecraft.level.registryAccess());
				return c == null ? Component.literal(j) : c;
			} catch (RuntimeException e) {
				return Component.literal(j);
			}
		});
	}

	private void hallTexture(CompoundTag view) {
		byte[] colors = view.getByteArray("colors");
		int layers = (layer("roads") ? 1 : 0) | (layer("edge") ? 2 : 0);
		if (minecraft == null || hallTexture != null && java.util.Arrays.equals(colors, hallColors) && layers == hallTextureLayers) {
			return;
		}
		com.mojang.blaze3d.platform.NativeImage image = mapImage(view, layer("roads"), layer("edge"));
		if (hallTexture != null) {
			minecraft.getTextureManager().release(HALL_MAP);
		}
		hallTexture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
		minecraft.getTextureManager().register(HALL_MAP, hallTexture);
		hallColors = colors;
		hallTextureLayers = layers;
	}

	private void renderHallMap(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		boardHits.clear();
		boardWhisper = Component.translatable("screen.aliveworkplace.hall.map.hint");
		ItemStack mapButton = menu.slots.get(VillageHallScreen.MAP).getItem();
		CompoundTag view = HallMap.view(mapButton);
		if (view == null) {
			return;
		}
		boolean small = compactRail();
		int[][] places = HallMap.layout(new int[]{x, y, w, h}, small);
		int s = places[0][2];
		int mx = places[0][0];
		int my = places[0][1];
		hallMapBox = places[0];
		hallTexture(view);
		// the window: the whole village, or zoomed in on the Hall (the Move page's map arithmetic, on this map)
		int step = Math.max(1, view.getInt("step"));
		int window = hallZoomed ? MovePage.MAP / 2 : MovePage.MAP;
		int hx = (view.getInt("hallX") - view.getInt("ox")) / step;
		int hz = (view.getInt("hallZ") - view.getInt("oz")) / step;
		moveMapX = mx;
		moveMapY = my;
		moveMapS = s;
		moveWindow = window;
		moveOriginX = Math.max(0, Math.min(MovePage.MAP - window, hx - window / 2));
		moveOriginZ = Math.max(0, Math.min(MovePage.MAP - window, hz - window / 2));
		g.fill(mx - 1, my - 1, mx + s + 1, my + s + 1, CopperStyle.VOID);
		g.blit(HALL_MAP, mx, my, s, s, moveOriginX, moveOriginZ, window, window, MovePage.MAP, MovePage.MAP);
		g.enableScissor(mx, my, mx + s, my + s);
		int count = HallMap.buildings(view);
		List<int[]> rects = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int[] b = HallMap.box(view, i);
			rects.add(moveRect(view, b[0], b[1], b[2], b[3]));
		}
		if (layer("buildings")) {
			for (int i = 0; i < count; i++) {
				outlined(g, rects.get(i), 0xE0000000 | (MOVE_BUILDING & 0xFFFFFF), 0xFF2A140A);
			}
		}
		if (hallSelected >= 0 && hallSelected < count) {
			int[] r = rects.get(hallSelected);
			g.renderOutline(r[0] - 2, r[1] - 2, r[2] - r[0] + 4, r[3] - r[1] + 4, CopperStyle.VOID);
			g.renderOutline(r[0] - 1, r[1] - 1, r[2] - r[0] + 2, r[3] - r[1] + 2, CopperStyle.LANTERN);
		}
		// the Hall's bell
		int[] hall = moveRect(view, view.getInt("hallX"), view.getInt("hallZ"), view.getInt("hallX"), view.getInt("hallZ"));
		int bx = (hall[0] + hall[2]) / 2 - 6;
		int by = (hall[1] + hall[3]) / 2 - 6;
		g.pose().pushPose();
		g.pose().translate(bx, by, 0);
		g.pose().scale(0.75f, 0.75f, 1f);
		g.renderItem(new ItemStack(net.minecraft.world.item.Items.BELL), 0, 0);
		g.pose().popPose();
		if (layer("names")) {
			List<int[]> taken = new ArrayList<>(rects);
			int[] bell = {bx, by, bx + 12, by + 12};
			taken.add(bell);
			mapLabel(g, Component.translatable("screen.aliveworkplace.move.legend.hall").getString(), bell, false, CopperStyle.QUARTZ, taken);
			if (hallSelected >= 0 && hallSelected < count) { // the selected one first, always shown
				mapLabel(g, buildingName(view, hallSelected).getString(), rects.get(hallSelected), true, CopperStyle.LANTERN, taken);
			}
			for (int i = 0; i < count; i++) {
				if (i != hallSelected) {
					mapLabel(g, buildingName(view, i).getString(), rects.get(i), true, CopperStyle.QUARTZ, taken, true);
				}
			}
		}
		g.disableScissor();
		// north, and the scale: a bar 16 blocks long
		String north = Component.translatable("screen.aliveworkplace.hall.map.north").getString();
		g.fill(mx + s - font.width(north) - 6, my + 2, mx + s - 2, my + 13, 0xC0000000);
		g.drawString(font, north, mx + s - font.width(north) - 4, my + 4, CopperStyle.QUARTZ, false);
		int bar = (int) Math.round(16.0 * s / (window * step));
		String scale = Component.translatable("screen.aliveworkplace.hall.map.scale", 16).getString();
		if (bar >= 4 && bar + font.width(scale) + 10 < s) {
			g.fill(mx + 2, my + s - 13, mx + bar + font.width(scale) + 10, my + s - 2, 0xC0000000);
			g.fill(mx + 4, my + s - 8, mx + 4 + bar, my + s - 6, CopperStyle.QUARTZ);
			g.drawString(font, scale, mx + bar + 7, my + s - 11, CopperStyle.QUARTZ, false);
		}
		boardHits.add(new int[]{14, 0, mx, my, s, s});
		if (over(hallMapBox, mouseX, mouseY)) {
			int[] at = moveBlockAt(view, mouseX, mouseY);
			int under = HallMap.buildingAt(view, at[0], at[1]);
			if (under >= 0) {
				boardWhisper = Component.translatable("screen.aliveworkplace.hall.map.whisper_building", buildingName(view, under).getString());
			}
		}
		// the right side: the layers, the building selected, Draw a map
		int px = places[1][0];
		int pw = places[1][2];
		int py = places[1][1];
		listHeading(g, Component.translatable("screen.aliveworkplace.hall.map.layers"), new int[]{px, py, pw, 10});
		py += 13;
		int rowH = small ? HallMap.LAYER_H_SMALL : HallMap.LAYER_H;
		for (int i = 0; i < HallMap.LAYERS.size(); i++) {
			String id = HallMap.LAYERS.get(i);
			int[] row = {px, py, pw, rowH - 1};
			boolean on = over(row, mouseX, mouseY);
			CopperStyle.toggle(g, px, py + (rowH - 10) / 2, layer(id), false, on);
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.hall.map.layer." + id).getString(), pw - 44), px + 24,
				py + (rowH - 8) / 2, layer(id) ? CopperStyle.QUARTZ : CopperStyle.TUFF, false);
			if (id.equals("buildings")) {
				String n = String.valueOf(count);
				g.drawString(font, n, px + pw - font.width(n) - 2, py + (rowH - 8) / 2, CopperStyle.TUFF, false);
			}
			if (on) {
				boardWhisper = Component.translatable("screen.aliveworkplace.hall.map.whisper_layer",
					Component.translatable("screen.aliveworkplace.hall.map.layer." + id));
			}
			boardHits.add(new int[]{13, i, row[0], row[1], row[2], row[3]});
			py += rowH;
		}
		py += 4;
		listHeading(g, Component.translatable("screen.aliveworkplace.hall.map.selected"), new int[]{px, py, pw, 10});
		py += 13;
		int drawH = places[2][3];
		int drawY = places[2][1];
		int boxH = Math.max(14, drawY - 4 - py);
		CopperStyle.panel(g, px, py, pw, boxH);
		int ty = py + 4;
		if (hallSelected >= 0 && hallSelected < count) {
			int[] b = HallMap.box(view, hallSelected);
			CopperStyle.icon(g, "buildings", px + 4, ty, 16);
			g.drawString(font, CopperStyle.trim(font, buildingName(view, hallSelected).getString(), pw - 26), px + 24, ty, CopperStyle.QUARTZ, true);
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.hall.map.size", b[2] - b[0] + 1, b[3] - b[1] + 1).getString(), pw - 26),
				px + 24, ty + 10, CopperStyle.TUFF, false);
			long dx = (b[0] + b[2]) / 2 - view.getInt("hallX");
			long dz = (b[1] + b[3]) / 2 - view.getInt("hallZ");
			int far = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			int fy = ty + 21;
			for (FormattedCharSequence part : font.split(Component.translatable("screen.aliveworkplace.hall.map.from_hall", far), pw - 8)) {
				if (fy + 9 > py + boxH) {
					break;
				}
				g.drawString(font, part, px + 4, fy, CopperStyle.TUFF, false);
				fy += 10;
			}
		} else {
			Component hint = Component.translatable(count == 0 ? "screen.aliveworkplace.hall.map.none" : "screen.aliveworkplace.hall.map.pick");
			for (FormattedCharSequence part : font.split(hint, pw - 8)) {
				if (ty + 9 > py + boxH) {
					break;
				}
				g.drawString(font, part, px + 4, ty, CopperStyle.TUFF, false);
				ty += 10;
			}
		}
		// Draw a map: the Map button's own click (a vanilla map of the village for an empty map), the screen's one copper button
		boolean overDraw = mouseX >= px && mouseX < px + pw && mouseY >= drawY && mouseY < drawY + drawH;
		CopperStyle.button(g, font, Component.translatable("screen.aliveworkplace.hall.map.draw"), null, px, drawY, pw, drawH, CopperStyle.Kind.COPPER, overDraw, false, false);
		boardHits.add(new int[]{11, VillageHallScreen.MAP, px, drawY, pw, drawH});
		if (overDraw) {
			boardWhisper = Component.translatable("screen.aliveworkplace.hall.map.whisper_draw");
			List<Component> lore = loreOf(mapButton);
			if (lore.size() > 1) {
				g.renderComponentTooltip(font, lore, mouseX, mouseY);
			}
		}
	}

	// --- the People table (40.2) ------------------------------------------------------------------------------------

	/** A table row's height, and a details button's. */
	private static final int RH = 14;
	private static final int BH = 14;
	/** The Mood column's colors and glyphs (Copperline: a glyph, a word and a color each). */
	private static final int MOOD_HAPPY = CopperStyle.VERDIGRIS;
	private static final int MOOD_UNHAPPY = CopperStyle.LANTERN;
	private static final int MOOD_NO_BED = CopperStyle.REDSTONE;

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
		return moveMode() || board() != null || chronicle();
	}

	// --- the Chronicle page (Copperline, 48.4): the Show column and the entries by day, drawn by ChronicleView ------------
	private final ChronicleView chronicleView = new ChronicleView(this);

	/** Whether the chronicle page is showing (its title carries the chronicle's mark). */
	private boolean chronicle() {
		return hasSidebar() && ChronicleView.showing(menu);
	}

	/** Plays the button click (the chronicle's entries open and close here, without the server). */
	void clickSound() {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		}
	}

	/** Opens the chronicle entry in {@code slot} as a click would (the screenshot harness). */
	public void openChronicleEntry(int slot) {
		chronicleView.open(slot);
	}

	/** The chronicle entry that is open, or -1 (the screenshot harness). */
	public int chronicleOpen() {
		return chronicle() ? chronicleView.openSlot() : -1;
	}

	/** How far the chronicle's list is scrolled, and how far it can go (the screenshot harness). */
	public int[] chronicleScroll() {
		return chronicle() ? chronicleView.scroll() : new int[]{0, 0};
	}

	/** The section the screen draws itself on the main page (People, Overview, and the Map once its view came), or null. */
	private Section board() {
		if (!hasSidebar() || !onMain()) {
			return null;
		}
		Section s = section();
		return s == Section.PEOPLE || s == Section.OVERVIEW || s == Section.MAP && HallMap.view(menu.slots.get(VillageHallScreen.MAP).getItem()) != null ? s : null;
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
	private int moveOriginX, moveOriginZ, moveWindow = MovePage.MAP;
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
		com.mojang.blaze3d.platform.NativeImage image = mapImage(view, true, true);
		if (moveTexture != null) {
			minecraft.getTextureManager().release(MOVE_MAP);
		}
		moveTexture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
		minecraft.getTextureManager().register(MOVE_MAP, moveTexture);
		moveColors = colors;
	}

	/**
	 * A village map's picture from a view's colours (the Move page's, or the Hall's Map section's): its roads light when
	 * {@code roads}, the land outside the village half as bright when {@code edge}.
	 */
	private static com.mojang.blaze3d.platform.NativeImage mapImage(CompoundTag view, boolean roads, boolean edge) {
		byte[] colors = view.getByteArray("colors");
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
				if (roads && kinds.length == n * n && kinds[i] == 1) {
					argb = MOVE_ROAD;
				}
				long dx = view.getInt("ox") + (long) x * step - view.getInt("hallX");
				long dz = view.getInt("oz") + (long) z * step - view.getInt("hallZ");
				if (edge && dx * dx + dz * dz > r) {
					argb = 0xFF000000 | ((argb >> 17 & 0x7F) << 16) | ((argb >> 9 & 0x7F) << 8) | (argb >> 1 & 0x7F); // outside: half as bright
				}
				image.setPixelRGBA(x, z, abgrToArgb(argb) | 0xFF000000);
			}
		}
		return image;
	}

	/** ABGR to ARGB and back (the same swap). */
	private static int abgrToArgb(int c) {
		return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
	}

	@Override
	public void removed() {
		if (hallTexture != null && minecraft != null) {
			minecraft.getTextureManager().release(HALL_MAP);
			hallTexture = null;
			hallColors = null;
		}
		if (moveTexture != null && minecraft != null) {
			minecraft.getTextureManager().release(MOVE_MAP);
			moveTexture = null;
			moveColors = null;
		}
		super.removed();
	}

	// --- the Move page in Copperline (47.3; boards/Move1-3.png) --------------------------------------------------------
	// The rail shows the three steps (Pick, Place, Confirm: done ones ticked, the current one copper-marked) and "Your
	// move" (the building, its size, the spot and the way it faces). Step 1 is a table of the village's buildings with
	// "Can it move?" (Yes, Wait or No with a lock, and why), the one picked and "What happens"; Next opens the picker.
	// Step 2 is the village map (the building now in a dashed frame, the new spot green where it fits and red hatched
	// where it's refused, its front in Lantern), the fit, Rotate and the preview key. Step 3 asks "Move the Bakery?" with
	// From, To, Takes and Faces, "What happens", a small map with From and To, the checklist and one copper "Move building".
	// Steps 2 and 3 are one server page (the picker); the screen shows step 3 once the spot fits and Next is pressed.

	/** Pseudo-slots of the Move page's own buttons in {@link #moveHits}: the map, Next to step 3, Back to step 2, Next from the list, Cancel on the list. */
	private static final int HIT_MAP = -1;
	private static final int HIT_TO_CONFIRM = -2;
	private static final int HIT_TO_PLACE = -3;
	private static final int HIT_LIST_NEXT = -4;
	private static final int HIT_LIST_CANCEL = -5;
	private static final int HIT_ROW = -100;
	/** Step 1's row picked (its slot, -1: none yet), the list's scroll, and whether step 3 is showing. */
	private int moveSelected = -1;
	private int moveListScroll;
	private boolean moveConfirming;
	/** Step 1's rows as last drawn: [slot, x, y, w, h]. */
	private final List<int[]> moveRows = new ArrayList<>();

	private boolean moveMode() {
		return moveMap() || moveList();
	}

	private static CompoundTag moveRow(ItemStack icon) {
		net.minecraft.world.item.component.CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(MovePage.ROW_MARK) ? null : data.copyTag().getCompound(MovePage.ROW_MARK);
	}

	/** Step 1's buildings: the slots of the list's rows, in order. */
	private List<Integer> moveRowSlots() {
		List<Integer> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (moveRow(menu.slots.get(slot).getItem()) != null) {
				out.add(slot);
			}
		}
		return out;
	}

	/** The row picked on step 1: the one clicked, else the first that can move, else the first. */
	private int moveSelectedSlot() {
		List<Integer> rows = moveRowSlots();
		if (rows.contains(moveSelected)) {
			return moveSelected;
		}
		for (int slot : rows) {
			int state = moveRow(menu.slots.get(slot).getItem()).getInt("state");
			if (state == MovePage.ROW_YES || state == MovePage.ROW_PLANNED) {
				return slot;
			}
		}
		return rows.isEmpty() ? -1 : rows.get(0);
	}

	private static boolean movable(CompoundTag row) {
		return row != null && (row.getInt("state") == MovePage.ROW_YES || row.getInt("state") == MovePage.ROW_PLANNED);
	}

	/** The step showing: 1 the list, 2 the map, 3 the confirm. */
	private int moveShownStep() {
		if (!moveMap()) {
			return 1;
		}
		CompoundTag view = moveView();
		if (moveConfirming && view != null && view.getInt("fit") == 0) {
			return 3;
		}
		moveConfirming = false;
		return 2;
	}

	private Component moveWhy(CompoundTag row) {
		String json = row == null ? "" : row.getString("why");
		if (json.isEmpty() || minecraft == null || minecraft.level == null) {
			return Component.translatable("screen.aliveworkplace.move.state.no");
		}
		try {
			Component c = Component.Serializer.fromJson(json, minecraft.level.registryAccess());
			return c == null ? Component.literal(json) : c;
		} catch (RuntimeException e) {
			return Component.literal(json);
		}
	}

	/** The first argument of a translated line ("Now: %s" gives the %s), else the whole line. */
	private static Component arg0(Component line) {
		if (line.getContents() instanceof TranslatableContents t && t.getArgs().length > 0) {
			Object a = t.getArgs()[0];
			return a instanceof Component c ? c : Component.literal(String.valueOf(a));
		}
		return line;
	}

	private static boolean hasKey(List<Component> lines, String key) {
		for (Component l : lines) {
			if (l.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
				return true;
			}
		}
		return false;
	}

	/** The building's name: the picker's, else the row picked. */
	private Component moveName() {
		if (moveMap()) {
			return menu.slots.get(MovePage.HEADER).getItem().getHoverName();
		}
		int slot = moveSelectedSlot();
		return slot < 0 ? Component.empty() : menu.slots.get(slot).getItem().getHoverName();
	}

	private String moveFacing(CompoundTag view) {
		return view == null ? "north" : net.minecraft.core.Direction.from2DDataValue(view.getInt("front")).getName();
	}

	/** The rail's step boxes (three rows from the rail's top, under the STEPS heading). */
	private int[][] moveStepBoxes() {
		int[] rail = io.github.jcondedata.aliveworkplace.ui.CopperLayout.rail(panelBox(), sidebarWidth());
		int top = rail[1] + (compactRail() ? 2 : 14);
		int rowH = compactRail() ? 18 : 20;
		int[][] out = new int[3][];
		for (int i = 0; i < 3; i++) {
			out[i] = new int[]{rail[0], top + i * (rowH + 1), rail[2], rowH};
		}
		return out;
	}

	private void renderMoveRail(GuiGraphics g, int mouseX, int mouseY) {
		int[] rail = io.github.jcondedata.aliveworkplace.ui.CopperLayout.rail(panelBox(), sidebarWidth());
		g.fill(rail[0] + rail[2], rail[1], rail[0] + rail[2] + 1, rail[1] + rail[3], CopperStyle.DEEP_600);
		boolean compact = compactRail();
		if (!compact) {
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.move.steps").getString().toUpperCase(java.util.Locale.ROOT), rail[2] - 8),
				rail[0] + 5, rail[1] + 3, CopperStyle.TUFF, false);
		}
		int now = moveShownStep();
		int[][] boxes = moveStepBoxes();
		String[] keys = {"pick", "place", "confirm"};
		for (int i = 0; i < 3; i++) {
			int[] b = boxes[i];
			boolean current = i + 1 == now;
			boolean done = i + 1 < now;
			boolean over = over(b, mouseX, mouseY) && done;
			if (current) {
				g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], CopperStyle.DEEP_700);
				g.fill(b[0], b[1], b[0] + 2, b[1] + b[3], CopperStyle.COPPER);
			} else if (over) {
				g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], CopperStyle.DEEP_800);
				g.fill(b[0], b[1], b[0] + 2, b[1] + b[3], CopperStyle.LANTERN);
			}
			// the step's number in a square: copper when current, verdigris with a tick when done, slate ahead
			int sq = 12;
			int sx = compact ? b[0] + (b[2] - sq) / 2 : b[0] + 5;
			int sy = b[1] + (b[3] - sq) / 2;
			g.fill(sx, sy, sx + sq, sy + sq, current ? CopperStyle.COPPER : done ? 0xFF2B6E5C : CopperStyle.DEEP_600);
			if (done) {
				CopperStyle.glyph(g, "good", sx + 2, sy + 2);
			} else {
				String n = String.valueOf(i + 1);
				g.drawString(font, n, sx + (sq - font.width(n)) / 2 + 1, sy + 2, current ? CopperStyle.VOID : CopperStyle.TUFF, false);
			}
			if (!compact) {
				String label = Component.translatable("screen.aliveworkplace.move.step." + keys[i]).getString();
				g.drawString(font, CopperStyle.trim(font, label, b[2] - sq - 12), sx + sq + 5, b[1] + (b[3] - 8) / 2,
					current ? CopperStyle.QUARTZ : done ? CopperStyle.QUARTZ : CopperStyle.TUFF, current);
			}
		}
		if (compact) {
			return;
		}
		// Your move: the building, its size, where it goes and the way it faces
		int y = boxes[2][1] + boxes[2][3] + 12;
		int x = rail[0] + 5;
		int w = rail[2] - 8;
		if (y + 40 > rail[1] + rail[3]) {
			return;
		}
		g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.move.your_move").getString().toUpperCase(java.util.Locale.ROOT), w),
			x, y, CopperStyle.TUFF, false);
		y += 13;
		Component name = moveName();
		if (name.getString().isEmpty()) {
			return;
		}
		CopperStyle.icon(g, "buildings", x, y - 4, 16);
		g.drawString(font, CopperStyle.trim(font, name.getString(), w - 20), x + 20, y, CopperStyle.QUARTZ, true);
		y += 14;
		List<Component> lines = new ArrayList<>();
		CompoundTag view = moveView();
		if (moveMap()) {
			List<Component> header = loreOf(menu.slots.get(MovePage.HEADER).getItem());
			if (header.size() > 1) {
				lines.add(header.get(1));
			}
			List<Component> guide = loreOf(menu.slots.get(MovePage.GUIDE).getItem());
			int fit = view == null ? 1 : view.getInt("fit");
			lines.add(fit == 1 || guide.size() < 2 ? Component.translatable("screen.aliveworkplace.move.summary.none")
				: Component.translatable("screen.aliveworkplace.move.summary.to", arg0(guide.get(1))));
			lines.add(Component.translatable("screen.aliveworkplace.move.summary.faces",
				Component.translatable("screen.aliveworkplace.move.direction." + moveFacing(view))));
		} else {
			CompoundTag row = moveRow(menu.slots.get(moveSelectedSlot()).getItem());
			if (row != null && row.contains("w")) {
				lines.add(Component.translatable("screen.aliveworkplace.move.row.size", row.getInt("w"), row.getInt("d")));
			}
			lines.add(Component.translatable("screen.aliveworkplace.move.summary.none"));
		}
		for (Component line : lines) {
			for (FormattedCharSequence part : font.split(line, w)) {
				if (y + 9 > rail[1] + rail[3]) {
					return;
				}
				g.drawString(font, part, x, y, CopperStyle.TUFF, false);
				y += 10;
			}
		}
	}

	private boolean moveRailClicked(double mx, double my) {
		int[][] boxes = moveStepBoxes();
		int now = moveShownStep();
		for (int i = 0; i < 3; i++) {
			if (i + 1 < now && io.github.jcondedata.aliveworkplace.ui.CopperLayout.inside(boxes[i], mx, my)) {
				click();
				if (i == 0) {
					press(MovePage.CANCEL, 0, false); // back to the list: the pick is forgotten
				} else {
					moveConfirming = false;
				}
				return true;
			}
		}
		return false;
	}

	private void click() {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		}
	}

	/** A Copperline button recorded for clicks; returns its box. */
	private int[] moveButton(GuiGraphics g, int hit, Component label, String glyph, CopperStyle.Kind kind, boolean off, int x, int y, int w, int mouseX, int mouseY) {
		int h = 14;
		boolean over = !off && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		CopperStyle.button(g, font, label, glyph, x, y, w, h, kind, over, false, off);
		if (!off) {
			moveHits.add(new int[]{hit, x, y, w, h});
		}
		return new int[]{x, y, w, h};
	}

	private int buttonW(Component label, String glyph) {
		return font.width(label) + 14 + (glyph == null ? 0 : 10);
	}

	// Step 1: the buildings and whether each can move.

	private void renderMoveList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		moveHits.clear();
		moveRows.clear();
		boardWhisper = Component.translatable("screen.aliveworkplace.move.whisper.list");
		boolean small = compactRail();
		int footY = y + h - 15;
		int sideW = small ? 0 : Math.max(110, w * 38 / 100);
		int lw = w - (sideW > 0 ? sideW + 8 : 0);
		List<Integer> rows = moveRowSlots();
		int selected = moveSelectedSlot();
		// the column heads
		g.drawString(font, Component.translatable("screen.aliveworkplace.move.col.building").getString().toUpperCase(java.util.Locale.ROOT), x + 3, y + 1, CopperStyle.TUFF, false);
		String can = Component.translatable("screen.aliveworkplace.move.col.can").getString().toUpperCase(java.util.Locale.ROOT);
		g.drawString(font, can, x + lw - font.width(can) - 3, y + 1, CopperStyle.TUFF, false);
		CopperStyle.headerRow(g, x, y, lw);
		int top = y + 14;
		int listH = footY - 4 - top;
		int rowH = 22;
		if (rows.isEmpty()) {
			// the empty state: the server's one note (no buildings yet)
			ItemStack note = ItemStack.EMPTY;
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
				if (!menu.slots.get(slot).getItem().isEmpty()) {
					note = menu.slots.get(slot).getItem();
					break;
				}
			}
			List<Component> lore = loreOf(note);
			CopperStyle.empty(g, font, "buildings", note.isEmpty() ? Component.translatable("screen.aliveworkplace.move.none") : note.getHoverName(),
				lore.isEmpty() ? Component.empty() : lore.get(0), null, x, top, lw, Math.min(listH, 80), false);
		}
		int content = rows.size() * rowH;
		moveListScroll = Math.max(0, Math.min(moveListScroll, Math.max(0, content - listH)));
		g.enableScissor(x, top, x + lw, top + listH);
		int ry = top - moveListScroll;
		for (int i = 0; i < rows.size(); i++, ry += rowH) {
			int slot = rows.get(i);
			if (ry + rowH <= top || ry >= top + listH) {
				continue;
			}
			ItemStack icon = menu.slots.get(slot).getItem();
			CompoundTag row = moveRow(icon);
			int state = row.getInt("state");
			boolean locked = state == MovePage.ROW_NO || state == MovePage.ROW_WAIT;
			boolean over = mouseX >= x && mouseX < x + lw && mouseY >= Math.max(ry, top) && mouseY < Math.min(ry + rowH, top + listH);
			CopperStyle.rowBand(g, x, ry, lw, rowH - 1, i % 2 == 1, over && slot != selected, slot == selected);
			CopperStyle.icon(g, "buildings", x + 5, ry + 3, 16);
			int nameColor = locked ? CopperStyle.TUFF : CopperStyle.QUARTZ;
			// the status at the right: a glyph and a word in its color
			String glyph = switch (state) {
				case MovePage.ROW_YES -> "good";
				case MovePage.ROW_PLANNED -> "clock";
				default -> "lock";
			};
			int color = switch (state) {
				case MovePage.ROW_YES -> CopperStyle.VERDIGRIS;
				case MovePage.ROW_PLANNED -> CopperStyle.LAPIS;
				case MovePage.ROW_WAIT -> CopperStyle.LANTERN;
				default -> CopperStyle.TUFF;
			};
			String word = Component.translatable("screen.aliveworkplace.move.state." + switch (state) {
				case MovePage.ROW_YES -> "yes";
				case MovePage.ROW_PLANNED -> "planned";
				case MovePage.ROW_WAIT -> "wait";
				default -> "no";
			}).getString();
			int ww = font.width(word);
			int wx = x + lw - 4 - ww;
			g.drawString(font, word, wx, ry + 7, color, false);
			CopperStyle.glyph(g, glyph, wx - 11, ry + 7);
			int textW = wx - 14 - (x + 25);
			g.drawString(font, CopperStyle.trim(font, icon.getHoverName().getString(), textW), x + 25, ry + 2, nameColor, !locked);
			// the line under it: why it can't move, else its size and where it stands
			String sub;
			if (locked) {
				sub = moveWhy(row).getString();
			} else {
				List<Component> lore = loreOf(icon);
				String size = row.contains("w") ? Component.translatable("screen.aliveworkplace.move.row.size", row.getInt("w"), row.getInt("d")).getString() : "";
				String where = lore.isEmpty() ? "" : arg0(lore.get(0)).getString();
				sub = size.isEmpty() ? where : where.isEmpty() ? size : size + " · " + where;
			}
			g.drawString(font, CopperStyle.trim(font, sub, textW), x + 25, ry + 12, locked ? CopperStyle.OFF_TEXT : CopperStyle.TUFF, false);
			moveRows.add(new int[]{slot, x, Math.max(ry, top), lw, Math.min(ry + rowH, top + listH) - Math.max(ry, top)});
			if (over) {
				boardWhisper = locked ? Component.translatable("screen.aliveworkplace.move.whisper.locked", icon.getHoverName(), moveWhy(row))
					: Component.translatable("screen.aliveworkplace.move.whisper.row", icon.getHoverName());
			}
		}
		g.disableScissor();
		CopperStyle.scrollbar(g, x + lw - 4, top, listH, moveListScroll, Math.max(0, content - listH), content, false);
		// What happens (or why it can't move) for the row picked
		CompoundTag picked = selected < 0 ? null : moveRow(menu.slots.get(selected).getItem());
		if (sideW > 0) {
			int sx = x + lw + 8;
			if (picked == null) {
				CopperStyle.whatHappens(g, font, Component.translatable("screen.aliveworkplace.move.what.none"), sx, y, sideW);
			} else if (!movable(picked)) {
				CopperStyle.refusal(g, font, Component.translatable("screen.aliveworkplace.move.why_heading", menu.slots.get(selected).getItem().getHoverName()),
					moveWhy(picked), sx, y, sideW);
			} else {
				Component name = menu.slots.get(selected).getItem().getHoverName();
				int bh = CopperStyle.whatHappens(g, font, Component.translatable("screen.aliveworkplace.move.what.list", name), sx, y, sideW);
				List<Component> lore = loreOf(menu.slots.get(selected).getItem());
				int ly = y + bh + 6;
				for (Component line : lore) {
					if (hasKey(List.of(line), "screen.aliveworkplace.move.click")) {
						continue;
					}
					for (FormattedCharSequence part : font.split(line, sideW - 4)) {
						if (ly + 9 > footY - 4) {
							break;
						}
						g.drawString(font, part, sx + 2, ly, CopperStyle.TUFF, false);
						ly += 10;
					}
				}
			}
		}
		// Cancel, and the one copper button: Next
		Component cancel = Component.translatable("screen.aliveworkplace.move.cancel");
		moveButton(g, HIT_LIST_CANCEL, cancel, null, CopperStyle.Kind.NEUTRAL, false, x, footY, buttonW(cancel, null), mouseX, mouseY);
		Component next = Component.translatable("screen.aliveworkplace.move.next.place");
		int nw = Math.min(w / 2, buttonW(next, "more"));
		moveButton(g, HIT_LIST_NEXT, next, "more", CopperStyle.Kind.COPPER, !movable(picked), x + w - nw, footY, nw, mouseX, mouseY);
	}

	// Steps 2 and 3: the picker.

	private void renderMoveMap(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		moveHits.clear();
		CompoundTag view = moveView();
		if (moveShownStep() == 3) {
			renderMoveConfirm(g, x, y, w, h, mouseX, mouseY, view);
			return;
		}
		boardWhisper = Component.translatable("screen.aliveworkplace.move.whisper.place");
		int footY = y + h - 15;
		boolean small = compactRail();
		int sideW = small ? Math.max(80, w * 40 / 100) : Math.max(118, w * 33 / 100);
		int s = Math.max(48, Math.min(footY - 4 - y, w - sideW - 8));
		List<Component> tip = null;
		if (view != null && !view.isEmpty()) {
			tip = drawMoveMap(g, view, x, y, s, mouseX, mouseY, true);
		}
		int fit = view == null ? 1 : view.getInt("fit");
		// the fit: green "Fits here", red "Refused here" with why, or "Pick a spot"
		int sx = x + s + 8;
		int sw = x + w - sx;
		int sy = y;
		ItemStack verdict = menu.slots.get(MovePage.VERDICT).getItem();
		Component line = verdict.isEmpty() ? Component.empty() : verdict.getHoverName();
		if (fit == 0) {
			sy += CopperStyle.noteBox(g, font, Component.translatable("screen.aliveworkplace.move.fits_here"), line, "good", CopperStyle.VERDIGRIS,
				CopperStyle.VERDIGRIS, sx, sy, sw);
		} else if (fit == 2) {
			sy += CopperStyle.refusal(g, font, Component.translatable("screen.aliveworkplace.move.refused_here"), line, sx, sy, sw);
		} else {
			ItemStack guide = menu.slots.get(MovePage.GUIDE).getItem();
			sy += CopperStyle.whatHappens(g, font, Component.literal(guide.getHoverName().getString()), sx, sy, sw);
		}
		sy += 5;
		// Rotate, its key, and Call off when a move is planned
		Component rotate = Component.translatable("screen.aliveworkplace.move.rotate");
		int rw = Math.min(sw - 18, buttonW(rotate, "refresh"));
		moveButton(g, MovePage.ROTATE, rotate, "refresh", CopperStyle.Kind.NEUTRAL, false, sx, sy, rw, mouseX, mouseY);
		CopperStyle.chip(g, font, "R", sx + rw + 4, sy + 1, CopperStyle.QUARTZ);
		sy += 18;
		if (!menu.slots.get(MovePage.CALL_OFF).getItem().isEmpty()) {
			Component off = Component.translatable("screen.aliveworkplace.move.call_off");
			moveButton(g, MovePage.CALL_OFF, off, "bad", CopperStyle.Kind.DANGER, false, sx, sy, Math.min(sw, buttonW(off, "bad")), mouseX, mouseY);
			sy += 18;
		}
		// the preview key
		if (sy + 46 < footY) {
			sy += 2;
			listHeading(g, Component.translatable("screen.aliveworkplace.move.key"), new int[]{sx, sy, sw, 10});
			sy += 13;
			String[] keys = {"fits", "refused", "now"};
			for (String key : keys) {
				int[] r = {sx, sy, sx + 10, sy + 10};
				switch (key) {
					case "fits" -> outlined(g, r, 0xA02B6E5C, CopperStyle.VERDIGRIS);
					case "refused" -> hatched(g, r, CopperStyle.REDSTONE);
					default -> dashed(g, r, CopperStyle.QUARTZ);
				}
				int ly = sy + 1;
				for (FormattedCharSequence part : font.split(Component.translatable("screen.aliveworkplace.move.key." + key), sw - 14)) {
					g.drawString(font, part, sx + 14, ly, CopperStyle.QUARTZ, false);
					ly += 10;
				}
				sy = Math.max(sy + 13, ly + 3);
				if (sy + 10 > footY) {
					break;
				}
			}
		}
		// Back to the list, and the one copper button: Next to confirm
		Component back = Component.translatable("screen.aliveworkplace.move.back_short");
		moveButton(g, MovePage.CANCEL, back, null, CopperStyle.Kind.NEUTRAL, false, x, footY, buttonW(back, null), mouseX, mouseY);
		Component next = Component.translatable("screen.aliveworkplace.move.next.confirm");
		int nw = Math.min(sw, buttonW(next, "more"));
		moveButton(g, HIT_TO_CONFIRM, next, "more", CopperStyle.Kind.COPPER, fit != 0, x + w - nw, footY, nw, mouseX, mouseY);
		if (tip != null) {
			g.renderComponentTooltip(font, tip, mouseX, mouseY);
		}
	}

	private void renderMoveConfirm(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, CompoundTag view) {
		boardWhisper = Component.translatable("screen.aliveworkplace.move.whisper.confirm");
		boolean small = compactRail();
		int footY = y + h - 15;
		int sideW = small ? 0 : Math.max(96, w * 34 / 100);
		int lw = w - (sideW > 0 ? sideW + 8 : 0);
		Component name = moveName();
		// the question, at twice the size when it fits
		String q = Component.translatable("screen.aliveworkplace.move.question", name).getString();
		int ty = y;
		if (font.width(q) * 2 <= lw) {
			big(g, q, x, ty, CopperStyle.QUARTZ);
			ty += 20;
		} else {
			g.drawString(font, CopperStyle.trim(font, q, lw), x, ty + 2, CopperStyle.QUARTZ, true);
			ty += 14;
		}
		// From, To, Takes and Faces
		List<Component> guide = loreOf(menu.slots.get(MovePage.GUIDE).getItem());
		List<Component[]> facts = new ArrayList<>();
		if (guide.size() > 3) {
			facts.add(new Component[]{Component.translatable("screen.aliveworkplace.move.from"), arg0(guide.get(0))});
			facts.add(new Component[]{Component.translatable("screen.aliveworkplace.move.to"), arg0(guide.get(1))});
			facts.add(new Component[]{Component.translatable("screen.aliveworkplace.move.takes"), Component.translatable("screen.aliveworkplace.move.takes_value", arg0(guide.get(3)))});
		}
		facts.add(new Component[]{Component.translatable("screen.aliveworkplace.move.faces_label"),
			Component.translatable("screen.aliveworkplace.move.direction." + moveFacing(view))});
		int labelW = 0;
		for (Component[] f : facts) {
			labelW = Math.max(labelW, font.width(f[0]));
		}
		// each value wraps under itself (at most two lines) rather than being cut
		int valueW = lw - labelW - 18;
		int factsH = 6;
		for (Component[] f : facts) {
			factsH += Math.min(2, font.split(f[1], valueW).size()) * 10 + 1;
		}
		CopperStyle.well(g, x, ty, lw, factsH);
		int fy = ty + 4;
		for (int i = 0; i < facts.size(); i++) {
			Component[] f = facts.get(i);
			g.drawString(font, f[0], x + 5, fy, CopperStyle.TUFF, false);
			List<FormattedCharSequence> parts = font.split(f[1], valueW);
			for (int p = 0; p < Math.min(2, parts.size()); p++) {
				g.drawString(font, parts.get(p), x + labelW + 12, fy, CopperStyle.QUARTZ, i == 1);
				fy += 10;
			}
			fy += 1;
		}
		ty += factsH + 5;
		// What happens
		if (ty + 30 < footY) {
			Component plan = Component.translatable("screen.aliveworkplace.move.plan");
			g.enableScissor(x, ty, x + lw, footY - 3);
			CopperStyle.whatHappens(g, font, plan, x, ty, lw);
			g.disableScissor();
		}
		// a small map with From and To, and the checklist
		List<Component> tip = null;
		List<Component> confirmLore = loreOf(menu.slots.get(MovePage.CONFIRM).getItem());
		boolean ready = menu.slots.get(MovePage.CONFIRM).getItem().is(net.minecraft.world.item.Items.PISTON);
		if (sideW > 0) {
			int sx = x + lw + 8;
			int ms = Math.max(48, Math.min(sideW, (footY - y) - 44));
			if (view != null && !view.isEmpty()) {
				tip = drawMoveMap(g, view, sx, y, ms, mouseX, mouseY, false);
			}
			int cy = y + ms + 5;
			boolean mover = !hasKey(confirmLore, "screen.aliveworkplace.move.needs_mover");
			boolean idle = !hasKey(confirmLore, "message.aliveworkplace.move.under_way") && !hasKey(confirmLore, "message.aliveworkplace.move.putting_back");
			boolean[] ok = {view != null && view.getInt("fit") == 0, mover, idle};
			String[] keys = {"spot", "mover", "idle"};
			for (int i = 0; i < 3; i++) {
				if (cy + 9 > footY - 3) {
					break;
				}
				CopperStyle.glyph(g, ok[i] ? "good" : "bad", sx, cy);
				for (FormattedCharSequence part : font.split(Component.translatable("screen.aliveworkplace.move.check." + keys[i] + (ok[i] ? "" : "_no")), sideW - 12)) {
					if (cy + 9 > footY - 3) {
						break;
					}
					g.drawString(font, part, sx + 11, cy, ok[i] ? CopperStyle.VERDIGRIS : CopperStyle.REDSTONE, false);
					cy += 10;
				}
				cy += 2;
			}
		}
		// Back, Cancel, and the one copper button: Move building
		Component back = Component.translatable("screen.aliveworkplace.move.back_short");
		moveButton(g, HIT_TO_PLACE, back, null, CopperStyle.Kind.NEUTRAL, false, x, footY, buttonW(back, null), mouseX, mouseY);
		Component go = Component.translatable("screen.aliveworkplace.move.do");
		int gw = Math.min(w / 2, buttonW(go, "good"));
		moveButton(g, MovePage.CONFIRM, go, "good", CopperStyle.Kind.COPPER, !ready, x + w - gw, footY, gw, mouseX, mouseY);
		Component cancel = Component.translatable("screen.aliveworkplace.move.cancel");
		int cw = buttonW(cancel, null);
		moveButton(g, MovePage.CANCEL, cancel, null, CopperStyle.Kind.NEUTRAL, false, x + w - gw - 4 - cw, footY, cw, mouseX, mouseY);
		if (tip != null) {
			g.renderComponentTooltip(font, tip, mouseX, mouseY);
		}
	}

	/**
	 * The village map at ({@code x}, {@code y}), {@code s} a side: the land, every other building, the building now in a
	 * dashed frame, the new spot (green where it fits, red hatched where it's refused, its front in Lantern) and the Hall's
	 * bell; with {@code clicks} a click puts the spot there. The labels: Now here / Fits / Refused (From / To without
	 * clicks, the confirm's small map). Returns the tooltip under the mouse, or null.
	 */
	private List<Component> drawMoveMap(GuiGraphics g, CompoundTag view, int x, int y, int s, int mouseX, int mouseY, boolean clicks) {
		moveMapX = x;
		moveMapY = y;
		moveMapS = s;
		moveTexture(view);
		moveFit(view);
		g.fill(x - 1, y - 1, x + s + 1, y + s + 1, CopperStyle.VOID);
		g.blit(MOVE_MAP, x, y, s, s, moveOriginX, moveOriginZ, moveWindow, moveWindow, MovePage.MAP, MovePage.MAP);
		g.enableScissor(x, y, x + s, y + s);
		int[] others = view.getIntArray("others");
		List<Component> names = loreOf(menu.slots.get(MovePage.VIEW).getItem());
		List<int[]> boxes = new ArrayList<>();
		for (int i = 0; i + 3 < others.length; i += 4) {
			int[] r = moveRect(view, others[i], others[i + 1], others[i + 2], others[i + 3]);
			boxes.add(r);
			outlined(g, r, 0xE0000000 | (MOVE_BUILDING & 0xFFFFFF), 0xFF2A140A);
		}
		int[] old = view.getIntArray("old");
		int[] spot = view.getIntArray("spot");
		int fit = view.getInt("fit");
		int[] oldR = old.length == 4 ? moveRect(view, old[0], old[1], old[2], old[3]) : null;
		if (oldR != null) {
			outlined(g, oldR, 0xC0000000 | (MOVE_BUILDING & 0xFFFFFF), 0xFF2A140A);
			dashed(g, new int[]{oldR[0] - 1, oldR[1] - 1, oldR[2] + 1, oldR[3] + 1}, CopperStyle.QUARTZ);
		}
		int[] spotR = spot.length == 4 && fit != 1 ? moveRect(view, spot[0], spot[1], spot[2], spot[3]) : null;
		if (spotR != null) {
			if (fit == 0) {
				outlined(g, spotR, 0xA02B6E5C, CopperStyle.VERDIGRIS);
				if (spotR[2] - spotR[0] >= 12 && spotR[3] - spotR[1] >= 12) {
					CopperStyle.glyph(g, "good", (spotR[0] + spotR[2]) / 2 - 4, (spotR[1] + spotR[3]) / 2 - 4);
				}
			} else {
				hatched(g, spotR, CopperStyle.REDSTONE);
			}
			// its front edge, in Lantern
			switch (net.minecraft.core.Direction.from2DDataValue(view.getInt("front"))) {
				case NORTH -> g.fill(spotR[0], spotR[1], spotR[2], spotR[1] + 2, CopperStyle.LANTERN);
				case SOUTH -> g.fill(spotR[0], spotR[3] - 2, spotR[2], spotR[3], CopperStyle.LANTERN);
				case WEST -> g.fill(spotR[0], spotR[1], spotR[0] + 2, spotR[3], CopperStyle.LANTERN);
				default -> g.fill(spotR[2] - 2, spotR[1], spotR[2], spotR[3], CopperStyle.LANTERN);
			}
		}
		// the Hall's bell, at three quarters' size
		int[] hall = moveRect(view, view.getInt("hallX"), view.getInt("hallZ"), view.getInt("hallX"), view.getInt("hallZ"));
		int bx = (hall[0] + hall[2]) / 2 - 6;
		int by = (hall[1] + hall[3]) / 2 - 6;
		g.pose().pushPose();
		g.pose().translate(bx, by, 0);
		g.pose().scale(0.75f, 0.75f, 1f);
		g.renderItem(new ItemStack(net.minecraft.world.item.Items.BELL), 0, 0);
		g.pose().popPose();
		List<int[]> taken = new ArrayList<>();
		int[] bell = {bx, by, bx + 12, by + 12};
		taken.add(bell);
		taken.addAll(boxes);
		if (oldR != null) {
			taken.add(oldR);
		}
		if (spotR != null) {
			taken.add(spotR);
		}
		if (spotR != null) {
			mapLabel(g, Component.translatable(clicks ? (fit == 0 ? "screen.aliveworkplace.move.label.fits" : "screen.aliveworkplace.move.label.refused")
				: "screen.aliveworkplace.move.to").getString(), spotR, false, fit == 0 ? CopperStyle.VERDIGRIS : CopperStyle.REDSTONE, taken);
		}
		if (oldR != null) {
			mapLabel(g, Component.translatable(clicks ? "screen.aliveworkplace.move.label.now" : "screen.aliveworkplace.move.from").getString(), oldR, false,
				CopperStyle.QUARTZ, taken);
		}
		if (clicks) {
			mapLabel(g, Component.translatable("screen.aliveworkplace.move.legend.hall").getString(), bell, false, CopperStyle.QUARTZ, taken, true);
			for (int i = 0; i < boxes.size() && i < names.size(); i++) {
				mapLabel(g, names.get(i).getString(), boxes.get(i), true, CopperStyle.TUFF, taken, true);
			}
		}
		g.disableScissor();
		if (clicks) {
			// the scale: a bar 16 blocks long
			int step = Math.max(1, view.getInt("step"));
			int bar = (int) Math.round(16.0 * s / (moveWindow * step));
			String scale = Component.translatable("screen.aliveworkplace.hall.map.scale", 16).getString();
			if (bar >= 4 && bar + font.width(scale) + 10 < s) {
				g.fill(x + 2, y + s - 13, x + bar + font.width(scale) + 10, y + s - 2, 0xC0000000);
				g.fill(x + 4, y + s - 8, x + 4 + bar, y + s - 6, CopperStyle.QUARTZ);
				g.drawString(font, scale, x + bar + 7, y + s - 11, CopperStyle.QUARTZ, false);
			}
			moveHits.add(new int[]{HIT_MAP, x, y, s, s});
			if (mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s) {
				return mapTip(view, names, mouseX, mouseY);
			}
		}
		return null;
	}

	/** A refused spot: a Redstone frame over red, hatched with diagonal lines every 4 pixels. */
	private static void hatched(GuiGraphics g, int[] r, int color) {
		g.fill(r[0], r[1], r[2], r[3], 0x50000000 | (color & 0xFFFFFF));
		for (int py = r[1] + 1; py < r[3] - 1; py++) {
			for (int px = r[0] + 1; px < r[2] - 1; px++) {
				if (((px - r[0]) + (py - r[1])) % 4 == 0) {
					g.fill(px, py, px + 1, py + 1, 0xC0000000 | (color & 0xFFFFFF));
				}
			}
		}
		g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], color);
	}

	/** Where the building stands now: a dashed frame (2 on, 2 off). */
	private static void dashed(GuiGraphics g, int[] r, int color) {
		for (int i = r[0]; i < r[2]; i += 4) {
			g.fill(i, r[1], Math.min(i + 2, r[2]), r[1] + 1, color);
			g.fill(i, r[3] - 1, Math.min(i + 2, r[2]), r[3], color);
		}
		for (int j = r[1]; j < r[3]; j += 4) {
			g.fill(r[0], j, r[0] + 1, Math.min(j + 2, r[3]), color);
			g.fill(r[2] - 1, j, r[2], Math.min(j + 2, r[3]), color);
		}
	}

	/** Picks step 1's row {@code slot} as a click on it does (the screenshot harness); false until the list shows it. */
	public boolean moveSelect(int slot) {
		if (!moveList() || !moveRowSlots().contains(slot)) {
			return false;
		}
		moveSelected = slot;
		return true;
	}

	/** Next on step 2: step 3 shows once the spot fits (the screenshot harness too); true when it did. */
	public boolean moveNext() {
		CompoundTag view = moveView();
		if (moveMap() && view != null && view.getInt("fit") == 0) {
			moveConfirming = true;
			return true;
		}
		return false;
	}

	/** Which step the Move page shows (1 to 3), or 0 when it isn't open (the screenshot harness). */
	public int moveStepShown() {
		return moveMode() ? moveShownStep() : 0;
	}

	/**
	 * The map's window: by default fitted and centred on what the village has (the Hall, every building, where it stands
	 * now and the new spot) with a margin, so the buildings are big enough to read; the whole map when zoomed out.
	 */
	private void moveFit(CompoundTag view) {
		if (moveZoom == MovePage.MAP) {
			moveWindow = MovePage.MAP;
			moveOriginX = 0;
			moveOriginZ = 0;
			return;
		}
		int step = Math.max(1, view.getInt("step"));
		int ox = view.getInt("ox");
		int oz = view.getInt("oz");
		int hx = (view.getInt("hallX") - ox) / step;
		int hz = (view.getInt("hallZ") - oz) / step;
		int[] b = {hx - 3, hz - 3, hx + 3, hz + 3};
		java.util.List<int[]> boxes = new ArrayList<>();
		boxes.add(view.getIntArray("old"));
		if (view.getInt("fit") != 1) {
			boxes.add(view.getIntArray("spot"));
		}
		int[] others = view.getIntArray("others");
		for (int i = 0; i + 3 < others.length; i += 4) {
			boxes.add(new int[]{others[i], others[i + 1], others[i + 2], others[i + 3]});
		}
		for (int[] box : boxes) {
			if (box.length == 4) {
				b[0] = Math.min(b[0], Math.floorDiv(box[0] - ox, step));
				b[1] = Math.min(b[1], Math.floorDiv(box[1] - oz, step));
				b[2] = Math.max(b[2], Math.floorDiv(box[2] - ox, step) + 1);
				b[3] = Math.max(b[3], Math.floorDiv(box[3] - oz, step) + 1);
			}
		}
		int extent = Math.max(b[2] - b[0], b[3] - b[1]);
		int window = Math.max(24, Math.min(MovePage.MAP, extent + Math.max(8, extent / 4) * 2));
		moveWindow = window;
		moveOriginX = Math.max(0, Math.min(MovePage.MAP - window, (b[0] + b[2]) / 2 - window / 2));
		moveOriginZ = Math.max(0, Math.min(MovePage.MAP - window, (b[1] + b[3]) / 2 - window / 2));
	}

	/** Blocks (inclusive corners) to a rectangle on the map: [x0, y0, x1, y1], at least 2 pixels a side. */
	private int[] moveRect(CompoundTag view, int minX, int minZ, int maxX, int maxZ) {
		int step = Math.max(1, view.getInt("step"));
		double scale = moveMapS / (double) (moveWindow * step);
		int ox = view.getInt("ox") + moveOriginX * step;
		int oz = view.getInt("oz") + moveOriginZ * step;
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

	/**
	 * A name on the map at two thirds' size on a dark backing: inside {@code box} if it fits there (and {@code inside} allows),
	 * else above it, else below, else beside it, wherever it covers nothing in {@code taken}; then it is taken too.
	 */
	private void mapLabel(GuiGraphics g, String text, int[] box, boolean inside, int color, List<int[]> taken) {
		mapLabel(g, text, box, inside, color, taken, false);
	}

	/** {@link #mapLabel}; with {@code skipCrowded} a name with no clear spot isn't drawn (the Hall's Map, 47.2). */
	private void mapLabel(GuiGraphics g, String text, int[] box, boolean inside, int color, List<int[]> taken, boolean skipCrowded) {
		int w = (int) Math.ceil(font.width(text) * 0.67f) + 2;
		int h = 7;
		int cx = (box[0] + box[2]) / 2 - w / 2;
		List<int[]> tries = new ArrayList<>();
		if (inside && box[2] - box[0] >= w + 2 && box[3] - box[1] >= h + 2) {
			tries.add(new int[]{cx, (box[1] + box[3]) / 2 - h / 2, 1});
		}
		tries.add(new int[]{cx, box[1] - h - 1, 0});
		tries.add(new int[]{cx, box[3] + 1, 0});
		tries.add(new int[]{box[2] + 1, (box[1] + box[3]) / 2 - h / 2, 0});
		tries.add(new int[]{box[0] - w - 1, (box[1] + box[3]) / 2 - h / 2, 0});
		int[] at = tries.get(0);
		boolean found = false;
		for (int[] t : tries) {
			int[] r = {t[0], t[1], t[0] + w, t[1] + h};
			boolean clear = r[0] >= moveMapX && r[1] >= moveMapY && r[2] <= moveMapX + moveMapS && r[3] <= moveMapY + moveMapS;
			for (int[] o : taken) {
				boolean own = o == box && t[2] == 1;
				if (clear && !own && r[0] < o[2] && r[2] > o[0] && r[1] < o[3] && r[3] > o[1]) {
					clear = false;
				}
			}
			if (clear) {
				at = t;
				found = true;
				break;
			}
		}
		if (!found && skipCrowded) {
			return;
		}
		g.fill(at[0], at[1], at[0] + w, at[1] + h, 0xB0000000);
		g.pose().pushPose();
		g.pose().translate(at[0] + 1, at[1] + 1, 200);
		g.pose().scale(0.67f, 0.67f, 1f);
		g.drawString(font, text, 0, 0, color, false);
		g.pose().popPose();
		taken.add(new int[]{at[0], at[1], at[0] + w, at[1] + h});
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
		int px = Math.max(0, Math.min(MovePage.MAP - 1, moveOriginX + (int) ((mouseX - moveMapX) * moveWindow / moveMapS)));
		int pz = Math.max(0, Math.min(MovePage.MAP - 1, moveOriginZ + (int) ((mouseY - moveMapY) * moveWindow / moveMapS)));
		int step = Math.max(1, view.getInt("step"));
		return new int[]{view.getInt("ox") + px * step + step / 2, view.getInt("oz") + pz * step + step / 2, px, pz};
	}

	private boolean moveMapClicked(double mx, double my, int button) {
		for (int[] hit : moveHits) {
			if (mx >= hit[1] && mx < hit[1] + hit[3] && my >= hit[2] && my < hit[2] + hit[4]) {
				click();
				moveHit(hit[0], mx, my, button);
				return true;
			}
		}
		for (int[] row : moveRows) {
			if (mx >= row[1] && mx < row[1] + row[3] && my >= row[2] && my < row[2] + row[4]) {
				click();
				moveSelected = row[0]; // picked; Next (or Enter) opens it
				return true;
			}
		}
		return false;
	}

	private void moveHit(int hit, double mx, double my, int button) {
		switch (hit) {
			case HIT_MAP -> {
				int[] at = moveBlockAt(moveView(), mx, my);
				menuButton(MovePage.MAP_CLICK + at[3] * MovePage.MAP + at[2]);
			}
			case HIT_TO_CONFIRM -> moveNext();
			case HIT_TO_PLACE -> moveConfirming = false;
			case HIT_LIST_NEXT -> {
				int slot = moveSelectedSlot();
				if (slot >= 0) {
					press(slot, 0, false);
				}
			}
			case HIT_LIST_CANCEL -> open(Section.BUILDINGS);
			default -> press(hit, button, false);
		}
	}

	/** Where on the screen the map shows block ({@code x}, {@code z}) (the screenshot harness points there), or null off the map. */
	public int[] moveMapPoint(int x, int z) {
		CompoundTag view = moveView();
		if (view == null || view.isEmpty() || moveMapS == 0) {
			return null;
		}
		int step = Math.max(1, view.getInt("step"));
		double scale = moveMapS / (double) (moveWindow * step);
		return new int[]{moveMapX + (int) ((x - view.getInt("ox") - moveOriginX * step + 0.5) * scale),
			moveMapY + (int) ((z - view.getInt("oz") - moveOriginZ * step + 0.5) * scale)};
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
		boardWhisper = null;
		if (chronicle()) {
			chronicleView.render(g, font, menu, new int[]{x, y, w, h}, mouseX, mouseY, compactRail());
			boardWhisper = chronicleView.whisper();
			return;
		}
		if (!moveMap()) {
			moveConfirming = false;
		}
		if (moveMap()) {
			renderMoveMap(g, x, y, w, h, mouseX, mouseY);
			return;
		}
		if (moveList()) {
			renderMoveList(g, x, y, w, h, mouseX, mouseY);
			return;
		}
		moveSelected = -1;
		Section board = board();
		if (board == Section.OVERVIEW) {
			renderOverviewBoard(g, x, y, w, h, mouseX, mouseY);
			return;
		}
		if (board == Section.MAP) {
			renderHallMap(g, x, y, w, h, mouseX, mouseY);
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
		// the counts as chips (Copperline, 47.2): everyone, working, without a job, children; the full line in the whisper
		int cx = x;
		int[] counts = {menu.state(HallSections.PEOPLE_TOTAL), menu.state(HallSections.PEOPLE_WORKING), menu.state(HallSections.PEOPLE_JOBLESS),
			menu.state(HallSections.PEOPLE_CHILDREN)};
		String[] chipKeys = {"all", "working", "jobless", "children"};
		int[] chipColors = {CopperStyle.QUARTZ, CopperStyle.VERDIGRIS, counts[2] > 0 ? CopperStyle.LANTERN : CopperStyle.TUFF, CopperStyle.TUFF};
		for (int c = 0; c < 4; c++) {
			if (c == 3 && counts[3] == 0) {
				break;
			}
			String chip = Component.translatable("screen.aliveworkplace.hall.people.chip." + chipKeys[c], counts[c]).getString();
			if (cx + font.width(chip) + 6 > x + w) {
				break;
			}
			cx += CopperStyle.chip(g, font, chip, cx, ly, chipColors[c]) + 3;
		}
		if (mouseX >= x && mouseX < cx && mouseY >= ly && mouseY < ly + 11) {
			boardWhisper = HallPeople.header(counts[0], counts[1], counts[2], counts[3]);
		}
		ly += 14;
		g.drawString(font, TableStyle.trim(font, Component.translatable("screen.aliveworkplace.hall.people.hint"), w), x, ly, TableStyle.FAINT, false);
		ly += 12;
		int[] cols = {x + 4, x + 4 + w * 34 / 100, x + 4 + w * 62 / 100, x + 4 + w * 78 / 100};
		int[] colW = {cols[1] - cols[0] - 4, cols[2] - cols[1] - 4, cols[3] - cols[2] - 4, x + w - 8 - cols[3]};
		String[] heads = {"name", "job", "level", "mood"};
		for (int c = 0; c < 4; c++) {
			g.drawString(font, TableStyle.trim(font, Component.literal(Component.translatable("screen.aliveworkplace.hall.people.col." + heads[c]).getString()
				.toUpperCase(java.util.Locale.ROOT)), colW[c]), cols[c], ly, CopperStyle.TUFF, false);
		}
		CopperStyle.headerRow(g, x, ly - 1, w);
		ly += 12;
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
				g.drawString(font, "...", cols[0], top + 3, TableStyle.LOADING, false);
				continue;
			}
			int slot = people.get(k);
			ItemStack icon = menu.slots.get(slot).getItem();
			CompoundTag row = HallPeople.row(icon);
			boolean open = i == openAt;
			boolean over = mouseX >= x && mouseX < x + w - 6 && mouseY >= Math.max(top, tableTop) && mouseY < top + RH && mouseY < tableTop + tableH;
			if (open) {
				g.fill(x + 1, top, x + w - 7, top + RH + openH - 1, CopperStyle.DEEP_800);
				CopperStyle.rowBand(g, x + 1, top, w - 8, RH, false, false, true);
			} else {
				CopperStyle.rowBand(g, x + 1, top, w - 8, RH, i % 2 == 1, over, false);
			}
			hits.add(new int[]{0, slot, x, top, w - 6, RH});
			String kind = row.getString("kind");
			g.drawString(font, TableStyle.trim(font, icon.getHoverName(), colW[0]), cols[0], top + 3, TableStyle.NAME, open);
			Component job = HallPeople.job(row); // "Undertaker (no workstation)" for a worker without one (B1173)
			int jobColour = kind.equals("builder") ? TableStyle.TITLE : kind.equals("child") || kind.equals("jobless") || kind.equals("nitwit") || row.getBoolean("no_site")
				? TableStyle.SUBLINE : TableStyle.BODY;
			if (kind.equals("jobless")) { // no job: the warning glyph and the word in Lantern
				CopperStyle.glyph(g, "warning", cols[1], top + 3);
				g.drawString(font, TableStyle.trim(font, job, colW[1] - 10), cols[1] + 10, top + 3, CopperStyle.LANTERN, false);
			} else {
				g.drawString(font, TableStyle.trim(font, job, colW[1]), cols[1], top + 3, jobColour, false);
			}
			g.drawString(font, TableStyle.trim(font, HallPeople.level(row.getInt("level")), colW[2]), cols[2], top + 3, TableStyle.BODY, false);
			HallPeople.MoodWord mood = moodOf(row);
			int moodColour = switch (mood) {
				case HAPPY -> MOOD_HAPPY;
				case UNHAPPY -> MOOD_UNHAPPY;
				case NO_BED -> MOOD_NO_BED;
				default -> TableStyle.BODY;
			};
			String moodGlyph = switch (mood) {
				case HAPPY -> "happy";
				case UNHAPPY -> "unhappy";
				case NO_BED -> "bed";
				case CONTENT -> "content";
				default -> null;
			};
			if (moodGlyph != null) {
				CopperStyle.glyph(g, moodGlyph, cols[3], top + 3);
			}
			g.drawString(font, TableStyle.trim(font, mood.word(), colW[3] - 10), cols[3] + 10, top + 3, moodColour, false);
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
		// the row's main action is copper (Copperline's one copper button): Bed for a villager without one, else Job for one without a job
		CompoundTag row = slot >= 0 && slot < ChoiceMenu.SIZE ? HallPeople.row(menu.slots.get(slot).getItem()) : null;
		boolean main = row != null && on && (kind == 3 && row.getString("mood").equals("no_bed")
			|| kind == 2 && !row.getString("mood").equals("no_bed") && row.getString("job_action").equals("assign"));
		CopperStyle.button(g, font, text, null, x, y, bw, BH, main ? CopperStyle.Kind.COPPER : CopperStyle.Kind.NEUTRAL, over, false, !on);
		if (on) {
			hits.add(new int[]{kind, slot, x, y, bw, BH});
		}
		return x + bw + 4;
	}

	@Override
	protected boolean bodyClicked(double mx, double my, int button) {
		if (chronicle()) {
			return chronicleView.clicked(mx, my, button);
		}
		if (moveMode()) {
			return moveMapClicked(mx, my, button);
		}
		Section board = board();
		if (board == Section.OVERVIEW || board == Section.MAP) {
			return boardClicked(mx, my, button);
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
		if (chronicle()) {
			chronicleView.scrolled(scrollY);
			return true;
		}
		if (board() == Section.MAP) {
			if (mx >= hallMapBox[0] && mx < hallMapBox[0] + hallMapBox[2] && my >= hallMapBox[1] && my < hallMapBox[1] + hallMapBox[3]) {
				hallZoomed = scrollY > 0; // the wheel zooms in on the Hall, and back out to the whole village
			}
			return true;
		}
		if (board() == Section.OVERVIEW) {
			return true;
		}
		if (moveList()) {
			moveListScroll = Math.max(0, moveListScroll - (int) Math.round(scrollY * 22));
			return true;
		}
		if (moveMap()) {
			if (mx >= moveMapX && mx < moveMapX + moveMapS && my >= moveMapY && my < moveMapY + moveMapS) {
				moveZoom = scrollY > 0 ? 0 : MovePage.MAP; // the wheel zooms out to the whole map, and back in to the village
			}
			return true;
		}
		tableScroll = Math.max(0, tableScroll - (int) Math.round(scrollY * RH * 2));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (moveMode() && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
			int step = moveShownStep(); // Enter: Next, or Move building (47.3)
			int picked = moveSelectedSlot();
			if (step == 1 && picked >= 0 && movable(moveRow(menu.slots.get(picked).getItem()))) {
				press(picked, 0, false);
			} else if (step == 2) {
				moveNext();
			} else if (step == 3 && menu.slots.get(MovePage.CONFIRM).getItem().is(net.minecraft.world.item.Items.PISTON)) {
				press(MovePage.CONFIRM, 0, false);
			}
			return true;
		}
		if (moveMap() && moveShownStep() == 2 && key == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
			press(MovePage.ROTATE, 0, false); // R: a quarter turn
			return true;
		}
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
