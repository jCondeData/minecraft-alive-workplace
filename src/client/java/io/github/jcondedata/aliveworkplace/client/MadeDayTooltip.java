package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;

/** An aging good's tooltip (ROADMAP 34.5): "Pressed on day 42 · vintage in 2 days", by the client's own clock. */
public final class MadeDayTooltip {
	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			LuxuryRecipes.MadeDay made = stack.get(ModComponents.MADE_DAY);
			Minecraft mc = Minecraft.getInstance();
			if (made != null && mc.level != null) {
				lines.add(LuxuryRecipes.tooltip(made, LuxuryRecipes.today(mc.level)));
			}
		});
	}

	private MadeDayTooltip() {
	}
}
