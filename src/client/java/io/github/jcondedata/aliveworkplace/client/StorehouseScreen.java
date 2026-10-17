package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Storehouse board (ROADMAP 26.12), drawn in the Blueprint Table's style ({@link ChoiceTableScreen}) instead of a
 * locked chest: the board's top row (what's in store, the Steward's shopping list, the stock orders) as icon cells under
 * the title and read out "at a glance", the requests (or the orders) as the list on the left, each with what it is and
 * whose, and the pane on the right reading out the one under the mouse. A click hands over what you have, as before.
 */
public class StorehouseScreen extends ChoiceTableScreen {
	public StorehouseScreen(ChoiceView menu, Inventory inventory, Component title) {
		super(menu, inventory, title, new int[]{0}, 1, -1);
	}
}
