package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The list screen (ROADMAP 42.1) for the menus that were a plain six-row chest ({@code ChoiceMenu.openList}): the
 * Village Hall's look, with the title and the money at the top, a tab row, rows with a button on each, gray rows that
 * can't be picked and a footer hint. It is {@link ChoiceTableScreen}'s list mode; what each icon is comes from its mark
 * ({@link io.github.jcondedata.aliveworkplace.work.ChoiceList}). The Move Tutor's lessons first.
 */
public class ChoiceListScreen extends ChoiceTableScreen {
	public ChoiceListScreen(ChoiceView menu, Inventory inventory, Component title) {
		super(menu, inventory, title, new int[]{0}, 0, -1);
	}

	@Override
	protected boolean listMode() {
		return true;
	}
}
