package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Pokémon Trader's own screen (ROADMAP 28.23), in the Village Hall's look since 43.2 ({@link TableStyle}, as the
 * Trainer screen and the list menus): a dark panel with a slate-blue edge and the trader's name in gold, the trader's
 * book in a framed cell beside it; the day's offers as rows down a dark list on the left, each with the Pokémon in a
 * framed cell, its name in white (a gold star when shiny), what it costs on a grey-blue second line and the ball it
 * comes in at the row's end, the picked offer's row and cell in gold; the player's party on the right under a gold
 * heading, two rows of three framed cells (a Pokémon that won't do greyed out by the server, the one waiting for the
 * second click in gold), the status lines under a thin rule, and a footer hint. With no offers the list says so.
 * A click anywhere on an offer's row picks it, as a click on its cell does. The clicks are the server's
 * {@code ChoiceMenu} ones and the texts are the server's (an icon's name), so nothing here decides a trade.
 */
public class PokemonTradeScreen extends AbstractContainerScreen<PokemonTradeView> {
	public static final int WIDTH = PokemonTradeView.WIDTH;
	public static final int HEIGHT = PokemonTradeView.HEIGHT;
	private static final String STAR = "★";

	public PokemonTradeScreen(PokemonTradeView menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = WIDTH;
		imageHeight = HEIGHT;
		titleLabelX = 8;
		titleLabelY = 8;
	}

	private ItemStack icon(int slot) {
		return menu.slots.get(slot).getItem();
	}

	private static boolean picked(ItemStack icon) {
		return Boolean.TRUE.equals(icon.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
	}

	/** The offer row under the mouse with an offer on it, or -1. */
	private int rowAt(double mouseX, double mouseY) {
		int row = PokemonTradeView.offerAt((int) Math.floor(mouseX) - leftPos, (int) Math.floor(mouseY) - topPos);
		return row >= 0 && ChoiceView.shown(icon(PokemonTradeView.OFFERS + row)) ? row : -1;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		TableStyle.panel(g, left, top, WIDTH, HEIGHT);
		// The shell (Copperline, 48.4): the trader's name on the plaque, today's offers on the lintel left of the trader's
		// book, the close X at its end; the whisper line at the foot (renderLabels).
		int[] win = window();
		int[] plaque = CopperStyle.plaque(g, font, title, win);
		CopperStyle.lintel(g, font, win, plaque, Component.empty(), overClose(mouseX, mouseY));
		int[] lintel = io.github.jcondedata.aliveworkplace.ui.CopperLayout.lintel(win, plaque);
		Component count = offersLine();
		int room = left + PokemonTradeView.INFO_X - 6 - lintel[0];
		if (room > 8) {
			String text = CopperStyle.trim(font, count.getString(), room);
			g.drawString(font, text, left + PokemonTradeView.INFO_X - 6 - font.width(text), lintel[1] + 4, CopperStyle.TUFF, false);
		}
		TableStyle.listBackground(g, left + PokemonTradeView.LIST_X, top + PokemonTradeView.LIST_Y, PokemonTradeView.LIST_W,
			PokemonTradeView.LIST_H);
		int over = rowAt(mouseX, mouseY);
		for (int i = 0; i < PokemonTradeView.MAX_OFFERS; i++) {
			ItemStack offer = icon(PokemonTradeView.OFFERS + i);
			if (!ChoiceView.shown(offer)) {
				continue;
			}
			int x = left + PokemonTradeView.CARD_X;
			int y = top + PokemonTradeView.cardY(i);
			if (picked(offer)) {
				g.fill(x, y, x + PokemonTradeView.CARD_W, y + PokemonTradeView.ROW_H, TableStyle.ROW_ON);
			} else if (over == i) {
				g.fill(x, y, x + PokemonTradeView.CARD_W, y + PokemonTradeView.ROW_H, TableStyle.ROW_OVER);
			}
			if (i + 1 < PokemonTradeView.MAX_OFFERS && ChoiceView.shown(icon(PokemonTradeView.OFFERS + i + 1))) {
				TableStyle.rule(g, x + 4, y + PokemonTradeView.ROW_H, PokemonTradeView.CARD_W - 8);
			}
		}
		TableStyle.rule(g, left + PokemonTradeView.PARTY_X, top + PokemonTradeView.STATUS_Y - 4, PokemonTradeView.SIDE_W);
		for (Slot slot : menu.slots) {
			int index = slot.index;
			boolean ball = index >= PokemonTradeView.BALLS && index < PokemonTradeView.BALLS + PokemonTradeView.MAX_OFFERS;
			if (slot.isActive() && !ball) {
				boolean offer = index >= PokemonTradeView.OFFERS && index < PokemonTradeView.OFFERS + PokemonTradeView.MAX_OFFERS;
				boolean hovered = isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY) || offer && over == index - PokemonTradeView.OFFERS;
				boolean on = index != PokemonTradeView.INFO && picked(slot.getItem());
				TableStyle.cell(g, leftPos + slot.x - 1, topPos + slot.y - 1, on, hovered);
			}
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, Component.translatable("screen.aliveworkplace.pokemon_trader.party"), PokemonTradeView.PARTY_X + 2,
			PokemonTradeView.PARTY_Y + 2, TableStyle.TITLE, false);
		boolean any = false;
		for (int i = 0; i < PokemonTradeView.MAX_OFFERS; i++) {
			ItemStack offer = icon(PokemonTradeView.OFFERS + i);
			if (!ChoiceView.shown(offer)) {
				continue;
			}
			any = true;
			int x = PokemonTradeView.CARD_X + 28;
			int y = PokemonTradeView.cardY(i);
			int room = PokemonTradeView.CARD_W - 28 - 26;
			String name = offer.getHoverName().getString();
			boolean shiny = name.endsWith(STAR);
			if (shiny) {
				name = name.substring(0, name.length() - STAR.length()).stripTrailing();
			}
			name = TableStyle.trim(font, name, room - (shiny ? 10 : 0));
			boolean on = picked(offer);
			g.drawString(font, name, x, y + 5, on ? TableStyle.TITLE : TableStyle.NAME, false);
			if (shiny) {
				g.drawString(font, STAR, x + font.width(name) + 3, y + 5, TableStyle.TITLE, false);
			}
			ItemStack cost = icon(PokemonTradeView.COSTS + i);
			if (!cost.isEmpty()) {
				g.drawString(font, TableStyle.trim(font, cost.getHoverName().getString(), room), x, y + 16, TableStyle.SUBLINE, false);
			}
		}
		if (!any) {
			int cx = PokemonTradeView.LIST_X + PokemonTradeView.LIST_W / 2;
			List<FormattedCharSequence> lines = font.split(Component.translatable("screen.aliveworkplace.pokemon_trader.no_offers"),
				PokemonTradeView.LIST_W - 20);
			int y = PokemonTradeView.LIST_Y + PokemonTradeView.LIST_H / 2 - lines.size() * 5;
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, cx - font.width(line) / 2, y, TableStyle.SUBLINE, false);
				y += 10;
			}
		}
		boolean party = false;
		for (int i = 0; i < 6; i++) {
			party |= ChoiceView.shown(icon(PokemonTradeView.PARTY + i));
		}
		if (!party && any) {
			int cx = PokemonTradeView.PARTY_X + PokemonTradeView.SIDE_W / 2;
			List<FormattedCharSequence> lines = font.split(Component.translatable("screen.aliveworkplace.pokemon_trader.no_party"),
				PokemonTradeView.SIDE_W - 8);
			int y = PokemonTradeView.PARTY_Y + 36 - lines.size() * 5;
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, cx - font.width(line) / 2, y, TableStyle.SUBLINE, false);
				y += 10;
			}
		}
		ItemStack status = icon(PokemonTradeView.STATUS);
		if (!status.isEmpty()) {
			int y = PokemonTradeView.STATUS_Y;
			for (FormattedCharSequence line : font.split(Component.literal(status.getHoverName().getString()), PokemonTradeView.SIDE_W - 4)) {
				if (y + 9 > PokemonTradeView.STATUS_BOTTOM) {
					break;
				}
				g.drawString(font, line, PokemonTradeView.PARTY_X + 2, y, TableStyle.BODY, false);
				y += 10;
			}
		}
		int[] whisper = io.github.jcondedata.aliveworkplace.ui.CopperLayout.whisper(new int[]{0, 0, WIDTH, HEIGHT});
		TableStyle.footerHint(g, font, whisperText(mouseX, mouseY), whisper[0] + 4, whisper[1] + 3, whisper[2] - 8);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		if (hoveredSlot == null) {
			// Over an offer's row but not its cell: the offer's own tooltip, as over the cell.
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				ItemStack offer = icon(PokemonTradeView.OFFERS + row);
				g.renderTooltip(font, getTooltipFromContainerItem(offer), offer.getTooltipImage(), mouseX, mouseY);
				return;
			}
		}
		renderTooltip(g, mouseX, mouseY);
	}

	/** The window's box on the screen (Copperline's shell is drawn round it). */
	private int[] window() {
		return new int[]{leftPos, topPos, WIDTH, HEIGHT};
	}

	private boolean overClose(double mouseX, double mouseY) {
		return io.github.jcondedata.aliveworkplace.ui.CopperLayout.inside(io.github.jcondedata.aliveworkplace.ui.CopperLayout.close(window()), mouseX, mouseY);
	}

	/** The lintel's words: how many offers the trader has today ("No offers today" with none). */
	private Component offersLine() {
		int n = 0;
		for (int i = 0; i < PokemonTradeView.MAX_OFFERS; i++) {
			n += ChoiceView.shown(icon(PokemonTradeView.OFFERS + i)) ? 1 : 0;
		}
		return n == 0 ? Component.translatable("screen.aliveworkplace.pokemon_trader.offers_none")
			: io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.pokemon_trader.offers", n, n);
	}

	/**
	 * The whisper line (48.4): what the thing under the mouse is (an offer's name and its price, a party Pokémon's name,
	 * the trader's book), or the screen's hint. The tooltip still gives the details.
	 */
	private Component whisperText(int mouseX, int mouseY) {
		mouseX += leftPos; // renderLabels draws from the panel's corner; the mouse is the screen's
		mouseY += topPos;
		if (overClose(mouseX, mouseY)) {
			return Component.translatable("screen.aliveworkplace.pokemon_trader.close_whisper");
		}
		int row = rowAt(mouseX, mouseY);
		if (row >= 0) {
			ItemStack cost = icon(PokemonTradeView.COSTS + row);
			String name = icon(PokemonTradeView.OFFERS + row).getHoverName().getString();
			return cost.isEmpty() ? Component.literal(name) : Component.translatable("screen.aliveworkplace.pokemon_trader.offer_whisper", name, cost.getHoverName());
		}
		if (hoveredSlot != null && hoveredSlot.isActive() && hoveredSlot.index < io.github.jcondedata.aliveworkplace.work.ChoiceMenu.SIZE && ChoiceView.shown(hoveredSlot.getItem())) {
			return hoveredSlot.getItem().getHoverName();
		}
		return Component.translatable("screen.aliveworkplace.pokemon_trader.hint");
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && overClose(mouseX, mouseY)) {
			onClose(); // the close X closes as Esc does
			return true;
		}
		if (hoveredSlot == null || !hoveredSlot.isActive()) {
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				Slot slot = menu.slots.get(PokemonTradeView.OFFERS + row);
				slotClicked(slot, slot.index, button, ClickType.PICKUP);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Where button {@code slot}'s middle is on the screen, in GUI pixels (the screenshot harness points at it). */
	public int[] centre(int slot) {
		Slot s = menu.slots.get(slot);
		return new int[]{leftPos + s.x + 8, topPos + s.y + 8};
	}
}
