package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The client's side of a {@link ChoiceMenu} shown on the Village Hall's own screen (ROADMAP 30.4a) instead of a chest.
 * It has the same slots in the same order (the 54 buttons, then the player's 36), so the server's clicks and icons
 * line up, but the buttons sit where the hall's drawn screen puts them: row 0 on the header plaque, row 1 on the
 * toolbar, rows 2 to 5 on the page panel, 20 pixels apart. Empty slots and divider panes are inactive (not drawn, not
 * hovered), and so is the player's inventory, which the hall doesn't show. Clicks change nothing here: the server
 * runs the button and sends the page back.
 */
public class ChoiceView extends AbstractContainerMenu {
	/** Each row's top edge (a button's 18x18 cell; its icon is 1 pixel in). */
	public static final int[] ROW_Y = {18, 44, 70, 90, 110, 130};

	/** A column's left edge. */
	public static int cellX(int column) {
		return 8 + column * 20;
	}

	public ChoiceView(int id, Inventory inventory) {
		super(ModBlocks.VILLAGE_HALL_MENU, id);
		SimpleContainer icons = new SimpleContainer(ChoiceMenu.SIZE);
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			addSlot(new Button(icons, i, cellX(i % 9) + 1, ROW_Y[i / 9] + 1));
		}
		// The player's inventory in ChestMenu's order (three rows, then the hotbar), hidden.
		for (int i = 0; i < 27; i++) {
			addSlot(new Hidden(inventory, 9 + i));
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Hidden(inventory, i));
		}
	}

	/** Whether {@code icon} is a real button (not nothing, not a divider pane). */
	public static boolean shown(ItemStack icon) {
		return !icon.isEmpty() && !icon.is(Items.GRAY_STAINED_GLASS_PANE);
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
		Button(SimpleContainer icons, int index, int x, int y) {
			super(icons, index, x, y);
		}

		@Override
		public boolean isActive() {
			return shown(getItem());
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

	private static final class Hidden extends Slot {
		Hidden(Inventory inventory, int index) {
			super(inventory, index, -1000, -1000);
		}

		@Override
		public boolean isActive() {
			return false;
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
