package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

/**
 * {@code grand_rebuild} (the Master Architect, 29.12): every {@code days} days the Legend hands the least busy builder of
 * the village the next upgrade of a building its builders finished, or, with none, the building redrawn in the
 * {@code style} style. See {@link GrandRebuild}.
 */
public record GrandRebuildPower(int days, String style) implements Power {
	static GrandRebuildPower read(JsonObject json) {
		int days = json.has("days") ? json.get("days").getAsInt() : 3;
		if (days < 1) {
			throw new IllegalArgumentException("'days' below 1");
		}
		return new GrandRebuildPower(days, json.has("style") ? json.get("style").getAsString() : GrandRebuild.GRAND);
	}

	@Override
	public String type() {
		return "grand_rebuild";
	}

	/** "Every 3 days, a finished building is upgraded or redrawn in the Grand style". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.grand_rebuild", days,
			io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.get(style)
				.map(io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.Style::title).orElse(Component.literal(style)));
	}
}
