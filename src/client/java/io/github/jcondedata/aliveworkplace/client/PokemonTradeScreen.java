package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Pokémon Trader's own screen (ROADMAP 28.23), drawn like the Village Hall's (30.4a) and vanilla's workstation screens
 * (textures/gui/pokemon_trader.png, drawn by tools/textures/art/gui.py): the day's offers as cards down a sunken panel on
 * the left, each with the Pokémon on its button, its name, level and shiny mark, what it costs and the ball it comes in;
 * the player's party on the right, a Pokémon that won't do greyed out and the one waiting for the second click pressed
 * in; and a line saying what to do next. With no offers the panel says so. The clicks are the server's
 * {@code ChoiceMenu} ones, and the texts are the server's (an icon's name), so nothing here decides a trade.
 */
public class PokemonTradeScreen extends AbstractContainerScreen<PokemonTradeView> {
	public static final ResourceLocation TEXTURE = AliveWorkplace.id("textures/gui/pokemon_trader.png");
	public static final int WIDTH = 256;
	public static final int HEIGHT = 156;
	private static final int BUTTON_V = 176;
	private static final int CARD_V = 194;
	private static final int CARD_W = 150;
	private static final int CARD_H = 30;
	private static final int TEXT = 0xFF404040;
	private static final int SOFT = 0xFF606060;
	private static final int GOLD = 0xFFB07A00;
	private static final int ON_DEEP = 0xFFE0E0E0;
	private static final String STAR = "★";

	public PokemonTradeScreen(PokemonTradeView menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = WIDTH;
		imageHeight = HEIGHT;
		titleLabelX = 8;
		titleLabelY = 6;
	}

	private ItemStack icon(int slot) {
		return menu.slots.get(slot).getItem();
	}

	private static boolean picked(ItemStack icon) {
		return Boolean.TRUE.equals(icon.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		for (int i = 0; i < PokemonTradeView.MAX_OFFERS; i++) {
			ItemStack offer = icon(PokemonTradeView.OFFERS + i);
			if (ChoiceView.shown(offer)) {
				g.blit(TEXTURE, leftPos + PokemonTradeView.CARD_X, topPos + PokemonTradeView.cardY(i), 0, CARD_V + (picked(offer) ? CARD_H : 0),
					CARD_W, CARD_H);
			}
		}
		for (Slot slot : menu.slots) {
			int index = slot.index;
			boolean ball = index >= PokemonTradeView.BALLS && index < PokemonTradeView.BALLS + PokemonTradeView.MAX_OFFERS;
			if (slot.isActive() && !ball) {
				boolean hovered = isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
				boolean pressed = index >= PokemonTradeView.PARTY && picked(slot.getItem());
				g.blit(TEXTURE, leftPos + slot.x - 1, topPos + slot.y - 1, pressed ? 36 : hovered ? 18 : 0, BUTTON_V, 18, 18);
			}
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
		g.drawString(font, Component.translatable("screen.aliveworkplace.pokemon_trader.party"), 166, 18, TEXT, false);
		boolean any = false;
		for (int i = 0; i < PokemonTradeView.MAX_OFFERS; i++) {
			ItemStack offer = icon(PokemonTradeView.OFFERS + i);
			if (!ChoiceView.shown(offer)) {
				continue;
			}
			any = true;
			int x = PokemonTradeView.CARD_X + 26;
			int y = PokemonTradeView.cardY(i);
			String name = offer.getHoverName().getString();
			boolean shiny = name.endsWith(STAR);
			if (shiny) {
				name = name.substring(0, name.length() - STAR.length()).stripTrailing();
			}
			int room = 102 - (shiny ? 10 : 0);
			name = fit(name, room);
			g.drawString(font, name, x, y + 6, TEXT, false);
			if (shiny) {
				g.drawString(font, STAR, x + font.width(name) + 3, y + 6, GOLD, false);
			}
			ItemStack cost = icon(PokemonTradeView.COSTS + i);
			if (!cost.isEmpty()) {
				g.drawString(font, fit(cost.getHoverName().getString(), 102), x, y + 17, SOFT, false);
			}
		}
		if (!any) {
			List<FormattedCharSequence> lines = font.split(Component.translatable("screen.aliveworkplace.pokemon_trader.no_offers"), 140);
			int y = 83 - lines.size() * 5;
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, 83 - font.width(line) / 2, y, ON_DEEP, false);
				y += 10;
			}
		}
		boolean party = false;
		for (int i = 0; i < 6; i++) {
			party |= ChoiceView.shown(icon(PokemonTradeView.PARTY + i));
		}
		if (!party && any) {
			List<FormattedCharSequence> lines = font.split(Component.translatable("screen.aliveworkplace.pokemon_trader.no_party"), 80);
			int y = 54 - lines.size() * 5;
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, 207 - font.width(line) / 2, y, ON_DEEP, false);
				y += 10;
			}
		}
		ItemStack status = icon(PokemonTradeView.STATUS);
		if (!status.isEmpty()) {
			int y = 90;
			for (FormattedCharSequence line : font.split(Component.literal(status.getHoverName().getString()), 84)) {
				if (y > 120) {
					break;
				}
				g.drawString(font, line, 166, y, TEXT, false);
				y += 10;
			}
		}
	}

	private String fit(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
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
