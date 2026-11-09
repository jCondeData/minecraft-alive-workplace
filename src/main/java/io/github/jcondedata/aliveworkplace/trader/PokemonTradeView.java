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
 * The client's side of the Pokémon Trader's screen (ROADMAP 28.23, in the Village Hall's look since 43.2): the server's
 * {@link ChoiceMenu} with the same 54 slots (then the player's 36, hidden), placed where the screen draws them. The slots
 * are:
 * <ul>
 * <li>{@link #OFFERS} to +3: the day's offers, one row each down the list on the left (the Pokémon in the row's cell);
 * <li>{@link #BALLS} to +3: the ball each offered Pokémon comes in, at the row's right end;
 * <li>{@link #INFO}: the trader's book, top right by the title;
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
	/** The panel's size (fixed: it fits the smallest GUI-scaled screen, 320 wide, with 8 pixels round it). */
	public static final int WIDTH = 300;
	public static final int HEIGHT = 174;
	/** The offers list's box on the panel. */
	public static final int LIST_X = 6;
	public static final int LIST_Y = 22;
	public static final int LIST_W = 180;
	public static final int LIST_H = 134;
	/** An offer row's left edge and top (rows are {@link #ROW_H} tall, {@link #ROW_STEP} apart). */
	public static final int CARD_X = LIST_X + 2;
	public static final int CARD_Y = LIST_Y + 3;
	public static final int CARD_W = LIST_W - 4;
	public static final int ROW_H = 30;
	public static final int ROW_STEP = 32;
	/** The party's box on the right: a heading, then two rows of three cells. */
	public static final int PARTY_X = LIST_X + LIST_W + 8;
	public static final int PARTY_Y = LIST_Y + 4;
	public static final int SIDE_W = WIDTH - PARTY_X - 6;
	/** Where the status lines start, under the party, and how far down they may go. */
	public static final int STATUS_Y = PARTY_Y + 64;
	public static final int STATUS_BOTTOM = LIST_Y + LIST_H;

	public static int cardY(int offer) {
		return CARD_Y + offer * ROW_STEP;
	}

	/**
	 * Which offer row the panel point ({@code x}, {@code y}) is on (relative to the panel's top left), or -1: a click
	 * anywhere on a row picks its offer, as a click on its cell does.
	 */
	public static int offerAt(int x, int y) {
		if (x < CARD_X || x >= CARD_X + CARD_W || y < CARD_Y) {
			return -1;
		}
		int row = (y - CARD_Y) / ROW_STEP;
		if (row >= MAX_OFFERS || y - cardY(row) >= ROW_H) {
			return -1;
		}
		return row;
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
				x = CARD_X + CARD_W - 22;
				y = cardY(i - BALLS) + 7;
			} else if (i >= PARTY && i < PARTY + 6) {
				x = PARTY_X + 5 + (i - PARTY) % 3 * 24;
				y = PARTY_Y + 17 + (i - PARTY) / 3 * 22;
			} else if (i == INFO) {
				x = WIDTH - 24;
				y = 4;
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
