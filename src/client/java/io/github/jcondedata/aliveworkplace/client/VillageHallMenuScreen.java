package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
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
 */
public class VillageHallMenuScreen extends AbstractContainerScreen<ChoiceView> {
	public static final ResourceLocation TEXTURE = AliveWorkplace.id("textures/gui/village_hall.png");
	public static final int WIDTH = 196;
	public static final int HEIGHT = 160;
	private static final int BUTTON_V = 176;
	private static final int TEXT = 0xFF404040;

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
		renderTooltip(g, mouseX, mouseY);
	}

	/** Where button {@code slot}'s middle is on the screen, in GUI pixels (the screenshot harness points at it). */
	public int[] centre(int slot) {
		Slot s = menu.slots.get(slot);
		return new int[]{leftPos + s.x + 8, topPos + s.y + 8};
	}
}
