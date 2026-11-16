package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperTradeMenu;
import io.github.jcondedata.aliveworkplace.ui.CopperTrades;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * The trades of the mod's own jobs in Copperline (ROADMAP 44.2): the villager's name on the plaque and the count of
 * offers on the lintel, the offer list on the left (a row per offer: price, second price, arrow, goods; sold out ones
 * with the bad glyph, cheaper ones with the down glyph), and round vanilla's real slots the level and its bar, the two
 * payment wells, the arrow and the goods, and the pack. Clicking an offer does what vanilla's button does: the price
 * moves from the pack into the payment slots and the server is told which offer is chosen.
 */
public class CopperTradeScreen extends CopperSlotScreen<CopperTradeMenu> {
	private static final int PAYMENT_A = 0;
	private static final int PAYMENT_B = 1;
	private static final int RESULT = 2;

	/** The chosen offer, or -1 before one is clicked (nothing is lit until then). */
	private int shopItem = -1;
	private int scrollOff;
	private boolean dragging;
	private int mouseX;
	private int mouseY;

	public CopperTradeScreen(CopperTradeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, CopperTrades.FRAME_H, CopperTrades.SHIFT, 0, CopperTrades.SHIFT);
	}

	/** A box relative to the slot frame, in screen pixels. */
	private int[] at(int[] box) {
		return new int[]{frameX() + box[0], topPos + box[1], box[2], box[3]};
	}

	/** The middle of offer row {@code i} as shown (the screenshot harness points at it). */
	public int[] rowCentre(int i) {
		int[] r = at(CopperTrades.row(i));
		return new int[]{r[0] + r[2] / 2, r[1] + r[3] / 2};
	}

	/** The offer row under the mouse, as an index into the offers, or -1. */
	private int offerAt(double mx, double my) {
		int row = CopperTrades.rowAt(mx - frameX(), my - topPos);
		int index = row < 0 ? -1 : scrollOff + row;
		return index >= 0 && index < menu.getOffers().size() ? index : -1;
	}

	@Override
	protected Component lintelText() {
		return counted("screen.aliveworkplace.trades.count", menu.getOffers().size());
	}

	@Override
	protected List<Header> headers() {
		return List.of(new Header(Component.translatable("screen.aliveworkplace.trades.offers"), CopperTrades.LIST_X, 6, CopperTrades.ROW_W + 6),
			new Header(Component.translatable("screen.aliveworkplace.trades.trade"), 8, 6, 160),
			new Header(Component.translatable("screen.aliveworkplace.copperline.pack"), 8, 74, 160));
	}

	@Override
	protected void renderPanel(GuiGraphics g, int[] box, int mouseX, int mouseY) {
		// No side panel: the offer list is in the gutter.
	}

	@Override
	protected void renderGutter(GuiGraphics g) {
		MerchantOffers offers = menu.getOffers();
		int[] list = at(new int[]{CopperTrades.LIST_X - 1, CopperTrades.LIST_Y - 1, CopperTrades.ROW_W + 2, CopperTrades.ROWS * CopperTrades.ROW_H + 2});
		CopperStyle.well(g, list[0], list[1], list[2], list[3]);
		int over = offerAt(mouseX, mouseY);
		for (int i = 0; i < CopperTrades.ROWS && scrollOff + i < offers.size(); i++) {
			int index = scrollOff + i;
			MerchantOffer offer = offers.get(index);
			int[] r = at(CopperTrades.row(i));
			CopperStyle.rowBand(g, r[0], r[1], r[2], r[3], i % 2 == 1, index == over, index == shopItem);
			int y = r[1] + 2;
			ItemStack costA = offer.getCostA();
			g.renderFakeItem(costA, r[0] + CopperTrades.COST_A, y);
			g.renderItemDecorations(font, costA, r[0] + CopperTrades.COST_A, y);
			if (costA.getCount() < offer.getBaseCostA().getCount()) {
				CopperStyle.glyph(g, "down", r[0] + CopperTrades.COST_A + 18, y + 8); // cheaper for this player
			}
			if (!offer.getCostB().isEmpty()) {
				g.renderFakeItem(offer.getCostB(), r[0] + CopperTrades.COST_B, y);
				g.renderItemDecorations(font, offer.getCostB(), r[0] + CopperTrades.COST_B, y);
			}
			if (offer.isOutOfStock()) {
				CopperStyle.glyph(g, "bad", r[0] + 55, y + 4);
			} else {
				arrow(g, r[0] + 53, y + 4, 11, CopperStyle.TUFF);
			}
			g.renderFakeItem(offer.getResult(), r[0] + CopperTrades.GOODS, y);
			g.renderItemDecorations(font, offer.getResult(), r[0] + CopperTrades.GOODS, y);
			if (offer.isOutOfStock()) {
				g.fill(r[0] + 2, r[1], r[0] + r[2], r[1] + r[3], 0x8016171D); // dimmed, the words say why
			}
		}
		if (offers.isEmpty()) {
			wrapped(g, Component.translatable("screen.aliveworkplace.trades.empty"), list[0] + 5, list[1] + 6, list[2] - 10, CopperStyle.TUFF);
		}
		int[] bar = at(CopperTrades.scrollbar());
		CopperStyle.scrollbar(g, bar[0], bar[1], bar[3], scrollOff, CopperTrades.maxScroll(offers.size()), offers.size() * CopperTrades.ROW_H, dragging);
		renderLevel(g);
		MerchantOffer chosen = shopItem >= 0 && shopItem < offers.size() ? offers.get(shopItem) : null;
		int[] a = at(CopperTrades.ARROW);
		if (chosen != null && chosen.isOutOfStock()) {
			CopperStyle.glyph(g, "bad", a[0] + 9, a[1] + 4);
		} else {
			arrow(g, a[0] + 2, a[1] + 3, 22, chosen == null ? CopperStyle.DEEP_600 : CopperStyle.COPPER);
		}
	}

	/** The level's name and its bar over the payment slots: earned in Verdigris, what the trade in the slots adds in Lantern. */
	private void renderLevel(GuiGraphics g) {
		if (!menu.showProgressBar() || menu.getOffers().isEmpty()) {
			return;
		}
		int level = menu.getTraderLevel();
		int[] box = at(CopperTrades.LEVEL);
		Component name = Component.translatable("merchant.level." + Math.max(1, Math.min(5, level)));
		g.drawString(font, name, box[0], box[1] + 1, CopperStyle.QUARTZ, false);
		float[] fill = CopperTrades.levelFill(level, menu.getTraderXp(), menu.getFutureTraderXp());
		if (fill == null) {
			CopperStyle.glyph(g, "star", box[0] + font.width(name) + 4, box[1]);
			return;
		}
		int x = box[0] + font.width(name) + 6;
		int w = box[0] + box[2] - x;
		CopperStyle.progress(g, x, box[1] + 1, w, 7, fill[0], CopperStyle.VERDIGRIS);
		if (fill[1] > 0) {
			int from = x + 1 + Math.round((w - 2) * fill[0]);
			int to = Math.min(x + w - 1, from + Math.max(1, Math.round((w - 2) * fill[1])));
			g.fill(from, box[1] + 2, to, box[1] + 7, CopperStyle.LANTERN);
		}
	}

	/** A pixel arrow pointing right, {@code len} long, its point 4 pixels tall either side of the shaft. */
	private static void arrow(GuiGraphics g, int x, int y, int len, int color) {
		g.fill(x, y + 3, x + len - 3, y + 5, color);
		for (int i = 0; i < 4; i++) {
			g.fill(x + len - 4 + i, y + i, x + len - 3 + i, y + 8 - i, color);
		}
	}

	@Override
	protected Component whisper(Slot slot) {
		if (slot == null) {
			return Component.translatable(menu.getOffers().isEmpty() ? "screen.aliveworkplace.trades.whisper.empty" : "screen.aliveworkplace.trades.whisper.idle");
		}
		return Component.translatable(switch (slot.index) {
			case PAYMENT_A, PAYMENT_B -> "screen.aliveworkplace.trades.whisper.pay";
			case RESULT -> "screen.aliveworkplace.trades.whisper.result";
			default -> "screen.aliveworkplace.trades.whisper.pack";
		});
	}

	@Override
	protected Component whisperAt(int mouseX, int mouseY) {
		int index = offerAt(mouseX, mouseY);
		if (index >= 0) {
			MerchantOffer offer = menu.getOffers().get(index);
			if (offer.isOutOfStock()) {
				return Component.translatable("screen.aliveworkplace.trades.whisper.sold_out");
			}
			if (offer.getCostA().getCount() < offer.getBaseCostA().getCount()) {
				return Component.translatable("screen.aliveworkplace.trades.whisper.cheaper", offer.getCostA().getCount(), offer.getBaseCostA().getCount());
			}
			return Component.translatable("screen.aliveworkplace.trades.whisper.offer");
		}
		if (menu.showProgressBar() && CopperLayout.inside(at(CopperTrades.LEVEL), mouseX, mouseY)) {
			int[] xp = CopperTrades.levelNumbers(menu.getTraderLevel(), menu.getTraderXp());
			return xp == null ? Component.translatable("screen.aliveworkplace.trades.whisper.master")
				: Component.translatable("screen.aliveworkplace.trades.whisper.level", xp[0], xp[1]);
		}
		return super.whisperAt(mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		super.render(g, mouseX, mouseY, partialTick);
		int index = offerAt(mouseX, mouseY);
		if (index < 0) {
			return;
		}
		MerchantOffer offer = menu.getOffers().get(index);
		int[] r = at(CopperTrades.row(index - scrollOff));
		int x = mouseX - r[0];
		if (mouseY >= r[1] + 2 && mouseY < r[1] + 18) {
			ItemStack shown = x >= CopperTrades.COST_A && x < CopperTrades.COST_A + 16 ? offer.getCostA()
				: x >= CopperTrades.COST_B && x < CopperTrades.COST_B + 16 ? offer.getCostB()
				: x >= CopperTrades.GOODS && x < CopperTrades.GOODS + 16 ? offer.getResult() : ItemStack.EMPTY;
			if (!shown.isEmpty()) {
				g.renderTooltip(font, shown, mouseX, mouseY);
			}
		}
	}

	/** What vanilla's offer button does: choose it, move its price from the pack into the slots, tell the server. */
	public void choose(int index) {
		shopItem = index;
		menu.setSelectionHint(index);
		menu.tryMoveItems(index);
		minecraft.getConnection().send(new ServerboundSelectTradePacket(index));
	}

	private boolean overScrollbar(double mx, double my) {
		int[] bar = at(CopperTrades.scrollbar());
		return mx >= bar[0] - 1 && mx < bar[0] + bar[2] + 1 && my >= bar[1] && my < bar[1] + bar[3];
	}

	private void dragTo(double my) {
		int[] bar = at(CopperTrades.scrollbar());
		int max = CopperTrades.maxScroll(menu.getOffers().size());
		float f = (float) ((my - bar[1]) / bar[3]);
		scrollOff = Math.max(0, Math.min(max, Math.round(f * max)));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int index = offerAt(mouseX, mouseY);
			if (index >= 0) {
				choose(index);
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
				return true;
			}
			if (CopperTrades.maxScroll(menu.getOffers().size()) > 0 && overScrollbar(mouseX, mouseY)) {
				dragging = true;
				dragTo(mouseY);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (dragging) {
			dragTo(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		int max = CopperTrades.maxScroll(menu.getOffers().size());
		if (max > 0 && dy != 0) {
			scrollOff = Math.max(0, Math.min(max, scrollOff - (int) Math.signum(dy)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, dx, dy);
	}
}
