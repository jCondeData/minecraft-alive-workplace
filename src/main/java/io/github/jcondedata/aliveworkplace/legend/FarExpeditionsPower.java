package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code far_expeditions} (the Pathfinder, 29.13): the Legend's own expeditions ({@code ExplorerWork}) range {@code factor}
 * times as far and also roll the loot table {@code loot} at every stop.
 */
public record FarExpeditionsPower(float factor, ResourceLocation loot) implements Power {
	static FarExpeditionsPower read(JsonObject json) {
		float factor = json.has("factor") ? json.get("factor").getAsFloat() : 2f;
		if (factor < 1f) {
			throw new IllegalArgumentException("'factor' below 1");
		}
		ResourceLocation loot = ResourceLocation.tryParse(json.has("loot") ? json.get("loot").getAsString() : "aliveworkplace:explorer/pathfinder");
		if (loot == null) {
			throw new IllegalArgumentException("bad 'loot'");
		}
		return new FarExpeditionsPower(factor, loot);
	}

	@Override
	public String type() {
		return "far_expeditions";
	}

	/** "Their own expeditions range 2 times as far, and bring back more maps". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.far_expeditions", factor == Math.round(factor) ? String.valueOf(Math.round(factor)) : String.valueOf(factor));
	}
}
