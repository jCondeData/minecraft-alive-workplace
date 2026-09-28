package io.github.jcondedata.aliveworkplace.work;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A screen of buttons shown as a six-row chest: every slot holds an icon, and clicking one runs code on
 * the server. Nothing can be taken out or put in (the player's own inventory is locked while it's open),
 * so the client needs no screen of its own. Used by jobs that offer choices (the Move Tutor's lessons).
 */
public class ChoiceMenu extends ChestMenu {
	public static final int ROWS = 6;
	public static final int SIZE = ROWS * 9;

	private final SimpleContainer icons;
	private final Predicate<Player> valid;
	private final Map<Integer, Consumer<ServerPlayer>> actions = new HashMap<>();

	private ChoiceMenu(int id, net.minecraft.world.entity.player.Inventory inventory, SimpleContainer icons, Predicate<Player> valid) {
		super(MenuType.GENERIC_9x6, id, inventory, icons, ROWS);
		this.icons = icons;
		this.valid = valid;
	}

	/** Opens an empty menu for {@code player}, lets {@code fill} lay out the buttons and returns it (or null). */
	@Nullable
	public static ChoiceMenu open(ServerPlayer player, Component title, Predicate<Player> valid, Consumer<ChoiceMenu> fill) {
		ChoiceMenu[] opened = new ChoiceMenu[1];
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
			ChoiceMenu menu = new ChoiceMenu(id, inventory, new SimpleContainer(SIZE), valid);
			fill.accept(menu);
			opened[0] = menu;
			return menu;
		}, title));
		return opened[0];
	}

	/** A menu that isn't shown to anyone (tests). */
	public static ChoiceMenu detached(ServerPlayer player, Consumer<ChoiceMenu> fill) {
		ChoiceMenu menu = new ChoiceMenu(0, player.getInventory(), new SimpleContainer(SIZE), p -> true);
		fill.accept(menu);
		return menu;
	}

	/** Removes every button (before laying out the next page). */
	public void clearButtons() {
		icons.clearContent();
		actions.clear();
	}

	/** Puts {@code icon} in {@code slot}; clicking it runs {@code action} (null: just a label). */
	public void button(int slot, ItemStack icon, @Nullable Consumer<ServerPlayer> action) {
		icons.setItem(slot, icon);
		if (action == null) {
			actions.remove(slot);
		} else {
			actions.put(slot, action);
		}
	}

	/** Grey glass across a row, to separate parts of the screen. */
	public void divider(int row) {
		for (int x = 0; x < 9; x++) {
			ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
			pane.set(DataComponents.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
			button(row * 9 + x, pane, null);
		}
	}

	public ItemStack icon(int slot) {
		return icons.getItem(slot);
	}

	/** Clicks a button as {@code player} would (also what a real click does). */
	public void press(int slot, ServerPlayer player) {
		Consumer<ServerPlayer> action = actions.get(slot);
		if (action != null) {
			action.accept(player);
		}
	}

	@Override
	public void clicked(int slot, int button, ClickType type, Player player) {
		// Nothing moves: the client's guess is corrected when the server sends the slots back.
		if (player instanceof ServerPlayer sp && slot >= 0 && slot < SIZE && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
			press(slot, sp);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return valid.test(player);
	}
}
