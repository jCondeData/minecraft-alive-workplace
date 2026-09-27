package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The mailbox: "To:" field and Send button over a row of outgoing slots, then the mail that arrived,
 * then the player's inventory. Drawn with plain fills in the vanilla container colours (no texture).
 */
public class MailboxScreen extends AbstractContainerScreen<MailboxMenu> {
	private static final int BG = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int DARK = 0xFF555555;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_EDGE = 0xFF373737;
	private static final int OUT_TINT = 0xFFB9A98A;
	private static final int TEXT = 0xFF404040;

	private EditBox to;

	public MailboxScreen(MailboxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 176;
		imageHeight = 222;
		inventoryLabelY = MailboxMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		to = new EditBox(font, leftPos + 26, topPos + 16, 92, 14, Component.translatable("screen.aliveworkplace.mailbox.to"));
		to.setMaxLength(16);
		to.setHint(Component.translatable("screen.aliveworkplace.mailbox.to_hint"));
		addRenderableWidget(to);
		addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.mailbox.send"), b -> send())
			.bounds(leftPos + 122, topPos + 14, 46, 18).build());
	}

	private void send() {
		String name = to.getValue().trim();
		if (!name.isEmpty()) {
			ClientPlayNetworking.send(new Mail.Send(name));
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (to.isFocused() && keyCode != 256) {
			if (keyCode == 257 || keyCode == 335) { // Enter sends
				send();
				return true;
			}
			return to.keyPressed(keyCode, scanCode, modifiers) || to.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
		g.fill(x0, y0, x1, y1, SLOT_EDGE);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, LIGHT);
		g.fill(x0 + 3, y0 + 3, x1 - 1, y1 - 1, DARK);
		g.fill(x0 + 3, y0 + 3, x1 - 3, y1 - 3, BG);
		for (Slot slot : menu.slots) {
			int sx = leftPos + slot.x, sy = topPos + slot.y;
			g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_EDGE);
			g.fill(sx, sy, sx + 17, sy + 17, LIGHT);
			g.fill(sx, sy, sx + 16, sy + 16, slot.index < MailboxMenu.OUT ? OUT_TINT : SLOT);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, 8, 5, TEXT, false);
		g.drawString(font, Component.translatable("screen.aliveworkplace.mailbox.to"), 8, 19, TEXT, false);
		g.drawString(font, Component.translatable("screen.aliveworkplace.mailbox.inbox"), 8, MailboxMenu.INBOX_Y - 11, TEXT, false);
		g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		renderTooltip(g, mouseX, mouseY);
	}
}
