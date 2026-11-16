package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperSlots;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Copperline round a screen with real item slots (ROADMAP 44.1): the copper plaque with the screen's name, the lintel
 * (a count on its right, the close X), the section headings over the slot rows, every slot an 18x18 well (Lantern
 * edged under the mouse), a side panel on the right and the whisper line saying what the hovered slot is for. The slots
 * stay where the menu puts them; {@link CopperSlots} places everything round them.
 */
public abstract class CopperSlotScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
	/** A section heading over slot rows, relative to the slot frame: Tuff words and a rule to {@code x + w}. */
	public record Header(Component text, int x, int y, int w) {
	}

	protected final int frameH;
	protected final int gutter;
	protected final int panelW;
	/** How far in the menu's own coordinates the slot frame starts (0, or the trades' {@code CopperTrades.SHIFT}, 44.2). */
	protected final int shift;

	protected CopperSlotScreen(T menu, Inventory inventory, Component title, int frameH, int gutter, int panelW) {
		this(menu, inventory, title, frameH, gutter, panelW, 0);
	}

	protected CopperSlotScreen(T menu, Inventory inventory, Component title, int frameH, int gutter, int panelW, int shift) {
		super(menu, inventory, title);
		this.frameH = frameH;
		this.gutter = gutter;
		this.panelW = panelW;
		this.shift = shift;
		imageWidth = CopperSlots.FRAME_W;
		imageHeight = frameH;
	}

	@Override
	protected void init() {
		super.init();
		int[] origin = CopperSlots.origin(width, height, frameH, gutter, panelW);
		leftPos = origin[0] - shift;
		topPos = origin[1];
	}

	/** The slot frame's left edge, in screen pixels (the menu's {@code leftPos} plus {@link #shift}). */
	protected int frameX() {
		return leftPos + shift;
	}

	/** The window, in screen pixels. */
	public int[] window() {
		return CopperSlots.at(CopperSlots.window(frameH, gutter, panelW), frameX(), topPos);
	}

	/** The side panel, in screen pixels. */
	protected int[] panel() {
		return CopperSlots.at(CopperSlots.panel(frameH, panelW), frameX(), topPos);
	}

	/** The middle of slot {@code index}, in screen pixels (the screenshot harness points at it). */
	public int[] centre(int index) {
		Slot slot = menu.slots.get(Math.max(0, Math.min(index, menu.slots.size() - 1)));
		return new int[]{leftPos + slot.x + 8, topPos + slot.y + 8};
	}

	/** The name on the plaque. */
	protected Component plaqueTitle() {
		return title;
	}

	/** The words on the lintel's right (a count), or empty. */
	protected abstract Component lintelText();

	/** The headings over the slot rows. */
	protected abstract List<Header> headers();

	/** What the whisper line says with the mouse over {@code slot}, or, with {@code slot} null, over nothing. */
	protected abstract Component whisper(Slot slot);

	/** Draws the side panel's content into {@code box} (screen pixels); the panel itself is already drawn. */
	protected abstract void renderPanel(GuiGraphics g, int[] box, int mouseX, int mouseY);

	/** Draws the gutter's labels left of the slots (none by default). */
	protected void renderGutter(GuiGraphics g) {
	}

	/** The whisper line's text with the mouse at {@code mouseX, mouseY}; a panel's part can say what it does. */
	protected Component whisperAt(int mouseX, int mouseY) {
		return whisper(hoveredSlot);
	}

	/** A section heading at {@code x, y} (screen pixels): Tuff words, then a Deepslate 600 rule to {@code x + w}. */
	protected void heading(GuiGraphics g, Component label, int x, int y, int w) {
		String text = CopperStyle.trim(font, label.getString(), w);
		g.drawString(font, text, x, y, CopperStyle.TUFF, false);
		int rule = x + font.width(text) + 4;
		if (rule < x + w) {
			g.fill(rule, y + 4, x + w, y + 5, CopperStyle.DEEP_600);
		}
	}

	/** Body text wrapped to {@code w} in {@code color}, from {@code y}; returns the y under it. */
	protected int wrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
		for (var line : font.split(text, w)) {
			g.drawString(font, line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	/** The count of slots {@code from} to {@code to} (exclusive) that hold something. */
	protected int filled(int from, int to) {
		int n = 0;
		for (int i = from; i < to && i < menu.slots.size(); i++) {
			if (menu.slots.get(i).hasItem()) {
				n++;
			}
		}
		return n;
	}

	/** {@code key + ".none"}, {@code key + ".one"} or the plural {@code key} itself with the count (the lang file's plural rule). */
	protected static Component counted(String key, int n) {
		return n == 0 ? Component.translatable(key + ".none")
			: n == 1 ? Component.translatable(key + ".one", n) : Component.translatable(key, n);
	}

	private boolean overClose(double mx, double my) {
		return CopperLayout.inside(CopperLayout.close(window()), mx, my);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int[] win = window();
		CopperStyle.window(g, win[0], win[1], win[2], win[3]);
		int[] plaque = CopperStyle.plaque(g, font, plaqueTitle(), win);
		CopperStyle.lintel(g, font, win, plaque, lintelText(), overClose(mouseX, mouseY));
		for (Header header : headers()) {
			heading(g, header.text(), frameX() + header.x(), topPos + header.y(), header.w());
		}
		renderGutter(g);
		if (panelW > 0) {
			int[] box = panel();
			CopperStyle.panel(g, box[0], box[1], box[2], box[3]);
		}
		for (Slot slot : menu.slots) {
			int[] well = CopperSlots.at(CopperSlots.well(slot.x, slot.y), leftPos, topPos);
			g.blitSprite(slot == hoveredSlot ? CopperStyle.WELL_FOCUS : CopperStyle.WELL, well[0], well[1], well[2], well[3]);
		}
		if (panelW > 0) {
			int[] box = panel();
			g.enableScissor(box[0], box[1], box[0] + box[2], box[1] + box[3]); // long words never spill out of the panel
			renderPanel(g, box, mouseX, mouseY);
			g.disableScissor();
		}
		int[] w = CopperSlots.at(CopperSlots.whisper(frameH, gutter, panelW), frameX(), topPos);
		CopperStyle.whisper(g, font, whisperAt(mouseX, mouseY), w[0], w[1] + 3, w[2], TableStyle.compact());
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		// Everything is drawn by renderBg: the headings, the plaque and the lintel are Copperline's.
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		renderTooltip(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && overClose(mouseX, mouseY)) {
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
		return !CopperLayout.inside(window(), mouseX, mouseY);
	}
}
