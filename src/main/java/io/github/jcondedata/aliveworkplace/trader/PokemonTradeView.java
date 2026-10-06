package io.github.jcondedata.aliveworkplace.trader;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The client's side of the Pokémon Trader's screen (ROADMAP 28.23): the server's {@link ChoiceMenu} with the same 54
 * slots (then the player's 36, hidden), placed where the trader's drawn screen puts them. The slots are:
 * <ul>
 * <li>{@link #OFFERS} to +3: the day's offers, one card each down the left panel (the Pokémon on the card's button);
 * <li>{@link #BALLS} to +3: the ball each offered Pokémon comes in, at the card's right end;
 * <li>{@link #INFO}: the trader's book, bottom right;
 * <li>{@link #PARTY} to +5: the player's party, two rows of three on the right;
 * <li>{@link #COSTS} to +3 and {@link #STATUS}: text the screen writes (an icon's name), never drawn as items.
 * </ul>
 * Clicks change nothing here: the server runs the button and sends the screen back.
 */
public class PokemonTradeView extends AbstractContainerMenu {
	public static final int OFFERS = 0;
	public static final int MAX_OFFERS = 4;
	public static final int INFO = 8;
	public static final int BALLS = 9;
	public static final int PARTY = 18;
	public static final int COSTS = 27;
	public static final int STATUS = 35;
	/** A card's top-left corner on the screen (cards are 150x30, 32 apart). */
	public static final int CARD_X = 8;
	public static final int CARD_Y = 18;
	public static final int CARD_STEP = 32;

	public static int cardY(int offer) {
		return CARD_Y + offer * CARD_STEP;
	}

	public PokemonTradeView(int id, Inventory inventory) {
		super(ModBlocks.POKEMON_TRADER_MENU, id);
		SimpleContainer icons = new SimpleContainer(ChoiceMenu.SIZE);
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			int x = -1000;
			int y = -1000;
			boolean shown = true;
			if (i >= OFFERS && i < OFFERS + MAX_OFFERS) {
				x = CARD_X + 5;
				y = cardY(i - OFFERS) + 7;
			} else if (i >= BALLS && i < BALLS + MAX_OFFERS) {
				x = CARD_X + 130;
				y = cardY(i - BALLS) + 7;
			} else if (i >= PARTY && i < PARTY + 6) {
				x = 175 + (i - PARTY) % 3 * 24;
				y = 34 + (i - PARTY) / 3 * 24;
			} else if (i == INFO) {
				x = 232;
				y = 132;
			} else {
				shown = false;
			}
			addSlot(new Button(icons, i, x, y, shown));
		}
		for (int i = 0; i < 27; i++) {
			addSlot(new Button(inventory, 9 + i, -1000, -1000, false));
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Button(inventory, i, -1000, -1000, false));
		}
	}

	@Override
	public void clicked(int slot, int button, ClickType type, Player player) {
		// Nothing moves: the server runs the button.
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	private static final class Button extends Slot {
		private final boolean shown;

		Button(net.minecraft.world.Container container, int index, int x, int y, boolean shown) {
			super(container, index, x, y);
			this.shown = shown;
		}

		@Override
		public boolean isActive() {
			return shown && ChoiceView.shown(getItem());
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
