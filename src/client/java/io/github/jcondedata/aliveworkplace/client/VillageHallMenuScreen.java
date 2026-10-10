package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Village Hall's own screen (ROADMAP 30.4a), drawn like vanilla's workstation screens: a grey bevelled window
 * (textures/gui/village_hall.png, drawn by tools/textures/art/gui.py) with the village's figures on a sunken plaque, the
 * page's actions on an etched toolbar and the page itself on a sunken panel. Every icon sits on a raised button that
 * lights up under the mouse; empty places and dividers are just the panel. Used for the hall's main page, all its pages
 * and the Book of Edicts; the clicks are the server's {@code ChoiceMenu} ones.
 *
 * <p>On the Trade page (ROADMAP 33.4) an icon carries marks ({@link TradePage#marks}), drawn over it from
 * textures/gui/trade_marks.png: a gold star or a red mark in its top-left corner (known for, short of), the price's
 * arrow in its bottom-right corner (up, down, steady), and a green bar under the open tab.
 */
public class VillageHallMenuScreen extends AbstractContainerScreen<ChoiceView> {
	public static final ResourceLocation TEXTURE = AliveWorkplace.id("textures/gui/village_hall.png");
	public static final int WIDTH = 196;
	public static final int HEIGHT = 160;
	private static final int BUTTON_V = 176;
	private static final int TEXT = 0xFF404040;
	/** The Trade page's marks: five 8x8 marks in a row (star, short of, up, down, steady), the open tab's 16x2 bar under them. */
	public static final ResourceLocation MARKS = AliveWorkplace.id("textures/gui/trade_marks.png");
	private static final int MARKS_W = 64;
	private static final int MARKS_H = 16;

	public VillageHallMenuScreen(ChoiceView menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = WIDTH;
		imageHeight = HEIGHT;
		titleLabelX = 8;
		titleLabelY = 6;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		for (Slot slot : menu.slots) {
			if (slot.isActive()) {
				boolean hovered = isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
				g.blit(TEXTURE, leftPos + slot.x - 1, topPos + slot.y - 1, hovered ? 18 : 0, BUTTON_V, 18, 18);
			}
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.pose().pushPose();
		g.pose().translate(0, 0, 300); // over the icons (and their counts), under the tooltip
		for (Slot slot : menu.slots) {
			if (slot.isActive()) {
				marks(g, leftPos + slot.x, topPos + slot.y, TradePage.marks(slot.getItem()));
			}
		}
		g.pose().popPose();
		renderTooltip(g, mouseX, mouseY);
	}

	/** Draws an icon's marks over it; {@code x}, {@code y} is the icon's top-left corner. */
	private static void marks(GuiGraphics g, int x, int y, Set<String> marks) {
		if (marks.isEmpty()) {
			return;
		}
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
		if (marks.contains(TradePage.OPEN)) {
			g.blit(MARKS, x, y + 15, 16, 2, 0f, 8f, 16, 2, MARKS_W, MARKS_H);
		}
	}

	private static void mark(GuiGraphics g, int x, int y, int index) {
		g.blit(MARKS, x, y, 8, 8, index * 8f, 0f, 8, 8, MARKS_W, MARKS_H);
	}

	/** Where button {@code slot}'s middle is on the screen, in GUI pixels (the screenshot harness points at it). */
	public int[] centre(int slot) {
		Slot s = menu.slots.get(slot);
		return new int[]{leftPos + s.x + 8, topPos + s.y + 8};
	}
}
