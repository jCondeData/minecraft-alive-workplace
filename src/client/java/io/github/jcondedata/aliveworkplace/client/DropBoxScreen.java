package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.ui.CopperSlots;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

/**
 * The Drop Box in Copperline (ROADMAP 44.1): its 27 slots and the player's pack where a chest has them, the count
 * waiting for a porter on the lintel, and beside them where the things go.
 */
public class DropBoxScreen extends CopperSlotScreen<ChestMenu> {
	private static final int BOX = 27;

	public DropBoxScreen(ChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 114 + menu.getRowCount() * 18, 0, CopperSlots.PANEL_W);
	}

	@Override
	protected Component lintelText() {
		return counted("screen.aliveworkplace.drop_box.waiting", filled(0, BOX));
	}

	@Override
	protected List<Header> headers() {
		int packY = menu.getRowCount() * 18 + 31 - 10;
		return List.of(new Header(Component.translatable("screen.aliveworkplace.drop_box.contents"), 8, 6, 160),
			new Header(Component.translatable("screen.aliveworkplace.copperline.pack"), 8, packY, 160));
	}

	@Override
	protected void renderPanel(GuiGraphics g, int[] p, int mouseX, int mouseY) {
		int x = p[0] + 4;
		int w = p[2] - 8;
		heading(g, Component.translatable("screen.aliveworkplace.drop_box.where"), x, topPos + 6, w);
		int y = topPos + 17;
		y += CopperStyle.whatHappens(g, font, Component.translatable("screen.aliveworkplace.drop_box.what_happens"), x, y, w) + 6;
		CopperStyle.glyph(g, "info", x, y);
		wrapped(g, Component.translatable("screen.aliveworkplace.drop_box.tip"), x + 11, y, w - 11, CopperStyle.TUFF);
	}

	@Override
	protected Component whisper(Slot slot) {
		if (slot == null) {
			return Component.translatable("screen.aliveworkplace.drop_box.whisper.idle");
		}
		return Component.translatable(slot.index < BOX ? "screen.aliveworkplace.drop_box.whisper.box" : "screen.aliveworkplace.drop_box.whisper.pack");
	}
}
