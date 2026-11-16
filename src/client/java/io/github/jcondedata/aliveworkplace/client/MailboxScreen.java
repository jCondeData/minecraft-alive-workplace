package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperSlots;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The mailbox in Copperline (ROADMAP 44.1): the mail that came in and the player's pack on the left, where a chest has
 * them; the side panel holds the "To:" and letter fields, the 3x3 parcel and the one copper Send button.
 */
public class MailboxScreen extends CopperSlotScreen<MailboxMenu> {
	private static final int INBOX_END = MailboxMenu.OUT + MailboxBlockEntity.SIZE;
	private static final int FIELD_X = 42;

	private EditBox to;
	private EditBox message;

	public MailboxScreen(MailboxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, MailboxMenu.FRAME_H, 0, CopperSlots.PANEL_W);
	}

	@Override
	protected void init() {
		super.init();
		int[] p = panel();
		int fw = p[2] - FIELD_X - 4;
		to = field(p[0] + FIELD_X, topPos + 18, fw, Component.translatable("screen.aliveworkplace.mailbox.to"), 16,
			Component.translatable("screen.aliveworkplace.mailbox.to_hint"));
		message = field(p[0] + FIELD_X, topPos + 36, fw, Component.translatable("screen.aliveworkplace.mailbox.message"), Mail.MAX_MESSAGE,
			Component.translatable("screen.aliveworkplace.mailbox.message_hint"));
	}

	/** A borderless text box sitting in a Copperline well ({@link #fieldWell}) at {@code x, y}. */
	private EditBox field(int x, int y, int w, Component name, int max, Component hint) {
		EditBox box = new EditBox(font, x + 4, y + 3, w - 8, 10, name);
		box.setBordered(false);
		box.setMaxLength(max);
		box.setTextColor(CopperStyle.QUARTZ);
		box.setHint(hint);
		addRenderableWidget(box);
		return box;
	}

	private static int[] fieldWell(EditBox box) {
		return new int[]{box.getX() - 4, box.getY() - 3, box.getWidth() + 8, 14};
	}

	/** The copper Send button, in screen pixels: under the parcel, level with the hotbar. */
	public int[] sendBox() {
		int[] p = panel();
		return new int[]{p[0] + 4, topPos + MailboxMenu.HOTBAR_Y, p[2] - 8, 16};
	}

	private boolean canSend() {
		return to != null && !to.getValue().trim().isEmpty();
	}

	private void send() {
		if (canSend()) {
			ClientPlayNetworking.send(new Mail.Send(to.getValue().trim(), message.getValue()));
			message.setValue("");
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && CopperLayout.inside(sendBox(), mouseX, mouseY)) {
			send();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for (EditBox box : new EditBox[]{to, message}) {
			if (box.isFocused() && keyCode != 256) {
				if (keyCode == 257 || keyCode == 335) { // Enter sends
					send();
					return true;
				}
				return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected Component lintelText() {
		return counted("screen.aliveworkplace.mailbox.waiting", filled(MailboxMenu.OUT, INBOX_END));
	}

	@Override
	protected List<Header> headers() {
		return List.of(
			new Header(Component.translatable("screen.aliveworkplace.mailbox.inbox_header"), 8, 6, 160),
			new Header(Component.translatable("screen.aliveworkplace.copperline.pack"), 8, MailboxMenu.INVENTORY_Y - 10, 160));
	}

	@Override
	protected void renderPanel(GuiGraphics g, int[] p, int mouseX, int mouseY) {
		int x = p[0] + 4;
		int w = p[2] - 8;
		heading(g, Component.translatable("screen.aliveworkplace.mailbox.send_header"), x, topPos + 6, w);
		for (EditBox box : new EditBox[]{to, message}) {
			int[] well = fieldWell(box);
			g.blitSprite(box.isFocused() ? CopperStyle.WELL_FOCUS : CopperStyle.WELL, well[0], well[1], well[2], well[3]);
		}
		g.drawString(font, Component.translatable("screen.aliveworkplace.mailbox.to"), x, topPos + 21, CopperStyle.QUARTZ, false);
		g.drawString(font, Component.translatable("screen.aliveworkplace.mailbox.message"), x, topPos + 39, CopperStyle.QUARTZ, false);
		heading(g, Component.translatable("screen.aliveworkplace.mailbox.parcel"), x, topPos + MailboxMenu.OUT_Y - 10, w);
		int textX = leftPos + MailboxMenu.OUT_X + MailboxMenu.OUT_COLUMNS * 18 + 4;
		wrapped(g, Component.translatable("screen.aliveworkplace.mailbox.parcel_hint"), textX, topPos + MailboxMenu.OUT_Y + 1,
			p[0] + p[2] - 4 - textX, CopperStyle.TUFF);
		int[] send = sendBox();
		boolean over = CopperLayout.inside(send, mouseX, mouseY);
		CopperStyle.button(g, font, Component.translatable("screen.aliveworkplace.mailbox.send"), null, send[0], send[1], send[2], send[3],
			CopperStyle.Kind.COPPER, over, false, !canSend());
	}

	@Override
	protected Component whisperAt(int mouseX, int mouseY) {
		if (CopperLayout.inside(sendBox(), mouseX, mouseY)) {
			return Component.translatable(canSend() ? "screen.aliveworkplace.mailbox.whisper.send" : "screen.aliveworkplace.mailbox.whisper.no_name");
		}
		if (to != null && CopperLayout.inside(fieldWell(to), mouseX, mouseY)) {
			return Component.translatable("screen.aliveworkplace.mailbox.whisper.to");
		}
		if (message != null && CopperLayout.inside(fieldWell(message), mouseX, mouseY)) {
			return Component.translatable("screen.aliveworkplace.mailbox.whisper.message");
		}
		return super.whisperAt(mouseX, mouseY);
	}

	@Override
	protected Component whisper(Slot slot) {
		if (slot == null) {
			return Component.translatable("screen.aliveworkplace.mailbox.whisper.idle");
		}
		if (slot.index < MailboxMenu.OUT) {
			return Component.translatable("screen.aliveworkplace.mailbox.whisper.out");
		}
		if (slot.index < INBOX_END) {
			return Component.translatable("screen.aliveworkplace.mailbox.whisper.inbox");
		}
		return Component.translatable("screen.aliveworkplace.mailbox.whisper.pack");
	}
}
