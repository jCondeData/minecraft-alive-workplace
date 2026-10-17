package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Village Hall's own screen (ROADMAP 30.4a), drawn in the Blueprint Table's style since 26.12 ({@link ChoiceTableScreen}):
 * the village's figures (the top row) and the page's actions (the toolbar row) as icon cells under the title, the page
 * itself (the page tabs, the people, a page's entries) as the list on the left, and the pane on the right reading out
 * what is under the mouse. Used for the hall's main page, all its pages and the Book of Edicts; the clicks are the
 * server's {@code ChoiceMenu} ones. The Trade page's marks (33.4) and the leaderboard's ribbon (33.19) are drawn over
 * the icons wherever they are.
 */
public class VillageHallMenuScreen extends ChoiceTableScreen {
	public VillageHallMenuScreen(ChoiceView menu, Inventory inventory, Component title) {
		super(menu, inventory, title, new int[]{0, 1}, 2, 2);
	}
}
