package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.ui.CopperSlots;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

/**
 * The Shop Counter in Copperline (ROADMAP 44.1): the price list's two rows where a chest has them, named in the gutter
 * ("Sells" over "Price"), the player's pack under them, how many columns are for sale on the lintel and how a sale
 * works in the side panel.
 */
public class ShopCounterScreen extends CopperSlotScreen<ChestMenu> {
	private static final int COLUMNS = ShopCounterBlockEntity.COLUMNS;
	/** The gutter left of the rows, for their names. */
	public static final int GUTTER = CopperSlots.LABEL_GUTTER;

	public ShopCounterScreen(ChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 114 + menu.getRowCount() * 18, GUTTER, CopperSlots.PANEL_W);
	}

	@Override
	protected Component plaqueTitle() {
		return Component.translatable("block.aliveworkplace.shop_counter");
	}

	/** Columns with both a sample and a price: the ones a shopkeeper sells. */
	private int forSale() {
		int n = 0;
		for (int c = 0; c < COLUMNS; c++) {
			if (menu.slots.get(c).hasItem() && menu.slots.get(c + COLUMNS).hasItem()) {
				n++;
			}
		}
		return n;
	}

	@Override
	protected Component lintelText() {
		return counted("screen.aliveworkplace.shop_counter.for_sale", forSale());
	}

	@Override
	protected List<Header> headers() {
		int packY = menu.getRowCount() * 18 + 31 - 10;
		return List.of(new Header(Component.translatable("screen.aliveworkplace.shop_counter.prices"), -GUTTER + 8, 6, GUTTER + 160),
			new Header(Component.translatable("screen.aliveworkplace.copperline.pack"), -GUTTER + 8, packY, GUTTER + 160));
	}

	/**
	 * Under each used column a 2-pixel mark: Verdigris when it has goods and a price (for sale), Redstone when one is
	 * missing; the whisper line says which in words.
	 */
	private void columnMarks(GuiGraphics g) {
		for (int c = 0; c < COLUMNS; c++) {
			boolean goods = menu.slots.get(c).hasItem();
			boolean price = menu.slots.get(c + COLUMNS).hasItem();
			if (goods || price) {
				Slot slot = menu.slots.get(c + COLUMNS);
				int x = leftPos + slot.x;
				int y = topPos + slot.y + 18;
				g.fill(x, y, x + 16, y + 2, goods && price ? CopperStyle.VERDIGRIS : CopperStyle.REDSTONE);
			}
		}
	}

	/** What column {@code c} still needs, in words: for sale, needs a price, needs goods, or empty. */
	private Component columnState(int c) {
		boolean goods = menu.slots.get(c).hasItem();
		boolean price = menu.slots.get(c + COLUMNS).hasItem();
		String state = goods && price ? "ready" : goods ? "no_price" : price ? "no_goods" : "empty";
		return Component.translatable("screen.aliveworkplace.shop_counter.column." + state);
	}

	@Override
	protected void renderGutter(GuiGraphics g) {
		columnMarks(g);
		int x = leftPos - GUTTER + 8;
		int w = GUTTER - 10;
		g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.shop_counter.sells").getString(), w),
			x, topPos + 18 + 4, CopperStyle.QUARTZ, false);
		CopperStyle.glyph(g, "coin", x, topPos + 36 + 4);
		g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.shop_counter.price").getString(), w - 10),
			x + 10, topPos + 36 + 4, CopperStyle.LANTERN, false);
	}

	@Override
	protected void renderPanel(GuiGraphics g, int[] p, int mouseX, int mouseY) {
		int x = p[0] + 4;
		int w = p[2] - 8;
		heading(g, Component.translatable("screen.aliveworkplace.shop_counter.how"), x, topPos + 6, w);
		int y = topPos + 17;
		y += CopperStyle.whatHappens(g, font, Component.translatable("screen.aliveworkplace.shop_counter.what_happens"), x, y, w) + 6;
		CopperStyle.glyph(g, "info", x, y);
		wrapped(g, Component.translatable("screen.aliveworkplace.shop_counter.tip"), x + 11, y, w - 11, CopperStyle.TUFF);
	}

	@Override
	protected Component whisper(Slot slot) {
		if (slot == null) {
			return Component.translatable("screen.aliveworkplace.shop_counter.whisper.idle");
		}
		if (slot.index < COLUMNS) {
			return Component.translatable("screen.aliveworkplace.shop_counter.whisper.column",
				Component.translatable("screen.aliveworkplace.shop_counter.whisper.goods"), columnState(slot.index));
		}
		if (slot.index < 2 * COLUMNS) {
			return Component.translatable("screen.aliveworkplace.shop_counter.whisper.column",
				Component.translatable("screen.aliveworkplace.shop_counter.whisper.price"), columnState(slot.index - COLUMNS));
		}
		return Component.translatable("screen.aliveworkplace.shop_counter.whisper.pack");
	}
}
