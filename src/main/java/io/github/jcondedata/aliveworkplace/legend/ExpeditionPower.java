package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

/**
 * {@code expedition} (the Pathfinder, 29.13): a sneak-right-click with {@code food} rations in their chests and they lead
 * the player to the nearest Stronghold, Ancient City or Trial Chambers within {@code reach} blocks, once a day. See
 * {@link Pathfinder}.
 */
public record ExpeditionPower(int food, int reach) implements Power {
	static ExpeditionPower read(JsonObject json) {
		int food = json.has("food") ? json.get("food").getAsInt() : 8;
		int reach = json.has("reach") ? json.get("reach").getAsInt() : 3000;
		if (food < 0 || reach < 16) {
			throw new IllegalArgumentException("'food' below 0 or 'reach' below 16");
		}
		return new ExpeditionPower(food, reach);
	}

	@Override
	public String type() {
		return "expedition";
	}

	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.expedition", food, reach);
	}
}
