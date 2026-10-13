package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.screens.Screen;

/**
 * A workstation's jobs on its tooltip (ROADMAP 21.1a): hold Shift over a composter, a grindstone, a Shop Counter... to
 * see which job a villager takes by themselves there and which item picks each of the others. The lines are
 * {@link Stations#tooltip}'s (none on a menu button's picture, B141).
 */
public final class StationTooltip {
	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> lines.addAll(Stations.tooltip(stack, Screen.hasShiftDown())));
	}

	private StationTooltip() {
	}
}
